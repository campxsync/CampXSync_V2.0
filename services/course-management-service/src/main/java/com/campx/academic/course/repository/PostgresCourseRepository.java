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

        String status = toDbStatus(course.getStatus());

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

            String level = (course.getLevel() != null && !course.getLevel().trim().isEmpty())
                    ? course.getLevel().trim() : "100";
            String metadataJson = buildMetadataJson(course);

            String insertSql = "INSERT INTO acd.courses " +
                    "(id, tenant_id, department_id, code, name, description, level, credits, status, metadata, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), 1) " +
                    "RETURNING id, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalCourseId);
                ps.setObject(2, tenantUuid);
                ps.setObject(3, depUuid);
                ps.setString(4, code);
                ps.setString(5, name);
                ps.setString(6, description);
                ps.setString(7, level);
                ps.setDouble(8, credits);
                ps.setString(9, finalStatus);
                ps.setString(10, metadataJson);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        course.setId(rs.getString("id"));
                        course.setTenantId(tenantUuid.toString());
                        if (depUuid != null) {
                            course.setDepartmentId(depUuid.toString());
                        }
                        course.setCourseCode(code);
                        course.setCourseName(name);
                        course.setLevel(level);
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
            String sql = "SELECT c.id, c.tenant_id, c.department_id, c.code, c.name, c.description, c.level, c.credits, c.status, c.metadata, c.created_at, c.updated_at, c.row_version, m.program_id " +
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
            String sql = "SELECT c.id, c.tenant_id, c.department_id, c.code, c.name, c.description, c.level, c.credits, c.status, c.metadata, c.created_at, c.updated_at, c.row_version, m.program_id " +
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
            String sql = "SELECT c.id, c.tenant_id, c.department_id, c.code, c.name, c.description, c.level, c.credits, c.status, c.metadata, c.created_at, c.updated_at, c.row_version, m.program_id " +
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
            UUID depUuid = null;
            boolean updateDept = false;
            if (course.getDepartmentId() != null && !course.getDepartmentId().trim().isEmpty()) {
                try {
                    UUID candidate = UUID.fromString(course.getDepartmentId().trim());
                    try (PreparedStatement depPs = conn.prepareStatement(
                            "SELECT id FROM core.departments WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                        depPs.setObject(1, context.getTenantId());
                        depPs.setObject(2, candidate);
                        try (ResultSet rs = depPs.executeQuery()) {
                            if (rs.next()) {
                                depUuid = candidate;
                                updateDept = true;
                            }
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }

            String domainStatus = (course.getStatus() != null && !course.getStatus().trim().isEmpty())
                    ? course.getStatus().trim().toUpperCase(Locale.ROOT)
                    : null;
            String dbStatus = domainStatus != null ? toDbStatus(domainStatus) : null;
            String metadataPatchJson = buildMetadataPatchJson(course);

            String sql = "UPDATE acd.courses SET " +
                    "name = COALESCE(?, name), " +
                    "description = COALESCE(?, description), " +
                    "credits = CASE WHEN ? > 0.0 THEN ? ELSE credits END, " +
                    "level = COALESCE(?, level), " +
                    "department_id = CASE WHEN ? THEN ? ELSE department_id END, " +
                    "status = COALESCE(?, status), " +
                    "metadata = COALESCE(metadata, '{}'::jsonb) || ?::jsonb, " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, course.getCourseName());
                ps.setString(2, course.getDescription());
                ps.setDouble(3, course.getTotalCredits());
                ps.setDouble(4, course.getTotalCredits());
                ps.setString(5, course.getLevel());
                ps.setBoolean(6, updateDept);
                ps.setObject(7, depUuid);
                ps.setString(8, dbStatus);
                ps.setString(9, metadataPatchJson);
                ps.setObject(10, courseUuid);
                ps.setObject(11, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new CourseNotFoundException("Course", course.getId());
                }
            }
            return null;
        });
    }

    @Override
    public void updateStatus(UserSecurityContext context, String courseId, String status) {
        if (context == null || context.getTenantId() == null || courseId == null || status == null || status.trim().isEmpty()) {
            return;
        }

        UUID courseUuid;
        try {
            courseUuid = UUID.fromString(courseId.trim());
        } catch (IllegalArgumentException e) {
            throw new CourseNotFoundException("Course", courseId);
        }

        String domainStatus = status.trim().toUpperCase(Locale.ROOT);
        String dbStatus = toDbStatus(domainStatus);

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.courses SET " +
                    "status = ?, " +
                    "metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{lifecycleStatus}', to_jsonb(?::text)), " +
                    "updated_at = now(), " +
                    "row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbStatus);
                ps.setString(2, domainStatus);
                ps.setObject(3, courseUuid);
                ps.setObject(4, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new CourseNotFoundException("Course", courseId);
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
            String sql = "UPDATE acd.courses SET deleted_at = now(), updated_at = now(), row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
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
        String lvl = rs.getString("level");
        c.setLevel(lvl != null ? lvl : "100");
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
        try {
            String meta = rs.getString("metadata");
            if (meta != null && !meta.trim().isEmpty()) {
                parseMetadata(c, meta);
            }
        } catch (SQLException ignored) {}
        return c;
    }

    private static String buildMetadataJson(Course course) {
        StringBuilder sb = new StringBuilder("{");
        String lifecycleStatus = course.getStatus() != null ? course.getStatus().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        sb.append("\"lifecycleStatus\":\"").append(escapeJson(lifecycleStatus)).append("\",");
        sb.append("\"courseType\":\"").append(escapeJson(course.getCourseType() != null ? course.getCourseType() : "THEORY")).append("\",");
        sb.append("\"courseCategory\":\"").append(escapeJson(course.getCourseCategory() != null ? course.getCourseCategory() : "CORE")).append("\",");
        sb.append("\"durationYears\":").append(course.getDurationYears()).append(",");
        sb.append("\"tags\":[");
        if (course.getTags() != null) {
            for (int i = 0; i < course.getTags().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(course.getTags().get(i))).append("\"");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildMetadataPatchJson(Course course) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (course.getStatus() != null && !course.getStatus().trim().isEmpty()) {
            sb.append("\"lifecycleStatus\":\"").append(escapeJson(course.getStatus().trim().toUpperCase(Locale.ROOT))).append("\"");
            first = false;
        }
        if (course.getCourseType() != null && !course.getCourseType().trim().isEmpty()) {
            if (!first) sb.append(",");
            sb.append("\"courseType\":\"").append(escapeJson(course.getCourseType().trim())).append("\"");
            first = false;
        }
        if (course.getCourseCategory() != null && !course.getCourseCategory().trim().isEmpty()) {
            if (!first) sb.append(",");
            sb.append("\"courseCategory\":\"").append(escapeJson(course.getCourseCategory().trim())).append("\"");
            first = false;
        }
        if (course.getDurationYears() > 0) {
            if (!first) sb.append(",");
            sb.append("\"durationYears\":").append(course.getDurationYears());
            first = false;
        }
        if (course.getTags() != null && !course.getTags().isEmpty()) {
            if (!first) sb.append(",");
            sb.append("\"tags\":[");
            for (int i = 0; i < course.getTags().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(course.getTags().get(i))).append("\"");
            }
            sb.append("]");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static void parseMetadata(Course course, String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            String ls = extractJsonString(json, "lifecycleStatus");
            if (ls != null && !ls.isEmpty()) {
                course.setStatus(ls);
            }
            String ct = extractJsonString(json, "courseType");
            if (ct != null && !ct.isEmpty()) {
                course.setCourseType(ct);
            }
            String cc = extractJsonString(json, "courseCategory");
            if (cc != null && !cc.isEmpty()) {
                course.setCourseCategory(cc);
            }
            Integer dy = extractJsonInt(json, "durationYears");
            if (dy != null && dy > 0) {
                course.setDurationYears(dy);
            }
            int tagsIdx = json.indexOf("\"tags\"");
            if (tagsIdx >= 0) {
                int openBracket = json.indexOf('[', tagsIdx);
                int closeBracket = json.indexOf(']', openBracket);
                if (openBracket >= 0 && closeBracket > openBracket) {
                    String arrayContent = json.substring(openBracket + 1, closeBracket);
                    String[] items = arrayContent.split(",");
                    List<String> tags = new ArrayList<>();
                    for (String item : items) {
                        String clean = item.trim().replace("\"", "");
                        if (!clean.isEmpty()) {
                            tags.add(clean);
                        }
                    }
                    if (!tags.isEmpty()) {
                        course.setTags(tags);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static Integer extractJsonInt(String json, String key) {
        if (json == null) return null;
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length() + 2);
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end > start) {
            try {
                return Integer.parseInt(json.substring(start, end));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length() + 2);
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('\"', colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf('\"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    public static String toDbStatus(String domainStatus) {
        if (domainStatus == null || domainStatus.trim().isEmpty()) {
            return "DRAFT";
        }
        String upper = domainStatus.trim().toUpperCase(Locale.ROOT);
        switch (upper) {
            case "ACTIVE":
            case "PUBLISHED":
                return "ACTIVE";
            case "RETIRED":
            case "ARCHIVED":
            case "DEACTIVATED":
                return "RETIRED";
            case "DRAFT":
            case "UNDER_REVIEW":
            case "APPROVED":
            case "SUSPENDED":
            default:
                return "DRAFT";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
