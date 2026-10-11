package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.model.CollegeModels.FeatureOverride;
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
 * Live integration tests for {@link PostgresFeatureOverrideRepository} against PostgreSQL.
 * Verifies feature override storage, flag listing, and tenant isolation.
 */
public class PostgresFeatureOverrideRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresFeatureOverrideRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private String testFlagKey;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresFeatureOverrideRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant", tenantId);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId);

        testFlagKey = "flag_test_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @After
    public void tearDown() throws Exception {
        if (tenantId != null && testFlagKey != null) {
            try (Connection conn = connectionManager.getConnection()) {
                conn.setAutoCommit(true);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM cfg.feature_overrides WHERE tenant_id = ? AND flag_id IN " +
                        "(SELECT id FROM cfg.feature_flags WHERE flag_key = ?)")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, testFlagKey);
                    ps.executeUpdate();
                } catch (Exception ignored) {}

                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM cfg.feature_flags WHERE flag_key = ?")) {
                    ps.setString(1, testFlagKey);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testStoreAndRetrieveFeatureOverride() {
        FeatureOverride fo = new FeatureOverride();
        fo.setFlagKey(testFlagKey);
        fo.setOverrideValue("true");
        fo.setReason("Enabled for testing beta features");

        FeatureOverride stored = repository.overrideFeatureFlag(tenantContext, fo);
        assertNotNull(stored);
        assertNotNull(stored.getId());
        assertEquals("true", stored.getOverrideValue());

        Optional<FeatureOverride> found = repository.findByFlagKey(tenantContext, testFlagKey);
        assertTrue(found.isPresent());
        assertEquals(testFlagKey, found.get().getFlagKey());
        assertEquals("true", found.get().getOverrideValue());

        List<FeatureOverride> list = repository.listFeatureOverrides(tenantContext);
        assertFalse(list.isEmpty());
        boolean hasFlag = list.stream().anyMatch(f -> testFlagKey.equals(f.getFlagKey()));
        assertTrue("Listed overrides must include " + testFlagKey, hasFlag);
    }

    @Test
    public void testUpdateFeatureOverride() {
        FeatureOverride fo = new FeatureOverride();
        fo.setFlagKey(testFlagKey);
        fo.setOverrideValue("true");

        repository.overrideFeatureFlag(tenantContext, fo);

        FeatureOverride update = new FeatureOverride();
        update.setFlagKey(testFlagKey);
        update.setOverrideValue("false");

        FeatureOverride updated = repository.overrideFeatureFlag(tenantContext, update);
        assertEquals("false", updated.getOverrideValue());

        Optional<FeatureOverride> found = repository.findByFlagKey(tenantContext, testFlagKey);
        assertTrue(found.isPresent());
        assertEquals("false", found.get().getOverrideValue());
    }

    @Test
    public void testCrossTenantIsolation() {
        FeatureOverride fo = new FeatureOverride();
        fo.setFlagKey(testFlagKey);
        fo.setOverrideValue("true");

        repository.overrideFeatureFlag(tenantContext, fo);

        Optional<FeatureOverride> cross = repository.findByFlagKey(otherTenantContext, testFlagKey);
        assertFalse("Cross-tenant lookup must not find override", cross.isPresent());
    }
}
