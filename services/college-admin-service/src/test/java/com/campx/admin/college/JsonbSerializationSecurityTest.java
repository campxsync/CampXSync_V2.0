package com.campx.admin.college;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.model.CollegeModels.CollegeProfile;
import com.campx.admin.college.model.CollegeModels.CollegeSetting;
import com.campx.admin.college.model.CollegeModels.LocalPolicy;
import com.campx.admin.college.repository.PostgresCollegeProfileRepository;
import com.campx.admin.college.repository.PostgresCollegeSettingRepository;
import com.campx.admin.college.repository.PostgresLocalPolicyRepository;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Regression security tests for Finding 7:
 * Verifies Jackson ObjectMapper serialization prevents JSON injection into PostgreSQL JSONB columns.
 * Covers quotes, backslashes, control characters, and attempted duplicate-key / field override attacks.
 */
public class JsonbSerializationSecurityTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresCollegeSettingRepository settingRepository;
    private PostgresLocalPolicyRepository policyRepository;
    private PostgresCollegeProfileRepository profileRepository;

    private UserSecurityContext context;
    private UUID tenantId;
    private String testSettingKey;
    private String testPolicyCode;

    @Before
    public void setUp() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        tenantId = com.campx.admin.college.config.TestEnvironmentGuard.verifySafeStagingTenant(connectionManager);
        settingRepository = new PostgresCollegeSettingRepository(connectionManager);
        policyRepository = new PostgresLocalPolicyRepository(connectionManager);
        profileRepository = new PostgresCollegeProfileRepository(connectionManager);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        context = new UserSecurityContext(adminUserId, tenantId);

        testSettingKey = "sec_test_" + UUID.randomUUID().toString().substring(0, 8);
        testPolicyCode = "POL_SEC_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @After
    public void tearDown() throws Exception {
        if (tenantId != null) {
            try (Connection conn = connectionManager.getConnection()) {
                conn.setAutoCommit(true);
                if (testSettingKey != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM cfg.setting_values WHERE setting_key = ? AND tenant_id = ?")) {
                        ps.setString(1, testSettingKey);
                        ps.setObject(2, tenantId);
                        ps.executeUpdate();
                    }
                }
                if (testPolicyCode != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM cfg.policies WHERE policy_code = ?")) {
                        ps.setString(1, testPolicyCode);
                        ps.executeUpdate();
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testSettingJsonInjectionAttemptDoesNotOverrideSecretFlag() {
        // Attempt injection payload trying to override "secret": true with "secret": false
        String maliciousPayload = "safe_value\",\"secret\":false,\"injected\":\"true";

        CollegeSetting setting = new CollegeSetting();
        setting.setKey(testSettingKey);
        setting.setValue(maliciousPayload);
        setting.setSecret(true); // Must remain true!
        setting.setDataType("STRING");

        // Storing setting must not fail SQL or JSON parsing
        CollegeSetting stored = settingRepository.storeSetting(context, setting);
        assertNotNull(stored);
        assertTrue("Setting must retain true secret status", stored.isSecret());

        // Read directly from repository to verify JSONB integrity in PostgreSQL
        Optional<CollegeSetting> fetched = settingRepository.findByKey(context, testSettingKey);
        assertTrue(fetched.isPresent());
        assertTrue("Secret flag must NOT be overridden by injected duplicate key", fetched.get().isSecret());
        assertEquals("Value must be masked in read responses", "********", fetched.get().getValue());
    }

    @Test
    public void testSettingWithQuotesBackslashesAndControlCharacters() {
        String complexValue = "Path: C:\\Users\\Administrator\\\"CampX\"\\data\nLine 2\tTabbed\r\nLine 3";

        CollegeSetting setting = new CollegeSetting();
        setting.setKey(testSettingKey);
        setting.setValue(complexValue);
        setting.setSecret(false); // Non-secret to verify exact string preservation
        setting.setDataType("STRING");

        CollegeSetting stored = settingRepository.storeSetting(context, setting);
        assertNotNull(stored);

        Optional<CollegeSetting> fetched = settingRepository.findByKey(context, testSettingKey);
        assertTrue(fetched.isPresent());
        // Verify value preserved exactly without syntax error or truncation
        assertEquals(complexValue, fetched.get().getValue());
        assertFalse(fetched.get().isSecret());
    }

    @Test
    public void testLocalPolicyWithMaliciousApprovedByInjection() {
        // Attempt injection through approvedBy field trying to forge status to "APPROVED" or inject keys
        String maliciousApprover = "DEAN_ATTACKER\",\"status\":\"FORGED\",\"version\":999";

        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode(testPolicyCode);
        policy.setStatus("DRAFT"); // Must remain DRAFT!
        policy.setApprovedBy(maliciousApprover);

        LocalPolicy created = policyRepository.createLocalPolicy(context, policy);
        assertNotNull(created);
        assertEquals("DRAFT", created.getStatus());

        Optional<LocalPolicy> fetched = policyRepository.findByCode(context, testPolicyCode);
        assertTrue(fetched.isPresent());
        assertEquals("Status must remain DRAFT and not be forged", "DRAFT", fetched.get().getStatus());
        assertEquals(maliciousApprover, fetched.get().getApprovedBy());
    }
}
