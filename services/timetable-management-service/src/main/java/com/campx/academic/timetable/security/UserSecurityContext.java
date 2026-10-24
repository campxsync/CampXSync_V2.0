package com.campx.academic.timetable.security;

import com.campx.academic.timetable.config.DatabaseConnectionManager;
import com.campx.academic.timetable.exception.TimetableForbiddenException;
import com.campx.academic.timetable.exception.TimetableUnauthorizedException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Handles authenticated security context extraction, UUID validation,
 * PostgreSQL transaction lifecycle, and role switching to 'authenticated'
 * with bound 'request.jwt.claims' for ACD-05 Timetable Management Service.
 */
public class UserSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(UserSecurityContext.class);

    private final UUID userId;
    private final UUID tenantId;
    private final UUID collegeId;
    private final UUID departmentId;
    private final String role;
    private final boolean platformScope;

    public static final String DEFAULT_SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000001";

    public UserSecurityContext(UUID userId, UUID tenantId) {
        this(userId, tenantId, null, null, "authenticated", false);
    }

    public UserSecurityContext(UUID userId, UUID tenantId, UUID collegeId, UUID departmentId, String role, boolean platformScope) {
        if (userId == null) {
            throw new TimetableUnauthorizedException("Missing or null authenticated user ID");
        }
        if (tenantId == null && !platformScope) {
            throw new TimetableForbiddenException("Missing or null authenticated tenant ID");
        }
        this.userId = userId;
        this.tenantId = tenantId;
        this.collegeId = collegeId;
        this.departmentId = departmentId;
        this.role = role != null ? role : "authenticated";
        this.platformScope = platformScope;
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCollegeId() { return collegeId; }
    public UUID getDepartmentId() { return departmentId; }
    public String getRole() { return role; }
    public boolean isPlatformScope() { return platformScope; }

    public static UserSecurityContext fromHeaders(String userIdHeader, String tenantIdHeader, String collegeIdHeader, String roleHeader) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            userIdHeader = DEFAULT_SYSTEM_USER_ID;
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new TimetableUnauthorizedException("Invalid X-User-Id format: " + userIdHeader);
        }

        if (tenantIdHeader == null || tenantIdHeader.trim().isEmpty()) {
            throw new TimetableForbiddenException("Missing required X-Tenant-Id header for timetable operations");
        }

        UUID tenantUuid;
        try {
            tenantUuid = UUID.fromString(tenantIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new TimetableForbiddenException("Invalid X-Tenant-Id format: " + tenantIdHeader);
        }

        UUID collegeUuid = null;
        if (collegeIdHeader != null && !collegeIdHeader.trim().isEmpty()) {
            try {
                collegeUuid = UUID.fromString(collegeIdHeader.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        String role = roleHeader != null && !roleHeader.trim().isEmpty() ? roleHeader.trim() : "authenticated";
        return new UserSecurityContext(userUuid, tenantUuid, collegeUuid, null, role, tenantUuid == null);
    }

    /**
     * Applies the in-transaction least-privilege role switch and claims binding:
     * 1. {@code SET LOCAL ROLE authenticated}
     * 2. {@code SELECT set_config('request.jwt.claims', ..., true)}
     *
     * @param conn Open active database connection.
     * @throws SQLException If database role switch or claims setting fails.
     */
    public void applyClaimsAndRole(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }

        String claimsJson;
        if (tenantId != null) {
            claimsJson = String.format(
                    "{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                    userId, tenantId, tenantId
            );
        } else {
            claimsJson = String.format(
                    "{\"sub\":\"%s\",\"role\":\"authenticated\"}",
                    userId
            );
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            ps.setString(1, claimsJson);
            ps.execute();
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * Executes a unit of work within a database transaction, applying authenticated
     * claims and least-privilege role, and ensuring complete role and claims reset
     * before returning the connection to the pool.
     */
    public <T> T executeInTransaction(DatabaseConnectionManager connectionManager, TransactionCallback<T> action) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                applyClaimsAndRole(conn);
                T result = action.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.warn("Transaction rollback encountered error: {}", rollbackEx.getMessage());
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Database operation failed: " + e.getMessage(), e);
            } finally {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("RESET ROLE");
                } catch (SQLException ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', '', false)")) {
                    ps.execute();
                } catch (SQLException ignored) {}
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error: " + e.getMessage(), e);
        }
    }
}
