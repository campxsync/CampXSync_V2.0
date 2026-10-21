package com.campx.admin.college.controller;

import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * End-to-end HTTP controller test suite for Academic Departments and Programs (Wave 3).
 * <p>
 * Tests full request/response lifecycle through CollegeAdminServer:
 * - Department CRUD: POST (201), GET by id (200), GET list (200), PUT (200), DELETE/retire (200)
 * - Program CRUD: POST (201), GET by id (200), GET list (200), PUT (200), DELETE/discontinue (200)
 * - Error handling: Duplicate code (409 Conflict), Malformed payload (400 Bad Request), Not Found (404)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DepartmentProgramControllerHttpTest {

    private static CollegeAdminServer server;
    private static CollegeAdminDomainService domainService;
    private static int servicePort;

    private static String createdDepartmentId;
    private static String createdProgramId;

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty("campx.internal.auth.enabled", "false");
        domainService = new CollegeAdminDomainService();
        server = new CollegeAdminServer(0, domainService);
        server.start();
        servicePort = server.getPort();
    }

    @AfterClass
    public static void teardown() {
        if (server != null) {
            server.stop();
        }
        System.clearProperty("campx.internal.auth.enabled");
        CampXLoggerFactory.flush();
    }

    // =========================================================================
    // Department HTTP Tests
    // =========================================================================

    @Test
    public void test01_createDepartmentSuccess_returns201() throws Exception {
        String json = "{"
                + "\"departmentCode\":\"AERO\","
                + "\"name\":\"Aerospace Engineering\","
                + "\"headUserId\":\"FAC_HOD_AERO\""
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue("Response must contain department code", resp.contains("\"code\":\"AERO\""));
        assertTrue("Response must contain department name", resp.contains("Aerospace Engineering"));
        assertTrue("Response must contain status ACTIVE", resp.contains("\"status\":\"ACTIVE\""));

        // Extract ID for subsequent tests
        int idIdx = resp.indexOf("\"id\":\"");
        if (idIdx != -1) {
            int endIdx = resp.indexOf("\"", idIdx + 6);
            createdDepartmentId = resp.substring(idIdx + 6, endIdx);
        }
        assertNotNull("Created department ID must be parsed", createdDepartmentId);
    }

    @Test
    public void test02_getDepartmentSuccess_returns200() throws Exception {
        assertNotNull("Department must be created first", createdDepartmentId);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments/" + createdDepartmentId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("\"code\":\"AERO\""));
        assertTrue(resp.contains("\"id\":\"" + createdDepartmentId + "\""));
    }

    @Test
    public void test03_listDepartmentsSuccess_returns200() throws Exception {
        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("departments"));
        assertTrue(resp.contains("AERO"));
    }

    @Test
    public void test04_updateDepartmentSuccess_returns200() throws Exception {
        assertNotNull("Department must be created first", createdDepartmentId);

        String json = "{"
                + "\"name\":\"Department of Aerospace Engineering\","
                + "\"headUserId\":\"FAC_HOD_AERO_NEW\""
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments/" + createdDepartmentId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("Department of Aerospace Engineering"));
    }

    @Test
    public void test05_createDepartmentDuplicateCode_returns409() throws Exception {
        String json = "{"
                + "\"departmentCode\":\"AERO\","
                + "\"name\":\"Duplicate Aerospace Department\""
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn.getResponseCode());
        String err = readStream(conn.getErrorStream());
        assertTrue("Error response must indicate conflict", err.contains("RESOURCE_CONFLICT") || err.contains("Conflict") || err.contains("AERO"));
    }

    @Test
    public void test06_createDepartmentMalformedPayload_returns400() throws Exception {
        String json = "{"
                + "\"departmentCode\":\"\","
                + "\"name\":\"\""
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(400, conn.getResponseCode());
    }

    // =========================================================================
    // Program HTTP Tests
    // =========================================================================

    @Test
    public void test07_createProgramSuccess_returns201() throws Exception {
        assertNotNull("Parent department must exist", createdDepartmentId);

        String json = "{"
                + "\"programCode\":\"BTECH_AERO\","
                + "\"name\":\"Bachelor of Technology in Aerospace\","
                + "\"departmentId\":\"" + createdDepartmentId + "\","
                + "\"durationYears\":4,"
                + "\"level\":\"UG\","
                + "\"published\":true"
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("BTECH_AERO"));
        assertTrue(resp.contains("Aerospace"));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));

        int idIdx = resp.indexOf("\"id\":\"");
        if (idIdx != -1) {
            int endIdx = resp.indexOf("\"", idIdx + 6);
            createdProgramId = resp.substring(idIdx + 6, endIdx);
        }
        assertNotNull("Created program ID must be parsed", createdProgramId);
    }

    @Test
    public void test08_getProgramSuccess_returns200() throws Exception {
        assertNotNull("Program must be created first", createdProgramId);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs/" + createdProgramId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("BTECH_AERO"));
        assertTrue(resp.contains("\"id\":\"" + createdProgramId + "\""));
    }

    @Test
    public void test09_listProgramsSuccess_returns200() throws Exception {
        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("programs"));
        assertTrue(resp.contains("BTECH_AERO"));
    }

    @Test
    public void test10_updateProgramSuccess_returns200() throws Exception {
        assertNotNull("Program must be created first", createdProgramId);

        String json = "{"
                + "\"name\":\"B.Tech in Aerospace Engineering & Avionics\","
                + "\"durationYears\":4"
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs/" + createdProgramId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("Avionics"));
    }

    @Test
    public void test11_createProgramDuplicateCode_returns409() throws Exception {
        assertNotNull("Parent department must exist", createdDepartmentId);

        String json = "{"
                + "\"programCode\":\"BTECH_AERO\","
                + "\"name\":\"Duplicate Aerospace Program\","
                + "\"departmentId\":\"" + createdDepartmentId + "\""
                + "}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn.getResponseCode());
        String err = readStream(conn.getErrorStream());
        assertTrue(err.contains("RESOURCE_CONFLICT") || err.contains("Conflict") || err.contains("BTECH_AERO"));
    }

    @Test
    public void test12_programNotFound_returns404() throws Exception {
        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs/NON_EXISTENT_PROG_ID");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(404, conn.getResponseCode());
    }

    @Test
    public void test13_discontinueProgramSuccess_returns200() throws Exception {
        assertNotNull("Program must be created first", createdProgramId);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/programs/" + createdProgramId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("DISCONTINUED"));
    }

    @Test
    public void test14_retireDepartmentSuccess_returns200() throws Exception {
        assertNotNull("Department must be created first", createdDepartmentId);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/college-admin/departments/" + createdDepartmentId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("RETIRED"));
    }

    private static String readStream(InputStream is) throws Exception {
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
}
