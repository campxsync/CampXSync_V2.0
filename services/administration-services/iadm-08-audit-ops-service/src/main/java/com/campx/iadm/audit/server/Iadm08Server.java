package com.campx.iadm.audit.server;

import com.campx.iadm.audit.controller.Iadm08Controller;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Iadm08Server {
    public static final int DEFAULT_PORT = 8106;
    private final HttpServer server;
    private final int port;

    public Iadm08Server(Iadm08Controller controller) throws IOException {
        this(controller, DEFAULT_PORT);
    }

    public Iadm08Server(Iadm08Controller controller, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/v1/iadm/08", controller);
        server.createContext("/health", controller);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int getPort() { return port; }
}
