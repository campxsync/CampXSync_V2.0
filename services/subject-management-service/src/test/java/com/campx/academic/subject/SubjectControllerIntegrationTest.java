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

    /**
     * CRIT-01: Malformed query parameter returns HTTP 400 Bad Request.
     */
    @Test
    public void testMalformedQueryParameterReturns400() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/subjects/search?limit=not_a_number");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ACD_BAD_REQUEST"));
        assertTrue(resp.contains("Invalid query parameter 'limit'"));
    }

    /**
     * CRIT-01: Invalid status string returns HTTP 400 Bad Request.
     */
    @Test
    public void testInvalidStatusStringReturns400() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/subjects/SUB-TEST-101/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        String payload = "{\"status\":\"COMPLETELY_INVALID_STATUS\",\"reason\":\"testing\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ACD_BAD_REQUEST"));
    }

    /**
     * CRIT-02: Idempotent version publish replay.
     */
    @Test
    public void testIdempotentVersionPublish() throws Exception {
        // 1. Create a subject
        String payload = "{"
                + "\"subjectCode\":\"SUB-IDEM-01\","
                + "\"name\":\"Distributed Systems\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}";
        String createdResp = postHttp("/api/v1/subjects", payload, "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(createdResp, "subjectId");

        // 2. Create version 2
        String verPayload = "{\"credits\":4.0,\"contactHours\":60.0,\"changeSummary\":\"Upgraded credits\"}";
        postHttp("/api/v1/subjects/" + subjectId + "/versions", verPayload, "ACADEMIC_ADMIN", 201);

        // 3. Publish version 2 with Idempotency-Key
        URL pubUrl = new URL(BASE_URL + "/api/v1/subjects/" + subjectId + "/versions/2/publish");
        HttpURLConnection pubConn1 = (HttpURLConnection) pubUrl.openConnection();
        pubConn1.setRequestMethod("POST");
        pubConn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        pubConn1.setRequestProperty("Idempotency-Key", "IDEMP-KEY-PUB-V2");
        assertEquals(200, pubConn1.getResponseCode());
        String pubResp1 = readResponse(pubConn1);

        // 4. Replay with identical Idempotency-Key should return cached response
        HttpURLConnection pubConn2 = (HttpURLConnection) pubUrl.openConnection();
        pubConn2.setRequestMethod("POST");
        pubConn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        pubConn2.setRequestProperty("Idempotency-Key", "IDEMP-KEY-PUB-V2");
        assertEquals(200, pubConn2.getResponseCode());
        String pubResp2 = readResponse(pubConn2);

        assertEquals(pubResp1, pubResp2);
    }

    /**
     * HIGH-01: Rate limiting triggers HTTP 429 Too Many Requests.
     */
    @Test
    public void testRateLimitingReturns429() throws Exception {
        // Fast burst using dedicated user key to exhaust quota
        String burstUser = "burst-tester-" + System.currentTimeMillis();
        boolean hit429 = false;
        long retryAfter = 0;

        for (int i = 0; i < 150; i++) {
            URL url = new URL(BASE_URL + "/api/v1/subjects/catalog");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-User-Id", burstUser);
            conn.setRequestProperty("X-Tenant-Id", "TENANT-BURST");
            conn.setRequestProperty("X-User-Role", "STUDENT");

            int code = conn.getResponseCode();
            if (code == 429) {
                hit429 = true;
                String retryHeader = conn.getHeaderField("Retry-After");
                assertNotNull("Retry-After header should be present", retryHeader);
                retryAfter = Long.parseLong(retryHeader);
                assertTrue(retryAfter >= 1);
                String resp = readResponse(conn);
                assertTrue(resp.contains("ACD_RATE_LIMIT_EXCEEDED"));
                break;
            }
        }

        assertTrue("Expected to hit HTTP 429 within 150 rapid requests", hit429);
    }

    /**
     * Test Course Outcomes HTTP Endpoints: PUT & GET (Story 1).
     */
    @Test
    public void testCourseOutcomesHttp() throws Exception {
        String subResp = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-HTTP-CO\","
                + "\"name\":\"Discrete Structures\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(subResp, "subjectId");

        // Create draft version 2
        postHttp("/api/v1/academics/subjects/" + subjectId + "/versions", "{\"changeSummary\":\"OBE update\"}", "ACADEMIC_ADMIN", 201);

        String coPayload = "[{\"outcomeCode\":\"CO1\",\"statement\":\"Demonstrate set theory\",\"bloomLevel\":\"K2\",\"targetAttainment\":75.0},"
                + "{\"outcomeCode\":\"CO2\",\"statement\":\"Apply graph algorithms\",\"bloomLevel\":\"K3\",\"targetAttainment\":80.0}]";

        String putResp = putHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/course-outcomes", coPayload, "ACADEMIC_ADMIN", 200);
        assertTrue(putResp.contains("CO1"));
        assertTrue(putResp.contains("K2"));

        String getResp = getHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/course-outcomes", "ACADEMIC_ADMIN", 200);
        assertTrue(getResp.contains("CO2"));
        assertTrue(getResp.contains("K3"));
    }

    /**
     * Test CO-PO Articulation Matrix HTTP Endpoints: PUT & GET (Story 2).
     */
    @Test
    public void testCoPoMatrixHttp() throws Exception {
        String subResp = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-HTTP-COPO\","
                + "\"name\":\"Calculus I\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(subResp, "subjectId");
        postHttp("/api/v1/academics/subjects/" + subjectId + "/versions", "{\"changeSummary\":\"Matrix update\"}", "ACADEMIC_ADMIN", 201);

        String matrixPayload = "[{\"outcomeCode\":\"CO1\",\"programOutcomeCode\":\"PO1\",\"correlationStrength\":3},"
                + "{\"outcomeCode\":\"CO1\",\"programOutcomeCode\":\"PO2\",\"correlationStrength\":2}]";

        String putResp = putHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/co-po-matrix", matrixPayload, "ACADEMIC_ADMIN", 200);
        assertTrue(putResp.contains("PO1"));

        String getResp = getHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/co-po-matrix", "ACADEMIC_ADMIN", 200);
        assertTrue(getResp.contains("PO2"));
    }

    /**
     * Test Modular Syllabus Units HTTP Endpoints: PUT & GET (Story 5).
     */
    @Test
    public void testSyllabusUnitsHttp() throws Exception {
        String subResp = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-HTTP-SYL\","
                + "\"name\":\"Digital Electronics\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(subResp, "subjectId");
        postHttp("/api/v1/academics/subjects/" + subjectId + "/versions", "{\"changeSummary\":\"Syllabus breakdown\"}", "ACADEMIC_ADMIN", 201);

        String sylPayload = "[{\"unitNumber\":1,\"title\":\"Logic Gates & Boolean Algebra\",\"hours\":12.0,\"topics\":[\"AND\",\"OR\",\"NAND\"]},"
                + "{\"unitNumber\":2,\"title\":\"Combinational Circuits\",\"hours\":15.0,\"topics\":[\"Adders\",\"Multiplexers\"]}]";

        String putResp = putHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/syllabus-units", sylPayload, "ACADEMIC_ADMIN", 200);
        assertTrue(putResp.contains("Logic Gates"));

        String getResp = getHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/syllabus-units", "ACADEMIC_ADMIN", 200);
        assertTrue(getResp.contains("Combinational Circuits"));
    }

    /**
     * Test BoS Governance Resolution HTTP Endpoints: PUT & GET (Story 9).
     */
    @Test
    public void testApprovalResolutionHttp() throws Exception {
        String subResp = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-HTTP-BOS\","
                + "\"name\":\"Compiler Design\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(subResp, "subjectId");
        postHttp("/api/v1/academics/subjects/" + subjectId + "/versions", "{\"changeSummary\":\"BoS resolution update\"}", "ACADEMIC_ADMIN", 201);

        String resPayload = "{"
                + "\"resolutionNumber\":\"BOS-2026-HTTP-01\","
                + "\"approvedByBoard\":\"Board of Studies in Computer Science\","
                + "\"meetingDate\":\"2026-05-20\","
                + "\"minutesUrl\":\"https://campx.internal/minutes/bos-2026.pdf\","
                + "\"gazetteNotificationNumber\":\"GAZ-ACAD-2026-441\""
                + "}";

        String putResp = putHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/resolution", resPayload, "ACADEMIC_ADMIN", 200);
        assertTrue(putResp.contains("BOS-2026-HTTP-01"));

        String getResp = getHttp("/api/v1/academics/subjects/" + subjectId + "/versions/2/resolution", "ACADEMIC_ADMIN", 200);
        assertTrue(getResp.contains("GAZ-ACAD-2026-441"));
    }

    /**
     * Test Subject Equivalence HTTP Endpoints: POST, GET, DELETE (Story 3).
     */
    @Test
    public void testSubjectEquivalenceHttp() throws Exception {
        String subResp1 = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-EQ-SRC\","
                + "\"name\":\"Old Python Programming\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String s1 = extractField(subResp1, "subjectId");

        String subResp2 = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-EQ-TGT\","
                + "\"name\":\"Modern Python & Data Science\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String s2 = extractField(subResp2, "subjectId");

        String eqPayload = "{"
                + "\"targetSubjectId\":\"" + s2 + "\","
                + "\"equivalenceType\":\"DIRECT_SUBSTITUTION\","
                + "\"minimumGrade\":\"C\","
                + "\"transferMultiplier\":1.0"
                + "}";

        String postEqResp = postHttp("/api/v1/academics/subjects/" + s1 + "/equivalences", eqPayload, "ACADEMIC_ADMIN", 201);
        assertTrue(postEqResp.contains("EQ-"));
        String eqId = extractField(postEqResp, "id");

        String getEqResp = getHttp("/api/v1/academics/subjects/" + s1 + "/equivalences", "ACADEMIC_ADMIN", 200);
        assertTrue(getEqResp.contains(eqId));

        // Revoke
        String delResp = deleteHttp("/api/v1/academics/subjects/" + s1 + "/equivalences/" + eqId, "ACADEMIC_ADMIN", 200);
        assertTrue(delResp.contains("REVOKED"));
    }

    /**
     * Test Version Diff Engine HTTP Endpoint: GET /versions/diff?v1=1&v2=2 (Story 10).
     */
    @Test
    public void testVersionDiffHttp() throws Exception {
        String subResp = postHttp("/api/v1/academics/subjects", "{"
                + "\"subjectCode\":\"SUB-HTTP-DIFF\","
                + "\"name\":\"Artificial Intelligence\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0"
                + "}", "ACADEMIC_ADMIN", 201);
        String subjectId = extractField(subResp, "subjectId");

        // Create version 2 with updated credits
        postHttp("/api/v1/academics/subjects/" + subjectId + "/versions", "{\"credits\":4.0,\"contactHours\":60.0,\"changeSummary\":\"Expanding lab hours\"}", "ACADEMIC_ADMIN", 201);

        String diffResp = getHttp("/api/v1/academics/subjects/" + subjectId + "/versions/diff?v1=1&v2=2", "ACADEMIC_ADMIN", 200);
        assertTrue(diffResp.contains("\"success\":true"));
        assertTrue(diffResp.contains("\"differences\":["));
        assertTrue(diffResp.contains("\"version1\":1"));
        assertTrue(diffResp.contains("\"version2\":2"));
        assertTrue(diffResp.contains("credits"));
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

    private String putHttp(String path, String payload, String role, int expectedStatus) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-User-Role", role);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(expectedStatus, conn.getResponseCode());
        return readResponse(conn);
    }

    private String getHttp(String path, String role, int expectedStatus) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", role);

        assertEquals(expectedStatus, conn.getResponseCode());
        return readResponse(conn);
    }

    private String deleteHttp(String path, String role, int expectedStatus) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("X-User-Role", role);

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
