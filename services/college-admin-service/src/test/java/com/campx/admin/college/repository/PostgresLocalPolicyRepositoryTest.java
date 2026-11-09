package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.model.CollegeModels.LocalPolicy;
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
 * Live integration tests for {@link PostgresLocalPolicyRepository} against PostgreSQL.
 * Verifies local policy lifecycle: creation, conflict handling, update, and retrieval.
 */
public class PostgresLocalPolicyRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresLocalPolicyRepository repository;
    private UserSecurityContext tenantContext;

    private UUID tenantId;
    private String testPolicyCode;

    @Before
    public void setUp() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        tenantId = com.campx.admin.college.config.TestEnvironmentGuard.verifySafeStagingTenant(connectionManager);
        repository = new PostgresLocalPolicyRepository(connectionManager);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        testPolicyCode = "POL_TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    @After
    public void tearDown() throws Exception {
        if (testPolicyCode != null) {
            try (Connection conn = connectionManager.getConnection()) {
                conn.setAutoCommit(true);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM cfg.policies WHERE policy_code = ?")) {
                    ps.setString(1, testPolicyCode);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveLocalPolicy() {
        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode(testPolicyCode);
        policy.setStatus("DRAFT");
        policy.setVersion(1);

        LocalPolicy created = repository.createLocalPolicy(tenantContext, policy);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(testPolicyCode, created.getPolicyCode());
        assertEquals("DRAFT", created.getStatus());

        Optional<LocalPolicy> found = repository.findByCode(tenantContext, testPolicyCode);
        assertTrue(found.isPresent());
        assertEquals(testPolicyCode, found.get().getPolicyCode());
        assertEquals("DRAFT", found.get().getStatus());
    }

    @Test(expected = CollegeResourceConflictException.class)
    public void testDuplicatePolicyConflict() {
        LocalPolicy policy1 = new LocalPolicy();
        policy1.setPolicyCode(testPolicyCode);
        policy1.setStatus("DRAFT");
        repository.createLocalPolicy(tenantContext, policy1);

        LocalPolicy policy2 = new LocalPolicy();
        policy2.setPolicyCode(testPolicyCode);
        policy2.setStatus("DRAFT");
        repository.createLocalPolicy(tenantContext, policy2);
    }

    @Test
    public void testUpdatePolicyStatus() {
        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode(testPolicyCode);
        policy.setStatus("DRAFT");
        repository.createLocalPolicy(tenantContext, policy);

        LocalPolicy update = new LocalPolicy();
        update.setPolicyCode(testPolicyCode);
        update.setStatus("APPROVED");
        update.setApprovedBy("dean_academic");
        update.setVersion(2);

        LocalPolicy updated = repository.updateLocalPolicy(tenantContext, update);
        assertNotNull(updated);
        assertEquals("APPROVED", updated.getStatus());

        Optional<LocalPolicy> found = repository.findByCode(tenantContext, testPolicyCode);
        assertTrue(found.isPresent());
        assertEquals("APPROVED", found.get().getStatus());
        assertEquals("dean_academic", found.get().getApprovedBy());
    }

    @Test
    public void testListPolicies() {
        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode(testPolicyCode);
        policy.setStatus("DRAFT");
        repository.createLocalPolicy(tenantContext, policy);

        List<LocalPolicy> list = repository.listPolicies(tenantContext);
        assertFalse(list.isEmpty());
        boolean hasPolicy = list.stream().anyMatch(p -> testPolicyCode.equals(p.getPolicyCode()));
        assertTrue("Policy list must contain " + testPolicyCode, hasPolicy);
    }
}
