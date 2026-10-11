package com.campx.academic.subject;

import com.campx.academic.subject.server.SubjectServer;
import com.campx.academic.subject.service.SubjectDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Bootstrap entry point for standalone execution of ACD-03 Subject Management Service.
 */
public class SubjectManagementApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SubjectManagementApplication.class);
    private static final int DEFAULT_PORT = 8085;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port argument '{}', defaulting to {}", args[0], DEFAULT_PORT);
            }
        }

        try {
            SubjectDomainService domainService = new SubjectDomainService();
            SubjectServer server = new SubjectServer(port, domainService);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping SubjectServer...");
                server.stop();
                CampXLoggerFactory.flush();
            }));

            logger.info("ACD-03 Subject Management Service successfully bootstrapped on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start ACD-03 Subject Management Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
