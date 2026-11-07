package com.campx.enrollment.security;

import com.campx.enrollment.Actor;
import com.campx.enrollment.config.DatabaseConnectionManager;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Binds tenant JWT claims and SET LOCAL ROLE authenticated for STM-03 RLS.
 */
public class EnrollmentSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(EnrollmentSecurityContext.class);

    private final UUID userId;
    private final UUID tenantId;
    private final String role;

    public EnrollmentSecurityContext(UUID userId, UUID tenantId, String role) {
        if (userId == null || tenantId == null) {
            throw new IllegalArgumentException("userId and tenantId are required for enrolment DB context");
        }
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role == null || role.trim().isEmpty() ? "authenticated" : role.trim();
    }

    public static EnrollmentSecurityContext fromActor(Actor actor) {
        return new EnrollmentSecurityContext(actor.getUserId(), actor.getTenantId(), actor.getRole());
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
                    logger.error("Enrolment transaction rollback failed", rollbackEx);
                }
                if (e instanceof com.campx.enrollment.EnrollmentException) {
                    logger.warn("Enrolment persistence rejected: {}", e.getMessage());
                    throw (RuntimeException) e;
                }
                logger.error("Enrolment database transaction failed", e);
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Enrolment database transaction failed", e);
            } finally {
                clearPostgresRoleContext(conn);
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // best effort restore
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to acquire enrolment database connection", e);
            throw new RuntimeException("Failed to acquire enrolment database connection", e);
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
            logger.warn("Failed to reset enrolment Postgres role/claims: {}", e.getMessage());
        }
    }
}
