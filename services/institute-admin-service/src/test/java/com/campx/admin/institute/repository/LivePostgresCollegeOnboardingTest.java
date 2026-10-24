package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.OnboardCollegeRequest;
import com.campx.admin.institute.model.OnboardCollegeResponse;
import com.campx.admin.institute.security.UserSecurityContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Live PostgreSQL College Onboarding Integration Test (Wave 9).
 * <p>
 * Verifies the 5-step institutional onboarding saga against live PostgreSQL database:
 * 1. Target protection: Verifies target ref 'azulbaxszsqxymqmlwfe', rejecting prod and staging refs.
 * 2. Platform Admin Authorization: Rejects non-platform callers.
 * 3. Atomic Multi-Table Saga: Verifies core.tenants, core.colleges, iam.roles, iam.user_profiles,
 *    iam.role_assignments, plat.tenant_provisioning, sys.outbox_events, and audit.events.
 * 4. Idempotency Replay: Verifies replaying with the same key returns existing details without duplicating rows.
 * 5. Conflict Detection: Rejects conflicting tenant codes under a different key.
 * 6. 3-Tenant Cross-Boundary Isolation: Onboards 3 distinct tenants and proves RLS prevents data cross-visibility.
 * 7. Transactional Safety: Terminating in conn.rollback(), verifying 0 dirty rows remain.
 */
public class LivePostgresCollegeOnboardingTest {

    private static final String APPROVED_DEV_REF = "azulbaxszsqxymqmlwfe";
    private static final String PROHIBITED_PROD_REF = "ekspahbsbwdpraaqfqui";
    private static final String PROHIBITED_STAGING_REF = "kpwdvvcschankgggitnj";
    private static final String APPROVAL_TOKEN = "APPROVE-WAVE9-ONBOARDING-WRITE";

    private static final UUID PLATFORM_ADMIN_USER_ID = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");

    private HikariDataSource dataSource;

