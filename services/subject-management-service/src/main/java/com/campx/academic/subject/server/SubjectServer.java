package com.campx.academic.subject.server;

import com.campx.academic.subject.controller.SubjectController;
import com.campx.academic.subject.service.OutboxRelayService;
import com.campx.academic.subject.service.SubjectDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Embedded HTTP/HTTPS server hosting the ACD-03 Subject Management Service.
 * Serves REST endpoints under {@code /api/v1/academics/subjects/**} and {@code /api/v1/subjects/**}.
 */
public class SubjectServer {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SubjectServer.class);

    private final int port;
    private final SubjectDomainService domainService;
    private HttpServer server;
    private boolean running = false;
    private boolean tlsEnabled = false;
    private OutboxRelayService outboxRelay;
    private java.util.concurrent.ScheduledExecutorService cleanupScheduler;

    public SubjectServer(int port, SubjectDomainService domainService) {
        this.port = port;
        this.domainService = domainService != null ? domainService : new SubjectDomainService();
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

        SubjectController controller = new SubjectController(domainService);
        server.createContext("/api/v1/academics/subjects", controller);
        server.createContext("/api/v1/subjects", controller);
        server.createContext("/v1/subjects", controller);
        server.createContext("/v1/subject-catalog", controller);
        server.createContext("/actuator/health", controller);
        server.createContext("/metrics", controller);

        server.start();
        running = true;

        // Start Outbox Relay worker daemon (Story 50)
        outboxRelay = new OutboxRelayService(domainService);
        outboxRelay.start();

        // Start Idempotency background cleanup scheduler (Story 56)
        cleanupScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idempotency-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(
                domainService::purgeExpiredIdempotencyRecords,
                1, 1, java.util.concurrent.TimeUnit.HOURS);

        logger.info("CampXSync ACD-03 Subject Management Service started on port {} (tls={})", port, tlsEnabled);
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public OutboxRelayService getOutboxRelay() {
        return outboxRelay;
    }

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
            logger.info("CampXSync ACD-03 Subject Management Service stopped on port {}", port);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
