package com.campx.lifecycle.security;

import com.campx.lifecycle.LifecycleException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;
import com.campx.logger.security.GatewayHmacSecurityConfig;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Lifecycle Gateway HMAC verifier aligned with {@link GatewayHmacProtocol}.
 * Fail-closed when internal auth is enabled.
 */
public class GatewayHmacVerifier {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayHmacVerifier.class);

    private final boolean authEnabled;
    private final String secret;
    private final long replayWindowSeconds;

    public GatewayHmacVerifier() {
        this.authEnabled = GatewayHmacSecurityConfig.isInternalAuthEnabled();
        this.secret = this.authEnabled ? GatewayHmacSecurityConfig.getInternalSecret() : null;
        this.replayWindowSeconds = GatewayHmacSecurityConfig.getReplayWindowSeconds();
    }

    public GatewayHmacVerifier(String secret) {
        this.authEnabled = GatewayHmacSecurityConfig.isInternalAuthEnabled();
        if (this.authEnabled) {
            String resolved = secret;
            if (resolved == null || resolved.trim().isEmpty()) {
                resolved = System.getenv("CAMPX_INTERNAL_SECRET");
            }
            if (resolved == null || resolved.trim().isEmpty()) {
                resolved = GatewayHmacSecurityConfig.getInternalSecret();
            }
            this.secret = resolved;
        } else {
            this.secret = secret;
        }
        this.replayWindowSeconds = GatewayHmacSecurityConfig.getReplayWindowSeconds();
    }

    public void verify(String method, String path, String timestamp, String signature, String body) {
        verify(method, path, timestamp, signature, null, null, body);
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

    public void verify(String method, String path, String timestamp, String signature,
                       String userId, String tenantId, String body) {
        if (!authEnabled) {
            return;
        }
        if (timestamp == null || timestamp.trim().isEmpty() || signature == null || signature.trim().isEmpty()) {
            logger.warn("Lifecycle gateway signature headers are missing");
            throw new LifecycleException(401, "GATEWAY_MISSING_INTERNAL_AUTH", "Missing required gateway signature headers");
        }
        long epoch;
        try {
            epoch = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new LifecycleException(401, "GATEWAY_TIMESTAMP_INVALID", "Invalid gateway timestamp format");
        }
        if (!GatewayHmacProtocol.isWithinReplayWindow(epoch, replayWindowSeconds)) {
            throw new LifecycleException(401, "GATEWAY_EXPIRED_INTERNAL_AUTH", "Gateway signature expired or clock skew exceeded");
        }
        if (secret == null || secret.trim().isEmpty()) {
            throw new LifecycleException(500, "GATEWAY_INTERNAL_AUTH_MISCONFIGURED", "Internal HMAC secret is not configured");
        }
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
        String bodySha = GatewayHmacProtocol.sha256Hex(bodyBytes);
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                timestamp.trim(), method, path, userId, tenantId, bodySha);
        String expected = GatewayHmacProtocol.calculateHmac(canonical, secret);
        if (!GatewayHmacProtocol.constantTimeEquals(expected, signature)) {
            logger.warn("Lifecycle gateway HMAC signature did not match");
            throw new LifecycleException(401, "GATEWAY_INVALID_INTERNAL_AUTH", "Gateway HMAC signature verification failed");
        }
    }

    private static String first(Map<String, String> headers, String a, String b) {
        String v = headers.get(a);
        if (v == null || v.trim().isEmpty()) {
            v = headers.get(b);
        }
        return v;
    }
}
