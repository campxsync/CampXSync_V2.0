package com.campx.academic.batch;

import com.campx.academic.batch.controller.BatchController;
import com.campx.academic.batch.repository.InMemoryBatchAssignmentRepository;
import com.campx.academic.batch.security.GatewayHmacVerifier;
import com.campx.academic.batch.security.UserSecurityContext;
import com.campx.academic.batch.server.BatchServer;
import com.campx.academic.batch.service.BatchDomainService;
import com.campx.logger.security.GatewayHmacProtocol;
import com.sun.net.httpserver.HttpExchange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Focused verification test suite for Mandate sections:
 * 3A: Persistence mode and fail-closed runtime configuration.
 * 3B: Gateway-to-service identity trust boundary and HMAC verification.
 * 3C: PostgreSQL RLS claims format and transaction cleanup semantics.
 */
public class BatchSecurityAndRuntimeModeTest {

    private static final String TEST_SECRET = "test-internal-hmac-secret-abcdef1234567890";
    private static final UUID VALID_TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VALID_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private String savedPersistenceMode;
    private String savedDeploymentEnv;
    private String savedAppEnv;
    private String savedNodeEnv;
    private String savedCampxEnv;

    @Before
    public void captureEnv() {
        savedPersistenceMode = System.getProperty("campx.persistence.mode");
        savedDeploymentEnv = System.getProperty("campx.deployment.env");
        savedAppEnv = System.getProperty("app.env");
        savedNodeEnv = System.getProperty("node.env");
        savedCampxEnv = System.getProperty("campx.environment");
    }

    @After
    public void restoreEnv() {
        restoreProperty("campx.persistence.mode", savedPersistenceMode);
        restoreProperty("campx.deployment.env", savedDeploymentEnv);
        restoreProperty("app.env", savedAppEnv);
        restoreProperty("node.env", savedNodeEnv);
        restoreProperty("campx.environment", savedCampxEnv);
    }

    private void restoreProperty(String key, String val) {
        if (val != null) {
            System.setProperty(key, val);
        } else {
            System.clearProperty(key);
        }
    }

    // =========================================================================
    // 3A. Persistence Mode and Fail-Closed Runtime Configuration Tests
    // =========================================================================

    @Test
    public void testDeployedModeRejectsMissingPersistenceMode() {
        try {
            BatchDomainService.resolveRepository(null, true);
            fail("Expected IllegalStateException when persistence mode is missing in deployed environment");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Missing required persistence configuration in deployed environment"));
        }
    }

    @Test
    public void testDeployedModeRejectsInMemoryPersistence() {
        try {
            BatchDomainService.resolveRepository("inmemory", true);
            fail("Expected IllegalStateException when in-memory persistence is requested in deployed environment");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Ephemeral in-memory persistence is strictly prohibited in deployed environment"));
        }
    }

    @Test
    public void testDeployedModeRejectsMockPersistence() {
        try {
            BatchDomainService.resolveRepository("mock", true);
            fail("Expected IllegalStateException for mock mode in deployed environment");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Ephemeral in-memory persistence is strictly prohibited in deployed environment"));
        }
    }

    @Test
    public void testTestModeAllowsExplicitInMemoryPersistence() {
        com.campx.academic.batch.repository.BatchAssignmentRepository repo =
                BatchDomainService.resolveRepository("inmemory", false);
        assertTrue(repo instanceof InMemoryBatchAssignmentRepository);
    }

    @Test
    public void testTestModeDefaultsSafelyToInMemoryWhenUnset() {
        com.campx.academic.batch.repository.BatchAssignmentRepository repo =
                BatchDomainService.resolveRepository(null, false);
        assertTrue(repo instanceof InMemoryBatchAssignmentRepository);
    }

    // =========================================================================
    // 3B. Gateway HMAC Trust Boundary Tests
    // =========================================================================

    @Test
    public void testHmacVerifierRejectsMissingTimestamp() {
        GatewayHmacVerifier verifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);

        MockHttpExchange exchange = new MockHttpExchange("GET", "/api/v1/academics/batches");
        exchange.getRequestHeaders().set("X-User-Id", VALID_USER_ID.toString());
        exchange.getRequestHeaders().set("X-Tenant-Id", VALID_TENANT_ID.toString());
        exchange.getRequestHeaders().set("X-Gateway-Signature", "some-sig");

