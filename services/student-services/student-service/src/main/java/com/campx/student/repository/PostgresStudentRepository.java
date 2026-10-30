package com.campx.student.repository;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.platform.audit.PostgresAuditRepository;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;
import com.campx.platform.outbox.TransactionalOutboxWriter;
import com.campx.student.config.DatabaseConnectionManager;
import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.*;
import com.campx.student.security.UserSecurityContext;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.students}.
 * Enforces transaction-scoped tenant claims and least-privilege role switching ('authenticated').
 * Shares database transaction atomically between entity persistence and transactional outbox.
 */
public class PostgresStudentRepository implements StudentRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStudentRepository.class);
    private static final String SERVICE_NAME = "student-service";
    private static final String AGGREGATE_TYPE = "STUDENT";
    private static final String SCHEMA_VERSION = "1.0.0";

    private final DatabaseConnectionManager connectionManager;
    private final AuditLogRepository auditRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ControlledIntelligenceAdapter intelligenceAdapter;

    public PostgresStudentRepository() {
        this(DatabaseConnectionManager.getInstance(),
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public PostgresStudentRepository(DatabaseConnectionManager connectionManager) {
        this(connectionManager,
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public PostgresStudentRepository(DatabaseConnectionManager connectionManager,
                                   AuditLogRepository auditRepository,
                                   TransactionalOutboxWriter outboxWriter,
                                   ControlledIntelligenceAdapter intelligenceAdapter) {
        if (connectionManager == null) throw new IllegalArgumentException("connectionManager cannot be null");
        this.connectionManager = connectionManager;
        this.auditRepository = auditRepository;
        this.outboxWriter = outboxWriter;
        this.intelligenceAdapter = intelligenceAdapter != null ? intelligenceAdapter : new ControlledIntelligenceAdapter();
    }

    public AuditLogRepository getAuditRepository() { return auditRepository; }
    public TransactionalOutboxWriter getOutboxWriter() { return outboxWriter; }
    public ControlledIntelligenceAdapter getIntelligenceAdapter() { return intelligenceAdapter; }

    @Override
    public StudentEntity save(UserSecurityContext context, StudentEntity student) {
        assertContext(context);
        if (student == null) throw new IllegalArgumentException("student cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            StudentEntity saved;
            String eventType;
            if (student.getId() == null) {
                saved = insertStudent(conn, context, student);
                eventType = "student.enrolled.v1";
            } else {
                saved = updateStudent(conn, context, student);
                eventType = "student.status_changed.v1";
            }

            String payloadJson = String.format(
                    "{\"studentId\":\"%s\",\"personId\":\"%s\",\"enrollmentNo\":\"%s\",\"programId\":\"%s\",\"collegeId\":\"%s\",\"status\":\"%s\"}",
                    saved.getId(), saved.getPersonId(), saved.getEnrollmentNo(),
                    saved.getProgramId() != null ? saved.getProgramId() : "",
                    saved.getCollegeId(), saved.getStatus()
            );

            // 1. Shared Services Transactional Outbox (sys.outbox_events) on the SAME database transaction
            if (outboxWriter != null) {
                try {
                    OutboxHeaders headers = OutboxHeaders.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : null)
                            .schemaVersion(SCHEMA_VERSION)
                            .build();

                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .tenantId(context.getTenantId())
                            .service(SERVICE_NAME)
                            .aggregateType(AGGREGATE_TYPE)
                            .aggregateId(saved.getId())
                            .eventType(eventType)
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for student: {}", e.getMessage());
                    throw new RuntimeException("Transactional outbox write failed: " + e.getMessage(), e);
                }
            }

            // 2. Shared Services Audit Log (audit.events) on the same transaction
            if (auditRepository != null) {
                try {
                    AuditRecord auditRecord = AuditRecord.builder()
                            .action(eventType.equals("student.enrolled.v1") ? "STUDENT_ENROLLED" : "STUDENT_UPDATED")
                            .tenantId(context.getTenantId())
                            .principalId(context.getUserId())
                            .principalType("USER")
                            .resourceType("STUDENT")
                            .resourceId(saved.getId())
                            .status("SUCCESS")
                            .severity("INFO")
                            .detailJson(payloadJson)
                            .build();
                    auditRepository.appendAuditEvent(conn, auditRecord);
                } catch (Exception e) {
                    logger.warn("Failed to append audit event for student: {}", e.getMessage());
                    throw new RuntimeException("Audit event persistence failed: " + e.getMessage(), e);
                }
            }

            // 3. Governed Intelligence Layer publication (v1.0.0)
            if (intelligenceAdapter != null) {
                try {
                    IntelligenceContextEvent intelEvent = IntelligenceContextEvent.builder()
                            .eventType(eventType)
                            .aggregateType(AGGREGATE_TYPE)
                            .aggregateId(saved.getId())
                            .tenantId(context.getTenantId())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : "SYSTEM")
                            .sourceService(SERVICE_NAME)
                            .payloadJson(payloadJson)
                            .build();
                    intelligenceAdapter.publish(intelEvent);
                } catch (Exception e) {
                    logger.warn("Failed to publish Intelligence event for student: {}", e.getMessage());
                }
            }

            return saved;
        });
    }

    @Override
    public Optional<StudentEntity> findById(UserSecurityContext context, UUID id) {
        assertContext(context);
        if (id == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, person_id, college_id, program_id, enrollment_no, " +
                    "admission_no, admission_date, admission_academic_year_id, status, " +
                    "created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM stu.students WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<StudentEntity> findByEnrollmentNo(UserSecurityContext context, String enrollmentNo) {
        assertContext(context);
        if (enrollmentNo == null || enrollmentNo.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, person_id, college_id, program_id, enrollment_no, " +
                    "admission_no, admission_date, admission_academic_year_id, status, " +
                    "created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM stu.students WHERE tenant_id = ? AND enrollment_no = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, enrollmentNo.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public boolean existsByEnrollmentNo(UserSecurityContext context, String enrollmentNo) {
        assertContext(context);
        if (enrollmentNo == null || enrollmentNo.trim().isEmpty()) return false;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT 1 FROM stu.students WHERE tenant_id = ? AND enrollment_no = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, enrollmentNo.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public Optional<StudentEntity> findByPersonId(UserSecurityContext context, UUID personId) {
        assertContext(context);
        if (personId == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, person_id, college_id, program_id, enrollment_no, " +
                    "admission_no, admission_date, admission_academic_year_id, status, " +
                    "created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM stu.students WHERE tenant_id = ? AND person_id = ? AND deleted_at IS NULL LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, personId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<StudentEntity> findByIamUserId(UserSecurityContext context, UUID iamUserId) {
        assertContext(context);
        if (iamUserId == null) return Optional.empty();

        // Looks up student linked via iam.user_profiles.person_id -> stu.students.person_id
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT s.id, s.tenant_id, s.person_id, s.college_id, s.program_id, s.enrollment_no, " +
                    "s.admission_no, s.admission_date, s.admission_academic_year_id, s.status, " +
                    "s.created_at, s.updated_at, s.created_by, s.updated_by, s.row_version, s.deleted_at " +
                    "FROM stu.students s " +
                    "JOIN iam.user_profiles u ON u.person_id = s.person_id AND u.tenant_id = s.tenant_id " +
                    "WHERE s.tenant_id = ? AND u.id = ? AND s.deleted_at IS NULL LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, iamUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public long countByTenant(UserSecurityContext context) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT count(*) FROM stu.students WHERE tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            return 0L;
        });
    }

    @Override
    public CursorPagedResult<StudentRosterItem> findRoster(UserSecurityContext context, StudentRosterQuery query) {
        assertContext(context);

        return context.executeInTransaction(connectionManager, conn -> {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append("s.id, s.enrollment_no, s.admission_no, s.status, s.college_id, s.program_id, s.created_at, ")
                    .append("p.display_name, p.first_name, p.last_name, p.primary_email, ")
                    .append("pr.name AS program_name, pr.code AS program_code, ")
                    .append("b.id AS batch_id, b.name AS batch_name, b.code AS batch_code, ")
                    .append("bs.roll_no ")
                    .append("FROM stu.students s ")
                    .append("LEFT JOIN core.persons p ON p.id = s.person_id AND p.tenant_id = s.tenant_id AND p.deleted_at IS NULL ")
                    .append("LEFT JOIN acd.programs pr ON pr.id = s.program_id AND pr.tenant_id = s.tenant_id AND pr.deleted_at IS NULL ")
                    .append("LEFT JOIN acd.batch_students bs ON bs.student_id = s.id AND bs.tenant_id = s.tenant_id AND bs.status = 'ACTIVE' AND bs.deleted_at IS NULL ")
                    .append("LEFT JOIN acd.batches b ON b.id = bs.batch_id AND b.tenant_id = s.tenant_id AND b.deleted_at IS NULL ")
                    .append("WHERE s.tenant_id = ? AND s.deleted_at IS NULL ");

            List<Object> params = new ArrayList<>();
            params.add(context.getTenantId());

            if (query != null) {
                if (query.getCursor() != null && !query.getCursor().trim().isEmpty()) {
                    String decodedCursor = decodeCursor(query.getCursor());
                    sql.append("AND s.enrollment_no > ? ");
                    params.add(decodedCursor);
                }
                if (query.getCollegeId() != null) {
                    sql.append("AND s.college_id = ? ");
                    params.add(query.getCollegeId());
                }
                if (query.getProgramId() != null) {
                    sql.append("AND s.program_id = ? ");
                    params.add(query.getProgramId());
                }
                if (query.getBatchId() != null) {
                    sql.append("AND bs.batch_id = ? ");
                    params.add(query.getBatchId());
                }
                if (query.getStatus() != null) {
                    sql.append("AND s.status = ? ");
                    params.add(query.getStatus().name());
                }
                if (query.getSearch() != null && !query.getSearch().trim().isEmpty()) {
                    String searchPattern = "%" + query.getSearch().trim().toLowerCase() + "%";
                    sql.append("AND (LOWER(s.enrollment_no) LIKE ? OR LOWER(p.display_name) LIKE ? OR LOWER(p.primary_email) LIKE ?) ");
                    params.add(searchPattern);
                    params.add(searchPattern);
                    params.add(searchPattern);
                }
            }

            int limit = query != null ? query.getLimit() : 50;
            sql.append("ORDER BY s.enrollment_no ASC LIMIT ?");
            params.add(limit + 1); // fetch limit + 1 to determine hasMore

            List<StudentRosterItem> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        StudentRosterItem item = new StudentRosterItem();
                        item.setId((UUID) rs.getObject("id"));
                        item.setEnrollmentNo(rs.getString("enrollment_no"));
                        item.setAdmissionNo(rs.getString("admission_no"));
                        String stStr = rs.getString("status");
                        if (stStr != null) {
                            try { item.setStatus(StudentStatus.valueOf(stStr)); } catch (Exception ignored) {}
                        }
                        item.setCollegeId((UUID) rs.getObject("college_id"));
                        item.setProgramId((UUID) rs.getObject("program_id"));
                        item.setProgramName(rs.getString("program_name"));
                        item.setProgramCode(rs.getString("program_code"));
                        item.setBatchId((UUID) rs.getObject("batch_id"));
                        item.setBatchName(rs.getString("batch_name"));
                        item.setBatchCode(rs.getString("batch_code"));
                        item.setRollNo(rs.getString("roll_no"));
                        item.setDisplayName(rs.getString("display_name"));
                        item.setFirstName(rs.getString("first_name"));
                        item.setLastName(rs.getString("last_name"));
                        item.setPrimaryEmail(rs.getString("primary_email"));

                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) {
                            item.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
                        }
                        items.add(item);
                    }
                }
            }

            boolean hasMore = items.size() > limit;
            List<StudentRosterItem> pagedItems = hasMore ? items.subList(0, limit) : items;

            String nextCursor = null;
            if (hasMore && !pagedItems.isEmpty()) {
                StudentRosterItem last = pagedItems.get(pagedItems.size() - 1);
                nextCursor = encodeCursor(last.getEnrollmentNo());
            }

            return new CursorPagedResult<>(pagedItems, nextCursor, hasMore, (long) pagedItems.size());
        });
    }

    @Override
    public void delete(UserSecurityContext context, UUID id) {
        assertContext(context);
        if (id == null) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE stu.students SET deleted_at = now(), status = 'WITHDRAWN', updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, id);
                ps.setObject(3, context.getTenantId());
                ps.executeUpdate();
            }

            // Shared Services Transactional Outbox write within same transaction
            if (outboxWriter != null) {
                try {
                    OutboxHeaders headers = OutboxHeaders.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : null)
                            .schemaVersion(SCHEMA_VERSION)
                            .build();

                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .tenantId(context.getTenantId())
                            .service(SERVICE_NAME)
                            .aggregateType(AGGREGATE_TYPE)
                            .aggregateId(id)
                            .eventType("student.deactivated.v1")
                            .headers(headers)
                            .payload(String.format("{\"studentId\":\"%s\",\"status\":\"WITHDRAWN\"}", id))
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for student deactivation: {}", e.getMessage());
                    throw new RuntimeException("Transactional outbox write failed: " + e.getMessage(), e);
                }
            }

            // Shared Services Audit Log (audit.events) within same transaction
            if (auditRepository != null) {
                try {
                    AuditRecord auditRecord = AuditRecord.builder()
                            .action("STUDENT_DEACTIVATED")
                            .tenantId(context.getTenantId())
                            .principalId(context.getUserId())
                            .principalType("USER")
                            .resourceType("STUDENT")
                            .resourceId(id)
                            .status("SUCCESS")
                            .severity("INFO")
                            .detailJson(String.format("{\"studentId\":\"%s\",\"status\":\"WITHDRAWN\"}", id))
                            .build();
                    auditRepository.appendAuditEvent(conn, auditRecord);
                } catch (Exception e) {
                    logger.warn("Failed to append audit event for student deactivation: {}", e.getMessage());
                    throw new RuntimeException("Audit event persistence failed: " + e.getMessage(), e);
                }
            }

            return null;
        });
    }

    private StudentEntity insertStudent(Connection conn, UserSecurityContext context, StudentEntity student) throws SQLException {
        UUID id = UUID.randomUUID();
        student.setId(id);
        student.setTenantId(context.getTenantId());

        String sql = "INSERT INTO stu.students (id, tenant_id, person_id, college_id, program_id, enrollment_no, " +
                "admission_no, admission_date, admission_academic_year_id, status, created_by, updated_by, row_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) " +
                "RETURNING created_at, updated_at, row_version";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, context.getTenantId());
            ps.setObject(3, student.getPersonId());
            ps.setObject(4, student.getCollegeId());
            ps.setObject(5, student.getProgramId());
            ps.setString(6, student.getEnrollmentNo());
            ps.setString(7, student.getAdmissionNo());
            if (student.getAdmissionDate() != null) {
                ps.setDate(8, java.sql.Date.valueOf(student.getAdmissionDate()));
            } else {
                ps.setNull(8, Types.DATE);
            }
            ps.setObject(9, student.getAdmissionAcademicYearId());
            ps.setString(10, student.getStatus() != null ? student.getStatus().name() : StudentStatus.ACTIVE.name());
            ps.setObject(11, context.getUserId());
            ps.setObject(12, context.getUserId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp cat = rs.getTimestamp("created_at");
                    Timestamp uat = rs.getTimestamp("updated_at");
                    student.setCreatedAt(cat != null ? cat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    student.setUpdatedAt(uat != null ? uat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    student.setRowVersion(rs.getInt("row_version"));
                }
            }
        }
        return student;
    }

    private StudentEntity updateStudent(Connection conn, UserSecurityContext context, StudentEntity student) throws SQLException {
        String sql = "UPDATE stu.students SET program_id = ?, admission_no = ?, admission_date = ?, " +
                "admission_academic_year_id = ?, status = ?, updated_by = ?, updated_at = now(), " +
                "row_version = row_version + 1 " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                "RETURNING updated_at, row_version";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, student.getProgramId());
            ps.setString(2, student.getAdmissionNo());
            if (student.getAdmissionDate() != null) {
                ps.setDate(3, java.sql.Date.valueOf(student.getAdmissionDate()));
            } else {
                ps.setNull(3, Types.DATE);
            }
            ps.setObject(4, student.getAdmissionAcademicYearId());
            ps.setString(5, student.getStatus() != null ? student.getStatus().name() : StudentStatus.ACTIVE.name());
            ps.setObject(6, context.getUserId());
            ps.setObject(7, student.getId());
            ps.setObject(8, context.getTenantId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp uat = rs.getTimestamp("updated_at");
                    student.setUpdatedAt(uat != null ? uat.toInstant().atOffset(ZoneOffset.UTC) : OffsetDateTime.now());
                    student.setRowVersion(rs.getInt("row_version"));
                }
            }
        }
        return student;
    }

    private StudentEntity mapResultSet(ResultSet rs) throws SQLException {
        StudentEntity s = new StudentEntity();
        s.setId((UUID) rs.getObject("id"));
        s.setTenantId((UUID) rs.getObject("tenant_id"));
        s.setPersonId((UUID) rs.getObject("person_id"));
        s.setCollegeId((UUID) rs.getObject("college_id"));
        s.setProgramId((UUID) rs.getObject("program_id"));
        s.setEnrollmentNo(rs.getString("enrollment_no"));
        s.setAdmissionNo(rs.getString("admission_no"));

        java.sql.Date adDate = rs.getDate("admission_date");
        if (adDate != null) s.setAdmissionDate(adDate.toLocalDate());

        s.setAdmissionAcademicYearId((UUID) rs.getObject("admission_academic_year_id"));

        String stStr = rs.getString("status");
        if (stStr != null) {
            try { s.setStatus(StudentStatus.valueOf(stStr)); } catch (IllegalArgumentException ignored) {}
        }

        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) s.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) s.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));

        s.setRowVersion(rs.getInt("row_version"));
        return s;
    }

    private void assertContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new StudentSecurityViolationException("Authenticated tenant context is strictly required");
        }
    }

    private String encodeCursor(String val) {
        if (val == null) return null;
        return Base64.getUrlEncoder().encodeToString(val.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeCursor(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return cursor.trim();
        }
    }
}
