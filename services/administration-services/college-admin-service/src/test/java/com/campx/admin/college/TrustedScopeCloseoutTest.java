package com.campx.admin.college;

import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.repository.CollegeResolver;
import com.campx.admin.college.service.CollegeAdminDomainService;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * CADM close-out — trusted college / batch-event tenant helpers.
 * Plain language: own institute works; second institute refused; placeholders overwritten later.
 */
public class TrustedScopeCloseoutTest {

    private static final UUID OWN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    public void collegeMustBelongToTrustedTenant() {
        CollegeResolver.assertCollegeBelongsToTenant(OWN, OWN);
        CollegeResolver.assertCollegeBelongsToTenant(OWN, null);
        try {
            CollegeResolver.assertCollegeBelongsToTenant(OWN, OTHER);
            fail("expected foreign college refusal");
        } catch (CollegeSecurityViolationException ex) {
            assertEquals(true, ex.getMessage().toLowerCase().contains("college"));
        }
    }

    @Test
    public void batchEventForeignUuidRefused() {
        CollegeAdminDomainService.assertBatchEventTenantTrusted(OWN.toString(), OWN.toString());
        CollegeAdminDomainService.assertBatchEventTenantTrusted(OWN.toString(), "TENANT-001");
        try {
            CollegeAdminDomainService.assertBatchEventTenantTrusted(OWN.toString(), OTHER.toString());
            fail("expected cross-tenant denial");
        } catch (CollegeSecurityViolationException ex) {
            assertEquals("CADM_CROSS_TENANT_DENIED", ex.getErrorCode());
        }
    }
}
