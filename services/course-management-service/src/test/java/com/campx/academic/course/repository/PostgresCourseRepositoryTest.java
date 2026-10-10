package com.campx.academic.course.repository;

import com.campx.academic.course.config.DatabaseConfig;
import com.campx.academic.course.config.DatabaseConnectionManager;
import com.campx.academic.course.exception.CourseCodeConflictException;
import com.campx.academic.course.model.CourseModels.Course;
import com.campx.academic.course.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresCourseRepository} against PostgreSQL.
 * Verifies course creation, retrieval, listing, updates, and cross-tenant isolation under RLS.
 */
public class PostgresCourseRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresCourseRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private String testCourseId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresCourseRepository(connectionManager);

        // Discover active tenant in core.tenants
        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }

            assumeNotNull("Requires at least one active tenant in core.tenants", tenantId);

            UUID adminUserId = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_id FROM iam.role_assignments WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        adminUserId = (UUID) rs.getObject("user_id");
                    }
                }
            }
            if (adminUserId == null) {
                adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
            }
            tenantContext = new UserSecurityContext(adminUserId, tenantId);
        }

        otherTenantId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        otherTenantContext = new UserSecurityContext(otherUserId, otherTenantId);
    }

    @After
    public void tearDown() {
        if (testCourseId != null && tenantContext != null) {
            try {
                repository.deleteCourse(tenantContext, testCourseId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testCreateAndRetrieveCourse() {
        Course course = new Course();
        String code = "CS" + System.currentTimeMillis() % 100000;
        course.setCourseCode(code);
        course.setCourseName("Data Structures & Algorithms");
        course.setDescription("Comprehensive analysis of linear and tree data structures.");
        course.setTotalCredits(4.0);

        Course created = repository.createCourse(tenantContext, course);
        assertNotNull(created.getId());
        testCourseId = created.getId();
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
        String code = "ISO" + System.currentTimeMillis() % 100000;
        course.setCourseCode(code);
        course.setCourseName("Tenant Isolated Course");
        course.setTotalCredits(3.0);

        Course created = repository.createCourse(tenantContext, course);
        testCourseId = created.getId();

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
        String code = "DUP" + System.currentTimeMillis() % 100000;

        Course course1 = new Course();
        course1.setCourseCode(code);
        course1.setCourseName("Duplicate Course 1");
        course1.setTotalCredits(3.0);
        Course created1 = repository.createCourse(tenantContext, course1);
        testCourseId = created1.getId();

        Course course2 = new Course();
        course2.setCourseCode(code);
        course2.setCourseName("Duplicate Course 2");
        course2.setTotalCredits(3.0);
        repository.createCourse(tenantContext, course2);
    }

    @Test
    public void testUpdateCourse() {
        Course course = new Course();
        String code = "UPD" + System.currentTimeMillis() % 100000;
        course.setCourseCode(code);
        course.setCourseName("Initial Course Title");
        course.setTotalCredits(4.0);

        Course created = repository.createCourse(tenantContext, course);
        testCourseId = created.getId();

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
}
