package com.campx.admin.institute.security;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.model.InstituteModels.NumberSequence;
import com.campx.admin.institute.model.InstituteModels.RoleTemplate;
import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.repository.InMemoryCalendarRepository;
import com.campx.admin.institute.repository.InMemoryNumberSequenceRepository;
import com.campx.admin.institute.repository.InMemoryRoleTemplateRepository;
import com.campx.admin.institute.repository.InMemoryTenantProvisioningRepository;
import com.campx.admin.institute.repository.TenantProvisioningStateMachine;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * MVP-ADM01-06: Platform vs Tenant Tier Isolation Regression Test Suite.
 *
 * Verifies:
 * 1. College-admin (tenant-scoped caller) cannot perform platform-tier mutations (Role Templates, Tenant Provisioning).
 * 2. Authenticated non-admin callers can read platform templates matching RLS SELECT policy, but cannot mutate.
 * 3. Platform identities cannot access tenant-tier data without an explicit tenant context.
 * 4. Tenant context is derived strictly from the authenticated security context, not client payloads.
 * 5. Strict multi-tenant data partitioning in ADM-01 domain repositories.
 */
public class PlatformTierIsolationRegressionTest {

    private UUID platformAdminUser;
    private UUID collegeAdminUser;
    private UUID unauthorizedUser;
    private UUID tenantA;
    private UUID tenantB;

    private UserSecurityContext platformAdminCtx;
    private UserSecurityContext collegeAdminCtxA;
    private UserSecurityContext collegeAdminCtxB;
    private UserSecurityContext spoofedPlatformCtx;

    private InMemoryRoleTemplateRepository roleTemplateRepo;
    private InMemoryTenantProvisioningRepository provisioningRepo;
    private InMemoryCalendarRepository calendarRepo;
    private InMemoryNumberSequenceRepository sequenceRepo;

    @Before
    public void setUp() {
        platformAdminUser = UUID.randomUUID();
        collegeAdminUser = UUID.randomUUID();
        unauthorizedUser = UUID.randomUUID();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        // Platform-scoped context (tenantId == null, platformScope == true)
        platformAdminCtx = UserSecurityContext.forPlatformAdmin(platformAdminUser);

        // College admin contexts (strictly tenant-scoped)
        collegeAdminCtxA = new UserSecurityContext(collegeAdminUser, tenantA);
        collegeAdminCtxB = new UserSecurityContext(collegeAdminUser, tenantB);

        // Spoofed context: claims platform scope (tenantId == null) but user is not in platform admin registry
        spoofedPlatformCtx = new UserSecurityContext(unauthorizedUser, null, true);

        // Repositories initialized with authorized platform admin
        roleTemplateRepo = new InMemoryRoleTemplateRepository(Collections.singletonList(platformAdminUser));
        provisioningRepo = new InMemoryTenantProvisioningRepository(Collections.singletonList(platformAdminUser));
        calendarRepo = new InMemoryCalendarRepository();
        sequenceRepo = new InMemoryNumberSequenceRepository();
    }

    // =========================================================================
    // 1. Platform-Tier Mutation Isolation: Role Templates
    // =========================================================================

    @Test
    public void testCollegeAdminCannotCreateRoleTemplate() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("CAMPUS_DIRECTOR");
        template.setName("Campus Director Template");
        template.setKind("administrative_staff");
        template.setScopeNote("Full operational authority over a college campus");

