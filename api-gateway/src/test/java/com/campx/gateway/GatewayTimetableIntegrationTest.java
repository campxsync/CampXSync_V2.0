package com.campx.gateway;

import com.campx.academic.timetable.server.TimetableServer;
import com.campx.academic.timetable.service.TimetableDomainService;
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
 * correlation tracking, and transparent pass-through to ACD-05 Timetable Management Service.
 */
public class GatewayTimetableIntegrationTest {

    private static TimetableServer timetableServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8093;
    private static final int TT_PORT = 8092;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-05 Timetable Management Service on port 8092
        timetableServer = new TimetableServer(TT_PORT, new TimetableDomainService());
        timetableServer.start();

        // 2. Start Gateway on port 8093 configured to proxy to timetableServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/timetables/metrics", "http://localhost:" + TT_PORT + "/metrics");
        config.addRoute("/api/v1/academics/timetables/metrics", "http://localhost:" + TT_PORT + "/metrics");
        config.addRoute("/api/v1/timetables", "http://localhost:" + TT_PORT + "/api/v1/academics/timetables");
        config.addRoute("/api/v1/academics/timetables", "http://localhost:" + TT_PORT + "/api/v1/academics/timetables");
        config.addRoute("/api/v1/export", "http://localhost:" + TT_PORT + "/api/v1/academics/timetables/export");
        config.addRoute("/v1/timetables", "http://localhost:" + TT_PORT + "/api/v1/academics/timetables");
        config.addRoute("/v1/timetable-catalog", "http://localhost:" + TT_PORT + "/api/v1/academics/timetables");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (timetableServer != null) timetableServer.stop();
        CampXLoggerFactory.flush();
    }

    @Test
    public void testRouteCreateTimetableViaGateway() throws Exception {
        String payload = "{"
                + "\"timetableCode\":\"GW_TT_101\","
                + "\"name\":\"B.Tech Gateway Timetable\","
                + "\"academicYear\":\"2026-2027\","
                + "\"semester\":\"1\","
                + "\"departmentId\":\"DEP-CSE\","
                + "\"programId\":\"PROG-BTECH-CSE\","
                + "\"effectiveFrom\":\"2026-08-15\","
                + "\"effectiveTo\":\"2026-12-15\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/timetables");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-TT-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-GW-TT-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"timetableCode\":\"GW_TT_101\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
    }

    @Test
    public void testRouteTimetableCanonicalAlias() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/timetables");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-TT-ALIAS");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("\"data\""));
    }

    @Test
    public void testRouteTimetableMetricsViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/timetables/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-TT-METRICS");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("acd05_request_total"));
        assertTrue(resp.contains("acd05_outbox_backlog"));
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
