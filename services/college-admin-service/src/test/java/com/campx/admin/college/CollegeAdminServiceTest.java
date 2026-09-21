package com.campx.admin.college;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
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

public class CollegeAdminServiceTest {

    private static CollegeAdminServer server;
    private static final int TEST_PORT = 8092;

    @BeforeClass
    public static void setup() throws Exception {
        server = new CollegeAdminServer(TEST_PORT, new CollegeAdminDomainService());
        server.start();
    }

    @AfterClass
    public static void teardown() {
        if (server != null) {
            server.stop();
        }
        CampXLoggerFactory.flush();
    }

    @Test
    public void testProfileAndDepartments() throws Exception {
        // 1. Get Profile
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/profile");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"code\":\"COL_ENGG_01\""));

        // 2. Create Department
        String depJson = "{"
                + "\"departmentCode\":\"MECH\","
                + "\"name\":\"Mechanical Engineering\","
                + "\"headUserId\":\"FAC_HOD_MECH\""
                + "}";
        URL depUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments");
        HttpURLConnection depConn = (HttpURLConnection) depUrl.openConnection();
        depConn.setRequestMethod("POST");
        depConn.setDoOutput(true);
        depConn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = depConn.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, depConn.getResponseCode());
        String depResp = readResponse(depConn);
        assertTrue(depResp.contains("\"code\":\"MECH\""));
    }

    @Test
    public void testDataImportJob() throws Exception {
        String importJson = "{"
                + "\"entityType\":\"STUDENT\","
                + "\"fileRef\":\"s3://campx-imports/admissions-2026.csv\","
                + "\"mode\":\"INSERT\","
                + "\"idempotencyKey\":\"IMPORT-KEY-7788\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/imports");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(importJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"COMPLETED\""));
        assertTrue(resp.contains("\"processed\":98"));
    }

    @Test
    public void testGovernanceDocumentWorkflow() throws Exception {
        // 1. Register Document
        String docJson = "{"
                + "\"documentType\":\"ACCREDITATION_REPORT\","
                + "\"title\":\"NBA Self-Assessment Report 2026\","
                + "\"ownerId\":\"DEAN_ACADEMICS\","
                + "\"classification\":\"CONFIDENTIAL\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(docJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"checksum\":\"SHA256-"));

        int idIdx = resp.indexOf("\"id\":\"") + 6;
        String docId = resp.substring(idIdx, resp.indexOf("\"", idIdx));

        // 2. Submit for approval
        URL apprUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents/" + docId + "/submit");
        HttpURLConnection apprConn = (HttpURLConnection) apprUrl.openConnection();
        apprConn.setRequestMethod("POST");

        assertEquals(200, apprConn.getResponseCode());
        String apprResp = readResponse(apprConn);
        assertTrue(apprResp.contains("\"status\":\"APPROVED\""));
    }

    @Test
    public void testDuplicateDepartmentCodeConflict409() throws Exception {
        String depJson = "{"
                + "\"departmentCode\":\"CIVIL\","
                + "\"name\":\"Civil Engineering\","
                + "\"headUserId\":\"FAC_HOD_CIVIL\""
                + "}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments");

        // First creation succeeds
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // Duplicate creation fails with 409 Conflict
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(depJson.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn2.getResponseCode();
        assertEquals(409, code);

        String errResp = readResponse(conn2);
        assertTrue(errResp.contains("\"status\":409"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_DUPLICATE_RESOURCE\""));
        assertTrue(errResp.contains("\"error\":\"Conflict\""));
        assertTrue(errResp.contains("CIVIL"));
    }

    @Test
    public void testRetireNonExistentDepartment404() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments/NON_EXISTENT_DEP");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":404"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_RESOURCE_NOT_FOUND\""));
        assertTrue(errResp.contains("\"error\":\"Not Found\""));
    }

    @Test
    public void testRetireDepartmentWithActiveProgramsLifecycle422() throws Exception {
        // CSE has active program 'B.Tech Computer Science and Engineering'
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/departments/CSE");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");

        int code = conn.getResponseCode();
        assertEquals(422, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":422"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_INVALID_LIFECYCLE_STATE\""));
        assertTrue(errResp.contains("actively referenced"));
    }

    @Test
    public void testDocumentWithoutClassification400() throws Exception {
        String docJson = "{"
                + "\"documentType\":\"AUDIT_REPORT\","
                + "\"title\":\"Internal Financial Audit 2026\","
                + "\"ownerId\":\"FINANCE_OFFICER\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/college-admin/documents");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(docJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM02_DOCUMENT_GOVERNANCE_ERROR\""));
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
