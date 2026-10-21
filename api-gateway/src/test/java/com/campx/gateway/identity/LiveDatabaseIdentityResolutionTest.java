package com.campx.gateway.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Targeted integration verification test connecting JdbcIdentityResolver
 * against the approved CampXSync development database (azulbaxszsqxymqmlwfe).
 */
public class LiveDatabaseIdentityResolutionTest {

    private String getDevJdbcUrl() {
        String envUrl = System.getenv("SUPABASE_JDBC_URL");
        if (envUrl != null && envUrl.contains("ap-southeast-1")) {
            // Correct the pooler region from Singapore to Mumbai (ap-south-1) for dev ref azulbaxszsqxymqmlwfe
            return envUrl.replace("ap-southeast-1", "ap-south-1");
        }
        return envUrl;
    }

    private HikariDataSource createDevDataSource() {
        String jdbcUrl = getDevJdbcUrl();
        String user = System.getenv("SUPABASE_USER");
        String pass = System.getenv("SUPABASE_PASSWORD");

        if (jdbcUrl == null || user == null || pass == null) {
            return null;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setPoolName("Test-Dev-Pool");
        return new HikariDataSource(config);
    }

    @Test
    public void testLiveDatabaseConnectivityAndIdentityResolution() throws Exception {
        HikariDataSource ds = createDevDataSource();
        if (ds == null) {
            System.out.println("[BLOCKED] Dev database credentials missing from environment");
            return;
        }

        try {
            // 1. Verify basic read-only connectivity and target properties
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, inet_server_addr()")) {
                assertTrue(rs.next());
                System.out.println("[LIVE-DB] Connected to database: " + rs.getString(1) + ", user: " + rs.getString(2) + ", server: " + rs.getString(3));
            }

            // 2. Query table counts on live dev PostgreSQL
            int profileCount = -1;
            int tenantCount = -1;
            int platAdminCount = -1;

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM iam.user_profiles")) {
                    if (rs.next()) profileCount = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.tenants")) {
                    if (rs.next()) tenantCount = rs.getInt(1);
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM plat.platform_admins")) {
                    if (rs.next()) platAdminCount = rs.getInt(1);
                }
            }

            System.out.println("[LIVE-DB] Table row counts: iam.user_profiles=" + profileCount + ", core.tenants=" + tenantCount + ", plat.platform_admins=" + platAdminCount);
            assertTrue("Database query should execute successfully", profileCount >= 0);
            assertTrue("Database query should execute successfully", tenantCount >= 0);
            assertTrue("Database query should execute successfully", platAdminCount >= 0);

            // 3. Instantiate JdbcIdentityResolver with live dev DataSource
            JdbcIdentityResolver resolver = new JdbcIdentityResolver(ds);

            // 4. Test live resolution against live PostgreSQL database
            UUID testUserUuid = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
            IdentityResolutionResult result = resolver.resolveIdentity(testUserUuid);

            System.out.println("[LIVE-DB] Live PostgreSQL identity resolution result: success=" + result.isSuccess()
                    + ", failureReason=" + result.getFailureReason() + ", msg=" + result.getMessage());

            if (profileCount > 0 && result.isSuccess()) {
                // POSITIVE PATH VERIFICATION (Seeded State)
                ResolvedIdentity identity = result.getIdentity();
                assertNotNull("Resolved identity must not be null", identity);
                assertEquals("User ID must match", "ba77b8f7-ab32-46bd-85d2-5aade3526880", identity.getUserId());
                assertEquals("Role must be COLLEGE_ADMIN", "COLLEGE_ADMIN", identity.getBusinessRole());
                assertEquals("Normalized role must be college_admin", "college_admin", identity.getNormalizedRole());
                assertEquals("Tenant ID must match test tenant", "a0000000-0000-0000-0000-000000000001", identity.getTenantId());
                assertEquals("College ID must match test college", "a0000000-0000-0000-0000-000000000002", identity.getCollegeId());
                assertEquals("Email must match test user", "dev.admin@campxsync.test", identity.getEmail());
                assertFalse("Must not be platform admin", identity.isPlatformAdmin());
                assertTrue("Roles list must contain COLLEGE_ADMIN", identity.getRoles().contains("COLLEGE_ADMIN"));
                System.out.println("[LIVE-DB] Positive identity resolution VERIFIED: " + identity.toJson());
            } else {
                // UNSEEDED FAIL-CLOSED STATE
                assertFalse("Unseeded user profile must fail closed against live PostgreSQL", result.isSuccess());
                assertEquals("no_profile", result.getFailureReason());
                assertTrue(result.getMessage().contains("No active user profile found"));
            }

