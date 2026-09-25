package com.campx.gateway;

import com.campx.academic.subject.server.SubjectServer;
import com.campx.academic.subject.service.SubjectDomainService;
import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.server.GatewayServer;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-End integration test verifying unified API Gateway reverse proxy routing,
 * correlation tracking, and error pass-through to ACD-03 Subject Management Service.
 */
public class GatewaySubjectIntegrationTest {

    private static SubjectServer subjectServer;
    private static GatewayServer gatewayServer;

    private static final int GW_PORT = 8099;
    private static final int SUB_PORT = 8098;

    @BeforeClass
    public static void startAll() throws Exception {
        // 1. Start ACD-03 Subject Management Service on port 8098
        subjectServer = new SubjectServer(SUB_PORT, new SubjectDomainService());
        subjectServer.start();

        // 2. Start Gateway on port 8099 configured to proxy to subjectServer
        GatewayConfig config = new GatewayConfig();
        config.setPort(GW_PORT);
        config.addRoute("/api/v1/subjects/metrics", "http://localhost:" + SUB_PORT + "/metrics");
        config.addRoute("/api/v1/academics/subjects/metrics", "http://localhost:" + SUB_PORT + "/metrics");
        config.addRoute("/api/v1/subjects", "http://localhost:" + SUB_PORT + "/api/v1/subjects");
        config.addRoute("/api/v1/academics/subjects", "http://localhost:" + SUB_PORT + "/api/v1/academics/subjects");
        config.addRoute("/v1/subjects", "http://localhost:" + SUB_PORT + "/api/v1/subjects");
        config.addRoute("/v1/subject-catalog", "http://localhost:" + SUB_PORT + "/api/v1/academics/subjects/catalog");

        gatewayServer = new GatewayServer(config);
        gatewayServer.start();
    }

    @AfterClass
    public static void stopAll() {
        if (gatewayServer != null) gatewayServer.stop();
        if (subjectServer != null) subjectServer.stop();
        CampXLoggerFactory.flush();
    }

