package com.campx.iadm.billing.security;

import com.campx.iadm.billing.exception.InstituteAdminException;

import java.util.UUID;

/**
 * Trusted institute scope helper (IADM-04 close-out).
 * Plain language: Super Admin (R01) and System Admin (R02) may pass an institute;
 * everyone else must use the institute from verified claims; a different one is refused (403).
 */
public final class TrustedInstituteScope {

    private TrustedInstituteScope() {
    }

    /**
     * @param canSelectInstitute true for R01 / R02 (and intentional SERVICE metering callers)
     * @param trustedFromClaims  institute from authenticated claims (may be null for platform-wide)
     * @param clientRequested    optional client-supplied institute
     * @return institute to use for the operation
     */
    public static UUID resolve(boolean canSelectInstitute, UUID trustedFromClaims, UUID clientRequested) {
        if (canSelectInstitute) {
            return clientRequested != null ? clientRequested : trustedFromClaims;
        }
        if (trustedFromClaims == null) {
            throw new InstituteAdminException(403, "IADM04_SCOPE_REQUIRED",
                    "Institute context could not be verified.");
        }
        if (clientRequested != null && !trustedFromClaims.equals(clientRequested)) {
            throw new InstituteAdminException(403, "IADM04_CROSS_TENANT_DENIED",
                    "Requesting another institute's data is refused.");
        }
        return trustedFromClaims;
    }
}
