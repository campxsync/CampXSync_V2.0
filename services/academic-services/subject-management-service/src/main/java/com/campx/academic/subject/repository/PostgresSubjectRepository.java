package com.campx.academic.subject.repository;

import com.campx.academic.subject.config.DatabaseConnectionManager;
import com.campx.academic.subject.exception.DuplicateSubjectCodeException;
import com.campx.academic.subject.exception.SubjectNotFoundException;
import com.campx.academic.subject.exception.SubjectUnauthorizedException;
import com.campx.academic.subject.exception.SubjectValidationException;
import com.campx.academic.subject.model.SubjectModels.Subject;
import com.campx.academic.subject.model.SubjectModels.SubjectStatus;
import com.campx.academic.subject.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code acd.subjects}.
 * Executes under caller identity via 'authenticated' role and RLS.
 */
public class PostgresSubjectRepository implements SubjectRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresSubjectRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresSubjectRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresSubjectRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Subject createSubject(UserSecurityContext context, Subject subject) {
        if (context == null || context.getTenantId() == null) {
            throw new SubjectUnauthorizedException("Missing required security context: tenantId");
        }
        if (subject == null) {
            throw new SubjectValidationException("Subject payload cannot be null");
        }
        if (subject.getSubjectCode() == null || subject.getSubjectCode().trim().isEmpty()) {
            throw new SubjectValidationException("Mandatory field 'subjectCode' is required");
        }
        if (subject.getName() == null || subject.getName().trim().isEmpty()) {
            throw new SubjectValidationException("Mandatory field 'name' is required");
        }

        String code = subject.getSubjectCode().trim().toUpperCase(Locale.ROOT);
        String name = subject.getName().trim();
        String dbType = toDbSubjectType(subject.getSubjectType(), subject.getClassification());
        String dbStatus = toDbStatus(subject.getStatus() != null ? subject.getStatus().name() : "DRAFT");
        double credits = subject.getCredits();
        if (credits < 0) {
            throw new SubjectValidationException("Credits cannot be negative");
        }

        UUID subjectUuid = parseOrGenerateUuid(subject.getId());
        String metadataJson = buildMetadataJson(subject);

