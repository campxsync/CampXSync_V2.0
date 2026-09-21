package com.campx.admin.college;

import com.campx.admin.college.server.CollegeAdminServer;
import com.campx.admin.college.service.CollegeAdminDomainService;
import com.campx.logger.CampXLoggerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
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

    private String readResponse(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
