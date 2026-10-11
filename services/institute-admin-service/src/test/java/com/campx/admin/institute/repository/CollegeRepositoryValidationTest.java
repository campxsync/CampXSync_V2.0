package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.InstituteModels.College;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Validation tests covering {@link CollegeRepository}, {@link InMemoryCollegeRepository},
 * and defensive validation in {@link PostgresCollegeRepository} across:
 * <ul>
 * <li>Create college</li>
 * <li>Duplicate college code within same institute (409 Conflict)</li>
 * <li>Get & list colleges</li>
 * <li>Update college</li>
 * <li>Non-existent UUID -> 404 Not Found</li>
 * <li>Malformed/non-UUID ID -> 404 Not Found (safe UUID handling, prevents PostgreSQL 22P02)</li>
 * <li>Optimistic locking / row_version behavior (409 Conflict on stale version)</li>
 * <li>Platform administrator authorization</li>
 * <li>Tenant isolation for non-platform updates (403 Forbidden on cross-tenant operation)</li>
 * <li>Defensive validation pre-SQL checks</li>
 * </ul>
 */
public class CollegeRepositoryValidationTest {

    private InMemoryCollegeRepository repository;
    private PostgresCollegeRepository postgresRepository;
    private UUID tenantId;
    private UUID platformAdminId;
    private UserSecurityContext tenantContext;
    private UserSecurityContext platformAdminContext;

    @Before
    public void setup() {
        repository = new InMemoryCollegeRepository();
        postgresRepository = new PostgresCollegeRepository(null);
        tenantId = UUID.randomUUID();
        platformAdminId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(UUID.randomUUID(), tenantId, false);
        platformAdminContext = UserSecurityContext.forPlatformAdmin(platformAdminId);
    }

    private College createSampleCollege(String code, String instId) {
        College col = new College();
        col.setCollegeCode(code);
        col.setName("College " + code);
        col.setLegalName("Legal " + code);
        col.setInstituteId(instId);
        col.setStatus("ACTIVE");
        return col;
    }

    // =========================================================================
    // 1. Create College
    // =========================================================================

