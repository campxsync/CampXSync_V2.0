package com.campx.gateway.identity;

/**
 * Result representation for identity resolution attempts.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class IdentityResolutionResult {

    private final boolean success;
    private final ResolvedIdentity identity;
    private final String failureReason;
    private final String message;

    private IdentityResolutionResult(boolean success, ResolvedIdentity identity, String failureReason, String message) {
        this.success = success;
        this.identity = identity;
        this.failureReason = failureReason;
        this.message = message != null ? message : "";
    }

    public static IdentityResolutionResult success(ResolvedIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("ResolvedIdentity cannot be null for successful resolution");
        }
        return new IdentityResolutionResult(true, identity, null, "Identity successfully resolved");
    }

    public static IdentityResolutionResult notFound(String message) {
        return new IdentityResolutionResult(false, null, "no_profile", message != null ? message : "Identity not found");
    }

    public static IdentityResolutionResult ineligible(String reason, String message) {
        return new IdentityResolutionResult(false, null, reason != null ? reason : "ineligible", message != null ? message : "Identity ineligible");
    }

    public boolean isSuccess() {
        return success;
    }

    public ResolvedIdentity getIdentity() {
        return identity;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getMessage() {
        return message;
    }
}
