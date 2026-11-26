package com.campx.iadm.security.simulator;

import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.service.SecurityOutageFlags;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DEMO ONLY signal and enforcement intake payloads (IADM-07-US-027).
 * Plain language: builds sample intake bodies for headed demos; not a real scanner.
 */
public final class SignalIntakeSimulator {

    public static final String LABEL = "DEMO ONLY — not a real security scanner";

    private SignalIntakeSimulator() {
    }

    public static void assertEnabled() {
        if (SecurityOutageFlags.isSimulatorDisabled()) {
            throw new InstituteAdminException(503, "IADM07_SIMULATOR_DISABLED",
                    "DEMO ONLY signal simulator is disabled.");
        }
    }

    public static Map<String, Object> newSignalPayload(String correlationId, UUID instituteId) {
        assertEnabled();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("label", LABEL);
        m.put("correlationId", correlationId == null ? "SEC-SIM-NEW-" + UUID.randomUUID().toString().substring(0, 8) : correlationId);
        m.put("source", "IADM07_DEV_SIGNAL_SIMULATOR");
        m.put("summary", "DEMO ONLY simulated security signal");
        m.put("severity", "MEDIUM");
        m.put("sourceDemoOnly", Boolean.TRUE);
        m.put("instituteId", instituteId == null ? null : instituteId.toString());
        return m;
    }

    public static Map<String, Object> duplicateSignalPayload(String existingCorrelationId, UUID instituteId) {
        Map<String, Object> m = newSignalPayload(existingCorrelationId, instituteId);
        m.put("scenario", "duplicate");
        return m;
    }

    public static Map<String, Object> conflictSignalPayload(String correlationId, UUID instituteId) {
        Map<String, Object> m = newSignalPayload(correlationId, instituteId);
        m.put("scenario", "conflict");
        m.put("summary", "DEMO ONLY conflicting signal metadata");
        return m;
    }

    public static Map<String, Object> enforcementPayload(String correlationId, UUID instituteId, UUID controlId) {
        assertEnabled();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("label", LABEL);
        m.put("correlationId", correlationId == null ? "SEC-ENF-" + UUID.randomUUID().toString().substring(0, 8) : correlationId);
        m.put("serviceName", "IADM07_DEV_ENFORCEMENT_SIMULATOR");
        m.put("reportedStatus", "REPORTED_DEMO");
        m.put("reportSource", "SIMULATOR");
        m.put("demoLabel", "DEMO ONLY");
        m.put("instituteId", instituteId == null ? null : instituteId.toString());
        m.put("controlId", controlId == null ? null : controlId.toString());
        return m;
    }
}