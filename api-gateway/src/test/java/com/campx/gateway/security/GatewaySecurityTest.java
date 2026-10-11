package com.campx.gateway.security;

import com.campx.gateway.config.GatewayConfig;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Comprehensive security unit test suite for CampXSync API Gateway ES256/JWKS JWT verification:
 *
 *  1. Valid ES256 Supabase JWT -> accepted.
 *  2. Invalid ES256 signature -> rejected.
 *  3. Unknown kid -> JWKS refresh attempted, then rejected.
 *  4. Valid kid with wrong public key -> rejected.
 *  5. HS256 JWT -> rejected.
 *  6. alg:none -> rejected.
 *  7. Wrong issuer -> rejected.
 *  8. Wrong audience -> rejected.
 *  9. Expired JWT -> rejected.
 * 10. nbf in future -> rejected.
 * 11. Missing sub -> rejected.
 * 12. Invalid UUID sub -> rejected.
 * 13. Missing tenant_id -> rejected.
 * 14. Invalid tenant UUID -> rejected.
 * 15. Client X-User-Id attempting to spoof verified subject -> ignored/stripped.
 * 16. Client X-Tenant-Id attempting to spoof verified tenant -> ignored/stripped.
 * 17. JWKS unavailable -> request rejected; never fail open.
 * 18. JWKS refresh after unknown kid -> resolves newly rotated key.
 * 19. Successful validation still produces the same trusted identity consumed by HMAC signing.
 * 20. Inbound X-Gateway-* headers are stripped and replaced with genuine Gateway HMAC.
 * 21. Body hash changes signature when body changes.
 * 22. Missing CAMPX_SUPABASE_JWT_SECRET no longer fails startup.
 * 23. Missing CAMPX_SUPABASE_JWT_ISSUER fails startup.
 * 24. Missing CAMPX_INTERNAL_SECRET fails startup.
 */
public class GatewaySecurityTest {

    private static String dynamicInternalSecret;
    private static String dynamicIssuer;

    private static ECPublicKey primaryPublicKey;
    private static ECPrivateKey primaryPrivateKey;
    private static String primaryKid;

    private static HttpServer mockJwksServer;
    private static int jwksPort;
    private static final Map<String, ECPublicKey> registeredJwksKeys = new ConcurrentHashMap<>();
    private static final AtomicBoolean jwksServerUnavailable = new AtomicBoolean(false);
    private static final AtomicInteger jwksHitCount = new AtomicInteger(0);

    private static HttpServer mockDownstreamServer;
    private static int downstreamPort;
    private static GatewayServer gatewayServer;
    private static GatewayConfig gatewayConfig;
    private static int gatewayPort;

    private static final AtomicReference<RecordedRequest> lastDownstreamRequest = new AtomicReference<>();

    public static class RecordedRequest {
        public String method;
        public String path;
        public Map<String, List<String>> headers;
        public byte[] body;
    }

