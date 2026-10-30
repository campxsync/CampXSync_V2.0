package com.campx.academic.calendar;

import com.campx.academic.calendar.server.CalendarServer;
import com.campx.academic.calendar.service.CalendarDomainService;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class CalendarControllerIntegrationTest {

    private static CalendarServer server;
    private static int port = 8096;

    @BeforeClass
    public static void setup() throws Exception {
        CalendarDomainService domainService = new CalendarDomainService();
        server = new CalendarServer(port, domainService);
        server.start();
    }

    @AfterClass
    public static void teardown() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/v1/academics";
    }

    @Test
    public void testFullAcademicCalendarHttpLifecycle() throws Exception {
        // 1. Create Calendar Draft
        String createJson = "{"
                + "\"calendarCode\":\"CAL-HTTP-2026\","
                + "\"name\":\"HTTP Lifecycle Calendar 2026-2027\","
                + "\"academicYear\":\"2026-2027\","
                + "\"campusId\":\"CAMP-ALPHA\","
                + "\"effectiveFrom\":\"2026-08-01\","
                + "\"effectiveTo\":\"2027-05-31\""
                + "}";

        HttpURLConnection cConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars").openConnection();
        cConn.setRequestMethod("POST");
        cConn.setRequestProperty("Content-Type", "application/json");
        cConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        cConn.setRequestProperty("X-User-Id", "admin-1");
        cConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        cConn.setDoOutput(true);
        try (OutputStream os = cConn.getOutputStream()) {
            os.write(createJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, cConn.getResponseCode());
        String cResp = readStream(cConn.getInputStream());
        assertTrue(cResp.contains("\"success\":true"));
        assertTrue(cResp.contains("\"calendarCode\":\"CAL-HTTP-2026\""));
        String calendarId = extractField(cResp, "\"id\":\"", "\"");

        // 2. Add Term to Calendar
        String termJson = "{"
                + "\"termCode\":\"TERM-FALL-26\","
                + "\"name\":\"Fall Semester 2026\","
                + "\"sequenceNo\":1,"
                + "\"startDate\":\"2026-08-01\","
                + "\"endDate\":\"2026-12-15\","
                + "\"instructionalStartDate\":\"2026-08-15\","
                + "\"instructionalEndDate\":\"2026-12-01\""
                + "}";

        HttpURLConnection tConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/terms").openConnection();
        tConn.setRequestMethod("POST");
        tConn.setRequestProperty("Content-Type", "application/json");
        tConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        tConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        tConn.setDoOutput(true);
        try (OutputStream os = tConn.getOutputStream()) {
            os.write(termJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, tConn.getResponseCode());
        String tResp = readStream(tConn.getInputStream());
        assertTrue(tResp.contains("\"termCode\":\"TERM-FALL-26\""));

        // 3. Add Holiday Event
        String eventJson = "{"
                + "\"eventCode\":\"HOL-DIWALI\","
                + "\"eventType\":\"HOLIDAY\","
                + "\"title\":\"Diwali Celebration\","
                + "\"startDate\":\"2026-11-01\","
                + "\"endDate\":\"2026-11-01\","
                + "\"workingDayImpact\":\"NON_WORKING\""
                + "}";

        HttpURLConnection eConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/events").openConnection();
        eConn.setRequestMethod("POST");
        eConn.setRequestProperty("Content-Type", "application/json");
        eConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        eConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        eConn.setDoOutput(true);
        try (OutputStream os = eConn.getOutputStream()) {
            os.write(eventJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, eConn.getResponseCode());
        String eResp = readStream(eConn.getInputStream());
        assertTrue(eResp.contains("\"eventCode\":\"HOL-DIWALI\""));

        // 4. Validate Calendar
        HttpURLConnection vConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/validate").openConnection();
        vConn.setRequestMethod("POST");
        vConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        vConn.setRequestProperty("X-Tenant-Id", "TENANT-001");

        assertEquals(200, vConn.getResponseCode());
        String vResp = readStream(vConn.getInputStream());
        assertTrue(vResp.contains("\"valid\":true"));
        assertTrue(vResp.contains("\"blockingCount\":0"));

        // 5. Submit Calendar
        HttpURLConnection sConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/submit").openConnection();
        sConn.setRequestMethod("POST");
        sConn.setRequestProperty("Content-Type", "application/json");
        sConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        sConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        sConn.setDoOutput(true);
        try (OutputStream os = sConn.getOutputStream()) {
            os.write("{\"reason\":\"Completed term and holiday schedules\"}".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, sConn.getResponseCode());
        String sResp = readStream(sConn.getInputStream());
        assertTrue(sResp.contains("\"status\":\"SUBMITTED\""));

        // 6. Approve Calendar by Registrar
        HttpURLConnection aConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/approval").openConnection();
        aConn.setRequestMethod("POST");
        aConn.setRequestProperty("Content-Type", "application/json");
        aConn.setRequestProperty("X-User-Role", "REGISTRAR");
        aConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        aConn.setDoOutput(true);
        try (OutputStream os = aConn.getOutputStream()) {
            os.write("{\"decision\":\"APPROVED\",\"reason\":\"Meets statutory requirements\"}".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, aConn.getResponseCode());
        String aResp = readStream(aConn.getInputStream());
        assertTrue(aResp.contains("\"status\":\"APPROVED\""));

        // 7. Publish Calendar
        HttpURLConnection pConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/publish").openConnection();
        pConn.setRequestMethod("POST");
        pConn.setRequestProperty("Content-Type", "application/json");
        pConn.setRequestProperty("X-User-Role", "REGISTRAR");
        pConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        pConn.setDoOutput(true);
        try (OutputStream os = pConn.getOutputStream()) {
            os.write("{\"expectedVersion\":1}".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, pConn.getResponseCode());
        String pResp = readStream(pConn.getInputStream());
        assertTrue(pResp.contains("\"status\":\"PUBLISHED\""));

        // 8. Query Current Published Calendar
        HttpURLConnection curConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/current?campusId=CAMP-ALPHA&academicYear=2026-2027").openConnection();
        curConn.setRequestMethod("GET");
        curConn.setRequestProperty("X-User-Role", "STUDENT");
        curConn.setRequestProperty("X-Tenant-Id", "TENANT-001");

        assertEquals(200, curConn.getResponseCode());
        String curResp = readStream(curConn.getInputStream());
        assertTrue(curResp.contains("\"calendarCode\":\"CAL-HTTP-2026\""));

        // 9. Effective Date Resolution
        HttpURLConnection resConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/effective-date?date=2026-11-01").openConnection();
        resConn.setRequestMethod("GET");
        resConn.setRequestProperty("X-User-Role", "FACULTY");
        resConn.setRequestProperty("X-Tenant-Id", "TENANT-001");

        assertEquals(200, resConn.getResponseCode());
        String resResp = readStream(resConn.getInputStream());
        assertTrue(resResp.contains("\"isHoliday\":true"));
        assertTrue(resResp.contains("\"holidayTitle\":\"Diwali Celebration\""));
        assertTrue(resResp.contains("\"workingDayStatus\":\"NON_WORKING\""));

        // 10. Analytics
        HttpURLConnection anConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars/" + calendarId + "/analytics").openConnection();
        anConn.setRequestMethod("GET");
        anConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        anConn.setRequestProperty("X-Tenant-Id", "TENANT-001");

        assertEquals(200, anConn.getResponseCode());
        String anResp = readStream(anConn.getInputStream());
        assertTrue(anResp.contains("\"totalTerms\":1"));
        assertTrue(anResp.contains("\"totalHolidays\":1"));
    }

    @Test
    public void testOperationalEndpointsAndRbac() throws Exception {
        // Health
        HttpURLConnection hConn = (HttpURLConnection) new URL("http://localhost:" + port + "/actuator/health").openConnection();
        assertEquals(200, hConn.getResponseCode());
        assertTrue(readStream(hConn.getInputStream()).contains("\"status\":\"UP\""));

        // Metrics
        HttpURLConnection mConn = (HttpURLConnection) new URL("http://localhost:" + port + "/metrics").openConnection();
        assertEquals(200, mConn.getResponseCode());
        assertTrue(readStream(mConn.getInputStream()).contains("acd07_requests_total"));

        // RBAC Negative Test: Student cannot create a calendar
        HttpURLConnection rbacConn = (HttpURLConnection) new URL(getBaseUrl() + "/calendars").openConnection();
        rbacConn.setRequestMethod("POST");
        rbacConn.setRequestProperty("Content-Type", "application/json");
        rbacConn.setRequestProperty("X-User-Role", "STUDENT");
        rbacConn.setDoOutput(true);
        try (OutputStream os = rbacConn.getOutputStream()) {
            os.write("{}".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, rbacConn.getResponseCode());
        String errResp = readStream(rbacConn.getErrorStream());
        assertTrue(errResp.contains("\"errorCode\":\"ACD_CALENDAR_FORBIDDEN\""));
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private static String extractField(String json, String prefix, String suffix) {
        int start = json.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        int end = json.indexOf(suffix, start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
