package com.campx.academic.attendance.server;

import com.campx.academic.attendance.controller.AttendanceController;
import com.campx.academic.attendance.service.AttendanceDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-06 Attendance Management Service.
 * Serves REST endpoints under {@code /api/v1/academics/attendance/**}, {@code /api/v1/attendance/**}, and {@code /v1/attendance/**}.
 */
public class AttendanceServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AttendanceServer.class);

    private final int port;
    private final AttendanceDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private AttendanceController controller;

    public AttendanceServer(int port, AttendanceDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new AttendanceDomainService();
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

        this.controller = new AttendanceController(domainService);
        server.createContext("/api/v1/academics/attendance", controller);
        server.createContext("/api/v1/attendance", controller);
        server.createContext("/v1/attendance", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-06 Attendance Management Service started successfully on port {}", port);
    }

    /**
     * Gracefully stops the embedded HTTP server.
     */
    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        server.stop(0);
        running = false;
        logger.info("CampXSync ACD-06 Attendance Management Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public AttendanceDomainService getDomainService() {
        return domainService;
    }

    public AttendanceController getController() {
        return controller;
    }
}
