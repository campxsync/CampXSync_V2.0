package com.campx.gateway.config;

import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * STM-07 — student-role IAM routes are unreachable through the gateway without authentication.
 */
public class StudentRoleGatewayAuthTest {

    private static HttpServer mockDownstream;
    private static GatewayServer gateway;
    private static int gatewayPort;
    private static String prevInternalAuth;
    private static String prevJwtIssuer;

    @BeforeClass
    public static void start() throws Exception {
        prevInternalAuth = System.getProperty("campx.internal.auth.enabled");
        prevJwtIssuer = System.getProperty("campx.supabase.jwt.issuer");

        mockDownstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int downPort = mockDownstream.getAddress().getPort();
        mockDownstream.createContext("/api/v1/admin/internal/roles", exchange -> {
            byte[] ok = "{\"status\":\"SHOULD_NOT_REACH\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });
        mockDownstream.start();

        System.setProperty("campx.internal.auth.enabled", "true");
        System.setProperty("campx.supabase.jwt.issuer", "http://127.0.0.1:1");

        GatewayConfig config = new GatewayConfig();
        config.setPort(0);
        config.addRoute("/api/v1/admin/internal/roles",
                "http://127.0.0.1:" + downPort + "/api/v1/admin/internal/roles");
        gateway = new GatewayServer(config);
        gateway.start();
        gatewayPort = gateway.getPort();
    }

    @AfterClass
    public static void stop() {
        if (gateway != null) {
            gateway.stop();
        }
        if (mockDownstream != null) {
            mockDownstream.stop(0);
        }
        restore("campx.internal.auth.enabled", prevInternalAuth);
        restore("campx.supabase.jwt.issuer", prevJwtIssuer);
        CampXLoggerFactory.flush();
    }

    private static void restore(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    public void studentRoleAssignUnreachableWithoutAuthentication() throws Exception {
        URL url = new URL("http://127.0.0.1:" + gatewayPort
                + "/api/v1/admin/internal/roles/student-assign");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type", "application/json");
        // Avoid fixed-length streaming so 401 error body is readable on JDK 8
        byte[] body = "{\"userId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"roleCode\":\"R25\"}"
                .getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int status = conn.getResponseCode();
        assertEquals(401, status);
        String resp = readResponse(conn);
        assertTrue("Expected GATEWAY_MISSING_AUTH in body: " + resp,
                resp.contains("GATEWAY_MISSING_AUTH"));
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream in = conn.getErrorStream();
        if (in == null) {
            try {
                in = conn.getInputStream();
            } catch (Exception e) {
                return "";
            }
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] data = new byte[512];
        int n;
        while ((n = in.read(data)) != -1) {
            buf.write(data, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
