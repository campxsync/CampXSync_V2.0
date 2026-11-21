package com.campx.iadm.securitygov;

import com.campx.iadm.securitygov.config.DatabaseConfig;
import com.campx.iadm.securitygov.config.DatabaseConnectionManager;
import com.campx.iadm.securitygov.controller.Iadm07Controller;
import com.campx.iadm.securitygov.server.Iadm07Server;
import com.campx.iadm.securitygov.service.Iadm07DomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm07Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm07Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm07Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-07");
            }
            DatabaseConnectionManager.getInstance().initialize();
            Iadm07DomainService service = new Iadm07DomainService();
            Iadm07Controller controller = new Iadm07Controller(service);
            Iadm07Server server = new Iadm07Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-07 IADM-07 Security and Compliance Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-07: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
