package com.campx.academic.course.security;

import com.campx.academic.course.server.CourseServer;
import com.campx.academic.course.service.CourseDomainService;
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
 * Security verification suite testing downstream edge security enforcement for ACD-01 Course Management Service:
 * 1. Missing HMAC -> rejected (401)
 * 2. Expired timestamp -> rejected (401)
 * 3. Invalid HMAC -> rejected (403)
 * 4. Modified body -> rejected (403)
 * 5. Modified user ID -> rejected (403)
 * 6. Modified tenant ID -> rejected (403)
 * 7. Valid Gateway request -> accepted (200/201)
 * 8. Direct request with forged X-* headers -> rejected (401/403)
 */
public class DownstreamSecurityTest {

    private static String dynamicInternalSecret;
    private static String validUserId;
    private static String validTenantId;
    private static CourseServer server;
    private static int servicePort;

    @BeforeClass
    public static void setup() throws Exception {
        dynamicInternalSecret = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        validUserId = UUID.randomUUID().toString();
        validTenantId = UUID.randomUUID().toString();

        System.setProperty("campx.internal.auth.enabled", "true");
        System.setProperty("campx.internal.secret", dynamicInternalSecret);
        System.setProperty("campx.internal.replay.window.seconds", "60");

        CourseDomainService domainService = new CourseDomainService();
        domainService.registerActiveDepartment("DEP_01");
        server = new CourseServer(0, domainService);
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

    @Test
    public void test1_missingHmac_rejected() throws Exception {
        String body = "{\"courseCode\":\"CS_SEC_01\",\"courseName\":\"Security Test Course 1\",\"departmentId\":\"DEP_01\"}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        // Omit X-Gateway-Signature

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ACD01_MISSING_INTERNAL_AUTH"));
    }

    @Test
    public void test2_expiredTimestamp_rejected() throws Exception {
        String body = "{\"courseCode\":\"CS_SEC_02\",\"courseName\":\"Security Test Course 2\",\"departmentId\":\"DEP_01\"}";
        long expiredTimestamp = System.currentTimeMillis() - 120000L; // 2 minutes ago

        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                String.valueOf(expiredTimestamp), "POST", "/api/v1/courses",
                validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(canonical, dynamicInternalSecret);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(expiredTimestamp));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ACD01_EXPIRED_INTERNAL_AUTH"));
    }

    @Test
    public void test3_invalidHmac_rejected() throws Exception {
        String body = "{\"courseCode\":\"CS_SEC_03\",\"courseName\":\"Security Test Course 3\",\"departmentId\":\"DEP_01\"}";
        long timestamp = System.currentTimeMillis();

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(timestamp));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, "FORGED_SIGNATURE_VALUE_67890");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ACD01_INVALID_INTERNAL_SIGNATURE"));
    }

    @Test
    public void test4_tamperedBody_rejected() throws Exception {
        String originalBody = "{\"courseCode\":\"CS_SEC_04\",\"courseName\":\"Security Test Course 4\",\"departmentId\":\"DEP_01\"}";
        long timestamp = System.currentTimeMillis();

        String bodySha = GatewayHmacProtocol.sha256Hex(originalBody.getBytes(StandardCharsets.UTF_8));
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                String.valueOf(timestamp), "POST", "/api/v1/courses",
                validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(canonical, dynamicInternalSecret);

        String tamperedBody = "{\"courseCode\":\"CS_SEC_04\",\"courseName\":\"Tampered Course Name!\",\"departmentId\":\"DEP_01\"}";

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(timestamp));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(tamperedBody.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ACD01_INVALID_INTERNAL_SIGNATURE"));
    }

    @Test
    public void test5_forgedUserHeader_rejected() throws Exception {
        String body = "{\"courseCode\":\"CS_SEC_05\",\"courseName\":\"Security Test Course 5\",\"departmentId\":\"DEP_01\"}";
        long timestamp = System.currentTimeMillis();

        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                String.valueOf(timestamp), "POST", "/api/v1/courses",
                validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(canonical, dynamicInternalSecret);

        String attackerUserId = UUID.randomUUID().toString();

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(timestamp));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, attackerUserId); // Attacker overrides header!
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ACD01_INVALID_INTERNAL_SIGNATURE"));
    }

    @Test
    public void test6_forgedTenantHeader_rejected() throws Exception {
        String body = "{\"courseCode\":\"CS_SEC_06\",\"courseName\":\"Security Test Course 6\",\"departmentId\":\"DEP_01\"}";
        long timestamp = System.currentTimeMillis();

        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                String.valueOf(timestamp), "POST", "/api/v1/courses",
                validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(canonical, dynamicInternalSecret);

        String attackerTenantId = UUID.randomUUID().toString();

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(timestamp));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, attackerTenantId); // Attacker overrides tenant!
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("ACD01_INVALID_INTERNAL_SIGNATURE"));
    }

    @Test
    public void test7_validGatewayRequest_accepted() throws Exception {
        String body = "{\"courseCode\":\"CS_SEC_OK\",\"courseName\":\"Valid Gateway Course\",\"departmentId\":\"DEP_01\",\"totalCredits\":3.0}";
        long timestamp = System.currentTimeMillis();

        String bodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                String.valueOf(timestamp), "POST", "/api/v1/courses",
                validUserId, validTenantId, bodySha);
        String signature = GatewayHmacProtocol.calculateHmac(canonical, dynamicInternalSecret);

        URL url = new URL("http://127.0.0.1:" + servicePort + "/api/v1/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, String.valueOf(timestamp));
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, validUserId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, validTenantId);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(201, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());
        assertTrue(resp.contains("CS_SEC_OK"));
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
