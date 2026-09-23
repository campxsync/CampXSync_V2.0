package com.campx.gateway;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying unified API Gateway routing
 * to both ADM-01 Institute Admin Service and ADM-02 College Admin Service.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayEndToEndIntegrationTest {

    private static InstituteAdminServer instituteServer;
    private static CollegeAdminServer collegeServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8080;
    private static final int INST_PORT = 8081;
    private static final int COL_PORT = 8082;

    /**
     * Boots the Institute Admin Service, College Admin Service, and the API Gateway.
     *
     * @throws Exception If any server fails to initialize.
     */
    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start Institute Admin Service
        instituteServer = new InstituteAdminServer(INST_PORT, new InstituteAdminDomainService());
        instituteServer.start();

        // 2. Start College Admin Service
        collegeServer = new CollegeAdminServer(COL_PORT, new CollegeAdminDomainService());
        collegeServer.start();

        // 3. Start Gateway configured to route to both services
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/admin", "http://localhost:" + INST_PORT);
        config.addRoute("/api/v1/college-admin", "http://localhost:" + COL_PORT);

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    /**
     * Gracefully stops all servers and flushes logging framework buffers.
     */
    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (instituteServer != null) instituteServer.stop();
        if (collegeServer != null) collegeServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * Verifies routing a tenant registration request through the Gateway
     * to the ADM-01 Institute Admin Service.
     *
     * @throws Exception If HTTP request fails.
     */
    @Test
    public void testRouteToInstituteAdminViaGateway() throws Exception {
        String institutePayload = "{"
                + "\"instituteCode\":\"INST_GW_01\","
                + "\"legalName\":\"Vellore Institute of Technology\","
                + "\"displayName\":\"VIT Campus\","
                + "\"timezone\":\"Asia/Kolkata\","
                + "\"locale\":\"en_IN\","
                + "\"defaultCurrency\":\"INR\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GATEWAY-TEST-001");
        conn.setRequestProperty("X-Tenant-Id", "VIT_CAMPUS");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(institutePayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GATEWAY-TEST-001", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"instituteCode\":\"INST_GW_01\""));
    }

    /**
     * Verifies routing a profile retrieval request through the Gateway
     * to the ADM-02 College Admin Service.
     *
     * @throws Exception If HTTP request fails.
     */
    @Test
    public void testRouteToCollegeAdminViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/college-admin/profile");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GATEWAY-TEST-002");

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String traceHeader = conn.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GATEWAY-TEST-002", traceHeader);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"code\":\"COL_ENGG_01\""));
    }

    /**
     * Verifies that duplicate resource errors from ADM-01 pass through
     * the Gateway with HTTP 409 status and error response body.
     *
     * @throws Exception If HTTP request fails.
     */
    @Test
    public void testDownstreamErrorPassThroughViaGateway() throws Exception {
        String payload = "{"
                + "\"instituteCode\":\"INST_GW_DUP_01\","
                + "\"legalName\":\"VIT Duplicate Test\","
                + "\"displayName\":\"VIT Duplicate\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/admin/institutes");

        // 1. First registration succeeds (201)
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        conn1.setRequestProperty("X-Trace-Id", "TRACE-GW-INIT-001");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // 2. Second registration with duplicate code via Gateway returns 409 Conflict
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("X-Trace-Id", "TRACE-GATEWAY-ERR-001");

        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn2.getResponseCode();
        assertEquals(409, code);

        String traceHeader = conn2.getHeaderField("X-Trace-Id");
        assertEquals("TRACE-GATEWAY-ERR-001", traceHeader);

        String resp = readResponse(conn2);
        assertTrue(resp.contains("\"status\":409"));
        assertTrue(resp.contains("\"errorCode\":\"ADM01_DUPLICATE_RESOURCE\""));
        assertTrue(resp.contains("\"error\":\"Conflict\""));
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        java.io.InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
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
