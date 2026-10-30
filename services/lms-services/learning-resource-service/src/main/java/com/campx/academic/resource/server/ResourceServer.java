package com.campx.academic.resource.server;

import com.campx.academic.resource.controller.ResourceController;
import com.campx.academic.resource.service.ResourceDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-08 Learning Resource Service.
 * Serves REST endpoints under {@code /api/v1/academics/resources/**}, {@code /v1/resources/**}, and related paths.
 */
public class ResourceServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ResourceServer.class);

    private final int port;
    private final ResourceDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private ResourceController controller;

    public ResourceServer(int port, ResourceDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new ResourceDomainService();
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        this.controller = new ResourceController(domainService);
        server.createContext("/api/v1/academics/resources", controller);
        server.createContext("/api/v1/resources", controller);
        server.createContext("/v1/resources", controller);
        server.createContext("/api/v1/academics/versions", controller);
        server.createContext("/api/v1/versions", controller);
        server.createContext("/v1/resource-versions", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-08 Learning Resource Service started successfully on port {}", port);
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        server.stop(0);
        running = false;
        logger.info("CampXSync ACD-08 Learning Resource Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public ResourceDomainService getDomainService() {
        return domainService;
    }
}
