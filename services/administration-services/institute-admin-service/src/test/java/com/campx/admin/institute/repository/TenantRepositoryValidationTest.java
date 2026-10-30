package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.InstituteModels.Institute;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Validation tests covering {@link TenantRepository}, {@link InMemoryTenantRepository},
 * and {@link PostgresTenantRepository} across:
 * <ul>
 * <li>Create institute</li>
 * <li>Duplicate institute code (409 Conflict)</li>
 * <li>Get & list institute</li>
 * <li>Update institute</li>
 * <li>Non-existent UUID -> 404 Not Found</li>
 * <li>Malformed/non-UUID ID -> 404 Not Found (safe UUID handling, prevents PostgreSQL 22P02)</li>
 * <li>Optimistic locking / row_version behavior (409 Conflict on stale version)</li>
 * <li>Platform administrator authorization (403 Forbidden for non-platform admin)</li>
 * <li>Tenant isolation for non-platform updates (403 Forbidden on cross-tenant update)</li>
 * </ul>
 */
public class TenantRepositoryValidationTest {

    private InMemoryTenantRepository repository;
    private PostgresTenantRepository postgresRepository;
    private UUID platformAdminId;
    private UserSecurityContext platformAdminContext;

    @Before
    public void setup() {
        repository = new InMemoryTenantRepository();
        postgresRepository = new PostgresTenantRepository(null);
        platformAdminId = UUID.randomUUID();
        platformAdminContext = UserSecurityContext.forPlatformAdmin(platformAdminId);
    }

    private Institute createSampleInstitute(String code) {
        Institute inst = new Institute();
        inst.setInstituteCode(code);
        inst.setDisplayName("Test " + code);
        inst.setLegalName("Legal " + code);
        inst.setTimezone("Asia/Kolkata");
        inst.setLocale("en_IN");
        inst.setDefaultCurrency("INR");
        return inst;
    }

    // =========================================================================
    // 1. Create Institute
    // =========================================================================

