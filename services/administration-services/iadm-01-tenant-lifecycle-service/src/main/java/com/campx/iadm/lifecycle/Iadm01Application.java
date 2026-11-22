package com.campx.iadm.lifecycle;

import com.campx.iadm.lifecycle.config.DatabaseConfig;
import com.campx.iadm.lifecycle.config.DatabaseConnectionManager;
import com.campx.iadm.lifecycle.controller.Iadm01Controller;
import com.campx.iadm.lifecycle.server.Iadm01Server;
import com.campx.iadm.lifecycle.service.TenantLifecycleService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm01Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm01Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm01Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-01");
            }
            DatabaseConnectionManager.getInstance().initialize();
            TenantLifecycleService service = new TenantLifecycleService();
            Iadm01Controller controller = new Iadm01Controller(service);
            Iadm01Server server = new Iadm01Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-01 Tenant Lifecycle Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-01: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
