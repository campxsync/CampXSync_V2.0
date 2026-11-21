package com.campx.iadm.analytics;

import com.campx.iadm.analytics.config.DatabaseConfig;
import com.campx.iadm.analytics.config.DatabaseConnectionManager;
import com.campx.iadm.analytics.controller.Iadm06Controller;
import com.campx.iadm.analytics.server.Iadm06Server;
import com.campx.iadm.analytics.service.Iadm06DomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm06Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm06Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm06Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-06");
            }
            DatabaseConnectionManager.getInstance().initialize();
            Iadm06DomainService service = new Iadm06DomainService();
            Iadm06Controller controller = new Iadm06Controller(service);
            Iadm06Server server = new Iadm06Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-06 IADM-06 Platform Analytics and Usage Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-06: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
