package com.campx.iadm.billing;

import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.security.TrustedInstituteScope;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * API-contract unit tests: platform may pass institute; others forced to claims; foreign refused.
 */
public class TrustedInstituteScopeTest {

    private static final UUID OWN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    public void platformMayPassForeignInstitute() {
        assertEquals(OTHER, TrustedInstituteScope.resolve(true, OWN, OTHER));
        assertEquals(OWN, TrustedInstituteScope.resolve(true, OWN, null));
        assertEquals(OTHER, TrustedInstituteScope.resolve(true, null, OTHER));
    }

    @Test
    public void ownInstituteWorksForNonPlatform() {
        assertEquals(OWN, TrustedInstituteScope.resolve(false, OWN, null));
        assertEquals(OWN, TrustedInstituteScope.resolve(false, OWN, OWN));
    }

    @Test
    public void secondInstituteRefusedForNonPlatform() {
        try {
            TrustedInstituteScope.resolve(false, OWN, OTHER);
            fail("expected 403");
        } catch (InstituteAdminException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("IADM04_CROSS_TENANT_DENIED", ex.getErrorCode());
        }
    }

    @Test
    public void missingClaimsRefusedForNonPlatform() {
        try {
            TrustedInstituteScope.resolve(false, null, OTHER);
            fail("expected 403");
        } catch (InstituteAdminException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("IADM04_SCOPE_REQUIRED", ex.getErrorCode());
        }
    }
}
