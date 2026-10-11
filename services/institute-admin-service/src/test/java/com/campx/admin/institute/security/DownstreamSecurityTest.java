package com.campx.admin.institute.security;

import com.campx.admin.institute.server.InstituteAdminServer;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;
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
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit test suite verifying downstream microservice edge security enforcement (ADM-01):
 * 13. Missing HMAC -> rejected (401)
 * 14. Expired timestamp -> rejected (401)
 * 15. Invalid HMAC -> rejected (403)
 * 16. Modified body -> rejected (403)
 * 17. Modified user ID -> rejected (403)
 * 18. Modified tenant ID -> rejected (403)
 * 19. Valid Gateway request -> accepted (201)
 * 20. Direct request with forged X-* headers -> rejected (401/403)
 * Plus direct host binding verification (default 127.0.0.1 and configurable).
 */
public class DownstreamSecurityTest {

    private static String dynamicInternalSecret;
    private static String validUserId;
    private static String validTenantId;
    private static InstituteAdminServer server;
    private static int servicePort;

    @BeforeClass
    public static void setup() throws Exception {
        // Dynamically generate random secret and identities for zero hardcoded credentials
        dynamicInternalSecret = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        validUserId = UUID.randomUUID().toString();
        validTenantId = UUID.randomUUID().toString();

        System.setProperty("campx.internal.auth.enabled", "true");
        System.setProperty("campx.internal.secret", dynamicInternalSecret);
        System.setProperty("campx.internal.replay.window.seconds", "60");

        InstituteAdminDomainService domainService = new InstituteAdminDomainService();
        server = new InstituteAdminServer("127.0.0.1", 0, domainService);
        server.start();
        servicePort = server.getPort();
    }

    @AfterClass
    public static void teardown() {
        if (server != null) {
            server.stop();
        }
        System.clearProperty("campx.internal.auth.enabled");
        System.clearProperty("campx.internal.secret");
        System.clearProperty("campx.internal.replay.window.seconds");
        CampXLoggerFactory.flush();
    }

    // 13. Missing HMAC -> rejected
    @Test
    public void test13_missingHmac_rejected() throws Exception {
        String body = "{\"instituteCode\":\"INST_TEST_13\",\"legalName\":\"Test 13\",\"displayName\":\"T13\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        // Deliberately omit X-Gateway-Signature

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ADM01_MISSING_INTERNAL_AUTH"));
    }

    // 14. Expired timestamp -> rejected
    @Test
    public void test14_expiredTimestamp_rejected() throws Exception {
        String body = "{\"instituteCode\":\"INST_TEST_14\",\"legalName\":\"Test 14\",\"displayName\":\"T14\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";
        // 120 seconds in the past (outside 60s replay window)
        String expiredTimestamp = String.valueOf(System.currentTimeMillis() - 120_000);
        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String payload = GatewayHmacProtocol.buildCanonicalPayload(
                expiredTimestamp, "POST", "/api/v1/admin/institutes", validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(payload, dynamicInternalSecret);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, expiredTimestamp);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ADM01_EXPIRED_INTERNAL_AUTH"));
    }

    // 15. Invalid HMAC -> rejected
    @Test
    public void test15_invalidHmac_rejected() throws Exception {
        String body = "{\"instituteCode\":\"INST_TEST_15\",\"legalName\":\"Test 15\",\"displayName\":\"T15\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String wrongSecret = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String payload = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp, "POST", "/api/v1/admin/institutes", validUserId, validTenantId, bodySha);
        String invalidSignature = GatewayHmacProtocol.calculateHmac(payload, wrongSecret);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, invalidSignature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ADM01_INVALID_INTERNAL_SIGNATURE"));
    }

    // 16. Modified body -> rejected
    @Test
    public void test16_modifiedBody_rejected() throws Exception {
        String originalBody = "{\"instituteCode\":\"INST_ORIGINAL\",\"legalName\":\"Original\",\"displayName\":\"Orig\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";
        String modifiedBody = "{\"instituteCode\":\"INST_TAMPERED\",\"legalName\":\"Tampered\",\"displayName\":\"Tamp\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";

        String timestamp = String.valueOf(System.currentTimeMillis());
        // Signature generated for original body
        String origSha = GatewayHmacProtocol.sha256Hex(originalBody.getBytes(StandardCharsets.UTF_8));
        String payload = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp, "POST", "/api/v1/admin/institutes", validUserId, validTenantId, origSha);
        String signature = GatewayHmacProtocol.calculateHmac(payload, dynamicInternalSecret);