        return context.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM acd.subjects WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL")) {
                checkPs.setObject(1, context.getTenantId());
                checkPs.setString(2, code);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new DuplicateSubjectCodeException(code, subject.getInstitutionId() != null ? subject.getInstitutionId() : "default");
                    }
                }
            }

            UUID depUuid = resolveDepartmentId(conn, context.getTenantId(), subject.getDepartmentId());

            String insertSql = "INSERT INTO acd.subjects " +
                    "(id, tenant_id, department_id, code, name, subject_type, credits, lecture_hours, status, metadata, created_at, updated_at, created_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), ?, 1) " +
                    "RETURNING id, created_at, updated_at, row_version, status, subject_type, department_id";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, subjectUuid);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, depUuid);
                ps.setString(4, code);
                ps.setString(5, name);
                ps.setString(6, dbType);
                ps.setDouble(7, credits);
                short lectureHours = (short) Math.max(0, Math.round(subject.getContactHours()));
                ps.setShort(8, lectureHours);
                ps.setString(9, dbStatus);
                ps.setString(10, metadataJson);
                ps.setObject(11, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        subject.setId(rs.getString("id"));
                        subject.setTenantId(context.getTenantId().toString());
                        subject.setSubjectCode(code);
                        subject.setName(name);
                        UUID dep = (UUID) rs.getObject("department_id");
                        if (dep != null) {
                            subject.setDepartmentId(dep.toString());
                        }
                        subject.setSubjectType(fromDbSubjectType(rs.getString("subject_type")));
                        subject.setStatus(fromDbStatus(rs.getString("status")));
                        subject.setCurrentVersion(rs.getInt("row_version"));
                        subject.setVersion(rs.getInt("row_version"));
                        Timestamp cat = rs.getTimestamp("created_at");
                        subject.setCreatedAt(cat != null ? cat.getTime() : System.currentTimeMillis());
                        Timestamp uat = rs.getTimestamp("updated_at");
                        subject.setUpdatedAt(uat != null ? uat.getTime() : System.currentTimeMillis());
                        logger.info("Persisted subject [{}] id={} in acd.subjects", code, subject.getId());
                        return subject;
                    }
                }
            }
            throw new RuntimeException("Failed to persist subject: insert returned no rows");
        });
    }

    @Override
    public Optional<Subject> findById(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        UUID subjectUuid;
        try {
            subjectUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, department_id, code, name, subject_type, credits, lecture_hours, status, metadata, created_at, updated_at, row_version " +
                    "FROM acd.subjects WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, subjectUuid);
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
    public Optional<Subject> findByCode(UserSecurityContext context, String code) {
        if (context == null || context.getTenantId() == null || code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, department_id, code, name, subject_type, credits, lecture_hours, status, metadata, created_at, updated_at, row_version " +
                    "FROM acd.subjects WHERE code = ? AND tenant_id = ? AND deleted_at IS NULL LIMIT 1";
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
    public List<Subject> listSubjects(UserSecurityContext context, String departmentId, String status,
                                      String codeContains, String nameContains, int offset, int limit) {
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
        String dbStatus = (status != null && !status.trim().isEmpty()) ? toDbStatus(status.trim()) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            List<Subject> list = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT id, tenant_id, department_id, code, name, subject_type, credits, lecture_hours, status, metadata, created_at, updated_at, row_version " +
                            "FROM acd.subjects WHERE tenant_id = ? AND deleted_at IS NULL ");
            List<Object> params = new ArrayList<>();
            params.add(context.getTenantId());

            if (finalDepUuid != null) {
                sql.append("AND department_id = ? ");
                params.add(finalDepUuid);
            }
            if (dbStatus != null) {
                sql.append("AND status = ? ");
                params.add(dbStatus);
            }
            if (codeContains != null && !codeContains.trim().isEmpty()) {
                sql.append("AND code ILIKE ? ");
                params.add("%" + codeContains.trim() + "%");
            }
            if (nameContains != null && !nameContains.trim().isEmpty()) {
                sql.append("AND name ILIKE ? ");
                params.add("%" + nameContains.trim() + "%");
            }
            sql.append("ORDER BY code ASC LIMIT ? OFFSET ?");
            params.add(limit > 0 ? limit : 50);
            params.add(Math.max(offset, 0));

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
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
    public void updateSubject(UserSecurityContext context, Subject subject) {
        if (context == null || context.getTenantId() == null || subject == null || subject.getId() == null) {
            return;
        }
        UUID subjectUuid = UUID.fromString(subject.getId().trim());
        context.executeInTransaction(connectionManager, conn -> {
            UUID depUuid = resolveDepartmentId(conn, context.getTenantId(), subject.getDepartmentId());
            String dbType = subject.getSubjectType() != null
                    ? toDbSubjectType(subject.getSubjectType(), subject.getClassification()) : null;
            String dbStatus = subject.getStatus() != null ? toDbStatus(subject.getStatus().name()) : null;
            String metadataJson = buildMetadataJson(subject);

            String sql = "UPDATE acd.subjects SET " +
                    "name = COALESCE(?, name), " +
                    "department_id = COALESCE(?, department_id), " +
                    "subject_type = COALESCE(?, subject_type), " +
                    "credits = CASE WHEN ? >= 0 THEN ? ELSE credits END, " +
                    "lecture_hours = CASE WHEN ? >= 0 THEN ? ELSE lecture_hours END, " +
                    "status = COALESCE(?, status), " +
                    "metadata = COALESCE(metadata, '{}'::jsonb) || ?::jsonb, " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, subject.getName());
                ps.setObject(2, depUuid);
                ps.setString(3, dbType);
                ps.setDouble(4, subject.getCredits());
                ps.setDouble(5, subject.getCredits());
                short hours = (short) Math.max(0, Math.round(subject.getContactHours()));
                ps.setShort(6, hours);
                ps.setShort(7, hours);
                ps.setString(8, dbStatus);
                ps.setString(9, metadataJson);
                ps.setObject(10, context.getUserId());
                ps.setObject(11, subjectUuid);
                ps.setObject(12, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new SubjectNotFoundException("Subject not found: " + subject.getId());
                }
            }
            return null;
        });
    }

    @Override
    public void updateStatus(UserSecurityContext context, String subjectId, String status) {
        if (context == null || context.getTenantId() == null || subjectId == null || status == null) {
            return;
        }
        UUID subjectUuid = UUID.fromString(subjectId.trim());
        String dbStatus = toDbStatus(status);
        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.subjects SET status = ?, " +
                    "metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{lifecycleStatus}', to_jsonb(?::text)), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbStatus);
                ps.setString(2, status.trim().toUpperCase(Locale.ROOT));
                ps.setObject(3, context.getUserId());
                ps.setObject(4, subjectUuid);
                ps.setObject(5, context.getTenantId());
                if (ps.executeUpdate() == 0) {
                    throw new SubjectNotFoundException("Subject not found: " + subjectId);
                }
            }
            return null;
        });
    }

    @Override
    public void deleteSubject(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null) {
            return;
        }
        UUID subjectUuid;
        try {
            subjectUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return;
        }
        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.subjects SET deleted_at = now(), updated_at = now(), row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, subjectUuid);
                ps.setObject(2, context.getTenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Subject mapRow(ResultSet rs) throws SQLException {
        Subject s = new Subject();
        s.setId(rs.getString("id"));
        s.setTenantId(rs.getString("tenant_id"));
        UUID dep = (UUID) rs.getObject("department_id");
        s.setDepartmentId(dep != null ? dep.toString() : null);
        s.setSubjectCode(rs.getString("code"));
        s.setName(rs.getString("name"));
        s.setSubjectType(fromDbSubjectType(rs.getString("subject_type")));
        s.setCredits(rs.getDouble("credits"));
        short lecture = rs.getShort("lecture_hours");
        if (!rs.wasNull()) {
            s.setContactHours(lecture);
        }
        s.setStatus(fromDbStatus(rs.getString("status")));
        s.setCurrentVersion(rs.getInt("row_version"));
        s.setVersion(rs.getInt("row_version"));
        Timestamp cat = rs.getTimestamp("created_at");
        s.setCreatedAt(cat != null ? cat.getTime() : System.currentTimeMillis());
        Timestamp uat = rs.getTimestamp("updated_at");
        s.setUpdatedAt(uat != null ? uat.getTime() : System.currentTimeMillis());

        String metadata = rs.getString("metadata");
        if (metadata != null && metadata.contains("\"elective\":true")) {
            s.setElective(true);
            s.setClassification("ELECTIVE");
        } else if (s.getSubjectType() != null && "PRACTICAL".equalsIgnoreCase(s.getSubjectType())) {
            s.setClassification("PRACTICAL");
        } else {
            s.setClassification("THEORY");
        }
        return s;
    }

    private static UUID parseOrGenerateUuid(String id) {
        if (id != null && !id.trim().isEmpty()) {
            try {
                return UUID.fromString(id.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        return UUID.randomUUID();
    }

    private static UUID resolveDepartmentId(Connection conn, UUID tenantId, String departmentId) throws SQLException {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return null;
        }
        try {
            UUID candidate = UUID.fromString(departmentId.trim());
            try (PreparedStatement depPs = conn.prepareStatement(
                    "SELECT id FROM core.departments WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                depPs.setObject(1, tenantId);
                depPs.setObject(2, candidate);
                try (ResultSet rs = depPs.executeQuery()) {
                    if (rs.next()) {
                        return candidate;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // non-UUID department codes are not persisted as FK
        }
        return null;
    }

    static String toDbSubjectType(String subjectType, String classification) {
        String raw = subjectType != null ? subjectType.trim().toUpperCase(Locale.ROOT) : "";
        if ("PRACTICAL".equals(raw) || "LAB".equals(raw)) return "LAB";
        if ("PROJECT".equals(raw)) return "PROJECT";
        if ("AUDIT".equals(raw)) return "AUDIT";
        if ("SEMINAR".equals(raw)) return "SEMINAR";
        if ("INTERNSHIP".equals(raw)) return "INTERNSHIP";
        if ("MOOC".equals(raw)) return "MOOC";
        if (classification != null && "PRACTICAL".equalsIgnoreCase(classification.trim())) return "LAB";
        return "THEORY";
    }

    static String fromDbSubjectType(String dbType) {
        if (dbType == null) return "CORE";
        switch (dbType.toUpperCase(Locale.ROOT)) {
            case "LAB": return "PRACTICAL";
            case "PROJECT": return "PROJECT";
            case "AUDIT": return "AUDIT";
            default: return "CORE";
        }
    }

    static String toDbStatus(String domainStatus) {
        if (domainStatus == null) return "DRAFT";
        switch (domainStatus.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE": return "ACTIVE";
            case "RETIRED":
            case "DEPRECATED":
            case "DEACTIVATED": return "RETIRED";
            default: return "DRAFT";
        }
    }

    static SubjectStatus fromDbStatus(String dbStatus) {
        if (dbStatus == null) return SubjectStatus.DRAFT;
        switch (dbStatus.toUpperCase(Locale.ROOT)) {
            case "ACTIVE": return SubjectStatus.ACTIVE;
            case "RETIRED": return SubjectStatus.RETIRED;
            default: return SubjectStatus.DRAFT;
        }
    }

    private static String buildMetadataJson(Subject subject) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"elective\":").append(subject.isElective());
        if (subject.getClassification() != null) {
            sb.append(",\"classification\":\"").append(escape(subject.getClassification())).append("\"");
        }
        if (subject.getShortName() != null) {
            sb.append(",\"shortName\":\"").append(escape(subject.getShortName())).append("\"");
        }
        if (subject.getDescription() != null) {
            sb.append(",\"description\":\"").append(escape(subject.getDescription())).append("\"");
        }
        if (subject.getAcademicYear() != null) {
            sb.append(",\"academicYear\":\"").append(escape(subject.getAcademicYear())).append("\"");
        }
        if (subject.getStatus() != null) {
            sb.append(",\"lifecycleStatus\":\"").append(subject.getStatus().name()).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
