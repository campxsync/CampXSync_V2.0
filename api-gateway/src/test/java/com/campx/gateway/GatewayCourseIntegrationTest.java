package com.campx.gateway;

import com.campx.academic.course.server.CourseServer;
import com.campx.academic.course.service.CourseDomainService;
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
 * correlation tracking, and RFC 7807 error pass-through to ACD-01 Course Management Service.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayCourseIntegrationTest {

    private static CourseServer courseServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8084;
    private static final int CRS_PORT = 8083;

    /**
     * Boots the ACD-01 Course Management Service and the API Gateway reverse proxy.
     *
     * @throws Exception If service startup fails.
     */
    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-01 Course Management Service on port 8083
        courseServer = new CourseServer(CRS_PORT, new CourseDomainService());
        courseServer.start();

        // 2. Start Gateway on port 8084 configured to proxy to courseServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/courses", "http://localhost:" + CRS_PORT + "/api/v1/courses");
        config.addRoute("/api/v1/academics/courses", "http://localhost:" + CRS_PORT + "/api/v1/academics/courses");
        config.addRoute("/v1/courses", "http://localhost:" + CRS_PORT + "/api/v1/courses");
        config.addRoute("/v1/course-catalog", "http://localhost:" + CRS_PORT + "/api/v1/courses/catalog");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    /**
     * Stops both backend and gateway test servers and flushes logs.
     */
    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (courseServer != null) courseServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * Verifies that course creation requests routed through the API Gateway
     * succeed with HTTP 201 Created and return matching correlation tokens.
     *
     * @throws Exception If HTTP request fails.
     */
    @Test
    public void testRouteCreateCourseViaGateway() throws Exception {
        String payload = "{"
                + "\"courseCode\":\"GW_CRS_01\","
                + "\"courseName\":\"Cloud Computing Architecture\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"totalCredits\":4.0,"
                + "\"durationYears\":1"
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-COURSE-001");
        conn.setRequestProperty("X-Tenant-Id", "CAMPUS_ALPHA");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GW-COURSE-001", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"courseCode\":\"GW_CRS_01\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
    }

    /**
     * Verifies that canonical short alias routes ({@code /v1/course-catalog})
     * proxy successfully to the downstream service endpoint.
     *
     * @throws Exception If HTTP request fails.
     */
    @Test
    public void testRouteCatalogShortAliasViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/course-catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-CATALOG-001");

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GW-CATALOG-001", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"catalog\":["));
    }

    /**
     * Verifies that downstream domain conflict exceptions (HTTP 409) pass
     * through the Gateway intact with preserved error codes and trace IDs.
     *
     * @throws Exception If HTTP request fails.
     */
    @Test
    public void testDownstreamConflictErrorPassThroughViaGateway() throws Exception {
        String payload = "{"
                + "\"courseCode\":\"GW_DUP_01\","
                + "\"courseName\":\"Duplicate Course Via Gateway\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"totalCredits\":3.0"
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/courses");

        // 1. First creation succeeds
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        conn1.setRequestProperty("X-Trace-Id", "TRACE-GW-DUP-INIT");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // 2. Second creation with duplicate code returns 409 Conflict via Gateway
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("X-Trace-Id", "TRACE-GW-DUP-CONFLICT");

        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn2.getResponseCode();
        assertEquals(409, code);

        String traceHeader = conn2.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GW-DUP-CONFLICT", traceHeader);

        String resp = readResponse(conn2);
        assertTrue(resp.contains("\"status\":409"));
        assertTrue(resp.contains("\"errorCode\":\"ACD_COURSE_CODE_EXISTS\""));
        assertTrue(resp.contains("\"error\":\"Conflict\""));
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
