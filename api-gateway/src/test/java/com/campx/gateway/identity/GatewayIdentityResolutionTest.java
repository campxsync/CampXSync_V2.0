package com.campx.gateway.identity;

import com.campx.gateway.config.GatewayConfig;
import com.campx.gateway.security.SupabaseJwtValidator;
import com.campx.gateway.server.GatewayServer;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Comprehensive unit and integration test suite for Wave 0 Task 2:
 * Authoritative Identity Resolution and /api/v1/auth/me endpoint.
 *
 * Verifies:
 *  1. Supabase JWT cryptographic validation remains the authentication boundary.
 *  2. Authenticated JWT 'sub' is used as identity key.
 *  3. GET /api/v1/auth/me returns documented, stable JSON response for valid College Admin.
 *  4. GET /api/v1/auth/me returns documented, stable JSON response for Faculty.
 *  5. GET /api/v1/auth/me returns documented, stable JSON response for Student.
 *  6. GET /api/v1/auth/me returns documented, stable JSON response for Platform Admin.
 *  7. Fail-closed: unmapped identity / missing profile returns HTTP 403 (GATEWAY_NO_PROFILE).
 *  8. Fail-closed: inactive user account returns HTTP 403 (GATEWAY_INACTIVE_MEMBERSHIP).
 *  9. Fail-closed: suspended user account returns HTTP 403 (GATEWAY_INACTIVE_MEMBERSHIP).
 * 10. Fail-closed: locked user account returns HTTP 403 (GATEWAY_INACTIVE_MEMBERSHIP).
 * 11. Fail-closed: suspended tenant returns HTTP 403 (GATEWAY_TENANT_SUSPENDED).
 * 12. Fail-closed: deleted / missing tenant returns HTTP 403 (GATEWAY_TENANT_NOT_FOUND).
 * 13. Fail-closed: missing role assignment returns HTTP 403 (GATEWAY_NO_ROLE_ASSIGNED).
 * 14. Fail-closed: expired role assignment returns HTTP 403 (GATEWAY_NO_ROLE_ASSIGNED).
 * 15. Fail-closed: future role assignment returns HTTP 403 (GATEWAY_NO_ROLE_ASSIGNED).
 * 16. Missing Authorization header returns HTTP 401 (GATEWAY_MISSING_AUTH).
 * 17. Non-Bearer Authorization header returns HTTP 401 (GATEWAY_MISSING_AUTH).
 * 18. Invalid JWT signature returns HTTP 401 (GATEWAY_INVALID_TOKEN_SIGNATURE).
 * 19. Expired JWT returns HTTP 401 (GATEWAY_EXPIRED_TOKEN).
 * 20. Method safety: POST /api/v1/auth/me returns HTTP 405 (GATEWAY_METHOD_NOT_ALLOWED).
 * 21. Downstream proxy: caller-supplied X-Tenant-Id and X-User-Role are stripped and replaced with authoritative resolved identity.
 * 22. Downstream proxy: ineligible / suspended identity fails closed at Gateway edge before reaching downstream microservice.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayIdentityResolutionTest {

    private static String dynamicInternalSecret;
    private static String dynamicIssuer;
    private static ECPublicKey primaryPublicKey;
    private static ECPrivateKey primaryPrivateKey;
    private static String primaryKid;

    private static HttpServer mockJwksServer;
    private static int jwksPort;
    private static final Map<String, ECPublicKey> registeredJwksKeys = new ConcurrentHashMap<>();

    private static HttpServer mockDownstreamServer;
    private static int downstreamPort;
    private static GatewayServer gatewayServer;
    private static GatewayConfig gatewayConfig;
    private static int gatewayPort;
    private static InMemoryIdentityResolver identityResolver;

    private static final AtomicReference<RecordedRequest> lastDownstreamRequest = new AtomicReference<>();

    public static class RecordedRequest {
        public String method;
        public String path;
        public Map<String, List<String>> headers;
        public byte[] body;
    }

    private static String prevInternalAuth;
    private static String prevInternalSecret;
    private static String prevJwtIssuer;
    private static String prevPersistenceMode;

    @BeforeClass
    public static void setup() throws Exception {
        prevInternalAuth = System.getProperty("campx.internal.auth.enabled");
        prevInternalSecret = System.getProperty("campx.internal.secret");
        prevJwtIssuer = System.getProperty("campx.supabase.jwt.issuer");
        prevPersistenceMode = System.getProperty("campx.persistence.mode");

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        primaryPublicKey = (ECPublicKey) kp.getPublic();
        primaryPrivateKey = (ECPrivateKey) kp.getPrivate();
        primaryKid = UUID.randomUUID().toString();
        registeredJwksKeys.put(primaryKid, primaryPublicKey);

        dynamicInternalSecret = UUID.randomUUID().toString() + "-internal-sec";

        // 1. Mock JWKS Server
        mockJwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksPort = mockJwksServer.getAddress().getPort();
        mockJwksServer.createContext("/.well-known/jwks.json", exchange -> {
            String jwksJson = buildJwksJson(registeredJwksKeys);
            byte[] resp = jwksJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockJwksServer.start();

        dynamicIssuer = "http://127.0.0.1:" + jwksPort;

        System.setProperty("campx.internal.auth.enabled", "true");
        System.setProperty("campx.internal.secret", dynamicInternalSecret);
        System.setProperty("campx.supabase.jwt.issuer", dynamicIssuer);
        System.setProperty("campx.persistence.mode", "test");

        // 2. Mock Downstream Server
        mockDownstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstreamPort = mockDownstreamServer.getAddress().getPort();
        mockDownstreamServer.createContext("/api/v1/mock", exchange -> {
            RecordedRequest req = new RecordedRequest();
            req.method = exchange.getRequestMethod();
            req.path = exchange.getRequestURI().getPath();
            req.headers = new HashMap<>(exchange.getRequestHeaders());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = exchange.getRequestBody();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            req.body = baos.toByteArray();
            lastDownstreamRequest.set(req);

            byte[] resp = "{\"status\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockDownstreamServer.start();

        // 3. API Gateway with In-Memory Identity Resolver
        identityResolver = new InMemoryIdentityResolver();

        gatewayConfig = new GatewayConfig();
        gatewayConfig.setPort(0);
        gatewayConfig.setIdentityResolver(identityResolver);
        gatewayConfig.setIdentityResolutionEnabled(true);
        gatewayConfig.addRoute("/api/v1/mock", "http://127.0.0.1:" + downstreamPort + "/api/v1/mock");

        gatewayServer = new GatewayServer(gatewayConfig);
        gatewayServer.start();
        gatewayPort = gatewayServer.getPort();
    }

    @AfterClass
    public static void teardown() {
        if (gatewayServer != null) gatewayServer.stop();
        if (mockDownstreamServer != null) mockDownstreamServer.stop(0);
        if (mockJwksServer != null) mockJwksServer.stop(0);
        restoreProperty("campx.internal.auth.enabled", prevInternalAuth);
        restoreProperty("campx.internal.secret", prevInternalSecret);
        restoreProperty("campx.supabase.jwt.issuer", prevJwtIssuer);
        restoreProperty("campx.persistence.mode", prevPersistenceMode);
        CampXLoggerFactory.flush();
    }

    private static void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    @Before
    public void reset() {
        lastDownstreamRequest.set(null);
        identityResolver.clear();
    }

    // 1. GET /api/v1/auth/me for valid College Admin
    @Test
    public void test01_authMe_validCollegeAdmin_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID collegeId = UUID.randomUUID();

        identityResolver.registerTenant(tenantId, "National Engineering College", "NEC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "admin.nec@college.edu", "Prof. S. Raman", "ACTIVE", collegeId, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", collegeId, null, LocalDate.now().minusDays(10), LocalDate.now().plusDays(365));

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());

        assertTrue("Response must contain userId", resp.contains("\"userId\":\"" + userId + "\""));
        assertTrue("Response must contain email", resp.contains("\"email\":\"admin.nec@college.edu\""));
        assertTrue("Response must contain fullName", resp.contains("\"fullName\":\"Prof. S. Raman\""));
        assertTrue("Response must contain business role", resp.contains("\"role\":\"COLLEGE_ADMIN\""));
        assertTrue("Response must contain normalized role", resp.contains("\"normalizedRole\":\"college_admin\""));
        assertTrue("Response must contain tenantId", resp.contains("\"tenantId\":\"" + tenantId + "\""));
        assertTrue("Response must contain tenantName", resp.contains("\"tenantName\":\"National Engineering College\""));
        assertTrue("Response must contain tenantStatus", resp.contains("\"tenantStatus\":\"ACTIVE\""));
        assertTrue("Response must contain collegeId", resp.contains("\"collegeId\":\"" + collegeId + "\""));
        assertTrue("Response must contain status", resp.contains("\"status\":\"ACTIVE\""));
        assertTrue("Response must contain isPlatformAdmin false", resp.contains("\"isPlatformAdmin\":false"));
    }

    // 2. GET /api/v1/auth/me for Faculty
    @Test
    public void test02_authMe_validFaculty_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID collegeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        identityResolver.registerTenant(tenantId, "City University", "CU", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "faculty.cse@city.edu", "Dr. A. Sharma", "ACTIVE", collegeId, deptId);
        identityResolver.registerRoleAssignment(userId, tenantId, "FACULTY", collegeId, deptId, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());

        assertTrue("Role must be FACULTY", resp.contains("\"role\":\"FACULTY\""));
        assertTrue("Normalized role must be faculty", resp.contains("\"normalizedRole\":\"faculty\""));
        assertTrue("Department ID must match", resp.contains("\"departmentId\":\"" + deptId + "\""));
    }

    // 3. GET /api/v1/auth/me for Student
    @Test
    public void test03_authMe_validStudent_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        identityResolver.registerTenant(tenantId, "Apex Institute", "APEX", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "student.01@apex.edu", "Rohan Mehta", "ACTIVE", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "STUDENT", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());

        assertTrue("Role must be STUDENT", resp.contains("\"role\":\"STUDENT\""));
        assertTrue("Normalized role must be student", resp.contains("\"normalizedRole\":\"student\""));
    }

    // 4. GET /api/v1/auth/me for Platform Super Admin
    @Test
    public void test04_authMe_platformAdmin_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        identityResolver.registerPlatformAdmin(userId, "System Super Administrator", "SUPER_ADMIN", "ACTIVE");

        String token = createJwt(userId.toString(), UUID.randomUUID().toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());

        assertTrue("Normalized role must be platform_admin", resp.contains("\"normalizedRole\":\"platform_admin\""));
        assertTrue("isPlatformAdmin must be true", resp.contains("\"isPlatformAdmin\":true"));
        assertTrue("tenantId must be null", resp.contains("\"tenantId\":null"));
    }

    // 5. Fail closed: User profile not in database -> 403 GATEWAY_NO_PROFILE
    @Test
    public void test05_authMe_userNotFound_failsClosed403() throws Exception {
        UUID unmappedUser = UUID.randomUUID();
        String token = createJwt(unmappedUser.toString(), UUID.randomUUID().toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_NO_PROFILE: " + resp, resp.contains("GATEWAY_NO_PROFILE"));
    }

    // 6. Fail closed: Inactive user profile status -> 403 GATEWAY_INACTIVE_MEMBERSHIP
    @Test
    public void test06_authMe_inactiveUser_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Test Tenant", "TT", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "inactive@test.com", "Inactive User", "INACTIVE", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_INACTIVE_MEMBERSHIP: " + resp, resp.contains("GATEWAY_INACTIVE_MEMBERSHIP"));
    }

    // 7. Fail closed: Suspended user profile status -> 403 GATEWAY_INACTIVE_MEMBERSHIP
    @Test
    public void test07_authMe_suspendedUser_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Test Tenant", "TT", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "suspended@test.com", "Suspended User", "SUSPENDED", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_INACTIVE_MEMBERSHIP: " + resp, resp.contains("GATEWAY_INACTIVE_MEMBERSHIP"));
    }

    // 8. Fail closed: Locked user profile status -> 403 GATEWAY_INACTIVE_MEMBERSHIP
    @Test
    public void test08_authMe_lockedUser_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Test Tenant", "TT", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "locked@test.com", "Locked User", "LOCKED", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_INACTIVE_MEMBERSHIP: " + resp, resp.contains("GATEWAY_INACTIVE_MEMBERSHIP"));
    }

    // 9. Fail closed: Suspended tenant status -> 403 GATEWAY_TENANT_SUSPENDED
    @Test
    public void test09_authMe_suspendedTenant_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Suspended College", "SC", "SUSPENDED");
        identityResolver.registerUserProfile(userId, tenantId, "admin@sc.edu", "Admin SC", "ACTIVE", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_TENANT_SUSPENDED: " + resp, resp.contains("GATEWAY_TENANT_SUSPENDED"));
    }

    // 10. Fail closed: Missing tenant -> 403 GATEWAY_TENANT_NOT_FOUND
    @Test
    public void test10_authMe_missingTenant_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID missingTenantId = UUID.randomUUID();
        identityResolver.registerUserProfile(userId, missingTenantId, "user@unknown.edu", "Unknown Tenant User", "ACTIVE", null, null);
        identityResolver.registerRoleAssignment(userId, missingTenantId, "COLLEGE_ADMIN", null, null, null, null);

        String token = createJwt(userId.toString(), missingTenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_TENANT_NOT_FOUND: " + resp, resp.contains("GATEWAY_TENANT_NOT_FOUND"));
    }

    // 11. Fail closed: No role assigned -> 403 GATEWAY_NO_ROLE_ASSIGNED
    @Test
    public void test11_authMe_noRoleAssigned_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Active College", "AC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "norole@ac.edu", "No Role User", "ACTIVE", null, null);
        // Zero role assignments registered

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_NO_ROLE_ASSIGNED: " + resp, resp.contains("GATEWAY_NO_ROLE_ASSIGNED"));
    }

    // 12. Fail closed: Expired role assignment -> 403 GATEWAY_NO_ROLE_ASSIGNED
    @Test
    public void test12_authMe_expiredRoleAssignment_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Active College", "AC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "expired@ac.edu", "Expired Role User", "ACTIVE", null, null);
        // Valid to was yesterday
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null,
                LocalDate.now().minusMonths(6), LocalDate.now().minusDays(1));

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_NO_ROLE_ASSIGNED: " + resp, resp.contains("GATEWAY_NO_ROLE_ASSIGNED"));
    }

    // 13. Fail closed: Future role assignment -> 403 GATEWAY_NO_ROLE_ASSIGNED
    @Test
    public void test13_authMe_futureRoleAssignment_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Active College", "AC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "future@ac.edu", "Future Role User", "ACTIVE", null, null);
        // Valid from starts tomorrow
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null,
                LocalDate.now().plusDays(1), LocalDate.now().plusYears(1));

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_NO_ROLE_ASSIGNED: " + resp, resp.contains("GATEWAY_NO_ROLE_ASSIGNED"));
    }

    // 14. Missing Authorization header -> 401 GATEWAY_MISSING_AUTH
    @Test
    public void test14_authMe_missingAuthHeader_returns401() throws Exception {
        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_MISSING_AUTH: " + resp, resp.contains("GATEWAY_MISSING_AUTH"));
    }

    // 15. Non-Bearer Authorization header -> 401 GATEWAY_MISSING_AUTH
    @Test
    public void test15_authMe_nonBearerAuthHeader_returns401() throws Exception {
        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Basic dXNlcjpwYXNz");

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_MISSING_AUTH: " + resp, resp.contains("GATEWAY_MISSING_AUTH"));
    }

    // 16. Invalid JWT signature -> 401 GATEWAY_INVALID_TOKEN_SIGNATURE
    @Test
    public void test16_authMe_invalidSignature_returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String validToken = createJwt(userId.toString(), UUID.randomUUID().toString(), "authenticated");

        String[] parts = validToken.split("\\.");
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        sig[0] ^= 0xAA;
        String corruptedToken = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + corruptedToken);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_INVALID_TOKEN_SIGNATURE: " + resp, resp.contains("GATEWAY_INVALID_TOKEN_SIGNATURE"));
    }

    // 17. Expired JWT -> 401 GATEWAY_EXPIRED_TOKEN
    @Test
    public void test17_authMe_expiredJwt_returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        String expiredToken = SupabaseJwtValidator.createSignedJwt(
                userId.toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 - 60, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + expiredToken);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_EXPIRED_TOKEN: " + resp, resp.contains("GATEWAY_EXPIRED_TOKEN"));
    }

    // 18. Method safety: POST /api/v1/auth/me -> 405 Method Not Allowed
    @Test
    public void test18_authMe_postMethod_returns405() throws Exception {
        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");

        assertEquals(405, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_METHOD_NOT_ALLOWED: " + resp, resp.contains("GATEWAY_METHOD_NOT_ALLOWED"));
    }

    // 19. Downstream proxy: Authoritative identity resolution replaces spoofed headers
    @Test
    public void test19_proxy_spoofedHeadersOverriddenByAuthoritativeIdentity() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID realTenantId = UUID.randomUUID();
        UUID collegeId = UUID.randomUUID();

        identityResolver.registerTenant(realTenantId, "Authoritative College", "AC", "ACTIVE");
        identityResolver.registerUserProfile(userId, realTenantId, "verified@ac.edu", "Verified Dean", "ACTIVE", collegeId, null);
        identityResolver.registerRoleAssignment(userId, realTenantId, "COLLEGE_ADMIN", collegeId, null, null, null);

        // JWT token contains real user UUID sub
        String token = createJwt(userId.toString(), realTenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        // Malicious client tries to spoof a different tenant and super admin role
        conn.setRequestProperty("X-Tenant-Id", "99999999-9999-9999-9999-999999999999");
        conn.setRequestProperty("X-User-Role", "SUPER_ADMIN");

        assertEquals(200, conn.getResponseCode());
        RecordedRequest downstreamReq = lastDownstreamRequest.get();
        assertNotNull("Request must reach downstream mock", downstreamReq);

        // Assert that downstream received the authoritative identity, NOT the spoofed headers
        assertEquals(userId.toString(), getFirstHeader(downstreamReq, GatewayHmacProtocol.HEADER_USER_ID));
        assertEquals(realTenantId.toString(), getFirstHeader(downstreamReq, GatewayHmacProtocol.HEADER_TENANT_ID));
        assertEquals("COLLEGE_ADMIN", getFirstHeader(downstreamReq, GatewayHmacProtocol.HEADER_USER_ROLE));
    }

    // 20. Downstream proxy fail closed: Ineligible / suspended user denied at gateway edge
    @Test
    public void test20_proxy_suspendedUserDeniedAtGatewayEdge() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        identityResolver.registerTenant(tenantId, "Valid College", "VC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "suspended@vc.edu", "Suspended Dean", "SUSPENDED", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/courses");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        assertNull("Suspended request must never reach downstream service", lastDownstreamRequest.get());
    }

    // 21. Fail closed: Inactive platform admin account -> 403 GATEWAY_INACTIVE_MEMBERSHIP
    @Test
    public void test21_authMe_inactivePlatformAdmin_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        identityResolver.registerPlatformAdmin(userId, "Inactive Super Admin", "SUPER_ADMIN", "INACTIVE");

        String token = createJwt(userId.toString(), UUID.randomUUID().toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_INACTIVE_MEMBERSHIP: " + resp, resp.contains("GATEWAY_INACTIVE_MEMBERSHIP"));
    }

    // 22. Fail closed: Suspended platform admin account -> 403 GATEWAY_INACTIVE_MEMBERSHIP
    @Test
    public void test22_authMe_suspendedPlatformAdmin_failsClosed403() throws Exception {
        UUID userId = UUID.randomUUID();
        identityResolver.registerPlatformAdmin(userId, "Suspended Super Admin", "SUPER_ADMIN", "SUSPENDED");

        String token = createJwt(userId.toString(), UUID.randomUUID().toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_INACTIVE_MEMBERSHIP: " + resp, resp.contains("GATEWAY_INACTIVE_MEMBERSHIP"));
    }

    // 23. Privilege escalation guard: Unrecognized role code fails closed, no elevation to COLLEGE_ADMIN
    @Test
    public void test23_authMe_unrecognizedRole_failsClosed403NoPrivilegeEscalation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Strict College", "SC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "guest@sc.edu", "Guest User", "ACTIVE", null, null);
        // User assigned an unrecognized role that contains 'ADMIN' substring or non-standard code
        identityResolver.registerRoleAssignment(userId, tenantId, "ADMISSION_STAFF", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(403, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Must contain GATEWAY_NO_ROLE_ASSIGNED: " + resp, resp.contains("GATEWAY_NO_ROLE_ASSIGNED"));
        assertFalse("Must never elevate to COLLEGE_ADMIN: " + resp, resp.contains("COLLEGE_ADMIN"));
    }

    // 24. Multi-role preservation: Preserves all active role codes in roles array and derives effective role
    @Test
    public void test24_authMe_multipleRoles_preservesAllRolesAndDerivesEffectiveRole() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID collegeId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        identityResolver.registerTenant(tenantId, "Multi-Role Institute", "MRI", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "dean.faculty@mri.edu", "Prof. Multi", "ACTIVE", collegeId, deptId);
        // User has both COLLEGE_ADMIN and FACULTY roles
        identityResolver.registerRoleAssignment(userId, tenantId, "FACULTY", collegeId, deptId, null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "COLLEGE_ADMIN", collegeId, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(200, conn.getResponseCode());
        String resp = readStream(conn.getInputStream());

        assertTrue("Effective role must be COLLEGE_ADMIN: " + resp, resp.contains("\"role\":\"COLLEGE_ADMIN\""));
        assertTrue("Roles array must contain COLLEGE_ADMIN: " + resp, resp.contains("\"COLLEGE_ADMIN\""));
        assertTrue("Roles array must contain FACULTY: " + resp, resp.contains("\"FACULTY\""));
    }

    // 25. Persistence mode guard: Production mode without PostgreSQL fails startup
    @Test
    public void test25_persistenceMode_productionWithoutPostgres_failsFast() {
        String savedMode = System.getProperty("campx.persistence.mode");
        String savedDbEnabled = System.getProperty("campx.db.enabled");
        String savedJdbcUrl = System.getProperty("campx.jdbc.url");
        try {
            System.clearProperty("campx.persistence.mode");
            System.setProperty("campx.db.enabled", "false");
            System.setProperty("campx.jdbc.url", "none");
            GatewayConfig prodConfig = new GatewayConfig();
            prodConfig.setPort(0);

            try {
                prodConfig.validatePersistenceConfiguration();
                fail("Expected IllegalStateException when validating persistence without PostgreSQL in production mode");
            } catch (IllegalStateException e) {
                assertTrue("Error message must mention PostgreSQL or production mode: " + e.getMessage(),
                        e.getMessage().contains("PostgreSQL") || e.getMessage().contains("production"));
            }

            try {
                prodConfig.getIdentityResolver();
                fail("Expected IllegalStateException when calling getIdentityResolver without PostgreSQL in production mode");
            } catch (IllegalStateException e) {
                assertTrue("Error message must indicate missing database or explicit test mode: " + e.getMessage(),
                        e.getMessage().contains("database configuration missing") || e.getMessage().contains("test mode"));
            }
        } finally {
            if (savedMode != null) {
                System.setProperty("campx.persistence.mode", savedMode);
            } else {
                System.clearProperty("campx.persistence.mode");
            }
            if (savedDbEnabled != null) {
                System.setProperty("campx.db.enabled", savedDbEnabled);
            } else {
                System.clearProperty("campx.db.enabled");
            }
            if (savedJdbcUrl != null) {
                System.setProperty("campx.jdbc.url", savedJdbcUrl);
            } else {
                System.clearProperty("campx.jdbc.url");
            }
        }
    }

    // 26. Persistence mode guard: InMemoryIdentityResolver rejected in production mode
    @Test
    public void test26_persistenceMode_inMemoryInProduction_throwsIllegalStateException() {
        String savedMode = System.getProperty("campx.persistence.mode");
        try {
            System.clearProperty("campx.persistence.mode");
            GatewayConfig prodConfig = new GatewayConfig();
            try {
                prodConfig.setIdentityResolver(new InMemoryIdentityResolver());
                fail("Expected IllegalStateException when assigning InMemoryIdentityResolver in production mode");
            } catch (IllegalStateException e) {
                assertTrue("Error message must indicate in-memory is restricted to test mode: " + e.getMessage(),
                        e.getMessage().contains("InMemoryIdentityResolver is strictly restricted to explicit test mode"));
            }
        } finally {
            if (savedMode != null) {
                System.setProperty("campx.persistence.mode", savedMode);
            } else {
                System.clearProperty("campx.persistence.mode");
            }
        }
    }

    // 27. Sanitized database error handling: 500 GATEWAY_DATABASE_ERROR without sensitive leakage
    @Test
    public void test27_authMe_databaseError_returns500WithoutLeakingDetails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        // Custom resolver simulating database outage
        IdentityResolver errorResolver = new IdentityResolver() {
            @Override
            public IdentityResolutionResult resolveIdentity(UUID id) {
                return IdentityResolutionResult.ineligible("database_error", "Failed to resolve identity from authoritative storage");
            }
        };

        GatewayConfig errorConfig = new GatewayConfig();
        errorConfig.setPort(0);
        errorConfig.setIdentityResolver(errorResolver);
        errorConfig.setIdentityResolutionEnabled(true);
        GatewayServer errorServer = new GatewayServer(errorConfig);
        errorServer.start();
        int errPort = errorServer.getPort();

        try {
            String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");
            URL url = new URL("http://127.0.0.1:" + errPort + "/api/v1/auth/me");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);

            assertEquals(500, conn.getResponseCode());
            String resp = readStream(conn.getErrorStream());
            assertTrue("Must contain GATEWAY_DATABASE_ERROR: " + resp, resp.contains("GATEWAY_DATABASE_ERROR"));
            assertTrue("Must contain sanitized message: " + resp, resp.contains("Failed to resolve identity from authoritative storage"));
            assertFalse("Must never leak SQL query text: " + resp, resp.contains("SELECT"));
            assertFalse("Must never leak table names: " + resp, resp.contains("plat.") || resp.contains("iam."));
        } finally {
            errorServer.stop();
        }
    }

    // 28. Cache-Control and security headers: Must include no-store on /auth/me
    @Test
    public void test28_authMe_cacheControlHeaders_enforcesNoStore() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        identityResolver.registerTenant(tenantId, "Cache Check College", "CCC", "ACTIVE");
        identityResolver.registerUserProfile(userId, tenantId, "cache@ccc.edu", "Cache User", "ACTIVE", null, null);
        identityResolver.registerRoleAssignment(userId, tenantId, "STUDENT", null, null, null, null);

        String token = createJwt(userId.toString(), tenantId.toString(), "authenticated");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/auth/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);

        assertEquals(200, conn.getResponseCode());
        String cacheControl = conn.getHeaderField("Cache-Control");
        assertNotNull("Cache-Control must be set", cacheControl);
        assertTrue("Cache-Control must contain no-store: " + cacheControl, cacheControl.contains("no-store"));
    }

    private static String createJwt(String sub, String tenantId, String role) throws Exception {
        return SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, role, dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);
    }

    private static String readStream(InputStream is) throws Exception {
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

    private static String getFirstHeader(RecordedRequest req, String name) {
        if (req == null || req.headers == null) return null;
        for (Map.Entry<String, List<String>> e : req.headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return (e.getValue() != null && !e.getValue().isEmpty()) ? e.getValue().get(0) : null;
            }
        }
        return null;
    }

    private static String buildJwksJson(Map<String, ECPublicKey> keys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        int count = 0;
        for (Map.Entry<String, ECPublicKey> entry : keys.entrySet()) {
            if (count > 0) sb.append(",");
            ECPublicKey pub = entry.getValue();
            String x = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArrayUnsigned(pub.getW().getAffineX(), 32));
            String y = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArrayUnsigned(pub.getW().getAffineY(), 32));
            sb.append("{\"kty\":\"EC\",\"crv\":\"P-256\",")
                    .append("\"kid\":\"").append(entry.getKey()).append("\",")
                    .append("\"x\":\"").append(x).append("\",")
                    .append("\"y\":\"").append(y).append("\"}");
            count++;
        }
        sb.append("]}");
        return sb.toString();
    }

    private static byte[] toByteArrayUnsigned(java.math.BigInteger bi, int byteLen) {
        byte[] src = bi.toByteArray();
        if (src.length == byteLen) return src;
        byte[] dest = new byte[byteLen];
        if (src.length > byteLen) {
            System.arraycopy(src, src.length - byteLen, dest, 0, byteLen);
        } else {
            System.arraycopy(src, 0, dest, byteLen - src.length, src.length);
        }
        return dest;
    }
}
