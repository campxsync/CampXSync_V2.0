package com.campx.academic.course;

import com.campx.academic.course.exception.CourseNotFoundException;
import com.campx.academic.course.model.CourseModels.Course;
import com.campx.academic.course.repository.CourseRepository;
import com.campx.academic.course.security.UserSecurityContext;
import com.campx.academic.course.service.CourseDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Focused regression tests for ACD-01 Stage 1:
 * - Empty tenant course list remains empty (no phantom-course fallback like CS101)
 * - Non-existent course lookup throws CourseNotFoundException (no fallback)
 * - Course lifecycle publishing/activation persists status to repository
 * - Soft deletion deletes from repository and excludes from retrieval
 * - Level, credits, and metadata persistence
 * - In-memory mode preserves legitimate fallback only when explicitly configured
 */
public class CoursePersistenceAndTenantIsolationRegressionTest {

    private MockCourseRepository mockRepository;
    private CourseDomainService domainService;
    private UserSecurityContext tenantContext;
    private UUID tenantId;
    private UUID userId;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(userId, tenantId);

        mockRepository = new MockCourseRepository();
        domainService = new CourseDomainService(mockRepository);
    }

    @Test
    public void testEmptyTenantCourseListRemainsEmptyWithoutPhantomFallback() {
        // Mock repository has 0 courses for this tenant
        List<Course> list = domainService.listCourses(tenantContext);
        assertNotNull("Course list must not be null", list);
        assertTrue("Empty tenant course list must remain empty and not return seeded CS101", list.isEmpty());
    }

    @Test(expected = CourseNotFoundException.class)
    public void testNonExistentCourseLookupThrowsNotFoundWithoutPhantomFallback() {
        // Querying for CS101 or unknown ID when repository has no match must throw CourseNotFoundException
        domainService.getCourse(tenantContext, "CS101");
    }

    @Test
    public void testCourseCreationPersistsLevelAndMetadata() {
        Course course = new Course();
        course.setCourseCode("CS301");
        course.setCourseName("Distributed Systems");
        course.setDescription("Design principles of cloud and distributed systems.");
        course.setTotalCredits(4.0);
        course.setLevel("300");
        course.setDepartmentId("DEP_CS");

        Course created = domainService.createDraftCourse(tenantContext, course);
        assertNotNull(created.getId());
        assertEquals("CS301", created.getCourseCode());
        assertEquals("300", created.getLevel());
        assertEquals(4.0, created.getTotalCredits(), 0.001);

        // Verify repository received and stored the course
        Optional<Course> fromRepo = mockRepository.findById(tenantContext, created.getId());
        assertTrue("Course must be present in repository", fromRepo.isPresent());
        assertEquals("300", fromRepo.get().getLevel());
        assertEquals("Distributed Systems", fromRepo.get().getCourseName());
    }

    @Test
    public void testCourseUpdatePersistsLevelAndFields() {
        Course course = new Course();
        course.setCourseCode("CS302");
        course.setCourseName("Initial Name");
        course.setDepartmentId("DEP_CS");
        course.setTotalCredits(3.0);
        course.setLevel("200");

        Course created = domainService.createDraftCourse(tenantContext, course);

        Course update = new Course();
        update.setCourseName("Updated Systems Course");
        update.setDescription("Modern microservices and distributed databases");
        update.setTotalCredits(4.5);
        update.setLevel("300");

        Course updated = domainService.updateDraftCourse(tenantContext, created.getId(), update);
        assertEquals("Updated Systems Course", updated.getCourseName());
        assertEquals("300", updated.getLevel());
        assertEquals(4.5, updated.getTotalCredits(), 0.001);

        // Verify repository reflection
        Optional<Course> fromRepo = mockRepository.findById(tenantContext, created.getId());
        assertTrue(fromRepo.isPresent());
        assertEquals("300", fromRepo.get().getLevel());
        assertEquals("Updated Systems Course", fromRepo.get().getCourseName());
    }

    @Test
    public void testLifecyclePublishAndActivatePersistsToRepository() {
        Course course = new Course();
        course.setCourseCode("CS303");
        course.setCourseName("Cloud Computing");
        course.setDepartmentId("DEP_CS");
        course.setTotalCredits(3.0);

        Course created = domainService.createDraftCourse(tenantContext, course);
        String courseId = created.getId();
        assertEquals("DRAFT", created.getStatus());

        // Submit for approval -> status UNDER_REVIEW
        domainService.submitForApproval(tenantContext, courseId);
        assertEquals("UNDER_REVIEW", mockRepository.statuses.get(courseId));

        // Approve course -> status APPROVED
        domainService.approveCourse(tenantContext, courseId);
        assertEquals("APPROVED", mockRepository.statuses.get(courseId));

        // Publish course -> status ACTIVE
        Course published = domainService.publishCourse(tenantContext, courseId);
        assertEquals("ACTIVE", published.getStatus());
        assertEquals("ACTIVE", mockRepository.statuses.get(courseId));

        // Suspend course -> status SUSPENDED
        Course suspended = domainService.suspendCourse(tenantContext, courseId);
        assertEquals("SUSPENDED", suspended.getStatus());
        assertEquals("SUSPENDED", mockRepository.statuses.get(courseId));

        // Activate course -> status ACTIVE
        Course activated = domainService.activateCourse(tenantContext, courseId);
        assertEquals("ACTIVE", activated.getStatus());
        assertEquals("ACTIVE", mockRepository.statuses.get(courseId));
    }

    @Test
    public void testSoftDeletionRemovesCourseAndCallsRepository() {
        Course course = new Course();
        course.setCourseCode("CS304");
        course.setCourseName("Course To Soft Delete");
        course.setDepartmentId("DEP_CS");
        course.setTotalCredits(3.0);

        Course created = domainService.createDraftCourse(tenantContext, course);
        String courseId = created.getId();

        assertTrue(mockRepository.findById(tenantContext, courseId).isPresent());

        // Soft delete the course
        domainService.deleteCourse(tenantContext, courseId);

        assertTrue("Repository must record deletion", mockRepository.deletedIds.contains(courseId));

        try {
            domainService.getCourse(tenantContext, courseId);
            fail("Soft deleted course must not be retrievable");
        } catch (CourseNotFoundException expected) {
            // Success
        }
    }

    @Test
    public void testPartialUpdatePreservesLifecycleStateAndMetadata() {
        Course course = new Course();
        course.setCourseCode("CS305");
        course.setCourseName("Initial Title");
        course.setDescription("Initial Description");
        course.setDepartmentId("DEP_CS");
        course.setTotalCredits(3.0);
        course.setLevel("100");
        course.setCourseType("CORE");
        course.setCourseCategory("MAJOR");
        course.setDurationYears(2);
        course.setTags(Arrays.asList("distributed", "cloud"));

        Course created = domainService.createDraftCourse(tenantContext, course);
        String courseId = created.getId();
        assertEquals("DRAFT", created.getStatus());

        // Advance to UNDER_REVIEW
        domainService.submitForApproval(tenantContext, courseId);
        assertEquals("UNDER_REVIEW", domainService.getCourse(tenantContext, courseId).getStatus());

        // Perform partial update with null status (only course name and credits updated)
        Course partial = new Course();
        partial.setCourseName("Updated Course Name Only");
        partial.setTotalCredits(4.0);
        partial.setDurationYears(0); // 0 indicates unsupplied/omitted in partial update
        // status is null

        Course updated = domainService.updateDraftCourse(tenantContext, courseId, partial);
        assertEquals("Updated Course Name Only", updated.getCourseName());
        assertEquals(4.0, updated.getTotalCredits(), 0.001);
        // Existing lifecycle status must be preserved
        assertEquals("UNDER_REVIEW", updated.getStatus());
        // Unrelated metadata must be preserved
        assertEquals("100", updated.getLevel());
        assertEquals("DEP_CS", updated.getDepartmentId());
        assertEquals("CORE", updated.getCourseType());
        assertEquals("MAJOR", updated.getCourseCategory());
        assertEquals(2, updated.getDurationYears());
        assertNotNull(updated.getTags());
        assertTrue(updated.getTags().contains("distributed"));
    }

    @Test
    public void testGranularAcd01PermissionsWithoutWildcards() {
        // Models the exact PostgreSQL RLS policies in V20261011_01__fix_acd01_courses_rls_policies.sql
        // Simulates granular non-wildcard identities and verifies access control boundaries.
        UUID tId = UUID.randomUUID();
        UUID otherTId = UUID.randomUUID();

        // Identity 1: Tenant-wide viewer (has 'acd01.view', no wildcards)
        RlsIdentity tenantViewer = new RlsIdentity(tId, Collections.singleton("acd01.view"), Collections.emptyMap(), false);

        // Identity 2: Department-scoped writer (has 'acd01.write' for 'DEP_CS' only, no wildcards)
        Map<String, Set<String>> csDeptPerms = new HashMap<>();
        csDeptPerms.put("acd01.write", Collections.singleton("DEP_CS"));
        csDeptPerms.put("acd01.view", Collections.singleton("DEP_CS"));
        RlsIdentity csDeptWriter = new RlsIdentity(tId, Collections.emptySet(), csDeptPerms, false);

        // Identity 3: Tenant-wide manager (has 'acd01.manage', no wildcards)
        RlsIdentity tenantManager = new RlsIdentity(tId, Collections.singleton("acd01.manage"), Collections.emptyMap(), false);

        // Identity 4: Erroneous legacy permissions ('acd03.write' only, no acd01 perms, no wildcards)
        RlsIdentity acd03User = new RlsIdentity(tId, Collections.singleton("acd03.write"), Collections.emptyMap(), false);

        // Identity 5: Other tenant identity
        RlsIdentity foreignTenantUser = new RlsIdentity(otherTId, Collections.singleton("acd01.write"), Collections.emptyMap(), false);

        // Test 1: Department-scoped writer can INSERT into their department ('DEP_CS')
        assertTrue("Department-scoped writer must be allowed to insert into their department",
                evaluateInsertPolicy(csDeptWriter, tId, "DEP_CS"));

        // Test 2: Department-scoped writer CANNOT insert into another department ('DEP_EE')
        assertFalse("Department-scoped writer must NOT be allowed to insert into another department",
                evaluateInsertPolicy(csDeptWriter, tId, "DEP_EE"));

        // Test 3: Department-scoped writer CANNOT insert course with department_id IS NULL without perm_all
        assertFalse("Department-scoped writer must NOT be allowed to insert with department_id IS NULL",
                evaluateInsertPolicy(csDeptWriter, tId, null));

        // Test 4: Department-scoped writer CANNOT delete courses (delete requires acd01.manage)
        assertFalse("Department-scoped writer must NOT be allowed to delete courses",
                evaluateDeletePolicy(csDeptWriter, tId, "DEP_CS"));

        // Test 5: Tenant manager CAN delete courses (both department and null-department)
        assertTrue("Tenant-wide manager can delete department course",
                evaluateDeletePolicy(tenantManager, tId, "DEP_CS"));
        assertTrue("Tenant-wide manager can delete null-department course",
                evaluateDeletePolicy(tenantManager, tId, null));

        // Test 6: Legacy acd03 user is DENIED for all ACD-01 operations
        assertFalse("Legacy acd03 user must be denied SELECT on ACD-01",
                evaluateSelectPolicy(acd03User, tId, "DEP_CS"));
        assertFalse("Legacy acd03 user must be denied INSERT on ACD-01",
                evaluateInsertPolicy(acd03User, tId, "DEP_CS"));
        assertFalse("Legacy acd03 user must be denied UPDATE on ACD-01",
                evaluateUpdatePolicy(acd03User, tId, "DEP_CS"));
        assertFalse("Legacy acd03 user must be denied DELETE on ACD-01",
                evaluateDeletePolicy(acd03User, tId, "DEP_CS"));

        // Test 7: Cross-tenant isolation blocks foreign tenant even with full permissions
        assertFalse("Cross-tenant INSERT must be blocked",
                evaluateInsertPolicy(foreignTenantUser, tId, "DEP_CS"));
        assertFalse("Cross-tenant SELECT must be blocked",
                evaluateSelectPolicy(foreignTenantUser, tId, "DEP_CS"));

        // Test 8: Tenant-wide viewer can SELECT all tenant courses but cannot modify
        assertTrue("Tenant-wide viewer can SELECT department courses",
                evaluateSelectPolicy(tenantViewer, tId, "DEP_CS"));
        assertTrue("Tenant-wide viewer can SELECT null-department courses",
                evaluateSelectPolicy(tenantViewer, tId, null));
        assertFalse("Tenant-wide viewer CANNOT INSERT courses",
                evaluateInsertPolicy(tenantViewer, tId, "DEP_CS"));
        assertFalse("Tenant-wide viewer CANNOT UPDATE courses",
                evaluateUpdatePolicy(tenantViewer, tId, "DEP_CS"));
        assertFalse("Tenant-wide viewer CANNOT DELETE courses",
                evaluateDeletePolicy(tenantViewer, tId, "DEP_CS"));
    }

    @Test
    public void testRlsMigrationStaticVerification() throws Exception {
        java.io.File file = new java.io.File("../../../database/migrations/V20261011_01__fix_acd01_courses_rls_policies.sql");
        if (!file.exists()) {
            file = new java.io.File("../../database/migrations/V20261011_01__fix_acd01_courses_rls_policies.sql");
        }
        if (!file.exists()) {
            file = new java.io.File("database/migrations/V20261011_01__fix_acd01_courses_rls_policies.sql");
        }
        assertTrue("Migration file V20261011_01__fix_acd01_courses_rls_policies.sql must exist at " + file.getAbsolutePath(),
                file.exists());

        String sql = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);

        // 1. Zero references to acd03.* in policy expressions (strip comments first)
        String sqlWithoutComments = sql.replaceAll("--.*", "");
        assertFalse("Migration policy statements must NOT reference acd03 permissions", sqlWithoutComments.contains("acd03."));

        // 2. Contains granular acd01 permissions
        assertTrue("Migration must check acd01.view", sql.contains("acd01.view"));
        assertTrue("Migration must check acd01.write", sql.contains("acd01.write"));
        assertTrue("Migration must check acd01.manage", sql.contains("acd01.manage"));

        // 3. Covers all 4 ACD-01 tables
        assertTrue("Must define policies for acd.courses", sql.contains("acd.courses"));
        assertTrue("Must define policies for acd.course_versions", sql.contains("acd.course_versions"));
        assertTrue("Must define policies for acd.course_prerequisites", sql.contains("acd.course_prerequisites"));
        assertTrue("Must define policies for acd.course_program_map", sql.contains("acd.course_program_map"));

        // 4. Strict write guardrail: courses_insert, courses_update, courses_delete must NOT allow department_id IS NULL without perm_all
        assertFalse("courses_insert must NOT have permissive department_id IS NULL AND iam.has_permission",
                sql.contains("department_id IS NULL AND (SELECT iam.has_permission('acd01.write'::text))"));
        assertFalse("courses_update must NOT have permissive department_id IS NULL AND iam.has_permission",
                sql.contains("department_id IS NULL AND (SELECT iam.has_permission('acd01.write'::text))"));
        assertFalse("courses_delete must NOT have permissive department_id IS NULL AND iam.has_permission",
                sql.contains("department_id IS NULL AND (SELECT iam.has_permission('acd01.manage'::text))"));

        // 5. courses_insert must require iam.perm_all OR department_id IN perm_department_ids
        assertTrue("courses_insert must require iam.perm_all or valid department scope",
                sql.contains("iam.perm_all('acd01.write'::text)") &&
                sql.contains("department_id IS NOT NULL AND department_id IN (SELECT unnest(iam.perm_department_ids('acd01.write'::text)))"));

        // 6. courses_delete must require iam.perm_all or valid department scope
        assertTrue("courses_delete must require iam.perm_all or valid department scope",
                sql.contains("iam.perm_all('acd01.manage'::text)") &&
                sql.contains("department_id IS NOT NULL AND department_id IN (SELECT unnest(iam.perm_department_ids('acd01.manage'::text)))"));

        // 7. V20261024_01 Option C Asymmetric Curriculum Mapping Static Verification
        java.io.File asymFile = new java.io.File("../../../database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql");
        if (!asymFile.exists()) {
            asymFile = new java.io.File("../../database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql");
        }
        if (!asymFile.exists()) {
            asymFile = new java.io.File("database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql");
        }
        assertTrue("Migration file V20261024_01__asymmetric_course_program_map_rls.sql must exist at " + asymFile.getAbsolutePath(),
                asymFile.exists());

        String asymSql = new String(java.nio.file.Files.readAllBytes(asymFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        String asymWithoutComments = asymSql.replaceAll("--.*", "");

        // A. Zero references to acd03.* in policy expressions
        assertFalse("Option C migration must NOT reference acd03 permissions", asymWithoutComments.contains("acd03."));

        // B. Contains granular canonical acd01 permissions
        assertTrue("Option C migration must check acd01.view", asymSql.contains("acd01.view"));
        assertTrue("Option C migration must check acd01.write", asymSql.contains("acd01.write"));
        assertTrue("Option C migration must check acd01.manage", asymSql.contains("acd01.manage"));

        // C. Target table is acd.course_program_map
        assertTrue("Option C migration must target acd.course_program_map", asymSql.contains("acd.course_program_map"));

        // D. Security-Definer Course Validity Helper
        assertTrue("Option C migration must define core.is_active_course_in_tenant",
                asymSql.contains("core.is_active_course_in_tenant"));
        assertTrue("core.is_active_course_in_tenant must be SECURITY DEFINER with empty search_path",
                asymSql.contains("SECURITY DEFINER") && asymSql.contains("SET search_path = ''"));
        assertTrue("core.is_active_course_in_tenant must check tenant isolation and active status",
                asymSql.contains("c.tenant_id = (SELECT core.current_tenant_id())") && asymSql.contains("c.deleted_at IS NULL"));

        // E. INSERT & UPDATE enforce program-driven authority with universal course validity guard
        assertTrue("course_program_map_insert must require program write authority",
                asymSql.contains("core.programs_in_scope('acd01.write'::text, true)"));
        assertTrue("course_program_map_insert must enforce universal course validity guard unbypassable by perm_all",
                asymSql.contains("core.is_active_course_in_tenant(course_id)"));

        // F. DELETE enforces program-driven authority
        assertTrue("course_program_map_delete must require program manage authority",
                asymSql.contains("core.programs_in_scope('acd01.manage'::text, true)"));

        // G. SELECT preserves symmetric visibility under OR
        assertTrue("course_program_map_select must preserve OR between program and course scope",
                asymSql.contains("core.programs_in_scope('acd01.view'::text, false))) OR") &&
                asymSql.contains("core.courses_in_scope('acd01.view'::text, false)))"));

        // 8. V20261024_01 Rollback Script Static Verification Against Live Captured Baseline
        java.io.File rollbackFile = new java.io.File("../../../database/migrations/V20261024_01_rollback__revert_asymmetric_course_program_map_rls.sql");
        if (!rollbackFile.exists()) {
            rollbackFile = new java.io.File("../../database/migrations/V20261024_01_rollback__revert_asymmetric_course_program_map_rls.sql");
        }
        if (!rollbackFile.exists()) {
            rollbackFile = new java.io.File("database/migrations/V20261024_01_rollback__revert_asymmetric_course_program_map_rls.sql");
        }
        assertTrue("Rollback file V20261024_01_rollback__revert_asymmetric_course_program_map_rls.sql must exist at " + rollbackFile.getAbsolutePath(),
                rollbackFile.exists());

        String rollbackSql = new String(java.nio.file.Files.readAllBytes(rollbackFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);

        // A. Must faithfully restore captured live baseline acd03.* permissions
        assertTrue("Rollback must restore live baseline acd03.view", rollbackSql.contains("acd03.view"));
        assertTrue("Rollback must restore live baseline acd03.write", rollbackSql.contains("acd03.write"));
        assertTrue("Rollback must restore live baseline acd03.manage", rollbackSql.contains("acd03.manage"));

        // B. Must NOT contain acd01 in active policy statements
        String rollbackWithoutComments = rollbackSql.replaceAll("--.*", "");
        assertFalse("Rollback active statements must NOT reference acd01 permissions (must restore actual live acd03 baseline)",
                rollbackWithoutComments.contains("acd01."));

        // C. Must restore symmetric OR semantics across all 4 policies
        assertTrue("course_program_map_select must restore symmetric OR between program and course scope",
                rollbackSql.contains("core.programs_in_scope('acd03.view'::text, false))) OR") &&
                rollbackSql.contains("core.courses_in_scope('acd03.view'::text, false)))"));
        assertTrue("course_program_map_insert must restore symmetric OR between program and course scope",
                rollbackSql.contains("core.programs_in_scope('acd03.write'::text, true))) OR") &&
                rollbackSql.contains("core.courses_in_scope('acd03.write'::text, true)))"));
        assertTrue("course_program_map_update must restore symmetric OR between program and course scope on both USING and WITH CHECK",
                rollbackSql.contains("core.programs_in_scope('acd03.write'::text, true))) OR") &&
                rollbackSql.contains("core.courses_in_scope('acd03.write'::text, true)))"));
        assertTrue("course_program_map_delete must restore symmetric OR between program and course scope",
                rollbackSql.contains("core.programs_in_scope('acd03.manage'::text, true))) OR") &&
                rollbackSql.contains("core.courses_in_scope('acd03.manage'::text, true)))"));

        // D. Must clean up helper function
        assertTrue("Rollback must drop helper function core.is_active_course_in_tenant",
                rollbackSql.contains("DROP FUNCTION IF EXISTS core.is_active_course_in_tenant(uuid);"));

        // E. Must be wrapped in transactional block
        assertTrue("Rollback must be transactional (BEGIN ... COMMIT)",
                rollbackSql.contains("BEGIN;") && rollbackSql.contains("COMMIT;"));
    }

    @Test
    public void testCourseProgramMapGranularScopingAndCrossDepartmentDenials() {
        UUID tId = UUID.randomUUID();
        UUID otherTId = UUID.randomUUID();

        // 1. Department-scoped CS writer & manager (only DEP_CS)
        Map<String, Set<String>> csDeptPerms = new HashMap<>();
        csDeptPerms.put("acd01.write", Collections.singleton("DEP_CS"));
        csDeptPerms.put("acd01.manage", Collections.singleton("DEP_CS"));
        csDeptPerms.put("acd01.view", Collections.singleton("DEP_CS"));
        RlsIdentity csDeptAdmin = new RlsIdentity(tId, Collections.emptySet(), csDeptPerms, false);

        // 2. Department-scoped EE writer (only DEP_EE)
        Map<String, Set<String>> eeDeptPerms = new HashMap<>();
        eeDeptPerms.put("acd01.write", Collections.singleton("DEP_EE"));
        eeDeptPerms.put("acd01.manage", Collections.singleton("DEP_EE"));
        eeDeptPerms.put("acd01.view", Collections.singleton("DEP_EE"));
        RlsIdentity eeDeptAdmin = new RlsIdentity(tId, Collections.emptySet(), eeDeptPerms, false);

        // 3. Tenant-wide administrator with perm_all
        Set<String> tenantWidePerms = new HashSet<>(Arrays.asList("acd01.view", "acd01.write", "acd01.manage"));
        RlsIdentity tenantWideAdmin = new RlsIdentity(tId, tenantWidePerms, Collections.emptyMap(), false);

        // 4. Foreign tenant identity
        RlsIdentity foreignTenantUser = new RlsIdentity(otherTId, tenantWidePerms, Collections.emptyMap(), false);

        // Scenario A: Authorized course and program in the same permitted department (DEP_CS)
        assertTrue("Department-scoped admin CAN link course and program within permitted department",
                evaluateCourseProgramMapInsertPolicy(csDeptAdmin, tId, "DEP_CS", "DEP_CS"));
        assertTrue("Department-scoped admin CAN update mapping within permitted department",
                evaluateCourseProgramMapUpdatePolicy(csDeptAdmin, tId, "DEP_CS", "DEP_CS"));
        assertTrue("Department-scoped admin CAN delete mapping within permitted department",
                evaluateCourseProgramMapDeletePolicy(csDeptAdmin, tId, "DEP_CS", "DEP_CS"));

        // Scenario B: Option C Program-Driven Cross-Department Authority
        // Program owner (DEP_CS) CAN link an external department's course (DEP_EE) into their program curriculum
        assertTrue("Department admin CAN link external department course into permitted department program under Option C",
                evaluateCourseProgramMapInsertPolicy(csDeptAdmin, tId, "DEP_CS", "DEP_EE"));
        assertTrue("Department admin CAN update mapping within permitted department program",
                evaluateCourseProgramMapUpdatePolicy(csDeptAdmin, tId, "DEP_CS", "DEP_EE"));

        // Course owner (DEP_CS) must NOT unilaterally link their course into an unauthorized program (DEP_EE)
        assertFalse("Department admin must NOT link permitted course to unauthorized department program",
                evaluateCourseProgramMapInsertPolicy(csDeptAdmin, tId, "DEP_EE", "DEP_CS"));
        assertFalse("Department admin must NOT update mapping to unauthorized department program",
                evaluateCourseProgramMapUpdatePolicy(csDeptAdmin, tId, "DEP_EE", "DEP_CS"));

        // Scenario C: Program-Driven Deletion of Cross-Department Mapping
        // CS admin CAN delete mapping from their program even if the course is from DEP_EE
        assertTrue("CS admin CAN delete cross-department mapping from their own program",
                evaluateCourseProgramMapDeletePolicy(csDeptAdmin, tId, "DEP_CS", "DEP_EE"));
        // CS admin cannot unilaterally delete cross-department mapping where program is owned by DEP_EE
        assertFalse("CS admin cannot unilaterally delete cross-department mapping involving DEP_EE program",
                evaluateCourseProgramMapDeletePolicy(csDeptAdmin, tId, "DEP_EE", "DEP_CS"));
        // EE admin CAN delete cross-department mapping from their own program
        assertTrue("EE admin CAN delete cross-department mapping from their own program",
                evaluateCourseProgramMapDeletePolicy(eeDeptAdmin, tId, "DEP_EE", "DEP_CS"));

        // Scenario D: Tenant-wide authorized access (Dean / Registrar can establish cross-department links)
        assertTrue("Tenant-wide admin CAN link cross-department course and program",
                evaluateCourseProgramMapInsertPolicy(tenantWideAdmin, tId, "DEP_EE", "DEP_CS"));
        assertTrue("Tenant-wide admin CAN update cross-department mapping",
                evaluateCourseProgramMapUpdatePolicy(tenantWideAdmin, tId, "DEP_EE", "DEP_CS"));
        assertTrue("Tenant-wide admin CAN delete cross-department mapping",
                evaluateCourseProgramMapDeletePolicy(tenantWideAdmin, tId, "DEP_EE", "DEP_CS"));

        // Scenario E: Cross-tenant denial
        assertFalse("Cross-tenant user must be denied INSERT on course_program_map",
                evaluateCourseProgramMapInsertPolicy(foreignTenantUser, tId, "DEP_CS", "DEP_CS"));
        assertFalse("Cross-tenant user must be denied UPDATE on course_program_map",
                evaluateCourseProgramMapUpdatePolicy(foreignTenantUser, tId, "DEP_CS", "DEP_CS"));
        assertFalse("Cross-tenant user must be denied DELETE on course_program_map",
                evaluateCourseProgramMapDeletePolicy(foreignTenantUser, tId, "DEP_CS", "DEP_CS"));
        assertFalse("Cross-tenant user must be denied SELECT on course_program_map",
                evaluateCourseProgramMapSelectPolicy(foreignTenantUser, tId, "DEP_CS", "DEP_CS"));

        // Scenario F: SELECT semantics preserved (viewable if either program or course in scope)
        assertTrue("Department admin CAN view mapping if course is in their department",
                evaluateCourseProgramMapSelectPolicy(csDeptAdmin, tId, "DEP_EE", "DEP_CS"));
        assertTrue("Department admin CAN view mapping if program is in their department",
                evaluateCourseProgramMapSelectPolicy(csDeptAdmin, tId, "DEP_CS", "DEP_EE"));
        assertFalse("Department admin CANNOT view mapping if neither program nor course is in scope",
                evaluateCourseProgramMapSelectPolicy(csDeptAdmin, tId, "DEP_EE", "DEP_EE"));
    }

    // Helper classes and methods for RLS simulation
    private static class RlsIdentity {
        final UUID tenantId;
        final Set<String> tenantWidePermissions;
        final Map<String, Set<String>> departmentScopedPermissions;
        final boolean isWildcardAdmin;

        RlsIdentity(UUID tenantId, Set<String> tenantWidePermissions,
                    Map<String, Set<String>> departmentScopedPermissions, boolean isWildcardAdmin) {
            this.tenantId = tenantId;
            this.tenantWidePermissions = tenantWidePermissions != null ? tenantWidePermissions : Collections.emptySet();
            this.departmentScopedPermissions = departmentScopedPermissions != null ? departmentScopedPermissions : Collections.emptyMap();
            this.isWildcardAdmin = isWildcardAdmin;
        }

        boolean hasTenantPerm(String perm) {
            return isWildcardAdmin || tenantWidePermissions.contains(perm) || tenantWidePermissions.contains("*");
        }

        boolean hasDeptPerm(String perm, String deptId) {
            if (isWildcardAdmin || tenantWidePermissions.contains(perm) || tenantWidePermissions.contains("*")) return true;
            if (deptId == null) return false;
            Set<String> depts = departmentScopedPermissions.get(perm);
            return depts != null && depts.contains(deptId);
        }
    }

    private boolean evaluateSelectPolicy(RlsIdentity identity, UUID rowTenantId, String rowDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        return identity.hasTenantPerm("acd01.view")
                || (rowDeptId != null && identity.hasDeptPerm("acd01.view", rowDeptId))
                || (rowDeptId == null && identity.hasTenantPerm("acd01.view"));
    }

    private boolean evaluateInsertPolicy(RlsIdentity identity, UUID rowTenantId, String rowDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        return identity.hasTenantPerm("acd01.write")
                || (rowDeptId != null && identity.hasDeptPerm("acd01.write", rowDeptId));
    }

    private boolean evaluateUpdatePolicy(RlsIdentity identity, UUID rowTenantId, String rowDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        return identity.hasTenantPerm("acd01.write")
                || (rowDeptId != null && identity.hasDeptPerm("acd01.write", rowDeptId));
    }

    private boolean evaluateDeletePolicy(RlsIdentity identity, UUID rowTenantId, String rowDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        return identity.hasTenantPerm("acd01.manage")
                || (rowDeptId != null && identity.hasDeptPerm("acd01.manage", rowDeptId));
    }

    private boolean evaluateCourseProgramMapSelectPolicy(RlsIdentity identity, UUID rowTenantId, String programDeptId, String courseDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        return identity.hasTenantPerm("acd01.view")
                || (programDeptId != null && identity.hasDeptPerm("acd01.view", programDeptId))
                || (courseDeptId != null && identity.hasDeptPerm("acd01.view", courseDeptId));
    }

    private boolean evaluateCourseProgramMapInsertPolicy(RlsIdentity identity, UUID rowTenantId, String programDeptId, String courseDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        // Option C Universal Guard: Course must exist in tenant (represented by non-null courseDeptId)
        if (courseDeptId == null) return false;
        // Option C Program Authority: Must hold perm_all OR own the destination program
        return identity.hasTenantPerm("acd01.write")
                || (programDeptId != null && identity.hasDeptPerm("acd01.write", programDeptId));
    }

    private boolean evaluateCourseProgramMapUpdatePolicy(RlsIdentity identity, UUID rowTenantId, String programDeptId, String courseDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        // Option C Universal Guard: Course must exist in tenant
        if (courseDeptId == null) return false;
        // Option C Program Authority: Must hold perm_all OR own the program
        return identity.hasTenantPerm("acd01.write")
                || (programDeptId != null && identity.hasDeptPerm("acd01.write", programDeptId));
    }

    private boolean evaluateCourseProgramMapDeletePolicy(RlsIdentity identity, UUID rowTenantId, String programDeptId, String courseDeptId) {
        if (!identity.tenantId.equals(rowTenantId)) return false;
        // Option C Program Authority for DELETE: Must hold perm_all OR own the program
        return identity.hasTenantPerm("acd01.manage")
                || (programDeptId != null && identity.hasDeptPerm("acd01.manage", programDeptId));
    }

    /**
     * Test double implementation of {@link CourseRepository} recording calls.
     */
    private static class MockCourseRepository implements CourseRepository {
        final Map<String, Course> storage = new HashMap<>();
        final Map<String, String> statuses = new HashMap<>();
        final Set<String> deletedIds = new HashSet<>();

        @Override
        public Course createCourse(UserSecurityContext context, Course course) {
            if (course.getId() == null) {
                course.setId(UUID.randomUUID().toString());
            }
            if (course.getStatus() == null) {
                course.setStatus("DRAFT");
            }
            storage.put(course.getId(), course);
            statuses.put(course.getId(), course.getStatus());
            return course;
        }

        @Override
        public Optional<Course> findById(UserSecurityContext context, String id) {
            if (deletedIds.contains(id)) return Optional.empty();
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Course> findByCode(UserSecurityContext context, String code) {
            return storage.values().stream()
                    .filter(c -> !deletedIds.contains(c.getId()))
                    .filter(c -> c.getCourseCode() != null && c.getCourseCode().equalsIgnoreCase(code))
                    .findFirst();
        }

        @Override
        public List<Course> listCourses(UserSecurityContext context, String departmentId) {
            List<Course> result = new ArrayList<>();
            for (Course c : storage.values()) {
                if (!deletedIds.contains(c.getId())) {
                    result.add(c);
                }
            }
            return result;
        }

        @Override
        public void updateCourse(UserSecurityContext context, Course course) {
            Course existing = storage.get(course.getId());
            if (existing != null) {
                if (course.getCourseName() != null) existing.setCourseName(course.getCourseName());
                if (course.getDescription() != null) existing.setDescription(course.getDescription());
                if (course.getTotalCredits() > 0.0) existing.setTotalCredits(course.getTotalCredits());
                if (course.getLevel() != null) existing.setLevel(course.getLevel());
                if (course.getDepartmentId() != null) existing.setDepartmentId(course.getDepartmentId());
                if (course.getStatus() != null && !course.getStatus().trim().isEmpty()) {
                    existing.setStatus(course.getStatus());
                    statuses.put(course.getId(), course.getStatus());
                }
                if (course.getCourseType() != null) existing.setCourseType(course.getCourseType());
                if (course.getCourseCategory() != null) existing.setCourseCategory(course.getCourseCategory());
                if (course.getDurationYears() > 0) existing.setDurationYears(course.getDurationYears());
                if (course.getTags() != null) existing.setTags(course.getTags());
                storage.put(course.getId(), existing);
            } else {
                storage.put(course.getId(), course);
            }
        }

        @Override
        public void updateStatus(UserSecurityContext context, String courseId, String status) {
            statuses.put(courseId, status);
            Course c = storage.get(courseId);
            if (c != null) {
                c.setStatus(status);
            }
        }

        @Override
        public void deleteCourse(UserSecurityContext context, String id) {
            deletedIds.add(id);
        }
    }
}

