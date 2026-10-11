package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.UserGroup;
import com.campx.admin.institute.model.InstituteModels.UserGroupMember;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 19: {@link PostgresUserGroupRepository} against Supabase PostgreSQL.
 * Verifies group creation, uniqueness, retrieval, membership association, deletion, and tenant boundary isolation.
 */
public class PostgresUserGroupRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresUserGroupRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;
    private static UUID testMemberUserId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdUserIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Set<UUID> createdGroupIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresUserGroupRepository(connectionManager);

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
        assumeNotNull("Requires an active tenant", tenantId);

        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
        otherTenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, otherTenantId);

        testMemberUserId = createTestUserProfile("grp_member");
    }

    private static UUID createTestUserProfile(String prefix) throws Exception {
        UUID uid = UUID.randomUUID();
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO auth.users (id, email) VALUES (?, ?)")) {
                ps.setObject(1, uid);
                ps.setString(2, prefix + "_" + uid.toString().substring(0, 8) + "@campx.local");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO iam.user_profiles (id, tenant_id, email, full_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')")) {
                ps.setObject(1, uid);
                ps.setObject(2, tenantId);
                ps.setString(3, prefix + "_" + uid.toString().substring(0, 8) + "@campx.local");
                ps.setString(4, "Group Test " + prefix);
                ps.executeUpdate();
            }
        }
        createdUserIds.add(uid);
        return uid;
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID gid : createdGroupIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.user_group_members WHERE group_id = ?")) {
                    ps.setObject(1, gid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.user_groups WHERE id = ?")) {
                    ps.setObject(1, gid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID uid : createdUserIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.user_profiles WHERE id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM auth.users WHERE id = ?")) {
                    ps.setObject(1, uid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveUserGroup() throws Exception {
        String code = "GRP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        UserGroup group = new UserGroup();
        group.setCode(code);
        group.setName("Academic Senate Committee");
        group.setDescription("Standing academic curriculum review panel");

        UserGroup created = repository.createUserGroup(tenantContext, group);
        assertNotNull(created.getId());
        createdGroupIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(code, created.getCode());
        assertEquals("Academic Senate Committee", created.getName());

        // Find by ID
        Optional<UserGroup> byId = repository.findById(tenantContext, created.getId());
        assertTrue("Group must be retrievable by ID", byId.isPresent());
        assertEquals("Academic Senate Committee", byId.get().getName());

        // Find by Code
        Optional<UserGroup> byCode = repository.findByCode(tenantContext, code);
        assertTrue("Group must be retrievable by Code", byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        // List groups
        List<UserGroup> list = repository.listUserGroups(tenantContext);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testUpdateUserGroup() throws Exception {
        String code = "GRP_UPD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        UserGroup group = new UserGroup();
        group.setCode(code);
        group.setName("Original Group Name");

        UserGroup created = repository.createUserGroup(tenantContext, group);
        createdGroupIds.add(UUID.fromString(created.getId()));

        UserGroup update = new UserGroup();
        update.setId(created.getId());
        update.setName("Updated Group Name");
        update.setDescription("Added group description");

        UserGroup updated = repository.updateUserGroup(tenantContext, update);
        assertEquals("Updated Group Name", updated.getName());
        assertEquals("Added group description", updated.getDescription());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testDeleteUserGroup() throws Exception {
        String code = "GRP_DEL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        UserGroup group = new UserGroup();
        group.setCode(code);
        group.setName("Group To Delete");

        UserGroup created = repository.createUserGroup(tenantContext, group);
        createdGroupIds.add(UUID.fromString(created.getId()));

        repository.deleteUserGroup(tenantContext, created.getId());

        Optional<UserGroup> afterDelete = repository.findById(tenantContext, created.getId());
        assertFalse("Deleted group must not be returned", afterDelete.isPresent());
    }

    @Test
    public void testMembersLifecycle() throws Exception {
        String code = "GRP_MBR_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        UserGroup group = new UserGroup();
        group.setCode(code);
        group.setName("Membership Test Group");

        UserGroup created = repository.createUserGroup(tenantContext, group);
        createdGroupIds.add(UUID.fromString(created.getId()));

        UserGroupMember member = repository.addMember(tenantContext, created.getId(), testMemberUserId.toString());
        assertNotNull(member.getId());
        assertEquals(created.getId(), member.getGroupId());
        assertEquals(testMemberUserId.toString(), member.getUserId());

        List<UserGroupMember> members = repository.listMembers(tenantContext, created.getId());
        assertEquals(1, members.size());
        assertEquals(testMemberUserId.toString(), members.get(0).getUserId());

        repository.removeMember(tenantContext, created.getId(), testMemberUserId.toString());
        List<UserGroupMember> membersAfterRemove = repository.listMembers(tenantContext, created.getId());
        assertTrue(membersAfterRemove.isEmpty());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        String code = "GRP_ISO_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        UserGroup group = new UserGroup();
        group.setCode(code);
        group.setName("Tenant Isolation Group");

        UserGroup created = repository.createUserGroup(tenantContext, group);
        createdGroupIds.add(UUID.fromString(created.getId()));

        Optional<UserGroup> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must not find group", cross.isPresent());
    }
}
