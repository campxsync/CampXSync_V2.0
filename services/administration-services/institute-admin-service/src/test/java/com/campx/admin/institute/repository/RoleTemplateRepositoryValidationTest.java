package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.RoleTemplatePermission;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Validation tests covering {@link RoleTemplateRepository}, {@link InMemoryRoleTemplateRepository},
 * and defensive validation in {@link PostgresRoleTemplateRepository} across:
 * <ul>
 * <li>Platform administrator authorization (403 Forbidden for tenant-scoped or non-whitelisted callers)</li>
 * <li>Role template lifecycle (create, get, list, update, soft-delete, row_version increment)</li>
 * <li>Code uniqueness (case-insensitive duplicate check)</li>
 * <li>Permission binding lifecycle (add, list, idempotent add, remove)</li>
 * <li>Read accessibility for authenticated non-admin callers (matching database RLS SELECT policy)</li>
 * <li>Defensive input and security context pre-SQL validation in PostgreSQL repository</li>
 * </ul>
 */
public class RoleTemplateRepositoryValidationTest {

    private InMemoryRoleTemplateRepository repository;
    private PostgresRoleTemplateRepository postgresRepository;
    private UUID platformAdminId;
    private UserSecurityContext platformAdminContext;
    private UserSecurityContext regularUserContext;

