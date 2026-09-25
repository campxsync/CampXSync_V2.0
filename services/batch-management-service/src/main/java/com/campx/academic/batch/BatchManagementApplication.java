package com.campx.academic.batch;

import com.campx.academic.batch.server.BatchServer;
import com.campx.academic.batch.service.BatchDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Main application bootstrap for ACD-04 Batch Management Service.
 */
public class BatchManagementApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(BatchManagementApplication.class);
    private static final int DEFAULT_PORT = 8086;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        }

        try {
            BatchDomainService domainService = new BatchDomainService();
            BatchServer server = new BatchServer(port, domainService);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, terminating ACD-04 Batch Management Service...");
                server.stop();
                CampXLoggerFactory.flush();
            }));

            logger.info("ACD-04 Batch Management Service is running on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start ACD-04 Batch Management Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
