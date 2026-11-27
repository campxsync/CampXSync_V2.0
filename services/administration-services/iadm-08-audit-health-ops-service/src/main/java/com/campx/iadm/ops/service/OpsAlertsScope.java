package com.campx.iadm.ops.service;

import com.campx.iadm.ops.exception.InstituteAdminException;

import java.util.UUID;

/**
 * Alerts institute-scope binding (IADM-08 Fix Part A).
 * Plain language: Institution Admin always sees own institute from trusted claims;
 * only Super Admin, System Admin, and Auditor may pick another institute.
 */
public final class OpsAlertsScope {

    private OpsAlertsScope() {
    }

    /**
     * @param canSelectInstitute true for R01 / R02 / R28; false for R03
     * @param trustedTenantId    institute from verified claims (never from client alone)
     * @param clientTenantId     optional query parameter (ignored/refused for R03 when foreign)
     * @return scoped tenant id to forward, or null when platform-wide (no filter)
     */
    public static String resolve(boolean canSelectInstitute, UUID trustedTenantId, String clientTenantId) {
        if (canSelectInstitute) {
            if (clientTenantId == null || clientTenantId.trim().isEmpty()) {
                return null;
            }
            return parseUuid(clientTenantId.trim()).toString();
        }
        if (trustedTenantId == null) {
            throw new InstituteAdminException(403, "IADM08_SCOPE_REQUIRED",
                    "Institute scope could not be verified.");
        }
        if (clientTenantId != null && !clientTenantId.trim().isEmpty()) {
            UUID requested = parseUuid(clientTenantId.trim());
            if (!trustedTenantId.equals(requested)) {
                throw new InstituteAdminException(403, "IADM08_SCOPE_DENIED",
                        "Institute Admin may only view alerts for their own institute.");
            }
        }
        return trustedTenantId.toString();
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new InstituteAdminException(400, "IADM08_BAD_REQUEST", "tenantId must be a UUID");
        }
    }
}