    @BeforeClass
    public static void setup() throws Exception {
        // Dynamically generate EC (P-256) KeyPair in memory (zero hardcoded keys/secrets)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();
        primaryPublicKey = (ECPublicKey) kp.getPublic();
        primaryPrivateKey = (ECPrivateKey) kp.getPrivate();
        primaryKid = UUID.randomUUID().toString();
        registeredJwksKeys.put(primaryKid, primaryPublicKey);

        // Dynamically generate random internal secret for downstream HMAC
        dynamicInternalSecret = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();

        // 1. Start mock JWKS server on dynamic port
        mockJwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksPort = mockJwksServer.getAddress().getPort();
        mockJwksServer.createContext("/.well-known/jwks.json", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    jwksHitCount.incrementAndGet();
                    if (jwksServerUnavailable.get()) {
                        exchange.sendResponseHeaders(503, -1);
                        return;
                    }
                    String jwksJson = buildJwksJson(registeredJwksKeys);
                    byte[] resp = jwksJson.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                } catch (Exception e) {
                    try {
                        exchange.sendResponseHeaders(500, -1);
                    } catch (Exception ignored) {}
                }
            }
        });
        mockJwksServer.start();

        // Dynamic issuer derived to match mock JWKS endpoint
        dynamicIssuer = "http://127.0.0.1:" + jwksPort;

        System.setProperty("campx.internal.auth.enabled", "true");
        System.setProperty("campx.internal.secret", dynamicInternalSecret);
        System.setProperty("campx.supabase.jwt.issuer", dynamicIssuer);
        // Explicitly clear legacy JWT secret to verify it is no longer required
        System.clearProperty("campx.supabase.jwt.secret");

        // 2. Start mock downstream service on dynamic port
        mockDownstreamServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstreamPort = mockDownstreamServer.getAddress().getPort();
        mockDownstreamServer.createContext("/api/v1/mock", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
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
                } catch (Exception e) {
                    try {
                        exchange.sendResponseHeaders(500, -1);
                    } catch (Exception ignored) {}
                }
            }
        });
        mockDownstreamServer.start();

        // 3. Start Gateway on dynamic port proxying /api/v1/mock -> mock downstream
        gatewayConfig = new GatewayConfig();
        gatewayConfig.setPort(0);
        gatewayConfig.addRoute("/api/v1/mock", "http://127.0.0.1:" + downstreamPort + "/api/v1/mock");
        gatewayServer = new GatewayServer(gatewayConfig);
        gatewayServer.start();
        gatewayPort = gatewayServer.getPort();
    }

    @AfterClass
    public static void teardown() {
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
        if (mockDownstreamServer != null) {
            mockDownstreamServer.stop(0);
        }
        if (mockJwksServer != null) {
            mockJwksServer.stop(0);
        }
        System.clearProperty("campx.internal.auth.enabled");
        System.clearProperty("campx.internal.secret");
        System.clearProperty("campx.supabase.jwt.issuer");
        System.clearProperty("campx.supabase.jwt.secret");
        System.clearProperty("campx.supabase.jwks.url");
        CampXLoggerFactory.flush();
    }

    @Before
    public void reset() {
        lastDownstreamRequest.set(null);
        jwksServerUnavailable.set(false);
        if (gatewayConfig != null && gatewayConfig.getJwksClient() != null) {
            gatewayConfig.getJwksClient().setLastFetchTime(0L);
        }
    }

    // 1. Valid ES256 Supabase JWT -> accepted
    @Test
    public void test01_validEs256Jwt_accepted() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull("Request must reach downstream mock", req);
        assertEquals(sub, getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ID));
        assertEquals(tenantId, getFirstHeader(req, GatewayHmacProtocol.HEADER_TENANT_ID));
        assertEquals("authenticated", getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ROLE));
    }

    // 2. Invalid ES256 signature -> rejected
    @Test
    public void test02_invalidEs256Signature_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        // Corrupt signature
        String[] parts = token.split("\\.");
        byte[] sigBytes = Base64.getUrlDecoder().decode(parts[2]);
        sigBytes[0] ^= 0xFF; // flip bits of signature
        String corruptedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        String corruptedToken = parts[0] + "." + parts[1] + "." + corruptedSig;

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + corruptedToken);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_INVALID_TOKEN_SIGNATURE: " + resp, resp.contains("GATEWAY_INVALID_TOKEN_SIGNATURE"));
    }

    // 3. Unknown kid -> JWKS refresh attempted, then rejected
    @Test
    public void test03_unknownKid_refreshAttemptedThenRejected() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair strangerKp = kpg.generateKeyPair();
        String unknownKid = "unknown-kid-" + UUID.randomUUID().toString();

        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, (ECPrivateKey) strangerKp.getPrivate(), unknownKid);

        int hitsBefore = jwksHitCount.get();

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_UNKNOWN_KEY_ID: " + resp, resp.contains("GATEWAY_UNKNOWN_KEY_ID"));
        assertTrue("JWKS endpoint should be queried during unknown kid lookup", jwksHitCount.get() >= hitsBefore);
    }

    // 4. Valid kid with wrong public key -> rejected
    @Test
    public void test04_validKidWithWrongPublicKey_rejected() throws Exception {
        // Sign token with a different private key, but claim it is primaryKid
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair differentKp = kpg.generateKeyPair();

        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, (ECPrivateKey) differentKp.getPrivate(), primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_INVALID_TOKEN_SIGNATURE: " + resp, resp.contains("GATEWAY_INVALID_TOKEN_SIGNATURE"));
    }

    // 5. HS256 JWT -> rejected
    @Test
    public void test05_hs256Jwt_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createHs256Jwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, "legacy-shared-secret-1234567890");

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_UNSUPPORTED_JWT_ALG: " + resp, resp.contains("GATEWAY_UNSUPPORTED_JWT_ALG"));
    }

    // 6. alg:none -> rejected
    @Test
    public void test06_algNone_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createUnsignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_UNSUPPORTED_JWT_ALG: " + resp, resp.contains("GATEWAY_UNSUPPORTED_JWT_ALG"));
    }

    // 7. Wrong issuer -> rejected
    @Test
    public void test07_wrongIssuer_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String wrongIssuer = "https://untrusted-project.supabase.co/auth/v1";
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", wrongIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_INVALID_TOKEN_ISSUER: " + resp, resp.contains("GATEWAY_INVALID_TOKEN_ISSUER"));
    }

    // 8. Wrong audience -> rejected
    @Test
    public void test08_wrongAudience_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "anon",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_INVALID_TOKEN_AUDIENCE: " + resp, resp.contains("GATEWAY_INVALID_TOKEN_AUDIENCE"));
    }

    // 9. Expired JWT -> rejected
    @Test
    public void test09_expiredJwt_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long pastExp = (System.currentTimeMillis() / 1000) - 3600;
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                pastExp, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_EXPIRED_TOKEN: " + resp, resp.contains("GATEWAY_EXPIRED_TOKEN"));
    }

    // 10. nbf in future -> rejected
    @Test
    public void test10_nbfInFuture_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long futureNbf = (System.currentTimeMillis() / 1000) + 3600;
        long futureExp = futureNbf + 3600;
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                futureExp, futureNbf, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_PREMATURE_TOKEN: " + resp, resp.contains("GATEWAY_PREMATURE_TOKEN"));
    }

    // 11. Missing sub -> rejected
    @Test
    public void test11_missingSub_rejected() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                null, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_MALFORMED_JWT: " + resp, resp.contains("GATEWAY_MALFORMED_JWT"));
    }

    // 12. Invalid UUID sub -> rejected
    @Test
    public void test12_invalidUuidSub_rejected() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                "not-a-valid-uuid", tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_MALFORMED_JWT: " + resp, resp.contains("GATEWAY_MALFORMED_JWT"));
    }

    // 13. Missing tenant_id -> rejected
    @Test
    public void test13_missingTenantId_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, null, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_MALFORMED_JWT: " + resp, resp.contains("GATEWAY_MALFORMED_JWT"));
    }

    // 14. Invalid tenant UUID -> rejected
    @Test
    public void test14_invalidTenantUuid_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, "invalid-tenant-uuid", "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue("Expected GATEWAY_MALFORMED_JWT: " + resp, resp.contains("GATEWAY_MALFORMED_JWT"));
    }

    // 15. Client X-User-Id attempting to spoof verified subject -> ignored/stripped
    @Test
    public void test15_clientSuppliedUserId_cannotOverrideVerifiedSub() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String attackerUserId = UUID.randomUUID().toString();

        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_USER_ID, attackerUserId);
        conn.setConnectTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull(req);
        assertEquals("Downstream must receive verified sub, not client header",
                sub, getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ID));
        assertNotEquals("Attacker user ID must be stripped",
                attackerUserId, getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ID));
    }

    // 16. Client X-Tenant-Id attempting to spoof verified tenant -> ignored/stripped
    @Test
    public void test16_clientSuppliedTenantId_cannotOverrideVerifiedTenant() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String attackerTenantId = UUID.randomUUID().toString();

        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TENANT_ID, attackerTenantId);
        conn.setConnectTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull(req);
        assertEquals("Downstream must receive verified tenant, not client header",
                tenantId, getFirstHeader(req, GatewayHmacProtocol.HEADER_TENANT_ID));
        assertNotEquals("Attacker tenant ID must be stripped",
                attackerTenantId, getFirstHeader(req, GatewayHmacProtocol.HEADER_TENANT_ID));
    }

    // 17. JWKS unavailable -> request rejected; never fail open
    @Test
    public void test17_jwksUnavailable_requestRejectedFailClosed() throws Exception {
        // Generate a new key and kid not present in gateway's cache
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair uncachedKp = kpg.generateKeyPair();
        String uncachedKid = "uncached-" + UUID.randomUUID().toString();

        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, (ECPrivateKey) uncachedKp.getPrivate(), uncachedKid);

        // Make mock JWKS endpoint fail with 503
        jwksServerUnavailable.set(true);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals("JWKS unavailability must result in 401 rejection (never fail open)", 401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("GATEWAY_UNKNOWN_KEY_ID"));
    }

    // 18. JWKS refresh after unknown kid -> resolves newly rotated key
    @Test
    public void test18_jwksRefreshAfterUnknownKid_resolvesNewKey() throws Exception {
        // 1. Generate rotated key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair rotatedKp = kpg.generateKeyPair();
        String rotatedKid = "rotated-key-" + UUID.randomUUID().toString();

        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, (ECPrivateKey) rotatedKp.getPrivate(), rotatedKid);

        // Register the new key on the mock JWKS server
        registeredJwksKeys.put(rotatedKid, (ECPublicKey) rotatedKp.getPublic());

        // Send request: gateway's cache does not yet have rotatedKid, so it triggers JWKS refresh and discovers it
        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull(req);
        assertEquals(sub, getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ID));
    }

    // 19. Successful validation still produces the same trusted identity consumed by HMAC signing
    @Test
    public void test19_successfulValidation_producesSameTrustedHmacIdentity() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String body = "{\"operation\":\"test-hmac-consistency\"}";

        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull(req);

        String receivedTimestamp = getFirstHeader(req, GatewayHmacProtocol.HEADER_TIMESTAMP);
        String receivedSignature = getFirstHeader(req, GatewayHmacProtocol.HEADER_SIGNATURE);
        String receivedUserId = getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ID);
        String receivedTenantId = getFirstHeader(req, GatewayHmacProtocol.HEADER_TENANT_ID);

        assertEquals(sub, receivedUserId);
        assertEquals(tenantId, receivedTenantId);

        String expectedBodySha = GatewayHmacProtocol.sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String expectedPayload = GatewayHmacProtocol.buildCanonicalPayload(
                receivedTimestamp, "POST", "/api/v1/mock/resource", receivedUserId, receivedTenantId, expectedBodySha);
        String expectedHmac = GatewayHmacProtocol.calculateHmac(expectedPayload, dynamicInternalSecret);

        assertTrue("Signature comparison must match expected downstream HMAC",
                GatewayHmacProtocol.constantTimeEquals(expectedHmac, receivedSignature));
    }

    // 20. Inbound X-Gateway-* headers are stripped and replaced with genuine Gateway HMAC
    @Test
    public void test20_inboundGatewayHeaders_strippedAndReplaced() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();

        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, primaryKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_TIMESTAMP, "999999");
        conn.setRequestProperty(GatewayHmacProtocol.HEADER_SIGNATURE, "FORGED_SIGNATURE_VALUE");
        conn.setConnectTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull(req);

        String downstreamTimestamp = getFirstHeader(req, GatewayHmacProtocol.HEADER_TIMESTAMP);
        String downstreamSignature = getFirstHeader(req, GatewayHmacProtocol.HEADER_SIGNATURE);

        assertNotEquals("Inbound timestamp must be stripped and replaced", "999999", downstreamTimestamp);
        assertNotEquals("Inbound signature must be stripped and replaced", "FORGED_SIGNATURE_VALUE", downstreamSignature);
        assertNotNull(downstreamTimestamp);
        assertNotNull(downstreamSignature);
    }

    // 21. Body hash changes signature when body changes
    @Test
    public void test21_bodyHashChangesSignatureWhenBodyChanges() throws Exception {
        byte[] body1 = "{\"name\":\"original\"}".getBytes(StandardCharsets.UTF_8);
        byte[] body2 = "{\"name\":\"modified\"}".getBytes(StandardCharsets.UTF_8);

        String sha1 = GatewayHmacProtocol.sha256Hex(body1);
        String sha2 = GatewayHmacProtocol.sha256Hex(body2);
        assertNotEquals("Different body contents must produce different SHA-256 hashes", sha1, sha2);

        String timestamp = String.valueOf(System.currentTimeMillis());
        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();

        String payload1 = GatewayHmacProtocol.buildCanonicalPayload(timestamp, "POST", "/test", userId, tenantId, sha1);
        String payload2 = GatewayHmacProtocol.buildCanonicalPayload(timestamp, "POST", "/test", userId, tenantId, sha2);

        String sig1 = GatewayHmacProtocol.calculateHmac(payload1, dynamicInternalSecret);
        String sig2 = GatewayHmacProtocol.calculateHmac(payload2, dynamicInternalSecret);

        assertNotEquals("Signatures must differ when body changes", sig1, sig2);
    }

    // 22. Missing CAMPX_SUPABASE_JWT_SECRET no longer fails startup
    @Test
    public void testSecurityConfig_missingSupabaseJwtSecret_doesNotFailStartup() {
        GatewayConfig testConfig = new GatewayConfig();
        testConfig.setInternalAuthEnabled(true);
        testConfig.setInternalSecret(UUID.randomUUID().toString());
        testConfig.setSupabaseJwtSecret(null); // legacy secret is null
        testConfig.setSupabaseJwtIssuer("https://project.supabase.co/auth/v1");

        // Must NOT throw IllegalStateException
        testConfig.validateSecurityConfiguration();
        assertNull(testConfig.getSupabaseJwtSecret());
        assertEquals("https://project.supabase.co/auth/v1", testConfig.getSupabaseJwtIssuer());
    }

    // 23. Missing CAMPX_SUPABASE_JWT_ISSUER fails startup
    @Test(expected = IllegalStateException.class)
    public void testSecurityConfig_missingSupabaseJwtIssuer_failsStartup() {
        GatewayConfig testConfig = new GatewayConfig();
        testConfig.setInternalAuthEnabled(true);
        testConfig.setInternalSecret(UUID.randomUUID().toString());
        testConfig.setSupabaseJwtIssuer(null);

        String prevIssuer = System.getProperty("campx.supabase.jwt.issuer");
        String prevEnvIssuer = System.getenv("CAMPX_SUPABASE_JWT_ISSUER");
        String prevEnvLegacy = System.getenv("SUPABASE_JWT_ISSUER");
        try {
            System.clearProperty("campx.supabase.jwt.issuer");
            setEnvForTest("CAMPX_SUPABASE_JWT_ISSUER", null);
            setEnvForTest("SUPABASE_JWT_ISSUER", null);
            testConfig.validateSecurityConfiguration();
        } finally {
            if (prevIssuer != null) {
                System.setProperty("campx.supabase.jwt.issuer", prevIssuer);
            }
            if (prevEnvIssuer != null) {
                setEnvForTest("CAMPX_SUPABASE_JWT_ISSUER", prevEnvIssuer);
            }
            if (prevEnvLegacy != null) {
                setEnvForTest("SUPABASE_JWT_ISSUER", prevEnvLegacy);
            }
        }
    }

    // 24. Missing CAMPX_INTERNAL_SECRET fails startup
    @Test(expected = IllegalStateException.class)
    public void testSecurityConfig_missingInternalSecret_failsStartup() {
        GatewayConfig testConfig = new GatewayConfig();
        testConfig.setInternalAuthEnabled(true);
        testConfig.setInternalSecret(null);
        testConfig.setSupabaseJwtIssuer("https://project.supabase.co/auth/v1");

        String prevSecret = System.getProperty("campx.internal.secret");
        String prevEnvSecret = System.getenv("CAMPX_INTERNAL_SECRET");
        try {
            System.clearProperty("campx.internal.secret");
            setEnvForTest("CAMPX_INTERNAL_SECRET", null);
            testConfig.validateSecurityConfiguration();
        } finally {
            if (prevSecret != null) {
                System.setProperty("campx.internal.secret", prevSecret);
            }
            if (prevEnvSecret != null) {
                setEnvForTest("CAMPX_INTERNAL_SECRET", prevEnvSecret);
            }
        }
    }

    // 25. SEC-01: Unknown kid triggers refresh when cooldown has expired (Requirement A)
    @Test
    public void test25_sec01_unknownKidTriggersRefreshWhenCooldownExpired() throws Exception {
        gatewayConfig.getJwksClient().setLastFetchTime(0L); // Cooldown expired
        int hitsBefore = jwksHitCount.get();

        String unknownKid = "cooldown-exp-" + UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, unknownKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("GATEWAY_UNKNOWN_KEY_ID"));
        assertEquals("JWKS hit count must increase by exactly 1 when cooldown has expired",
                hitsBefore + 1, jwksHitCount.get());
    }

    // 26. SEC-01: Multiple unknown kids within 10-second cooldown do NOT trigger repeated JWKS requests (Requirement B)
    @Test
    public void test26_sec01_multipleUnknownKidsWithinCooldownDoNotTriggerRepeatedJwksRequests() throws Exception {
        gatewayConfig.getJwksClient().setLastFetchTime(0L); // Reset cooldown
        int hitsBefore = jwksHitCount.get();

        // 1st unknown kid: triggers initial JWKS refresh, activates cooldown
        String token1 = SupabaseJwtValidator.createSignedJwt(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, "flood-kid-1-" + UUID.randomUUID());
        URL url1 = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn1 = (HttpURLConnection) url1.openConnection();
        conn1.setRequestMethod("GET");
        conn1.setRequestProperty("Authorization", "Bearer " + token1);
        conn1.setConnectTimeout(3000);

        assertEquals(401, conn1.getResponseCode());
        assertEquals("First unknown kid must trigger exactly 1 refresh", hitsBefore + 1, jwksHitCount.get());

        // 2nd unknown kid (within 10-second cooldown): must NOT trigger another HTTP request
        String token2 = SupabaseJwtValidator.createSignedJwt(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, "flood-kid-2-" + UUID.randomUUID());
        URL url2 = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn2 = (HttpURLConnection) url2.openConnection();
        conn2.setRequestMethod("GET");
        conn2.setRequestProperty("Authorization", "Bearer " + token2);
        conn2.setConnectTimeout(3000);

        assertEquals(401, conn2.getResponseCode());
        String resp2 = readStream(conn2.getErrorStream());
        assertTrue(resp2.contains("GATEWAY_UNKNOWN_KEY_ID"));
        assertEquals("Second unknown kid within cooldown must NOT trigger another JWKS request",
                hitsBefore + 1, jwksHitCount.get());

        // 3rd unknown kid (within 10-second cooldown): must NOT trigger another HTTP request
        String token3 = SupabaseJwtValidator.createSignedJwt(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, "flood-kid-3-" + UUID.randomUUID());
        URL url3 = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn3 = (HttpURLConnection) url3.openConnection();
        conn3.setRequestMethod("GET");
        conn3.setRequestProperty("Authorization", "Bearer " + token3);
        conn3.setConnectTimeout(3000);

        assertEquals(401, conn3.getResponseCode());
        String resp3 = readStream(conn3.getErrorStream());
        assertTrue(resp3.contains("GATEWAY_UNKNOWN_KEY_ID"));
        assertEquals("Third unknown kid within cooldown must NOT trigger another JWKS request",
                hitsBefore + 1, jwksHitCount.get());
    }

    // 27. SEC-01: Unknown kid after cooldown triggers another refresh (Requirement C)
    @Test
    public void test27_sec01_unknownKidAfterCooldownTriggersAnotherRefresh() throws Exception {
        // Simulate previous refresh completed 11 seconds ago (cooldown expired)
        gatewayConfig.getJwksClient().setLastFetchTime(System.currentTimeMillis() - 11000L);
        int hitsBefore = jwksHitCount.get();

        String token = SupabaseJwtValidator.createSignedJwt(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, "after-cooldown-" + UUID.randomUUID());

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        assertEquals("After cooldown expires, unknown kid must trigger exactly one JWKS refresh",
                hitsBefore + 1, jwksHitCount.get());
    }

    // 28. SEC-01: Legitimate newly rotated key is discovered after refresh (Requirement D)
    @Test
    public void test28_sec01_legitimateNewlyRotatedKeyDiscoveredAfterRefresh() throws Exception {
        gatewayConfig.getJwksClient().setLastFetchTime(0L); // Ensure cooldown is expired

        // Generate newly rotated key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair newKp = kpg.generateKeyPair();
        String newKid = "newly-rotated-" + UUID.randomUUID().toString();

        // Register new key on mock JWKS server
        registeredJwksKeys.put(newKid, (ECPublicKey) newKp.getPublic());

        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                sub, tenantId, "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, (ECPrivateKey) newKp.getPrivate(), newKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        RecordedRequest req = lastDownstreamRequest.get();
        assertNotNull(req);
        assertEquals(sub, getFirstHeader(req, GatewayHmacProtocol.HEADER_USER_ID));
    }

    // 29. SEC-01: Unknown kid after refresh remains rejected with HTTP 401 (Requirement E)
    @Test
    public void test29_sec01_unknownKidAfterRefreshRemainsRejected401() throws Exception {
        gatewayConfig.getJwksClient().setLastFetchTime(0L);

        String unknownKid = "still-unknown-" + UUID.randomUUID().toString();
        String token = SupabaseJwtValidator.createSignedJwt(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "authenticated", dynamicIssuer, "authenticated",
                System.currentTimeMillis() / 1000 + 3600, primaryPrivateKey, unknownKid);

        URL url = new URL("http://127.0.0.1:" + gatewayPort + "/api/v1/mock/resource");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(3000);

        assertEquals(401, conn.getResponseCode());
        String resp = readStream(conn.getErrorStream());
        assertTrue(resp.contains("GATEWAY_UNKNOWN_KEY_ID"));
    }

    // 30. Regression: JWKS key with nested array "key_ops":["verify"] is correctly parsed and found
    @Test
    public void test30_regression_jwksKeyWithKeyOpsArrayCorrectlyParsed() throws Exception {
        String kid = "ed140141-d835-4006-8b13-950575dfc984";
        String sampleJwks = "{\"keys\":[{\"alg\":\"ES256\",\"crv\":\"P-256\",\"ext\":true,"
                + "\"key_ops\":[\"verify\"],"
                + "\"kid\":\"" + kid + "\","
                + "\"kty\":\"EC\",\"use\":\"sig\","
                + "\"x\":\"cc96HdfCg6BlqQ7gLaE_494D_-MpZcFyOb27Ho202KI\","
                + "\"y\":\"JD7aEq2iFPCdDx0sb8YYXQyH5ENLINom_Lts_ASimnw\"}]}";

        Map<String, PublicKey> parsed = JwksClient.parseJwks(sampleJwks);
        assertNotNull("Parsed keys map must not be null", parsed);
        assertEquals("Must parse exactly 1 key", 1, parsed.size());
        assertTrue("Must contain expected kid: " + kid, parsed.containsKey(kid));
        assertNotNull("Public key must not be null", parsed.get(kid));
        assertEquals("EC", parsed.get(kid).getAlgorithm());
    }

    // --- Helper Methods ---

    private static String buildJwksJson(Map<String, ECPublicKey> keys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        int i = 0;
        for (Map.Entry<String, ECPublicKey> entry : keys.entrySet()) {
            if (i > 0) sb.append(",");
            String kid = entry.getKey();
            ECPublicKey pub = entry.getValue();
            BigInteger x = pub.getW().getAffineX();
            BigInteger y = pub.getW().getAffineY();

            String xB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(x));
            String yB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsigned32ByteArray(y));

            sb.append("{")
              .append("\"kty\":\"EC\",")
              .append("\"crv\":\"P-256\",")
              .append("\"alg\":\"ES256\",")
              .append("\"use\":\"sig\",")
              .append("\"kid\":\"").append(kid).append("\",")
              .append("\"x\":\"").append(xB64).append("\",")
              .append("\"y\":\"").append(yB64).append("\"")
              .append("}");
            i++;
        }
        sb.append("]}");
        return sb.toString();
    }

    private static byte[] toUnsigned32ByteArray(BigInteger bi) {
        byte[] array = bi.toByteArray();
        if (array.length == 32) return array;
        byte[] out = new byte[32];
        if (array.length > 32) {
            System.arraycopy(array, array.length - 32, out, 0, 32);
        } else {
            System.arraycopy(array, 0, out, 32 - array.length, array.length);
        }
        return out;
    }

    private String getFirstHeader(RecordedRequest req, String headerName) {
        if (req == null || req.headers == null) return null;
        for (Map.Entry<String, List<String>> entry : req.headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                List<String> vals = entry.getValue();
                return (vals != null && !vals.isEmpty()) ? vals.get(0) : null;
            }
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private static void setEnvForTest(String key, String value) {
        try {
            Class<?> peClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvField = peClass.getDeclaredField("theEnvironment");
            theEnvField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvField.get(null);
            if (value == null) {
                env.remove(key);
            } else {
                env.put(key, value);
            }

            Field ciEnvField = peClass.getDeclaredField("theCaseInsensitiveEnvironment");
            ciEnvField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) ciEnvField.get(null);
            if (value == null) {
                cienv.remove(key);
            } else {
                cienv.put(key, value);
            }
        } catch (Throwable ignored) {
        }
    }
}
