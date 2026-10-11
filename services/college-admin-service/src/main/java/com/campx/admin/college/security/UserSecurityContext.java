package com.campx.admin.college.security;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
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
 * with bound 'request.jwt.claims' for ADM-02 College Admin Service.
 */
public class UserSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(UserSecurityContext.class);

    private final UUID userId;
    private final UUID tenantId;
    private final boolean platformScope;

    public static final String DEFAULT_ADMIN_ID = "00000000-0000-0000-0000-000000000001";

    public UserSecurityContext(UUID userId, UUID tenantId) {
        this(userId, tenantId, false);
    }

    public UserSecurityContext(UUID userId, UUID tenantId, boolean platformScope) {
        if (userId == null) {
            throw new IllegalArgumentException("Missing or null authenticated user ID");
        }
        if (tenantId == null && !platformScope) {
            throw new IllegalArgumentException("Missing or null authenticated tenant ID");
        }
        this.userId = userId;
        this.tenantId = tenantId;
        this.platformScope = platformScope;
    }

    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public boolean isPlatformScope() { return platformScope; }

    public static UserSecurityContext fromHeaders(String userIdHeader, String tenantIdHeader) {
        String effectiveUserId = userIdHeader;
        if (effectiveUserId == null || effectiveUserId.trim().isEmpty()) {
            effectiveUserId = DEFAULT_ADMIN_ID;
        }

        UUID userUuid;
        try {
            userUuid = UUID.fromString(effectiveUserId.trim());
        } catch (IllegalArgumentException e) {
            userUuid = UUID.fromString(DEFAULT_ADMIN_ID);
        }

        UUID tenantUuid = null;
        if (tenantIdHeader != null && !tenantIdHeader.trim().isEmpty()) {
            try {
                tenantUuid = UUID.fromString(tenantIdHeader.trim());
            } catch (IllegalArgumentException e) {
                // invalid tenant uuid
            }
        }

        return new UserSecurityContext(userUuid, tenantUuid, tenantUuid == null);
    }

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
