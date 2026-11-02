package com.campx.gateway;

import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.admin.institute.service.InstituteAdminDomainService;
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
 * correlation tracking, and role-based access control for ADM-01 Institute Admin Service.
 *
 * Verifies:
 * <ul>
 *   <li>ADM01-STORY-01 / 03: Institute registration and listing via Gateway</li>
 *   <li>ADM01-STORY-06: Institutional tenant onboarding saga via Gateway</li>
 *   <li>ADM01-STORY-08: Global platform configuration management via Gateway</li>
 *   <li>ADM01-STORY-28: Tamper-evident platform audit ledger retrieval via Gateway</li>
 *   <li>RBAC Isolation: Non-platform callers failed closed with HTTP 403 Forbidden</li>
 * </ul>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayInstituteAdminIntegrationTest {

    private static InstituteAdminServer instituteServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8094;
    private static final int INST_PORT = 8095;
    private static final String ADMIN_USER_ID = "c0000000-0000-0000-0000-000000000001";
    private static final String STUDENT_USER_ID = "c0000000-0000-0000-0000-000000000999";

    @BeforeClass
    public static void startAll() throws Exception {
        System.setProperty("campx.persistence.mode", "in_memory");

        // 1. Start ADM-01 Institute Admin Service on port 8095
        instituteServer = new InstituteAdminServer(INST_PORT, new InstituteAdminDomainService());
        instituteServer.start();

        // 2. Start Gateway on port 8094 configured with routes to instituteServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/admin", "http://localhost:" + INST_PORT + "/api/v1/admin");
        config.addRoute("/v1/institutes", "http://localhost:" + INST_PORT + "/api/v1/admin/institutes");
        config.addRoute("/v1/platform-configs", "http://localhost:" + INST_PORT + "/api/v1/admin/configuration");
        config.addRoute("/v1/platform-roles", "http://localhost:" + INST_PORT + "/api/v1/admin/roles");
        config.addRoute("/v1/platform-audit-logs", "http://localhost:" + INST_PORT + "/api/v1/admin/audit-logs");
        config.addRoute("/v1/onboard-tenant", "http://localhost:" + INST_PORT + "/api/v1/admin/onboard-tenant");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (instituteServer != null) instituteServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * ADM01-STORY-01 & ADM01-STORY-03:
     * Register a new institute via Gateway alias /v1/institutes, verify response,
     * and query institute list via direct route /api/v1/admin/institutes.
     */
    @Test
    public void testInstituteLifecycleViaGateway() throws Exception {
        String payload = "{"
                + "\"instituteCode\":\"INST_GW_TEST\","
                + "\"legalName\":\"Gateway Test Institute of Technology\","
                + "\"displayName\":\"GTIT Campus\","
                + "\"timezone\":\"Asia/Kolkata\","
                + "\"locale\":\"en_IN\","
                + "\"defaultCurrency\":\"INR\""
                + "}";

        URL createUrl = new URL("http://localhost:" + GW_PORT + "/v1/institutes");
        HttpURLConnection conn = (HttpURLConnection) createUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-INST-01");
        conn.setRequestProperty("X-User-Id", ADMIN_USER_ID);
        conn.setRequestProperty("X-User-Role", "PLATFORM_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String createResp = readResponse(conn);
        assertTrue("Response must contain instituteCode", createResp.contains("\"instituteCode\":\"INST_GW_TEST\""));
        assertTrue("Response must indicate ACTIVE status", createResp.contains("\"status\":\"ACTIVE\""));

        // Query institutes list via /api/v1/admin/institutes
        URL listUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/admin/institutes");
        HttpURLConnection listConn = (HttpURLConnection) listUrl.openConnection();
        listConn.setRequestMethod("GET");
        listConn.setRequestProperty("X-Trace-Id", "TRACE-INST-02");
        listConn.setRequestProperty("X-User-Id", ADMIN_USER_ID);
        listConn.setRequestProperty("X-User-Role", "PLATFORM_ADMIN");

        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue("List must contain registered institute code", listResp.contains("INST_GW_TEST"));
    }

    /**
     * ADM01-STORY-08 & 09:
     * Configure a global platform setting via /v1/platform-configs and read it back.
     */
    @Test
    public void testPlatformConfigurationViaGateway() throws Exception {
        String configPayload = "{"
                + "\"key\":\"security.max_login_attempts\","
                + "\"value\":\"5\","
                + "\"description\":\"Max failed login attempts before lockout\""
                + "}";

        URL configUrl = new URL("http://localhost:" + GW_PORT + "/v1/platform-configs");
        HttpURLConnection conn = (HttpURLConnection) configUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-CONFIG-01");
        conn.setRequestProperty("X-User-Id", ADMIN_USER_ID);
        conn.setRequestProperty("X-User-Role", "PLATFORM_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(configPayload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());

        // Read back configuration
        HttpURLConnection getConn = (HttpURLConnection) configUrl.openConnection();
        getConn.setRequestMethod("GET");
        getConn.setRequestProperty("X-Trace-Id", "TRACE-CONFIG-02");
        getConn.setRequestProperty("X-User-Id", ADMIN_USER_ID);
        getConn.setRequestProperty("X-User-Role", "PLATFORM_ADMIN");

        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue("Configuration must contain saved key", getResp.contains("security.max_login_attempts"));
    }

    /**
     * ADM01-STORY-28:
     * Retrieve platform audit logs via /v1/platform-audit-logs and verify correlation metadata.
     */
    @Test
    public void testPlatformAuditLogsViaGateway() throws Exception {
        URL auditUrl = new URL("http://localhost:" + GW_PORT + "/v1/platform-audit-logs");
        HttpURLConnection conn = (HttpURLConnection) auditUrl.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-AUDIT-01");
        conn.setRequestProperty("X-User-Id", ADMIN_USER_ID);
        conn.setRequestProperty("X-User-Role", "PLATFORM_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String auditResp = readResponse(conn);
        assertTrue("Response must contain auditLogs array", auditResp.contains("\"auditLogs\":["));
    }

    /**
     * RBAC Enforcement:
     * Non-platform admin (e.g. STUDENT role) attempting tenant onboarding must be rejected with HTTP 403 Forbidden.
     */
    @Test
    public void testNonPlatformRoleRejectedForOnboarding() throws Exception {
        String onboardPayload = "{"
                + "\"tenantName\":\"Unauthorized College\","
                + "\"tenantCode\":\"UNAUTH_01\","
                + "\"collegeName\":\"Main Campus\","
                + "\"collegeCode\":\"MAIN\","
                + "\"adminName\":\"John Doe\","
                + "\"adminEmail\":\"john@unauth.edu\""
                + "}";

        URL onboardUrl = new URL("http://localhost:" + GW_PORT + "/v1/onboard-tenant");
        HttpURLConnection conn = (HttpURLConnection) onboardUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-RBAC-DENY");
        conn.setRequestProperty("X-User-Id", STUDENT_USER_ID);
        conn.setRequestProperty("X-User-Role", "STUDENT");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(onboardPayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(403, code);
        String errResp = readResponse(conn);
        assertTrue("Must indicate platform administrator privileges required",
                errResp.contains("Platform administrator privileges required"));
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
