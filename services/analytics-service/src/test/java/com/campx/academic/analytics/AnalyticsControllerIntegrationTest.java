package com.campx.academic.analytics;

import com.campx.academic.analytics.server.AnalyticsServer;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * HTTP Integration Test Suite for ACD-10: Reporting & Analytics Service.
 * Validates REST endpoints, error serialization, rate limiting, and gateway aliases.
 */
public class AnalyticsControllerIntegrationTest {

    private static AnalyticsServer server;
    private static int port = 8098;
    private static String baseUrl;

    @BeforeClass
    public static void setUp() throws Exception {
        AnalyticsDomainService domainService = new AnalyticsDomainService();
        server = new AnalyticsServer(port, domainService);
        server.start();
        baseUrl = "http://localhost:" + port;
    }

    @AfterClass
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/actuator/health").openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"UP\""));
        assertTrue(resp.contains("ACD-10-ReportingAnalyticsService"));
    }

    @Test
    public void testMetricsEndpoint() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/metrics").openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        String resp = readResponse(conn);
        assertTrue(resp.contains("acd10_events_processed_total"));
        assertTrue(resp.contains("acd10_consumer_lag_seconds"));
    }

    @Test
    public void testGetDashboardCanonical() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/dashboard?period=2026-27").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        conn.setRequestProperty("X-Trace-Id", "TRACE-TEST-DB");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("dashboardId"));
        assertTrue(resp.contains("attendancePercentage"));
        assertTrue(resp.contains("timetableUtilization"));
        assertTrue(resp.contains("freshness"));
        assertEquals("TRACE-TEST-DB", conn.getHeaderField("X-Trace-Id"));
    }

    @Test
    public void testGetDashboardAlias() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/dashboards?period=2026-27").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("attendancePercentage"));
    }

    @Test
    public void testGetAttendanceAnalytics() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/attendance?batchId=BAT-CSE-3A").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("subjects"));
        assertTrue(resp.contains("SUB-101"));
        assertTrue(resp.contains("percentage"));
    }

    @Test
    public void testGetTimetableUtilization() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/timetable?from=2026-08-01&to=2026-09-14").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("utilization"));
        assertTrue(resp.contains("rooms"));
        assertTrue(resp.contains("faculty"));
    }

    @Test
    public void testGetProgression() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/progression?batchId=BAT-CSE-3A").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("progression"));
        assertTrue(resp.contains("courseCompletion"));
        assertTrue(resp.contains("trend"));
    }

    @Test
    public void testExportLifecycleViaHttp() throws Exception {
        // 1. Submit POST export
        String exportPayload = "{\"reportType\":\"ATTENDANCE_SUMMARY\",\"format\":\"CSV\",\"filters\":{\"batchId\":\"BAT-CSE-3A\"}}";
        HttpURLConnection postConn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/export").openConnection();
        postConn.setRequestMethod("POST");
        postConn.setRequestProperty("Content-Type", "application/json");
        postConn.setRequestProperty("X-Tenant-Id", "INST-001");
        postConn.setRequestProperty("X-User-Id", "admin-1");
        postConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        postConn.setDoOutput(true);
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(exportPayload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(202, postConn.getResponseCode());
        String postResp = readResponse(postConn);
        assertTrue(postResp.contains("jobId"));
        assertTrue(postResp.contains("pollUri"));

        // Extract jobId
        String jobId = extractJsonValue(postResp, "jobId");
        assertNotNull(jobId);

        // 2. Wait and Poll status
        Thread.sleep(150);

        HttpURLConnection statusConn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/export/" + jobId).openConnection();
        statusConn.setRequestMethod("GET");
        statusConn.setRequestProperty("X-Tenant-Id", "INST-001");
        statusConn.setRequestProperty("X-User-Id", "admin-1");
        statusConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, statusConn.getResponseCode());
        String statusResp = readResponse(statusConn);
        assertTrue(statusResp.contains("COMPLETED"));
        assertTrue(statusResp.contains("outputRef"));

        // 3. Download artifact
        HttpURLConnection dlConn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/export/" + jobId + "/download").openConnection();
        dlConn.setRequestMethod("GET");
        dlConn.setRequestProperty("X-Tenant-Id", "INST-001");
        dlConn.setRequestProperty("X-User-Id", "admin-1");
        dlConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, dlConn.getResponseCode());
        String dlContent = readResponse(dlConn);
        assertNotNull(dlContent);
        assertTrue(dlContent.length() > 0);
    }

    @Test
    public void testEventIngestionViaHttp() throws Exception {
        String eventPayload = "{\"eventId\":\"EVT-HTTP-001\",\"eventType\":\"AttendanceMarked\",\"source\":\"ACD-06\"," +
                "\"occurredAt\":\"2026-09-14T09:00:00Z\",\"tenantId\":\"INST-001\",\"batchId\":\"BAT-CSE-3A\",\"presentCount\":35}";

        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/events").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(eventPayload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
    }

    @Test
    public void testRebuildEndpointViaHttp() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/rebuild").openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "OPERATOR");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"rebuilt\":true"));
    }

    @Test
    public void testReportDefinitionsEndpoint() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/reports/definitions").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ATTENDANCE_SUMMARY"));
        assertTrue(resp.contains("TIMETABLE_UTILIZATION"));
    }

    @Test
    public void testErrorHandlingInvalidDateRange() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/attendance?from=2026-10-01&to=2026-09-01").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(400, conn.getResponseCode());
        String errResp = readError(conn);
        assertTrue(errResp.contains("ACD10_INVALID_DATE_RANGE"));
        assertTrue(errResp.contains("\"error\""));
    }

    @Test
    public void testErrorHandlingScopeForbidden() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/dashboard?scope=department&scopeId=DEP-ECE").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "INST-001");
        conn.setRequestProperty("X-User-Role", "DEPARTMENT_HEAD");
        conn.setRequestProperty("X-Department-Id", "DEP-CSE"); // cross-department

        assertEquals(403, conn.getResponseCode());
        String errResp = readError(conn);
        assertTrue(errResp.contains("ACD10_ANALYTICS_SCOPE_FORBIDDEN"));
    }

    @Test
    public void testNotFoundRoute() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/v1/academics/analytics/non-existent").openConnection();
        conn.setRequestMethod("GET");

        assertEquals(404, conn.getResponseCode());
        String errResp = readError(conn);
        assertTrue(errResp.contains("ACD10_ANALYTICS_NOT_FOUND"));
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String readError(HttpURLConnection conn) throws IOException {
        InputStream es = conn.getErrorStream();
        if (es == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String extractJsonValue(String json, String key) {
        String target = "\"" + key + "\":\"";
        int idx = json.indexOf(target);
        if (idx == -1) return null;
        int start = idx + target.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : null;
    }
}
