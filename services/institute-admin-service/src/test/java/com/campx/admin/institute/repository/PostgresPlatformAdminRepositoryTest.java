package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.PlatformAdmin;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Live integration tests for Item 16: {@link PostgresPlatformAdminRepository} backed by Supabase PostgreSQL.
 * Verifies platform admin persistence, role assignment, update, soft-delete, and RLS kernel isolation.
 */
public class PostgresPlatformAdminRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresPlatformAdminRepository repository;
    private static UserSecurityContext platformAdminContext;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdUserIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresPlatformAdminRepository(connectionManager);
        platformAdminContext = UserSecurityContext.forPlatformAdmin(SUPER_ADMIN_UUID);
    }

    private static UUID createTestAuthUser() throws Exception {
        UUID uid = UUID.randomUUID();
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO auth.users (id, email) VALUES (?, ?)")) {
                ps.setObject(1, uid);
                ps.setString(2, "test_admin_" + uid.toString().substring(0, 8) + "@campx.local");
                ps.executeUpdate();
            }
        }
        createdUserIds.add(uid);
        return uid;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (createdUserIds.isEmpty()) return;
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID uid : createdUserIds) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM plat.platform_admins WHERE user_id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM auth.users WHERE id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    public void testCreateAndRetrievePlatformAdmin() throws Exception {
        UUID userUuid = createTestAuthUser();

        PlatformAdmin admin = new PlatformAdmin();
        admin.setUserId(userUuid.toString());
        admin.setFullName("Test Operations Administrator");
        admin.setRoleCode("SYSTEM_ADMIN");
        admin.setStatus("ACTIVE");

        PlatformAdmin created = repository.createPlatformAdmin(platformAdminContext, admin);
        assertNotNull(created.getId());
        assertEquals(userUuid.toString(), created.getUserId());
        assertEquals("SYSTEM_ADMIN", created.getRoleCode());
        assertEquals("ACTIVE", created.getStatus());

        // Find by internal ID
        Optional<PlatformAdmin> byId = repository.findById(platformAdminContext, created.getId());
        assertTrue("Admin must be retrievable by ID", byId.isPresent());
        assertEquals("Test Operations Administrator", byId.get().getFullName());

        // Find by Auth User ID
        Optional<PlatformAdmin> byUserId = repository.findByUserId(platformAdminContext, userUuid.toString());
        assertTrue("Admin must be retrievable by User ID", byUserId.isPresent());
        assertEquals(created.getId(), byUserId.get().getId());
    }

    @Test
    public void testListPlatformAdmins() {
        List<PlatformAdmin> list = repository.listPlatformAdmins(platformAdminContext);
        assertNotNull(list);
        assertFalse("List must not be empty (at least super admin exists)", list.isEmpty());

        boolean foundSuperAdmin = false;
        for (PlatformAdmin a : list) {
            if (SUPER_ADMIN_UUID.toString().equalsIgnoreCase(a.getUserId())) {
                foundSuperAdmin = true;
                break;
            }
        }
        assertTrue("Pre-existing Super Admin must be present in active listing", foundSuperAdmin);
    }

    @Test
    public void testUpdatePlatformAdmin() throws Exception {
        UUID userUuid = createTestAuthUser();

        PlatformAdmin admin = new PlatformAdmin();
        admin.setUserId(userUuid.toString());
        admin.setFullName("Billing Lead Specialist");
        admin.setRoleCode("BILLING");
        admin.setStatus("ACTIVE");

        PlatformAdmin created = repository.createPlatformAdmin(platformAdminContext, admin);

        // Update fields
        PlatformAdmin toUpdate = new PlatformAdmin();
        toUpdate.setId(created.getId());
        toUpdate.setFullName("Senior Financial Systems Director");
        toUpdate.setRoleCode("AUDITOR");
        toUpdate.setStatus("ACTIVE");

        PlatformAdmin updated = repository.updatePlatformAdmin(platformAdminContext, toUpdate);
        assertEquals("Senior Financial Systems Director", updated.getFullName());
        assertEquals("AUDITOR", updated.getRoleCode());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testSoftDeletePlatformAdmin() throws Exception {
        UUID userUuid = createTestAuthUser();

        PlatformAdmin admin = new PlatformAdmin();
        admin.setUserId(userUuid.toString());
        admin.setFullName("Temporary Support Admin");
        admin.setRoleCode("SUPPORT");
        admin.setStatus("ACTIVE");

        PlatformAdmin created = repository.createPlatformAdmin(platformAdminContext, admin);
        assertNotNull(created.getId());

        repository.deletePlatformAdmin(platformAdminContext, created.getId());

        Optional<PlatformAdmin> postDelete = repository.findById(platformAdminContext, created.getId());
        assertFalse("Soft-deleted platform admin must not be retrievable", postDelete.isPresent());
    }

    @Test
    public void testNonPlatformAdminAccessDenied() throws Exception {
        UUID unauthUser = createTestAuthUser();
        UserSecurityContext unauthorizedContext = UserSecurityContext.forPlatformAdmin(unauthUser);

        UUID targetUser = createTestAuthUser();
        PlatformAdmin admin = new PlatformAdmin();
        admin.setUserId(targetUser.toString());
        admin.setFullName("Malicious Elevation Attempt");
        admin.setRoleCode("SUPER_ADMIN");

        try {
            repository.createPlatformAdmin(unauthorizedContext, admin);
            fail("Non-platform admin caller must be blocked by RLS / security violation");
        } catch (Exception e) {
            assertTrue("Exception must indicate RLS violation or security denial: " + e.getMessage(),
                    e.getMessage().toLowerCase().contains("row-level security") ||
                    e.getMessage().toLowerCase().contains("denied") ||
                    e.getMessage().toLowerCase().contains("violation") ||
                    e.getMessage().toLowerCase().contains("permission"));
        }
    }
}
