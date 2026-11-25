package com.campx.iadm.billing;

import com.campx.iadm.billing.config.DatabaseConfig;
import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.controller.Iadm04Controller;
import com.campx.iadm.billing.server.Iadm04Server;
import com.campx.iadm.billing.service.BillingDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

public class Iadm04Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm04Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm04Server.DEFAULT_PORT;
            if (args != null && args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-04");
            }
            DatabaseConnectionManager.getInstance().initialize();
            BillingDomainService service = new BillingDomainService();
            Iadm04Controller controller = new Iadm04Controller(service);
            final Iadm04Server server = new Iadm04Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    DatabaseConnectionManager.getInstance().close();
                }
            }));
            logger.info("IADM-04 Subscription Billing and Entitlements Service started on port {}",
                    Integer.valueOf(server.getPort()));
        } catch (Exception e) {
            logger.error("Failed to start IADM-04: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