    @Before
    public void setUp() {
        String envUrl = System.getenv("SUPABASE_JDBC_URL");
        String user = System.getenv("SUPABASE_USER");
        String pass = System.getenv("SUPABASE_PASSWORD");

        if (envUrl == null || user == null || pass == null) {
            return;
        }

        // Safety Guardrails: reject production and legacy staging
        if (!user.contains(APPROVED_DEV_REF)) {
            throw new SecurityException("FATAL: Target user does not match approved dev ref: " + APPROVED_DEV_REF);
        }
        if (user.contains(PROHIBITED_PROD_REF) || envUrl.contains(PROHIBITED_PROD_REF)) {
            throw new SecurityException("FATAL SAFETY VIOLATION: Production target detected!");
        }
        if (user.contains(PROHIBITED_STAGING_REF) || envUrl.contains(PROHIBITED_STAGING_REF)) {
            throw new SecurityException("FATAL SAFETY VIOLATION: Prohibited staging target detected!");
        }

        String targetJdbcUrl = envUrl.contains("ap-southeast-1")
                ? envUrl.replace("ap-southeast-1", "ap-south-1")
                : envUrl;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(targetJdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setAutoCommit(false);
        config.setPoolName("LiveCollegeOnboardingPool");

        dataSource = new HikariDataSource(config);
    }

    @After
    public void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    public void testPlatformAdminAuthorizationEnforced() {
        PostgresCollegeOnboardingRepository repo = new PostgresCollegeOnboardingRepository();
        UserSecurityContext nonPlatformContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID(), false);
        OnboardCollegeRequest req = new OnboardCollegeRequest();
        req.setTenantName("Unauthorized Attempt");
        req.setTenantCode("UNAUTH_01");
        req.setCollegeName("Unauthorized College");
        req.setCollegeCode("UNAUTH_COL");
        req.setAdminName("Unauthorized Admin");
        req.setAdminEmail("unauth@campx.edu");

        try {
            repo.onboardCollege(nonPlatformContext, req);
            fail("Expected UserProfileAccessDeniedException for non-platform admin caller");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("platform administrators"));
        }
    }

    @Test
    public void testLiveCollegeOnboardingSagaAndIsolationRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave9.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            System.out.println("=== LIVE COLLEGE ONBOARDING WRITE TEST GATED ===");
            System.out.println("This test requires explicit owner authorization before executing live database writes.");
            System.out.println("Proposed Operation:");
            System.out.println("  - Target Ref: " + APPROVED_DEV_REF);
            System.out.println("  - Tables Written in Transaction: core.tenants, core.colleges, iam.roles, iam.user_profiles, iam.role_assignments, plat.tenant_provisioning, sys.outbox_events, audit.events");
            System.out.println("  - Mode: Uncommitted transaction (autoCommit=false), terminating in rollback()");
            System.out.println("  - To authorize: Run with -Dcampx.approve.wave9.write=true");
            System.out.println("Status: SAFELY HALTED AT WRITE APPROVAL GATE.");
            return;
        }

        assertNotNull("DataSource must be configured", dataSource);

        final String runSuffix = UUID.randomUUID().toString().substring(0, 8);
        final String tenantCodeA = "LIVE_A_" + runSuffix.toUpperCase();
        final String tenantCodeB = "LIVE_B_" + runSuffix.toUpperCase();
        final String tenantCodeC = "LIVE_C_" + runSuffix.toUpperCase();

        try (Connection rawConn = dataSource.getConnection()) {
            rawConn.setAutoCommit(false);

            // Step 1: Target Verification
            try (Statement stmt = rawConn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, inet_server_addr()")) {
                    assertTrue(rs.next());
                    System.out.println("=== LIVE TARGET VERIFIED FOR WAVE 9 ONBOARDING ===");
                    System.out.println("Database: " + rs.getString(1) + " | User: " + rs.getString(2) + " | IP: " + rs.getString(3));
                    assertEquals("postgres", rs.getString(1));
                }
            }

            // Create commit/close-suppressing connection proxy to maintain active uncommitted transaction
            Connection proxyConn = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("commit".equals(method.getName()) || "close".equals(method.getName())
                                || "setAutoCommit".equals(method.getName()) || "rollback".equals(method.getName())) {
                            return null;
                        }
                        return method.invoke(rawConn, args);
                    }
            );

            DatabaseConnectionManager testConnManager = new DatabaseConnectionManager() {
                @Override
                public Connection getConnection() {
                    return proxyConn;
                }
                @Override
                public void initialize() {}
                @Override
                public void close() {}
            };

            PostgresCollegeOnboardingRepository repo = new PostgresCollegeOnboardingRepository(testConnManager);
            UserSecurityContext platformAdminCtx = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN_USER_ID);

            try {
                // =========================================================================
                // Scenario 1: Full Onboarding Saga (Tenant A)
                // =========================================================================
                OnboardCollegeRequest reqA = new OnboardCollegeRequest();
                reqA.setTenantName("Live Institute Alpha " + runSuffix);
                reqA.setTenantCode(tenantCodeA);
                reqA.setCollegeName("Alpha College of Engineering");
                reqA.setCollegeCode("AENG_" + runSuffix.toUpperCase());
                reqA.setAdminName("Dr. Alice Adams");
                reqA.setAdminEmail("alice." + runSuffix + "@campx.edu");
                reqA.setSubdomain("alpha-" + runSuffix);
                reqA.setPlan("ENTERPRISE");
                reqA.setIdempotencyKey("idem-key-alpha-" + runSuffix);

                long startTime = System.currentTimeMillis();
                OnboardCollegeResponse respA = repo.onboardCollege(platformAdminCtx, reqA);
                long duration = System.currentTimeMillis() - startTime;
                System.out.println("[Wave 9 Onboarding] Tenant A created in " + duration + " ms");

                assertNotNull(respA);
                assertNotNull("TenantId must be generated", respA.getTenantId());
                assertNotNull("CollegeId must be generated", respA.getCollegeId());
                assertNotNull("AdminUserId must be generated", respA.getAdminUserId());
                assertEquals("ACTIVE", respA.getStatus());
                assertFalse("Initial creation must not be marked idempotent replay", respA.isIdempotentReplay());
                assertEquals(6, respA.getSagaSteps().size());

                // Direct Verification of Database Records in Transaction
                try (PreparedStatement ps = rawConn.prepareStatement("SELECT status FROM core.tenants WHERE id = ?")) {
                    ps.setObject(1, respA.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Tenant row must exist in core.tenants", rs.next());
                        assertEquals("ACTIVE", rs.getString("status"));
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT status, provisioning_status FROM core.colleges WHERE id = ?")) {
                    ps.setObject(1, respA.getCollegeId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("College row must exist in core.colleges", rs.next());
                        assertEquals("ACTIVE", rs.getString("status"));
                        assertEquals("ACTIVE", rs.getString("provisioning_status"));
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT count(*) FROM iam.roles WHERE tenant_id = ?")) {
                    ps.setObject(1, respA.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        int roleCount = rs.getInt(1);
                        assertTrue("System roles must be seeded (found: " + roleCount + ")", roleCount > 0);
                        System.out.println("[Wave 9 Onboarding] Seeded " + roleCount + " system roles for Tenant A");
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT email, status FROM iam.user_profiles WHERE id = ?")) {
                    ps.setObject(1, respA.getAdminUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Admin profile must exist in iam.user_profiles", rs.next());
                        assertEquals(reqA.getAdminEmail(), rs.getString("email"));
                        assertEquals("ACTIVE", rs.getString("status"));
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT count(*) FROM iam.role_assignments WHERE tenant_id = ? AND user_id = ?")) {
                    ps.setObject(1, respA.getTenantId());
                    ps.setObject(2, respA.getAdminUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("College Admin role assignment must exist", 1, rs.getInt(1));
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT status FROM plat.tenant_provisioning WHERE tenant_id = ?")) {
                    ps.setObject(1, respA.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Provisioning job must exist in plat.tenant_provisioning", rs.next());
                        assertEquals("COMPLETED", rs.getString("status"));
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT count(*) FROM sys.outbox_events WHERE tenant_id = ? AND event_type = 'tenant.onboarded.v1'")) {
                    ps.setObject(1, respA.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Outbox event 'tenant.onboarded.v1' must be registered", 1, rs.getInt(1));
                    }
                }

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT count(*) FROM audit.events WHERE tenant_id = ? AND action = 'TENANT_ONBOARDED'")) {
                    ps.setObject(1, respA.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Audit event 'TENANT_ONBOARDED' must be logged", 1, rs.getInt(1));
                    }
                }

                // =========================================================================
                // Scenario 2: Idempotent Replay
                // =========================================================================
                OnboardCollegeResponse respA2 = repo.onboardCollege(platformAdminCtx, reqA);
                assertNotNull(respA2);
                assertTrue("Replay must be flagged as idempotent", respA2.isIdempotentReplay());
                assertEquals("Replayed tenant ID must match", respA.getTenantId(), respA2.getTenantId());
                assertEquals("Replayed college ID must match", respA.getCollegeId(), respA2.getCollegeId());
                assertEquals("Replayed admin ID must match", respA.getAdminUserId(), respA2.getAdminUserId());

                try (PreparedStatement ps = rawConn.prepareStatement("SELECT count(*) FROM core.tenants WHERE code = ?")) {
                    ps.setString(1, tenantCodeA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Idempotent replay must not create duplicate tenant row", 1, rs.getInt(1));
                    }
                }

                // =========================================================================
                // Scenario 3: Conflict Detection (Duplicate Tenant Code with Different Key)
                // =========================================================================
                OnboardCollegeRequest reqConflict = new OnboardCollegeRequest();
                reqConflict.setTenantName("Conflicting Institute");
                reqConflict.setTenantCode(tenantCodeA); // duplicate code!
                reqConflict.setCollegeName("Different College");
                reqConflict.setCollegeCode("DIF_COL");
                reqConflict.setAdminName("Dr. Different");
                reqConflict.setAdminEmail("diff." + runSuffix + "@campx.edu");
                reqConflict.setIdempotencyKey("diff-key-" + runSuffix);

                try {
                    repo.onboardCollege(platformAdminCtx, reqConflict);
                    fail("Expected ResourceConflictException for duplicate tenant code");
                } catch (ResourceConflictException e) {
                    System.out.println("[Wave 9 Onboarding] Correctly detected conflict: " + e.getMessage());
                }

                // =========================================================================
                // Scenario 4: 3-Tenant Cross-Boundary Isolation
                // =========================================================================
                // Onboard Tenant B
                OnboardCollegeRequest reqB = new OnboardCollegeRequest();
                reqB.setTenantName("Live Institute Beta " + runSuffix);
                reqB.setTenantCode(tenantCodeB);
                reqB.setCollegeName("Beta College of Pharmacy");
                reqB.setCollegeCode("BPHARM_" + runSuffix.toUpperCase());
                reqB.setAdminName("Dr. Bob Builder");
                reqB.setAdminEmail("bob." + runSuffix + "@campx.edu");
                reqB.setIdempotencyKey("idem-key-beta-" + runSuffix);

                OnboardCollegeResponse respB = repo.onboardCollege(platformAdminCtx, reqB);
                assertNotNull(respB);
                assertNotEquals(respA.getTenantId(), respB.getTenantId());

                // Onboard Tenant C
                OnboardCollegeRequest reqC = new OnboardCollegeRequest();
                reqC.setTenantName("Live Institute Gamma " + runSuffix);
                reqC.setTenantCode(tenantCodeC);
                reqC.setCollegeName("Gamma College of Management");
                reqC.setCollegeCode("GMGT_" + runSuffix.toUpperCase());
                reqC.setAdminName("Dr. Carol Clark");
                reqC.setAdminEmail("carol." + runSuffix + "@campx.edu");
                reqC.setIdempotencyKey("idem-key-gamma-" + runSuffix);

                OnboardCollegeResponse respC = repo.onboardCollege(platformAdminCtx, reqC);
                assertNotNull(respC);
                assertNotEquals(respA.getTenantId(), respC.getTenantId());
                assertNotEquals(respB.getTenantId(), respC.getTenantId());

                System.out.println("=== 3 DISTINCT DEVELOPMENT TENANTS PROVISIONED IN TRANSACTION ===");
                System.out.println("Tenant A: " + respA.getTenantId() + " (" + tenantCodeA + ")");
                System.out.println("Tenant B: " + respB.getTenantId() + " (" + tenantCodeB + ")");
                System.out.println("Tenant C: " + respC.getTenantId() + " (" + tenantCodeC + ")");

                // Assert RLS Multi-Tenant Isolation
                try (Statement stmt = rawConn.createStatement()) {
                    stmt.execute("SET LOCAL ROLE authenticated");

                    // 4a. Tenant A Claims Evaluation
                    String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                            respA.getAdminUserId(), respA.getTenantId(), respA.getTenantId());
                    stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claimsA));
                    stmt.execute(String.format("SELECT set_config('request.jwt.claim.tenant_id', '%s', true)", respA.getTenantId()));
                    stmt.execute(String.format("SELECT set_config('request.jwt.claim.sub', '%s', true)", respA.getAdminUserId()));
                    stmt.execute("SELECT set_config('request.jwt.claim.role', 'authenticated', true)");

                    try (ResultSet rs = stmt.executeQuery("SELECT core.current_tenant_id(), auth.uid()")) {
                        if (rs.next()) {
                            System.out.println("DEBUG current_tenant_id=" + rs.getObject(1) + ", auth.uid=" + rs.getObject(2));
                        }
                    }

                    List<String> visibleEmailsUnderA = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery("SELECT email FROM iam.user_profiles WHERE tenant_id IN ('" + respA.getTenantId() + "','" + respB.getTenantId() + "','" + respC.getTenantId() + "')")) {
                        while (rs.next()) {
                            visibleEmailsUnderA.add(rs.getString("email"));
                        }
                    }
                    assertEquals("Tenant A caller must see only Tenant A admin profile", 1, visibleEmailsUnderA.size());
                    assertEquals(reqA.getAdminEmail(), visibleEmailsUnderA.get(0));

                    // 4b. Tenant B Claims Evaluation
                    String claimsB = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                            respB.getAdminUserId(), respB.getTenantId(), respB.getTenantId());
                    stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claimsB));
                    stmt.execute(String.format("SELECT set_config('request.jwt.claim.tenant_id', '%s', true)", respB.getTenantId()));
                    stmt.execute(String.format("SELECT set_config('request.jwt.claim.sub', '%s', true)", respB.getAdminUserId()));
                    stmt.execute("SELECT set_config('request.jwt.claim.role', 'authenticated', true)");

                    List<String> visibleEmailsUnderB = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery("SELECT email FROM iam.user_profiles WHERE tenant_id IN ('" + respA.getTenantId() + "','" + respB.getTenantId() + "','" + respC.getTenantId() + "')")) {
                        while (rs.next()) {
                            visibleEmailsUnderB.add(rs.getString("email"));
                        }
                    }
                    assertEquals("Tenant B caller must see only Tenant B admin profile", 1, visibleEmailsUnderB.size());
                    assertEquals(reqB.getAdminEmail(), visibleEmailsUnderB.get(0));

                    // 4c. Tenant C Claims Evaluation
                    String claimsC = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                            respC.getAdminUserId(), respC.getTenantId(), respC.getTenantId());
                    stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claimsC));
                    stmt.execute(String.format("SELECT set_config('request.jwt.claim.tenant_id', '%s', true)", respC.getTenantId()));
                    stmt.execute(String.format("SELECT set_config('request.jwt.claim.sub', '%s', true)", respC.getAdminUserId()));
                    stmt.execute("SELECT set_config('request.jwt.claim.role', 'authenticated', true)");

                    List<String> visibleEmailsUnderC = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery("SELECT email FROM iam.user_profiles WHERE tenant_id IN ('" + respA.getTenantId() + "','" + respB.getTenantId() + "','" + respC.getTenantId() + "')")) {
                        while (rs.next()) {
                            visibleEmailsUnderC.add(rs.getString("email"));
                        }
                    }
                    assertEquals("Tenant C caller must see only Tenant C admin profile", 1, visibleEmailsUnderC.size());
                    assertEquals(reqC.getAdminEmail(), visibleEmailsUnderC.get(0));

                    // Reset role back to session user
                    stmt.execute("RESET ROLE");
                }

                System.out.println("=== 3-TENANT CROSS-BOUNDARY RLS ISOLATION VERIFIED ===");

            } finally {
                // =========================================================================
                // Scenario 5: Clean Rollback Guarantee (Zero Dirty Rows)
                // =========================================================================
                rawConn.rollback();
                System.out.println("=== UNCONDITIONAL TRANSACTION ROLLBACK EXECUTED ===");
            }

            // Post-Rollback Cleanliness Verification on fresh query
            try (Statement checkStmt = rawConn.createStatement()) {
                try (ResultSet rs = checkStmt.executeQuery(
                        "SELECT count(*) FROM core.tenants WHERE code IN ('" + tenantCodeA + "','" + tenantCodeB + "','" + tenantCodeC + "')")) {
                    assertTrue(rs.next());
                    int leftoverRows = rs.getInt(1);
                    assertEquals("All test tenant rows must be rolled back cleanly (0 residual rows)", 0, leftoverRows);
                    System.out.println("[Wave 9 Onboarding] Verified 0 dirty rows remain after rollback");
                }
            }
        }
    }
}
