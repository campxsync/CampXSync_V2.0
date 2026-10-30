package com.campx.student;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.server.StudentServer;
import com.campx.student.service.StudentDomainService;

/**
 * Main application bootstrap for STM-01 Student Identity & Profile Domain Service.
 */
public class StudentApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentApplication.class);
    private static final int DEFAULT_PORT = 8093;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        try {
            String mode = System.getProperty("campx.persistence.mode");
            if (mode == null || mode.trim().isEmpty()) {
                mode = System.getenv("CAMPX_PERSISTENCE_MODE");
            }
            if (StudentDomainService.isDeployedEnvironment()) {
                if (mode == null || mode.trim().isEmpty()) {
                    logger.error("FATAL: Missing required persistence configuration. 'campx.persistence.mode' or CAMPX_PERSISTENCE_MODE must be set explicitly to 'postgres'.");
                    throw new IllegalStateException("Missing required persistence configuration: 'campx.persistence.mode' or CAMPX_PERSISTENCE_MODE must be explicitly configured in deployed environments.");
                }
                String clean = mode.trim();
                if ("in_memory".equalsIgnoreCase(clean) || "inmemory".equalsIgnoreCase(clean) || "in-memory".equalsIgnoreCase(clean) || "mock".equalsIgnoreCase(clean)) {
                    throw new IllegalStateException("Ephemeral in-memory persistence is strictly prohibited in deployed environment.");
                }
                // Eagerly verify PostgreSQL connection pool to fail fast on invalid credentials or unreachable database
                com.campx.student.config.DatabaseConnectionManager.getInstance().initialize();
            }

            StudentDomainService domainService = new StudentDomainService();
            StudentServer server = new StudentServer(port, domainService);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, terminating STM-01 Student Domain Service...");
                server.stop();
                CampXLoggerFactory.flush();
            }));

            logger.info("STM-01 Student Domain Service is running on port {} with persistence mode: {}", port, mode != null ? mode : "default");
        } catch (Exception e) {
            logger.error("Failed to start STM-01 Student Domain Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
