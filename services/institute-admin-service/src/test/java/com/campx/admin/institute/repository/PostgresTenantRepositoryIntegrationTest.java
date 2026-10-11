package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.InstituteAlreadyExistsException;
import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileConflictException;
import com.campx.admin.institute.model.InstituteModels.Institute;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.*;

import java.sql.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * End-to-end integration tests for {@link PostgresTenantRepository} executing against
 * the live Supabase PostgreSQL database.
 * <p>
 * Validates the full production persistence and security path:
 * <pre>
 * JUnit
 *   ↓
 * PostgresTenantRepository
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
 * core.tenants
 * </pre>
 */
public class PostgresTenantRepositoryIntegrationTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresTenantRepository repository;
    private static UUID platformAdminUserId;
    private static UserSecurityContext platformAdminContext;

    // Set of created tenant IDs for deterministic cleanup
    private static final List<UUID> createdTenantIds = Collections.synchronizedList(new ArrayList<>());
    private static final String TEST_CODE_PREFIX = "PG_";

    @BeforeClass
    public static void setupDatabase() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        connectionManager.initialize();
        repository = new PostgresTenantRepository(connectionManager);

        // Find or register an authenticated platform administrator in plat.platform_admins
        try (Connection conn = connectionManager.getConnection()) {
            // First check if an active platform admin already exists
            String findAdminSql = "SELECT user_id FROM plat.platform_admins WHERE status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(findAdminSql)) {
                if (rs.next()) {
                    platformAdminUserId = (UUID) rs.getObject("user_id");
                }
            }

            // If none exists, look up a registered user from auth.users and enroll them
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
    }

    @AfterClass
    public static void cleanupDatabase() {
        if (connectionManager != null) {
            try (Connection conn = connectionManager.getConnection()) {
                // Soft-delete test tenants to respect the immutable audit trail (sys.forbid_mutation)
                // All repository operations filter WHERE deleted_at IS NULL, ensuring complete isolation
                try (PreparedStatement psTenants = conn.prepareStatement(
                        "UPDATE core.tenants SET deleted_at = now(), status = 'INACTIVE' WHERE code LIKE ? AND deleted_at IS NULL")) {
                    psTenants.setString(1, TEST_CODE_PREFIX + "%");
                    int count = psTenants.executeUpdate();
                    System.out.println("[PostgreSQL Cleanup] Soft-deleted " + count + " test records from core.tenants");
                }
            } catch (SQLException e) {
                System.err.println("[PostgreSQL Cleanup Error] " + e.getMessage());
            }
        }
    }

    private Institute buildTestInstitute(String suffix) {
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        String code = "PG_" + suffix.toUpperCase(Locale.ROOT) + "_" + rand;
        if (code.length() > 30) {
            code = code.substring(0, 30);
        }
        Institute inst = new Institute();
        inst.setInstituteCode(code);
        inst.setDisplayName("Postgres Test " + code);
        inst.setLegalName("Postgres Legal " + code);
        inst.setTimezone("Asia/Kolkata");
        inst.setLocale("en-IN");
        inst.setDefaultCurrency("INR");
        inst.setStatus("ACTIVE");
        return inst;
    }

    // =========================================================================
    // TEST A — CREATE: Physical insert into core.tenants
    // =========================================================================
    @Test
    public void testA_CreateInstitutePersistsPhysicallyInPostgres() throws Exception {
        Institute input = buildTestInstitute("CREATE");
        Institute created = repository.createInstitute(platformAdminContext, input);

        assertNotNull("Created institute ID must not be null", created.getId());
        UUID createdUuid = UUID.fromString(created.getId());
        createdTenantIds.add(createdUuid);

        assertEquals(input.getInstituteCode(), created.getInstituteCode());
        assertEquals(input.getDisplayName(), created.getDisplayName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(1, created.getVersion());
        assertTrue(created.getCreatedAt() > 0);

        // Independent JDBC SELECT to prove the row physically exists in PostgreSQL core.tenants
        try (Connection directConn = connectionManager.getConnection();
             PreparedStatement ps = directConn.prepareStatement(
                     "SELECT id, code, name, legal_name, timezone, locale, currency_code, status, row_version, created_at "
                     + "FROM core.tenants WHERE id = ?")) {
            ps.setObject(1, createdUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("Physical row must exist in core.tenants", rs.next());
                assertEquals(created.getId(), rs.getObject("id").toString());
                assertEquals(input.getInstituteCode(), rs.getString("code"));
                assertEquals(input.getDisplayName(), rs.getString("name"));
                assertEquals(input.getLegalName(), rs.getString("legal_name"));
                assertEquals(input.getTimezone(), rs.getString("timezone"));
                assertEquals(input.getLocale(), rs.getString("locale"));
                assertEquals(input.getDefaultCurrency(), rs.getString("currency_code"));
                assertEquals("ACTIVE", rs.getString("status"));
                assertEquals(1, rs.getInt("row_version"));
                assertNotNull(rs.getTimestamp("created_at"));
            }
        }
    }

    // =========================================================================
    // TEST B — GET: Retrieve persisted row through PostgresTenantRepository
    // =========================================================================
    @Test
    public void testB_GetInstituteByIdRetrievesFromPostgres() {
        Institute input = buildTestInstitute("GET");
        Institute created = repository.createInstitute(platformAdminContext, input);
        createdTenantIds.add(UUID.fromString(created.getId()));

        Optional<Institute> fetchedOpt = repository.getInstituteById(platformAdminContext, created.getId());
        assertTrue("Institute must be retrieved from PostgreSQL", fetchedOpt.isPresent());

        Institute fetched = fetchedOpt.get();
        assertEquals(created.getId(), fetched.getId());
        assertEquals(created.getInstituteCode(), fetched.getInstituteCode());
        assertEquals(created.getDisplayName(), fetched.getDisplayName());
        assertEquals(created.getLegalName(), fetched.getLegalName());
        assertEquals(created.getTimezone(), fetched.getTimezone());
        assertEquals(created.getLocale(), fetched.getLocale());
        assertEquals(created.getDefaultCurrency(), fetched.getDefaultCurrency());
        assertEquals(1, fetched.getVersion());
    }

    // =========================================================================
    // TEST C — LIST: Query list through repository and find created row
    // =========================================================================
    @Test
    public void testC_ListInstitutesIncludesCreatedRecord() {
        Institute input = buildTestInstitute("LIST");
        Institute created = repository.createInstitute(platformAdminContext, input);
        createdTenantIds.add(UUID.fromString(created.getId()));

        List<Institute> list = repository.listInstitutes(platformAdminContext, "ACTIVE", 1, 100);
        assertNotNull(list);
        assertFalse(list.isEmpty());

        boolean found = false;
        for (Institute inst : list) {
            if (created.getId().equals(inst.getId())) {
                found = true;
                assertEquals(input.getInstituteCode(), inst.getInstituteCode());
                break;
            }
        }
        assertTrue("Newly created institute must be present in repository list", found);
    }

    // =========================================================================
    // TEST D — DUPLICATE CODE: PostgreSQL unique constraint enforcement
    // =========================================================================
    @Test(expected = InstituteAlreadyExistsException.class)
    public void testD_DuplicateCodeProducesConflictFromPostgresConstraint() {
        Institute input1 = buildTestInstitute("DUP");
        Institute created1 = repository.createInstitute(platformAdminContext, input1);
        createdTenantIds.add(UUID.fromString(created1.getId()));

        // Attempt second insert with EXACT duplicate institute code
        Institute input2 = buildTestInstitute("DUP_COPY");
        input2.setInstituteCode(input1.getInstituteCode()); // duplicate!

        repository.createInstitute(platformAdminContext, input2);
    }

    // =========================================================================
    // TEST E — UPDATE: Real row update and row_version increment
    // =========================================================================
    @Test
    public void testE_UpdateInstituteModifiesDatabaseAndIncrementsVersion() throws Exception {
        Institute created = repository.createInstitute(platformAdminContext, buildTestInstitute("UPDATE"));
        UUID createdUuid = UUID.fromString(created.getId());
        createdTenantIds.add(createdUuid);
        assertEquals(1, created.getVersion());

        Institute update = new Institute();
        update.setDisplayName("Updated Display Name");
        update.setTimezone("America/New_York");
        update.setStatus("ACTIVE");

        Institute updated = repository.updateInstitute(platformAdminContext, created.getId(), update, 1);
        assertEquals("Updated Display Name", updated.getDisplayName());
        assertEquals("America/New_York", updated.getTimezone());
        assertEquals(2, updated.getVersion());

        // Independent JDBC query to verify PostgreSQL table state
        try (Connection directConn = connectionManager.getConnection();
             PreparedStatement ps = directConn.prepareStatement(
                     "SELECT name, timezone, row_version, updated_at FROM core.tenants WHERE id = ?")) {
            ps.setObject(1, createdUuid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Updated Display Name", rs.getString("name"));
                assertEquals("America/New_York", rs.getString("timezone"));
                assertEquals(2, rs.getInt("row_version"));
                assertNotNull(rs.getTimestamp("updated_at"));
            }
        }
    }

    // =========================================================================
    // TEST F — OPTIMISTIC LOCKING: Stale row_version rejection
    // =========================================================================
    @Test(expected = UserProfileConflictException.class)
    public void testF_OptimisticLockingRejectsStaleRowVersionInPostgres() {
        Institute created = repository.createInstitute(platformAdminContext, buildTestInstitute("OPT"));
        createdTenantIds.add(UUID.fromString(created.getId()));

        Institute update1 = new Institute();
        update1.setDisplayName("First Concurrent Update");
        repository.updateInstitute(platformAdminContext, created.getId(), update1, 1);

        // Second update still expects version 1, but database is now version 2
        Institute update2 = new Institute();
        update2.setDisplayName("Stale Second Update");
        repository.updateInstitute(platformAdminContext, created.getId(), update2, 1);
    }

    // =========================================================================
    // TEST G — INVALID UUID: Safe pre-SQL validation preventing 22P02
    // =========================================================================
    @Test
    public void testG_InvalidUuidReturnsNotFoundWithoutPostgres22P02Error() {
        // Must throw InstituteNotFoundException, NOT PSQLException 22P02
        try {
            repository.updateInstitute(platformAdminContext, "INVALID-NON-UUID-STRING", new Institute(), 1);
            fail("Expected InstituteNotFoundException for malformed UUID");
        } catch (InstituteNotFoundException ex) {
            assertEquals("ADM01_RESOURCE_NOT_FOUND", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("INVALID-NON-UUID-STRING"));
        }

        Optional<Institute> opt = repository.getInstituteById(platformAdminContext, "MALFORMED-UUID-GET");
        assertFalse("Malformed UUID must safely return empty Optional without SQL error", opt.isPresent());
    }

    // =========================================================================
    // TEST H — PLATFORM ADMIN AUTHORIZATION: Verified via plat.platform_admins
    // =========================================================================
    @Test
    public void testH_PlatformAdminAuthorizationVerifiedAgainstDatabase() {
        // 1. Authorized user present in plat.platform_admins succeeds
        Institute created = repository.createInstitute(platformAdminContext, buildTestInstitute("AUTH_OK"));
        assertNotNull(created.getId());
        createdTenantIds.add(UUID.fromString(created.getId()));

        // 2. User absent from plat.platform_admins is rejected with 403
        UUID unauthorizedUserId = UUID.randomUUID();
        UserSecurityContext unauthorizedContext = UserSecurityContext.forPlatformAdmin(unauthorizedUserId);

        try {
            repository.createInstitute(unauthorizedContext, buildTestInstitute("AUTH_DENIED"));
            fail("Expected UserProfileAccessDeniedException for non-platform admin");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue("Exception message should indicate lack of privilege",
                    ex.getMessage().contains("lacks required platform administrator privilege"));
        }
    }

    // =========================================================================
    // TEST I — RLS / TENANT CONTEXT: Row-Level Security isolation check
    // =========================================================================
    @Test
    public void testI_RowLevelSecurityEnforcesTenantIsolation() {
        // Platform admin creates tenant
        Institute created = repository.createInstitute(platformAdminContext, buildTestInstitute("RLS_ISO"));
        UUID createdTenantId = UUID.fromString(created.getId());
        createdTenantIds.add(createdTenantId);

        // A regular tenant admin from a DIFFERENT tenant attempts to read via RLS
        UUID differentTenantId = UUID.randomUUID();
        UUID regularUserId = UUID.randomUUID();
        UserSecurityContext regularTenantContext = new UserSecurityContext(regularUserId, differentTenantId, false);

        // Under RLS policy tenants_read: id = core.current_tenant_id() OR is_platform_admin()
        // Because caller belongs to differentTenantId and is NOT platform admin, PostgreSQL RLS filters out the row!
        Optional<Institute> readOpt = repository.getInstituteById(regularTenantContext, created.getId());
        assertFalse("Cross-tenant reading by non-platform admin must return empty due to PostgreSQL RLS", readOpt.isPresent());
    }
}
