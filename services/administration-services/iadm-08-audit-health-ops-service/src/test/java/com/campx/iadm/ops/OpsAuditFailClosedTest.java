package com.campx.iadm.ops;

import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.iadm.ops.security.UserSecurityContext;
import com.campx.iadm.ops.service.DurableAuditWriter;
import com.campx.iadm.ops.service.OpsOutageFlags;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Fail-closed read recording (US-019 / D3).
 * Plain language: if the shared audit cannot record the read, the request is refused.
 */
public class OpsAuditFailClosedTest {

    @After
    public void reset() {
        OpsOutageFlags.reset();
    }

    @Test
    public void auditOutageRefusesRecording() {
        OpsOutageFlags.setAuditDisabled(true);
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        try {
            DurableAuditWriter.append(null, ctx, null, "IADM08_AUDIT_SEARCH", "audit_search", null, "test");
            fail("expected refuse");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM08_AUDIT_UNAVAILABLE", ex.getErrorCode());
        } catch (Exception ex) {
            fail(ex.getMessage());
        }
    }
}
