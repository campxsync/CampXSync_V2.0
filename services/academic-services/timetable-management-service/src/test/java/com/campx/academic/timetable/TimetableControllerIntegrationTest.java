package com.campx.academic.timetable;

import com.campx.academic.timetable.server.TimetableServer;
import com.campx.academic.timetable.service.TimetableDomainService;
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
 * End-to-End HTTP REST Integration tests for ACD-05 Timetable Management Service.
 * Validates request/response envelopes, HTTP status codes, RFC 7807 error taxonomy,
 * RBAC matrix, conflict detection, versioning, publication, cloning, supersession, and idempotency.
 */
public class TimetableControllerIntegrationTest {

    private static TimetableServer server;
    private static TimetableDomainService domainService;
    private static final int TEST_PORT = 8097;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeClass
    public static void startServer() throws Exception {
        domainService = new TimetableDomainService();
        server = new TimetableServer(TEST_PORT, domainService);
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
        HttpURLConnection conn = open("GET", "/actuator/health", null, null, null);
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"UP\""));
        assertTrue(resp.contains("ACD-05-TimetableManagementService"));
    }

    @Test
    public void testMetricsEndpoint() throws Exception {
        HttpURLConnection conn = open("GET", "/metrics", null, null, null);
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("acd05_request_total"));
        assertTrue(resp.contains("acd05_outbox_backlog"));
        assertTrue(resp.contains("acd05_dlq_events_total"));
    }

    @Test
    public void testCreateTimetableDraftViaHttp() throws Exception {
        String payload = "{"
                + "\"timetableCode\":\"TT_HTTP_TEST_01\","
                + "\"name\":\"B.Tech CSE HTTP Schedule\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\","
                + "\"effectiveFrom\":\"2026-08-15\","
                + "\"effectiveTo\":\"2026-12-15\""
                + "}";

        HttpURLConnection conn = open("POST", "/api/v1/academics/timetables", payload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, conn.getResponseCode());

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"timetableCode\":\"TT_HTTP_TEST_01\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
        assertTrue(resp.contains("\"currentVersionNo\":1"));
        assertNotNull(conn.getHeaderField("X-Trace-Id"));
    }

    @Test
    public void testE2EConflictDetectionBlocksPublicationThenSucceedsAfterCorrection() throws Exception {
        // Step 1: Create draft timetable
        String createPayload = "{"
                + "\"timetableCode\":\"TT_E2E_CONFLICT\","
                + "\"name\":\"E2E Conflict Timetable\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\","
                + "\"effectiveFrom\":\"2026-08-15\","
                + "\"effectiveTo\":\"2026-12-15\""
                + "}";
        HttpURLConnection conn = open("POST", "/api/v1/academics/timetables", createPayload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, conn.getResponseCode());
        String ttId = extractJsonString(readResponse(conn), "id");

        // Step 2: Add Entry 1 (Faculty FAC-100 on MONDAY P2)
        String e1Payload = "{"
                + "\"batchId\":\"BATCH-001\","
                + "\"subjectId\":\"SUB-201\","
                + "\"facultyId\":\"FAC-100\","
                + "\"roomId\":\"ROOM-12\","
                + "\"dayOfWeek\":\"MONDAY\","
                + "\"period\":2,"
                + "\"entryType\":\"TH\""
                + "}";
        HttpURLConnection connE1 = open("POST", "/api/v1/academics/timetables/" + ttId + "/entries", e1Payload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connE1.getResponseCode());

        // Step 3: Add Entry 2 (Same faculty FAC-100 also on MONDAY P2 - double booking!)
        String e2Payload = "{"
                + "\"batchId\":\"BATCH-002\","
                + "\"subjectId\":\"SUB-202\","
                + "\"facultyId\":\"FAC-100\","
                + "\"roomId\":\"ROOM-101\","
                + "\"dayOfWeek\":\"MONDAY\","
                + "\"period\":2,"
                + "\"entryType\":\"TH\""
                + "}";
        HttpURLConnection connE2 = open("POST", "/api/v1/academics/timetables/" + ttId + "/entries", e2Payload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connE2.getResponseCode());
        String e2Id = extractJsonString(readResponse(connE2), "id");

        // Step 4: Run validation -> detects blocking FACULTY conflict
        HttpURLConnection connVal1 = open("POST", "/api/v1/academics/timetables/" + ttId + "/validate", "", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connVal1.getResponseCode());
        String valResp1 = readResponse(connVal1);
        assertTrue(valResp1.contains("\"status\":\"INVALID\""));
        assertTrue(valResp1.contains("\"blockingConflicts\":1"));
        assertTrue(valResp1.contains("Faculty FAC-100 is double-booked in MONDAY P2"));

        // Step 5: Attempt publish -> rejected with 422 Unprocessable Entity (Story 36, 79)
        HttpURLConnection connPub1 = open("POST", "/api/v1/academics/timetables/" + ttId + "/publish", "{}", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(422, connPub1.getResponseCode());
        String errPub1 = readError(connPub1);
        assertTrue(errPub1.contains("ACD_TIMETABLE_VALIDATION_FAILED"));

        // Step 6: Correct the conflict by moving Entry 2 to period 3
        String updatePayload = "{"
                + "\"period\":3,"
                + "\"periodId\":\"P3\""
                + "}";
        HttpURLConnection connUpdate = open("PUT", "/api/v1/academics/timetables/" + ttId + "/entries/" + e2Id, updatePayload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(200, connUpdate.getResponseCode());

        // Step 7: Re-run validation -> zero blocking conflicts, transitions to VALIDATED
        HttpURLConnection connVal2 = open("POST", "/api/v1/academics/timetables/" + ttId + "/validate", "", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connVal2.getResponseCode());
        String valResp2 = readResponse(connVal2);
        assertTrue(valResp2.contains("\"status\":\"VALIDATED\""));
        assertTrue(valResp2.contains("\"blockingConflicts\":0"));

        // Step 8: Publish timetable -> succeeds with 201 Created
        HttpURLConnection connPub2 = open("POST", "/api/v1/academics/timetables/" + ttId + "/publish", "{}", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connPub2.getResponseCode());
        String pubResp2 = readResponse(connPub2);
        assertTrue(pubResp2.contains("\"status\":\"PUBLISHED\""));
        assertTrue(pubResp2.contains("\"versionNo\":1"));
    }

    @Test
    public void testE2EMidTermChangeClonesRevalidatesAndSupersedes() throws Exception {
        // Step 1: Create, populate, validate and publish v1
        String createPayload = "{"
                + "\"timetableCode\":\"TT_E2E_CHANGE\","
                + "\"name\":\"Original Schedule\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\","
                + "\"effectiveFrom\":\"2026-08-15\""
                + "}";
        HttpURLConnection conn = open("POST", "/api/v1/academics/timetables", createPayload, "TENANT-001", "ACADEMIC_ADMIN");
        String ttId = extractJsonString(readResponse(conn), "id");

        String ePayload = "{"
                + "\"batchId\":\"BATCH-001\","
                + "\"subjectId\":\"SUB-201\","
                + "\"facultyId\":\"FAC-100\","
                + "\"roomId\":\"ROOM-12\","
                + "\"dayOfWeek\":\"THURSDAY\","
                + "\"period\":1,"
                + "\"entryType\":\"TH\""
                + "}";
        HttpURLConnection connEntry = open("POST", "/api/v1/academics/timetables/" + ttId + "/entries", ePayload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connEntry.getResponseCode());

        HttpURLConnection connVal1 = open("POST", "/api/v1/academics/timetables/" + ttId + "/validate", "", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connVal1.getResponseCode());
        HttpURLConnection connPub1 = open("POST", "/api/v1/academics/timetables/" + ttId + "/publish", "{}", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connPub1.getResponseCode());

        // Step 2: Clone effective version into new draft v2 (Change workflow Story 39)
        String clonePayload = "{\"reason\":\"Room reassignment to Room 101\"}";
        HttpURLConnection connClone = open("POST", "/api/v1/academics/timetables/" + ttId + "/clone", clonePayload, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connClone.getResponseCode());
        String cloneResp = readResponse(connClone);
        assertTrue(cloneResp.contains("\"currentVersionNo\":2"));
        assertTrue(cloneResp.contains("\"status\":\"DRAFT\""));

        // Step 3: Validate and publish v2
        HttpURLConnection connVal2 = open("POST", "/api/v1/academics/timetables/" + ttId + "/validate", "", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connVal2.getResponseCode());
        HttpURLConnection connPub2 = open("POST", "/api/v1/academics/timetables/" + ttId + "/publish", "{}", "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(201, connPub2.getResponseCode());

        // Step 4: Verify versions history -> v1 is SUPERSEDED, v2 is PUBLISHED
        HttpURLConnection connVer = open("GET", "/api/v1/academics/timetables/" + ttId + "/versions", null, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(200, connVer.getResponseCode());
        String verResp = readResponse(connVer);
        assertTrue(verResp.contains("\"versionNo\":1"));
        assertTrue(verResp.contains("\"status\":\"SUPERSEDED\""));
        assertTrue(verResp.contains("\"versionNo\":2"));
        assertTrue(verResp.contains("\"status\":\"PUBLISHED\""));
    }

    @Test
    public void testCrossTenantReferenceRejection() throws Exception {
        String createPayload = "{"
                + "\"timetableCode\":\"TT_CT_TEST\","
                + "\"name\":\"Cross Tenant Test\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\""
                + "}";
        HttpURLConnection conn = open("POST", "/api/v1/academics/timetables", createPayload, "TENANT-001", "ACADEMIC_ADMIN");
        String ttId = extractJsonString(readResponse(conn), "id");

        // Attempting to add an entry with batch from foreign tenant
        String foreignEntry = "{"
                + "\"batchId\":\"BATCH-DIFF-TENANT\","
                + "\"subjectId\":\"SUB-201\","
                + "\"facultyId\":\"FAC-100\","
                + "\"dayOfWeek\":\"MONDAY\","
                + "\"period\":1"
                + "}";
        HttpURLConnection connE = open("POST", "/api/v1/academics/timetables/" + ttId + "/entries", foreignEntry, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(422, connE.getResponseCode());
        String err = readError(connE);
        assertTrue(err.contains("ACD_TIMETABLE_CROSS_TENANT_FORBIDDEN"));
    }

    @Test
    public void testLabRightsMissingRejection() throws Exception {
        String createPayload = "{"
                + "\"timetableCode\":\"TT_LAB_RIGHTS_TEST\","
                + "\"name\":\"Lab Rights Test\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\""
                + "}";
        HttpURLConnection conn = open("POST", "/api/v1/academics/timetables", createPayload, "TENANT-001", "ACADEMIC_ADMIN");
        String ttId = extractJsonString(readResponse(conn), "id");

        // FAC-200 does not possess lab rights
        String labEntry = "{"
                + "\"batchId\":\"BATCH-001\","
                + "\"subjectId\":\"SUB-201\","
                + "\"facultyId\":\"FAC-200\","
                + "\"roomId\":\"LAB-01\","
                + "\"dayOfWeek\":\"TUESDAY\","
                + "\"period\":1,"
                + "\"entryType\":\"LAB\""
                + "}";
        HttpURLConnection connE = open("POST", "/api/v1/academics/timetables/" + ttId + "/entries", labEntry, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(422, connE.getResponseCode());
        String err = readError(connE);
        assertTrue(err.contains("ACD_TIMETABLE_LAB_RIGHTS_MISSING"));
    }

    @Test
    public void testRBACPermissions() throws Exception {
        // Facilities Manager accessing room utilization -> Allowed 200
        HttpURLConnection connFac = open("GET", "/api/v1/academics/timetables/room/ROOM-12", null, "TENANT-001", "FACILITIES_MANAGER");
        assertEquals(200, connFac.getResponseCode());

        // Facilities Manager attempting to create a timetable -> Forbidden 403
        HttpURLConnection connFacCreate = open("POST", "/api/v1/academics/timetables", "{}", "TENANT-001", "FACILITIES_MANAGER");
        assertEquals(403, connFacCreate.getResponseCode());
        String errFac = readError(connFacCreate);
        assertTrue(errFac.contains("ACD_TIMETABLE_FORBIDDEN"));

        // Student attempting to create a timetable -> Forbidden 403
        HttpURLConnection connStu = open("POST", "/api/v1/academics/timetables", "{}", "TENANT-001", "STUDENT");
        assertEquals(403, connStu.getResponseCode());

        // Student accessing batch timetable -> Allowed 200
        HttpURLConnection connStuBatch = open("GET", "/api/v1/academics/timetables/batch/BATCH-001", null, "TENANT-001", "STUDENT");
        assertEquals(200, connStuBatch.getResponseCode());

        // External API Key consumer writing -> Forbidden 403
        URL url = new URL(BASE_URL + "/api/v1/academics/timetables");
        HttpURLConnection connExt = (HttpURLConnection) url.openConnection();
        connExt.setRequestMethod("POST");
        connExt.setRequestProperty("X-API-Key", "API-KEY-LMS-001");
        connExt.setRequestProperty("X-Tenant-Id", "TENANT-001");
        connExt.setDoOutput(true);
        try (OutputStream os = connExt.getOutputStream()) {
            os.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(403, connExt.getResponseCode());
    }

    @Test
    public void testIdempotentRequestHandling() throws Exception {
        String idempKey = "IDEMP-TEST-" + System.currentTimeMillis();
        String payload = "{"
                + "\"timetableCode\":\"TT_IDEMP_" + System.currentTimeMillis() + "\","
                + "\"name\":\"Idempotent Draft\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\""
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/timetables");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setRequestProperty("Idempotency-Key", idempKey);
        conn1.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        conn1.setDoOutput(true);
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());
        String resp1 = readResponse(conn1);

        // Retry with same Idempotency-Key
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setRequestProperty("Idempotency-Key", idempKey);
        conn2.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        conn2.setDoOutput(true);
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn2.getResponseCode());
        String resp2 = readResponse(conn2);

        assertEquals(resp1, resp2);
    }

    @Test
    public void testErrorResponseHasBothCodeAndErrorCode() throws Exception {
        // Request invalid timetable ID
        HttpURLConnection conn = open("GET", "/api/v1/academics/timetables/TT-NON-EXISTENT", null, "TENANT-001", "ACADEMIC_ADMIN");
        assertEquals(404, conn.getResponseCode());

        String err = readError(conn);
        assertTrue(err.contains("\"code\":\"ACD_TIMETABLE_NOT_FOUND\""));
        assertTrue(err.contains("\"errorCode\":\"ACD_TIMETABLE_NOT_FOUND\""));
        assertTrue(err.contains("\"success\":false"));
        assertTrue(err.contains("\"meta\""));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HttpURLConnection open(String method, String path, String body, String tenantId, String userRole) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-" + System.currentTimeMillis());
        if (tenantId != null) conn.setRequestProperty("X-Tenant-Id", tenantId);
        if (userRole != null) conn.setRequestProperty("X-User-Role", userRole);
        conn.setRequestProperty("X-User-Id", "user-test");

        if (body != null && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        return readStream(conn.getInputStream());
    }

    private String readError(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getErrorStream();
        if (is == null) is = conn.getInputStream();
        return readStream(is);
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