    /**
     * Tests proxying subject creation via API Gateway through /api/v1/academics/subjects.
     * Verifies HTTP 201 Created and correlation tracing header propagation.
     */
    @Test
    public void testRouteCreateSubjectViaGateway() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"GW-SUB-101\","
                + "\"name\":\"Distributed Systems\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"campusId\":\"MAIN\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0,"
                + "\"elective\":false,"
                + "\"academicYear\":\"2026-2027\""
                + "}";

        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Trace-Id", "TRACE-GW-SUB-001");
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        assertEquals("TRACE-GW-SUB-001", conn.getHeaderField("X-Trace-Id"));

        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
        assertTrue(resp.contains("GW-SUB-101"));
        assertTrue(resp.contains("\"status\":\"ACTIVE\""));
    }

    /**
     * Tests transparent error pass-through (HTTP 409 Conflict) through API Gateway.
     */
    @Test
    public void testRouteDuplicateSubjectCodeErrorPassThrough() throws Exception {
        String payload = "{"
                + "\"subjectCode\":\"GW-DUP-001\","
                + "\"name\":\"Network Security\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}";

        // First creation succeeds
        URL url1 = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn1 = (HttpURLConnection) url1.openConnection();
        conn1.setRequestMethod("POST");
        conn1.setDoOutput(true);
        conn1.setRequestProperty("Content-Type", "application/json");
        conn1.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn1.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, conn1.getResponseCode());

        // Duplicate creation yields 409 Conflict propagated transparently
        URL url2 = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
        conn2.setRequestMethod("POST");
        conn2.setDoOutput(true);
        conn2.setRequestProperty("Content-Type", "application/json");
        conn2.setRequestProperty("X-Trace-Id", "TRACE-GW-SUB-409");
        conn2.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = conn2.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(409, conn2.getResponseCode());
        assertEquals("TRACE-GW-SUB-409", conn2.getHeaderField("X-Trace-Id"));
        String err = readResponse(conn2);
        assertTrue(err.contains("ACD_SUBJECT_CODE_DUPLICATE"));
    }

    /**
     * Tests canonical alias routing: GET /v1/subject-catalog proxies to /api/v1/academics/subjects/catalog.
     */
    @Test
    public void testRouteCanonicalSubjectCatalogAlias() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/v1/subject-catalog");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-User-Role", "STUDENT");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("\"success\":true"));
    }

    /**
     * Tests prometheus metrics routing: GET /api/v1/subjects/metrics proxies to downstream /metrics.
     */
    @Test
    public void testRouteSubjectMetricsViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/subjects/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("acd03_request_total"));
    }

    /**
     * Tests Story 10: Semantic Version Diffing via Gateway.
     */
    @Test
    public void testRouteSubjectVersionDiffViaGateway() throws Exception {
        // 1. Create a subject (Version 1)
        String subjectCode = "GW-DIFF-01";
        String createPayload = "{"
                + "\"subjectCode\":\"" + subjectCode + "\","
                + "\"name\":\"Compiler Design\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0,"
                + "\"academicYear\":\"2026-2027\""
                + "}";

        URL postUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String createResp = readResponse(postConn);
        String subjectId = extractField(createResp, "subjectId");
        assertNotNull(subjectId);

        // 2. Create Version 2 with modified credits and contact hours
        String v2Payload = "{"
                + "\"credits\":5.0,"
                + "\"contactHours\":75.0,"
                + "\"changeSummary\":\"Advanced code optimization and LLVM IR\""
                + "}";
        URL v2Url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions");
        HttpURLConnection v2Conn = (HttpURLConnection) v2Url.openConnection();
        v2Conn.setRequestMethod("POST");
        v2Conn.setDoOutput(true);
        v2Conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = v2Conn.getOutputStream()) {
            os.write(v2Payload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, v2Conn.getResponseCode());

        // 3. Query Version Diff via Gateway: GET /api/v1/academics/subjects/{id}/versions/diff?v1=1&v2=2
        URL diffUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions/diff?v1=1&v2=2");
        HttpURLConnection diffConn = (HttpURLConnection) diffUrl.openConnection();
        diffConn.setRequestMethod("GET");
        assertEquals(200, diffConn.getResponseCode());

        String diffResp = readResponse(diffConn);
        assertTrue(diffResp.contains("\"version1\":1"));
        assertTrue(diffResp.contains("\"version2\":2"));
        assertTrue(diffResp.contains("\"differences\":["));
        assertTrue(diffResp.contains("credits"));
    }

    /**
     * Tests Story 1 & 2: Course Outcomes (OBE) and CO-PO Matrix Alignment via Gateway.
     */
    @Test
    public void testRouteCourseOutcomesAndMatrixViaGateway() throws Exception {
        // 1. Create a subject in DRAFT status so Version 1 is DRAFT and mutable
        String subjectCode = "GW-OBE-01";
        String createPayload = "{"
                + "\"subjectCode\":\"" + subjectCode + "\","
                + "\"name\":\"Machine Learning\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0,"
                + "\"status\":\"DRAFT\""
                + "}";

        URL postUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String subjectId = extractField(readResponse(postConn), "subjectId");

        // 2. Add Course Outcome (Story 1) via PUT
        String coPayload = "["
                + "{"
                + "\"outcomeCode\":\"CO1\","
                + "\"statement\":\"Understand supervised and unsupervised learning algorithms\","
                + "\"bloomLevel\":\"K2_UNDERSTAND\","
                + "\"targetAttainment\":75.0"
                + "}"
                + "]";
        URL coUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions/1/course-outcomes");
        HttpURLConnection coConn = (HttpURLConnection) coUrl.openConnection();
        coConn.setRequestMethod("PUT");
        coConn.setDoOutput(true);
        coConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = coConn.getOutputStream()) {
            os.write(coPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, coConn.getResponseCode());

        // 3. Query Course Outcomes via Gateway
        URL getCoUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions/1/course-outcomes");
        HttpURLConnection getCoConn = (HttpURLConnection) getCoUrl.openConnection();
        getCoConn.setRequestMethod("GET");
        assertEquals(200, getCoConn.getResponseCode());
        String getCoResp = readResponse(getCoConn);
        assertTrue(getCoResp.contains("CO1"));
        assertTrue(getCoResp.contains("K2_UNDERSTAND"));

        // 4. Update CO-PO Matrix (Story 2) via PUT
        String matrixPayload = "["
                + "{\"outcomeCode\":\"CO1\",\"programOutcomeCode\":\"PO1\",\"correlationStrength\":3}"
                + "]";
        URL matrixUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions/1/co-po-matrix");
        HttpURLConnection matrixConn = (HttpURLConnection) matrixUrl.openConnection();
        matrixConn.setRequestMethod("PUT");
        matrixConn.setDoOutput(true);
        matrixConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = matrixConn.getOutputStream()) {
            os.write(matrixPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, matrixConn.getResponseCode());

        // 5. Query CO-PO Matrix via Gateway
        HttpURLConnection getMatrixConn = (HttpURLConnection) matrixUrl.openConnection();
        getMatrixConn.setRequestMethod("GET");
        assertEquals(200, getMatrixConn.getResponseCode());
        String matrixResp = readResponse(getMatrixConn);
        assertTrue(matrixResp.contains("PO1"));
        assertTrue(matrixResp.contains("\"correlationStrength\":3"));
    }

    /**
     * Tests Story 5: Syllabus Units Breakdown via Gateway.
     */
    @Test
    public void testRouteSyllabusUnitsViaGateway() throws Exception {
        // 1. Create a subject in DRAFT status so Version 1 is DRAFT and mutable
        String subjectCode = "GW-SYL-01";
        String createPayload = "{"
                + "\"subjectCode\":\"" + subjectCode + "\","
                + "\"name\":\"Cloud Computing\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":3.0,"
                + "\"contactHours\":45.0,"
                + "\"status\":\"DRAFT\""
                + "}";

        URL postUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String subjectId = extractField(readResponse(postConn), "subjectId");

        // 2. Add Syllabus Unit via PUT
        String unitPayload = "["
                + "{"
                + "\"unitNumber\":1,"
                + "\"title\":\"Virtualization and Containers\","
                + "\"topics\":[\"Hypervisors\",\"Docker\",\"Kubernetes\"],"
                + "\"hours\":12.0"
                + "}"
                + "]";
        URL unitUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions/1/syllabus-units");
        HttpURLConnection unitConn = (HttpURLConnection) unitUrl.openConnection();
        unitConn.setRequestMethod("PUT");
        unitConn.setDoOutput(true);
        unitConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = unitConn.getOutputStream()) {
            os.write(unitPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, unitConn.getResponseCode());

        // 3. Query Syllabus Units via Gateway
        HttpURLConnection getUnitConn = (HttpURLConnection) unitUrl.openConnection();
        getUnitConn.setRequestMethod("GET");
        assertEquals(200, getUnitConn.getResponseCode());
        String unitsResp = readResponse(getUnitConn);
        assertTrue(unitsResp.contains("Virtualization and Containers"));
        assertTrue(unitsResp.contains("Kubernetes"));
    }

    /**
     * Tests Story 3: Subject Equivalences via Gateway.
     */
    @Test
    public void testRouteSubjectEquivalencesViaGateway() throws Exception {
        // 1. Create a subject
        String subjectCode = "GW-EQ-S1";
        String createPayload = "{"
                + "\"subjectCode\":\"" + subjectCode + "\","
                + "\"name\":\"Advanced Algorithms\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}";

        URL postUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String subjectId = extractField(readResponse(postConn), "subjectId");

        // 2. Add Equivalence
        String equivPayload = "{"
                + "\"targetSubjectId\":\"SUB-TARGET-99\","
                + "\"equivalenceType\":\"DIRECT_SUBSTITUTION\","
                + "\"transferMultiplier\":1.0,"
                + "\"minimumGrade\":\"C\","
                + "\"externalInstitutionName\":\"CampX Institute\","
                + "\"effectiveFrom\":\"2026-2027\","
                + "\"effectiveTo\":\"2030-2031\","
                + "\"status\":\"ACTIVE\""
                + "}";

        URL eqUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/equivalences");
        HttpURLConnection eqConn = (HttpURLConnection) eqUrl.openConnection();
        eqConn.setRequestMethod("POST");
        eqConn.setDoOutput(true);
        eqConn.setRequestProperty("Content-Type", "application/json");
        eqConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = eqConn.getOutputStream()) {
            os.write(equivPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, eqConn.getResponseCode());

        // 3. Query Equivalences via Gateway
        URL getUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/equivalences");
        HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String getResp = readResponse(getConn);
        assertTrue(getResp.contains("SUB-TARGET-99"));
        assertTrue(getResp.contains("DIRECT_SUBSTITUTION"));
    }

    /**
     * Tests Story 9: Board of Studies (BoS) Approval Resolution via Gateway.
     */
    @Test
    public void testRouteBoSApprovalResolutionViaGateway() throws Exception {
        // 1. Create a subject
        String subjectCode = "GW-BOS-01";
        String createPayload = "{"
                + "\"subjectCode\":\"" + subjectCode + "\","
                + "\"name\":\"Quantum Computing\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"CORE\","
                + "\"classification\":\"THEORY\","
                + "\"credits\":4.0,"
                + "\"contactHours\":60.0"
                + "}";

        URL postUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String subjectId = extractField(readResponse(postConn), "subjectId");

        // 2. PUT BoS Resolution (Story 9)
        String resPayload = "{"
                + "\"resolutionNumber\":\"BOS-CSE-2026-R09\","
                + "\"approvedByBoard\":\"Board of Studies\","
                + "\"meetingDate\":\"2026-09-01\","
                + "\"minutesUrl\":\"https://campx.edu/minutes/bos-2026-001\","
                + "\"gazetteNotificationNumber\":\"GZ-2026-99\""
                + "}";

        URL resUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/versions/1/resolution");
        HttpURLConnection resConn = (HttpURLConnection) resUrl.openConnection();
        resConn.setRequestMethod("PUT");
        resConn.setDoOutput(true);
        resConn.setRequestProperty("Content-Type", "application/json");
        resConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        try (OutputStream os = resConn.getOutputStream()) {
            os.write(resPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, resConn.getResponseCode());

        // 3. Query BoS Resolution via Gateway
        HttpURLConnection getConn = (HttpURLConnection) resUrl.openConnection();
        getConn.setRequestMethod("GET");
        assertEquals(200, getConn.getResponseCode());
        String resp = readResponse(getConn);
        assertTrue(resp.contains("BOS-CSE-2026-R09"));
        assertTrue(resp.contains("Board of Studies"));
    }

    /**
     * Tests History / Audit Trail Query and Lifecycle Hard Deletion via Gateway.
     */
    @Test
    public void testRouteSubjectHistoryAndLifecycleDeleteViaGateway() throws Exception {
        // 1. Create temporary subject
        String subjectCode = "GW-TEMP-01";
        String createPayload = "{"
                + "\"subjectCode\":\"" + subjectCode + "\","
                + "\"name\":\"Temporary Subject\","
                + "\"departmentId\":\"DEPT-CA\","
                + "\"subjectType\":\"ELECTIVE\","
                + "\"classification\":\"PRACTICAL\","
                + "\"credits\":2.0,"
                + "\"contactHours\":30.0"
                + "}";

        URL postUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection postConn = (HttpURLConnection) postUrl.openConnection();
        postConn.setRequestMethod("POST");
        postConn.setDoOutput(true);
        postConn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = postConn.getOutputStream()) {
            os.write(createPayload.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(201, postConn.getResponseCode());
        String subjectId = extractField(readResponse(postConn), "subjectId");

        // 2. Query History: GET /api/v1/academics/subjects/{id}/history
        URL histUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId + "/history");
        HttpURLConnection histConn = (HttpURLConnection) histUrl.openConnection();
        histConn.setRequestMethod("GET");
        histConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, histConn.getResponseCode());
        String histResp = readResponse(histConn);
        assertTrue(histResp.contains(subjectId));

        // 3. Hard Delete via Gateway (HTTP DELETE)
        URL delUrl = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects/" + subjectId);
        HttpURLConnection delConn = (HttpURLConnection) delUrl.openConnection();
        delConn.setRequestMethod("DELETE");
        delConn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
        assertEquals(200, delConn.getResponseCode());
        String delResp = readResponse(delConn);
        assertTrue(delResp.contains("\"DELETED\""));
    }

    /**
     * Tests Story 62: External API Key Authentication Pass-Through via Gateway.
     */
    @Test
    public void testRouteExternalApiKeyAuthViaGateway() throws Exception {
        URL url = new URL("http://localhost:" + GW_PORT + "/api/v1/academics/subjects");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("X-API-Key", "INVALID_API_KEY_999");

        assertEquals(401, conn.getResponseCode());
        String resp = readResponse(conn);
        assertTrue(resp.contains("ACD_UNAUTHORIZED"));
    }

    private static String extractField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
