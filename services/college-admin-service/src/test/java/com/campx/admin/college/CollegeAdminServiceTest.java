package com.campx.admin.college;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration test suite for ADM-02 College Admin Service (College Operational Tier).
 * <p>
 * Boots the embedded HTTP server on test port 8092 and exercises all operational workflows:
 * <ul>
 *   <li>College profile governance, legal identity, and accreditation references</li>
 *   <li>Department and program lifecycle with referential integrity</li>
 *   <li>Bulk asynchronous data ingestion jobs with idempotent replay</li>
 *   <li>Governance document registration, versioning, permissions, and approvals</li>
 *   <li>College-scoped RBAC, role definitions, and access reviews</li>
 *   <li>Transactional reliability: idempotency records, outbox/inbox events, and DLQ</li>
 *   <li>Academic calendar synchronization, reporting definitions, and workflows</li>
 * </ul>
 */
public class CollegeAdminServiceTest {

    /**
     * Embedded test HTTP server instance.
     */
    private static CollegeAdminServer server;

    /**
     * Shared domain business service for state assertion and testing.
     */
    private static CollegeAdminDomainService domainService;

    /**
     * Dedicated TCP port for running integration tests against College Admin Service.
     */
    private static final int TEST_PORT = 8092;

    /**
     * Initializes domain service and boots embedded server on port 8092 before tests execute.
     *
     * @throws Exception if server bootstrap or port binding fails
     */
    @BeforeClass
    public static void setup() throws Exception {
        domainService = new CollegeAdminDomainService();
        server = new CollegeAdminServer(TEST_PORT, domainService);
        server.start();
    }

