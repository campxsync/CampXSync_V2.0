package com.campx.gateway;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Entrypoint for launching the standalone CampXSync API Gateway.
 */
public class ApiGatewayApplication {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ApiGatewayApplication.class);

    public static void main(String[] args) {
        try {
            GatewayConfig config = new GatewayConfig();
            if (args.length > 0) {
                config.setPort(Integer.parseInt(args[0]));
            }

            GatewayServer server = new GatewayServer(config);
            server.start();

            logger.info("CampXSync API Gateway is operational on port {}", server.getPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down CampXSync API Gateway...");
                server.stop();
                CampXLoggerFactory.shutdown();
            }));

        } catch (Exception e) {
            logger.error("Failed to start CampXSync API Gateway: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
