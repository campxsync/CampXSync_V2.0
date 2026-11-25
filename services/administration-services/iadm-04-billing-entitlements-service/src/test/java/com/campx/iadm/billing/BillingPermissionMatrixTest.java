package com.campx.iadm.billing;

import com.campx.iadm.billing.security.BillingPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillingPermissionMatrixTest {

    @Test
    public void r01ManagesPlansR02DoesNot() {
        assertTrue(BillingPermissionMatrix.allows("R01", "iadm04.plans.manage"));
        assertFalse(BillingPermissionMatrix.allows("R02", "iadm04.plans.manage"));
        assertFalse(BillingPermissionMatrix.allows("R02", "iadm04.plan_change.decide"));
        assertTrue(BillingPermissionMatrix.allows("R02", "iadm04.payments.callback.retry"));
    }

    @Test
    public void r03OwnSubscriptionOnly() {
        assertTrue(BillingPermissionMatrix.allows("R03", "iadm04.subscriptions.view.own"));
        assertTrue(BillingPermissionMatrix.allows("R03", "iadm04.plan_change.request"));
        assertFalse(BillingPermissionMatrix.allows("R03", "iadm04.subscriptions.view"));
        assertFalse(BillingPermissionMatrix.allows("R04", "iadm04.plans.view"));
    }

    @Test
    public void r28ReadOnly() {
        assertTrue(BillingPermissionMatrix.allows("R28", "iadm04.audit.view"));
        assertFalse(BillingPermissionMatrix.allows("R28", "iadm04.plans.manage"));
        assertFalse(BillingPermissionMatrix.allows("R28", "iadm04.payments.offline.record"));
    }
}
