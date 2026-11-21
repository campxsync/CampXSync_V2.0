package com.campx.iadm.config;

import com.campx.iadm.config.config.DatabaseConfig;
import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.controller.Iadm02Controller;
import com.campx.iadm.config.server.Iadm02Server;
import com.campx.iadm.config.service.PlatformConfigService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm02Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm02Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm02Server.DEFAULT_PORT;
            if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-02");
            }
            DatabaseConnectionManager.getInstance().initialize();
            Iadm02Server server = new Iadm02Server(new Iadm02Controller(new PlatformConfigService()), port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                DatabaseConnectionManager.getInstance().close();
            }));
            logger.info("IADM-02 Platform Config Service started on port {}", server.getPort());
        } catch (Exception e) {
            logger.error("Failed to start IADM-02: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
