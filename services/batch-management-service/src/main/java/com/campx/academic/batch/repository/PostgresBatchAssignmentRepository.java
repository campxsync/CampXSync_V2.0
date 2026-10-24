package com.campx.academic.batch.repository;

import com.campx.academic.batch.config.DatabaseConnectionManager;
import com.campx.academic.batch.exception.BatchException;
import com.campx.academic.batch.exception.BatchForbiddenException;
import com.campx.academic.batch.exception.BatchNotFoundException;
import com.campx.academic.batch.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative PostgreSQL JDBC repository for ACD-04 Batch Management operations.
 * Enforces transaction-scoped tenant claims and least-privilege role switching ('authenticated')
 * against {@code acd.batch_students}, {@code acd.batches}, {@code stu.students}, and {@code audit.events}.
 */
public class PostgresBatchAssignmentRepository implements BatchAssignmentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresBatchAssignmentRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresBatchAssignmentRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresBatchAssignmentRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    @Override
    public BatchAssignmentEntity assignStudent(UserSecurityContext ctx, BatchAssignmentEntity assignment) {
        assertContext(ctx);
        if (assignment == null) throw new IllegalArgumentException("assignment cannot be null");
        if (assignment.getBatchId() == null) throw new IllegalArgumentException("batchId cannot be null");
        if (assignment.getStudentId() == null) throw new IllegalArgumentException("studentId cannot be null");

        return ctx.executeInTransaction(connectionManager, conn -> {
            // 1. Verify batch exists in tenant
            String batchSql = "SELECT id, college_id, name, code FROM acd.batches WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL LIMIT 1";
            UUID collegeId = null;
            String batchName = null;
            String batchCode = null;

            try (PreparedStatement ps = conn.prepareStatement(batchSql)) {
                ps.setObject(1, assignment.getBatchId());
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BatchNotFoundException("Batch not found in tenant workspace: " + assignment.getBatchId());
                    }
                    collegeId = rs.getObject("college_id", UUID.class);
                    batchName = rs.getString("name");
                    batchCode = rs.getString("code");
                }
            }

            // 2. Verify student exists in tenant
            String studentSql = "SELECT s.id, s.college_id, s.enrollment_no, p.display_name " +
                    "FROM stu.students s " +
                    "LEFT JOIN core.persons p ON p.id = s.person_id " +
                    "WHERE s.id = ? AND s.tenant_id = ? AND s.deleted_at IS NULL LIMIT 1";
            String studentName = null;
            String enrollmentNo = null;

            try (PreparedStatement ps = conn.prepareStatement(studentSql)) {
                ps.setObject(1, assignment.getStudentId());
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BatchNotFoundException("Student not found in tenant workspace: " + assignment.getStudentId());
                    }
                    UUID studentCollegeId = rs.getObject("college_id", UUID.class);
                    if (studentCollegeId != null) {
                        collegeId = studentCollegeId;
                    }
                    enrollmentNo = rs.getString("enrollment_no");
                    studentName = rs.getString("display_name");
                    if (studentName == null || studentName.trim().isEmpty()) {
                        studentName = enrollmentNo;
                    }
                }
            }

            // 3. Upsert into acd.batch_students
            UUID assignmentId = assignment.getId() != null ? assignment.getId() : UUID.randomUUID();
            String effectiveRollNo = assignment.getRollNo() != null && !assignment.getRollNo().trim().isEmpty()
                    ? assignment.getRollNo().trim()
                    : enrollmentNo;

            String upsertSql = "INSERT INTO acd.batch_students (" +
                    "id, tenant_id, batch_id, student_id, roll_no, joined_on, status, row_version, created_at, updated_at" +
                    ") VALUES (?, ?, ?, ?, ?, COALESCE(?, CURRENT_DATE), 'ACTIVE', 1, now(), now()) " +
                    "ON CONFLICT (tenant_id, batch_id, student_id) WHERE deleted_at IS NULL " +
                    "DO UPDATE SET " +
                    "status = 'ACTIVE', " +
                    "roll_no = COALESCE(EXCLUDED.roll_no, acd.batch_students.roll_no), " +
                    "row_version = acd.batch_students.row_version + 1, " +
                    "updated_at = now() " +
                    "RETURNING id, tenant_id, batch_id, student_id, roll_no, joined_on, status, row_version, created_at, updated_at";

            BatchAssignmentEntity persisted = new BatchAssignmentEntity();
            try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                ps.setObject(1, assignmentId);
                ps.setObject(2, ctx.getTenantId());
                ps.setObject(3, assignment.getBatchId());
                ps.setObject(4, assignment.getStudentId());
                ps.setString(5, effectiveRollNo);
                if (assignment.getJoinedOn() != null) {
                    ps.setDate(6, java.sql.Date.valueOf(assignment.getJoinedOn()));
                } else {
                    ps.setNull(6, Types.DATE);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        persisted.setId(rs.getObject("id", UUID.class));
                        persisted.setTenantId(rs.getObject("tenant_id", UUID.class));
                        persisted.setBatchId(rs.getObject("batch_id", UUID.class));
                        persisted.setStudentId(rs.getObject("student_id", UUID.class));
                        persisted.setRollNo(rs.getString("roll_no"));
                        java.sql.Date joined = rs.getDate("joined_on");
                        if (joined != null) {
                            persisted.setJoinedOn(joined.toLocalDate());
                        }
                        persisted.setStatus(rs.getString("status"));
                        persisted.setRowVersion(rs.getInt("row_version"));
                        Timestamp createdTs = rs.getTimestamp("created_at");
                        if (createdTs != null) {
                            persisted.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
                        }
                        Timestamp updatedTs = rs.getTimestamp("updated_at");
                        if (updatedTs != null) {
                            persisted.setUpdatedAt(updatedTs.toInstant().atOffset(ZoneOffset.UTC));
                        }
                    }
                }
            }

            // 4. Record Audit Event in audit.events
            String detailJson = String.format("{\"enrollmentNo\":\"%s\",\"studentName\":\"%s\",\"batchId\":\"%s\",\"batchName\":\"%s\",\"batchCode\":\"%s\",\"rollNo\":\"%s\"}",
                    escapeJson(enrollmentNo), escapeJson(studentName), assignment.getBatchId(), escapeJson(batchName), escapeJson(batchCode), escapeJson(effectiveRollNo));

            String auditSql = "INSERT INTO audit.events (" +
                    "tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail" +
                    ") VALUES (?, ?, now(), 'STUDENT_BATCH_ASSIGNED', 'COLLEGE_ADMIN', ?, 'STUDENT', ?, 'SUCCESS', 'INFO', ?)";

            try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
                ps.setObject(1, ctx.getTenantId());
                ps.setObject(2, collegeId);
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, assignment.getStudentId());
                ps.setString(5, detailJson);
                ps.executeUpdate();
            }

            // 5. Enrich returned metadata
            persisted.setStudentName(studentName);
            persisted.setEnrollmentNo(enrollmentNo);
            persisted.setBatchName(batchName);
            persisted.setBatchCode(batchCode);
            persisted.setCollegeId(collegeId);

            logger.info("Successfully assigned student {} to batch {} in tenant {}: rollNo={}",
                    assignment.getStudentId(), assignment.getBatchId(), ctx.getTenantId(), effectiveRollNo);

            return persisted;
        });
    }

    @Override
    public Optional<BatchAssignmentEntity> findAssignment(UserSecurityContext ctx, UUID batchId, UUID studentId) {
        assertContext(ctx);
        String sql = "SELECT bs.id, bs.tenant_id, bs.batch_id, bs.student_id, bs.roll_no, bs.joined_on, bs.status, bs.row_version, bs.created_at, bs.updated_at, " +
                "b.name AS batch_name, b.code AS batch_code, b.college_id, s.enrollment_no, p.display_name AS student_name " +
                "FROM acd.batch_students bs " +
                "LEFT JOIN acd.batches b ON b.id = bs.batch_id AND b.tenant_id = bs.tenant_id " +
                "LEFT JOIN stu.students s ON s.id = bs.student_id AND s.tenant_id = bs.tenant_id " +
                "LEFT JOIN core.persons p ON p.id = s.person_id " +
                "WHERE bs.tenant_id = ? AND bs.batch_id = ? AND bs.student_id = ? AND bs.deleted_at IS NULL LIMIT 1";

        return ctx.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, ctx.getTenantId());
                ps.setObject(2, batchId);
                ps.setObject(3, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BatchAssignmentEntity ent = new BatchAssignmentEntity();
                        ent.setId(rs.getObject("id", UUID.class));
                        ent.setTenantId(rs.getObject("tenant_id", UUID.class));
                        ent.setBatchId(rs.getObject("batch_id", UUID.class));
                        ent.setStudentId(rs.getObject("student_id", UUID.class));
                        ent.setRollNo(rs.getString("roll_no"));
                        java.sql.Date joined = rs.getDate("joined_on");
                        if (joined != null) ent.setJoinedOn(joined.toLocalDate());
                        ent.setStatus(rs.getString("status"));
                        ent.setRowVersion(rs.getInt("row_version"));
                        ent.setBatchName(rs.getString("batch_name"));
                        ent.setBatchCode(rs.getString("batch_code"));
                        ent.setCollegeId(rs.getObject("college_id", UUID.class));
                        ent.setEnrollmentNo(rs.getString("enrollment_no"));
                        ent.setStudentName(rs.getString("student_name"));
                        return Optional.of(ent);
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<BatchAssignmentEntity> listAssignmentsByBatch(UserSecurityContext ctx, UUID batchId) {
        assertContext(ctx);
        String sql = "SELECT bs.id, bs.tenant_id, bs.batch_id, bs.student_id, bs.roll_no, bs.joined_on, bs.status, bs.row_version, bs.created_at, bs.updated_at, " +
                "s.enrollment_no, p.display_name AS student_name " +
                "FROM acd.batch_students bs " +
                "LEFT JOIN stu.students s ON s.id = bs.student_id AND s.tenant_id = bs.tenant_id " +
                "LEFT JOIN core.persons p ON p.id = s.person_id " +
                "WHERE bs.tenant_id = ? AND bs.batch_id = ? AND bs.status = 'ACTIVE' AND bs.deleted_at IS NULL " +
                "ORDER BY bs.roll_no ASC";

        return ctx.executeInTransaction(connectionManager, conn -> {
            List<BatchAssignmentEntity> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, ctx.getTenantId());
                ps.setObject(2, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BatchAssignmentEntity ent = new BatchAssignmentEntity();
                        ent.setId(rs.getObject("id", UUID.class));
                        ent.setTenantId(rs.getObject("tenant_id", UUID.class));
                        ent.setBatchId(rs.getObject("batch_id", UUID.class));
                        ent.setStudentId(rs.getObject("student_id", UUID.class));
                        ent.setRollNo(rs.getString("roll_no"));
                        java.sql.Date joined = rs.getDate("joined_on");
                        if (joined != null) ent.setJoinedOn(joined.toLocalDate());
                        ent.setStatus(rs.getString("status"));
                        ent.setRowVersion(rs.getInt("row_version"));
                        ent.setEnrollmentNo(rs.getString("enrollment_no"));
                        ent.setStudentName(rs.getString("student_name"));
                        list.add(ent);
                    }
                }
            }
            return list;
        });
    }

    @Override
    public BatchAssignmentEntity removeAssignment(UserSecurityContext ctx, UUID batchId, UUID studentId, String reason) {
        assertContext(ctx);
        String sql = "UPDATE acd.batch_students " +
                "SET status = 'ENDED', updated_at = now(), deleted_at = now() " +
                "WHERE tenant_id = ? AND batch_id = ? AND student_id = ? AND deleted_at IS NULL " +
                "RETURNING id, tenant_id, batch_id, student_id, roll_no, joined_on, status, row_version, created_at, updated_at";

        return ctx.executeInTransaction(connectionManager, conn -> {
            BatchAssignmentEntity removed = new BatchAssignmentEntity();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, ctx.getTenantId());
                ps.setObject(2, batchId);
                ps.setObject(3, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new BatchNotFoundException("Active assignment not found for student " + studentId + " in batch " + batchId);
                    }
                    removed.setId(rs.getObject("id", UUID.class));
                    removed.setTenantId(rs.getObject("tenant_id", UUID.class));
                    removed.setBatchId(rs.getObject("batch_id", UUID.class));
                    removed.setStudentId(rs.getObject("student_id", UUID.class));
                    removed.setRollNo(rs.getString("roll_no"));
                    removed.setStatus(rs.getString("status"));
                }
            }

            // Log removal in audit.events
            String auditSql = "INSERT INTO audit.events (" +
                    "tenant_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail" +
                    ") VALUES (?, now(), 'STUDENT_BATCH_REMOVED', 'COLLEGE_ADMIN', ?, 'STUDENT', ?, 'SUCCESS', 'INFO', ?)";
            try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
                ps.setObject(1, ctx.getTenantId());
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, studentId);
                ps.setString(4, reason != null ? reason : "Student withdrawn from batch");
                ps.executeUpdate();
            }

            return removed;
        });
    }

    @Override
    public boolean verifyBatchExists(UserSecurityContext ctx, UUID batchId) {
        assertContext(ctx);
        String sql = "SELECT 1 FROM acd.batches WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL LIMIT 1";
        return ctx.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, batchId);
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public boolean verifyStudentExists(UserSecurityContext ctx, UUID studentId) {
        assertContext(ctx);
        String sql = "SELECT 1 FROM stu.students WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL LIMIT 1";
        return ctx.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, studentId);
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private void assertContext(UserSecurityContext ctx) {
        if (ctx == null) {
            throw new BatchForbiddenException("Missing security context for batch assignment operation");
        }
        if (ctx.getTenantId() == null) {
            throw new BatchForbiddenException("Missing tenant context for batch assignment operation");
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
