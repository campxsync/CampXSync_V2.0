package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.GlobalPolicy;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Integration test for {@link PostgresGlobalPolicyRepository} verifying persistence to {@code cfg.policies} (ADM-01 Item 6).
 */
public class PostgresGlobalPolicyRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresGlobalPolicyRepository repository;
    private UserSecurityContext platformContext;
    private String testPolicyCode;

    @Before
    public void setUp() {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Requires live JDBC configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresGlobalPolicyRepository(connectionManager);

        UUID adminId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        platformContext = UserSecurityContext.forPlatformAdmin(adminId);
    }

    @After
    public void tearDown() {
        if (testPolicyCode != null) {
            try {
                repository.deletePolicy(platformContext, testPolicyCode);
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testSaveAndRetrieveGlobalPolicy() {
        testPolicyCode = "POL_TEST_" + System.currentTimeMillis();
        GlobalPolicy policy = new GlobalPolicy();
        policy.setPolicyCode(testPolicyCode);
        policy.setPolicyType("GOVERNANCE");
        policy.setRules(Arrays.asList("RULE_ENFORCE_MFA", "RULE_SESSION_30M"));

        GlobalPolicy saved = repository.savePolicy(platformContext, policy);
        assertNotNull(saved.getId());
        assertEquals(testPolicyCode, saved.getPolicyCode());

        Optional<GlobalPolicy> found = repository.getPolicyByCode(platformContext, testPolicyCode);
        assertTrue(found.isPresent());
        assertEquals(testPolicyCode, found.get().getPolicyCode());
        assertEquals("GOVERNANCE", found.get().getPolicyType());
    }
}
