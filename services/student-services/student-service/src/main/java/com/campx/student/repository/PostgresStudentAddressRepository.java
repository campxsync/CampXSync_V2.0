package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.model.StudentAddressEntity;
import com.campx.student.security.UserSecurityContext;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code core.addresses}.
 * Persists normalized postal addresses (current, permanent) for students and guardians (STM01-US-017).
 */
public class PostgresStudentAddressRepository implements StudentAddressRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStudentAddressRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresStudentAddressRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresStudentAddressRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("connectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public StudentAddressEntity save(UserSecurityContext context, StudentAddressEntity address) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (address == null) throw new IllegalArgumentException("address cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (address.getId() == null) {
                return insertAddress(conn, context, address);
            } else {
                return updateAddress(conn, context, address);
            }
        });
    }

    private StudentAddressEntity insertAddress(Connection conn, UserSecurityContext context, StudentAddressEntity address) throws SQLException {
        UUID id = UUID.randomUUID();
        address.setId(id);
        address.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        address.setRowVersion(1);

        String country = address.getCountryCode() != null && !address.getCountryCode().trim().isEmpty() ? address.getCountryCode().trim() : "IND";
        if (country.length() > 3) country = country.substring(0, 3);
        address.setCountryCode(country);

        String sql = "INSERT INTO core.addresses (" +
                "id, tenant_id, line1, line2, landmark, city, district, state_code, postal_code, " +
                "country_code, created_at, updated_at, created_by, updated_by, row_version" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, address.getId());
            ps.setObject(2, address.getTenantId());
            ps.setString(3, address.getLine1());
            ps.setString(4, address.getLine2());
            ps.setString(5, address.getLandmark());
            ps.setString(6, address.getCity());
            ps.setString(7, address.getDistrict());
            ps.setString(8, address.getStateCode());
            ps.setString(9, address.getPostalCode());
            ps.setString(10, address.getCountryCode());
            ps.setTimestamp(11, Timestamp.from(now.toInstant()));
            ps.setTimestamp(12, Timestamp.from(now.toInstant()));
            ps.setObject(13, context.getUserId());
            ps.setObject(14, context.getUserId());
            ps.setInt(15, address.getRowVersion());

            ps.executeUpdate();
            return address;
        }
    }

    private StudentAddressEntity updateAddress(Connection conn, UserSecurityContext context, StudentAddressEntity address) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        address.setUpdatedAt(now);
        int newVersion = address.getRowVersion() + 1;

        String country = address.getCountryCode() != null && !address.getCountryCode().trim().isEmpty() ? address.getCountryCode().trim() : "IND";
        if (country.length() > 3) country = country.substring(0, 3);
        address.setCountryCode(country);

        String sql = "UPDATE core.addresses SET " +
                "line1 = ?, line2 = ?, landmark = ?, city = ?, district = ?, state_code = ?, postal_code = ?, " +
                "country_code = ?, updated_at = ?, updated_by = ?, row_version = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getLine1());
            ps.setString(2, address.getLine2());
            ps.setString(3, address.getLandmark());
            ps.setString(4, address.getCity());
            ps.setString(5, address.getDistrict());
            ps.setString(6, address.getStateCode());
            ps.setString(7, address.getPostalCode());
            ps.setString(8, address.getCountryCode());
            ps.setTimestamp(9, Timestamp.from(now.toInstant()));
            ps.setObject(10, context.getUserId());
            ps.setInt(11, newVersion);
            ps.setObject(12, address.getId());
            ps.setObject(13, context.getTenantId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                address.setRowVersion(newVersion);
            }
            return address;
        }
    }

    @Override
    public Optional<StudentAddressEntity> findById(UserSecurityContext context, UUID id) {
        if (context == null || id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, line1, line2, landmark, city, district, state_code, " +
                    "postal_code, country_code, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.addresses WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

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

    private StudentAddressEntity mapResultSet(ResultSet rs) throws SQLException {
        StudentAddressEntity entity = new StudentAddressEntity();
        entity.setId((UUID) rs.getObject("id"));
        entity.setTenantId((UUID) rs.getObject("tenant_id"));
        entity.setLine1(rs.getString("line1"));
        entity.setLine2(rs.getString("line2"));
        entity.setLandmark(rs.getString("landmark"));
        entity.setCity(rs.getString("city"));
        entity.setDistrict(rs.getString("district"));
        entity.setStateCode(rs.getString("state_code"));
        entity.setPostalCode(rs.getString("postal_code"));
        entity.setCountryCode(rs.getString("country_code"));

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
}
