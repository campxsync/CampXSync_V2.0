package com.campx.admin.college.security;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.security.GatewayHmacProtocol;
import com.campx.logger.security.GatewayHmacSecurityConfig;
import com.sun.net.httpserver.HttpExchange;

import java.util.UUID;

/**
 * Downstream request verifier enforcing platform trust boundary integrity for ADM-02 College Admin Service.
 * <p>
 * Ensures incoming HTTP requests:
 * <ol>
 *   <li>Originate from the authorized API Gateway via HMAC-SHA256 signature verification.</li>
 *   <li>Possess a non-expired timestamp within the replay window (default: 60s).</li>
 *   <li>Have untampered method, path, body, and identity headers ({@code X-User-Id}, {@code X-Tenant-Id}).</li>
 * </ol>
 * </p>
 */
public class GatewayHmacVerifier {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayHmacVerifier.class);

    private final boolean authEnabled;
    private final String internalSecret;
    private final long replayWindowSeconds;

    public GatewayHmacVerifier() {
        this.authEnabled = GatewayHmacSecurityConfig.isInternalAuthEnabled();
        this.internalSecret = this.authEnabled ? GatewayHmacSecurityConfig.getInternalSecret() : null;
        this.replayWindowSeconds = GatewayHmacSecurityConfig.getReplayWindowSeconds();
    }

    public GatewayHmacVerifier(boolean authEnabled, String internalSecret, long replayWindowSeconds) {
        this.authEnabled = authEnabled;
        this.internalSecret = internalSecret;
        this.replayWindowSeconds = replayWindowSeconds;
    }

    /**
     * Verification outcome containing status, error details, or verified identity.
     */
    public static class VerificationResult {
        private final boolean success;
        private final int status;
        private final String error;
        private final String errorCode;
        private final String message;
        private final String userId;
        private final String tenantId;
        private final String userRole;

        private VerificationResult(boolean success, int status, String error, String errorCode, String message,
                                   String userId, String tenantId, String userRole) {
            this.success = success;
            this.status = status;
            this.error = error;
            this.errorCode = errorCode;
            this.message = message;
            this.userId = userId;
            this.tenantId = tenantId;
            this.userRole = userRole;
        }

        public static VerificationResult success(String userId, String tenantId, String userRole) {
            return new VerificationResult(true, 200, null, null, null, userId, tenantId, userRole);
        }

        public static VerificationResult failure(int status, String error, String errorCode, String message) {
            return new VerificationResult(false, status, error, errorCode, message, null, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }

        public String getUserId() {
            return userId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getUserRole() {
            return userRole;
        }
    }

    /**
     * Verifies the authenticity and integrity of an incoming HTTP request.
     *
     * @param exchange  the incoming HTTP exchange
     * @param method    effective HTTP method
     * @param path      request URI path
     * @param bodyBytes exact raw request body bytes
     * @return {@link VerificationResult} indicating success or failure
     */
    public VerificationResult verify(HttpExchange exchange, String method, String path, byte[] bodyBytes) {
        String userId = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_USER_ID);
        String tenantId = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_TENANT_ID);
        String userRole = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_USER_ROLE);

        if (!authEnabled) {
            return VerificationResult.success(userId, tenantId, userRole);
        }

        // 1. Verify X-Gateway-Timestamp exists and is numeric
        String tsHeader = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_TIMESTAMP);
        if (tsHeader == null || tsHeader.trim().isEmpty()) {
            logger.warn("[GatewayHmacVerifier] Missing X-Gateway-Timestamp header for [{}] {}", method, path);
            return VerificationResult.failure(401, "Unauthorized", "ADM02_MISSING_INTERNAL_AUTH",
                    "Missing required X-Gateway-Timestamp header");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(tsHeader.trim());
        } catch (NumberFormatException e) {
            logger.warn("[GatewayHmacVerifier] Non-numeric X-Gateway-Timestamp: {}", tsHeader);
            return VerificationResult.failure(401, "Unauthorized", "ADM02_MISSING_INTERNAL_AUTH",
                    "Invalid non-numeric X-Gateway-Timestamp header");
        }

        // 2. Verify timestamp is within replay window (default 60s)
        if (!GatewayHmacProtocol.isWithinReplayWindow(timestamp, replayWindowSeconds)) {
            logger.warn("[GatewayHmacVerifier] Expired timestamp: {} outside window of {}s", timestamp, replayWindowSeconds);
            return VerificationResult.failure(401, "Unauthorized", "ADM02_EXPIRED_INTERNAL_AUTH",
                    "Gateway request timestamp is outside acceptable replay window of " + replayWindowSeconds + " seconds");
        }

        // 3. Verify X-Gateway-Signature exists
        String sigHeader = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_SIGNATURE);
        if (sigHeader == null || sigHeader.trim().isEmpty()) {
            logger.warn("[GatewayHmacVerifier] Missing X-Gateway-Signature header for [{}] {}", method, path);
            return VerificationResult.failure(401, "Unauthorized", "ADM02_MISSING_INTERNAL_AUTH",
                    "Missing required X-Gateway-Signature header");
        }

        // 4. Validate identity headers are present and valid UUID format
        if (userId == null || userId.trim().isEmpty() || tenantId == null || tenantId.trim().isEmpty()) {
            logger.warn("[GatewayHmacVerifier] Missing identity headers: userId={}, tenantId={}", userId, tenantId);
            return VerificationResult.failure(401, "Unauthorized", "ADM02_MISSING_INTERNAL_AUTH",
                    "Missing required identity headers X-User-Id or X-Tenant-Id");
        }

        try {
            UUID.fromString(userId.trim());
            UUID.fromString(tenantId.trim());
        } catch (IllegalArgumentException e) {
            logger.warn("[GatewayHmacVerifier] Invalid UUID format in identity headers");
            return VerificationResult.failure(401, "Unauthorized", "ADM02_MISSING_INTERNAL_AUTH",
                    "Identity headers must contain valid UUID strings");
        }

        // 5. Recalculate body SHA-256 and reconstruct canonical payload
        String bodySha256 = GatewayHmacProtocol.sha256Hex(bodyBytes);
        String canonicalPayload = GatewayHmacProtocol.buildCanonicalPayload(
                tsHeader.trim(), method, path, userId.trim(), tenantId.trim(), bodySha256);

        // 6. Calculate expected HMAC-SHA256 and perform constant-time verification
        String expectedSignature = GatewayHmacProtocol.calculateHmac(canonicalPayload, internalSecret);
        if (!GatewayHmacProtocol.constantTimeEquals(expectedSignature, sigHeader.trim())) {
            logger.warn("[GatewayHmacVerifier] Signature mismatch for [{}] {}", method, path);
            return VerificationResult.failure(403, "Forbidden", "ADM02_INVALID_INTERNAL_SIGNATURE",
                    "Cryptographic verification of gateway request signature failed");
        }

        logger.debug("[GatewayHmacVerifier] Cryptographic verification passed for user {} in tenant {}", userId, tenantId);
        return VerificationResult.success(userId.trim(), tenantId.trim(), userRole != null ? userRole.trim() : "authenticated");
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public long getReplayWindowSeconds() {
        return replayWindowSeconds;
    }
}
