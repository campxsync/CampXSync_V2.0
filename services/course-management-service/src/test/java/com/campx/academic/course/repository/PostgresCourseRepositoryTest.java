package com.campx.academic.course.repository;

import com.campx.academic.course.config.DatabaseConfig;
import com.campx.academic.course.config.DatabaseConnectionManager;
import com.campx.academic.course.config.TestEnvironmentGuard;
import com.campx.academic.course.exception.CourseCodeConflictException;
import com.campx.academic.course.model.CourseModels.Course;
import com.campx.academic.course.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresCourseRepository} against PostgreSQL.
 * Verifies course creation, retrieval, listing, updates, and cross-tenant isolation under RLS.
 * <p>
 * Security & Environment Guardrails:
 * - Fails safely (skips) unless an explicitly designated test environment (CAMPX_TEST_ENV)
 *   and test tenant (CAMPX_TEST_TENANT_ID) are configured.
 * - Prevents accidental execution against arbitrary production tenants.
 * - Uses collision-resistant UUID-based course codes.
 * - Performs hard cleanup during teardown to avoid polluting live databases with persistent soft-deleted test records.
 */
public class PostgresCourseRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresCourseRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private final List<String> createdCourseIds = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        tenantId = TestEnvironmentGuard.verifySafeStagingTenant(connectionManager);
        repository = new PostgresCourseRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            UUID adminUserId = TestEnvironmentGuard.resolveSafeTestUserId(conn, tenantId);
            tenantContext = new UserSecurityContext(adminUserId, tenantId);
        }

        otherTenantId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        otherTenantContext = new UserSecurityContext(otherUserId, otherTenantId);
    }

    @After
    public void tearDown() {
        if (connectionManager == null || createdCourseIds.isEmpty()) {
            return;
        }

        // Hard cleanup of all test courses created during execution to avoid persistent soft-deleted rows
        try (Connection conn = connectionManager.getConnection()) {
            for (String courseId : createdCourseIds) {
                try {
                    UUID cid = UUID.fromString(courseId);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM acd.course_program_map WHERE course_id = ?")) {
                        ps.setObject(1, cid);
                        ps.executeUpdate();
                    } catch (Exception ignored) {}
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM acd.course_prerequisites WHERE course_id = ? OR prerequisite_course_id = ?")) {
                        ps.setObject(1, cid);
                        ps.setObject(2, cid);
                        ps.executeUpdate();
                    } catch (Exception ignored) {}
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM acd.course_versions WHERE course_id = ?")) {
                        ps.setObject(1, cid);
                        ps.executeUpdate();
                    } catch (Exception ignored) {}
                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM acd.courses WHERE id = ?")) {
                        ps.setObject(1, cid);
                        ps.executeUpdate();
                    } catch (Exception ignored) {}
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
        createdCourseIds.clear();
    }

    private String generateTestCourseCode(String prefix) {
        return "TEST_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private Course createAndTrackCourse(Course course) {
        Course created = repository.createCourse(tenantContext, course);
        if (created != null && created.getId() != null) {
            createdCourseIds.add(created.getId());
        }
        return created;
    }

    @Test
    public void testCreateAndRetrieveCourse() {
        Course course = new Course();
        String code = generateTestCourseCode("CS");
        course.setCourseCode(code);
        course.setCourseName("Data Structures & Algorithms");
        course.setDescription("Comprehensive analysis of linear and tree data structures.");
        course.setTotalCredits(4.0);

        Course created = createAndTrackCourse(course);
        assertNotNull(created.getId());
        assertEquals(code, created.getCourseCode());
        assertEquals("DRAFT", created.getStatus());

        Optional<Course> found = repository.findById(tenantContext, created.getId());
        assertTrue(found.isPresent());
        assertEquals("Data Structures & Algorithms", found.get().getCourseName());
        assertEquals(code, found.get().getCourseCode());
        assertEquals(4.0, found.get().getTotalCredits(), 0.001);
    }

    @Test
    public void testCrossTenantIsolationReturnsEmpty() {
        Course course = new Course();
        String code = generateTestCourseCode("ISO");
        course.setCourseCode(code);
        course.setCourseName("Tenant Isolated Course");
        course.setTotalCredits(3.0);

        Course created = createAndTrackCourse(course);

        // Querying from another tenant must NOT see this course
        Optional<Course> isolated = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must return empty", isolated.isPresent());

        List<Course> otherList = repository.listCourses(otherTenantContext, null);
        for (Course c : otherList) {
            assertNotEquals(created.getId(), c.getId());
        }
    }

    @Test(expected = CourseCodeConflictException.class)
    public void testDuplicateCourseCodeRejected() {
        String code = generateTestCourseCode("DUP");

        Course course1 = new Course();
        course1.setCourseCode(code);
        course1.setCourseName("Duplicate Course 1");
        course1.setTotalCredits(3.0);
        createAndTrackCourse(course1);

        Course course2 = new Course();
        course2.setCourseCode(code);
        course2.setCourseName("Duplicate Course 2");
        course2.setTotalCredits(3.0);
        createAndTrackCourse(course2);
    }

    @Test
    public void testUpdateCourse() {
        Course course = new Course();
        String code = generateTestCourseCode("UPD");
        course.setCourseCode(code);
        course.setCourseName("Initial Course Title");
        course.setTotalCredits(4.0);

        Course created = createAndTrackCourse(course);

        created.setCourseName("Updated Course Title");
        created.setDescription("Updated course syllabus and topics.");
        created.setStatus("ACTIVE");
        repository.updateCourse(tenantContext, created);

        Optional<Course> updated = repository.findById(tenantContext, created.getId());
        assertTrue(updated.isPresent());
        assertEquals("Updated Course Title", updated.get().getCourseName());
        assertEquals("Updated course syllabus and topics.", updated.get().getDescription());
        assertEquals("ACTIVE", updated.get().getStatus());
    }

    @Test
    public void testCreateAndRetrieveCourseWithLevel() {
        Course course = new Course();
        String code = generateTestCourseCode("LVL");
        course.setCourseCode(code);
        course.setCourseName("Advanced Algorithms");
        course.setDescription("Graduate-level algorithm design and analysis.");
        course.setTotalCredits(4.0);
        course.setLevel("400");

        Course created = createAndTrackCourse(course);
        assertNotNull(created.getId());
        assertEquals("400", created.getLevel());

        Optional<Course> found = repository.findById(tenantContext, created.getId());
        assertTrue(found.isPresent());
        assertEquals("400", found.get().getLevel());
        assertEquals(4.0, found.get().getTotalCredits(), 0.001);
    }

    @Test
    public void testUpdateSupportedFields() {
        Course course = new Course();
        String code = generateTestCourseCode("FLD");
        course.setCourseCode(code);
        course.setCourseName("Initial Title");
        course.setDescription("Initial Description");
        course.setTotalCredits(3.0);
        course.setLevel("100");

        Course created = createAndTrackCourse(course);

        created.setCourseName("Updated Title");
        created.setDescription("Updated Description");
        created.setTotalCredits(4.5);
        created.setLevel("200");
        repository.updateCourse(tenantContext, created);

        Optional<Course> updated = repository.findById(tenantContext, created.getId());
        assertTrue(updated.isPresent());
        assertEquals("Updated Title", updated.get().getCourseName());
        assertEquals("Updated Description", updated.get().getDescription());
        assertEquals(4.5, updated.get().getTotalCredits(), 0.001);
        assertEquals("200", updated.get().getLevel());
    }

    @Test
    public void testLifecycleStatusPersistence() {
        Course course = new Course();
        String code = generateTestCourseCode("LC");
        course.setCourseCode(code);
        course.setCourseName("Lifecycle Verification Course");
        course.setTotalCredits(3.0);

        Course created = createAndTrackCourse(course);
        assertEquals("DRAFT", created.getStatus());

        // Advance to UNDER_REVIEW
        repository.updateStatus(tenantContext, created.getId(), "UNDER_REVIEW");
        Optional<Course> c1 = repository.findById(tenantContext, created.getId());
        assertTrue(c1.isPresent());
        assertEquals("UNDER_REVIEW", c1.get().getStatus());

        // Advance to APPROVED
        repository.updateStatus(tenantContext, created.getId(), "APPROVED");
        Optional<Course> c2 = repository.findById(tenantContext, created.getId());
        assertTrue(c2.isPresent());
        assertEquals("APPROVED", c2.get().getStatus());

        // Publish to ACTIVE
        repository.updateStatus(tenantContext, created.getId(), "ACTIVE");
        Optional<Course> c3 = repository.findById(tenantContext, created.getId());
        assertTrue(c3.isPresent());
        assertEquals("ACTIVE", c3.get().getStatus());

        // Suspend
        repository.updateStatus(tenantContext, created.getId(), "SUSPENDED");
        Optional<Course> c4 = repository.findById(tenantContext, created.getId());
        assertTrue(c4.isPresent());
        assertEquals("SUSPENDED", c4.get().getStatus());
    }

    @Test
    public void testPartialUpdatePreservesLifecycleStateAndMetadata() {
        Course course = new Course();
        String code = generateTestCourseCode("MD");
        course.setCourseCode(code);
        course.setCourseName("Metadata Preservation Test");
        course.setDescription("Initial Description");
        course.setTotalCredits(3.0);
        course.setLevel("100");
        course.setCourseType("CORE");
        course.setCourseCategory("MAJOR");
        course.setDurationYears(2);
        course.setTags(Arrays.asList("ai", "systems"));

        Course created = createAndTrackCourse(course);
        assertNotNull(created.getId());
        assertEquals("DRAFT", created.getStatus());

        // Advance lifecycle to UNDER_REVIEW
        repository.updateStatus(tenantContext, created.getId(), "UNDER_REVIEW");
        Optional<Course> c1 = repository.findById(tenantContext, created.getId());
        assertTrue(c1.isPresent());
        assertEquals("UNDER_REVIEW", c1.get().getStatus());

        // Perform partial update with null status (e.g. only course name and credits updated)
        Course partialUpdate = new Course();
        partialUpdate.setId(created.getId());
        partialUpdate.setCourseName("Updated Course Name Only");
        partialUpdate.setTotalCredits(4.0);
        // status is left null
        repository.updateCourse(tenantContext, partialUpdate);

        Optional<Course> afterPartial = repository.findById(tenantContext, created.getId());
        assertTrue(afterPartial.isPresent());
        assertEquals("Updated Course Name Only", afterPartial.get().getCourseName());
        assertEquals(4.0, afterPartial.get().getTotalCredits(), 0.001);
        // Verify lifecycle state preserved
        assertEquals("UNDER_REVIEW", afterPartial.get().getStatus());
        // Verify unrelated metadata preserved
        assertEquals("100", afterPartial.get().getLevel());
        assertEquals("CORE", afterPartial.get().getCourseType());
        assertEquals("MAJOR", afterPartial.get().getCourseCategory());
        assertEquals(2, afterPartial.get().getDurationYears());
        assertNotNull(afterPartial.get().getTags());
        assertTrue(afterPartial.get().getTags().contains("ai"));
    }

    @Test
    public void testSoftDeletionSetsDeletedAtAndExcludesFromRetrieval() throws Exception {
        Course course = new Course();
        String code = generateTestCourseCode("DEL");
        course.setCourseCode(code);
        course.setCourseName("Soft Delete Verification Course");
        course.setTotalCredits(3.0);

        Course created = createAndTrackCourse(course);
        String courseId = created.getId();

        // Verify it exists initially
        assertTrue(repository.findById(tenantContext, courseId).isPresent());

        // Soft delete the course
        repository.deleteCourse(tenantContext, courseId);

        // findById must return empty
        Optional<Course> deletedLookup = repository.findById(tenantContext, courseId);
        assertFalse("Soft deleted course must not be found via findById", deletedLookup.isPresent());

        // listCourses must exclude the course
        List<Course> list = repository.listCourses(tenantContext, null);
        for (Course c : list) {
            assertNotEquals("Soft deleted course must not appear in listCourses", courseId, c.getId());
        }

        // Verify in DB that deleted_at is actually set (not hard deleted initially)
        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT deleted_at FROM acd.courses WHERE id = ?::uuid")) {
                ps.setString(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Row should still exist in acd.courses with deleted_at set", rs.next());
                    assertNotNull("deleted_at timestamp must be populated", rs.getTimestamp("deleted_at"));
                }
            }
        }
    }

    @Test
    public void testEmptyTenantCourseListRemainsEmpty() {
        // Generate a random tenant ID with no courses
        UUID emptyTenantId = UUID.randomUUID();
        UserSecurityContext emptyContext = new UserSecurityContext(UUID.randomUUID(), emptyTenantId);

        List<Course> courses = repository.listCourses(emptyContext, null);
        assertNotNull(courses);
        assertTrue("Querying a tenant with no courses must return an empty list", courses.isEmpty());
    }
}

