package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.GuardianshipEntity;
import com.campx.student.security.UserSecurityContext;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code stu.guardianships}.
 * Enforces transaction-scoped tenant claims and least-privilege role switching.
 */
public class PostgresGuardianshipRepository implements GuardianshipRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresGuardianshipRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresGuardianshipRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresGuardianshipRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) throw new IllegalArgumentException("connectionManager cannot be null");
        this.connectionManager = connectionManager;
    }

    @Override
    public GuardianshipEntity save(UserSecurityContext context, GuardianshipEntity guardianship) {
        assertContext(context);
        if (guardianship == null) throw new IllegalArgumentException("guardianship cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (guardianship.getId() == null) {
                return insertGuardianship(conn, context, guardianship);
            } else {
                return updateGuardianship(conn, context, guardianship);
            }
        });
    }

    @Override
    public List<GuardianshipEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        assertContext(context);
        if (studentId == null) return new ArrayList<>();

        return context.executeInTransaction(connectionManager, conn -> {
            List<GuardianshipEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, student_id, guardian_person_id, relationship, " +
                    "is_primary, can_view_academics, can_view_fees, can_pay_fees, status, " +
                    "created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM stu.guardianships " +
                    "WHERE tenant_id = ? AND student_id = ? AND deleted_at IS NULL " +
                    "ORDER BY is_primary DESC, created_at ASC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, studentId);
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
    public Optional<GuardianshipEntity> findPrimaryByStudentId(UserSecurityContext context, UUID studentId) {
        assertContext(context);
        if (studentId == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, student_id, guardian_person_id, relationship, " +
                    "is_primary, can_view_academics, can_view_fees, can_pay_fees, status, " +
                    "created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM stu.guardianships " +
                    "WHERE tenant_id = ? AND student_id = ? AND is_primary = true AND deleted_at IS NULL " +
                    "LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    private GuardianshipEntity insertGuardianship(Connection conn, UserSecurityContext context, GuardianshipEntity g) throws SQLException {
        UUID id = UUID.randomUUID();
        g.setId(id);
        g.setTenantId(context.getTenantId());

        String sql = "INSERT INTO stu.guardianships (id, tenant_id, student_id, guardian_person_id, relationship, " +
                "is_primary, can_view_academics, can_view_fees, can_pay_fees, status, " +
                "created_by, updated_by, row_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) " +
                "RETURNING created_at, updated_at, row_version";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, context.getTenantId());
            ps.setObject(3, g.getStudentId());
            ps.setObject(4, g.getGuardianPersonId());
            ps.setString(5, g.getRelationship());
            ps.setBoolean(6, g.isPrimary());
            ps.setBoolean(7, g.isCanViewAcademics());
            ps.setBoolean(8, g.isCanViewFees());
            ps.setBoolean(9, g.isCanPayFees());
            ps.setString(10, g.getStatus() != null ? g.getStatus() : "ACTIVE");
            ps.setObject(11, context.getUserId());
            ps.setObject(12, context.getUserId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp cat = rs.getTimestamp("created_at");
                    Timestamp uat = rs.getTimestamp("updated_at");
                    g.setCreatedAt(cat != null ? cat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    g.setUpdatedAt(uat != null ? uat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    g.setRowVersion(rs.getInt("row_version"));
                }
            }
        }
        return g;
    }

    private GuardianshipEntity updateGuardianship(Connection conn, UserSecurityContext context, GuardianshipEntity g) throws SQLException {
        String sql = "UPDATE stu.guardianships SET relationship = ?, is_primary = ?, " +
                "can_view_academics = ?, can_view_fees = ?, can_pay_fees = ?, updated_by = ?, updated_at = now(), " +
                "row_version = row_version + 1 " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                "RETURNING updated_at, row_version";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.getRelationship());
            ps.setBoolean(2, g.isPrimary());
            ps.setBoolean(3, g.isCanViewAcademics());
            ps.setBoolean(4, g.isCanViewFees());
            ps.setBoolean(5, g.isCanPayFees());
            ps.setObject(6, context.getUserId());
            ps.setObject(7, g.getId());
            ps.setObject(8, context.getTenantId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp uat = rs.getTimestamp("updated_at");
                    g.setUpdatedAt(uat != null ? uat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    g.setRowVersion(rs.getInt("row_version"));
                }
            }
        }
        return g;
    }

    private GuardianshipEntity mapResultSet(ResultSet rs) throws SQLException {
        GuardianshipEntity g = new GuardianshipEntity();
        g.setId((UUID) rs.getObject("id"));
        g.setTenantId((UUID) rs.getObject("tenant_id"));
        g.setStudentId((UUID) rs.getObject("student_id"));
        g.setGuardianPersonId((UUID) rs.getObject("guardian_person_id"));
        g.setRelationship(rs.getString("relationship"));
        g.setPrimary(rs.getBoolean("is_primary"));
        g.setCanViewAcademics(rs.getBoolean("can_view_academics"));
        g.setCanViewFees(rs.getBoolean("can_view_fees"));
        g.setCanPayFees(rs.getBoolean("can_pay_fees"));
        g.setStatus(rs.getString("status"));

        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) g.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) g.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));

        g.setRowVersion(rs.getInt("row_version"));
        return g;
    }

    private void assertContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new StudentSecurityViolationException("Authenticated tenant context is strictly required");
        }
    }
}
