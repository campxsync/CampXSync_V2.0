package com.campx.gateway;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ApiGatewayRoutingTest {

    private static GatewayServer gatewayServer;
    private static final int TEST_PORT = 8090;

    @BeforeClass
    public static void setup() throws Exception {
        GatewayConfig config = new GatewayConfig();
        config.setPort(TEST_PORT);
        config.addRoute("/api/v1/offline-service", "http://localhost:59999");
        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void teardown() {
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/actuator/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String traceId = conn.getHeaderField("X-Trace-Id");
        assertNotNull("Gateway must inject X-Trace-Id header", traceId);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"UP\""));
    }

    @Test
    public void testRoutesEndpoint() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/gateway/routes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String resp = readResponse(conn);
        assertTrue(resp.contains("/api/v1/admin"));
        assertTrue(resp.contains("/api/v1/college-admin"));
    }

    @Test
    public void testRouteNotFound404() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/unknown-endpoint/test");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String traceId = conn.getHeaderField("X-Trace-Id");
        assertNotNull("Gateway must inject X-Trace-Id header on errors", traceId);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":404"));
        assertTrue(resp.contains("\"errorCode\":\"GATEWAY_ROUTE_NOT_FOUND\""));
        assertTrue(resp.contains("\"error\":\"Not Found\""));
    }

    @Test
    public void testDownstreamServiceUnavailable503() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/offline-service/test");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        int code = conn.getResponseCode();
        assertEquals(503, code);

        String traceId = conn.getHeaderField("X-Trace-Id");
        assertNotNull("Gateway must inject X-Trace-Id header on errors", traceId);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":503"));
        assertTrue(resp.contains("\"errorCode\":\"GATEWAY_SERVICE_UNAVAILABLE\""));
        assertTrue(resp.contains("\"error\":\"Service Unavailable\""));
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        java.io.InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