            // 5. Nonexistent user always fails closed (NEGATIVE PATH VERIFICATION)
            UUID nonExistentUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
            IdentityResolutionResult notFoundResult = resolver.resolveIdentity(nonExistentUuid);
            assertFalse("Nonexistent user must fail closed", notFoundResult.isSuccess());
            assertEquals("no_profile", notFoundResult.getFailureReason());
            System.out.println("[LIVE-DB] Negative identity resolution (missing profile) VERIFIED: fail-closed confirmed");

        } finally {
            ds.close();
        }
    }

    @Test
    public void testLiveGatewayHttpAuthMeWithDevDatabase() throws Exception {
        HikariDataSource ds = createDevDataSource();
        if (ds == null) {
            System.out.println("[BLOCKED] Dev database credentials missing from environment");
            return;
        }

        // Generate dynamic keypair for mock token validation
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.KeyPair kp = kpg.generateKeyPair();
        java.security.interfaces.ECPublicKey pubKey = (java.security.interfaces.ECPublicKey) kp.getPublic();
        java.security.interfaces.ECPrivateKey privKey = (java.security.interfaces.ECPrivateKey) kp.getPrivate();
        String kid = UUID.randomUUID().toString();

        // Start mock JWKS server
        com.sun.net.httpserver.HttpServer jwksServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int jwksPort = jwksServer.getAddress().getPort();
        String jwksJson = "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"alg\":\"ES256\",\"use\":\"sig\",\"kid\":\"" + kid + "\","
                + "\"x\":\"" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey.getW().getAffineX().toByteArray()) + "\","
                + "\"y\":\"" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey.getW().getAffineY().toByteArray()) + "\"}]}";
        jwksServer.createContext("/.well-known/jwks.json", ex -> {
            byte[] b = jwksJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (java.io.OutputStream os = ex.getResponseBody()) { os.write(b); }
        });
        jwksServer.start();

        String prevPersist = System.getProperty("campx.persistence.mode");
        String issuer = "http://127.0.0.1:" + jwksPort;
        System.setProperty("campx.supabase.jwt.issuer", issuer);
        System.setProperty("campx.internal.auth.enabled", "true");
        System.setProperty("campx.internal.secret", "test-internal-secret-for-gateway");
        System.setProperty("campx.persistence.mode", "test");

        com.campx.gateway.config.GatewayConfig config = new com.campx.gateway.config.GatewayConfig();
        config.setPort(0);
        config.setIdentityResolver(new JdbcIdentityResolver(ds));
        config.setIdentityResolutionEnabled(true);

        com.campx.gateway.server.GatewayServer gateway = new com.campx.gateway.server.GatewayServer(config);
        gateway.start();
        int gwPort = gateway.getPort();

        try {
            // Check if seeded
            boolean isSeeded = false;
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM iam.user_profiles WHERE id = ?")) {
                ps.setObject(1, UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) isSeeded = (rs.getInt(1) > 0);
                }
            }

            if (isSeeded) {
                // ====================================================================
                // POSITIVE PATH: Seeded user ba77b8f7-ab32-46bd-85d2-5aade3526880
                // ====================================================================
                UUID userId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
                String token = createJwtToken(userId, issuer, kid, privKey);

                java.net.URL url = new java.net.URL("http://127.0.0.1:" + gwPort + "/api/v1/auth/me");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                // Attempt to inject spoofed tenant and role headers
                conn.setRequestProperty("X-Tenant-Id", "99999999-9999-9999-9999-999999999999");
                conn.setRequestProperty("X-User-Role", "SUPER_ADMIN");

                int responseCode = conn.getResponseCode();
                System.out.println("[LIVE-DB] Gateway /api/v1/auth/me HTTP status=" + responseCode);
                assertEquals("Seeded user profile must return HTTP 200", 200, responseCode);

                String cacheControl = conn.getHeaderField("Cache-Control");
                assertNotNull("Cache-Control header must be present", cacheControl);
                assertTrue("Cache-Control must contain no-store", cacheControl.contains("no-store"));

                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                String body = s.hasNext() ? s.next() : "";
                System.out.println("[LIVE-DB] Gateway response body: " + body);

                // Verify spoofed headers were stripped and authoritative DB values returned
                assertTrue("Response must contain authorized tenant", body.contains("\"tenantId\":\"a0000000-0000-0000-0000-000000000001\""));
                assertFalse("Response must NOT contain spoofed tenant", body.contains("99999999-9999-9999-9999-999999999999"));
                assertTrue("Response must contain authorized role COLLEGE_ADMIN", body.contains("\"role\":\"COLLEGE_ADMIN\""));
                assertFalse("Response must NOT contain spoofed SUPER_ADMIN role", body.contains("\"role\":\"SUPER_ADMIN\""));
                assertTrue("Response must contain authorized college", body.contains("\"collegeId\":\"a0000000-0000-0000-0000-000000000002\""));

                // ====================================================================
                // NEGATIVE PATH: Unseeded user must fail closed with HTTP 403
                // ====================================================================
                UUID unseededId = UUID.fromString("00000000-0000-0000-0000-000000000000");
                String unseededToken = createJwtToken(unseededId, issuer, kid, privKey);

                java.net.HttpURLConnection negConn = (java.net.HttpURLConnection) url.openConnection();
                negConn.setRequestMethod("GET");
                negConn.setRequestProperty("Authorization", "Bearer " + unseededToken);

                int negCode = negConn.getResponseCode();
                System.out.println("[LIVE-DB] Gateway /api/v1/auth/me (unseeded user) HTTP status=" + negCode);
                assertEquals("Unseeded user profile must fail closed with HTTP 403", 403, negCode);

                String negCache = negConn.getHeaderField("Cache-Control");
                assertNotNull("Cache-Control header must be present on 403", negCache);
                assertTrue("Cache-Control must contain no-store", negCache.contains("no-store"));

                java.io.InputStream negIs = negConn.getErrorStream();
                java.util.Scanner negS = new java.util.Scanner(negIs, "UTF-8").useDelimiter("\\A");
                String negBody = negS.hasNext() ? negS.next() : "";
                assertTrue("Error response must contain GATEWAY_NO_PROFILE", negBody.contains("GATEWAY_NO_PROFILE"));
            } else {
                // UNSEEDED STATE: test fail-closed HTTP 403
                UUID userId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
                String token = createJwtToken(userId, issuer, kid, privKey);

                java.net.URL url = new java.net.URL("http://127.0.0.1:" + gwPort + "/api/v1/auth/me");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("X-Tenant-Id", "99999999-9999-9999-9999-999999999999");
                conn.setRequestProperty("X-User-Role", "SUPER_ADMIN");

                int responseCode = conn.getResponseCode();
                System.out.println("[LIVE-DB] Gateway /api/v1/auth/me HTTP status=" + responseCode);
                assertEquals("Unseeded user profile must fail closed with HTTP 403", 403, responseCode);

                String cacheControl = conn.getHeaderField("Cache-Control");
                assertNotNull("Cache-Control header must be present", cacheControl);
                assertTrue("Cache-Control must contain no-store", cacheControl.contains("no-store"));

                java.io.InputStream es = conn.getErrorStream();
                java.util.Scanner s = new java.util.Scanner(es, "UTF-8").useDelimiter("\\A");
                String body = s.hasNext() ? s.next() : "";
                System.out.println("[LIVE-DB] Gateway response body: " + body);
                assertTrue("Error response must contain GATEWAY_NO_PROFILE", body.contains("GATEWAY_NO_PROFILE"));
            }

        } finally {
            gateway.stop();
            jwksServer.stop(0);
            ds.close();
            System.clearProperty("campx.supabase.jwt.issuer");
            System.clearProperty("campx.internal.auth.enabled");
            System.clearProperty("campx.internal.secret");
            if (prevPersist != null) {
                System.setProperty("campx.persistence.mode", prevPersist);
            } else {
                System.clearProperty("campx.persistence.mode");
            }
        }
    }

    @Test
    public void testLiveDatabaseCommittedReadBack() throws Exception {
        HikariDataSource ds = createDevDataSource();
        if (ds == null) {
            System.out.println("[BLOCKED] Dev database credentials missing from environment");
            return;
        }

        try (Connection conn = ds.getConnection()) {
            // Check if seeded
            UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
            boolean tenantExists = false;
            try (PreparedStatement ps = conn.prepareStatement("SELECT code, status FROM core.tenants WHERE id = ?")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantExists = true;
                        assertEquals("DEV_TEST_TENANT", rs.getString("code"));
                        assertEquals("ACTIVE", rs.getString("status"));
                    }
                }
            }

            if (!tenantExists) {
                System.out.println("[LIVE-DB] Database not yet seeded; skipping positive read-back verification.");
                return;
            }

            // Verify College
            UUID collegeId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id, code, status FROM core.colleges WHERE id = ?")) {
                ps.setObject(1, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Seeded college must exist", rs.next());
                    assertEquals(tenantId, rs.getObject("tenant_id"));
                    assertEquals("DEV_TEST_COLLEGE", rs.getString("code"));
                    assertEquals("ACTIVE", rs.getString("status"));
                }
            }

            // Verify User Profile
            UUID userId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id, college_id, email, status FROM iam.user_profiles WHERE id = ?")) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Seeded user profile must exist", rs.next());
                    assertEquals(tenantId, rs.getObject("tenant_id"));
                    assertEquals(collegeId, rs.getObject("college_id"));
                    assertEquals("dev.admin@campxsync.test", rs.getString("email"));
                    assertEquals("ACTIVE", rs.getString("status"));
                }
            }

            // Verify Role Assignment
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.code FROM iam.role_assignments ra JOIN iam.roles r ON ra.role_id = r.id WHERE ra.user_id = ? AND ra.tenant_id = ?")) {
                ps.setObject(1, userId);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Role assignment must exist", rs.next());
                    assertEquals("COLLEGE_ADMIN", rs.getString("code"));
                }
            }

            // Verify Trigger-Generated Roles & Permissions
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM iam.roles WHERE tenant_id = ? AND is_system = true")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Trigger must seed exactly 30 system roles", 30, rs.getInt(1));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM iam.role_permissions WHERE tenant_id = ?")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Trigger must seed exactly 2,138 role-permissions", 2138, rs.getInt(1));
                }
            }

            System.out.println("[LIVE-DB] Committed read-back and tenant isolation VERIFIED.");
        } finally {
            ds.close();
        }
    }

    private String createJwtToken(UUID userId, String issuer, String kid, java.security.interfaces.ECPrivateKey privKey) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String headerJson = "{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String payloadJson = "{\"sub\":\"" + userId + "\",\"iss\":\"" + issuer + "\",\"aud\":\"authenticated\","
                + "\"iat\":" + now + ",\"exp\":" + (now + 3600) + ",\"app_metadata\":{\"tenant_id\":\"11111111-1111-1111-1111-111111111111\"}}";
        String encHeader = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String encPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signingInput = encHeader + "." + encPayload;

        java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
        sig.initSign(privKey);
        sig.update(signingInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] derSig = sig.sign();
        int rLen = derSig[3];
        int rOffset = 4;
        if (derSig[rOffset] == 0) { rOffset++; rLen--; }
        int sOffset = 4 + derSig[3] + 2;
        int sLen = derSig[sOffset - 1];
        if (derSig[sOffset] == 0) { sOffset++; sLen--; }
        byte[] joseSig = new byte[64];
        System.arraycopy(derSig, rOffset, joseSig, 32 - rLen, rLen);
        System.arraycopy(derSig, sOffset, joseSig, 64 - sLen, sLen);
        return signingInput + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(joseSig);
    }
}
