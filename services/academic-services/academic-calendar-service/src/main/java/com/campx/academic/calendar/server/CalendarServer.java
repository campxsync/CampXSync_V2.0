package com.campx.academic.calendar.server;

import com.campx.academic.calendar.controller.CalendarController;
import com.campx.academic.calendar.service.CalendarDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-07 Academic Calendar Service.
 * Serves REST endpoints under {@code /api/v1/academics/calendars/**}, {@code /v1/calendars/**}, and related paths.
 */
public class CalendarServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CalendarServer.class);

    private final int port;
    private final CalendarDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private CalendarController controller;

    public CalendarServer(int port, CalendarDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new CalendarDomainService();
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        this.controller = new CalendarController(domainService);
        server.createContext("/api/v1/academics/calendars", controller);
        server.createContext("/api/v1/calendars", controller);
        server.createContext("/v1/calendars", controller);
        server.createContext("/api/v1/academics/terms", controller);
        server.createContext("/v1/calendar-terms", controller);
        server.createContext("/api/v1/academics/events", controller);
        server.createContext("/v1/calendar-events", controller);
        server.createContext("/events", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-07 Academic Calendar Service started successfully on port {}", port);
    }

    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        server.stop(0);
        running = false;
        logger.info("CampXSync ACD-07 Academic Calendar Service stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public CalendarDomainService getDomainService() {
        return domainService;
    }
}
