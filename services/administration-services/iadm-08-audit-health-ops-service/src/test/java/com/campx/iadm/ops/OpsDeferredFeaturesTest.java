package com.campx.iadm.ops;

import com.campx.iadm.ops.security.OpsPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Deferred feature absence checks (US-010 / US-015 / US-022).
 * Plain language: no export key; manage key never granted; acknowledge is not a capability key.
 */
public class OpsDeferredFeaturesTest {

    @Test
    public void noExportPermissionKeyExists() {
        for (String k : OpsPermissionMatrix.ALL_KEYS) {
            assertFalse("export key must not exist: " + k, k.toLowerCase().contains("export"));
        }
    }

    @Test
    public void manageKeyReservedNotGrantedToAnyPersona() {
        assertFalse(OpsPermissionMatrix.allows("R01", OpsPermissionMatrix.KEY_MANAGE));
        assertFalse(OpsPermissionMatrix.allows("R02", OpsPermissionMatrix.KEY_MANAGE));
        assertFalse(OpsPermissionMatrix.allows("R03", OpsPermissionMatrix.KEY_MANAGE));
        assertFalse(OpsPermissionMatrix.allows("R04", OpsPermissionMatrix.KEY_MANAGE));
        assertFalse(OpsPermissionMatrix.allows("R28", OpsPermissionMatrix.KEY_MANAGE));
    }

    @Test
    public void noAcknowledgePermissionKey() {
        for (String k : OpsPermissionMatrix.ALL_KEYS) {
            assertFalse(k.toLowerCase().contains("ack"));
            assertFalse(k.toLowerCase().contains("acknowledge"));
        }
        assertTrue(OpsPermissionMatrix.KEY_ALERTS.contains("alerts"));
    }
}
