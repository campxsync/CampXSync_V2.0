package com.campx.academic.resource;

import com.campx.academic.resource.server.ResourceServer;
import com.campx.academic.resource.service.ResourceDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.IOException;

/**
 * Entry point for the ACD-08 Learning Resource Service.
 * Bootstraps the embedded HTTP server on default port 8090.
 */
public class LearningResourceApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(LearningResourceApplication.class);
    public static final int DEFAULT_PORT = 8090;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port argument '{}', defaulting to {}", args[0], DEFAULT_PORT);
            }
        }

        ResourceDomainService domainService = new ResourceDomainService();
        ResourceServer server = new ResourceServer(port, domainService);

        try {
            server.start();
            logger.info("================================================================================");
            logger.info(" CampXSync ACD-08 Learning Resource Service running on http://localhost:{}", port);
            logger.info(" Authoritative Learning Resource Metadata, Storage Integration & Access Engine");
            logger.info("================================================================================");

            final ResourceServer finalServer = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, terminating ACD-08 server...");
                finalServer.stop();
                CampXLoggerFactory.flush();
            }));

        } catch (IOException e) {
            logger.error("Failed to start ACD-08 Learning Resource Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
