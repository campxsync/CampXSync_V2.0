package com.campx.iadm.identity.server;

import com.campx.iadm.identity.controller.Iadm03Controller;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Iadm03Server {
    public static final int DEFAULT_PORT = 8101;
    private final HttpServer server;
    private final int port;

    public Iadm03Server(Iadm03Controller controller) throws IOException {
        this(controller, DEFAULT_PORT);
    }

    public Iadm03Server(Iadm03Controller controller, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/v1/iadm/03", controller);
        server.createContext("/health", controller);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int getPort() { return port; }
}
