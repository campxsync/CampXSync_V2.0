package com.campx.iadm.audit;

import com.campx.iadm.audit.config.DatabaseConfig;
import com.campx.iadm.audit.config.DatabaseConnectionManager;
import com.campx.iadm.audit.exception.UserProfileAccessDeniedException;
import com.campx.iadm.audit.security.UserSecurityContext;
import com.campx.iadm.audit.service.Iadm08DomainService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Wave E0 contract 4: Shared audit read (IADM-08-US-001 / CADM-07).
 */
public class Iadm08ContractLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");
    private static final UUID STUDENT = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");

    private static boolean dbReady;

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
    }

    @AfterClass
    public static void teardown() {
        if (dbReady) DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void institutionAdminCanSearchAudit() {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        Map<String, Object> out = new Iadm08DomainService().searchAudit(ctx, null, null);
        assertNotNull(out.get("items"));
        assertTrue(out.containsKey("count"));
    }

    @Test
    public void studentDeniedAdm01View() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(STUDENT, TENANT, false);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('adm01.view')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertFalse("Student must not hold adm01.view (audit search denied)", rs.getBoolean(1));
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    public void studentSearchAuditFailsClosed() {
        UserSecurityContext ctx = new UserSecurityContext(STUDENT, TENANT, false);
        try {
            new Iadm08DomainService().searchAudit(ctx, null, null);
            fail("Expected access denied");
        } catch (UserProfileAccessDeniedException expected) {
            assertTrue(expected.getMessage().contains("adm01.view")
                    || expected.getMessage().toLowerCase().contains("platform"));
        } catch (RuntimeException ex) {
            Throwable c = ex;
            while (c.getCause() != null) c = c.getCause();
            assertTrue("Expected UserProfileAccessDeniedException, got " + c,
                    c instanceof UserProfileAccessDeniedException
                            || (c.getMessage() != null && c.getMessage().contains("lacks")));
        }
    }
}
