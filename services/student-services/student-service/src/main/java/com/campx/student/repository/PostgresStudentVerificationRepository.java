package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.model.StudentVerificationEntity;
import com.campx.student.security.UserSecurityContext;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code doc.verifications}.
 * Persists profile and document verification decisions, expiry dates, and audit evidence (STM01-US-013, STM01-US-014).
 */
public class PostgresStudentVerificationRepository implements StudentVerificationRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStudentVerificationRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresStudentVerificationRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresStudentVerificationRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("connectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public StudentVerificationEntity save(UserSecurityContext context, StudentVerificationEntity verification) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (verification == null) throw new IllegalArgumentException("verification cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (verification.getId() == null) {
                return insertVerification(conn, context, verification);
            } else {
                return updateVerification(conn, context, verification);
            }
        });
    }

    private StudentVerificationEntity insertVerification(Connection conn, UserSecurityContext context, StudentVerificationEntity verification) throws SQLException {
        UUID id = UUID.randomUUID();
        verification.setId(id);
        verification.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        verification.setCreatedAt(now);
        verification.setUpdatedAt(now);
        verification.setRowVersion(1);

        String sql = "INSERT INTO doc.verifications (" +
                "id, tenant_id, entity_type, entity_id, verification_code, result, verified_at, " +
                "created_at, created_by" +
                ") VALUES (?, ?, 'STUDENT', ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, verification.getId());
            ps.setObject(2, verification.getTenantId());
            ps.setObject(3, verification.getStudentId());
            ps.setString(4, verification.getVerificationType() != null ? verification.getVerificationType() : "IDENTITY");
            ps.setString(5, verification.getStatus() != null ? verification.getStatus() : "PENDING");
            ps.setTimestamp(6, verification.getVerifiedAt() != null ? Timestamp.from(verification.getVerifiedAt().toInstant()) : Timestamp.from(now.toInstant()));
            ps.setTimestamp(7, Timestamp.from(now.toInstant()));
            ps.setObject(8, verification.getVerifiedBy() != null ? verification.getVerifiedBy() : context.getUserId());

            ps.executeUpdate();
            return verification;
        }
    }

    private StudentVerificationEntity updateVerification(Connection conn, UserSecurityContext context, StudentVerificationEntity verification) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        verification.setUpdatedAt(now);
        int newVersion = verification.getRowVersion() + 1;

        String sql = "UPDATE doc.verifications SET " +
                "verification_code = ?, result = ?, verified_at = ? " +
                "WHERE id = ? AND tenant_id = ? AND entity_type = 'STUDENT'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, verification.getVerificationType());
            ps.setString(2, verification.getStatus());
            ps.setTimestamp(3, verification.getVerifiedAt() != null ? Timestamp.from(verification.getVerifiedAt().toInstant()) : Timestamp.from(now.toInstant()));
            ps.setObject(4, verification.getId());
            ps.setObject(5, context.getTenantId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                verification.setRowVersion(newVersion);
            }
            return verification;
        }
    }

    @Override
    public Optional<StudentVerificationEntity> findById(UserSecurityContext context, UUID id) {
        if (context == null || id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, entity_id, verification_code, result, verified_at, created_at, created_by " +
                    "FROM doc.verifications WHERE id = ? AND tenant_id = ? AND entity_type = 'STUDENT'";

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
    public List<StudentVerificationEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (context == null || studentId == null) return new ArrayList<>();

        return context.executeInTransaction(connectionManager, conn -> {
            List<StudentVerificationEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, entity_id, verification_code, result, verified_at, created_at, created_by " +
                    "FROM doc.verifications WHERE entity_id = ? AND tenant_id = ? AND entity_type = 'STUDENT' " +
                    "ORDER BY verified_at DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, studentId);
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
    public Optional<StudentVerificationEntity> findLatestByStudentId(UserSecurityContext context, UUID studentId) {
        if (context == null || studentId == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, entity_id, verification_code, result, verified_at, created_at, created_by " +
                    "FROM doc.verifications WHERE entity_id = ? AND tenant_id = ? AND entity_type = 'STUDENT' " +
                    "ORDER BY verified_at DESC LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, studentId);
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

    private StudentVerificationEntity mapResultSet(ResultSet rs) throws SQLException {
        StudentVerificationEntity entity = new StudentVerificationEntity();
        entity.setId((UUID) rs.getObject("id"));
        entity.setTenantId((UUID) rs.getObject("tenant_id"));
        entity.setStudentId((UUID) rs.getObject("entity_id"));
        entity.setVerificationType(rs.getString("verification_code"));
        entity.setStatus(rs.getString("result"));

        Timestamp vAt = rs.getTimestamp("verified_at");
        if (vAt != null) entity.setVerifiedAt(vAt.toInstant().atOffset(ZoneOffset.UTC));

        Timestamp cAt = rs.getTimestamp("created_at");
        if (cAt != null) {
            entity.setCreatedAt(cAt.toInstant().atOffset(ZoneOffset.UTC));
            entity.setUpdatedAt(cAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        entity.setVerifiedBy((UUID) rs.getObject("created_by"));
        entity.setRowVersion(1);

        return entity;
    }
}
