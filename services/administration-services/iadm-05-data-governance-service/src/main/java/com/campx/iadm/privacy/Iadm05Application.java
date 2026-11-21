package com.campx.iadm.privacy;

import com.campx.iadm.privacy.config.DatabaseConfig;
import com.campx.iadm.privacy.config.DatabaseConnectionManager;
import com.campx.iadm.privacy.controller.Iadm05Controller;
import com.campx.iadm.privacy.server.Iadm05Server;
import com.campx.iadm.privacy.service.Iadm05DomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm05Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm05Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm05Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-05");
            }
            DatabaseConnectionManager.getInstance().initialize();
            Iadm05DomainService service = new Iadm05DomainService();
            Iadm05Controller controller = new Iadm05Controller(service);
            Iadm05Server server = new Iadm05Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-05 IADM-05 Data Governance and Privacy Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-05: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
