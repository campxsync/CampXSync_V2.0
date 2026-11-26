package com.campx.iadm.security;

import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.security.UserSecurityContext;
import com.campx.iadm.security.service.DurableAuditWriter;
import com.campx.iadm.security.service.SecurityOutageFlags;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-07-US-025 — audit unavailable must refuse privileged action (fail closed).
 */
public class SecurityAuditFailClosedTest {

    @After
    public void resetFlags() {
        SecurityOutageFlags.set(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
    }

    @Test
    public void auditDisabledFailsClosed() {
        SecurityOutageFlags.set(Boolean.TRUE, null, null);
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        try {
            DurableAuditWriter.append(null, ctx, null, "IADM07_TEST", "signal", null, "should fail");
            fail("Expected audit unavailable");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM07_AUDIT_UNAVAILABLE", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("security governance change cannot be confirmed"));
        } catch (Exception ex) {
            fail("Unexpected: " + ex);
        }
    }
}