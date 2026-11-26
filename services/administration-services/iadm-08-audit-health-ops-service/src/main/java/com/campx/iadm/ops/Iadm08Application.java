package com.campx.iadm.ops;

import com.campx.iadm.ops.config.DatabaseConfig;
import com.campx.iadm.ops.config.DatabaseConnectionManager;
import com.campx.iadm.ops.controller.Iadm08Controller;
import com.campx.iadm.ops.server.Iadm08Server;
import com.campx.iadm.ops.service.OpsDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * IADM-08 Audit, Health & Operations Oversight entrypoint (port 8106).
 * Plain language: starts the read-only oversight service; creates no audit events store and no health history.
 */
public class Iadm08Application {
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm08Application.class);

    public static void main(String[] args) {
        try {
            int port = Iadm08Server.DEFAULT_PORT;
            if (args != null && args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            DatabaseConfig cfg = DatabaseConfig.load();
            if (cfg.getJdbcUrl() == null || cfg.getJdbcUrl().trim().isEmpty()) {
                throw new IllegalStateException("SUPABASE_JDBC_URL / CAMPX_DB_URL required for IADM-08");
            }
            DatabaseConnectionManager.getInstance().initialize();
            OpsDomainService service = new OpsDomainService();
            Iadm08Controller controller = new Iadm08Controller(service);
            final Iadm08Server server = new Iadm08Server(controller, port);
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                    DatabaseConnectionManager.getInstance().close();
                }
            }));
            logger.info("IADM-08 Audit Health & Operations Service started on port {}",
                    Integer.valueOf(server.getPort()));
        } catch (Exception e) {
            logger.error("Failed to start IADM-08: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
