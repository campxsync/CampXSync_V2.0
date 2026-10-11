package com.campx.logger.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Platform-wide canonical protocol specification for Gateway -> Downstream
 * HMAC-SHA256 request authentication and integrity verification.
 * <p>
 * Ensures that identity headers ({@code X-User-Id}, {@code X-Tenant-Id}, {@code X-User-Role})
 * can only be established downstream if cryptographically verified and signed by the API Gateway.
 * </p>
 */
public final class GatewayHmacProtocol {

    public static final String HEADER_TIMESTAMP = "X-Gateway-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Gateway-Signature";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /**
     * Unconditional set of client-supplied identity and gateway headers that MUST
     * be stripped by the API Gateway before forwarding to downstream microservices.
     */
    public static final Set<String> STRIPPED_INBOUND_HEADERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "x-user-id",
            "x-tenant-id",
            "x-user-role",
            "x-gateway-timestamp",
            "x-gateway-signature"
    )));

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private GatewayHmacProtocol() {}

    /**
     * Computes the SHA-256 hash of the exact request body bytes.
     * Returns a 64-character lowercase hex string.
     * For empty or null body bytes, returns the SHA-256 hash of the empty byte sequence:
     * {@code e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855}.
     *
     * @param body request body bytes, or {@code null}
     * @return lowercase hex-encoded SHA-256 hash
     */
    public static String sha256Hex(byte[] body) {
        byte[] input = (body != null) ? body : new byte[0];
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] digest = md.digest(input);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available in JVM", e);
        }
    }

    /**
     * Builds the canonical payload string to be signed or verified:
     * <pre>
     * timestamp + ":" + method + ":" + path + ":" + userId + ":" + tenantId + ":" + bodySHA256
     * </pre>
     *
     * @param timestamp  numeric epoch timestamp string
     * @param method     HTTP method (e.g. GET, POST, PUT, DELETE, PATCH)
     * @param path       HTTP request URI path (e.g. /api/v1/admin/users)
     * @param userId     UUID of the authenticated user
     * @param tenantId   UUID of the authenticated tenant
     * @param bodySha256 lowercase SHA-256 hex string of the request body
     * @return canonical UTF-8 signature payload string
     */
    public static String buildCanonicalPayload(String timestamp, String method, String path,
                                              String userId, String tenantId, String bodySha256) {
        String safeTs = (timestamp != null) ? timestamp.trim() : "";
        String safeMethod = (method != null) ? method.trim().toUpperCase(Locale.ROOT) : "";
        String safePath = (path != null) ? path.trim() : "";
        String safeUser = (userId != null) ? userId.trim() : "";
        String safeTenant = (tenantId != null) ? tenantId.trim() : "";
        String safeBodyHash = (bodySha256 != null && !bodySha256.trim().isEmpty())
                ? bodySha256.trim().toLowerCase(Locale.ROOT)
                : sha256Hex(new byte[0]);

        return safeTs + ":" +
                safeMethod + ":" +
                safePath + ":" +
                safeUser + ":" +
                safeTenant + ":" +
                safeBodyHash;
    }

    /**
     * Calculates the HMAC-SHA256 hex string over the canonical payload using the provided secret.
     *
     * @param canonicalPayload canonical payload string
     * @param secret           internal shared secret (must not be null or empty)
     * @return 64-character lowercase hex signature
     */
    public static String calculateHmac(String canonicalPayload, String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("Internal HMAC secret cannot be null or empty");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Performs a constant-time comparison between two signature strings to prevent timing attacks.
     *
     * @param expectedSignature signature computed by verifier
     * @param actualSignature   signature supplied in request header
     * @return {@code true} if signatures match exactly
     */
    public static boolean constantTimeEquals(String expectedSignature, String actualSignature) {
        if (expectedSignature == null || actualSignature == null) {
            return false;
        }
        byte[] expectedBytes = expectedSignature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actualSignature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * Verifies that the given epoch timestamp is within the acceptable replay window.
     * Supports both epoch milliseconds (13-digit) and epoch seconds (10-digit).
     *
     * @param timestamp     epoch timestamp from header
     * @param windowSeconds maximum allowed time skew in seconds
     * @return {@code true} if timestamp is within {@code +/- windowSeconds} of current system time
     */
    public static boolean isWithinReplayWindow(long timestamp, long windowSeconds) {
        long nowMillis = System.currentTimeMillis();
        long tsMillis = (timestamp > 100_000_000_000L) ? timestamp : (timestamp * 1000L);
        long allowedDiffMillis = windowSeconds * 1000L;
        return Math.abs(nowMillis - tsMillis) <= allowedDiffMillis;
    }

    /**
     * Converts a byte array to lowercase hexadecimal string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
