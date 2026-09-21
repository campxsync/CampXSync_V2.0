package com.campx.gateway.router;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.filter.CorrelationFilter;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reverse proxy handler that dynamically dispatches client requests to the
 * appropriate downstream microservice (Institute Admin or College Admin)
 * while injecting correlation tokens and recording execution flow metrics.
 */
public class ReverseProxyHandler implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ReverseProxyHandler.class);

    private final GatewayConfig config;
    private final CorrelationFilter correlationFilter = new CorrelationFilter();

    public ReverseProxyHandler(GatewayConfig config) {
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // 1. Initialize correlation & LogContext
        String traceId = correlationFilter.apply(exchange);

        try (FlowTracker flow = logger.flow("GatewayRouteDispatch", "GW-" + traceId)) {
            logger.info("Incoming Gateway request: [{}] {} from {}", method, path, exchange.getRemoteAddress());

            // 2. Health & Route Info endpoints handled directly
            if ("/actuator/health".equals(path)) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"gateway\":\"CampXSync-API-Gateway\"}");
                return;
            }
            if ("/api/v1/gateway/routes".equals(path)) {
                StringBuilder routesJson = new StringBuilder("{\"routes\":[");
                int i = 0;
                for (Map.Entry<String, String> entry : config.getRouteTable().entrySet()) {
                    if (i > 0) routesJson.append(",");
                    routesJson.append("{\"prefix\":\"").append(entry.getKey()).append("\",\"target\":\"").append(entry.getValue()).append("\"}");
                    i++;
                }
                routesJson.append("]}");
                sendJson(exchange, 200, routesJson.toString());
                return;
            }

            // 3. Resolve target downstream destination URL
            String query = exchange.getRequestURI().getQuery();
            String destinationUrlStr = resolveDestinationUrl(path, query);
            if (destinationUrlStr == null) {
                logger.warn("No route registered for request path: {}", path);
                sendJson(exchange, 404, "{\"error\":\"Route Not Found\",\"path\":\"" + path + "\"}");
                return;
            }

            logger.debug("Proxying [{}] {} -> {}", method, path, destinationUrlStr);
            proxyRequest(exchange, destinationUrlStr, method);
        } catch (Exception e) {
            logger.error("Gateway proxy error for [{}]: {}", path, e.getMessage(), e);
            sendJson(exchange, 502, "{\"error\":\"Bad Gateway\",\"message\":\"" + e.getMessage() + "\"}");
        } finally {
            LogContext.clear();
        }
    }

    private String resolveDestinationUrl(String path, String query) {
        for (Map.Entry<String, String> entry : config.getRouteTable().entrySet()) {
            String prefix = entry.getKey();
            if (path.startsWith(prefix)) {
                String target = entry.getValue();
                String destination;
                try {
                    URL targetUrl = new URL(target);
                    String targetPath = targetUrl.getPath();
                    if (targetPath == null || targetPath.isEmpty() || "/".equals(targetPath)) {
                        // Target is base host (e.g., http://localhost:8081) - preserve full request path
                        String cleanTarget = target.replaceAll("/+$", "");
                        destination = cleanTarget + (path.startsWith("/") ? path : "/" + path);
                    } else {
                        // Target has explicit destination path - append remaining relative path
                        String remaining = path.substring(prefix.length());
                        String cleanTarget = target.replaceAll("/+$", "");
                        destination = cleanTarget + (remaining.startsWith("/") ? remaining : (remaining.isEmpty() ? "" : "/" + remaining));
                    }
                } catch (Exception e) {
                    String remaining = path.substring(prefix.length());
                    destination = target + remaining;
                }
                return destination + (query != null ? "?" + query : "");
            }
        }
        return null;
    }

    private void proxyRequest(HttpExchange clientExchange, String destinationUrlStr, String method) throws IOException {
        URL targetUrl = new URL(destinationUrlStr);
        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(false);

        // Copy incoming headers to target connection
        Headers incomingHeaders = clientExchange.getRequestHeaders();
        for (Map.Entry<String, List<String>> header : incomingHeaders.entrySet()) {
            String name = header.getKey();
            if (!"Host".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                for (String val : header.getValue()) {
                    conn.addRequestProperty(name, val);
                }
            }
        }

        // Forward body if present
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            conn.setDoOutput(true);
            byte[] body = readAllBytes(clientExchange.getRequestBody());
            if (body.length > 0) {
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }
            }
        }

        // Receive response
        int responseCode;
        InputStream respStream;
        try {
            responseCode = conn.getResponseCode();
            respStream = conn.getInputStream();
        } catch (IOException e) {
            responseCode = conn.getResponseCode();
            respStream = conn.getErrorStream();
        }

        // Copy response headers
        Headers outgoingHeaders = clientExchange.getResponseHeaders();
        for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
            String name = header.getKey();
            if (name != null && !"Transfer-Encoding".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name)) {
                for (String val : header.getValue()) {
                    outgoingHeaders.add(name, val);
                }
            }
        }

        byte[] respBytes = (respStream != null) ? readAllBytes(respStream) : new byte[0];
        clientExchange.sendResponseHeaders(responseCode, respBytes.length);

        if (respBytes.length > 0) {
            try (OutputStream os = clientExchange.getResponseBody()) {
                os.write(respBytes);
            }
        }

        logger.info("Gateway proxy completed: [{}] {} -> status={}", method, destinationUrlStr, responseCode);
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
