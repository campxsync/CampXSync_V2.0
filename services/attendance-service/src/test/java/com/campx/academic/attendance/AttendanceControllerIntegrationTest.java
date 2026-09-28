package com.campx.academic.attendance;

import com.campx.academic.attendance.server.AttendanceServer;
import com.campx.academic.attendance.service.AttendanceDomainService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * End-to-end HTTP integration test suite for ACD-06 Attendance Management Service.
 */
public class AttendanceControllerIntegrationTest {

    private static AttendanceServer server;
    private static AttendanceDomainService domainService;
    private static int testPort;

    @BeforeClass
    public static void startServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }
        domainService = new AttendanceDomainService();
        server = new AttendanceServer(testPort, domainService);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + testPort + "/api/v1/academics/attendance";
    }

    @Test
    public void testHealthAndMetricsEndpoints() throws Exception {
        // Health
        HttpURLConnection hConn = (HttpURLConnection) new URL("http://localhost:" + testPort + "/actuator/health").openConnection();
        hConn.setRequestMethod("GET");
        assertEquals(200, hConn.getResponseCode());
        String hResp = readStream(hConn.getInputStream());
        assertTrue(hResp.contains("\"status\":\"UP\""));

        // Metrics
        HttpURLConnection mConn = (HttpURLConnection) new URL("http://localhost:" + testPort + "/metrics").openConnection();
        mConn.setRequestMethod("GET");
        assertEquals(200, mConn.getResponseCode());
        String mResp = readStream(mConn.getInputStream());
        assertTrue(mResp.contains("acd06_requests_total"));
    }

    @Test
    public void testFullAttendanceLifecycleHttpFlow() throws Exception {
        // 1. Create Session: POST /sessions
        String createJson = "{" +
                "\"batchId\":\"BATCH-001\"," +
                "\"subjectId\":\"SUB-101\"," +
                "\"timetableEntryId\":\"TT-SLOT-001\"," +
                "\"attendanceDate\":\"2026-11-02\"," +
                "\"periodNo\":1" +
                "}";

        HttpURLConnection cConn = (HttpURLConnection) new URL(getBaseUrl() + "/sessions").openConnection();
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
        assertTrue(cResp.contains("\"sessionId\":\"ATT-SESS-"));
        assertTrue(cResp.contains("\"status\":\"OPEN\""));

        // Extract session ID
        String sessionId = extractJsonValue(cResp, "sessionId");
        assertNotNull(sessionId);

        // 2. Get Session: GET /sessions/{id}
        HttpURLConnection gConn = (HttpURLConnection) new URL(getBaseUrl() + "/sessions/" + sessionId).openConnection();
        gConn.setRequestMethod("GET");
        gConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, gConn.getResponseCode());
        String gResp = readStream(gConn.getInputStream());
        assertTrue(gResp.contains(sessionId));

        // 3. Mark Attendance: POST /sessions/{id}/records
        String markJson = "{" +
                "\"submittedBy\":\"FAC-001\"," +
                "\"records\":[" +
                "{\"studentId\":\"STU-001\",\"status\":\"PRESENT\"}," +
                "{\"studentId\":\"STU-002\",\"status\":\"ABSENT\"}," +
                "{\"studentId\":\"STU-003\",\"status\":\"PRESENT\"}" +
                "]}";

        HttpURLConnection mConn = (HttpURLConnection) new URL(getBaseUrl() + "/sessions/" + sessionId + "/records").openConnection();
        mConn.setRequestMethod("POST");
        mConn.setRequestProperty("Content-Type", "application/json");
        mConn.setRequestProperty("X-User-Role", "FACULTY");
        mConn.setRequestProperty("X-User-Id", "FAC-001");
        mConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        mConn.setDoOutput(true);
        try (OutputStream os = mConn.getOutputStream()) {
            os.write(markJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, mConn.getResponseCode());
        String mResp = readStream(mConn.getInputStream());
        assertTrue(mResp.contains("\"markedCount\":3"));
        assertTrue(mResp.contains("\"presentCount\":2"));
        assertTrue(mResp.contains("\"absentCount\":1"));

        // 4. Correct Record: PUT /sessions/{id}/records/{studentId}/correct
        String corrJson = "{" +
                "\"studentId\":\"STU-002\"," +
                "\"newStatus\":\"PRESENT\"," +
                "\"reason\":\"Student arrived with official college bus delay slip\"," +
                "\"expectedVersion\":1" +
                "}";

        HttpURLConnection corrConn = (HttpURLConnection) new URL(getBaseUrl() + "/sessions/" + sessionId + "/records/STU-002/correct").openConnection();
        corrConn.setRequestMethod("PUT");
        corrConn.setRequestProperty("Content-Type", "application/json");
        corrConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        corrConn.setRequestProperty("X-User-Id", "admin-1");
        corrConn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        corrConn.setDoOutput(true);
        try (OutputStream os = corrConn.getOutputStream()) {
            os.write(corrJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, corrConn.getResponseCode());
        String corrResp = readStream(corrConn.getInputStream());
        assertTrue(corrResp.contains("\"newStatus\":\"PRESENT\""));
        assertTrue(corrResp.contains("\"oldStatus\":\"ABSENT\""));

        // 5. Submit Session: POST /sessions/{id}/submit
        HttpURLConnection subConn = (HttpURLConnection) new URL(getBaseUrl() + "/sessions/" + sessionId + "/submit").openConnection();
        subConn.setRequestMethod("POST");
        subConn.setRequestProperty("X-User-Role", "FACULTY");
        subConn.setRequestProperty("X-User-Id", "FAC-001");
        assertEquals(200, subConn.getResponseCode());
        String subResp = readStream(subConn.getInputStream());
        assertTrue(subResp.contains("\"status\":\"SUBMITTED\""));

        // 6. View Student Summary: GET /summaries/student/{studentId}
        HttpURLConnection sumConn = (HttpURLConnection) new URL(getBaseUrl() + "/summaries/student/STU-001").openConnection();
        sumConn.setRequestMethod("GET");
        sumConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, sumConn.getResponseCode());
        String sumResp = readStream(sumConn.getInputStream());
        assertTrue(sumResp.contains("\"studentId\":\"STU-001\""));

        // 7. Report Query: GET /report
        HttpURLConnection repConn = (HttpURLConnection) new URL(getBaseUrl() + "/report?batchId=BATCH-001").openConnection();
        repConn.setRequestMethod("GET");
        repConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, repConn.getResponseCode());
        String repResp = readStream(repConn.getInputStream());
        assertTrue(repResp.contains("\"totalSessions\":"));

        // 8. Export Query: GET /export
        HttpURLConnection expConn = (HttpURLConnection) new URL(getBaseUrl() + "/export?batchId=BATCH-001&format=PDF").openConnection();
        expConn.setRequestMethod("GET");
        expConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, expConn.getResponseCode());
        String expResp = readStream(expConn.getInputStream());
        assertTrue(expResp.contains("\"format\":\"PDF\""));
        assertTrue(expResp.contains("\"downloadUrl\":"));
    }

    @Test
    public void testRbacStudentIsolationEnforcement() throws Exception {
        // Student STU-001 attempting to inspect STU-002 history -> 403 Forbidden
        HttpURLConnection conn = (HttpURLConnection) new URL(getBaseUrl() + "/student/STU-002").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "STUDENT");
        conn.setRequestProperty("X-User-Id", "STU-001"); // Mismatch!
        assertEquals(403, conn.getResponseCode());

        String err = readStream(conn.getErrorStream());
        assertTrue(err.contains("\"code\":\"ACD_ATTENDANCE_FORBIDDEN\""));
        assertTrue(err.contains("\"errorCode\":\"ACD_ATTENDANCE_FORBIDDEN\""));
    }

    @Test
    public void testRfc7807ProblemDetailsErrorFormat() throws Exception {
        // Request with non-existent session
        HttpURLConnection conn = (HttpURLConnection) new URL(getBaseUrl() + "/sessions/NON-EXISTENT-ID").openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(404, conn.getResponseCode());

        String err = readStream(conn.getErrorStream());
        assertTrue(err.contains("\"code\":\"ACD_ATTENDANCE_NOT_FOUND\""));
        assertTrue(err.contains("\"errorCode\":\"ACD_ATTENDANCE_NOT_FOUND\""));
        assertTrue(err.contains("\"status\":404"));
        assertTrue(err.contains("\"detail\":"));
        assertTrue(err.contains("\"instance\":"));
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
