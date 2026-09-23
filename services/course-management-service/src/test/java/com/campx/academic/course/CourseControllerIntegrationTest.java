package com.campx.academic.course;

import com.campx.academic.course.server.CourseServer;
import com.campx.academic.course.service.CourseDomainService;
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
import static org.junit.Assert.assertTrue;

/**
 * HTTP REST integration tests for ACD-01 Course Management Service.
 * <p>
 * Exercises end-to-end HTTP request handling over loopback port 8089:
 * <ul>
 *   <li>Course creation via HTTP POST with payload validation</li>
 *   <li>Course lifecycle transitions (approval, publication)</li>
 *   <li>Public catalog projection querying</li>
 *   <li>Prerequisite linking and DAG cycle validation</li>
 * </ul>
 */
public class CourseControllerIntegrationTest {

    /**
     * Embedded test HTTP server instance.
     */
    private static CourseServer server;

    /**
     * Dedicated TCP port for running integration tests against Course Management Service.
     */
    private static final int TEST_PORT = 8089;

    /**
     * Base HTTP URL endpoint for test requests.
     */
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    /**
     * Starts the embedded HTTP server on test port 8089 before test execution.
     *
     * @throws Exception if socket creation or server bootstrap fails
     */
    @BeforeClass
    public static void startServer() throws Exception {
        server = new CourseServer(TEST_PORT, new CourseDomainService());
        server.start();
    }

    /**
     * Stops the embedded HTTP server and flushes logging buffers after test suite completion.
     */
    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testCreateCourseViaHttp() throws Exception {
        String payload = "{"
                + "\"courseCode\":\"HTTP_CS101\","
                + "\"courseName\":\"HTTP Programming Course\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"totalCredits\":4.0,"
                + "\"durationYears\":1"
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-ACD01-HTTP-001");
        conn.setRequestProperty("X-Tenant-Id", "CAMPUS_ALPHA");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-ACD01-HTTP-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"courseCode\":\"HTTP_CS101\""));
        assertTrue(resp.contains("\"status\":\"DRAFT\""));
    }

    @Test
    public void testGetCatalogViaHttp() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/courses/catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Trace-Id", "TRACE-CATALOG-001");

        assertEquals(200, conn.getResponseCode());
        assertEquals("TRACE-CATALOG-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"catalog\":["));
        assertTrue(resp.contains("CS101")); // seeded initial course
    }

    @Test
    public void testDuplicateCourseCodeReturnsRfc7807Conflict() throws Exception {
        String payload = "{"
                + "\"courseCode\":\"DUP_CS_01\","
                + "\"courseName\":\"Duplicate Course Test\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"totalCredits\":3.0"
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/courses");

        // 1. First creation succeeds
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // 2. Second creation with duplicate code returns 409 Conflict with RFC 7807 payload
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("X-Trace-Id", "TRACE-DUP-TEST-002");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn2.getResponseCode());
        assertEquals("TRACE-DUP-TEST-002", conn2.getHeaderField("X-Trace-Id"));

        String errResp = readResponse(conn2);
        assertTrue(errResp.contains("\"status\":409"));
        assertTrue(errResp.contains("\"errorCode\":\"ACD_COURSE_CODE_EXISTS\""));
        assertTrue(errResp.contains("\"error\":\"Conflict\""));
        assertTrue(errResp.contains("DUP_CS_01"));
    }

    @Test
    public void testPrerequisiteCycleReturns422ViaHttp() throws Exception {
        // Create 2 courses: CYC_A and CYC_B
        String pA = "{\"courseCode\":\"CYC_A\",\"courseName\":\"Cycle Course A\",\"departmentId\":\"DEP_CS\",\"totalCredits\":3.0}";
        String pB = "{\"courseCode\":\"CYC_B\",\"courseName\":\"Cycle Course B\",\"departmentId\":\"DEP_CS\",\"totalCredits\":3.0}";

        post(BASE_URL + "/api/v1/courses", pA);
        post(BASE_URL + "/api/v1/courses", pB);

        // B depends on A: POST /api/v1/courses/CYC_B/prerequisites with prerequisiteCourseId=CYC_A
        String prereqPayload1 = "{\"prerequisiteCourseId\":\"CYC_A\",\"relationshipType\":\"MANDATORY\"}";
        int code1 = post(BASE_URL + "/api/v1/courses/CYC_B/prerequisites", prereqPayload1);
        assertEquals(201, code1);

        // A depends on B: would create cycle A -> B -> A! Must return 422 Unprocessable Entity
        String prereqPayload2 = "{\"prerequisiteCourseId\":\"CYC_B\",\"relationshipType\":\"MANDATORY\"}";
        URL url = new URL(BASE_URL + "/api/v1/courses/CYC_A/prerequisites");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(prereqPayload2.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(422, conn.getResponseCode());
        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"errorCode\":\"ACD_PREREQUISITE_CYCLE\""));
        assertTrue(errResp.contains("\"status\":422"));
    }

    @Test
    public void testCourseCreditsEndpointViaHttp() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/courses/CS101/credits");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"totalCredits\":4.0"));
        assertTrue(resp.contains("\"internalWeightage\":40.0"));
        assertTrue(resp.contains("\"externalWeightage\":60.0"));
    }

    private int post(String urlStr, String json) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return conn.getResponseCode();
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
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
