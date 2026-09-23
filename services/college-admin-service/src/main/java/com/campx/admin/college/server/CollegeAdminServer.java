package com.campx.admin.college.server;

import com.campx.admin.college.controller.CollegeAdminController;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ADM-02 College Admin Service on port 8082.
 * Mounts the REST controller under {@code /api/v1/college-admin/**}.
 */
public class CollegeAdminServer {

    /**
     * Structured logger instance for HTTP server operations.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminServer.class);

    /**
     * Port on which the HTTP server listens.
     */
    private final int port;

    /**
     * Backing college admin domain service business logic instance.
     */
    private final CollegeAdminDomainService domainService;

    /**
     * Underlying JDK HTTP server instance.
     */
    private HttpServer server;

    /**
     * Server execution status flag.
     */
    private boolean running = false;

    /**
     * Constructs a {@code CollegeAdminServer} using the default port (8082) and a newly instantiated domain service.
     */
    public CollegeAdminServer() {
        this(8082, new CollegeAdminDomainService());
    }

    /**
     * Constructs a {@code CollegeAdminServer} with a specific port and domain service.
     *
     * @param port          the TCP port to bind
     * @param domainService the domain service handling business logic
     */
    public CollegeAdminServer(int port, CollegeAdminDomainService domainService) {
        this.port = port;
        this.domainService = domainService;
    }

    /**
     * Binds and starts the HTTP server on the configured port, registering the controller context.
     *
     * @throws IOException if the server fails to bind to the socket address
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        CollegeAdminController controller = new CollegeAdminController(domainService);
        server.createContext("/api/v1/college-admin", controller);

        server.start();
        running = true;
        logger.info("ADM-02 College Admin Service started on port {}", port);
    }

    /**
     * Gracefully stops the HTTP server and releases bound socket resources.
     */
    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("ADM-02 College Admin Service stopped");
        }
    }

    /**
     * Checks whether the HTTP server is currently running.
     *
     * @return {@code true} if running, {@code false} otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the TCP port this server is configured to listen on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the underlying domain service instance.
     *
     * @return the domain service
     */
    public CollegeAdminDomainService getDomainService() {
        return domainService;
    }
}
