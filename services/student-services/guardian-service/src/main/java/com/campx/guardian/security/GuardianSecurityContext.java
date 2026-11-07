package com.campx.guardian.security;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianException;
import com.campx.guardian.config.DatabaseConnectionManager;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Binds the gateway tenant claim and {@code SET LOCAL ROLE authenticated} for STM-05.
 */
public class GuardianSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GuardianSecurityContext.class);
    private final Actor actor;

    public GuardianSecurityContext(Actor actor) {
        this.actor = actor;
    }

    public static GuardianSecurityContext fromActor(Actor actor) {
        return new GuardianSecurityContext(actor);
    }

    public interface TransactionCallback<T> {
        T execute(Connection conn) throws SQLException;
    }

    public <T> T executeInTransaction(DatabaseConnectionManager connectionManager, TransactionCallback<T> action) {
        try (Connection conn = connectionManager.getConnection()) {
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                apply(conn);
                T result = action.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.error("Guardian transaction rollback failed", rollbackEx);
                }
                if (isRowSecurityDenial(e)) {
                    logger.warn("Guardian write was denied by row-level security tenantId={}", actor.getTenantId());
                    throw new GuardianException(403, "GUARDIAN_PERSISTENCE_DENIED",
                            "The database rejected this guardian change for the signed-in role");
                }
                if (e instanceof GuardianException) {
                    logger.warn("Guardian transaction rejected code={}", ((GuardianException) e).getCode());
                    throw (GuardianException) e;
                }
                logger.error("Guardian database transaction failed", e);
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Guardian database transaction failed", e);
            } finally {
                clear(conn);
                try {
                    conn.setAutoCommit(previous);
                } catch (SQLException ignored) {
                    logger.warn("Failed to restore guardian connection auto-commit");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to acquire guardian database connection", e);
            throw new RuntimeException("Failed to acquire guardian database connection", e);
        }
    }

private static boolean isRowSecurityDenial(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException && "42501".equals(((SQLException) current).getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }
        String claims = "{\"sub\":\"" + actor.getUserId() + "\",\"tenant_id\":\"" + actor.getTenantId()
                + "\",\"app_metadata\":{\"tenant_id\":\"" + actor.getTenantId() + "\"},\"user_role\":\""
                + actor.getRole() + "\"}";
        try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            ps.setString(1, claims);
            ps.execute();
        }
    }

    private void clear(Connection connection) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("RESET ROLE");
            stmt.execute("SELECT set_config('request.jwt.claims', '', true)");
        } catch (SQLException e) {
            logger.warn("Failed to reset guardian Postgres role/claims: {}", e.getMessage());
        }
    }
}
