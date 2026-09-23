package com.campx.gateway;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

/**
 * Entrypoint for launching the standalone CampXSync API Gateway.
 * <p>
 * The API Gateway acts as the unified reverse proxy and routing edge for all
 * microservices in the CampXSync platform (Institute Admin Service, College
 * Admin Service, Course Management Service, etc.). It initializes the gateway
 * configuration, binds the embedded HTTP server to the designated port, and
 * registers a graceful JVM shutdown hook.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class ApiGatewayApplication {

    /**
     * Logger instance for the API Gateway entrypoint lifecycle.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ApiGatewayApplication.class);

    /**
     * Bootstraps and starts the CampXSync API Gateway application.
     *
     * @param args Optional command line arguments. If provided, {@code args[0]}
     *             specifies the HTTP listening port override (e.g., "8080").
     */
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
