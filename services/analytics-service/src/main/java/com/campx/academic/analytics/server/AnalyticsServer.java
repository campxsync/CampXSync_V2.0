package com.campx.academic.analytics.server;

import com.campx.academic.analytics.controller.AnalyticsController;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting ACD-10: Reporting & Analytics Service.
 * Serves canonical and aliased REST endpoints on port 8092.
 */
public class AnalyticsServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AnalyticsServer.class);

    private final int port;
    private final AnalyticsDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private AnalyticsController controller;

    public AnalyticsServer(int port, AnalyticsDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new AnalyticsDomainService();
    }

    /**
     * Initializes and starts the embedded HTTP server.
     *
     * @throws IOException if network socket binding fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        this.controller = new AnalyticsController(domainService);

        // Canonical endpoints
        server.createContext("/api/v1/academics/analytics", controller);
        server.createContext("/api/v1/analytics", controller);
        server.createContext("/v1/analytics", controller);

        // Gateway aliases
        server.createContext("/api/v1/dashboards", controller);
        server.createContext("/api/v1/kpis", controller);
        server.createContext("/api/v1/reports", controller);

        // Operational endpoints
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-10 Reporting & Analytics Service started successfully on port {}", port);
    }

    /**
     * Gracefully stops the embedded HTTP server and background threads.
     */
    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        server.stop(0);
        domainService.getExportService().shutdown();
        running = false;
        logger.info("CampXSync ACD-10 Reporting & Analytics Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public AnalyticsDomainService getDomainService() {
        return domainService;
    }

    public AnalyticsController getController() {
        return controller;
    }
}
