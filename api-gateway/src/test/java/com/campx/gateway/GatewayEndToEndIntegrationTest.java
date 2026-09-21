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
 */
public class GatewayEndToEndIntegrationTest {

    private static InstituteAdminServer instituteServer;
    private static CollegeAdminServer collegeServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8080;
    private static final int INST_PORT = 8081;
    private static final int COL_PORT = 8082;

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

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (instituteServer != null) instituteServer.stop();
        if (collegeServer != null) collegeServer.stop();
        CampXLoggerFactory.flush();
    }

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

    private String readResponse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
