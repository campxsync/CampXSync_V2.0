package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.DataClassification;
import com.campx.admin.institute.model.InstituteModels.DataRetentionPolicy;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 28: {@link PostgresDataGovernanceRepository} against Supabase PostgreSQL.
 * Verifies data retention policies, data classifications taxonomy, and platform admin RLS enforcement.
 */
public class PostgresDataGovernanceRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresDataGovernanceRepository repository;
    private static UserSecurityContext platformAdminContext;

    private static UUID tenantId;
    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdPolicyIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Set<UUID> createdClassIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresDataGovernanceRepository(connectionManager);

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

        platformAdminContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID pid : createdPolicyIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM cfg.data_retention_policies WHERE id = ?")) {
                    ps.setObject(1, pid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID cid : createdClassIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM cfg.data_classifications WHERE id = ?")) {
                    ps.setObject(1, cid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testRetentionPolicyLifecycle() throws Exception {
        String code = "RET_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        DataRetentionPolicy policy = new DataRetentionPolicy();
        policy.setPolicyCode(code);
        policy.setEntityType("EXAM_SUBMISSIONS");
        policy.setRetentionDays(1095); // 3 years
        policy.setAction("ARCHIVE");

        DataRetentionPolicy created = repository.createRetentionPolicy(platformAdminContext, policy);
        assertNotNull(created.getId());
        createdPolicyIds.add(UUID.fromString(created.getId()));

        assertEquals(code, created.getPolicyCode());
        assertEquals("EXAM_SUBMISSIONS", created.getEntityType());
        assertEquals(1095, created.getRetentionDays());
        assertEquals("ARCHIVE", created.getAction());

        Optional<DataRetentionPolicy> byId = repository.findRetentionPolicyById(platformAdminContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<DataRetentionPolicy> byCode = repository.findRetentionPolicyByCode(platformAdminContext, code);
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        created.setRetentionDays(1460); // 4 years
        DataRetentionPolicy updated = repository.updateRetentionPolicy(platformAdminContext, created);
        assertEquals(1460, updated.getRetentionDays());
        assertEquals(2, updated.getRowVersion());

        repository.deleteRetentionPolicy(platformAdminContext, created.getId());
        Optional<DataRetentionPolicy> deleted = repository.findRetentionPolicyById(platformAdminContext, created.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testClassificationLifecycle() throws Exception {
        String code = "CLASS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        DataClassification classification = new DataClassification();
        classification.setClassificationCode(code);
        classification.setSensitivityLevel("HIGH");
        classification.setEncryptionRequired(true);

        DataClassification created = repository.createClassification(platformAdminContext, classification);
        assertNotNull(created.getId());
        createdClassIds.add(UUID.fromString(created.getId()));

        assertEquals(code, created.getClassificationCode());
        assertEquals("HIGH", created.getSensitivityLevel());
        assertTrue(created.isEncryptionRequired());

        Optional<DataClassification> byId = repository.findClassificationById(platformAdminContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<DataClassification> byCode = repository.findClassificationByCode(platformAdminContext, code);
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        created.setSensitivityLevel("RESTRICTED");
        DataClassification updated = repository.updateClassification(platformAdminContext, created);
        assertEquals("RESTRICTED", updated.getSensitivityLevel());
        assertEquals(2, updated.getRowVersion());

        repository.deleteClassification(platformAdminContext, created.getId());
        Optional<DataClassification> deleted = repository.findClassificationById(platformAdminContext, created.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testListPoliciesAndClassifications() throws Exception {
        List<DataRetentionPolicy> policies = repository.listRetentionPolicies(platformAdminContext);
        assertNotNull(policies);

        List<DataClassification> classifications = repository.listClassifications(platformAdminContext);
        assertNotNull(classifications);
    }
}
