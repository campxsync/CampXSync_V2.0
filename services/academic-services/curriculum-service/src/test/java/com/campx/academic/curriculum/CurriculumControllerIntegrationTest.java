package com.campx.academic.curriculum;

import com.campx.academic.curriculum.controller.CurriculumController;
import com.campx.academic.curriculum.model.CurriculumModels.DataClassification;
import com.campx.academic.curriculum.server.CurriculumServer;
import com.campx.academic.curriculum.service.CurriculumDomainService;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End HTTP REST Integration tests for ACD-02 Curriculum Management Service.
 * Validates request/response envelopes, HTTP status codes, error codes, and RBAC over port 8096.
 */
public class CurriculumControllerIntegrationTest {

    private static CurriculumServer server;
    private static final int TEST_PORT = 8096;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeClass
    public static void startServer() throws Exception {
        server = new CurriculumServer(TEST_PORT, new CurriculumDomainService());
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
     * Tests HTTP POST /api/v1/academics/curricula creating a draft curriculum.
     * Verifies HTTP 201 Created, trace correlation headers, and envelope metadata.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testCreateCurriculumViaHttp() throws Exception {
        String payload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"CBCS\","
                + "\"academicYear\":\"2026-2027\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"campusId\":\"MAIN\","
                + "\"name\":\"MCA Curriculum 2026-27\""
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-CURR-HTTP-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-CURR-HTTP-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
        assertTrue(resp.contains("\"version\":1"));
        assertTrue(resp.contains("\"meta\":{"));
    }

    /**
     * Tests mapping duplicate subjects to the same semester (BR-08).
     * Verifies HTTP 409 Conflict with error code ACD2_DUPLICATE_MAPPING.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testDuplicateSubjectMappingReturns409() throws Exception {
        // 1. Create a curriculum
        String createPayload = "{"
                + "\"courseId\":\"CS101\","
                + "\"academicPattern\":\"NEP\","
                + "\"academicYear\":\"2026-2027\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"campusId\":\"CAMPUS_MAIN\","
                + "\"name\":\"Computer Science Curriculum\""
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-DUP-TEST-001");
        conn.setRequestProperty("X-Tenant-Id", "CAMPUS_MAIN");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn.getResponseCode());
        String createResp = readResponse(conn);
        String curriculumId = extractId(createResp);

        // 2. Add semester 1
        String semPayload = "{\"semesterNo\":1,\"name\":\"Semester 1\"}";
        URL semUrl = new URL(BASE_URL + "/api/v1/academics/curricula/" + curriculumId + "/versions/1/semesters");
        HttpURLConnection semConn = (HttpURLConnection) semUrl.openConnection();
        semConn.setRequestMethod("POST");
        semConn.setDoOutput(true);
        semConn.setRequestProperty("Content-Type", "application/json");
        semConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = semConn.getOutputStream()) {
            os.write(semPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, semConn.getResponseCode());

        // 3. Map subject SUB-201
        String mapPayload = "{\"subjectId\":\"SUB-201\",\"semesterNo\":1,\"credits\":4.0}";
        URL mapUrl = new URL(BASE_URL + "/api/v1/academics/curricula/" + curriculumId + "/versions/1/subjects");
        HttpURLConnection mapConn1 = (HttpURLConnection) mapUrl.openConnection();
        mapConn1.setRequestMethod("POST");
        mapConn1.setDoOutput(true);
        mapConn1.setRequestProperty("Content-Type", "application/json");
        mapConn1.setRequestProperty("X-Tenant-Id", "CAMPUS_MAIN");
        mapConn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = mapConn1.getOutputStream()) {
            os.write(mapPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, mapConn1.getResponseCode());

        // 4. Map same subject again -> Expected 409 Conflict
        HttpURLConnection mapConn2 = (HttpURLConnection) mapUrl.openConnection();
        mapConn2.setRequestMethod("POST");
        mapConn2.setDoOutput(true);
        mapConn2.setRequestProperty("Content-Type", "application/json");
        mapConn2.setRequestProperty("X-Tenant-Id", "CAMPUS_MAIN");
        mapConn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = mapConn2.getOutputStream()) {
            os.write(mapPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(409, mapConn2.getResponseCode());
        String errResp = readResponse(mapConn2);
        assertTrue(errResp.contains("\"errorCode\":\"ACD2_DUPLICATE_MAPPING\""));
    }

    /**
     * Tests role-based access control (RBAC) ensuring unauthorized roles (e.g. STUDENT)
     * cannot create curricula, returning HTTP 403 Forbidden.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testStudentCannotCreateCurriculumReturns403() throws Exception {
        String payload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"CBCS\""
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-STUDENT-ERR-001");
        conn.setRequestProperty("X-User-Role", "STUDENT");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"errorCode\":\"ACD2_FORBIDDEN\""));
    }

    /**
     * Tests HTTP GET /actuator/health liveness probe returning HTTP 200 and UP status.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testHealthEndpoint() throws Exception {
        URL url = new URL(BASE_URL + "/actuator/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"UP\""));
    }

    /**
     * Tests HTTP PUT and GET for syllabus modules (FR-05, Story 29, 30).
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testUpdateSyllabusWithValidBody() throws Exception {
        // Create curriculum first with NEP pattern to ensure uniqueness
        String createPayload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"NEP\","
                + "\"academicYear\":\"2026-2027\","
                + "\"name\":\"Syllabus Test Curriculum\""
                + "}";
        URL createUrl = new URL(BASE_URL + "/api/v1/academics/curricula");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        createConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String currId = extractId(readResponse(createConn));

        // Update syllabus with 2 custom modules
        String syllabusPayload = "{\"modules\":["
                + "{\"moduleId\":\"MOD-101\",\"title\":\"Data Structures & Algorithms\",\"order\":1,\"topics\":[\"Arrays\",\"Trees\",\"Graphs\"],\"hours\":40.0},"
                + "{\"moduleId\":\"MOD-102\",\"title\":\"Distributed Systems\",\"order\":2,\"topics\":[\"Consensus\",\"Raft\",\"Paxos\"],\"hours\":35.0}"
                + "]}";

        URL sylUrl = new URL(BASE_URL + "/api/v1/academics/curricula/" + currId + "/versions/1/syllabus");
        HttpURLConnection sylConn = (HttpURLConnection) sylUrl.openConnection();
        sylConn.setRequestMethod("PUT");
        sylConn.setDoOutput(true);
        sylConn.setRequestProperty("Content-Type", "application/json");
        sylConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = sylConn.getOutputStream()) {
            os.write(syllabusPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, sylConn.getResponseCode());
        String sylResp = readResponse(sylConn);
        assertTrue(sylResp.contains("\"moduleCount\":2"));

        // Fetch syllabus and verify modules
        URL getSylUrl = new URL(BASE_URL + "/api/v1/academics/curricula/" + currId + "/versions/1/syllabus");
        HttpURLConnection getSylConn = (HttpURLConnection) getSylUrl.openConnection();
        getSylConn.setRequestMethod("GET");
        assertEquals(200, getSylConn.getResponseCode());
        String getSylResp = readResponse(getSylConn);
        assertTrue(getSylResp.contains("MOD-101"));
        assertTrue(getSylResp.contains("Data Structures & Algorithms"));
        assertTrue(getSylResp.contains("MOD-102"));
        assertTrue(getSylResp.contains("Distributed Systems"));
        assertTrue(getSylResp.contains("Consensus"));
    }

    /**
     * Tests validation failure when syllabus update payload contains an empty modules array.
     * Verifies HTTP 400 Bad Request with ACD2_VALIDATION_ERROR.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testUpdateSyllabusEmptyModulesReturns400() throws Exception {
        String sylPayload = "{\"modules\":[]}";
        URL url = new URL(BASE_URL + "/api/v1/academics/curricula/CURR-NONEXISTENT/versions/1/syllabus");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(sylPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ACD2_VALIDATION_ERROR"));
    }

    /**
     * Tests HTTP GET /api/v1/academics/curricula/{id}/compliance-view by ACCREDITATION_TEAM.
     * Verifies HTTP 200 OK and presence of curriculum, versions, and audit history envelopes.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testComplianceViewEndpointSuccess() throws Exception {
        // Create curriculum with SEMESTER pattern
        String createPayload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"SEMESTER\","
                + "\"academicYear\":\"2026-2027\","
                + "\"name\":\"Compliance View Endpoint Test\""
                + "}";
        URL createUrl = new URL(BASE_URL + "/api/v1/academics/curricula");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        createConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String currId = extractId(readResponse(createConn));

        // Call compliance-view with ACCREDITATION_TEAM role
        URL compUrl = new URL(BASE_URL + "/api/v1/academics/curricula/" + currId + "/compliance-view");
        HttpURLConnection compConn = (HttpURLConnection) compUrl.openConnection();
        compConn.setRequestMethod("GET");
        compConn.setRequestProperty("X-User-Role", "ACCREDITATION_TEAM");
        assertEquals(200, compConn.getResponseCode());
        String compResp = readResponse(compConn);
        assertTrue(compResp.contains("\"curriculum\":"));
        assertTrue(compResp.contains("\"versions\":"));
        assertTrue(compResp.contains("\"auditHistory\":"));
    }

    /**
     * Tests RBAC on compliance-view endpoint rejecting STUDENT role with HTTP 403.
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testComplianceViewEndpointForbiddenForStudent() throws Exception {
        URL compUrl = new URL(BASE_URL + "/api/v1/academics/curricula/CURR-SAMPLE/compliance-view");
        HttpURLConnection compConn = (HttpURLConnection) compUrl.openConnection();
        compConn.setRequestMethod("GET");
        compConn.setRequestProperty("X-User-Role", "STUDENT");
        assertEquals(403, compConn.getResponseCode());
        String compResp = readResponse(compConn);
        assertTrue(compResp.contains("ACD2_FORBIDDEN"));
    }

    /**
     * Tests external API Key authentication permitting read access to curricula (Story 41).
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testExternalApiKeyReadOnlyAccess() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-API-Key", "ak_live_campx_valid_12345");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"curricula\":"));
    }

    /**
     * Tests that external API Key authentication rejects write/mutate operations with HTTP 403 Forbidden (Story 41).
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testExternalApiKeyWriteRejected() throws Exception {
        String payload = "{"
                + "\"courseId\":\"COURSE-001\","
                + "\"academicPattern\":\"CBCS\""
                + "}";
        URL url = new URL(BASE_URL + "/api/v1/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-API-Key", "ak_live_campx_valid_12345");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(403, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ACD2_FORBIDDEN"));
    }

    /**
     * Tests that requests with invalid, unhashed, or revoked API keys receive HTTP 401 Unauthorized (Story 41).
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testInvalidApiKeyReturns401() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/curricula");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-API-Key", "ak_invalid_revoked_key");
        assertEquals(401, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ACD2_UNAUTHORIZED"));
    }

    /**
     * Unit tests data classification field masking across tiers (L1_PUBLIC to L4_RESTRICTED - Story 42).
     */
    @Test
    public void testDataClassificationFieldFilteringUnit() {
        String sampleJson = "{\"id\":\"C-1\",\"name\":\"MCA\",\"tenantId\":\"TENANT-001\",\"institutionId\":\"INST-1\","
                + "\"createdBy\":\"admin\",\"approvedBy\":\"dean\",\"changeSummary\":\"init\","
                + "\"checksum\":\"abc1234\",\"syllabus\":[\"MOD-1\"]}";

        // L1 PUBLIC: strips L2, L3, L4 fields
        String l1 = CurriculumController.filterFieldsByClassification(sampleJson, DataClassification.L1_PUBLIC);
        assertFalse(l1.contains("tenantId"));
        assertFalse(l1.contains("institutionId"));
        assertFalse(l1.contains("createdBy"));
        assertFalse(l1.contains("approvedBy"));
        assertFalse(l1.contains("syllabus"));
        assertTrue(l1.contains("id"));
        assertTrue(l1.contains("name"));

        // L2 INTERNAL: strips L3, L4 fields, preserves syllabus (L2)
        String l2 = CurriculumController.filterFieldsByClassification(sampleJson, DataClassification.L2_INTERNAL);
        assertFalse(l2.contains("tenantId"));
        assertFalse(l2.contains("createdBy"));
        assertTrue(l2.contains("syllabus"));
        assertTrue(l2.contains("name"));

        // L3 CONFIDENTIAL: strips L4 fields, preserves createdBy and syllabus
        String l3 = CurriculumController.filterFieldsByClassification(sampleJson, DataClassification.L3_CONFIDENTIAL);
        assertFalse(l3.contains("tenantId"));
        assertTrue(l3.contains("createdBy"));
        assertTrue(l3.contains("syllabus"));

        // L4 RESTRICTED: preserves all fields
        String l4 = CurriculumController.filterFieldsByClassification(sampleJson, DataClassification.L4_RESTRICTED);
        assertTrue(l4.contains("tenantId"));
        assertTrue(l4.contains("createdBy"));
        assertTrue(l4.contains("syllabus"));
    }

    /**
     * Tests HTTP GET /metrics returning Prometheus-compatible text metric format (Story 69).
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testPrometheusMetricsEndpointHttp() throws Exception {
        URL url = new URL(BASE_URL + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int status = conn.getResponseCode();
        assertEquals(200, status);
        String contentType = conn.getHeaderField("Content-Type");
        assertTrue(contentType != null && contentType.contains("text/plain"));

        String body = readResponse(conn);
        assertTrue(body.contains("acd02_request_total"));
        assertTrue(body.contains("acd02_outbox_pending_count"));
        assertTrue(body.contains("acd02_curricula_total"));
        assertTrue(body.contains("acd02_error_total"));
    }

    /**
     * Tests HTTP GET /api/v1/curricula/metrics routed through the curricula path prefix (Story 69).
     *
     * @throws Exception if HTTP connection fails
     */
    @Test
    public void testPrometheusMetricsThroughCurriculaRoute() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/curricula/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int status = conn.getResponseCode();
        assertEquals(200, status);
        String body = readResponse(conn);
        assertTrue(body.contains("acd02_request_total"));
    }

    private String extractId(String json) {
        int idx = json.indexOf("\"curriculumId\":\"");
        if (idx != -1) {
            int end = json.indexOf("\"", idx + 16);
            return json.substring(idx + 16, end);
        }
        return "CURR-UNKNOWN";
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
