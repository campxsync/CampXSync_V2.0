package com.campx.student;

import com.campx.student.controller.StudentController;
import com.campx.student.controller.StudentController.HttpResponse;
import com.campx.student.exception.*;
import com.campx.student.model.*;
import com.campx.student.repository.*;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.server.StudentServer;
import com.campx.student.service.StudentDomainService;
import com.sun.net.httpserver.HttpExchange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Targeted verification for STM-01 engineering quality, repository resolution,
 * fail-closed runtime persistence mode, HTTP error mapping, correlation propagation,
 * and sanitized error responses.
 */
public class StudentEngineeringQualityAndSecurityTest {

    private UUID tenantId;
    private UUID staffUserId;
    private UUID studentUserId;
    private StudentDomainService domainService;
    private StudentController controller;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();
        studentUserId = UUID.randomUUID();

        domainService = new StudentDomainService();
        controller = new StudentController(domainService);
    }

    // =========================================================================
    // 1. Persistence Mode and Fail-Closed Runtime Configuration Tests
    // =========================================================================

    @Test
    public void testDeployedModeRejectsMissingPersistenceMode() {
        try {
            StudentDomainService.resolveRepositories(null, true);
            fail("Expected IllegalStateException when persistence mode is missing in deployed environment");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Missing required persistence configuration in deployed environment"));
        }
    }

    @Test
    public void testDeployedModeRejectsInMemoryPersistence() {
        try {
            StudentDomainService.resolveRepositories("in_memory", true);
            fail("Expected IllegalStateException when in-memory persistence is requested in deployed environment");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Ephemeral in-memory persistence is strictly prohibited in deployed environment"));
        }
    }

    @Test
    public void testDeployedModeRejectsMockPersistence() {
        try {
            StudentDomainService.resolveRepositories("mock", true);
            fail("Expected IllegalStateException for mock mode in deployed environment");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Ephemeral in-memory persistence is strictly prohibited in deployed environment"));
        }
    }

    @Test
    public void testTestModeAllowsExplicitInMemoryPersistence() {
        StudentDomainService.RepositoryBundle bundle = StudentDomainService.resolveRepositories("in_memory", false);
        assertNotNull(bundle);
        assertTrue(bundle.studentRepository instanceof InMemoryStudentRepository);
        assertTrue(bundle.personRepository instanceof InMemoryPersonRepository);
        assertTrue(bundle.guardianshipRepository instanceof InMemoryGuardianshipRepository);
        assertTrue(bundle.correctionRequestRepository instanceof InMemoryCorrectionRequestRepository);
        assertTrue(bundle.verificationRepository instanceof InMemoryStudentVerificationRepository);
        assertTrue(bundle.identifierRepository instanceof InMemoryPersonIdentifierRepository);
        assertTrue(bundle.documentRepository instanceof InMemoryStudentDocumentRepository);
        assertTrue(bundle.addressRepository instanceof InMemoryStudentAddressRepository);
    }

    @Test
    public void testTestModeResolvesPostgresRepositories() {
        StudentDomainService.RepositoryBundle bundle = StudentDomainService.resolveRepositories("postgres", false);
        assertNotNull(bundle);
        assertTrue(bundle.studentRepository instanceof PostgresStudentRepository);
        assertTrue(bundle.personRepository instanceof PostgresPersonRepository);
        assertTrue(bundle.guardianshipRepository instanceof PostgresGuardianshipRepository);
        assertTrue(bundle.correctionRequestRepository instanceof PostgresCorrectionRequestRepository);
        assertTrue(bundle.verificationRepository instanceof PostgresStudentVerificationRepository);
        assertTrue(bundle.identifierRepository instanceof PostgresPersonIdentifierRepository);
        assertTrue(bundle.documentRepository instanceof PostgresStudentDocumentRepository);
        assertTrue(bundle.addressRepository instanceof PostgresStudentAddressRepository);
    }

    @Test
    public void testDeployedEnvironmentRejectsInMemoryRepositoriesInConstructor() {
        String prevEnv = System.getProperty("campx.environment");
        try {
            System.setProperty("campx.environment", "production");
            assertTrue(StudentDomainService.isDeployedEnvironment());

            StudentDomainService.RepositoryBundle inMemBundle = StudentDomainService.createInMemoryBundle();
            try {
                new StudentDomainService(inMemBundle);
                fail("Expected IllegalStateException when in-memory repositories are injected in deployed environment");
            } catch (IllegalStateException e) {
                assertTrue(e.getMessage().contains("Ephemeral in-memory repositories cannot be used in a deployed environment"));
            }
        } finally {
            if (prevEnv != null) {
                System.setProperty("campx.environment", prevEnv);
            } else {
                System.clearProperty("campx.environment");
            }
        }
    }

    // =========================================================================
    // 2. Exception to HTTP Status Code & RFC 7807 Mapping Tests
    // =========================================================================

    @Test
    public void testControllerMapsStudentConflictExceptionTo409() {
        // 1. Enroll a student
        UUID collegeId = UUID.randomUUID();
        UserSecurityContext staffCtx = new UserSecurityContext(staffUserId, tenantId, collegeId, null, "REGISTRAR", false);
        EnrollStudentRequest enrollReq = new EnrollStudentRequest();
        enrollReq.setCollegeId(collegeId);
        enrollReq.setFirstName("Rohan");
        enrollReq.setLastName("Mehta");
        enrollReq.setPrimaryEmail("rohan.conflict@campx.edu");
        enrollReq.setEnrollmentNo("STU2026CONF01");
        StudentProfileResponse student = domainService.enrollStudent(staffCtx, enrollReq);

        // 2. Attempt duplicate enrollment with same email/enrollmentNo -> Conflict 409
        Map<String, String> headers = createStaffHeaders();
        headers.put("X-College-Id", collegeId.toString());
        String duplicateBody = String.format("{\"collegeId\":\"%s\",\"firstName\":\"Rohan\",\"lastName\":\"Mehta\",\"primaryEmail\":\"rohan.conflict@campx.edu\",\"enrollmentNo\":\"STU2026CONF01\"}", collegeId);
        HttpResponse resp = controller.enrollStudent(headers, duplicateBody);
        assertEquals(409, resp.getStatusCode());
        assertTrue(resp.getBody().contains("Conflict"));
        assertTrue(resp.getBody().contains("already exists"));

        // 3. Optimistic concurrency conflict via If-Match -> Conflict 409
        headers.put("If-Match", "999"); // Stale row version
        String patchBody = "{\"preferredName\":\"Rohan Concurrency\"}";
        HttpResponse patchResp = controller.updatePermittedFields(headers, student.getId().toString(), patchBody);
        assertEquals(409, patchResp.getStatusCode());
        assertTrue(patchResp.getBody().contains("Concurrency conflict"));
    }

    @Test
    public void testControllerMapsValidationExceptionTo400() {
        Map<String, String> headers = createStaffHeaders();
        // Missing required fields
        HttpResponse resp = controller.enrollStudent(headers, "{}");
        assertEquals(400, resp.getStatusCode());
        assertTrue(resp.getBody().contains("Bad Request"));
    }

    @Test
    public void testControllerMapsSecurityViolationExceptionTo403() {
        // Enforce cross-tenant access rejection
        Map<String, String> studentHeaders = new HashMap<>();
        studentHeaders.put("X-User-Id", studentUserId.toString());
        studentHeaders.put("X-Tenant-Id", UUID.randomUUID().toString()); // Different tenant
        studentHeaders.put("X-User-Role", "STUDENT");

        HttpResponse resp = controller.getMyProfile(studentHeaders);
        assertEquals(404, resp.getStatusCode()); // Self-profile not found in other tenant
    }

    @Test
    public void testControllerMapsNotFoundExceptionTo404() {
        Map<String, String> headers = createStaffHeaders();
        HttpResponse resp = controller.getStudentById(headers, UUID.randomUUID().toString());
        assertEquals(404, resp.getStatusCode());
        assertTrue(resp.getBody().contains("Not Found"));
    }

    @Test
    public void testControllerMapsInvalidUuidTo400() {
        Map<String, String> headers = createStaffHeaders();
        HttpResponse resp = controller.getStudentById(headers, "not-a-valid-uuid");
        assertEquals(400, resp.getStatusCode());
        assertTrue(resp.getBody().contains("Invalid student UUID format"));
    }

    @Test
    public void testSanitizedErrorResponseNoInternalStacktraceLeaked() {
        Map<String, String> headers = createStaffHeaders();
        HttpResponse resp = controller.getStudentById(headers, UUID.randomUUID().toString());
        assertEquals(404, resp.getStatusCode());

        // Verify sanitized JSON envelope without stack trace
        assertFalse(resp.getBody().contains("Exception"));
        assertFalse(resp.getBody().contains("at com.campx"));
        assertFalse(resp.getBody().contains(".java:"));
        assertTrue(resp.getBody().contains("\"type\":\"https://campxsync.edu/errors/not-found\""));
        assertTrue(resp.getBody().contains("\"status\":404"));
    }

    // =========================================================================
    // 3. Correlation-ID & Operational Endpoints Tests
    // =========================================================================

    @Test
    public void testCorrelationIdEchoInHttpHandler() throws Exception {
        MockHttpExchange exchange = new MockHttpExchange("GET", "/api/v1/students/me");
        String testCorrId = "CORR-STU-TRACE-987654";
        exchange.getRequestHeaders().set("X-Correlation-Id", testCorrId);
        exchange.getRequestHeaders().set("X-User-Id", studentUserId.toString());
        exchange.getRequestHeaders().set("X-Tenant-Id", tenantId.toString());
        exchange.getRequestHeaders().set("X-User-Role", "STUDENT");

        controller.handle(exchange);

        assertEquals(testCorrId, exchange.getResponseHeaders().getFirst("X-Correlation-Id"));
        assertEquals(testCorrId, exchange.getResponseHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    public void testActuatorHealthAndMetricsEndpoints() throws Exception {
        // 1. Health endpoint
        MockHttpExchange healthExchange = new MockHttpExchange("GET", "/actuator/health");
        controller.handle(healthExchange);
        assertEquals(200, healthExchange.getResponseCode());
        assertTrue(healthExchange.getResponseBodyContent().contains("\"status\":\"UP\""));

        // 2. Metrics endpoint
        MockHttpExchange metricsExchange = new MockHttpExchange("GET", "/metrics");
        controller.handle(metricsExchange);
        assertEquals(200, metricsExchange.getResponseCode());
        assertTrue(metricsExchange.getResponseBodyContent().contains("student_service_up"));
    }

    // =========================================================================
    // 4. Server Lifecycle Tests
    // =========================================================================

    @Test
    public void testStudentServerStartupAndShutdown() throws IOException {
        int ephemeralPort = 18093;
        StudentServer server = new StudentServer(ephemeralPort, domainService);
        assertFalse(server.isRunning());

        server.start();
        assertTrue(server.isRunning());
        assertEquals(ephemeralPort, server.getPort());

        server.stop();
        assertFalse(server.isRunning());
    }

    private Map<String, String> createStaffHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-User-Id", staffUserId.toString());
        headers.put("X-Tenant-Id", tenantId.toString());
        headers.put("X-User-Role", "REGISTRAR");
        headers.put("X-Correlation-Id", "CORR-" + UUID.randomUUID());
        return headers;
    }

    // =========================================================================
    // Mock HttpExchange for controller testing without socket binding
    // =========================================================================

    private static class MockHttpExchange extends HttpExchange {
        private final String method;
        private final URI uri;
        private final com.sun.net.httpserver.Headers requestHeaders = new com.sun.net.httpserver.Headers();
        private final com.sun.net.httpserver.Headers responseHeaders = new com.sun.net.httpserver.Headers();
        private final ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        private int responseCode = -1;

        public MockHttpExchange(String method, String path) {
            this.method = method;
            this.uri = URI.create("http://localhost:8093" + path);
        }

        @Override
        public com.sun.net.httpserver.Headers getRequestHeaders() { return requestHeaders; }
        @Override
        public com.sun.net.httpserver.Headers getResponseHeaders() { return responseHeaders; }
        @Override
        public URI getRequestURI() { return uri; }
        @Override
        public String getRequestMethod() { return method; }
        @Override
        public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override
        public void close() {}
        @Override
        public InputStream getRequestBody() { return new ByteArrayInputStream(new byte[0]); }
        @Override
        public OutputStream getResponseBody() { return responseStream; }
        @Override
        public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
        @Override
        public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
        @Override
        public int getResponseCode() { return responseCode; }
        @Override
        public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8093); }
        @Override
        public String getProtocol() { return "HTTP/1.1"; }
        @Override
        public Object getAttribute(String name) { return null; }
        @Override
        public void setAttribute(String name, Object value) {}
        @Override
        public void setStreams(InputStream i, OutputStream o) {}
        @Override
        public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }

        public String getResponseBodyContent() {
            return responseStream.toString();
        }
    }
}
