package com.campx.gateway.server;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.router.ReverseProxyHandler;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the API Gateway.
 * <p>
 * Manages the lifecycle of the {@link HttpServer} instance, binding to the
 * configured port and installing the root {@link ReverseProxyHandler} context.
 * Thread safety is guaranteed via synchronized state transitions on start/stop.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayServer {

    /**
     * Logger instance for the gateway server lifecycle.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayServer.class);

    /**
     * Gateway routing configuration containing port and route table.
     */
    private final GatewayConfig config;

    /**
     * Underlying JDK HTTP server instance.
     */
    private HttpServer server;

    /**
     * Flag indicating whether the HTTP server is currently active.
     */
    private boolean running = false;

    /**
     * Constructs a GatewayServer instance with the given configuration.
     *
     * @param config The gateway configuration, or {@code null} to use defaults.
     */
    public GatewayServer(GatewayConfig config) {
        this.config = config != null ? config : new GatewayConfig();
    }

    /**
     * Starts the embedded HTTP server and mounts the reverse proxy handler.
     *
     * @throws IOException If the server cannot bind to the specified port.
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
        server.setExecutor(null); // default executor

        ReverseProxyHandler proxyHandler = new ReverseProxyHandler(config);
        server.createContext("/", proxyHandler);

        server.start();
        running = true;
        logger.info("CampXSync API Gateway started on port {}", config.getPort());
    }

    /**
     * Stops the embedded HTTP server immediately.
     */
    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("CampXSync API Gateway stopped");
        }
    }

    /**
     * Checks if the gateway server is currently running.
     *
     * @return {@code true} if running, {@code false} otherwise.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the configured port number.
     *
     * @return The integer port number.
     */
    public int getPort() {
        return config.getPort();
    }
}
