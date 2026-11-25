package com.campx.iadm.analytics.service;

/**
 * DEMO ONLY outage switches for failure-table demos (IADM-06-US-009 / US-027).
 * Plain language: simulate audit or source outage without new tooling.
 */
public final class AnalyticsOutageFlags {

    private static volatile boolean auditDisabled;
    private static volatile boolean iadm01Disabled;
    private static volatile boolean iadm04Disabled;
    private static volatile boolean iadm05Disabled;

    private AnalyticsOutageFlags() {
    }

    public static boolean isAuditDisabled() {
        return auditDisabled;
    }

    public static boolean isIadm01Disabled() {
        return iadm01Disabled;
    }

    public static boolean isIadm04Disabled() {
        return iadm04Disabled;
    }

    public static boolean isIadm05Disabled() {
        return iadm05Disabled;
    }

    public static void set(Boolean audit, Boolean src01, Boolean src04, Boolean src05) {
        if (audit != null) {
            auditDisabled = audit.booleanValue();
        }
        if (src01 != null) {
            iadm01Disabled = src01.booleanValue();
        }
        if (src04 != null) {
            iadm04Disabled = src04.booleanValue();
        }
        if (src05 != null) {
            iadm05Disabled = src05.booleanValue();
        }
    }

    public static java.util.Map<String, Object> snapshot() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
        m.put("auditDisabled", Boolean.valueOf(auditDisabled));
        m.put("iadm01Disabled", Boolean.valueOf(iadm01Disabled));
        m.put("iadm04Disabled", Boolean.valueOf(iadm04Disabled));
        m.put("iadm05Disabled", Boolean.valueOf(iadm05Disabled));
        m.put("label", "DEMO ONLY");
        return m;
    }
}
