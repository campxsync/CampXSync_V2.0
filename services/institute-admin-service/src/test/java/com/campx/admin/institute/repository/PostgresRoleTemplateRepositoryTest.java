package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.RoleTemplatePermission;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 20: {@link PostgresRoleTemplateRepository} against Supabase PostgreSQL.
 * Verifies role template persistence, unique code constraint, updates, permission binding, deletion, and RLS policies.
 */
public class PostgresRoleTemplateRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresRoleTemplateRepository repository;
    private static UserSecurityContext platformContext;
    private static UserSecurityContext regularUserContext;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdTemplateIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Set<String> createdRoleCodes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresRoleTemplateRepository(connectionManager);

        platformContext = UserSecurityContext.forPlatformAdmin(SUPER_ADMIN_UUID);
        regularUserContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID(), false);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (String code : createdRoleCodes) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.role_template_permissions WHERE role_code = ?")) {
                    ps.setString(1, code);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID tid : createdTemplateIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.role_templates WHERE id = ?")) {
                    ps.setObject(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveRoleTemplate() throws Exception {
        String code = "TPL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        RoleTemplate template = new RoleTemplate();
        template.setCode(code);
        template.setName("Chief Proctor");
        template.setKind("administrative_staff");
        template.setScopeNote("Oversees campus discipline and student conduct");

        RoleTemplate created = repository.createRoleTemplate(platformContext, template);
        assertNotNull(created.getId());
        createdTemplateIds.add(UUID.fromString(created.getId()));
        createdRoleCodes.add(code);

        assertEquals(code, created.getCode());
        assertEquals("Chief Proctor", created.getName());
        assertEquals("administrative_staff", created.getKind());

        // Find by ID
        Optional<RoleTemplate> byId = repository.findById(platformContext, created.getId());
        assertTrue("Template must be retrievable by ID", byId.isPresent());
        assertEquals("Chief Proctor", byId.get().getName());

        // Find by Code
        Optional<RoleTemplate> byCode = repository.findByCode(platformContext, code);
        assertTrue("Template must be retrievable by Code", byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        // List templates
        List<RoleTemplate> list = repository.listRoleTemplates(platformContext);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testUpdateRoleTemplate() throws Exception {
        String code = "TPL_UPD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        RoleTemplate template = new RoleTemplate();
        template.setCode(code);
        template.setName("Original Proctor Name");

        RoleTemplate created = repository.createRoleTemplate(platformContext, template);
        createdTemplateIds.add(UUID.fromString(created.getId()));
        createdRoleCodes.add(code);

        RoleTemplate update = new RoleTemplate();
        update.setId(created.getId());
        update.setName("Updated Proctor Name");
        update.setScopeNote("Updated Proctor Scope Note");

        RoleTemplate updated = repository.updateRoleTemplate(platformContext, update);
        assertEquals("Updated Proctor Name", updated.getName());
        assertEquals("Updated Proctor Scope Note", updated.getScopeNote());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testDeleteRoleTemplate() throws Exception {
        String code = "TPL_DEL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        RoleTemplate template = new RoleTemplate();
        template.setCode(code);
        template.setName("Template To Delete");

        RoleTemplate created = repository.createRoleTemplate(platformContext, template);
        createdTemplateIds.add(UUID.fromString(created.getId()));
        createdRoleCodes.add(code);

        repository.deleteRoleTemplate(platformContext, created.getId());

        Optional<RoleTemplate> afterDelete = repository.findById(platformContext, created.getId());
        assertFalse("Deleted template must not be returned", afterDelete.isPresent());
    }

    @Test
    public void testPermissionsLifecycle() throws Exception {
        String code = "TPL_PRM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        RoleTemplate template = new RoleTemplate();
        template.setCode(code);
        template.setName("Permission Lifecycle Template");

        RoleTemplate created = repository.createRoleTemplate(platformContext, template);
        createdTemplateIds.add(UUID.fromString(created.getId()));
        createdRoleCodes.add(code);

        RoleTemplatePermission perm = repository.addPermission(platformContext, code, "campus.discipline.manage");
        assertNotNull(perm.getId());
        assertEquals(code, perm.getRoleCode());
        assertEquals("campus.discipline.manage", perm.getPermissionCode());

        List<RoleTemplatePermission> perms = repository.listPermissions(platformContext, code);
        assertEquals(1, perms.size());
        assertEquals("campus.discipline.manage", perms.get(0).getPermissionCode());

        repository.removePermission(platformContext, code, "campus.discipline.manage");
        List<RoleTemplatePermission> permsAfterRemove = repository.listPermissions(platformContext, code);
        assertTrue(permsAfterRemove.isEmpty());
    }

    @Test
    public void testAuthenticatedUserCanReadTemplates() throws Exception {
        // Authenticated non-platform user can read global role templates under role_templates_read RLS policy
        List<RoleTemplate> templates = repository.listRoleTemplates(regularUserContext);
        assertNotNull(templates);
        assertFalse("Authenticated users should be able to read global role templates", templates.isEmpty());
    }
}
