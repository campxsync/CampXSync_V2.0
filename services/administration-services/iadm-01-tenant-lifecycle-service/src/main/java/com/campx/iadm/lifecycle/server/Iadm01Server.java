package com.campx.iadm.lifecycle.server;

import com.campx.iadm.lifecycle.controller.Iadm01Controller;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Iadm01Server {
    public static final int DEFAULT_PORT = 8099;
    private final HttpServer server;
    private final int port;

    public Iadm01Server(Iadm01Controller controller) throws IOException {
        this(controller, DEFAULT_PORT);
    }

    public Iadm01Server(Iadm01Controller controller, int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/v1/iadm/01", controller);
        server.createContext("/health", controller);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    public int getPort() { return port; }
}
