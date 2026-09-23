package com.campx.admin.institute;

import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.admin.institute.service.InstituteAdminDomainService;
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
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration test suite for ADM-01 Institute Admin Service.
 * <p>
 * Boots the embedded HTTP server on test port 8091 and tests all platform capabilities:
 * <ul>
 *   <li>Institute & College tenant onboarding lifecycle</li>
 *   <li>Commercial pricing plans and tenant subscriptions</li>
 *   <li>Global configuration settings and versioning</li>
 *   <li>Transactional outbox/inbox reliability and dead letter queue</li>
 *   <li>RBAC role bindings and privileged access reviews</li>
 *   <li>Tamper-evident audit chain integrity verification</li>
 * </ul>
 */
public class InstituteAdminServiceTest {

    private static InstituteAdminServer server;
    private static InstituteAdminDomainService domainService;
    private static final int TEST_PORT = 8091;

    /**
     * Initializes domain service, starts the HTTP server on test port 8091, and awaits readiness.
     *
     * @throws Exception if socket creation or server bootstrap fails
     */
    @BeforeClass
    public static void setup() throws Exception {
        domainService = new InstituteAdminDomainService();
        server = new InstituteAdminServer(TEST_PORT, domainService);
        server.start();
    }

    /**
     * Halts the HTTP server and flushes asynchronous log appenders upon test suite completion.
     */
    @AfterClass
    public static void teardown() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testRegisterInstituteAndColleges() throws Exception {
        // 1. Create Institute
        String createJson = "{"
                + "\"instituteCode\":\"INST_TEST_01\","
                + "\"legalName\":\"National Institute of Technology\","
                + "\"displayName\":\"NIT Central\","
                + "\"timezone\":\"Asia/Kolkata\","
                + "\"locale\":\"en_IN\","
                + "\"defaultCurrency\":\"INR\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(createJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"instituteCode\":\"INST_TEST_01\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));

        // Extract id
        int idIdx = resp.indexOf("\"id\":\"") + 6;
        String instId = resp.substring(idIdx, resp.indexOf("\"", idIdx));

        // 2. Register College under Institute
        String collegeJson = "{"
                + "\"collegeCode\":\"NIT_ENGG\","
                + "\"name\":\"College of Engineering\","
                + "\"instituteId\":\"" + instId + "\""
                + "}";

        URL colUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/colleges");
        HttpURLConnection colConn = (HttpURLConnection) colUrl.openConnection();
        colConn.setRequestMethod("POST");
        colConn.setDoOutput(true);
        colConn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = colConn.getOutputStream()) {
            os.write(collegeJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, colConn.getResponseCode());
        String colResp = readResponse(colConn);
        assertTrue(colResp.contains("\"collegeCode\":\"NIT_ENGG\""));
    }