    @Test
    public void testCreateInstituteSuccess() {
        Institute input = createSampleInstitute("INST_VAL_01");
        Institute created = repository.createInstitute(platformAdminContext, input);

        assertNotNull(created.getId());
        assertEquals("INST_VAL_01", created.getInstituteCode());
        assertEquals("Test INST_VAL_01", created.getDisplayName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(1, created.getVersion());
        assertTrue(created.getCreatedAt() > 0);
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
    }

    // =========================================================================
    // 2. Duplicate Institute Code -> 409 Conflict
    // =========================================================================

    @Test(expected = InstituteAlreadyExistsException.class)
    public void testDuplicateInstituteCodeThrowsConflict() {
        Institute inst1 = createSampleInstitute("INST_DUP_01");
        repository.createInstitute(platformAdminContext, inst1);

        Institute inst2 = createSampleInstitute("INST_DUP_01");
        repository.createInstitute(platformAdminContext, inst2);
    }

    // =========================================================================
    // 3. Get and List Institutes
    // =========================================================================

    @Test
    public void testGetAndListInstitutes() {
        Institute inst = repository.createInstitute(platformAdminContext, createSampleInstitute("INST_GET_01"));

        Optional<Institute> byId = repository.getInstituteById(platformAdminContext, inst.getId());
        assertTrue(byId.isPresent());
        assertEquals("INST_GET_01", byId.get().getInstituteCode());

        Optional<Institute> byCode = repository.getInstituteByCode(platformAdminContext, "inst_get_01");
        assertTrue(byCode.isPresent());
        assertEquals(inst.getId(), byCode.get().getId());

        List<Institute> all = repository.listInstitutes(platformAdminContext);
        assertEquals(1, all.size());
    }

    // =========================================================================
    // 4. Update Institute
    // =========================================================================

    @Test
    public void testUpdateInstituteSuccess() {
        Institute created = repository.createInstitute(platformAdminContext, createSampleInstitute("INST_UPD_01"));

        Institute update = new Institute();
        update.setDisplayName("Updated Display Name");
        update.setStatus("INACTIVE");

        Institute updated = repository.updateInstitute(platformAdminContext, created.getId(), update, 1);
        assertEquals("Updated Display Name", updated.getDisplayName());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals(2, updated.getVersion());
        assertTrue(updated.getUpdatedAt() >= created.getCreatedAt());
    }

    // =========================================================================
    // 5. Non-Existent UUID -> 404 Not Found
    // =========================================================================

    @Test(expected = InstituteNotFoundException.class)
    public void testNonExistentUuidThrowsNotFound() {
        String randomUuid = UUID.randomUUID().toString();
        Institute update = new Institute();
        update.setDisplayName("Won't Exist");

        repository.updateInstitute(platformAdminContext, randomUuid, update, 1);
    }

    // =========================================================================
    // 6. Malformed / Non-UUID ID -> 404 Not Found (Safe handling, prevents 22P02)
    // =========================================================================

    @Test(expected = InstituteNotFoundException.class)
    public void testMalformedIdThrowsNotFoundInMemory() {
        Institute update = new Institute();
        update.setDisplayName("Invalid ID");
        repository.updateInstitute(platformAdminContext, "MALFORMED_NON_UUID", update, 1);
    }

    @Test(expected = InstituteNotFoundException.class)
    public void testMalformedIdThrowsNotFoundPostgresRepoBeforeSql() {
        Institute update = new Institute();
        update.setDisplayName("Invalid ID");
        // Must validate UUID before issuing SQL to prevent 22P02
        postgresRepository.updateInstitute(platformAdminContext, "NOT-A-UUID", update, 1);
    }

    @Test
    public void testMalformedIdGetByIdReturnsEmptyWithoutSql() {
        Optional<Institute> optPostgres = postgresRepository.getInstituteById(platformAdminContext, "NOT-A-UUID");
        assertFalse(optPostgres.isPresent());

        Optional<Institute> optInMemory = repository.getInstituteById(platformAdminContext, "NOT-A-UUID");
        assertFalse(optInMemory.isPresent());
    }

    // =========================================================================
    // 7. Optimistic Locking / Row Version Behavior
    // =========================================================================

    @Test(expected = UserProfileConflictException.class)
    public void testStaleRowVersionThrowsConflict() {
        Institute created = repository.createInstitute(platformAdminContext, createSampleInstitute("INST_OPT_01"));

        Institute update = new Institute();
        update.setDisplayName("Stale Update");

        // Expected version is 99, but current version is 1 -> 409 Conflict
        repository.updateInstitute(platformAdminContext, created.getId(), update, 99);
    }

    @Test(expected = SecurityViolationException.class)
    public void testImmutableIdentityKeyModificationThrowsViolation() {
        Institute created = repository.createInstitute(platformAdminContext, createSampleInstitute("INST_IMMUT_01"));

        Institute update = new Institute();
        update.setInstituteCode("INST_IMMUT_CHANGED");

        repository.updateInstitute(platformAdminContext, created.getId(), update, 1);
    }

    // =========================================================================
    // 8. Platform Admin Authorization
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testNonPlatformAdminCreationDeniedWhenWhitelistActive() {
        UUID configuredAdmin = UUID.randomUUID();
        repository.addPlatformAdmin(configuredAdmin);

        UUID nonAdminUser = UUID.randomUUID();
        UserSecurityContext nonAdminCtx = UserSecurityContext.forPlatformAdmin(nonAdminUser);

        // Caller is not in platform admin whitelist -> 403 Forbidden
        repository.createInstitute(nonAdminCtx, createSampleInstitute("INST_UNAUTH_01"));
    }

    @Test
    public void testPlatformAdminCreationAllowedWhenInWhitelist() {
        UUID configuredAdmin = UUID.randomUUID();
        repository.addPlatformAdmin(configuredAdmin);

        UserSecurityContext adminCtx = UserSecurityContext.forPlatformAdmin(configuredAdmin);
        Institute created = repository.createInstitute(adminCtx, createSampleInstitute("INST_AUTH_01"));
        assertNotNull(created);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testMissingSecurityContextThrowsAccessDenied() {
        repository.createInstitute(null, createSampleInstitute("INST_NO_CTX"));
    }

    // =========================================================================
    // 9. Tenant Isolation on Non-Platform Update
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testCrossTenantUpdateDeniedForNonPlatformAdmin() {
        UUID configuredAdmin = UUID.randomUUID();
        repository.addPlatformAdmin(configuredAdmin);

        // Created by platform admin
        Institute inst = repository.createInstitute(
                UserSecurityContext.forPlatformAdmin(configuredAdmin),
                createSampleInstitute("INST_ISO_01")
        );

        // Regular tenant admin from a DIFFERENT tenant tries to update inst
        UUID differentTenantId = UUID.randomUUID();
        UUID callerUserId = UUID.randomUUID();
        UserSecurityContext tenantCtx = new UserSecurityContext(callerUserId, differentTenantId, false);

        Institute update = new Institute();
        update.setDisplayName("Cross Tenant Attack");

        repository.updateInstitute(tenantCtx, inst.getId(), update, 1);
    }

    // =========================================================================
    // 10. Input Validation / Security Checks in PostgresTenantRepository
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContext() {
        postgresRepository.createInstitute(null, createSampleInstitute("INST_NULL_CTX"));
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsNullPayload() {
        postgresRepository.createInstitute(platformAdminContext, null);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsMissingInstituteCode() {
        Institute inst = new Institute();
        postgresRepository.createInstitute(platformAdminContext, inst);
    }
}
