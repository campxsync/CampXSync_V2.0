package com.campx.academic.timetable.server;

import com.campx.academic.timetable.controller.TimetableController;
import com.campx.academic.timetable.service.TimetableDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-05 Timetable Management Service.
 * Serves REST endpoints under {@code /api/v1/academics/timetables/**}, {@code /api/v1/timetables/**}, and {@code /v1/timetables/**}.
 */
public class TimetableServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TimetableServer.class);

    private final int port;
    private final TimetableDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private TimetableController controller;

    public TimetableServer(int port, TimetableDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new TimetableDomainService();
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

        this.controller = new TimetableController(domainService);
        server.createContext("/api/v1/academics/timetables", controller);
        server.createContext("/api/v1/timetables", controller);
        server.createContext("/v1/timetables", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-05 Timetable Management Service started successfully on port {}", port);
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
        logger.info("CampXSync ACD-05 Timetable Management Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public TimetableDomainService getDomainService() {
        return domainService;
    }

    public TimetableController getController() {
        return controller;
    }
}
