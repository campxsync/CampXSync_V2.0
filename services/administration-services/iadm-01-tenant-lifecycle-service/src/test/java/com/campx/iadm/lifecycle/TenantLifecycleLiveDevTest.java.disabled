package com.campx.iadm.lifecycle;

import com.campx.iadm.lifecycle.config.DatabaseConfig;
import com.campx.iadm.lifecycle.config.DatabaseConnectionManager;
import com.campx.iadm.lifecycle.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Live DEV test (azulbaxszsqxymqmlwfe): rolled-back transaction mutates then discards.
 */
public class TenantLifecycleLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PLATFORM_ADMIN = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
    private static final UUID COLLEGE_ADMIN = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");

    private static boolean dbReady;

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured; skipping live test", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
    }

    @AfterClass
    public static void teardown() {
        if (dbReady) DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void collegeAdminDeniedLifecycleEventInsert_rolledBack() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(COLLEGE_ADMIN, TENANT, false);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                boolean allowed;
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('adm01.manage')")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        allowed = rs.next() && rs.getBoolean(1);
                    }
                }
                assertFalse("College Admin (R04) must not hold adm01.manage", allowed);
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    public void platformAdminCanInsertLifecycleEvent_rolledBack() throws Exception {
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                ctx.checkPlatformAdmin(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO plat.institute_lifecycle_events (tenant_id, event_type, actor_id, reason, payload, created_by) " +
                                "VALUES (?, 'STATUS_CHANGE', ?, 'live-dev-test', '{}'::jsonb, ?)")) {
                    ps.setObject(1, TENANT);
                    ps.setObject(2, PLATFORM_ADMIN);
                    ps.setObject(3, PLATFORM_ADMIN);
                    assertEquals(1, ps.executeUpdate());
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }
}
