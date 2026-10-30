package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.model.PersonIdentifierEntity;
import com.campx.student.security.UserSecurityContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code core.person_identifiers}.
 * Persists alternate and national identifiers with SHA-256 hashing for duplicate detection
 * and field-level masking support (STM01-US-008, STM01-US-009).
 */
public class PostgresPersonIdentifierRepository implements PersonIdentifierRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresPersonIdentifierRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresPersonIdentifierRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresPersonIdentifierRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("connectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public PersonIdentifierEntity save(UserSecurityContext context, PersonIdentifierEntity identifier) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (identifier == null) throw new IllegalArgumentException("identifier cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (identifier.getId() == null) {
                return insertIdentifier(conn, context, identifier);
            } else {
                return updateIdentifier(conn, context, identifier);
            }
        });
    }

    private PersonIdentifierEntity insertIdentifier(Connection conn, UserSecurityContext context, PersonIdentifierEntity identifier) throws SQLException {
        UUID id = UUID.randomUUID();
        identifier.setId(id);
        identifier.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        identifier.setCreatedAt(now);
        identifier.setUpdatedAt(now);
        identifier.setRowVersion(1);

        byte[] hash = computeHash(identifier.getRawValue());
        byte[] ciphertext = identifier.getRawValue() != null ? identifier.getRawValue().getBytes(StandardCharsets.UTF_8) : null;

        String sql = "INSERT INTO core.person_identifiers (" +
                "id, tenant_id, person_id, id_type, masked_value, value_ciphertext, value_hash, " +
                "issuing_authority, valid_from, valid_to, is_verified, created_at, updated_at, " +
                "created_by, updated_by, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, identifier.getId());
            ps.setObject(2, identifier.getTenantId());
            ps.setObject(3, identifier.getPersonId());
            ps.setString(4, identifier.getIdType());
            ps.setString(5, identifier.getMaskedValue());
            ps.setBytes(6, ciphertext);
            ps.setBytes(7, hash);
            ps.setString(8, identifier.getIssuingAuthority());
            ps.setDate(9, identifier.getValidFrom() != null ? Date.valueOf(identifier.getValidFrom()) : null);
            ps.setDate(10, identifier.getValidTo() != null ? Date.valueOf(identifier.getValidTo()) : null);
            ps.setBoolean(11, identifier.isVerified());
            ps.setTimestamp(12, Timestamp.from(now.toInstant()));
            ps.setTimestamp(13, Timestamp.from(now.toInstant()));
            ps.setObject(14, context.getUserId());
            ps.setObject(15, context.getUserId());
            ps.setInt(16, identifier.getRowVersion());

            ps.executeUpdate();
            return identifier;
        }
    }

    private PersonIdentifierEntity updateIdentifier(Connection conn, UserSecurityContext context, PersonIdentifierEntity identifier) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        identifier.setUpdatedAt(now);
        int newVersion = identifier.getRowVersion() + 1;

        byte[] hash = computeHash(identifier.getRawValue());
        byte[] ciphertext = identifier.getRawValue() != null ? identifier.getRawValue().getBytes(StandardCharsets.UTF_8) : null;

        String sql = "UPDATE core.person_identifiers SET " +
                "masked_value = ?, value_ciphertext = ?, value_hash = ?, issuing_authority = ?, " +
                "valid_from = ?, valid_to = ?, is_verified = ?, updated_at = ?, updated_by = ?, row_version = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier.getMaskedValue());
            ps.setBytes(2, ciphertext);
            ps.setBytes(3, hash);
            ps.setString(4, identifier.getIssuingAuthority());
            ps.setDate(5, identifier.getValidFrom() != null ? Date.valueOf(identifier.getValidFrom()) : null);
            ps.setDate(6, identifier.getValidTo() != null ? Date.valueOf(identifier.getValidTo()) : null);
            ps.setBoolean(7, identifier.isVerified());
            ps.setTimestamp(8, Timestamp.from(now.toInstant()));
            ps.setObject(9, context.getUserId());
            ps.setInt(10, newVersion);
            ps.setObject(11, identifier.getId());
            ps.setObject(12, context.getTenantId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                identifier.setRowVersion(newVersion);
            }
            return identifier;
        }
    }

    @Override
    public Optional<PersonIdentifierEntity> findById(UserSecurityContext context, UUID id) {
        if (context == null || id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, person_id, id_type, masked_value, value_ciphertext, " +
                    "issuing_authority, valid_from, valid_to, is_verified, created_at, updated_at, " +
                    "created_by, updated_by, row_version, deleted_at " +
                    "FROM core.person_identifiers WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<PersonIdentifierEntity> findByPersonId(UserSecurityContext context, UUID personId) {
        if (context == null || personId == null) return new ArrayList<>();

        return context.executeInTransaction(connectionManager, conn -> {
            List<PersonIdentifierEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, person_id, id_type, masked_value, value_ciphertext, " +
                    "issuing_authority, valid_from, valid_to, is_verified, created_at, updated_at, " +
                    "created_by, updated_by, row_version, deleted_at " +
                    "FROM core.person_identifiers WHERE person_id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "ORDER BY created_at ASC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, personId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapResultSet(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public Optional<PersonIdentifierEntity> findByPersonIdAndType(UserSecurityContext context, UUID personId, String idType) {
        if (context == null || personId == null || idType == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, person_id, id_type, masked_value, value_ciphertext, " +
                    "issuing_authority, valid_from, valid_to, is_verified, created_at, updated_at, " +
                    "created_by, updated_by, row_version, deleted_at " +
                    "FROM core.person_identifiers WHERE person_id = ? AND tenant_id = ? " +
                    "AND LOWER(id_type) = LOWER(?) AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, personId);
                ps.setObject(2, context.getTenantId());
                ps.setString(3, idType.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean existsByTypeAndValue(UserSecurityContext context, String idType, String rawValue) {
        if (context == null || idType == null || rawValue == null) return false;

        byte[] hash = computeHash(rawValue);

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT 1 FROM core.person_identifiers WHERE tenant_id = ? " +
                    "AND LOWER(id_type) = LOWER(?) AND value_hash = ? AND deleted_at IS NULL LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, idType.trim());
                ps.setBytes(3, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private PersonIdentifierEntity mapResultSet(ResultSet rs) throws SQLException {
        PersonIdentifierEntity entity = new PersonIdentifierEntity();
        entity.setId((UUID) rs.getObject("id"));
        entity.setTenantId((UUID) rs.getObject("tenant_id"));
        entity.setPersonId((UUID) rs.getObject("person_id"));
        entity.setIdType(rs.getString("id_type"));
        entity.setMaskedValue(rs.getString("masked_value"));

        byte[] ciphertext = rs.getBytes("value_ciphertext");
        if (ciphertext != null) {
            entity.setRawValue(new String(ciphertext, StandardCharsets.UTF_8));
        }

        entity.setIssuingAuthority(rs.getString("issuing_authority"));
        Date vFrom = rs.getDate("valid_from");
        if (vFrom != null) entity.setValidFrom(vFrom.toLocalDate());
        Date vTo = rs.getDate("valid_to");
        if (vTo != null) entity.setValidTo(vTo.toLocalDate());
        entity.setVerified(rs.getBoolean("is_verified"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) entity.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) entity.setUpdatedAt(updatedAt.toInstant().atOffset(ZoneOffset.UTC));

        entity.setCreatedBy((UUID) rs.getObject("created_by"));
        entity.setUpdatedBy((UUID) rs.getObject("updated_by"));
        entity.setRowVersion(rs.getInt("row_version"));

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) entity.setDeletedAt(deletedAt.toInstant().atOffset(ZoneOffset.UTC));

        return entity;
    }

    private byte[] computeHash(String rawValue) {
        if (rawValue == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(rawValue.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
