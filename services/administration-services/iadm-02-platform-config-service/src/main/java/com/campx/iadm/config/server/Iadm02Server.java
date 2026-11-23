package com.campx.iadm.config.server;

import com.campx.iadm.config.controller.Iadm02Controller;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Iadm02Server {
    public static final int DEFAULT_PORT = 8100;
    public static final String CONTEXT_PREFIX = "/api/v1/iadm/02";
    public static final String HEALTH_PATH = "/api/v1/iadm/02/health";
    public static final String ROOT_HEALTH_PATH = "/health";

    private final HttpServer server;
    private final int port;

    public Iadm02Server(Iadm02Controller controller) throws IOException {
        this(controller, DEFAULT_PORT);
    }

    public Iadm02Server(Iadm02Controller controller, int port) throws IOException {
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
