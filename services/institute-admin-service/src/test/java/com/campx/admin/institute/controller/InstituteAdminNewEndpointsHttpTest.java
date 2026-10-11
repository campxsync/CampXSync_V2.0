package com.campx.admin.institute.controller;

import com.campx.admin.institute.model.InstituteModels.*;
import com.campx.admin.institute.security.GatewayHmacVerifier;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * HTTP Integration tests verifying REST endpoints for newly completed ADM-01 platform migrations:
 * - Academic Calendars & Events (Item 23)
 * - Number Sequences (Item 24)
 * - Reference Lookups (Item 25)
 * - Access Events Audit Log (Item 26)
 * - Change Log Audit Trail (Item 27)
 */
public class InstituteAdminNewEndpointsHttpTest {

    private static int TEST_PORT;
    private static HttpServer server;
    private static InstituteAdminDomainService domainService;

    private static final String CALLER_USER_ID = UUID.randomUUID().toString();
    private static final String CALLER_TENANT_ID = "0c914bb2-f63b-472c-a99a-39977112935d";

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @BeforeClass
    public static void startServer() throws Exception {
        TEST_PORT = findFreePort();
        domainService = new InstituteAdminDomainService();
        InstituteAdminController controller = new InstituteAdminController(
                domainService, null, new GatewayHmacVerifier(false, null, 60));

        server = HttpServer.create(new InetSocketAddress(TEST_PORT), 0);
        server.createContext("/api/v1/admin", controller);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private HttpURLConnection openConnection(String path, String method) throws IOException {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-User-Id", CALLER_USER_ID);
        conn.setRequestProperty("X-Tenant-Id", CALLER_TENANT_ID);
        conn.setRequestProperty("X-User-Role", "SUPER_ADMIN");
        conn.setRequestProperty("Content-Type", "application/json");
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // =========================================================================
    // 1. Calendars & Events (Item 23)
    // =========================================================================

    @Test
    public void testCalendarLifecycleAndEvents() throws Exception {
        String calCode = ("CAL_TEST_" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
        String calPayload = "{\"calendarCode\":\"" + calCode + "\",\"name\":\"Academic Calendar 2026-27\",\"timezone\":\"Asia/Kolkata\"}";

        // POST /api/v1/admin/calendars
        HttpURLConnection createConn = openConnection("/api/v1/admin/calendars", "POST");
        createConn.setDoOutput(true);
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(calPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        System.out.println("DEBUG calendar createResp=" + createResp);
        assertTrue(createResp.contains("\"code\":\"" + calCode + "\""));

        // GET /api/v1/admin/calendars
        HttpURLConnection listConn = openConnection("/api/v1/admin/calendars", "GET");
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("\"calendars\":["));
        assertTrue(listResp.contains(calCode));

        // Extract ID from createResp
        int idIdx = createResp.indexOf("\"id\":\"") + 6;
        String calendarId = createResp.substring(idIdx, createResp.indexOf("\"", idIdx));

        // POST /api/v1/admin/calendars/{calendarId}/events
        String eventPayload = "{\"title\":\"Semester Orientation\",\"eventType\":\"EVENT\",\"isHoliday\":false}";
        HttpURLConnection eventConn = openConnection("/api/v1/admin/calendars/" + calendarId + "/events", "POST");
        eventConn.setDoOutput(true);
        try (OutputStream os = eventConn.getOutputStream()) {
            os.write(eventPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, eventConn.getResponseCode());
        String eventResp = readResponse(eventConn);
        assertTrue(eventResp.contains("\"title\":\"Semester Orientation\""));

        // GET /api/v1/admin/calendars/{calendarId}/events
        HttpURLConnection listEventsConn = openConnection("/api/v1/admin/calendars/" + calendarId + "/events", "GET");
        assertEquals(200, listEventsConn.getResponseCode());
        String listEventsResp = readResponse(listEventsConn);
        assertTrue(listEventsResp.contains("\"events\":["));
        assertTrue(listEventsResp.contains("Semester Orientation"));
    }

    // =========================================================================
    // 2. Number Sequences (Item 24)
    // =========================================================================

    @Test
    public void testNumberSequenceCreationAndAtomicGeneration() throws Exception {
        String scopeKey = ("SEQ_" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
        String seqPayload = "{\"scopeKey\":\"" + scopeKey + "\",\"prefix\":\"STU-\",\"nextValue\":1001,\"padding\":6}";

        // POST /api/v1/admin/number-sequences
        HttpURLConnection createConn = openConnection("/api/v1/admin/number-sequences", "POST");
        createConn.setDoOutput(true);
        try (OutputStream os = createConn.getOutputStream()) {
            os.write(seqPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        System.out.println("DEBUG sequence createResp=" + createResp);
        assertTrue(createResp.contains("\"scopeKey\":\"" + scopeKey + "\""));

        // GET /api/v1/admin/number-sequences
        HttpURLConnection listConn = openConnection("/api/v1/admin/number-sequences", "GET");
        assertEquals(200, listConn.getResponseCode());
        String listResp = readResponse(listConn);
        assertTrue(listResp.contains("\"sequences\":["));
        assertTrue(listResp.contains(scopeKey));

        // POST /api/v1/admin/number-sequences/next
        String nextPayload = "{\"scopeKey\":\"" + scopeKey + "\"}";
        HttpURLConnection nextConn = openConnection("/api/v1/admin/number-sequences/next", "POST");
        nextConn.setDoOutput(true);
        try (OutputStream os = nextConn.getOutputStream()) {
            os.write(nextPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, nextConn.getResponseCode());
        String nextResp = readResponse(nextConn);
        assertTrue(nextResp.contains("\"generatedNumber\":\"STU-001001\""));
    }

    // =========================================================================
    // 3. Reference Lookups (Item 25)
    // =========================================================================

    @Test
    public void testLookupTypesAndValues() throws Exception {
        String typeCode = ("LOOKUP_" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
        String typePayload = "{\"code\":\"" + typeCode + "\",\"name\":\"Academic Program Level\"}";

        // POST /api/v1/admin/lookups/types
        HttpURLConnection createTypeConn = openConnection("/api/v1/admin/lookups/types", "POST");
        createTypeConn.setDoOutput(true);
        try (OutputStream os = createTypeConn.getOutputStream()) {
            os.write(typePayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, createTypeConn.getResponseCode());
        String typeResp = readResponse(createTypeConn);
        assertTrue(typeResp.contains("\"code\":\"" + typeCode + "\""));

        // Extract Type ID
        int idIdx = typeResp.indexOf("\"id\":\"") + 6;
        String typeId = typeResp.substring(idIdx, typeResp.indexOf("\"", idIdx));

        // GET /api/v1/admin/lookups/types
        HttpURLConnection listTypeConn = openConnection("/api/v1/admin/lookups/types", "GET");
        assertEquals(200, listTypeConn.getResponseCode());
        String listTypeResp = readResponse(listTypeConn);
        assertTrue(listTypeResp.contains("\"lookupTypes\":["));
        assertTrue(listTypeResp.contains(typeCode));

        // POST /api/v1/admin/lookups/values
        String valPayload = "{\"lookupTypeId\":\"" + typeId + "\",\"code\":\"UG\",\"label\":\"Undergraduate Degree\"}";
        HttpURLConnection valConn = openConnection("/api/v1/admin/lookups/values", "POST");
        valConn.setDoOutput(true);
        try (OutputStream os = valConn.getOutputStream()) {
            os.write(valPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, valConn.getResponseCode());
        String valResp = readResponse(valConn);
        assertTrue(valResp.contains("\"code\":\"UG\""));
        assertTrue(valResp.contains("\"label\":\"Undergraduate Degree\""));

        // GET /api/v1/admin/lookups/values?lookupTypeId={typeId}
        HttpURLConnection listValConn = openConnection("/api/v1/admin/lookups/values?lookupTypeId=" + typeId, "GET");
        assertEquals(200, listValConn.getResponseCode());
        String listValResp = readResponse(listValConn);
        assertTrue(listValResp.contains("\"lookupValues\":["));
        assertTrue(listValResp.contains("Undergraduate Degree"));
    }

    // =========================================================================
    // 4. Access Events & Audit Change Log (Items 26 & 27)
    // =========================================================================

    @Test
    public void testAuditAccessEventsAndChangeLogs() throws Exception {
        com.campx.logger.context.LogContext.setTenantId(CALLER_TENANT_ID);
        com.campx.logger.context.LogContext.setUserId(CALLER_USER_ID);

        // Record an access event via domain service
        AccessEvent event = new AccessEvent();
        event.setPrincipalId(CALLER_USER_ID);
        event.setResourceType("STUDENT_RECORD");
        event.setResourceId("STU_9941");
        event.setAccessType("READ");
        event.setIp("127.0.0.1");
        domainService.recordAccessEvent(event);

        // GET /api/v1/admin/audit/access-events
        HttpURLConnection accessConn = openConnection("/api/v1/admin/audit/access-events?limit=10", "GET");
        assertEquals(200, accessConn.getResponseCode());
        String accessResp = readResponse(accessConn);
        assertTrue(accessResp.contains("\"accessEvents\":["));
        assertTrue(accessResp.contains("STUDENT_RECORD"));

        // Record a change log via domain service
        AuditChangeLog change = new AuditChangeLog();
        change.setTableSchema("core");
        change.setTableName("courses");
        change.setRecordId("CRS_9901");
        change.setAction("U");
        change.setActorId(CALLER_USER_ID);
        domainService.recordChangeLog(change);

        // GET /api/v1/admin/audit/change-log
        HttpURLConnection changeConn = openConnection("/api/v1/admin/audit/change-log?limit=10", "GET");
        assertEquals(200, changeConn.getResponseCode());
        String changeResp = readResponse(changeConn);
        assertTrue(changeResp.contains("\"changeLogs\":["));
        assertTrue(changeResp.contains("courses"));
    }
}
