package com.campx.document;

import com.campx.document.config.DatabaseConfig;
import com.campx.document.config.DatabaseConnectionManager;
import com.campx.document.intelligence.DocumentIntelligenceService;
import com.campx.document.persistence.DocumentPersistence;
import com.campx.document.persistence.InMemoryDocumentPersistence;
import com.campx.document.persistence.PostgresDocumentPersistence;
import com.campx.document.security.GatewayHmacVerifier;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * STM-06 Student Documents Service.
 * Listens on 127.0.0.1:8098 and persists to PostgreSQL when JDBC settings are present.
 */
public class DocumentApplication {

    public static final int DEFAULT_PORT = 8098;
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DocumentApplication.class);

    public static void main(String[] args) throws IOException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        DatabaseConnectionManager connectionManager = DatabaseConnectionManager.getInstance();
        DocumentPersistence persistence;
        boolean persistent = false;

        if (dbConfig.isConfigured()) {
            connectionManager.initialize(dbConfig);
            persistence = new PostgresDocumentPersistence(connectionManager);
            persistent = true;
            logger.info("STM-06 document service starting with PostgreSQL persistence on azulbaxszsqxymqmlwfe");
        } else if (isDeployedEnvironment()) {
            throw new IllegalStateException("STM-06 refuses deployed start without a configured development database");
        } else {
            persistence = new InMemoryDocumentPersistence();
            logger.warn("STM-06 document service starting in local in-memory mode");
        }

        DocumentIntelligenceService intelligenceService = new DocumentIntelligenceService();
        DocumentDomainService domain = new DocumentDomainService(persistence, intelligenceService);
        String hmacSecret = System.getenv("CAMPX_INTERNAL_SECRET");
        if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
            hmacSecret = System.getenv("CAMPX_GATEWAY_HMAC_SECRET");
        }
        final DocumentController controller = new DocumentController(
                domain,
                new GatewayHmacVerifier(hmacSecret)
        );

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", DEFAULT_PORT), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Map<String, String> headers = new HashMap<String, String>();
                for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
                    }
                }
                String query = exchange.getRequestURI().getRawQuery();
                if (query != null && !query.trim().isEmpty()) {
                    for (String param : query.split("&")) {
                        int idx = param.indexOf('=');
                        if (idx > 0) {
                            String k = param.substring(0, idx).trim().toLowerCase();
                            String v = URLDecoder.decode(param.substring(idx + 1).trim(), StandardCharsets.UTF_8.name());
                            if (!headers.containsKey("x-query-" + k)) {
                                headers.put("x-query-" + k, v);
                            }
                        }
                    }
                }
                DocumentController.Response response = controller.handle(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        headers,
                        read(exchange.getRequestBody())
                );
                String traceId = headers.get("x-trace-id");
                if (traceId == null || traceId.trim().isEmpty()) {
                    traceId = headers.get("x-correlation-id");
                }
                if (traceId != null && !traceId.trim().isEmpty()) {
                    exchange.getResponseHeaders().set("X-Trace-Id", traceId.trim());
                    exchange.getResponseHeaders().set("X-Correlation-Id", traceId.trim());
                }
                byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
                String contentType = response.status >= 400 ? "application/problem+json; charset=UTF-8" : "application/json; charset=UTF-8";
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(response.status, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });
        server.start();
        logger.info("STM-06 student documents service listening on 127.0.0.1:{} persistent={}", DEFAULT_PORT, persistent);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
        }
    }

    static boolean isDeployedEnvironment() {
        String env = System.getenv("CAMPX_ENVIRONMENT");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("NODE_ENV");
        }
        return env != null && !env.trim().isEmpty()
                && !env.equalsIgnoreCase("test")
                && !env.equalsIgnoreCase("local")
                && !env.equalsIgnoreCase("development");
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int n;
        while ((n = input.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}

