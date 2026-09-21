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
 */
public class GatewayServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayServer.class);

    private final GatewayConfig config;
    private HttpServer server;
    private boolean running = false;

    public GatewayServer(GatewayConfig config) {
        this.config = config != null ? config : new GatewayConfig();
    }

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

    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("CampXSync API Gateway stopped");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return config.getPort();
    }
}
