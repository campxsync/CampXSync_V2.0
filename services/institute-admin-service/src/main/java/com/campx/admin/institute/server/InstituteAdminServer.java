package com.campx.admin.institute.server;

import com.campx.admin.institute.controller.InstituteAdminController;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ADM-01 Institute Admin Service on port 8081.
 * <p>
 * Mounts {@link InstituteAdminController} at {@code /api/v1/admin} to service platform-level
 * tenant, RBAC, reliability, and governance requests.
 *
 * @see InstituteAdminController
 * @see InstituteAdminDomainService
 */
public class InstituteAdminServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminServer.class);

    private final int port;
    private final InstituteAdminDomainService domainService;
    private HttpServer server;
    private boolean running = false;

    /**
     * Initializes the server with default port 8081 and a new domain service instance.
     */
    public InstituteAdminServer() {
        this(8081, new InstituteAdminDomainService());
    }

    /**
     * Initializes the server with custom port and domain service.
     *
     * @param port          TCP port to listen on
     * @param domainService business domain service instance
     */
    public InstituteAdminServer(int port, InstituteAdminDomainService domainService) {
        this.port = port;
        this.domainService = domainService;
    }

    /**
     * Binds the server socket, registers request contexts, and begins accepting HTTP requests.
     *
     * @throws IOException if network socket creation or binding fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null);

        InstituteAdminController controller = new InstituteAdminController(domainService);
        server.createContext("/api/v1/admin", controller);

        server.start();
        running = true;
        logger.info("ADM-01 Institute Admin Service started on port {}", port);
    }

    /**
     * Stops the HTTP server and terminates socket listeners.
     */
    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("ADM-01 Institute Admin Service stopped");
        }
    }

    /**
     * Checks if the HTTP server is currently active and processing requests.
     *
     * @return {@code true} if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the TCP port bound to this server.
     *
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the underlying domain service instance managing platform state.
     *
     * @return {@link InstituteAdminDomainService}
     */
    public InstituteAdminDomainService getDomainService() {
        return domainService;
    }
}
