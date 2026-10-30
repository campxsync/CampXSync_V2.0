package com.campx.academic.curriculum.server;

import com.campx.academic.curriculum.controller.CurriculumController;
import com.campx.academic.curriculum.service.CurriculumDomainService;
import com.campx.academic.curriculum.service.OutboxRelayService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP server hosting the ACD-02 Curriculum Management Service.
 * Serves REST endpoints under {@code /api/v1/curricula/**} and {@code /api/v1/academics/curricula/**}.
 */
public class CurriculumServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CurriculumServer.class);

    private final int port;
    private final CurriculumDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private boolean tlsEnabled = false;
    private com.campx.academic.curriculum.service.OutboxRelayService outboxRelay;
    private java.util.concurrent.ScheduledExecutorService cleanupScheduler;

    /**
     * Constructs a new CurriculumServer instance.
     *
     * @param port the TCP port to bind HTTP/HTTPS listeners to
     * @param domainService the domain logic engine, or null to instantiate a default instance
     */
    public CurriculumServer(int port, CurriculumDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new CurriculumDomainService();
    }

    /**
     * Initializes and starts the embedded HTTP/HTTPS server, background outbox relay worker,
     * and scheduled idempotency cleanup task.
     *
     * @throws IOException if network binding fails
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        String keystorePath = System.getProperty("HTTPS_KEYSTORE_PATH");
        if (keystorePath == null || keystorePath.isEmpty()) {
            keystorePath = System.getenv("HTTPS_KEYSTORE_PATH");
        }

        if (keystorePath != null && new java.io.File(keystorePath).exists()) {
            try {
                String keystorePass = System.getProperty("HTTPS_KEYSTORE_PASSWORD");
                if (keystorePass == null) keystorePass = System.getenv("HTTPS_KEYSTORE_PASSWORD");
                char[] password = (keystorePass != null ? keystorePass : "changeit").toCharArray();

                java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
                try (java.io.FileInputStream fis = new java.io.FileInputStream(keystorePath)) {
                    ks.load(fis, password);
                }

                javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, password);

                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
                sslContext.init(kmf.getKeyManagers(), null, null);

                com.sun.net.httpserver.HttpsServer httpsServer = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(port), 0);
                httpsServer.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(sslContext) {
                    public void configure(com.sun.net.httpserver.HttpsParameters params) {
                        try {
                            javax.net.ssl.SSLContext c = getSSLContext();
                            javax.net.ssl.SSLEngine engine = c.createSSLEngine();
                            params.setNeedClientAuth(false);
                            params.setCipherSuites(engine.getEnabledCipherSuites());
                            params.setProtocols(engine.getEnabledProtocols());
                            params.setSSLParameters(c.getDefaultSSLParameters());
                        } catch (Exception ex) {
                            logger.error("Failed to configure HTTPS parameters", ex);
                        }
                    }
                });
                server = httpsServer;
                tlsEnabled = true;
                logger.info("TLS configuration enabled with keystore: {}", keystorePath);
            } catch (Exception e) {
                logger.warn("Failed to initialize TLS/HTTPS server, falling back to standard HTTP: {}", e.getMessage());
                server = HttpServer.create(new InetSocketAddress(port), 0);
                tlsEnabled = false;
            }
        } else {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            tlsEnabled = false;
        }

        server.setExecutor(null);

        CurriculumController controller = new CurriculumController(domainService);
        server.createContext("/api/v1/curricula", controller);
        server.createContext("/api/v1/academics/curricula", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);
        server.createContext("/v1/curriculum-catalog", controller);

        server.start();
        running = true;

        // Start Outbox Relay worker daemon (Story 51)
        outboxRelay = new OutboxRelayService(domainService);
        outboxRelay.start();

        // Start Idempotency background cleanup scheduler (Story 57)
        cleanupScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idempotency-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(
                domainService::purgeExpiredIdempotencyRecords,
                1, 1, java.util.concurrent.TimeUnit.HOURS);

        logger.info("CampXSync ACD-02 Curriculum Management Service started on port {} (tls={})", port, tlsEnabled);
    }

    /**
     * Checks if TLS/HTTPS transport encryption is active.
     *
     * @return true if secured via TLS, false if HTTP
     */
    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    /**
     * Retrieves the background outbox relay service daemon.
     *
     * @return active outbox relay instance
     */
    public OutboxRelayService getOutboxRelay() {
        return outboxRelay;
    }

    /**
     * Gracefully stops the HTTP server, shuts down the outbox relay daemon, and terminates scheduled jobs.
     */
    public synchronized void stop() {
        if (outboxRelay != null) {
            outboxRelay.stop();
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("CampXSync ACD-02 Curriculum Management Service stopped");
        }
    }

    /**
     * Checks whether the HTTP server is currently listening and active.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the configured TCP port.
     *
     * @return server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the underlying domain service instance.
     *
     * @return curriculum domain service
     */
    public CurriculumDomainService getDomainService() {
        return domainService;
    }
}
