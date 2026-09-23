package com.campx.academic.subject;

import com.campx.academic.subject.model.SubjectModels.Subject;
import com.campx.academic.subject.server.SubjectServer;
import com.campx.academic.subject.service.SubjectDomainService;
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
 * End-to-End HTTP REST Integration tests for ACD-03 Subject Management Service.
 * Validates request/response envelopes, HTTP status codes, error taxonomy, RBAC, and idempotency.
 */
public class SubjectControllerIntegrationTest {

    private static SubjectServer server;
    private static SubjectDomainService domainService;
    private static final int TEST_PORT = 8097;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeClass
    public static void startServer() throws Exception {
        domainService = new SubjectDomainService();
        server = new SubjectServer(TEST_PORT, domainService);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    /**
     * Story 3: POST /api/v1/academics/subjects creates active subject with initial version=1.
     */
    @Test
    public void testCreateSubjectViaHttp() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"SUB-TEST-101\","
                + "\"name\":\"Advanced Database Systems\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"campusId\":\"MAIN\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0,"
                + "\"elective\":false,"
                + "\"academicYear\":\"2026-2027\""
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/subjects");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-SUB-HTTP-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-SUB-HTTP-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"subjectCode\":\"SUB-TEST-101\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
        assertTrue(resp.contains("\"version\":1"));
        assertTrue(resp.contains("\"meta\":{"));
    }

    /**
     * Story 4: Duplicate subject code returns 409 Conflict.
     */
    @Test
    public void testDuplicateSubjectCodeReturns409() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"SUB-DUP-01\","
                + "\"name\":\"Algorithms\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}";

        // First creation succeeds
        postHttp("/api/v1/academics/subjects", payload, "ACADEMIC_ADMIN", 201);

        // Second creation with same code returns 409
        URL url = new URL(BASE_URL + "/api/v1/academics/subjects");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn.getResponseCode());
        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"success\":false"));
        assertTrue(errResp.contains("ACD_SUBJECT_CODE_DUPLICATE"));
    }

    /**
     * Story 7, 16: Published Subject Catalog & Role-Based Access (Students and Parents).
     */
    @Test
    public void testPublishedCatalogAndParentAccess() throws Exception {
        // Create an active subject
        String payload = "{"
                + "\"subjectCode\":\"SUB-CATALOG-1\","
                + "\"name\":\"Digital Electronics\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}";
        postHttp("/api/v1/academics/subjects", payload, "ACADEMIC_ADMIN", 201);

        // Parent role accessing catalog gets 200 OK (Story 60)
        URL url = new URL(BASE_URL + "/api/v1/academics/subjects/catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "PARENT");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("SUB-CATALOG-1"));
    }

    /**
     * Story 59: RBAC Unauthorized modification returns 403 Forbidden.
     */
    @Test
    public void testUnauthorizedMutationReturns403() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"SUB-HACK\","
                + "\"name\":\"Hacked Subject\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/subjects");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", "STUDENT"); // Student cannot create

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String err = readResponse(conn);
        assertTrue(err.contains("ACD_FORBIDDEN"));
    }

    /**
     * Story 61: Auditor role has read-only access to subject history.
     */
    @Test
    public void testAuditorHistoryAccess() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"SUB-AUDIT-1\","
                + "\"name\":\"Software Quality Assurance\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}";
        String createdJson = postHttp("/api/v1/academics/subjects", payload, "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(createdJson, "subjectId");

        // Auditor querying history
        URL url = new URL(BASE_URL + "/api/v1/academics/subjects/" + subjectId + "/history");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "AUDITOR");

        assertEquals(200, conn.getResponseCode());
        String histResp = readResponse(conn);
        assertTrue(histResp.contains("\"success\":true"));
        assertTrue(histResp.contains("CREATE"));
    }

    /**
     * Story 62: External API Key Authentication (read-only allowed, mutation rejected).
     */
    @Test
    public void testExternalApiKeyAccess() throws Exception {
        // GET with valid API Key returns 200
        URL getUrl = new URL(BASE_URL + "/api/v1/academics/subjects/catalog");
        HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
        getConn.setRequestMethod("GET");
        getConn.setRequestProperty("X-API-Key", "campx_test_key_123");

        assertEquals(200, getConn.getResponseCode());

        // POST with API Key returns 403 (Read-only access enforced)
        URL postUrl = new URL(BASE_URL + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        postConn.setRequestProperty("X-API-Key", "campx_test_key_123");

        try (OutputStream os = postConn.getOutputStream()) {
            os.write("{}".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, postConn.getResponseCode());
    }

    /**
     * Story 8, 80: Hard deletion of referenced subject rejected with 409 Conflict.
     */
    @Test
    public void testDeleteReferencedSubjectReturns409() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"SUB-DEL-1\","
                + "\"name\":\"Networks\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}";
        String created = postHttp("/api/v1/academics/subjects", payload, "ACADEMIC_ADMIN", 201);
        String subId = extractField(created, "subjectId");

        // Mark subject as active in curriculum
        domainService.addReferencedSubject(subId);

        // Attempt DELETE
        URL delUrl = new URL(BASE_URL + "/api/v1/academics/subjects/" + subId);
        HttpURLConnection delConn = (HttpURLConnection) delUrl.openConnection();
        delConn.setRequestMethod("DELETE");
        delConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(409, delConn.getResponseCode());
        String err = readResponse(delConn);
        assertTrue(err.contains("ACD_SUBJECT_REFERENCED"));

        // Deactivation should succeed instead
        URL deactUrl = new URL(BASE_URL + "/api/v1/academics/subjects/" + subId + "/deactivate");
        HttpURLConnection deactConn = (HttpURLConnection) deactUrl.openConnection();
        deactConn.setRequestMethod("POST");
        deactConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, deactConn.getResponseCode());
    }

    /**
     * Health and Metrics endpoints.
     */
    @Test
    public void testHealthAndMetricsEndpoints() throws Exception {
        URL healthUrl = new URL(BASE_URL + "/actuator/health");
        HttpURLConnection healthConn = (HttpURLConnection) healthUrl.openConnection();
        assertEquals(200, healthConn.getResponseCode());
        assertTrue(readResponse(healthConn).contains("\"status\":\"UP\""));

        URL metricsUrl = new URL(BASE_URL + "/metrics");
        HttpURLConnection metricsConn = (HttpURLConnection) metricsUrl.openConnection();
        assertEquals(200, metricsConn.getResponseCode());
        String metrics = readResponse(metricsConn);
        assertTrue(metrics.contains("acd03_request_total"));
        assertTrue(metrics.contains("acd03_subjects_total"));
    }

    // Helper utilities
    private String postHttp(String path, String payload, String role, int expectedStatus) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", role);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(expectedStatus, conn.getResponseCode());
        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private String extractField(String json, String field) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }
}
