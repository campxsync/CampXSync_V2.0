package com.campx.academic.course.repository;

import com.campx.academic.course.config.DatabaseConnectionManager;
import com.campx.academic.course.exception.CourseCodeConflictException;
import com.campx.academic.course.exception.CourseNotFoundException;
import com.campx.academic.course.exception.CourseSecurityException;
import com.campx.academic.course.exception.CourseValidationException;
import com.campx.academic.course.model.CourseModels.Course;
import com.campx.academic.course.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code acd.courses} table.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresCourseRepository implements CourseRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCourseRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresCourseRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCourseRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Course createCourse(UserSecurityContext context, Course course) {
        if (context == null || context.getTenantId() == null) {
            throw CourseSecurityException.unauthorized("Missing required security context: tenantId");
        }
        if (course == null) {
            throw new CourseValidationException("Course payload cannot be null");
        }
        if (course.getCourseCode() == null || course.getCourseCode().trim().isEmpty()) {
            throw new CourseValidationException("Mandatory field 'courseCode' is required");
        }
        if (course.getCourseName() == null || course.getCourseName().trim().isEmpty()) {
            throw new CourseValidationException("Mandatory field 'courseName' is required");
        }

        String code = course.getCourseCode().trim().toUpperCase(Locale.ROOT);
        String name = course.getCourseName().trim();
        String description = course.getDescription();
        double credits = course.getTotalCredits();
        if (credits < 0) {
            throw new CourseValidationException("Credits cannot be negative");
        }

        String status = (course.getStatus() != null && !course.getStatus().trim().isEmpty())
                ? course.getStatus().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        if (!"DRAFT".equals(status) && !"ACTIVE".equals(status) && !"RETIRED".equals(status)) {
            status = "DRAFT";
        }

        UUID tenantUuid = context.getTenantId();

        UUID courseUuid;
        if (course.getId() != null && !course.getId().trim().isEmpty()) {
            try {
                courseUuid = UUID.fromString(course.getId().trim());
            } catch (IllegalArgumentException e) {
                courseUuid = UUID.randomUUID();
            }
        } else {
            courseUuid = UUID.randomUUID();
        }

        UUID finalCourseId = courseUuid;
        String finalStatus = status;

        return context.executeInTransaction(connectionManager, conn -> {
            // Check scoped code uniqueness
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM acd.courses WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL")) {
                checkPs.setObject(1, tenantUuid);
                checkPs.setString(2, code);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new CourseCodeConflictException(code);
                    }
                }
            }

            // Resolve department_id: must be a valid UUID existing in core.departments under this tenant
            UUID depUuid = null;
            if (course.getDepartmentId() != null && !course.getDepartmentId().trim().isEmpty()) {
                try {
                    UUID candidate = UUID.fromString(course.getDepartmentId().trim());
                    try (PreparedStatement depPs = conn.prepareStatement(
                            "SELECT id FROM core.departments WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                        depPs.setObject(1, tenantUuid);
                        depPs.setObject(2, candidate);
                        try (ResultSet rs = depPs.executeQuery()) {
                            if (rs.next()) {
                                depUuid = candidate;
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Non-UUID placeholder (e.g. "DEP_CS")
                }
            }

            String insertSql = "INSERT INTO acd.courses " +
                    "(id, tenant_id, department_id, code, name, description, credits, status, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 1) " +
                    "RETURNING id, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalCourseId);
                ps.setObject(2, tenantUuid);
                ps.setObject(3, depUuid);
                ps.setString(4, code);
                ps.setString(5, name);
                ps.setString(6, description);
                ps.setDouble(7, credits);
                ps.setString(8, finalStatus);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        course.setId(rs.getString("id"));
                        course.setTenantId(tenantUuid.toString());
                        if (depUuid != null) {
                            course.setDepartmentId(depUuid.toString());
                        }
                        course.setCourseCode(code);
                        course.setCourseName(name);
                        course.setStatus(finalStatus);
                        course.setCurrentVersion(rs.getInt("row_version"));
                        Timestamp cat = rs.getTimestamp("created_at");
                        course.setCreatedAt(cat != null ? cat.getTime() : System.currentTimeMillis());
                        Timestamp uat = rs.getTimestamp("updated_at");
                        course.setUpdatedAt(uat != null ? uat.getTime() : System.currentTimeMillis());

                        // Map to program in acd.course_program_map if programId is provided
                        if (course.getProgramId() != null && !course.getProgramId().trim().isEmpty()) {
                            try {
                                UUID progUuid = UUID.fromString(course.getProgramId().trim());
                                try (PreparedStatement progPs = conn.prepareStatement(
                                        "SELECT id FROM acd.programs WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                                    progPs.setObject(1, tenantUuid);
                                    progPs.setObject(2, progUuid);
                                    try (ResultSet prs = progPs.executeQuery()) {
                                        if (prs.next()) {
                                            try (PreparedStatement mapPs = conn.prepareStatement(
                                                    "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                                                    "VALUES (?, ?, ?, ?, now())")) {
                                                mapPs.setObject(1, UUID.randomUUID());
                                                mapPs.setObject(2, tenantUuid);
                                                mapPs.setObject(3, finalCourseId);
                                                mapPs.setObject(4, progUuid);
                                                mapPs.executeUpdate();
                                                logger.info("Mapped course [{}] to program [{}] in acd.course_program_map", finalCourseId, progUuid);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                logger.warn("Could not map course to program: {}", ex.getMessage());
                            }
                        }

                        logger.info("Persisted course [{}] id={} in acd.courses", code, course.getId());
                        return course;
                    }
                }
            }
            throw new RuntimeException("Failed to persist course: insert returned no rows");
        });
    }

    @Override
    public Optional<Course> findById(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        UUID courseUuid;
        try {
            courseUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT c.id, c.tenant_id, c.department_id, c.code, c.name, c.description, c.credits, c.status, c.created_at, c.updated_at, c.row_version, m.program_id " +
                    "FROM acd.courses c " +
                    "LEFT JOIN acd.course_program_map m ON m.tenant_id = c.tenant_id AND m.course_id = c.id " +
                    "WHERE c.id = ? AND c.tenant_id = ? AND c.deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, courseUuid);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<Course> findByCode(UserSecurityContext context, String code) {
        if (context == null || context.getTenantId() == null || code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT c.id, c.tenant_id, c.department_id, c.code, c.name, c.description, c.credits, c.status, c.created_at, c.updated_at, c.row_version, m.program_id " +
                    "FROM acd.courses c " +
                    "LEFT JOIN acd.course_program_map m ON m.tenant_id = c.tenant_id AND m.course_id = c.id " +
                    "WHERE c.code = ? AND c.tenant_id = ? AND c.deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<Course> listCourses(UserSecurityContext context, String departmentId) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        UUID depUuid = null;
        if (departmentId != null && !departmentId.trim().isEmpty()) {
            try {
                depUuid = UUID.fromString(departmentId.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        UUID finalDepUuid = depUuid;

        return context.executeInTransaction(connectionManager, conn -> {
            List<Course> list = new ArrayList<>();
            String sql = "SELECT c.id, c.tenant_id, c.department_id, c.code, c.name, c.description, c.credits, c.status, c.created_at, c.updated_at, c.row_version, m.program_id " +
                    "FROM acd.courses c " +
                    "LEFT JOIN acd.course_program_map m ON m.tenant_id = c.tenant_id AND m.course_id = c.id " +
                    "WHERE c.tenant_id = ? AND c.deleted_at IS NULL " +
                    (finalDepUuid != null ? "AND c.department_id = ? " : "") +
                    "ORDER BY c.code ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                if (finalDepUuid != null) {
                    ps.setObject(2, finalDepUuid);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public void updateCourse(UserSecurityContext context, Course course) {
        if (context == null || context.getTenantId() == null || course == null || course.getId() == null) {
            return;
        }

        UUID courseUuid;
        try {
            courseUuid = UUID.fromString(course.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new CourseNotFoundException("Course", course.getId());
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.courses SET name = ?, description = ?, status = ?, updated_at = now(), row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, course.getCourseName());
                ps.setString(2, course.getDescription());
                ps.setString(3, course.getStatus() != null ? course.getStatus() : "ACTIVE");
                ps.setObject(4, courseUuid);
                ps.setObject(5, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new CourseNotFoundException("Course", course.getId());
                }
            }
            return null;
        });
    }

    @Override
    public void deleteCourse(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return;
        }

        UUID courseUuid;
        try {
            courseUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM acd.courses WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, courseUuid);
                ps.setObject(2, context.getTenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Course mapRow(ResultSet rs) throws SQLException {
        Course c = new Course();
        c.setId(rs.getString("id"));
        c.setTenantId(rs.getString("tenant_id"));
        UUID dep = (UUID) rs.getObject("department_id");
        c.setDepartmentId(dep != null ? dep.toString() : null);
        c.setCourseCode(rs.getString("code"));
        c.setCourseName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setTotalCredits(rs.getDouble("credits"));
        c.setStatus(rs.getString("status"));
        c.setCurrentVersion(rs.getInt("row_version"));
        Timestamp cat = rs.getTimestamp("created_at");
        c.setCreatedAt(cat != null ? cat.getTime() : System.currentTimeMillis());
        Timestamp uat = rs.getTimestamp("updated_at");
        c.setUpdatedAt(uat != null ? uat.getTime() : System.currentTimeMillis());
        try {
            UUID prog = (UUID) rs.getObject("program_id");
            if (prog != null) {
                c.setProgramId(prog.toString());
            }
        } catch (SQLException ignored) {}
        return c;
    }
}
