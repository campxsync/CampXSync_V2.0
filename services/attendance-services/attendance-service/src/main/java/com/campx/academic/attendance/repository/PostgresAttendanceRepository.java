package com.campx.academic.attendance.repository;

import com.campx.academic.attendance.config.DatabaseConnectionManager;
import com.campx.academic.attendance.exception.AttendanceNotFoundException;
import com.campx.academic.attendance.exception.DuplicateAttendanceRecordException;
import com.campx.academic.attendance.exception.DuplicateSessionException;
import com.campx.academic.attendance.model.AttendanceCorrectionEntity;
import com.campx.academic.attendance.model.AttendanceRecordEntity;
import com.campx.academic.attendance.model.AttendanceSessionEntity;
import com.campx.academic.attendance.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.audit.AuditLogRepository;
import com.campx.platform.audit.AuditRecord;
import com.campx.platform.audit.PostgresAuditRepository;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;
import com.campx.platform.outbox.TransactionalOutboxWriter;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * PostgreSQL JDBC repository for Attendance aggregate tables:
 * {@code acd.attendance_sessions}, {@code acd.attendance_records}, {@code acd.attendance_corrections}.
 * Enforces transaction-scoped tenant claims and least-privilege role switching ('authenticated').
 * Coordinates atomic entity writes with {@code sys.outbox_events} and {@code audit.events}.
 */
