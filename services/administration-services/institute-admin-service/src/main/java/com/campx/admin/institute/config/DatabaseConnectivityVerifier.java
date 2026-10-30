package com.campx.admin.institute.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Standalone database connectivity verification utility for ADM-01: Institute Admin Service.
 * <p>
 * Performs a harmless read-only connectivity check against Supabase PostgreSQL:
 * <ol>
 *   <li>Initializes the HikariCP datasource</li>
 *   <li>Borrows one JDBC {@link Connection}</li>
 *   <li>Executes {@code SELECT 1} and extracts database metadata</li>
 *   <li>Closes the JDBC connection</li>
 *   <li>Cleanly closes the {@link DatabaseConnectionManager} and connection pool</li>
 * </ol>
 * This utility is decoupled from normal domain business logic and transactional flows.
 *
 * @see DatabaseConfig
 * @see DatabaseConnectionManager
 */
public class DatabaseConnectivityVerifier {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DatabaseConnectivityVerifier.class);

    /**
     * Immutable outcome of the database connectivity verification check.
     */
    public static class VerificationResult {
        private final boolean success;
        private final String sanitizedJdbcUrl;
        private final String databaseProductName;
        private final String databaseProductVersion;
        private final Integer selectOneResult;
        private final boolean connectionClosed;
        private final boolean datasourceClosed;
        private final String message;
        private final Throwable error;

        public VerificationResult(boolean success,
                                  String sanitizedJdbcUrl,
                                  String databaseProductName,
                                  String databaseProductVersion,
                                  Integer selectOneResult,
                                  boolean connectionClosed,
                                  boolean datasourceClosed,
                                  String message,
                                  Throwable error) {
            this.success = success;
            this.sanitizedJdbcUrl = sanitizedJdbcUrl;
            this.databaseProductName = databaseProductName;
            this.databaseProductVersion = databaseProductVersion;
            this.selectOneResult = selectOneResult;
            this.connectionClosed = connectionClosed;
            this.datasourceClosed = datasourceClosed;
            this.message = message;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getSanitizedJdbcUrl() { return sanitizedJdbcUrl; }
        public String getDatabaseProductName() { return databaseProductName; }
        public String getDatabaseProductVersion() { return databaseProductVersion; }
        public Integer getSelectOneResult() { return selectOneResult; }
        public boolean isConnectionClosed() { return connectionClosed; }
        public boolean isDatasourceClosed() { return datasourceClosed; }
        public String getMessage() { return message; }
        public Throwable getError() { return error; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=================================================================\n");
            sb.append("      ADM-01 Database Connectivity Verification Report          \n");
            sb.append("=================================================================\n");
            sb.append(" Status:                 ").append(success ? "SUCCESS" : "FAILED").append("\n");
            sb.append(" Sanitized JDBC URL:     ").append(sanitizedJdbcUrl).append("\n");
            if (databaseProductName != null) {
                sb.append(" Database Product:       ").append(databaseProductName).append("\n");
            }
            if (databaseProductVersion != null) {
                sb.append(" Database Version:       ").append(databaseProductVersion).append("\n");
            }
            if (selectOneResult != null) {
                sb.append(" 'SELECT 1' Output:      ").append(selectOneResult).append("\n");
            }
            sb.append(" Connection Closed:      ").append(connectionClosed).append("\n");
            sb.append(" Datasource Closed:      ").append(datasourceClosed).append("\n");
            sb.append(" Details:                ").append(message).append("\n");
            if (error != null) {
                sb.append(" Error Class:            ").append(error.getClass().getName()).append("\n");
                sb.append(" Error Message:          ").append(error.getMessage()).append("\n");
            }
            sb.append("=================================================================");
            return sb.toString();
        }
    }

    /**
     * Executes the connectivity verification using default environment-resolved configuration.
     *
     * @return {@link VerificationResult} describing outcome
     */
    public static VerificationResult verify() {
        return verify(DatabaseConfig.load());
    }

    /**
     * Executes the connectivity verification using the supplied configuration.
     *
     * @param config database configuration
     * @return {@link VerificationResult} describing outcome
     */
    public static VerificationResult verify(DatabaseConfig config) {
        if (config == null) {
            return new VerificationResult(false, "null", null, null, null, false, false,
                    "DatabaseConfig cannot be null", new IllegalArgumentException("DatabaseConfig is null"));
        }

        String sanitizedUrl = config.getSanitizedJdbcUrl();
        logger.info("Initiating ADM-01 database connectivity check against [{}]", sanitizedUrl);

        DatabaseConnectionManager connectionManager = null;
        boolean connClosed = false;
        boolean dsClosed = false;

        try {
            connectionManager = new DatabaseConnectionManager(config);
            connectionManager.initialize();

            String productName = null;
            String productVersion = null;
            int selectResult;

            try (Connection conn = connectionManager.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                productName = metaData.getDatabaseProductName();
                productVersion = metaData.getDatabaseProductVersion();

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    if (rs.next()) {
                        selectResult = rs.getInt(1);
                    } else {
                        throw new SQLException("SELECT 1 returned no rows");
                    }
                }
                connClosed = conn.isClosed();
            }

            // Cleanly close the datasource
            connectionManager.close();
            dsClosed = connectionManager.isClosed();

            logger.info("ADM-01 database connectivity check succeeded: {} {}, SELECT 1={}",
                    productName, productVersion, selectResult);

            return new VerificationResult(true, sanitizedUrl, productName, productVersion, selectResult,
                    true, dsClosed, "Connectivity check succeeded and resources closed cleanly", null);

        } catch (Throwable t) {
            logger.error("ADM-01 database connectivity check failed against [{}]: {}", sanitizedUrl, t.getMessage(), t);

            if (connectionManager != null) {
                try {
                    connectionManager.close();
                    dsClosed = connectionManager.isClosed();
                } catch (Exception closeEx) {
                    logger.warn("Exception closing connection manager after failure: {}", closeEx.getMessage());
                }
            }

            return new VerificationResult(false, sanitizedUrl, null, null, null,
                    connClosed, dsClosed, "Connectivity check failed: " + t.getMessage(), t);
        }
    }

    /**
     * Standalone CLI runner for verifying ADM-01 database connectivity.
     *
     * @param args optional arguments
     */
    public static void main(String[] args) {
        VerificationResult result = verify();
        System.out.println(result);
        if (!result.isSuccess()) {
            System.exit(1);
        }
    }
}
