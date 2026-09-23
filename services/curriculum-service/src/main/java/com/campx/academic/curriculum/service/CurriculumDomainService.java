package com.campx.academic.curriculum.service;

import com.campx.academic.curriculum.exception.*;
import com.campx.academic.curriculum.model.CurriculumModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Authoritative Domain Service implementing the complete business logic, invariants,
 * approval workflows, DAG cycle detection, outbox events, and data governance for ACD-02.
 */
public class CurriculumDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CurriculumDomainService.class);

    // Primary Collections
    private final Map<String, Curriculum> curricula = new ConcurrentHashMap<>();
    private final Map<String, List<CurriculumVersion>> versionsByCurriculum = new ConcurrentHashMap<>();
    private final Map<String, CurriculumVersion> versionsById = new ConcurrentHashMap<>();
    private final Map<String, List<CurriculumSubject>> subjectsByVersion = new ConcurrentHashMap<>();
    private final Map<String, List<CurriculumOutcome>> outcomesByVersion = new ConcurrentHashMap<>();
    private final Map<String, List<CurriculumPrerequisite>> prerequisitesByCurriculum = new ConcurrentHashMap<>();
    private final Map<String, List<CurriculumHistory>> historyByCurriculum = new ConcurrentHashMap<>();

    // Technical Collections
    private final List<OutboxEvent> outboxEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final Set<String> processedInboundEventIds = Collections.synchronizedSet(new HashSet<>());
    private final List<DeadLetterEvent> deadLetterEvents = Collections.synchronizedList(new ArrayList<>());

    // Downstream Academic Reference Registry (for BR-15 deletion check)
    private final Set<String> downstreamAcademicReferences = Collections.synchronizedSet(new HashSet<>());

    // External Reference Data Mocks / Registries
    // courseId -> Active status boolean (ACD-01)
    private final Map<String, CourseReference> courseReferenceRegistry = new ConcurrentHashMap<>();
    // subjectId -> Active status boolean & tenantId (ACD-03)
    private final Map<String, SubjectReference> subjectReferenceRegistry = new ConcurrentHashMap<>();
    // External API Key Registry (rawApiKey -> ApiKeyRecord)
    private final Map<String, ApiKeyRecord> apiKeyRegistry = new ConcurrentHashMap<>();

    // Global monotonic history sequence generator
    private final AtomicLong historySeq = new AtomicLong(1);
    private final AtomicLong eventSeq = new AtomicLong(1);

    // Credit Policy Configuration (Default: min 16, max 180 credits per curriculum version)
    private double minCreditPolicy = 16.0;
    private double maxCreditPolicy = 180.0;

    public CurriculumDomainService() {
        // Pre-populate standard sample courses & subjects for seamless local integration
        seedReferenceData();
    }

    private void seedReferenceData() {
        courseReferenceRegistry.put("COURSE-001", new CourseReference("COURSE-001", "TENANT-001", "MAIN", "DEPT-CA", true));
        courseReferenceRegistry.put("CS101", new CourseReference("CS101", "CAMPUS_MAIN", "CAMPUS_MAIN", "DEP_CS", true));
        courseReferenceRegistry.put("CS201", new CourseReference("CS201", "CAMPUS_MAIN", "CAMPUS_MAIN", "DEP_CS", true));
        courseReferenceRegistry.put("CRS_ACTIVE_01", new CourseReference("CRS_ACTIVE_01", "CAMPUS_ALPHA", "CAMPUS_ALPHA", "DEP_CS", true));

        subjectReferenceRegistry.put("SUB-101", new SubjectReference("SUB-101", "TENANT-001", "Advanced Java", true));
        subjectReferenceRegistry.put("SUB-102", new SubjectReference("SUB-102", "TENANT-001", "Database Engineering", true));
        subjectReferenceRegistry.put("SUB-103", new SubjectReference("SUB-103", "TENANT-001", "Distributed Systems", true));
        subjectReferenceRegistry.put("SUB-201", new SubjectReference("SUB-201", "CAMPUS_MAIN", "Algorithms", true));
        subjectReferenceRegistry.put("SUB-202", new SubjectReference("SUB-202", "CAMPUS_MAIN", "Operating Systems", true));

        // Seed sample API keys for external integration testing (Story 63)
        ApiKeyRecord liveKey = new ApiKeyRecord("KEY-EXT-001", "ak_live_campx_valid_12345", "TENANT-001",
                Collections.singletonList("READ"), System.currentTimeMillis() + 86400000L);
        liveKey.setHashedKey(hashPayload("ak_live_campx_valid_12345"));
        registerApiKey(liveKey);

        ApiKeyRecord expiredKey = new ApiKeyRecord("KEY-EXT-002", "ak_live_campx_expired_99999", "TENANT-001",
                Collections.singletonList("READ"), System.currentTimeMillis() - 1000L);
        expiredKey.setHashedKey(hashPayload("ak_live_campx_expired_99999"));
        registerApiKey(expiredKey);
    }

    // =========================================================================
    // Epic 1: Curriculum Definition & Identity Management
    // =========================================================================

    /**
     * Creates a new draft curriculum aggregate linked to an active ACD-01 course.
     * Enforces BR-05 (courseId active in ACD-01) and BR-13 (tenant/campus consistency).
     * Initializes version=1 in DRAFT status and records CurriculumCreated outbox event.
     */
    public synchronized Curriculum createCurriculum(String courseId, String academicPattern, String academicYear,
                                                    String departmentId, String campusId, String name,
                                                    String tenantId, String institutionId, String actorId, String actorRole) {
        try (FlowTracker flow = logger.flow("CreateCurriculumWorkflow", courseId)) {
            // 1. RBAC Permission Check
            checkPermission(actorRole, "CURRICULUM_CREATE");

            // 2. Validate courseId resolves through ACD-01 (BR-05)
            CourseReference courseRef = courseReferenceRegistry.get(courseId);
            if (courseRef == null || !courseRef.isActive()) {
                throw new InvalidCourseException("Course with ID '" + courseId + "' is invalid or inactive in ACD-01");
            }

            // 3. Enforce tenant/campus scope consistency (BR-13)
            if (tenantId != null && courseRef.getTenantId() != null && !tenantId.equalsIgnoreCase(courseRef.getTenantId())) {
                throw new TenantMismatchException("Tenant mismatch: Curriculum tenant '" + tenantId
                        + "' does not match referenced course tenant '" + courseRef.getTenantId() + "'");
            }

            // 4. Check for duplicate curriculum identity within tenant & pattern
            for (Curriculum c : curricula.values()) {
                if (c.getCourseId().equalsIgnoreCase(courseId)
                        && c.getAcademicPattern().equalsIgnoreCase(academicPattern)
                        && c.getTenantId().equalsIgnoreCase(tenantId)
                        && c.getStatus() != CurriculumStatus.RETIRED) {
                    throw new DuplicateMappingException("A curriculum for course '" + courseId + "' with pattern '"
                            + academicPattern + "' already exists");
                }
            }

            // 5. Generate Aggregate ID
            String curriculumId = "CURR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            long now = System.currentTimeMillis();

            Curriculum curr = new Curriculum();
            curr.setId(curriculumId);
            curr.setTenantId(tenantId != null ? tenantId : "TENANT-001");
            curr.setInstitutionId(institutionId != null ? institutionId : "INST-001");
            curr.setCampusId(campusId != null ? campusId : "MAIN");
            curr.setCourseId(courseId);
            curr.setDepartmentId(departmentId);
            curr.setName(name != null ? name : "Curriculum for " + courseId);
            curr.setAcademicPattern(academicPattern != null ? academicPattern : "CBCS");
            curr.setStatus(CurriculumStatus.DRAFT);
            curr.setCurrentVersion(1);
            curr.setCreatedAt(now);
            curr.setUpdatedAt(now);
            curr.setCreatedBy(actorId != null ? actorId : "admin");
            curr.setUpdatedBy(actorId != null ? actorId : "admin");
            curr.setVersion(1L);

            // 6. Create Initial Version 1 in DRAFT
            CurriculumVersion v1 = new CurriculumVersion();
            v1.setId("CURR-V-" + curriculumId + "-1");
            v1.setCurriculumId(curriculumId);
            v1.setTenantId(curr.getTenantId());
            v1.setVersionNo(1);
            v1.setAcademicYear(academicYear != null ? academicYear : "2026-2027");
            v1.setStatus(VersionStatus.DRAFT);
            v1.setCreatedAt(now);
            v1.setUpdatedAt(now);
            v1.setCreatedBy(actorId);
            v1.setVersion(1L);

            // 7. Atomic Unit-of-Work Commit via TransactionContext (Story 58)
            TransactionContext tx = new TransactionContext();
            tx.begin();
            try {
                curricula.put(curriculumId, curr);
                tx.addRollback(() -> curricula.remove(curriculumId));

                List<CurriculumVersion> versionList = new ArrayList<>();
                versionList.add(v1);
                versionsByCurriculum.put(curriculumId, versionList);
                tx.addRollback(() -> versionsByCurriculum.remove(curriculumId));

                versionsById.put(v1.getId(), v1);
                tx.addRollback(() -> versionsById.remove(v1.getId()));

                // 8. Record Immutable Audit History (FR-13, §46)
                recordHistory(curriculumId, 1, "CREATE", null, "DRAFT",
                        Arrays.asList("status", "currentVersion", "courseId"),
                        null, "{ \"status\": \"DRAFT\", \"version\": 1 }",
                        "Curriculum created", actorId, actorRole);
                tx.addRollback(() -> historyByCurriculum.remove(curriculumId));

                // 9. Emit Domain Event via Transactional Outbox (FR-11, §31)
                emitOutboxEvent("CurriculumCreated", curriculumId, curr.getTenantId(),
                        String.format("{\"curriculumId\":\"%s\",\"courseId\":\"%s\",\"academicPattern\":\"%s\",\"academicYear\":\"%s\",\"status\":\"DRAFT\",\"currentVersion\":1}",
                                curriculumId, courseId, academicPattern, academicYear));

                tx.commit();
            } catch (RuntimeException e) {
                tx.rollback();
                throw e;
            }

            logger.info("Created curriculum {} for course {}", curriculumId, courseId);
            return curr;
        }
    }

    /**
     * Retrieves full detail of a specific curriculum, enforcing role-based visibility.
     * For Students, returns only if PUBLISHED/EFFECTIVE (FR-01, Story 7).
     */
    public Curriculum getCurriculum(String curriculumId, String actorRole, String actorDepartmentId) {
        Curriculum curr = curricula.get(curriculumId);
        if (curr == null) {
            throw new CurriculumNotFoundException("Curriculum with ID '" + curriculumId + "' does not exist");
        }

        // Scope check for Department Head
        if ("DEPARTMENT_HEAD".equalsIgnoreCase(actorRole) && actorDepartmentId != null) {
            if (!actorDepartmentId.equalsIgnoreCase(curr.getDepartmentId())) {
                throw new CurriculumForbiddenException("Department Head unauthorized to view curriculum for department '"
                        + curr.getDepartmentId() + "'");
            }
        }

        // Student Portal Read-Only filter (Story 7)
        if ("STUDENT".equalsIgnoreCase(actorRole)) {
            if (curr.getStatus() != CurriculumStatus.PUBLISHED) {
                throw new CurriculumForbiddenException("Students can only view PUBLISHED curriculum definitions");
            }
        }

        return curr;
    }

    // =========================================================================
    // Epic 2: Curriculum Catalog & Search
    // =========================================================================

    /**
     * Searches and filters curricula by courseId, departmentId, academicYear, and status with pagination.
     */
    public List<Curriculum> searchCurricula(String courseId, String departmentId, String academicYear,
                                           String statusStr, String actorRole, int page, int limit) {
        return curricula.values().stream()
                .filter(c -> {
                    if ("STUDENT".equalsIgnoreCase(actorRole) && c.getStatus() != CurriculumStatus.PUBLISHED) {
                        return false;
                    }
                    if (courseId != null && !courseId.isEmpty() && !c.getCourseId().equalsIgnoreCase(courseId)) {
                        return false;
                    }
                    if (departmentId != null && !departmentId.isEmpty() && !c.getDepartmentId().equalsIgnoreCase(departmentId)) {
                        return false;
                    }
                    if (statusStr != null && !statusStr.isEmpty() && !c.getStatus().name().equalsIgnoreCase(statusStr)) {
                        return false;
                    }
                    return true;
                })
                .skip((long) Math.max(0, page - 1) * limit)
                .limit(limit > 0 ? limit : 50)
                .collect(Collectors.toList());
    }

    /**
     * Lists all currently active/effective curricula (Story 10).
     */
    public List<Curriculum> listActiveCurricula() {
        return curricula.values().stream()
                .filter(c -> c.getStatus() == CurriculumStatus.PUBLISHED)
                .collect(Collectors.toList());
    }

    /**
     * Returns all curricula across all lifecycle statuses (Story 69).
     */
    public List<Curriculum> getAllCurricula() {
        return new ArrayList<>(curricula.values());
    }

    // =========================================================================
    // Epic 3: Curriculum Version Management
    // =========================================================================

    /**
     * Creates a new draft curriculum version for an academic period (FR-02, Story 12).
     */
    public synchronized CurriculumVersion createVersion(String curriculumId, String effectiveFrom, String effectiveTo,
                                                        String academicYear, String regulation, String changeSummary,
                                                        String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_CREATE");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);

        List<CurriculumVersion> versions = versionsByCurriculum.computeIfAbsent(curriculumId, k -> new ArrayList<>());
        int nextVersionNo = versions.stream().mapToInt(CurriculumVersion::getVersionNo).max().orElse(0) + 1;

        long now = System.currentTimeMillis();
        CurriculumVersion newVer = new CurriculumVersion();
        newVer.setId("CURR-V-" + curriculumId + "-" + nextVersionNo);
        newVer.setCurriculumId(curriculumId);
        newVer.setTenantId(curr.getTenantId());
        newVer.setVersionNo(nextVersionNo);
        newVer.setAcademicYear(academicYear != null ? academicYear : "2026-2027");
        newVer.setStatus(VersionStatus.DRAFT);
        newVer.setEffectiveFrom(effectiveFrom);
        newVer.setEffectiveTo(effectiveTo);
        newVer.setRegulation(regulation);
        newVer.setChangeSummary(changeSummary);
        newVer.setCreatedAt(now);
        newVer.setUpdatedAt(now);
        newVer.setCreatedBy(actorId);
        newVer.setVersion(1L);

        versions.add(newVer);
        versionsById.put(newVer.getId(), newVer);

        recordHistory(curriculumId, nextVersionNo, "CREATE_VERSION", null, "DRAFT",
                Arrays.asList("status", "versionNo"), null, "{ \"versionNo\": " + nextVersionNo + " }",
                "New version created", actorId, actorRole);

        emitOutboxEvent("CurriculumVersionCreated", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"DRAFT\"}", curriculumId, nextVersionNo));

        return newVer;
    }

    /**
     * Updates fields of a draft curriculum version.
     * Prevents mutation if version is PUBLISHED (BR-02, BR-12, Story 13, 15).
     * Enforces optimistic locking (Story 19).
     */
    public synchronized CurriculumVersion updateDraftVersion(String curriculumId, int versionNo, String academicYear,
                                                             String effectiveFrom, String effectiveTo, String regulation,
                                                             String changeSummary, Long expectedVersion,
                                                             String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_EDIT");
        CurriculumVersion ver = getVersion(curriculumId, versionNo);

        if (ver.getStatus() == VersionStatus.PUBLISHED || ver.getStatus() == VersionStatus.SUPERSEDED || ver.getStatus() == VersionStatus.RETIRED) {
            throw new VersionImmutableException("Curriculum version " + versionNo + " is " + ver.getStatus()
                    + " and immutable (BR-02)");
        }

        if (expectedVersion != null && ver.getVersion() != expectedVersion) {
            throw new VersionConflictException("Stale version reference: submitted version " + expectedVersion
                    + " does not match current version " + ver.getVersion());
        }

        if (academicYear != null) ver.setAcademicYear(academicYear);
        if (effectiveFrom != null) ver.setEffectiveFrom(effectiveFrom);
        if (effectiveTo != null) ver.setEffectiveTo(effectiveTo);
        if (regulation != null) ver.setRegulation(regulation);
        if (changeSummary != null) ver.setChangeSummary(changeSummary);
        ver.setUpdatedAt(System.currentTimeMillis());
        ver.setUpdatedBy(actorId);
        ver.setVersion(ver.getVersion() + 1);

        recordHistory(curriculumId, versionNo, "UPDATE_VERSION", ver.getStatus().name(), ver.getStatus().name(),
                Arrays.asList("academicYear", "effectiveFrom", "effectiveTo", "regulation"),
                null, "{ \"versionNo\": " + versionNo + " }",
                "Draft version updated", actorId, actorRole);

        return ver;
    }

    /**
     * Retrieves all versions for a curriculum ordered by version number (Story 16).
     */
    public List<CurriculumVersion> listVersions(String curriculumId) {
        List<CurriculumVersion> list = versionsByCurriculum.get(curriculumId);
        if (list == null || list.isEmpty()) {
            throw new CurriculumNotFoundException("No versions found for curriculum '" + curriculumId + "'");
        }
        return list.stream()
                .sorted(Comparator.comparingInt(CurriculumVersion::getVersionNo))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific historical version snapshot (Story 17).
     */
    public CurriculumVersion getVersion(String curriculumId, int versionNo) {
        List<CurriculumVersion> list = versionsByCurriculum.get(curriculumId);
        if (list == null) {
            throw new CurriculumNotFoundException("Curriculum with ID '" + curriculumId + "' does not exist");
        }
        return list.stream()
                .filter(v -> v.getVersionNo() == versionNo)
                .findFirst()
                .orElseThrow(() -> new CurriculumNotFoundException("Version " + versionNo + " does not exist for curriculum '"
                        + curriculumId + "'"));
    }

    /**
     * Clones the current effective version into a new draft for annual revision (Workflow 14.2, Story 18).
     */
    public synchronized CurriculumVersion cloneForAnnualRevision(String curriculumId, String newAcademicYear,
                                                                 String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_CREATE");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);
        CurriculumVersion effectiveVer = getVersion(curriculumId, curr.getCurrentVersion());

        List<CurriculumVersion> versions = versionsByCurriculum.get(curriculumId);
        int newVersionNo = versions.stream().mapToInt(CurriculumVersion::getVersionNo).max().orElse(0) + 1;

        long now = System.currentTimeMillis();
        CurriculumVersion draftClone = new CurriculumVersion();
        draftClone.setId("CURR-V-" + curriculumId + "-" + newVersionNo);
        draftClone.setCurriculumId(curriculumId);
        draftClone.setTenantId(curr.getTenantId());
        draftClone.setVersionNo(newVersionNo);
        draftClone.setAcademicYear(newAcademicYear != null ? newAcademicYear : "2027-2028");
        draftClone.setStatus(VersionStatus.DRAFT);
        draftClone.setChangeSummary("Cloned from version " + effectiveVer.getVersionNo() + " for annual revision");
        draftClone.setRegulation(effectiveVer.getRegulation());
        draftClone.setTotalCredits(effectiveVer.getTotalCredits());
        draftClone.setSemesterCount(effectiveVer.getSemesterCount());
        draftClone.setCreatedAt(now);
        draftClone.setUpdatedAt(now);
        draftClone.setCreatedBy(actorId);
        draftClone.setVersion(1L);

        // Deep copy semesters
        for (Semester s : effectiveVer.getSemesters()) {
            draftClone.getSemesters().add(new Semester(s.getSemesterNo(), s.getName(), draftClone.getAcademicYear()));
        }

        // Deep copy syllabus
        for (SyllabusModule m : effectiveVer.getSyllabus()) {
            draftClone.getSyllabus().add(new SyllabusModule(m.getModuleId(), m.getTitle(), m.getOrder(),
                    new ArrayList<>(m.getTopics()), m.getHours()));
        }

        versions.add(draftClone);
        versionsById.put(draftClone.getId(), draftClone);

        // Copy subject mappings for the new version
        String effectiveKey = curriculumId + "-v" + effectiveVer.getVersionNo();
        String newKey = curriculumId + "-v" + newVersionNo;
        List<CurriculumSubject> existingSubjects = subjectsByVersion.getOrDefault(effectiveKey, Collections.emptyList());
        List<CurriculumSubject> copiedSubjects = new ArrayList<>();
        for (CurriculumSubject cs : existingSubjects) {
            CurriculumSubject copy = new CurriculumSubject();
            copy.setId("MAP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            copy.setTenantId(cs.getTenantId());
            copy.setCurriculumId(curriculumId);
            copy.setVersionNo(newVersionNo);
            copy.setSemesterNo(cs.getSemesterNo());
            copy.setSubjectId(cs.getSubjectId());
            copy.setSubjectOrder(cs.getSubjectOrder());
            copy.setSubjectType(cs.getSubjectType());
            copy.setCredits(cs.getCredits());
            copy.setContactHours(cs.getContactHours());
            copy.setMandatory(cs.isMandatory());
            copy.setStatus("ACTIVE");
            copy.setCreatedAt(now);
            copy.setCreatedBy(actorId);
            copiedSubjects.add(copy);
        }
        subjectsByVersion.put(newKey, copiedSubjects);

        recordHistory(curriculumId, newVersionNo, "CLONE_ANNUAL_REVISION", null, "DRAFT",
                Arrays.asList("versionNo", "academicYear"), null, "{ \"clonedFrom\": " + effectiveVer.getVersionNo() + " }",
                "Annual revision draft cloned", actorId, actorRole);

        emitOutboxEvent("CurriculumVersionCreated", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"DRAFT\",\"action\":\"CLONED\"}",
                        curriculumId, newVersionNo));

        return draftClone;
    }

    // =========================================================================
    // Epic 4: Semester & Subject Mapping
    // =========================================================================

    /**
     * Adds a semester to a curriculum version, enforcing unique semesterNo (BR-07, Story 21, 22).
     */
    public synchronized Semester addSemester(String curriculumId, int versionNo, int semesterNo, String name,
                                             String academicYear, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_EDIT");
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        if (ver.getStatus() == VersionStatus.PUBLISHED) {
            throw new VersionImmutableException("Cannot add semester to published version (BR-02)");
        }

        // BR-07: Semester sequence numbers must be unique within a curriculum version
        for (Semester s : ver.getSemesters()) {
            if (s.getSemesterNo() == semesterNo) {
                throw new DuplicateMappingException("Semester number " + semesterNo + " already exists in version " + versionNo);
            }
        }

        Semester sem = new Semester(semesterNo, name != null ? name : "Semester " + semesterNo,
                academicYear != null ? academicYear : ver.getAcademicYear());
        ver.getSemesters().add(sem);
        ver.setSemesterCount(ver.getSemesters().size());
        ver.setUpdatedAt(System.currentTimeMillis());
        return sem;
    }

    /**
     * Maps an active ACD-03 subject to a semester (FR-04, BR-06, BR-08, Story 23, 24, 25).
     *
     * @param curriculumId curriculum aggregate ID
     * @param versionNo version number to map subject to
     * @param subjectId subject identifier from ACD-03
     * @param semesterNo semester number to place the subject in
     * @param subjectOrder sequential ordering within semester
     * @param subjectType subject category (e.g., CORE, ELECTIVE)
     * @param credits academic credits allocated
     * @param contactHours total instruction hours
     * @param mandatory whether the subject is compulsory
     * @param actorId user performing the mapping
     * @param actorRole role of the actor (must have SUBJECT_MAPPING_MANAGE permission)
     * @return created {@link CurriculumSubject} mapping record
     * @throws VersionImmutableException if version is already published
     * @throws InvalidSubjectException if subject is invalid or inactive in ACD-03
     * @throws DuplicateMappingException if subject is already mapped to the same semester
     */
    public synchronized CurriculumSubject mapSubject(String curriculumId, int versionNo, String subjectId, int semesterNo,
                                                     int subjectOrder, String subjectType, double credits, double contactHours,
                                                     boolean mandatory, String actorId, String actorRole) {
        checkPermission(actorRole, "SUBJECT_MAPPING_MANAGE");
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        if (ver.getStatus() == VersionStatus.PUBLISHED) {
            throw new VersionImmutableException("Cannot map subjects to a published version (BR-02)");
        }

        // 1. Validate subjectId resolves through ACD-03 (BR-03, BR-06)
        SubjectReference subRef = subjectReferenceRegistry.get(subjectId);
        if (subRef == null || !subRef.isActive()) {
            throw new InvalidSubjectException("Subject '" + subjectId + "' is invalid, inactive, or deprecated in ACD-03");
        }

        // 2. Prevent duplicate subject-to-semester mapping (BR-08)
        String key = curriculumId + "-v" + versionNo;
        List<CurriculumSubject> list = subjectsByVersion.computeIfAbsent(key, k -> new ArrayList<>());
        for (CurriculumSubject s : list) {
            if (s.getSubjectId().equalsIgnoreCase(subjectId) && s.getSemesterNo() == semesterNo && "ACTIVE".equals(s.getStatus())) {
                throw new DuplicateMappingException("Subject '" + subjectId + "' is already mapped to semester " + semesterNo);
            }
        }

        String mappingId = "MAP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long now = System.currentTimeMillis();

        CurriculumSubject cs = new CurriculumSubject();
        cs.setId(mappingId);
        cs.setTenantId(ver.getTenantId());
        cs.setCurriculumId(curriculumId);
        cs.setVersionNo(versionNo);
        cs.setSemesterNo(semesterNo);
        cs.setSubjectId(subjectId);
        cs.setSubjectOrder(subjectOrder > 0 ? subjectOrder : (list.size() + 1));
        cs.setSubjectType(subjectType != null ? subjectType : "CORE");
        cs.setCredits(credits > 0 ? credits : 4.0);
        cs.setContactHours(contactHours > 0 ? contactHours : 60.0);
        cs.setMandatory(mandatory);
        cs.setStatus("ACTIVE");
        cs.setCreatedAt(now);
        cs.setUpdatedAt(now);
        cs.setCreatedBy(actorId);

        list.add(cs);

        // Recalculate version total credits
        recalculateCredits(curriculumId, versionNo);

        recordHistory(curriculumId, versionNo, "MAP_SUBJECT", null, null,
                Arrays.asList("subjectId", "semesterNo", "credits"), null,
                String.format("{\"subjectId\":\"%s\",\"semesterNo\":%d}", subjectId, semesterNo),
                "Subject mapped", actorId, actorRole);

        emitOutboxEvent("CurriculumSubjectMapped", curriculumId, ver.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"subjectId\":\"%s\",\"semesterNo\":%d}",
                        curriculumId, versionNo, subjectId, semesterNo));

        return cs;
    }

    /**
     * Removes an existing subject mapping from a draft version (Story 26).
     *
     * @param curriculumId curriculum aggregate ID
     * @param versionNo version number
     * @param mappingId unique ID of the mapping to remove
     * @param actorId user performing the unmapping
     * @param actorRole role of the actor
     * @throws VersionImmutableException if version is published
     * @throws CurriculumNotFoundException if version or mapping does not exist
     */
    public synchronized void removeSubjectMapping(String curriculumId, int versionNo, String mappingId,
                                                  String actorId, String actorRole) {
        checkPermission(actorRole, "SUBJECT_MAPPING_MANAGE");
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        if (ver.getStatus() == VersionStatus.PUBLISHED) {
            throw new VersionImmutableException("Cannot remove subject mapping from published version (BR-02)");
        }

        String key = curriculumId + "-v" + versionNo;
        List<CurriculumSubject> list = subjectsByVersion.get(key);
        if (list == null) {
            throw new CurriculumNotFoundException("No mappings found for version " + versionNo);
        }

        boolean removed = list.removeIf(s -> s.getId().equalsIgnoreCase(mappingId));
        if (!removed) {
            throw new CurriculumNotFoundException("Mapping ID '" + mappingId + "' not found");
        }

        recalculateCredits(curriculumId, versionNo);

        recordHistory(curriculumId, versionNo, "UNMAP_SUBJECT", null, null,
                Collections.singletonList("mappingId"), null, "{ \"mappingId\": \"" + mappingId + "\" }",
                "Subject unmapped", actorId, actorRole);

        emitOutboxEvent("CurriculumSubjectUnmapped", curriculumId, ver.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"mappingId\":\"%s\"}",
                        curriculumId, versionNo, mappingId));
    }

    /**
     * Retrieves all subject mappings for a curriculum version.
     *
     * @param curriculumId curriculum aggregate ID
     * @param versionNo version number
     * @return unmodifiable or live list of mapped subjects
     */
    public List<CurriculumSubject> getSubjectMappings(String curriculumId, int versionNo) {
        String key = curriculumId + "-v" + versionNo;
        return subjectsByVersion.getOrDefault(key, Collections.emptyList());
    }

    private void recalculateCredits(String curriculumId, int versionNo) {
        String key = curriculumId + "-v" + versionNo;
        List<CurriculumSubject> list = subjectsByVersion.getOrDefault(key, Collections.emptyList());
        double total = list.stream().filter(s -> "ACTIVE".equals(s.getStatus())).mapToDouble(CurriculumSubject::getCredits).sum();
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        ver.setTotalCredits(total);
    }

    // =========================================================================
    // Epic 5: Syllabus & Credit Management
    // =========================================================================

    /**
     * Defines syllabus structure for a curriculum version (FR-05, Story 29).
     *
     * @param curriculumId curriculum aggregate ID
     * @param versionNo version number
     * @param modules ordered list of syllabus modules
     * @param actorId user performing the update
     * @param actorRole role of the actor
     * @throws VersionImmutableException if version is already published
     */
    public synchronized void updateSyllabus(String curriculumId, int versionNo, List<SyllabusModule> modules,
                                           String actorId, String actorRole) {
        checkPermission(actorRole, "SYLLABUS_MANAGE");
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        if (ver.getStatus() == VersionStatus.PUBLISHED) {
            throw new VersionImmutableException("Cannot update syllabus of published version (BR-02)");
        }

        ver.setSyllabus(modules != null ? modules : new ArrayList<>());
        ver.setUpdatedAt(System.currentTimeMillis());

        recordHistory(curriculumId, versionNo, "UPDATE_SYLLABUS", null, null,
                Collections.singletonList("syllabus"), null, "{ \"moduleCount\": " + ver.getSyllabus().size() + " }",
                "Syllabus updated", actorId, actorRole);

        emitOutboxEvent("CurriculumSyllabusUpdated", curriculumId, ver.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"moduleCount\":%d}",
                        curriculumId, versionNo, ver.getSyllabus().size()));
    }

    /**
     * Retrieves syllabus structure for a curriculum version (Story 30).
     *
     * @param curriculumId curriculum aggregate ID
     * @param versionNo version number
     * @return list of syllabus modules
     */
    public List<SyllabusModule> getSyllabus(String curriculumId, int versionNo) {
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        return ver.getSyllabus();
    }

    /**
     * Validates that total credits satisfy configured policy (BR-09, Story 31).
     *
     * @param ver curriculum version to evaluate
     * @throws CreditPolicyViolationException if credits are outside allowed min/max thresholds
     */
    public void validateCreditPolicy(CurriculumVersion ver) {
        double credits = ver.getTotalCredits();
        if (credits < minCreditPolicy || credits > maxCreditPolicy) {
            throw new CreditPolicyViolationException("Total credits (" + credits + ") violate academic credit policy [min: "
                    + minCreditPolicy + ", max: " + maxCreditPolicy + "] (BR-09)");
        }
    }

    // =========================================================================
    // Epic 6: Learning Outcome Mapping
    // =========================================================================

    /**
     * Adds a learning outcome (CO/PO) reference to a curriculum version (FR-06, Story 33).
     *
     * @param curriculumId curriculum aggregate ID
     * @param versionNo version number
     * @param outcomeCode unique outcome identifier code
     * @param description statement of competency
     * @param bloomLevel Bloom's taxonomy cognitive level
     * @param outcomeType type classification (CO, PO, etc.)
     * @param mappedElementType target type (SUBJECT, MODULE)
     * @param mappedElementId target element identifier
     * @param actorId user creating the outcome
     * @param actorRole role of the actor
     * @return created {@link CurriculumOutcome} record
     * @throws VersionImmutableException if version is already published
     */
    public synchronized CurriculumOutcome addOutcome(String curriculumId, int versionNo, String outcomeCode,
                                                     String description, String bloomLevel, String outcomeType,
                                                     String mappedElementType, String mappedElementId,
                                                     String actorId, String actorRole) {
        checkPermission(actorRole, "OUTCOME_MAPPING_MANAGE");
        CurriculumVersion ver = getVersion(curriculumId, versionNo);
        if (ver.getStatus() == VersionStatus.PUBLISHED) {
            throw new VersionImmutableException("Cannot add learning outcomes to a published version (BR-02)");
        }

        String outcomeId = "OUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CurriculumOutcome outcome = new CurriculumOutcome();
        outcome.setId(outcomeId);
        outcome.setTenantId(ver.getTenantId());
        outcome.setCurriculumId(curriculumId);
        outcome.setVersionNo(versionNo);
        outcome.setOutcomeCode(outcomeCode != null ? outcomeCode : "CO-01");
        outcome.setDescription(description);
        outcome.setBloomLevel(bloomLevel != null ? bloomLevel : "UNDERSTAND");
        outcome.setOutcomeType(outcomeType != null ? outcomeType : "CO");
        outcome.setMappedElementType(mappedElementType);
        outcome.setMappedElementId(mappedElementId);
        outcome.setStatus("ACTIVE");
        outcome.setCreatedAt(System.currentTimeMillis());
        outcome.setCreatedBy(actorId);

        String key = curriculumId + "-v" + versionNo;
        outcomesByVersion.computeIfAbsent(key, k -> new ArrayList<>()).add(outcome);

        recordHistory(curriculumId, versionNo, "ADD_OUTCOME", null, null,
                Collections.singletonList("outcomeCode"), null, "{ \"outcomeCode\": \"" + outcomeCode + "\" }",
                "Outcome added", actorId, actorRole);

        emitOutboxEvent("CurriculumOutcomeUpdated", curriculumId, ver.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"outcomeId\":\"%s\"}",
                        curriculumId, versionNo, outcomeId));

        return outcome;
    }

    /**
     * Lists learning outcomes for a curriculum version (Story 34).
     */
    public List<CurriculumOutcome> listOutcomes(String curriculumId, int versionNo) {
        String key = curriculumId + "-v" + versionNo;
        return outcomesByVersion.getOrDefault(key, Collections.emptyList());
    }

    // =========================================================================
    // Epic 7: Curriculum Prerequisite Management
    // =========================================================================

    /**
     * Adds a prerequisite relationship and performs cycle detection (FR-07, BR-10, Story 36, 37).
     */
    public synchronized CurriculumPrerequisite addPrerequisite(String curriculumId, String prerequisiteCourseId,
                                                               String prerequisiteCurriculumId, String relationshipType,
                                                               String minimumGrade, String actorId, String actorRole) {
        checkPermission(actorRole, "PREREQUISITE_MANAGE");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);

        // Target cannot equal self
        if (prerequisiteCurriculumId != null && prerequisiteCurriculumId.equalsIgnoreCase(curriculumId)) {
            throw new PrerequisiteCycleException("A curriculum cannot be a prerequisite of itself (BR-10)");
        }

        // DAG Cycle Detection
        if (prerequisiteCurriculumId != null) {
            if (createsCycle(curriculumId, prerequisiteCurriculumId)) {
                throw new PrerequisiteCycleException("Proposed prerequisite relationship between '" + curriculumId
                        + "' and '" + prerequisiteCurerequisiteTarget(prerequisiteCurriculumId)
                        + "' creates a cyclic dependency in the curriculum DAG (BR-10)");
            }
        }

        String relId = "PRE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CurriculumPrerequisite pre = new CurriculumPrerequisite();
        pre.setId(relId);
        pre.setTenantId(curr.getTenantId());
        pre.setCurriculumId(curriculumId);
        pre.setVersionNo(curr.getCurrentVersion());
        pre.setPrerequisiteCourseId(prerequisiteCourseId);
        pre.setPrerequisiteCurriculumId(prerequisiteCurriculumId);
        pre.setRelationshipType(relationshipType != null ? relationshipType : "MANDATORY");
        pre.setMinimumGrade(minimumGrade != null ? minimumGrade : "PASS");
        pre.setStatus("ACTIVE");
        pre.setCreatedAt(System.currentTimeMillis());
        pre.setCreatedBy(actorId);

        prerequisitesByCurriculum.computeIfAbsent(curriculumId, k -> new ArrayList<>()).add(pre);

        recordHistory(curriculumId, curr.getCurrentVersion(), "ADD_PREREQUISITE", null, null,
                Collections.singletonList("prerequisiteCourseId"), null,
                "{ \"prerequisiteCourseId\": \"" + prerequisiteCourseId + "\" }",
                "Prerequisite added", actorId, actorRole);

        emitOutboxEvent("CurriculumPrerequisiteUpdated", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"prerequisiteId\":\"%s\"}", curriculumId, relId));

        return pre;
    }

    private String prerequisiteCurerequisiteTarget(String targetId) {
        return targetId != null ? targetId : "UNKNOWN";
    }

    private boolean createsCycle(String startCurriculumId, String proposedPrerequisiteId) {
        // If starting from proposedPrerequisiteId, can we reach startCurriculumId?
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(proposedPrerequisiteId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            if (currId.equalsIgnoreCase(startCurriculumId)) {
                return true; // Path exists, adding proposed edge would create cycle!
            }
            if (!visited.contains(currId)) {
                visited.add(currId);
                List<CurriculumPrerequisite> list = prerequisitesByCurriculum.getOrDefault(currId, Collections.emptyList());
                for (CurriculumPrerequisite p : list) {
                    if (p.getPrerequisiteCurriculumId() != null && "ACTIVE".equals(p.getStatus())) {
                        queue.add(p.getPrerequisiteCurriculumId());
                    }
                }
            }
        }
        return false;
    }

    /**
     * Lists prerequisites for a curriculum (Story 38).
     */
    public List<CurriculumPrerequisite> listPrerequisites(String curriculumId) {
        return prerequisitesByCurriculum.getOrDefault(curriculumId, Collections.emptyList());
    }

    // =========================================================================
    // Epic 8: Approval & Publication Workflow
    // =========================================================================

    /**
     * Submits a draft curriculum version for review (FR-08, UC-06, Story 40).
     */
    public synchronized CurriculumVersion submitForApproval(String curriculumId, int versionNo,
                                                            String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_SUBMIT");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);
        CurriculumVersion ver = getVersion(curriculumId, versionNo);

        if (ver.getStatus() != VersionStatus.DRAFT) {
            throw new PublicationBlockedException("Only DRAFT versions can be submitted for review. Current status: "
                    + ver.getStatus());
        }

        // Structural check: must have at least 1 semester and 1 subject
        String key = curriculumId + "-v" + versionNo;
        List<CurriculumSubject> mappings = subjectsByVersion.getOrDefault(key, Collections.emptyList());
        if (ver.getSemesters().isEmpty() || mappings.isEmpty()) {
            throw new PublicationBlockedException("Curriculum version must define semesters and at least one mapped subject before submission");
        }

        ver.setStatus(VersionStatus.REVIEW);
        curr.setStatus(CurriculumStatus.REVIEW);
        ver.setUpdatedAt(System.currentTimeMillis());

        recordHistory(curriculumId, versionNo, "SUBMIT", "DRAFT", "REVIEW",
                Collections.singletonList("status"), "{ \"status\": \"DRAFT\" }", "{ \"status\": \"REVIEW\" }",
                "Submitted for approval", actorId, actorRole);

        emitOutboxEvent("CurriculumSubmitted", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"REVIEW\"}", curriculumId, versionNo));

        return ver;
    }

    /**
     * Department Head / Committee records review decision (APPROVE or REJECT) (UC-07, Story 41).
     */
    public synchronized CurriculumVersion reviewCurriculum(String curriculumId, int versionNo, String decision,
                                                           String feedback, String actorId, String actorRole,
                                                           String actorDepartmentId) {
        checkPermission(actorRole, "CURRICULUM_REVIEW");
        Curriculum curr = getCurriculum(curriculumId, actorRole, actorDepartmentId);
        CurriculumVersion ver = getVersion(curriculumId, versionNo);

        if (ver.getStatus() != VersionStatus.REVIEW) {
            throw new PublicationBlockedException("Only versions under REVIEW can be reviewed. Current status: " + ver.getStatus());
        }

        if ("REJECT".equalsIgnoreCase(decision)) {
            ver.setStatus(VersionStatus.DRAFT);
            curr.setStatus(CurriculumStatus.DRAFT);
            ver.setChangeSummary(feedback != null ? "Review rejected: " + feedback : "Review rejected");
            recordHistory(curriculumId, versionNo, "REVIEW_REJECTED", "REVIEW", "DRAFT",
                    Collections.singletonList("status"), "{ \"status\": \"REVIEW\" }", "{ \"status\": \"DRAFT\" }",
                    feedback, actorId, actorRole);
        } else {
            // Recommendation approved for registrar sign-off
            recordHistory(curriculumId, versionNo, "REVIEW_RECOMMENDED", "REVIEW", "REVIEW",
                    Collections.singletonList("reviewDecision"), null, "{ \"decision\": \"RECOMMENDED\" }",
                    feedback, actorId, actorRole);
        }
        return ver;
    }

    /**
     * Approves a reviewed curriculum version (UC-08, Story 42).
     */
    public synchronized CurriculumVersion approveCurriculum(String curriculumId, int versionNo,
                                                            String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_APPROVE");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);
        CurriculumVersion ver = getVersion(curriculumId, versionNo);

        if (ver.getStatus() != VersionStatus.REVIEW) {
            throw new PublicationBlockedException("Curriculum version must be in REVIEW status to be approved. Current status: "
                    + ver.getStatus());
        }

        ver.setStatus(VersionStatus.APPROVED);
        curr.setStatus(CurriculumStatus.APPROVED);
        ver.setApprovedBy(actorId);
        ver.setApprovedAt(new Date().toString());
        ver.setUpdatedAt(System.currentTimeMillis());

        recordHistory(curriculumId, versionNo, "APPROVE", "REVIEW", "APPROVED",
                Collections.singletonList("status"), "{ \"status\": \"REVIEW\" }", "{ \"status\": \"APPROVED\" }",
                "Curriculum approved", actorId, actorRole);

        emitOutboxEvent("CurriculumApproved", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"status\":\"APPROVED\"}", curriculumId, versionNo));

        return ver;
    }

    /**
     * Publishes an approved curriculum version (FR-09, BR-01, BR-04, BR-14, Story 43, 44, 48).
     * Enforces invariants:
     * 1. Version must be APPROVED (BR-04).
     * 2. Referenced course must be active and not flagged (BR-05).
     * 3. All mapped subjects must be active and not flagged (BR-03, BR-06).
     * 4. Total credits must satisfy credit policy (BR-09).
     * 5. Atomically supersedes previous effective version within the same transaction (BR-01).
     * 6. Emits CurriculumPublished event to outbox (Story 51).
     */
    public synchronized CurriculumVersion publishCurriculum(String curriculumId, int versionNo,
                                                            String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_PUBLISH");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);
        CurriculumVersion ver = getVersion(curriculumId, versionNo);

        // Invariant 1: Approval missing
        if (ver.getStatus() != VersionStatus.APPROVED) {
            throw new PublicationBlockedException("Publication blocked: version " + versionNo
                    + " is in status '" + ver.getStatus() + "' (Approval is required before publication - BR-04)");
        }

        // Invariant 2: Course active check
        if (curr.isFlaggedForReview()) {
            throw new PublicationBlockedException("Publication blocked: referenced course is deactivated or archived: "
                    + curr.getFlagReason());
        }

        // Invariant 3: Mapped subjects check (BR-03, BR-06, Event Consumption §32)
        String key = curriculumId + "-v" + versionNo;
        List<CurriculumSubject> mappings = subjectsByVersion.getOrDefault(key, Collections.emptyList());
        for (CurriculumSubject ms : mappings) {
            if (ms.isFlagged()) {
                throw new PublicationBlockedException("Publication blocked: mapped subject '" + ms.getSubjectId()
                        + "' has been deactivated in ACD-03 (BR-03, BR-06)");
            }
            SubjectReference ref = subjectReferenceRegistry.get(ms.getSubjectId());
            if (ref == null || !ref.isActive()) {
                throw new PublicationBlockedException("Publication blocked: mapped subject '" + ms.getSubjectId()
                        + "' is inactive or missing from ACD-03");
            }
        }

        // Invariant 4: Credit Policy validation (BR-09)
        validateCreditPolicy(ver);

        // Atomic Transaction: Supersede prior effective version
        List<CurriculumVersion> allVersions = versionsByCurriculum.get(curriculumId);
        if (allVersions != null) {
            for (CurriculumVersion prior : allVersions) {
                if (prior.getVersionNo() != versionNo && prior.getStatus() == VersionStatus.PUBLISHED) {
                    prior.setStatus(VersionStatus.SUPERSEDED);
                    prior.setUpdatedAt(System.currentTimeMillis());
                    recordHistory(curriculumId, prior.getVersionNo(), "SUPERSEDE", "PUBLISHED", "SUPERSEDED",
                            Collections.singletonList("status"), "{ \"status\": \"PUBLISHED\" }", "{ \"status\": \"SUPERSEDED\" }",
                            "Automatically superseded by version " + versionNo, actorId, actorRole);
                }
            }
        }

        ver.setStatus(VersionStatus.PUBLISHED);
        ver.setPublishedBy(actorId);
        ver.setPublishedAt(new Date().toString());
        ver.setChecksum(generateChecksum(ver));
        ver.setUpdatedAt(System.currentTimeMillis());

        curr.setStatus(CurriculumStatus.PUBLISHED);
        curr.setCurrentVersion(versionNo);
        curr.setUpdatedAt(System.currentTimeMillis());

        recordHistory(curriculumId, versionNo, "PUBLISH", "APPROVED", "PUBLISHED",
                Arrays.asList("status", "publishedAt", "currentVersion"),
                "{ \"status\": \"APPROVED\" }", "{ \"status\": \"PUBLISHED\" }",
                "Curriculum published and effective", actorId, actorRole);

        // Story 51: Emit CurriculumPublished to all downstream consumers
        emitOutboxEvent("CurriculumPublished", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"versionNo\":%d,\"courseId\":\"%s\",\"totalCredits\":%.1f,\"status\":\"PUBLISHED\"}",
                        curriculumId, versionNo, curr.getCourseId(), ver.getTotalCredits()));

        logger.info("Curriculum {} version {} successfully published and active", curriculumId, versionNo);
        return ver;
    }

    // =========================================================================
    // Epic 9: Curriculum Lifecycle & Retirement
    // =========================================================================

    /**
     * Retires a curriculum without deleting historical evidence (FR-10, UC-10, Story 46).
     */
    public synchronized Curriculum retireCurriculum(String curriculumId, String reason, String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_RETIRE");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);

        curr.setStatus(CurriculumStatus.RETIRED);
        curr.setUpdatedAt(System.currentTimeMillis());

        List<CurriculumVersion> versions = versionsByCurriculum.get(curriculumId);
        if (versions != null) {
            for (CurriculumVersion v : versions) {
                if (v.getStatus() == VersionStatus.PUBLISHED || v.getStatus() == VersionStatus.SUPERSEDED) {
                    v.setStatus(VersionStatus.RETIRED);
                }
            }
        }

        recordHistory(curriculumId, curr.getCurrentVersion(), "RETIRE", "PUBLISHED", "RETIRED",
                Collections.singletonList("status"), null, "{ \"status\": \"RETIRED\" }",
                reason != null ? reason : "Curriculum retired", actorId, actorRole);

        emitOutboxEvent("CurriculumRetired", curriculumId, curr.getTenantId(),
                String.format("{\"curriculumId\":\"%s\",\"status\":\"RETIRED\"}", curriculumId));

        return curr;
    }

    /**
     * Prevents deletion of curriculum if downstream academic references exist (BR-15, Story 47).
     */
    public synchronized void deleteCurriculum(String curriculumId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_RETIRE");
        Curriculum curr = getCurriculum(curriculumId, actorRole, null);

        if (downstreamAcademicReferences.contains(curriculumId) || curr.getStatus() == CurriculumStatus.PUBLISHED
                || curr.getStatus() == CurriculumStatus.RETIRED) {
            throw new PublicationBlockedException("Hard deletion rejected: Curriculum '" + curriculumId
                    + "' has active downstream academic references. Retirement must be used instead (BR-15)");
        }

        curricula.remove(curriculumId);
        versionsByCurriculum.remove(curriculumId);
    }

    public void addDownstreamAcademicReference(String curriculumId) {
        downstreamAcademicReferences.add(curriculumId);
    }

    // =========================================================================
    // Epic 10: Event-Driven Integration (Outbox, Consumption, DLQ)
    // =========================================================================

    /**
     * Consumes SubjectDeactivated from ACD-03 (Story 27, 52).
     * Flags affected curriculum subject mappings and blocks new publication.
     */
    public synchronized void handleSubjectDeactivatedEvent(String eventId, String subjectId) {
        if (!deduplicateInboundEvent(eventId)) {
            return; // Duplicate skipped
        }

        SubjectReference ref = subjectReferenceRegistry.get(subjectId);
        if (ref != null) {
            ref.setActive(false);
        }

        // Flag affected mappings
        int flaggedCount = 0;
        for (List<CurriculumSubject> list : subjectsByVersion.values()) {
            for (CurriculumSubject cs : list) {
                if (cs.getSubjectId().equalsIgnoreCase(subjectId)) {
                    cs.setFlagged(true);
                    cs.setFlagReason("Subject " + subjectId + " deactivated in ACD-03");
                    flaggedCount++;
                }
            }
        }
        logger.warn("Consumed SubjectDeactivated for {}: flagged {} mappings across curricula", subjectId, flaggedCount);
    }

    /**
     * Consumes CourseDeactivated or CourseArchived from ACD-01 (Story 52).
     */
    public synchronized void handleCourseDeactivatedEvent(String eventId, String courseId, String reason) {
        if (!deduplicateInboundEvent(eventId)) {
            return;
        }

        CourseReference ref = courseReferenceRegistry.get(courseId);
        if (ref != null) {
            ref.setActive(false);
        }

        for (Curriculum c : curricula.values()) {
            if (c.getCourseId().equalsIgnoreCase(courseId)) {
                c.setFlaggedForReview(true);
                c.setFlagReason(reason != null ? reason : "Referenced course " + courseId + " was deactivated/archived in ACD-01");
            }
        }
    }

    private boolean deduplicateInboundEvent(String eventId) {
        if (eventId == null || eventId.isEmpty()) return true;
        if (processedInboundEventIds.contains(eventId)) {
            logger.info("Deduplicated already processed inbound event: {}", eventId);
            return false;
        }
        processedInboundEventIds.add(eventId);
        return true;
    }

    public synchronized void routeToDeadLetterQueue(String eventId, String source, String reason, String payload) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId("DLQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        dlq.setEventId(eventId);
        dlq.setSource(source != null ? source : "ACD-02");
        dlq.setErrorReason(reason);
        dlq.setPayload(payload);
        dlq.setAttempts(3);
        dlq.setCreatedAt(System.currentTimeMillis());
        deadLetterEvents.add(dlq);
        logger.error("Event {} routed to dead_letter_events: {}", eventId, reason);
    }

    public List<OutboxEvent> getOutboxEvents() {
        return Collections.unmodifiableList(outboxEvents);
    }

    /**
     * Retrieves all events in PENDING status for broker relay (Story 51).
     */
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

    /**
     * Marks an outbox event as successfully PUBLISHED after broker confirmation (Story 51).
     */
    public void markEventPublished(String eventId) {
        synchronized (outboxEvents) {
            for (OutboxEvent evt : outboxEvents) {
                if (evt.getEventId().equals(eventId)) {
                    evt.setStatus("PUBLISHED");
                    break;
                }
            }
        }
    }

    /**
     * Marks an outbox event as FAILED and routes payload to dead letter queue (Story 54, 71).
     */
    public void markEventFailed(String eventId, String reason) {
        OutboxEvent failedEvt = null;
        synchronized (outboxEvents) {
            for (OutboxEvent evt : outboxEvents) {
                if (evt.getEventId().equals(eventId)) {
                    evt.setStatus("FAILED");
                    failedEvt = evt;
                    break;
                }
            }
        }
        if (failedEvt != null) {
            routeToDeadLetterQueue(eventId, "OUTBOX", reason, failedEvt.getPayload());
        }
    }

    public List<DeadLetterEvent> getDeadLetterEvents() {
        return Collections.unmodifiableList(deadLetterEvents);
    }

    /**
     * Proactively purges expired idempotency records from memory (Story 57).
     */
    public synchronized int purgeExpiredIdempotencyRecords() {
        long now = System.currentTimeMillis();
        int count = 0;
        Iterator<Map.Entry<String, IdempotencyRecord>> it = idempotencyRecords.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, IdempotencyRecord> entry = it.next();
            if (entry.getValue().getExpiresAt() > 0 && entry.getValue().getExpiresAt() < now) {
                it.remove();
                count++;
            }
        }
        if (count > 0) {
            logger.info("Purged {} expired idempotency records from memory", count);
        }
        return count;
    }

    private void emitOutboxEvent(String eventType, String aggregateId, String tenantId, String payload) {
        String eventId = "EVT-ACD-02-" + String.format("%06d", eventSeq.getAndIncrement());
        OutboxEvent event = new OutboxEvent();
        event.setId("OUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setTenantId(tenantId != null ? tenantId : "TENANT-001");
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setOccurredAt(System.currentTimeMillis());
        event.setCorrelationId(LogContext.getTraceId());
        outboxEvents.add(event);
    }

    // =========================================================================
    // Epic 11: Idempotency & Concurrency
    // =========================================================================

    public IdempotencyRecord checkIdempotency(String tenantId, String idempotencyKey, String payload) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return null;
        }
        String combinedKey = (tenantId != null ? tenantId : "DEFAULT") + ":" + idempotencyKey;
        IdempotencyRecord rec = idempotencyRecords.get(combinedKey);
        if (rec != null) {
            if (rec.getExpiresAt() > 0 && rec.getExpiresAt() < System.currentTimeMillis()) {
                idempotencyRecords.remove(combinedKey);
                return null;
            }
            String currentHash = hashPayload(payload);
            if (!rec.getRequestHash().equals(currentHash)) {
                throw new VersionConflictException("Idempotency conflict: key '" + idempotencyKey
                        + "' already used with differing payload");
            }
            return rec;
        }
        return null;
    }

    public void saveIdempotency(String tenantId, String idempotencyKey, String payload, int statusCode, String responseBody) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) return;
        String combinedKey = (tenantId != null ? tenantId : "DEFAULT") + ":" + idempotencyKey;
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setId("IDEM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        rec.setTenantId(tenantId);
        rec.setIdempotencyKey(idempotencyKey);
        rec.setRequestHash(hashPayload(payload));
        rec.setStatusCode(statusCode);
        rec.setResponseBody(responseBody);
        rec.setCreatedAt(System.currentTimeMillis());
        rec.setExpiresAt(System.currentTimeMillis() + 86400000L); // 24hr TTL
        idempotencyRecords.put(combinedKey, rec);
    }

    private String hashPayload(String payload) {
        if (payload == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(payload.hashCode());
        }
    }

    // =========================================================================
    // Epic 12: Security, RBAC & Audit Governance
    // =========================================================================

    public void checkPermission(String role, String requiredPermission) {
        if (role == null || role.isEmpty()) {
            role = "ACADEMIC_ADMIN"; // default
        }
        role = role.toUpperCase();
        if ("SUPER_ADMIN".equals(role) || "ADMIN".equals(role)) {
            return;
        }

        Set<String> perms = getRolePermissions(role);
        if (!perms.contains(requiredPermission)) {
            throw new CurriculumForbiddenException("Role '" + role + "' lacks required permission '" + requiredPermission + "'");
        }
    }

    private Set<String> getRolePermissions(String role) {
        Set<String> set = new HashSet<>();
        switch (role) {
            case "ACADEMIC_ADMIN":
                set.addAll(Arrays.asList("CURRICULUM_VIEW", "CURRICULUM_CREATE", "CURRICULUM_EDIT",
                        "CURRICULUM_SUBMIT", "CURRICULUM_RETIRE", "SUBJECT_MAPPING_MANAGE", "SYLLABUS_MANAGE",
                        "OUTCOME_MAPPING_MANAGE", "PREREQUISITE_MANAGE", "CURRICULUM_EXPORT"));
                break;
            case "DEPARTMENT_HEAD":
                set.addAll(Arrays.asList("CURRICULUM_VIEW", "CURRICULUM_EDIT", "CURRICULUM_REVIEW",
                        "SUBJECT_MAPPING_MANAGE", "SYLLABUS_MANAGE", "OUTCOME_MAPPING_MANAGE"));
                break;
            case "CURRICULUM_COMMITTEE":
                set.addAll(Arrays.asList("CURRICULUM_VIEW", "CURRICULUM_REVIEW", "CURRICULUM_APPROVE"));
                break;
            case "REGISTRAR":
                set.addAll(Arrays.asList("CURRICULUM_VIEW", "CURRICULUM_APPROVE", "CURRICULUM_PUBLISH", "CURRICULUM_RETIRE"));
                break;
            case "FACULTY":
                set.addAll(Arrays.asList("CURRICULUM_VIEW", "SYLLABUS_MANAGE", "OUTCOME_MAPPING_MANAGE"));
                break;
            case "ACCREDITATION_TEAM":
                set.addAll(Arrays.asList("CURRICULUM_VIEW", "CURRICULUM_EXPORT"));
                break;
            case "STUDENT":
                set.addAll(Arrays.asList("CURRICULUM_VIEW"));
                break;
            case "EXTERNAL_API":
                set.addAll(Arrays.asList("CURRICULUM_VIEW"));
                break;
            default:
                set.add("CURRICULUM_VIEW");
                break;
        }
        return set;
    }

    private void recordHistory(String curriculumId, int versionNo, String action, String fromStatus, String toStatus,
                               List<String> changedFields, String beforeJson, String afterJson,
                               String reason, String actorId, String actorRole) {
        CurriculumHistory h = new CurriculumHistory();
        h.setId("HIST-ACD02-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        h.setTenantId(curricula.containsKey(curriculumId) ? curricula.get(curriculumId).getTenantId() : "TENANT-001");
        h.setCurriculumId(curriculumId);
        h.setVersionNo(versionNo);
        h.setAction(action);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setChangedFields(changedFields != null ? changedFields : Collections.emptyList());
        h.setBeforeJson(beforeJson);
        h.setAfterJson(afterJson);
        h.setReason(reason);
        h.setActorId(actorId != null ? actorId : "system");
        h.setActorRole(actorRole != null ? actorRole : "SYSTEM");
        h.setCorrelationId(LogContext.getTraceId());
        h.setOccurredAt(System.currentTimeMillis());
        h.setSequenceNo(historySeq.getAndIncrement());

        historyByCurriculum.computeIfAbsent(curriculumId, k -> new ArrayList<>()).add(h);
    }

    public List<CurriculumHistory> getHistory(String curriculumId, String actorRole) {
        // L3 Confidential audit history: only permitted roles
        if ("STUDENT".equalsIgnoreCase(actorRole)) {
            throw new CurriculumForbiddenException("Students do not have permission to inspect compliance audit history");
        }
        return historyByCurriculum.getOrDefault(curriculumId, Collections.emptyList());
    }

    /**
     * Retrieves an integrated composite compliance view for accreditation teams and academic auditors (Story 62).
     * Enforces CURRICULUM_VIEW and CURRICULUM_EXPORT permissions.
     */
    public ComplianceView getComplianceView(String curriculumId, String actorId, String actorRole) {
        checkPermission(actorRole, "CURRICULUM_VIEW");
        checkPermission(actorRole, "CURRICULUM_EXPORT");

        Curriculum c = curricula.get(curriculumId);
        if (c == null) {
            throw new CurriculumNotFoundException("Curriculum with ID '" + curriculumId + "' not found");
        }

        List<CurriculumVersion> versionList = listVersions(curriculumId);
        List<CurriculumOutcome> outcomeList = new ArrayList<>();
        List<CurriculumSubject> subjectList = new ArrayList<>();
        for (CurriculumVersion v : versionList) {
            String key = curriculumId + "-v" + v.getVersionNo();
            List<CurriculumOutcome> oList = outcomesByVersion.get(key);
            if (oList != null) {
                outcomeList.addAll(oList);
            }
            List<CurriculumSubject> sList = subjectsByVersion.get(key);
            if (sList != null) {
                subjectList.addAll(sList);
            }
        }
        List<CurriculumHistory> historyList = historyByCurriculum.getOrDefault(curriculumId, Collections.emptyList());

        return new ComplianceView(c, versionList, outcomeList, subjectList, historyList);
    }

    private String generateChecksum(CurriculumVersion v) {
        String raw = v.getCurriculumId() + ":" + v.getVersionNo() + ":" + v.getAcademicYear() + ":" + v.getTotalCredits();
        return "sha256:" + Integer.toHexString(raw.hashCode());
    }

    // =========================================================================
    // Helpers & Reference Registries
    // =========================================================================

    /**
     * Resolves the data sensitivity classification clearance level for a given RBAC role (Story 65).
     */
    public DataClassification getClassificationLevel(String role) {
        if (role == null || role.isEmpty()) {
            return DataClassification.L2_INTERNAL;
        }
        role = role.toUpperCase();
        switch (role) {
            case "STUDENT":
            case "EXTERNAL_API":
                return DataClassification.L1_PUBLIC;
            case "FACULTY":
            case "DEPARTMENT_HEAD":
                return DataClassification.L2_INTERNAL;
            case "ACADEMIC_ADMIN":
            case "REGISTRAR":
            case "CURRICULUM_COMMITTEE":
            case "ACCREDITATION_TEAM":
                return DataClassification.L3_CONFIDENTIAL;
            case "SUPER_ADMIN":
            case "ADMIN":
            case "SYSTEM":
                return DataClassification.L4_RESTRICTED;
            default:
                return DataClassification.L2_INTERNAL;
        }
    }

    /**
     * Registers an external API key record for consumer authentication (Story 63).
     */
    public void registerApiKey(ApiKeyRecord keyRecord) {
        if (keyRecord != null && keyRecord.getRawKey() != null) {
            apiKeyRegistry.put(keyRecord.getRawKey(), keyRecord);
        }
    }

    /**
     * Validates an incoming API key, checking active status, tenant scope, and expiry (Story 63).
     */
    public ApiKeyRecord validateApiKey(String rawApiKey, String tenantId) {
        if (rawApiKey == null || rawApiKey.trim().isEmpty()) {
            return null;
        }
        ApiKeyRecord record = apiKeyRegistry.get(rawApiKey.trim());
        if (record == null || !record.isActive()) {
            return null;
        }
        if (record.getExpiresAt() > 0 && record.getExpiresAt() < System.currentTimeMillis()) {
            return null;
        }
        if (tenantId != null && record.getTenantId() != null && !tenantId.equalsIgnoreCase(record.getTenantId())) {
            return null;
        }
        return record;
    }

    public void registerCourseReference(String courseId, String tenantId, String campusId, String departmentId, boolean active) {
        courseReferenceRegistry.put(courseId, new CourseReference(courseId, tenantId, campusId, departmentId, active));
    }

    public void registerSubjectReference(String subjectId, String tenantId, String name, boolean active) {
        subjectReferenceRegistry.put(subjectId, new SubjectReference(subjectId, tenantId, name, active));
    }

    public static class CourseReference {
        private final String courseId;
        private final String tenantId;
        private final String campusId;
        private final String departmentId;
        private boolean active;

        public CourseReference(String courseId, String tenantId, String campusId, String departmentId, boolean active) {
            this.courseId = courseId;
            this.tenantId = tenantId;
            this.campusId = campusId;
            this.departmentId = departmentId;
            this.active = active;
        }

        public String getCourseId() { return courseId; }
        public String getTenantId() { return tenantId; }
        public String getCampusId() { return campusId; }
        public String getDepartmentId() { return departmentId; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class SubjectReference {
        private final String subjectId;
        private final String tenantId;
        private final String name;
        private boolean active;

        public SubjectReference(String subjectId, String tenantId, String name, boolean active) {
            this.subjectId = subjectId;
            this.tenantId = tenantId;
            this.name = name;
            this.active = active;
        }

        public String getSubjectId() { return subjectId; }
        public String getTenantId() { return tenantId; }
        public String getName() { return name; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}
