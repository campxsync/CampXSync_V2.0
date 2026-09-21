package com.campx.admin.institute;

import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Entrypoint for launching ADM-01 Institute Admin Service standalone.
 */
public class InstituteAdminApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminApplication.class);

    public static void main(String[] args) {
        try {
            int port = 8081;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }

            InstituteAdminServer server = new InstituteAdminServer(port, new com.campx.admin.institute.service.InstituteAdminDomainService());
            server.start();

            logger.info("CampXSync Institute Admin Service (ADM-01) is active on port {}", port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Institute Admin Service...");
                server.stop();
                CampXLoggerFactory.shutdown();
            }));

        } catch (Exception e) {
            logger.error("Failed to start Institute Admin Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
