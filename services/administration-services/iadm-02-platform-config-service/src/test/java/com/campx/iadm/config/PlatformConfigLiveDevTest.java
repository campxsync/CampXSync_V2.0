package com.campx.iadm.config;

import com.campx.iadm.config.config.DatabaseConfig;
import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.Assert.*;

public class PlatformConfigLiveDevTest {
    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PLATFORM_ADMIN = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
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
    public void resolveEffectiveFunctionExistsAndReturnsPlatformDefault_rolledBack() throws Exception {
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                // Ensure a disposable definition exists then resolve
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO cfg.setting_definitions (setting_key, data_type, default_value, platform_locked, delegable_to_college, allowed_scopes, created_by) " +
                                "VALUES ('iadm02.test.resolve', 'STRING', '\"platform-default\"'::jsonb, false, true, '{INSTITUTE,COLLEGE}', ?) " +
                                "ON CONFLICT DO NOTHING")) {
                    // partial unique index — use delete+insert if conflict unsupported
                    ins.setObject(1, PLATFORM_ADMIN);
                    try { ins.executeUpdate(); } catch (Exception ignored) {}
                }
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM cfg.setting_definitions WHERE setting_key = 'iadm02.test.resolve'");
                     PreparedStatement rein = conn.prepareStatement(
                        "INSERT INTO cfg.setting_definitions (setting_key, data_type, default_value, platform_locked, delegable_to_college, allowed_scopes, created_by) " +
                                "VALUES ('iadm02.test.resolve', 'STRING', '\"platform-default\"'::jsonb, false, true, '{INSTITUTE,COLLEGE}', ?)")) {
                    del.executeUpdate();
                    rein.setObject(1, PLATFORM_ADMIN);
                    rein.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT source, denied FROM cfg.resolve_effective_setting(?, NULL, 'iadm02.test.resolve')")) {
                    ps.setObject(1, TENANT);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("PLATFORM", rs.getString("source"));
                        assertFalse(rs.getBoolean("denied"));
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    public void defaultPortIs8100() {
        assertEquals(8100, com.campx.iadm.config.server.Iadm02Server.DEFAULT_PORT);
    }

    @Test
    public void platformLockedSettingResolvesToPlatform_rolledBack() throws Exception {
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN);
        String key = "iadm02.test.lock." + (System.currentTimeMillis() % 100000);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO cfg.setting_definitions (setting_key, data_type, default_value, platform_locked, delegable_to_college, allowed_scopes, created_by) " +
                                "VALUES (?, 'STRING', '\"locked-platform\"'::jsonb, true, false, '{INSTITUTE,COLLEGE}', ?)")) {
                    ins.setString(1, key);
                    ins.setObject(2, PLATFORM_ADMIN);
                    ins.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT source, platform_locked, denied FROM cfg.resolve_effective_setting(?, NULL, ?)")) {
                    ps.setObject(1, TENANT);
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("PLATFORM", rs.getString("source"));
                        assertTrue(rs.getBoolean("platform_locked"));
                        assertFalse(rs.getBoolean("denied"));
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }
}
