package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.RoleTemplatePermission;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class InMemoryRoleTemplateRepositoryTest {

    private InMemoryRoleTemplateRepository repository;
    private UserSecurityContext platformContext;

    @Before
    public void setUp() {
        repository = new InMemoryRoleTemplateRepository();
        platformContext = UserSecurityContext.forPlatformAdmin(UUID.randomUUID());
    }

    @Test
    public void testCreateAndRetrieveRoleTemplate() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("LAB_ASSISTANT");
        template.setName("Laboratory Assistant");
        template.setKind("technical_staff");
        template.setScopeNote("Operates subject science laboratories");

        RoleTemplate created = repository.createRoleTemplate(platformContext, template);
        assertNotNull(created.getId());
        assertEquals("LAB_ASSISTANT", created.getCode());
        assertEquals("Laboratory Assistant", created.getName());

        Optional<RoleTemplate> byId = repository.findById(platformContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals("Laboratory Assistant", byId.get().getName());

        Optional<RoleTemplate> byCode = repository.findByCode(platformContext, "lab_assistant");
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<RoleTemplate> list = repository.listRoleTemplates(platformContext);
        assertEquals(1, list.size());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDuplicateCodeThrows() {
        RoleTemplate t1 = new RoleTemplate();
        t1.setCode("TPL_DUP");
        t1.setName("First");
        repository.createRoleTemplate(platformContext, t1);

        RoleTemplate t2 = new RoleTemplate();
        t2.setCode("tpl_dup");
        t2.setName("Second");
        repository.createRoleTemplate(platformContext, t2);
    }

    @Test
    public void testUpdateRoleTemplate() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("TPL_UPD");
        template.setName("Original Name");
        RoleTemplate created = repository.createRoleTemplate(platformContext, template);

        RoleTemplate update = new RoleTemplate();
        update.setId(created.getId());
        update.setName("Updated Name");
        update.setScopeNote("Updated Scope");

        RoleTemplate updated = repository.updateRoleTemplate(platformContext, update);
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated Scope", updated.getScopeNote());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testDeleteRoleTemplate() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("TPL_DEL");
        template.setName("To Delete");
        RoleTemplate created = repository.createRoleTemplate(platformContext, template);

        repository.deleteRoleTemplate(platformContext, created.getId());
        Optional<RoleTemplate> byId = repository.findById(platformContext, created.getId());
        assertFalse(byId.isPresent());
    }

    @Test
    public void testPermissionsLifecycle() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("TPL_PERM");
        template.setName("Permission Test Template");
        repository.createRoleTemplate(platformContext, template);

        RoleTemplatePermission p = repository.addPermission(platformContext, "TPL_PERM", "lab.manage");
        assertNotNull(p.getId());
        assertEquals("TPL_PERM", p.getRoleCode());
        assertEquals("lab.manage", p.getPermissionCode());

        List<RoleTemplatePermission> perms = repository.listPermissions(platformContext, "TPL_PERM");
        assertEquals(1, perms.size());

        repository.removePermission(platformContext, "TPL_PERM", "lab.manage");
        List<RoleTemplatePermission> permsAfterRemove = repository.listPermissions(platformContext, "TPL_PERM");
        assertTrue(permsAfterRemove.isEmpty());
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingContextThrows() {
        repository.listRoleTemplates(null);
    }
}
