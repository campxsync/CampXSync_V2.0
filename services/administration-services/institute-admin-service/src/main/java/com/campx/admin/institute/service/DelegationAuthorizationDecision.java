package com.campx.admin.institute.service;

/**
 * Explicit Path C authorization decision for STM approve/reject delegation checks (M1).
 */
public final class DelegationAuthorizationDecision {

    private final boolean allowed;
    private final String reason;
    private final String permissionKey;
    private final String subjectUserId;
    private final String tenantId;

    public DelegationAuthorizationDecision(boolean allowed, String reason, String permissionKey,
                                           String subjectUserId, String tenantId) {
        this.allowed = allowed;
        this.reason = reason == null ? "" : reason;
        this.permissionKey = permissionKey == null ? "" : permissionKey;
        this.subjectUserId = subjectUserId == null ? "" : subjectUserId;
        this.tenantId = tenantId == null ? "" : tenantId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }

    public String getPermissionKey() {
        return permissionKey;
    }

    public String getSubjectUserId() {
        return subjectUserId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String toJson() {
        return "{"
                + "\"allowed\":" + allowed + ","
                + "\"reason\":\"" + escape(reason) + "\","
                + "\"permissionKey\":\"" + escape(permissionKey) + "\","
                + "\"subjectUserId\":\"" + escape(subjectUserId) + "\","
                + "\"tenantId\":\"" + escape(tenantId) + "\""
                + "}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
