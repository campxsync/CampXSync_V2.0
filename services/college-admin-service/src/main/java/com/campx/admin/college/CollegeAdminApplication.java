package com.campx.admin.college;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Entrypoint for launching ADM-02 College Admin Service standalone.
 */
public class CollegeAdminApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminApplication.class);

    public static void main(String[] args) {
        try {
            int port = 8082;
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }

            CollegeAdminServer server = new CollegeAdminServer(port, new CollegeAdminDomainService());
            server.start();

            logger.info("CampXSync College Admin Service (ADM-02) is active on port {}", port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down College Admin Service...");
                server.stop();
                CampXLoggerFactory.shutdown();
            }));

        } catch (Exception e) {
            logger.error("Failed to start College Admin Service: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
