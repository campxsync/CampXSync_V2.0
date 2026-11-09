package com.campx.gateway.security;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Regression test suite for SEC-CRIT-01: Verifies AST-based Jackson JWT claim parsing in {@link SupabaseJwtValidator}.
 * Ensures that nested claims in user_metadata or unexpected structures cannot hijack root claims.
 */
public class SupabaseJwtClaimParsingRegressionTest {

    private static final String TEST_ISSUER = "https://test.supabase.co/auth/v1";
    private static final String TEST_KID = "test-sec-kid-01";
    private static ECPrivateKey privateKey;
    private static JwksClient mockJwksClient;

    @BeforeClass
    public static void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();
        privateKey = (ECPrivateKey) kp.getPrivate();
        ECPublicKey publicKey = (ECPublicKey) kp.getPublic();

        mockJwksClient = new JwksClient("http://127.0.0.1:0/.well-known/jwks.json") {
            @Override
            public PublicKey getPublicKey(String kid) {
                if (TEST_KID.equals(kid)) {
                    return publicKey;
                }
                return null;
            }
        };
    }

    private String createTokenWithHeaderAndPayload(String headerJson, String payloadJson) throws Exception {
        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String content = headerB64 + "." + payloadB64;

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        byte[] derSig = signer.sign();
        byte[] rawSig = SupabaseJwtValidator.derToConcatSignature(derSig);
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSig);

        return content + "." + sigB64;
    }

    private String createTokenWithPayload(String payloadJson) throws Exception {
        return createTokenWithHeaderAndPayload("{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"" + TEST_KID + "\"}", payloadJson);
    }

    // 1. A valid token with legitimate root claims
    @Test
    public void test01_validTokenWithRootClaims_extractedCorrectly() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;

        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);

        assertNotNull(claims);
        assertEquals(sub, claims.getUserId());
        assertEquals(tenantId, claims.getTenantId());
        assertEquals("authenticated", claims.getRole());
    }

    // 2. user_metadata.role = "SUPER_ADMIN" while root role = "authenticated"
    @Test
    public void test02_userMetadataRoleSuperAdmin_doesNotOverrideRootRole() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;

        // user_metadata is serialized before role, mimicking Supabase GoTrue output
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"user_metadata\":{\"role\":\"SUPER_ADMIN\",\"title\":\"attacker\"},"
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);

        assertNotNull(claims);
        assertEquals("Root role must remain 'authenticated'", "authenticated", claims.getRole());
        assertNotEquals("SUPER_ADMIN in user_metadata must not override root role", "SUPER_ADMIN", claims.getRole());
    }

    // 3. user_metadata.sub containing a forged user ID
    @Test
    public void test03_userMetadataSubForged_doesNotOverrideRootSub() throws Exception {
        String legitimateSub = UUID.randomUUID().toString();
        String forgedSub = "00000000-0000-0000-0000-000000000001";
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;

        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"user_metadata\":{\"sub\":\"" + forgedSub + "\"},"
                + "\"sub\":\"" + legitimateSub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);

        assertNotNull(claims);
        assertEquals("Legitimate sub must be returned", legitimateSub, claims.getUserId());
        assertNotEquals("Forged sub in user_metadata must be ignored", forgedSub, claims.getUserId());
    }

    // 4. Nested user_metadata.app_metadata.tenant_id containing a forged tenant ID
    @Test
    public void test04_userMetadataNestedAppMetadataTenantId_doesNotOverrideRootTenantId() throws Exception {
        String sub = UUID.randomUUID().toString();
        String legitimateTenantId = UUID.randomUUID().toString();
        String forgedTenantId = "00000000-0000-0000-0000-000000000002";
        long exp = System.currentTimeMillis() / 1000 + 3600;

        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"user_metadata\":{\"app_metadata\":{\"tenant_id\":\"" + forgedTenantId + "\"}},"
                + "\"app_metadata\":{\"tenant_id\":\"" + legitimateTenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);

        assertNotNull(claims);
        assertEquals("Legitimate root tenant_id must be returned", legitimateTenantId, claims.getTenantId());
        assertNotEquals("Forged tenant_id in user_metadata must be ignored", forgedTenantId, claims.getTenantId());
    }

    // 5. Legitimate root app_metadata.tenant_id alongside misleading nested metadata
    @Test
    public void test05_legitimateRootClaimsAlongsideMultipleMisleadingNestedMetadata() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;

        String payload = "{"
                + "\"user_metadata\":{"
                + "  \"sub\":\"00000000-0000-0000-0000-000000000003\","
                + "  \"role\":\"SUPER_ADMIN\","
                + "  \"tenant_id\":\"00000000-0000-0000-0000-000000000004\","
                + "  \"app_metadata\":{\"tenant_id\":\"00000000-0000-0000-0000-000000000005\"}"
                + "},"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);

        assertNotNull(claims);
        assertEquals(sub, claims.getUserId());
        assertEquals(tenantId, claims.getTenantId());
        assertEquals("authenticated", claims.getRole());
    }

    // 6. Malformed JSON and missing or invalid required claims
    @Test(expected = JwtValidationException.class)
    public void test06a_malformedPayloadJson_rejected() throws Exception {
        String token = createTokenWithPayload("{unclosed json payload");
        SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
    }

    @Test
    public void test06b_missingSubClaim_rejectedWithMalformedJwt() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"}"
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for missing 'sub'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("sub"));
        }
    }

    @Test
    public void test06c_invalidSubUuidFormat_rejectedWithMalformedJwt() throws Exception {
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"not-a-valid-uuid\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"}"
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for invalid 'sub' UUID");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("Invalid UUID"));
        }
    }

    @Test
    public void test06d_missingTenantId_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for missing 'tenant_id'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("tenant_id"));
        }
    }

    @Test
    public void test06e_invalidTenantIdUuidFormat_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"invalid-tenant-uuid\"}"
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for invalid 'tenant_id' UUID");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("Invalid UUID"));
        }
    }

    @Test
    public void test06f_missingExpClaim_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"}"
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for missing 'exp'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("exp"));
        }
    }

    @Test
    public void test06g_unexpectedExpType_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"exp\":\"tomorrow\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"}"
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for string 'exp'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("exp"));
        }
    }

    @Test
    public void test06h_unexpectedAppMetadataType_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"exp\":" + exp + ","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":\"not-an-object\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for string 'app_metadata'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("app_metadata"));
        }
    }

    @Test
    public void test06i_unexpectedRoleType_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"exp\":" + exp + ","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":12345"
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for number 'role'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertTrue(e.getMessage().contains("role"));
        }
    }

    // 7. Root tenant_id without app_metadata.tenant_id is rejected
    @Test
    public void test07a_rootTenantIdWithoutAppMetadata_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String rootTenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"tenant_id\":\"" + rootTenantId + "\","
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException when app_metadata is missing");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
            assertTrue(e.getMessage().contains("tenant_id"));
        }
    }

    @Test
    public void test07b_rootTenantIdWithEmptyAppMetadata_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String rootTenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"tenant_id\":\"" + rootTenantId + "\","
                + "\"app_metadata\":{},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException when app_metadata lacks tenant_id");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
            assertTrue(e.getMessage().contains("tenant_id"));
        }
    }

    // 8. Valid app_metadata.tenant_id accepted and extracted
    @Test
    public void test08_validAppMetadataTenantId_accepted() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);

        assertNotNull(claims);
        assertEquals(sub, claims.getUserId());
        assertEquals(tenantId, claims.getTenantId());
        assertEquals("authenticated", claims.getRole());
    }

    // 9. Duplicate root keys rejected by strict duplicate detection
    @Test
    public void test09a_duplicateRootRoleKey_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\","
                + "\"role\":\"ACADEMIC_ADMIN\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for duplicate root 'role'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    @Test
    public void test09b_duplicateRootSubKey_rejectedWithMalformedJwt() throws Exception {
        String sub1 = UUID.randomUUID().toString();
        String sub2 = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub1 + "\","
                + "\"sub\":\"" + sub2 + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for duplicate root 'sub'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    @Test
    public void test09c_duplicateRootAppMetadataKey_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenant1 = UUID.randomUUID().toString();
        String tenant2 = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenant1 + "\"},"
                + "\"app_metadata\":{\"tenant_id\":\"" + tenant2 + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for duplicate root 'app_metadata'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 10. Duplicate tenant_id keys inside app_metadata rejected
    @Test
    public void test10_duplicateTenantIdInsideAppMetadata_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenant1 = UUID.randomUUID().toString();
        String tenant2 = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{"
                + "  \"tenant_id\":\"" + tenant1 + "\","
                + "  \"tenant_id\":\"" + tenant2 + "\""
                + "},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for duplicate nested 'tenant_id'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 11. Duplicate keys in the JWT header rejected
    @Test
    public void test11_duplicateHeaderKeys_rejectedWithMalformedJwt() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String header = "{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"" + TEST_KID + "\",\"kid\":\"" + TEST_KID + "\"}";
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithHeaderAndPayload(header, payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for duplicate key in JWT header");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 12. Fractional exp claim rejected
    @Test
    public void test12_fractionalExp_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":1718000000.5,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for fractional 'exp'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 13. Oversized exp exceeding 64-bit signed long rejected
    @Test
    public void test13_oversizedExp_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":999999999999999999999999999999,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for oversized 'exp'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 14. Negative exp claim rejected
    @Test
    public void test14_negativeExp_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":-100,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for negative 'exp'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 15. Fractional nbf claim rejected
    @Test
    public void test15_fractionalNbf_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"nbf\":1718000000.75,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for fractional 'nbf'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 16. Oversized nbf claim rejected
    @Test
    public void test16_oversizedNbf_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"nbf\":123456789012345678901234567890,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for oversized 'nbf'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 17. Negative nbf claim rejected
    @Test
    public void test17_negativeNbf_rejected() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + exp + ","
                + "\"nbf\":-50,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        try {
            SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
            fail("Expected JwtValidationException for negative 'nbf'");
        } catch (JwtValidationException e) {
            assertEquals("GATEWAY_MALFORMED_JWT", e.getErrorCode());
            assertEquals(401, e.getStatusCode());
        }
    }

    // 18. Valid boundary values accepted (exp = Long.MAX_VALUE, nbf = 0)
    @Test
    public void test18_boundaryValidValues_accepted() throws Exception {
        String sub = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String payload = "{"
                + "\"aud\":\"authenticated\","
                + "\"exp\":" + Long.MAX_VALUE + ","
                + "\"nbf\":0,"
                + "\"iss\":\"" + TEST_ISSUER + "\","
                + "\"sub\":\"" + sub + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},"
                + "\"role\":\"authenticated\""
                + "}";

        String token = createTokenWithPayload(payload);
        SupabaseJwtValidator.VerifiedClaims claims = SupabaseJwtValidator.validate("Bearer " + token, mockJwksClient, TEST_ISSUER);
        assertNotNull(claims);
        assertEquals(sub, claims.getUserId());
        assertEquals(tenantId, claims.getTenantId());
    }
}
