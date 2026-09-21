package com.campx.admin.institute;

import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.admin.institute.service.InstituteAdminDomainService;
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

public class InstituteAdminServiceTest {

    private static InstituteAdminServer server;
    private static final int TEST_PORT = 8091;

    @BeforeClass
    public static void setup() throws Exception {
        server = new InstituteAdminServer(TEST_PORT, new InstituteAdminDomainService());
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
    public void testRegisterInstituteAndColleges() throws Exception {
        // 1. Create Institute
        String createJson = "{"
                + "\"instituteCode\":\"INST_TEST_01\","
                + "\"legalName\":\"National Institute of Technology\","
                + "\"displayName\":\"NIT Central\","
                + "\"timezone\":\"Asia/Kolkata\","
                + "\"locale\":\"en_IN\","
                + "\"defaultCurrency\":\"INR\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(createJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"instituteCode\":\"INST_TEST_01\""));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));

        // Extract id
        int idIdx = resp.indexOf("\"id\":\"") + 6;
        String instId = resp.substring(idIdx, resp.indexOf("\"", idIdx));

        // 2. Register College under Institute
        String collegeJson = "{"
                + "\"collegeCode\":\"NIT_ENGG\","
                + "\"name\":\"College of Engineering\","
                + "\"instituteId\":\"" + instId + "\""
                + "}";

        URL colUrl = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/colleges");
        HttpURLConnection colConn = (HttpURLConnection) colUrl.openConnection();
        colConn.setRequestMethod("POST");
        colConn.setDoOutput(true);
        colConn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = colConn.getOutputStream()) {
            os.write(collegeJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, colConn.getResponseCode());
        String colResp = readResponse(colConn);
        assertTrue(colResp.contains("\"collegeCode\":\"NIT_ENGG\""));
    }

    @Test
    public void testTenantProvisioningWorkflow() throws Exception {
        String provJson = "{"
                + "\"targetScope\":\"CAMPUS_NORTH\","
                + "\"planId\":\"ENTERPRISE_CAMPUS_2026\","
                + "\"idempotencyKey\":\"IDEM-PROV-9901\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/tenants/TENANT_NIT_01/provision");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(provJson.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"status\":\"COMPLETED\""));
        assertTrue(resp.contains("\"tenantId\":\"TENANT_NIT_01\""));
    }

    @Test
    public void testDuplicateInstituteCodeConflict409() throws Exception {
        String json = "{"
                + "\"instituteCode\":\"INST_DUP_01\","
                + "\"legalName\":\"Duplicate Institute Test\","
                + "\"displayName\":\"Duplicate Inst\","
                + "\"timezone\":\"Asia/Kolkata\","
                + "\"locale\":\"en_IN\","
                + "\"defaultCurrency\":\"INR\""
                + "}";

        // First creation succeeds
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn1 = (HttpURLConnection) url.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // Second creation with duplicate code must fail with 409 Conflict
        HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn2.getResponseCode();
        assertEquals(409, code);

        String errResp = readResponse(conn2);
        assertTrue(errResp.contains("\"status\":409"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_DUPLICATE_RESOURCE\""));
        assertTrue(errResp.contains("\"error\":\"Conflict\""));
        assertTrue(errResp.contains("INST_DUP_01"));
    }

    @Test
    public void testNonExistentInstituteUpdate404() throws Exception {
        String updateJson = "{\"displayName\":\"Non Existent Updated\"}";
        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes/NON_EXISTENT_ID");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(updateJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":404"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_RESOURCE_NOT_FOUND\""));
        assertTrue(errResp.contains("\"error\":\"Not Found\""));
    }

    @Test
    public void testPlaintextSecretSecurityViolation400() throws Exception {
        String configJson = "{"
                + "\"key\":\"security.oauth2.client_secret\","
                + "\"value\":\"plain-unencrypted-secret\","
                + "\"isSecret\":\"true\","
                + "\"scope\":\"GLOBAL\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/configuration");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(configJson.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_PLAINTEXT_SECRET_REJECTED\""));
        assertTrue(errResp.contains("\"error\":\"Bad Request\""));
    }

    @Test
    public void testMissingInstituteCodeReturns400() throws Exception {
        String payload = "{"
                + "\"legalName\":\"No Code University\","
                + "\"displayName\":\"NCU\""
                + "}";

        URL url = new URL("http://localhost:" + TEST_PORT + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(400, code);

        String errResp = readResponse(conn);
        assertTrue(errResp.contains("\"status\":400"));
        assertTrue(errResp.contains("\"errorCode\":\"ADM01_MALFORMED_PAYLOAD\""));
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
