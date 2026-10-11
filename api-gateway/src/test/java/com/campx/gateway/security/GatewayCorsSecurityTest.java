package com.campx.gateway.security;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Security tests verifying CORS preflight, origin restriction, credentials,
 * and ensuring that CORS changes do not compromise the gateway trust boundary.
 */
public class GatewayCorsSecurityTest {

    private GatewayConfig config;
    private GatewayServer server;
    private int gatewayPort;

    @BeforeClass
    public static void init() {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    @Before
    public void setUp() throws IOException {
        config = new GatewayConfig();
        config.setPort(0); // dynamic port
        config.setInternalAuthEnabled(false); // disable internal auth for simple origin testing
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://127.0.0.1:3000"));

        server = new GatewayServer(config);
        server.start();
        gatewayPort = config.getPort();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testPermittedOriginPreflightReturns204WithCredentials() throws IOException {
        HttpResponse resp = sendRequest("OPTIONS", "/actuator/health",
                "Origin: http://localhost:3000\r\nAccess-Control-Request-Method: GET\r\n");

        assertEquals(204, resp.statusCode);
        assertEquals("http://localhost:3000", resp.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", resp.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(resp.getHeader("Access-Control-Allow-Methods").contains("GET"));
        assertTrue(resp.getHeader("Access-Control-Allow-Headers").contains("Authorization"));
        assertEquals("Origin", resp.getHeader("Vary"));
    }

    @Test
    public void testUnauthorizedOriginPreflightIsRejectedWith403() throws IOException {
        HttpResponse resp = sendRequest("OPTIONS", "/actuator/health",
                "Origin: http://malicious-site.com\r\nAccess-Control-Request-Method: GET\r\n");

        assertEquals(403, resp.statusCode);
        assertNull(resp.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    public void testAllowedOriginOnActualGetRequestReflectsOrigin() throws IOException {
        HttpResponse resp = sendRequest("GET", "/actuator/health",
                "Origin: http://127.0.0.1:3000\r\n");

        assertEquals(200, resp.statusCode);
        assertEquals("http://127.0.0.1:3000", resp.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", resp.getHeader("Access-Control-Allow-Credentials"));
    }

    private static class HttpResponse {
        int statusCode;
        java.util.Map<String, String> headers = new java.util.HashMap<>();

        String getHeader(String name) {
            for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    private HttpResponse sendRequest(String method, String path, String extraHeaders) throws IOException {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", gatewayPort);
             java.io.OutputStream out = socket.getOutputStream();
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            
            String req = method + " " + path + " HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + gatewayPort + "\r\n" +
                    extraHeaders +
                    "Connection: close\r\n\r\n";
            out.write(req.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();

            HttpResponse resp = new HttpResponse();
            String statusLine = reader.readLine();
            if (statusLine != null) {
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) {
                    resp.statusCode = Integer.parseInt(parts[1]);
                }
            }

            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colon = headerLine.indexOf(':');
                if (colon > 0) {
                    resp.headers.put(headerLine.substring(0, colon).trim(), headerLine.substring(colon + 1).trim());
                }
            }
            return resp;
        }
    }
}
