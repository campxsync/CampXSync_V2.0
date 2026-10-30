package com.campx.academic.assessment;

import com.campx.academic.assessment.server.AssessmentServer;
import com.campx.academic.assessment.service.AssessmentDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.IOException;

/**
 * Entry point for the ACD-09 Assessment Mapping Service.
 * Bootstraps the embedded HTTP server on default port 8091.
 */
public class AssessmentMappingApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AssessmentMappingApplication.class);
    public static final int DEFAULT_PORT = 8091;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port argument '{}', defaulting to {}", args[0], DEFAULT_PORT);
            }
        }

        AssessmentDomainService domainService = new AssessmentDomainService();
        AssessmentServer server = new AssessmentServer(port, domainService);

        try {
            server.start();
            logger.info("================================================================================");
            logger.info(" CampXSync ACD-09 Assessment Mapping Service running on http://localhost:{}", port);
            logger.info(" Authoritative Assessment Structure, Weightage Reconciler & Outcome Mapping Engine");
            logger.info("================================================================================");

            final AssessmentServer finalServer = server;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, terminating ACD-09 server...");
                finalServer.stop();
                CampXLoggerFactory.flush();
            }));

        } catch (IOException e) {
            logger.error("Failed to start ACD-09 Assessment Mapping Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
