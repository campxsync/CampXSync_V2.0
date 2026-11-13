package com.campx.lifecycle.security;

import com.campx.lifecycle.Actor;
import com.campx.lifecycle.config.DatabaseConnectionManager;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class LifecycleSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(LifecycleSecurityContext.class);

    private final UUID userId;
    private final UUID tenantId;
    private final String role;

    public LifecycleSecurityContext(UUID userId, UUID tenantId, String role) {
        if (userId == null || tenantId == null) {
            throw new IllegalArgumentException("userId and tenantId are required for lifecycle DB context");
        }
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role == null || role.trim().isEmpty() ? "authenticated" : role.trim();
    }

    public static LifecycleSecurityContext fromActor(Actor actor) {
        return new LifecycleSecurityContext(actor.getUserId(), actor.getTenantId(), actor.getRole());
    }

    public void applyPostgresRoleContext(Connection connection) throws SQLException {
        if (connection == null) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }
        String claimsJson = "{\"sub\":\"" + userId + "\",\"tenant_id\":\"" + tenantId
                + "\",\"user_role\":\"" + role + "\"}";
        try (PreparedStatement pstmt = connection.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            pstmt.setString(1, claimsJson);
            pstmt.execute();
        }
    }

    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException;
    }

    public <T> T executeInTransaction(DatabaseConnectionManager connectionManager, TransactionCallback<T> action) {
        try (Connection conn = connectionManager.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
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
                    logger.error("Lifecycle transaction rollback failed", rollbackEx);
                }
                if (e instanceof com.campx.lifecycle.LifecycleException) {
                    logger.warn("Lifecycle persistence rejected: {}", e.getMessage());
                    throw (RuntimeException) e;
                }
                logger.error("Lifecycle database transaction failed", e);
                e.printStackTrace();
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Lifecycle database transaction failed: " + e.getMessage(), e);
            } finally {
                clearPostgresRoleContext(conn);
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // best effort restore
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to acquire lifecycle database connection", e);
            throw new RuntimeException("Failed to acquire lifecycle database connection", e);
        }
    }

    public static void clearPostgresRoleContext(Connection connection) {
        if (connection == null) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET ROLE");
            stmt.execute("SELECT set_config('request.jwt.claims', '', true)");
        } catch (SQLException e) {
            logger.warn("Failed to reset lifecycle Postgres role/claims: {}", e.getMessage());
        }
    }
}
