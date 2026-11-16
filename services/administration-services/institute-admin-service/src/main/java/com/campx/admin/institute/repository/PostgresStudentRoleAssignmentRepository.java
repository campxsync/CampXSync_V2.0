package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL persistence for STM-07 student role assignments on {@code iam.role_assignments}.
 * Catalogue code for R25 is {@code STUDENT}.
 */
public class PostgresStudentRoleAssignmentRepository implements StudentRoleAssignmentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStudentRoleAssignmentRepository.class);
    public static final String STUDENT_ROLE_CODE = "STUDENT";

    private final DatabaseConnectionManager connectionManager;

    public PostgresStudentRoleAssignmentRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresStudentRoleAssignmentRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public Optional<UUID> findStudentRoleId(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id FROM iam.roles WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, STUDENT_ROLE_CODE);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of((UUID) rs.getObject("id"));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<UUID> findActiveAssignmentId(UserSecurityContext context, UUID userId, UUID roleId) {
        checkContext(context);
        if (userId == null || roleId == null) {
            return Optional.empty();
        }
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id FROM iam.role_assignments "
                    + "WHERE tenant_id = ? AND user_id = ? AND role_id = ? AND deleted_at IS NULL "
                    + "AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) "
                    + "LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, userId);
                ps.setObject(3, roleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of((UUID) rs.getObject("id"));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public UUID insertAssignment(UserSecurityContext context, UUID userId, UUID roleId, UUID collegeId) {
        checkContext(context);
        if (userId == null || roleId == null) {
            throw new SecurityViolationException("userId and roleId are required for role assignment");
        }
        UUID assignmentId = UUID.randomUUID();
        UUID grantedBy = context.getUserId();
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO iam.role_assignments ("
                    + "id, tenant_id, role_id, user_id, college_id, granted_by, "
                    + "created_at, updated_at, created_by, updated_by, row_version"
                    + ") VALUES (?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, assignmentId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, roleId);
                ps.setObject(4, userId);
                ps.setObject(5, collegeId);
                ps.setObject(6, grantedBy);
                ps.setObject(7, grantedBy);
                ps.setObject(8, grantedBy);
                ps.executeUpdate();
            }
            logger.info("Inserted student role assignment id={} tenant={} user={}",
                    assignmentId, context.getTenantId(), userId);
            return assignmentId;
        });
    }

    @Override
    public boolean softDeleteAssignment(UserSecurityContext context, UUID assignmentId) {
        checkContext(context);
        if (assignmentId == null) {
            return false;
        }
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE iam.role_assignments SET deleted_at = now(), updated_at = now(), "
                    + "updated_by = ?, row_version = row_version + 1 "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, assignmentId);
                ps.setObject(3, context.getTenantId());
                int updated = ps.executeUpdate();
                return updated > 0;
            }
        });
    }
}
