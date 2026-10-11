package com.campx.gateway;

import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.academic.assessment.server.AssessmentServer;
import com.campx.academic.assessment.service.AssessmentDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
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

import static org.junit.Assert.*;

/**
 * End-to-End integration test verifying API Gateway reverse proxy routing,
 * correlation tracking, and transparent pass-through to ACD-09 Assessment Mapping Service.
 */
public class GatewayAssessmentIntegrationTest {

    private static AssessmentServer assessmentServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8072;
    private static final int ASM_PORT = 8073;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-09 Assessment Mapping Service on port 8093
        AssessmentDomainService domainService = new AssessmentDomainService();
        CreateAssessmentRequest seed = new CreateAssessmentRequest();
        seed.assessmentCode = "ASM-GW-SEED";
        seed.assessmentName = "Gateway Seed Assessment";
        seed.subjectId = "SUB-CS101";
        seed.courseId = "COURSE-CS-BS";
        seed.curriculumId = "CURR-2026-CS";
        seed.academicYear = "2026-2027";
        seed.termId = "TERM-1";
        seed.assessmentType = "INTERNAL";
        seed.totalMarks = 100.0;
        seed.totalWeightage = 100.0;
        domainService.createAssessment(seed, "TENANT-001", "faculty-1", "FACULTY", null, "GW-SEED-TRACE");

        assessmentServer = new AssessmentServer(ASM_PORT, domainService);
        assessmentServer.start();

        // 2. Start Gateway on port 8092 configured to proxy to assessmentServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/academics/assessments/metrics", "http://localhost:" + ASM_PORT + "/metrics");
        config.addRoute("/api/v1/assessments/metrics", "http://localhost:" + ASM_PORT + "/metrics");
        config.addRoute("/api/v1/academics/assessments", "http://localhost:" + ASM_PORT + "/api/v1/academics/assessments");
        config.addRoute("/api/v1/assessments", "http://localhost:" + ASM_PORT + "/api/v1/academics/assessments");
        config.addRoute("/v1/assessments", "http://localhost:" + ASM_PORT + "/api/v1/academics/assessments");
        config.addRoute("/v1/assessment-catalog", "http://localhost:" + ASM_PORT + "/api/v1/academics/assessments");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
        if (assessmentServer != null) {
            assessmentServer.stop();
        }
    }

    private HttpURLConnection sendGatewayRequest(String method, String path, String body) throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Id", "faculty-1");
        conn.setRequestProperty("X-User-Role", "FACULTY");
        conn.setRequestProperty("X-Correlation-Id", "GW-ASM-CORR-12345");
        conn.setRequestProperty("Content-Type", "application/json");

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    @Test
    public void testGatewayRoutingToAssessmentMetrics() throws Exception {
        HttpURLConnection conn = sendGatewayRequest("GET", "/api/v1/academics/assessments/metrics", null);
        assertEquals(200, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("totalRequests"));
    }

    @Test
    public void testGatewayRoutingToAssessmentQuery() throws Exception {
        HttpURLConnection conn = sendGatewayRequest("GET", "/api/v1/academics/assessments?subjectId=SUB-CS101", null);
        assertEquals(200, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("ASM-GW-SEED"));
        assertTrue(body.contains("Gateway Seed Assessment"));
    }

    @Test
    public void testGatewayRoutingViaCanonicalV1Assessments() throws Exception {
        HttpURLConnection conn = sendGatewayRequest("GET", "/v1/assessments?subjectId=SUB-CS101", null);
        assertEquals(200, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("ASM-GW-SEED"));
    }

    @Test
    public void testGatewayTransparentPostCreation() throws Exception {
        String json = "{"
                + "\"assessmentCode\":\"ASM-GW-POST-01\","
                + "\"assessmentName\":\"Created Through API Gateway\","
                + "\"subjectId\":\"SUB-CS101\","
                + "\"courseId\":\"COURSE-CS-BS\","
                + "\"curriculumId\":\"CURR-2026-CS\","
                + "\"academicYear\":\"2026-2027\","
                + "\"termId\":\"TERM-1\","
                + "\"assessmentType\":\"INTERNAL\","
                + "\"totalMarks\":100.0,"
                + "\"totalWeightage\":100.0"
                + "}";

        HttpURLConnection conn = sendGatewayRequest("POST", "/api/v1/academics/assessments", json);
        assertEquals(201, conn.getResponseCode());
        String body = readBody(conn);
        assertTrue(body.contains("ASM-GW-POST-01"));
        assertTrue(body.contains("Created Through API Gateway"));
    }
}
