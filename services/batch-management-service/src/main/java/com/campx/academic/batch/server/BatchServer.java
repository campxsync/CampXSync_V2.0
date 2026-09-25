package com.campx.academic.batch.server;

import com.campx.academic.batch.controller.BatchController;
import com.campx.academic.batch.service.BatchDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-04 Batch Management Service.
 * Serves REST endpoints under {@code /api/v1/academics/batches/**} and {@code /api/v1/batches/**}.
 */
public class BatchServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(BatchServer.class);

    private final int port;
    private final BatchDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private BatchController controller;

    public BatchServer(int port, BatchDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new BatchDomainService();
    }

    /**
     * Initializes and starts the embedded HTTP server.
     *
     * @throws IOException if network binding fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        this.controller = new BatchController(domainService);
        server.createContext("/api/v1/academics/batches", controller);
        server.createContext("/api/v1/batches", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-04 Batch Management Service started successfully on port {}", port);
    }

    /**
     * Gracefully stops the embedded HTTP server.
     */
    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        server.stop(1);
        running = false;
        logger.info("CampXSync ACD-04 Batch Management Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public BatchDomainService getDomainService() {
        return domainService;
    }

    public BatchController getController() {
        return controller;
    }
}
