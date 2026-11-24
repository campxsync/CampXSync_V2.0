package com.campx.iadm.access;

import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.iadm.access.service.DurableAuditWriter;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * IADM-03-US-004 — if durable audit cannot be guaranteed, do not report success.
 */
public class DurableAuditFailClosedTest {

    @Test
    public void nullConnectionFailsClosed() throws Exception {
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        try {
            DurableAuditWriter.append(null, ctx, UUID.randomUUID(),
                    "IADM03_TEST", "TEST", "x", "should fail");
            fail("expected audit unavailable");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM03_AUDIT_UNAVAILABLE", ex.getErrorCode());
        }
    }
}
