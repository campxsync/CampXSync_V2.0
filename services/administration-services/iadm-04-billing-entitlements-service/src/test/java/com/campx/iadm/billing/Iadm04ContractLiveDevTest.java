package com.campx.iadm.billing;

import com.campx.iadm.billing.config.DatabaseConfig;
import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.UserProfileAccessDeniedException;
import com.campx.iadm.billing.security.UserSecurityContext;
import com.campx.iadm.billing.service.Iadm04DomainService;
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
 * Wave E0 contract 5: Entitlement check fail-closed (IADM-04-US-006).
 */
public class Iadm04ContractLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");
    private static final UUID COLLEGE_ADMIN = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private static final UUID PLATFORM_ADMIN = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
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
    public void unknownEntitlementFailsClosed_viaSql() throws Exception {
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN)
                .withTenant(TENANT);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT allowed, deny_reason FROM plat.check_entitlement(?, ?)")) {
                    ps.setObject(1, TENANT);
                    ps.setString(2, "wave_e0_unknown_entitlement");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertFalse(rs.getBoolean(1));
                        assertEquals("UNKNOWN_ENTITLEMENT", rs.getString(2));
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    public void institutionAdminCheckEntitlementUnknownKey() {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        Map<String, Object> out = new Iadm04DomainService().checkEntitlement(ctx, TENANT, "no_such_key_e0");
        assertEquals(Boolean.FALSE, out.get("allowed"));
        assertEquals("UNKNOWN_ENTITLEMENT", out.get("denyReason"));
    }

    @Test
    public void studentDeniedEntitlementApiPermission() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(STUDENT, TENANT, false);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                boolean adm01;
                boolean iadm04;
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('adm01.manage')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    adm01 = rs.getBoolean(1);
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('iadm04.view')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    iadm04 = rs.getBoolean(1);
                }
                assertFalse(adm01);
                assertFalse(iadm04);
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
        try {
            new Iadm04DomainService().checkEntitlement(ctx, TENANT, "any");
            fail("Student must be denied entitlement API");
        } catch (UserProfileAccessDeniedException expected) {
            // fail closed
        } catch (RuntimeException ex) {
            Throwable c = ex;
            while (c.getCause() != null) c = c.getCause();
            assertTrue(c instanceof UserProfileAccessDeniedException
                    || (c.getMessage() != null && c.getMessage().contains("lacks")));
        }
    }

    @Test
    public void collegeAdminLacksIadm04View_deniedAtPermissionLayer() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(COLLEGE_ADMIN, TENANT, false);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('iadm04.view')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertFalse("College Admin (R04) must not hold iadm04.view", rs.getBoolean(1));
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }
}
