package com.campx.iadm.security.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DEMO ONLY outage switches for IADM-07 failure demos (US-025 / intake simulator).
 * Plain language: simulate audit or simulator outage without new tooling.
 * Notifications disabled never blocks workflows.
 */
public final class SecurityOutageFlags {

    private static volatile boolean auditDisabled = envTrue("IADM07_AUDIT_DISABLED");
    private static volatile boolean simulatorDisabled = envTrue("IADM07_SIMULATOR_DISABLED");
    private static volatile boolean notificationsDisabled = envTrue("IADM07_NOTIFICATIONS_DISABLED");

    private SecurityOutageFlags() {
    }

    public static boolean isAuditDisabled() {
        return auditDisabled;
    }

    public static boolean isSimulatorDisabled() {
        return simulatorDisabled;
    }

    /** When true, shared notifications are skipped; alert workflows still succeed. */
    public static boolean isNotificationsDisabled() {
        return notificationsDisabled;
    }

    public static synchronized void set(Boolean audit, Boolean simulator, Boolean notifications) {
        if (audit != null) {
            auditDisabled = audit.booleanValue();
        }
        if (simulator != null) {
            simulatorDisabled = simulator.booleanValue();
        }
        if (notifications != null) {
            notificationsDisabled = notifications.booleanValue();
        }
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("auditDisabled", Boolean.valueOf(auditDisabled));
        m.put("simulatorDisabled", Boolean.valueOf(simulatorDisabled));
        m.put("notificationsDisabled", Boolean.valueOf(notificationsDisabled));
        m.put("label", "DEMO ONLY");
        return m;
    }

    private static boolean envTrue(String name) {
        String v = System.getenv(name);
        return v != null && ("1".equals(v) || "true".equalsIgnoreCase(v));
    }
}