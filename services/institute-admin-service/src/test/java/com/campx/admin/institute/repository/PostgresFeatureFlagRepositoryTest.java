package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.FeatureFlag;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Integration test for {@link PostgresFeatureFlagRepository} verifying persistence to {@code cfg.feature_flags} (ADM-01 Item 5).
 */
public class PostgresFeatureFlagRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresFeatureFlagRepository repository;
    private UserSecurityContext platformContext;
    private String testFlagKey;

    @Before
    public void setUp() {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Requires live JDBC configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresFeatureFlagRepository(connectionManager);

        // Platform admin context
        UUID adminId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        platformContext = UserSecurityContext.forPlatformAdmin(adminId);
    }

    @After
    public void tearDown() {
        if (testFlagKey != null) {
            try {
                repository.deleteFlag(platformContext, testFlagKey);
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testSaveAndRetrieveFeatureFlag() {
        testFlagKey = "test.feature." + System.currentTimeMillis();
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey(testFlagKey);
        flag.setDescription("Integration test feature flag toggle");
        flag.setStatus("ACTIVE");

        FeatureFlag saved = repository.saveFlag(platformContext, flag);
        assertNotNull(saved.getId());
        assertEquals(testFlagKey, saved.getFlagKey());

        Optional<FeatureFlag> found = repository.getFlagByKey(platformContext, testFlagKey);
        assertTrue(found.isPresent());
        assertEquals(testFlagKey, found.get().getFlagKey());
        assertEquals("ACTIVE", found.get().getStatus());
    }
}
