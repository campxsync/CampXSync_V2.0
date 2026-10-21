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

    private final String host;
    private final int port;
    private final InstituteAdminDomainService domainService;
    private final com.campx.admin.institute.repository.UserPreferenceRepository userPreferenceRepository;
    private final com.campx.admin.institute.controller.UserPreferenceController userPreferenceController;
    private HttpServer server;
    private boolean running = false;

    /**
     * Initializes the server with resolved bind host, default port 8081, and a new domain service instance.
     */
    public InstituteAdminServer() {
        this(resolveBindHost(), 8081, new InstituteAdminDomainService(), new com.campx.admin.institute.repository.UserPreferenceRepository());
    }

    /**
     * Initializes the server with resolved bind host, custom port, and domain service.
     *
     * @param port          TCP port to listen on
     * @param domainService business domain service instance
     */
    public InstituteAdminServer(int port, InstituteAdminDomainService domainService) {
        this(resolveBindHost(), port, domainService, new com.campx.admin.institute.repository.UserPreferenceRepository());
    }

    /**
     * Initializes the server with custom bind host, custom port, and domain service.
     *
     * @param host          bind address (default: 127.0.0.1)
     * @param port          TCP port to listen on
     * @param domainService business domain service instance
     */
    public InstituteAdminServer(String host, int port, InstituteAdminDomainService domainService) {
        this(host, port, domainService, new com.campx.admin.institute.repository.UserPreferenceRepository());
    }

    /**
     * Initializes the server with custom bind host, custom port, domain service, and user preference repository.
     *
     * @param host                   bind address
     * @param port                   TCP port to listen on
     * @param domainService          business domain service instance
     * @param userPreferenceRepository user preference repository instance
     */
    public InstituteAdminServer(String host, int port, InstituteAdminDomainService domainService,
                                com.campx.admin.institute.repository.UserPreferenceRepository userPreferenceRepository) {
        this.host = (host != null && !host.trim().isEmpty()) ? host.trim() : resolveBindHost();
        this.port = port;
        this.domainService = domainService;
        this.userPreferenceRepository = userPreferenceRepository != null ? userPreferenceRepository : new com.campx.admin.institute.repository.UserPreferenceRepository();
        this.userPreferenceController = new com.campx.admin.institute.controller.UserPreferenceController(this.userPreferenceRepository);
    }

    /**
     * Initializes the server with custom bind host, port, domain service, and preference controller.
     */
    public InstituteAdminServer(String host, int port, InstituteAdminDomainService domainService,
                                com.campx.admin.institute.controller.UserPreferenceController userPreferenceController) {
        this.host = (host != null && !host.trim().isEmpty()) ? host.trim() : resolveBindHost();
        this.port = port;
        this.domainService = domainService;
        this.userPreferenceController = userPreferenceController != null ? userPreferenceController : new com.campx.admin.institute.controller.UserPreferenceController();
        this.userPreferenceRepository = this.userPreferenceController.getRepository();
    }

    private static String resolveBindHost() {
        return com.campx.logger.security.GatewayHmacSecurityConfig.getBindHost();
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

        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(null);

        InstituteAdminController controller = new InstituteAdminController(domainService);
        server.createContext("/api/v1/admin", controller);

        // Mount User Preferences endpoints (Wave 2)
        server.createContext("/api/v1/user/preferences", userPreferenceController);
        server.createContext("/api/v1/user", userPreferenceController);

        server.start();
        running = true;
        logger.info("ADM-01 Institute Admin Service started on {}:{}", host, port);
    }

    /**
     * Returns the host address bound to this server.
     *
     * @return host address
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the TCP port bound to this server.
     *
     * @return bound TCP port
     */
    public int getPort() {
        return server != null ? server.getAddress().getPort() : port;
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
     * Returns the underlying domain service instance managing platform state.
     *
     * @return {@link InstituteAdminDomainService}
     */
    public InstituteAdminDomainService getDomainService() {
        return domainService;
    }

    public com.campx.admin.institute.repository.UserPreferenceRepository getUserPreferenceRepository() {
        return userPreferenceRepository;
    }

    public com.campx.admin.institute.controller.UserPreferenceController getUserPreferenceController() {
        return userPreferenceController;
    }
}
