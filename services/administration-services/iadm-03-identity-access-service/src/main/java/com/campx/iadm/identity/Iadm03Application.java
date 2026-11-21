package com.campx.iadm.identity;

import com.campx.iadm.identity.config.DatabaseConfig;
import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.controller.Iadm03Controller;
import com.campx.iadm.identity.server.Iadm03Server;
import com.campx.iadm.identity.service.Iadm03DomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm03Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm03Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm03Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-03");
            }
            DatabaseConnectionManager.getInstance().initialize();
            Iadm03DomainService service = new Iadm03DomainService();
            Iadm03Controller controller = new Iadm03Controller(service);
            Iadm03Server server = new Iadm03Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-03 IADM-03 Platform Identity and Access Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-03: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