    @Test
    public void testTenantProvisioningWorkflow() throws Exception {
        String provJson = "{"
                + "\"targetScope\":\"CAMPUS_NORTH\","
                + "\"planId\":\"ENTERPRISE_CAMPUS_2026\","
                + "\"idempotencyKey\":\"IDEM-PROV-9901\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/tenants/TENANT_NIT_01/provision");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(provJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"COMPLETED\""));
        assertTrue(resp.contains("\"tenantId\":\"TENANT_NIT_01\""));
    }

    @Test
    public void testDuplicateInstituteCodeConflict409() throws Exception {
        String json = "{"
                + "\"instituteCode\":\"INST_DUP_01\","
                + "\"legalName\":\"Duplicate Institute Test\","
                + "\"displayName\":\"Duplicate Inst\","
                + "\"timezone\":\"Asia/Kolkata\","
                + "\"locale\":\"en_IN\","
                + "\"defaultCurrency\":\"INR\""
                + "}";

        // First creation succeeds
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // Second creation with duplicate code must fail with 409 Conflict
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn2.getResponseCode();
        assertEquals(409, code);

        String errResp = readResponse(conn2);
        assertTrue(errResp.contains("\"status\":409"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_DUPLICATE_RESOURCE\""));
        assertTrue(errResp.contains("\"error\":\"Conflict\""));
        assertTrue(errResp.contains("INST_DUP_01"));
    }

    @Test
    public void testNonExistentInstituteUpdate404() throws Exception {
        String updateJson = "{\"displayName\":\"Non Existent Updated\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes/NON_EXISTENT_ID");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(updateJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":404"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_RESOURCE_NOT_FOUND\""));
        assertTrue(errResp.contains("\"error\":\"Not Found\""));
    }

    @Test
    public void testPlaintextSecretSecurityViolation400() throws Exception {
        String configJson = "{"
                + "\"key\":\"security.oauth2.client_secret\","
                + "\"value\":\"plain-unencrypted-secret\","
                + "\"isSecret\":\"true\","
                + "\"scope\":\"GLOBAL\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/configuration");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(configJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_PLAINTEXT_SECRET_REJECTED\""));
        assertTrue(errResp.contains("\"error\":\"Bad Request\""));
    }

    @Test
    public void testMissingInstituteCodeReturns400() throws Exception {
        String payload = "{"
                + "\"legalName\":\"No Code University\","
                + "\"displayName\":\"NCU\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_MALFORMED_PAYLOAD\""));
    }

    // =========================================================================
    // Phase 1: RBAC & Identity Tests (User Story Lines 17–21)
    // =========================================================================

    @Test
    public void testRegisterAdminUser() throws Exception {
        String payload = "{"
                + "\"userId\":\"iam_super_admin_01\","
                + "\"displayName\":\"Super Admin\","
                + "\"email\":\"superadmin@campx.edu\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"userId\":\"iam_super_admin_01\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    @Test
    public void testRegisterAdminUserUnresolvableIAM() throws Exception {
        String payload = "{"
                + "\"userId\":\"unknown_iam_user\","
                + "\"displayName\":\"Unknown User\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ADM01_IAM_REFERENCE_UNRESOLVABLE"));
    }

    @Test
    public void testCreatePlatformRole() throws Exception {
        String payload = "{"
                + "\"roleCode\":\"RBAC_TEST_ROLE\","
                + "\"name\":\"Test Role\","
                + "\"scope\":\"PLATFORM\","
                + "\"permissionCodes\":\"READ_INSTITUTES,WRITE_CONFIG\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/roles");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"roleCode\":\"RBAC_TEST_ROLE\""));
        assertTrue(resp.contains("\"version\":1"));
    }

    @Test
    public void testDeleteProtectedRoleBlocked() throws Exception {
        // First create a protected role
        String payload = "{"
                + "\"roleCode\":\"SUPER_ADMIN_SYS\","
                + "\"name\":\"Super Admin System\","
                + "\"scope\":\"PLATFORM\","
                + "\"protectedSystemRole\":\"true\""
                + "}";

        URL createUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/roles");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        // Extract roleId from response
        int idStart = createResp.indexOf("\"id\":\"") + 6;
        int idEnd = createResp.indexOf("\"", idStart);
        String roleId = createResp.substring(idStart, idEnd);

        // Try to delete — should fail
        URL deleteUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/roles/" + roleId);
        HttpURLConnection deleteConn = (HttpURLConnection) deleteUrl.openConnection();
        deleteConn.setRequestMethod("DELETE");
        assertEquals(400, deleteConn.getResponseCode());
        String errResp = readResponse(deleteConn);
        assertTrue(errResp.contains("ADM01_PROTECTED_ROLE_DELETE_BLOCKED"));
    }

    @Test
    public void testCreatePermission() throws Exception {
        String payload = "{"
                + "\"permissionCode\":\"MANAGE_INSTITUTES\","
                + "\"resource\":\"INSTITUTE\","
                + "\"action\":\"MANAGE\","
                + "\"description\":\"Full institute management\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/permissions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"permissionCode\":\"MANAGE_INSTITUTES\""));
    }

    @Test
    public void testDuplicatePermissionRejected() throws Exception {
        String payload = "{"
                + "\"permissionCode\":\"DUP_PERM_01\","
                + "\"resource\":\"CONFIG\","
                + "\"action\":\"READ\""
                + "}";

        // First creation
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/permissions");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) { os.write(payload.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn1.getResponseCode());

        // Second creation — should fail
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) { os.write(payload.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(409, conn2.getResponseCode());
    }

    @Test
    public void testAccessReviewCreation() throws Exception {
        String payload = "{"
                + "\"principalId\":\"user_alice\","
                + "\"tenantId\":\"TENANT_01\","
                + "\"dueAt\":\"" + (System.currentTimeMillis() + 604800000L) + "\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/access/reviews");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"OPEN\""));
        assertTrue(resp.contains("\"principalId\":\"user_alice\""));
    }

    @Test
    public void testSelfCertificationBlocked() throws Exception {
        // Create a review for user_bob
        String createPayload = "{"
                + "\"principalId\":\"user_bob\","
                + "\"tenantId\":\"TENANT_01\","
                + "\"dueAt\":\"" + (System.currentTimeMillis() + 604800000L) + "\""
                + "}";

        URL createUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/access/reviews");
        HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
        createConn.setRequestMethod("POST");
        createConn.setDoOutput(true);
        createConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        int rIdStart = createResp.indexOf("\"reviewId\":\"") + 12;
        int rIdEnd = createResp.indexOf("\"", rIdStart);
        String reviewId = createResp.substring(rIdStart, rIdEnd);

        // Try to complete with user_bob as reviewer — should fail (self-certification)
        String completePayload = "{"
                + "\"reviewerId\":\"user_bob\","
                + "\"decision\":\"CERTIFY\""
                + "}";

        URL completeUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/access/reviews/" + reviewId);
        HttpURLConnection completeConn = (HttpURLConnection) completeUrl.openConnection();
        completeConn.setRequestMethod("PUT");
        completeConn.setDoOutput(true);
        completeConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = completeConn.getOutputStream()) {
            os.write(completePayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(400, completeConn.getResponseCode());
        String errResp = readResponse(completeConn);
        assertTrue(errResp.contains("ADM01_SELF_CERTIFICATION_BLOCKED"));
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Tests (User Story Lines 40–43)
    // =========================================================================

    @Test
    public void testOutboxEventsEmitted() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/events/outbox");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"outbox\":["));
    }

    @Test
    public void testInboxEventDeduplication() throws Exception {
        String payload = "{"
                + "\"eventId\":\"INB_ADM01_TEST_101\","
                + "\"sourceService\":\"ACD-01\","
                + "\"consumerGroup\":\"ADM-01\","
                + "\"payload\":\"{\\\"action\\\":\\\"COURSE_PUBLISHED\\\"}\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/events/inbox");
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
    public void testDeadLetterQueueAndReplay() throws Exception {
        // Route an event to DLQ via domain service
        domainService.routeToDeadLetter("ERR_EVT_001", "BillingEvent", "PAYMENT_FAILED", 3, "{\"order\":\"ORD-1\"}");

        // Verify it appears in DLQ endpoint
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/events/dead-letter");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ERR_EVT_001"));
        assertTrue(resp.contains("PAYMENT_FAILED"));

        // Find DLQ id
        int idStart = resp.indexOf("\"id\":\"") + 6;
        int idEnd = resp.indexOf("\"", idStart);
        String dlqId = resp.substring(idStart, idEnd);

        // Replay DLQ event
        URL replayUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/events/dead-letter/" + dlqId + "/replay");
        HttpURLConnection replayConn = (HttpURLConnection) replayUrl.openConnection();
        replayConn.setRequestMethod("POST");
        assertEquals(200, replayConn.getResponseCode());
        String replayResp = readResponse(replayConn);
        assertTrue(replayResp.contains("\"status\":\"REPLAYED\""));
    }

    @Test
    public void testAuditTamperEvidentHashChain() {
        List<Map<String, Object>> auditTrail = domainService.getAuditTrail();
        assertTrue("Audit trail should not be empty", auditTrail.size() > 0);
        for (Map<String, Object> entry : auditTrail) {
            assertTrue("Audit entry must contain beforeHash", entry.containsKey("beforeHash"));
            assertTrue("Audit entry must contain afterHash", entry.containsKey("afterHash"));
            assertEquals("afterHash must be SHA-256 hex string", 64, ((String) entry.get("afterHash")).length());
        }
    }

    @Test
    public void testIdempotencyHashMismatchConflict() {
        String key = "IDEMP-TEST-KEY-001";
        domainService.checkOrRecordIdempotency(key, "TEST_OP", "HASH_AAA", 3600000L);

        try {
            // Same key, different hash
            domainService.checkOrRecordIdempotency(key, "TEST_OP", "HASH_DIFFERENT", 3600000L);
            org.junit.Assert.fail("Expected InstituteAlreadyExistsException on hash mismatch");
        } catch (com.campx.admin.institute.exception.InstituteAlreadyExistsException e) {
            assertTrue(e.getMessage().contains("Payload hash mismatch"));
        }
    }

    // =========================================================================
    // Phase 3 Tests: Configuration & Policy Engine (ADM-01)
    // =========================================================================

    @Test
    public void testFeatureFlagLifecycleAndTenantOverride() throws Exception {
        String flagJson = "{\"flagKey\":\"FEATURE_AI_ASSISTANT\",\"defaultValue\":\"false\",\"description\":\"AI Assistant Flag\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/feature-flags");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(flagJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"flagKey\":\"FEATURE_AI_ASSISTANT\""));

        // Add tenant override
        String overrideJson = "{\"tenantId\":\"TENANT_CAMPUS_A\",\"overrideValue\":\"true\",\"reason\":\"Pilot launch\"}";
        URL overUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/feature-flags/FEATURE_AI_ASSISTANT/overrides");
        HttpURLConnection overConn = (HttpURLConnection) overUrl.openConnection();
        overConn.setRequestMethod("POST");
        overConn.setDoOutput(true);
        overConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = overConn.getOutputStream()) { os.write(overrideJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(200, overConn.getResponseCode());
        String overResp = readResponse(overConn);
        assertTrue(overResp.contains("\"status\":\"OVERRIDDEN\""));
    }

    @Test
    public void testLockedFeatureFlagOverrideRejected() throws Exception {
        String flagJson = "{\"flagKey\":\"CORE_AUDIT_LOCK\",\"defaultValue\":\"true\",\"globallyLocked\":true}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/feature-flags");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(flagJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());

        // Override attempt should be blocked
        String overrideJson = "{\"tenantId\":\"TENANT_ROGUE\",\"overrideValue\":\"false\"}";
        URL overUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/feature-flags/CORE_AUDIT_LOCK/overrides");
        HttpURLConnection overConn = (HttpURLConnection) overUrl.openConnection();
        overConn.setRequestMethod("POST");
        overConn.setDoOutput(true);
        overConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = overConn.getOutputStream()) { os.write(overrideJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(400, overConn.getResponseCode());
        String errResp = readResponse(overConn);
        assertTrue(errResp.contains("ADM01_FLAG_LOCKED"));
    }

    @Test
    public void testGlobalPolicyLifecycleApprovalAndPublication() throws Exception {
        String policyJson = "{\"policyCode\":\"POL_SECURITY_2026\",\"policyType\":\"GOVERNANCE\",\"rules\":\"MFA_REQUIRED,PASSWORD_COMPLEXITY\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/policies");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(policyJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, conn.getResponseCode());

        // Attempt publish before approval -> 422
        URL pubUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/policies/POL_SECURITY_2026/publish");
        HttpURLConnection unapprovedPub = (HttpURLConnection) pubUrl.openConnection();
        unapprovedPub.setRequestMethod("POST");
        assertEquals(422, unapprovedPub.getResponseCode());

        // Approve policy
        URL appUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/policies/POL_SECURITY_2026/approve");
        HttpURLConnection appConn = (HttpURLConnection) appUrl.openConnection();
        appConn.setRequestMethod("POST");
        appConn.setDoOutput(true);
        appConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = appConn.getOutputStream()) { os.write("{\"approvedBy\":\"CISO_OFFICER\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, appConn.getResponseCode());

        // Now publish succeeds
        HttpURLConnection pubConn = (HttpURLConnection) pubUrl.openConnection();
        pubConn.setRequestMethod("POST");
        assertEquals(200, pubConn.getResponseCode());
        String pubResp = readResponse(pubConn);
        assertTrue(pubResp.contains("\"status\":\"PUBLISHED\""));
    }

    @Test
    public void testConfigurationSnapshotAndRollback() throws Exception {
        URL snapUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/configuration/snapshot");
        HttpURLConnection snapConn = (HttpURLConnection) snapUrl.openConnection();
        snapConn.setRequestMethod("POST");
        snapConn.setDoOutput(true);
        snapConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = snapConn.getOutputStream()) { os.write("{\"scope\":\"GLOBAL\"}".getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, snapConn.getResponseCode());
        String snapResp = readResponse(snapConn);
        assertTrue(snapResp.contains("\"version\":"));
        assertTrue(snapResp.contains("\"checksum\":"));

        // Rollback
        URL rbUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/configuration/rollback");
        HttpURLConnection rbConn = (HttpURLConnection) rbUrl.openConnection();
        rbConn.setRequestMethod("POST");
        rbConn.setDoOutput(true);
        rbConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = rbConn.getOutputStream()) { os.write("{\"scope\":\"GLOBAL\",\"targetVersion\":1}".getBytes(StandardCharsets.UTF_8)); }

        assertEquals(200, rbConn.getResponseCode());
        String rbResp = readResponse(rbConn);
        assertTrue(rbResp.contains("\"status\":\"ACTIVE\""));
    }

    // =========================================================================
    // Phase 4 Tests: Commercial & Billing (ADM-01)
    // =========================================================================

    @Test
    public void testCommercialPlanLifecyclePublishing() throws Exception {
        String planJson = "{\"planCode\":\"CAMPUS_STARTER_2026\",\"name\":\"Starter Campus\",\"price\":250000.0,\"currency\":\"INR\",\"entitlements\":\"STUDENT_MGMT\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/plans");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(planJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"planCode\":\"CAMPUS_STARTER_2026\""));
        assertTrue(resp.contains("\"published\":false"));

        // Publish plan
        URL pubUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/plans/CAMPUS_STARTER_2026/publish");
        HttpURLConnection pubConn = (HttpURLConnection) pubUrl.openConnection();
        pubConn.setRequestMethod("POST");
        assertEquals(200, pubConn.getResponseCode());
        String pubResp = readResponse(pubConn);
        assertTrue(pubResp.contains("\"published\":true"));
    }

    @Test
    public void testSubscriptionCreationAndStateTransitions() throws Exception {
        String subJson = "{\"tenantId\":\"TENANT_PILOT_01\",\"planId\":\"ENTERPRISE_CAMPUS_2026\",\"seatCount\":100}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/subscriptions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(subJson.getBytes(StandardCharsets.UTF_8)); }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));

        int idStart = resp.indexOf("\"id\":\"") + 6;
        int idEnd = resp.indexOf("\"", idStart);
        String subId = resp.substring(idStart, idEnd);

        // Transition to SUSPENDED
        URL transUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/subscriptions/" + subId);
        HttpURLConnection transConn = (HttpURLConnection) transUrl.openConnection();
        transConn.setRequestMethod("PUT");
        transConn.setDoOutput(true);
        transConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = transConn.getOutputStream()) { os.write("{\"status\":\"SUSPENDED\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, transConn.getResponseCode());

        // Transition to CANCELLED
        HttpURLConnection cancelConn = (HttpURLConnection) transUrl.openConnection();
        cancelConn.setRequestMethod("PUT");
        cancelConn.setDoOutput(true);
        cancelConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = cancelConn.getOutputStream()) { os.write("{\"status\":\"CANCELLED\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, cancelConn.getResponseCode());

        // Attempt transition from CANCELLED to ACTIVE -> should fail with 422
        HttpURLConnection reactivateConn = (HttpURLConnection) transUrl.openConnection();
        reactivateConn.setRequestMethod("PUT");
        reactivateConn.setDoOutput(true);
        reactivateConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = reactivateConn.getOutputStream()) { os.write("{\"status\":\"ACTIVE\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(422, reactivateConn.getResponseCode());
    }

    @Test
    public void testInvoiceIssuanceAndPaymentReconciliation() throws Exception {
        // Create sub
        String subJson = "{\"tenantId\":\"TENANT_INV_01\",\"planId\":\"ENTERPRISE_CAMPUS_2026\"}";
        URL subUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/subscriptions");
        HttpURLConnection subConn = (HttpURLConnection) subUrl.openConnection();
        subConn.setRequestMethod("POST");
        subConn.setDoOutput(true);
        subConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = subConn.getOutputStream()) { os.write(subJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, subConn.getResponseCode());
        String subResp = readResponse(subConn);
        int subIdStart = subResp.indexOf("\"id\":\"") + 6;
        String subId = subResp.substring(subIdStart, subResp.indexOf("\"", subIdStart));

        // Issue invoice
        URL invUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/invoices");
        HttpURLConnection invConn = (HttpURLConnection) invUrl.openConnection();
        invConn.setRequestMethod("POST");
        invConn.setDoOutput(true);
        invConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = invConn.getOutputStream()) { os.write(("{\"subscriptionId\":\"" + subId + "\",\"billingPeriod\":\"2026-09\"}").getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, invConn.getResponseCode());
        String invResp = readResponse(invConn);
        assertTrue(invResp.contains("\"status\":\"ISSUED\""));
        assertTrue(invResp.contains("\"total\":1416000.0")); // 1.2M + 18% tax = 1.416M

        int invIdStart = invResp.indexOf("\"id\":\"") + 6;
        String invId = invResp.substring(invIdStart, invResp.indexOf("\"", invIdStart));

        // Reconcile payment via gateway transaction webhook
        String txnJson = "{\"gatewayTransactionId\":\"RAZORPAY_TXN_001\",\"invoiceId\":\"" + invId + "\",\"amount\":1416000.0,\"currency\":\"INR\"}";
        URL txnUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/billing/transactions");
        HttpURLConnection txnConn = (HttpURLConnection) txnUrl.openConnection();
        txnConn.setRequestMethod("POST");
        txnConn.setDoOutput(true);
        txnConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = txnConn.getOutputStream()) { os.write(txnJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, txnConn.getResponseCode());
        String txnResp = readResponse(txnConn);
        assertTrue(txnResp.contains("\"status\":\"SUCCESS\""));

        // Idempotent webhook replay
        HttpURLConnection txnReplayConn = (HttpURLConnection) txnUrl.openConnection();
        txnReplayConn.setRequestMethod("POST");
        txnReplayConn.setDoOutput(true);
        txnReplayConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = txnReplayConn.getOutputStream()) { os.write(txnJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, txnReplayConn.getResponseCode());
    }

    @Test
    public void testTenantUsageMetricsRecordingAndRetrieval() throws Exception {
        String usageJson = "{\"tenantId\":\"TENANT_USAGE_TEST\",\"metricType\":\"STORAGE_MB\",\"period\":\"2026-09\",\"value\":524288}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/usage");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(usageJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());

        // Query usage
        URL getUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/usage?tenantId=TENANT_USAGE_TEST");
        HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("TENANT_USAGE_TEST"));
        assertTrue(getResp.contains("\"value\":524288"));
    }

    // =========================================================================
    // Phase 6 Tests: Operations, Alerts, & Workflows
    // =========================================================================

    @Test
    public void testPlatformHealthSnapshots() throws Exception {
        String healthJson = "{\"component\":\"GATEWAY_ROUTER\",\"region\":\"ap-south-1\",\"status\":\"HEALTHY\",\"latencyMs\":18,\"errorRate\":0.001}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/operations/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(healthJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());

        URL getUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/operations/health");
        HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("GATEWAY_ROUTER"));
        assertTrue(getResp.contains("\"latencyMs\":18"));
    }

    @Test
    public void testOperationalAlertLifecycleAndDeduplication() throws Exception {
        String alertJson = "{\"alertCode\":\"HIGH_CPU_LOAD\",\"source\":\"NODE_WORKER_01\",\"severity\":\"WARNING\",\"deduplicationKey\":\"DEDUP_NODE_CPU_01\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/operations/alerts");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(alertJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());
        String alertResp = readResponse(conn);
        int idIdx = alertResp.indexOf("\"id\":\"") + 6;
        String alertId = alertResp.substring(idIdx, alertResp.indexOf("\"", idIdx));
        assertTrue(alertResp.contains("\"status\":\"OPEN\""));

        // Deduplication test: re-post same alert key
        HttpURLConnection dedupConn = (HttpURLConnection) url.openConnection();
        dedupConn.setRequestMethod("POST");
        dedupConn.setDoOutput(true);
        dedupConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = dedupConn.getOutputStream()) { os.write(alertJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, dedupConn.getResponseCode());
        String dedupResp = readResponse(dedupConn);
        assertTrue("Duplicate alert must return identical ID", dedupResp.contains(alertId));

        // Acknowledge alert
        URL ackUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/operations/alerts/" + alertId + "/acknowledge");
        HttpURLConnection ackConn = (HttpURLConnection) ackUrl.openConnection();
        ackConn.setRequestMethod("POST");
        ackConn.setDoOutput(true);
        ackConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = ackConn.getOutputStream()) { os.write("{\"assignee\":\"ENG_SRE_TEAM\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, ackConn.getResponseCode());
        String ackResp = readResponse(ackConn);
        assertTrue(ackResp.contains("\"status\":\"ACKNOWLEDGED\""));
        assertTrue(ackResp.contains("ENG_SRE_TEAM"));

        // Resolve alert
        URL resUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/operations/alerts/" + alertId + "/resolve");
        HttpURLConnection resConn = (HttpURLConnection) resUrl.openConnection();
        resConn.setRequestMethod("POST");
        resConn.setDoOutput(true);
        resConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = resConn.getOutputStream()) { os.write("{\"resolutionNotes\":\"Node restarted\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, resConn.getResponseCode());
        String resResp = readResponse(resConn);
        assertTrue(resResp.contains("\"status\":\"RESOLVED\""));
    }

    @Test
    public void testAdministrativeWorkflowLifecycle() throws Exception {
        String wfJson = "{\"workflowType\":\"TENANT_ONBOARDING\",\"subject\":\"Onboarding New University\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/workflows");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(wfJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());
        String createResp = readResponse(conn);
        int idIdx = createResp.indexOf("\"id\":\"") + 6;
        String wfId = createResp.substring(idIdx, createResp.indexOf("\"", idIdx));
        assertTrue(createResp.contains("\"status\":\"INITIATED\""));

        // Transition workflow to COMPLETED
        URL putUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/workflows/" + wfId);
        HttpURLConnection putConn = (HttpURLConnection) putUrl.openConnection();
        putConn.setRequestMethod("PUT");
        putConn.setDoOutput(true);
        putConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = putConn.getOutputStream()) { os.write("{\"status\":\"COMPLETED\",\"stepName\":\"DNS_CONFIGURED\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, putConn.getResponseCode());
        String putResp = readResponse(putConn);
        assertTrue(putResp.contains("\"status\":\"COMPLETED\""));

        // Query workflow
        HttpURLConnection getConn = (HttpURLConnection) putUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("\"status\":\"COMPLETED\""));
    }

    // =========================================================================
    // Phase 7 Tests: Data Governance & Audit Enhancement (ADM-01)
    // =========================================================================

    @Test
    public void testRetentionPolicyLegalMinimumEnforcement() throws Exception {
        // Attempt creating retention policy where retentionDays < legalMinimumDays -> 400 Bad Request
        String invalidPolicyJson = "{\"policyCode\":\"RET_VIOLATION_01\",\"dataClass\":\"STUDENT_PII\",\"retentionDays\":30,\"legalMinimumDays\":90}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/data-governance/retention");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(invalidPolicyJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(400, conn.getResponseCode());

        // Valid policy where retentionDays >= legalMinimumDays -> 201 Created
        String validPolicyJson = "{\"policyCode\":\"RET_VALID_01\",\"dataClass\":\"STUDENT_PII\",\"retentionDays\":365,\"legalMinimumDays\":90}";
        HttpURLConnection validConn = (HttpURLConnection) url.openConnection();
        validConn.setRequestMethod("POST");
        validConn.setDoOutput(true);
        validConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = validConn.getOutputStream()) { os.write(validPolicyJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, validConn.getResponseCode());
        String resp = readResponse(validConn);
        assertTrue(resp.contains("RET_VALID_01"));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    @Test
    public void testDataClassificationVersioning() throws Exception {
        String classJson = "{\"classCode\":\"RESTRICTED_PII\",\"sensitivity\":\"HIGH\",\"exportRules\":\"APPROVAL_REQUIRED\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/data-governance/classifications");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(classJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"classCode\":\"RESTRICTED_PII\""));
        assertTrue(resp.contains("\"version\":1"));

        // List classifications
        HttpURLConnection getConn = (HttpURLConnection) url.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("RESTRICTED_PII"));
    }

    @Test
    public void testExportRequestMandatoryExpiry() throws Exception {
        // Request without expiresAt (0) -> 400 Bad Request
        String invalidExportJson = "{\"requestedBy\":\"ADMIN_USER_01\",\"dataScope\":\"TENANT:CAMPUS_A\",\"format\":\"CSV\",\"expiresAt\":0}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/exports");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(invalidExportJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(400, conn.getResponseCode());

        // Valid request with future expiresAt -> 201 Created
        String validExportJson = "{\"requestedBy\":\"ADMIN_USER_01\",\"dataScope\":\"TENANT:CAMPUS_A\",\"format\":\"CSV\",\"expiresAt\":1790000000000}";
        HttpURLConnection validConn = (HttpURLConnection) url.openConnection();
        validConn.setRequestMethod("POST");
        validConn.setDoOutput(true);
        validConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = validConn.getOutputStream()) { os.write(validExportJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, validConn.getResponseCode());
        String validResp = readResponse(validConn);
        assertTrue(validResp.contains("\"status\":\"PENDING\""));
    }

    @Test
    public void testExportApprovalAndCompletion() throws Exception {
        // 1. Create Export
        String exportJson = "{\"requestedBy\":\"COMPLIANCE_OFFICER\",\"dataScope\":\"GLOBAL:FINANCIAL\",\"format\":\"JSON\",\"expiresAt\":1790000000000}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/exports");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(exportJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        int idIdx = resp.indexOf("\"id\":\"") + 6;
        String exportId = resp.substring(idIdx, resp.indexOf("\"", idIdx));

        // 2. Approve Export
        URL approveUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/exports/" + exportId + "/approve");
        HttpURLConnection approveConn = (HttpURLConnection) approveUrl.openConnection();
        approveConn.setRequestMethod("POST");
        approveConn.setDoOutput(true);
        approveConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = approveConn.getOutputStream()) { os.write("{\"approvedBy\":\"CHIEF_DPO\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, approveConn.getResponseCode());
        String approveResp = readResponse(approveConn);
        assertTrue(approveResp.contains("\"status\":\"APPROVED\""));
        assertTrue(approveResp.contains("CHIEF_DPO"));

        // 3. Complete Export
        URL completeUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/exports/" + exportId + "/complete");
        HttpURLConnection completeConn = (HttpURLConnection) completeUrl.openConnection();
        completeConn.setRequestMethod("POST");
        completeConn.setDoOutput(true);
        completeConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = completeConn.getOutputStream()) { os.write("{\"objectRef\":\"s3://campx-exports/export-001.json\",\"checksum\":\"SHA256-abcdef012345\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(200, completeConn.getResponseCode());
        String completeResp = readResponse(completeConn);
        assertTrue(completeResp.contains("\"status\":\"COMPLETED\""));
        assertTrue(completeResp.contains("s3://campx-exports/export-001.json"));
    }

    @Test
    public void testExportProhibitedByClassification() throws Exception {
        // Create PROHIBITED classification
        String classJson = "{\"classCode\":\"TOP_SECRET_RESEARCH\",\"sensitivity\":\"CRITICAL\",\"exportRules\":\"PROHIBITED\"}";
        URL cUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/data-governance/classifications");
        HttpURLConnection cConn = (HttpURLConnection) cUrl.openConnection();
        cConn.setRequestMethod("POST");
        cConn.setDoOutput(true);
        cConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = cConn.getOutputStream()) { os.write(classJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, cConn.getResponseCode());

        // Create export with that data class
        String expJson = "{\"requestedBy\":\"RESEARCH_ADMIN\",\"dataScope\":\"RESEARCH:DEFENSE\",\"dataClass\":\"TOP_SECRET_RESEARCH\",\"expiresAt\":1790000000000}";
        URL expUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/exports");
        HttpURLConnection expConn = (HttpURLConnection) expUrl.openConnection();
        expConn.setRequestMethod("POST");
        expConn.setDoOutput(true);
        expConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = expConn.getOutputStream()) { os.write(expJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, expConn.getResponseCode());
        String expResp = readResponse(expConn);
        int idIdx = expResp.indexOf("\"id\":\"") + 6;
        String exportId = expResp.substring(idIdx, expResp.indexOf("\"", idIdx));

        // Attempt approve -> must be rejected with 400 Bad Request
        URL appUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/exports/" + exportId + "/approve");
        HttpURLConnection appConn = (HttpURLConnection) appUrl.openConnection();
        appConn.setRequestMethod("POST");
        appConn.setDoOutput(true);
        appConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = appConn.getOutputStream()) { os.write("{\"approvedBy\":\"DPO\"}".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(400, appConn.getResponseCode());
    }

    @Test
    public void testAuditSearchByCorrelationId() throws Exception {
        String traceId = "TRACE-AUDIT-CORR-" + UUID.randomUUID().toString().substring(0, 8);
        String instJson = "{\"instituteCode\":\"INST_CORR_" + UUID.randomUUID().toString().substring(0, 6) + "\",\"instituteName\":\"Corr Search Test\",\"domain\":\"corr.edu\",\"tier\":\"ENTERPRISE\"}";
        URL instUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection instConn = (HttpURLConnection) instUrl.openConnection();
        instConn.setRequestMethod("POST");
        instConn.setRequestProperty("X-Trace-Id", traceId);
        instConn.setRequestProperty("Content-Type", "application/json");
        instConn.setDoOutput(true);
        try (OutputStream os = instConn.getOutputStream()) { os.write(instJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, instConn.getResponseCode());

        // Search audit by correlationId
        URL searchUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/audit-logs/search?correlationId=" + traceId);
        HttpURLConnection searchConn = (HttpURLConnection) searchUrl.openConnection();
        searchConn.setRequestMethod("GET");
        assertEquals(200, searchConn.getResponseCode());
        String searchResp = readResponse(searchConn);
        assertTrue("Search result must contain correlationId", searchResp.contains(traceId));
    }

    @Test
    public void testAuditSearchByActorAndDateRange() throws Exception {
        URL searchUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/audit-logs/search?actorId=SUPER_ADMIN&fromDate=0&toDate=9999999999999");
        HttpURLConnection searchConn = (HttpURLConnection) searchUrl.openConnection();
        searchConn.setRequestMethod("GET");
        assertEquals(200, searchConn.getResponseCode());
        String searchResp = readResponse(searchConn);
        assertTrue(searchResp.contains("auditEntries"));
        assertTrue(searchResp.contains("SUPER_ADMIN"));
    }

    @Test
    public void testRetentionPolicyCodeUniqueness() throws Exception {
        String policyJson = "{\"policyCode\":\"RET_DUP_POLICY_01\",\"dataClass\":\"LOGS\",\"retentionDays\":180,\"legalMinimumDays\":30}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/data-governance/retention");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(policyJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(201, conn.getResponseCode());

        // Second creation with same policyCode -> 409 Conflict
        HttpURLConnection dupConn = (HttpURLConnection) url.openConnection();
        dupConn.setRequestMethod("POST");
        dupConn.setDoOutput(true);
        dupConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = dupConn.getOutputStream()) { os.write(policyJson.getBytes(StandardCharsets.UTF_8)); }
        assertEquals(409, dupConn.getResponseCode());
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

