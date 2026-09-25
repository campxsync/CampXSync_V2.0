package com.campx.gateway;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
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
 * correlation tracking, and error pass-through for ADM-02 College Admin Service,
 * specifically covering User Stories 39, 40, and 41:
 * <ul>
 *   <li>Story 39: Cross-module batch split/merge permissions & Separation of Duties (SoD)</li>
 *   <li>Story 40: Batch split/merge workflow event ingestion and Registrar decision routing</li>
 *   <li>Story 41: Cryptographic audit trail logging for batch approval decisions</li>
 * </ul>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayCollegeAdminIntegrationTest {

    private static CollegeAdminServer collegeServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8098;
    private static final int COL_PORT = 8099;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ADM-02 College Admin Service on port 8099
        collegeServer = new CollegeAdminServer(COL_PORT, new CollegeAdminDomainService());
        collegeServer.start();

        // 2. Start Gateway on port 8098 configured with canonical and direct routes to collegeServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/college-admin", "http://localhost:" + COL_PORT + "/api/v1/college-admin");
        config.addRoute("/v1/batch-approvals", "http://localhost:" + COL_PORT + "/api/v1/college-admin/workflows/batch-approvals");
        config.addRoute("/v1/college-workflows", "http://localhost:" + COL_PORT + "/api/v1/college-admin/workflows");
        config.addRoute("/v1/college-roles", "http://localhost:" + COL_PORT + "/api/v1/college-admin/roles");
        config.addRoute("/v1/college-permissions", "http://localhost:" + COL_PORT + "/api/v1/college-admin/permissions");
        config.addRoute("/v1/college-audit-logs", "http://localhost:" + COL_PORT + "/api/v1/college-admin/audit-logs");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (collegeServer != null) collegeServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * Story 40 & Story 41:
     * Ingest BatchSplitApprovalRequested event via API Gateway, verify deduplication,
     * query via canonical alias /v1/batch-approvals, and decide approval with Registrar role.
     */
    @Test
    public void testBatchApprovalLifecycleViaGateway() throws Exception {
        String eventPayload = "{"
                + "\"eventId\":\"EVT_GW_SPLIT_01\","
                + "\"eventType\":\"BatchSplitApprovalRequested\","
                + "\"correlationId\":\"CORR-GW-SPLIT-99\","
                + "\"batchId\":\"BATCH-CS-2026-A\","
                + "\"proposedSections\":[\"A1\",\"A2\"],"
                + "\"requesterId\":\"USER_ACAD_COORD\","
                + "\"reason\":\"Split into two lab cohorts due to lab capacity\""
                + "}";

        // 1. Post event via Gateway direct route /api/v1/college-admin/workflows/batch-approvals/events
        URL eventUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/college-admin/workflows/batch-approvals/events");
        HttpURLConnection conn = (HttpURLConnection) eventUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-EVT-01");
        conn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(eventPayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);
        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertNotNull("Gateway should preserve X-Trace-Id", traceHeader);

        String eventResp = readResponse(conn);
        assertTrue("Response must indicate pending status", eventResp.contains("\"status\":\"PENDING\""));
        assertTrue("Response must contain requestId", eventResp.contains("\"requestId\":"));

        // 2. Query batch approvals via Canonical Alias /v1/batch-approvals
        URL listUrl = new URL("http://localhost:" + GW_PORT + "/v1/batch-approvals");
        HttpURLConnection listConn = (HttpURLConnection) listUrl.openConnection();
        listConn.setRequestMethod("GET");
        listConn.setRequestProperty("X-Trace-Id", "TRACE-GW-LIST-01");
        listConn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");

        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("EVT_GW_SPLIT_01"));
        assertTrue(listResp.contains("BATCH-CS-2026-A"));

        // 3. Test Deduplication via Gateway
        HttpURLConnection dupConn = (HttpURLConnection) eventUrl.openConnection();
        dupConn.setRequestMethod("POST");
        dupConn.setDoOutput(true);
        dupConn.setRequestProperty("Content-Type", "application/json");
        dupConn.setRequestProperty("X-Trace-Id", "TRACE-GW-DUP-01");
        dupConn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");
        try (OutputStream os = dupConn.getOutputStream()) {
            os.write(eventPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, dupConn.getResponseCode());
        String dupResp = readResponse(dupConn);
        assertTrue("Duplicate event must return DUPLICATE status", dupResp.contains("DUPLICATE"));

        // 4. Decide approval via Canonical Alias /v1/batch-approvals/EVT_GW_SPLIT_01/decide
        URL decideUrl = new URL("http://localhost:" + GW_PORT + "/v1/batch-approvals/EVT_GW_SPLIT_01/decide");
        HttpURLConnection decideConn = (HttpURLConnection) decideUrl.openConnection();
        decideConn.setRequestMethod("POST");
        decideConn.setDoOutput(true);
        decideConn.setRequestProperty("Content-Type", "application/json");
        decideConn.setRequestProperty("X-Trace-Id", "TRACE-GW-DECIDE-01");
        decideConn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");
        decideConn.setRequestProperty("X-User-Id", "REGISTRAR_CHIEF");
        decideConn.setRequestProperty("X-User-Role", "REGISTRAR");

        String decidePayload = "{"
                + "\"decision\":\"APPROVED\","
                + "\"reason\":\"Lab infrastructure verified and approved\""
                + "}";

        try (OutputStream os = decideConn.getOutputStream()) {
            os.write(decidePayload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, decideConn.getResponseCode());
        String decideResp = readResponse(decideConn);
        assertTrue("Response must indicate APPROVED", decideResp.contains("\"decision\":\"APPROVED\""));
        assertTrue("Response must contain auditRecordId", decideResp.contains("\"auditRecordId\":"));
        assertTrue("Response must contain beforeHash", decideResp.contains("\"beforeHash\":"));
        assertTrue("Response must contain afterHash", decideResp.contains("\"afterHash\":"));
    }

    /**
     * Story 39:
     * Separation of Duties (SoD) enforcement pass-through via Gateway.
     * Attempting to grant conflicting request and approve permissions in a single role must return HTTP 400.
     */
    @Test
    public void testSeparationOfDutiesPassThroughViaGateway() throws Exception {
        String toxicRolePayload = "{"
                + "\"roleCode\":\"ROLE_TOXIC_COMBO\","
                + "\"name\":\"Toxic Conflict Role\","
                + "\"permissions\":[\"BATCH_SPLIT_REQUEST\",\"BATCH_SPLIT_APPROVE\"]"
                + "}";

        URL roleUrl = new URL("http://localhost:" + GW_PORT + "/v1/college-roles");
        HttpURLConnection conn = (HttpURLConnection) roleUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-SOD-01");
        conn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");
        conn.setRequestProperty("X-User-Role", "COLLEGE_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(toxicRolePayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertNotNull(traceHeader);

        String errResp = readResponse(conn);
        assertTrue("Must contain ADM02_SEPARATION_OF_DUTIES_VIOLATION", errResp.contains("ADM02_SEPARATION_OF_DUTIES_VIOLATION"));
        assertTrue("Must indicate separation of duties conflict", errResp.contains("Separation of duties violation"));
    }

    /**
     * Story 40:
     * Anti-self-certification enforcement pass-through via Gateway.
     * The requester cannot approve their own batch request, returning HTTP 400.
     */
    @Test
    public void testSelfCertificationBlockedViaGateway() throws Exception {
        String eventPayload = "{"
                + "\"eventId\":\"EVT_GW_SELFCERT_01\","
                + "\"eventType\":\"BatchMergeApprovalRequested\","
                + "\"correlationId\":\"CORR-GW-SELFCERT-01\","
                + "\"batchIds\":[\"B-1\",\"B-2\"],"
                + "\"requesterId\":\"USER_SELF_APPLICANT\","
                + "\"reason\":\"Merge low enrollment cohorts\""
                + "}";

        URL eventUrl = new URL("http://localhost:" + GW_PORT + "/v1/batch-approvals/events");
        HttpURLConnection conn = (HttpURLConnection) eventUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-SELF-01");
        conn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(eventPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn.getResponseCode());

        // Attempt to approve as the same user (USER_SELF_APPLICANT) even with REGISTRAR role
        URL decideUrl = new URL("http://localhost:" + GW_PORT + "/v1/batch-approvals/EVT_GW_SELFCERT_01/decide");
        HttpURLConnection decideConn = (HttpURLConnection) decideUrl.openConnection();
        decideConn.setRequestMethod("POST");
        decideConn.setDoOutput(true);
        decideConn.setRequestProperty("Content-Type", "application/json");
        decideConn.setRequestProperty("X-Trace-Id", "TRACE-GW-SELF-02");
        decideConn.setRequestProperty("X-Tenant-Id", "COLLEGE_MAIN");
        decideConn.setRequestProperty("X-User-Id", "USER_SELF_APPLICANT");
        decideConn.setRequestProperty("X-User-Role", "REGISTRAR");

        String decidePayload = "{\"decision\":\"APPROVED\",\"reason\":\"Self approval test\"}";
        try (OutputStream os = decideConn.getOutputStream()) {
            os.write(decidePayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = decideConn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(decideConn);
        assertTrue(errResp.contains("Self-certification violation"));
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
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