        try {
            roleTemplateRepo.createRoleTemplate(collegeAdminCtxA, template);
            fail("Expected UserProfileAccessDeniedException when college admin attempts role template creation");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue("Message must indicate platform admin requirement",
                    e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testCollegeAdminCannotUpdateRoleTemplate() {
        // Setup: create template via platform admin
        RoleTemplate template = new RoleTemplate();
        template.setCode("DEPT_HEAD");
        template.setName("Department Head");
        template.setKind("academic_staff");
        RoleTemplate created = roleTemplateRepo.createRoleTemplate(platformAdminCtx, template);

        RoleTemplate updatePayload = new RoleTemplate();
        updatePayload.setId(created.getId());
        updatePayload.setName("Tampered Name");

        try {
            roleTemplateRepo.updateRoleTemplate(collegeAdminCtxA, updatePayload);
            fail("Expected UserProfileAccessDeniedException on role template update by college admin");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testCollegeAdminCannotDeleteRoleTemplate() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("LAB_ASSISTANT");
        template.setName("Lab Assistant");
        template.setKind("technical_staff");
        RoleTemplate created = roleTemplateRepo.createRoleTemplate(platformAdminCtx, template);

        try {
            roleTemplateRepo.deleteRoleTemplate(collegeAdminCtxA, created.getId());
            fail("Expected UserProfileAccessDeniedException on role template deletion by college admin");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testCollegeAdminCannotBindOrUnbindRoleTemplatePermission() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("FACULTY_LEAD");
        template.setName("Faculty Lead");
        template.setKind("academic_staff");
        RoleTemplate created = roleTemplateRepo.createRoleTemplate(platformAdminCtx, template);

        // Bind permission attempt
        try {
            roleTemplateRepo.addPermission(collegeAdminCtxA, created.getCode(), "acd01.course.manage");
            fail("Expected UserProfileAccessDeniedException on addPermission by college admin");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("Platform administrator privileges required"));
        }

        // Unbind permission attempt
        try {
            roleTemplateRepo.removePermission(collegeAdminCtxA, created.getCode(), "acd01.course.manage");
            fail("Expected UserProfileAccessDeniedException on removePermission by college admin");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testNonPlatformAdminInPlatformScopeDeniedRoleTemplateMutations() {
        RoleTemplate template = new RoleTemplate();
        template.setCode("SYSTEM_INTRUDER");
        template.setName("Unauthorized Creation");
        template.setKind("external");

        try {
            roleTemplateRepo.createRoleTemplate(spoofedPlatformCtx, template);
            fail("Expected UserProfileAccessDeniedException when non-platform-admin user attempts platform mutation");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue("Message must indicate lack of platform admin privilege",
                    e.getMessage().contains("lacks required platform administrator privilege"));
        }
    }

    @Test
    public void testCollegeAdminCanReadRoleTemplatesMatchingRlsSelectPolicy() {
        // Setup: create template via platform admin
        RoleTemplate template = new RoleTemplate();
        template.setCode("READ_ONLY_TEMPLATE");
        template.setName("Visible To All Authenticated");
        template.setKind("shared");
        roleTemplateRepo.createRoleTemplate(platformAdminCtx, template);

        // College admin can read matching the RLS SELECT policy
        Optional<RoleTemplate> found = roleTemplateRepo.findByCode(collegeAdminCtxA, "READ_ONLY_TEMPLATE");
        assertTrue("Role template must be visible to authenticated tenant caller", found.isPresent());
        assertEquals("READ_ONLY_TEMPLATE", found.get().getCode());

        List<RoleTemplate> all = roleTemplateRepo.listRoleTemplates(collegeAdminCtxA);
        assertFalse("Role templates list should not be empty for authenticated caller", all.isEmpty());
    }

    // =========================================================================
    // 2. Platform-Tier Mutation Isolation: Tenant Provisioning
    // =========================================================================

    @Test
    public void testCollegeAdminCannotCreateTenantProvisioningJob() {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantA.toString());
        job.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);

        try {
            provisioningRepo.createProvisioningJob(collegeAdminCtxA, job);
            fail("Expected UserProfileAccessDeniedException when college admin attempts to create provisioning job");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue("Message must indicate platform administrator requirement",
                    e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testCollegeAdminCannotUpdateProvisioningJobStatus() {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantA.toString());
        job.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);
        TenantProvisioning created = provisioningRepo.createProvisioningJob(platformAdminCtx, job);
        UUID jobId = UUID.fromString(created.getProvisioningId());

        try {
            provisioningRepo.updateProvisioningStatus(
                    collegeAdminCtxA, jobId, TenantProvisioningStateMachine.STATUS_REQUESTED,
                    TenantProvisioningStateMachine.STATUS_IN_PROGRESS, "INIT_DB");
            fail("Expected UserProfileAccessDeniedException when college admin attempts status transition");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testCollegeAdminCannotRetryProvisioningJob() {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantA.toString());
        job.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);
        TenantProvisioning created = provisioningRepo.createProvisioningJob(platformAdminCtx, job);
        UUID jobId = UUID.fromString(created.getProvisioningId());

        provisioningRepo.updateProvisioningStatus(
                platformAdminCtx, jobId, TenantProvisioningStateMachine.STATUS_REQUESTED,
                TenantProvisioningStateMachine.STATUS_IN_PROGRESS, "INIT_DB");
        provisioningRepo.updateProvisioningStatus(
                platformAdminCtx, jobId, TenantProvisioningStateMachine.STATUS_IN_PROGRESS,
                TenantProvisioningStateMachine.STATUS_FAILED, "INIT_DB");

        try {
            provisioningRepo.retryProvisioningJob(collegeAdminCtxA, jobId);
            fail("Expected UserProfileAccessDeniedException when college admin attempts retry operation");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("Platform administrator privileges required"));
        }
    }

    @Test
    public void testNonPlatformAdminInPlatformScopeDeniedTenantProvisioning() {
        TenantProvisioning job = new TenantProvisioning();
        job.setTenantId(tenantA.toString());
        job.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);

        try {
            provisioningRepo.createProvisioningJob(spoofedPlatformCtx, job);
            fail("Expected UserProfileAccessDeniedException when unauthorized user claims platform scope");
        } catch (UserProfileAccessDeniedException e) {
            assertTrue(e.getMessage().contains("lacks required platform administrator privilege"));
        }
    }

    // =========================================================================
    // 3. Tenant-Tier Access Requires Explicit Tenant Security Context
    // =========================================================================

    @Test
    public void testPlatformIdentityWithoutTenantContextCannotAccessCalendars() {
        Calendar cal = new Calendar();
        cal.setCode("CAL_2026");
        cal.setName("Calendar 2026");
        cal.setCalendarType("ACADEMIC");
        cal.setStatus("DRAFT");

        // Attempting to create a calendar without tenant context in security context
        try {
            calendarRepo.createCalendar(platformAdminCtx, cal);
            fail("Expected SecurityViolationException when calling tenant repository without tenantId");
        } catch (SecurityViolationException e) {
            assertTrue("Must require tenantId in security context",
                    e.getMessage().contains("Missing required security context: tenantId"));
        }

        try {
            calendarRepo.listCalendars(platformAdminCtx);
            fail("Expected SecurityViolationException when listing calendars without tenantId");
        } catch (SecurityViolationException e) {
            assertTrue(e.getMessage().contains("Missing required security context: tenantId"));
        }
    }

    @Test
    public void testPlatformIdentityWithoutTenantContextCannotAccessNumberSequences() {
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey("STU_REG");
        seq.setNextValue(1001);
        seq.setResetPolicy("NEVER");

        try {
            sequenceRepo.createSequence(platformAdminCtx, seq);
            fail("Expected SecurityViolationException when creating sequence without tenant context");
        } catch (SecurityViolationException e) {
            assertTrue("Must require tenantId in security context",
                    e.getMessage().contains("Missing required security context: tenantId"));
        }

        try {
            sequenceRepo.listSequences(platformAdminCtx);
            fail("Expected SecurityViolationException when listing sequences without tenant context");
        } catch (SecurityViolationException e) {
            assertTrue(e.getMessage().contains("Missing required security context: tenantId"));
        }
    }

    @Test
    public void testPlatformAdminCanSafelyTargetTenantViaExplicitContextTransition() {
        // Platform admin transitions context to Tenant A explicitly
        UserSecurityContext scopedPlatformCtxA = platformAdminCtx.withTenant(tenantA);
        assertFalse("Must be tenant-scoped once target tenant is bound", scopedPlatformCtxA.isPlatformScope());
        assertEquals(tenantA, scopedPlatformCtxA.getTenantId());

        Calendar cal = new Calendar();
        cal.setCode("AY_2026_TENA");
        cal.setName("Tenant A 2026 Academic Calendar");
        cal.setCalendarType("ACADEMIC");
        cal.setStatus("PUBLISHED");

        Calendar created = calendarRepo.createCalendar(scopedPlatformCtxA, cal);
        assertNotNull(created.getId());
        assertEquals(tenantA.toString(), created.getTenantId());

        // Platform admin with Tenant B context CANNOT see Tenant A's calendar
        UserSecurityContext scopedPlatformCtxB = platformAdminCtx.withTenant(tenantB);
        List<Calendar> listTenantB = calendarRepo.listCalendars(scopedPlatformCtxB);
        assertTrue("Tenant B must not see Tenant A calendar", listTenantB.isEmpty());
    }

    // =========================================================================
    // 4. Security Context Spoofing & Tenant Payload Binding
    // =========================================================================

    @Test
    public void testTenantContextComesOnlyFromSecurityContextNotClientPayload() {
        // College Admin of Tenant A attempts to inject Tenant B ID into the Calendar entity
        Calendar cal = new Calendar();
        cal.setCode("SPOOF_CAL");
        cal.setName("Spoofing Attempt Calendar");
        cal.setTenantId(tenantB.toString()); // malicious client body payload!
        cal.setCalendarType("ACADEMIC");
        cal.setStatus("DRAFT");

        Calendar created = calendarRepo.createCalendar(collegeAdminCtxA, cal);

        // Repository MUST bind the entity to Tenant A from security context, ignoring payload
        assertEquals("Created entity must be bound to caller's security context tenant ID",
                tenantA.toString(), created.getTenantId());

        // Verify Tenant B caller CANNOT see this calendar
        List<Calendar> tenantBCals = calendarRepo.listCalendars(collegeAdminCtxB);
        assertTrue("Tenant B must have zero visibility into Tenant A data", tenantBCals.isEmpty());

        // Verify Tenant A caller CAN see this calendar
        List<Calendar> tenantACals = calendarRepo.listCalendars(collegeAdminCtxA);
        assertEquals(1, tenantACals.size());
        assertEquals("SPOOF_CAL", tenantACals.get(0).getCode());
    }

    @Test
    public void testCrossTenantProvisioningJobIsolation() {
        // Platform admin provisions Tenant A
        TenantProvisioning jobA = new TenantProvisioning();
        jobA.setTenantId(tenantA.toString());
        jobA.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);
        TenantProvisioning createdA = provisioningRepo.createProvisioningJob(platformAdminCtx, jobA);

        // Platform admin provisions Tenant B
        TenantProvisioning jobB = new TenantProvisioning();
        jobB.setTenantId(tenantB.toString());
        jobB.setProvisioningStatus(TenantProvisioningStateMachine.STATUS_REQUESTED);
        TenantProvisioning createdB = provisioningRepo.createProvisioningJob(platformAdminCtx, jobB);

        UUID jobBId = UUID.fromString(createdB.getProvisioningId());

        // Querying by tenant ID strictly partitions the jobs for tenant callers
        List<TenantProvisioning> listA = provisioningRepo.listProvisioningJobs(collegeAdminCtxA, tenantA);
        assertEquals(1, listA.size());
        assertEquals(createdA.getProvisioningId(), listA.get(0).getProvisioningId());

        List<TenantProvisioning> listB = provisioningRepo.listProvisioningJobs(collegeAdminCtxB, tenantB);
        assertEquals(1, listB.size());
        assertEquals(createdB.getProvisioningId(), listB.get(0).getProvisioningId());

        // College admin of Tenant A cannot lookup Tenant B's job by ID
        Optional<TenantProvisioning> crossLookup = provisioningRepo.getProvisioningJobById(collegeAdminCtxA, jobBId);
        assertFalse("Tenant A caller cannot retrieve Tenant B job by ID", crossLookup.isPresent());

        // College admin of Tenant A querying Tenant B's list gets empty or only their own tenant
        List<TenantProvisioning> spoofedList = provisioningRepo.listProvisioningJobs(collegeAdminCtxA, tenantB);
        // Notice: non-platform caller is strictly bound to caller's security context tenant
        assertTrue("Tenant A caller cannot retrieve Tenant B jobs",
                spoofedList.stream().noneMatch(j -> tenantB.toString().equals(j.getTenantId())));
    }

    // =========================================================================
    // 5. Header Extraction Security Validation
    // =========================================================================

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testHeaderExtractionRejectsMissingTenantHeaderForTenantOperation() {
        UserSecurityContext.fromHeaders(UUID.randomUUID().toString(), null);
    }

    @Test(expected = UserProfileAccessDeniedException.class)
    public void testHeaderExtractionRejectsBlankTenantHeaderForTenantOperation() {
        UserSecurityContext.fromHeaders(UUID.randomUUID().toString(), "   ");
    }

    @Test(expected = SecurityViolationException.class)
    public void testHeaderExtractionRejectsInvalidUuidTenantHeader() {
        UserSecurityContext.fromHeaders(UUID.randomUUID().toString(), "not-a-valid-uuid");
    }
}