public class PostgresAttendanceRepository implements AttendanceRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAttendanceRepository.class);
    private static final String SERVICE_NAME = "attendance-service";
    private static final String AGGREGATE_TYPE = "ATTENDANCE_SESSION";
    private static final String SCHEMA_VERSION = "1.0.0";

    private final DatabaseConnectionManager connectionManager;
    private final AuditLogRepository auditRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ControlledIntelligenceAdapter intelligenceAdapter;

    public PostgresAttendanceRepository() {
        this(DatabaseConnectionManager.getInstance(),
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public PostgresAttendanceRepository(DatabaseConnectionManager connectionManager,
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
    public AttendanceSessionEntity createSession(UserSecurityContext context, AttendanceSessionEntity session) {
        assertContext(context);
        if (session == null) throw new IllegalArgumentException("session cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            AttendanceSessionEntity saved;
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            session.setTenantId(context.getTenantId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            session.setCreatedAt(now);
            session.setUpdatedAt(now);

            String sql = "INSERT INTO acd.attendance_sessions (" +
                    "id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, created_at, updated_at, created_by, row_version" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, session.getId());
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, session.getSectionId());
                ps.setObject(4, session.getTimetableEntryId());
                ps.setObject(5, session.getEmployeeId());
                ps.setDate(6, java.sql.Date.valueOf(session.getSessionDate()));
                ps.setTime(7, session.getStartTime() != null ? Time.valueOf(session.getStartTime()) : Time.valueOf("10:00:00"));
                ps.setTime(8, session.getEndTime() != null ? Time.valueOf(session.getEndTime()) : Time.valueOf("11:00:00"));
                ps.setString(9, session.getStatus() != null ? session.getStatus() : "SCHEDULED");
                ps.setTimestamp(10, Timestamp.from(now.toInstant()));
                ps.setTimestamp(11, Timestamp.from(now.toInstant()));
                ps.setObject(12, context.getUserId());
                ps.setInt(13, session.getRowVersion());

                try {
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if ("23505".equals(e.getSQLState())) {
                        throw new DuplicateSessionException("Attendance session already exists for this section, date, and start time.");
                    }
                    throw e;
                }
            }
            saved = session;

            // 1. Transactional Outbox (sys.outbox_events) on the same transaction
            if (outboxWriter != null) {
                try {
                    String payloadJson = String.format(
                            "{\"sessionId\":\"%s\",\"tenantId\":\"%s\",\"sectionId\":\"%s\",\"timetableEntryId\":\"%s\",\"sessionDate\":\"%s\",\"status\":\"%s\"}",
                            saved.getId(), context.getTenantId(), saved.getSectionId(),
                            saved.getTimetableEntryId() != null ? saved.getTimetableEntryId().toString() : "",
                            saved.getSessionDate(), saved.getStatus()
                    );

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
                            .eventType("attendance.session_opened.v1")
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for session open: {}", e.getMessage());
                    throw new RuntimeException("Transactional outbox write failed: " + e.getMessage(), e);
                }
            }

            // 2. Audit Trail
            if (auditRepository != null) {
                try {
                    AuditRecord auditRecord = AuditRecord.builder()
                            .action("ATTENDANCE_SESSION_OPENED")
                            .tenantId(context.getTenantId())
                            .principalId(context.getUserId())
                            .principalType("USER")
                            .resourceType(AGGREGATE_TYPE)
                            .resourceId(saved.getId())
                            .status("SUCCESS")
                            .severity("INFO")
                            .detailJson(String.format("{\"sectionId\":\"%s\",\"sessionDate\":\"%s\"}", saved.getSectionId(), saved.getSessionDate()))
                            .build();
                    auditRepository.appendAuditEvent(conn, auditRecord);
                } catch (Exception e) {
                    logger.warn("Failed to append audit event for session open: {}", e.getMessage());
                }
            }

            return saved;
        });
    }

    @Override
    public Optional<AttendanceSessionEntity> findSessionById(UserSecurityContext context, UUID sessionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, locked_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_sessions WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sessionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapSession(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<AttendanceSessionEntity> findSessionByTimetableEntryAndDate(UserSecurityContext context, UUID timetableEntryId, LocalDate sessionDate) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, locked_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_sessions WHERE timetable_entry_id = ? AND session_date = ? AND tenant_id = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, timetableEntryId);
                ps.setDate(2, java.sql.Date.valueOf(sessionDate));
                ps.setObject(3, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapSession(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<AttendanceSessionEntity> findSessionsBySectionAndDate(UserSecurityContext context, UUID sectionId, LocalDate sessionDate) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<AttendanceSessionEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, locked_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_sessions WHERE section_id = ? AND session_date = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY start_time";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sectionId);
                ps.setDate(2, java.sql.Date.valueOf(sessionDate));
                ps.setObject(3, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapSession(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<AttendanceSessionEntity> listSessions(UserSecurityContext context, UUID sectionId, LocalDate fromDate, LocalDate toDate, int limit, int offset) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<AttendanceSessionEntity> list = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, locked_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_sessions WHERE tenant_id = ? AND deleted_at IS NULL ");
            List<Object> params = new ArrayList<>();
            params.add(context.getTenantId());

            if (sectionId != null) {
                sql.append("AND section_id = ? ");
                params.add(sectionId);
            }
            if (fromDate != null) {
                sql.append("AND session_date >= ? ");
                params.add(java.sql.Date.valueOf(fromDate));
            }
            if (toDate != null) {
                sql.append("AND session_date <= ? ");
                params.add(java.sql.Date.valueOf(toDate));
            }
            sql.append("ORDER BY session_date DESC, start_time ASC LIMIT ? OFFSET ?");
            params.add(limit > 0 ? limit : 50);
            params.add(Math.max(offset, 0));

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapSession(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<AttendanceRecordEntity> markAttendance(UserSecurityContext context, UUID sessionId, List<AttendanceRecordEntity> items) {
        assertContext(context);
        if (sessionId == null) throw new IllegalArgumentException("sessionId cannot be null");
        if (items == null || items.isEmpty()) return Collections.emptyList();

        return context.executeInTransaction(connectionManager, conn -> {
            // Check session exists under tenant
            String sessCheck = "SELECT id FROM acd.attendance_sessions WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sessCheck)) {
                ps.setObject(1, sessionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AttendanceNotFoundException("Attendance session not found: " + sessionId);
                    }
                }
            }

            List<AttendanceRecordEntity> savedList = new ArrayList<>();
            String insertSql = "INSERT INTO acd.attendance_records (" +
                    "id, tenant_id, session_id, student_id, status, method, marked_by, marked_at, remarks, created_at, updated_at, created_by, row_version" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (AttendanceRecordEntity r : items) {
                    if (r.getId() == null) {
                        r.setId(UUID.randomUUID());
                    }
                    r.setTenantId(context.getTenantId());
                    r.setSessionId(sessionId);
                    r.setCreatedAt(now);
                    r.setUpdatedAt(now);
                    if (r.getMarkedAt() == null) {
                        r.setMarkedAt(now);
                    }

                    ps.setObject(1, r.getId());
                    ps.setObject(2, context.getTenantId());
                    ps.setObject(3, sessionId);
                    ps.setObject(4, r.getStudentId());
                    ps.setString(5, r.getStatus() != null ? r.getStatus() : "PRESENT");
                    ps.setString(6, r.getMethod() != null ? r.getMethod() : "MANUAL");
                    ps.setObject(7, r.getMarkedBy() != null ? r.getMarkedBy() : context.getUserId());
                    ps.setTimestamp(8, Timestamp.from(r.getMarkedAt().toInstant()));
                    ps.setString(9, r.getRemarks());
                    ps.setTimestamp(10, Timestamp.from(now.toInstant()));
                    ps.setTimestamp(11, Timestamp.from(now.toInstant()));
                    ps.setObject(12, context.getUserId());
                    ps.setInt(13, r.getRowVersion());

                    try {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        if ("23505".equals(e.getSQLState())) {
                            throw new DuplicateAttendanceRecordException("Duplicate attendance record for student " + r.getStudentId() + " in session " + sessionId);
                        }
                        throw e;
                    }
                    savedList.add(r);
                }
            }

            // Transactional Outbox Event
            if (outboxWriter != null) {
                try {
                    String payloadJson = String.format(
                            "{\"sessionId\":\"%s\",\"tenantId\":\"%s\",\"markedCount\":%d,\"markedAt\":\"%s\"}",
                            sessionId, context.getTenantId(), savedList.size(), now.toString()
                    );

                    OutboxHeaders headers = OutboxHeaders.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : null)
                            .schemaVersion(SCHEMA_VERSION)
                            .build();

                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .tenantId(context.getTenantId())
                            .service(SERVICE_NAME)
                            .aggregateType(AGGREGATE_TYPE)
                            .aggregateId(sessionId)
                            .eventType("attendance.marked.v1")
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for attendance marking: {}", e.getMessage());
                    throw new RuntimeException("Transactional outbox write failed: " + e.getMessage(), e);
                }
            }

            return savedList;
        });
    }

    @Override
    public List<AttendanceRecordEntity> findRecordsBySessionId(UserSecurityContext context, UUID sessionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<AttendanceRecordEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, session_id, student_id, status, method, marked_by, marked_at, remarks, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_records WHERE session_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sessionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRecord(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public Optional<AttendanceRecordEntity> findRecordById(UserSecurityContext context, UUID recordId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, session_id, student_id, status, method, marked_by, marked_at, remarks, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_records WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, recordId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRecord(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public AttendanceCorrectionEntity requestCorrection(UserSecurityContext context, AttendanceCorrectionEntity correction) {
        assertContext(context);
        if (correction == null) throw new IllegalArgumentException("correction cannot be null");
        if (correction.getRecordId() == null) throw new IllegalArgumentException("recordId cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            if (correction.getId() == null) {
                correction.setId(UUID.randomUUID());
            }
            correction.setTenantId(context.getTenantId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            correction.setCreatedAt(now);
            correction.setUpdatedAt(now);
            correction.setStatus("PENDING");

            // Look up current status if not set
            if (correction.getOldStatus() == null || correction.getOldStatus().trim().isEmpty()) {
                String recSql = "SELECT status FROM acd.attendance_records WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
                try (PreparedStatement ps = conn.prepareStatement(recSql)) {
                    ps.setObject(1, correction.getRecordId());
                    ps.setObject(2, context.getTenantId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            correction.setOldStatus(rs.getString("status"));
                        } else {
                            throw new AttendanceNotFoundException("Attendance record not found: " + correction.getRecordId());
                        }
                    }
                }
            }

            if (correction.getNewStatus() != null && correction.getNewStatus().equalsIgnoreCase(correction.getOldStatus())) {
                throw new IllegalArgumentException("New status must differ from current status: " + correction.getOldStatus());
            }

            String sql = "INSERT INTO acd.attendance_corrections (" +
                    "id, tenant_id, record_id, old_status, new_status, reason, requested_by, workflow_instance_id, status, created_at, updated_at, created_by, row_version" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, correction.getId());
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, correction.getRecordId());
                ps.setString(4, correction.getOldStatus());
                ps.setString(5, correction.getNewStatus());
                ps.setString(6, correction.getReason());
                ps.setObject(7, correction.getRequestedBy() != null ? correction.getRequestedBy() : context.getUserId());
                ps.setObject(8, correction.getWorkflowInstanceId());
                ps.setString(9, "PENDING");
                ps.setTimestamp(10, Timestamp.from(now.toInstant()));
                ps.setTimestamp(11, Timestamp.from(now.toInstant()));
                ps.setObject(12, context.getUserId());
                ps.setInt(13, correction.getRowVersion());
                ps.executeUpdate();
            }

            // Transactional Outbox
            if (outboxWriter != null) {
                try {
                    String payloadJson = String.format(
                            "{\"correctionId\":\"%s\",\"recordId\":\"%s\",\"tenantId\":\"%s\",\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"status\":\"PENDING\"}",
                            correction.getId(), correction.getRecordId(), context.getTenantId(), correction.getOldStatus(), correction.getNewStatus()
                    );
                    OutboxHeaders headers = OutboxHeaders.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : null)
                            .schemaVersion(SCHEMA_VERSION)
                            .build();

                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .tenantId(context.getTenantId())
                            .service(SERVICE_NAME)
                            .aggregateType("ATTENDANCE_CORRECTION")
                            .aggregateId(correction.getId())
                            .eventType("attendance.correction_requested.v1")
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for correction request: {}", e.getMessage());
                }
            }

            return correction;
        });
    }

    @Override
    public AttendanceCorrectionEntity applyCorrection(UserSecurityContext context, UUID correctionId, String decision, UUID approverId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String selSql = "SELECT id, tenant_id, record_id, old_status, new_status, reason, requested_by, approved_by, workflow_instance_id, status, decided_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_corrections WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            AttendanceCorrectionEntity c;
            try (PreparedStatement ps = conn.prepareStatement(selSql)) {
                ps.setObject(1, correctionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AttendanceNotFoundException("Attendance correction not found: " + correctionId);
                    }
                    c = mapCorrection(rs);
                }
            }

            if (!"PENDING".equalsIgnoreCase(c.getStatus())) {
                throw new IllegalStateException("Correction " + correctionId + " has already been decided: " + c.getStatus());
            }

            // Self-approval prevention (Wave 7 invariant: requester cannot approve their own request)
            UUID effectiveApproverId = approverId != null ? approverId : context.getUserId();
            if (effectiveApproverId != null && effectiveApproverId.equals(c.getRequestedBy())) {
                throw new com.campx.academic.attendance.exception.AttendanceCorrectionForbiddenException(
                        "Self-approval prohibited: requester cannot approve their own correction request");
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            c.setStatus(decision);
            c.setApprovedBy(effectiveApproverId);
            c.setDecidedAt(now);
            c.setUpdatedAt(now);

            String updCorr = "UPDATE acd.attendance_corrections SET status = ?, approved_by = ?, decided_at = ?, updated_at = ?, updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(updCorr)) {
                ps.setString(1, decision);
                ps.setObject(2, effectiveApproverId);
                ps.setTimestamp(3, Timestamp.from(now.toInstant()));
                ps.setTimestamp(4, Timestamp.from(now.toInstant()));
                ps.setObject(5, context.getUserId());
                ps.setObject(6, correctionId);
                ps.setObject(7, context.getTenantId());
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new IllegalStateException("Correction " + correctionId + " was concurrently updated or already decided");
                }
            }

            // If APPROVED, update original attendance record with version increment
            if ("APPROVED".equalsIgnoreCase(decision)) {
                String updRec = "UPDATE acd.attendance_records SET status = ?, remarks = ?, row_version = row_version + 1, updated_at = ?, updated_by = ? WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updRec)) {
                    ps.setString(1, c.getNewStatus());
                    ps.setString(2, c.getReason());
                    ps.setTimestamp(3, Timestamp.from(now.toInstant()));
                    ps.setObject(4, context.getUserId());
                    ps.setObject(5, c.getRecordId());
                    ps.setObject(6, context.getTenantId());
                    int recAffected = ps.executeUpdate();
                    if (recAffected == 0) {
                        throw new AttendanceNotFoundException("Attendance record not found for update: " + c.getRecordId());
                    }
                }
            }

            // Transactional Outbox
            if (outboxWriter != null) {
                try {
                    String eventType = "APPROVED".equalsIgnoreCase(decision)
                            ? "attendance.correction_applied.v1"
                            : "attendance.correction_rejected.v1";

                    String payloadJson = String.format(
                            "{\"correctionId\":\"%s\",\"recordId\":\"%s\",\"tenantId\":\"%s\",\"decision\":\"%s\",\"newStatus\":\"%s\",\"approvedBy\":\"%s\"}",
                            c.getId(), c.getRecordId(), context.getTenantId(), decision, c.getNewStatus(), effectiveApproverId != null ? effectiveApproverId : ""
                    );
                    OutboxHeaders headers = OutboxHeaders.builder()
                            .correlationId(UUID.randomUUID().toString())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : null)
                            .schemaVersion(SCHEMA_VERSION)
                            .build();

                    OutboxEvent outboxEvent = OutboxEvent.builder()
                            .tenantId(context.getTenantId())
                            .service(SERVICE_NAME)
                            .aggregateType("ATTENDANCE_CORRECTION")
                            .aggregateId(c.getId())
                            .eventType(eventType)
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for correction decision: {}", e.getMessage());
                }
            }

            return c;
        });
    }

    @Override
    public Optional<AttendanceCorrectionEntity> findCorrectionById(UserSecurityContext context, UUID correctionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, record_id, old_status, new_status, reason, requested_by, approved_by, workflow_instance_id, status, decided_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_corrections WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, correctionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCorrection(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<AttendanceCorrectionEntity> findCorrectionsBySessionId(UserSecurityContext context, UUID sessionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<AttendanceCorrectionEntity> list = new ArrayList<>();
            String sql = "SELECT ac.id, ac.tenant_id, ac.record_id, ac.old_status, ac.new_status, ac.reason, ac.requested_by, ac.approved_by, ac.workflow_instance_id, ac.status, ac.decided_at, ac.created_at, ac.updated_at, ac.created_by, ac.updated_by, ac.row_version " +
                    "FROM acd.attendance_corrections ac " +
                    "JOIN acd.attendance_records ar ON ar.id = ac.record_id AND ar.tenant_id = ac.tenant_id " +
                    "WHERE ar.session_id = ? AND ac.tenant_id = ? AND ac.deleted_at IS NULL ORDER BY ac.created_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, sessionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapCorrection(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<AttendanceCorrectionEntity> findCorrectionsByRecordId(UserSecurityContext context, UUID recordId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<AttendanceCorrectionEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, record_id, old_status, new_status, reason, requested_by, approved_by, workflow_instance_id, status, decided_at, created_at, updated_at, created_by, updated_by, row_version " +
                    "FROM acd.attendance_corrections WHERE record_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, recordId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapCorrection(rs));
                    }
                }
            }
            return list;
        });
    }

    private AttendanceSessionEntity mapSession(ResultSet rs) throws SQLException {
        AttendanceSessionEntity s = new AttendanceSessionEntity();
        s.setId((UUID) rs.getObject("id"));
        s.setTenantId((UUID) rs.getObject("tenant_id"));
        s.setSectionId((UUID) rs.getObject("section_id"));
        s.setTimetableEntryId((UUID) rs.getObject("timetable_entry_id"));
        s.setEmployeeId((UUID) rs.getObject("employee_id"));
        java.sql.Date d = rs.getDate("session_date");
        if (d != null) s.setSessionDate(d.toLocalDate());
        Time st = rs.getTime("start_time");
        if (st != null) s.setStartTime(st.toLocalTime());
        Time et = rs.getTime("end_time");
        if (et != null) s.setEndTime(et.toLocalTime());
        s.setStatus(rs.getString("status"));
        Timestamp lat = rs.getTimestamp("locked_at");
        if (lat != null) s.setLockedAt(lat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) s.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) s.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
        s.setCreatedBy((UUID) rs.getObject("created_by"));
        s.setUpdatedBy((UUID) rs.getObject("updated_by"));
        s.setRowVersion(rs.getInt("row_version"));
        return s;
    }

    private AttendanceRecordEntity mapRecord(ResultSet rs) throws SQLException {
        AttendanceRecordEntity r = new AttendanceRecordEntity();
        r.setId((UUID) rs.getObject("id"));
        r.setTenantId((UUID) rs.getObject("tenant_id"));
        r.setSessionId((UUID) rs.getObject("session_id"));
        r.setStudentId((UUID) rs.getObject("student_id"));
        r.setStatus(rs.getString("status"));
        r.setMethod(rs.getString("method"));
        r.setMarkedBy((UUID) rs.getObject("marked_by"));
        Timestamp mat = rs.getTimestamp("marked_at");
        if (mat != null) r.setMarkedAt(mat.toInstant().atOffset(ZoneOffset.UTC));
        r.setRemarks(rs.getString("remarks"));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) r.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) r.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
        r.setCreatedBy((UUID) rs.getObject("created_by"));
        r.setUpdatedBy((UUID) rs.getObject("updated_by"));
        r.setRowVersion(rs.getInt("row_version"));
        return r;
    }

    private AttendanceCorrectionEntity mapCorrection(ResultSet rs) throws SQLException {
        AttendanceCorrectionEntity c = new AttendanceCorrectionEntity();
        c.setId((UUID) rs.getObject("id"));
        c.setTenantId((UUID) rs.getObject("tenant_id"));
        c.setRecordId((UUID) rs.getObject("record_id"));
        c.setOldStatus(rs.getString("old_status"));
        c.setNewStatus(rs.getString("new_status"));
        c.setReason(rs.getString("reason"));
        c.setRequestedBy((UUID) rs.getObject("requested_by"));
        c.setApprovedBy((UUID) rs.getObject("approved_by"));
        c.setWorkflowInstanceId((UUID) rs.getObject("workflow_instance_id"));
        c.setStatus(rs.getString("status"));
        Timestamp dat = rs.getTimestamp("decided_at");
        if (dat != null) c.setDecidedAt(dat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) c.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) c.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
        c.setCreatedBy((UUID) rs.getObject("created_by"));
        c.setUpdatedBy((UUID) rs.getObject("updated_by"));
        c.setRowVersion(rs.getInt("row_version"));
        return c;
    }

    private void assertContext(UserSecurityContext context) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (context.getTenantId() == null && !context.isPlatformScope()) {
            throw new IllegalArgumentException("Tenant ID is required for attendance database persistence");
        }
    }
}
