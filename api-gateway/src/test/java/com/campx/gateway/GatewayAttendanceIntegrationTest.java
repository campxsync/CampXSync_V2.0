package com.campx.gateway;

import com.campx.academic.attendance.server.AttendanceServer;
import com.campx.academic.attendance.service.AttendanceDomainService;
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
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying unified API Gateway reverse proxy routing,
 * correlation tracking, and transparent pass-through to ACD-06 Attendance Management Service.
 */
public class GatewayAttendanceIntegrationTest {

    private static AttendanceServer attendanceServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8095;
    private static final int ATT_PORT = 8094;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-06 Attendance Management Service on port 8094
        AttendanceDomainService domainService = new AttendanceDomainService();
        domainService.addTimetableSlot("SLOT-GW-1", "BATCH-001", "SUBJ-001", "FAC-001");
        domainService.addBatchRoster("BATCH-001", "TENANT-001", Arrays.asList("STU-001", "STU-002", "STU-003"));
        domainService.setMarkingWindowEnforced(false);

        attendanceServer = new AttendanceServer(ATT_PORT, domainService);
        attendanceServer.start();

        // 2. Start Gateway on port 8095 configured to proxy to attendanceServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/attendance/metrics", "http://localhost:" + ATT_PORT + "/metrics");
        config.addRoute("/api/v1/academics/attendance/metrics", "http://localhost:" + ATT_PORT + "/metrics");
        config.addRoute("/api/v1/attendance", "http://localhost:" + ATT_PORT + "/api/v1/academics/attendance");
        config.addRoute("/api/v1/academics/attendance", "http://localhost:" + ATT_PORT + "/api/v1/academics/attendance");
        config.addRoute("/v1/attendance", "http://localhost:" + ATT_PORT + "/api/v1/academics/attendance");
        config.addRoute("/v1/attendance-sessions", "http://localhost:" + ATT_PORT + "/api/v1/academics/attendance/sessions");
        config.addRoute("/v1/attendance-summaries", "http://localhost:" + ATT_PORT + "/api/v1/academics/attendance/summaries");
        config.addRoute("/v1/attendance-reports", "http://localhost:" + ATT_PORT + "/api/v1/academics/attendance/report");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (attendanceServer != null) attendanceServer.stop();
        CampXLoggerFactory.flush();
    }

    @Test
    public void testRouteCreateAttendanceSessionViaGateway() throws Exception {
        String payload = "{"
                + "\"batchId\":\"BATCH-001\","
                + "\"subjectId\":\"SUBJ-001\","
                + "\"timetableEntryId\":\"SLOT-GW-1\","
                + "\"attendanceDate\":\"2026-10-15\","
                + "\"periodNo\":1,"
                + "\"startTime\":\"09:00\","
                + "\"endTime\":\"10:00\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/attendance/sessions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-ATT-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "FACULTY");
        conn.setRequestProperty("X-User-Id", "FAC-001");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-GW-ATT-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"batchId\":\"BATCH-001\""));
        assertTrue(resp.contains("\"status\":\"OPEN\""));
        assertTrue(resp.contains("\"rosterCount\":3"));
    }

    @Test
    public void testRouteAttendanceCanonicalAlias() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/attendance/status-catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-ATT-CAT");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-GW-ATT-CAT", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"statusCode\":\"PRESENT\""));
        assertTrue(resp.contains("\"statusCode\":\"ABSENT\""));
    }

    @Test
    public void testRouteAttendanceMetricsViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/attendance/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-ATT-METRICS");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-GW-ATT-METRICS", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("acd06_requests_total"));
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
