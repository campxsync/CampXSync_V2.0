package com.campx.document.security;

import com.campx.document.Actor;
import com.campx.document.DocumentException;
import com.campx.document.config.DatabaseConnectionManager;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Binds tenant claim and authenticated role in PostgreSQL for STM-06 operations.
 */
public class DocumentSecurityContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DocumentSecurityContext.class);
    private final Actor actor;

    public DocumentSecurityContext(Actor actor) {
        this.actor = actor;
    }

    public static DocumentSecurityContext fromActor(Actor actor) {
        return new DocumentSecurityContext(actor);
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
                    logger.error("Document transaction rollback failed", rollbackEx);
                }
                if (isRowSecurityDenial(e)) {
                    logger.warn("Document operation denied by row-level security tenantId={} message={}", actor.getTenantId(), e.getMessage());
                    throw new DocumentException(403, "DOCUMENT_PERSISTENCE_DENIED",
                            "The database rejected this document operation for the signed-in role: " + e.getMessage());
                }
                if (e instanceof DocumentException) {
                    logger.warn("Document transaction rejected code={}", ((DocumentException) e).getCode());
                    throw (DocumentException) e;
                }
                logger.error("Document database transaction failed", e);
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Document database transaction failed", e);
            } finally {
                clear(conn);
                try {
                    conn.setAutoCommit(previous);
                } catch (SQLException ignored) {
                    logger.warn("Failed to restore document connection auto-commit");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to acquire document database connection", e);
            throw new RuntimeException("Failed to acquire document database connection", e);
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
            logger.warn("Failed to reset document Postgres role/claims: {}", e.getMessage());
        }
    }
}

