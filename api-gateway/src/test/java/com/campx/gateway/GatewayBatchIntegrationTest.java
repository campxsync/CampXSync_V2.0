package com.campx.gateway;

import com.campx.academic.batch.model.BatchModels.*;
import com.campx.academic.batch.server.BatchServer;
import com.campx.academic.batch.service.BatchDomainService;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying unified API Gateway reverse proxy routing,
 * correlation tracking, and error pass-through to ACD-04 Batch Management Service.
 */
public class GatewayBatchIntegrationTest {

    private static BatchServer batchServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8095;
    private static final int BATCH_PORT = 8094;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-04 Batch Management Service on port 8094
        batchServer = new BatchServer(BATCH_PORT, new BatchDomainService());
        batchServer.start();

        // 2. Start Gateway on port 8095 configured to proxy to batchServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/batches/metrics", "http://localhost:" + BATCH_PORT + "/metrics");
        config.addRoute("/api/v1/academics/batches/metrics", "http://localhost:" + BATCH_PORT + "/metrics");
        config.addRoute("/api/v1/batches", "http://localhost:" + BATCH_PORT + "/api/v1/academics/batches");
        config.addRoute("/api/v1/academics/batches", "http://localhost:" + BATCH_PORT + "/api/v1/academics/batches");
        config.addRoute("/v1/batches", "http://localhost:" + BATCH_PORT + "/api/v1/academics/batches");
        config.addRoute("/v1/batch-catalog", "http://localhost:" + BATCH_PORT + "/api/v1/academics/batches");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (batchServer != null) batchServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * Tests proxying batch creation via API Gateway through /api/v1/academics/batches.
     * Verifies HTTP 201 Created and correlation tracing header propagation.
     */
    @Test
    public void testRouteCreateBatchViaGateway() throws Exception {
        String payload = "{"
                + "\"batchCode\":\"GW-BATCH-101\","
                + "\"name\":\"Distributed Computing Batch\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"departmentId\":\"DEP-CS\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":50"
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/batches");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-BATCH-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-GW-BATCH-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"batchCode\":\"GW-BATCH-101\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
    }

    /**
     * Tests canonical route prefix /v1/batch-catalog dispatching through API Gateway.
     */
    @Test
    public void testCanonicalRouteBatchCatalogViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/batch-catalog?courseId=CRS-CS101");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-BATCH-CAT");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-GW-BATCH-CAT", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("data"));
    }

    /**
     * Tests full end-to-end lifecycle and roster enrollment through API Gateway.
     */
    @Test
    public void testBatchLifecycleAndRosterThroughGateway() throws Exception {
        // 1. Create Batch via Gateway
        String createPayload = "{"
                + "\"batchCode\":\"GW-ROSTER-FLOW-01\","
                + "\"name\":\"Roster Flow Cohort\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":5"
                + "}";

        URL createUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/batches");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        createConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        String batchId = extractField(createResp, "batchId");
        assertNotNull(batchId);

        // 2. Open Batch via Gateway
        URL openUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/batches/" + batchId + "/open");
        HttpURLConnection openConn = (HttpURLConnection) openUrl.openConnection();
        openConn.setRequestMethod("POST");
        openConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, openConn.getResponseCode());

        // 3. Add Student via Gateway
        URL addStuUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/batches/" + batchId + "/students");
        HttpURLConnection addStuConn = (HttpURLConnection) addStuUrl.openConnection();
        addStuConn.setRequestMethod("POST");
        addStuConn.setDoOutput(true);
        addStuConn.setRequestProperty("Content-Type", "application/json");
        addStuConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        String stuPayload = "{\"studentId\":\"STU-1001\",\"effectiveFrom\":\"2024-09-01\"}";
        try (OutputStream os = addStuConn.getOutputStream()) {
            os.write(stuPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, addStuConn.getResponseCode());

        // 4. Query Roster via Gateway
        URL rosterUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/batches/" + batchId + "/roster");
        HttpURLConnection rosterConn = (HttpURLConnection) rosterUrl.openConnection();
        rosterConn.setRequestMethod("GET");
        rosterConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, rosterConn.getResponseCode());
        String rosterResp = readResponse(rosterConn);
        assertTrue(rosterResp.contains("STU-1001"));

        // 5. Close Batch via Gateway
        URL closeUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/batches/" + batchId + "/close");
        HttpURLConnection closeConn = (HttpURLConnection) closeUrl.openConnection();
        closeConn.setRequestMethod("POST");
        closeConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, closeConn.getResponseCode());

        // 6. Enrollment after closure must be rejected with 409
        HttpURLConnection closedAddConn = (HttpURLConnection) addStuUrl.openConnection();
        closedAddConn.setRequestMethod("POST");
        closedAddConn.setDoOutput(true);
        closedAddConn.setRequestProperty("Content-Type", "application/json");
        closedAddConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        String stu2Payload = "{\"studentId\":\"STU-1002\",\"effectiveFrom\":\"2024-09-01\"}";
        try (OutputStream os = closedAddConn.getOutputStream()) {
            os.write(stu2Payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(409, closedAddConn.getResponseCode());
        String errResp = readResponse(closedAddConn);
        assertTrue(errResp.contains("\"code\":\"ACD_BATCH_CLOSED\""));
    }

    /**
     * Tests metrics scraping through API Gateway reverse proxy.
     */
    @Test
    public void testMetricsScrapeThroughGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/batches/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("acd04_request_total"));
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

    private String extractField(String json, String key) {
        String pattern = "\"" + key + "\":\"([^\"]+)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
