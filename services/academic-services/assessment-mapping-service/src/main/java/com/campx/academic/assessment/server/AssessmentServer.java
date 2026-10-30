package com.campx.academic.assessment.server;

import com.campx.academic.assessment.controller.AssessmentController;
import com.campx.academic.assessment.service.AssessmentDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-09 Assessment Mapping Service.
 * Serves REST endpoints under /api/v1/academics/assessments, /v1/assessments, and related paths.
 */
public class AssessmentServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AssessmentServer.class);

    private final int port;
    private final AssessmentDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private AssessmentController controller;

    public AssessmentServer(int port, AssessmentDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new AssessmentDomainService();
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        this.controller = new AssessmentController(domainService);
        server.createContext("/api/v1/academics/assessments", controller);
        server.createContext("/api/v1/assessments", controller);
        server.createContext("/v1/assessments", controller);
        server.createContext("/v1/assessment-catalog", controller);
        server.createContext("/v1/assessment-mappings", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-09 Assessment Mapping Service started successfully on port {}", port);
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        server.stop(0);
        running = false;
        logger.info("CampXSync ACD-09 Assessment Mapping Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public AssessmentDomainService getDomainService() {
        return domainService;
    }

    public AssessmentController getController() {
        return controller;
    }
}
