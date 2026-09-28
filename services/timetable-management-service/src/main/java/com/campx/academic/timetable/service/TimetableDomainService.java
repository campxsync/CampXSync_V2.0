package com.campx.academic.timetable.service;

import com.campx.academic.timetable.exception.*;
import com.campx.academic.timetable.model.TimetableModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Core Domain Service for ACD-05: Timetable Management Service.
 * Implements authoritative weekly timetable lifecycle, entry management,
 * deterministic conflict detection engine, immutable versioning, outbox event relay,
 * reference validation, and RBAC / sensitivity governance.
 */
public class TimetableDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TimetableDomainService.class);

    // =========================================================================
    // 1. Data Collections (Thread-safe MongoDB in-memory representations)
    // =========================================================================
    private final Map<String, Timetable> timetables = new ConcurrentHashMap<>();
    private final Map<String, TimetableEntry> timetableEntries = new ConcurrentHashMap<>();
    private final Map<String, TimetableVersion> timetableVersions = new ConcurrentHashMap<>();
    private final Map<String, List<ConflictResult>> conflictResults = new ConcurrentHashMap<>();
    private final Map<String, OutboxEvent> outboxEvents = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final Map<String, DeadLetterEvent> deadLetterEvents = new ConcurrentHashMap<>();
    private final List<TimetableAuditLog> auditLogs = new CopyOnWriteArrayList<>();
    private final Map<String, WeeklyTemplate> weeklyTemplates = new ConcurrentHashMap<>();
    private final Map<String, ApiKeyRecord> apiKeys = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<>());

    // =========================================================================
    // 2. Upstream Reference Registries (ACD-04, ACD-03, HRM, Facilities, ACD-07)
    // =========================================================================
    public static class BatchRef {
        public String batchId;
        public String tenantId;
        public String departmentId;
        public String programId;
        public boolean active = true;

        public BatchRef(String batchId, String tenantId, String departmentId, String programId) {
            this.batchId = batchId;
            this.tenantId = tenantId;
            this.departmentId = departmentId;
            this.programId = programId;
        }
    }

    public static class SubjectRef {
        public String subjectId;
        public String tenantId;
        public String departmentId;
        public boolean active = true;

        public SubjectRef(String subjectId, String tenantId, String departmentId) {
            this.subjectId = subjectId;
            this.tenantId = tenantId;
            this.departmentId = departmentId;
        }
    }

    public static class FacultyRef {
        public String facultyId;
        public String tenantId;
        public String departmentId;
        public boolean hasLabRights = false;
        public boolean active = true;

        public FacultyRef(String facultyId, String tenantId, String departmentId, boolean hasLabRights) {
            this.facultyId = facultyId;
            this.tenantId = tenantId;
            this.departmentId = departmentId;
            this.hasLabRights = hasLabRights;
        }
    }

    public static class RoomRef {
        public String roomId;
        public String tenantId;
        public int capacity = 60;
        public boolean active = true;

        public RoomRef(String roomId, String tenantId, int capacity) {
            this.roomId = roomId;
            this.tenantId = tenantId;
            this.capacity = capacity;
        }
    }

    public static class CalendarWindow {
        public String startDate;
        public String endDate;

        public CalendarWindow(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    private final Map<String, BatchRef> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, SubjectRef> activeSubjects = new ConcurrentHashMap<>();
    private final Map<String, FacultyRef> activeFaculty = new ConcurrentHashMap<>();
    private final Map<String, RoomRef> activeRooms = new ConcurrentHashMap<>();
    private final Map<String, CalendarWindow> calendarWindows = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> facultyUnavailability = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomUnavailability = new ConcurrentHashMap<>();

    public TimetableDomainService() {
        seedDefaults();
    }

    /**
     * Seeds initial reference data for seamless testing and standard sandbox operation.
     */
    private void seedDefaults() {
        String defaultTenant = "TENANT-001";

        // ACD-04 Batches
        activeBatches.put("BATCH-001", new BatchRef("BATCH-001", defaultTenant, "DEP-CSE", "PROG-BTECH-CSE"));
        activeBatches.put("BATCH-002", new BatchRef("BATCH-002", defaultTenant, "DEP-CSE", "PROG-BTECH-CSE"));
        activeBatches.put("BAT-2026-CS-A", new BatchRef("BAT-2026-CS-A", defaultTenant, "DEP-CSE", "PROG-BTECH-CSE"));
        activeBatches.put("BATCH-DIFF-TENANT", new BatchRef("BATCH-DIFF-TENANT", "TENANT-FOREIGN", "DEP-CSE", "PROG-BTECH-CSE"));

        // ACD-03 Subjects
        activeSubjects.put("SUB-201", new SubjectRef("SUB-201", defaultTenant, "DEP-CSE"));
        activeSubjects.put("SUB-202", new SubjectRef("SUB-202", defaultTenant, "DEP-CSE"));
        activeSubjects.put("CS101", new SubjectRef("CS101", defaultTenant, "DEP-CSE"));
        activeSubjects.put("CS201", new SubjectRef("CS201", defaultTenant, "DEP-CSE"));
        activeSubjects.put("SUB-DIFF-TENANT", new SubjectRef("SUB-DIFF-TENANT", "TENANT-FOREIGN", "DEP-CSE"));

        // HRM / Faculty (with lab rights flag, Story 36)
        activeFaculty.put("FAC-100", new FacultyRef("FAC-100", defaultTenant, "DEP-CSE", true));
        activeFaculty.put("FAC-200", new FacultyRef("FAC-200", defaultTenant, "DEP-CSE", false)); // No lab rights
        activeFaculty.put("FAC-300", new FacultyRef("FAC-300", defaultTenant, "DEP-CSE", true));
        activeFaculty.put("FAC-DIFF-TENANT", new FacultyRef("FAC-DIFF-TENANT", "TENANT-FOREIGN", "DEP-CSE", true));

        // Facilities / Rooms
        activeRooms.put("ROOM-12", new RoomRef("ROOM-12", defaultTenant, 60));
        activeRooms.put("LAB-01", new RoomRef("LAB-01", defaultTenant, 40));
        activeRooms.put("ROOM-101", new RoomRef("ROOM-101", defaultTenant, 75));
        activeRooms.put("ROOM-DIFF-TENANT", new RoomRef("ROOM-DIFF-TENANT", "TENANT-FOREIGN", 60));

        // ACD-07 Academic Calendar policy window (e.g. 2026-08-01 to 2026-12-31)
        calendarWindows.put(defaultTenant, new CalendarWindow("2026-08-01", "2026-12-31"));

        // API Keys (External Integrations, Story 50)
        apiKeys.put("API-KEY-LMS-001", new ApiKeyRecord("KEY-LMS", "API-KEY-LMS-001", defaultTenant));
    }

    // =========================================================================
    // 3. Timetable Draft & Identity Management (Stories 1-4)
    // =========================================================================

    public Timetable createDraft(CreateTimetableRequest req, String tenantId, String userId) {
        if (req == null) {
            throw new TimetableBadRequestException("Timetable creation payload is required");
        }
        if (req.timetableCode == null || req.timetableCode.trim().isEmpty()) {
            throw new TimetableBadRequestException("timetableCode is required");
        }
        if (req.name == null || req.name.trim().isEmpty()) {
            throw new TimetableBadRequestException("name is required");
        }
        if (req.academicYear == null || req.academicYear.trim().isEmpty()) {
            throw new TimetableBadRequestException("academicYear is required");
        }
        if (req.departmentId == null || req.departmentId.trim().isEmpty()) {
            throw new TimetableBadRequestException("departmentId is required");
        }

        // Duplicate code check within tenant
        for (Timetable existing : timetables.values()) {
            if (existing.getTenantId().equals(tenantId) && !existing.isDeleted() &&
                    existing.getTimetableCode().equalsIgnoreCase(req.timetableCode.trim())) {
                throw new TimetableConflictException("Timetable with code " + req.timetableCode + " already exists in this tenant");
            }
        }

        String id = "TT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long now = System.currentTimeMillis();

        Timetable tt = new Timetable();
        tt.setId(id);
        tt.setTenantId(tenantId);
        tt.setTimetableCode(req.timetableCode.trim());
        tt.setName(req.name.trim());
        tt.setAcademicYear(req.academicYear.trim());
        tt.setSemester(req.semester != null ? req.semester.trim() : "1");
        tt.setTermId(req.termId != null ? req.termId.trim() : tt.getSemester());
        tt.setDepartmentId(req.departmentId.trim());
        tt.setProgramId(req.programId != null ? req.programId.trim() : "PROG-DEFAULT");
        tt.setBatchId(req.batchId != null ? req.batchId.trim() : null);
        tt.setEffectiveFrom(req.effectiveFrom);
        tt.setEffectiveTo(req.effectiveTo);
        tt.setStatus(TimetableStatus.DRAFT);
        tt.setCurrentVersionNo(1);
        tt.setCreatedAt(now);
        tt.setUpdatedAt(now);
        tt.setCreatedBy(userId);
        tt.setUpdatedBy(userId);
        tt.setVersion(1L);

        timetables.put(id, tt);

        // Create Version 1 snapshot container
        TimetableVersion v1 = new TimetableVersion();
        v1.setId("TTV-" + id + "-v1");
        v1.setTenantId(tenantId);
        v1.setTimetableId(id);
        v1.setVersionNo(1);
        v1.setStatus(TimetableStatus.DRAFT);
        v1.setValidationStatus(ValidationStatus.NOT_VALIDATED);
        v1.setEffectiveFrom(req.effectiveFrom);
        v1.setEffectiveTo(req.effectiveTo);
        v1.setCreatedAt(now);
        timetableVersions.put(id + ":1", v1);

        // Emit TimetableCreated outbox event (Story 39, 40)
        recordOutboxEvent("TimetableCreated", id, id, MapBuilder.create()
                .put("timetableId", id)
                .put("timetableCode", tt.getTimetableCode())
                .put("status", tt.getStatus().name())
                .put("currentVersion", "v1")
                .put("versionNo", 1)
                .put("departmentId", tt.getDepartmentId())
                .put("academicYear", tt.getAcademicYear())
                .build());

        // Audit Trail (Story 51)
        recordAudit(tenantId, id, "TIMETABLE_CREATED", userId, "ACADEMIC_ADMIN", "Draft timetable created: " + tt.getTimetableCode());

        return tt;
    }

    public Timetable updateDraftMetadata(String id, UpdateTimetableRequest req, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(id, tenantId);

        // Immutable check (BR-05, Story 2)
        if (tt.getStatus() == TimetableStatus.PUBLISHED || tt.getStatus() == TimetableStatus.SUPERSEDED) {
            throw new TimetableImmutableException("Cannot modify published or superseded timetable: " + id);
        }

        // Optimistic concurrency check (BR-10, Story 35)
        if (req.version != null && !req.version.equals(tt.getVersion())) {
            throw new TimetableStaleVersionException("Stale timetable version. Current version: " + tt.getVersion() + ", submitted: " + req.version);
        }

        if (req.name != null && !req.name.trim().isEmpty()) {
            tt.setName(req.name.trim());
        }
        if (req.academicYear != null) {
            tt.setAcademicYear(req.academicYear.trim());
        }
        if (req.semester != null) {
            tt.setSemester(req.semester.trim());
            tt.setTermId(req.semester.trim());
        }
        if (req.departmentId != null) {
            tt.setDepartmentId(req.departmentId.trim());
        }
        if (req.programId != null) {
            tt.setProgramId(req.programId.trim());
        }
        if (req.effectiveFrom != null) {
            tt.setEffectiveFrom(req.effectiveFrom);
        }
        if (req.effectiveTo != null) {
            tt.setEffectiveTo(req.effectiveTo);
        }

        tt.setUpdatedAt(System.currentTimeMillis());
        tt.setUpdatedBy(userId);
        tt.setVersion(tt.getVersion() + 1);

        recordAudit(tenantId, id, "TIMETABLE_METADATA_UPDATED", userId, "ACADEMIC_ADMIN", "Updated metadata for timetable: " + id);
        return tt;
    }

    public Timetable getTimetable(String id, String tenantId) {
        return getTimetableOrThrow(id, tenantId);
    }

    public void deleteDraft(String id, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(id, tenantId);

        // Story 4: Soft delete if draft and has NEVER been published
        for (TimetableVersion v : timetableVersions.values()) {
            if (v.getTimetableId().equals(id) &&
                    (v.getStatus() == TimetableStatus.PUBLISHED || v.getStatus() == TimetableStatus.SUPERSEDED)) {
                throw new TimetableConflictException("Cannot delete timetable " + id + " because it contains published version history");
            }
        }

        tt.setDeleted(true);
        tt.setStatus(TimetableStatus.DELETED);
        tt.setUpdatedAt(System.currentTimeMillis());
        tt.setUpdatedBy(userId);

        recordOutboxEvent("TimetableArchived", id, id, MapBuilder.create()
                .put("timetableId", id)
                .put("status", "DELETED")
                .build());

        recordAudit(tenantId, id, "TIMETABLE_DRAFT_DELETED", userId, "ACADEMIC_ADMIN", "Soft-deleted draft timetable: " + id);
    }

    // =========================================================================
    // 4. Timetable Entry Management (Stories 11-15, 36, 37)
    // =========================================================================

    public TimetableEntry addEntry(String timetableId, CreateEntryRequest req, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);

        if (tt.getStatus() == TimetableStatus.PUBLISHED || tt.getStatus() == TimetableStatus.SUPERSEDED) {
            throw new TimetableImmutableException("Cannot add entries to a published or superseded timetable");
        }

        validateEntryRequest(req, tenantId);

        String entryId = "ENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long now = System.currentTimeMillis();

        TimetableEntry entry = new TimetableEntry();
        entry.setId(entryId);
        entry.setTenantId(tenantId);
        entry.setTimetableId(timetableId);
        entry.setVersionNo(tt.getCurrentVersionNo());
        entry.setBatchId(req.batchId);
        entry.setSubjectId(req.subjectId);
        entry.setFacultyId(req.facultyId);
        entry.setRoomId(req.roomId);
        entry.setDayOfWeek(DayOfWeek.valueOf(req.dayOfWeek.toUpperCase()));
        entry.setPeriod(req.period != null ? req.period : 1);
        if (req.periodId != null) {
            entry.setPeriodId(req.periodId);
        }
        entry.setStartTime(req.startTime);
        entry.setEndTime(req.endTime);
        entry.setEntryType(EntryType.valueOf(req.entryType.toUpperCase()));
        entry.setStatus(EntryStatus.ACTIVE);
        entry.setAdHoc(Boolean.TRUE.equals(req.isAdHoc));
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);

        timetableEntries.put(entryId, entry);

        // Invalidate prior validation status when draft entries mutate
        if (tt.getStatus() == TimetableStatus.VALIDATED || tt.getStatus() == TimetableStatus.INVALID) {
            tt.setStatus(TimetableStatus.DRAFT);
        }

        recordOutboxEvent("TimetableEntryAdded", timetableId, entryId, MapBuilder.create()
                .put("timetableId", timetableId)
                .put("entryId", entryId)
                .put("batchId", entry.getBatchId())
                .put("subjectId", entry.getSubjectId())
                .put("facultyId", entry.getFacultyId())
                .put("roomId", entry.getRoomId())
                .put("dayOfWeek", entry.getDayOfWeek().name())
                .put("period", entry.getPeriod())
                .put("entryType", entry.getEntryType().name())
                .build());

        recordAudit(tenantId, timetableId, "ENTRY_ADDED", userId, "ACADEMIC_ADMIN", "Slot entry added: " + entryId);
        return entry;
    }

    public TimetableEntry updateEntry(String timetableId, String entryId, UpdateEntryRequest req, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);

        if (tt.getStatus() == TimetableStatus.PUBLISHED || tt.getStatus() == TimetableStatus.SUPERSEDED) {
            throw new TimetableImmutableException("Cannot update entries on a published or superseded timetable");
        }

        TimetableEntry entry = timetableEntries.get(entryId);
        if (entry == null || !entry.getTimetableId().equals(timetableId) || !entry.getTenantId().equals(tenantId)
                || entry.getStatus() == EntryStatus.REMOVED) {
            throw new TimetableNotFoundException("Entry " + entryId + " not found on timetable " + timetableId);
        }

        if (req.batchId != null) {
            validateBatchReference(req.batchId, tenantId);
            entry.setBatchId(req.batchId);
        }
        if (req.subjectId != null) {
            validateSubjectReference(req.subjectId, tenantId);
            entry.setSubjectId(req.subjectId);
        }
        if (req.facultyId != null) {
            validateFacultyReference(req.facultyId, tenantId);
            entry.setFacultyId(req.facultyId);
        }
        if (req.roomId != null) {
            validateRoomReference(req.roomId, tenantId);
            entry.setRoomId(req.roomId);
        }
        if (req.dayOfWeek != null) {
            entry.setDayOfWeek(DayOfWeek.valueOf(req.dayOfWeek.toUpperCase()));
        }
        if (req.period != null) {
            entry.setPeriod(req.period);
        }
        if (req.periodId != null) {
            entry.setPeriodId(req.periodId);
        }
        if (req.startTime != null) {
            entry.setStartTime(req.startTime);
        }
        if (req.endTime != null) {
            entry.setEndTime(req.endTime);
        }
        if (req.entryType != null) {
            entry.setEntryType(EntryType.valueOf(req.entryType.toUpperCase()));
        }
        if (req.isAdHoc != null) {
            entry.setAdHoc(req.isAdHoc);
        }

        // Lab rights check on update
        if (entry.getEntryType() == EntryType.LAB) {
            FacultyRef fac = activeFaculty.get(entry.getFacultyId());
            if (fac == null || !fac.hasLabRights) {
                throw new LabRightsMissingException("Faculty " + entry.getFacultyId() + " does not hold required lab qualification for LAB slot");
            }
        }

        entry.setUpdatedAt(System.currentTimeMillis());

        // Invalidate prior validation status
        if (tt.getStatus() == TimetableStatus.VALIDATED || tt.getStatus() == TimetableStatus.INVALID) {
            tt.setStatus(TimetableStatus.DRAFT);
        }

        recordOutboxEvent("TimetableEntryUpdated", timetableId, entryId, MapBuilder.create()
                .put("timetableId", timetableId)
                .put("entryId", entryId)
                .put("facultyId", entry.getFacultyId())
                .put("roomId", entry.getRoomId())
                .put("period", entry.getPeriod())
                .build());

        recordAudit(tenantId, timetableId, "ENTRY_UPDATED", userId, "ACADEMIC_ADMIN", "Slot entry updated: " + entryId);
        return entry;
    }

    public void removeEntry(String timetableId, String entryId, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);

        if (tt.getStatus() == TimetableStatus.PUBLISHED || tt.getStatus() == TimetableStatus.SUPERSEDED) {
            throw new TimetableImmutableException("Cannot remove entries from a published or superseded timetable");
        }

        TimetableEntry entry = timetableEntries.get(entryId);
        if (entry == null || !entry.getTimetableId().equals(timetableId) || !entry.getTenantId().equals(tenantId)) {
            throw new TimetableNotFoundException("Entry " + entryId + " not found on timetable " + timetableId);
        }

        entry.setStatus(EntryStatus.REMOVED);
        entry.setUpdatedAt(System.currentTimeMillis());

        // Invalidate prior validation status
        if (tt.getStatus() == TimetableStatus.VALIDATED || tt.getStatus() == TimetableStatus.INVALID) {
            tt.setStatus(TimetableStatus.DRAFT);
        }

        recordOutboxEvent("TimetableEntryRemoved", timetableId, entryId, MapBuilder.create()
                .put("timetableId", timetableId)
                .put("entryId", entryId)
                .build());

        recordAudit(tenantId, timetableId, "ENTRY_REMOVED", userId, "ACADEMIC_ADMIN", "Slot entry removed: " + entryId);
    }

    public BulkEntriesResult bulkAddEntries(String timetableId, BulkEntriesRequest req, String tenantId, String userId) {
        if (req == null || req.entries == null) {
            throw new TimetableBadRequestException("Bulk entries list is required");
        }

        BulkEntriesResult result = new BulkEntriesResult();
        for (int i = 0; i < req.entries.size(); i++) {
            CreateEntryRequest slotReq = req.entries.get(i);
            try {
                TimetableEntry created = addEntry(timetableId, slotReq, tenantId, userId);
                result.successfulEntries.add(created);
            } catch (TimetableException te) {
                result.errors.add(new BulkRowError(i, te.getErrorCode(), te.getMessage()));
            } catch (Exception ex) {
                result.errors.add(new BulkRowError(i, "ACD_ENTRY_ERROR", ex.getMessage()));
            }
        }
        return result;
    }

    public TimetableEntry createAdHocSession(String timetableId, AdHocSessionRequest req, String tenantId, String userId) {
        if (req == null) {
            throw new TimetableBadRequestException("Ad-hoc session details required");
        }

        // Validate that ad-hoc session does not conflict with published schedule (Story 37)
        DayOfWeek dow = req.dayOfWeek != null ? DayOfWeek.valueOf(req.dayOfWeek.toUpperCase()) : DayOfWeek.MONDAY;
        int period = req.period != null ? req.period : 1;

        List<TimetableEntry> publishedSlots = getPublishedEntries();
        for (TimetableEntry pub : publishedSlots) {
            if (pub.getDayOfWeek() == dow && pub.getPeriod() == period) {
                if (pub.getFacultyId().equals(req.facultyId)) {
                    throw new TimetableConflictException("Ad-hoc session conflicts with published faculty schedule: " + req.facultyId);
                }
                if (pub.getRoomId() != null && pub.getRoomId().equals(req.roomId)) {
                    throw new TimetableConflictException("Ad-hoc session conflicts with published room schedule: " + req.roomId);
                }
                if (pub.getBatchId().equals(req.batchId)) {
                    throw new TimetableConflictException("Ad-hoc session conflicts with published batch schedule: " + req.batchId);
                }
            }
        }

        CreateEntryRequest entryReq = new CreateEntryRequest();
        entryReq.batchId = req.batchId;
        entryReq.subjectId = req.subjectId;
        entryReq.facultyId = req.facultyId;
        entryReq.roomId = req.roomId;
        entryReq.dayOfWeek = dow.name();
        entryReq.period = period;
        entryReq.entryType = req.entryType != null ? req.entryType : "EVT";
        entryReq.isAdHoc = true;

        return addEntry(timetableId, entryReq, tenantId, userId);
    }

    // =========================================================================
    // 5. Reference Validation (Stories 16-18)
    // =========================================================================

    private void validateEntryRequest(CreateEntryRequest req, String tenantId) {
        if (req.batchId == null || req.batchId.trim().isEmpty()) {
            throw new TimetableBadRequestException("batchId is required");
        }
        if (req.subjectId == null || req.subjectId.trim().isEmpty()) {
            throw new TimetableBadRequestException("subjectId is required");
        }
        if (req.facultyId == null || req.facultyId.trim().isEmpty()) {
            throw new TimetableBadRequestException("facultyId is required");
        }
        if (req.dayOfWeek == null) {
            throw new TimetableBadRequestException("dayOfWeek is required");
        }
        if (req.period == null) {
            throw new TimetableBadRequestException("period is required");
        }

        validateBatchReference(req.batchId, tenantId);
        validateSubjectReference(req.subjectId, tenantId);
        validateFacultyReference(req.facultyId, tenantId);
        if (req.roomId != null && !req.roomId.trim().isEmpty()) {
            validateRoomReference(req.roomId, tenantId);
        }

        // Validate Entry Type
        try {
            EntryType type = EntryType.valueOf(req.entryType.toUpperCase());
            if (type == EntryType.LAB) {
                FacultyRef fac = activeFaculty.get(req.facultyId);
                if (fac == null || !fac.hasLabRights) {
                    throw new LabRightsMissingException("Faculty " + req.facultyId + " does not hold required lab qualification for LAB slot");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new TimetableBadRequestException("Invalid entryType: " + req.entryType + ". Valid types: TH, PR, LAB, TU, SEM, EVT, EXM");
        }
    }

    public void validateBatchReference(String batchId, String tenantId) {
        BatchRef ref = activeBatches.get(batchId);
        if (ref == null || !ref.active) {
            throw new InvalidReferenceException("Invalid or inactive batch reference: " + batchId);
        }
        if (!ref.tenantId.equals(tenantId)) {
            throw new CrossTenantReferenceException("Cross-tenant batch reference prohibited: " + batchId);
        }
    }

    public void validateSubjectReference(String subjectId, String tenantId) {
        SubjectRef ref = activeSubjects.get(subjectId);
        if (ref == null || !ref.active) {
            throw new InvalidReferenceException("Invalid or inactive subject reference: " + subjectId);
        }
        if (!ref.tenantId.equals(tenantId)) {
            throw new CrossTenantReferenceException("Cross-tenant subject reference prohibited: " + subjectId);
        }
    }

    public void validateFacultyReference(String facultyId, String tenantId) {
        FacultyRef ref = activeFaculty.get(facultyId);
        if (ref == null || !ref.active) {
            throw new InvalidReferenceException("Invalid or inactive faculty reference: " + facultyId);
        }
        if (!ref.tenantId.equals(tenantId)) {
            throw new CrossTenantReferenceException("Cross-tenant faculty reference prohibited: " + facultyId);
        }
    }

    public void validateRoomReference(String roomId, String tenantId) {
        RoomRef ref = activeRooms.get(roomId);
        if (ref == null || !ref.active) {
            throw new InvalidReferenceException("Invalid or inactive room reference: " + roomId);
        }
        if (!ref.tenantId.equals(tenantId)) {
            throw new CrossTenantReferenceException("Cross-tenant room reference prohibited: " + roomId);
        }
    }

    // =========================================================================
    // 6. Conflict Detection Engine (Stories 19-27, BR-01, 02, 03, 06, 07, 11)
    // =========================================================================

    public List<ConflictResult> validateTimetable(String timetableId, String tenantId, String userId) {
        long startTime = System.currentTimeMillis();
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);

        tt.setStatus(TimetableStatus.VALIDATING);

        // Fetch all active entries for current version
        List<TimetableEntry> entries = timetableEntries.values().stream()
                .filter(e -> e.getTimetableId().equals(timetableId)
                        && e.getVersionNo() == tt.getCurrentVersionNo()
                        && e.getStatus() == EntryStatus.ACTIVE)
                .collect(Collectors.toList());

        List<ConflictResult> detectedConflicts = new ArrayList<>();

        // 1. Check Faculty Double-Booking (BR-01, Story 20)
        Map<String, List<TimetableEntry>> facultySlotMap = new HashMap<>();
        for (TimetableEntry e : entries) {
            String key = e.getFacultyId() + ":" + e.getDayOfWeek() + ":" + e.getPeriod();
            facultySlotMap.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<TimetableEntry>> entry : facultySlotMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> entryIds = entry.getValue().stream().map(TimetableEntry::getId).collect(Collectors.toList());
                TimetableEntry first = entry.getValue().get(0);
                String msg = String.format("Faculty %s is double-booked in %s P%d.",
                        first.getFacultyId(), first.getDayOfWeek().name(), first.getPeriod());
                detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                        ConflictType.FACULTY, ConflictSeverity.BLOCKING, entryIds, msg));
            }
        }

        // 2. Check Room Double-Booking (BR-02, Story 21)
        Map<String, List<TimetableEntry>> roomSlotMap = new HashMap<>();
        for (TimetableEntry e : entries) {
            if (e.getRoomId() != null && !e.getRoomId().trim().isEmpty()) {
                String key = e.getRoomId() + ":" + e.getDayOfWeek() + ":" + e.getPeriod();
                roomSlotMap.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
        }
        for (Map.Entry<String, List<TimetableEntry>> entry : roomSlotMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> entryIds = entry.getValue().stream().map(TimetableEntry::getId).collect(Collectors.toList());
                TimetableEntry first = entry.getValue().get(0);
                String msg = String.format("Room %s is double-booked in %s P%d.",
                        first.getRoomId(), first.getDayOfWeek().name(), first.getPeriod());
                detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                        ConflictType.ROOM, ConflictSeverity.BLOCKING, entryIds, msg));
            }
        }

        // 3. Check Batch Double-Booking (BR-03, Story 22)
        Map<String, List<TimetableEntry>> batchSlotMap = new HashMap<>();
        for (TimetableEntry e : entries) {
            String key = e.getBatchId() + ":" + e.getDayOfWeek() + ":" + e.getPeriod();
            batchSlotMap.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<TimetableEntry>> entry : batchSlotMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> entryIds = entry.getValue().stream().map(TimetableEntry::getId).collect(Collectors.toList());
                TimetableEntry first = entry.getValue().get(0);
                String msg = String.format("Batch %s is double-booked in %s P%d.",
                        first.getBatchId(), first.getDayOfWeek().name(), first.getPeriod());
                detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                        ConflictType.BATCH, ConflictSeverity.BLOCKING, entryIds, msg));
            }
        }

        // 4. Check Duplicate Entry (Story 23, Conflict Model §15)
        Map<String, List<TimetableEntry>> duplicateKeyMap = new HashMap<>();
        for (TimetableEntry e : entries) {
            String key = e.getDayOfWeek() + ":" + e.getPeriod() + ":" + e.getSubjectId() + ":" + e.getBatchId();
            duplicateKeyMap.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<TimetableEntry>> entry : duplicateKeyMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> entryIds = entry.getValue().stream().map(TimetableEntry::getId).collect(Collectors.toList());
                TimetableEntry first = entry.getValue().get(0);
                String msg = String.format("Duplicate entry in %s P%d for subject %s and batch %s.",
                        first.getDayOfWeek().name(), first.getPeriod(), first.getSubjectId(), first.getBatchId());
                detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                        ConflictType.DUPLICATE_ENTRY, ConflictSeverity.BLOCKING, entryIds, msg));
            }
        }

        // 5. Check Academic Calendar Alignment (BR-07, Story 24)
        CalendarWindow calWindow = calendarWindows.get(tenantId);
        if (calWindow != null && tt.getEffectiveFrom() != null) {
            if (tt.getEffectiveFrom().compareTo(calWindow.startDate) < 0 ||
                    (calWindow.endDate != null && tt.getEffectiveFrom().compareTo(calWindow.endDate) > 0)) {
                String msg = String.format("Effective date %s is outside academic calendar window [%s to %s]",
                        tt.getEffectiveFrom(), calWindow.startDate, calWindow.endDate);
                detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                        ConflictType.CALENDAR, ConflictSeverity.BLOCKING, Collections.emptyList(), msg));
            }
        }

        // 6. Check Faculty & Room Availability Windows (Story 24)
        for (TimetableEntry e : entries) {
            String facKey = e.getFacultyId() + ":" + e.getDayOfWeek().name() + ":" + e.getPeriod();
            Set<String> unavailFac = facultyUnavailability.get(e.getFacultyId());
            if (unavailFac != null && unavailFac.contains(e.getDayOfWeek().name() + ":" + e.getPeriod())) {
                detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                        ConflictType.AVAILABILITY, ConflictSeverity.BLOCKING, Collections.singletonList(e.getId()),
                        "Faculty " + e.getFacultyId() + " is marked unavailable for slot " + e.getDayOfWeek().name() + " P" + e.getPeriod()));
            }

            if (e.getRoomId() != null) {
                Set<String> unavailRoom = roomUnavailability.get(e.getRoomId());
                if (unavailRoom != null && unavailRoom.contains(e.getDayOfWeek().name() + ":" + e.getPeriod())) {
                    detectedConflicts.add(new ConflictResult(timetableId, tt.getCurrentVersionNo(),
                            ConflictType.AVAILABILITY, ConflictSeverity.BLOCKING, Collections.singletonList(e.getId()),
                            "Room " + e.getRoomId() + " is marked unavailable for slot " + e.getDayOfWeek().name() + " P" + e.getPeriod()));
                }
            }
        }

        // Persist conflict results collection (Reproducible conflict results BR-11)
        conflictResults.put(timetableId + ":" + tt.getCurrentVersionNo(), detectedConflicts);

        long blockingCount = detectedConflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.BLOCKING).count();
        long warningCount = detectedConflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.WARNING).count();

        // Update TimetableVersion validation state
        TimetableVersion version = timetableVersions.get(timetableId + ":" + tt.getCurrentVersionNo());
        if (version != null) {
            version.setBlockingConflictCount((int) blockingCount);
            version.setWarningCount((int) warningCount);
            if (blockingCount == 0) {
                version.setValidationStatus(ValidationStatus.PASSED);
                tt.setStatus(TimetableStatus.VALIDATED);
            } else {
                version.setValidationStatus(ValidationStatus.FAILED);
                tt.setStatus(TimetableStatus.INVALID);
            }
        }

        tt.setRevalidationFlag(false);
        tt.setUpdatedAt(System.currentTimeMillis());

        // Emit validation event
        if (blockingCount == 0) {
            recordOutboxEvent("TimetableValidated", timetableId, timetableId, MapBuilder.create()
                    .put("timetableId", timetableId)
                    .put("versionNo", tt.getCurrentVersionNo())
                    .put("status", "VALIDATED")
                    .put("blockingConflicts", 0)
                    .put("warnings", warningCount)
                    .build());
        } else {
            recordOutboxEvent("TimetableConflictDetected", timetableId, timetableId, MapBuilder.create()
                    .put("timetableId", timetableId)
                    .put("versionNo", tt.getCurrentVersionNo())
                    .put("status", "INVALID")
                    .put("blockingConflicts", blockingCount)
                    .put("warnings", warningCount)
                    .build());
        }

        MetricsCollector.getInstance().recordValidation(System.currentTimeMillis() - startTime);
        recordAudit(tenantId, timetableId, "TIMETABLE_VALIDATED", userId, "ACADEMIC_ADMIN",
                "Validation completed. Blocking: " + blockingCount + ", Warnings: " + warningCount);

        return detectedConflicts;
    }

    public List<ConflictResult> getConflicts(String timetableId, String tenantId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);
        List<ConflictResult> res = conflictResults.get(timetableId + ":" + tt.getCurrentVersionNo());
        return res != null ? res : Collections.emptyList();
    }

    // =========================================================================
    // 7. Versioning & Publication (Stories 28-35, 39, 40, BR-05, BR-08, BR-12)
    // =========================================================================

    public TimetableVersion publishTimetable(String timetableId, PublishRequest req, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);

        // Verification: must be in status=VALIDATED with zero blocking conflicts (Story 36, BR-06)
        TimetableVersion curVer = timetableVersions.get(timetableId + ":" + tt.getCurrentVersionNo());
        if (tt.getStatus() != TimetableStatus.VALIDATED || curVer == null ||
                curVer.getValidationStatus() != ValidationStatus.PASSED || curVer.getBlockingConflictCount() > 0) {
            throw new TimetableValidationException("Cannot publish unvalidated or conflicting timetable " + timetableId +
                    ". Status: " + tt.getStatus() + ", blocking conflicts: " + (curVer != null ? curVer.getBlockingConflictCount() : -1));
        }

        // Effective date calendar policy check (BR-07, Story 37)
        String effFrom = req != null && req.effectiveFrom != null ? req.effectiveFrom : tt.getEffectiveFrom();
        CalendarWindow calWindow = calendarWindows.get(tenantId);
        if (calWindow != null && effFrom != null) {
            if (effFrom.compareTo(calWindow.startDate) < 0 ||
                    (calWindow.endDate != null && effFrom.compareTo(calWindow.endDate) > 0)) {
                throw new CalendarViolationException("EffectiveFrom date " + effFrom +
                        " is outside academic calendar window [" + calWindow.startDate + " to " + calWindow.endDate + "]");
            }
        }

        long now = System.currentTimeMillis();

        // 1. Freeze snapshot of entries in current version (Immutable Published Versions BR-05)
        List<TimetableEntry> entriesSnapshot = timetableEntries.values().stream()
                .filter(e -> e.getTimetableId().equals(timetableId)
                        && e.getVersionNo() == tt.getCurrentVersionNo()
                        && e.getStatus() == EntryStatus.ACTIVE)
                .map(TimetableEntry::new)
                .collect(Collectors.toList());

        curVer.setEntriesSnapshot(entriesSnapshot);
        curVer.setStatus(TimetableStatus.PUBLISHED);
        curVer.setEffectiveFrom(effFrom);
        if (req != null && req.effectiveTo != null) {
            curVer.setEffectiveTo(req.effectiveTo);
        }
        curVer.setPublishedAt(now);
        curVer.setPublishedBy(userId);

        // 2. Mark any previously published version for this timetable as SUPERSEDED atomically (BR-12, Story 40)
        for (TimetableVersion v : timetableVersions.values()) {
            if (v.getTimetableId().equals(timetableId) && v.getVersionNo() != tt.getCurrentVersionNo()
                    && v.getStatus() == TimetableStatus.PUBLISHED) {
                v.setStatus(TimetableStatus.SUPERSEDED);
                recordOutboxEvent("TimetableSuperseded", timetableId, v.getId(), MapBuilder.create()
                        .put("timetableId", timetableId)
                        .put("supersededVersion", v.getVersionNo())
                        .put("effectiveVersion", tt.getCurrentVersionNo())
                        .build());
            }
        }

        // 3. Update Timetable Aggregate
        tt.setStatus(TimetableStatus.PUBLISHED);
        tt.setEffectiveFrom(effFrom);
        tt.setUpdatedAt(now);
        tt.setUpdatedBy(userId);
        tt.setVersion(tt.getVersion() + 1);

        // 4. Emit TimetablePublished & TimetableChanged events to Outbox (Story 40, 48, 49)
        recordOutboxEvent("TimetablePublished", timetableId, timetableId, MapBuilder.create()
                .put("timetableVersion", curVer.getVersionNo())
                .put("effectiveFrom", curVer.getEffectiveFrom())
                .put("status", "PUBLISHED")
                .put("batchId", tt.getBatchId())
                .put("departmentId", tt.getDepartmentId())
                .put("academicYear", tt.getAcademicYear())
                .build());

        recordOutboxEvent("TimetableChanged", timetableId, timetableId, MapBuilder.create()
                .put("timetableId", timetableId)
                .put("effectiveVersion", curVer.getVersionNo())
                .put("status", "PUBLISHED")
                .build());

        MetricsCollector.getInstance().recordPublish(true);
        recordAudit(tenantId, timetableId, "TIMETABLE_PUBLISHED", userId, "ACADEMIC_ADMIN",
                "Timetable version v" + curVer.getVersionNo() + " published successfully");

        return curVer;
    }

    public Timetable cloneEffectiveVersion(String timetableId, CloneRequest req, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);

        // Find the effective published version
        TimetableVersion effectiveVer = null;
        for (TimetableVersion v : timetableVersions.values()) {
            if (v.getTimetableId().equals(timetableId) && v.getStatus() == TimetableStatus.PUBLISHED) {
                effectiveVer = v;
                break;
            }
        }
        if (effectiveVer == null) {
            throw new TimetableConflictException("Cannot clone timetable: No currently effective PUBLISHED version exists for " + timetableId);
        }

        long now = System.currentTimeMillis();
        int newVerNo = tt.getCurrentVersionNo() + 1;

        // Clone entries snapshot into new draft version (Story 39)
        for (TimetableEntry snap : effectiveVer.getEntriesSnapshot()) {
            TimetableEntry cloneEntry = new TimetableEntry(snap);
            cloneEntry.setId("ENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cloneEntry.setVersionNo(newVerNo);
            cloneEntry.setCreatedAt(now);
            cloneEntry.setUpdatedAt(now);
            timetableEntries.put(cloneEntry.getId(), cloneEntry);
        }

        // Create new draft version
        TimetableVersion newVer = new TimetableVersion();
        newVer.setId("TTV-" + timetableId + "-v" + newVerNo);
        newVer.setTenantId(tenantId);
        newVer.setTimetableId(timetableId);
        newVer.setVersionNo(newVerNo);
        newVer.setStatus(TimetableStatus.DRAFT);
        newVer.setValidationStatus(ValidationStatus.NOT_VALIDATED);
        newVer.setSourceVersionNo(effectiveVer.getVersionNo());
        newVer.setEffectiveFrom(effectiveVer.getEffectiveFrom());
        newVer.setCreatedAt(now);
        timetableVersions.put(timetableId + ":" + newVerNo, newVer);

        // Update Timetable aggregate
        tt.setCurrentVersionNo(newVerNo);
        tt.setStatus(TimetableStatus.DRAFT);
        tt.setUpdatedAt(now);
        tt.setUpdatedBy(userId);
        tt.setVersion(tt.getVersion() + 1);

        recordAudit(tenantId, timetableId, "TIMETABLE_VERSION_CLONED", userId, "ACADEMIC_ADMIN",
                "Cloned v" + effectiveVer.getVersionNo() + " into new draft v" + newVerNo + ". Reason: " + (req != null ? req.reason : "N/A"));

        return tt;
    }

    public List<TimetableVersion> getVersionHistory(String timetableId, String tenantId) {
        getTimetableOrThrow(timetableId, tenantId);
        return timetableVersions.values().stream()
                .filter(v -> v.getTimetableId().equals(timetableId))
                .sorted(Comparator.comparingInt(TimetableVersion::getVersionNo))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 8. Timetable Search & Views (Stories 5-10, FR-11)
    // =========================================================================

    public List<Timetable> searchTimetables(String tenantId, String batchId, String facultyId,
                                            String roomId, String subjectId, String departmentId,
                                            String status, String academicYear, int page, int size) {
        return timetables.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && !t.isDeleted())
                .filter(t -> departmentId == null || departmentId.equalsIgnoreCase(t.getDepartmentId()))
                .filter(t -> status == null || status.equalsIgnoreCase(t.getStatus().name()))
                .filter(t -> academicYear == null || academicYear.equalsIgnoreCase(t.getAcademicYear()))
                .filter(t -> batchId == null || matchesBatch(t, batchId))
                .filter(t -> facultyId == null || matchesFaculty(t, facultyId))
                .filter(t -> roomId == null || matchesRoom(t, roomId))
                .filter(t -> subjectId == null || matchesSubject(t, subjectId))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    private boolean matchesBatch(Timetable t, String batchId) {
        if (batchId.equalsIgnoreCase(t.getBatchId())) return true;
        return timetableEntries.values().stream()
                .anyMatch(e -> e.getTimetableId().equals(t.getId())
                        && e.getVersionNo() == t.getCurrentVersionNo()
                        && e.getStatus() == EntryStatus.ACTIVE
                        && batchId.equalsIgnoreCase(e.getBatchId()));
    }

    private boolean matchesFaculty(Timetable t, String facultyId) {
        return timetableEntries.values().stream()
                .anyMatch(e -> e.getTimetableId().equals(t.getId())
                        && e.getVersionNo() == t.getCurrentVersionNo()
                        && e.getStatus() == EntryStatus.ACTIVE
                        && facultyId.equalsIgnoreCase(e.getFacultyId()));
    }

    private boolean matchesRoom(Timetable t, String roomId) {
        return timetableEntries.values().stream()
                .anyMatch(e -> e.getTimetableId().equals(t.getId())
                        && e.getVersionNo() == t.getCurrentVersionNo()
                        && e.getStatus() == EntryStatus.ACTIVE
                        && roomId.equalsIgnoreCase(e.getRoomId()));
    }

    private boolean matchesSubject(Timetable t, String subjectId) {
        return timetableEntries.values().stream()
                .anyMatch(e -> e.getTimetableId().equals(t.getId())
                        && e.getVersionNo() == t.getCurrentVersionNo()
                        && e.getStatus() == EntryStatus.ACTIVE
                        && subjectId.equalsIgnoreCase(e.getSubjectId()));
    }

    public List<TimetableEntry> getPublishedBatchTimetable(String batchId, String tenantId) {
        return getPublishedEntries().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && batchId.equalsIgnoreCase(e.getBatchId()))
                .collect(Collectors.toList());
    }

    public List<TimetableEntry> getPublishedFacultyTimetable(String facultyId, String tenantId) {
        return getPublishedEntries().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && facultyId.equalsIgnoreCase(e.getFacultyId()))
                .collect(Collectors.toList());
    }

    public List<TimetableEntry> getPublishedRoomUtilization(String roomId, String tenantId) {
        return getPublishedEntries().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && roomId.equalsIgnoreCase(e.getRoomId()))
                .collect(Collectors.toList());
    }

    public List<Timetable> getDepartmentTimetables(String departmentId, String tenantId) {
        return timetables.values().stream()
                .filter(t -> t.getTenantId().equals(tenantId) && !t.isDeleted()
                        && departmentId.equalsIgnoreCase(t.getDepartmentId())
                        && t.getStatus() == TimetableStatus.PUBLISHED)
                .collect(Collectors.toList());
    }

    public Map<String, Object> exportTimetable(String timetableId, String format, String tenantId, String userId) {
        Timetable tt = getTimetableOrThrow(timetableId, tenantId);
        List<TimetableEntry> entries = getEntriesForTimetable(timetableId, tt.getCurrentVersionNo());

        String fmt = format != null ? format.toUpperCase() : "PDF";
        String exportId = "EXP-" + UUID.randomUUID().toString().substring(0, 8);

        recordOutboxEvent("TimetableExported", timetableId, exportId, MapBuilder.create()
                .put("timetableId", timetableId)
                .put("exportId", exportId)
                .put("format", fmt)
                .put("exportedBy", userId)
                .build());

        recordAudit(tenantId, timetableId, "TIMETABLE_EXPORTED", userId, "ACADEMIC_ADMIN", "Exported timetable in format: " + fmt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exportId", exportId);
        result.put("timetableId", timetableId);
        result.put("timetableCode", tt.getTimetableCode());
        result.put("format", fmt);
        result.put("status", "COMPLETED");
        result.put("entryCount", entries.size());
        result.put("generatedAt", System.currentTimeMillis());
        return result;
    }

    private List<TimetableEntry> getPublishedEntries() {
        List<TimetableEntry> result = new ArrayList<>();
        for (TimetableVersion v : timetableVersions.values()) {
            if (v.getStatus() == TimetableStatus.PUBLISHED) {
                result.addAll(v.getEntriesSnapshot());
            }
        }
        return result;
    }

    public List<TimetableEntry> getEntriesForTimetable(String timetableId, int versionNo) {
        return timetableEntries.values().stream()
                .filter(e -> e.getTimetableId().equals(timetableId)
                        && e.getVersionNo() == versionNo
                        && e.getStatus() == EntryStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 9. Event Ingestion & Outbox Handling (Stories 47-53)
    // =========================================================================

    public boolean consumeEvent(Map<String, Object> event) {
        if (event == null) return false;
        String eventId = (String) event.get("eventId");
        String eventType = (String) event.get("eventType");

        if (eventId != null && !processedEventIds.add(eventId)) {
            logger.info("Duplicate event ignored: {}", eventId);
            return true; // Idempotent success (Story 52)
        }

        try {
            if ("BatchCreated".equalsIgnoreCase(eventType) || "BatchUpdated".equalsIgnoreCase(eventType)) {
                handleBatchEvent(event);
            } else if ("SubjectUpdated".equalsIgnoreCase(eventType)) {
                handleSubjectEvent(event);
            } else if ("FacultyAvailabilityChanged".equalsIgnoreCase(eventType)) {
                handleFacultyAvailabilityEvent(event);
            } else if ("AcademicCalendarPublished".equalsIgnoreCase(eventType)) {
                handleCalendarEvent(event);
            } else {
                logger.info("Unrecognized event type {} ignored", eventType);
            }
            return true;
        } catch (Exception ex) {
            logger.error("Failed to process event {}: {}", eventId, ex.getMessage(), ex);
            routeToDlq(eventId, eventType, (String) event.get("source"), event.toString(), ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBatchEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        String batchId = (String) event.get("entityId");
        if (batchId == null && data != null) {
            batchId = (String) data.get("batchId");
        }
        String tenantId = (String) event.get("tenantId");
        if (tenantId == null) tenantId = "TENANT-001";

        if (batchId != null) {
            activeBatches.put(batchId, new BatchRef(batchId, tenantId, "DEP-CSE", "PROG-DEFAULT"));
            // Flag referencing drafts for re-validation (Story 23)
            flagDraftsForRevalidationByBatch(batchId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSubjectEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        String subjectId = (String) event.get("entityId");
        if (subjectId == null && data != null) {
            subjectId = (String) data.get("subjectId");
        }
        String tenantId = (String) event.get("tenantId");
        if (tenantId == null) tenantId = "TENANT-001";

        if (subjectId != null) {
            activeSubjects.put(subjectId, new SubjectRef(subjectId, tenantId, "DEP-CSE"));
            flagDraftsForRevalidationBySubject(subjectId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleFacultyAvailabilityEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        String facultyId = (String) event.get("entityId");
        if (facultyId == null && data != null) {
            facultyId = (String) data.get("facultyId");
        }

        if (facultyId != null && data != null && data.containsKey("unavailableSlot")) {
            String slot = (String) data.get("unavailableSlot");
            facultyUnavailability.computeIfAbsent(facultyId, k -> new HashSet<>()).add(slot);
            flagDraftsForRevalidationByFaculty(facultyId);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCalendarEvent(Map<String, Object> event) {
        Map<String, Object> data = (Map<String, Object>) event.get("data");
        String tenantId = (String) event.get("tenantId");
        if (tenantId == null) tenantId = "TENANT-001";

        if (data != null && data.containsKey("startDate") && data.containsKey("endDate")) {
            calendarWindows.put(tenantId, new CalendarWindow((String) data.get("startDate"), (String) data.get("endDate")));
        }
    }

    private void flagDraftsForRevalidationByBatch(String batchId) {
        for (Timetable tt : timetables.values()) {
            if (tt.getStatus() == TimetableStatus.DRAFT || tt.getStatus() == TimetableStatus.VALIDATED) {
                if (matchesBatch(tt, batchId)) {
                    tt.setRevalidationFlag(true);
                }
            }
        }
    }

    private void flagDraftsForRevalidationBySubject(String subjectId) {
        for (Timetable tt : timetables.values()) {
            if (tt.getStatus() == TimetableStatus.DRAFT || tt.getStatus() == TimetableStatus.VALIDATED) {
                if (matchesSubject(tt, subjectId)) {
                    tt.setRevalidationFlag(true);
                }
            }
        }
    }

    private void flagDraftsForRevalidationByFaculty(String facultyId) {
        for (Timetable tt : timetables.values()) {
            if (tt.getStatus() == TimetableStatus.DRAFT || tt.getStatus() == TimetableStatus.VALIDATED) {
                if (matchesFaculty(tt, facultyId)) {
                    tt.setRevalidationFlag(true);
                }
            }
        }
    }

    public void routeToDlq(String eventId, String eventType, String source, String payload, String reason) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId(UUID.randomUUID().toString());
        dlq.setEventId(eventId);
        dlq.setEventType(eventType);
        dlq.setSource(source != null ? source : "UNKNOWN");
        dlq.setOccurredAt(System.currentTimeMillis());
        dlq.setPayload(payload);
        dlq.setFailureReason(reason);
        dlq.setRetryAttempts(3);
        dlq.setCreatedAt(System.currentTimeMillis());
        deadLetterEvents.put(dlq.getId(), dlq);
    }

    // =========================================================================
    // 10. Idempotency Support (Stories 54, 55)
    // =========================================================================

    public IdempotencyRecord getIdempotency(String idempotencyKey, String tenantId) {
        if (idempotencyKey == null) return null;
        return idempotencyRecords.get(tenantId + ":" + idempotencyKey);
    }

    public void saveIdempotency(String idempotencyKey, String tenantId, String hash, int status, String body) {
        if (idempotencyKey == null) return;
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setId(UUID.randomUUID().toString());
        rec.setIdempotencyKey(idempotencyKey);
        rec.setTenantId(tenantId);
        rec.setRequestHash(hash);
        rec.setStatusCode(status);
        rec.setResponseBody(body);
        rec.setCreatedAt(System.currentTimeMillis());
        rec.setExpiresAt(System.currentTimeMillis() + 86400000L); // 24 hours
        idempotencyRecords.put(tenantId + ":" + idempotencyKey, rec);
    }

    // =========================================================================
    // 11. Security & Helpers
    // =========================================================================

    public ApiKeyRecord validateApiKey(String key, String tenantId) {
        ApiKeyRecord rec = apiKeys.get(key);
        if (rec != null && rec.isActive() && (tenantId == null || rec.getTenantId().equals(tenantId))) {
            return rec;
        }
        return null;
    }

    public Timetable getTimetableOrThrow(String id, String tenantId) {
        Timetable tt = timetables.get(id);
        if (tt == null || tt.isDeleted() || (tenantId != null && !tt.getTenantId().equals(tenantId))) {
            throw new TimetableNotFoundException("Timetable not found with ID: " + id);
        }
        return tt;
    }

    private void recordOutboxEvent(String eventType, String timetableId, String entityId, Map<String, Object> data) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(eventType);
        event.setEntityId(entityId);
        event.setData(data);
        event.setStatus(EventStatus.PENDING);
        outboxEvents.put(event.getId(), event);
    }

    private void recordAudit(String tenantId, String timetableId, String action, String actorId, String userRole, String details) {
        auditLogs.add(new TimetableAuditLog(tenantId, timetableId, action, actorId, userRole, details));
    }

    // Gauges / Metrics getters
    public int getPendingOutboxCount() {
        return (int) outboxEvents.values().stream().filter(e -> e.getStatus() == EventStatus.PENDING).count();
    }

    public int getDlqCount() {
        return deadLetterEvents.size();
    }

    public int getStaleDraftCount() {
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 3600 * 1000);
        return (int) timetables.values().stream()
                .filter(t -> t.getStatus() == TimetableStatus.DRAFT && t.getUpdatedAt() < sevenDaysAgo)
                .count();
    }

    public List<OutboxEvent> getOutboxEvents() {
        return new ArrayList<>(outboxEvents.values());
    }

    public List<TimetableAuditLog> getAuditLogs() {
        return new ArrayList<>(auditLogs);
    }

    public Map<String, BatchRef> getActiveBatches() {
        return activeBatches;
    }

    public Map<String, SubjectRef> getActiveSubjects() {
        return activeSubjects;
    }

    public Map<String, FacultyRef> getActiveFaculty() {
        return activeFaculty;
    }

    public Map<String, RoomRef> getActiveRooms() {
        return activeRooms;
    }

    public Map<String, CalendarWindow> getCalendarWindows() {
        return calendarWindows;
    }

    public static class MapBuilder {
        private final Map<String, Object> map = new LinkedHashMap<>();
        public static MapBuilder create() { return new MapBuilder(); }
        public MapBuilder put(String k, Object v) { map.put(k, v); return this; }
        public Map<String, Object> build() { return map; }
    }
}
