package com.campx.iadm.access;

import com.campx.iadm.access.config.DatabaseConfig;
import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.controller.Iadm03Controller;
import com.campx.iadm.access.server.Iadm03Server;
import com.campx.iadm.access.service.AccessGovernanceService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm03Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm03Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm03Server.DEFAULT_PORT;
            if (args != null && args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-03");
            }
            DatabaseConnectionManager.getInstance().initialize();
            AccessGovernanceService service = new AccessGovernanceService();
            Iadm03Controller controller = new Iadm03Controller(service);
            Iadm03Server server = new Iadm03Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    DatabaseConnectionManager.getInstance().close();
                }
            }));
            logger.info("IADM-03 Identity and Access Governance Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-03: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