    @Test
    public void testCreateCollegeSuccess() {
        College input = createSampleCollege("ENGG_01", tenantId.toString());
        College created = repository.createCollege(tenantContext, input);

        assertNotNull(created.getId());
        assertEquals("ENGG_01", created.getCollegeCode());
        assertEquals("College ENGG_01", created.getName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(tenantId.toString(), created.getInstituteId());
        assertEquals(1, created.getVersion());
        assertTrue(created.getCreatedAt() > 0);
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
    }

    // =========================================================================
    // 2. Duplicate College Code within Same Tenant -> 409 Conflict
    // =========================================================================

    @Test(expected = InstituteAlreadyExistsException.class)
    public void testDuplicateCollegeCodeThrowsConflict() {
        College col1 = createSampleCollege("DUP_COL_01", tenantId.toString());
        repository.createCollege(tenantContext, col1);

        College col2 = createSampleCollege("DUP_COL_01", tenantId.toString());
        repository.createCollege(tenantContext, col2);
    }

    // =========================================================================
    // 3. Get and List Colleges
    // =========================================================================

    @Test
    public void testGetAndListColleges() {
        College col = repository.createCollege(tenantContext, createSampleCollege("GET_COL_01", tenantId.toString()));

        Optional<College> byId = repository.getCollegeById(tenantContext, col.getId());
        assertTrue(byId.isPresent());
        assertEquals("GET_COL_01", byId.get().getCollegeCode());

        Optional<College> byCode = repository.getCollegeByCode(tenantContext, tenantId.toString(), "get_col_01");
        assertTrue(byCode.isPresent());
        assertEquals(col.getId(), byCode.get().getId());

        List<College> list = repository.listColleges(tenantContext, tenantId.toString());
        assertEquals(1, list.size());
    }

    // =========================================================================
    // 4. Update College
    // =========================================================================

    @Test
    public void testUpdateCollegeSuccess() {
        College created = repository.createCollege(tenantContext, createSampleCollege("UPD_COL_01", tenantId.toString()));

        College update = new College();
        update.setName("Updated College Name");
        update.setStatus("INACTIVE");

        College updated = repository.updateCollege(tenantContext, created.getId(), update, 1);
        assertEquals("Updated College Name", updated.getName());
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
        College update = new College();
        update.setName("Non Existent");

        repository.updateCollege(tenantContext, randomUuid, update, 1);
    }

    // =========================================================================
    // 6. Malformed / Non-UUID ID -> 404 Not Found (Safe handling, prevents 22P02)
    // =========================================================================

    @Test(expected = InstituteNotFoundException.class)
    public void testMalformedIdThrowsNotFoundInMemory() {
        College update = new College();
        update.setName("Invalid ID");
        repository.updateCollege(tenantContext, "MALFORMED_NON_UUID", update, 1);
    }

    @Test(expected = InstituteNotFoundException.class)
    public void testMalformedIdThrowsNotFoundPostgresRepoBeforeSql() {
        College update = new College();
        update.setName("Invalid ID");
        postgresRepository.updateCollege(tenantContext, "NOT-A-UUID", update, 1);
    }

    @Test
    public void testMalformedIdGetByIdReturnsEmptyWithoutSql() {
        Optional<College> optPostgres = postgresRepository.getCollegeById(tenantContext, "NOT-A-UUID");
        assertFalse(optPostgres.isPresent());

        Optional<College> optInMemory = repository.getCollegeById(tenantContext, "NOT-A-UUID");
        assertFalse(optInMemory.isPresent());
    }

    // =========================================================================
    // 7. Optimistic Locking / Row Version Behavior
    // =========================================================================

    @Test(expected = UserProfileConflictException.class)
    public void testStaleRowVersionThrowsConflict() {
        College created = repository.createCollege(tenantContext, createSampleCollege("OPT_COL_01", tenantId.toString()));

        College update = new College();
        update.setName("Stale Update");

        repository.updateCollege(tenantContext, created.getId(), update, 99);
    }

    @Test(expected = SecurityViolationException.class)
    public void testImmutableIdentityKeyModificationThrowsViolation() {
        College created = repository.createCollege(tenantContext, createSampleCollege("IMMUT_COL_01", tenantId.toString()));

        College update = new College();
        update.setCollegeCode("IMMUT_COL_CHANGED");

        repository.updateCollege(tenantContext, created.getId(), update, 1);
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

        repository.createCollege(nonAdminCtx, createSampleCollege("UNAUTH_COL_01", tenantId.toString()));
    }

    @Test
    public void testPlatformAdminCreationAllowedWhenInWhitelist() {
        UUID configuredAdmin = UUID.randomUUID();
        repository.addPlatformAdmin(configuredAdmin);

        UserSecurityContext adminCtx = UserSecurityContext.forPlatformAdmin(configuredAdmin);
        College created = repository.createCollege(adminCtx, createSampleCollege("AUTH_COL_01", tenantId.toString()));
        assertNotNull(created);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testMissingSecurityContextThrowsAccessDenied() {
        repository.createCollege(null, createSampleCollege("NO_CTX", tenantId.toString()));
    }

    // =========================================================================
    // 9. Tenant Isolation on Non-Platform Access
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testCrossTenantCreationDeniedForTenantAdmin() {
        UUID differentTenantId = UUID.randomUUID();
        College col = createSampleCollege("CROSS_COL_01", differentTenantId.toString());

        // Caller belongs to tenantId, but attempts to create under differentTenantId -> 403 Forbidden
        repository.createCollege(tenantContext, col);
    }

    @Test
    public void testCrossTenantReadingReturnsEmpty() {
        College col = repository.createCollege(tenantContext, createSampleCollege("ISO_COL_01", tenantId.toString()));

        UUID differentTenantId = UUID.randomUUID();
        UserSecurityContext diffContext = new UserSecurityContext(UUID.randomUUID(), differentTenantId, false);

        Optional<College> opt = repository.getCollegeById(diffContext, col.getId());
        assertFalse("Cross-tenant access must return empty Optional", opt.isPresent());
    }

    // =========================================================================
    // 10. Defensive Validation in PostgresCollegeRepository
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testPostgresRepoRejectsNullSecurityContext() {
        postgresRepository.createCollege(null, createSampleCollege("NULL_CTX", tenantId.toString()));
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsNullPayload() {
        postgresRepository.createCollege(tenantContext, null);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsMissingCollegeCode() {
        College col = new College();
        col.setName("Some Name");
        col.setInstituteId(tenantId.toString());
        postgresRepository.createCollege(tenantContext, col);
    }

    @Test(expected = MalformedPayloadException.class)
    public void testPostgresRepoRejectsMissingName() {
        College col = new College();
        col.setCollegeCode("VALID_CODE");
        col.setInstituteId(tenantId.toString());
        postgresRepository.createCollege(tenantContext, col);
    }

    @Test(expected = InstituteNotFoundException.class)
    public void testPostgresRepoRejectsMissingInstituteId() {
        College col = new College();
        col.setCollegeCode("VALID_CODE");
        col.setName("Valid Name");
        postgresRepository.createCollege(tenantContext, col);
    }

    @Test(expected = InstituteNotFoundException.class)
    public void testPostgresRepoRejectsMalformedInstituteId() {
        College col = new College();
        col.setCollegeCode("VALID_CODE");
        col.setName("Valid Name");
        col.setInstituteId("NOT-A-UUID");
        postgresRepository.createCollege(tenantContext, col);
    }
}
