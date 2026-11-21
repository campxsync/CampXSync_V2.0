package com.campx.iadm.config;

import com.campx.iadm.config.config.DatabaseConfig;
import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.security.UserSecurityContext;
import com.campx.iadm.config.service.PlatformConfigService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Wave F1 live DEV checks for IADM-02: empty scopes, locked deny, rollback via setting_value_versions.
 */
public class Iadm02WaveFLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PLATFORM_ADMIN = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");

    private static boolean dbReady;
    private static PlatformConfigService service;

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
        service = new PlatformConfigService();
    }

    @AfterClass
    public static void teardown() {
        if (dbReady) DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void emptyScopesRejected() {
        UserSecurityContext ctx = new UserSecurityContext(PLATFORM_ADMIN, TENANT, true);
        try {
            service.upsertDefinition(ctx, "iadm02.wavef.empty_scopes", "STRING", "\"x\"",
                    false, true, new String[0]);
            fail("Expected IADM02_AMBIGUOUS_SCOPE");
        } catch (InstituteAdminException e) {
            assertEquals("IADM02_AMBIGUOUS_SCOPE", e.getErrorCode());
            assertEquals(400, e.getStatus());
        }
    }

    @Test
    public void typeMismatchRejected_andLockedDenied() throws Exception {
        String key = "iadm02.wavef.lock." + (System.currentTimeMillis() % 100000);
        UserSecurityContext platform = new UserSecurityContext(PLATFORM_ADMIN, TENANT, true);
        UserSecurityContext r03 = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);

        // Seed unlocked STRING definition for type check, then locked definition for deny
        service.upsertDefinition(platform, key, "STRING", "\"ok\"", false, true,
                new String[]{"INSTITUTE", "COLLEGE"});
        try {
            service.setInstituteValue(r03, key, "true");
            fail("Expected IADM02_TYPE_MISMATCH");
        } catch (InstituteAdminException e) {
            assertEquals("IADM02_TYPE_MISMATCH", e.getErrorCode());
            assertEquals(400, e.getStatus());
        }

        String lockedKey = "iadm02.wavef.locked." + (System.currentTimeMillis() % 100000);
        service.upsertDefinition(platform, lockedKey, "STRING", "\"locked\"", true, false,
                new String[]{"INSTITUTE", "COLLEGE"});
        try {
            service.setInstituteValue(r03, lockedKey, "\"override\"");
            fail("Expected IADM02_LOCKED");
        } catch (InstituteAdminException e) {
            assertEquals("IADM02_LOCKED", e.getErrorCode());
            assertEquals(403, e.getStatus());
        } finally {
            softDeleteDefinition(lockedKey);
            softDeleteDefinition(key);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rollbackPathWithR03_whenVersionsPresent() throws Exception {
        Assume.assumeTrue("cfg.setting_value_versions missing", versionsTablePresent());

        String key = "iadm02.wavef.rollback." + (System.currentTimeMillis() % 100000);
        UserSecurityContext platform = new UserSecurityContext(PLATFORM_ADMIN, TENANT, true);
        UserSecurityContext r03 = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);

        service.upsertDefinition(platform, key, "STRING", "\"platform-default\"", false, true,
                new String[]{"INSTITUTE", "COLLEGE"});
        try {
            service.setInstituteValue(r03, key, "\"v1\"");
            Map<String, Object> hist1 = service.settingHistory(r03, key);
            assertEquals("cfg.setting_value_versions", hist1.get("source"));
            List<Map<String, Object>> items1 = (List<Map<String, Object>>) hist1.get("items");
            assertFalse("expected at least one version after set v1", items1.isEmpty());
            String versionV1 = String.valueOf(items1.get(0).get("versionId"));

            service.setInstituteValue(r03, key, "\"v2\"");
            Map<String, Object> rolled = service.rollbackSetting(r03, key, versionV1);
            assertEquals("\"v1\"", String.valueOf(rolled.get("value")).replace(" ", ""));

            Map<String, Object> effective = service.resolveEffective(r03, TENANT, null, key);
            String eff = String.valueOf(effective.get("effectiveValue"));
            assertTrue("effective should reflect rolled-back v1, got " + eff, eff.contains("v1"));
        } finally {
            softDeleteValues(key);
            softDeleteDefinition(key);
        }
    }

    private static boolean versionsTablePresent() throws Exception {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.tables WHERE table_schema='cfg' AND table_name='setting_value_versions'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private static void softDeleteDefinition(String key) throws Exception {
        UserSecurityContext platform = new UserSecurityContext(PLATFORM_ADMIN, TENANT, true);
        platform.executeInTransaction(DatabaseConnectionManager.getInstance(), conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.setting_definitions SET deleted_at = now(), updated_at = now() " +
                            "WHERE setting_key = ? AND deleted_at IS NULL")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private static void softDeleteValues(String key) throws Exception {
        UserSecurityContext r03 = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        r03.executeInTransaction(DatabaseConnectionManager.getInstance(), conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.setting_values SET deleted_at = now(), updated_at = now() " +
                            "WHERE tenant_id = ? AND setting_key = ? AND deleted_at IS NULL")) {
                ps.setObject(1, TENANT);
                ps.setString(2, key);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
