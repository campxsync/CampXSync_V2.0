package com.campx.iadm.governance;

import com.campx.iadm.governance.security.GovernancePermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IADM-05-US-023 — offline denial proofs for permission-key mapping.
 * Plain language: View does not imply approve; R02 cannot approve; R03/R04/R28 cannot write;
 * a role outside R01–R04/R28 gets nothing.
 */
public class GovernancePermissionMatrixTest {

    @Test
    public void r01HasApproveAndPublish() {
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.policy.approve"));
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.policy.publish"));
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.export.decide"));
    }

    @Test
    public void r02MayProposeButNeverApprove() {
        assertTrue(GovernancePermissionMatrix.allows("R02", "iadm05.classification.propose"));
        assertTrue(GovernancePermissionMatrix.allows("R02", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.policy.publish"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.export.decide"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.erasure.close"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.sharing.decide"));
    }

    @Test
    public void r03ViewOnlyNoWrite() {
        assertTrue(GovernancePermissionMatrix.allows("R03", "iadm05.adherence.view"));
        assertTrue(GovernancePermissionMatrix.allows("R03", "iadm05.classification.view"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.classification.propose"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.export.request"));
    }

    @Test
    public void r04ViewOnlyNoTrigger() {
        assertTrue(GovernancePermissionMatrix.allows("R04", "iadm05.college.view"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.classification.propose"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.policy.approve"));
    }

    @Test
    public void r28ReadOnly() {
        assertTrue(GovernancePermissionMatrix.allows("R28", "iadm05.audit.view"));
        assertTrue(GovernancePermissionMatrix.allows("R28", "iadm05.policy.history"));
        assertFalse(GovernancePermissionMatrix.allows("R28", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R28", "iadm05.classification.propose"));
    }

    @Test
    public void r05ThroughR39Denied() {
        assertFalse(GovernancePermissionMatrix.allows("R05", "iadm05.classification.view"));
        assertFalse(GovernancePermissionMatrix.allows("R10", "iadm05.policy.history"));
        assertFalse(GovernancePermissionMatrix.allows("R25", "iadm05.adherence.view"));
        assertFalse(GovernancePermissionMatrix.allows("R31", "iadm05.college.view"));
    }

    @Test
    public void viewDoesNotImplyExportOrApprove() {
        assertTrue(GovernancePermissionMatrix.allows("R03", "iadm05.classification.view"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.policy.approve"));
    }

    @Test
    public void noWildcardKeyPresent() {
        for (String key : GovernancePermissionMatrix.NEW_KEYS) {
            assertFalse("wildcard key forbidden", key.contains("*"));
        }
    }
}
