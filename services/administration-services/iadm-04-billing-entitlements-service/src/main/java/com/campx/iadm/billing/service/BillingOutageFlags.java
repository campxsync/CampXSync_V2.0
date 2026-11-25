package com.campx.iadm.billing.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DEV-only outage switches for US-038 headed demos.
 * Bootstrap from env; toggle at runtime via /dev/outage-flags (no restart).
 */
public final class BillingOutageFlags {

    private static volatile boolean auditDisabled = envTrue("IADM04_AUDIT_DISABLED");
    private static volatile boolean notificationsDisabled = envTrue("IADM04_NOTIFICATIONS_DISABLED");

    private BillingOutageFlags() {
    }

    public static boolean isAuditDisabled() {
        return auditDisabled;
    }

    public static boolean isNotificationsDisabled() {
        return notificationsDisabled;
    }

    public static synchronized void set(Boolean audit, Boolean notifications) {
        if (audit != null) {
            auditDisabled = audit.booleanValue();
        }
        if (notifications != null) {
            notificationsDisabled = notifications.booleanValue();
        }
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("auditDisabled", Boolean.valueOf(auditDisabled));
        out.put("notificationsDisabled", Boolean.valueOf(notificationsDisabled));
        out.put("labelledDevOnly", Boolean.TRUE);
        out.put("note", "When audit is disabled, financial changes fail closed and the payment simulator is not called.");
        return out;
    }

    private static boolean envTrue(String key) {
        String v = System.getenv(key);
        return v != null && ("1".equals(v.trim()) || "true".equalsIgnoreCase(v.trim()));
    }
}
