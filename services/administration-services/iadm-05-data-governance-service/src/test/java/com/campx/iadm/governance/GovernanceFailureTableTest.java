package com.campx.iadm.governance;

import com.campx.iadm.governance.security.GovernancePermissionMatrix;
import com.campx.iadm.governance.simulator.DomainExecutionSimulator;
import com.campx.iadm.governance.service.GovernanceOutageFlags;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IADM-05-US-031 — offline coverage for blueprint failure-table behaviours that do not need DB.
 * Plain language: proves role denials, simulator partial/fail outcomes, and audit outage flag.
 */
public class GovernanceFailureTableTest {

    @After
    public void reset() {
        GovernanceOutageFlags.set(Boolean.FALSE, Boolean.FALSE);
    }

    @Test
    public void r02CannotApproveOrPublish() {
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.policy.publish"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.export.decide"));
    }

    @Test
    public void r03R04R28CannotMutate() {
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.classification.propose"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R28", "iadm05.policy.approve"));
    }

    @Test
    public void r05FamilyDenied() {
        assertFalse(GovernancePermissionMatrix.allows("R05", "iadm05.classification.view"));
        assertFalse(GovernancePermissionMatrix.allows("R31", "iadm05.college.view"));
    }

    @Test
    public void simulatorPartialAndRejectOutcomes() {
        DomainExecutionSimulator sim = new DomainExecutionSimulator();
        UUID corr = UUID.randomUUID();
        Map<String, Object> meta = Collections.emptyMap();
        assertEquals("COMPLETED", sim.execute("EXPORT", "DEMO_DOMAIN_A", corr, meta).get("outcome"));
        assertEquals("PENDING", sim.execute("ERASURE", "DEMO_DOMAIN_B", corr, meta).get("outcome"));
        assertEquals("REJECTED", sim.execute("EXPORT", "DEMO_DOMAIN_B_FAIL", corr, meta).get("outcome"));
        assertEquals("UNAVAILABLE", sim.execute("EXPORT", "X_UNAVAILABLE", corr, meta).get("outcome"));
        assertFalse(Boolean.TRUE.equals(sim.execute("EXPORT", "DEMO_DOMAIN_A", corr, meta)
                .get("touchesRealDomainData")));
    }

    @Test
    public void viewDoesNotImplyExport() {
        assertTrue(GovernancePermissionMatrix.allows("R03", "iadm05.classification.view"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.export.request"));
    }
}
