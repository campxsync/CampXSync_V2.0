package com.campx.admin.institute;

import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Entrypoint for launching ADM-01 Institute Admin Service standalone.
 * <p>
 * Binds to default port 8081 (or custom CLI argument port), configures the domain service layer,
 * starts the HTTP transport server, and installs a JVM graceful shutdown hook.
 *
 * @see InstituteAdminServer
 * @see com.campx.admin.institute.service.InstituteAdminDomainService
 */
public class InstituteAdminApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminApplication.class);

    /**
     * Bootstraps and executes the Institute Admin Service runtime.
     *
     * @param args optional command line arguments; args[0] specifies server TCP port (default: 8081)
     */
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
