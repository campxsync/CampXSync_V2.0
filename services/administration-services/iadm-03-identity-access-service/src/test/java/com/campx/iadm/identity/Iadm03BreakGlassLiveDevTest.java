package com.campx.iadm.identity;

import com.campx.iadm.identity.config.DatabaseConfig;
import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.security.UserSecurityContext;
import com.campx.iadm.identity.service.Iadm03DomainService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * IADM-03-US-008 live DEV: R04 requests, R01 approves, session action + close (OWNER-DEC-BG-01).
 */
public class Iadm03BreakGlassLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID R01 = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
    private static final UUID R04 = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private static final UUID STUDENT = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");

    private static boolean dbReady;
    private static final Iadm03DomainService service = new Iadm03DomainService();

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
    }

    @AfterClass
    public static void teardown() throws Exception {
        if (!dbReady) return;
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement a = conn.prepareStatement(
                     "DELETE FROM plat.break_glass_actions WHERE detail::text LIKE '%IADM-03-US-008 live%' " +
                             "OR session_id IN (SELECT id FROM plat.break_glass_sessions WHERE reason LIKE 'IADM-03-US-008 live%')");
             PreparedStatement s = conn.prepareStatement(
                     "UPDATE plat.break_glass_sessions SET deleted_at = now(), closed_at = COALESCE(closed_at, now()) " +
                             "WHERE reason LIKE 'IADM-03-US-008 live%' AND deleted_at IS NULL");
             PreparedStatement r = conn.prepareStatement(
                     "UPDATE plat.break_glass_requests SET deleted_at = now() " +
                             "WHERE reason LIKE 'IADM-03-US-008 live%' AND deleted_at IS NULL")) {
            a.executeUpdate();
            s.executeUpdate();
            r.executeUpdate();
        } finally {
            DatabaseConnectionManager.resetInstance();
        }
    }

    @Test
    public void collegeAdminRequests_platformApproves_sessionAudited() {
        UserSecurityContext college = new UserSecurityContext(R04, TENANT, false);
        UserSecurityContext platform = UserSecurityContext.forPlatformAdmin(R01);

        Map<String, Object> req = service.requestBreakGlass(college, TENANT,
                "IADM-03-US-008 live lockout recovery " + System.currentTimeMillis(), 30);
        assertEquals("PENDING", req.get("status"));
        assertEquals("R04", req.get("requesterRole"));
        UUID requestId = UUID.fromString(req.get("requestId").toString());

        try {
            service.decideBreakGlass(college, requestId, "APPROVE", "no");
            fail("R04 must not approve");
        } catch (Exception ex) {
            assertTrue(ex instanceof InstituteAdminException || ex.getMessage() != null);
        }

        Map<String, Object> decided = service.decideBreakGlass(platform, requestId, "APPROVE", "R01 opens session");
        assertEquals("APPROVED", decided.get("status"));
        assertNotNull(decided.get("sessionId"));
        assertNotNull(decided.get("expiresAt"));
        UUID sessionId = UUID.fromString(decided.get("sessionId").toString());

        Map<String, Object> note = service.recordBreakGlassAction(platform, sessionId, "RECOVERY_NOTE",
                "{\"note\":\"IADM-03-US-008 live inspected tenant admins\"}");
        assertEquals("RECOVERY_NOTE", note.get("actionType"));

        Map<String, Object> closed = service.closeBreakGlassSession(platform, sessionId, "IADM-03-US-008 live done");
        assertEquals("CLOSED", closed.get("status"));

        try {
            service.recordBreakGlassAction(platform, sessionId, "STATUS_CHECK", "{}");
            fail("Closed session must reject actions");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM03_BG_EXPIRED", ex.getErrorCode());
        }
    }

    @Test
    public void studentDeniedRequest() {
        UserSecurityContext student = new UserSecurityContext(STUDENT, TENANT, false);
        try {
            service.requestBreakGlass(student, TENANT, "should fail", 30);
            fail("Student must not request break-glass");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM03_BG_REQUESTER", ex.getErrorCode());
        }
    }
}
