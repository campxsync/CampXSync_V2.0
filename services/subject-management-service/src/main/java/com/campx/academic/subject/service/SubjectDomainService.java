package com.campx.academic.subject.service;

import com.campx.academic.subject.exception.*;
import com.campx.academic.subject.model.SubjectModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core Domain Service implementing all business logic, taxonomy rules,
 * lifecycle state machines, prerequisite DAG validation, versioning,
 * and event publishing for ACD-03 Subject Management Service.
 */
public class SubjectDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(SubjectDomainService.class);

    // In-Memory simulated MongoDB Collections (§35-42)
    private final Map<String, Subject> subjects = new ConcurrentHashMap<>();
    private final Map<String, List<SubjectVersion>> subjectVersions = new ConcurrentHashMap<>();
    private final Map<String, SubjectMetadata> subjectMetadata = new ConcurrentHashMap<>();
    private final Map<String, SubjectPrerequisite> subjectPrerequisites = new ConcurrentHashMap<>();
    private final List<SubjectHistory> historyRecords = Collections.synchronizedList(new ArrayList<>());
    private final List<OutboxEvent> outboxEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ApiKeyRecord> apiKeys = new ConcurrentHashMap<>();
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<>());

    // Active reference tracking: subjects referenced by active curriculum or batches (BR-05, Story 8, 80)
    private final Set<String> activeReferencedSubjectIds = Collections.synchronizedSet(new HashSet<>());

    // Department reference cache (BR-08)
    private final Map<String, Boolean> activeDepartments = new ConcurrentHashMap<>();

    private final AtomicLong eventSequence = new AtomicLong(1);

    public SubjectDomainService() {
        // Pre-populate standard active departments
        activeDepartments.put("DEPT-CA", true);
        activeDepartments.put("DEP_CSE_01", true);
        activeDepartments.put("DEP_CS", true);
        activeDepartments.put("DEP_MECH_01", true);
        activeDepartments.put("DEP_ECE_01", true);
        activeDepartments.put("DEPT-MATH", true);

        // Pre-seed mock API keys for testing
        apiKeys.put("key-ext-001", new ApiKeyRecord("key-ext-001", "campx_test_key_123", "TENANT-001",
                Arrays.asList("SUBJECT_VIEW", "SUBJECT_CATALOG_READ"), System.currentTimeMillis() + 86400000L));
    }

    // =========================================================================
    // 1. Subject Definition & Identity Management (Epic 1, 2)
    // =========================================================================

    /**
     * Creates a new reusable subject master and initial version (Story 3, FR-01).
     */
    public Subject createSubject(Subject subject, String academicYear, String actorId, String actorRole) {
        validateSubjectAttributes(subject);

        // BR-01: subjectCode uniqueness within tenant + institution
        ensureSubjectCodeUnique(subject.getTenantId(), subject.getInstitutionId(), subject.getSubjectCode(), null);

        // BR-08: Department existence and active state
        validateDepartment(subject.getDepartmentId());

        String id = subject.getId() != null ? subject.getId() : "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        subject.setId(id);
        if (subject.getStatus() == null) {
            subject.setStatus(SubjectStatus.ACTIVE);
        }
        subject.setCurrentVersion(1);
        subject.setVersion(1L);
        long now = System.currentTimeMillis();
        subject.setCreatedAt(now);
        subject.setUpdatedAt(now);
        subject.setCreatedBy(actorId);
        subject.setUpdatedBy(actorId);
        if (academicYear != null) {
            subject.setAcademicYear(academicYear);
        }

        // Create initial version at versionNo = 1
        SubjectVersion initialVersion = new SubjectVersion();
        initialVersion.setId("VER-" + id + "-1");
        initialVersion.setSubjectId(id);
        initialVersion.setTenantId(subject.getTenantId());
        initialVersion.setVersionNo(1);
        initialVersion.setStatus(subject.getStatus() == SubjectStatus.ACTIVE ? VersionStatus.PUBLISHED : VersionStatus.DRAFT);
        initialVersion.setAcademicYear(subject.getAcademicYear() != null ? subject.getAcademicYear() : "2026-2027");
        initialVersion.setCredits(subject.getCredits());
        initialVersion.setContactHours(subject.getContactHours());
        initialVersion.setSubjectType(subject.getSubjectType());
        initialVersion.setClassification(subject.getClassification());
        initialVersion.setElective(subject.isElective());
        initialVersion.setChangeSummary("Initial subject creation");
        initialVersion.setCreatedAt(now);
        initialVersion.setCreatedBy(actorId);
        initialVersion.setChecksum(generateChecksum(subject));

        // Atomic commit across aggregate, version, history, and outbox (§56)
        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            subjects.put(id, subject.copy());
            tx.addRollback(() -> subjects.remove(id));

            List<SubjectVersion> vList = new ArrayList<>();
            vList.add(initialVersion);
            subjectVersions.put(id, vList);
            tx.addRollback(() -> subjectVersions.remove(id));

            // Record History (Story 63)
            SubjectHistory hist = recordHistoryEntry(subject.getTenantId(), id, 1, "CREATE",
                    null, subject.getStatus().name(), Collections.singletonList("all"),
                    null, toJson(subject), "Initial creation", actorId, actorRole);
            tx.addRollback(() -> historyRecords.remove(hist));

            // Write Outbox Event (Story 50, §31)
            OutboxEvent outbox = createOutboxEvent(subject.getTenantId(), id, "SubjectCreated", toJson(subject));
            outboxEvents.add(outbox);
            tx.addRollback(() -> outboxEvents.remove(outbox));

            tx.commit();
            logger.info("Successfully created subject '{}' ({}) at version 1", subject.getName(), id);
            return subject;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    /**
     * Updates subject master fields with optimistic concurrency locking (Story 6, BR-12).
     */
    public Subject updateSubject(String id, Subject updateRequest, long clientVersion, String actorId, String actorRole) {
        Subject existing = subjects.get(id);
        if (existing == null) {
            throw new SubjectNotFoundException("Subject not found with id: " + id);
        }

        // Optimistic locking check (BR-12, Story 26)
        if (clientVersion > 0 && existing.getVersion() != clientVersion) {
            throw new VersionConflictException("Subject has been modified concurrently. Expected version: "
                    + clientVersion + ", current version: " + existing.getVersion());
        }

        if (updateRequest.getSubjectCode() != null && !updateRequest.getSubjectCode().equalsIgnoreCase(existing.getSubjectCode())) {
            ensureSubjectCodeUnique(existing.getTenantId(), existing.getInstitutionId(), updateRequest.getSubjectCode(), id);
            existing.setSubjectCode(updateRequest.getSubjectCode());
        }

        if (updateRequest.getDepartmentId() != null) {
            validateDepartment(updateRequest.getDepartmentId());
            existing.setDepartmentId(updateRequest.getDepartmentId());
        }

        List<String> changedFields = new ArrayList<>();
        String beforeJson = toJson(existing);

        if (updateRequest.getName() != null) {
            existing.setName(updateRequest.getName());
            changedFields.add("name");
        }
        if (updateRequest.getShortName() != null) {
            existing.setShortName(updateRequest.getShortName());
            changedFields.add("shortName");
        }
        if (updateRequest.getDescription() != null) {
            existing.setDescription(updateRequest.getDescription());
            changedFields.add("description");
        }
        if (updateRequest.getCampusId() != null) {
            existing.setCampusId(updateRequest.getCampusId());
            changedFields.add("campusId");
        }
        if (updateRequest.getSubjectType() != null) {
            validateTaxonomy(updateRequest.getSubjectType(), updateRequest.getClassification() != null ? updateRequest.getClassification() : existing.getClassification());
            existing.setSubjectType(updateRequest.getSubjectType());
            changedFields.add("subjectType");
        }
        if (updateRequest.getClassification() != null) {
            existing.setClassification(updateRequest.getClassification());
            changedFields.add("classification");
        }
        if (updateRequest.getCredits() > 0) {
            validateCredits(updateRequest.getCredits(), updateRequest.getContactHours() > 0 ? updateRequest.getContactHours() : existing.getContactHours());
            existing.setCredits(updateRequest.getCredits());
            changedFields.add("credits");
        }
        if (updateRequest.getContactHours() >= 0) {
            existing.setContactHours(updateRequest.getContactHours());
            changedFields.add("contactHours");
        }

        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(actorId);

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            subjects.put(id, existing.copy());

            SubjectHistory hist = recordHistoryEntry(existing.getTenantId(), id, existing.getCurrentVersion(),
                    "UPDATE", existing.getStatus().name(), existing.getStatus().name(),
                    changedFields, beforeJson, toJson(existing), "Master field update", actorId, actorRole);
            tx.addRollback(() -> historyRecords.remove(hist));

            OutboxEvent outbox = createOutboxEvent(existing.getTenantId(), id, "SubjectUpdated", toJson(existing));
            outboxEvents.add(outbox);
            tx.addRollback(() -> outboxEvents.remove(outbox));

            tx.commit();
            logger.info("Subject '{}' ({}) updated successfully to version lock {}", existing.getName(), id, existing.getVersion());
            return existing;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    /**
     * Retrieves subject details (Story 7, UC-02).
     */
    public Subject getSubject(String id) {
        Subject s = subjects.get(id);
        if (s == null) {
            throw new SubjectNotFoundException("Subject not found with id: " + id);
        }
        return s.copy();
    }

    /**
     * Prevents hard deletion of a referenced or in-use subject (Story 8, BR-05, Exceptions §15).
     */
    public boolean deleteSubject(String id, String actorId, String actorRole) {
        Subject existing = subjects.get(id);
        if (existing == null) {
            throw new SubjectNotFoundException("Subject not found with id: " + id);
        }

        // BR-05: Check if referenced in curriculum or active batches
        if (activeReferencedSubjectIds.contains(id)) {
            throw new SubjectReferencedException(id, "curriculum mapping or active batch offerings");
        }

        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            subjects.remove(id);
            tx.addRollback(() -> subjects.put(id, existing));

            recordHistoryEntry(existing.getTenantId(), id, existing.getCurrentVersion(), "DELETE",
                    existing.getStatus().name(), "DELETED", Collections.singletonList("all"),
                    toJson(existing), null, "Hard deletion", actorId, actorRole);

            tx.commit();
            logger.warn("Subject {} hard deleted by {}", id, actorId);
            return true;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    public void addReferencedSubject(String subjectId) {
        activeReferencedSubjectIds.add(subjectId);
    }

    public void removeReferencedSubject(String subjectId) {
        activeReferencedSubjectIds.remove(subjectId);
    }

    // =========================================================================
    // 2. Subject Version Management (Epic 4, FR-06, FR-07, BR-06, BR-07, BR-10)
    // =========================================================================

    /**
     * Creates a new monotonic draft version of a subject (Story 19, 23, BR-10).
     */
    public SubjectVersion createSubjectVersion(String subjectId, SubjectVersion newVersionData, String actorId, String actorRole) {
        Subject existing = subjects.get(subjectId);
        if (existing == null) {
            throw new SubjectNotFoundException("Subject not found with id: " + subjectId);
        }

        List<SubjectVersion> versions = subjectVersions.computeIfAbsent(subjectId, k -> new ArrayList<>());
        synchronized (versions) {
            int nextVersionNo = versions.size() + 1;
            SubjectVersion version = new SubjectVersion();
            version.setId("VER-" + subjectId + "-" + nextVersionNo);
            version.setSubjectId(subjectId);
            version.setTenantId(existing.getTenantId());
            version.setVersionNo(nextVersionNo);
            version.setStatus(VersionStatus.DRAFT);
            version.setAcademicYear(newVersionData.getAcademicYear() != null ? newVersionData.getAcademicYear() : existing.getAcademicYear());
            version.setCredits(newVersionData.getCredits() > 0 ? newVersionData.getCredits() : existing.getCredits());
            version.setContactHours(newVersionData.getContactHours() >= 0 ? newVersionData.getContactHours() : existing.getContactHours());
            version.setSubjectType(newVersionData.getSubjectType() != null ? newVersionData.getSubjectType() : existing.getSubjectType());
            version.setClassification(newVersionData.getClassification() != null ? newVersionData.getClassification() : existing.getClassification());
            version.setElective(newVersionData.isElective());
            version.setChangeSummary(newVersionData.getChangeSummary() != null ? newVersionData.getChangeSummary() : "New version created");
            version.setCreatedAt(System.currentTimeMillis());
            version.setCreatedBy(actorId);
            version.setChecksum(generateChecksum(existing));

            versions.add(version);

            recordHistoryEntry(existing.getTenantId(), subjectId, nextVersionNo, "VERSION_CREATED",
                    null, VersionStatus.DRAFT.name(), Collections.singletonList("versionNo"),
                    null, toJson(version), version.getChangeSummary(), actorId, actorRole);

            OutboxEvent outbox = createOutboxEvent(existing.getTenantId(), subjectId, "SubjectVersionCreated", toJson(version));
            outboxEvents.add(outbox);

            logger.info("Created draft version {} for subject {}", nextVersionNo, subjectId);
            return version;
        }
    }

    /**
     * Updates fields of a draft subject version (Story 20, 22, BR-06).
     * Rejects updates to already PUBLISHED versions with 409 Conflict.
     */
    public SubjectVersion updateDraftSubjectVersion(String subjectId, int versionNo, SubjectVersion updateData, String actorId, String actorRole) {
        SubjectVersion version = getSubjectVersion(subjectId, versionNo);

        // BR-06: Published subject versions are immutable!
        if (version.getStatus() == VersionStatus.PUBLISHED || version.getStatus() == VersionStatus.SUPERSEDED) {
            throw new VersionImmutableException(subjectId, versionNo);
        }

        if (updateData.getCredits() > 0) {
            validateCredits(updateData.getCredits(), updateData.getContactHours() >= 0 ? updateData.getContactHours() : version.getContactHours());
            version.setCredits(updateData.getCredits());
        }
        if (updateData.getContactHours() >= 0) {
            version.setContactHours(updateData.getContactHours());
        }
        if (updateData.getSubjectType() != null) {
            validateTaxonomy(updateData.getSubjectType(), updateData.getClassification() != null ? updateData.getClassification() : version.getClassification());
            version.setSubjectType(updateData.getSubjectType());
        }
        if (updateData.getClassification() != null) {
            version.setClassification(updateData.getClassification());
        }
        if (updateData.getChangeSummary() != null) {
            version.setChangeSummary(updateData.getChangeSummary());
        }
        if (updateData.getAcademicYear() != null) {
            version.setAcademicYear(updateData.getAcademicYear());
        }
        version.setUpdatedAt(System.currentTimeMillis());
        version.setUpdatedBy(actorId);

        recordHistoryEntry(version.getTenantId(), subjectId, versionNo, "VERSION_UPDATED",
                version.getStatus().name(), version.getStatus().name(),
                Collections.singletonList("draftFields"), null, toJson(version), "Draft revision", actorId, actorRole);

        logger.info("Draft version {} of subject {} updated", versionNo, subjectId);
        return version;
    }

    /**
     * Publishes a subject version and marks prior published versions as SUPERSEDED (Story 21, UC-06).
     */
    public SubjectVersion publishSubjectVersion(String subjectId, int versionNo, String approverId, String approverRole) {
        Subject existing = subjects.get(subjectId);
        if (existing == null) {
            throw new SubjectNotFoundException("Subject not found with id: " + subjectId);
        }

        List<SubjectVersion> versions = subjectVersions.get(subjectId);
        if (versions == null) {
            throw new SubjectNotFoundException("No versions exist for subject " + subjectId);
        }

        SubjectVersion target = null;
        SubjectVersion priorPublished = null;
        long now = System.currentTimeMillis();

        synchronized (versions) {
            for (SubjectVersion v : versions) {
                if (v.getVersionNo() == versionNo) {
                    target = v;
                } else if (v.getStatus() == VersionStatus.PUBLISHED) {
                    priorPublished = v;
                }
            }

            if (target == null) {
                throw new SubjectNotFoundException("Version " + versionNo + " not found for subject " + subjectId);
            }

            // Mark prior published as SUPERSEDED
            if (priorPublished != null) {
                priorPublished.setStatus(VersionStatus.SUPERSEDED);
                priorPublished.setEffectiveTo(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date(now)));
                outboxEvents.add(createOutboxEvent(existing.getTenantId(), subjectId, "SubjectVersionSuperseded", toJson(priorPublished)));
            }

            target.setStatus(VersionStatus.PUBLISHED);
            target.setApprovedBy(approverId);
            target.setApprovedAt(now);
            target.setPublishedBy(approverId);
            target.setPublishedAt(now);
            target.setEffectiveFrom(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date(now)));

            // Update Subject Master
            existing.setCurrentVersion(versionNo);
            existing.setCredits(target.getCredits());
            existing.setContactHours(target.getContactHours());
            existing.setSubjectType(target.getSubjectType());
            existing.setClassification(target.getClassification());
            existing.setStatus(SubjectStatus.ACTIVE);
            existing.setUpdatedAt(now);
            existing.setUpdatedBy(approverId);
            existing.setVersion(existing.getVersion() + 1);

            // Emit SubjectVersionPublished event
            outboxEvents.add(createOutboxEvent(existing.getTenantId(), subjectId, "SubjectVersionPublished", toJson(target)));

            // Record History
            recordHistoryEntry(existing.getTenantId(), subjectId, versionNo, "VERSION_PUBLISHED",
                    VersionStatus.DRAFT.name(), VersionStatus.PUBLISHED.name(),
                    Arrays.asList("status", "publishedAt", "publishedBy"),
                    null, toJson(target), "Published version " + versionNo, approverId, approverRole);

            logger.info("Published version {} of subject {}", versionNo, subjectId);
            return target;
        }
    }

    public List<SubjectVersion> getSubjectVersions(String subjectId) {
        List<SubjectVersion> versions = subjectVersions.get(subjectId);
        if (versions == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(versions);
    }

    public SubjectVersion getSubjectVersion(String subjectId, int versionNo) {
        List<SubjectVersion> versions = subjectVersions.get(subjectId);
        if (versions != null) {
            for (SubjectVersion v : versions) {
                if (v.getVersionNo() == versionNo) {
                    return v;
                }
            }
        }
        throw new SubjectNotFoundException("Subject version " + versionNo + " not found for subject: " + subjectId);
    }

    // =========================================================================
    // 3. Subject Prerequisite & Co-requisite Management (Epic 6, §39)
    // =========================================================================

    /**
     * Adds a prerequisite or co-requisite edge with DAG cycle detection (Story 32, 33, 34, 35).
     */
    public SubjectPrerequisite addPrerequisite(String subjectId, SubjectPrerequisite req, String actorId, String actorRole) {
        String prereqId = req.getPrerequisiteSubjectId();
        if (subjectId.equalsIgnoreCase(prereqId)) {
            throw new SubjectValidationException("Subject cannot be a prerequisite of itself: " + subjectId);
        }

        Subject current = subjects.get(subjectId);
        if (current == null) {
            throw new SubjectNotFoundException("Subject not found: " + subjectId);
        }

        Subject target = subjects.get(prereqId);
        if (target == null) {
            throw new SubjectNotFoundException("Prerequisite subject not found: " + prereqId);
        }

        // Story 35: Block deactivated subjects from being newly introduced as prerequisites
        if (target.getStatus() == SubjectStatus.DEACTIVATED || target.getStatus() == SubjectStatus.RETIRED) {
            throw new SubjectValidationException("Cannot add deactivated or retired subject '" + prereqId + "' as prerequisite");
        }

        // Story 34: Prevent duplicate active prerequisite relationships
        for (SubjectPrerequisite existing : subjectPrerequisites.values()) {
            if (existing.getSubjectId().equalsIgnoreCase(subjectId)
                    && existing.getPrerequisiteSubjectId().equalsIgnoreCase(prereqId)
                    && "ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new DuplicatePrerequisiteException(subjectId, prereqId);
            }
        }

        // Story 33: DAG Cycle Prevention via Depth-First Search
        if (wouldCreateCycle(subjectId, prereqId)) {
            throw new PrerequisiteCycleException(subjectId, prereqId);
        }

        String relId = "PREREQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        req.setId(relId);
        req.setSubjectId(subjectId);
        req.setTenantId(current.getTenantId());
        req.setInstitutionId(current.getInstitutionId());
        req.setStatus("ACTIVE");
        long now = System.currentTimeMillis();
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        req.setCreatedBy(actorId);
        req.setUpdatedBy(actorId);
        req.setVersion(1L);

        subjectPrerequisites.put(relId, req);

        // Emit outbox event & history
        outboxEvents.add(createOutboxEvent(current.getTenantId(), subjectId, "SubjectPrerequisiteAdded", toJson(req)));
        recordHistoryEntry(current.getTenantId(), subjectId, current.getCurrentVersion(), "PREREQUISITE_ADDED",
                null, "ACTIVE", Collections.singletonList("prerequisites"),
                null, toJson(req), "Added prerequisite " + prereqId, actorId, actorRole);

        logger.info("Added prerequisite '{}' -> '{}' ({})", subjectId, prereqId, relId);
        return req;
    }

    /**
     * Soft-deactivates/removes a prerequisite edge (Story 36).
     */
    public boolean removePrerequisite(String subjectId, String prerequisiteId, String actorId, String actorRole) {
        SubjectPrerequisite found = null;
        for (SubjectPrerequisite sp : subjectPrerequisites.values()) {
            if (sp.getId().equalsIgnoreCase(prerequisiteId) ||
                    (sp.getSubjectId().equalsIgnoreCase(subjectId) && sp.getPrerequisiteSubjectId().equalsIgnoreCase(prerequisiteId))) {
                found = sp;
                break;
            }
        }

        if (found == null) {
            throw new SubjectNotFoundException("Prerequisite relationship not found between " + subjectId + " and " + prerequisiteId);
        }

        found.setStatus("INACTIVE");
        found.setUpdatedAt(System.currentTimeMillis());
        found.setUpdatedBy(actorId);

        outboxEvents.add(createOutboxEvent(found.getTenantId(), subjectId, "SubjectPrerequisiteRemoved", toJson(found)));
        recordHistoryEntry(found.getTenantId(), subjectId, 1, "PREREQUISITE_REMOVED",
                "ACTIVE", "INACTIVE", Collections.singletonList("prerequisites"),
                toJson(found), null, "Removed prerequisite", actorId, actorRole);

        logger.info("Deactivated prerequisite relationship {}", found.getId());
        return true;
    }

    /**
     * Builds prerequisite graph view with forward dependencies and reverse dependents (Story 37).
     */
    public PrerequisiteGraphView getPrerequisiteGraph(String subjectId) {
        Subject s = subjects.get(subjectId);
        if (s == null) {
            throw new SubjectNotFoundException("Subject not found: " + subjectId);
        }

        PrerequisiteGraphView view = new PrerequisiteGraphView(subjectId);

        // Forward lookup: subjects that this subject depends on
        for (SubjectPrerequisite sp : subjectPrerequisites.values()) {
            if (sp.getSubjectId().equalsIgnoreCase(subjectId) && "ACTIVE".equalsIgnoreCase(sp.getStatus())) {
                Subject target = subjects.get(sp.getPrerequisiteSubjectId());
                String code = target != null ? target.getSubjectCode() : sp.getPrerequisiteSubjectId();
                String name = target != null ? target.getName() : "Unknown";
                view.getPrerequisites().add(new PrerequisiteNode(sp.getPrerequisiteSubjectId(), code, name,
                        sp.getRelationshipType() != null ? sp.getRelationshipType().name() : "PREREQUISITE",
                        sp.isMandatory(), sp.getMinimumGrade(), "FORWARD"));
            }
        }

        // Reverse lookup: subjects that depend on this subject
        for (SubjectPrerequisite sp : subjectPrerequisites.values()) {
            if (sp.getPrerequisiteSubjectId().equalsIgnoreCase(subjectId) && "ACTIVE".equalsIgnoreCase(sp.getStatus())) {
                Subject source = subjects.get(sp.getSubjectId());
                String code = source != null ? source.getSubjectCode() : sp.getSubjectId();
                String name = source != null ? source.getName() : "Unknown";
                view.getDependents().add(new PrerequisiteNode(sp.getSubjectId(), code, name,
                        sp.getRelationshipType() != null ? sp.getRelationshipType().name() : "PREREQUISITE",
                        sp.isMandatory(), sp.getMinimumGrade(), "REVERSE"));
            }
        }

        return view;
    }

    /**
     * Performs depth-first search on prerequisite graph to detect cycles.
     */
    private boolean wouldCreateCycle(String subjectId, String candidatePrereqId) {
        // If candidatePrereqId depends on subjectId directly or transitively, adding subjectId -> candidatePrereqId creates a cycle
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(candidatePrereqId);
        visited.add(candidatePrereqId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equalsIgnoreCase(subjectId)) {
                return true;
            }
            for (SubjectPrerequisite sp : subjectPrerequisites.values()) {
                if (sp.getSubjectId().equalsIgnoreCase(current) && "ACTIVE".equalsIgnoreCase(sp.getStatus())) {
                    String next = sp.getPrerequisiteSubjectId();
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return false;
    }

    // =========================================================================
    // 4. Extended Subject Metadata (Epic 5, §39)
    // =========================================================================

    /**
     * Saves or updates extended subject metadata (Story 28).
     */
    public SubjectMetadata saveOrUpdateMetadata(String subjectId, SubjectMetadata update, String actorId, String actorRole) {
        Subject s = subjects.get(subjectId);
        if (s == null) {
            throw new SubjectNotFoundException("Subject not found: " + subjectId);
        }

        SubjectMetadata existing = subjectMetadata.get(subjectId);
        long now = System.currentTimeMillis();

        if (existing == null) {
            existing = new SubjectMetadata();
            existing.setId("META-" + subjectId);
            existing.setSubjectId(subjectId);
            existing.setTenantId(s.getTenantId());
            existing.setCreatedAt(now);
            existing.setVersion(1L);
        }

        if (update.getTags() != null) existing.setTags(update.getTags());
        if (update.getCategory() != null) existing.setCategory(update.getCategory());
        if (update.getDeliveryMode() != null) existing.setDeliveryMode(update.getDeliveryMode());
        if (update.getAssessmentMode() != null) existing.setAssessmentMode(update.getAssessmentMode());
        if (update.getRegulatoryCode() != null) existing.setRegulatoryCode(update.getRegulatoryCode());
        if (update.getIndustryRelevance() != null) existing.setIndustryRelevance(update.getIndustryRelevance());
        if (update.getLanguageOfInstruction() != null) existing.setLanguageOfInstruction(update.getLanguageOfInstruction());
        if (update.getPrerequisiteNotes() != null) existing.setPrerequisiteNotes(update.getPrerequisiteNotes());
        if (update.getCustomAttributes() != null) existing.setCustomAttributes(update.getCustomAttributes());

        existing.setUpdatedAt(now);
        existing.setUpdatedBy(actorId);
        existing.setVersion(existing.getVersion() + 1);

        subjectMetadata.put(subjectId, existing);

        recordHistoryEntry(s.getTenantId(), subjectId, s.getCurrentVersion(), "METADATA_UPDATED",
                null, null, Collections.singletonList("metadata"), null, toJson(existing),
                "Updated metadata", actorId, actorRole);

        logger.info("Saved extended metadata for subject {}", subjectId);
        return existing;
    }

    /**
     * Retrieves extended metadata with role-based field filtering (Story 29, 64).
     */
    public SubjectMetadata getMetadata(String subjectId, String userRole) {
        SubjectMetadata meta = subjectMetadata.get(subjectId);
        if (meta == null) {
            throw new SubjectNotFoundException("Metadata not found for subject: " + subjectId);
        }

        // Field-level security: strip L3 Confidential fields (regulatoryCode, prerequisiteNotes)
        // for non-privileged roles (Students, Parents, External API consumers)
        if ("STUDENT".equalsIgnoreCase(userRole) || "PARENT".equalsIgnoreCase(userRole) || "EXTERNAL_API".equalsIgnoreCase(userRole)) {
            SubjectMetadata filtered = new SubjectMetadata();
            filtered.setId(meta.getId());
            filtered.setTenantId(meta.getTenantId());
            filtered.setSubjectId(meta.getSubjectId());
            filtered.setTags(meta.getTags());
            filtered.setCategory(meta.getCategory());
            filtered.setDeliveryMode(meta.getDeliveryMode());
            filtered.setAssessmentMode(meta.getAssessmentMode());
            filtered.setIndustryRelevance(meta.getIndustryRelevance());
            filtered.setLanguageOfInstruction(meta.getLanguageOfInstruction());
            filtered.setCreatedAt(meta.getCreatedAt());
            filtered.setUpdatedAt(meta.getUpdatedAt());
            // regulatoryCode and prerequisiteNotes omitted
            return filtered;
        }

        return meta;
    }

    // =========================================================================
    // 5. Subject Lifecycle Governance (Epic 7, FR-08, BR-09, BR-11, BR-13)
    // =========================================================================

    public Subject deactivateSubject(String subjectId, String reason, String actorId, String actorRole) {
        return transitionLifecycle(subjectId, SubjectStatus.DEACTIVATED, reason, actorId, actorRole);
    }

    public Subject reactivateSubject(String subjectId, String reason, String actorId, String actorRole) {
        return transitionLifecycle(subjectId, SubjectStatus.ACTIVE, reason, actorId, actorRole);
    }

    public Subject deprecateSubject(String subjectId, String reason, String actorId, String actorRole) {
        return transitionLifecycle(subjectId, SubjectStatus.DEPRECATED, reason, actorId, actorRole);
    }

    public Subject retireSubject(String subjectId, String reason, String actorId, String actorRole) {
        return transitionLifecycle(subjectId, SubjectStatus.RETIRED, reason, actorId, actorRole);
    }

    /**
     * Single governed lifecycle transition endpoint (§13, Story 44).
     */
    public Subject transitionLifecycle(String subjectId, SubjectStatus targetStatus, String reason, String actorId, String actorRole) {
        Subject existing = subjects.get(subjectId);
        if (existing == null) {
            throw new SubjectNotFoundException("Subject not found: " + subjectId);
        }

        SubjectStatus currentStatus = existing.getStatus();
        if (currentStatus == targetStatus) {
            return existing;
        }

        // Validate allowed state transitions (§13)
        // ACTIVE -> DEPRECATED or DEACTIVATED
        // DEPRECATED -> RETIRED
        // DEACTIVATED -> ACTIVE (Reactivation) or RETIRED
        // DRAFT -> ACTIVE
        boolean validTransition = false;
        if (currentStatus == SubjectStatus.DRAFT && (targetStatus == SubjectStatus.ACTIVE || targetStatus == SubjectStatus.DEACTIVATED)) {
            validTransition = true;
        } else if (currentStatus == SubjectStatus.ACTIVE && (targetStatus == SubjectStatus.DEACTIVATED || targetStatus == SubjectStatus.DEPRECATED)) {
            validTransition = true;
        } else if (currentStatus == SubjectStatus.DEACTIVATED && (targetStatus == SubjectStatus.ACTIVE || targetStatus == SubjectStatus.RETIRED)) {
            validTransition = true;
        } else if (currentStatus == SubjectStatus.DEPRECATED && (targetStatus == SubjectStatus.RETIRED || targetStatus == SubjectStatus.ACTIVE)) {
            validTransition = true;
        }

        if (!validTransition) {
            throw new SubjectValidationException("Illegal lifecycle transition from " + currentStatus + " to " + targetStatus);
        }

        existing.setStatus(targetStatus);
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setUpdatedBy(actorId);
        existing.setVersion(existing.getVersion() + 1);

        String eventType = "SubjectUpdated";
        if (targetStatus == SubjectStatus.DEACTIVATED) eventType = "SubjectDeactivated";
        else if (targetStatus == SubjectStatus.ACTIVE && currentStatus == SubjectStatus.DEACTIVATED) eventType = "SubjectReactivated";
        else if (targetStatus == SubjectStatus.DEPRECATED) eventType = "SubjectDeprecated";
        else if (targetStatus == SubjectStatus.RETIRED) eventType = "SubjectRetired";

        outboxEvents.add(createOutboxEvent(existing.getTenantId(), subjectId, eventType, toJson(existing)));

        recordHistoryEntry(existing.getTenantId(), subjectId, existing.getCurrentVersion(),
                "LIFECYCLE_TRANSITION", currentStatus.name(), targetStatus.name(),
                Collections.singletonList("status"), currentStatus.name(), targetStatus.name(),
                reason != null ? reason : "Governed lifecycle change", actorId, actorRole);

        logger.info("Transitioned subject {} lifecycle from {} to {}", subjectId, currentStatus, targetStatus);
        return existing;
    }

    // =========================================================================
    // 6. Search, Filter & Catalog (Epic 3, FR-09)
    // =========================================================================

    /**
     * Published subject catalog (Story 16, 60): only returns published/ACTIVE subjects.
     */
    public List<Subject> getPublishedCatalog(String tenantId, String institutionId) {
        return subjects.values().stream()
                .filter(s -> s.getStatus() == SubjectStatus.ACTIVE)
                .filter(s -> tenantId == null || tenantId.equalsIgnoreCase(s.getTenantId()))
                .filter(s -> institutionId == null || institutionId.equalsIgnoreCase(s.getInstitutionId()))
                .map(Subject::copy)
                .collect(Collectors.toList());
    }

    /**
     * Search and filter subjects with pagination (Story 15, 30).
     */
    public List<Subject> searchSubjects(String code, String name, String type, String departmentId,
                                        String status, String tag, int offset, int limit) {
        return subjects.values().stream()
                .filter(s -> code == null || s.getSubjectCode().toLowerCase().contains(code.toLowerCase()))
                .filter(s -> name == null || s.getName().toLowerCase().contains(name.toLowerCase()))
                .filter(s -> type == null || (s.getSubjectType() != null && s.getSubjectType().equalsIgnoreCase(type)))
                .filter(s -> departmentId == null || s.getDepartmentId().equalsIgnoreCase(departmentId))
                .filter(s -> status == null || (s.getStatus() != null && s.getStatus().name().equalsIgnoreCase(status)))
                .filter(s -> {
                    if (tag == null || tag.trim().isEmpty()) return true;
                    SubjectMetadata meta = subjectMetadata.get(s.getId());
                    return meta != null && meta.getTags() != null && meta.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(tag));
                })
                .skip(offset > 0 ? offset : 0)
                .limit(limit > 0 ? limit : 50)
                .map(Subject::copy)
                .collect(Collectors.toList());
    }

    public List<Subject> getSubjectsByCategory(String category) {
        return subjects.values().stream()
                .filter(s -> s.getStatus() == SubjectStatus.ACTIVE)
                .filter(s -> {
                    if (s.getClassification() != null && s.getClassification().equalsIgnoreCase(category)) {
                        return true;
                    }
                    SubjectMetadata meta = subjectMetadata.get(s.getId());
                    return meta != null && category.equalsIgnoreCase(meta.getCategory());
                })
                .map(Subject::copy)
                .collect(Collectors.toList());
    }

    public List<Subject> getSubjectsByDepartment(String deptId) {
        return subjects.values().stream()
                .filter(s -> s.getStatus() == SubjectStatus.ACTIVE)
                .filter(s -> s.getDepartmentId() != null && s.getDepartmentId().equalsIgnoreCase(deptId))
                .map(Subject::copy)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 7. Bulk Import & Export (Epic 8, UC-10)
    // =========================================================================

    /**
     * Bulk import subjects with row-level validation and non-aborting execution (Story 47).
     */
    public BulkImportResult bulkImportSubjects(List<Subject> rows, String academicYear, String actorId, String actorRole) {
        BulkImportResult result = new BulkImportResult();
        result.setTotalRows(rows.size());

        for (int i = 0; i < rows.size(); i++) {
            Subject s = rows.get(i);
            int rowNum = i + 1;
            try {
                Subject created = createSubject(s, academicYear, actorId, actorRole);
                result.getCreatedSubjectIds().add(created.getId());
                result.setSuccessfulRows(result.getSuccessfulRows() + 1);
            } catch (SubjectException e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(new BulkImportResult.RowError(rowNum, s.getSubjectCode(), e.getErrorCode(), e.getMessage()));
            } catch (Exception e) {
                result.setFailedRows(result.getFailedRows() + 1);
                result.getErrors().add(new BulkImportResult.RowError(rowNum, s.getSubjectCode(), "UNEXPECTED_ERROR", e.getMessage()));
            }
        }
        return result;
    }

    /**
     * Exports active subjects in CSV format (Story 48).
     */
    public String exportCatalogAsCsv(String userRole) {
        StringBuilder sb = new StringBuilder();
        sb.append("subjectCode,name,departmentId,subjectType,classification,credits,contactHours,status,version\n");
        for (Subject s : subjects.values()) {
            if (s.getStatus() == SubjectStatus.ACTIVE) {
                sb.append(escapeCsv(s.getSubjectCode())).append(",")
                        .append(escapeCsv(s.getName())).append(",")
                        .append(escapeCsv(s.getDepartmentId())).append(",")
                        .append(escapeCsv(s.getSubjectType())).append(",")
                        .append(escapeCsv(s.getClassification())).append(",")
                        .append(s.getCredits()).append(",")
                        .append(s.getContactHours()).append(",")
                        .append(s.getStatus().name()).append(",")
                        .append(s.getCurrentVersion()).append("\n");
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // 8. Event-Driven Inbound Integration & DLQ (Epic 9, FR-10, FR-11)
    // =========================================================================

    /**
     * Consumes DepartmentUpdated or DepartmentDeactivated event with deduplication (Story 51, 53).
     */
    public boolean consumeDepartmentEvent(String eventId, String eventType, String departmentId, boolean active) {
        if (processedEventIds.contains(eventId)) {
            logger.info("[EventDeduplication] Inbound event {} already processed, skipping", eventId);
            return false;
        }

        activeDepartments.put(departmentId, active);

        // Flag affected subjects if department deactivated
        if (!active) {
            for (Subject s : subjects.values()) {
                if (s.getDepartmentId() != null && s.getDepartmentId().equalsIgnoreCase(departmentId)) {
                    s.setDepartmentActive(false);
                    s.setFlaggedForReview(true);
                    s.setFlagReason("Owning department deactivated");
                }
            }
        }

        processedEventIds.add(eventId);
        logger.info("Processed department event {} (type={}, deptId={}, active={})", eventId, eventType, departmentId, active);
        return true;
    }

    /**
     * Consumes CourseUpdated or CourseDeactivated event from ACD-01 (Story 52, 53).
     */
    public boolean consumeCourseEvent(String eventId, String eventType, String courseId, boolean active) {
        if (processedEventIds.contains(eventId)) {
            logger.info("[EventDeduplication] Inbound course event {} already processed, skipping", eventId);
            return false;
        }

        for (Subject s : subjects.values()) {
            if (s.getCourseId() != null && s.getCourseId().equalsIgnoreCase(courseId)) {
                if (!active) {
                    s.setFlaggedForReview(true);
                    s.setFlagReason("Associated course deactivated");
                }
            }
        }

        processedEventIds.add(eventId);
        logger.info("Processed course event {} (type={}, courseId={}, active={})", eventId, eventType, courseId, active);
        return true;
    }

    public void routeToDeadLetterQueue(OutboxEvent event, String reason) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId("DLQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        dlq.setEventId(event.getEventId());
        dlq.setSource("ACD-03");
        dlq.setErrorReason(reason);
        dlq.setPayload(event.getPayload());
        dlq.setAttempts(event.getAttempts());
        dlq.setCreatedAt(System.currentTimeMillis());
        deadLetterEvents.add(dlq);
        logger.error("[DLQ] Event {} routed to Dead Letter Queue: {}", event.getEventId(), reason);
    }

    // =========================================================================
    // 9. Idempotency Support (Epic 10, §55)
    // =========================================================================

    public IdempotencyRecord checkIdempotency(String idempotencyKey, String tenantId, String currentRequestHash) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return null;
        }
        String key = tenantId + ":" + idempotencyKey.trim();
        IdempotencyRecord record = idempotencyRecords.get(key);
        if (record != null) {
            if (!record.getRequestHash().equals(currentRequestHash)) {
                throw new SubjectValidationException("IDEMP_HASH_MISMATCH",
                        "Idempotency key reused with different request payload");
            }
            return record;
        }
        return null;
    }

    public void recordIdempotency(String idempotencyKey, String tenantId, String requestHash,
                                  String operation, int statusCode, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return;
        }
        String key = tenantId + ":" + idempotencyKey.trim();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId("IDEMP-" + UUID.randomUUID().toString().substring(0, 8));
        record.setTenantId(tenantId);
        record.setIdempotencyKey(idempotencyKey.trim());
        record.setRequestHash(requestHash);
        record.setOperation(operation);
        record.setStatusCode(statusCode);
        record.setResponseBody(responseBody);
        long now = System.currentTimeMillis();
        record.setCreatedAt(now);
        record.setExpiresAt(now + 86400000L); // 24-hour TTL

        idempotencyRecords.put(key, record);
    }

    public void purgeExpiredIdempotencyRecords() {
        long now = System.currentTimeMillis();
        idempotencyRecords.entrySet().removeIf(e -> e.getValue().getExpiresAt() < now);
    }

    // =========================================================================
    // 10. Security & API Keys (Epic 11, §47-50)
    // =========================================================================

    public ApiKeyRecord validateApiKey(String apiKey, String tenantId) {
        for (ApiKeyRecord r : apiKeys.values()) {
            if (r.isActive() && r.getRawKey().equals(apiKey)) {
                if (tenantId != null && !tenantId.equalsIgnoreCase(r.getTenantId())) {
                    return null;
                }
                return r;
            }
        }
        return null;
    }

    // =========================================================================
    // 11. History & Inspection (§46)
    // =========================================================================

    public List<SubjectHistory> getSubjectHistory(String subjectId) {
        List<SubjectHistory> result = new ArrayList<>();
        synchronized (historyRecords) {
            for (SubjectHistory h : historyRecords) {
                if (h.getSubjectId().equalsIgnoreCase(subjectId)) {
                    result.add(h);
                }
            }
        }
        return result;
    }

    public List<Subject> getAllSubjects() {
        return new ArrayList<>(subjects.values());
    }

    public List<OutboxEvent> getOutboxEvents() {
        return new ArrayList<>(outboxEvents);
    }

    public List<OutboxEvent> getPendingOutboxEvents() {
        List<OutboxEvent> pending = new ArrayList<>();
        synchronized (outboxEvents) {
            for (OutboxEvent evt : outboxEvents) {
                if ("PENDING".equalsIgnoreCase(evt.getStatus())) {
                    pending.add(evt);
                }
            }
        }
        return pending;
    }

    public List<DeadLetterEvent> getDeadLetterEvents() {
        return new ArrayList<>(deadLetterEvents);
    }

    // =========================================================================
    // 12. Helper Validations & Conversions
    // =========================================================================

    private void validateSubjectAttributes(Subject s) {
        if (s.getSubjectCode() == null || s.getSubjectCode().trim().isEmpty()) {
            throw new SubjectValidationException("subjectCode is mandatory");
        }
        if (s.getName() == null || s.getName().trim().isEmpty()) {
            throw new SubjectValidationException("name is mandatory");
        }
        if (s.getTenantId() == null || s.getTenantId().trim().isEmpty()) {
            s.setTenantId("TENANT-001");
        }
        if (s.getInstitutionId() == null || s.getInstitutionId().trim().isEmpty()) {
            s.setInstitutionId("INST-001");
        }

        validateTaxonomy(s.getSubjectType(), s.getClassification());
        validateCredits(s.getCredits(), s.getContactHours());
    }

    private void validateTaxonomy(String subjectType, String classification) {
        if (subjectType == null || subjectType.trim().isEmpty()) {
            throw new InvalidTaxonomyException("subjectType is mandatory");
        }
        boolean validSubjectType = false;
        for (SubjectType st : SubjectType.values()) {
            if (st.name().equalsIgnoreCase(subjectType.trim())) {
                validSubjectType = true;
                break;
            }
        }
        if (!validSubjectType) {
            throw new InvalidTaxonomyException("Invalid subjectType '" + subjectType + "'. Allowed values: CORE, ELECTIVE, PRACTICAL, PROJECT, AUDIT");
        }

        if (classification != null && !classification.trim().isEmpty()) {
            boolean validClassification = false;
            for (Classification c : Classification.values()) {
                if (c.name().equalsIgnoreCase(classification.trim())) {
                    validClassification = true;
                    break;
                }
            }
            if (!validClassification) {
                throw new InvalidTaxonomyException("Invalid classification '" + classification + "'. Allowed: THEORY, PRACTICAL, TUTORIAL, PROJECT, ELECTIVE, AUDIT");
            }
        }
    }

    private void validateCredits(double credits, double contactHours) {
        if (credits <= 0 || credits > 20.0) {
            throw new CreditPolicyViolationException("Subject credits must be greater than 0 and within institutional range (0 - 20). Given: " + credits);
        }
        if (contactHours < 0) {
            throw new CreditPolicyViolationException("Contact hours cannot be negative. Given: " + contactHours);
        }
    }

    private void validateDepartment(String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            throw new SubjectValidationException("departmentId is mandatory");
        }
        Boolean active = activeDepartments.get(departmentId);
        if (active == null || !active) {
            throw new SubjectValidationException("Department '" + departmentId + "' does not exist or is inactive");
        }
    }

    private void ensureSubjectCodeUnique(String tenantId, String institutionId, String subjectCode, String excludeId) {
        for (Subject s : subjects.values()) {
            if (excludeId != null && s.getId().equalsIgnoreCase(excludeId)) {
                continue;
            }
            if (s.getTenantId().equalsIgnoreCase(tenantId)
                    && s.getInstitutionId().equalsIgnoreCase(institutionId)
                    && s.getSubjectCode().equalsIgnoreCase(subjectCode)) {
                throw new DuplicateSubjectCodeException(subjectCode, institutionId);
            }
        }
    }

    private OutboxEvent createOutboxEvent(String tenantId, String aggregateId, String eventType, String payload) {
        OutboxEvent out = new OutboxEvent();
        out.setId("OUT-" + UUID.randomUUID().toString().substring(0, 8));
        out.setEventId(String.format("EVT-ACD-03-%06d", eventSequence.getAndIncrement()));
        out.setEventType(eventType);
        out.setSource("ACD-03");
        out.setTenantId(tenantId);
        out.setAggregateId(aggregateId);
        out.setPayload(payload);
        out.setStatus("PENDING");
        out.setAttempts(0);
        out.setOccurredAt(System.currentTimeMillis());
        out.setCorrelationId(LogContext.getTraceId());
        return out;
    }

    private SubjectHistory recordHistoryEntry(String tenantId, String subjectId, int versionNo, String action,
                                              String fromStatus, String toStatus, List<String> changedFields,
                                              String beforeJson, String afterJson, String reason,
                                              String actorId, String actorRole) {
        SubjectHistory hist = new SubjectHistory();
        hist.setId("HIST-" + UUID.randomUUID().toString().substring(0, 8));
        hist.setTenantId(tenantId);
        hist.setSubjectId(subjectId);
        hist.setVersionNo(versionNo);
        hist.setAction(action);
        hist.setFromStatus(fromStatus);
        hist.setToStatus(toStatus);
        hist.setChangedFields(changedFields);
        hist.setBeforeJson(beforeJson);
        hist.setAfterJson(afterJson);
        hist.setReason(reason);
        hist.setActorId(actorId != null ? actorId : "system");
        hist.setActorRole(actorRole != null ? actorRole : "ACADEMIC_ADMIN");
        hist.setCorrelationId(LogContext.getTraceId());
        hist.setOccurredAt(System.currentTimeMillis());
        hist.setSource("ACD-03");

        historyRecords.add(hist);
        return hist;
    }

    private String generateChecksum(Subject s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String data = s.getSubjectCode() + ":" + s.getName() + ":" + s.getCredits() + ":" + s.getContactHours();
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "CHECKSUM-" + s.getSubjectCode().hashCode();
        }
    }

    public static String computeHash(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "HASH-" + payload.hashCode();
        }
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Subject) {
            Subject s = (Subject) obj;
            return "{"
                    + "\"subjectId\":\"" + escape(s.getId()) + "\","
                    + "\"subjectCode\":\"" + escape(s.getSubjectCode()) + "\","
                    + "\"name\":\"" + escape(s.getName()) + "\","
                    + "\"shortName\":\"" + escape(s.getShortName()) + "\","
                    + "\"description\":\"" + escape(s.getDescription()) + "\","
                    + "\"departmentId\":\"" + escape(s.getDepartmentId()) + "\","
                    + "\"campusId\":\"" + escape(s.getCampusId()) + "\","
                    + "\"subjectType\":\"" + escape(s.getSubjectType()) + "\","
                    + "\"classification\":\"" + escape(s.getClassification()) + "\","
                    + "\"elective\":" + s.isElective() + ","
                    + "\"credits\":" + s.getCredits() + ","
                    + "\"contactHours\":" + s.getContactHours() + ","
                    + "\"status\":\"" + (s.getStatus() != null ? s.getStatus().name() : "") + "\","
                    + "\"version\":" + s.getCurrentVersion() + ","
                    + "\"academicYear\":\"" + escape(s.getAcademicYear()) + "\","
                    + "\"departmentActive\":" + s.isDepartmentActive() + ","
                    + "\"flaggedForReview\":" + s.isFlaggedForReview() + ","
                    + "\"versionLock\":" + s.getVersion()
                    + "}";
        }
        if (obj instanceof SubjectVersion) {
            SubjectVersion v = (SubjectVersion) obj;
            return "{"
                    + "\"versionId\":\"" + escape(v.getId()) + "\","
                    + "\"subjectId\":\"" + escape(v.getSubjectId()) + "\","
                    + "\"versionNo\":" + v.getVersionNo() + ","
                    + "\"status\":\"" + (v.getStatus() != null ? v.getStatus().name() : "") + "\","
                    + "\"academicYear\":\"" + escape(v.getAcademicYear()) + "\","
                    + "\"credits\":" + v.getCredits() + ","
                    + "\"contactHours\":" + v.getContactHours() + ","
                    + "\"changeSummary\":\"" + escape(v.getChangeSummary()) + "\","
                    + "\"approvedBy\":\"" + escape(v.getApprovedBy()) + "\","
                    + "\"publishedBy\":\"" + escape(v.getPublishedBy()) + "\""
                    + "}";
        }
        if (obj instanceof SubjectPrerequisite) {
            SubjectPrerequisite sp = (SubjectPrerequisite) obj;
            return "{"
                    + "\"id\":\"" + escape(sp.getId()) + "\","
                    + "\"subjectId\":\"" + escape(sp.getSubjectId()) + "\","
                    + "\"prerequisiteSubjectId\":\"" + escape(sp.getPrerequisiteSubjectId()) + "\","
                    + "\"relationshipType\":\"" + (sp.getRelationshipType() != null ? sp.getRelationshipType().name() : "") + "\","
                    + "\"mandatory\":" + sp.isMandatory() + ","
                    + "\"minimumGrade\":\"" + escape(sp.getMinimumGrade()) + "\","
                    + "\"status\":\"" + escape(sp.getStatus()) + "\""
                    + "}";
        }
        if (obj instanceof SubjectMetadata) {
            SubjectMetadata m = (SubjectMetadata) obj;
            return "{"
                    + "\"subjectId\":\"" + escape(m.getSubjectId()) + "\","
                    + "\"category\":\"" + escape(m.getCategory()) + "\","
                    + "\"deliveryMode\":\"" + escape(m.getDeliveryMode()) + "\","
                    + "\"assessmentMode\":\"" + escape(m.getAssessmentMode()) + "\","
                    + "\"regulatoryCode\":\"" + escape(m.getRegulatoryCode()) + "\","
                    + "\"languageOfInstruction\":\"" + escape(m.getLanguageOfInstruction()) + "\","
                    + "\"prerequisiteNotes\":\"" + escape(m.getPrerequisiteNotes()) + "\""
                    + "}";
        }
        return "{}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
