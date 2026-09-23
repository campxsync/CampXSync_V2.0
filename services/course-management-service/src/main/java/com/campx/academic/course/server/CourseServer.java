package com.campx.academic.course.server;

import com.campx.academic.course.controller.CourseController;
import com.campx.academic.course.service.CourseDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-01 Course Management Service.
 * Serves REST endpoints under {@code /api/v1/courses/**} and {@code /api/v1/academics/courses/**}.
 */
public class CourseServer {

    /**
     * Structured logger instance for HTTP server operations.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CourseServer.class);

    /**
     * TCP port configured for the HTTP server.
     */
    private final int port;

    /**
     * Backing course domain business logic service.
     */
    private final CourseDomainService domainService;

    /**
     * Underlying JDK HTTP server instance.
     */
    private HttpServer server;

    /**
     * Running state flag.
     */
    private boolean running = false;

    /**
     * Constructs a {@code CourseServer} with the specified port and domain service.
     *
     * @param port          the TCP port to bind
     * @param domainService the domain business logic service
     */
    public CourseServer(int port, CourseDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new CourseDomainService();
    }

    /**
     * Starts the embedded HTTP server and binds controller contexts.
     *
     * @throws IOException if socket creation or binding fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null); // default executor

        CourseController controller = new CourseController(domainService);
        server.createContext("/api/v1/courses", controller);
        server.createContext("/api/v1/academics/courses", controller);

        server.start();
        running = true;
        logger.info("CampXSync ACD-01 Course Management Service started on port {}", port);
    }

    /**
     * Gracefully stops the HTTP server.
     */
    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("CampXSync ACD-01 Course Management Service stopped");
        }
    }

    /**
     * Checks whether the server is currently running.
     *
     * @return {@code true} if running, {@code false} otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the configured port number.
     *
     * @return TCP port
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the underlying domain service instance.
     *
     * @return course domain service
     */
    public CourseDomainService getDomainService() {
        return domainService;
    }
}
