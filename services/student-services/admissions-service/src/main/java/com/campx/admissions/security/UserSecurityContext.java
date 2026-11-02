package com.campx.admissions.security;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.exception.AdmissionsSecurityViolationException;
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
 * with bound 'request.jwt.claims' for STM-02 Admissions Domain Service.
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

    public UserSecurityContext(UUID userId, UUID tenantId, UUID collegeId, UUID departmentId, String role,
            boolean platformScope) {
        if (userId == null) {
            throw new AdmissionsSecurityViolationException("Missing or null authenticated user ID");
        }
        if (tenantId == null && !platformScope) {
            throw new AdmissionsSecurityViolationException("Missing or null authenticated tenant ID");
        }
        this.userId = userId;
        this.tenantId = tenantId;
        this.collegeId = collegeId;
        this.departmentId = departmentId;
        this.role = role != null ? role : "authenticated";
        this.platformScope = platformScope;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCollegeId() {
        return collegeId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getRole() {
        return role;
    }

    public boolean isPlatformScope() {
        return platformScope;
    }

    public static UserSecurityContext fromHeaders(String userIdHeader, String tenantIdHeader, String collegeIdHeader,
            String roleHeader) {
        return fromHeaders(userIdHeader, tenantIdHeader, collegeIdHeader, null, roleHeader);
    }

    public static UserSecurityContext fromHeaders(String userIdHeader, String tenantIdHeader, String collegeIdHeader,
            String deptIdHeader, String roleHeader) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            userIdHeader = DEFAULT_SYSTEM_USER_ID;
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new AdmissionsSecurityViolationException("Invalid X-User-Id format: " + userIdHeader);
        }

        if (tenantIdHeader == null || tenantIdHeader.trim().isEmpty()) {
            throw new AdmissionsSecurityViolationException(
                    "Missing required X-Tenant-Id header for admissions domain operations");
        }

        UUID tenantUuid;
        try {
            tenantUuid = UUID.fromString(tenantIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new AdmissionsSecurityViolationException("Invalid X-Tenant-Id format: " + tenantIdHeader);
        }

        UUID collegeUuid = null;
        if (collegeIdHeader != null && !collegeIdHeader.trim().isEmpty()) {
            try {
                collegeUuid = UUID.fromString(collegeIdHeader.trim());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid X-College-Id header format; ignoring: {}", collegeIdHeader);
            }
        }

        UUID deptUuid = null;
        if (deptIdHeader != null && !deptIdHeader.trim().isEmpty()) {
            try {
                deptUuid = UUID.fromString(deptIdHeader.trim());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid X-Department-Id header format; ignoring: {}", deptIdHeader);
            }
        }

        String normalizedRole = (roleHeader != null && !roleHeader.trim().isEmpty()) ? roleHeader.trim() : "Applicant";
        return new UserSecurityContext(userUuid, tenantUuid, collegeUuid, deptUuid, normalizedRole, false);
    }

    public void applyPostgresRoleContext(Connection connection) throws SQLException {
        if (connection == null)
            return;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }

        StringBuilder claimsJson = new StringBuilder();
        claimsJson.append("{\"sub\":\"").append(userId.toString()).append("\",");
        claimsJson.append("\"tenant_id\":\"").append(tenantId.toString()).append("\",");
        claimsJson.append("\"user_role\":\"").append(role).append("\"");
        if (collegeId != null) {
            claimsJson.append(",\"college_id\":\"").append(collegeId.toString()).append("\"");
        }
        if (departmentId != null) {
            claimsJson.append(",\"department_id\":\"").append(departmentId.toString()).append("\"");
        }
        claimsJson.append("}");

        try (PreparedStatement pstmt = connection
                .prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            pstmt.setString(1, claimsJson.toString());
            pstmt.execute();
        }
    }

    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException;
    }

    public <T> T executeInTransaction(DatabaseConnectionManager connectionManager, TransactionCallback<T> action) {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                applyPostgresRoleContext(conn);
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
                throw new RuntimeException("Database transaction failed: " + e.getMessage(), e);
            } finally {
                clearPostgresRoleContext(conn);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to acquire database connection: " + e.getMessage(), e);
        }
    }

    public static void clearPostgresRoleContext(Connection connection) {
        if (connection == null)
            return;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET ROLE");
            stmt.execute("SELECT set_config('request.jwt.claims', '', true)");
        } catch (SQLException e) {
            logger.warn("Failed to reset Postgres role/claims context cleanly: {}", e.getMessage());
        }
    }
}