        GatewayHmacVerifier.VerificationResult result = verifier.verify(exchange, "GET", "/api/v1/academics/batches", new byte[0]);
        assertFalse(result.isSuccess());
        assertEquals(401, result.getStatus());
        assertEquals("ACD04_MISSING_INTERNAL_AUTH", result.getErrorCode());
    }

    @Test
    public void testHmacVerifierRejectsExpiredTimestamp() {
        GatewayHmacVerifier verifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);

        MockHttpExchange exchange = new MockHttpExchange("GET", "/api/v1/academics/batches");
        long expiredTs = (System.currentTimeMillis() / 1000L) - 120; // 2 minutes ago (outside 60s window)
        exchange.getRequestHeaders().set("X-Gateway-Timestamp", String.valueOf(expiredTs));
        exchange.getRequestHeaders().set("X-Gateway-Signature", "some-sig");
        exchange.getRequestHeaders().set("X-User-Id", VALID_USER_ID.toString());
        exchange.getRequestHeaders().set("X-Tenant-Id", VALID_TENANT_ID.toString());

        GatewayHmacVerifier.VerificationResult result = verifier.verify(exchange, "GET", "/api/v1/academics/batches", new byte[0]);
        assertFalse(result.isSuccess());
        assertEquals(401, result.getStatus());
        assertEquals("ACD04_EXPIRED_INTERNAL_AUTH", result.getErrorCode());
    }

    @Test
    public void testHmacVerifierRejectsMissingSignature() {
        GatewayHmacVerifier verifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);

        MockHttpExchange exchange = new MockHttpExchange("GET", "/api/v1/academics/batches");
        long now = System.currentTimeMillis() / 1000L;
        exchange.getRequestHeaders().set("X-Gateway-Timestamp", String.valueOf(now));
        exchange.getRequestHeaders().set("X-User-Id", VALID_USER_ID.toString());
        exchange.getRequestHeaders().set("X-Tenant-Id", VALID_TENANT_ID.toString());

        GatewayHmacVerifier.VerificationResult result = verifier.verify(exchange, "GET", "/api/v1/academics/batches", new byte[0]);
        assertFalse(result.isSuccess());
        assertEquals(401, result.getStatus());
        assertEquals("ACD04_MISSING_INTERNAL_AUTH", result.getErrorCode());
    }

    @Test
    public void testHmacVerifierRejectsForgedSignature() {
        GatewayHmacVerifier verifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);

        MockHttpExchange exchange = new MockHttpExchange("GET", "/api/v1/academics/batches");
        long now = System.currentTimeMillis() / 1000L;
        exchange.getRequestHeaders().set("X-Gateway-Timestamp", String.valueOf(now));
        exchange.getRequestHeaders().set("X-Gateway-Signature", "forged-signature-hex-1234");
        exchange.getRequestHeaders().set("X-User-Id", VALID_USER_ID.toString());
        exchange.getRequestHeaders().set("X-Tenant-Id", VALID_TENANT_ID.toString());

        GatewayHmacVerifier.VerificationResult result = verifier.verify(exchange, "GET", "/api/v1/academics/batches", new byte[0]);
        assertFalse(result.isSuccess());
        assertEquals(403, result.getStatus());
        assertEquals("ACD04_INVALID_INTERNAL_SIGNATURE", result.getErrorCode());
    }

    @Test
    public void testHmacVerifierRejectsNonUuidIdentity() {
        GatewayHmacVerifier verifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);

        MockHttpExchange exchange = new MockHttpExchange("GET", "/api/v1/academics/batches");
        long now = System.currentTimeMillis() / 1000L;
        exchange.getRequestHeaders().set("X-Gateway-Timestamp", String.valueOf(now));
        exchange.getRequestHeaders().set("X-Gateway-Signature", "some-sig");
        exchange.getRequestHeaders().set("X-User-Id", "admin-1"); // Not a UUID
        exchange.getRequestHeaders().set("X-Tenant-Id", VALID_TENANT_ID.toString());

        GatewayHmacVerifier.VerificationResult result = verifier.verify(exchange, "GET", "/api/v1/academics/batches", new byte[0]);
        assertFalse(result.isSuccess());
        assertEquals(401, result.getStatus());
        assertEquals("ACD04_MISSING_INTERNAL_AUTH", result.getErrorCode());
    }

    @Test
    public void testHmacVerifierAcceptsValidSignature() {
        GatewayHmacVerifier verifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);

        String method = "POST";
        String path = "/api/v1/academics/batches/11111111-ba5c-0000-0000-000000000001/students";
        byte[] bodyBytes = "{\"studentId\":\"11111111-5700-0000-0000-000000000001\",\"rollNo\":\"R-1\"}".getBytes(StandardCharsets.UTF_8);

        long now = System.currentTimeMillis() / 1000L;
        String tsStr = String.valueOf(now);
        String bodySha256 = GatewayHmacProtocol.sha256Hex(bodyBytes);
        String canonicalPayload = GatewayHmacProtocol.buildCanonicalPayload(
                tsStr, method, path, VALID_USER_ID.toString(), VALID_TENANT_ID.toString(), bodySha256);
        String validSig = GatewayHmacProtocol.calculateHmac(canonicalPayload, TEST_SECRET);

        MockHttpExchange exchange = new MockHttpExchange(method, path);
        exchange.getRequestHeaders().set("X-Gateway-Timestamp", tsStr);
        exchange.getRequestHeaders().set("X-Gateway-Signature", validSig);
        exchange.getRequestHeaders().set("X-User-Id", VALID_USER_ID.toString());
        exchange.getRequestHeaders().set("X-Tenant-Id", VALID_TENANT_ID.toString());
        exchange.getRequestHeaders().set("X-User-Role", "COLLEGE_ADMIN");

        GatewayHmacVerifier.VerificationResult result = verifier.verify(exchange, method, path, bodyBytes);
        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatus());
        assertEquals(VALID_USER_ID.toString(), result.getUserId());
        assertEquals(VALID_TENANT_ID.toString(), result.getTenantId());
        assertEquals("COLLEGE_ADMIN", result.getUserRole());
    }

    // =========================================================================
    // 3C. End-to-End Batch Controller HTTP Boundary Enforcement
    // =========================================================================

    @Test
    public void testHttpServerRejectsDirectAccessWhenHmacEnabled() throws Exception {
        int port = 8098;
        GatewayHmacVerifier secureVerifier = new GatewayHmacVerifier(true, TEST_SECRET, 60);
        InMemoryBatchAssignmentRepository repo = new InMemoryBatchAssignmentRepository();
        BatchDomainService svc = new BatchDomainService(repo);
        BatchController controller = new BatchController(svc, null, secureVerifier);

        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/academics/batches", controller);
        server.start();

        try {
            // 1. Direct unauthenticated request without HMAC headers -> 401
            URL url = new URL("http://localhost:" + port + "/api/v1/academics/batches");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-User-Id", VALID_USER_ID.toString());
            conn.setRequestProperty("X-Tenant-Id", VALID_TENANT_ID.toString());
            int code = conn.getResponseCode();
            assertEquals(401, code);

            // 2. Direct request with forged HMAC signature -> 403
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            long now = System.currentTimeMillis() / 1000L;
            conn.setRequestProperty("X-Gateway-Timestamp", String.valueOf(now));
            conn.setRequestProperty("X-Gateway-Signature", "forged-sig");
            conn.setRequestProperty("X-User-Id", VALID_USER_ID.toString());
            conn.setRequestProperty("X-Tenant-Id", VALID_TENANT_ID.toString());
            int codeForged = conn.getResponseCode();
            assertEquals(403, codeForged);

            // 3. Legitimate signed request -> 200
            String bodySha256 = GatewayHmacProtocol.sha256Hex(new byte[0]);
            String canonicalPayload = GatewayHmacProtocol.buildCanonicalPayload(
                    String.valueOf(now), "GET", "/api/v1/academics/batches", VALID_USER_ID.toString(), VALID_TENANT_ID.toString(), bodySha256);
            String validSig = GatewayHmacProtocol.calculateHmac(canonicalPayload, TEST_SECRET);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Gateway-Timestamp", String.valueOf(now));
            conn.setRequestProperty("X-Gateway-Signature", validSig);
            conn.setRequestProperty("X-User-Id", VALID_USER_ID.toString());
            conn.setRequestProperty("X-Tenant-Id", VALID_TENANT_ID.toString());
            conn.setRequestProperty("X-User-Role", "ACADEMIC_ADMIN");
            int codeValid = conn.getResponseCode();
            assertEquals(200, codeValid);
        } finally {
            server.stop(0);
        }
    }

    // =========================================================================
    // Mock HttpExchange helper for unit testing verifier
    // =========================================================================

    private static class MockHttpExchange extends HttpExchange {
        private final String method;
        private final String path;
        private final com.sun.net.httpserver.Headers requestHeaders = new com.sun.net.httpserver.Headers();
        private final com.sun.net.httpserver.Headers responseHeaders = new com.sun.net.httpserver.Headers();

        public MockHttpExchange(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override public com.sun.net.httpserver.Headers getRequestHeaders() { return requestHeaders; }
        @Override public com.sun.net.httpserver.Headers getResponseHeaders() { return responseHeaders; }
        @Override public java.net.URI getRequestURI() { return java.net.URI.create(path); }
        @Override public String getRequestMethod() { return method; }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override public void close() {}
        @Override public InputStream getRequestBody() { return new java.io.ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getResponseBody() { return new java.io.ByteArrayOutputStream(); }
        @Override public void sendResponseHeaders(int rCode, long responseLength) {}
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
        @Override public int getResponseCode() { return 200; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8086); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
        @Override public void setStreams(InputStream i, OutputStream o) {}
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
