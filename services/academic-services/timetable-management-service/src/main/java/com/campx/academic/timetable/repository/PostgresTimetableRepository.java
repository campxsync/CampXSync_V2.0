package com.campx.academic.timetable.repository;

import com.campx.academic.timetable.config.DatabaseConnectionManager;
import com.campx.academic.timetable.exception.InvalidReferenceException;
import com.campx.academic.timetable.exception.TimetableConflictException;
import com.campx.academic.timetable.exception.TimetableValidationException;
import com.campx.academic.timetable.model.*;
import com.campx.academic.timetable.security.UserSecurityContext;
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

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * PostgreSQL JDBC repository for Timetable aggregate tables (acd.timetables, acd.timetable_versions, acd.timetable_entries).
 * Enforces transaction-scoped tenant claims and least-privilege role switching ('authenticated').
 * Shares database transaction atomically between entity persistence and transactional outbox.
 */
public class PostgresTimetableRepository implements TimetableRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresTimetableRepository.class);
    private static final String SERVICE_NAME = "timetable-management-service";
    private static final String AGGREGATE_TYPE = "TIMETABLE";
    private static final String SCHEMA_VERSION = "1.0.0";

    private final DatabaseConnectionManager connectionManager;
    private final AuditLogRepository auditRepository;
    private final TransactionalOutboxWriter outboxWriter;
    private final ControlledIntelligenceAdapter intelligenceAdapter;

    public PostgresTimetableRepository() {
        this(DatabaseConnectionManager.getInstance(),
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public PostgresTimetableRepository(DatabaseConnectionManager connectionManager) {
        this(connectionManager,
                new PostgresAuditRepository(),
                new TransactionalOutboxWriter(),
                new ControlledIntelligenceAdapter());
    }

    public PostgresTimetableRepository(DatabaseConnectionManager connectionManager,
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
    public TimetableEntity saveTimetable(UserSecurityContext context, TimetableEntity timetable) {
        assertContext(context);
        if (timetable == null) throw new IllegalArgumentException("timetable cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            TimetableEntity saved;
            if (timetable.getId() == null) {
                saved = insertTimetable(conn, context, timetable);
            } else {
                saved = updateTimetable(conn, context, timetable);
            }

            String payloadJson = String.format(
                    "{\"timetableId\":\"%s\",\"tenantId\":\"%s\",\"collegeId\":\"%s\",\"termId\":\"%s\",\"name\":\"%s\",\"status\":\"%s\"}",
                    saved.getId(), context.getTenantId(), saved.getCollegeId(), saved.getTermId(),
                    saved.getName() != null ? saved.getName().replace("\"", "\\\"") : "", saved.getStatus()
            );

            // 1. Transactional Outbox (sys.outbox_events) on the same transaction
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
                            .eventType("timetable.created.v1")
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for timetable: {}", e.getMessage());
                    throw new RuntimeException("Transactional outbox write failed: " + e.getMessage(), e);
                }
            }

            // 2. Audit Log (audit.events)
            if (auditRepository != null) {
                try {
                    AuditRecord auditRecord = AuditRecord.builder()
                            .action("TIMETABLE_CREATED")
                            .tenantId(context.getTenantId())
                            .principalId(context.getUserId())
                            .principalType("USER")
                            .resourceType("TIMETABLE")
                            .resourceId(saved.getId())
                            .status("SUCCESS")
                            .severity("INFO")
                            .detailJson(payloadJson)
                            .build();
                    auditRepository.appendAuditEvent(conn, auditRecord);
                } catch (Exception e) {
                    logger.warn("Failed to append audit event for timetable: {}", e.getMessage());
                }
            }

            return saved;
        });
    }

    private TimetableEntity insertTimetable(Connection conn, UserSecurityContext context, TimetableEntity t) throws SQLException {
        if (t.getId() == null) {
            t.setId(UUID.randomUUID());
        }
        t.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);

        String sql = "INSERT INTO acd.timetables (" +
                "id, tenant_id, college_id, term_id, batch_id, name, status, created_at, updated_at, created_by" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, t.getId());
            ps.setObject(2, context.getTenantId());
            ps.setObject(3, t.getCollegeId());
            ps.setObject(4, t.getTermId());
            ps.setObject(5, t.getBatchId());
            ps.setString(6, t.getName());
            ps.setString(7, t.getStatus() != null ? t.getStatus() : "DRAFT");
            ps.setTimestamp(8, Timestamp.from(now.toInstant()));
            ps.setTimestamp(9, Timestamp.from(now.toInstant()));
            ps.setObject(10, context.getUserId());
            ps.executeUpdate();
        }
        return t;
    }

    private TimetableEntity updateTimetable(Connection conn, UserSecurityContext context, TimetableEntity t) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        t.setUpdatedAt(now);

        String sql = "UPDATE acd.timetables SET " +
                "name = ?, status = ?, batch_id = ?, updated_at = ?, updated_by = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getName());
            ps.setString(2, t.getStatus());
            ps.setObject(3, t.getBatchId());
            ps.setTimestamp(4, Timestamp.from(now.toInstant()));
            ps.setObject(5, context.getUserId());
            ps.setObject(6, t.getId());
            ps.setObject(7, context.getTenantId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Timetable not found or update unauthorized: " + t.getId());
            }
        }
        return t;
    }

    @Override
    public Optional<TimetableEntity> findTimetableById(UserSecurityContext context, UUID timetableId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, college_id, term_id, batch_id, name, status, created_at, updated_at, row_version " +
                    "FROM acd.timetables WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, timetableId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TimetableEntity t = new TimetableEntity();
                        t.setId((UUID) rs.getObject("id"));
                        t.setTenantId((UUID) rs.getObject("tenant_id"));
                        t.setCollegeId((UUID) rs.getObject("college_id"));
                        t.setTermId((UUID) rs.getObject("term_id"));
                        t.setBatchId((UUID) rs.getObject("batch_id"));
                        t.setName(rs.getString("name"));
                        t.setStatus(rs.getString("status"));
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) t.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
                        Timestamp uat = rs.getTimestamp("updated_at");
                        if (uat != null) t.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
                        t.setRowVersion(rs.getInt("row_version"));
                        return Optional.of(t);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public TimetableVersionEntity saveVersion(UserSecurityContext context, TimetableVersionEntity version) {
        assertContext(context);
        if (version == null) throw new IllegalArgumentException("version cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            TimetableVersionEntity saved;
            if (version.getId() == null) {
                saved = insertVersion(conn, context, version);
            } else {
                saved = updateVersion(conn, context, version);
            }

            if ("PUBLISHED".equalsIgnoreCase(saved.getStatus()) && outboxWriter != null) {
                try {
                    String payloadJson = String.format(
                            "{\"timetableVersionId\":\"%s\",\"timetableId\":\"%s\",\"versionNo\":%d,\"status\":\"%s\"}",
                            saved.getId(), saved.getTimetableId(), saved.getVersionNo(), saved.getStatus()
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
                            .eventType("timetable.version_published.v1")
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for version publish: {}", e.getMessage());
                }
            }

            return saved;
        });
    }

    private TimetableVersionEntity insertVersion(Connection conn, UserSecurityContext context, TimetableVersionEntity v) throws SQLException {
        if (v.getId() == null) {
            v.setId(UUID.randomUUID());
        }
        v.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        v.setCreatedAt(now);
        v.setUpdatedAt(now);

        String sql = "INSERT INTO acd.timetable_versions (" +
                "id, tenant_id, timetable_id, version_no, status, change_note, published_at, created_at, updated_at, created_by" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, v.getId());
            ps.setObject(2, context.getTenantId());
            ps.setObject(3, v.getTimetableId());
            ps.setInt(4, v.getVersionNo());
            ps.setString(5, v.getStatus() != null ? v.getStatus() : "DRAFT");
            ps.setString(6, v.getChangeNote());
            if (v.getPublishedAt() != null) {
                ps.setTimestamp(7, Timestamp.from(v.getPublishedAt().toInstant()));
            } else if ("PUBLISHED".equalsIgnoreCase(v.getStatus())) {
                ps.setTimestamp(7, Timestamp.from(now.toInstant()));
            } else {
                ps.setNull(7, Types.TIMESTAMP);
            }
            ps.setTimestamp(8, Timestamp.from(now.toInstant()));
            ps.setTimestamp(9, Timestamp.from(now.toInstant()));
            ps.setObject(10, context.getUserId());
            ps.executeUpdate();
        }
        return v;
    }

    private TimetableVersionEntity updateVersion(Connection conn, UserSecurityContext context, TimetableVersionEntity v) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        v.setUpdatedAt(now);

        String sql = "UPDATE acd.timetable_versions SET " +
                "status = ?, change_note = ?, published_at = ?, updated_at = ?, updated_by = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getStatus());
            ps.setString(2, v.getChangeNote());
            if (v.getPublishedAt() != null) {
                ps.setTimestamp(3, Timestamp.from(v.getPublishedAt().toInstant()));
            } else {
                ps.setNull(3, Types.TIMESTAMP);
            }
            ps.setTimestamp(4, Timestamp.from(now.toInstant()));
            ps.setObject(5, context.getUserId());
            ps.setObject(6, v.getId());
            ps.setObject(7, context.getTenantId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Timetable version not found or update unauthorized: " + v.getId());
            }
        }
        return v;
    }

    @Override
    public Optional<TimetableVersionEntity> findVersionById(UserSecurityContext context, UUID versionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, timetable_id, version_no, status, change_note, published_at, created_at, updated_at " +
                    "FROM acd.timetable_versions WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, versionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TimetableVersionEntity v = new TimetableVersionEntity();
                        v.setId((UUID) rs.getObject("id"));
                        v.setTenantId((UUID) rs.getObject("tenant_id"));
                        v.setTimetableId((UUID) rs.getObject("timetable_id"));
                        v.setVersionNo(rs.getInt("version_no"));
                        v.setStatus(rs.getString("status"));
                        v.setChangeNote(rs.getString("change_note"));
                        Timestamp pat = rs.getTimestamp("published_at");
                        if (pat != null) v.setPublishedAt(pat.toInstant().atOffset(ZoneOffset.UTC));
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) v.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
                        Timestamp uat = rs.getTimestamp("updated_at");
                        if (uat != null) v.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
                        return Optional.of(v);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<TimetableVersionEntity> findActiveVersionByTimetableId(UserSecurityContext context, UUID timetableId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, timetable_id, version_no, status, change_note, published_at, created_at, updated_at " +
                    "FROM acd.timetable_versions " +
                    "WHERE timetable_id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "ORDER BY version_no DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, timetableId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TimetableVersionEntity v = new TimetableVersionEntity();
                        v.setId((UUID) rs.getObject("id"));
                        v.setTenantId((UUID) rs.getObject("tenant_id"));
                        v.setTimetableId((UUID) rs.getObject("timetable_id"));
                        v.setVersionNo(rs.getInt("version_no"));
                        v.setStatus(rs.getString("status"));
                        v.setChangeNote(rs.getString("change_note"));
                        Timestamp pat = rs.getTimestamp("published_at");
                        if (pat != null) v.setPublishedAt(pat.toInstant().atOffset(ZoneOffset.UTC));
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) v.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
                        Timestamp uat = rs.getTimestamp("updated_at");
                        if (uat != null) v.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
                        return Optional.of(v);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public void ensureSectionFacultyAssignment(UserSecurityContext context, UUID sectionId, UUID employeeId) {
        assertContext(context);
        if (sectionId == null || employeeId == null) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role) " +
                    "VALUES (?, ?, ?, 'PRIMARY') " +
                    "ON CONFLICT DO NOTHING";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, sectionId);
                ps.setObject(3, employeeId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public TimetableEntryEntity saveEntry(UserSecurityContext context, TimetableEntryEntity entry) {
        assertContext(context);
        if (entry == null) throw new IllegalArgumentException("entry cannot be null");

        return context.executeInTransaction(connectionManager, conn -> {
            TimetableEntryEntity saved;
            try {
                if (entry.getId() == null) {
                    saved = insertEntry(conn, context, entry);
                } else {
                    saved = updateEntry(conn, context, entry);
                }
            } catch (SQLException e) {
                handlePostgresConflict(e);
                throw new RuntimeException("Slot entry persistence error: " + e.getMessage(), e);
            }

            String payloadJson = String.format(
                    "{\"slotId\":\"%s\",\"timetableVersionId\":\"%s\",\"sectionId\":\"%s\",\"subjectId\":\"%s\",\"employeeId\":\"%s\",\"dayOfWeek\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\",\"entryType\":\"%s\"}",
                    saved.getId(), saved.getTimetableVersionId(),
                    saved.getSectionId() != null ? saved.getSectionId() : "",
                    saved.getSubjectId() != null ? saved.getSubjectId() : "",
                    saved.getEmployeeId() != null ? saved.getEmployeeId() : "",
                    saved.getDayOfWeek(),
                    saved.getStartTime() != null ? saved.getStartTime().toString() : "",
                    saved.getEndTime() != null ? saved.getEndTime().toString() : "",
                    saved.getEntryType()
            );

            // 1. Transactional Outbox (sys.outbox_events) on the same transaction
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
                            .eventType("timetable.slot_changed.v1")
                            .headers(headers)
                            .payload(payloadJson)
                            .build();

                    outboxWriter.write(conn, outboxEvent);
                } catch (Exception e) {
                    logger.warn("Failed to enqueue transactional outbox event for slot: {}", e.getMessage());
                    throw new RuntimeException("Transactional outbox write failed: " + e.getMessage(), e);
                }
            }

            // 2. Audit Record (audit.events)
            if (auditRepository != null) {
                try {
                    AuditRecord auditRecord = AuditRecord.builder()
                            .action("TIMETABLE_ENTRY_CREATED")
                            .tenantId(context.getTenantId())
                            .principalId(context.getUserId())
                            .principalType("USER")
                            .resourceType("TIMETABLE_ENTRY")
                            .resourceId(saved.getId())
                            .status("SUCCESS")
                            .severity("INFO")
                            .detailJson(payloadJson)
                            .build();
                    auditRepository.appendAuditEvent(conn, auditRecord);
                } catch (Exception e) {
                    logger.warn("Failed to append audit event for slot: {}", e.getMessage());
                }
            }

            // 3. Controlled Intelligence Layer Relay
            if (intelligenceAdapter != null) {
                try {
                    IntelligenceContextEvent intelEvent = IntelligenceContextEvent.builder()
                            .eventType("timetable.slot_changed.v1")
                            .aggregateType(AGGREGATE_TYPE)
                            .aggregateId(saved.getId())
                            .tenantId(context.getTenantId())
                            .actorId(context.getUserId() != null ? context.getUserId().toString() : "SYSTEM")
                            .sourceService(SERVICE_NAME)
                            .payloadJson(payloadJson)
                            .build();
                    intelligenceAdapter.publish(intelEvent);
                } catch (Exception e) {
                    logger.warn("Intelligence layer context relay failed: {}", e.getMessage());
                }
            }

            return saved;
        });
    }

    private void handlePostgresConflict(SQLException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("ex_tte_employee")) {
            throw new TimetableConflictException("ACD_TIMETABLE_CONFLICT",
                    "Faculty schedule conflict: assigned faculty is already scheduled for another slot during this time period.", 409, null);
        }
        if (msg.contains("ex_tte_section")) {
            throw new TimetableConflictException("ACD_TIMETABLE_CONFLICT",
                    "Batch/Section schedule conflict: section is already scheduled for another class during this time period.", 409, null);
        }
        if (msg.contains("ex_tte_room")) {
            throw new TimetableConflictException("ACD_TIMETABLE_CONFLICT",
                    "Room conflict: room is already booked for another class during this time period.", 409, null);
        }
        if (msg.contains("ck_timetable_entries_x1")) {
            throw new TimetableValidationException("Slot end time must be after start time");
        }
        if (msg.contains("23514") || msg.contains("guard_timetable_entry") || msg.contains("employee is not assigned")) {
            throw new InvalidReferenceException(msg);
        }
    }

    private TimetableEntryEntity insertEntry(Connection conn, UserSecurityContext context, TimetableEntryEntity e) throws SQLException {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        e.setTenantId(context.getTenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        String sql = "INSERT INTO acd.timetable_entries (" +
                "id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, room_id, " +
                "day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at, created_by" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::time, ?::time, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, e.getId());
            ps.setObject(2, context.getTenantId());
            ps.setObject(3, e.getTimetableVersionId());
            ps.setObject(4, e.getSectionId());
            ps.setObject(5, e.getSubjectId());
            ps.setObject(6, e.getEmployeeId());
            ps.setObject(7, e.getRoomId());
            ps.setInt(8, e.getDayOfWeek());
            ps.setString(9, e.getStartTime() != null ? e.getStartTime().toString() : "10:00:00");
            ps.setString(10, e.getEndTime() != null ? e.getEndTime().toString() : "11:00:00");
            ps.setString(11, e.getEntryType() != null ? e.getEntryType() : "LECTURE");
            if (e.getValidFrom() != null) {
                ps.setDate(12, java.sql.Date.valueOf(e.getValidFrom()));
            } else {
                ps.setNull(12, Types.DATE);
            }
            if (e.getValidTo() != null) {
                ps.setDate(13, java.sql.Date.valueOf(e.getValidTo()));
            } else {
                ps.setNull(13, Types.DATE);
            }
            ps.setTimestamp(14, Timestamp.from(now.toInstant()));
            ps.setTimestamp(15, Timestamp.from(now.toInstant()));
            ps.setObject(16, context.getUserId());
            ps.executeUpdate();
        }
        return e;
    }

    private TimetableEntryEntity updateEntry(Connection conn, UserSecurityContext context, TimetableEntryEntity e) throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        e.setUpdatedAt(now);

        String sql = "UPDATE acd.timetable_entries SET " +
                "section_id = ?, subject_id = ?, employee_id = ?, room_id = ?, day_of_week = ?, " +
                "start_time = ?::time, end_time = ?::time, entry_type = ?, valid_from = ?, valid_to = ?, " +
                "updated_at = ?, updated_by = ? " +
                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, e.getSectionId());
            ps.setObject(2, e.getSubjectId());
            ps.setObject(3, e.getEmployeeId());
            ps.setObject(4, e.getRoomId());
            ps.setInt(5, e.getDayOfWeek());
            ps.setString(6, e.getStartTime() != null ? e.getStartTime().toString() : "10:00:00");
            ps.setString(7, e.getEndTime() != null ? e.getEndTime().toString() : "11:00:00");
            ps.setString(8, e.getEntryType() != null ? e.getEntryType() : "LECTURE");
            if (e.getValidFrom() != null) {
                ps.setDate(9, java.sql.Date.valueOf(e.getValidFrom()));
            } else {
                ps.setNull(9, Types.DATE);
            }
            if (e.getValidTo() != null) {
                ps.setDate(10, java.sql.Date.valueOf(e.getValidTo()));
            } else {
                ps.setNull(10, Types.DATE);
            }
            ps.setTimestamp(11, Timestamp.from(now.toInstant()));
            ps.setObject(12, context.getUserId());
            ps.setObject(13, e.getId());
            ps.setObject(14, context.getTenantId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Timetable entry not found or update unauthorized: " + e.getId());
            }
        }
        return e;
    }

    @Override
    public Optional<TimetableEntryEntity> findEntryById(UserSecurityContext context, UUID entryId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, room_id, " +
                    "day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at " +
                    "FROM acd.timetable_entries WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, entryId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapEntry(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<TimetableEntryEntity> findEntriesByVersionId(UserSecurityContext context, UUID versionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<TimetableEntryEntity> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, room_id, " +
                    "day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at " +
                    "FROM acd.timetable_entries WHERE timetable_version_id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "ORDER BY day_of_week, start_time";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, versionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapEntry(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<TimetableSlotResponse> findSlotResponsesByVersionId(UserSecurityContext context, UUID versionId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            List<TimetableSlotResponse> list = new ArrayList<>();
            String sql = "SELECT " +
                    "tte.id, tte.tenant_id, tte.timetable_version_id, tte.section_id, tte.subject_id, tte.employee_id, tte.room_id, " +
                    "tte.day_of_week, tte.start_time, tte.end_time, tte.entry_type, tte.created_at, " +
                    "sub.name as subject_name, sub.code as subject_code, " +
                    "p.display_name as faculty_name, " +
                    "sec.section_code, " +
                    "r.room_number as room_code " +
                    "FROM acd.timetable_entries tte " +
                    "LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id AND sub.tenant_id = tte.tenant_id " +
                    "LEFT JOIN hrm.employees emp ON emp.id = tte.employee_id AND emp.tenant_id = tte.tenant_id " +
                    "LEFT JOIN core.persons p ON p.id = emp.person_id AND p.tenant_id = emp.tenant_id " +
                    "LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id AND sec.tenant_id = tte.tenant_id " +
                    "LEFT JOIN core.rooms r ON r.id = tte.room_id AND r.tenant_id = tte.tenant_id " +
                    "WHERE tte.timetable_version_id = ? AND tte.tenant_id = ? AND tte.deleted_at IS NULL " +
                    "ORDER BY tte.day_of_week, tte.start_time";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, versionId);
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TimetableSlotResponse res = new TimetableSlotResponse();
                        res.setId((UUID) rs.getObject("id"));
                        res.setTenantId((UUID) rs.getObject("tenant_id"));
                        res.setTimetableVersionId((UUID) rs.getObject("timetable_version_id"));
                        res.setSectionId((UUID) rs.getObject("section_id"));
                        res.setSectionCode(rs.getString("section_code"));
                        res.setSubjectId((UUID) rs.getObject("subject_id"));
                        res.setSubjectName(rs.getString("subject_name"));
                        res.setSubjectCode(rs.getString("subject_code"));
                        res.setEmployeeId((UUID) rs.getObject("employee_id"));
                        res.setFacultyName(rs.getString("faculty_name"));
                        res.setRoomId((UUID) rs.getObject("room_id"));
                        res.setRoomCode(rs.getString("room_code"));
                        res.setDayOfWeek(rs.getInt("day_of_week"));
                        Time st = rs.getTime("start_time");
                        if (st != null) res.setStartTime(st.toString());
                        Time et = rs.getTime("end_time");
                        if (et != null) res.setEndTime(et.toString());
                        res.setEntryType(rs.getString("entry_type"));
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) res.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
                        list.add(res);
                    }
                }
            }
            return list;
        });
    }

    @Override
    public boolean deleteEntry(UserSecurityContext context, UUID entryId) {
        assertContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.timetable_entries SET deleted_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, entryId);
                ps.setObject(3, context.getTenantId());
                return ps.executeUpdate() > 0;
            }
        });
    }

    private TimetableEntryEntity mapEntry(ResultSet rs) throws SQLException {
        TimetableEntryEntity e = new TimetableEntryEntity();
        e.setId((UUID) rs.getObject("id"));
        e.setTenantId((UUID) rs.getObject("tenant_id"));
        e.setTimetableVersionId((UUID) rs.getObject("timetable_version_id"));
        e.setSectionId((UUID) rs.getObject("section_id"));
        e.setSubjectId((UUID) rs.getObject("subject_id"));
        e.setEmployeeId((UUID) rs.getObject("employee_id"));
        e.setRoomId((UUID) rs.getObject("room_id"));
        e.setDayOfWeek(rs.getInt("day_of_week"));
        Time st = rs.getTime("start_time");
        if (st != null) e.setStartTime(st.toLocalTime());
        Time et = rs.getTime("end_time");
        if (et != null) e.setEndTime(et.toLocalTime());
        e.setEntryType(rs.getString("entry_type"));
        java.sql.Date vf = rs.getDate("valid_from");
        if (vf != null) e.setValidFrom(vf.toLocalDate());
        java.sql.Date vt = rs.getDate("valid_to");
        if (vt != null) e.setValidTo(vt.toLocalDate());
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) e.setCreatedAt(cat.toInstant().atOffset(ZoneOffset.UTC));
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) e.setUpdatedAt(uat.toInstant().atOffset(ZoneOffset.UTC));
        return e;
    }

    private void assertContext(UserSecurityContext context) {
        if (context == null) throw new IllegalArgumentException("UserSecurityContext cannot be null");
        if (context.getTenantId() == null && !context.isPlatformScope()) {
            throw new IllegalArgumentException("Tenant ID is required for timetable database persistence");
        }
    }
}
