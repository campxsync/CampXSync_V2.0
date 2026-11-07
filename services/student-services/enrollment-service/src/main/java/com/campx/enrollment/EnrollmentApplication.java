package com.campx.enrollment;

import com.campx.enrollment.config.DatabaseConfig;
import com.campx.enrollment.config.DatabaseConnectionManager;
import com.campx.enrollment.persistence.PostgresEnrollmentPersistence;
import com.campx.enrollment.security.GatewayHmacVerifier;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnrollmentApplication {

    public static final int DEFAULT_PORT = 8095;
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(EnrollmentApplication.class);

    public static void main(String[] args) throws IOException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        DatabaseConnectionManager connectionManager = DatabaseConnectionManager.getInstance();
        EnrollmentDomainService domain;

        if (dbConfig.isConfigured()) {
            connectionManager.initialize(dbConfig);
            domain = new EnrollmentDomainService(Clock.systemUTC(), new PostgresEnrollmentPersistence(connectionManager));
            logger.info("STM-03 enrolment service starting with PostgreSQL persistence");
        } else if (isDeployedEnvironment()) {
            throw new IllegalStateException(
                    "STM-03 refuses deployed start without CAMPX_DB_URL/CAMPX_DB_PASSWORD (or SUPABASE_JDBC_URL/SUPABASE_PASSWORD).");
        } else {
            domain = new EnrollmentDomainService();
            logger.warn("STM-03 enrolment service starting in local in-memory mode (no JDBC configured)");
        }

        String secret = System.getenv("CAMPX_GATEWAY_HMAC_SECRET");
        final EnrollmentController controller = new EnrollmentController(domain, new GatewayHmacVerifier(secret));
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
                String body = read(exchange.getRequestBody());
                EnrollmentController.Response response = controller.handle(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body);
                byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });
        server.start();
        logger.info("STM-03 enrolment service listening on 127.0.0.1:{} persistent={}", DEFAULT_PORT, domain.isPersistent());
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
        int read;
        while ((read = input.read(data)) != -1) {
            buffer.write(data, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
