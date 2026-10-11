package com.campx.admin.college;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeResourceNotFoundException;
import com.campx.admin.college.model.CollegeModels.CollegeProfile;
import com.campx.admin.college.repository.CollegeResolver;
import com.campx.admin.college.repository.PostgresCollegeProfileRepository;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live database regression tests for Finding 6:
 * Verifies shared CollegeResolver behavior, ensuring no phantom COL_AUTO records
 * are auto-seeded and that missing colleges return domain errors.
 */
public class CollegeResolverLiveRegressionTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresCollegeProfileRepository profileRepository;
    private UUID tenantId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Requires live JDBC connection", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        profileRepository = new PostgresCollegeProfileRepository(connectionManager);

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
        assumeNotNull("Requires active tenant in database", tenantId);
    }

    @Test
    public void testResolvesExistingCollegeForAuthorizedTenant() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            UUID resolvedId = CollegeResolver.resolveCollegeId(conn, tenantId);
            assertNotNull("Existing college must be resolved for active tenant", resolvedId);
        }
    }

    @Test
    public void testUnprovisionedTenantThrowsNotFoundAndDoesNotSeedPhantomRecord() throws Exception {
        UUID phantomTenantId = UUID.randomUUID();

        try (Connection conn = connectionManager.getConnection()) {
            try {
                CollegeResolver.resolveCollegeId(conn, phantomTenantId);
                fail("Expected CollegeResourceNotFoundException for unprovisioned tenant");
            } catch (CollegeResourceNotFoundException e) {
                assertTrue("Exception must identify missing College", e.getMessage().contains("College"));
            }

            // CRITICAL CHECK: Verify no phantom COL_AUTO row was created
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM core.colleges WHERE tenant_id = ? AND code = 'COL_AUTO'")) {
                ps.setObject(1, phantomTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Zero phantom COL_AUTO records must be seeded", 0, rs.getInt(1));
                }
            }
        }
    }
}
