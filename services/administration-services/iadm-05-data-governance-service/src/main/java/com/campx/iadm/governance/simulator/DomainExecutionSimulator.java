package com.campx.iadm.governance.simulator;

import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.service.GovernanceOutageFlags;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DEMO ONLY domain execution simulator (IADM-05-US-029).
 * Plain language: pretends to be two domain services calling the real contract shape.
 * Touches no real domain data. Disable with IADM05_SIMULATOR_DISABLED or outage flag.
 */
public class DomainExecutionSimulator {

    public static final String LABEL = "DEMO ONLY — not a real domain service";
    public static final String PROVIDER_NAME = "IADM05_DEV_DOMAIN_SIMULATOR";

    /**
     * Simulate domain outcome. Domain codes ending with _FAIL → REJECTED;
     * ending with _PENDING → PENDING/UNAVAILABLE; else COMPLETED.
     */
    public Map<String, Object> execute(String requestType, String domainCode,
                                       UUID correlationId, Map<String, Object> requestMeta) {
        if (GovernanceOutageFlags.isSimulatorDisabled()) {
            throw new InstituteAdminException(503, "IADM05_SIMULATOR_DISABLED",
                    "DEMO ONLY simulator is disabled.");
        }
        String code = domainCode == null ? "" : domainCode.trim().toUpperCase();
        String outcome;
        String reason = null;
        if (code.endsWith("_FAIL") || code.endsWith("_REJECT")) {
            outcome = "REJECTED";
            reason = "DEMO_DOMAIN_REJECT";
        } else if (code.endsWith("_PENDING") || code.endsWith("_UNAVAILABLE")) {
            outcome = code.endsWith("_UNAVAILABLE") ? "UNAVAILABLE" : "PENDING";
            reason = "DEMO_DOMAIN_PENDING";
        } else if ("DEMO_DOMAIN_B".equals(code) && "ERASURE".equals(requestType)) {
            // Default erasure demo: A completes, B stays pending (US-008)
            outcome = "PENDING";
            reason = "DEMO_PARTIAL";
        } else {
            outcome = "COMPLETED";
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("labelled", LABEL);
        out.put("provider", PROVIDER_NAME);
        out.put("domainCode", domainCode);
        out.put("outcome", outcome);
        out.put("reasonCode", reason);
        out.put("receiptRef", "demo-receipt-" + (correlationId == null ? "none" : correlationId.toString().substring(0, 8)));
        out.put("correlationId", correlationId == null ? null : correlationId.toString());
        out.put("touchesRealDomainData", Boolean.FALSE);
        return out;
    }
}
