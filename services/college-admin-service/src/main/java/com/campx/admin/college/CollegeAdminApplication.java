package com.campx.admin.college;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Entrypoint for launching ADM-02 College Admin Service standalone.
 * Configures the HTTP server port, initializes the domain service, and manages graceful shutdown.
 */
public class CollegeAdminApplication {

    /**
     * Shared structured logger instance for service lifecycle events.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminApplication.class);

    /**
     * Application entry point for College Admin Service.
     *
     * @param args optional command-line arguments; args[0] may specify the HTTP server port (defaults to 8082)
     */
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
