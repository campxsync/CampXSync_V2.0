package com.campx.academic.batch;

import com.campx.academic.batch.model.BatchModels.*;
import com.campx.academic.batch.server.BatchServer;
import com.campx.academic.batch.service.BatchDomainService;
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

import static org.junit.Assert.*;

/**
 * End-to-End HTTP REST Integration tests for ACD-04 Batch Management Service.
 * Validates request/response envelopes, HTTP status codes, error taxonomy, RBAC,
 * lifecycle endpoints, capacity overrides, sections, split/merge requests, and idempotency.
 */
public class BatchControllerIntegrationTest {

    private static BatchServer server;
    private static BatchDomainService domainService;
    private static final int TEST_PORT = 8096;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeClass
    public static void startServer() throws Exception {
        domainService = new BatchDomainService();
        server = new BatchServer(TEST_PORT, domainService);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        URL url = new URL(BASE_URL + "/actuator/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"UP\""));
        assertTrue(resp.contains("ACD-04-BatchManagementService"));
    }

    @Test
    public void testMetricsEndpoint() throws Exception {
        URL url = new URL(BASE_URL + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("acd04_request_total"));
        assertTrue(resp.contains("acd04_batch_count"));
        assertTrue(resp.contains("acd04_roster_reconciliation_mismatches"));
    }

    @Test
    public void testCreateBatchViaHttp() throws Exception {
        String payload = "{"
                + "\"batchCode\":\"HTTP-CS-01\","
                + "\"name\":\"Computer Science Batch 2024\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"departmentId\":\"DEP-CS\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":50"
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/batches");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-BATCH-HTTP-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-BATCH-HTTP-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"batchCode\":\"HTTP-CS-01\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
        assertTrue(resp.contains("\"rosterCount\":0"));
        assertTrue(resp.contains("\"capacity\":50"));
        assertTrue(resp.contains("\"meta\":{"));
    }

    @Test
    public void testDuplicateBatchCodeReturns409() throws Exception {
        String payload = "{"
                + "\"batchCode\":\"HTTP-DUP-01\","
                + "\"name\":\"Batch Original\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":50"
                + "}";

        // 1. Create first batch
        URL url = new URL(BASE_URL + "/api/v1/academics/batches");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        conn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // 2. Second create with same code must return 409
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(409, conn2.getResponseCode());
        String errResp = readResponse(conn2);
        assertTrue(errResp.contains("\"code\":\"ACD_BATCH_CODE_DUPLICATE\""));
    }

    @Test
    public void testInactiveCourseReturns422() throws Exception {
        String payload = "{"
                + "\"batchCode\":\"HTTP-CRS-INVAL\","
                + "\"name\":\"Invalid Course Batch\","
                + "\"courseId\":\"CRS-INACTIVE\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":50"
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/batches");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(422, conn.getResponseCode());
        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"code\":\"ACD_BATCH_COURSE_INVALID\""));
    }

    @Test
    public void testFullLifecycleAndRosterViaHttp() throws Exception {
        // 1. Create Draft Batch
        String createPayload = "{"
                + "\"batchCode\":\"HTTP-LC-01\","
                + "\"name\":\"Lifecycle Batch\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":2"
                + "}";

        URL createUrl = new URL(BASE_URL + "/api/v1/academics/batches");
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

        // 2. Open / Activate Batch
        URL openUrl = new URL(BASE_URL + "/api/v1/academics/batches/" + batchId + "/open");
        HttpURLConnection openConn = (HttpURLConnection) openUrl.openConnection();
        openConn.setRequestMethod("POST");
        openConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, openConn.getResponseCode());
        String openResp = readResponse(openConn);
        assertTrue(openResp.contains("\"status\":\"ACTIVE\""));

        // 3. Add Section A
        URL secUrl = new URL(BASE_URL + "/api/v1/academics/batches/" + batchId + "/sections");
        HttpURLConnection secConn = (HttpURLConnection) secUrl.openConnection();
        secConn.setRequestMethod("POST");
        secConn.setDoOutput(true);
        secConn.setRequestProperty("Content-Type", "application/json");
        secConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        String secPayload = "{\"sectionCode\":\"A\",\"name\":\"Section Alpha\",\"capacity\":30}";
        try (OutputStream os = secConn.getOutputStream()) {
            os.write(secPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, secConn.getResponseCode());

        // 4. Enroll Student STU-1001
        URL stuUrl = new URL(BASE_URL + "/api/v1/academics/batches/" + batchId + "/students");
        HttpURLConnection stuConn = (HttpURLConnection) stuUrl.openConnection();
        stuConn.setRequestMethod("POST");
        stuConn.setDoOutput(true);
        stuConn.setRequestProperty("Content-Type", "application/json");
        stuConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        String stuPayload = "{\"studentId\":\"STU-1001\",\"effectiveFrom\":\"2024-09-01\"}";
        try (OutputStream os = stuConn.getOutputStream()) {
            os.write(stuPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, stuConn.getResponseCode());

        // 5. Get Roster
        URL rosterUrl = new URL(BASE_URL + "/api/v1/academics/batches/" + batchId + "/roster");
        HttpURLConnection rosterConn = (HttpURLConnection) rosterUrl.openConnection();
        rosterConn.setRequestMethod("GET");
        rosterConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, rosterConn.getResponseCode());
        String rosterResp = readResponse(rosterConn);
        assertTrue(rosterResp.contains("STU-1001"));

        // 6. Close Batch
        URL closeUrl = new URL(BASE_URL + "/api/v1/academics/batches/" + batchId + "/close");
        HttpURLConnection closeConn = (HttpURLConnection) closeUrl.openConnection();
        closeConn.setRequestMethod("POST");
        closeConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, closeConn.getResponseCode());

        // 7. Enrolling on Closed Batch must return 409 ACD_BATCH_CLOSED
        HttpURLConnection enrollClosedConn = (HttpURLConnection) stuUrl.openConnection();
        enrollClosedConn.setRequestMethod("POST");
        enrollClosedConn.setDoOutput(true);
        enrollClosedConn.setRequestProperty("Content-Type", "application/json");
        enrollClosedConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        String stu2Payload = "{\"studentId\":\"STU-1002\",\"effectiveFrom\":\"2024-09-01\"}";
        try (OutputStream os = enrollClosedConn.getOutputStream()) {
            os.write(stu2Payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(409, enrollClosedConn.getResponseCode());
        String closedErr = readResponse(enrollClosedConn);
        assertTrue(closedErr.contains("\"code\":\"ACD_BATCH_CLOSED\""));
    }

    @Test
    public void testIdempotentRequestReplay() throws Exception {
        String payload = "{"
                + "\"batchCode\":\"HTTP-IDEM-01\","
                + "\"name\":\"Idempotent Batch\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":50"
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/batches");

        // 1st request with Idempotency-Key
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        conn1.setRequestProperty("Idempotency-Key", "IDEM-KEY-UNIQUE-999");
        conn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());
        String resp1 = readResponse(conn1);

        // 2nd request with exact same Idempotency-Key -> cached replay
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("Idempotency-Key", "IDEM-KEY-UNIQUE-999");
        conn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn2.getResponseCode());
        assertEquals("true", conn2.getHeaderField("X-Idempotent-Replay"));
        String resp2 = readResponse(conn2);
        assertEquals(resp1, resp2);
    }

    @Test
    public void testRbacRejection() throws Exception {
        String payload = "{"
                + "\"batchCode\":\"HTTP-RBAC-01\","
                + "\"name\":\"Unauthorized Batch\","
                + "\"courseId\":\"CRS-CS101\","
                + "\"academicYear\":\"2024-2025\","
                + "\"semesterNo\":1,"
                + "\"capacity\":50"
                + "}";

        // Student role attempting to create batch -> 403 Forbidden
        URL url = new URL(BASE_URL + "/api/v1/academics/batches");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", "STUDENT");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String err = readResponse(conn);
        assertTrue(err.contains("\"code\":\"ACD_FORBIDDEN\""));
        assertTrue(err.contains("\"errorCode\":\"ACD_FORBIDDEN\""));
    }

    @Test
    public void testCourseDeactivatedEventWithIdFallbackViaHttp() throws Exception {
        // Event payload with legacy "id" instead of "courseId" (GAP-02)
        String eventPayload = "{\"id\":\"CRS-MATH201\",\"eventId\":\"EVT-DEACT-HTTP-01\"}";
        URL url = new URL(BASE_URL + "/api/v1/academics/batches/events/course-deactivated");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(eventPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"ACK\""));
        assertTrue(resp.contains("CRS-MATH201"));
    }

    @Test
    public void testCurriculumPublishedAndRetiredEventViaHttp() throws Exception {
        // Publish event (GAP-08)
        String pubPayload = "{\"curriculumId\":\"CURR-HTTP-2026\",\"eventId\":\"EVT-PUB-HTTP-01\"}";
        URL pubUrl = new URL(BASE_URL + "/api/v1/academics/batches/events/curriculum-published");
        HttpURLConnection conn = (HttpURLConnection) pubUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(pubPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, conn.getResponseCode());
        String pubResp = readResponse(conn);
        assertTrue(pubResp.contains("\"status\":\"ACK\""));
        assertTrue(pubResp.contains("CURR-HTTP-2026"));

        // Retire event (GAP-08)
        String retPayload = "{\"curriculumId\":\"CURR-HTTP-2026\",\"eventId\":\"EVT-RET-HTTP-01\"}";
        URL retUrl = new URL(BASE_URL + "/api/v1/academics/batches/events/curriculum-retired");
        HttpURLConnection retConn = (HttpURLConnection) retUrl.openConnection();
        retConn.setRequestMethod("POST");
        retConn.setDoOutput(true);
        retConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = retConn.getOutputStream()) {
            os.write(retPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, retConn.getResponseCode());
        String retResp = readResponse(retConn);
        assertTrue(retResp.contains("\"status\":\"ACK\""));
    }

    @Test
    public void testCorrelationIdHeaderAliasSymmetry() throws Exception {
        URL url = new URL(BASE_URL + "/actuator/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Correlation-Id", "CORR-ALIAS-TEST-999");
        assertEquals(200, conn.getResponseCode());
        assertEquals("CORR-ALIAS-TEST-999", conn.getHeaderField("X-Correlation-Id"));
        assertEquals("CORR-ALIAS-TEST-999", conn.getHeaderField("X-Trace-Id"));
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
