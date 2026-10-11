package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.model.CollegeModels.CollegeProfile;
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
 * Live integration tests for {@link PostgresCollegeProfileRepository} against PostgreSQL.
 * Verifies profile persistence, update, accreditation syncing, and multi-tenant isolation.
 */
public class PostgresCollegeProfileRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresCollegeProfileRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private String testCollegeCode;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresCollegeProfileRepository(connectionManager);

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

        testCollegeCode = "COL_T_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    @After
    public void tearDown() throws Exception {
        if (tenantId != null && testCollegeCode != null) {
            try (Connection conn = connectionManager.getConnection()) {
                conn.setAutoCommit(true);
                // Clean up accreditations
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM core.college_accreditations WHERE college_id IN (SELECT id FROM core.colleges WHERE code = ? AND tenant_id = ?)")) {
                    ps.setString(1, testCollegeCode);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}

                // Clean up college
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM core.colleges WHERE code = ? AND tenant_id = ?")) {
                    ps.setString(1, testCollegeCode);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveProfile() {
        CollegeProfile profile = new CollegeProfile();
        profile.setCollegeCode(testCollegeCode);
        profile.setDisplayName("Test College of Tech");
        profile.setLegalName("Test Legal College LLC");
        profile.setStatus("ACTIVE");
        profile.setAccreditationRefs(Arrays.asList("NAAC_A", "NBA_TIER_1"));

        CollegeProfile saved = repository.saveOrUpdateProfile(tenantContext, profile);
        assertNotNull(saved);
        assertEquals(testCollegeCode, saved.getCollegeCode());
        assertEquals(1, saved.getCurrentVersion());

        Optional<CollegeProfile> retrieved = repository.getProfile(tenantContext, testCollegeCode);
        assertTrue(retrieved.isPresent());
        assertEquals("Test College of Tech", retrieved.get().getDisplayName());
        assertEquals("Test Legal College LLC", retrieved.get().getLegalName());
        assertEquals("ACTIVE", retrieved.get().getStatus());
        assertEquals(2, retrieved.get().getAccreditationRefs().size());
    }

    @Test
    public void testUpdateProfile() {
        CollegeProfile profile = new CollegeProfile();
        profile.setCollegeCode(testCollegeCode);
        profile.setDisplayName("Initial Name");
        profile.setLegalName("Initial Legal Name");
        profile.setStatus("ACTIVE");

        repository.saveOrUpdateProfile(tenantContext, profile);

        CollegeProfile update = new CollegeProfile();
        update.setCollegeCode(testCollegeCode);
        update.setDisplayName("Updated College Name");
        update.setLegalName("Updated Legal Name");
        update.setStatus("ACTIVE");
        update.setAccreditationRefs(Collections.singletonList("NIRF_TOP_50"));

        CollegeProfile updated = repository.saveOrUpdateProfile(tenantContext, update);
        assertEquals(2, updated.getCurrentVersion());

        Optional<CollegeProfile> retrieved = repository.getProfile(tenantContext, testCollegeCode);
        assertTrue(retrieved.isPresent());
        assertEquals("Updated College Name", retrieved.get().getDisplayName());
        assertEquals(2, retrieved.get().getCurrentVersion());
        assertEquals(1, retrieved.get().getAccreditationRefs().size());
    }

    @Test
    public void testCrossTenantIsolation() {
        CollegeProfile profile = new CollegeProfile();
        profile.setCollegeCode(testCollegeCode);
        profile.setDisplayName("Isolated College");
        profile.setStatus("ACTIVE");

        repository.saveOrUpdateProfile(tenantContext, profile);

        Optional<CollegeProfile> cross = repository.getProfile(otherTenantContext, testCollegeCode);
        assertFalse("Cross-tenant lookup must not find profile", cross.isPresent());
    }
}
