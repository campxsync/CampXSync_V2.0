package com.campx.gateway;

import com.campx.academic.calendar.server.CalendarServer;
import com.campx.academic.calendar.service.CalendarDomainService;
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
 * correlation tracking, and transparent pass-through to ACD-07 Academic Calendar Service.
 */
public class GatewayCalendarIntegrationTest {

    private static CalendarServer calendarServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8096;
    private static final int CAL_PORT = 8097;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-07 Academic Calendar Service on port 8097
        CalendarDomainService domainService = new CalendarDomainService();
        com.campx.academic.calendar.model.CalendarModels.CreateCalendarRequest seed = new com.campx.academic.calendar.model.CalendarModels.CreateCalendarRequest();
        seed.calendarCode = "CAL-GW-SEED";
        seed.name = "Seed Calendar";
        seed.academicYear = "2026-2027";
        seed.campusId = "CAMPUS-001";
        seed.effectiveFrom = "2026-08-01";
        seed.effectiveTo = "2027-05-31";
        domainService.createCalendar(seed, "TENANT-001", "admin-1", "ACADEMIC_ADMIN");

        calendarServer = new CalendarServer(CAL_PORT, domainService);
        calendarServer.start();

        // 2. Start Gateway on port 8096 configured to proxy to calendarServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/calendars/metrics", "http://localhost:" + CAL_PORT + "/metrics");
        config.addRoute("/api/v1/academics/calendars/metrics", "http://localhost:" + CAL_PORT + "/metrics");
        config.addRoute("/api/v1/calendars", "http://localhost:" + CAL_PORT + "/api/v1/academics/calendars");
        config.addRoute("/api/v1/academics/calendars", "http://localhost:" + CAL_PORT + "/api/v1/academics/calendars");
        config.addRoute("/api/v1/terms", "http://localhost:" + CAL_PORT + "/api/v1/academics/terms");
        config.addRoute("/api/v1/academics/terms", "http://localhost:" + CAL_PORT + "/api/v1/academics/terms");
        config.addRoute("/api/v1/events", "http://localhost:" + CAL_PORT + "/api/v1/academics/events");
        config.addRoute("/api/v1/academics/events", "http://localhost:" + CAL_PORT + "/api/v1/academics/events");
        config.addRoute("/v1/calendars", "http://localhost:" + CAL_PORT + "/api/v1/academics/calendars");
        config.addRoute("/v1/calendar-terms", "http://localhost:" + CAL_PORT + "/api/v1/academics/terms");
        config.addRoute("/v1/calendar-events", "http://localhost:" + CAL_PORT + "/api/v1/academics/events");
        config.addRoute("/v1/calendar-catalog", "http://localhost:" + CAL_PORT + "/api/v1/academics/calendars");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (calendarServer != null) calendarServer.stop();
        CampXLoggerFactory.flush();
    }

    @Test
    public void testCreateCalendarViaGateway() throws Exception {
        String payload = "{"
                + "\"calendarCode\":\"CAL-GW-2026\","
                + "\"name\":\"Gateway Academic Calendar 2026-27\","
                + "\"description\":\"Annual calendar routed through Gateway\","
                + "\"academicYear\":\"2026-2027\","
                + "\"campusId\":\"CAMP-ALPHA\","
                + "\"effectiveFrom\":\"2026-08-01\","
                + "\"effectiveTo\":\"2027-05-31\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/calendars");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-CAL-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ADMIN");
        conn.setRequestProperty("X-User-Id", "admin-1");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-GW-CAL-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"calendarCode\":\"CAL-GW-2026\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
    }

    @Test
    public void testGetCalendarListCanonicalAliasViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/calendars");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-CAL-LIST");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "STUDENT");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-GW-CAL-LIST", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"data\""));
        assertTrue(resp.contains("\"calendarCode\":\"CAL-GW-SEED\""));
    }

    @Test
    public void testCalendarMetricsViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/calendars/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-CAL-METRICS");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-GW-CAL-METRICS", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("acd07_requests_total"));
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
