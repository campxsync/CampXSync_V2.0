package com.campx.iadm.security;

import com.campx.iadm.security.config.DatabaseConfig;
import com.campx.iadm.security.config.DatabaseConnectionManager;
import com.campx.iadm.security.controller.Iadm07Controller;
import com.campx.iadm.security.server.Iadm07Server;
import com.campx.iadm.security.service.SecurityDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * IADM-07 Security & Compliance Governance entrypoint (port 8105).
 * Plain language: starts the security posture and registers service; never enforces policy.
 */
public class Iadm07Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm07Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm07Server.DEFAULT_PORT;
            if (args != null && args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-07");
            }
            DatabaseConnectionManager.getInstance().initialize();
            SecurityDomainService service = new SecurityDomainService();
            Iadm07Controller controller = new Iadm07Controller(service);
            final Iadm07Server server = new Iadm07Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    DatabaseConnectionManager.getInstance().close();
                }
            }));
            logger.info("IADM-07 Security & Compliance Service started on port {}",
                    Integer.valueOf(server.getPort()));
        } catch (Exception e) {
            logger.error("Failed to start IADM-07: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}