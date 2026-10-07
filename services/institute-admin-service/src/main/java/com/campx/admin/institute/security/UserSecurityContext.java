package com.campx.admin.institute.security;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.InstituteAdminException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Handles authenticated security context extraction, UUID validation,
 * PostgreSQL transaction lifecycle, and role switching to 'authenticated'
 * with bound 'request.jwt.claims'.
 */
public class UserSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(UserSecurityContext.class);

    private final UUID userId;
    private final UUID tenantId;

    public UserSecurityContext(UUID userId, UUID tenantId) {
        if (userId == null) {
            throw new SecurityViolationException("Missing or null authenticated user ID");
        }
        if (tenantId == null) {
            throw new SecurityViolationException("Missing or null authenticated tenant ID");
        }
        this.userId = userId;
        this.tenantId = tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * Extracts and validates the caller security context from HTTP request headers.
     * Rejects request if user ID or tenant ID is missing or not a valid UUID.
     *
     * @param userIdHeader   value of X-User-Id header
     * @param tenantIdHeader value of X-Tenant-Id header
     * @return validated {@link UserSecurityContext}
     */
    public static UserSecurityContext fromHeaders(String userIdHeader, String tenantIdHeader) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-User-Id");
        }
        if (tenantIdHeader == null || tenantIdHeader.trim().isEmpty()) {
            throw new UserProfileAccessDeniedException("Missing required security context: X-Tenant-Id");
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new SecurityViolationException("Invalid authenticated user ID format (must be UUID): " + userIdHeader);
        }

        UUID tenantUuid;
        try {
            tenantUuid = UUID.fromString(tenantIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new SecurityViolationException("Invalid authenticated tenant ID format (must be UUID): " + tenantIdHeader);
        }

        return new UserSecurityContext(userUuid, tenantUuid);
    }

    /**
     * Prepares the transaction on the JDBC connection by switching role to 'authenticated'
     * and setting the transactional request.jwt.claims configuration parameter.
     *
     * @param conn active transaction connection
     * @throws SQLException if role switching or parameter binding fails
     */
    public void applyClaimsAndRole(Connection conn) throws SQLException {
        // 1. SET LOCAL ROLE authenticated (drops rolbypassrls privilege)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }

        // 2. Bound request.jwt.claims parameter
        String claimsJson = String.format(
                "{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                userId, tenantId, tenantId
        );

        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            ps.setString(1, claimsJson);
            ps.execute();
        }
    }

    /**
     * Verifies that the authenticated caller has the specified permission code in iam.permissions.
     *
     * @param conn           active transaction connection
     * @param permissionCode permission code to evaluate (e.g. 'adm08.view.all', 'adm08.write')
     * @throws SQLException                  if query execution fails
     * @throws UserProfileAccessDeniedException if the caller lacks the permission
     */
    public void checkPermission(Connection conn, String permissionCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
            ps.setString(1, permissionCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new UserProfileAccessDeniedException(
                            String.format("Caller '%s' lacks required permission '%s' in tenant '%s'",
                                    userId, permissionCode, tenantId));
                }
            }
        }
    }

    /**
     * Functional callback interface for transactional SQL execution.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException, InstituteAdminException;
    }

    /**
     * Runs the provided action in an isolated transaction subjected to RLS under the authenticated role.
     * Ensures autoCommit is restored and connection is closed in finally.
     *
     * @param connectionManager connection manager
     * @param action            transactional action to execute
     * @param <T>               result type
     * @return outcome of the action
     */
    public <T> T executeInTransaction(DatabaseConnectionManager connectionManager, TransactionCallback<T> action) {
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
                if (e instanceof InstituteAdminException) {
                    throw (InstituteAdminException) e;
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Database operation failed: " + e.getMessage(), e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error: " + e.getMessage(), e);
        }
    }
}
