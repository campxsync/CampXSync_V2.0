package com.campx.iadm.ops.service;

/**
 * DEMO ONLY runtime switches for IADM-08 source-failure demos (US-014).
 * Plain language: simulate shared-audit or health-source failures without changing other services.
 */
public final class OpsOutageFlags {

    private static volatile boolean auditDisabled;
    private static volatile boolean healthDisabled;
    private static volatile boolean iadm05Disabled;
    private static volatile boolean iadm07Disabled;

    private OpsOutageFlags() {
    }

    public static boolean isAuditDisabled() {
        return auditDisabled;
    }

    public static boolean isHealthDisabled() {
        return healthDisabled;
    }

    public static boolean isIadm05Disabled() {
        return iadm05Disabled;
    }

    public static boolean isIadm07Disabled() {
        return iadm07Disabled;
    }

    public static void setAuditDisabled(boolean v) {
        auditDisabled = v;
    }

    public static void setHealthDisabled(boolean v) {
        healthDisabled = v;
    }

    public static void setIadm05Disabled(boolean v) {
        iadm05Disabled = v;
    }

    public static void setIadm07Disabled(boolean v) {
        iadm07Disabled = v;
    }

    public static void reset() {
        auditDisabled = false;
        healthDisabled = false;
        iadm05Disabled = false;
        iadm07Disabled = false;
    }
}
