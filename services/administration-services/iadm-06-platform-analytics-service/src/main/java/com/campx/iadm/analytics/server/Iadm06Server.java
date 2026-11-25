package com.campx.iadm.analytics.server;

import com.campx.iadm.analytics.controller.Iadm06Controller;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * HTTP server for IADM-06 on port 8104 under /api/v1/iadm/06.
 * Plain language: Platform Analytics & Usage Insights ingress (IADM-06-US-017).
 */
public class Iadm06Server {
    public static final int DEFAULT_PORT = 8104;
    public static final String CONTEXT_PREFIX = "/api/v1/iadm/06";
    public static final String HEALTH_PATH = "/api/v1/iadm/06/health";
    public static final String ROOT_HEALTH_PATH = "/health";

    private final HttpServer server;
    private final int port;

    public Iadm06Server(Iadm06Controller controller) throws IOException {
        this(controller, DEFAULT_PORT);
    }

    public Iadm06Server(Iadm06Controller controller, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext(CONTEXT_PREFIX, controller);
        server.createContext(ROOT_HEALTH_PATH, controller);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return port;
    }
}
