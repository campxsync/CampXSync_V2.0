package com.campx.academic.curriculum;

import com.campx.academic.curriculum.server.CurriculumServer;
import com.campx.academic.curriculum.service.CurriculumDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Bootstrap entry point for standalone execution of ACD-02 Curriculum Management Service.
 */
public class CurriculumManagementApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CurriculumManagementApplication.class);
    private static final int DEFAULT_PORT = 8084;

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
            CurriculumDomainService domainService = new CurriculumDomainService();
            CurriculumServer server = new CurriculumServer(port, domainService);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered. Stopping CurriculumServer...");
                server.stop();
                CampXLoggerFactory.flush();
            }));

            logger.info("ACD-02 Curriculum Management Service successfully bootstrapped on port {}", port);
        } catch (Exception e) {
            logger.error("Failed to start ACD-02 Curriculum Management Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
