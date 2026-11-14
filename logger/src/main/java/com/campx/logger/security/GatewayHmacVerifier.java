package com.campx.logger.security;

import com.sun.net.httpserver.HttpExchange;

/**
 * Shared Gateway → downstream HMAC verifier (GatewayHmacProtocol).
 * Fail-closed when {@code campx.internal.auth.enabled=true}.
 */
public class GatewayHmacVerifier {

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

    public static final class VerificationResult {
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

        public boolean isSuccess() { return success; }
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getUserRole() { return userRole; }
    }

    public VerificationResult verify(HttpExchange exchange, String method, String path, byte[] bodyBytes) {
        String userId = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_USER_ID);
        String tenantId = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_TENANT_ID);
        String userRole = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_USER_ROLE);

        if (!authEnabled) {
            return VerificationResult.success(userId, tenantId, userRole);
        }

        String tsHeader = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_TIMESTAMP);
        if (tsHeader == null || tsHeader.trim().isEmpty()) {
            return VerificationResult.failure(401, "Unauthorized", "GATEWAY_MISSING_INTERNAL_AUTH",
                    "Missing required X-Gateway-Timestamp header");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(tsHeader.trim());
        } catch (NumberFormatException e) {
            return VerificationResult.failure(401, "Unauthorized", "GATEWAY_MISSING_INTERNAL_AUTH",
                    "Invalid non-numeric X-Gateway-Timestamp header");
        }

        if (!GatewayHmacProtocol.isWithinReplayWindow(timestamp, replayWindowSeconds)) {
            return VerificationResult.failure(401, "Unauthorized", "GATEWAY_EXPIRED_INTERNAL_AUTH",
                    "Gateway request timestamp is outside acceptable replay window of " + replayWindowSeconds + " seconds");
        }

        String signature = exchange.getRequestHeaders().getFirst(GatewayHmacProtocol.HEADER_SIGNATURE);
        if (signature == null || signature.trim().isEmpty()) {
            return VerificationResult.failure(401, "Unauthorized", "GATEWAY_MISSING_INTERNAL_AUTH",
                    "Missing required X-Gateway-Signature header");
        }

        if (internalSecret == null || internalSecret.trim().isEmpty()) {
            return VerificationResult.failure(500, "Internal Server Error", "GATEWAY_INTERNAL_AUTH_MISCONFIGURED",
                    "Internal HMAC secret is not configured");
        }

        String bodySha256 = GatewayHmacProtocol.sha256Hex(bodyBytes);
        String canonical = GatewayHmacProtocol.buildCanonicalPayload(
                tsHeader.trim(), method, path, userId, tenantId, bodySha256);
        String expected = GatewayHmacProtocol.calculateHmac(canonical, internalSecret);
        if (!GatewayHmacProtocol.constantTimeEquals(expected, signature)) {
            return VerificationResult.failure(401, "Unauthorized", "GATEWAY_INVALID_INTERNAL_AUTH",
                    "Gateway HMAC signature verification failed");
        }

        return VerificationResult.success(userId, tenantId, userRole);
    }
}
