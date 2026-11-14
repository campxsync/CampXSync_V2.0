package com.campx.enrollment;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STM-03 enrolment authority.
 * <p>
 * Students (R25) view and change only their own enrolment. Registrars (R31), including
 * college and institution administrators, configure terms, windows, courses, and sections,
 * and may allocate or promote. Faculty (R10) may read only a section roster assigned to them.
 * Business rule failures are returned as {@link EnrollmentException} and written to
 * {@link CampXLogger} before they leave this service. PostgreSQL remains the source of truth
 * when a persistence implementation is bound.
 */
public class EnrollmentDomainService {

    public static final String STUDENT_ENROLLED = "student.enrolled.v1";
    /** Emitted when an approved admission is converted into a PENDING enrolment (STM-03-US-001). */
    public static final String ENROLMENT_OPENED = "student.enrolment.opened.v1";
    public static final String SCHEMA_VERSION = "1.0.0";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(EnrollmentDomainService.class);

    private final Clock clock;
    private final com.campx.enrollment.persistence.EnrollmentPersistence persistence;
    private final com.campx.enrollment.intelligence.EnrollmentIntelligenceService intelligenceService;
    private final Map<String, Term> terms = new ConcurrentHashMap<String, Term>();
    private final Map<String, Window> windows = new ConcurrentHashMap<String, Window>();
    private final Map<String, Course> courses = new ConcurrentHashMap<String, Course>();
    private final Map<String, Section> sections = new ConcurrentHashMap<String, Section>();
    private final Map<String, Set<UUID>> cleared = new ConcurrentHashMap<String, Set<UUID>>();
    private final Map<String, Enrolment> enrolments = new ConcurrentHashMap<String, Enrolment>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<String, String>();
    private final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<Map<String, Object>>());

    public EnrollmentDomainService() {
        this(Clock.systemUTC(), null, null);
    }

    public EnrollmentDomainService(Clock clock) {
        this(clock, null, null);
    }

    public EnrollmentDomainService(Clock clock, com.campx.enrollment.persistence.EnrollmentPersistence persistence) {
        this(clock, persistence, null);
    }

    public EnrollmentDomainService(Clock clock, com.campx.enrollment.persistence.EnrollmentPersistence persistence,
                                   com.campx.enrollment.intelligence.EnrollmentIntelligenceService intelligenceService) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.persistence = persistence;
        this.intelligenceService = intelligenceService == null
                ? new com.campx.enrollment.intelligence.EnrollmentIntelligenceService()
                : intelligenceService;
    }

    public boolean isPersistent() {
        return persistence != null;
    }

    public Term seedTerm(UUID tenantId, String yearCode, String termCode, String name) {
        Term term = new Term(UUID.randomUUID(), tenantId, yearCode.trim(), termCode.trim(), name == null ? termCode : name.trim());
        terms.put(key(tenantId, term.id), term);
        return term;
    }

    public Window seedWindow(UUID tenantId, UUID termId, Instant opensAt, Instant closesAt, int minCredits, int maxCredits) {
        Window window = new Window(UUID.randomUUID(), tenantId, termId, opensAt, closesAt, minCredits, maxCredits);
        windows.put(key(tenantId, termId), window);
        return window;
    }

    public Course seedCourse(UUID tenantId, UUID programId, UUID termId, String code, int credits, boolean core, List<UUID> prerequisites) {
        Course course = new Course(UUID.randomUUID(), tenantId, programId, termId, code, credits, core,
                prerequisites == null ? Collections.<UUID>emptyList() : new ArrayList<UUID>(prerequisites));
        courses.put(key(tenantId, course.id), course);
        return course;
    }

    public Section seedSection(UUID tenantId, UUID courseId, int capacity, UUID facultyUserId) {
        Section section = new Section(UUID.randomUUID(), tenantId, courseId, capacity, 0, facultyUserId);
        sections.put(key(tenantId, section.id), section);
        return section;
    }

    public void seedClearedCourse(UUID tenantId, UUID studentId, UUID courseId) {
        String clearedKey = tenantId + ":" + studentId;
        Set<UUID> set = cleared.get(clearedKey);
        if (set == null) {
            set = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
            cleared.put(clearedKey, set);
        }
        set.add(courseId);
    }

    public Enrolment seedHandover(UUID tenantId, UUID applicationId, UUID studentId, UUID programId, UUID termId) {
        Enrolment enrolment = new Enrolment(UUID.randomUUID(), tenantId, applicationId, studentId, programId, termId, "PENDING");
        enrolments.put(key(tenantId, enrolment.id), enrolment);
        return enrolment;
    }

    /**
     * Creates an academic term for the registrar's tenant.
     *
     * @throws EnrollmentException 403 when the caller cannot configure the catalogue
     */
    public Term createTerm(Actor actor, String yearCode, String termCode, String name) {
        requireStaffWrite(actor);
        requireText(yearCode, "yearCode");
        requireText(termCode, "termCode");
        String year = yearCode.trim();
        String code = termCode.trim();
        hydrateTerms(actor);
        for (Term existing : terms.values()) {
            if (existing.tenantId.equals(actor.getTenantId())
                    && year.equals(existing.yearCode)
                    && code.equals(existing.termCode)) {
                return existing;
            }
        }
        Term term = new Term(UUID.randomUUID(), actor.getTenantId(), year, code, name == null ? code : name.trim());
        terms.put(key(actor.getTenantId(), term.id), term);
        persistTerm(actor, term);
        log(actor, "term.created", term.id);
        return term;
    }

    /**
     * Opens a registration window for a term, including the allowed credit range.
     *
     * @throws EnrollmentException 422 when the window dates or credit bounds are invalid
     */
    public Window createWindow(Actor actor, UUID termId, Instant opensAt, Instant closesAt, int minCredits, int maxCredits) {
        requireStaffWrite(actor);
        Term term = requireTerm(actor.getTenantId(), termId);
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt)) {
            throw reject(422, "WINDOW_INVALID", "Registration window close must be after open");
        }
        if (minCredits < 0 || maxCredits < minCredits) {
            throw reject(422, "CREDIT_BOUNDS_INVALID", "Credit bounds are invalid");
        }
        Window window = new Window(UUID.randomUUID(), term.tenantId, term.id, opensAt, closesAt, minCredits, maxCredits);
        windows.put(key(term.tenantId, term.id), window);
        persistWindow(actor, window);
        log(actor, "window.configured", window.id);
        return window;
    }

    /**
     * Adds a course to a programme and term. Core courses are mandatory at registration.
     *
     * @throws EnrollmentException 422 when the code, programme, or credits are missing
     */
    public Course addCourse(Actor actor, UUID programId, UUID termId, String code, int credits, boolean core, List<UUID> prerequisites) {
        requireStaffWrite(actor);
        requireTerm(actor.getTenantId(), termId);
        if (programId == null || credits <= 0 || code == null || code.trim().isEmpty()) {
            throw reject(422, "COURSE_INVALID", "Course code, programme, and positive credits are required");
        }
        Course course = new Course(UUID.randomUUID(), actor.getTenantId(), programId, termId, code.trim(), credits, core,
                prerequisites == null ? Collections.<UUID>emptyList() : new ArrayList<UUID>(prerequisites));
        courses.put(key(actor.getTenantId(), course.id), course);
        if (persistence != null) {
            persistence.saveCourse(actor, course);
        }
        log(actor, "course.catalogued", course.id);
        return course;
    }

    /**
     * Adds a course section and assigns the faculty member who may read its roster.
     *
     * @throws EnrollmentException 422 when capacity or the faculty assignment is missing
     */
    public Section addSection(Actor actor, UUID courseId, int capacity, UUID facultyUserId) {
        requireStaffWrite(actor);
        Course course = requireCourse(actor.getTenantId(), courseId);
        if (capacity <= 0 || facultyUserId == null) {
            throw reject(422, "SECTION_INVALID", "Section capacity and faculty assignment are required");
        }
        Section section = new Section(UUID.randomUUID(), course.tenantId, course.id, capacity, 0, facultyUserId);
        sections.put(key(course.tenantId, section.id), section);
        if (persistence != null) {
            persistence.saveSection(actor, section);
        }
        log(actor, "section.opened", section.id);
        return section;
    }

    public void recordClearedCourse(Actor actor, UUID studentId, UUID courseId) {
        requireStaffWrite(actor);
        if (studentId == null || courseId == null) {
            throw reject(422, "CLEARED_COURSE_INVALID", "Student and course are required");
        }
        String key = actor.getTenantId() + ":" + studentId;
        Set<UUID> set = cleared.get(key);
        if (set == null) {
            set = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
            cleared.put(key, set);
        }
        set.add(courseId);
        if (persistence != null) {
            persistence.saveClearedCourse(actor, studentId, courseId);
        }
        log(actor, "prerequisite.recorded", studentId);
    }

    public List<Course> listCoursesForEnrolment(Actor actor, UUID enrolmentId) {
        Enrolment enrolment = getEnrolment(actor, enrolmentId);
        List<Course> available = new ArrayList<Course>();
        for (Course course : courses.values()) {
            if (course.tenantId.equals(enrolment.tenantId)
                    && course.programId.equals(enrolment.programId)
                    && course.termId.equals(enrolment.termId)) {
                available.add(course);
            }
        }
        return available;
    }

    public List<Section> listSectionsForCourse(Actor actor, UUID enrolmentId, UUID courseId) {
        getEnrolment(actor, enrolmentId);
        Course course = requireCourse(actor.getTenantId(), courseId);
        List<Section> available = new ArrayList<Section>();
        for (Section section : sections.values()) {
            if (section.tenantId.equals(actor.getTenantId()) && section.courseId.equals(course.id)) {
                available.add(section);
            }
        }
        return available;
    }

    public List<Map<String, String>> listHistory(Actor actor, UUID enrolmentId) {
        Enrolment enrolment = getEnrolment(actor, enrolmentId);
        if (persistence != null && (isRegistrar(actor) || (isStudent(actor) && ownsEnrolment(actor, enrolment)))) {
            List<Map<String, String>> persisted = persistence.findHistory(actor, enrolmentId);
            if (persisted != null && !persisted.isEmpty()) {
                return persisted;
            }
        }
        return new ArrayList<Map<String, String>>(enrolment.history);
    }

    public int waitlistPosition(Actor actor, UUID sectionId) {
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "WAITLIST_VIEW_FORBIDDEN", "Waitlist position is limited to the student or registrar");
        }
        Section section = requireSection(actor.getTenantId(), sectionId);
        if (isStudent(actor)) {
            int index = section.waitlist.indexOf(actor.getUserId());
            if (index < 0) {
                if (persistence != null) {
                    Integer position = persistence.findWaitlistPosition(actor, sectionId, actor.getUserId());
                    if (position == null) {
                        throw reject(404, "WAITLIST_NOT_FOUND", "Student is not on this waitlist");
                    }
                    return position;
                }
                throw reject(404, "WAITLIST_NOT_FOUND", "Student is not on this waitlist");
            }
            return index + 1;
        }
        return section.waitlist.size();
    }

    /**
     * Converts an approved admission handover into one enrolment for the term (STM-03-US-001).
     * Registrar / college staff (catalogue R31 and mapped admin roles) may open; students and
     * faculty are denied. Same applicationId is idempotent; same student+term rejects as duplicate.
     *
     * @throws EnrollmentException 401 when tenant context is missing
     * @throws EnrollmentException 403 when the caller is not registrar-scoped staff
     * @throws EnrollmentException 404 when the academic term is not found in the tenant
     * @throws EnrollmentException 409 when that student already has an enrolment for the term
     * @throws EnrollmentException 422 when a required identifier is missing
     */
    public Enrolment openFromHandover(Actor actor, UUID applicationId, UUID studentId, UUID programId, UUID termId) {
        requireStaffWrite(actor);
        if (applicationId == null || studentId == null || programId == null || termId == null) {
            throw reject(422, "HANDOVER_INVALID", "applicationId, studentId, programId, and termId are required");
        }
        hydrateTerms(actor);
        requireTerm(actor.getTenantId(), termId);
        hydrateTenantEnrolments(actor);
        for (Enrolment existing : enrolments.values()) {
            if (existing.tenantId.equals(actor.getTenantId()) && existing.applicationId.equals(applicationId)) {
                return existing;
            }
            if (existing.tenantId.equals(actor.getTenantId()) && existing.studentId.equals(studentId) && existing.termId.equals(termId)
                    && !"REJECTED".equals(existing.status)) {
                throw reject(409, "DUPLICATE_ENROLMENT", "Student already has an enrolment for this term");
            }
        }
        Enrolment enrolment = new Enrolment(UUID.randomUUID(), actor.getTenantId(), applicationId, studentId, programId, termId, "PENDING");
        Map<String, String> historyRow = history(actor, "PENDING", "handover");
        enrolment.history.add(historyRow);
        enrolments.put(key(actor.getTenantId(), enrolment.id), enrolment);
        persistEnrolment(actor, enrolment);
        persistHistory(actor, enrolment, historyRow);
        log(actor, "enrolment.opened", enrolment.id);
        publishEnrolmentOpened(actor, enrolment);
        return enrolment;
    }

    /**
     * Returns one enrolment when the caller owns it or is a registrar in the same tenant.
     *
     * @throws EnrollmentException 403 when the caller is outside that scope
     * @throws EnrollmentException 404 when the enrolment does not exist
     */
    public Enrolment getEnrolment(Actor actor, UUID enrolmentId) {
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        if (isRegistrar(actor)) {
            return enrolment;
        }
        if (isStudent(actor) && ownsEnrolment(actor, enrolment)) {
            return enrolment;
        }
        throw reject(403, "ENROLMENT_FORBIDDEN", "Caller is not authorized to view this enrolment");
    }

    /**
     * Student self-service: enrolments owned by the caller. Registrar may pass studentId filter.
     */
    /**
     * Lists enrolments visible to the caller. A student receives only their own records.
     *
     * @throws EnrollmentException 403 when the caller cannot list enrolments
     */
    public List<Enrolment> listMyEnrolments(Actor actor) {
        if (actor == null || actor.getTenantId() == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
        List<Enrolment> mine = new ArrayList<Enrolment>();
        if (isStudent(actor)) {
            UUID subjectId = actor.subjectStudentId();
            for (Enrolment enrolment : enrolments.values()) {
                if (enrolment.tenantId.equals(actor.getTenantId()) && enrolment.studentId.equals(subjectId)) {
                    mine.add(enrolment);
                }
            }
            if (mine.isEmpty() && persistence != null) {
                mine.addAll(persistence.findEnrolmentsForStudent(actor, subjectId));
                for (Enrolment loaded : mine) {
                    enrolments.put(key(loaded.tenantId, loaded.id), loaded);
                }
            }
            return mine;
        }
        if (isRegistrar(actor)) {
            hydrateTenantEnrolments(actor);
            for (Enrolment enrolment : enrolments.values()) {
                if (enrolment.tenantId.equals(actor.getTenantId())) {
                    mine.add(enrolment);
                }
            }
            return mine;
        }
        throw reject(403, "ENROLMENT_LIST_FORBIDDEN", "Caller is not authorized to list enrolments");
    }

    /**
     * Catalogue for registration UI: courses with nested section availability.
     */
    public List<Term> listTerms(Actor actor) {
        requireStaffWrite(actor);
        hydrateTerms(actor);
        List<Term> visible = new ArrayList<Term>();
        for (Term term : terms.values()) {
            if (term.tenantId.equals(actor.getTenantId())) {
                visible.add(term);
            }
        }
        return visible;
    }

    public List<Map<String, Object>> listRegistrationCatalogue(Actor actor, UUID enrolmentId) {
        Enrolment enrolment = getEnrolment(actor, enrolmentId);
        hydrateCatalogue(actor, enrolment);
        List<Map<String, Object>> catalogue = new ArrayList<Map<String, Object>>();
        for (Course course : courses.values()) {
            if (!course.tenantId.equals(enrolment.tenantId)
                    || !course.programId.equals(enrolment.programId)
                    || !course.termId.equals(enrolment.termId)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", course.id);
            row.put("code", course.code);
            row.put("credits", Integer.valueOf(course.credits));
            row.put("core", Boolean.valueOf(course.core));
            List<Map<String, Object>> sectionRows = new ArrayList<Map<String, Object>>();
            for (Section section : sections.values()) {
                if (!section.tenantId.equals(enrolment.tenantId) || !section.courseId.equals(course.id)) {
                    continue;
                }
                Map<String, Object> sectionRow = new LinkedHashMap<String, Object>();
                sectionRow.put("id", section.id);
                sectionRow.put("capacity", Integer.valueOf(section.capacity));
                sectionRow.put("enrolledCount", Integer.valueOf(section.enrolledCount));
                sectionRow.put("seatsAvailable", Integer.valueOf(Math.max(0, section.capacity - section.enrolledCount)));
                sectionRows.add(sectionRow);
            }
            row.put("sections", sectionRows);
            catalogue.add(row);
        }
        return catalogue;
    }

    /**
     * Checks prerequisites, core courses, and the registration window for an enrolment.
     *
     * @return stable failure codes, or an empty list when the enrolment can be registered
     */
    public List<String> evaluateEligibility(Actor actor, UUID enrolmentId) {
        Enrolment enrolment = getEnrolment(actor, enrolmentId);
        hydrateCatalogue(actor, enrolment);
        List<String> failures = eligibilityFailures(enrolment);
        if (failures.isEmpty() && "PENDING".equals(enrolment.status)) {
            enrolment.status = "ELIGIBLE";
            Map<String, String> row = history(actor, "ELIGIBLE", "eligibility");
            enrolment.history.add(row);
            persistEnrolment(actor, enrolment);
            persistHistory(actor, enrolment, row);
            log(actor, "enrolment.eligible", enrolment.id);
        }
        return failures;
    }

    /**
     * Registers the student's selected sections. Repeating the same idempotency key returns the saved result.
     *
     * @throws EnrollmentException 403 when the caller is not the owning student
     * @throws EnrollmentException 409 when a selected section is full
     * @throws EnrollmentException 422 when eligibility or credit limits fail
     */
    public Enrolment register(Actor actor, UUID enrolmentId, List<Selection> selections, String idempotencyKey) {
        if (!isStudent(actor)) {
            throw reject(403, "REGISTER_FORBIDDEN", "Only the student may select courses");
        }
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        if (!ownsEnrolment(actor, enrolment)) {
            throw reject(403, "REGISTER_FORBIDDEN", "Students may register only their own enrolment");
        }
        hydrateCatalogue(actor, enrolment);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            String prior = idempotency.get(actor.getTenantId() + ":" + idempotencyKey);
            if (prior != null) {
                return requireEnrolment(actor.getTenantId(), UUID.fromString(prior));
            }
        }
        if ("ENROLLED".equals(enrolment.status)) {
            return enrolment;
        }
        List<String> failures = eligibilityFailures(enrolment);
        if (!failures.isEmpty()) {
            throw reject(422, failures.get(0), "Enrolment is not eligible");
        }
        Window window = windows.get(key(enrolment.tenantId, enrolment.termId));
        List<Course> chosen = resolveSelections(enrolment, selections);
        int credits = 0;
        for (Course course : chosen) {
            assertPrerequisites(enrolment, course);
            credits += course.credits;
        }
        if (credits < window.minCredits) {
            throw reject(422, "CREDIT_BELOW_MINIMUM", "Registered credits are below the term minimum");
        }
        if (credits > window.maxCredits) {
            throw reject(422, "CREDIT_ABOVE_MAXIMUM", "Registered credits exceed the term maximum");
        }
        Map<String, String> historyRow;
        synchronized (this) {
            List<Section> reserved = new ArrayList<Section>();
            for (Course course : chosen) {
                Selection selection = selectionFor(selections, course.id);
                Section section = requireSection(enrolment.tenantId, selection.sectionId);
                if (!section.courseId.equals(course.id)) {
                    throw reject(422, "SECTION_COURSE_MISMATCH", "Section does not belong to the selected course");
                }
                if (persistence != null) {
                    if (!persistence.tryReserveSeat(actor, section.id)) {
                        throw reject(409, "SECTION_FULL", "Section capacity is exhausted");
                    }
                    section.enrolledCount++;
                } else if (section.enrolledCount >= section.capacity) {
                    throw reject(409, "SECTION_FULL", "Section capacity is exhausted");
                } else {
                    section.enrolledCount++;
                }
                reserved.add(section);
            }
            enrolment.selections.clear();
            for (int i = 0; i < chosen.size(); i++) {
                Section section = reserved.get(i);
                enrolment.selections.add(new Selection(chosen.get(i).id, section.id));
            }
            enrolment.status = "ENROLLED";
            enrolment.totalCredits = credits;
            historyRow = history(actor, "ENROLLED", "registration");
            enrolment.history.add(historyRow);
            if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                idempotency.put(actor.getTenantId() + ":" + idempotencyKey, enrolment.id.toString());
            }
        }
        publishEnrolled(actor, enrolment);
        persistEnrolment(actor, enrolment);
        persistHistory(actor, enrolment, historyRow);
        log(actor, "enrolment.registered", enrolment.id);
        return enrolment;
    }

    /**
     * Updates an enrolment to ELIGIBLE, SUSPENDED, or REJECTED.
     *
     * @throws EnrollmentException 422 when the status is not one of those values
     */
    public Enrolment updateStatus(Actor actor, UUID enrolmentId, String status) {
        requireRegistrar(actor);
        if (status == null || (!"ELIGIBLE".equals(status) && !"SUSPENDED".equals(status) && !"REJECTED".equals(status))) {
            throw reject(422, "STATUS_INVALID", "Status must be ELIGIBLE, SUSPENDED, or REJECTED");
        }
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        enrolment.status = status;
        Map<String, String> historyRow = history(actor, status, "status");
        enrolment.history.add(historyRow);
        persistEnrolment(actor, enrolment);
        persistHistory(actor, enrolment, historyRow);
        log(actor, "enrolment.status", enrolment.id);
        return enrolment;
    }

    /**
     * Moves the owning student from one section of a course to another inside the open window.
     *
     * @throws EnrollmentException 403 when the caller does not own the enrolment
     * @throws EnrollmentException 422 when the window is closed or the section is for another course
     */
    public Enrolment swapSection(Actor actor, UUID enrolmentId, UUID courseId, UUID sectionId) {
        if (!isStudent(actor)) {
            throw reject(403, "SWAP_FORBIDDEN", "Only the student may swap a section");
        }
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        if (!ownsEnrolment(actor, enrolment)) {
            throw reject(403, "SWAP_FORBIDDEN", "Students may swap only their own sections");
        }
        hydrateCatalogue(actor, enrolment);
        Window window = windows.get(key(enrolment.tenantId, enrolment.termId));
        Instant now = clock.instant();
        if (window == null || now.isBefore(window.opensAt) || now.isAfter(window.closesAt)) {
            throw reject(422, "WINDOW_CLOSED", "Section swap is outside the registration window");
        }
        return moveSelection(actor, enrolment, courseId, sectionId, "section-swap");
    }

    /**
     * Places a student into a section. Registrar and allocation roles may call this.
     *
     * @throws EnrollmentException 409 when the section is full
     */
    public Enrolment allocateSection(Actor actor, UUID enrolmentId, UUID courseId, UUID sectionId) {
        requireAllocator(actor);
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        hydrateCatalogue(actor, enrolment);
        return moveSelection(actor, enrolment, courseId, sectionId, "section-allocation");
    }

    private Enrolment moveSelection(Actor actor, Enrolment enrolment, UUID courseId, UUID sectionId, String action) {
        Section section = requireSection(actor.getTenantId(), sectionId);
        if (!section.courseId.equals(courseId)) {
            throw reject(422, "SECTION_COURSE_MISMATCH", "Section does not belong to the course");
        }
        boolean found = false;
        for (Selection selection : enrolment.selections) {
            if (selection.courseId.equals(courseId)) {
                Section previous = requireSection(actor.getTenantId(), selection.sectionId);
                if (!previous.id.equals(section.id)) {
                    if (persistence != null) {
                        if (!persistence.tryReserveSeat(actor, section.id)) {
                            throw reject(409, "SECTION_FULL", "Section capacity is exhausted");
                        }
                        persistence.releaseSeat(actor, previous.id);
                        section.enrolledCount++;
                        previous.enrolledCount = Math.max(0, previous.enrolledCount - 1);
                    } else if (section.enrolledCount >= section.capacity) {
                        throw reject(409, "SECTION_FULL", "Section capacity is exhausted");
                    } else {
                        previous.enrolledCount = Math.max(0, previous.enrolledCount - 1);
                        section.enrolledCount++;
                    }
                    selection.sectionId = section.id;
                }
                found = true;
            }
        }
        if (!found) {
            throw reject(404, "SELECTION_NOT_FOUND", "Course is not on the enrolment");
        }
        Map<String, String> historyRow = history(actor, enrolment.status, action);
        enrolment.history.add(historyRow);
        persistEnrolment(actor, enrolment);
        persistHistory(actor, enrolment, historyRow);
        log(actor, action, enrolment.id);
        return enrolment;
    }

    /**
     * Adds the owning student to a full section's waitlist and returns the 1-based position.
     *
     * @throws EnrollmentException 422 when the section still has a seat
     */
    public int joinWaitlist(Actor actor, UUID enrolmentId, UUID sectionId) {
        if (!isStudent(actor)) {
            throw reject(403, "WAITLIST_FORBIDDEN", "Only the student may join a section waitlist");
        }
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        if (!ownsEnrolment(actor, enrolment)) {
            throw reject(403, "WAITLIST_FORBIDDEN", "Students may join a waitlist only for their own enrolment");
        }
        hydrateCatalogue(actor, enrolment);
        Section section = requireSection(actor.getTenantId(), sectionId);
        if (section.enrolledCount < section.capacity) {
            throw reject(422, "SECTION_AVAILABLE", "The section still has capacity");
        }
        if (!section.waitlist.contains(enrolment.studentId)) {
            section.waitlist.add(enrolment.studentId);
            Map<String, String> historyRow = history(actor, enrolment.status, "waitlist");
            enrolment.history.add(historyRow);
            persistHistory(actor, enrolment, historyRow);
            if (persistence != null) {
                persistence.replaceWaitlist(actor, section);
            }
        }
        log(actor, "waitlist.joined", section.id);
        recordAlert(actor, enrolment, "enrolment.waitlist_joined.v1");
        return section.waitlist.indexOf(enrolment.studentId) + 1;
    }

    /**
     * Promotes the first waiting student into a seat and returns the remaining waitlist size.
     *
     * @throws EnrollmentException 404 when nobody is waiting
     * @throws EnrollmentException 409 when the section is still full
     */
    public int promoteWaitlist(Actor actor, UUID sectionId) {
        requireRegistrar(actor);
        Section section = requireSection(actor.getTenantId(), sectionId);
        if (section.waitlist.isEmpty()) {
            throw reject(404, "WAITLIST_EMPTY", "No students are waiting for this section");
        }
        if (persistence != null) {
            if (!persistence.tryReserveSeat(actor, section.id)) {
                throw reject(409, "SECTION_FULL", "Section capacity is exhausted");
            }
            section.enrolledCount++;
        } else if (section.enrolledCount >= section.capacity) {
            throw reject(409, "SECTION_FULL", "Section capacity is exhausted");
        } else {
            section.enrolledCount++;
        }
        UUID promotedStudentId = section.waitlist.remove(0);
        for (Enrolment enrolment : enrolments.values()) {
            if (enrolment.tenantId.equals(actor.getTenantId()) && enrolment.studentId.equals(promotedStudentId)) {
                Map<String, String> historyRow = history(actor, enrolment.status, "waitlist-promoted");
                enrolment.history.add(historyRow);
                persistHistory(actor, enrolment, historyRow);
                break;
            }
        }
        if (persistence != null) {
            persistence.replaceWaitlist(actor, section);
        }
        log(actor, "waitlist.promoted", section.id);
        return section.waitlist.size();
    }

    /**
     * Returns enrolled student ids for one section.
     * Faculty may read the roster only when the section is assigned to them.
     *
     * @throws EnrollmentException 403 when the faculty member is not assigned to the section
     */
    public List<UUID> roster(Actor actor, UUID sectionId) {
        Section section = requireSection(actor.getTenantId(), sectionId);
        if (!isFaculty(actor) || !section.facultyUserId.equals(actor.getUserId())) {
            throw reject(403, "ROSTER_FORBIDDEN", "Faculty may view only an assigned section roster");
        }
        List<UUID> students = new ArrayList<UUID>();
        for (Enrolment enrolment : enrolments.values()) {
            if (!enrolment.tenantId.equals(actor.getTenantId()) || !"ENROLLED".equals(enrolment.status)) {
                continue;
            }
            for (Selection selection : enrolment.selections) {
                if (selection.sectionId.equals(sectionId)) {
                    students.add(enrolment.studentId);
                }
            }
        }
        return students;
    }

    /**
     * STM-03-US-019: sections assigned to this faculty member and the enrolled students in each.
     */
    /**
     * Returns every section roster assigned to the calling faculty member.
     *
     * @throws EnrollmentException 403 when the caller is not faculty
     */
    public List<Map<String, Object>> listAssignedRosters(Actor actor) {
        if (!isFaculty(actor)) {
            throw reject(403, "ROSTER_FORBIDDEN", "Faculty may view only an assigned section roster");
        }
        if (persistence != null) {
            return groupRosterRows(persistence.findFacultyRosterRows(actor));
        }
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (Section section : sections.values()) {
            if (!section.tenantId.equals(actor.getTenantId()) || !actor.getUserId().equals(section.facultyUserId)) {
                continue;
            }
            String courseCode = "";
            for (Course course : courses.values()) {
                if (course.id.equals(section.courseId)) {
                    courseCode = course.code;
                    break;
                }
            }
            boolean anyStudent = false;
            for (Enrolment enrolment : enrolments.values()) {
                if (!enrolment.tenantId.equals(actor.getTenantId()) || !"ENROLLED".equals(enrolment.status)) {
                    continue;
                }
                for (Selection selection : enrolment.selections) {
                    if (selection.sectionId.equals(section.id)) {
                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("sectionId", section.id.toString());
                        row.put("courseCode", courseCode);
                        row.put("capacity", Integer.toString(section.capacity));
                        row.put("enrolledCount", Integer.toString(section.enrolledCount));
                        row.put("studentId", enrolment.studentId.toString());
                        rows.add(row);
                        anyStudent = true;
                    }
                }
            }
            if (!anyStudent) {
                Map<String, String> row = new LinkedHashMap<String, String>();
                row.put("sectionId", section.id.toString());
                row.put("courseCode", courseCode);
                row.put("capacity", Integer.toString(section.capacity));
                row.put("enrolledCount", Integer.toString(section.enrolledCount));
                row.put("studentId", "");
                rows.add(row);
            }
        }
        return groupRosterRows(rows);
    }

    private List<Map<String, Object>> groupRosterRows(List<Map<String, String>> rows) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, String> row : rows) {
            String sectionId = row.get("sectionId");
            Map<String, Object> section = grouped.get(sectionId);
            if (section == null) {
                section = new LinkedHashMap<String, Object>();
                section.put("sectionId", sectionId);
                section.put("courseCode", row.get("courseCode"));
                section.put("capacity", Integer.valueOf(row.get("capacity")));
                section.put("enrolledCount", Integer.valueOf(row.get("enrolledCount")));
                section.put("studentIds", new ArrayList<String>());
                grouped.put(sectionId, section);
            }
            String studentId = row.get("studentId");
            if (studentId != null && !studentId.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<String> studentIds = (List<String>) section.get("studentIds");
                if (!studentIds.contains(studentId)) {
                    studentIds.add(studentId);
                }
            }
        }
        return new ArrayList<Map<String, Object>>(grouped.values());
    }

    /**
     * Removes an elective from the owning student's enrolment while the window is open.
     *
     * @throws EnrollmentException 422 when the course is a required core or the window is closed
     */
    public Enrolment drop(Actor actor, UUID enrolmentId, UUID courseId) {
        if (!isStudent(actor)) {
            throw reject(403, "DROP_FORBIDDEN", "Only the student may drop a course");
        }
        Enrolment enrolment = requireEnrolment(actor.getTenantId(), enrolmentId);
        if (!ownsEnrolment(actor, enrolment)) {
            throw reject(403, "DROP_FORBIDDEN", "Students may drop only their own courses");
        }
        hydrateCatalogue(actor, enrolment);
        Window window = windows.get(key(enrolment.tenantId, enrolment.termId));
        Instant now = clock.instant();
        if (window == null || now.isBefore(window.opensAt) || now.isAfter(window.closesAt)) {
            throw reject(422, "WINDOW_CLOSED", "Course removal is outside the registration window");
        }
        Selection removed = null;
        for (Selection selection : enrolment.selections) {
            if (selection.courseId.equals(courseId)) {
                removed = selection;
            }
        }
        if (removed == null) {
            throw reject(404, "SELECTION_NOT_FOUND", "Course is not on the enrolment");
        }
        Course course = requireCourse(enrolment.tenantId, courseId);
        if (course.core) {
            throw reject(422, "CORE_REQUIRED", "Mandatory core courses cannot be removed");
        }
        Section section = requireSection(enrolment.tenantId, removed.sectionId);
        section.enrolledCount = Math.max(0, section.enrolledCount - 1);
        if (persistence != null) {
            persistence.releaseSeat(actor, section.id);
        }
        enrolment.selections.remove(removed);
        enrolment.totalCredits = Math.max(0, enrolment.totalCredits - course.credits);
        Map<String, String> historyRow = history(actor, enrolment.status, "drop");
        enrolment.history.add(historyRow);
        persistEnrolment(actor, enrolment);
        persistHistory(actor, enrolment, historyRow);
        log(actor, "course.dropped", enrolment.id);
        recordAlert(actor, enrolment, "enrolment.course_dropped.v1");
        return enrolment;
    }

    /**
     * Returns enrolment alerts recorded for the calling student.
     * This is the in-service alert record. It is not a delivered notification channel.
     *
     * @throws EnrollmentException 403 when the caller is not a student
     */
    public List<Map<String, Object>> listAlerts(Actor actor) {
        if (!isStudent(actor)) {
            throw reject(403, "ALERTS_FORBIDDEN", "Enrolment alerts are limited to the student");
        }
        UUID subjectId = actor.subjectStudentId();
        List<Map<String, Object>> alerts = new ArrayList<Map<String, Object>>();
        synchronized (events) {
            for (Map<String, Object> event : events) {
                if (actor.getTenantId().equals(event.get("tenantId")) && subjectId.equals(event.get("studentId"))) {
                    alerts.add(event);
                }
            }
        }
        return alerts;
    }

    public List<Map<String, Object>> eventsForTenant(UUID tenantId) {
        List<Map<String, Object>> visible = new ArrayList<Map<String, Object>>();
        synchronized (events) {
            for (Map<String, Object> event : events) {
                if (tenantId.equals(event.get("tenantId"))) {
                    visible.add(event);
                }
            }
        }
        return visible;
    }

    private Selection selectionFor(List<Selection> selections, UUID courseId) {
        for (Selection selection : selections) {
            if (courseId.equals(selection.courseId)) {
                return selection;
            }
        }
        throw reject(422, "SECTION_REQUIRED", "Every selected course needs a section");
    }

    private List<Course> resolveSelections(Enrolment enrolment, List<Selection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw reject(422, "SELECTION_REQUIRED", "At least one course section is required");
        }
        Map<UUID, Course> chosen = new LinkedHashMap<UUID, Course>();
        for (Course course : courses.values()) {
            if (course.tenantId.equals(enrolment.tenantId) && course.programId.equals(enrolment.programId)
                    && course.termId.equals(enrolment.termId) && course.core) {
                chosen.put(course.id, course);
            }
        }
        for (Selection selection : selections) {
            Course course = requireCourse(enrolment.tenantId, selection.courseId);
            if (!course.programId.equals(enrolment.programId) || !course.termId.equals(enrolment.termId)) {
                throw reject(422, "COURSE_NOT_IN_TERM", "Course is outside the enrolment programme and term");
            }
            if (chosen.containsKey(course.id)) {
                continue;
            }
            chosen.put(course.id, course);
        }
        for (Course core : chosen.values()) {
            if (!core.core) {
                continue;
            }
            boolean selected = false;
            for (Selection selection : selections) {
                if (selection.courseId.equals(core.id)) {
                    selected = true;
                }
            }
            if (!selected) {
                throw reject(422, "CORE_REQUIRED", "Mandatory core courses must be selected");
            }
        }
        return new ArrayList<Course>(chosen.values());
    }

    private void assertPrerequisites(Enrolment enrolment, Course course) {
        Set<UUID> held = cleared.get(enrolment.tenantId + ":" + enrolment.studentId);
        for (UUID prerequisite : course.prerequisites) {
            if (held == null || !held.contains(prerequisite)) {
                throw reject(422, "PREREQUISITE_NOT_CLEARED", "A prerequisite has not been cleared");
            }
        }
    }

    private List<String> eligibilityFailures(Enrolment enrolment) {
        List<String> failures = new ArrayList<String>();
        Window window = windows.get(key(enrolment.tenantId, enrolment.termId));
        Instant now = clock.instant();
        if (window == null || now.isBefore(window.opensAt) || now.isAfter(window.closesAt)) {
            failures.add("WINDOW_CLOSED");
        }
        if ("REJECTED".equals(enrolment.status) || "SUSPENDED".equals(enrolment.status)) {
            failures.add("ENROLMENT_NOT_ACTIVE");
        }
        return failures;
    }

    private void recordAlert(Actor actor, Enrolment enrolment, String eventType) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("eventType", eventType);
        event.put("tenantId", enrolment.tenantId);
        event.put("enrolmentId", enrolment.id);
        event.put("studentId", enrolment.studentId);
        event.put("correlationId", actor.getCorrelationId());
        events.add(event);
    }

    private void publishEnrolled(Actor actor, Enrolment enrolment) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("eventType", STUDENT_ENROLLED);
        event.put("tenantId", enrolment.tenantId);
        event.put("enrolmentId", enrolment.id);
        event.put("studentId", enrolment.studentId);
        event.put("applicationId", enrolment.applicationId);
        event.put("termId", enrolment.termId);
        event.put("totalCredits", enrolment.totalCredits);
        List<String> courseIds = new ArrayList<String>();
        for (Selection selection : enrolment.selections) {
            courseIds.add(selection.courseId.toString());
        }
        event.put("courseIds", courseIds);
        event.put("correlationId", actor.getCorrelationId());
        events.add(event);
        intelligenceService.publishStudentEnrolled(event);
    }

    private void publishEnrolmentOpened(Actor actor, Enrolment enrolment) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("eventType", ENROLMENT_OPENED);
        event.put("tenantId", enrolment.tenantId);
        event.put("enrolmentId", enrolment.id);
        event.put("studentId", enrolment.studentId);
        event.put("applicationId", enrolment.applicationId);
        event.put("programId", enrolment.programId);
        event.put("termId", enrolment.termId);
        event.put("status", enrolment.status);
        event.put("correlationId", actor.getCorrelationId());
        events.add(event);
        intelligenceService.publishEnrolmentOpened(event);
    }

    private void requireStaffWrite(Actor actor) {
        requireRegistrar(actor);
    }

    private void requireRegistrar(Actor actor) {
        if (actor == null || actor.getTenantId() == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
        if (!isRegistrar(actor)) {
            throw reject(403, "CONFIG_FORBIDDEN",
                    "STM-03 configuration and approval require catalogue role R31 Registrar");
        }
    }

    private void requireAllocator(Actor actor) {
        if (actor == null || actor.getTenantId() == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
        if (!isAllocator(actor)) {
            throw reject(403, "ALLOCATE_FORBIDDEN",
                    "Section allocation requires catalogue role R07 Dean or R08 HOD");
        }
    }

    private boolean isRegistrar(Actor actor) {
        String role = actor.getRole();
        return "R31".equals(role) || "REGISTRAR".equals(role)
                || "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role);
    }

    private void hydrateTerms(Actor actor) {
        if (persistence == null) {
            return;
        }
        for (Term term : persistence.findTerms(actor)) {
            terms.put(key(term.tenantId, term.id), term);
        }
    }

    private void hydrateTenantEnrolments(Actor actor) {
        if (persistence == null) {
            return;
        }
        for (Enrolment loaded : persistence.findEnrolmentsForTenant(actor)) {
            String enrolmentKey = key(loaded.tenantId, loaded.id);
            if (!enrolments.containsKey(enrolmentKey)) {
                enrolments.put(enrolmentKey, loaded);
            }
        }
    }

    private void hydrateCatalogue(Actor actor, Enrolment enrolment) {
        if (persistence == null || enrolment == null) {
            return;
        }
        if (!terms.containsKey(key(enrolment.tenantId, enrolment.termId))) {
            for (Term term : persistence.findTerms(actor)) {
                if (term.id.equals(enrolment.termId)) {
                    terms.put(key(term.tenantId, term.id), term);
                }
            }
        }
        if (!windows.containsKey(key(enrolment.tenantId, enrolment.termId))) {
            Window window = persistence.findWindow(actor, enrolment.termId);
            if (window != null) {
                windows.put(key(window.tenantId, window.termId), window);
            }
        }
        for (Course course : persistence.findCourses(actor, enrolment.programId, enrolment.termId)) {
            courses.put(key(course.tenantId, course.id), course);
            for (Section section : persistence.findSections(actor, course.id)) {
                String sectionKey = key(section.tenantId, section.id);
                if (!sections.containsKey(sectionKey)) {
                    sections.put(sectionKey, section);
                }
            }
        }
        String clearedKey = enrolment.tenantId + ":" + enrolment.studentId;
        Set<UUID> held = cleared.get(clearedKey);
        if (held == null) {
            held = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
            cleared.put(clearedKey, held);
        }
        held.addAll(persistence.findClearedCourses(actor, enrolment.studentId));
    }

    private boolean isAllocator(Actor actor) {
        String role = actor.getRole();
        return "R07".equals(role) || "DEAN".equals(role) || "R08".equals(role) || "HOD".equals(role);
    }

    private boolean isStudent(Actor actor) {
        return "R25".equals(actor.getRole()) || "STUDENT".equals(actor.getRole());
    }

    private boolean isFaculty(Actor actor) {
        return "R10".equals(actor.getRole()) || "FACULTY".equals(actor.getRole());
    }

    private boolean ownsEnrolment(Actor actor, Enrolment enrolment) {
        UUID subjectId = actor.subjectStudentId();
        return subjectId != null && subjectId.equals(enrolment.studentId);
    }

    private Term requireTerm(UUID tenantId, UUID termId) {
        Term term = terms.get(key(tenantId, termId));
        if (term == null) {
            throw reject(404, "TERM_NOT_FOUND", "Academic term was not found");
        }
        return term;
    }

    private Course requireCourse(UUID tenantId, UUID courseId) {
        Course course = courses.get(key(tenantId, courseId));
        if (course == null) {
            throw reject(404, "COURSE_NOT_FOUND", "Course was not found");
        }
        return course;
    }

    private Section requireSection(UUID tenantId, UUID sectionId) {
        Section section = sections.get(key(tenantId, sectionId));
        if (section == null) {
            throw reject(404, "SECTION_NOT_FOUND", "Section was not found");
        }
        return section;
    }

    private Enrolment requireEnrolment(UUID tenantId, UUID enrolmentId) {
        if (tenantId == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
        Enrolment enrolment = enrolments.get(key(tenantId, enrolmentId));
        if (enrolment != null) {
            return enrolment;
        }
        if (persistence != null) {
            Actor lookup = new Actor(tenantId, tenantId, "REGISTRAR", "persistence-load");
            Enrolment loaded = persistence.findEnrolment(lookup, enrolmentId);
            enrolments.put(key(tenantId, loaded.id), loaded);
            return loaded;
        }
        throw reject(404, "ENROLMENT_NOT_FOUND", "Enrolment was not found");
    }

    private void persistTerm(Actor actor, Term term) {
        if (persistence != null) {
            persistence.saveTerm(actor, term);
        }
    }

    private void persistWindow(Actor actor, Window window) {
        if (persistence != null) {
            persistence.saveWindow(actor, window);
        }
    }

    private void persistEnrolment(Actor actor, Enrolment enrolment) {
        if (persistence != null) {
            persistence.saveEnrolment(actor, enrolment);
        }
    }

    private void persistHistory(Actor actor, Enrolment enrolment, Map<String, String> historyRow) {
        if (persistence != null && historyRow != null) {
            persistence.appendHistory(actor, enrolment, historyRow);
        }
    }

    private Map<String, String> history(Actor actor, String status, String action) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        row.put("status", status);
        row.put("action", action);
        row.put("actorRole", actor.getRole());
        row.put("at", clock.instant().toString());
        return row;
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw reject(422, "FIELD_REQUIRED", field + " is required");
        }
    }

    /**
     * Records a rejected business rule through {@link CampXLogger} and returns the exception
     * the caller must throw. Status 500 and above are logged as errors; other rejections are warnings.
     */
    private EnrollmentException reject(int status, String code, String message) {
        if (status >= 500) {
            logger.error("stm03 rejected status={} code={} message={}", status, code, message);
        } else {
            logger.warn("stm03 rejected status={} code={} message={}", status, code, message);
        }
        return new EnrollmentException(status, code, message);
    }

    private void log(Actor actor, String action, UUID resourceId) {
        logger.info("stm03 action={} tenantId={} correlationId={} resourceId={}",
                action, actor.getTenantId(), actor.getCorrelationId(), resourceId);
        logger.audit(AuditEvent.builder()
                .action(action)
                .tenantId(actor.getTenantId() == null ? null : actor.getTenantId().toString())
                .principalId(actor.getUserId() == null ? null : actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("ENROLMENT")
                .resourceId(resourceId == null ? null : resourceId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description(action)
                .build());
    }

    private static String key(UUID tenantId, UUID id) {
        return tenantId + ":" + id;
    }

    public static final class Term {
        public final UUID id;
        public final UUID tenantId;
        public final String yearCode;
        public final String termCode;
        public final String name;

        public Term(UUID id, UUID tenantId, String yearCode, String termCode, String name) {
            this.id = id;
            this.tenantId = tenantId;
            this.yearCode = yearCode;
            this.termCode = termCode;
            this.name = name;
        }
    }

    public static final class Window {
        public final UUID id;
        public final UUID tenantId;
        public final UUID termId;
        public final Instant opensAt;
        public final Instant closesAt;
        public final int minCredits;
        public final int maxCredits;

        public Window(UUID id, UUID tenantId, UUID termId, Instant opensAt, Instant closesAt, int minCredits, int maxCredits) {
            this.id = id;
            this.tenantId = tenantId;
            this.termId = termId;
            this.opensAt = opensAt;
            this.closesAt = closesAt;
            this.minCredits = minCredits;
            this.maxCredits = maxCredits;
        }
    }

    public static final class Course {
        public final UUID id;
        public final UUID tenantId;
        public final UUID programId;
        public final UUID termId;
        public final String code;
        public final int credits;
        public final boolean core;
        public final List<UUID> prerequisites;

        public Course(UUID id, UUID tenantId, UUID programId, UUID termId, String code, int credits, boolean core, List<UUID> prerequisites) {
            this.id = id;
            this.tenantId = tenantId;
            this.programId = programId;
            this.termId = termId;
            this.code = code;
            this.credits = credits;
            this.core = core;
            this.prerequisites = prerequisites;
        }
    }

    public static final class Section {
        public final UUID id;
        public final UUID tenantId;
        public final UUID courseId;
        public final int capacity;
        public int enrolledCount;
        public final UUID facultyUserId;

        public Section(UUID id, UUID tenantId, UUID courseId, int capacity, int enrolledCount, UUID facultyUserId) {
            this.id = id;
            this.tenantId = tenantId;
            this.courseId = courseId;
        this.capacity = capacity;
        this.enrolledCount = enrolledCount;
        this.facultyUserId = facultyUserId;
    }

    public final List<UUID> waitlist = new ArrayList<UUID>();
}

    public static final class Selection {
        public final UUID courseId;
        public UUID sectionId;

        public Selection(UUID courseId, UUID sectionId) {
            this.courseId = courseId;
            this.sectionId = sectionId;
        }
    }

    public static final class Enrolment {
        public final UUID id;
        public final UUID tenantId;
        public final UUID applicationId;
        public final UUID studentId;
        public final UUID programId;
        public final UUID termId;
        public String status;
        public int totalCredits;
        public final List<Selection> selections = new ArrayList<Selection>();
        public final List<Map<String, String>> history = new ArrayList<Map<String, String>>();

        public Enrolment(UUID id, UUID tenantId, UUID applicationId, UUID studentId, UUID programId, UUID termId, String status) {
            this.id = id;
            this.tenantId = tenantId;
            this.applicationId = applicationId;
            this.studentId = studentId;
            this.programId = programId;
            this.termId = termId;
            this.status = status;
        }
    }
}
