package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.UserGroup;
import com.campx.admin.institute.model.InstituteModels.UserGroupMember;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryUserGroupRepositoryTest {

    private InMemoryUserGroupRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;
    private UUID tenantId;

    @Before
    public void setUp() {
        repository = new InMemoryUserGroupRepository();
        tenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveGroup() {
        UserGroup group = new UserGroup();
        group.setCode("FACULTY_CS");
        group.setName("Computer Science Faculty Group");
        group.setDescription("All faculty in CS department");

        UserGroup created = repository.createUserGroup(tenantContext, group);
        assertNotNull(created.getId());
        assertEquals("FACULTY_CS", created.getCode());
        assertEquals(tenantId.toString(), created.getTenantId());

        Optional<UserGroup> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals("Computer Science Faculty Group", byId.get().getName());

        Optional<UserGroup> byCode = repository.findByCode(tenantContext, "faculty_cs");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<UserGroup> list = repository.listUserGroups(tenantContext);
        assertEquals(1, list.size());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateCodeThrows() {
        UserGroup g1 = new UserGroup();
        g1.setCode("GRP_DUP");
        g1.setName("First");
        repository.createUserGroup(tenantContext, g1);

        UserGroup g2 = new UserGroup();
        g2.setCode("grp_dup");
        g2.setName("Second");
        repository.createUserGroup(tenantContext, g2);
    }

    @Test
    public void testUpdateGroup() {
        UserGroup group = new UserGroup();
        group.setCode("GRP_UPDATE");
        group.setName("Original Name");
        UserGroup created = repository.createUserGroup(tenantContext, group);

        UserGroup update = new UserGroup();
        update.setId(created.getId());
        update.setName("Updated Name");
        update.setDescription("Updated Description");

        UserGroup updated = repository.updateUserGroup(tenantContext, update);
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated Description", updated.getDescription());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testDeleteGroup() {
        UserGroup group = new UserGroup();
        group.setCode("GRP_DELETE");
        group.setName("To Delete");
        UserGroup created = repository.createUserGroup(tenantContext, group);

        repository.deleteUserGroup(tenantContext, created.getId());
        Optional<UserGroup> byId = repository.findById(tenantContext, created.getId());
        assertFalse(byId.isPresent());
    }

    @Test
    public void testMembersLifecycle() {
        UserGroup group = new UserGroup();
        group.setCode("GRP_MEMBERS");
        group.setName("Members Group");
        UserGroup created = repository.createUserGroup(tenantContext, group);

        String userId = UUID.randomUUID().toString();
        UserGroupMember member = repository.addMember(tenantContext, created.getId(), userId);
        assertNotNull(member.getId());
        assertEquals(created.getId(), member.getGroupId());
        assertEquals(userId, member.getUserId());

        List<UserGroupMember> members = repository.listMembers(tenantContext, created.getId());
        assertEquals(1, members.size());

        repository.removeMember(tenantContext, created.getId(), userId);
        List<UserGroupMember> membersAfterRemove = repository.listMembers(tenantContext, created.getId());
        assertTrue(membersAfterRemove.isEmpty());
    }

    @Test
    public void testTenantIsolation() {
        UserGroup group = new UserGroup();
        group.setCode("GRP_ISOLATE");
        group.setName("Isolated");
        UserGroup created = repository.createUserGroup(tenantContext, group);

        Optional<UserGroup> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse(cross.isPresent());

        List<UserGroup> otherList = repository.listUserGroups(otherTenantContext);
        assertTrue(otherList.isEmpty());
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingContextThrows() {
        repository.listUserGroups(null);
    }
}