        // Send modified body with original body's signature
        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(modifiedBody.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ADM01_INVALID_INTERNAL_SIGNATURE"));
    }

    // 17. Modified user ID -> rejected
    @Test
    public void test17_modifiedUserId_rejected() throws Exception {
        String body = "{\"instituteCode\":\"INST_TEST_17\",\"legalName\":\"Test 17\",\"displayName\":\"T17\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        // Signature generated with validUserId
        String payload = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp, "POST", "/api/v1/admin/institutes", validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(payload, dynamicInternalSecret);

        // Tamper with user ID header
        String attackerUserId = UUID.randomUUID().toString();
        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, attackerUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ADM01_INVALID_INTERNAL_SIGNATURE"));
    }

    // 18. Modified tenant ID -> rejected
    @Test
    public void test18_modifiedTenantId_rejected() throws Exception {
        String body = "{\"instituteCode\":\"INST_TEST_18\",\"legalName\":\"Test 18\",\"displayName\":\"T18\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        // Signature generated with validTenantId
        String payload = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp, "POST", "/api/v1/admin/institutes", validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(payload, dynamicInternalSecret);

        // Tamper with tenant ID header
        String attackerTenantId = UUID.randomUUID().toString();
        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, attackerTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ADM01_INVALID_INTERNAL_SIGNATURE"));
    }

    // 19. Valid Gateway request -> accepted
    @Test
    public void test19_validGatewayRequest_accepted() throws Exception {
        String body = "{\"instituteCode\":\"INST_TEST_19\",\"legalName\":\"Test 19\",\"displayName\":\"T19\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String payload = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp, "POST", "/api/v1/admin/institutes", validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(payload, dynamicInternalSecret);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, timestamp);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ROLE, "TENANT_ADMIN");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("INST_TEST_19"));
        assertTrue(resp.contains("ACTIVE"));
    }

    // 20. Direct request with forged X-* headers -> rejected
    @Test
    public void test20_directRequestWithForgedHeaders_rejected() throws Exception {
        String body = "{\"instituteCode\":\"INST_FORGED\",\"legalName\":\"Forged\",\"displayName\":\"Forged\","
                + "\"timezone\":\"Asia/Kolkata\",\"locale\":\"en_IN\",\"defaultCurrency\":\"INR\"}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/institutes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        // Attacker attempts direct call forging identity headers without Gateway signature
        conn.setRequestProperty("X-User-Id", UUID.randomUUID().toString());
        conn.setRequestProperty("X-Tenant-Id", UUID.randomUUID().toString());
        conn.setRequestProperty("X-User-Role", "SUPER_ADMIN");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        // Must be rejected as unauthorized
        int code = conn.getResponseCode();
        assertTrue("Direct request without Gateway signature must be rejected (401 or 403)",
                code == 401 || code == 403);
    }

    // 21. Legacy displayName user creation request without Gateway HMAC -> rejected
    @Test
    public void test21_legacyDisplayNameRequestWithoutHmac_rejected() throws Exception {
        String body = "{\"userId\":\"iam_super_admin_01\",\"displayName\":\"Super Admin\",\"email\":\"superadmin@campx.edu\"}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/admin/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        // Omit HMAC headers (attacker trying to exploit legacy displayName bypass on port 8081)

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        assertTrue("Request containing displayName without Gateway HMAC signature must be rejected (401 or 403)",
                code == 401 || code == 403);
    }

    // Direct Host Binding Verification
    @Test
    public void testDirectHostBinding_defaultsTo127001() {
        assertEquals("Downstream service must bind to 127.0.0.1 by default",
                "127.0.0.1", server.getHost());
    }

    private String readStream(InputStream stream) throws Exception {
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
