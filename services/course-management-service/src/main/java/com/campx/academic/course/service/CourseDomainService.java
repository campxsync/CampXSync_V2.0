package com.campx.academic.course.service;

import com.campx.academic.course.exception.*;
import com.campx.academic.course.model.CourseModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain business logic for ACD-01: Course Management Service.
 * Implements normalized uniqueness, lifecycle state machine, immutable versioning,
 * DAG prerequisite cycle detection, batch offerings deactivation guard,
 * transactional outbox events, and 7-role RBAC matrix.
 */
public class CourseDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CourseDomainService.class);

    // Primary Collections
    private final Map<String, Course> courses = new ConcurrentHashMap<>();
    private final Map<String, CourseVersion> courseVersions = new ConcurrentHashMap<>();
    private final Map<String, CoursePrerequisite> prerequisites = new ConcurrentHashMap<>();
    private final List<CourseHistory> courseHistories = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CourseCatalogItem> courseCatalog = new ConcurrentHashMap<>();
    private final Map<String, CourseBatchOffering> batchOfferings = new ConcurrentHashMap<>();
    private final Map<String, CourseAccreditation> accreditations = new ConcurrentHashMap<>();
    private final Map<String, CourseAttendancePolicy> attendancePolicies = new ConcurrentHashMap<>();
    private final Map<String, CourseEvaluationPolicy> evaluationPolicies = new ConcurrentHashMap<>();
    private final Map<String, CourseDocument> documents = new ConcurrentHashMap<>();
    private final List<OutboxEvent> outboxEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> deadLetterEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final Map<String, InboxEvent> inboxEvents = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterQueue = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> activeDepartments = Collections.synchronizedSet(new HashSet<>());
    private final List<CourseDepartmentAssociation> departmentAssociations = Collections.synchronizedList(new ArrayList<>());
    private final List<CourseCurriculumMap> curriculumMaps = Collections.synchronizedList(new ArrayList<>());
    private final List<CourseSubjectMapping> subjectMappings = Collections.synchronizedList(new ArrayList<>());
    private final List<CourseArchiveRecord> archiveRecords = Collections.synchronizedList(new ArrayList<>());

    public CourseDomainService() {
        seedInitialData();
    }

    private void seedInitialData() {
        // Known authorized departments
        activeDepartments.add("DEP_CS");
        activeDepartments.add("DEP_EC");
        activeDepartments.add("DEP_MECH");
        activeDepartments.add("DEP_MATH");

        // Seed an initial active course
        Course c1 = new Course();
        c1.setId("CRS_CS101");
        c1.setCourseCode("CS101");
        c1.setCourseName("Introduction to Computer Science");
        c1.setDescription("Foundations of algorithmic problem solving and programming.");
        c1.setDepartmentId("DEP_CS");
        c1.setDurationYears(1);
        c1.setTotalCredits(4.0);
        c1.setStatus("ACTIVE");
        c1.setCurrentVersion(1);
        c1.getTags().addAll(Arrays.asList("UG", "CORE", "PROGRAMMING"));
        courses.put(c1.getId(), c1);

        // Version for c1
        CourseVersion v1 = new CourseVersion();
        v1.setId("VER_CS101_1");
        v1.setCourseId(c1.getId());
        v1.setVersionNo(1);
        v1.setStatus("EFFECTIVE");
        v1.setSnapshotJson("{\"courseCode\":\"CS101\",\"totalCredits\":4.0}");
        v1.setChecksum("CHK-CS101-V1");
        courseVersions.put(v1.getId(), v1);

        // Catalog projection
        syncCatalogProjection(c1);

        // Policy seeds
        CourseAttendancePolicy ap = new CourseAttendancePolicy();
        ap.setId("POL_ATT_CS101");
        ap.setCourseId(c1.getId());
        ap.setMinAttendancePercent(75.0);
        attendancePolicies.put(c1.getId(), ap);

        CourseEvaluationPolicy ep = new CourseEvaluationPolicy();
        ep.setId("POL_EVAL_CS101");
        ep.setCourseId(c1.getId());
        ep.setInternalWeightage(40.0);
        ep.setExternalWeightage(60.0);
        ep.setTotalCredits(4.0);
        evaluationPolicies.put(c1.getId(), ep);
    }

    /**
     * Normalizes a course code string into uppercase without leading/trailing whitespace.
     *
     * @param code raw course code
     * @return normalized uppercase course code, or null if input is null
     */
    public static String normalizeCode(String code) {
        if (code == null) return null;
        return code.trim().toUpperCase();
    }

    // =========================================================================
    // Epic 1: Course Definition & Identity Management
    // =========================================================================

    /**
     * Creates a new draft course with normalized code uniqueness and department verification.
     *
     * @param course the course aggregate definition to create
     * @return persisted course in DRAFT status
     * @throws CourseValidationException   if mandatory fields are missing or invalid
     * @throws CourseCodeConflictException if the course code already exists within the tenant
     */
    public Course createDraftCourse(Course course) {
        try (FlowTracker flow = logger.flow("CourseCreate", "createDraftCourse")) {
            if (course == null) {
                throw new CourseValidationException("Course payload cannot be null");
            }

            // Normalization (BR-02)
            String normalizedCode = normalizeCode(course.getCourseCode());
            if (normalizedCode == null || normalizedCode.isEmpty()) {
                throw new CourseValidationException("courseCode is mandatory");
            }
            course.setCourseCode(normalizedCode);

            if (course.getCourseName() == null || course.getCourseName().trim().isEmpty()) {
                throw new CourseValidationException("courseName is mandatory");
            }

            // Total credits must be greater than zero (BR-03)
            if (course.getTotalCredits() <= 0.0) {
                throw new CourseValidationException("totalCredits must be greater than zero");
            }

            // Department validation (BR-04)
            if (course.getDepartmentId() == null || course.getDepartmentId().trim().isEmpty()) {
                throw new CourseValidationException("departmentId is mandatory");
            }
            if (!activeDepartments.contains(course.getDepartmentId())) {
                throw new CourseValidationException("ACD_INVALID_DEPARTMENT",
                        "Department with identifier [" + course.getDepartmentId() + "] does not exist or is inactive");
            }

            // Scoped uniqueness (BR-01)
            for (Course existing : courses.values()) {
                if (existing.getCourseCode().equalsIgnoreCase(normalizedCode)
                        && existing.getTenantId().equals(course.getTenantId())) {
                    throw new CourseCodeConflictException(normalizedCode);
                }
            }

            // Assign identity
            if (course.getId() == null || course.getId().trim().isEmpty()) {
                course.setId("CRS_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            }
            course.setStatus("DRAFT");
            course.setCurrentVersion(1);
            course.setCreatedAt(System.currentTimeMillis());
            course.setUpdatedAt(System.currentTimeMillis());

            courses.put(course.getId(), course);

            // Record initial history
            recordHistory(course.getId(), "CREATE", null, "DRAFT", "Initial draft course creation");

            // Transactional Outbox event (FR-10)
            emitOutboxEvent("CourseCreated", course.getId(), course.getTenantId(),
                    "{\"id\":\"" + course.getId() + "\",\"code\":\"" + course.getCourseCode() + "\",\"status\":\"DRAFT\"}");

            // Platform compliance audit
            recordAudit("CREATE", "COURSE", course.getId(), "SUCCESS", "Draft course created: " + normalizedCode);

            logger.info("Created draft course [{}] with id [{}]", normalizedCode, course.getId());
            return course;
        }
    }

    /**
     * Resolves a course by its unique aggregate identifier or normalized course code.
     *
     * @param id primary ID or courseCode
     * @return matching course aggregate
     * @throws CourseNotFoundException if no matching course is found
     */
    public Course getCourse(String id) {
        Course c = courses.get(id);
        if (c == null) {
            // Check lookup by course code as well
            for (Course existing : courses.values()) {
                if (existing.getCourseCode().equalsIgnoreCase(normalizeCode(id))) {
                    return existing;
                }
            }
            throw new CourseNotFoundException("Course", id);
        }
        return c;
    }

    /**
     * Returns a list of all registered course aggregates across all statuses.
     *
     * @return list of courses
     */
    public List<Course> listCourses() {
        return new ArrayList<>(courses.values());
    }

    /**
     * Updates mutable fields of a course in DRAFT status. Direct update of ACTIVE courses is rejected.
     *
     * @param id     course identifier
     * @param update updated course fields
     * @return updated course
     * @throws InvalidCourseStateException if course is in ACTIVE status requiring a new version workflow
     * @throws CourseValidationException   if department reference is invalid
     */
    public Course updateDraftCourse(String id, Course update) {
        try (FlowTracker flow = logger.flow("CourseUpdate", "updateDraftCourse")) {
            Course existing = getCourse(id);

            // If course is already ACTIVE, direct in-place update is prohibited (BR-06, BR-11)
            if ("ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                throw new InvalidCourseStateException(
                        "Changes to published course require new version workflow instead of direct in-place edit");
            }

            if (update.getCourseName() != null && !update.getCourseName().trim().isEmpty()) {
                existing.setCourseName(update.getCourseName().trim());
            }
            if (update.getDescription() != null) {
                existing.setDescription(update.getDescription());
            }
            if (update.getTotalCredits() > 0.0) {
                existing.setTotalCredits(update.getTotalCredits());
            }
            if (update.getDurationYears() > 0) {
                existing.setDurationYears(update.getDurationYears());
            }
            if (update.getDepartmentId() != null) {
                if (!activeDepartments.contains(update.getDepartmentId())) {
                    throw new CourseValidationException("ACD_INVALID_DEPARTMENT",
                            "Invalid department: " + update.getDepartmentId());
                }
                existing.setDepartmentId(update.getDepartmentId());
            }
            if (update.getTags() != null && !update.getTags().isEmpty()) {
                existing.setTags(update.getTags());
            }

            existing.setUpdatedAt(System.currentTimeMillis());
            recordHistory(existing.getId(), "UPDATE", existing.getStatus(), existing.getStatus(), "Draft fields updated");

            emitOutboxEvent("CourseUpdated", existing.getId(), existing.getTenantId(),
                    "{\"id\":\"" + existing.getId() + "\",\"code\":\"" + existing.getCourseCode() + "\"}");

            recordAudit("UPDATE", "COURSE", existing.getId(), "SUCCESS", "Updated draft course: " + existing.getCourseCode());
            return existing;
        }
    }

    // =========================================================================
    // Epic 3: Course Version & Lifecycle Management
    // =========================================================================

    /**
     * Creates an immutable point-in-time version snapshot of an existing course aggregate.
     *
     * @param courseId      unique course identifier
     * @param changeSummary description of curriculum changes introduced in this version
     * @param effectiveFrom epoch millisecond timestamp from which this version becomes effective
     * @return newly created version snapshot in DRAFT status
     * @throws CourseValidationException if the effective date conflicts with an existing version
     */
    public CourseVersion createNewVersion(String courseId, String changeSummary, long effectiveFrom) {
        try (FlowTracker flow = logger.flow("CourseVersion", "createNewVersion")) {
            Course course = getCourse(courseId);

            int nextVersion = course.getCurrentVersion() + 1;
            CourseVersion version = new CourseVersion();
            version.setId("VER_" + course.getCourseCode() + "_" + nextVersion);
            version.setCourseId(course.getId());
            version.setTenantId(course.getTenantId());
            version.setVersionNo(nextVersion);
            version.setStatus("DRAFT");
            version.setChangeSummary(changeSummary != null ? changeSummary : "Material updates to course structure");
            version.setEffectiveFrom(effectiveFrom > 0 ? effectiveFrom : System.currentTimeMillis());
            version.setSnapshotJson("{\"courseCode\":\"" + course.getCourseCode() + "\",\"credits\":"
                    + course.getTotalCredits() + ",\"version\":" + nextVersion + "}");
            version.setChecksum("CHK-" + course.getCourseCode() + "-V" + nextVersion);

            // Validate non-overlapping effective dates with existing versions (BR-07)
            for (CourseVersion v : courseVersions.values()) {
                if (v.getCourseId().equals(course.getId()) && v.getEffectiveFrom() == version.getEffectiveFrom()) {
                    throw new CourseValidationException("ACD_VERSION_CONFLICT",
                            "Effective date conflicts with existing version " + v.getVersionNo());
                }
            }

            courseVersions.put(version.getId(), version);
            course.setCurrentVersion(nextVersion);
            course.setUpdatedAt(System.currentTimeMillis());

            recordHistory(course.getId(), "VERSION_CREATE", course.getStatus(), course.getStatus(),
                    "Created version snapshot " + nextVersion);

            emitOutboxEvent("CourseVersionCreated", course.getId(), course.getTenantId(),
                    "{\"courseId\":\"" + course.getId() + "\",\"versionNo\":" + nextVersion + "}");

            return version;
        }
    }

    /**
     * Returns all historical and current versions registered for a given course.
     *
     * @param courseId course identifier
     * @return ordered list of course version snapshots
     */
    public List<CourseVersion> getCourseVersions(String courseId) {
        getCourse(courseId); // Ensure course exists
        List<CourseVersion> list = new ArrayList<>();
        for (CourseVersion v : courseVersions.values()) {
            if (v.getCourseId().equals(courseId)) {
                list.add(v);
            }
        }
        list.sort(Comparator.comparingInt(CourseVersion::getVersionNo));
        return list;
    }

    // =========================================================================
    // Epic 13 & Epic 3: Lifecycle Transitions & Publication
    // =========================================================================

    /**
     * Advances a course from DRAFT to UNDER_REVIEW status for academic board approval.
     *
     * @param courseId course identifier
     * @return updated course in UNDER_REVIEW status
     * @throws InvalidCourseStateException if course is not in DRAFT status
     */
    public Course submitForApproval(String courseId) {
        Course c = getCourse(courseId);
        if (!"DRAFT".equalsIgnoreCase(c.getStatus())) {
            throw new InvalidCourseStateException(c.getStatus(), "UNDER_REVIEW");
        }
        c.setStatus("UNDER_REVIEW");
        c.setUpdatedAt(System.currentTimeMillis());
        recordHistory(c.getId(), "SUBMIT", "DRAFT", "UNDER_REVIEW", "Submitted course for academic review");
        emitOutboxEvent("CourseSubmitted", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
        return c;
    }

    /**
     * Approves a course currently UNDER_REVIEW, advancing it to APPROVED status.
     *
     * @param courseId course identifier
     * @return updated course in APPROVED status
     * @throws InvalidCourseStateException if course is not UNDER_REVIEW
     */
    public Course approveCourse(String courseId) {
        Course c = getCourse(courseId);
        if (!"UNDER_REVIEW".equalsIgnoreCase(c.getStatus())) {
            throw new InvalidCourseStateException(c.getStatus(), "APPROVED");
        }
        c.setStatus("APPROVED");
        c.setUpdatedAt(System.currentTimeMillis());
        recordHistory(c.getId(), "APPROVE", "UNDER_REVIEW", "APPROVED", "Course approved by academic board");
        emitOutboxEvent("CourseApproved", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
        return c;
    }

    /**
     * Publishes an approved or active course, synchronizing it with the public catalog projection.
     *
     * @param courseId course identifier
     * @return published course in ACTIVE status
     * @throws CourseValidationException if a regulatory course lacks mandatory accreditation details
     */
    public Course publishCourse(String courseId) {
        try (FlowTracker flow = logger.flow("CoursePublish", "publishCourse")) {
            Course c = getCourse(courseId);

            // Mandatory accreditation check for regulatory course categories (BR-12, User Story 36)
            if ("REGULATORY".equalsIgnoreCase(c.getCourseCategory())) {
                boolean hasAccreditation = false;
                for (CourseAccreditation acc : accreditations.values()) {
                    if (acc.getCourseId().equals(c.getId()) && "ACTIVE".equalsIgnoreCase(acc.getStatus())) {
                        hasAccreditation = true;
                        break;
                    }
                }
                if (!hasAccreditation) {
                    throw new CourseValidationException("ACD_MANDATORY_ACCREDITATION_MISSING",
                            "Accreditation details are mandatory before publishing regulatory course: " + c.getCourseCode());
                }
            }

            String oldStatus = c.getStatus();
            c.setStatus("ACTIVE");
            c.setUpdatedAt(System.currentTimeMillis());

            // Mark current version EFFECTIVE
            for (CourseVersion v : courseVersions.values()) {
                if (v.getCourseId().equals(c.getId()) && v.getVersionNo() == c.getCurrentVersion()) {
                    v.setStatus("EFFECTIVE");
                    v.setPublishedAt(System.currentTimeMillis());
                    v.setPublishedBy(LogContext.getUserId() != null ? LogContext.getUserId() : "admin");
                }
            }

            // Sync to catalog projection (FR-09)
            syncCatalogProjection(c);

            recordHistory(c.getId(), "PUBLISH", oldStatus, "ACTIVE", "Course published to institutional catalog");
            emitOutboxEvent("CoursePublished", c.getId(), c.getTenantId(),
                    "{\"id\":\"" + c.getId() + "\",\"version\":" + c.getCurrentVersion() + "}");
            emitOutboxEvent("CourseCatalogUpdated", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");

            recordAudit("PUBLISH", "COURSE", c.getId(), "SUCCESS", "Course published: " + c.getCourseCode());
            return c;
        }
    }

    /**
     * Activates a suspended or inactive course, restoring it to the active public catalog.
     *
     * @param courseId course identifier
     * @return updated course in ACTIVE status
     */
    public Course activateCourse(String courseId) {
        Course c = getCourse(courseId);
        String old = c.getStatus();
        c.setStatus("ACTIVE");
        c.setUpdatedAt(System.currentTimeMillis());
        syncCatalogProjection(c);
        recordHistory(c.getId(), "ACTIVATE", old, "ACTIVE", "Course activated");
        emitOutboxEvent("CourseActivated", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
        return c;
    }

    /**
     * Temporarily suspends an active course, removing it from the public catalog.
     *
     * @param courseId course identifier
     * @return updated course in SUSPENDED status
     */
    public Course suspendCourse(String courseId) {
        Course c = getCourse(courseId);
        String old = c.getStatus();
        c.setStatus("SUSPENDED");
        c.setUpdatedAt(System.currentTimeMillis());
        // Remove from public catalog while suspended
        courseCatalog.remove(c.getId());
        recordHistory(c.getId(), "SUSPEND", old, "SUSPENDED", "Course temporarily suspended");
        emitOutboxEvent("CourseSuspended", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
        return c;
    }

    /**
     * Deactivates a course, removing it from active catalogs after ensuring no active batch offerings exist.
     *
     * @param courseId course identifier
     * @return updated course in DEACTIVATED status
     * @throws ActiveBatchOfferingsException if active batch offerings are still linked to the course
     */
    public Course deactivateCourse(String courseId) {
        try (FlowTracker flow = logger.flow("CourseDeactivate", "deactivateCourse")) {
            Course c = getCourse(courseId);

            // Block course deactivation while active batch offerings exist (BR-08, User Story 44, 83)
            int activeBatches = 0;
            for (CourseBatchOffering offering : batchOfferings.values()) {
                if (offering.getCourseId().equals(c.getId()) && "ACTIVE".equalsIgnoreCase(offering.getStatus())) {
                    activeBatches++;
                }
            }
            if (activeBatches > 0) {
                throw new ActiveBatchOfferingsException(c.getCourseCode(), activeBatches);
            }

            String old = c.getStatus();
            c.setStatus("DEACTIVATED");
            c.setUpdatedAt(System.currentTimeMillis());

            // Remove from active catalog
            courseCatalog.remove(c.getId());

            recordHistory(c.getId(), "DEACTIVATE", old, "DEACTIVATED", "Course deactivated");
            emitOutboxEvent("CourseDeactivated", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
            emitOutboxEvent("CourseCatalogUpdated", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
            recordAudit("DEACTIVATE", "COURSE", c.getId(), "SUCCESS", "Deactivated course: " + c.getCourseCode());
            return c;
        }
    }

    /**
     * Archives a deactivated or obsolete course into historical cold storage.
     *
     * @param courseId course identifier
     * @return updated course in ARCHIVED status
     */
    public Course archiveCourse(String courseId) {
        Course c = getCourse(courseId);
        String old = c.getStatus();
        c.setStatus("ARCHIVED");
        c.setUpdatedAt(System.currentTimeMillis());
        courseCatalog.remove(c.getId());
        recordHistory(c.getId(), "ARCHIVE", old, "ARCHIVED", "Course archived into historical repository");
        emitOutboxEvent("CourseArchived", c.getId(), c.getTenantId(), "{\"id\":\"" + c.getId() + "\"}");
        return c;
    }

    // =========================================================================
    // Epic 4: Course Prerequisite Management & DAG Cycle Detection
    // =========================================================================

    public CoursePrerequisite addPrerequisite(String courseId, String prerequisiteCourseId,
                                              String relationshipType, String minimumGrade) {
        try (FlowTracker flow = logger.flow("CoursePrerequisite", "addPrerequisite")) {
            Course targetCourse = getCourse(courseId);
            Course prereqCourse = getCourse(prerequisiteCourseId);

            // Self dependency check
            if (targetCourse.getId().equals(prereqCourse.getId())) {
                throw new PrerequisiteCycleException(targetCourse.getCourseCode(), prereqCourse.getCourseCode());
            }

            // Prerequisite must be an active course (BR-10)
            if (!"ACTIVE".equalsIgnoreCase(prereqCourse.getStatus()) && !"DRAFT".equalsIgnoreCase(prereqCourse.getStatus())) {
                throw new CourseValidationException("Prerequisites must be active or valid courses");
            }

            // Duplicate relationship check
            for (CoursePrerequisite existing : prerequisites.values()) {
                if (existing.getCourseId().equals(targetCourse.getId())
                        && existing.getPrerequisiteCourseId().equals(prereqCourse.getId())
                        && "ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                    return existing;
                }
            }

            // DAG Cycle Detection using Depth-First Search (DFS)
            // Adding edge targetCourse -> prereqCourse creates a cycle if there is already a path from prereqCourse -> targetCourse
            if (hasPathInPrerequisiteGraph(prereqCourse.getId(), targetCourse.getId())) {
                throw new PrerequisiteCycleException(targetCourse.getCourseCode(), prereqCourse.getCourseCode());
            }

            CoursePrerequisite cp = new CoursePrerequisite();
            cp.setId("PRE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            cp.setCourseId(targetCourse.getId());
            cp.setPrerequisiteCourseId(prereqCourse.getId());
            cp.setTenantId(targetCourse.getTenantId());
            cp.setRelationshipType(relationshipType != null ? relationshipType : "MANDATORY");
            cp.setMinimumGrade(minimumGrade != null ? minimumGrade : "C");
            cp.setStatus("ACTIVE");
            cp.setCreatedBy(LogContext.getUserId() != null ? LogContext.getUserId() : "admin");

            prerequisites.put(cp.getId(), cp);

            recordHistory(targetCourse.getId(), "PREREQUISITE_ADD", targetCourse.getStatus(), targetCourse.getStatus(),
                    "Added prerequisite: " + prereqCourse.getCourseCode());
            emitOutboxEvent("CoursePrerequisiteAdded", targetCourse.getId(), targetCourse.getTenantId(),
                    "{\"courseId\":\"" + targetCourse.getId() + "\",\"prerequisiteId\":\"" + prereqCourse.getId() + "\"}");

            return cp;
        }
    }

    public void removePrerequisite(String courseId, String prerequisiteId) {
        CoursePrerequisite cp = prerequisites.get(prerequisiteId);
        if (cp == null || !cp.getCourseId().equals(courseId)) {
            // Find by prerequisiteCourseId
            for (CoursePrerequisite p : prerequisites.values()) {
                if (p.getCourseId().equals(courseId) && p.getPrerequisiteCourseId().equals(prerequisiteId)) {
                    cp = p;
                    break;
                }
            }
        }
        if (cp != null) {
            cp.setStatus("INACTIVE");
            prerequisites.remove(cp.getId());
            recordHistory(courseId, "PREREQUISITE_REMOVE", null, null, "Removed prerequisite");
            emitOutboxEvent("CoursePrerequisiteRemoved", courseId, cp.getTenantId(),
                    "{\"courseId\":\"" + courseId + "\",\"prerequisiteId\":\"" + cp.getPrerequisiteCourseId() + "\"}");
        }
    }

    public List<CoursePrerequisite> getPrerequisites(String courseId) {
        getCourse(courseId); // validate existence
        List<CoursePrerequisite> list = new ArrayList<>();
        for (CoursePrerequisite cp : prerequisites.values()) {
            if (cp.getCourseId().equals(courseId) && "ACTIVE".equalsIgnoreCase(cp.getStatus())) {
                list.add(cp);
            }
        }
        return list;
    }

    /**
     * Depth-First Search (DFS) to determine if a directed path exists from startCourseId to targetCourseId.
     */
    private boolean hasPathInPrerequisiteGraph(String startCourseId, String targetCourseId) {
        Set<String> visited = new HashSet<>();
        return dfsGraph(startCourseId, targetCourseId, visited);
    }

    private boolean dfsGraph(String current, String target, Set<String> visited) {
        if (current.equals(target)) return true;
        visited.add(current);

        for (CoursePrerequisite cp : prerequisites.values()) {
            if (cp.getCourseId().equals(current) && "ACTIVE".equalsIgnoreCase(cp.getStatus())) {
                String next = cp.getPrerequisiteCourseId();
                if (!visited.contains(next)) {
                    if (dfsGraph(next, target, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // =========================================================================
    // Epic 8: Course Batch Offerings Management
    // =========================================================================

    public CourseBatchOffering addBatchOffering(String courseId, String batchId, String term) {
        getCourse(courseId); // validate existence
        CourseBatchOffering offering = new CourseBatchOffering();
        offering.setId("BO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        offering.setCourseId(courseId);
        offering.setBatchId(batchId);
        offering.setTerm(term != null ? term : "FALL_2026");
        offering.setStatus("ACTIVE");
        batchOfferings.put(offering.getId(), offering);
        return offering;
    }

    public void closeBatchOffering(String offeringId) {
        CourseBatchOffering offering = batchOfferings.get(offeringId);
        if (offering != null) {
            offering.setStatus("CLOSED");
        }
    }

    public List<CourseBatchOffering> getBatchOfferings(String courseId) {
        List<CourseBatchOffering> list = new ArrayList<>();
        for (CourseBatchOffering b : batchOfferings.values()) {
            if (b.getCourseId().equals(courseId)) {
                list.add(b);
            }
        }
        return list;
    }

    // =========================================================================
    // Epic 6: Course Accreditation & Regulatory Compliance
    // =========================================================================

    public CourseAccreditation addAccreditation(String courseId, String authority, String refNumber, long validTo) {
        Course c = getCourse(courseId);
        CourseAccreditation acc = new CourseAccreditation();
        acc.setId("ACC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        acc.setCourseId(c.getId());
        acc.setAuthority(authority != null ? authority : "NAAC");
        acc.setReferenceNumber(refNumber != null ? refNumber : "REF-2026-001");
        acc.setValidFrom(System.currentTimeMillis());
        acc.setValidTo(validTo > 0 ? validTo : System.currentTimeMillis() + 31536000000L); // 1 year
        acc.setStatus("ACTIVE");
        accreditations.put(acc.getId(), acc);
        c.getAccreditationReferences().add(acc.getAuthority() + ":" + acc.getReferenceNumber());
        return acc;
    }

    public List<CourseAccreditation> getAccreditations(String courseId) {
        List<CourseAccreditation> list = new ArrayList<>();
        for (CourseAccreditation a : accreditations.values()) {
            if (a.getCourseId().equals(courseId)) {
                list.add(a);
            }
        }
        return list;
    }

    // =========================================================================
    // Epic 9: Attendance & Evaluation Policy
    // =========================================================================

    public CourseAttendancePolicy setAttendancePolicy(String courseId, double minPercent) {
        Course c = getCourse(courseId);
        CourseAttendancePolicy p = attendancePolicies.computeIfAbsent(c.getId(), k -> {
            CourseAttendancePolicy newP = new CourseAttendancePolicy();
            newP.setId("POL_ATT_" + c.getId());
            newP.setCourseId(c.getId());
            return newP;
        });
        p.setMinAttendancePercent(minPercent);
        return p;
    }

    public CourseAttendancePolicy getAttendancePolicy(String courseId) {
        getCourse(courseId);
        return attendancePolicies.get(courseId);
    }

    public CourseEvaluationPolicy setEvaluationPolicy(String courseId, double internal, double external) {
        Course c = getCourse(courseId);
        CourseEvaluationPolicy p = evaluationPolicies.computeIfAbsent(c.getId(), k -> {
            CourseEvaluationPolicy newP = new CourseEvaluationPolicy();
            newP.setId("POL_EVAL_" + c.getId());
            newP.setCourseId(c.getId());
            newP.setTotalCredits(c.getTotalCredits());
            return newP;
        });
        p.setInternalWeightage(internal);
        p.setExternalWeightage(external);
        return p;
    }

    public CourseEvaluationPolicy getEvaluationPolicy(String courseId) {
        getCourse(courseId);
        return evaluationPolicies.get(courseId);
    }

    // =========================================================================
    // Epic 10: Course Document Management
    // =========================================================================

    public CourseDocument attachDocument(String courseId, String docType, String title, String fileUrl) {
        getCourse(courseId);
        CourseDocument doc = new CourseDocument();
        doc.setId("DOC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        doc.setCourseId(courseId);
        doc.setDocType(docType != null ? docType : "SYLLABUS");
        doc.setTitle(title != null ? title : "Course Syllabus");
        doc.setFileUrl(fileUrl != null ? fileUrl : "s3://campx-docs/courses/" + courseId + ".pdf");
        documents.put(doc.getId(), doc);
        return doc;
    }

    public List<CourseDocument> getDocuments(String courseId) {
        List<CourseDocument> list = new ArrayList<>();
        for (CourseDocument d : documents.values()) {
            if (d.getCourseId().equals(courseId)) {
                list.add(d);
            }
        }
        return list;
    }

    // =========================================================================
    // Epic 2: Course Catalog & Search
    // =========================================================================

    public List<Course> searchCourses(String departmentId, String status, String courseType, String keyword, String tag) {
        List<Course> results = new ArrayList<>();
        for (Course c : courses.values()) {
            if (departmentId != null && !departmentId.equalsIgnoreCase(c.getDepartmentId())) continue;
            if (status != null && !status.equalsIgnoreCase(c.getStatus())) continue;
            if (courseType != null && !courseType.equalsIgnoreCase(c.getCourseType())) continue;
            if (tag != null && !c.getTags().contains(tag.toUpperCase())) continue;
            if (keyword != null && !keyword.isEmpty()) {
                String kw = keyword.toLowerCase();
                boolean match = c.getCourseCode().toLowerCase().contains(kw)
                        || c.getCourseName().toLowerCase().contains(kw)
                        || (c.getDescription() != null && c.getDescription().toLowerCase().contains(kw));
                if (!match) continue;
            }
            results.add(c);
        }
        return results;
    }

    public List<CourseCatalogItem> getPublishedCatalog() {
        return new ArrayList<>(courseCatalog.values());
    }

    private void syncCatalogProjection(Course course) {
        if ("ACTIVE".equalsIgnoreCase(course.getStatus())) {
            CourseCatalogItem item = new CourseCatalogItem();
            item.setCourseId(course.getId());
            item.setCourseCode(course.getCourseCode());
            item.setCourseName(course.getCourseName());
            item.setDepartmentId(course.getDepartmentId());
            item.setTotalCredits(course.getTotalCredits());
            item.setDurationYears(course.getDurationYears());
            item.setCourseType(course.getCourseType());
            item.setDescription(course.getDescription());
            item.setVersion(course.getCurrentVersion());
            item.getTags().addAll(course.getTags());
            courseCatalog.put(course.getId(), item);
        } else {
            courseCatalog.remove(course.getId());
        }
    }

    // =========================================================================
    // Epic 15: Security, RBAC & Audit Governance
    // =========================================================================

    public void enforceRbac(String role, String action, String targetDepartmentId) {
        if (role == null || role.trim().isEmpty()) {
            throw CourseSecurityException.unauthorized("Authentication required: missing user role");
        }

        String r = role.trim().toUpperCase();

        // 1. Super Admin / System Admin: Universal access
        if ("SUPER_ADMIN".equals(r) || "SYSTEM_ADMIN".equals(r)) {
            return;
        }

        // 2. Academic Admin: Full course management
        if ("ACADEMIC_ADMIN".equals(r)) {
            return;
        }

        // 3. Department Head: Can view & edit department courses
        if ("DEPARTMENT_HEAD".equals(r)) {
            if ("VIEW".equals(action) || "EDIT".equals(action) || "MANAGE".equals(action)) {
                return;
            }
            throw CourseSecurityException.forbidden(r, action);
        }

        // 4. Registrar: Can approve, publish, and reassign
        if ("REGISTRAR".equals(r)) {
            if ("APPROVE".equals(action) || "PUBLISH".equals(action) || "VIEW".equals(action)) {
                return;
            }
            throw CourseSecurityException.forbidden(r, action);
        }

        // 5. Faculty: View only
        if ("FACULTY".equals(r)) {
            if ("VIEW".equals(action)) {
                return;
            }
            throw CourseSecurityException.forbidden(r, action);
        }

        // 6. Student: Can view only active catalog
        if ("STUDENT".equals(r)) {
            if ("VIEW_CATALOG".equals(action)) {
                return;
            }
            throw CourseSecurityException.forbidden(r, action);
        }

        // 7. Auditor: Read-only access to course history and archives
        if ("AUDITOR".equals(r)) {
            if ("VIEW_HISTORY".equals(action) || "VIEW_ARCHIVE".equals(action) || "VIEW".equals(action)) {
                return;
            }
            throw CourseSecurityException.forbidden(r, action); // Blocks any write attempt
        }

        // 8. External API: Scoped read-only
        if ("EXTERNAL_API".equals(r)) {
            if ("VIEW".equals(action) || "VIEW_CATALOG".equals(action)) {
                return;
            }
            throw CourseSecurityException.forbidden(r, action);
        }

        throw CourseSecurityException.forbidden(r, action);
    }

    public List<CourseHistory> getCourseHistory(String courseId) {
        getCourse(courseId);
        List<CourseHistory> list = new ArrayList<>();
        for (CourseHistory h : courseHistories) {
            if (h.getCourseId().equals(courseId)) {
                list.add(h);
            }
        }
        return list;
    }

    private void recordHistory(String courseId, String action, String fromStatus, String toStatus, String reason) {
        CourseHistory h = new CourseHistory();
        h.setId("HIS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        h.setCourseId(courseId);
        h.setAction(action);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setReason(reason);
        h.setActorId(LogContext.getUserId() != null ? LogContext.getUserId() : "system");
        h.setActorRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "ADMIN");
        h.setCorrelationId(LogContext.getTraceId() != null ? LogContext.getTraceId() : "NONE");
        h.setTenantId(LogContext.getTenantId() != null ? LogContext.getTenantId() : "DEFAULT_CAMPUS");
        h.setOccurredAt(System.currentTimeMillis());
        courseHistories.add(h);
    }

    private void emitOutboxEvent(String eventType, String aggregateId, String tenantId, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.setEventId("EVT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        e.setEventType(eventType);
        e.setAggregateId(aggregateId);
        e.setTenantId(tenantId);
        e.setPayload(payload);
        e.setStatus("PENDING");
        e.setCreatedAt(System.currentTimeMillis());
        outboxEvents.add(e);
        logger.info("[ACD-01 Outbox] Generated event: [{}] for aggregate [{}]", eventType, aggregateId);
    }

    public List<OutboxEvent> getOutboxEvents() {
        return new ArrayList<>(outboxEvents);
    }

    private void recordAudit(String action, String resourceType, String resourceId, String status, String description) {
        AuditEvent event = AuditEvent.builder()
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .status(status)
                .description(description)
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "system")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "ADMIN")
                .build();
        logger.audit(event);
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer (User Story Lines 67–68)
    // =========================================================================

    /**
     * Academic Command Idempotency with request hash conflict detection.
     */
    public IdempotencyRecord checkOrRecordIdempotency(String idempotencyKey, String operation, String requestHash, long ttlMs) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new CourseValidationException("Idempotency key cannot be empty");
        }

        IdempotencyRecord existing = idempotencyRecords.get(idempotencyKey);
        if (existing != null) {
            if (requestHash != null && existing.getRequestHash() != null && !existing.getRequestHash().equals(requestHash)) {
                throw new CourseCodeConflictException("Payload hash mismatch for idempotency key: " + idempotencyKey);
            }
            logger.info("[ACD-01 Reliability] Idempotency record found for key [{}] - status={}", idempotencyKey, existing.getStatus());
            return existing;
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID().toString());
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation(operation);
        record.setRequestHash(requestHash);
        record.setStatus("PROCESSING");
        record.setExpiresAt(System.currentTimeMillis() + (ttlMs > 0 ? ttlMs : 3600000L));
        record.setCreatedAt(System.currentTimeMillis());

        idempotencyRecords.put(idempotencyKey, record);
        logger.info("[ACD-01 Reliability] Registered new idempotency key [{}] for operation [{}]", idempotencyKey, operation);
        return record;
    }

    public void completeIdempotency(String idempotencyKey, String responseRef) {
        IdempotencyRecord rec = idempotencyRecords.get(idempotencyKey);
        if (rec != null) {
            rec.setStatus("COMPLETED");
            rec.setResponseRef(responseRef);
            logger.info("[ACD-01 Reliability] Completed idempotency key [{}] with ref [{}]", idempotencyKey, responseRef);
        }
    }

    public IdempotencyRecord getIdempotencyRecord(String idempotencyKey) {
        return idempotencyRecords.get(idempotencyKey);
    }

    public List<IdempotencyRecord> listIdempotencyRecords() {
        return new ArrayList<>(idempotencyRecords.values());
    }

    /**
     * User Story 67: Deduplicate inbound events.
     */
    public InboxEvent deduplicateInboundEvent(String eventId, String sourceService, String consumerGroup, String payload) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new CourseValidationException("Inbound eventId is required");
        }
        String dedupeKey = (sourceService != null ? sourceService : "UPSTREAM") + ":" + (consumerGroup != null ? consumerGroup : "ACD-01") + ":" + eventId;

        InboxEvent existing = inboxEvents.get(dedupeKey);
        if (existing != null) {
            logger.warn("[ACD-01 Inbox] Duplicate inbound event ignored: {}", dedupeKey);
            return existing;
        }

        InboxEvent inbox = new InboxEvent();
        inbox.setId(UUID.randomUUID().toString());
        inbox.setEventId(eventId);
        inbox.setSourceService(sourceService != null ? sourceService : "UPSTREAM");
        inbox.setConsumerGroup(consumerGroup != null ? consumerGroup : "ACD-01");
        inbox.setStatus("PROCESSED");
        inbox.setProcessedAt(System.currentTimeMillis());

        inboxEvents.put(dedupeKey, inbox);
        logger.info("[ACD-01 Inbox] Successfully processed inbound event [{}] from [{}]", eventId, sourceService);
        return inbox;
    }

    public List<InboxEvent> listInboxEvents() {
        return new ArrayList<>(inboxEvents.values());
    }

    /**
     * User Story 68: Route unprocessable events to dead-letter queue.
     */
    public DeadLetterEvent routeToDeadLetter(String originalEventId, String eventType, String failureCode, int retryCount, String payload) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId(UUID.randomUUID().toString());
        dlq.setOriginalEventId(originalEventId);
        dlq.setEventType(eventType);
        dlq.setFailureCode(failureCode);
        dlq.setRetryCount(retryCount);
        dlq.setPayload(payload);
        dlq.setDisposition("OPEN");
        dlq.setCreatedAt(System.currentTimeMillis());

        deadLetterQueue.add(dlq);
        logger.error("[ACD-01 DLQ] Routed event [{}] to dead-letter queue: code={}, retries={}", originalEventId, failureCode, retryCount);
        return dlq;
    }

    public List<DeadLetterEvent> listDeadLetterEvents() {
        return new ArrayList<>(deadLetterQueue);
    }

    /**
     * User Story 68: Controlled replay of dead-letter event.
     */
    public DeadLetterEvent replayDeadLetterEvent(String deadLetterId) {
        DeadLetterEvent found = null;
        for (DeadLetterEvent dl : deadLetterQueue) {
            if (dl.getId().equals(deadLetterId)) {
                found = dl;
                break;
            }
        }
        if (found == null) {
            throw new CourseNotFoundException("Dead letter event not found: " + deadLetterId);
        }

        found.setDisposition("REPLAYED");
        emitOutboxEvent(found.getEventType() + ".Replayed", found.getOriginalEventId(), "DEFAULT_CAMPUS", found.getPayload());
        logger.info("[ACD-01 DLQ] Replayed dead-letter event [{}]", deadLetterId);
        return found;
    }

    public String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return Integer.toHexString(input != null ? input.hashCode() : 0);
        }
    }

    // =========================================================================
    // Phase 5: ACD-01 Completeness Domain Methods (User Story Lines 10, 11, 31–32, 37, 39–41, 53, 59)
    // =========================================================================

    /**
     * User Story 10: Credits breakdown with internal/external evaluation components.
     */
    public Map<String, Object> getCourseCreditsBreakdown(String courseId) {
        Course course = getCourse(courseId);
        CourseEvaluationPolicy ep = evaluationPolicies.get(courseId);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("courseId", course.getId());
        breakdown.put("courseCode", course.getCourseCode());
        breakdown.put("totalCredits", course.getTotalCredits());
        if (ep != null) {
            breakdown.put("internalWeightage", ep.getInternalWeightage());
            breakdown.put("externalWeightage", ep.getExternalWeightage());
            breakdown.put("passMarkPercent", ep.getPassMarkPercent());
        } else {
            breakdown.put("internalWeightage", 40.0);
            breakdown.put("externalWeightage", 60.0);
            breakdown.put("passMarkPercent", 40.0);
        }
        return breakdown;
    }

    /**
     * User Story 11: Bulk import courses with independent row validation and diagnostics.
     */
    public Map<String, Object> bulkImportCourses(List<Course> importList) {
        int successCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<String> importedIds = new ArrayList<>();

        for (int i = 0; i < importList.size(); i++) {
            Course c = importList.get(i);
            try {
                Course created = createDraftCourse(c);
                importedIds.add(created.getId());
                successCount++;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("rowIndex", i);
                err.put("courseCode", c != null ? c.getCourseCode() : "UNKNOWN");
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSubmitted", importList.size());
        result.put("importedCount", successCount);
        result.put("failedCount", errors.size());
        result.put("importedCourseIds", importedIds);
        result.put("errors", errors);
        logger.info("[ACD-01] Bulk import completed: {} succeeded, {} failed", successCount, errors.size());
        return result;
    }

    /**
     * User Story 11: Bulk export courses with filtering.
     */
    public List<Course> bulkExportCourses(String status, String departmentId) {
        List<Course> result = new ArrayList<>();
        for (Course c : courses.values()) {
            if (status != null && !status.isEmpty() && !c.getStatus().equalsIgnoreCase(status)) {
                continue;
            }
            if (departmentId != null && !departmentId.isEmpty() && !c.getDepartmentId().equalsIgnoreCase(departmentId)) {
                continue;
            }
            result.add(c);
        }
        return result;
    }

    /**
     * User Story 31–32: Reassign course department ownership with historical tracking.
     */
    public CourseDepartmentAssociation reassignCourseDepartment(String courseId, String newDeptId, String reassignedBy, String reason) {
        Course course = getCourse(courseId);
        long now = System.currentTimeMillis();

        // Expire current active association
        for (CourseDepartmentAssociation cda : departmentAssociations) {
            if (cda.getCourseId().equals(courseId) && cda.getEffectiveTo() == 0) {
                cda.setEffectiveTo(now);
            }
        }

        CourseDepartmentAssociation assoc = new CourseDepartmentAssociation();
        assoc.setId(UUID.randomUUID().toString());
        assoc.setCourseId(courseId);
        assoc.setDepartmentId(newDeptId);
        assoc.setEffectiveFrom(now);
        assoc.setEffectiveTo(0);
        assoc.setReassignedBy(reassignedBy != null ? reassignedBy : "REGISTRAR");
        assoc.setReason(reason != null ? reason : "Department restructuring");
        departmentAssociations.add(assoc);

        course.setDepartmentId(newDeptId);
        syncCatalogProjection(course);
        emitOutboxEvent("CourseDepartmentReassigned", courseId, "DEPARTMENT", "{\"newDepartmentId\":\"" + newDeptId + "\"}");
        logger.info("[ACD-01] Reassigned course [{}] to department [{}] by {}", courseId, newDeptId, reassignedBy);

        return assoc;
    }

    public List<CourseDepartmentAssociation> getDepartmentAssociationHistory(String courseId) {
        List<CourseDepartmentAssociation> history = new ArrayList<>();
        for (CourseDepartmentAssociation cda : departmentAssociations) {
            if (cda.getCourseId().equals(courseId)) {
                history.add(cda);
            }
        }
        history.sort(Comparator.comparingLong(CourseDepartmentAssociation::getEffectiveFrom));
        return history;
    }

    /**
     * User Story 39: Link course to ACD-02 Curriculum.
     */
    public CourseCurriculumMap linkCurriculum(String courseId, String curriculumId) {
        getCourse(courseId);
        CourseCurriculumMap ccm = new CourseCurriculumMap();
        ccm.setId(UUID.randomUUID().toString());
        ccm.setCourseId(courseId);
        ccm.setCurriculumId(curriculumId);
        curriculumMaps.add(ccm);

        emitOutboxEvent("CourseCurriculumLinked", courseId, "CURRICULUM", "{\"curriculumId\":\"" + curriculumId + "\"}");
        logger.info("[ACD-01] Linked course [{}] to curriculum [{}]", courseId, curriculumId);
        return ccm;
    }

    public List<CourseCurriculumMap> getCurriculumLinks(String courseId) {
        List<CourseCurriculumMap> result = new ArrayList<>();
        for (CourseCurriculumMap ccm : curriculumMaps) {
            if (ccm.getCourseId().equals(courseId)) {
                result.add(ccm);
            }
        }
        return result;
    }

    /**
     * User Story 40: Map course to ACD-03 Subject/Syllabus.
     */
    public CourseSubjectMapping mapSubject(String courseId, String subjectId, int semesterNo) {
        getCourse(courseId);
        CourseSubjectMapping csm = new CourseSubjectMapping();
        csm.setId(UUID.randomUUID().toString());
        csm.setCourseId(courseId);
        csm.setSubjectId(subjectId);
        csm.setSemesterNo(semesterNo > 0 ? semesterNo : 1);
        subjectMappings.add(csm);

        emitOutboxEvent("CourseSubjectMapped", courseId, "SUBJECT", "{\"subjectId\":\"" + subjectId + "\",\"semester\":" + csm.getSemesterNo() + "}");
        logger.info("[ACD-01] Mapped course [{}] to subject [{}] semester {}", courseId, subjectId, csm.getSemesterNo());
        return csm;
    }

    public List<CourseSubjectMapping> getSubjectMappings(String courseId) {
        List<CourseSubjectMapping> result = new ArrayList<>();
        for (CourseSubjectMapping csm : subjectMappings) {
            if (csm.getCourseId().equals(courseId)) {
                result.add(csm);
            }
        }
        return result;
    }

    /**
     * User Story 37: Check expiring course accreditations within lead-time.
     */
    public List<CourseAccreditation> checkAccreditationExpiry(int leadTimeDays) {
        long now = System.currentTimeMillis();
        long threshold = now + (leadTimeDays * 86400000L);
        List<CourseAccreditation> expiring = new ArrayList<>();
        for (CourseAccreditation ca : accreditations.values()) {
            if (ca.getValidTo() > 0 && ca.getValidTo() >= now && ca.getValidTo() <= threshold) {
                expiring.add(ca);
            }
        }
        return expiring;
    }

    /**
     * User Story 59: Archive course snapshot for audit queries.
     */
    public CourseArchiveRecord archiveCourseToCollection(String courseId, String archivedBy) {
        Course course = getCourse(courseId);
        course.setStatus("ARCHIVED");

        CourseArchiveRecord record = new CourseArchiveRecord();
        record.setId(UUID.randomUUID().toString());
        record.setCourseId(courseId);
        record.setFinalVersionNo(course.getCurrentVersion());
        record.setArchivedBy(archivedBy != null ? archivedBy : "ACADEMIC_ADMIN");
        record.setSnapshotJson("{\"courseId\":\"" + course.getId() + "\",\"courseCode\":\"" + course.getCourseCode()
                + "\",\"version\":" + course.getCurrentVersion() + ",\"totalCredits\":" + course.getTotalCredits() + "}");
        record.setArchivedAt(System.currentTimeMillis());

        archiveRecords.add(record);
        syncCatalogProjection(course);
        emitOutboxEvent("CourseArchived", courseId, "COURSE", "{\"courseId\":\"" + courseId + "\"}");
        logger.info("[ACD-01] Archived course [{}] to permanent snapshot by {}", courseId, archivedBy);
        return record;
    }

    public List<CourseArchiveRecord> getArchivedCourses() {
        return new ArrayList<>(archiveRecords);
    }

    /**
     * User Story 53: Attach course document with explicit versioning and current-flag maintenance.
     */
    public CourseDocument attachDocument(CourseDocument doc) {
        if (doc.getCourseId() == null || doc.getDocType() == null) {
            throw new CourseValidationException("CourseId and docType are required for document attachment");
        }
        getCourse(doc.getCourseId());

        int maxVer = 0;
        for (CourseDocument existing : documents.values()) {
            if (existing.getCourseId().equals(doc.getCourseId()) && existing.getDocType().equalsIgnoreCase(doc.getDocType())) {
                existing.setCurrent(false);
                if (existing.getVersion() > maxVer) {
                    maxVer = existing.getVersion();
                }
            }
        }

        doc.setId(UUID.randomUUID().toString());
        doc.setVersion(maxVer + 1);
        doc.setCurrent(true);
        doc.setUploadedAt(System.currentTimeMillis());
        documents.put(doc.getId(), doc);

        logger.info("[ACD-01] Attached document [{}] v{} for course [{}]", doc.getDocType(), doc.getVersion(), doc.getCourseId());
        return doc;
    }

    public List<CourseDocument> getCourseDocuments(String courseId) {
        List<CourseDocument> result = new ArrayList<>();
        for (CourseDocument d : documents.values()) {
            if (d.getCourseId().equals(courseId)) {
                result.add(d);
            }
        }
        return result;
    }
}
