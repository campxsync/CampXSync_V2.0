package com.campx.iadm.governance;

import com.campx.iadm.governance.config.DatabaseConfig;
import com.campx.iadm.governance.config.DatabaseConnectionManager;
import com.campx.iadm.governance.controller.Iadm05Controller;
import com.campx.iadm.governance.server.Iadm05Server;
import com.campx.iadm.governance.service.GovernanceDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * IADM-05 Data Governance & Privacy service entrypoint (port 8103).
 * Plain language: starts the governance policy service; does not own domain data.
 */
public class Iadm05Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm05Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm05Server.DEFAULT_PORT;
            if (args != null && args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-05");
            }
            DatabaseConnectionManager.getInstance().initialize();
            GovernanceDomainService service = new GovernanceDomainService();
            Iadm05Controller controller = new Iadm05Controller(service);
            final Iadm05Server server = new Iadm05Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    DatabaseConnectionManager.getInstance().close();
                }
            }));
            logger.info("IADM-05 Data Governance and Privacy Service started on port {}",
                    Integer.valueOf(server.getPort()));
        } catch (Exception e) {
            logger.error("Failed to start IADM-05: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
