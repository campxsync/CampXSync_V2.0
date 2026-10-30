package com.campx.academic.assessment;

import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.academic.assessment.server.AssessmentServer;
import com.campx.academic.assessment.service.AssessmentDomainService;
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
 * HTTP Integration tests for ACD-09 REST Controller and server on an ephemeral port.
 */
public class AssessmentControllerIntegrationTest {

    private static AssessmentServer server;
    private static final int PORT = 8097;
    private static final String BASE_URL = "http://localhost:" + PORT;

    @BeforeClass
    public static void startServer() throws Exception {
        AssessmentDomainService domainService = new AssessmentDomainService();
        server = new AssessmentServer(PORT, domainService);
        server.start();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpURLConnection sendRequest(String method, String path, String body, String role) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-Tenant-Id", "TENANT-001");
        conn.setRequestProperty("X-User-Id", "admin-1");
        conn.setRequestProperty("X-User-Role", role != null ? role : "ACADEMIC_ADMIN");
        conn.setRequestProperty("X-Trace-Id", "TRACE-IT-001");
        conn.setRequestProperty("Content-Type", "application/json");

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
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
    public void testHealthAndMetricsEndpoints() throws Exception {
        HttpURLConnection healthConn = sendRequest("GET", "/health", null, "ACADEMIC_ADMIN");
        assertEquals(200, healthConn.getResponseCode());
        String healthBody = readResponse(healthConn);
        assertTrue(healthBody.contains("\"status\":\"UP\""));

        HttpURLConnection metricsConn = sendRequest("GET", "/metrics", null, "ACADEMIC_ADMIN");
        assertEquals(200, metricsConn.getResponseCode());
        String metricsBody = readResponse(metricsConn);
        assertTrue(metricsBody.contains("totalRequests"));
    }

    @Test
    public void testEndToEndAssessmentWorkflow() throws Exception {
        // 1. Create Assessment Structure
        String createJson = "{"
                + "\"assessmentCode\":\"CS201-E2E-01\","
                + "\"assessmentName\":\"Data Structures E2E Assessment\","
                + "\"subjectId\":\"SUB-CS101\","
                + "\"courseId\":\"COURSE-CS-BS\","
                + "\"curriculumId\":\"CURR-2026-CS\","
                + "\"academicYear\":\"2026-2027\","
                + "\"termId\":\"TERM-1\","
                + "\"assessmentType\":\"INTERNAL\","
                + "\"totalMarks\":100.0,"
                + "\"totalWeightage\":100.0"
                + "}";

        HttpURLConnection createConn = sendRequest("POST", "/api/v1/academics/assessments", createJson, "ACADEMIC_ADMIN");
        assertEquals(201, createConn.getResponseCode());
        String createResp = readResponse(createConn);
        assertTrue(createResp.contains("CS201-E2E-01"));

        // Extract ID
        int idIdx = createResp.indexOf("\"id\":\"") + 6;
        String assessmentId = createResp.substring(idIdx, createResp.indexOf("\"", idIdx));
        assertNotNull(assessmentId);

        // 2. Add Component (100% weightage, 100 marks)
        String compJson = "{"
                + "\"componentCode\":\"E2E-TEST\","
                + "\"componentName\":\"Comprehensive Final Assessment\","
                + "\"componentType\":\"TEST\","
                + "\"sequenceNo\":1,"
                + "\"maxMarks\":100.0,"
                + "\"passingMarks\":40.0,"
                + "\"weightage\":100.0"
                + "}";

        HttpURLConnection compConn = sendRequest("POST", "/api/v1/academics/assessments/" + assessmentId + "/components", compJson, "ACADEMIC_ADMIN");
        assertEquals(201, compConn.getResponseCode());
        String compResp = readResponse(compConn);
        assertTrue(compResp.contains("E2E-TEST"));

        // 3. Add Outcome Mapping
        String mapJson = "{"
                + "\"outcomeType\":\"CO\","
                + "\"outcomeCode\":\"CO1\","
                + "\"mappingLevel\":\"HIGH\","
                + "\"weight\":3.0"
                + "}";
        HttpURLConnection mapConn = sendRequest("POST", "/api/v1/academics/assessments/" + assessmentId + "/mappings", mapJson, "ACADEMIC_ADMIN");
        assertEquals(201, mapConn.getResponseCode());
        String mapResp = readResponse(mapConn);
        assertTrue(mapResp.contains("CO1"));

        // 4. Validate Assessment
        HttpURLConnection valConn = sendRequest("GET", "/api/v1/academics/assessments/" + assessmentId + "/validate", null, "ACADEMIC_ADMIN");
        assertEquals(200, valConn.getResponseCode());
        String valResp = readResponse(valConn);
        assertTrue(valResp.contains("\"valid\":true"));

        // 5. Submit for Review
        HttpURLConnection revConn = sendRequest("POST", "/api/v1/academics/assessments/" + assessmentId + "/submit-review", "{}", "ACADEMIC_ADMIN");
        assertEquals(200, revConn.getResponseCode());
        assertTrue(readResponse(revConn).contains("\"status\":\"REVIEW\""));

        // 6. Approve by HOD
        String appJson = "{\"approvalRef\":\"HOD-E2E-APP-01\",\"comments\":\"Approved by Dept Head\"}";
        HttpURLConnection appConn = sendRequest("POST", "/api/v1/academics/assessments/" + assessmentId + "/approve", appJson, "DEPT_HEAD");
        assertEquals(200, appConn.getResponseCode());
        assertTrue(readResponse(appConn).contains("\"status\":\"APPROVED\""));

        // 7. Publish
        String pubJson = "{\"expectedVersion\":1,\"reason\":\"Ready for rollout\"}";
        HttpURLConnection pubConn = sendRequest("POST", "/api/v1/academics/assessments/" + assessmentId + "/publish", pubJson, "ACADEMIC_ADMIN");
        assertEquals(200, pubConn.getResponseCode());
        String pubResp = readResponse(pubConn);
        assertTrue(pubResp.contains("\"status\":\"PUBLISHED\""));
        assertTrue(pubResp.contains("\"effectiveVersion\":1"));

        // 8. Query effective published mapping by subject (EXM access)
        HttpURLConnection subjConn = sendRequest("GET", "/api/v1/academics/assessments/subject/SUB-CS101?academicYear=2026-2027&termId=TERM-1", null, "EXAMINATION_COORDINATOR");
        // Role EXAMINATION_COORDINATOR can query read-only endpoints
        assertEquals(200, subjConn.getResponseCode());
        String subjResp = readResponse(subjConn);
        assertTrue(subjResp.contains("Data Structures E2E Assessment"));
        assertTrue(subjResp.contains("E2E-TEST"));
        assertTrue(subjResp.contains("CO1"));

        // 9. Query Coverage Analytics
        HttpURLConnection covConn = sendRequest("GET", "/api/v1/academics/assessments/analytics/coverage", null, "ACADEMIC_ADMIN");
        assertEquals(200, covConn.getResponseCode());
        String covResp = readResponse(covConn);
        assertTrue(covResp.contains("totalAssessments"));
    }

    @Test
    public void testDuplicateAssessment_ReturnsConflict409() throws Exception {
        String json = "{"
                + "\"assessmentCode\":\"CS301-DUP\","
                + "\"assessmentName\":\"Duplicate Test\","
                + "\"subjectId\":\"SUB-CS101\","
                + "\"courseId\":\"COURSE-CS-BS\","
                + "\"curriculumId\":\"CURR-2026-CS\","
                + "\"academicYear\":\"2026-2027\","
                + "\"termId\":\"TERM-1\","
                + "\"assessmentType\":\"INTERNAL\","
                + "\"totalMarks\":100.0,"
                + "\"totalWeightage\":100.0"
                + "}";

        HttpURLConnection c1 = sendRequest("POST", "/api/v1/academics/assessments", json, "ACADEMIC_ADMIN");
        assertEquals(201, c1.getResponseCode());

        HttpURLConnection c2 = sendRequest("POST", "/api/v1/academics/assessments", json, "ACADEMIC_ADMIN");
        assertEquals(409, c2.getResponseCode());
        String err = readResponse(c2);
        assertTrue(err.contains("ACD_ASSESSMENT_DUPLICATE_IDENTITY"));
    }

    @Test
    public void testRbac_UnauthorizedRole_ReturnsForbidden403() throws Exception {
        String json = "{"
                + "\"assessmentCode\":\"FORBIDDEN-TEST\","
                + "\"assessmentName\":\"Forbidden\","
                + "\"subjectId\":\"SUB-CS101\","
                + "\"courseId\":\"COURSE-CS-BS\","
                + "\"academicYear\":\"2026-2027\","
                + "\"termId\":\"TERM-1\","
                + "\"assessmentType\":\"INTERNAL\","
                + "\"totalMarks\":100.0"
                + "}";

        HttpURLConnection conn = sendRequest("POST", "/api/v1/academics/assessments", json, "STUDENT");
        assertEquals(403, conn.getResponseCode());
        String err = readResponse(conn);
        assertTrue(err.contains("ACD_ASSESSMENT_FORBIDDEN"));
    }
}
