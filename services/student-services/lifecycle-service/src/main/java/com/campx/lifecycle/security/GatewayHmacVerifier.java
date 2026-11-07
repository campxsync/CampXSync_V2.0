package com.campx.lifecycle.security;

import com.campx.lifecycle.LifecycleException;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Verifies the gateway HMAC on a lifecycle request when a shared secret is configured.
 * A missing secret skips verification so local development can run without the gateway secret.
 * Signature material is never written to the log.
 */
public class GatewayHmacVerifier {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayHmacVerifier.class);
    private static final long MAX_SKEW_SECONDS = 300;
    private final String secret;

    public GatewayHmacVerifier(String secret) {
        this.secret = secret;
    }

    public void verify(String method, String path, String timestamp, String signature, String body) {
        if (secret == null || secret.trim().isEmpty()) {
            return;
        }
        if (timestamp == null || signature == null) {
            logger.warn("Lifecycle gateway signature headers are missing");
            throw new LifecycleException(401, "GATEWAY_SIGNATURE_MISSING", "Missing required gateway signature headers");
        }
        long epoch;
        try {
            epoch = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            logger.warn("Lifecycle gateway timestamp is not a number", e);
            throw new LifecycleException(401, "GATEWAY_TIMESTAMP_INVALID", "Invalid gateway timestamp format");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - epoch) > MAX_SKEW_SECONDS) {
            logger.warn("Lifecycle gateway signature is outside the allowed clock skew");
            throw new LifecycleException(401, "GATEWAY_SIGNATURE_EXPIRED", "Gateway signature expired or clock skew exceeded");
        }
        String canonical = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + (body != null ? body : "");
        String calculated = calculateHmac(canonical, secret);
        if (!MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            logger.warn("Lifecycle gateway HMAC signature did not match");
            throw new LifecycleException(401, "GATEWAY_SIGNATURE_INVALID", "Gateway HMAC signature verification failed");
        }
    }

    public static String calculateHmac(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Lifecycle HMAC calculation failed", e);
            throw new LifecycleException(500, "GATEWAY_SIGNATURE_ERROR", "Failed to calculate HMAC signature");
        }
    }
}
