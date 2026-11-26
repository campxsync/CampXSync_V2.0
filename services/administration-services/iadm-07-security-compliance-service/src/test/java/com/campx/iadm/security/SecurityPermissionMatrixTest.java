package com.campx.iadm.security;

import com.campx.iadm.security.security.SecurityPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IADM-07-US-023 — offline denial proofs for permission-key mapping.
 */
public class SecurityPermissionMatrixTest {

    @Test
    public void r01HasApproveAndDecide_notServiceIntake() {
        assertTrue(SecurityPermissionMatrix.allows("R01", "iadm07.standard.approve"));
        assertTrue(SecurityPermissionMatrix.allows("R01", "iadm07.exception.decide"));
        // Signal intake is authenticated service caller only
        assertFalse(SecurityPermissionMatrix.allows("R01", "iadm07.signal.intake"));
    }

    @Test
    public void r02NeverApproveOrDecide() {
        assertTrue(SecurityPermissionMatrix.allows("R02", "iadm07.standard.propose"));
        assertFalse(SecurityPermissionMatrix.allows("R02", "iadm07.standard.approve"));
        assertFalse(SecurityPermissionMatrix.allows("R02", "iadm07.exception.decide"));
    }

    @Test
    public void r03InstituteScopedWrites() {
        assertTrue(SecurityPermissionMatrix.allows("R03", "iadm07.register.write"));
        assertTrue(SecurityPermissionMatrix.allows("R03", "iadm07.exception.request"));
        assertFalse(SecurityPermissionMatrix.allows("R03", "iadm07.standard.propose"));
    }

    @Test
    public void r04SummaryOnly() {
        assertTrue(SecurityPermissionMatrix.allows("R04", "iadm07.summary.view"));
        assertFalse(SecurityPermissionMatrix.allows("R04", "iadm07.register.read"));
        assertFalse(SecurityPermissionMatrix.allows("R04", "iadm07.posture.view"));
    }

    @Test
    public void r28ReadOnlyAuditor() {
        assertTrue(SecurityPermissionMatrix.allows("R28", "iadm07.audit.view"));
        assertTrue(SecurityPermissionMatrix.allows("R28", "iadm07.register.read"));
        assertFalse(SecurityPermissionMatrix.allows("R28", "iadm07.register.write"));
        assertFalse(SecurityPermissionMatrix.allows("R28", "iadm07.standard.approve"));
    }

    @Test
    public void serviceSignalIntakeOnly() {
        assertTrue(SecurityPermissionMatrix.allows("SERVICE", "iadm07.signal.intake"));
        assertFalse(SecurityPermissionMatrix.allows("SERVICE", "iadm07.register.read"));
    }

    @Test
    public void otherRolesDenied() {
        assertFalse(SecurityPermissionMatrix.allows("R05", "iadm07.posture.view"));
        assertFalse(SecurityPermissionMatrix.allows("R10", "iadm07.register.read"));
        assertFalse(SecurityPermissionMatrix.allows("R25", "iadm07.summary.view"));
    }
}