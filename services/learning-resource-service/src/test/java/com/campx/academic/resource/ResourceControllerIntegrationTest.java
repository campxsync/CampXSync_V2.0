package com.campx.academic.resource;

import com.campx.academic.resource.model.ResourceModels.CreateResourceRequest;
import com.campx.academic.resource.server.ResourceServer;
import com.campx.academic.resource.service.ResourceDomainService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Full HTTP Integration Test for ACD-08 Learning Resource Service REST endpoints.
 */
public class ResourceControllerIntegrationTest {

    private static ResourceServer server;
    private static ResourceDomainService domainService;
    private static final int TEST_PORT = 8092;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeClass
    public static void startServer() throws Exception {
        domainService = new ResourceDomainService();

        // Seed a sample resource
        CreateResourceRequest seed = new CreateResourceRequest();
        seed.resourceCode = "RES-SEED-01";
        seed.title = "Seed Learning Resource";
        seed.resourceType = "STUDY_MATERIAL";
        seed.departmentId = "DEP_CS";
        seed.storageObjectRef = "materials/seed.pdf";
        seed.mimeType = "application/pdf";
        seed.fileSize = 1000L;
        seed.checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        domainService.createResource(seed, "TENANT-001", "faculty-1", "FACULTY", null, "SEED-TRACE");

        server = new ResourceServer(TEST_PORT, domainService);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        URL url = new URL(BASE_URL + "/actuator/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        assertEquals(200, code);

        String body = readStream(conn.getInputStream());
        assertTrue(body.contains("\"status\":\"UP\""));
        assertTrue(body.contains("ACD-08-LearningResourceService"));
    }

    @Test
    public void testMetricsEndpoint() throws Exception {
        URL url = new URL(BASE_URL + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        assertEquals(200, code);

        String body = readStream(conn.getInputStream());
        assertTrue(body.contains("acd08_resource_registrations_total"));
        assertTrue(body.contains("acd08_http_requests_total"));
    }

    @Test
    public void testRegisterResourceViaRest() throws Exception {
        String payload = "{"
                + "\"resourceCode\":\"RES-REST-TEST-01\","
                + "\"title\":\"Data Structures Lecture Slides\","
                + "\"resourceType\":\"LECTURE_NOTES\","
                + "\"subjectId\":\"SUB_CS101\","
                + "\"departmentId\":\"DEP_CS\","
                + "\"description\":\"Slides for Chapter 1\","
                + "\"storageObjectRef\":\"slides/cs101_ch1.pdf\","
                + "\"mimeType\":\"application/pdf\","
                + "\"fileSize\":500000,"
                + "\"checksum\":\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\""
                + "}";

        URL url = new URL(BASE_URL + "/api/v1/academics/resources");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Id", "faculty-1");
        conn.setRequestProperty("X-User-Role", "FACULTY");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertEquals(201, code);

        String body = readStream(conn.getInputStream());
        assertTrue(body.contains("\"status\":\"SUCCESS\""));
        assertTrue(body.contains("RES-REST-TEST-01"));
    }

    @Test
    public void testListAndSearchResourcesViaRest() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/academics/resources?departmentId=DEP_CS");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Id", "admin-1");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        int code = conn.getResponseCode();
        assertEquals(200, code);

        String body = readStream(conn.getInputStream());
        assertTrue(body.contains("RES-SEED-01"));
    }

    @Test
    public void testNotFoundReturnsRfc7807ProblemDetails() throws Exception {
        URL url = new URL(BASE_URL + "/api/v1/academics/resources/NON-EXISTENT-ID");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");

        int code = conn.getResponseCode();
        assertEquals(404, code);

        String body = readStream(conn.getErrorStream());
        assertTrue(body.contains("ACD_RESOURCE_NOT_FOUND"));
        assertTrue(body.contains("\"status\":404"));
        assertTrue(body.contains("\"type\":\"https://api.campx.internal/errors/acd_resource_not_found\""));
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
