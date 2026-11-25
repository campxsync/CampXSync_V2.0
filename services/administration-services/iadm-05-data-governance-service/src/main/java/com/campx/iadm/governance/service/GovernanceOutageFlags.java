package com.campx.iadm.governance.service;

/**
 * DEV outage flags for IADM-05 (US-025 / US-031).
 * Plain language: turns audit or the DEMO ONLY simulator off so fail-closed behaviour can be proven.
 */
public final class GovernanceOutageFlags {

    private static volatile boolean auditDisabled = envTrue("IADM05_AUDIT_DISABLED");
    private static volatile boolean simulatorDisabled = envTrue("IADM05_SIMULATOR_DISABLED");

    private GovernanceOutageFlags() {
    }

    public static boolean isAuditDisabled() {
        return auditDisabled;
    }

    public static boolean isSimulatorDisabled() {
        return simulatorDisabled;
    }

    public static synchronized void set(Boolean audit, Boolean simulator) {
        if (audit != null) {
            auditDisabled = audit.booleanValue();
        }
        if (simulator != null) {
            simulatorDisabled = simulator.booleanValue();
        }
    }

    private static boolean envTrue(String name) {
        String v = System.getenv(name);
        return v != null && ("1".equals(v) || "true".equalsIgnoreCase(v));
    }
}