    /**
     * Shuts down the embedded HTTP server and flushes log buffers after test completion.
     */
    @AfterClass
    public static void teardown() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testProfileAndDepartments() throws Exception {
        // 1. Get Profile
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/profile");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"code\":\"COL_ENGG_01\""));

        // 2. Create Department
        String depJson = "{"
                + "\"departmentCode\":\"MECH\","
                + "\"name\":\"Mechanical Engineering\","
                + "\"headUserId\":\"FAC_HOD_MECH\""
                + "}";
        URL depUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments");
        HttpURLConnection depConn = (HttpURLConnection) depUrl.openConnection();
        depConn.setRequestMethod("POST");
        depConn.setDoOutput(true);
        depConn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = depConn.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, depConn.getResponseCode());
        String depResp = readResponse(depConn);
        assertTrue(depResp.contains("\"code\":\"MECH\""));
    }

    @Test
    public void testDataImportJob() throws Exception {
        String importJson = "{"
                + "\"entityType\":\"STUDENT\","
                + "\"fileRef\":\"s3://campx-imports/admissions-2026.csv\","
                + "\"mode\":\"INSERT\","
                + "\"idempotencyKey\":\"IMPORT-KEY-7788\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/imports");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(importJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"COMPLETED\""));
        assertTrue(resp.contains("\"processed\":98"));
    }

    @Test
    public void testGovernanceDocumentWorkflow() throws Exception {
        // 1. Register Document
        String docJson = "{"
                + "\"documentType\":\"ACCREDITATION_REPORT\","
                + "\"title\":\"NBA Self-Assessment Report 2026\","
                + "\"ownerId\":\"DEAN_ACADEMICS\","
                + "\"classification\":\"CONFIDENTIAL\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(docJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"checksum\":\"SHA256-"));

        int idIdx = resp.indexOf("\"id\":\"") + 6;
        String docId = resp.substring(idIdx, resp.indexOf("\"", idIdx));

        // 2. Submit for approval
        URL apprUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/submit");
        HttpURLConnection apprConn = (HttpURLConnection) apprUrl.openConnection();
        apprConn.setRequestMethod("POST");

        assertEquals(200, apprConn.getResponseCode());
        String apprResp = readResponse(apprConn);
        assertTrue(apprResp.contains("\"status\":\"APPROVED\""));
    }

    @Test
    public void testDuplicateDepartmentCodeConflict409() throws Exception {
        String depJson = "{"
                + "\"departmentCode\":\"CIVIL\","
                + "\"name\":\"Civil Engineering\","
                + "\"headUserId\":\"FAC_HOD_CIVIL\""
                + "}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments");

        // First creation succeeds
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // Duplicate creation fails with 409 Conflict
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn2.getResponseCode();
        assertEquals(409, code);

        String errResp = readResponse(conn2);
        assertTrue(errResp.contains("\"status\":409"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_DUPLICATE_RESOURCE\""));
        assertTrue(errResp.contains("\"error\":\"Conflict\""));
        assertTrue(errResp.contains("CIVIL"));
    }

    @Test
    public void testRetireNonExistentDepartment404() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments/NON_EXISTENT_DEP");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":404"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_RESOURCE_NOT_FOUND\""));
        assertTrue(errResp.contains("\"error\":\"Not Found\""));
    }

    @Test
    public void testRetireDepartmentWithActiveProgramsLifecycle422() throws Exception {
        // CSE has active program 'B.Tech Computer Science and Engineering'
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments/CSE");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");

        int code = conn.getResponseCode();
        assertEquals(422, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":422"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_INVALID_LIFECYCLE_STATE\""));
        assertTrue(errResp.contains("actively referenced"));
    }

    @Test
    public void testDocumentWithoutClassification400() throws Exception {
        String docJson = "{"
                + "\"documentType\":\"AUDIT_REPORT\","
                + "\"title\":\"Internal Financial Audit 2026\","
                + "\"ownerId\":\"FINANCE_OFFICER\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(docJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_DOCUMENT_GOVERNANCE_ERROR\""));
    }

    @Test
    public void testMissingDepartmentCodeReturns400() throws Exception {
        String depJson = "{\"name\":\"Department Without Code\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_MALFORMED_PAYLOAD\""));
    }

    @Test
    public void testNonExistentImportJobReturns404() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/imports/JOB_NON_EXISTENT");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":404"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_RESOURCE_NOT_FOUND\""));
    }

    // =========================================================================
    // Phase 1: College RBAC & Identity Tests (User Story Lines 12–16)
    // =========================================================================

    @Test
    public void testRegisterCollegeUser() throws Exception {
        String payload = "{"
                + "\"userId\":\"iam_prof_smith\","
                + "\"displayName\":\"Professor Smith\","
                + "\"employeeRef\":\"EMP-2026-001\","
                + "\"departmentId\":\"DEP_CS\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"userId\":\"iam_prof_smith\""));
        assertTrue(resp.contains("\"displayName\":\"Professor Smith\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    @Test
    public void testRegisterCollegeUserMissingUserIdReturns400() throws Exception {
        String payload = "{"
                + "\"displayName\":\"No IAM User\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ADM02_MALFORMED_PAYLOAD"));
    }

    @Test
    public void testRegisterCollegeUserDuplicateUserIdReturns409() throws Exception {
        String payload = "{"
                + "\"userId\":\"iam_prof_dup\","
                + "\"displayName\":\"Duplicate User\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/users");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(409, conn2.getResponseCode());
        String resp = readResponse(conn2);
        assertTrue(resp.contains("ADM02_DUPLICATE_RESOURCE"));
    }

    @Test
    public void testCreateCollegeRole() throws Exception {
        String payload = "{"
                + "\"roleCode\":\"COL_HOD_CS\","
                + "\"name\":\"Head of CS Department\","
                + "\"permissions\":\"APPROVE_SYLLABUS,ASSIGN_FACULTY\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/roles");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"roleCode\":\"COL_HOD_CS\""));
        assertTrue(resp.contains("\"version\":1"));
    }

    @Test
    public void testDeleteProtectedCollegeRoleBlocked() throws Exception {
        // Create protected role
        String payload = "{"
                + "\"roleCode\":\"COL_DEAN_PROTECTED\","
                + "\"name\":\"Dean of Faculty\","
                + "\"protectedSystemRole\":\"true\""
                + "}";

        URL createUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/roles");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        int idStart = createResp.indexOf("\"id\":\"") + 6;
        int idEnd = createResp.indexOf("\"", idStart);
        String roleId = createResp.substring(idStart, idEnd);

        // Try to delete protected role — should be blocked with 422
        URL deleteUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/roles/" + roleId);
        HttpURLConnection deleteConn = (HttpURLConnection) deleteUrl.openConnection();
        deleteConn.setRequestMethod("DELETE");
        assertEquals(422, deleteConn.getResponseCode());
        String errResp = readResponse(deleteConn);
        assertTrue(errResp.contains("ADM02_INVALID_LIFECYCLE_STATE"));
        assertTrue(errResp.contains("protected system role"));
    }

    @Test
    public void testCreateCollegePermissionAndDuplicateCheck() throws Exception {
        String payload = "{"
                + "\"permissionCode\":\"SUBMIT_GRADE_SHEET\","
                + "\"resource\":\"ACADEMIC\","
                + "\"action\":\"SUBMIT\","
                + "\"description\":\"Submit semester grades\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/permissions");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());
        String resp = readResponse(conn1);
        assertTrue(resp.contains("\"permissionCode\":\"SUBMIT_GRADE_SHEET\""));

        // Duplicate permission check
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(409, conn2.getResponseCode());
    }

    @Test
    public void testCreateAndRevokeCollegeRoleBinding() throws Exception {
        // First create a role to bind
        String rolePayload = "{"
                + "\"roleCode\":\"COL_LAB_INCHARGE\","
                + "\"name\":\"Lab In-charge\""
                + "}";
        URL roleUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/roles");
        HttpURLConnection roleConn = (HttpURLConnection) roleUrl.openConnection();
        roleConn.setRequestMethod("POST");
        roleConn.setDoOutput(true);
        roleConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = roleConn.getOutputStream()) {
            os.write(rolePayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, roleConn.getResponseCode());
        String roleResp = readResponse(roleConn);
        int idStart = roleResp.indexOf("\"id\":\"") + 6;
        int idEnd = roleResp.indexOf("\"", idStart);
        String roleId = roleResp.substring(idStart, idEnd);

        // Bind role to principal
        String bindPayload = "{"
                + "\"principalId\":\"fac_rajesh_01\","
                + "\"roleId\":\"" + roleId + "\","
                + "\"scopeType\":\"DEPARTMENT\","
                + "\"scopeId\":\"DEP_CS\""
                + "}";
        URL bindUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/role-bindings");
        HttpURLConnection bindConn = (HttpURLConnection) bindUrl.openConnection();
        bindConn.setRequestMethod("POST");
        bindConn.setDoOutput(true);
        bindConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = bindConn.getOutputStream()) {
            os.write(bindPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, bindConn.getResponseCode());
        String bindResp = readResponse(bindConn);
        assertTrue(bindResp.contains("\"principalId\":\"fac_rajesh_01\""));
        assertTrue(bindResp.contains("\"scopeType\":\"DEPARTMENT\""));

        int bIdStart = bindResp.indexOf("\"id\":\"") + 6;
        int bIdEnd = bindResp.indexOf("\"", bIdStart);
        String bindingId = bindResp.substring(bIdStart, bIdEnd);

        // Revoke binding
        URL revokeUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/role-bindings/" + bindingId);
        HttpURLConnection revokeConn = (HttpURLConnection) revokeUrl.openConnection();
        revokeConn.setRequestMethod("DELETE");
        assertEquals(200, revokeConn.getResponseCode());
        String revokeResp = readResponse(revokeConn);
        assertTrue(revokeResp.contains("\"status\":\"REVOKED\""));
    }

    @Test
    public void testCollegeAccessReviewAndSelfCertificationBlocked() throws Exception {
        // 1. Create review for principal user_carol
        String createPayload = "{"
                + "\"principalId\":\"user_carol\","
                + "\"roleBindingId\":\"BIND-001\","
                + "\"dueAt\":\"" + (System.currentTimeMillis() + 604800000L) + "\""
                + "}";

        URL createUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/access/reviews");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        assertTrue(createResp.contains("\"status\":\"OPEN\""));
        int rIdStart = createResp.indexOf("\"reviewId\":\"") + 12;
        int rIdEnd = createResp.indexOf("\"", rIdStart);
        String reviewId = createResp.substring(rIdStart, rIdEnd);

        // 2. Self-certification attempt: reviewerId == principalId ("user_carol")
        String selfReviewPayload = "{"
                + "\"reviewerId\":\"user_carol\","
                + "\"decision\":\"CERTIFY\""
                + "}";
        URL reviewUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/access/reviews/" + reviewId);
        HttpURLConnection selfConn = (HttpURLConnection) reviewUrl.openConnection();
        selfConn.setRequestMethod("PUT");
        selfConn.setDoOutput(true);
        selfConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = selfConn.getOutputStream()) {
            os.write(selfReviewPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(422, selfConn.getResponseCode());
        String selfErrResp = readResponse(selfConn);
        assertTrue(selfErrResp.contains("ADM02_INVALID_LIFECYCLE_STATE"));
        assertTrue(selfErrResp.contains("Separation of duties"));

        // 3. Valid certification by independent reviewer
        String validReviewPayload = "{"
                + "\"reviewerId\":\"dean_patel\","
                + "\"decision\":\"CERTIFY\""
                + "}";
        HttpURLConnection validConn = (HttpURLConnection) reviewUrl.openConnection();
        validConn.setRequestMethod("PUT");
        validConn.setDoOutput(true);
        validConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = validConn.getOutputStream()) {
            os.write(validReviewPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, validConn.getResponseCode());
        String validResp = readResponse(validConn);
        assertTrue(validResp.contains("\"status\":\"COMPLETED\""));
        assertTrue(validResp.contains("\"decision\":\"CERTIFY\""));
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Tests (User Story Lines 35–38)
    // =========================================================================

    @Test
    public void testCollegeOutboxEventsEmitted() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/events/outbox");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"outbox\":["));
    }

    @Test
    public void testCollegeInboxDeduplication() throws Exception {
        String payload = "{"
                + "\"eventId\":\"INB_COL_ACD07_CAL_01\","
                + "\"sourceService\":\"ACD-07\","
                + "\"consumerGroup\":\"ADM-02\","
                + "\"payload\":\"{\\\"action\\\":\\\"ACADEMIC_CALENDAR_CHANGED\\\"}\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/events/inbox");
        // 1st consumption
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) { os.write(payload.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, conn1.getResponseCode());
        String resp1 = readResponse(conn1);
        assertTrue(resp1.contains("\"status\":\"PROCESSED\""));

        // 2nd consumption (duplicate) - should succeed idempotently
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) { os.write(payload.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, conn2.getResponseCode());
        String resp2 = readResponse(conn2);
        assertTrue(resp2.contains("\"status\":\"PROCESSED\""));
    }

    @Test
    public void testCollegeDeadLetterQueueAndReplay() throws Exception {
        domainService.routeToDeadLetter("ERR_DOC_SYNC_001", "DocumentSync", "CHECKSUM_MISMATCH", 3, "{\"docId\":\"DOC-1\"}");

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/events/dead-letter");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ERR_DOC_SYNC_001"));
        assertTrue(resp.contains("CHECKSUM_MISMATCH"));

        int idStart = resp.indexOf("\"id\":\"") + 6;
        int idEnd = resp.indexOf("\"", idStart);
        String dlqId = resp.substring(idStart, idEnd);

        URL replayUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/events/dead-letter/" + dlqId + "/replay");
        HttpURLConnection replayConn = (HttpURLConnection) replayUrl.openConnection();
        replayConn.setRequestMethod("POST");
        assertEquals(200, replayConn.getResponseCode());
        String replayResp = readResponse(replayConn);
        assertTrue(replayResp.contains("\"status\":\"REPLAYED\""));
    }

    @Test
    public void testCollegeAuditTamperEvidentHashChain() {
        List<Map<String, Object>> auditTrail = domainService.getAuditTrail();
        assertTrue("Audit trail should not be empty", auditTrail.size() > 0);
        for (Map<String, Object> entry : auditTrail) {
            assertTrue("Audit entry must contain beforeHash", entry.containsKey("beforeHash"));
            assertTrue("Audit entry must contain afterHash", entry.containsKey("afterHash"));
            assertEquals("afterHash must be SHA-256 hex string", 64, ((String) entry.get("afterHash")).length());
        }
    }

    @Test
    public void testCollegeIdempotencyHashMismatchConflict() {
        String key = "COL-IDEMP-TEST-KEY-001";
        domainService.checkOrRecordIdempotency(key, "TEST_OP", "HASH_AAA", 3600000L);

        try {
            domainService.checkOrRecordIdempotency(key, "TEST_OP", "HASH_DIFFERENT", 3600000L);
            org.junit.Assert.fail("Expected CollegeResourceConflictException on hash mismatch");
        } catch (com.campx.admin.college.exception.CollegeResourceConflictException e) {
            assertTrue(e.getMessage().contains("Payload hash mismatch"));
        }
    }

    // =========================================================================
    // Phase 3 Tests: Configuration & Governance (ADM-02)
    // =========================================================================

    @Test
    public void testCollegeSettingsWithSecretMasking() throws Exception {
        // Secret setting
        String secretJson = "{\"key\":\"payment.gateway.secret\",\"value\":\"SUPER_CONFIDENTIAL_KEY\",\"dataType\":\"STRING\",\"isSecret\":true}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/settings");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(secretJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(200, conn.getResponseCode());

        // Get settings and verify value is masked
        HttpURLConnection getConn = (HttpURLConnection) url.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("payment.gateway.secret"));
        assertTrue(getResp.contains("********"));
        assertTrue(!getResp.contains("SUPER_CONFIDENTIAL_KEY"));
    }

    @Test
    public void testCollegeFeatureOverrideAndSafetyLockRejection() throws Exception {
        // Normal override succeeds
        String overrideJson = "{\"flagKey\":\"STUDENT_PORTAL_V2\",\"overrideValue\":\"true\",\"reason\":\"Department trial\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/feature-overrides");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(overrideJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"APPLIED\""));

        // Safety lock override blocked
        String safetyJson = "{\"flagKey\":\"SAFETY_CRITICAL_MODE\",\"overrideValue\":\"false\"}";
        HttpURLConnection safeConn = (HttpURLConnection) url.openConnection();
        safeConn.setRequestMethod("POST");
        safeConn.setDoOutput(true);
        safeConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = safeConn.getOutputStream()) { os.write(safetyJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(400, safeConn.getResponseCode());
        String safeErr = readResponse(safeConn);
        assertTrue(safeErr.contains("ADM02_FLAG_OVERRIDE_BLOCKED"));
    }

    @Test
    public void testLocalPolicyApprovalAndPublication() throws Exception {
        String policyJson = "{\"policyCode\":\"LAB_SAFETY_STANDARD\",\"rules\":\"HELMET_REQUIRED,COAT_REQUIRED\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/policies");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(policyJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, conn.getResponseCode());

        // Attempt publish before approval -> 422
        URL pubUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/policies/LAB_SAFETY_STANDARD/publish");
        HttpURLConnection unapprovedPub = (HttpURLConnection) pubUrl.openConnection();
        unapprovedPub.setRequestMethod("POST");
        assertEquals(422, unapprovedPub.getResponseCode());

        // Approve policy
        URL appUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/policies/LAB_SAFETY_STANDARD/approve");
        HttpURLConnection appConn = (HttpURLConnection) appUrl.openConnection();
        appConn.setRequestMethod("POST");
        appConn.setDoOutput(true);
        appConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = appConn.getOutputStream()) { os.write("{\"approvedBy\":\"DEAN_ACADEMICS\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, appConn.getResponseCode());

        // Publish policy
        HttpURLConnection pubConn = (HttpURLConnection) pubUrl.openConnection();
        pubConn.setRequestMethod("POST");
        assertEquals(200, pubConn.getResponseCode());
        String pubResp = readResponse(pubConn);
        assertTrue(pubResp.contains("\"status\":\"PUBLISHED\""));
    }

    // =========================================================================
    // Phase 6 Tests: Operations, Reporting & Workflows (ADM-02)
    // =========================================================================

    @Test
    public void testCalendarConfigurationSync() throws Exception {
        String configJson = "{"
                + "\"sourceCalendarId\":\"CAL_UNIV_2026\","
                + "\"syncMode\":\"AUTOMATIC\","
                + "\"localRules\":\"CAMPUS_HOLIDAYS\""
                + "}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/calendar/config");
        HttpURLConnection putConn = (HttpURLConnection) url.openConnection();
        putConn.setRequestMethod("PUT");
        putConn.setDoOutput(true);
        putConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = putConn.getOutputStream()) {
            os.write(configJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, putConn.getResponseCode());
        String putResp = readResponse(putConn);
        assertTrue(putResp.contains("\"syncMode\":\"AUTOMATIC\""));
        assertTrue(putResp.contains("CAL_UNIV_2026"));

        // GET calendar config
        HttpURLConnection getConn = (HttpURLConnection) url.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("CAL_UNIV_2026"));
        assertTrue(getResp.contains("CAMPUS_HOLIDAYS"));
    }

    @Test
    public void testReportDefinitionExecutionAndScheduling() throws Exception {
        // 1. Create Report Definition
        String repJson = "{"
                + "\"reportCode\":\"REP_STUDENT_ENROLLMENT\","
                + "\"reportName\":\"Semester Enrollment Report\","
                + "\"querySpec\":\"SELECT * FROM ENROLLMENTS\","
                + "\"dataClass\":\"CONFIDENTIAL\""
                + "}";
        URL createUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/reports");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(repJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());

        // 2. List Report Definitions
        HttpURLConnection listConn = (HttpURLConnection) createUrl.openConnection();
        listConn.setRequestMethod("GET");
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("REP_STUDENT_ENROLLMENT"));

        // 3. Execute Report
        URL execUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/reports/REP_STUDENT_ENROLLMENT/execute");
        HttpURLConnection execConn = (HttpURLConnection) execUrl.openConnection();
        execConn.setRequestMethod("POST");
        execConn.setDoOutput(true);
        execConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = execConn.getOutputStream()) {
            os.write("{\"requestedBy\":\"REGISTRAR\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, execConn.getResponseCode());
        String execResp = readResponse(execConn);
        assertTrue(execResp.contains("\"status\":\"COMPLETED\""));
        assertTrue(execResp.contains("s3://campx-reports/REP_STUDENT_ENROLLMENT/"));

        // 4. List Report Runs
        URL runsUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/reports/REP_STUDENT_ENROLLMENT/runs");
        HttpURLConnection runsConn = (HttpURLConnection) runsUrl.openConnection();
        runsConn.setRequestMethod("GET");
        assertEquals(200, runsConn.getResponseCode());
        String runsResp = readResponse(runsConn);
        assertTrue(runsResp.contains("\"COMPLETED\""));

        // 5. Schedule Report
        String schedJson = "{"
                + "\"reportCode\":\"REP_STUDENT_ENROLLMENT\","
                + "\"cronExpression\":\"0 0 1 * *\","
                + "\"timezone\":\"UTC\""
                + "}";
        URL schedUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/reports/schedules");
        HttpURLConnection schedConn = (HttpURLConnection) schedUrl.openConnection();
        schedConn.setRequestMethod("POST");
        schedConn.setDoOutput(true);
        schedConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = schedConn.getOutputStream()) {
            os.write(schedJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, schedConn.getResponseCode());
        String schedResp = readResponse(schedConn);
        assertTrue(schedResp.contains("0 0 1 * *"));

        // 6. List Schedules
        HttpURLConnection listSchedConn = (HttpURLConnection) schedUrl.openConnection();
        listSchedConn.setRequestMethod("GET");
        assertEquals(200, listSchedConn.getResponseCode());
        String listSchedResp = readResponse(listSchedConn);
        assertTrue(listSchedResp.contains("REP_STUDENT_ENROLLMENT"));
    }

    @Test
    public void testDashboardSnapshots() throws Exception {
        String snapJson = "{"
                + "\"dashboardCode\":\"EXEC_DASHBOARD\","
                + "\"period\":\"2026-Q1\","
                + "\"metrics\":\"{\\\"activeStudents\\\":1250,\\\"facultyCount\\\":85}\""
                + "}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/dashboards/snapshots");
        HttpURLConnection createConn = (HttpURLConnection) url.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(snapJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());

        // Retrieve snapshots by query param
        URL queryUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/dashboards/snapshots?dashboardCode=EXEC_DASHBOARD");
        HttpURLConnection getConn = (HttpURLConnection) queryUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("2026-Q1"));
        assertTrue(getResp.contains("activeStudents"));
    }

    @Test
    public void testApprovalRequestWorkflowAndSelfCertificationBlocked() throws Exception {
        // 1. Submit approval request
        String reqJson = "{"
                + "\"requestType\":\"CURRICULUM_REVISION\","
                + "\"subjectType\":\"COURSE\","
                + "\"subjectId\":\"CS101\","
                + "\"submittedBy\":\"HOD_CS\""
                + "}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/approvals");
        HttpURLConnection postConn = (HttpURLConnection) url.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(reqJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String postResp = readResponse(postConn);
        assertTrue(postResp.contains("\"status\":\"PENDING\""));
        int idStart = postResp.indexOf("\"id\":\"") + 6;
        int idEnd = postResp.indexOf("\"", idStart);
        String reqId = postResp.substring(idStart, idEnd);

        // 2. Self-certification attempt: approverId == submittedBy ("HOD_CS") -> 400 Bad Request
        URL decideUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/approvals/" + reqId + "/decide");
        HttpURLConnection selfConn = (HttpURLConnection) decideUrl.openConnection();
        selfConn.setRequestMethod("POST");
        selfConn.setDoOutput(true);
        selfConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = selfConn.getOutputStream()) {
            os.write("{\"approverId\":\"HOD_CS\",\"decision\":\"APPROVE\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(400, selfConn.getResponseCode());
        String selfResp = readResponse(selfConn);
        assertTrue(selfResp.contains("ADM02_SELF_CERTIFICATION_BLOCKED"));

        // 3. Valid independent decision -> 200 OK
        HttpURLConnection validConn = (HttpURLConnection) decideUrl.openConnection();
        validConn.setRequestMethod("POST");
        validConn.setDoOutput(true);
        validConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = validConn.getOutputStream()) {
            os.write("{\"approverId\":\"DEAN_ACADEMICS\",\"decision\":\"APPROVE\",\"notes\":\"Approved by board\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, validConn.getResponseCode());
        String validResp = readResponse(validConn);
        assertTrue(validResp.contains("\"status\":\"APPROVED\""));

        // 4. List approvals by status
        URL listUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/approvals?status=APPROVED");
        HttpURLConnection listConn = (HttpURLConnection) listUrl.openConnection();
        listConn.setRequestMethod("GET");
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("CURRICULUM_REVISION"));
    }

    @Test
    public void testCollegeWorkflowCoordinationAndCompensation() throws Exception {
        // 1. Start college workflow
        String startJson = "{"
                + "\"workflowType\":\"SEMESTER_INITIALIZATION\","
                + "\"subject\":\"Spring 2026 Registration\""
                + "}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/workflows");
        HttpURLConnection startConn = (HttpURLConnection) url.openConnection();
        startConn.setRequestMethod("POST");
        startConn.setDoOutput(true);
        startConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = startConn.getOutputStream()) {
            os.write(startJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, startConn.getResponseCode());
        String startResp = readResponse(startConn);
        assertTrue(startResp.contains("\"status\":\"INITIATED\""));
        int idStart = startResp.indexOf("\"id\":\"") + 6;
        int idEnd = startResp.indexOf("\"", idStart);
        String wfId = startResp.substring(idStart, idEnd);

        // 2. Transition workflow with compensation
        String transJson = "{"
                + "\"status\":\"COMPENSATING\","
                + "\"stepName\":\"ROLLBACK_REGISTRATIONS\","
                + "\"compensationAction\":\"REVOKE_TIMETABLES\""
                + "}";
        URL transUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/workflows/" + wfId);
        HttpURLConnection transConn = (HttpURLConnection) transUrl.openConnection();
        transConn.setRequestMethod("POST");
        transConn.setDoOutput(true);
        transConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = transConn.getOutputStream()) {
            os.write(transJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, transConn.getResponseCode());
        String transResp = readResponse(transConn);
        assertTrue(transResp.contains("\"status\":\"COMPENSATING\""));

        // 3. GET workflow
        HttpURLConnection getConn = (HttpURLConnection) transUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("\"status\":\"COMPENSATING\""));
        assertTrue(getResp.contains("SEMESTER_INITIALIZATION"));

        // 4. List workflows
        HttpURLConnection listConn = (HttpURLConnection) url.openConnection();
        listConn.setRequestMethod("GET");
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("SEMESTER_INITIALIZATION"));
    }

    // =========================================================================
    // Block B Tests: Document Governance Refinement (ADM-02)
    // =========================================================================

    @Test
    public void testDocumentVersionImmutabilityAfterPublish() throws Exception {
        // 1. Create Document
        String docJson = "{\"documentType\":\"POLICY\",\"title\":\"Exam Code of Conduct\",\"ownerId\":\"DEAN_01\",\"classification\":\"INTERNAL\"}";
        URL docUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection docConn = (HttpURLConnection) docUrl.openConnection();
        docConn.setRequestMethod("POST");
        docConn.setDoOutput(true);
        docConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = docConn.getOutputStream()) { os.write(docJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, docConn.getResponseCode());
        String docResp = readResponse(docConn);
        int idIdx = docResp.indexOf("\"id\":\"") + 6;
        String docId = docResp.substring(idIdx, docResp.indexOf("\"", idIdx));

        // 2. Create Version 1
        String verJson = "{\"objectRef\":\"s3://campx-docs/" + docId + "/v1.pdf\",\"createdBy\":\"POLICY_COMMITTEE\"}";
        URL verUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/versions");
        HttpURLConnection verConn = (HttpURLConnection) verUrl.openConnection();
        verConn.setRequestMethod("POST");
        verConn.setDoOutput(true);
        verConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = verConn.getOutputStream()) { os.write(verJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, verConn.getResponseCode());
        String verResp = readResponse(verConn);
        assertTrue(verResp.contains("\"versionNo\":1"));
        assertTrue(verResp.contains("\"status\":\"DRAFT\""));

        // 3. Publish Version 1
        URL pubUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/versions/1/publish");
        HttpURLConnection pubConn = (HttpURLConnection) pubUrl.openConnection();
        pubConn.setRequestMethod("POST");
        assertEquals(200, pubConn.getResponseCode());
        String pubResp = readResponse(pubConn);
        assertTrue(pubResp.contains("\"status\":\"PUBLISHED\""));

        // 4. Re-publish attempt should be rejected with 422 Unprocessable Entity (immutability enforcement)
        HttpURLConnection repubConn = (HttpURLConnection) pubUrl.openConnection();
        repubConn.setRequestMethod("POST");
        assertEquals(422, repubConn.getResponseCode());
    }

    @Test
    public void testDocumentPermissionTimeBound() throws Exception {
        // 1. Create Document
        String docJson = "{\"documentType\":\"REGULATION\",\"title\":\"Lab Safety Regulations\",\"ownerId\":\"SAFETY_HEAD\",\"classification\":\"RESTRICTED\"}";
        URL docUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection docConn = (HttpURLConnection) docUrl.openConnection();
        docConn.setRequestMethod("POST");
        docConn.setDoOutput(true);
        docConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = docConn.getOutputStream()) { os.write(docJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, docConn.getResponseCode());
        String docResp = readResponse(docConn);
        int idIdx = docResp.indexOf("\"id\":\"") + 6;
        String docId = docResp.substring(idIdx, docResp.indexOf("\"", idIdx));

        // 2. Grant active time-bound permission
        String permActiveJson = "{\"principalType\":\"USER\",\"principalId\":\"RESEARCHER_ALICE\",\"permission\":\"VIEW\",\"effectiveTo\":" + (System.currentTimeMillis() + 86400000L) + "}";
        URL permUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/permissions");
        HttpURLConnection pConn = (HttpURLConnection) permUrl.openConnection();
        pConn.setRequestMethod("POST");
        pConn.setDoOutput(true);
        pConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = pConn.getOutputStream()) { os.write(permActiveJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, pConn.getResponseCode());
        String pResp = readResponse(pConn);
        int pIdIdx = pResp.indexOf("\"id\":\"") + 6;
        String permId = pResp.substring(pIdIdx, pResp.indexOf("\"", pIdIdx));

        // 3. Grant expired permission (effectiveTo in past)
        String permExpiredJson = "{\"principalType\":\"USER\",\"principalId\":\"EXPIRED_USER_BOB\",\"permission\":\"VIEW\",\"effectiveTo\":1000}";
        HttpURLConnection pExpConn = (HttpURLConnection) permUrl.openConnection();
        pExpConn.setRequestMethod("POST");
        pExpConn.setDoOutput(true);
        pExpConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = pExpConn.getOutputStream()) { os.write(permExpiredJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, pExpConn.getResponseCode());

        // 4. List permissions — active researcher must be present, expired user must NOT
        HttpURLConnection listConn = (HttpURLConnection) permUrl.openConnection();
        listConn.setRequestMethod("GET");
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("RESEARCHER_ALICE"));
        assertFalse(listResp.contains("EXPIRED_USER_BOB"));

        // 5. Revoke active permission
        URL revokeUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/permissions/" + permId);
        HttpURLConnection revConn = (HttpURLConnection) revokeUrl.openConnection();
        revConn.setRequestMethod("DELETE");
        assertEquals(200, revConn.getResponseCode());

        // 6. Verify revoked permission is no longer returned as active
        HttpURLConnection afterListConn = (HttpURLConnection) permUrl.openConnection();
        afterListConn.setRequestMethod("GET");
        assertEquals(200, afterListConn.getResponseCode());
        String afterResp = readResponse(afterListConn);
        assertFalse(afterResp.contains("RESEARCHER_ALICE"));
    }

    @Test
    public void testDocumentApprovalImmutableAfterDecision() throws Exception {
        // 1. Create Document
        String docJson = "{\"documentType\":\"GUIDELINE\",\"title\":\"Grading Guidelines\",\"ownerId\":\"ACAD_HEAD\",\"classification\":\"INTERNAL\"}";
        URL docUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection docConn = (HttpURLConnection) docUrl.openConnection();
        docConn.setRequestMethod("POST");
        docConn.setDoOutput(true);
        docConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = docConn.getOutputStream()) { os.write(docJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, docConn.getResponseCode());
        String docResp = readResponse(docConn);
        int idIdx = docResp.indexOf("\"id\":\"") + 6;
        String docId = docResp.substring(idIdx, docResp.indexOf("\"", idIdx));

        // 2. Create Version 1
        String verJson = "{\"objectRef\":\"s3://campx-docs/" + docId + "/v1.pdf\",\"createdBy\":\"AUTHOR_CAROL\"}";
        URL verUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/versions");
        HttpURLConnection verConn = (HttpURLConnection) verUrl.openConnection();
        verConn.setRequestMethod("POST");
        verConn.setDoOutput(true);
        verConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = verConn.getOutputStream()) { os.write(verJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, verConn.getResponseCode());

        // 3. Submit Version for Approval
        String subJson = "{\"versionNo\":1,\"submittedBy\":\"AUTHOR_CAROL\",\"approverId\":\"DEAN_OFFICE\"}";
        URL subUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/submit-version");
        HttpURLConnection subConn = (HttpURLConnection) subUrl.openConnection();
        subConn.setRequestMethod("POST");
        subConn.setDoOutput(true);
        subConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = subConn.getOutputStream()) { os.write(subJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, subConn.getResponseCode());
        String subResp = readResponse(subConn);
        int aIdIdx = subResp.indexOf("\"id\":\"") + 6;
        String approvalId = subResp.substring(aIdIdx, subResp.indexOf("\"", aIdIdx));

        // 4. Decide Approval -> APPROVED
        String decJson = "{\"decision\":\"APPROVED\",\"approverId\":\"DEAN_OFFICE\",\"comments\":\"Approved without revisions\"}";
        URL decUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/approvals/" + approvalId + "/decide");
        HttpURLConnection decConn = (HttpURLConnection) decUrl.openConnection();
        decConn.setRequestMethod("POST");
        decConn.setDoOutput(true);
        decConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = decConn.getOutputStream()) { os.write(decJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, decConn.getResponseCode());
        String decResp = readResponse(decConn);
        assertTrue(decResp.contains("\"decision\":\"APPROVED\""));

        // 5. Attempt second decision -> 409 Conflict (decision immutability)
        HttpURLConnection redecConn = (HttpURLConnection) decUrl.openConnection();
        redecConn.setRequestMethod("POST");
        redecConn.setDoOutput(true);
        redecConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = redecConn.getOutputStream()) { os.write("{\"decision\":\"REJECTED\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(409, redecConn.getResponseCode());
    }

    @Test
    public void testDocumentApprovalAutoPublishesVersion() throws Exception {
        // 1. Create Document
        String docJson = "{\"documentType\":\"SYLLABUS\",\"title\":\"CS Curriculum 2026\",\"ownerId\":\"CS_CHAIR\",\"classification\":\"PUBLIC\"}";
        URL docUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection docConn = (HttpURLConnection) docUrl.openConnection();
        docConn.setRequestMethod("POST");
        docConn.setDoOutput(true);
        docConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = docConn.getOutputStream()) { os.write(docJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, docConn.getResponseCode());
        String docResp = readResponse(docConn);
        int idIdx = docResp.indexOf("\"id\":\"") + 6;
        String docId = docResp.substring(idIdx, docResp.indexOf("\"", idIdx));

        // 2. Create Version 1
        String verJson = "{\"objectRef\":\"s3://campx-docs/" + docId + "/v1.pdf\",\"createdBy\":\"CS_PROF\"}";
        URL verUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/versions");
        HttpURLConnection verConn = (HttpURLConnection) verUrl.openConnection();
        verConn.setRequestMethod("POST");
        verConn.setDoOutput(true);
        verConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = verConn.getOutputStream()) { os.write(verJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, verConn.getResponseCode());

        // 3. Submit Version for Approval
        String subJson = "{\"versionNo\":1,\"submittedBy\":\"CS_PROF\"}";
        URL subUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/submit-version");
        HttpURLConnection subConn = (HttpURLConnection) subUrl.openConnection();
        subConn.setRequestMethod("POST");
        subConn.setDoOutput(true);
        subConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = subConn.getOutputStream()) { os.write(subJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, subConn.getResponseCode());
        String subResp = readResponse(subConn);
        int aIdIdx = subResp.indexOf("\"id\":\"") + 6;
        String approvalId = subResp.substring(aIdIdx, subResp.indexOf("\"", aIdIdx));

        // 4. Approve Decision
        URL decUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/approvals/" + approvalId + "/decide");
        HttpURLConnection decConn = (HttpURLConnection) decUrl.openConnection();
        decConn.setRequestMethod("POST");
        decConn.setDoOutput(true);
        decConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = decConn.getOutputStream()) { os.write("{\"decision\":\"APPROVED\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, decConn.getResponseCode());

        // 5. Verify Version status is automatically PUBLISHED
        HttpURLConnection listVerConn = (HttpURLConnection) verUrl.openConnection();
        listVerConn.setRequestMethod("GET");
        assertEquals(200, listVerConn.getResponseCode());
        String listVerResp = readResponse(listVerConn);
        assertTrue(listVerResp.contains("\"status\":\"PUBLISHED\""));
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
