package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.model.CollegeModels.CollegeSetting;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresCollegeSettingRepository} against PostgreSQL.
 * Verifies setting persistence, retrieval, secret masking, and multi-tenant isolation.
 */
public class PostgresCollegeSettingRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresCollegeSettingRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private String testSettingKey;

    @Before
    public void setUp() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        tenantId = com.campx.admin.college.config.TestEnvironmentGuard.verifySafeStagingTenant(connectionManager);
        repository = new PostgresCollegeSettingRepository(connectionManager);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId);

        testSettingKey = "setting_test_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @After
    public void tearDown() throws Exception {
        if (tenantId != null && testSettingKey != null) {
            try (Connection conn = connectionManager.getConnection()) {
                conn.setAutoCommit(true);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM cfg.setting_values WHERE setting_key = ? AND tenant_id = ?")) {
                    ps.setString(1, testSettingKey);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testStoreAndRetrieveSetting() {
        CollegeSetting setting = new CollegeSetting();
        setting.setKey(testSettingKey);
        setting.setValue("CAMPUS_GATEWAY_V2");
        setting.setDataType("STRING");
        setting.setSecret(false);

        CollegeSetting stored = repository.storeSetting(tenantContext, setting);
        assertNotNull(stored);
        assertNotNull(stored.getId());
        assertEquals(1, stored.getVersion());

        Optional<CollegeSetting> opt = repository.findByKey(tenantContext, testSettingKey);
        assertTrue(opt.isPresent());
        assertEquals("CAMPUS_GATEWAY_V2", opt.get().getValue());
        assertFalse(opt.get().isSecret());

        List<CollegeSetting> list = repository.getCollegeSettings(tenantContext, false);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testSecretMasking() {
        CollegeSetting setting = new CollegeSetting();
        setting.setKey(testSettingKey);
        setting.setValue("SUPER_SECRET_TOKEN_XYZ");
        setting.setDataType("SECRET");
        setting.setSecret(true);

        repository.storeSetting(tenantContext, setting);

        Optional<CollegeSetting> opt = repository.findByKey(tenantContext, testSettingKey);
        assertTrue(opt.isPresent());
        assertTrue(opt.get().isSecret());
        assertEquals("********", opt.get().getValue());
    }

    @Test
    public void testCrossTenantIsolation() {
        CollegeSetting setting = new CollegeSetting();
        setting.setKey(testSettingKey);
        setting.setValue("VAL_ISOLATED");

        repository.storeSetting(tenantContext, setting);

        Optional<CollegeSetting> cross = repository.findByKey(otherTenantContext, testSettingKey);
        assertFalse("Cross-tenant lookup must not find setting", cross.isPresent());
    }
}
