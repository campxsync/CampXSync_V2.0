package com.campx.enrollment.security;

import com.campx.enrollment.EnrollmentException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Verifies the gateway HMAC on an enrolment request when a shared secret is configured.
 * Aligns with {@link GatewayHmacProtocol} (epoch ms or seconds, body SHA-256 canonical form).
 * A missing secret skips verification so isolated unit tests can run without the gateway secret.
 * Signature material is never written to the log.
 */
public class GatewayHmacVerifier {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayHmacVerifier.class);
    private static final long DEFAULT_REPLAY_WINDOW_SECONDS = 60L;

    private final String secret;
    private final long replayWindowSeconds;

    public GatewayHmacVerifier(String secret) {
        this(secret, DEFAULT_REPLAY_WINDOW_SECONDS);
    }

    public GatewayHmacVerifier(String secret, long replayWindowSeconds) {
        this.secret = secret;
        this.replayWindowSeconds = replayWindowSeconds > 0 ? replayWindowSeconds : DEFAULT_REPLAY_WINDOW_SECONDS;
    }

    public void verify(String method, String path, Map<String, String> headers, String body) {
        if (headers == null) {
            verify(method, path, null, null, null, null, body);
            return;
        }
        verify(
                method,
                path,
                first(headers, "x-gateway-timestamp", "X-Gateway-Timestamp"),
                first(headers, "x-gateway-signature", "X-Gateway-Signature"),
                first(headers, "x-user-id", "X-User-Id"),
                first(headers, "x-tenant-id", "X-Tenant-Id"),
                body
        );
    }

    /**
     * Legacy overload retained for unit tests that supply timestamp/signature directly.
     */
    public void verify(String method, String path, String timestamp, String signature, String body) {
        verify(method, path, timestamp, signature, null, null, body);
    }

    public void verify(String method, String path, String timestamp, String signature,
                       String userId, String tenantId, String body) {
        if (secret == null || secret.trim().isEmpty()) {
            return;
        }
        if (timestamp == null || timestamp.trim().isEmpty() || signature == null || signature.trim().isEmpty()) {
            logger.warn("Enrolment gateway signature headers are missing");
            throw new EnrollmentException(401, "GATEWAY_SIGNATURE_MISSING", "Missing required gateway signature headers");
        }
        long epoch;
        try {
            epoch = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            logger.warn("Enrolment gateway timestamp is not a number", e);
            throw new EnrollmentException(401, "GATEWAY_TIMESTAMP_INVALID", "Invalid gateway timestamp format");
        }
        if (!GatewayHmacProtocol.isWithinReplayWindow(epoch, replayWindowSeconds)) {
            logger.warn("Enrolment gateway signature is outside the allowed clock skew");
            throw new EnrollmentException(401, "GATEWAY_SIGNATURE_EXPIRED", "Gateway signature expired or clock skew exceeded");
        }
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        String bodySha = GatewayHmacProtocol.sha256Hex(bodyBytes);
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp.trim(), method, path, userId, tenantId, bodySha);
        String expected = GatewayHmacProtocol.calculateHmac(canonical, secret);
        if (!GatewayHmacProtocol.constantTimeEquals(expected, signature)) {
            logger.warn("Enrolment gateway HMAC signature did not match");
            throw new EnrollmentException(401, "GATEWAY_SIGNATURE_INVALID", "Gateway HMAC signature verification failed");
        }
    }

    private static String first(Map<String, String> headers, String a, String b) {
        String v = headers.get(a);
        if (v == null || v.trim().isEmpty()) {
            v = headers.get(b);
        }
        if (v == null || v.trim().isEmpty()) {
            v = headers.get(a.toLowerCase());
        }
        return v;
    }
}
