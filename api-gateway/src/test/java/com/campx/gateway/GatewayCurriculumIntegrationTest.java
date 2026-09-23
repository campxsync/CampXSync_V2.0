package com.campx.gateway;

import com.campx.academic.curriculum.server.CurriculumServer;
import com.campx.academic.curriculum.service.CurriculumDomainService;
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
 * correlation tracking, and error pass-through to ACD-02 Curriculum Management Service.
 */
public class GatewayCurriculumIntegrationTest {

    private static CurriculumServer curriculumServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8095;
    private static final int CURR_PORT = 8094;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-02 Curriculum Management Service on port 8094
        curriculumServer = new CurriculumServer(CURR_PORT, new CurriculumDomainService());
        curriculumServer.start();

        // 2. Start Gateway on port 8095 configured to proxy to curriculumServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/curricula", "http://localhost:" + CURR_PORT + "/api/v1/curricula");
        config.addRoute("/api/v1/academics/curricula", "http://localhost:" + CURR_PORT + "/api/v1/academics/curricula");
        config.addRoute("/v1/curricula", "http://localhost:" + CURR_PORT + "/api/v1/curricula");
        config.addRoute("/v1/curriculum-catalog", "http://localhost:" + CURR_PORT + "/api/v1/curricula/active");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (curriculumServer != null) curriculumServer.stop();
        CampXLoggerFactory.flush();
    }

    @Test
    public void testRouteCreateCurriculumViaGateway() throws Exception {
        String payload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"NEP\","
                + "\"academicYear\":\"2026-2027\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"campusId\":\"MAIN\","
                + "\"name\":\"MCA NEP Curriculum 2026\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-CURR-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GW-CURR-001", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
    }

    @Test
    public void testRouteCatalogShortAliasViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/curriculum-catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-CATALOG-002");

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GW-CATALOG-002", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"activeCurricula\":["));
    }

    @Test
    public void testDownstreamForbiddenPassThroughViaGateway() throws Exception {
        String payload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"CBCS\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-FORBIDDEN-003");
        conn.setRequestProperty("X-User-Role", "STUDENT");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(403, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GW-FORBIDDEN-003", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"errorCode\":\"ACD2_FORBIDDEN\""));
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
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
