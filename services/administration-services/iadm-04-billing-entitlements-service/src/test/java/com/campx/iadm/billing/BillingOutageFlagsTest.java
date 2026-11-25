package com.campx.iadm.billing;

import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.payments.DevPaymentSimulator;
import com.campx.iadm.billing.security.BillingPermissionMatrix;
import com.campx.iadm.billing.service.BillingOutageFlags;
import com.campx.iadm.billing.service.DurableAuditWriter;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-04 S5 — US-038 outage flags + R02 plan-write deny (offline matrix + fail-closed audit).
 */
public class BillingOutageFlagsTest {

    @After
    public void resetFlags() {
        BillingOutageFlags.set(Boolean.FALSE, Boolean.FALSE);
        DevPaymentSimulator.resetInitiateCallCount();
    }

    @Test
    public void auditDisabledFailsClosedBeforeProvider() {
        BillingOutageFlags.set(Boolean.TRUE, Boolean.FALSE);
        long before = DevPaymentSimulator.getInitiateCallCount();
        try {
            DurableAuditWriter.append(null, null, null, "TEST", "t", "id", "msg");
            fail("Expected audit unavailable");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM04_AUDIT_UNAVAILABLE", ex.getErrorCode());
        } catch (Exception ex) {
            fail("Unexpected exception: " + ex);
        }
        assertEquals("Simulator must not be called when audit is down",
                before, DevPaymentSimulator.getInitiateCallCount());
    }

    @Test
    public void r02CannotManagePlansByAnyPermissionKey() {
        assertFalse(BillingPermissionMatrix.allows("R02", "iadm04.plans.manage"));
        assertFalse(BillingPermissionMatrix.allows("SYSTEM_ADMIN", "iadm04.plans.manage"));
        assertTrue(BillingPermissionMatrix.allows("R01", "iadm04.plans.manage"));
    }

    @Test
    public void notificationsFlagSnapshot() {
        BillingOutageFlags.set(Boolean.FALSE, Boolean.TRUE);
        assertTrue(BillingOutageFlags.isNotificationsDisabled());
        assertFalse(BillingOutageFlags.isAuditDisabled());
        assertTrue(Boolean.TRUE.equals(BillingOutageFlags.snapshot().get("notificationsDisabled")));
    }
}
