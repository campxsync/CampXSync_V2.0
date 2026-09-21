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

    private String readResponse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
