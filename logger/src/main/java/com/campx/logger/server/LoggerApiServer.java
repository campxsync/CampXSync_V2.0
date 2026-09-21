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
 * Lightweight embedded HTTP REST server exposing runtime management and log ingestion APIs
 * for other services in the CampXSync College ERP ecosystem.
 *
 * Built on the standard JDK HttpServer to ensure zero external dependencies.
 */
public class LoggerApiServer {

    private final int port;
    private HttpServer server;
    private boolean started = false;

    public LoggerApiServer(int port) {
        this.port = port;
    }

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

    public synchronized void stop() {
        if (server != null && started) {
            server.stop(0);
            started = false;
        }
    }

    public boolean isStarted() {
        return started;
    }

    public int getPort() {
        return port;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

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
     * GET /api/v1/logger/status
     */
    private static class StatusHandler implements HttpHandler {
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
     * GET / POST /api/v1/logger/level?level=DEBUG
     */
    private static class LevelHandler implements HttpHandler {
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
     * POST /api/v1/logger/rotate
     */
    private static class RotateHandler implements HttpHandler {
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
     * POST /api/v1/logs
     * Remote ingestion endpoint for other microservices (e.g. ai-service, frontend gateway)
     */
    private static class IngestionHandler implements HttpHandler {
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
