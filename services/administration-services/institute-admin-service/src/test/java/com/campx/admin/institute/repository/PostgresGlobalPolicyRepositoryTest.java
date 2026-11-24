package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.GlobalPolicy;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * After IADM-02 cutover, institute-admin {@code cfg.policies} writers are retired.
 */
public class PostgresGlobalPolicyRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresGlobalPolicyRepository repository;
    private UserSecurityContext platformContext;
    private String testPolicyCode;

    @Before
    public void setUp() {
        connectionManager = DatabaseConnectionManager.getInstance();
        com.campx.admin.institute.config.TestEnvironmentGuard.verifySafeStagingEnvironment(connectionManager);

        repository = new PostgresGlobalPolicyRepository(connectionManager);

        UUID adminId = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
        platformContext = UserSecurityContext.forPlatformAdmin(adminId);
    }

    @After
    public void tearDown() {
        if (connectionManager == null || connectionManager.isClosed() || testPolicyCode == null) {
            return;
        }
        try {
            repository.deletePolicy(platformContext, testPolicyCode);
        } catch (Exception ignored) {}
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testSavePolicyRetiredAfterIadm02Cutover() {
        testPolicyCode = "POL_TEST_" + System.currentTimeMillis();
        GlobalPolicy policy = new GlobalPolicy();
        policy.setPolicyCode(testPolicyCode);
        policy.setPolicyType("GOVERNANCE");
        policy.setRules(Arrays.asList("RULE_ENFORCE_MFA", "RULE_SESSION_30M"));

        repository.savePolicy(platformContext, policy);
    }
}
