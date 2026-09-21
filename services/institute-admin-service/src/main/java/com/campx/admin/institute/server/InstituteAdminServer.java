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
 */
public class InstituteAdminServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminServer.class);

    private final int port;
    private final InstituteAdminDomainService domainService;
    private HttpServer server;
    private boolean running = false;

    public InstituteAdminServer() {
        this(8081, new InstituteAdminDomainService());
    }

    public InstituteAdminServer(int port, InstituteAdminDomainService domainService) {
        this.port = port;
        this.domainService = domainService;
    }

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

    public synchronized void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("ADM-01 Institute Admin Service stopped");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public InstituteAdminDomainService getDomainService() {
        return domainService;
    }
}
