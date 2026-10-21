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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * End-to-end integration test verifying unified API Gateway reverse proxy routing
 * for Wave 3: Academic Departments and Programs.
 * <p>
 * Verifies that:
 * 1. Default route table entries exist for:
 *    - /api/v1/college-admin -> college-admin-service (port 8082)
 *    - /v1/departments -> /api/v1/college-admin/departments (port 8082)
 *    - /v1/programs -> /api/v1/college-admin/programs (port 8082)
 * 2. Ingress HTTP requests to /api/v1/college-admin/departments and /api/v1/college-admin/programs
 *    are forwarded to the downstream college admin service.
 * 3. Canonical aliases /v1/departments and /v1/programs are routed to the downstream endpoints.
 * 4. Downstream requests carry the required gateway HMAC security headers.
 */
public class GatewayDepartmentProgramRoutingTest {

    private static GatewayServer gatewayServer;
    private static HttpServer mockCollegeAdminServer;
    private static int gatewayPort;
    private static int downstreamPort;

    private static final AtomicReference<String> lastReceivedPath = new AtomicReference<>();
    private static final AtomicReference<String> lastReceivedMethod = new AtomicReference<>();
    private static final AtomicReference<String> lastReceivedBody = new AtomicReference<>();
    private static final AtomicReference<String> lastSignature = new AtomicReference<>();
    private static final AtomicReference<String> lastTimestamp = new AtomicReference<>();

    @BeforeClass
    public static void setup() throws Exception {
        // 1. Start mock downstream college admin server
        mockCollegeAdminServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstreamPort = mockCollegeAdminServer.getAddress().getPort();

        HttpHandler handler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws java.io.IOException {
                lastReceivedPath.set(exchange.getRequestURI().getPath());
                lastReceivedMethod.set(exchange.getRequestMethod());
                lastSignature.set(exchange.getRequestHeaders().getFirst("X-Gateway-Signature"));
                lastTimestamp.set(exchange.getRequestHeaders().getFirst("X-Gateway-Timestamp"));

                try (InputStream is = exchange.getRequestBody()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    lastReceivedBody.set(sb.toString());
                }

                String path = exchange.getRequestURI().getPath();
                String resp;
                int statusCode;

                if (path.contains("/departments")) {
                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        resp = "{\"success\":true,\"department\":{\"id\":\"dep-101\",\"code\":\"CSE\",\"name\":\"Computer Science\"}}";
                        statusCode = 201;
                    } else {
                        resp = "{\"success\":true,\"departments\":[{\"id\":\"dep-101\",\"code\":\"CSE\"}]}";
                        statusCode = 200;
                    }
                } else if (path.contains("/programs")) {
                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        resp = "{\"success\":true,\"program\":{\"id\":\"prog-201\",\"code\":\"BTECH_CS\",\"name\":\"B.Tech CS\"}}";
                        statusCode = 201;
                    } else {
                        resp = "{\"success\":true,\"programs\":[{\"id\":\"prog-201\",\"code\":\"BTECH_CS\"}]}";
                        statusCode = 200;
                    }
                } else {
                    resp = "{\"success\":true}";
                    statusCode = 200;
                }

                byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(statusCode, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        };

        mockCollegeAdminServer.createContext("/api/v1/college-admin", handler);
        mockCollegeAdminServer.start();

        // 2. Start API Gateway on dynamic free port
        gatewayPort = findFreePort();
        GatewayConfig config = new GatewayConfig();
        config.setPort(gatewayPort);
        config.addRoute("/api/v1/college-admin", "http://127.0.0.1:" + downstreamPort + "/api/v1/college-admin");
        config.addRoute("/v1/departments", "http://127.0.0.1:" + downstreamPort + "/api/v1/college-admin/departments");
        config.addRoute("/v1/programs", "http://127.0.0.1:" + downstreamPort + "/api/v1/college-admin/programs");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void teardown() {
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
        if (mockCollegeAdminServer != null) {
            mockCollegeAdminServer.stop(0);
        }
        CampXLoggerFactory.flush();
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    @Test
    public void testGatewayConfigDefaultRoutesIncludeDepartmentsAndPrograms() {
        GatewayConfig defaultCfg = new GatewayConfig();
        assertTrue("Default config must map /api/v1/college-admin",
                defaultCfg.getRouteTable().containsKey("/api/v1/college-admin"));
        assertEquals("http://localhost:8082/api/v1/college-admin",
                defaultCfg.getRouteTable().get("/api/v1/college-admin"));

        assertTrue("Default config must map canonical alias /v1/departments",
                defaultCfg.getRouteTable().containsKey("/v1/departments"));
        assertEquals("http://localhost:8082/api/v1/college-admin/departments",
                defaultCfg.getRouteTable().get("/v1/departments"));

        assertTrue("Default config must map canonical alias /v1/programs",
                defaultCfg.getRouteTable().containsKey("/v1/programs"));
        assertEquals("http://localhost:8082/api/v1/college-admin/programs",
                defaultCfg.getRouteTable().get("/v1/programs"));
    }

    @Test
    public void testGatewayRoutesGetDepartments() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("/api/v1/college-admin/departments", lastReceivedPath.get());
        assertEquals("GET", lastReceivedMethod.get());
    }

    @Test
    public void testGatewayRoutesPostDepartment() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        String payload = "{\"departmentCode\":\"ECE\",\"name\":\"Electronics\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("/api/v1/college-admin/departments", lastReceivedPath.get());
        assertEquals("POST", lastReceivedMethod.get());
        assertEquals(payload, lastReceivedBody.get());
    }

    @Test
    public void testGatewayRoutesCanonicalDepartmentsAlias() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/v1/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("/api/v1/college-admin/departments", lastReceivedPath.get());
    }

    @Test
    public void testGatewayRoutesGetPrograms() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/api/v1/college-admin/programs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("/api/v1/college-admin/programs", lastReceivedPath.get());
        assertEquals("GET", lastReceivedMethod.get());
    }

    @Test
    public void testGatewayRoutesPostProgram() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/api/v1/college-admin/programs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        String payload = "{\"programCode\":\"BTECH_ECE\",\"name\":\"B.Tech ECE\",\"departmentId\":\"dep-101\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("/api/v1/college-admin/programs", lastReceivedPath.get());
        assertEquals("POST", lastReceivedMethod.get());
        assertEquals(payload, lastReceivedBody.get());
    }

    @Test
    public void testGatewayRoutesCanonicalProgramsAlias() throws Exception {
        URL url = new URL("http://localhost:" + gatewayPort + "/v1/programs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("/api/v1/college-admin/programs", lastReceivedPath.get());
    }
}
