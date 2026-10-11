package com.campx.academic.attendance.service;

import com.campx.academic.attendance.exception.*;
import com.campx.academic.attendance.model.AttendanceModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core Domain Service for ACD-06: Attendance Management Service.
 * Implements business rules FR-01 through FR-12, lifecycle governance,
 * deterministic timetable validation, atomic marking, audited corrections,
 * derived summaries, and shortage signal generation.
 */
public class AttendanceDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AttendanceDomainService.class);

    // =========================================================================
    // Core Domain State Stores (Thread-Safe Concurrent Storage)
    // =========================================================================

    private final Map<String, AttendanceSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, AttendanceRecord> records = new ConcurrentHashMap<>();
    private final List<AttendanceCorrection> corrections = new CopyOnWriteArrayList<>();
    private final Map<String, AttendanceSummary> summaries = new ConcurrentHashMap<>();
    private final Map<String, StatusCatalogEntry> statusCatalog = new ConcurrentHashMap<>();

    // Compound unique index for sessions: tenantId:batchId:subjectId:attendanceDate:periodNo -> sessionId
    private final Map<String, String> sessionUniqueIndex = new ConcurrentHashMap<>();

    // Compound unique index for records: tenantId:sessionId:studentId -> recordId
    private final Map<String, String> recordUniqueIndex = new ConcurrentHashMap<>();

    // Compound unique index for summaries: tenantId:studentId:subjectId:termId -> summaryId
    private final Map<String, String> summaryUniqueIndex = new ConcurrentHashMap<>();

    // Technical collections
    private final Map<String, OutboxEvent> outbox = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterQueue = new CopyOnWriteArrayList<>();
    private final List<AttendanceAuditLog> auditLogs = new CopyOnWriteArrayList<>();
    private final Map<String, ApiKeyRecord> apiKeys = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<>());

    // Reference Caches from Upstream Modules
    private final Map<String, TimetableSlotRef> activeTimetableSlots = new ConcurrentHashMap<>();
    private final Map<String, BatchRosterRef> batchRosters = new ConcurrentHashMap<>();
    private final Map<String, Boolean> studentEligibility = new ConcurrentHashMap<>();
    private final Set<String> holidays = Collections.synchronizedSet(new HashSet<>());

    // Configurable Settings & Flags
    private volatile boolean failClosedOnTimetableOutage = false;
    private volatile boolean failClosedOnRosterOutage = false;
    private volatile boolean markingWindowEnforced = false;
    private volatile long markingWindowMinutes = 1440; // 24 hours default
    private volatile double defaultShortageThreshold = 75.0; // 75.0%

    // ID Generators
    private final AtomicLong sessionIdSeq = new AtomicLong(1000);
    private final AtomicLong recordIdSeq = new AtomicLong(5000);
    private final AtomicLong correctionIdSeq = new AtomicLong(3000);
    private final AtomicLong summaryIdSeq = new AtomicLong(2000);
    private final AtomicLong outboxIdSeq = new AtomicLong(1);
    private final AtomicLong dlqIdSeq = new AtomicLong(1);

    public AttendanceDomainService() {
        seedStatusCatalog();
        seedDefaultReferenceData();
    }

    // =========================================================================
    // Initialization & Seeding
    // =========================================================================

    private void seedStatusCatalog() {
        StatusCatalogEntry present = new StatusCatalogEntry();
        present.statusCode = "PRESENT";
        present.name = "Present";
        present.countsAsPresent = true;
        present.weight = 1.0;
        statusCatalog.put(present.statusCode, present);

        StatusCatalogEntry absent = new StatusCatalogEntry();
        absent.statusCode = "ABSENT";
        absent.name = "Absent";
        absent.countsAsPresent = false;
        absent.weight = 0.0;
        statusCatalog.put(absent.statusCode, absent);

        StatusCatalogEntry leave = new StatusCatalogEntry();
        leave.statusCode = "LEAVE";
        leave.name = "Approved Leave";
        leave.countsAsPresent = false;
        leave.weight = 0.0;
        leave.requiresReason = true;
        statusCatalog.put(leave.statusCode, leave);

        StatusCatalogEntry late = new StatusCatalogEntry();
        late.statusCode = "LATE";
        late.name = "Late Entry";
        late.countsAsPresent = true;
        late.weight = 1.0;
        statusCatalog.put(late.statusCode, late);

        StatusCatalogEntry onDuty = new StatusCatalogEntry();
        onDuty.statusCode = "ON_DUTY";
        onDuty.name = "On Institutional Duty";
        onDuty.countsAsPresent = true;
        onDuty.weight = 1.0;
        statusCatalog.put(onDuty.statusCode, onDuty);
    }

    private void seedDefaultReferenceData() {
        // Seed default API keys
        apiKeys.put("KEY-LMS-2026", new ApiKeyRecord("KEY-LMS-2026", "TENANT-001", "campx-lms-secret-key-2026"));
        apiKeys.put("KEY-EXT-DEVICE", new ApiKeyRecord("KEY-EXT-DEVICE", "TENANT-001", "campx-device-secret-key-2026"));

        // Seed default timetable slots from ACD-05
        activeTimetableSlots.put("TT-SLOT-001", new TimetableSlotRef("TT-SLOT-001", "BATCH-001", "SUB-101", "FAC-001", "MONDAY", "09:00", "10:00", 1));
        activeTimetableSlots.put("TT-SLOT-002", new TimetableSlotRef("TT-SLOT-002", "BATCH-001", "SUB-102", "FAC-002", "MONDAY", "10:00", "11:00", 1));
        activeTimetableSlots.put("TT-SLOT-003", new TimetableSlotRef("TT-SLOT-003", "BATCH-002", "SUB-101", "FAC-001", "TUESDAY", "11:00", "12:00", 1));

        // Seed default batch rosters from ACD-04
        batchRosters.put("BATCH-001", new BatchRosterRef("BATCH-001", "TENANT-001",
                Arrays.asList("STU-001", "STU-002", "STU-003", "STU-004", "STU-005")));
        batchRosters.put("BATCH-002", new BatchRosterRef("BATCH-002", "TENANT-001",
                Arrays.asList("STU-006", "STU-007", "STU-008")));

        // Seed student eligibility
        for (int i = 1; i <= 10; i++) {
            studentEligibility.put("STU-00" + i, true);
        }

        // Seed sample holiday
        holidays.add("2026-12-25"); // Christmas
        holidays.add("2026-01-01"); // New Year
    }

    // =========================================================================
    // 1. Attendance Session Management (Stories 1-6)
    // =========================================================================

    public AttendanceSession createSession(CreateSessionRequest req, String tenantId, String userId, String userRole) {
        if (req == null) {
            throw new AttendanceBadRequestException("Create session request body cannot be null");
        }
        if (req.batchId == null || req.batchId.trim().isEmpty()) {
            throw new AttendanceBadRequestException("batchId is required");
        }
        if (req.subjectId == null || req.subjectId.trim().isEmpty()) {
            throw new AttendanceBadRequestException("subjectId is required");
        }
        if (req.timetableEntryId == null || req.timetableEntryId.trim().isEmpty()) {
            throw new AttendanceBadRequestException("timetableEntryId is required");
        }
        if (req.attendanceDate == null || req.attendanceDate.trim().isEmpty()) {
            throw new AttendanceBadRequestException("attendanceDate is required (YYYY-MM-DD)");
        }

        // Fail closed if ACD-05 timetable dependency is unavailable (Story 2)
        if (failClosedOnTimetableOutage) {
            throw new DependencyUnavailableException("ACD-05 Timetable Management service is unavailable. Session creation failed closed.");
        }

        // Validate timetable slot (Story 2)
        TimetableSlotRef slot = activeTimetableSlots.get(req.timetableEntryId);
        if (slot == null || !slot.active) {
            throw new InvalidTimetableException("Timetable entry '" + req.timetableEntryId + "' does not exist or is inactive in ACD-05");
        }
        if (!slot.batchId.equals(req.batchId) || !slot.subjectId.equals(req.subjectId)) {
            throw new InvalidTimetableException("Timetable slot mapping mismatch: expected batch=" + slot.batchId +
                    ", subject=" + slot.subjectId + " but received batch=" + req.batchId + ", subject=" + req.subjectId);
        }

        // Calendar check (Story 13): Reject if date is a declared holiday or non-teaching day
        if (holidays.contains(req.attendanceDate)) {
            throw new CalendarViolationException("Attendance session cannot be created on declared institutional holiday: " + req.attendanceDate);
        }

        // Faculty assignment scope check (Story 64)
        if ("FACULTY".equalsIgnoreCase(userRole)) {
            if (slot.facultyId != null && !slot.facultyId.equalsIgnoreCase(userId)) {
                throw new AttendanceForbiddenException("Faculty " + userId + " is not assigned to timetable slot " + req.timetableEntryId);
            }
        }

        // Enforce duplicate session prevention (Story 3)
        String uniqueKey = tenantId + ":" + req.batchId + ":" + req.subjectId + ":" + req.attendanceDate + ":" + req.periodNo;
        synchronized (sessionUniqueIndex) {
            String existingId = sessionUniqueIndex.get(uniqueKey);
            if (existingId != null) {
                AttendanceSession existing = sessions.get(existingId);
                if (existing != null && existing.getStatus() != SessionStatus.CANCELLED) {
                    throw new DuplicateSessionException("An active session already exists for batch " + req.batchId +
                            ", subject " + req.subjectId + ", date " + req.attendanceDate + ", period " + req.periodNo);
                }
            }

            // Resolve roster count from ACD-04 (Story 1, 9)
            if (failClosedOnRosterOutage && !batchRosters.containsKey(req.batchId)) {
                throw new DependencyUnavailableException("ACD-04 Batch Management roster service unavailable");
            }
            BatchRosterRef roster = batchRosters.get(req.batchId);
            int resolvedCount = (roster != null) ? roster.studentIds.size() : 0;

            String sessionId = "ATT-SESS-" + sessionIdSeq.incrementAndGet();
            AttendanceSession session = new AttendanceSession();
            session.setId(sessionId);
            session.setTenantId(tenantId);
            session.setBatchId(req.batchId);
            session.setSubjectId(req.subjectId);
            session.setTimetableEntryId(req.timetableEntryId);
            session.setAttendanceDate(req.attendanceDate);
            session.setPeriodNo(req.periodNo);
            session.setStartTime(req.startTime != null ? req.startTime : slot.startTime);
            session.setEndTime(req.endTime != null ? req.endTime : slot.endTime);
            session.setStatus(SessionStatus.OPEN);
            session.setRosterCount(resolvedCount);
            session.setVersion(1);

            sessions.put(sessionId, session);
            sessionUniqueIndex.put(uniqueKey, sessionId);

            // Publish Outbox Event (Story 1, 50)
            publishOutboxEvent("AttendanceSessionCreated", sessionId, serializeSession(session), tenantId);

            // Audit
            recordAudit(tenantId, "SESSION_CREATED", userId, userRole, sessionId,
                    "Created attendance session for batch " + req.batchId + " date " + req.attendanceDate);

            logger.info("[ACD-06] Created attendance session {} for batch {} on {}", sessionId, req.batchId, req.attendanceDate);
            return session;
        }
    }

    public AttendanceSession getSession(String sessionId, String tenantId) {
        AttendanceSession session = sessions.get(sessionId);
        if (session == null || !session.getTenantId().equals(tenantId)) {
            throw new AttendanceNotFoundException("Attendance session not found with identifier: " + sessionId);
        }
        return session;
    }

    public AttendanceSession updateSession(String sessionId, UpdateSessionRequest req, String tenantId, String userId, String userRole) {
        AttendanceSession session = getSession(sessionId, tenantId);

        // Immutability post-lock (Story 4, 25)
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new InvalidLifecycleStateException("Session " + sessionId + " in state " + session.getStatus() +
                    " cannot be modified directly. Only OPEN sessions permit administrative updates.");
        }

        // Optimistic concurrency (Story 4, 60)
        if (req.expectedVersion != null && req.expectedVersion != session.getVersion()) {
            throw new AttendanceVersionConflictException("Session version conflict: expected version " + req.expectedVersion +
                    " but current version is " + session.getVersion());
        }

        if (req.startTime != null) session.setStartTime(req.startTime);
        if (req.endTime != null) session.setEndTime(req.endTime);
        session.setVersion(session.getVersion() + 1);
        session.setUpdatedAt(System.currentTimeMillis());

        publishOutboxEvent("AttendanceSessionUpdated", sessionId, serializeSession(session), tenantId);
        recordAudit(tenantId, "SESSION_UPDATED", userId, userRole, sessionId, "Updated session metadata");
        return session;
    }

    public List<AttendanceSession> listSessions(String tenantId, String batchId, String subjectId, String date, SessionStatus status) {
        return sessions.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId))
                .filter(s -> batchId == null || s.getBatchId().equalsIgnoreCase(batchId))
                .filter(s -> subjectId == null || s.getSubjectId().equalsIgnoreCase(subjectId))
                .filter(s -> date == null || s.getAttendanceDate().equals(date))
                .filter(s -> status == null || s.getStatus() == status)
                .sorted(Comparator.comparing(AttendanceSession::getAttendanceDate).reversed())
                .collect(Collectors.toList());
    }

    public AttendanceSession cancelSession(String sessionId, String tenantId, String userId, String userRole, String reason) {
        AttendanceSession session = getSession(sessionId, tenantId);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            return session; // idempotent
        }

        session.setStatus(SessionStatus.CANCELLED);
        session.setUpdatedAt(System.currentTimeMillis());
        session.setVersion(session.getVersion() + 1);

        // Recalculate all affected student summaries to exclude cancelled session (Story 6, 31)
        recalculateSummariesForBatch(tenantId, session.getBatchId(), session.getSubjectId());

        publishOutboxEvent("AttendanceSessionCancelled", sessionId, "{\"sessionId\":\"" + sessionId + "\",\"reason\":\"" + escape(reason) + "\"}", tenantId);
        recordAudit(tenantId, "SESSION_CANCELLED", userId, userRole, sessionId, "Cancelled session: " + reason);
        logger.info("[ACD-06] Session {} cancelled by actor {}", sessionId, userId);
        return session;
    }

    // =========================================================================
    // 2. Attendance Marking & Roster Validation (Stories 7-15)
    // =========================================================================

    public MarkSummaryResponse markAttendance(String sessionId, MarkAttendanceRequest req, String tenantId, String userId, String userRole) {
        if (req == null || req.records == null || req.records.isEmpty()) {
            throw new AttendanceBadRequestException("Attendance records list cannot be empty");
        }

        AttendanceSession session = getSession(sessionId, tenantId);

        // Session must be OPEN (Story 8, 25)
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new InvalidLifecycleStateException("Attendance cannot be marked for session " + sessionId +
                    " with status " + session.getStatus() + ". Only OPEN sessions can be marked.");
        }

        // Marking window enforcement (Story 12)
        if (markingWindowEnforced && isWindowExpired(session)) {
            throw new AttendanceWindowExpiredException("Marking window has expired for session " + sessionId);
        }

        // Validate batch roster from ACD-04 (Story 9)
        BatchRosterRef roster = batchRosters.get(session.getBatchId());
        Set<String> validStudentIds = (roster != null) ? roster.studentIds : Collections.emptySet();

        // Atomic pre-validation: check every record before any writes (FR-06)
        Set<String> seenStudentsInBatch = new HashSet<>();
        for (RecordItem item : req.records) {
            if (item.studentId == null || item.studentId.trim().isEmpty()) {
                throw new AttendanceBadRequestException("studentId cannot be blank");
            }
            if (!validStudentIds.contains(item.studentId)) {
                throw new StudentNotInBatchException("Student '" + item.studentId +
                        "' does not belong to session batch '" + session.getBatchId() + "'");
            }
            if (!seenStudentsInBatch.add(item.studentId)) {
                throw new DuplicateAttendanceRecordException("Duplicate student entry '" + item.studentId + "' in marking request");
            }

            // Status catalog check (Story 10)
            String statusUpper = item.status != null ? item.status.toUpperCase() : "PRESENT";
            if (!statusCatalog.containsKey(statusUpper)) {
                throw new InvalidAttendanceStatusException("Invalid attendance status '" + item.status +
                        "'. Allowed values: " + statusCatalog.keySet());
            }

            // Check if student already has a record in this session (Story 11)
            String recordKey = tenantId + ":" + sessionId + ":" + item.studentId;
            if (recordUniqueIndex.containsKey(recordKey)) {
                throw new DuplicateAttendanceRecordException("Attendance already marked for student '" + item.studentId +
                        "' in session '" + sessionId + "'. Use the correction flow to adjust outcomes.");
            }
        }

        // Persist records atomically
        int pCount = 0;
        int aCount = 0;
        int lCount = 0;
        int lateCount = 0;

        for (RecordItem item : req.records) {
            String recordId = "ATT-REC-" + recordIdSeq.incrementAndGet();
            AttendanceStatus statusEnum = AttendanceStatus.valueOf(item.status.toUpperCase());

            AttendanceRecord record = new AttendanceRecord(
                    recordId, tenantId, sessionId, item.studentId, statusEnum,
                    item.statusReason, userId, CaptureSource.FACULTY
            );
            records.put(recordId, record);
            recordUniqueIndex.put(tenantId + ":" + sessionId + ":" + item.studentId, recordId);

            if (statusEnum == AttendanceStatus.PRESENT || statusEnum == AttendanceStatus.ON_DUTY) pCount++;
            else if (statusEnum == AttendanceStatus.ABSENT) aCount++;
            else if (statusEnum == AttendanceStatus.LEAVE) lCount++;
            else if (statusEnum == AttendanceStatus.LATE) {
                pCount++;
                lateCount++;
            }
        }

        // Update session counters
        session.setPresentCount(session.getPresentCount() + pCount);
        session.setAbsentCount(session.getAbsentCount() + aCount);
        session.setLeaveCount(session.getLeaveCount() + lCount);
        session.setLateCount(session.getLateCount() + lateCount);
        session.setMarkedBy(userId);
        session.setMarkedAt(System.currentTimeMillis());
        session.setVersion(session.getVersion() + 1);
        session.setUpdatedAt(System.currentTimeMillis());

        // Recalculate student summaries & evaluate shortage (Story 30, 36)
        List<String> shortageStudents = new ArrayList<>();
        for (RecordItem item : req.records) {
            AttendanceSummary sum = recalculateStudentSummary(tenantId, item.studentId, session.getSubjectId(), session.getBatchId());
            if (sum.isShortageFlag()) {
                shortageStudents.add(item.studentId);
            }
        }

        // Outbox event
        publishOutboxEvent("AttendanceMarked", sessionId,
                "{\"sessionId\":\"" + sessionId + "\",\"markedCount\":" + req.records.size() +
                        ",\"presentCount\":" + session.getPresentCount() + ",\"absentCount\":" + session.getAbsentCount() + "}", tenantId);

        recordAudit(tenantId, "ATTENDANCE_MARKED", userId, userRole, sessionId,
                "Marked attendance for " + req.records.size() + " students in session " + sessionId);

        return new MarkSummaryResponse(sessionId, req.records.size(), session.getPresentCount(),
                session.getAbsentCount(), session.getLeaveCount(), shortageStudents);
    }

    // =========================================================================
    // 3. Attendance Corrections & Audit Trail (Stories 17-23)
    // =========================================================================

    public AttendanceCorrection correctAttendance(String sessionId, String studentId, CorrectionRequest req,
                                                  String tenantId, String userId, String userRole) {
        if (req == null) {
            throw new AttendanceBadRequestException("Correction request cannot be null");
        }
        if (req.reason == null || req.reason.trim().isEmpty()) {
            throw new AttendanceBadRequestException("Mandatory correction reason must be provided (FR-07)");
        }
        if (req.newStatus == null || req.newStatus.trim().isEmpty()) {
            throw new AttendanceBadRequestException("newStatus is required");
        }

        AttendanceSession session = getSession(sessionId, tenantId);

        // RBAC enforcement for corrections (Story 18): Faculty within assignment or Academic Admin
        if ("FACULTY".equalsIgnoreCase(userRole)) {
            TimetableSlotRef slot = activeTimetableSlots.get(session.getTimetableEntryId());
            if (slot != null && slot.facultyId != null && !slot.facultyId.equalsIgnoreCase(userId)) {
                throw new AttendanceCorrectionForbiddenException("Faculty " + userId + " lacks correction authority for session " + sessionId);
            }
        } else if (!"ACADEMIC_ADMIN".equalsIgnoreCase(userRole) && !"SUPER_ADMIN".equalsIgnoreCase(userRole)) {
            throw new AttendanceCorrectionForbiddenException("Role '" + userRole + "' is not permitted to correct attendance records");
        }

        // Find existing record
        String recordKey = tenantId + ":" + sessionId + ":" + studentId;
        String recordId = recordUniqueIndex.get(recordKey);
        if (recordId == null) {
            throw new AttendanceNotFoundException("No existing attendance record for student " + studentId + " in session " + sessionId);
        }
        AttendanceRecord record = records.get(recordId);

        // Optimistic concurrency check (Story 20)
        if (req.expectedVersion != null && req.expectedVersion != record.getRecordVersion()) {
            throw new AttendanceVersionConflictException("Record version conflict for student " + studentId +
                    ": expected " + req.expectedVersion + " but record is version " + record.getRecordVersion());
        }

        // Validate new status
        String statusUpper = req.newStatus.toUpperCase();
        if (!statusCatalog.containsKey(statusUpper)) {
            throw new InvalidAttendanceStatusException("Invalid correction status '" + req.newStatus + "'");
        }
        AttendanceStatus newStatusEnum = AttendanceStatus.valueOf(statusUpper);
        AttendanceStatus oldStatusEnum = record.getStatus();

        // Append-only correction log (Story 17, 19)
        String corrId = "ATT-CORR-" + correctionIdSeq.incrementAndGet();
        AttendanceCorrection corr = new AttendanceCorrection();
        corr.setId(corrId);
        corr.setTenantId(tenantId);
        corr.setSessionId(sessionId);
        corr.setStudentId(studentId);
        corr.setRecordId(recordId);
        corr.setOldStatus(oldStatusEnum);
        corr.setNewStatus(newStatusEnum);
        corr.setReason(req.reason.trim());
        corr.setCorrectedBy(userId);
        corr.setAuthorizationScope(userRole);
        corr.setPreviousVersion(record.getRecordVersion());
        corr.setNewVersion(record.getRecordVersion() + 1);
        corr.setCorrelationId(LogContext.getTraceId());
        corr.setWorkflowStatus(CorrectionWorkflowStatus.APPROVED);

        corrections.add(corr);

        // Apply change to record
        record.setStatus(newStatusEnum);
        record.setRecordVersion(record.getRecordVersion() + 1);
        record.setUpdatedAt(System.currentTimeMillis());

        // Update session state & counters
        session.setStatus(SessionStatus.CORRECTED);
        recomputeSessionCounters(session);
        session.setVersion(session.getVersion() + 1);
        session.setUpdatedAt(System.currentTimeMillis());

        // Recalculate summary & check shortage
        recalculateStudentSummary(tenantId, studentId, session.getSubjectId(), session.getBatchId());

        publishOutboxEvent("AttendanceCorrected", sessionId,
                "{\"sessionId\":\"" + sessionId + "\",\"studentId\":\"" + studentId +
                        "\",\"oldStatus\":\"" + oldStatusEnum + "\",\"newStatus\":\"" + newStatusEnum +
                        "\",\"reason\":\"" + escape(req.reason) + "\"}", tenantId);

        recordAudit(tenantId, "ATTENDANCE_CORRECTED", userId, userRole, sessionId,
                "Corrected student " + studentId + " from " + oldStatusEnum + " to " + newStatusEnum);

        logger.info("[ACD-06] Attendance corrected for student {} in session {}: {} -> {}", studentId, sessionId, oldStatusEnum, newStatusEnum);
        return corr;
    }

    public List<AttendanceCorrection> getSessionCorrections(String sessionId, String tenantId) {
        return corrections.stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getSessionId().equals(sessionId))
                .collect(Collectors.toList());
    }

    public List<AttendanceCorrection> getStudentCorrections(String studentId, String tenantId) {
        return corrections.stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getStudentId().equals(studentId))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 4. Lifecycle Governance (Stories 24-28)
    // =========================================================================

    public AttendanceSession submitSession(String sessionId, String tenantId, String userId, String userRole) {
        AttendanceSession session = getSession(sessionId, tenantId);

        if (session.getStatus() != SessionStatus.OPEN && session.getStatus() != SessionStatus.CORRECTED) {
            throw new InvalidLifecycleStateException("Cannot submit session " + sessionId + " in state " + session.getStatus());
        }

        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedBy(userId);
        session.setSubmittedAt(System.currentTimeMillis());
        session.setVersion(session.getVersion() + 1);
        session.setUpdatedAt(System.currentTimeMillis());

        publishOutboxEvent("AttendanceSessionSubmitted", sessionId, serializeSession(session), tenantId);
        recordAudit(tenantId, "SESSION_SUBMITTED", userId, userRole, sessionId, "Submitted session");
        return session;
    }

    public AttendanceSession lockSession(String sessionId, String tenantId, String userId, String userRole) {
        AttendanceSession session = getSession(sessionId, tenantId);

        if (session.getStatus() != SessionStatus.SUBMITTED && session.getStatus() != SessionStatus.CORRECTED) {
            throw new InvalidLifecycleStateException("Only SUBMITTED or CORRECTED sessions can be locked. Current state: " + session.getStatus());
        }

        session.setStatus(SessionStatus.LOCKED);
        session.setLockedBy(userId);
        session.setLockedAt(System.currentTimeMillis());
        session.setVersion(session.getVersion() + 1);
        session.setUpdatedAt(System.currentTimeMillis());

        publishOutboxEvent("AttendanceSessionLocked", sessionId, serializeSession(session), tenantId);
        recordAudit(tenantId, "SESSION_LOCKED", userId, userRole, sessionId, "Locked session");
        return session;
    }

    public AttendanceSession archiveSession(String sessionId, String tenantId, String userId, String userRole) {
        AttendanceSession session = getSession(sessionId, tenantId);

        if (session.getStatus() != SessionStatus.LOCKED && session.getStatus() != SessionStatus.CORRECTED) {
            throw new InvalidLifecycleStateException("Only LOCKED or CORRECTED sessions can be archived. Current state: " + session.getStatus());
        }

        session.setStatus(SessionStatus.ARCHIVED);
        session.setVersion(session.getVersion() + 1);
        session.setUpdatedAt(System.currentTimeMillis());

        publishOutboxEvent("AttendanceSessionArchived", sessionId, serializeSession(session), tenantId);
        recordAudit(tenantId, "SESSION_ARCHIVED", userId, userRole, sessionId, "Archived completed session");
        return session;
    }

    public int closeEndOfDaySessions(String tenantId, String date, String userId) {
        List<AttendanceSession> openSessions = sessions.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId))
                .filter(s -> s.getStatus() == SessionStatus.OPEN || s.getStatus() == SessionStatus.SUBMITTED)
                .filter(s -> date == null || s.getAttendanceDate().compareTo(date) <= 0)
                .collect(Collectors.toList());

        for (AttendanceSession s : openSessions) {
            s.setStatus(SessionStatus.LOCKED);
            s.setLockedBy("SYSTEM-EOD");
            s.setLockedAt(System.currentTimeMillis());
            s.setVersion(s.getVersion() + 1);
            s.setUpdatedAt(System.currentTimeMillis());
            publishOutboxEvent("AttendanceSessionClosed", s.getId(), "{\"sessionId\":\"" + s.getId() + "\",\"reason\":\"END_OF_DAY_AUTO_CLOSE\"}", tenantId);
        }

        recordAudit(tenantId, "EOD_SESSIONS_CLOSED", userId, "SYSTEM", "ALL", "Closed " + openSessions.size() + " sessions at end-of-day");
        return openSessions.size();
    }

    // =========================================================================
    // 5. Summaries & Shortage Engine (Stories 30-39)
    // =========================================================================

    public AttendanceSummary recalculateStudentSummary(String tenantId, String studentId, String subjectId, String batchId) {
        String termId = "TERM-2026-FALL";
        String summaryKey = tenantId + ":" + studentId + ":" + subjectId + ":" + termId;

        AttendanceSummary sum = summaries.computeIfAbsent(summaryKey, k -> {
            AttendanceSummary s = new AttendanceSummary();
            s.setId("ATT-SUM-" + summaryIdSeq.incrementAndGet());
            s.setTenantId(tenantId);
            s.setStudentId(studentId);
            s.setSubjectId(subjectId);
            s.setTermId(termId);
            s.setBatchId(batchId);
            s.setShortageThreshold(defaultShortageThreshold);
            return s;
        });

        // Compute counts from all authoritative, non-cancelled records
        int total = 0;
        int present = 0;
        int absent = 0;
        int leave = 0;
        int late = 0;

        for (AttendanceRecord rec : records.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getStudentId().equals(studentId)) {
                AttendanceSession sess = sessions.get(rec.getSessionId());
                if (sess != null && sess.getStatus() != SessionStatus.CANCELLED && sess.getSubjectId().equals(subjectId)) {
                    total++;
                    if (rec.getStatus() == AttendanceStatus.PRESENT || rec.getStatus() == AttendanceStatus.ON_DUTY) {
                        present++;
                    } else if (rec.getStatus() == AttendanceStatus.ABSENT) {
                        absent++;
                    } else if (rec.getStatus() == AttendanceStatus.LEAVE) {
                        leave++;
                    } else if (rec.getStatus() == AttendanceStatus.LATE) {
                        present++;
                        late++;
                    }
                }
            }
        }

        double pct = (total > 0) ? ((double) present * 100.0) / total : 0.0;
        boolean wasShortage = sum.isShortageFlag();
        boolean isShortage = (total > 0) && (pct < sum.getShortageThreshold());

        sum.setTotalSessions(total);
        sum.setPresentCount(present);
        sum.setAbsentCount(absent);
        sum.setLeaveCount(leave);
        sum.setLateCount(late);
        sum.setAttendancePercentage(Math.round(pct * 10.0) / 10.0);
        sum.setShortageFlag(isShortage);
        sum.setCalculatedAt(System.currentTimeMillis());
        sum.setSourceVersion(sum.getSourceVersion() + 1);

        // Story 37: Emit AttendanceShortageDetected ONLY on false -> true transition!
        if (!wasShortage && isShortage) {
            publishOutboxEvent("AttendanceShortageDetected", sum.getId(),
                    "{\"studentId\":\"" + studentId + "\",\"subjectId\":\"" + subjectId +
                            "\",\"percentage\":" + sum.getAttendancePercentage() +
                            ",\"threshold\":" + sum.getShortageThreshold() + "}", tenantId);
            logger.warn("[ACD-06 Shortage] Student {} fell below threshold: {}% < {}%", studentId, sum.getAttendancePercentage(), sum.getShortageThreshold());
        }

        return sum;
    }

    public void recalculateSummariesForBatch(String tenantId, String batchId, String subjectId) {
        BatchRosterRef roster = batchRosters.get(batchId);
        if (roster != null) {
            for (String sId : roster.studentIds) {
                recalculateStudentSummary(tenantId, sId, subjectId, batchId);
            }
        }
    }

    public int reconcileAllSummaries(String tenantId) {
        int count = 0;
        for (AttendanceSummary sum : summaries.values()) {
            if (sum.getTenantId().equals(tenantId)) {
                recalculateStudentSummary(tenantId, sum.getStudentId(), sum.getSubjectId(), sum.getBatchId());
                sum.setReconciledAt(System.currentTimeMillis());
                count++;
            }
        }
        return count;
    }

    public List<AttendanceSummary> getStudentSummaries(String studentId, String tenantId) {
        return summaries.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId) && s.getStudentId().equals(studentId))
                .collect(Collectors.toList());
    }

    public List<AttendanceSummary> getBatchSummaries(String batchId, String tenantId) {
        return summaries.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId) && batchId.equalsIgnoreCase(s.getBatchId()))
                .collect(Collectors.toList());
    }

    public List<AttendanceSummary> getShortageList(String batchId, String tenantId) {
        return summaries.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId))
                .filter(s -> batchId == null || batchId.equalsIgnoreCase(s.getBatchId()))
                .filter(AttendanceSummary::isShortageFlag)
                .collect(Collectors.toList());
    }

    public List<AttendanceRecord> getStudentHistory(String studentId, String tenantId) {
        return records.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getStudentId().equals(studentId))
                .sorted(Comparator.comparing(AttendanceRecord::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 6. Reporting & Export (Stories 40-44)
    // =========================================================================

    public Map<String, Object> generateReport(String tenantId, String batchId, String subjectId, String fromDate, String toDate) {
        List<AttendanceSession> matchedSessions = sessions.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId))
                .filter(s -> batchId == null || s.getBatchId().equalsIgnoreCase(batchId))
                .filter(s -> subjectId == null || s.getSubjectId().equalsIgnoreCase(subjectId))
                .filter(s -> fromDate == null || s.getAttendanceDate().compareTo(fromDate) >= 0)
                .filter(s -> toDate == null || s.getAttendanceDate().compareTo(toDate) <= 0)
                .collect(Collectors.toList());

        int totalSessions = matchedSessions.size();
        int totalPresent = matchedSessions.stream().mapToInt(AttendanceSession::getPresentCount).sum();
        int totalAbsent = matchedSessions.stream().mapToInt(AttendanceSession::getAbsentCount).sum();

        Map<String, Object> report = new HashMap<>();
        report.put("tenantId", tenantId);
        report.put("batchId", batchId != null ? batchId : "ALL");
        report.put("subjectId", subjectId != null ? subjectId : "ALL");
        report.put("totalSessions", totalSessions);
        report.put("totalPresent", totalPresent);
        report.put("totalAbsent", totalAbsent);
        report.put("sessions", matchedSessions);
        return report;
    }

    public Map<String, Object> exportAttendance(String tenantId, String batchId, String format, String userId, String userRole) {
        String fmt = (format != null) ? format.toUpperCase() : "PDF";
        Map<String, Object> export = new HashMap<>();
        export.put("exportId", "EXP-" + UUID.randomUUID().toString().substring(0, 8));
        export.put("format", fmt);
        export.put("tenantId", tenantId);
        export.put("batchId", batchId);
        export.put("exportedAt", System.currentTimeMillis());
        export.put("exportedBy", userId);
        export.put("totalBatchesExported", 1);
        export.put("downloadUrl", "/api/v1/academics/attendance/export/downloads/" + export.get("exportId") + "." + fmt.toLowerCase());

        publishOutboxEvent("AttendanceExported", (String) export.get("exportId"), "{\"format\":\"" + fmt + "\"}", tenantId);
        recordAudit(tenantId, "ATTENDANCE_EXPORTED", userId, userRole, batchId, "Exported attendance report in " + fmt);
        return export;
    }

    // =========================================================================
    // 7. Bulk Import & Device Integration (Stories 45-48)
    // =========================================================================

    public Map<String, Object> bulkImport(List<BulkImportRow> rows, String tenantId, String userId, String userRole) {
        if (rows == null || rows.isEmpty()) {
            throw new AttendanceBadRequestException("Import rows cannot be empty");
        }

        int successCount = 0;
        int failureCount = 0;
        List<Map<String, Object>> rowResults = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkImportRow r = rows.get(i);
            Map<String, Object> res = new HashMap<>();
            res.put("rowNo", i + 1);
            res.put("studentId", r.studentId);

            try {
                // Find or resolve session
                String uniqueKey = tenantId + ":" + r.batchId + ":" + r.subjectId + ":" + r.attendanceDate + ":" + r.periodNo;
                String sessId = sessionUniqueIndex.get(uniqueKey);
                if (sessId == null) {
                    throw new AttendanceNotFoundException("No matching session for batch " + r.batchId + " date " + r.attendanceDate);
                }

                // Check duplicate
                String recordKey = tenantId + ":" + sessId + ":" + r.studentId;
                if (recordUniqueIndex.containsKey(recordKey)) {
                    throw new DuplicateAttendanceRecordException("Duplicate record for student " + r.studentId);
                }

                AttendanceStatus st = AttendanceStatus.valueOf(r.status.toUpperCase());
                String recId = "ATT-REC-IMP-" + recordIdSeq.incrementAndGet();
                AttendanceRecord record = new AttendanceRecord(recId, tenantId, sessId, r.studentId, st, r.statusReason, userId, CaptureSource.IMPORT);
                records.put(recId, record);
                recordUniqueIndex.put(recordKey, recId);

                AttendanceSession sess = sessions.get(sessId);
                if (st == AttendanceStatus.PRESENT || st == AttendanceStatus.ON_DUTY) sess.setPresentCount(sess.getPresentCount() + 1);
                else if (st == AttendanceStatus.ABSENT) sess.setAbsentCount(sess.getAbsentCount() + 1);

                recalculateStudentSummary(tenantId, r.studentId, r.subjectId, r.batchId);

                res.put("status", "SUCCESS");
                successCount++;
            } catch (Exception ex) {
                res.put("status", "FAILED");
                res.put("error", ex.getMessage());
                failureCount++;
            }
            rowResults.add(res);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalRows", rows.size());
        response.put("successCount", successCount);
        response.put("failureCount", failureCount);
        response.put("results", rowResults);
        return response;
    }

    public Map<String, Object> deviceCapture(DeviceCaptureRequest req, String tenantId) {
        if (req == null || req.studentId == null || req.deviceId == null) {
            throw new AttendanceBadRequestException("deviceId and studentId are required for device capture");
        }

        // Map to active session or buffer capture
        Map<String, Object> result = new HashMap<>();
        result.put("captureId", "CAP-" + UUID.randomUUID().toString().substring(0, 8));
        result.put("deviceId", req.deviceId);
        result.put("studentId", req.studentId);
        result.put("source", "BIOMETRIC");
        result.put("status", "CAPTURED");
        result.put("timestamp", req.timestamp);

        publishOutboxEvent("AttendanceDeviceCaptured", (String) result.get("captureId"),
                "{\"studentId\":\"" + req.studentId + "\",\"deviceId\":\"" + req.deviceId + "\"}", tenantId);
        return result;
    }

    // =========================================================================
    // 8. Event Ingestion & Outbox Handling (Stories 49-57)
    // =========================================================================

    public boolean consumeEvent(Map<String, Object> event) {
        if (event == null) return false;
        String eventId = (String) event.get("eventId");
        String eventType = (String) event.get("eventType");

        // Idempotent deduplication (Story 55)
        if (eventId != null && !processedEventIds.add(eventId)) {
            logger.info("[ACD-06] Duplicate event skipped: {}", eventId);
            return true;
        }

        try {
            if ("TimetablePublished".equalsIgnoreCase(eventType) ||
                    "TimetableChanged".equalsIgnoreCase(eventType) ||
                    "TimetableSuperseded".equalsIgnoreCase(eventType)) {
                handleTimetableEvent(event);
            } else if ("BatchCreated".equalsIgnoreCase(eventType) || "BatchUpdated".equalsIgnoreCase(eventType)) {
                handleBatchEvent(event);
            } else if ("BatchSplit".equalsIgnoreCase(eventType) || "BatchMerged".equalsIgnoreCase(eventType)) {
                handleBatchSplitMergeEvent(event);
            } else if ("StudentAddedToBatch".equalsIgnoreCase(eventType)) {
                handleStudentAddedEvent(event);
            } else if ("StudentStatusChanged".equalsIgnoreCase(eventType)) {
                handleStudentStatusEvent(event);
            } else if ("AcademicCalendarPublished".equalsIgnoreCase(eventType)) {
                handleCalendarEvent(event);
            } else {
                logger.info("[ACD-06] Unhandled event type ignored: {}", eventType);
            }
            return true;
        } catch (Exception ex) {
            logger.error("[ACD-06] Error processing event {}: {}", eventId, ex.getMessage(), ex);
            routeToDlq(eventId, eventType, (String) event.get("source"), event.toString(), ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleTimetableEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        String entryId = (String) event.get("entityId");
        if (data != null && data.containsKey("timetableEntryId")) {
            entryId = (String) data.get("timetableEntryId");
        }
        if (entryId != null) {
            String bId = data != null && data.containsKey("batchId") ? (String) data.get("batchId") : "BATCH-001";
            String sId = data != null && data.containsKey("subjectId") ? (String) data.get("subjectId") : "SUB-101";
            String fId = data != null && data.containsKey("facultyId") ? (String) data.get("facultyId") : "FAC-001";
            activeTimetableSlots.put(entryId, new TimetableSlotRef(entryId, bId, sId, fId, "MONDAY", "09:00", "10:00", 1));
            logger.info("[ACD-06] Ingested timetable slot update: {}", entryId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBatchEvent(Map<String, Object> event) {
        String batchId = (String) event.get("entityId");
        if (batchId != null) {
            batchRosters.putIfAbsent(batchId, new BatchRosterRef(batchId, "TENANT-001", new HashSet<>()));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBatchSplitMergeEvent(Map<String, Object> event) {
        // Story 57: Keep attendance continuity during batch split or merge
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        if (data != null && data.containsKey("reassignmentMap")) {
            Map<String, String> reassignment = (Map<String, String>) data.get("reassignmentMap");
            for (Map.Entry<String, String> e : reassignment.entrySet()) {
                String studentId = e.getKey();
                String newBatchId = e.getValue();
                BatchRosterRef newRoster = batchRosters.computeIfAbsent(newBatchId, k -> new BatchRosterRef(newBatchId, "TENANT-001", new HashSet<>()));
                newRoster.studentIds.add(studentId);

                // Update summary batchId without losing historical percentages
                for (AttendanceSummary s : summaries.values()) {
                    if (s.getStudentId().equals(studentId)) {
                        s.setBatchId(newBatchId);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleStudentAddedEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        if (data != null) {
            String bId = (String) data.get("batchId");
            String sId = (String) data.get("studentId");
            if (bId != null && sId != null) {
                BatchRosterRef roster = batchRosters.computeIfAbsent(bId, k -> new BatchRosterRef(bId, "TENANT-001", new HashSet<>()));
                roster.studentIds.add(sId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleStudentStatusEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        if (data != null) {
            String sId = (String) data.get("studentId");
            String status = (String) data.get("status");
            if (sId != null && status != null) {
                studentEligibility.put(sId, "ACTIVE".equalsIgnoreCase(status) || "ENROLLED".equalsIgnoreCase(status));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCalendarEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        if (data != null && data.containsKey("holidays")) {
            List<String> list = (List<String>) data.get("holidays");
            if (list != null) holidays.addAll(list);
        }
    }

    // =========================================================================
    // Technical Helpers: Outbox, Idempotency, DLQ, Audit
    // =========================================================================

    public OutboxEvent publishOutboxEvent(String eventType, String entityId, String payload, String tenantId) {
        OutboxEvent evt = new OutboxEvent();
        evt.setId("EVT-ACD06-" + outboxIdSeq.incrementAndGet());
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType(eventType);
        evt.setSource("ACD-06-AttendanceService");
        evt.setOccurredAt(System.currentTimeMillis());
        evt.setEntityType("ATTENDANCE");
        evt.setEntityId(entityId);
        evt.setVersion(1);
        evt.setCorrelationId(LogContext.getTraceId());
        evt.setPayload(payload);
        evt.setStatus("PENDING");

        outbox.put(evt.getId(), evt);
        return evt;
    }

    public void routeToDlq(String eventId, String eventType, String source, String payload, String error) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId("DLQ-" + dlqIdSeq.incrementAndGet());
        dlq.setEventId(eventId);
        dlq.setEventType(eventType);
        dlq.setSource(source);
        dlq.setPayload(payload);
        dlq.setFailureCode("CONSUME_ERROR");
        dlq.setFailureReason(error);
        dlq.setAttemptCount(3);
        dlq.setCreatedAt(System.currentTimeMillis());
        deadLetterQueue.add(dlq);
    }

    public void saveIdempotency(String key, String tenantId, String hash, int status, String body) {
        idempotency.put(tenantId + ":" + key, new IdempotencyRecord(key, tenantId, hash, status, body));
    }

    public IdempotencyRecord getIdempotency(String key, String tenantId) {
        return idempotency.get(tenantId + ":" + key);
    }

    public void recordAudit(String tenantId, String action, String actorId, String actorRole, String resourceId, String details) {
        auditLogs.add(new AttendanceAuditLog(tenantId, action, actorId, actorRole, resourceId, details, LogContext.getTraceId()));
    }

    public List<AttendanceAuditLog> getAuditLogs() {
        return new ArrayList<>(auditLogs);
    }

    public ApiKeyRecord validateApiKey(String key, String tenantId) {
        ApiKeyRecord rec = apiKeys.get(key);
        if (rec != null && rec.isActive() && rec.getTenantId().equals(tenantId)) {
            return rec;
        }
        return null;
    }

    public void registerApiKey(String key, String tenantId, String keyId) {
        apiKeys.put(key, new ApiKeyRecord(keyId, tenantId, key));
    }

    public int relayPendingOutboxEvents() {
        int count = 0;
        for (OutboxEvent evt : outbox.values()) {
            if ("PENDING".equals(evt.getStatus())) {
                evt.setStatus("PUBLISHED");
                count++;
            }
        }
        return count;
    }

    private void recomputeSessionCounters(AttendanceSession session) {
        int p = 0, a = 0, l = 0, lt = 0;
        for (AttendanceRecord r : records.values()) {
            if (r.getSessionId().equals(session.getId())) {
                if (r.getStatus() == AttendanceStatus.PRESENT || r.getStatus() == AttendanceStatus.ON_DUTY) p++;
                else if (r.getStatus() == AttendanceStatus.ABSENT) a++;
                else if (r.getStatus() == AttendanceStatus.LEAVE) l++;
                else if (r.getStatus() == AttendanceStatus.LATE) { p++; lt++; }
            }
        }
        session.setPresentCount(p);
        session.setAbsentCount(a);
        session.setLeaveCount(l);
        session.setLateCount(lt);
    }

    private boolean isWindowExpired(AttendanceSession session) {
        try {
            LocalDate date = LocalDate.parse(session.getAttendanceDate());
            LocalDate today = LocalDate.now();
            return date.isBefore(today.minusDays(1));
        } catch (Exception e) {
            return false;
        }
    }

    private String serializeSession(AttendanceSession s) {
        return "{\"sessionId\":\"" + s.getId() + "\",\"batchId\":\"" + s.getBatchId() +
                "\",\"subjectId\":\"" + s.getSubjectId() + "\",\"attendanceDate\":\"" + s.getAttendanceDate() +
                "\",\"periodNo\":" + s.getPeriodNo() + ",\"status\":\"" + s.getStatus() + "\"}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================================
    // Inspection & Metric Counters
    // =========================================================================

    public int getSessionCount() { return sessions.size(); }
    public int getRecordCount() { return records.size(); }
    public int getShortageCount() {
        return (int) summaries.values().stream().filter(AttendanceSummary::isShortageFlag).count();
    }
    public int getPendingOutboxCount() {
        return (int) outbox.values().stream().filter(e -> "PENDING".equals(e.getStatus())).count();
    }
    public int getDlqCount() { return deadLetterQueue.size(); }

    // Test Control Knobs
    public void setFailClosedOnTimetableOutage(boolean v) { this.failClosedOnTimetableOutage = v; }
    public void setFailClosedOnRosterOutage(boolean v) { this.failClosedOnRosterOutage = v; }
    public void setMarkingWindowEnforced(boolean v) { this.markingWindowEnforced = v; }
    public void setDefaultShortageThreshold(double v) { this.defaultShortageThreshold = v; }
    public void addTimetableSlot(String slotId, String batchId, String subjectId, String facultyId) {
        activeTimetableSlots.put(slotId, new TimetableSlotRef(slotId, batchId, subjectId, facultyId, "MONDAY", "09:00", "10:00", 1));
    }
    public void addBatchRoster(String batchId, String tenantId, List<String> studentIds) {
        batchRosters.put(batchId, new BatchRosterRef(batchId, tenantId, studentIds));
    }
    public List<StatusCatalogEntry> getStatusCatalog() {
        return new ArrayList<>(statusCatalog.values());
    }

    public void addHoliday(String date) { holidays.add(date); }
}
