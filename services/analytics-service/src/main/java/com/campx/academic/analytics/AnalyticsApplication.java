package com.campx.academic.analytics;

import com.campx.academic.analytics.server.AnalyticsServer;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.IOException;

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

        AnalyticsDomainService domainService = new AnalyticsDomainService();
        AnalyticsServer server = new AnalyticsServer(port, domainService);

        try {
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        } catch (IOException e) {
            logger.error("Failed to start ACD-10 Reporting & Analytics Service on port {}: {}", port, e.getMessage(), e);
            System.exit(1);
        }
    }
}
