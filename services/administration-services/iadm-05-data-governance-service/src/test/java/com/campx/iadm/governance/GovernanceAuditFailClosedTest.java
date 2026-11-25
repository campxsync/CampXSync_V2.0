package com.campx.iadm.governance;

import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.security.UserSecurityContext;
import com.campx.iadm.governance.service.DurableAuditWriter;
import com.campx.iadm.governance.service.GovernanceOutageFlags;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * IADM-05-US-025 — audit unavailable must refuse privileged action (fail closed).
 */
public class GovernanceAuditFailClosedTest {

    @After
    public void resetFlags() {
        GovernanceOutageFlags.set(Boolean.FALSE, Boolean.FALSE);
    }

    @Test
    public void auditDisabledFailsClosed() {
        GovernanceOutageFlags.set(Boolean.TRUE, null);
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        try {
            DurableAuditWriter.append(null, ctx, null, "IADM05_TEST", "policy", null, "should fail");
            fail("Expected audit unavailable");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM05_AUDIT_UNAVAILABLE", ex.getErrorCode());
        } catch (Exception ex) {
            fail("Unexpected: " + ex);
        }
    }
}
