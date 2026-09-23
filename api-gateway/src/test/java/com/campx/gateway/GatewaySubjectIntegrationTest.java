package com.campx.gateway;

import com.campx.academic.subject.server.SubjectServer;
import com.campx.academic.subject.service.SubjectDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying unified API Gateway reverse proxy routing,
 * correlation tracking, and error pass-through to ACD-03 Subject Management Service.
 */
public class GatewaySubjectIntegrationTest {

    private static SubjectServer subjectServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8099;
    private static final int SUB_PORT = 8098;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-03 Subject Management Service on port 8098
        subjectServer = new SubjectServer(SUB_PORT, new SubjectDomainService());
        subjectServer.start();

        // 2. Start Gateway on port 8099 configured to proxy to subjectServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/subjects", "http://localhost:" + SUB_PORT + "/api/v1/subjects");
        config.addRoute("/api/v1/academics/subjects", "http://localhost:" + SUB_PORT + "/api/v1/academics/subjects");
        config.addRoute("/v1/subjects", "http://localhost:" + SUB_PORT + "/api/v1/subjects");
        config.addRoute("/v1/subject-catalog", "http://localhost:" + SUB_PORT + "/api/v1/academics/subjects/catalog");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (subjectServer != null) subjectServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * Tests proxying subject creation via API Gateway through /api/v1/academics/subjects.
     * Verifies HTTP 201 Created and correlation tracing header propagation.
     */
    @Test
    public void testRouteCreateSubjectViaGateway() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"GW-SUB-101\","
                + "\"name\":\"Distributed Systems\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"campusId\":\"MAIN\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0,"
                + "\"elective\":false,"
                + "\"academicYear\":\"2026-2027\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-SUB-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-GW-SUB-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("GW-SUB-101"));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    /**
     * Tests transparent error pass-through (HTTP 409 Conflict) through API Gateway.
     */
    @Test
    public void testRouteDuplicateSubjectCodeErrorPassThrough() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"GW-DUP-001\","
                + "\"name\":\"Network Security\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}";

        // First creation succeeds
        URL url1 = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn1 = (HttpURLConnection) url1.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        conn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // Duplicate creation yields 409 Conflict propagated transparently
        URL url2 = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("X-Trace-Id", "TRACE-GW-SUB-409");
        conn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn2.getResponseCode());
        assertEquals("TRACE-GW-SUB-409", conn2.getHeaderField("X-Trace-Id"));
        String err = readResponse(conn2);
        assertTrue(err.contains("ACD_SUBJECT_CODE_DUPLICATE"));
    }

    /**
     * Tests canonical alias routing: GET /v1/subject-catalog proxies to /api/v1/academics/subjects/catalog.
     */
    @Test
    public void testRouteCanonicalSubjectCatalogAlias() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/subject-catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "STUDENT");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
