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
 */
public class CollegeAdminServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminServer.class);

    private final int port;
    private final CollegeAdminDomainService domainService;
    private HttpServer server;
    private boolean running = false;

    public CollegeAdminServer() {
        this(8082, new CollegeAdminDomainService());
    }

    public CollegeAdminServer(int port, CollegeAdminDomainService domainService) {
        this.port = port;
        this.domainService = domainService;
    }

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

    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("ADM-02 College Admin Service stopped");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public CollegeAdminDomainService getDomainService() {
        return domainService;
    }
}
