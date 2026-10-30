package com.campx.student.server;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.student.controller.StudentController;
import com.campx.student.service.StudentDomainService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the STM-01 Student Domain Service.
 * Serves REST endpoints under {@code /api/v1/students/**} and {@code /v1/students/**}.
 */
public class StudentServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentServer.class);

    private final int port;
    private final StudentDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private StudentController controller;

    public StudentServer(int port, StudentDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new StudentDomainService();
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

        String bindHost = com.campx.logger.security.GatewayHmacSecurityConfig.getBindHost();
        server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        server.setExecutor(null);

        this.controller = new StudentController(domainService);
        server.createContext("/api/v1/students", controller);
        server.createContext("/v1/students", controller);
        server.createContext("/v1/student-roster", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync STM-01 Student Domain Service started successfully on port {}", port);
    }

    /**
     * Gracefully stops the embedded HTTP server.
     */
    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        logger.info("Stopping STM-01 Student Domain Service on port {}...", port);
        server.stop(1);
        running = false;
        logger.info("STM-01 Student Domain Service stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