    @Before
    public void setup() {
        repository = new InMemoryRoleTemplateRepository();
        postgresRepository = new PostgresRoleTemplateRepository(null);
        platformAdminId = UUID.randomUUID();
        platformAdminContext = UserSecurityContext.forPlatformAdmin(platformAdminId);
        regularUserContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID(), false);
    }

    private RoleTemplate createSampleTemplate(String code, String name) {
        RoleTemplate t = new RoleTemplate();
        t.setCode(code);
        t.setName(name);
        t.setKind("administrative_staff");
        t.setScopeNote("Test template scope note");
        return t;
    }

    // =========================================================================
    // 1. Create Role Template
    // =========================================================================

    @Test
    public void testCreateRoleTemplatePlatformAdminSuccess() {
        RoleTemplate input = createSampleTemplate("TPL_DEAN", "Dean of Academics");
        RoleTemplate created = repository.createRoleTemplate(platformAdminContext, input);

        assertNotNull(created.getId());
        assertEquals("TPL_DEAN", created.getCode());
        assertEquals("Dean of Academics", created.getName());
        assertEquals("administrative_staff", created.getKind());
        assertEquals("Test template scope note", created.getScopeNote());
        assertEquals(1, created.getRowVersion());
        assertTrue(created.getCreatedAt() > 0);
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
        assertEquals(platformAdminId.toString(), created.getCreatedBy());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateRoleTemplateDuplicateCodeThrows() {
        RoleTemplate t1 = createSampleTemplate("TPL_UNIQUE", "Original Template");
        repository.createRoleTemplate(platformAdminContext, t1);

        RoleTemplate t2 = createSampleTemplate("tpl_unique", "Duplicate Template");
        repository.createRoleTemplate(platformAdminContext, t2);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateRoleTemplateMissingCodeThrows() {
        RoleTemplate t = createSampleTemplate(null, "Template without code");
        repository.createRoleTemplate(platformAdminContext, t);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateRoleTemplateMissingNameThrows() {
        RoleTemplate t = createSampleTemplate("TPL_NONAME", "");
        repository.createRoleTemplate(platformAdminContext, t);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testCreateRoleTemplateNullPayloadThrows() {
        repository.createRoleTemplate(platformAdminContext, null);
    }

    // =========================================================================
    // 2. Retrieval and Listing
    // =========================================================================

    @Test
    public void testFindByIdAndFindByCode() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_HOD", "Head of Department"));

        Optional<RoleTemplate> byId = repository.findById(platformAdminContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals("Head of Department", byId.get().getName());

        Optional<RoleTemplate> byCodeUpper = repository.findByCode(platformAdminContext, "TPL_HOD");
        assertTrue(byCodeUpper.isPresent());
        assertEquals(created.getId(), byCodeUpper.get().getId());

        Optional<RoleTemplate> byCodeLower = repository.findByCode(platformAdminContext, "tpl_hod");
        assertTrue(byCodeLower.isPresent());
        assertEquals(created.getId(), byCodeLower.get().getId());
    }

    @Test
    public void testFindByIdNotFoundReturnsEmpty() {
        Optional<RoleTemplate> byId = repository.findById(platformAdminContext, UUID.randomUUID().toString());
        assertFalse(byId.isPresent());

        Optional<RoleTemplate> byNullId = repository.findById(platformAdminContext, null);
        assertFalse(byNullId.isPresent());
    }

    @Test
    public void testListRoleTemplatesExcludesDeleted() {
        RoleTemplate t1 = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_ACTIVE", "Active Template"));
        RoleTemplate t2 = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_TO_DELETE", "To Delete Template"));

        assertEquals(2, repository.listRoleTemplates(platformAdminContext).size());

        repository.deleteRoleTemplate(platformAdminContext, t2.getId());

        List<RoleTemplate> list = repository.listRoleTemplates(platformAdminContext);
        assertEquals(1, list.size());
        assertEquals("TPL_ACTIVE", list.get(0).getCode());
    }

    // =========================================================================
    // 3. Update Role Template
    // =========================================================================

    @Test
    public void testUpdateRoleTemplateSuccess() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_UPDATE", "Original Name"));

        RoleTemplate update = new RoleTemplate();
        update.setId(created.getId());
        update.setName("Updated Role Name");
        update.setScopeNote("Updated Scope");
        update.setKind("executive");

        RoleTemplate updated = repository.updateRoleTemplate(platformAdminContext, update);
        assertEquals("Updated Role Name", updated.getName());
        assertEquals("Updated Scope", updated.getScopeNote());
        assertEquals("executive", updated.getKind());
        assertEquals(2, updated.getRowVersion());
        assertTrue(updated.getUpdatedAt() >= created.getCreatedAt());
        assertEquals(platformAdminId.toString(), updated.getUpdatedBy());
    }

    @Test(expected = ResourceNotFoundException.class)
    public void testUpdateRoleTemplateNotFoundThrows() {
        RoleTemplate update = new RoleTemplate();
        update.setId(UUID.randomUUID().toString());
        update.setName("Does Not Exist");
        repository.updateRoleTemplate(platformAdminContext, update);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testUpdateRoleTemplateMissingIdThrows() {
        RoleTemplate update = new RoleTemplate();
        update.setName("No ID Specified");
        repository.updateRoleTemplate(platformAdminContext, update);
    }

    // =========================================================================
    // 4. Soft-Delete Role Template
    // =========================================================================

    @Test
    public void testDeleteRoleTemplateSoftDeletes() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_DEL_TEST", "Delete Test"));

        repository.deleteRoleTemplate(platformAdminContext, created.getId());

        assertFalse(repository.findById(platformAdminContext, created.getId()).isPresent());
        assertFalse(repository.findByCode(platformAdminContext, "TPL_DEL_TEST").isPresent());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testDeleteRoleTemplateMissingIdThrows() {
        repository.deleteRoleTemplate(platformAdminContext, "  ");
    }

    // =========================================================================
    // 5. Permission Binding Lifecycle
    // =========================================================================

    @Test
    public void testPermissionsLifecycle() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_PERM_TEST", "Permission Target"));

        RoleTemplatePermission p1 = repository.addPermission(
                platformAdminContext, created.getCode(), "acd01.course.manage");
        assertNotNull(p1.getId());
        assertEquals("TPL_PERM_TEST", p1.getRoleCode());
        assertEquals("acd01.course.manage", p1.getPermissionCode());

        // Idempotent duplicate check
        RoleTemplatePermission pDup = repository.addPermission(
                platformAdminContext, created.getCode(), "acd01.course.manage");
        assertEquals(p1.getId(), pDup.getId());

        List<RoleTemplatePermission> perms = repository.listPermissions(
                platformAdminContext, created.getCode());
        assertEquals(1, perms.size());

        repository.removePermission(platformAdminContext, created.getCode(), "acd01.course.manage");
        List<RoleTemplatePermission> permsAfter = repository.listPermissions(
                platformAdminContext, created.getCode());
        assertTrue(permsAfter.isEmpty());
    }

    @Test(expected = MalformedPayloadException.class)
    public void testAddPermissionMissingRoleCodeThrows() {
        repository.addPermission(platformAdminContext, null, "acd01.course.manage");
    }

    @Test(expected = MalformedPayloadException.class)
    public void testAddPermissionMissingPermissionCodeThrows() {
        repository.addPermission(platformAdminContext, "TPL_TEST", "");
    }

    // =========================================================================
    // 6. Platform Administrator Authorization Enforcement
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedUserCannotCreateRoleTemplate() {
        // Tenant-scoped context (not platform scope) -> 403 Forbidden
        repository.createRoleTemplate(regularUserContext, createSampleTemplate("TPL_ATTACK", "Attack"));
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedUserCannotUpdateRoleTemplate() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_SAFE", "Safe"));

        RoleTemplate update = new RoleTemplate();
        update.setId(created.getId());
        update.setName("Tampered");

        repository.updateRoleTemplate(regularUserContext, update);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedUserCannotDeleteRoleTemplate() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_SAFE2", "Safe 2"));

        repository.deleteRoleTemplate(regularUserContext, created.getId());
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedUserCannotAddPermission() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_SAFE3", "Safe 3"));

        repository.addPermission(regularUserContext, created.getCode(), "sys.admin");
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testTenantScopedUserCannotRemovePermission() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_SAFE4", "Safe 4"));

        repository.removePermission(regularUserContext, created.getCode(), "sys.admin");
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPlatformAdminWhitelistEnforcedWhenActive() {
        UUID configuredAdmin = UUID.randomUUID();
        repository.addPlatformAdmin(configuredAdmin);

        UUID unauthorizedAdmin = UUID.randomUUID();
        UserSecurityContext unauthorizedContext = UserSecurityContext.forPlatformAdmin(unauthorizedAdmin);

        // Caller has platform scope but is not in the whitelist -> 403 Forbidden
        repository.createRoleTemplate(unauthorizedContext, createSampleTemplate("TPL_UNAUTH", "Unauth"));
    }

    @Test
    public void testPlatformAdminWhitelistAllowsConfiguredAdmin() {
        UUID configuredAdmin = UUID.randomUUID();
        repository.addPlatformAdmin(configuredAdmin);

        UserSecurityContext authorizedContext = UserSecurityContext.forPlatformAdmin(configuredAdmin);
        RoleTemplate created = repository.createRoleTemplate(
                authorizedContext, createSampleTemplate("TPL_CONFIGURED", "Configured Admin"));
        assertNotNull(created);
    }

    @Test
    public void testAuthenticatedTenantUserCanReadTemplates() {
        RoleTemplate created = repository.createRoleTemplate(
                platformAdminContext, createSampleTemplate("TPL_PUBLIC_READ", "Public Read"));
        repository.addPermission(platformAdminContext, created.getCode(), "sample.perm");

        // Authenticated non-admin caller can read templates & permissions (mirrors role_templates_select policy)
        assertTrue(repository.findById(regularUserContext, created.getId()).isPresent());
        assertTrue(repository.findByCode(regularUserContext, "TPL_PUBLIC_READ").isPresent());
        assertFalse(repository.listRoleTemplates(regularUserContext).isEmpty());
        assertEquals(1, repository.listPermissions(regularUserContext, "TPL_PUBLIC_READ").size());
    }

    @Test(expected = SecurityViolationException.class)
    public void testMissingSecurityContextThrows() {
        repository.listRoleTemplates(null);
    }

    // =========================================================================
    // 7. Defensive Validation in PostgresRoleTemplateRepository
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContextOnCreate() {
        postgresRepository.createRoleTemplate(null, createSampleTemplate("TPL_POSTGRES", "Test"));
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContextOnUpdate() {
        RoleTemplate t = new RoleTemplate();
        t.setId(UUID.randomUUID().toString());
        postgresRepository.updateRoleTemplate(null, t);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContextOnDelete() {
        postgresRepository.deleteRoleTemplate(null, UUID.randomUUID().toString());
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContextOnAddPermission() {
        postgresRepository.addPermission(null, "TPL_POSTGRES", "perm.code");
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContextOnRemovePermission() {
        postgresRepository.removePermission(null, "TPL_POSTGRES", "perm.code");
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsNullPayload() {
        postgresRepository.createRoleTemplate(platformAdminContext, null);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsMissingCode() {
        RoleTemplate t = new RoleTemplate();
        t.setName("Missing Code");
        postgresRepository.createRoleTemplate(platformAdminContext, t);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsMissingName() {
        RoleTemplate t = new RoleTemplate();
        t.setCode("MISSING_NAME");
        postgresRepository.createRoleTemplate(platformAdminContext, t);
    }
}
