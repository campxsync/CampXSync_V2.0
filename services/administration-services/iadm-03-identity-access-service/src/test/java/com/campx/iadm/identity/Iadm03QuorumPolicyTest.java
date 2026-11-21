package com.campx.iadm.identity;

import com.campx.iadm.identity.exception.InstituteAdminException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Offline policy smoke for IADM-03-US-009 error codes. */
public class Iadm03QuorumPolicyTest {

    @Test
    public void selfApproveCode() {
        InstituteAdminException ex = new InstituteAdminException(403, "IADM03_QUORUM_SELF_APPROVE", "no");
        assertEquals("IADM03_QUORUM_SELF_APPROVE", ex.getErrorCode());
        assertEquals(403, ex.getStatus());
    }

    @Test
    public void sameTrackCode() {
        InstituteAdminException ex = new InstituteAdminException(403, "IADM03_QUORUM_SAME_TRACK", "no");
        assertTrue(ex.getErrorCode().contains("SAME_TRACK"));
    }
}
