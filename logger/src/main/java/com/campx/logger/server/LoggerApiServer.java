package com.campx.logger.server;

import com.campx.logger.api.LogEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.core.LogManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight embedded HTTP REST management server exposing runtime administration and ingestion APIs.
 * <p>
 * Implemented using the standard JDK {@link HttpServer} to guarantee zero third-party dependencies.
 * Exposes the following HTTP REST endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/logger/status}: Health check, queue telemetry, and file paths</li>
 *   <li>{@code GET /api/v1/logger/level}: Inquires active root severity level</li>
 *   <li>{@code POST /api/v1/logger/level}: Dynamically alters root or category logging severity</li>
 *   <li>{@code POST /api/v1/logger/rotate}: Triggers immediate log file archive rotation</li>
 *   <li>{@code POST /api/v1/logs}: Ingestion endpoint for remote microservices, browser clients, and AI agents</li>
 * </ul>
 *
 * @see LogManager
 */
public class LoggerApiServer {

    private final int port;
    private HttpServer server;
    private boolean started = false;

    /**
     * Initializes the API server configured for the specified port.
     *
     * @param port TCP port to bind (e.g. 9898)
     */
    public LoggerApiServer(int port) {
        this.port = port;
    }

    /**
     * Starts the HTTP server, initializes route contexts, and begins accepting network connections.
     *
     * @throws IOException if port binding or socket creation fails
     */
    public synchronized void start() throws IOException {
        if (started) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(null); // default executor

        // Register handlers
        server.createContext("/api/v1/logger/status", new StatusHandler());
        server.createContext("/api/v1/logger/level", new LevelHandler());
        server.createContext("/api/v1/logger/rotate", new RotateHandler());
        server.createContext("/api/v1/logs", new IngestionHandler());

        server.start();
        started = true;
    }

    /**
     * Halts the HTTP server immediately and releases socket bindings.
     */
    public synchronized void stop() {
        if (server != null && started) {
            server.stop(0);
            started = false;
        }
    }

    /**
     * Checks if the HTTP server is currently running.
     *
     * @return {@code true} if server is active
     */
    public boolean isStarted() {
        return started;
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
     * Helper writing a UTF-8 JSON response payload with proper HTTP headers.
     *
     * @param exchange     the active HTTP exchange
     * @param statusCode   HTTP response status code
     * @param responseJson serialized JSON response string
     * @throws IOException if network writing fails
     */
    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Reads and decodes the UTF-8 HTTP request payload.
     *
     * @param exchange the active HTTP exchange
     * @return body string
     * @throws IOException if reading the stream fails
     */
    private static String readRequestBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Parses standard URL query string key-value pairs into a map.
     *
     * @param query raw query string (e.g. "level=DEBUG&logger=com.campx")
     * @return map of query parameters
     */
    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return map;
        }
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                map.put(pair[0].trim(), pair[1].trim());
            } else if (pair.length == 1) {
                map.put(pair[0].trim(), "");
            }
        }
        return map;
    }

    /**
     * HTTP handler for {@code GET /api/v1/logger/status}.
     * Returns service health, buffer queue capacity, current sizes, and active log paths.
     */
    private static class StatusHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            LogManager lm = LogManager.getInstance();
            String json = "{"
                    + "\"status\":\"UP\","
                    + "\"service\":\"CampXSync-Logger\","
                    + "\"rootLevel\":\"" + lm.getConfig().getRootLevel().name() + "\","
                    + "\"queueSize\":" + lm.getAsyncProcessor().getQueueSize() + ","
                    + "\"queueCapacity\":" + lm.getAsyncProcessor().getQueueCapacity() + ","
                    + "\"logFilePath\":\"" + lm.getConfig().getFilePath() + "\","
                    + "\"jsonFilePath\":\"" + lm.getConfig().getJsonFilePath() + "\""
                    + "}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    /**
     * HTTP handler for {@code GET / POST /api/v1/logger/level}.
     * Inspects or dynamically updates the minimum log severity threshold at runtime.
     */
    private static class LevelHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LogManager lm = LogManager.getInstance();
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                String json = "{\"rootLevel\":\"" + lm.getConfig().getRootLevel().name() + "\"}";
                sendJsonResponse(exchange, 200, json);
                return;
            }

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                String levelStr = queryParams.get("level");
                String loggerName = queryParams.get("logger");

                // Check request body if not in query
                if (levelStr == null || levelStr.isEmpty()) {
                    String body = readRequestBody(exchange);
                    Matcher matcher = Pattern.compile("(?i)\"level\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                    if (matcher.find()) {
                        levelStr = matcher.group(1);
                    }
                    Matcher loggerMatcher = Pattern.compile("(?i)\"logger\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
                    if (loggerMatcher.find()) {
                        loggerName = loggerMatcher.group(1);
                    }
                }

                if (levelStr == null || levelStr.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Missing required parameter 'level'\"}");
                    return;
                }

                LogLevel newLevel = LogLevel.fromString(levelStr, null);
                if (newLevel == null) {
                    sendJsonResponse(exchange, 400, "{\"error\":\"Invalid log level: " + levelStr + "\"}");
                    return;
                }

                if (loggerName != null && !loggerName.isEmpty() && !"ROOT".equalsIgnoreCase(loggerName)) {
                    lm.setLoggerLevel(loggerName, newLevel);
                    String resp = "{\"status\":\"SUCCESS\",\"logger\":\"" + loggerName + "\",\"level\":\"" + newLevel.name() + "\"}";
                    sendJsonResponse(exchange, 200, resp);
                } else {
                    lm.setRootLevel(newLevel);
                    String resp = "{\"status\":\"SUCCESS\",\"logger\":\"ROOT\",\"level\":\"" + newLevel.name() + "\"}";
                    sendJsonResponse(exchange, 200, resp);
                }
                return;
            }

            sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
        }
    }

    /**
     * HTTP handler for {@code POST /api/v1/logger/rotate}.
     * Executes on-demand file archive rotation across active disk appenders.
     */
    private static class RotateHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            LogManager.getInstance().rotateAppenders();
            sendJsonResponse(exchange, 200, "{\"status\":\"SUCCESS\",\"message\":\"Log rotation executed\"}");
        }
    }

    /**
     * HTTP handler for {@code POST /api/v1/logs}.
     * Remote ingestion endpoint enabling sibling services, gateways, or browser clients to send logs.
     */
    private static class IngestionHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String body = readRequestBody(exchange);
            if (body.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Request body is empty\"}");
                return;
            }

            // Extract fields using pattern parsing
            String levelStr = extractField(body, "level", "INFO");
            String loggerName = extractField(body, "logger", "EXTERNAL_SERVICE");
            String message = extractField(body, "message", "");
            String flowId = extractField(body, "flowId", null);
            String operation = extractField(body, "operation", null);

            LogLevel level = LogLevel.fromString(levelStr, LogLevel.INFO);

            LogEvent.Builder builder = LogEvent.builder()
                    .level(level)
                    .loggerName(loggerName)
                    .message(message)
                    .flowId(flowId)
                    .operation(operation)
                    .addTag("INGESTION_API");

            LogManager.getInstance().dispatch(builder.build());
            sendJsonResponse(exchange, 202, "{\"status\":\"ACCEPTED\",\"message\":\"Log event ingested\"}");
        }

        /**
         * Extracts a string field from a JSON object string.
         *
         * @param json       raw JSON string
         * @param key        attribute name
         * @param defaultVal fallback if key is not matched
         * @return extracted string value
         */
        private String extractField(String json, String key, String defaultVal) {
            Pattern p = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
            Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return defaultVal;
        }
    }
}
