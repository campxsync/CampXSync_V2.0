package com.campx.gateway;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Verifies API Gateway ingress routing and header propagation for User Preferences (Wave 2).
 */
public class GatewayUserPreferencesRoutingTest {

    private static GatewayServer gatewayServer;
    private static HttpServer mockDownstreamServer;
    private static int gatewayPort;
    private static int downstreamPort;

    private static final AtomicReference<String> lastReceivedPath = new AtomicReference<>();
    private static final AtomicReference<String> lastReceivedQuery = new AtomicReference<>();
    private static final AtomicReference<String> lastReceivedMethod = new AtomicReference<>();

    @BeforeClass
    public static void setup() throws Exception {
        // 1. Start mock downstream service simulating Institute Admin Service
        mockDownstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstreamPort = mockDownstreamServer.getAddress().getPort();
        mockDownstreamServer.createContext("/api/v1/user/preferences", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                lastReceivedPath.set(exchange.getRequestURI().getPath());
                lastReceivedQuery.set(exchange.getRequestURI().getQuery());
                lastReceivedMethod.set(exchange.getRequestMethod());

                String resp = "{\"success\":true,\"preferences\":{\"version\":1,\"theme\":\"dark\"}}";
                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        mockDownstreamServer.start();

        // 2. Start Gateway routing /api/v1/user/preferences to mock downstream
        GatewayConfig config = new GatewayConfig();
        // Ephemeral port for test gateway
        gatewayPort = findFreePort();
        config.setPort(gatewayPort);
        config.addRoute("/api/v1/user/preferences", "http://127.0.0.1:" + downstreamPort + "/api/v1/user/preferences");
        config.addRoute("/api/v1/user", "http://127.0.0.1:" + downstreamPort + "/api/v1/user");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void teardown() {
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
        if (mockDownstreamServer != null) {
            mockDownstreamServer.stop(0);
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testGatewayConfigDefaultRoutesIncludeUserPreferences() {
        GatewayConfig defaultCfg = new GatewayConfig();
        assertTrue(defaultCfg.getRouteTable().containsKey("/api/v1/user/preferences"));
        assertEquals("http://localhost:8081/api/v1/user/preferences", defaultCfg.getRouteTable().get("/api/v1/user/preferences"));

        assertTrue(defaultCfg.getRouteTable().containsKey("/api/v1/user"));
        assertEquals("http://localhost:8081/api/v1/user", defaultCfg.getRouteTable().get("/api/v1/user"));
    }

    @Test
    public void testGatewayRoutesGetPreferencesToDownstream() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/api/v1/user/preferences?role=faculty");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        int code = conn.getResponseCode();
        assertEquals(200, code);

        assertEquals("/api/v1/user/preferences", lastReceivedPath.get());
        assertEquals("role=faculty", lastReceivedQuery.get());
        assertEquals("GET", lastReceivedMethod.get());

        // Check correlation header injected by gateway
        assertNotNull(conn.getHeaderField("X-Trace-Id"));

        String body = readResponse(conn);
        assertTrue(body.contains("\"success\":true"));
        assertTrue(body.contains("\"theme\":\"dark\""));
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            return 8099;
        }
    }
}
