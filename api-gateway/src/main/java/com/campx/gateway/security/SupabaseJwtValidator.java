package com.campx.gateway.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pure Java 8 Supabase Auth JWT cryptographic parser and validator for the API Gateway.
 * <p>
 * Enforces ES256 asymmetric signature verification using public keys discovered via
 * Supabase JWKS. Legacy HS256 tokens and unsigned tokens ({@code alg:none}) are strictly rejected.
 * </p>
 * <p>
 * Ensures that client identity (user ID, tenant ID, role) is derived strictly
 * from a verified Supabase JWT and never from unauthenticated client headers.
 * </p>
 */
public final class SupabaseJwtValidator {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SupabaseJwtValidator.class);

    private static final String REQUIRED_ALGORITHM = "ES256";
    private static final String REQUIRED_AUDIENCE = "authenticated";
    private static final String EC_SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private SupabaseJwtValidator() {}

    /**
     * Verified security claims extracted from a valid Supabase JWT.
     */
    public static class VerifiedClaims {
        private final String userId;
        private final String tenantId;
        private final String role;

        public VerifiedClaims(String userId, String tenantId, String role) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.role = role;
        }

        public String getUserId() {
            return userId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getRole() {
            return role;
        }
    }

    /**
     * Validates the incoming Authorization header, cryptographically verifies the token using ES256/JWKS,
     * validates all required claims, and returns the verified principal claims.
     *
     * @param authHeader     raw {@code Authorization: Bearer <JWT>} header
     * @param jwksClient     JWKS client providing public keys for signature verification
     * @param expectedIssuer expected Supabase project issuer URL
     * @return verified claims containing userId, tenantId, and role
     * @throws JwtValidationException if token is missing, malformed, expired, or invalid
     */
    public static VerifiedClaims validate(String authHeader, JwksClient jwksClient, String expectedIssuer) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            throw new JwtValidationException(401, "GATEWAY_MISSING_AUTH", "Missing Authorization header");
        }

        String trimmedHeader = authHeader.trim();
        if (!trimmedHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new JwtValidationException(401, "GATEWAY_MISSING_AUTH", "Authorization header must use Bearer scheme");
        }

        String token = trimmedHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new JwtValidationException(401, "GATEWAY_MISSING_AUTH", "Empty Bearer token provided");
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Malformed JWT: expected 3 dot-separated parts");
        }

        // 1. Decode and validate header
        byte[] headerBytes;
        try {
            headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Malformed JWT header encoding");
        }
        String headerJson = new String(headerBytes, StandardCharsets.UTF_8);

        // Enforce ES256 ONLY. Strictly reject HS256, none, and all unexpected algorithms
        String alg = extractJsonString(headerJson, "alg");
        if (alg == null || !REQUIRED_ALGORITHM.equals(alg.trim())) {
            throw new JwtValidationException(401, "GATEWAY_UNSUPPORTED_JWT_ALG",
                    "Unsupported or missing JWT signing algorithm: " + (alg != null ? alg : "NONE"));
        }

        // Require valid kid in header
        String kid = extractJsonString(headerJson, "kid");
        if (kid == null || kid.trim().isEmpty()) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Missing required 'kid' in JWT header");
        }

        // 2. Select matching JWKS public key (fail-closed if JWKS unavailable or kid unknown)
        if (jwksClient == null) {
            if (expectedIssuer != null && !expectedIssuer.trim().isEmpty()) {
                jwksClient = new JwksClient(JwksClient.deriveJwksUrl(expectedIssuer));
            } else {
                throw new IllegalStateException("JwksClient or CAMPX_SUPABASE_JWT_ISSUER must be configured");
            }
        }

        PublicKey publicKey = jwksClient.getPublicKey(kid.trim());
        if (publicKey == null) {
            logger.warn("JWKS could not resolve public key for kid: {}", kid);
            throw new JwtValidationException(401, "GATEWAY_UNKNOWN_KEY_ID", "Unknown or unresolvable key ID: " + kid);
        }

        // 3. Cryptographic signature verification BEFORE trusting any payload claims
        byte[] rawSignature;
        try {
            rawSignature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Malformed JWT signature encoding");
        }

        if (rawSignature.length != 64) {
            logger.warn("Invalid ES256 signature length: {}", rawSignature.length);
            throw new JwtValidationException(401, "GATEWAY_INVALID_TOKEN_SIGNATURE", "Invalid ES256 signature length: " + rawSignature.length);
        }

        byte[] derSignature;
        try {
            derSignature = concatSignatureToDer(rawSignature);
        } catch (Exception e) {
            logger.warn("Failed to convert JWS signature to ASN.1 DER: {}", e.getMessage());
            throw new JwtValidationException(401, "GATEWAY_INVALID_TOKEN_SIGNATURE", "Malformed ES256 signature format");
        }

        String signedContent = parts[0] + "." + parts[1];
        boolean signatureValid = false;
        try {
            Signature verifier = Signature.getInstance(EC_SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signedContent.getBytes(StandardCharsets.UTF_8));
            signatureValid = verifier.verify(derSignature);
        } catch (Exception e) {
            logger.error("Internal cryptographic error during ES256 verification: {}", e.getMessage());
            throw new JwtValidationException(401, "GATEWAY_INVALID_TOKEN_SIGNATURE", "Cryptographic verification error");
        }

        if (!signatureValid) {
            logger.warn("ES256 signature verification failed for incoming request");
            throw new JwtValidationException(401, "GATEWAY_INVALID_TOKEN_SIGNATURE", "Cryptographic verification of JWT signature failed");
        }

        // 4. Decode and validate payload
        byte[] payloadBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Malformed JWT payload encoding");
        }
        String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);

        // 5. Validate Issuer (iss) - strictly required
        if (expectedIssuer == null || expectedIssuer.trim().isEmpty()) {
            throw new IllegalStateException("CAMPX_SUPABASE_JWT_ISSUER is required when Gateway security is enabled. "
                    + "Set environment variable CAMPX_SUPABASE_JWT_ISSUER or JVM system property campx.supabase.jwt.issuer.");
        }
        String iss = extractJsonString(payloadJson, "iss");
        if (iss == null || !expectedIssuer.trim().equals(iss.trim())) {
            logger.warn("JWT issuer mismatch: expected '{}' but received '{}'", expectedIssuer, iss);
            throw new JwtValidationException(401, "GATEWAY_INVALID_TOKEN_ISSUER", "JWT issuer does not match expected Supabase project issuer");
        }

        // 6. Validate Audience (aud)
        String aud = extractJsonString(payloadJson, "aud");
        if (aud == null || !REQUIRED_AUDIENCE.equals(aud.trim())) {
            logger.warn("JWT audience mismatch: expected '{}' but received '{}'", REQUIRED_AUDIENCE, aud);
            throw new JwtValidationException(401, "GATEWAY_INVALID_TOKEN_AUDIENCE", "JWT audience must be 'authenticated'");
        }

        // 7. Validate Expiration (exp)
        Long exp = extractJsonLong(payloadJson, "exp");
        if (exp == null) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Missing required 'exp' claim in JWT");
        }
        long nowSeconds = Instant.now().getEpochSecond();
        if (nowSeconds >= exp) {
            logger.warn("Expired JWT presented: exp={}, now={}", exp, nowSeconds);
            throw new JwtValidationException(401, "GATEWAY_EXPIRED_TOKEN", "JWT has expired");
        }

        // 8. Validate Not Before (nbf) when present
        Long nbf = extractJsonLong(payloadJson, "nbf");
        if (nbf != null && nowSeconds < nbf) {
            logger.warn("Premature JWT presented: nbf={}, now={}", nbf, nowSeconds);
            throw new JwtValidationException(401, "GATEWAY_PREMATURE_TOKEN", "JWT is not yet active (nbf)");
        }

        // 9. Extract and validate user ID (sub)
        String sub = extractJsonString(payloadJson, "sub");
        if (sub == null || sub.trim().isEmpty()) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Missing required 'sub' claim in JWT");
        }
        try {
            UUID.fromString(sub.trim());
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Invalid UUID format for 'sub' claim");
        }

        // 10. Extract and validate tenant ID from app_metadata.tenant_id
        String tenantId = extractNestedField(payloadJson, "app_metadata", "tenant_id");
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = extractJsonString(payloadJson, "tenant_id");
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Missing required 'tenant_id' in app_metadata");
        }
        try {
            UUID.fromString(tenantId.trim());
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(401, "GATEWAY_MALFORMED_JWT", "Invalid UUID format for 'tenant_id'");
        }

        // 11. Extract role (defaults to 'authenticated')
        String role = extractJsonString(payloadJson, "role");
        if (role == null || role.trim().isEmpty()) {
            role = "authenticated";
        }

        return new VerifiedClaims(sub.trim(), tenantId.trim(), role.trim());
    }

    /**
     * Backward-compatibility convenience method delegating to ES256/JWKS validation.
     * Legacy secrets are completely ignored and never used to validate HS256.
     */
    public static VerifiedClaims validate(String authHeader, String expectedIssuer) {
        if (expectedIssuer == null || expectedIssuer.trim().isEmpty()) {
            throw new IllegalStateException("CAMPX_SUPABASE_JWT_ISSUER is required when Gateway security is enabled. "
                    + "Set environment variable CAMPX_SUPABASE_JWT_ISSUER or JVM system property campx.supabase.jwt.issuer.");
        }
        return validate(authHeader, new JwksClient(JwksClient.deriveJwksUrl(expectedIssuer)), expectedIssuer);
    }

    /**
     * Backward-compatibility method signature. Ignores legacy secret parameter and executes ES256/JWKS validation.
     */
    public static VerifiedClaims validate(String authHeader, String ignoredSecret, String expectedIssuer) {
        return validate(authHeader, expectedIssuer);
    }

    // --- Cryptographic Conversions (IEEE P1363 raw 64-byte R||S <-> ASN.1 DER) ---

    /**
     * Converts a 64-byte concatenated IEEE P1363 ES256 signature (32 bytes R + 32 bytes S)
     * into an ASN.1 DER SEQUENCE expected by Java's {@code SHA256withECDSA} verifier.
     */
    public static byte[] concatSignatureToDer(byte[] jwsSignature) throws Exception {
        if (jwsSignature == null || jwsSignature.length != 64) {
            throw new IllegalArgumentException("Invalid ES256 signature length: " + (jwsSignature != null ? jwsSignature.length : 0));
        }
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(jwsSignature, 0, r, 0, 32);
        System.arraycopy(jwsSignature, 32, s, 0, 32);

        BigInteger rInt = new BigInteger(1, r);
        BigInteger sInt = new BigInteger(1, s);

        byte[] rBytes = rInt.toByteArray();
        byte[] sBytes = sInt.toByteArray();

        int len = 2 + rBytes.length + 2 + sBytes.length;
        byte[] der = new byte[2 + len];
        der[0] = 0x30; // SEQUENCE
        der[1] = (byte) len;
        der[2] = 0x02; // INTEGER
        der[3] = (byte) rBytes.length;
        System.arraycopy(rBytes, 0, der, 4, rBytes.length);
        int sOffset = 4 + rBytes.length;
        der[sOffset] = 0x02; // INTEGER
        der[sOffset + 1] = (byte) sBytes.length;
        System.arraycopy(sBytes, 0, der, sOffset + 2, sBytes.length);

        return der;
    }

    /**
     * Converts an ASN.1 DER SEQUENCE signature into a 64-byte concatenated IEEE P1363 signature.
     */
    public static byte[] derToConcatSignature(byte[] derSignature) throws Exception {
        int offset = 0;
        if (derSignature[offset++] != 0x30) throw new IllegalArgumentException("Not a DER sequence");
        int seqLen = derSignature[offset++] & 0xFF;
        if ((seqLen & 0x80) != 0) {
            int numOctets = seqLen & 0x7F;
            offset += numOctets;
        }
        if (derSignature[offset++] != 0x02) throw new IllegalArgumentException("Expected integer for R");
        int rLen = derSignature[offset++] & 0xFF;
        byte[] rBytes = new byte[rLen];
        System.arraycopy(derSignature, offset, rBytes, 0, rLen);
        offset += rLen;

        if (derSignature[offset++] != 0x02) throw new IllegalArgumentException("Expected integer for S");
        int sLen = derSignature[offset++] & 0xFF;
        byte[] sBytes = new byte[sLen];
        System.arraycopy(derSignature, offset, sBytes, 0, sLen);

        BigInteger r = new BigInteger(1, rBytes);
        BigInteger s = new BigInteger(1, sBytes);

        byte[] rawR = toUnsigned32ByteArray(r);
        byte[] rawS = toUnsigned32ByteArray(s);

        byte[] concat = new byte[64];
        System.arraycopy(rawR, 0, concat, 0, 32);
        System.arraycopy(rawS, 0, concat, 32, 32);
        return concat;
    }

    public static byte[] toUnsigned32ByteArray(BigInteger bi) {
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

    // --- Test Helpers for Constructing Signed JWTs Dynamically ---

    /**
     * Helper to create and sign test ES256 Supabase JWTs.
     */
    public static String createSignedJwt(String sub, String tenantId, String role,
                                         String issuer, String audience, long expEpochSeconds,
                                         ECPrivateKey privateKey, String kid) {
        return createSignedJwt(sub, tenantId, role, issuer, audience, expEpochSeconds, null, privateKey, kid);
    }

    /**
     * Helper to create and sign test ES256 Supabase JWTs with optional not-before (nbf) claim.
     */
    public static String createSignedJwt(String sub, String tenantId, String role,
                                         String issuer, String audience, long expEpochSeconds,
                                         Long nbfEpochSeconds,
                                         ECPrivateKey privateKey, String kid) {
        String headerJson = "{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        StringBuilder payload = new StringBuilder("{");
        if (sub != null) payload.append("\"sub\":\"").append(sub).append("\",");
        if (issuer != null) payload.append("\"iss\":\"").append(issuer).append("\",");
        if (audience != null) payload.append("\"aud\":\"").append(audience).append("\",");
        payload.append("\"exp\":").append(expEpochSeconds).append(",");
        if (nbfEpochSeconds != null) {
            payload.append("\"nbf\":").append(nbfEpochSeconds).append(",");
        }
        payload.append("\"role\":\"").append(role != null ? role : "authenticated").append("\"");
        if (tenantId != null) {
            payload.append(",\"app_metadata\":{\"tenant_id\":\"").append(tenantId).append("\"}");
        }
        payload.append("}");

        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String content = headerB64 + "." + payloadB64;

        try {
            Signature signer = Signature.getInstance(EC_SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(content.getBytes(StandardCharsets.UTF_8));
            byte[] derSig = signer.sign();
            byte[] rawSig = derToConcatSignature(derSig);
            String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSig);
            return content + "." + sigB64;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test ES256 signed JWT", e);
        }
    }

    /**
     * Helper to create test HS256 tokens for negative testing.
     */
    public static String createHs256Jwt(String sub, String tenantId, String role,
                                        String issuer, String audience, long expEpochSeconds, String secret) {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = "{"
                + "\"sub\":\"" + sub + "\","
                + "\"iss\":\"" + issuer + "\","
                + "\"aud\":\"" + audience + "\","
                + "\"exp\":" + expEpochSeconds + ","
                + "\"role\":\"" + (role != null ? role : "authenticated") + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"}"
                + "}";

        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String content = headerB64 + "." + payloadB64;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sigBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
            return content + "." + sigB64;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test HS256 JWT", e);
        }
    }

    /**
     * Helper to create unsigned test tokens (alg: none) for negative testing.
     */
    public static String createUnsignedJwt(String sub, String tenantId, String role,
                                           String issuer, String audience, long expEpochSeconds) {
        String headerJson = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payloadJson = "{"
                + "\"sub\":\"" + sub + "\","
                + "\"iss\":\"" + issuer + "\","
                + "\"aud\":\"" + audience + "\","
                + "\"exp\":" + expEpochSeconds + ","
                + "\"role\":\"" + (role != null ? role : "authenticated") + "\","
                + "\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"}"
                + "}";

        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return headerB64 + "." + payloadB64 + ".";
    }

    // --- JSON Extraction Helpers ---

    private static String extractJsonString(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        Pattern p = Pattern.compile("(?i)\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private static Long extractJsonLong(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        Pattern p = Pattern.compile("(?i)\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String extractNestedField(String json, String parentObjKey, String childKey) {
        if (json == null || json.isEmpty()) return null;
        Pattern parentPattern = Pattern.compile("(?i)\"" + Pattern.quote(parentObjKey) + "\"\\s*:\\s*\\{([^}]*)\\}");
        Matcher parentMatcher = parentPattern.matcher(json);
        if (parentMatcher.find()) {
            String innerJson = parentMatcher.group(1);
            return extractJsonString(innerJson, childKey);
        }
        return null;
    }
}
