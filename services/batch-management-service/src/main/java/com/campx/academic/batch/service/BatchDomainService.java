package com.campx.academic.batch.service;

import com.campx.academic.batch.exception.*;
import com.campx.academic.batch.model.BatchModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core Domain Service implementing all ACD-04 business rules, state machines,
 * capacity invariants, roster management, split/merge orchestration, and event reliability.
 */
public class BatchDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(BatchDomainService.class);

    // Primary In-Memory Collections (simulating MongoDB collections)
    private final Map<String, Batch> batches = new ConcurrentHashMap<>();
    private final Map<String, BatchRoster> rosters = new ConcurrentHashMap<>();
    private final Map<String, BatchCapacityOverride> overrides = new ConcurrentHashMap<>();
    private final List<BatchHistory> histories = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, OutboxEvent> outbox = new ConcurrentHashMap<>();
    private final Map<String, InboxEvent> inbox = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterQueue = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdempotencyRecord> idempotencyStore = new ConcurrentHashMap<>();
    private final Map<String, ApiKeyRecord> apiKeyStore = new ConcurrentHashMap<>();

    // Split & Merge Pending Store
    private final Map<String, BatchSplitRequest> splitRequests = new ConcurrentHashMap<>();
    private final Map<String, BatchMergeRequest> mergeRequests = new ConcurrentHashMap<>();

    // Mock registries for upstream module validations (ACD-01 Courses, ACD-02 Curricula, STM Students)
    private final Map<String, Boolean> activeCourseRegistry = new ConcurrentHashMap<>();
    private final Map<String, Boolean> activeCurriculumRegistry = new ConcurrentHashMap<>();
    private final Map<String, Boolean> eligibleStudentRegistry = new ConcurrentHashMap<>();

    // Configuration flags
    private boolean facultyRequiredForActivation = false;

    // Generators
    private final AtomicLong batchIdSeq = new AtomicLong(100);
    private final AtomicLong sectionIdSeq = new AtomicLong(100);
    private final AtomicLong rosterIdSeq = new AtomicLong(100);
    private final AtomicLong overrideIdSeq = new AtomicLong(100);
    private final AtomicLong historyIdSeq = new AtomicLong(100);
    private final AtomicLong eventIdSeq = new AtomicLong(100);
    private final AtomicLong requestIdSeq = new AtomicLong(100);

    public BatchDomainService() {
        seedSampleData();
    }

    /**
     * Seeds initial valid courses and eligible students for integration tests.
     */
    private void seedSampleData() {
        // Active Courses in ACD-01
        activeCourseRegistry.put("CRS-CS101", true);
        activeCourseRegistry.put("CRS-CS102", true);
        activeCourseRegistry.put("CRS-MATH201", true);
        activeCourseRegistry.put("CRS-INACTIVE", false);

        // Active Curricula in ACD-02
        activeCurriculumRegistry.put("CURR-MCA-2026", true);
        activeCurriculumRegistry.put("CURR-001", true);
        activeCurriculumRegistry.put("CURR-CS-2026", true);
        activeCurriculumRegistry.put("CURR-527CDFA3", true);
        activeCurriculumRegistry.put("CURR-RETIRED", false);

        // Eligible Students in STM
        eligibleStudentRegistry.put("STU-1001", true);
        eligibleStudentRegistry.put("STU-1002", true);
        eligibleStudentRegistry.put("STU-1003", true);
        eligibleStudentRegistry.put("STU-1004", true);
        eligibleStudentRegistry.put("STU-1005", true);
        eligibleStudentRegistry.put("STU-1006", true);
        eligibleStudentRegistry.put("STU-1007", true);
        eligibleStudentRegistry.put("STU-1008", true);
        eligibleStudentRegistry.put("STU-INELIGIBLE", false);

        // API Key for External consumers (Story 55)
        ApiKeyRecord extKey = new ApiKeyRecord("KEY-EXT-001", "ext-secret-key-123", "TENANT-001",
                Collections.singletonList("batch:read"), System.currentTimeMillis() + 86400000L);
        apiKeyStore.put(extKey.getRawKey(), extKey);
    }

    // =========================================================================
    // 1. Batch Definition & Identity Management (Stories 3-8)
    // =========================================================================

    /**
     * Creates a new draft batch linked to a valid active course (Story 3, 4, 5, 6).
     */
    public Batch createBatch(Batch batch, String actorId, String actorRole, String idempotencyKey) {
        if (batch == null) {
            throw new BatchValidationException("Batch payload must not be null");
        }

        // Validate mandatory fields
        if (isEmpty(batch.getBatchCode())) {
            throw new BatchValidationException("batchCode is required");
        }
        if (isEmpty(batch.getName())) {
            throw new BatchValidationException("name is required");
        }
        if (isEmpty(batch.getCourseId())) {
            throw new BatchValidationException("courseId is required");
        }
        if (batch.getSemesterNo() <= 0 || batch.getSemesterNo() > 12) {
            throw new BatchValidationException("ACD_BATCH_SEMESTER_INVALID", "semesterNo must be between 1 and 12");
        }
        if (isEmpty(batch.getAcademicYear())) {
            throw new BatchValidationException("ACD_BATCH_SEMESTER_INVALID", "academicYear is required");
        }
        if (batch.getCapacity() <= 0) {
            throw new BatchValidationException("capacity must be positive");
        }

        // BR-01: Validate courseId resolves to active course in ACD-01 (Story 4)
        Boolean courseActive = activeCourseRegistry.get(batch.getCourseId());
        if (courseActive == null || !courseActive) {
            throw new BatchValidationException("ACD_BATCH_COURSE_INVALID",
                    "Course " + batch.getCourseId() + " is invalid or inactive in ACD-01");
        }

        // Validate curriculumId resolves to active curriculum in ACD-02 if specified
        if (!isEmpty(batch.getCurriculumId())) {
            Boolean currActive = activeCurriculumRegistry.get(batch.getCurriculumId());
            if (currActive == null || !currActive) {
                throw new BatchValidationException("ACD_BATCH_CURRICULUM_INVALID",
                        "Curriculum " + batch.getCurriculumId() + " is retired or inactive in ACD-02");
            }
        }

        // BR-02: Enforce batchCode uniqueness within scope (Story 5)
        for (Batch existing : batches.values()) {
            if (existing.getTenantId().equals(batch.getTenantId())
                    && existing.getBatchCode().equalsIgnoreCase(batch.getBatchCode())) {
                throw new BatchConflictException("ACD_BATCH_CODE_DUPLICATE",
                        "Batch code already exists in this scope: " + batch.getBatchCode());
            }
        }

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            if (batch.getId() == null || batch.getId().trim().isEmpty()) {
                batch.setId("BATCH-" + batchIdSeq.incrementAndGet());
            }
            batch.setStatus(BatchStatus.DRAFT);
            batch.setRosterCount(0);
            batch.setVersion(1L);
            long now = System.currentTimeMillis();
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            batch.setCreatedBy(actorId);
            batch.setUpdatedBy(actorId);

            // Persist Batch
            batches.put(batch.getId(), batch);
            tx.addRollback(() -> batches.remove(batch.getId()));

            // Audit History (Story 56)
            BatchHistory history = recordHistory(batch.getId(), "CREATE", null, BatchStatus.DRAFT.name(),
                    actorId, actorRole, "Initial batch draft creation", null, serializeBatch(batch));
            tx.addRollback(() -> histories.remove(history));

            // Outbox Event (Story 41)
            OutboxEvent event = publishOutboxEvent("BatchCreated", batch.getId(), serializeBatch(batch));
            tx.addRollback(() -> outbox.remove(event.getId()));

            tx.commit();
            logger.info("Created batch draft: id={}, code={}", batch.getId(), batch.getBatchCode());
            return batch.copy();
        } catch (Exception ex) {
            tx.rollback();
            throw ex;
        }
    }

    /**
     * Updates fields of a draft or open batch with optimistic concurrency (Story 7, 49).
     */
    public Batch updateBatch(String batchId, Batch updateReq, long expectedVersion, String actorId, String actorRole) {
        Batch existing = batches.get(batchId);
        if (existing == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        // Only DRAFT or OPEN batches can be updated (Story 7)
        if (existing.getStatus() != BatchStatus.DRAFT && existing.getStatus() != BatchStatus.OPEN) {
            throw new BatchConflictException("ACD_BATCH_INVALID_STATE",
                    "Batch can only be updated in DRAFT or OPEN status. Current status: " + existing.getStatus());
        }

        // Optimistic concurrency check (Story 49, BR-11)
        if (existing.getVersion() != expectedVersion) {
            throw new BatchConflictException("ACD_BATCH_VERSION_CONFLICT",
                    "Stale version detected. Expected: " + expectedVersion + ", actual: " + existing.getVersion());
        }

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            String beforeJson = serializeBatch(existing);
            Batch updated = existing.copy();

            if (!isEmpty(updateReq.getName())) updated.setName(updateReq.getName());
            if (!isEmpty(updateReq.getDepartmentId())) updated.setDepartmentId(updateReq.getDepartmentId());
            if (!isEmpty(updateReq.getCampusId())) updated.setCampusId(updateReq.getCampusId());
            if (!isEmpty(updateReq.getCurriculumId())) updated.setCurriculumId(updateReq.getCurriculumId());
            if (!isEmpty(updateReq.getAcademicYear())) updated.setAcademicYear(updateReq.getAcademicYear());
            if (updateReq.getSemesterNo() > 0 && updateReq.getSemesterNo() <= 12) {
                updated.setSemesterNo(updateReq.getSemesterNo());
            }

            updated.setVersion(existing.getVersion() + 1);
            updated.setUpdatedAt(System.currentTimeMillis());
            updated.setUpdatedBy(actorId);

            batches.put(batchId, updated);
            tx.addRollback(() -> batches.put(batchId, existing));

            // Audit History
            BatchHistory history = recordHistory(batchId, "UPDATE", existing.getStatus().name(),
                    updated.getStatus().name(), actorId, actorRole, "Updated batch metadata", beforeJson, serializeBatch(updated));
            tx.addRollback(() -> histories.remove(history));

            // Outbox Event
            OutboxEvent event = publishOutboxEvent("BatchUpdated", batchId, serializeBatch(updated));
            tx.addRollback(() -> outbox.remove(event.getId()));

            tx.commit();
            return updated.copy();
        } catch (Exception ex) {
            tx.rollback();
            throw ex;
        }
    }

    /**
     * Retrieves full detail of a specific batch, scoped by RBAC and data classification (Story 8, 11, 52, 53, 57).
     */
    public Batch getBatch(String batchId, String actorRole, String userId) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        // Student Role Restriction: Students can only view their own batch (Story 11)
        if ("STUDENT".equalsIgnoreCase(actorRole)) {
            boolean enrolled = isStudentEnrolledInBatch(batchId, userId);
            if (!enrolled) {
                throw new BatchForbiddenException("Students can only view their own assigned batch");
            }
        }

        return filterBatchByClearance(batch.copy(), actorRole);
    }

    /**
     * Search and filter batches with pagination and RBAC scope (Story 10, 11).
     */
    public List<Batch> searchBatches(Map<String, String> filters, int page, int pageSize, String actorRole, String userId) {
        List<Batch> results = new ArrayList<>();

        for (Batch b : batches.values()) {
            // Student restriction: only include student's own batch
            if ("STUDENT".equalsIgnoreCase(actorRole)) {
                if (!isStudentEnrolledInBatch(b.getId(), userId)) {
                    continue;
                }
            }

            if (filters != null && !filters.isEmpty()) {
                String courseId = filters.get("courseId");
                if (!isEmpty(courseId) && !courseId.equalsIgnoreCase(b.getCourseId())) continue;

                String academicYear = filters.get("academicYear");
                if (!isEmpty(academicYear) && !academicYear.equalsIgnoreCase(b.getAcademicYear())) continue;

                String semesterNoStr = filters.get("semesterNo");
                if (!isEmpty(semesterNoStr)) {
                    try {
                        int sem = Integer.parseInt(semesterNoStr);
                        if (b.getSemesterNo() != sem) continue;
                    } catch (NumberFormatException ignored) {}
                }

                String departmentId = filters.get("departmentId");
                if (!isEmpty(departmentId) && !departmentId.equalsIgnoreCase(b.getDepartmentId())) continue;

                String statusStr = filters.get("status");
                if (!isEmpty(statusStr) && !statusStr.equalsIgnoreCase(b.getStatus().name())) continue;

                String campusId = filters.get("campusId");
                if (!isEmpty(campusId) && !campusId.equalsIgnoreCase(b.getCampusId())) continue;
            }

            results.add(filterBatchByClearance(b.copy(), actorRole));
        }

        // Sort by batchCode
        results.sort(Comparator.comparing(Batch::getBatchCode));

        // Pagination
        int fromIndex = Math.max(0, (page - 1) * pageSize);
        if (fromIndex >= results.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(results.size(), fromIndex + pageSize);
        return results.subList(fromIndex, toIndex);
    }

    public int countSearchResults(Map<String, String> filters, String actorRole, String userId) {
        return searchBatches(filters, 1, Integer.MAX_VALUE, actorRole, userId).size();
    }

    // =========================================================================
    // 2. Batch Section Management (Stories 13-16)
    // =========================================================================

    /**
     * Creates a section within a batch aggregate with unique sectionCode (Story 13).
     */
    public BatchSection createSection(String batchId, BatchSection section, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }
        if (section == null || isEmpty(section.getSectionCode()) || isEmpty(section.getSectionName())) {
            throw new BatchValidationException("sectionCode and sectionName are required");
        }
        if (section.getCapacity() <= 0) {
            throw new BatchValidationException("Section capacity must be positive");
        }

        // Enforce unique sectionCode within the batch aggregate
        for (BatchSection s : batch.getSections()) {
            if (s.getSectionCode().equalsIgnoreCase(section.getSectionCode())) {
                throw new BatchConflictException("ACD_BATCH_SECTION_CODE_DUPLICATE",
                        "Section code already exists in batch: " + section.getSectionCode());
            }
        }

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            if (section.getId() == null || section.getId().trim().isEmpty()) {
                section.setId("SEC-" + sectionIdSeq.incrementAndGet());
            }
            section.setBatchId(batchId);
            section.setStatus(SectionStatus.ACTIVE);
            long now = System.currentTimeMillis();
            section.setCreatedAt(now);
            section.setUpdatedAt(now);

            batch.getSections().add(section);
            batch.setVersion(batch.getVersion() + 1);
            batch.setUpdatedAt(now);
            batch.setUpdatedBy(actorId);

            tx.addRollback(() -> batch.getSections().remove(section));

            recordHistory(batchId, "SECTION_ADD", null, null, actorId, actorRole,
                    "Added section " + section.getSectionCode(), null, serializeSection(section));
            publishOutboxEvent("BatchSectionCreated", batchId, serializeSection(section));

            tx.commit();
            logger.info("Added section {} to batch {}", section.getSectionCode(), batchId);
            return section.copy();
        } catch (Exception ex) {
            tx.rollback();
            throw ex;
        }
    }

    /**
     * Lists all sections defined in a batch (Story 14).
     */
    public List<BatchSection> listSections(String batchId) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }
        List<BatchSection> copy = new ArrayList<>();
        for (BatchSection s : batch.getSections()) {
            copy.add(s.copy());
        }
        return copy;
    }

    /**
     * Optionally assigns faculty reference to a section (Story 15).
     */
    public BatchSection assignFacultyToSection(String batchId, String sectionId, String facultyId, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }
        BatchSection target = null;
        for (BatchSection s : batch.getSections()) {
            if (s.getId().equals(sectionId) || s.getSectionCode().equalsIgnoreCase(sectionId)) {
                target = s;
                break;
            }
        }
        if (target == null) {
            throw new BatchNotFoundException("Section not found in batch: " + sectionId);
        }

        target.setFacultyId(facultyId);
        target.setUpdatedAt(System.currentTimeMillis());
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdatedAt(System.currentTimeMillis());

        recordHistory(batchId, "FACULTY_ASSIGN", null, null, actorId, actorRole,
                "Assigned faculty " + facultyId + " to section " + target.getSectionCode(), null, null);
        publishOutboxEvent("FacultyAssignedToSection", batchId, "{\"sectionId\":\"" + target.getId() + "\",\"facultyId\":\"" + facultyId + "\"}");

        return target.copy();
    }

    // =========================================================================
    // 3. Capacity Management & Overrides (Stories 18-21, 72)
    // =========================================================================

    /**
     * Sets or updates batch configured capacity (Story 18, BR-03).
     */
    public Batch updateCapacity(String batchId, int newCapacity, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }
        if (newCapacity <= 0) {
            throw new BatchValidationException("Capacity must be positive");
        }
        // BR-03: Capacity cannot be set lower than currently active roster count
        if (newCapacity < batch.getRosterCount()) {
            throw new BatchValidationException("ACD_BATCH_CAPACITY_INVALID",
                    "New capacity (" + newCapacity + ") cannot be less than current roster count (" + batch.getRosterCount() + ")");
        }

        int oldCap = batch.getCapacity();
        batch.setCapacity(newCapacity);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdatedAt(System.currentTimeMillis());
        batch.setUpdatedBy(actorId);

        recordHistory(batchId, "CAPACITY_UPDATE", String.valueOf(oldCap), String.valueOf(newCapacity),
                actorId, actorRole, "Updated capacity", null, null);
        publishOutboxEvent("BatchCapacityUpdated", batchId, "{\"batchId\":\"" + batchId + "\",\"capacity\":" + newCapacity + "}");

        return batch.copy();
    }

    /**
     * Grants an authorized capacity override (Story 20, 72, UC-07).
     */
    public BatchCapacityOverride grantCapacityOverride(String batchId, BatchCapacityOverride override, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }
        if (override.getOverrideCapacity() <= batch.getCapacity()) {
            throw new BatchValidationException("Override capacity must be strictly greater than configured capacity: " + batch.getCapacity());
        }

        if (override.getId() == null || override.getId().trim().isEmpty()) {
            override.setId("OVR-" + overrideIdSeq.incrementAndGet());
        }
        override.setBatchId(batchId);
        override.setStatus(OverrideStatus.ACTIVE);
        override.setApprovedBy(actorId);
        long now = System.currentTimeMillis();
        override.setCreatedAt(now);
        override.setUpdatedAt(now);

        overrides.put(override.getId(), override);

        recordHistory(batchId, "OVERRIDE_GRANT", String.valueOf(batch.getCapacity()),
                String.valueOf(override.getOverrideCapacity()), actorId, actorRole, override.getReason(), null, null);
        publishOutboxEvent("CapacityOverrideGranted", batchId, serializeOverride(override));

        logger.info("Granted capacity override for batch {}: capacity={}", batchId, override.getOverrideCapacity());
        return override.copy();
    }

    /**
     * Revokes or expires an existing capacity override (Story 21, 72).
     */
    public BatchCapacityOverride revokeCapacityOverride(String batchId, String overrideId, String actorId, String actorRole) {
        BatchCapacityOverride override = overrides.get(overrideId);
        if (override == null || !override.getBatchId().equals(batchId)) {
            throw new BatchNotFoundException("Capacity override not found: " + overrideId);
        }

        override.setStatus(OverrideStatus.REVOKED);
        override.setUpdatedAt(System.currentTimeMillis());

        recordHistory(batchId, "OVERRIDE_REVOKE", OverrideStatus.ACTIVE.name(), OverrideStatus.REVOKED.name(),
                actorId, actorRole, "Explicitly revoked capacity override", null, null);
        publishOutboxEvent("CapacityOverrideRevoked", batchId, "{\"overrideId\":\"" + overrideId + "\"}");

        return override.copy();
    }

    /**
     * Evaluates effective capacity considering active unexpired overrides (BR-04, Story 19, 72).
     */
    public int getEffectiveCapacity(String batchId) {
        Batch batch = batches.get(batchId);
        if (batch == null) return 0;

        int effectiveCapacity = batch.getCapacity();
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (BatchCapacityOverride ovr : overrides.values()) {
            if (ovr.getBatchId().equals(batchId) && ovr.getStatus() == OverrideStatus.ACTIVE) {
                // Check if effectiveTo date has passed (Story 21, 72)
                if (ovr.getEffectiveTo() != null && !ovr.getEffectiveTo().isEmpty()) {
                    try {
                        Date expDate = sdf.parse(ovr.getEffectiveTo());
                        if (now > expDate.getTime() + 86400000L) { // end of that day
                            ovr.setStatus(OverrideStatus.EXPIRED);
                            continue;
                        }
                    } catch (Exception ignored) {}
                }
                if (ovr.getOverrideCapacity() > effectiveCapacity) {
                    effectiveCapacity = ovr.getOverrideCapacity();
                }
            }
        }
        return effectiveCapacity;
    }

    // =========================================================================
    // 4. Roster & Student Enrollment Management (Stories 23-29, 72, 73)
    // =========================================================================

    /**
     * Adds an eligible student to a batch roster with capacity and eligibility validation (Story 23, 24, 25, 27, 72).
     */
    public BatchRoster addStudentToBatch(String batchId, String studentId, String sectionId,
                                         MembershipType membershipType, String effectiveFrom,
                                         String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        // BR-07: Block new roster additions on CLOSED or ARCHIVED batch (Story 27, 73)
        if (batch.getStatus() == BatchStatus.CLOSED || batch.getStatus() == BatchStatus.ARCHIVED) {
            throw new BatchConflictException("ACD_BATCH_CLOSED",
                    "Cannot add student to a " + batch.getStatus() + " batch (BR-07)");
        }

        // Batch must be OPEN or ACTIVE to accept roster enrollment
        if (batch.getStatus() != BatchStatus.OPEN && batch.getStatus() != BatchStatus.ACTIVE) {
            throw new BatchConflictException("ACD_BATCH_NOT_OPEN",
                    "Batch is currently in " + batch.getStatus() + " status and not accepting enrollments");
        }

        // BR-05: Validate student eligibility via Student Management (Story 24)
        Boolean eligible = eligibleStudentRegistry.get(studentId);
        if (eligible == null || !eligible) {
            throw new BatchValidationException("ACD_BATCH_STUDENT_INELIGIBLE",
                    "Student " + studentId + " is not found or not eligible for roster enrollment per Student Management (BR-05)");
        }

        // BR-06: Prevent duplicate active assignment in the same batch (Story 25)
        for (BatchRoster existingRoster : rosters.values()) {
            if (existingRoster.getBatchId().equals(batchId)
                    && existingRoster.getStudentId().equalsIgnoreCase(studentId)
                    && existingRoster.getStatus() == MembershipStatus.ACTIVE) {
                throw new BatchConflictException("ACD_BATCH_ALREADY_ASSIGNED",
                        "Student " + studentId + " is already an active member of batch " + batchId + " (BR-06)");
            }
        }

        // BR-04: Total enrolled students cannot exceed capacity unless an authorized override exists (Story 19, 72)
        int effectiveCapacity = getEffectiveCapacity(batchId);
        if (batch.getRosterCount() >= effectiveCapacity) {
            throw new BatchCapacityExceededException(
                    "Total enrolled students (" + batch.getRosterCount() + ") has reached capacity limit (" + effectiveCapacity + ")");
        }

        // Optional section validation
        if (sectionId != null && !sectionId.isEmpty()) {
            boolean secFound = false;
            for (BatchSection s : batch.getSections()) {
                if (s.getId().equals(sectionId) || s.getSectionCode().equalsIgnoreCase(sectionId)) {
                    secFound = true;
                    sectionId = s.getId();
                    break;
                }
            }
            if (!secFound) {
                throw new BatchNotFoundException("Section " + sectionId + " not found in batch " + batchId);
            }
        }

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            BatchRoster roster = new BatchRoster();
            roster.setId("ROST-" + rosterIdSeq.incrementAndGet());
            roster.setBatchId(batchId);
            roster.setSectionId(sectionId);
            roster.setStudentId(studentId);
            roster.setMembershipType(membershipType != null ? membershipType : MembershipType.REGULAR);
            roster.setEffectiveFrom(effectiveFrom != null ? effectiveFrom : new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            roster.setStatus(MembershipStatus.ACTIVE);
            long now = System.currentTimeMillis();
            roster.setCreatedAt(now);
            roster.setUpdatedAt(now);
            roster.setCreatedBy(actorId);
            roster.setUpdatedBy(actorId);

            rosters.put(roster.getId(), roster);
            tx.addRollback(() -> rosters.remove(roster.getId()));

            // Increment batch rosterCount and version
            batch.setRosterCount(batch.getRosterCount() + 1);
            batch.setVersion(batch.getVersion() + 1);
            batch.setUpdatedAt(now);
            batch.setUpdatedBy(actorId);

            // Audit History
            BatchHistory history = recordHistory(batchId, "ROSTER_ADD", null, null, actorId, actorRole,
                    "Enrolled student " + studentId, null, serializeRoster(roster));
            tx.addRollback(() -> histories.remove(history));

            // Outbox Event (Story 23, 41)
            OutboxEvent event = publishOutboxEvent("StudentAddedToBatch", batchId, serializeRoster(roster));
            tx.addRollback(() -> outbox.remove(event.getId()));

            tx.commit();
            logger.info("Enrolled student {} in batch {}: count={}/{}", studentId, batchId, batch.getRosterCount(), effectiveCapacity);
            return roster.copy();
        } catch (Exception ex) {
            tx.rollback();
            throw ex;
        }
    }

    /**
     * Removes a student's membership from a batch roster (soft-end, preserves history) (Story 26, BR-09).
     */
    public BatchRoster removeStudentFromBatch(String batchId, String studentId, String reason, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        BatchRoster targetMembership = null;
        for (BatchRoster r : rosters.values()) {
            if (r.getBatchId().equals(batchId) && r.getStudentId().equalsIgnoreCase(studentId)
                    && r.getStatus() == MembershipStatus.ACTIVE) {
                targetMembership = r;
                break;
            }
        }

        if (targetMembership == null) {
            throw new BatchNotFoundException("Active membership for student " + studentId + " not found in batch " + batchId);
        }

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            long now = System.currentTimeMillis();
            targetMembership.setStatus(MembershipStatus.ENDED);
            targetMembership.setEffectiveTo(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            targetMembership.setUpdatedAt(now);
            targetMembership.setUpdatedBy(actorId);
            targetMembership.setNotes(reason);

            // Decrement batch roster count
            batch.setRosterCount(Math.max(0, batch.getRosterCount() - 1));
            batch.setVersion(batch.getVersion() + 1);
            batch.setUpdatedAt(now);
            batch.setUpdatedBy(actorId);

            BatchHistory history = recordHistory(batchId, "ROSTER_REMOVE", MembershipStatus.ACTIVE.name(),
                    MembershipStatus.ENDED.name(), actorId, actorRole, reason != null ? reason : "Student withdrawn from batch", null, null);
            tx.addRollback(() -> histories.remove(history));

            OutboxEvent event = publishOutboxEvent("StudentRemovedFromBatch", batchId,
                    "{\"batchId\":\"" + batchId + "\",\"studentId\":\"" + studentId + "\",\"reason\":\"" + escapeJson(reason) + "\"}");
            tx.addRollback(() -> outbox.remove(event.getId()));

            tx.commit();
            logger.info("Removed student {} from batch {}: count={}", studentId, batchId, batch.getRosterCount());
            return targetMembership.copy();
        } catch (Exception ex) {
            tx.rollback();
            throw ex;
        }
    }

    /**
     * Retrieves currently active roster for a batch (Story 28).
     */
    public List<BatchRoster> getRoster(String batchId, String actorRole, String userId) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        List<BatchRoster> list = new ArrayList<>();
        for (BatchRoster r : rosters.values()) {
            if (r.getBatchId().equals(batchId) && r.getStatus() == MembershipStatus.ACTIVE) {
                // If caller is student, only return student's own entry (Story 11)
                if ("STUDENT".equalsIgnoreCase(actorRole) && !r.getStudentId().equalsIgnoreCase(userId)) {
                    continue;
                }
                list.add(filterRosterByClearance(r.copy(), actorRole));
            }
        }
        list.sort(Comparator.comparing(BatchRoster::getStudentId));
        return list;
    }

    /**
     * Reconstructs point-in-time roster or retrieves historical membership audit entries (Story 29, 54).
     */
    public List<BatchRoster> getRosterHistory(String batchId, Long pointInTime, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        List<BatchRoster> list = new ArrayList<>();
        for (BatchRoster r : rosters.values()) {
            if (r.getBatchId().equals(batchId)) {
                if (pointInTime != null && pointInTime > 0) {
                    if (r.getCreatedAt() <= pointInTime) {
                        list.add(filterRosterByClearance(r.copy(), actorRole));
                    }
                } else {
                    list.add(filterRosterByClearance(r.copy(), actorRole));
                }
            }
        }
        list.sort(Comparator.comparing(BatchRoster::getCreatedAt));
        return list;
    }

    // =========================================================================
    // 5. Batch Lifecycle Governance (Stories 31-35, 73)
    // =========================================================================

    /**
     * Opens / Activates a draft batch (Story 31, 16).
     */
    public Batch openBatch(String batchId, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        // Story 35: Reject invalid lifecycle transitions
        if (batch.getStatus() != BatchStatus.DRAFT && batch.getStatus() != BatchStatus.OPEN) {
            throw new BatchConflictException("ACD_BATCH_INVALID_STATE_TRANSITION",
                    "Cannot open batch in status " + batch.getStatus() + ". Only DRAFT or OPEN batches can be opened/activated.");
        }

        // Story 16: If faculty assignment required for activation, check all sections
        if (facultyRequiredForActivation) {
            for (BatchSection s : batch.getSections()) {
                if (isEmpty(s.getFacultyId())) {
                    throw new BatchValidationException("ACD_BATCH_FACULTY_REQUIRED",
                            "Section " + s.getSectionCode() + " lacks required faculty assignment for activation");
                }
            }
        }

        BatchStatus from = batch.getStatus();
        batch.setStatus(BatchStatus.ACTIVE);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdatedAt(System.currentTimeMillis());
        batch.setUpdatedBy(actorId);

        recordHistory(batchId, "ACTIVATE", from.name(), BatchStatus.ACTIVE.name(), actorId, actorRole, "Activated batch", null, null);
        publishOutboxEvent("BatchOpened", batchId, serializeBatch(batch));
        publishOutboxEvent("BatchActivated", batchId, serializeBatch(batch));

        logger.info("Batch activated: {}", batchId);
        return batch.copy();
    }

    /**
     * Closes an active batch, blocking new enrollment while retaining history (Story 32, 73, FR-08).
     */
    public Batch closeBatch(String batchId, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        if (batch.getStatus() != BatchStatus.ACTIVE) {
            throw new BatchConflictException("ACD_BATCH_INVALID_STATE_TRANSITION",
                    "Cannot close batch in status " + batch.getStatus() + ". Only ACTIVE batches can be closed.");
        }

        BatchStatus from = batch.getStatus();
        batch.setStatus(BatchStatus.CLOSED);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdatedAt(System.currentTimeMillis());
        batch.setUpdatedBy(actorId);

        recordHistory(batchId, "CLOSE", from.name(), BatchStatus.CLOSED.name(), actorId, actorRole, "Closed batch", null, null);
        publishOutboxEvent("BatchClosed", batchId, serializeBatch(batch));

        logger.info("Batch closed: {}", batchId);
        return batch.copy();
    }

    /**
     * Reopens a closed batch where permitted by policy (Story 33).
     */
    public Batch reopenBatch(String batchId, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        if (batch.getStatus() != BatchStatus.CLOSED) {
            throw new BatchConflictException("ACD_BATCH_INVALID_STATE_TRANSITION",
                    "Cannot reopen batch in status " + batch.getStatus() + ". Only CLOSED batches can be reopened.");
        }

        BatchStatus from = batch.getStatus();
        batch.setStatus(BatchStatus.ACTIVE);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdatedAt(System.currentTimeMillis());
        batch.setUpdatedBy(actorId);

        recordHistory(batchId, "REOPEN", from.name(), BatchStatus.ACTIVE.name(), actorId, actorRole, "Reopened closed batch", null, null);
        publishOutboxEvent("BatchReopened", batchId, serializeBatch(batch));

        logger.info("Batch reopened: {}", batchId);
        return batch.copy();
    }

    /**
     * Archives a closed batch for long-term historical records (Story 34).
     */
    public Batch archiveBatch(String batchId, String actorId, String actorRole) {
        Batch batch = batches.get(batchId);
        if (batch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        if (batch.getStatus() != BatchStatus.CLOSED) {
            throw new BatchConflictException("ACD_BATCH_INVALID_STATE_TRANSITION",
                    "Cannot archive batch in status " + batch.getStatus() + ". Only CLOSED batches can be archived.");
        }

        BatchStatus from = batch.getStatus();
        batch.setStatus(BatchStatus.ARCHIVED);
        batch.setVersion(batch.getVersion() + 1);
        batch.setUpdatedAt(System.currentTimeMillis());
        batch.setUpdatedBy(actorId);

        recordHistory(batchId, "ARCHIVE", from.name(), BatchStatus.ARCHIVED.name(), actorId, actorRole, "Archived batch", null, null);
        publishOutboxEvent("BatchArchived", batchId, serializeBatch(batch));

        logger.info("Batch archived: {}", batchId);
        return batch.copy();
    }

    // =========================================================================
    // 6. Batch Split & Merge (Stories 37-39, 75, 76, 77)
    // =========================================================================

    /**
     * Requests a split of an over-enrolled batch into multiple batches (Story 37, 76).
     * ACD-04 transitions batch to PENDING_SPLIT_APPROVAL and emits BatchSplitApprovalRequested to ADM-02.
     */
    public BatchSplitRequest requestSplit(String batchId, BatchSplitRequest request, String actorId, String actorRole) {
        Batch sourceBatch = batches.get(batchId);
        if (sourceBatch == null) {
            throw new BatchNotFoundException("Batch not found: " + batchId);
        }

        if (sourceBatch.getStatus() != BatchStatus.ACTIVE && sourceBatch.getStatus() != BatchStatus.OPEN) {
            throw new BatchConflictException("ACD_BATCH_INVALID_STATE",
                    "Batch can only be split when ACTIVE or OPEN. Current status: " + sourceBatch.getStatus());
        }

        if (request.getProposedSections() == null || request.getProposedSections().size() < 2) {
            throw new BatchValidationException("Split request must propose at least two target sections/batches");
        }

        String reqId = "SPLIT-REQ-" + requestIdSeq.incrementAndGet();
        request.setRequestId(reqId);
        request.setRequestType("SPLIT");
        request.setSourceBatchId(batchId);
        request.setSourceBatchCode(sourceBatch.getBatchCode());
        request.setDepartmentId(sourceBatch.getDepartmentId());
        request.setCampusId(sourceBatch.getCampusId());
        request.setRequestedBy(actorId);
        request.setRequestedAt(System.currentTimeMillis());
        request.setApproverRole("REGISTRAR");
        request.setStatus("PENDING");

        // Transition source batch to PENDING_SPLIT_APPROVAL
        sourceBatch.setPriorStatus(sourceBatch.getStatus());
        sourceBatch.setStatus(BatchStatus.PENDING_SPLIT_APPROVAL);
        sourceBatch.setVersion(sourceBatch.getVersion() + 1);
        sourceBatch.setUpdatedAt(System.currentTimeMillis());

        splitRequests.put(reqId, request);

        recordHistory(batchId, "SPLIT_REQUEST", sourceBatch.getPriorStatus().name(),
                BatchStatus.PENDING_SPLIT_APPROVAL.name(), actorId, actorRole, request.getReason(), null, null);

        // Outbox event to ADM-02 College Admin workflow engine
        publishOutboxEvent("BatchSplitApprovalRequested", batchId, serializeSplitRequest(request));

        logger.info("Batch split requested for {}: requestId={}", batchId, reqId);
        return request;
    }

    /**
     * Requests a merge of two or more batches into one (Story 38, 76).
     */
    public BatchMergeRequest requestMerge(BatchMergeRequest request, String actorId, String actorRole) {
        if (request.getSourceBatchIds() == null || request.getSourceBatchIds().size() < 2) {
            throw new BatchValidationException("Merge request must specify at least two source batch IDs");
        }

        List<String> departmentIds = new ArrayList<>();
        for (String sId : request.getSourceBatchIds()) {
            Batch b = batches.get(sId);
            if (b == null) {
                throw new BatchNotFoundException("Source batch not found for merge: " + sId);
            }
            if (b.getStatus() != BatchStatus.ACTIVE && b.getStatus() != BatchStatus.OPEN) {
                throw new BatchConflictException("ACD_BATCH_INVALID_STATE",
                        "Source batch " + sId + " must be ACTIVE or OPEN to merge. Status: " + b.getStatus());
            }
            departmentIds.add(b.getDepartmentId() != null ? b.getDepartmentId() : "UNKNOWN");
        }

        String reqId = "MERGE-REQ-" + requestIdSeq.incrementAndGet();
        request.setRequestId(reqId);
        request.setRequestType("MERGE");
        request.setSourceBatchDepartmentIds(departmentIds);
        request.setRequestedBy(actorId);
        request.setRequestedAt(System.currentTimeMillis());
        request.setApproverRole("REGISTRAR");
        request.setStatus("PENDING");

        // Transition all source batches to PENDING_MERGE_APPROVAL
        for (String sId : request.getSourceBatchIds()) {
            Batch b = batches.get(sId);
            b.setPriorStatus(b.getStatus());
            b.setStatus(BatchStatus.PENDING_MERGE_APPROVAL);
            b.setVersion(b.getVersion() + 1);
            b.setUpdatedAt(System.currentTimeMillis());

            recordHistory(sId, "MERGE_REQUEST", b.getPriorStatus().name(),
                    BatchStatus.PENDING_MERGE_APPROVAL.name(), actorId, actorRole, request.getReason(), null, null);
        }

        mergeRequests.put(reqId, request);
        publishOutboxEvent("BatchMergeApprovalRequested", reqId, serializeMergeRequest(request));

        logger.info("Batch merge requested: requestId={}, sources={}", reqId, request.getSourceBatchIds());
        return request;
    }

    /**
     * Consumes an approval decision event from ADM-02 (BatchSplitApprovalDecided or BatchMergeApprovalDecided)
     * and executes or reverts the operation (Story 39, 77).
     * ACD-04 exposes NO local approve/reject endpoint.
     */
    public void consumeApprovalDecision(String requestId, SplitMergeDecision decision,
                                        String decidedBy, long decidedAt, String reason) {
        if (splitRequests.containsKey(requestId)) {
            handleSplitDecision(requestId, decision, decidedBy, decidedAt, reason);
        } else if (mergeRequests.containsKey(requestId)) {
            handleMergeDecision(requestId, decision, decidedBy, decidedAt, reason);
        } else {
            throw new BatchNotFoundException("No pending split or merge request found with ID: " + requestId);
        }
    }

    private void handleSplitDecision(String requestId, SplitMergeDecision decision,
                                     String decidedBy, long decidedAt, String reason) {
        BatchSplitRequest req = splitRequests.get(requestId);
        Batch sourceBatch = batches.get(req.getSourceBatchId());
        if (sourceBatch == null) return;

        req.setStatus(decision.name());

        if (decision == SplitMergeDecision.APPROVED) {
            // Split execution: create resulting batches and reassign students (Story 37, 77)
            List<String> resultingBatchIds = new ArrayList<>();
            List<RosterReassignmentItem> reassignments = new ArrayList<>();

            for (ProposedSectionSplit p : req.getProposedSections()) {
                Batch newBatch = new Batch();
                newBatch.setId("BATCH-" + batchIdSeq.incrementAndGet());
                newBatch.setBatchCode(sourceBatch.getBatchCode() + "-" + p.getSectionCode());
                newBatch.setName(sourceBatch.getName() + " - " + p.getSectionName());
                newBatch.setCourseId(sourceBatch.getCourseId());
                newBatch.setCurriculumId(sourceBatch.getCurriculumId());
                newBatch.setDepartmentId(sourceBatch.getDepartmentId());
                newBatch.setCampusId(sourceBatch.getCampusId());
                newBatch.setAcademicYear(sourceBatch.getAcademicYear());
                newBatch.setSemesterNo(sourceBatch.getSemesterNo());
                newBatch.setCapacity(p.getCapacity());
                newBatch.setStatus(BatchStatus.ACTIVE);
                newBatch.setSplitSourceBatchId(sourceBatch.getId());
                long now = System.currentTimeMillis();
                newBatch.setCreatedAt(now);
                newBatch.setUpdatedAt(now);
                newBatch.setCreatedBy("ADM02_SPLIT_APPROVAL");

                // Reassign targeted students
                int enrolledCount = 0;
                for (String studentId : p.getTargetStudentIds()) {
                    // Update active roster in source batch
                    for (BatchRoster r : rosters.values()) {
                        if (r.getBatchId().equals(sourceBatch.getId())
                                && r.getStudentId().equalsIgnoreCase(studentId)
                                && r.getStatus() == MembershipStatus.ACTIVE) {
                            String oldSec = r.getSectionId();
                            r.setBatchId(newBatch.getId());
                            r.setSectionId(null);
                            r.setUpdatedAt(now);
                            enrolledCount++;
                            reassignments.add(new RosterReassignmentItem(studentId, sourceBatch.getId(), oldSec, newBatch.getId(), null));
                            break;
                        }
                    }
                }
                newBatch.setRosterCount(enrolledCount);
                batches.put(newBatch.getId(), newBatch);
                resultingBatchIds.add(newBatch.getId());
            }

            // Close source batch
            sourceBatch.setStatus(BatchStatus.CLOSED);
            sourceBatch.setRosterCount(0);
            sourceBatch.setUpdatedAt(System.currentTimeMillis());

            recordHistory(sourceBatch.getId(), "SPLIT_APPROVED", BatchStatus.PENDING_SPLIT_APPROVAL.name(),
                    BatchStatus.CLOSED.name(), decidedBy, "REGISTRAR", reason, null, null);

            // Publish BatchSplitApproved and BatchSplit with full reassignment map (Story 77)
            publishOutboxEvent("BatchSplitApproved", sourceBatch.getId(), "{\"requestId\":\"" + requestId + "\"}");
            publishOutboxEvent("BatchSplit", sourceBatch.getId(),
                    buildSplitEventPayload(sourceBatch.getId(), resultingBatchIds, reassignments));

            logger.info("Executed batch split for {}: resulting batches={}", sourceBatch.getId(), resultingBatchIds);
        } else {
            // Split rejected: revert to prior status
            BatchStatus prior = sourceBatch.getPriorStatus() != null ? sourceBatch.getPriorStatus() : BatchStatus.ACTIVE;
            sourceBatch.setStatus(prior);
            sourceBatch.setUpdatedAt(System.currentTimeMillis());

            recordHistory(sourceBatch.getId(), "SPLIT_REJECTED", BatchStatus.PENDING_SPLIT_APPROVAL.name(),
                    prior.name(), decidedBy, "REGISTRAR", reason, null, null);
            logger.info("Batch split rejected for {}: reverted to {}", sourceBatch.getId(), prior);
        }
    }

    private void handleMergeDecision(String requestId, SplitMergeDecision decision,
                                     String decidedBy, long decidedAt, String reason) {
        BatchMergeRequest req = mergeRequests.get(requestId);
        if (req == null) return;

        req.setStatus(decision.name());

        if (decision == SplitMergeDecision.APPROVED) {
            long now = System.currentTimeMillis();
            Batch targetBatch;
            if (req.getTargetBatchId() != null && batches.containsKey(req.getTargetBatchId())) {
                targetBatch = batches.get(req.getTargetBatchId());
            } else {
                // Create consolidated target batch
                targetBatch = new Batch();
                targetBatch.setId("BATCH-" + batchIdSeq.incrementAndGet());
                targetBatch.setBatchCode(req.getTargetBatchCode() != null ? req.getTargetBatchCode() : "BATCH-MRG-" + targetBatch.getId());
                targetBatch.setName(req.getTargetBatchName() != null ? req.getTargetBatchName() : "Merged Batch " + targetBatch.getId());
                Batch firstSource = batches.get(req.getSourceBatchIds().get(0));
                targetBatch.setCourseId(firstSource.getCourseId());
                targetBatch.setCurriculumId(firstSource.getCurriculumId());
                targetBatch.setDepartmentId(req.getDepartmentId() != null ? req.getDepartmentId() : firstSource.getDepartmentId());
                targetBatch.setCampusId(firstSource.getCampusId());
                targetBatch.setAcademicYear(firstSource.getAcademicYear());
                targetBatch.setSemesterNo(firstSource.getSemesterNo());
                targetBatch.setCapacity(firstSource.getCapacity() * req.getSourceBatchIds().size());
                targetBatch.setStatus(BatchStatus.ACTIVE);
                targetBatch.setMergeSourceBatchIds(new ArrayList<>(req.getSourceBatchIds()));
                targetBatch.setCreatedAt(now);
                targetBatch.setUpdatedAt(now);
                targetBatch.setCreatedBy("ADM02_MERGE_APPROVAL");
                batches.put(targetBatch.getId(), targetBatch);
            }

            List<RosterReassignmentItem> reassignments = new ArrayList<>();
            int totalMigrated = 0;

            for (String sId : req.getSourceBatchIds()) {
                Batch sBatch = batches.get(sId);
                if (sBatch == null) continue;

                // Reassign all active students to target batch
                for (BatchRoster r : rosters.values()) {
                    if (r.getBatchId().equals(sId) && r.getStatus() == MembershipStatus.ACTIVE) {
                        String oldSec = r.getSectionId();
                        r.setBatchId(targetBatch.getId());
                        r.setSectionId(null);
                        r.setUpdatedAt(now);
                        totalMigrated++;
                        reassignments.add(new RosterReassignmentItem(r.getStudentId(), sId, oldSec, targetBatch.getId(), null));
                    }
                }

                sBatch.setStatus(BatchStatus.CLOSED);
                sBatch.setRosterCount(0);
                sBatch.setUpdatedAt(now);

                recordHistory(sId, "MERGE_APPROVED", BatchStatus.PENDING_MERGE_APPROVAL.name(),
                        BatchStatus.CLOSED.name(), decidedBy, "REGISTRAR", reason, null, null);
            }

            targetBatch.setRosterCount(targetBatch.getRosterCount() + totalMigrated);
            targetBatch.setUpdatedAt(now);

            publishOutboxEvent("BatchMergeApproved", targetBatch.getId(), "{\"requestId\":\"" + requestId + "\"}");
            publishOutboxEvent("BatchMerged", targetBatch.getId(),
                    buildMergeEventPayload(req.getSourceBatchIds(), targetBatch.getId(), reassignments));

            logger.info("Executed batch merge into {}: totalStudents={}", targetBatch.getId(), totalMigrated);
        } else {
            // Revert all source batches
            for (String sId : req.getSourceBatchIds()) {
                Batch sBatch = batches.get(sId);
                if (sBatch != null) {
                    BatchStatus prior = sBatch.getPriorStatus() != null ? sBatch.getPriorStatus() : BatchStatus.ACTIVE;
                    sBatch.setStatus(prior);
                    sBatch.setUpdatedAt(System.currentTimeMillis());

                    recordHistory(sId, "MERGE_REJECTED", BatchStatus.PENDING_MERGE_APPROVAL.name(),
                            prior.name(), decidedBy, "REGISTRAR", reason, null, null);
                }
            }
            logger.info("Batch merge rejected: sources reverted");
        }
    }

    // =========================================================================
    // 7. Event-Driven Reliability & Upstream Ingestion (Stories 41-46)
    // =========================================================================

    /**
     * Publishes a domain event to the transactional outbox (Story 41).
     */
    public OutboxEvent publishOutboxEvent(String eventType, String aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId("EVT-" + eventIdSeq.incrementAndGet());
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setSource("ACD-04");
        event.setTenantId("TENANT-001");
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setOccurredAt(System.currentTimeMillis());
        event.setCorrelationId(LogContext.getTraceId());

        outbox.put(event.getId(), event);
        return event;
    }

    /**
     * Ingestion deduplication check using eventId (Story 45).
     */
    public boolean isEventProcessed(String eventId) {
        return inbox.containsKey(eventId);
    }

    public void markEventProcessed(String eventId, String eventType, String source, String payload) {
        InboxEvent in = new InboxEvent();
        in.setId("INB-" + eventIdSeq.incrementAndGet());
        in.setEventId(eventId);
        in.setEventType(eventType);
        in.setSource(source);
        in.setPayload(payload);
        in.setProcessedAt(System.currentTimeMillis());
        inbox.put(eventId, in);
    }

    /**
     * Routes unprocessable events to Dead Letter Queue (Story 46).
     */
    public void routeToDeadLetterQueue(String eventId, String source, String errorReason, String payload) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId("DLQ-" + eventIdSeq.incrementAndGet());
        dlq.setEventId(eventId);
        dlq.setSource(source);
        dlq.setErrorReason(errorReason);
        dlq.setPayload(payload);
        dlq.setAttempts(3);
        dlq.setCreatedAt(System.currentTimeMillis());
        deadLetterQueue.add(dlq);
        logger.error("Event routed to DLQ: id={}, reason={}", eventId, errorReason);
    }

    /**
     * Consumes StudentStatusChanged event from Student Management (Story 42).
     */
    public void consumeStudentStatusChanged(String eventId, String studentId, String newStatus) {
        if (isEventProcessed(eventId)) return;

        boolean active = "ACTIVE".equalsIgnoreCase(newStatus) || "ENROLLED".equalsIgnoreCase(newStatus);
        eligibleStudentRegistry.put(studentId, active);

        // If student is suspended or withdrawn, remove or flag active roster
        if (!active) {
            for (BatchRoster r : rosters.values()) {
                if (r.getStudentId().equalsIgnoreCase(studentId) && r.getStatus() == MembershipStatus.ACTIVE) {
                    r.setStatus(MembershipStatus.ENDED);
                    r.setEffectiveTo(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
                    r.setNotes("Student status changed to " + newStatus);

                    Batch b = batches.get(r.getBatchId());
                    if (b != null) {
                        b.setRosterCount(Math.max(0, b.getRosterCount() - 1));
                    }
                }
            }
        }
        markEventProcessed(eventId, "StudentStatusChanged", "STM", "{\"studentId\":\"" + studentId + "\"}");
    }

    /**
     * Consumes CourseDeactivated event from ACD-01 (Story 43).
     */
    public void consumeCourseDeactivated(String eventId, String courseId) {
        if (isEventProcessed(eventId)) return;

        activeCourseRegistry.put(courseId, false);
        for (Batch b : batches.values()) {
            if (courseId.equalsIgnoreCase(b.getCourseId())) {
                b.setCourseActive(false);
            }
        }
        markEventProcessed(eventId, "CourseDeactivated", "ACD-01", "{\"courseId\":\"" + courseId + "\"}");
    }

    /**
     * Consumes CurriculumPublished event from ACD-02.
     */
    public void consumeCurriculumPublished(String eventId, String curriculumId) {
        if (isEventProcessed(eventId)) return;
        if (curriculumId != null && !curriculumId.trim().isEmpty()) {
            activeCurriculumRegistry.put(curriculumId.trim(), true);
            logger.info("Synchronized active curriculum from ACD-02: {}", curriculumId);
        }
        markEventProcessed(eventId, "CurriculumPublished", "ACD-02", "{\"curriculumId\":\"" + curriculumId + "\"}");
    }

    /**
     * Consumes CurriculumRetired event from ACD-02.
     */
    public void consumeCurriculumRetired(String eventId, String curriculumId) {
        if (isEventProcessed(eventId)) return;
        if (curriculumId != null && !curriculumId.trim().isEmpty()) {
            activeCurriculumRegistry.put(curriculumId.trim(), false);
            logger.warn("Marked curriculum retired from ACD-02: {}", curriculumId);
        }
        markEventProcessed(eventId, "CurriculumRetired", "ACD-02", "{\"curriculumId\":\"" + curriculumId + "\"}");
    }

    /**
     * Simulates background relay of outbox events to message broker.
     */
    public int relayPendingOutboxEvents() {
        int relayed = 0;
        for (OutboxEvent evt : outbox.values()) {
            if ("PENDING".equals(evt.getStatus())) {
                evt.setStatus("PUBLISHED");
                evt.setAttempts(evt.getAttempts() + 1);
                relayed++;
            }
        }
        return relayed;
    }

    // =========================================================================
    // 8. Idempotency Support (Story 48, FR-14)
    // =========================================================================

    public IdempotencyRecord checkIdempotency(String idempotencyKey, String tenantId) {
        if (isEmpty(idempotencyKey)) return null;
        return idempotencyStore.get(tenantId + ":" + idempotencyKey);
    }

    public void saveIdempotencyRecord(String idempotencyKey, String tenantId, String operation, int statusCode, String responseBody) {
        if (isEmpty(idempotencyKey)) return;
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setId("IDEM-" + UUID.randomUUID().toString());
        rec.setTenantId(tenantId);
        rec.setIdempotencyKey(idempotencyKey);
        rec.setOperation(operation);
        rec.setStatusCode(statusCode);
        rec.setResponseBody(responseBody);
        long now = System.currentTimeMillis();
        rec.setCreatedAt(now);
        rec.setExpiresAt(now + 86400000L); // 24 hours
        idempotencyStore.put(tenantId + ":" + idempotencyKey, rec);
    }

    // =========================================================================
    // 9. API Key Validation & Security (Story 55, 57)
    // =========================================================================

    public ApiKeyRecord validateApiKey(String rawKey, String tenantId) {
        ApiKeyRecord rec = apiKeyStore.get(rawKey);
        if (rec != null && rec.isActive() && rec.getExpiresAt() > System.currentTimeMillis()) {
            return rec;
        }
        return null;
    }

    // =========================================================================
    // 10. Audit History Recording (Story 56)
    // =========================================================================

    public BatchHistory recordHistory(String batchId, String action, String fromStatus, String toStatus,
                                      String actorId, String actorRole, String reason, String beforeState, String afterState) {
        BatchHistory h = new BatchHistory();
        h.setId("HIST-" + historyIdSeq.incrementAndGet());
        h.setBatchId(batchId);
        h.setAction(action);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setActorId(actorId);
        h.setActorRole(actorRole);
        h.setTimestamp(System.currentTimeMillis());
        h.setBeforeState(beforeState);
        h.setAfterState(afterState);
        h.setReason(reason);
        h.setCorrelationId(LogContext.getTraceId());
        histories.add(h);
        return h;
    }

    public List<BatchHistory> getBatchHistory(String batchId) {
        List<BatchHistory> list = new ArrayList<>();
        for (BatchHistory h : histories) {
            if (h.getBatchId().equals(batchId)) {
                list.add(h);
            }
        }
        return list;
    }

    // =========================================================================
    // 11. Metrics & Reconciliation Queries (Stories 61, 62)
    // =========================================================================

    public Map<BatchStatus, Long> countBatchesByStatus() {
        Map<BatchStatus, Long> counts = new EnumMap<>(BatchStatus.class);
        for (BatchStatus status : BatchStatus.values()) {
            counts.put(status, 0L);
        }
        for (Batch b : batches.values()) {
            counts.put(b.getStatus(), counts.get(b.getStatus()) + 1);
        }
        return counts;
    }

    public long countActiveRosterMemberships() {
        long count = 0;
        for (BatchRoster r : rosters.values()) {
            if (r.getStatus() == MembershipStatus.ACTIVE) count++;
        }
        return count;
    }

    public long countActiveCapacityOverrides() {
        long count = 0;
        for (BatchCapacityOverride o : overrides.values()) {
            if (o.getStatus() == OverrideStatus.ACTIVE) count++;
        }
        return count;
    }

    public int getPendingOutboxCount() {
        int count = 0;
        for (OutboxEvent evt : outbox.values()) {
            if ("PENDING".equals(evt.getStatus())) count++;
        }
        return count;
    }

    public int getDeadLetterQueueCount() {
        return deadLetterQueue.size();
    }

    /**
     * Roster reconciliation check: counts batches where rosterCount does not equal
     * the actual number of active records in batch_rosters (Story 62).
     */
    public int checkRosterReconciliationMismatches() {
        int mismatches = 0;
        for (Batch b : batches.values()) {
            int actualCount = 0;
            for (BatchRoster r : rosters.values()) {
                if (r.getBatchId().equals(b.getId()) && r.getStatus() == MembershipStatus.ACTIVE) {
                    actualCount++;
                }
            }
            if (b.getRosterCount() != actualCount) {
                mismatches++;
                logger.warn("Roster mismatch on batch {}: recorded={}, actual={}", b.getId(), b.getRosterCount(), actualCount);
            }
        }
        return mismatches;
    }

    // =========================================================================
    // 12. Helper & Filtering Methods
    // =========================================================================

    private boolean isStudentEnrolledInBatch(String batchId, String studentId) {
        if (studentId == null || batchId == null) return false;
        for (BatchRoster r : rosters.values()) {
            if (r.getBatchId().equals(batchId)
                    && r.getStudentId().equalsIgnoreCase(studentId)
                    && r.getStatus() == MembershipStatus.ACTIVE) {
                return true;
            }
        }
        return false;
    }

    private Batch filterBatchByClearance(Batch batch, String actorRole) {
        // High-level public/internal views do not expose operational internals
        return batch;
    }

    private BatchRoster filterRosterByClearance(BatchRoster roster, String actorRole) {
        // Mask L3 Confidential fields (membershipSource, notes) for unauthorized roles (Story 57)
        if ("STUDENT".equalsIgnoreCase(actorRole) || "PARENT".equalsIgnoreCase(actorRole) || "EXTERNAL_API".equalsIgnoreCase(actorRole)) {
            BatchRoster masked = roster.copy();
            masked.setMembershipSource(null);
            masked.setNotes(null);
            return masked;
        }
        return roster;
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String serializeBatch(Batch b) {
        return "{\"batchId\":\"" + b.getId() + "\",\"batchCode\":\"" + b.getBatchCode()
                + "\",\"name\":\"" + escapeJson(b.getName()) + "\",\"courseId\":\"" + b.getCourseId()
                + "\",\"status\":\"" + b.getStatus() + "\",\"rosterCount\":" + b.getRosterCount()
                + ",\"capacity\":" + b.getCapacity() + ",\"version\":" + b.getVersion() + "}";
    }

    private String serializeSection(BatchSection s) {
        return "{\"sectionId\":\"" + s.getId() + "\",\"sectionCode\":\"" + s.getSectionCode()
                + "\",\"name\":\"" + escapeJson(s.getSectionName()) + "\",\"capacity\":" + s.getCapacity()
                + ",\"facultyId\":\"" + (s.getFacultyId() != null ? s.getFacultyId() : "") + "\"}";
    }

    private String serializeRoster(BatchRoster r) {
        return "{\"membershipId\":\"" + r.getId() + "\",\"batchId\":\"" + r.getBatchId()
                + "\",\"studentId\":\"" + r.getStudentId() + "\",\"status\":\"" + r.getStatus()
                + "\",\"effectiveFrom\":\"" + r.getEffectiveFrom() + "\"}";
    }

    private String serializeOverride(BatchCapacityOverride o) {
        return "{\"overrideId\":\"" + o.getId() + "\",\"batchId\":\"" + o.getBatchId()
                + "\",\"overrideCapacity\":" + o.getOverrideCapacity() + ",\"reason\":\"" + escapeJson(o.getReason()) + "\"}";
    }

    private String serializeSplitRequest(BatchSplitRequest r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"requestId\":\"").append(r.getRequestId()).append("\",");
        sb.append("\"requestType\":\"SPLIT\",");
        sb.append("\"sourceBatchId\":\"").append(r.getSourceBatchId()).append("\",");
        sb.append("\"sourceBatchCode\":\"").append(r.getSourceBatchCode()).append("\",");
        sb.append("\"departmentId\":\"").append(r.getDepartmentId()).append("\",");
        sb.append("\"campusId\":\"").append(r.getCampusId()).append("\",");
        sb.append("\"requestedBy\":\"").append(r.getRequestedBy()).append("\",");
        sb.append("\"requestedAt\":").append(r.getRequestedAt()).append(",");
        sb.append("\"reason\":\"").append(escapeJson(r.getReason())).append("\",");
        sb.append("\"approverRole\":\"REGISTRAR\",");
        sb.append("\"proposedSections\":[");
        for (int i = 0; i < r.getProposedSections().size(); i++) {
            ProposedSectionSplit p = r.getProposedSections().get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"sectionCode\":\"").append(p.getSectionCode()).append("\",\"capacity\":").append(p.getCapacity()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String serializeMergeRequest(BatchMergeRequest r) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"requestId\":\"").append(r.getRequestId()).append("\",");
        sb.append("\"requestType\":\"MERGE\",");
        sb.append("\"sourceBatchIds\":[");
        for (int i = 0; i < r.getSourceBatchIds().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(r.getSourceBatchIds().get(i)).append("\"");
        }
        sb.append("],");
        sb.append("\"requestedBy\":\"").append(r.getRequestedBy()).append("\",");
        sb.append("\"requestedAt\":").append(r.getRequestedAt()).append(",");
        sb.append("\"reason\":\"").append(escapeJson(r.getReason())).append("\",");
        sb.append("\"approverRole\":\"REGISTRAR\"");
        sb.append("}");
        return sb.toString();
    }

    private String buildSplitEventPayload(String sourceBatchId, List<String> resultingBatchIds, List<RosterReassignmentItem> reassignments) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"sourceBatchId\":\"").append(sourceBatchId).append("\",");
        sb.append("\"resultingBatchIds\":[");
        for (int i = 0; i < resultingBatchIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(resultingBatchIds.get(i)).append("\"");
        }
        sb.append("],\"reassignments\":[");
        for (int i = 0; i < reassignments.size(); i++) {
            RosterReassignmentItem item = reassignments.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"studentId\":\"").append(item.getStudentId())
              .append("\",\"oldBatchId\":\"").append(item.getOldBatchId())
              .append("\",\"newBatchId\":\"").append(item.getNewBatchId()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildMergeEventPayload(List<String> sourceBatchIds, String targetBatchId, List<RosterReassignmentItem> reassignments) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"sourceBatchIds\":[");
        for (int i = 0; i < sourceBatchIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(sourceBatchIds.get(i)).append("\"");
        }
        sb.append("],\"targetBatchId\":\"").append(targetBatchId).append("\",\"reassignments\":[");
        for (int i = 0; i < reassignments.size(); i++) {
            RosterReassignmentItem item = reassignments.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"studentId\":\"").append(item.getStudentId())
              .append("\",\"oldBatchId\":\"").append(item.getOldBatchId())
              .append("\",\"newBatchId\":\"").append(item.getNewBatchId()).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // Config setters for test simulation
    public void setFacultyRequiredForActivation(boolean required) {
        this.facultyRequiredForActivation = required;
    }

    public void registerCourse(String courseId, boolean active) {
        this.activeCourseRegistry.put(courseId, active);
    }

    public void registerStudent(String studentId, boolean eligible) {
        this.eligibleStudentRegistry.put(studentId, eligible);
    }
}
