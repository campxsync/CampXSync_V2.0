package com.campx.academic.analytics;

import com.campx.academic.analytics.server.AnalyticsServer;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Bootstrap entry point for ACD-10: Reporting & Analytics Service.
 */
public class AnalyticsApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AnalyticsApplication.class);
    public static final int DEFAULT_PORT = 8092;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid CLI port argument '{}', using default port {}", args[0], DEFAULT_PORT);
            }
        } else {
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.trim().isEmpty()) {
                try {
                    port = Integer.parseInt(envPort.trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid PORT env variable '{}', using default port {}", envPort, DEFAULT_PORT);
                }
            }
        }

        try {
            String mode = System.getProperty("campx.persistence.mode");
            if (mode == null || mode.trim().isEmpty()) {
                mode = System.getenv("CAMPX_PERSISTENCE_MODE");
            }
            if (mode == null || mode.trim().isEmpty()) {
                logger.error("FATAL: Missing required persistence configuration. 'campx.persistence.mode' or CAMPX_PERSISTENCE_MODE must be set explicitly to 'postgres' (or 'in_memory' for local testing).");
                throw new IllegalStateException("Missing required persistence configuration: 'campx.persistence.mode' or CAMPX_PERSISTENCE_MODE must be explicitly configured.");
            }

            AnalyticsDomainService domainService = new AnalyticsDomainService();
            AnalyticsServer server = new AnalyticsServer(port, domainService);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping AnalyticsServer...");
                server.stop();
                CampXLoggerFactory.flush();
            }));
            logger.info("ACD-10 Reporting & Analytics Service successfully bootstrapped on port {} with persistence mode: {}", port, mode);
        } catch (Exception e) {
            logger.error("Failed to start ACD-10 Reporting & Analytics Service on port {}: {}", port, e.getMessage(), e);
            System.exit(1);
        }
    }
}
