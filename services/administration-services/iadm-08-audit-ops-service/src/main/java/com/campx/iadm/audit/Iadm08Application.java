package com.campx.iadm.audit;

import com.campx.iadm.audit.config.DatabaseConfig;
import com.campx.iadm.audit.config.DatabaseConnectionManager;
import com.campx.iadm.audit.controller.Iadm08Controller;
import com.campx.iadm.audit.server.Iadm08Server;
import com.campx.iadm.audit.service.Iadm08DomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm08Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm08Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm08Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-08");
            }
            DatabaseConnectionManager.getInstance().initialize();
            Iadm08DomainService service = new Iadm08DomainService();
            Iadm08Controller controller = new Iadm08Controller(service);
            Iadm08Server server = new Iadm08Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-08 IADM-08 Audit Health and Operations Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-08: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
