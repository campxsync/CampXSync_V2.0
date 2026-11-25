package com.campx.iadm.billing;

import com.campx.iadm.billing.security.BillingPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * S4 permission expectations for jobs / usage / reports (offline matrix).
 */
public class BillingLifecyclePolicyTest {

    @Test
    public void serviceCanRunJobsAndRecordUsage() {
        assertTrue(BillingPermissionMatrix.allows("SERVICE", "iadm04.jobs.run"));
        assertTrue(BillingPermissionMatrix.allows("SERVICE", "iadm04.usage.record"));
    }

    @Test
    public void r01HasReportsAndJobs() {
        assertTrue(BillingPermissionMatrix.allows("R01", "iadm04.reports.view"));
        assertTrue(BillingPermissionMatrix.allows("R01", "iadm04.jobs.run"));
        assertTrue(BillingPermissionMatrix.allows("R01", "iadm04.audit.view"));
    }

    @Test
    public void collegeAdminDeniedBillingKeys() {
        assertFalse(BillingPermissionMatrix.allows("R04", "iadm04.reports.view"));
        assertFalse(BillingPermissionMatrix.allows("R04", "iadm04.usage.record"));
        assertFalse(BillingPermissionMatrix.allows("R04", "iadm04.jobs.run"));
        assertFalse(BillingPermissionMatrix.allows("R04", "iadm04.subscriptions.view"));
    }

    @Test
    public void auditorReadOnlyKeys() {
        assertTrue(BillingPermissionMatrix.allows("R28", "iadm04.audit.view"));
        assertTrue(BillingPermissionMatrix.allows("R28", "iadm04.reports.view"));
        assertFalse(BillingPermissionMatrix.allows("R28", "iadm04.jobs.run"));
        assertFalse(BillingPermissionMatrix.allows("R28", "iadm04.usage.record"));
        assertFalse(BillingPermissionMatrix.allows("R28", "iadm04.payments.online.initiate"));
    }
}
