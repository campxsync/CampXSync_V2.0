package com.campx.gateway;

import com.campx.academic.analytics.server.AnalyticsServer;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying unified API Gateway reverse proxy routing,
 * correlation tracking, and transparent pass-through to ACD-10 Reporting & Analytics Service.
 */
public class GatewayAnalyticsIntegrationTest {

    private static AnalyticsServer analyticsServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8096;
    private static final int ANL_PORT = 8097;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-10 Analytics Service on port 8097
        AnalyticsDomainService domainService = new AnalyticsDomainService();
        analyticsServer = new AnalyticsServer(ANL_PORT, domainService);
        analyticsServer.start();

        // 2. Start Gateway on port 8096 configured to proxy to analyticsServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/academics/analytics/metrics", "http://localhost:" + ANL_PORT + "/metrics");
        config.addRoute("/api/v1/academics/analytics", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics");
        config.addRoute("/api/v1/analytics", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics");
        config.addRoute("/api/v1/dashboards", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics/dashboard");
        config.addRoute("/api/v1/kpis", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics/dashboard");
        config.addRoute("/api/v1/reports", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics/export");
        config.addRoute("/v1/analytics", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics");
        config.addRoute("/v1/dashboards", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics/dashboard");
        config.addRoute("/v1/reports", "http://localhost:" + ANL_PORT + "/api/v1/academics/analytics/export");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (analyticsServer != null) analyticsServer.stop();
        CampXLoggerFactory.flush();
    }

    @Test
    public void testRouteDashboardViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/analytics/dashboard?period=2026-27");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-ANL-001");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        conn.setRequestProperty("X-User-Id", "admin-1");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-GW-ANL-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("dashboardId"));
        assertTrue(resp.contains("attendancePercentage"));
        assertTrue(resp.contains("timetableUtilization"));
    }

    @Test
    public void testRouteDashboardAliasViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/dashboards");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-ANL-002");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("dashboardId"));
    }

    @Test
    public void testRouteExportViaReportsAlias() throws Exception {
        String payload = "{\"reportType\":\"ATTENDANCE_SUMMARY\",\"format\":\"CSV\"}";
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/reports");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-ANL-003");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        conn.setRequestProperty("X-User-Id", "admin-1");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(202, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("jobId"));
        assertTrue(resp.contains("pollUri"));
    }

    @Test
    public void testRouteGatewayActuatorHealth() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/actuator/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"UP\""));
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
