package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.InstituteAlreadyExistsException;
import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileConflictException;
import com.campx.admin.institute.model.InstituteModels.College;
import com.campx.admin.institute.model.InstituteModels.Institute;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.*;

import java.sql.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * End-to-end integration tests for {@link PostgresCollegeRepository} executing against
 * the live Supabase PostgreSQL database.
 * <p>
 * Validates the full production persistence and security path:
 * <pre>
 * JUnit
 *   ↓
 * College Domain / Service
 *   ↓
 * PostgresCollegeRepository
 *   ↓
 * HikariCP DataSource (DatabaseConnectionManager)
 *   ↓
 * JDBC PreparedStatement
 *   ↓
 * UserSecurityContext.executeInTransaction
 *   ↓
 * SET LOCAL ROLE authenticated + request.jwt.claims
 *   ↓
 * PostgreSQL Row-Level Security (RLS) kernel policies
 *   ↓
 * core.colleges
 * </pre>
 */
public class PostgresCollegeRepositoryIntegrationTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresTenantRepository tenantRepository;
    private static PostgresCollegeRepository repository;
    private static UUID platformAdminUserId;
    private static UserSecurityContext platformAdminContext;

    private static UUID testTenantId;
    private static UserSecurityContext tenantAdminContext;

    private static UUID testTenantBId;
    private static UserSecurityContext tenantBContext;

    private static final String TEST_COL_PREFIX = "PG_COL_";
    private static final String TEST_TENANT_PREFIX = "PG_CTN_";

    @BeforeClass
    public static void setupDatabase() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        connectionManager.initialize();
        tenantRepository = new PostgresTenantRepository(connectionManager);
        repository = new PostgresCollegeRepository(connectionManager);

        // 1. Find or enroll an authenticated platform administrator in plat.platform_admins
        try (Connection conn = connectionManager.getConnection()) {
            String findAdminSql = "SELECT user_id FROM plat.platform_admins WHERE status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(findAdminSql)) {
                if (rs.next()) {
                    platformAdminUserId = (UUID) rs.getObject("user_id");
                }
            }

            if (platformAdminUserId == null) {
                String findAuthUserSql = "SELECT id FROM auth.users LIMIT 1";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(findAuthUserSql)) {
                    if (rs.next()) {
                        platformAdminUserId = (UUID) rs.getObject("id");
                    }
                }

                if (platformAdminUserId != null) {
                    String insertAdminSql = "INSERT INTO plat.platform_admins (user_id, full_name, role_code, status) "
                            + "VALUES (?, 'CampXSync Platform Administrator', 'SUPER_ADMIN', 'ACTIVE')";
                    try (PreparedStatement ps = conn.prepareStatement(insertAdminSql)) {
                        ps.setObject(1, platformAdminUserId);
                        ps.executeUpdate();
                    }
                }
            }
        }

        assertNotNull("Must have an active user in auth.users / plat.platform_admins for testing", platformAdminUserId);
        platformAdminContext = UserSecurityContext.forPlatformAdmin(platformAdminUserId);

        // 2. Resolve primary test tenant (CAMPXSYNC demo tenant where platformAdminUserId is assigned TENANT_ADMIN)
        testTenantId = UUID.fromString("0c914bb2-f63b-472c-a99a-39977112935d");
        tenantAdminContext = new UserSecurityContext(platformAdminUserId, testTenantId, false);

        // 3. Create secondary test tenant in core.tenants for tenant isolation tests
        String randB = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        Institute tenantB = new Institute();
        tenantB.setInstituteCode(TEST_TENANT_PREFIX + "B_" + randB);
        tenantB.setDisplayName("College Test Tenant B " + randB);
        tenantB.setLegalName("Legal College Tenant B " + randB);
        tenantB.setStatus("ACTIVE");
        Institute createdTenantB = tenantRepository.createInstitute(platformAdminContext, tenantB);
        testTenantBId = UUID.fromString(createdTenantB.getId());

        tenantBContext = new UserSecurityContext(UUID.randomUUID(), testTenantBId, false);
    }

    @AfterClass
    public static void cleanupDatabase() {
        if (connectionManager != null) {
            try (Connection conn = connectionManager.getConnection()) {
                // Soft-delete test colleges to respect immutable change log trigger sys.forbid_mutation()
                try (PreparedStatement psColleges = conn.prepareStatement(
                        "UPDATE core.colleges SET deleted_at = now(), status = 'INACTIVE' WHERE code LIKE ? AND deleted_at IS NULL")) {
                    psColleges.setString(1, TEST_COL_PREFIX + "%");
                    int count = psColleges.executeUpdate();
                    System.out.println("[PostgreSQL Cleanup] Soft-deleted " + count + " test colleges from core.colleges");
                }

                // Soft-delete test tenants
                try (PreparedStatement psTenants = conn.prepareStatement(
                        "UPDATE core.tenants SET deleted_at = now(), status = 'INACTIVE' WHERE code LIKE ? AND deleted_at IS NULL")) {
                    psTenants.setString(1, TEST_TENANT_PREFIX + "%");
                    int countT = psTenants.executeUpdate();
                    System.out.println("[PostgreSQL Cleanup] Soft-deleted " + countT + " test tenants from core.tenants");
                }
            } catch (SQLException e) {
                System.err.println("[PostgreSQL Cleanup Error] " + e.getMessage());
            }
        }
    }

    private College buildTestCollege(String suffix, UUID parentTenantId) {
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        String code = TEST_COL_PREFIX + suffix.toUpperCase(Locale.ROOT) + "_" + rand;
        if (code.length() > 30) {
            code = code.substring(0, 30);
        }
        College col = new College();
        col.setCollegeCode(code);
        col.setName("Postgres College " + code);
        col.setLegalName("Postgres Legal " + code);
        col.setInstituteId(parentTenantId.toString());
        col.setStatus("ACTIVE");
        return col;
    }

    // =========================================================================
    // TEST A — CREATE: Physical insert into core.colleges
    // =========================================================================
    @Test
    public void testA_CreateCollegePersistsPhysicallyInPostgres() throws Exception {
        College input = buildTestCollege("CREATE", testTenantId);
        College created = repository.createCollege(tenantAdminContext, input);

        assertNotNull("Created college ID must not be null", created.getId());
        UUID createdUuid = UUID.fromString(created.getId());

        assertEquals(input.getCollegeCode(), created.getCollegeCode());
        assertEquals(input.getName(), created.getName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(testTenantId.toString(), created.getInstituteId());
        assertEquals(1, created.getVersion());
        assertTrue(created.getCreatedAt() > 0);

        // Independent JDBC SELECT to prove the row physically exists in PostgreSQL core.colleges
        try (Connection directConn = connectionManager.getConnection();
             PreparedStatement ps = directConn.prepareStatement(
                     "SELECT id, tenant_id, code, name, legal_name, status, provisioning_status, row_version, created_at "
                     + "FROM core.colleges WHERE id = ?")) {
            ps.setObject(1, createdUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("Physical row must exist in core.colleges", rs.next());
                assertEquals(created.getId(), rs.getObject("id").toString());
                assertEquals(testTenantId.toString(), rs.getObject("tenant_id").toString());
                assertEquals(input.getCollegeCode(), rs.getString("code"));
                assertEquals(input.getName(), rs.getString("name"));
                assertEquals(input.getLegalName(), rs.getString("legal_name"));
                assertEquals("ACTIVE", rs.getString("status"));
                assertEquals(1, rs.getInt("row_version"));
                assertNotNull(rs.getTimestamp("created_at"));
            }
        }
    }

    // =========================================================================
    // TEST B — GET: Retrieve persisted row through PostgresCollegeRepository
    // =========================================================================
    @Test
    public void testB_GetCollegeByIdRetrievesFromPostgres() {
        College input = buildTestCollege("GET", testTenantId);
        College created = repository.createCollege(tenantAdminContext, input);

        Optional<College> fetchedOpt = repository.getCollegeById(tenantAdminContext, created.getId());
        assertTrue("College must be retrieved from PostgreSQL", fetchedOpt.isPresent());

        College fetched = fetchedOpt.get();
        assertEquals(created.getId(), fetched.getId());
        assertEquals(created.getCollegeCode(), fetched.getCollegeCode());
        assertEquals(created.getName(), fetched.getName());
        assertEquals(created.getLegalName(), fetched.getLegalName());
        assertEquals(testTenantId.toString(), fetched.getInstituteId());
        assertEquals(1, fetched.getVersion());
    }

    // =========================================================================
    // TEST C — LIST: Query list through repository and find created row
    // =========================================================================
    @Test
    public void testC_ListCollegesIncludesCreatedRecord() {
        College input = buildTestCollege("LIST", testTenantId);
        College created = repository.createCollege(tenantAdminContext, input);

        List<College> list = repository.listColleges(tenantAdminContext, testTenantId.toString());
        assertNotNull(list);
        assertFalse(list.isEmpty());

        boolean found = false;
        for (College col : list) {
            if (created.getId().equals(col.getId())) {
                found = true;
                assertEquals(input.getCollegeCode(), col.getCollegeCode());
                break;
            }
        }
        assertTrue("Newly created college must be present in repository list", found);
    }

    // =========================================================================
    // TEST D — PARENT TENANT VALIDATION: Non-existent parent tenant is rejected
    // =========================================================================
    @Test(expected = InstituteNotFoundException.class)
    public void testD_NonExistentParentTenantRejected() {
        UUID nonExistentTenantId = UUID.randomUUID();
        College input = buildTestCollege("NO_PRNT", nonExistentTenantId);

        // Platform admin context allowed to target any tenant, but database verification fails because parent does not exist
        repository.createCollege(platformAdminContext, input);
    }

    // =========================================================================
    // TEST E — DUPLICATE COLLEGE CODE: PostgreSQL unique constraint enforcement
    // =========================================================================
    @Test(expected = InstituteAlreadyExistsException.class)
    public void testE_DuplicateCollegeCodeProducesConflictFromPostgresConstraint() {
        College input1 = buildTestCollege("DUP", testTenantId);
        College created1 = repository.createCollege(tenantAdminContext, input1);
        assertNotNull(created1.getId());

        // Attempt second insert with duplicate code within same tenant
        College input2 = buildTestCollege("DUP_COPY", testTenantId);
        input2.setCollegeCode(input1.getCollegeCode());

        repository.createCollege(tenantAdminContext, input2);
    }

    // =========================================================================
    // TEST F — UPDATE: Real row update and row_version increment
    // =========================================================================
    @Test
    public void testF_UpdateCollegeModifiesDatabaseAndIncrementsVersion() throws Exception {
        College created = repository.createCollege(tenantAdminContext, buildTestCollege("UPDATE", testTenantId));
        UUID createdUuid = UUID.fromString(created.getId());
        assertEquals(1, created.getVersion());

        College update = new College();
        update.setName("Updated College Display Name");
        update.setStatus("INACTIVE");

        College updated = repository.updateCollege(tenantAdminContext, created.getId(), update, 1);
        assertEquals("Updated College Display Name", updated.getName());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals(2, updated.getVersion());

        // Independent JDBC query to verify physical PostgreSQL table state
        try (Connection directConn = connectionManager.getConnection();
             PreparedStatement ps = directConn.prepareStatement(
                     "SELECT name, status, row_version, updated_at FROM core.colleges WHERE id = ?")) {
            ps.setObject(1, createdUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Updated College Display Name", rs.getString("name"));
                assertEquals("INACTIVE", rs.getString("status"));
                assertEquals(2, rs.getInt("row_version"));
                assertNotNull(rs.getTimestamp("updated_at"));
            }
        }
    }

    // =========================================================================
    // TEST G — OPTIMISTIC LOCKING: Stale row_version rejection
    // =========================================================================
    @Test(expected = UserProfileConflictException.class)
    public void testG_OptimisticLockingRejectsStaleRowVersionInPostgres() {
        College created = repository.createCollege(tenantAdminContext, buildTestCollege("OPT", testTenantId));

        College update1 = new College();
        update1.setName("First Concurrent Update");
        repository.updateCollege(tenantAdminContext, created.getId(), update1, 1);

        // Second update expects version 1, but database is now version 2 -> 409 Conflict
        College update2 = new College();
        update2.setName("Stale Second Update");
        repository.updateCollege(tenantAdminContext, created.getId(), update2, 1);
    }

    // =========================================================================
    // TEST H — TENANT ISOLATION: Cross-tenant operations prevented by PostgreSQL RLS
    // =========================================================================
    @Test
    public void testH_RowLevelSecurityEnforcesTenantIsolation() {
        // College created under Tenant A
        College collegeA = repository.createCollege(tenantAdminContext, buildTestCollege("RLS_A", testTenantId));
        assertNotNull(collegeA.getId());

        // Tenant B caller attempts to read College A via RLS
        // Under RLS policy colleges_select: tenant_id = core.current_tenant_id()
        // Because Tenant B != Tenant A, PostgreSQL RLS hides the row!
        Optional<College> readOpt = repository.getCollegeById(tenantBContext, collegeA.getId());
        assertFalse("Cross-tenant reading by different tenant must return empty due to PostgreSQL RLS", readOpt.isPresent());

        // Tenant B caller attempts to list colleges under Tenant A -> denied
        List<College> crossList = repository.listColleges(tenantBContext, testTenantId.toString());
        assertTrue("Cross-tenant listing must be blocked and return empty", crossList.isEmpty());
    }

    // =========================================================================
    // TEST I — PLATFORM ADMIN AUTHORIZATION
    // =========================================================================
    @Test
    public void testI_PlatformAdminAuthorizationVerifiedAgainstDatabase() {
        // 1. Authorized platform admin can create college under tenant
        College created = repository.createCollege(platformAdminContext, buildTestCollege("PLT_OK", testTenantId));
        assertNotNull(created.getId());

        // 2. Unauthorized caller claiming platform scope is rejected with 403
        UUID unauthorizedUserId = UUID.randomUUID();
        UserSecurityContext unauthorizedContext = UserSecurityContext.forPlatformAdmin(unauthorizedUserId);

        try {
            repository.createCollege(unauthorizedContext, buildTestCollege("PLT_DENY", testTenantId));
            fail("Expected UserProfileAccessDeniedException for non-platform admin");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue("Exception message should indicate lack of platform admin privilege",
                    ex.getMessage().contains("lacks required platform administrator privilege"));
        }
    }

    // =========================================================================
    // TEST J — MALFORMED ID / SAFE UUID HANDLING (Prevents PostgreSQL 22P02)
    // =========================================================================
    @Test
    public void testJ_InvalidUuidReturnsNotFoundWithoutPostgres22P02Error() {
        try {
            repository.updateCollege(tenantAdminContext, "INVALID-NON-UUID-COLLEGE", new College(), 1);
            fail("Expected InstituteNotFoundException for malformed UUID");
        } catch (InstituteNotFoundException ex) {
            assertEquals("ADM01_RESOURCE_NOT_FOUND", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("INVALID-NON-UUID-COLLEGE"));
        }

        Optional<College> opt = repository.getCollegeById(tenantAdminContext, "MALFORMED-UUID-GET");
        assertFalse("Malformed UUID must safely return empty Optional without SQL error", opt.isPresent());
    }
}
