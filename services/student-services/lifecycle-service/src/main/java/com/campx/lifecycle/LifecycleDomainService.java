package com.campx.lifecycle;

import com.campx.lifecycle.intelligence.LifecycleIntelligenceService;
import com.campx.lifecycle.persistence.LifecyclePersistence;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * STM-04 student lifecycle authority.
 * <p>
 * Students (R25) view and submit requests only for their own record. Registrars (R31), including
 * college and institution administrators, approve requests, configure transition rules, and record
 * graduation. Parents (R26) see a limited status for the linked student named in the request header.
 * Faculty are denied the student record; an advisor may see only assigned advisees.
 * Business rule failures are returned as {@link LifecycleException} and written to {@link CampXLogger}.
 * PostgreSQL remains the source of truth when a persistence implementation is bound.
 */
public class LifecycleDomainService {

    public static final String LIFECYCLE_CHANGED = "student.lifecycle.changed.v1";
    public static final String SCHEMA_VERSION = "1.0.0";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(LifecycleDomainService.class);

    private final Clock clock;
    private final LifecyclePersistence persistence;
    private final LifecycleIntelligenceService intelligenceService;

    private final Map<String, LifecycleStatus> statuses = new ConcurrentHashMap<String, LifecycleStatus>();
    private final Map<String, List<HistoryEntry>> history = new ConcurrentHashMap<String, List<HistoryEntry>>();
    private final Map<String, LifecycleRequest> requests = new ConcurrentHashMap<String, LifecycleRequest>();
    private final Map<String, TransitionRule> rules = new ConcurrentHashMap<String, TransitionRule>();
    private final Map<String, GraduationRecord> graduations = new ConcurrentHashMap<String, GraduationRecord>();
    private final Map<String, Set<UUID>> adviseesByAdvisor = new ConcurrentHashMap<String, Set<UUID>>();
    private final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<Map<String, Object>>());

    public LifecycleDomainService() {
        this(Clock.systemUTC(), null, null);
    }

    public LifecycleDomainService(Clock clock) {
        this(clock, null, null);
    }

    public LifecycleDomainService(Clock clock, LifecyclePersistence persistence) {
        this(clock, persistence, null);
    }

    public LifecycleDomainService(Clock clock, LifecyclePersistence persistence, LifecycleIntelligenceService intelligenceService) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.persistence = persistence;
        this.intelligenceService = intelligenceService == null
                ? new LifecycleIntelligenceService()
                : intelligenceService;
    }

    public boolean isPersistent() {
        return persistence != null;
    }

    public LifecycleStatus seedStudent(UUID tenantId, UUID studentId) {
        return seedStudent(tenantId, studentId, "ACTIVE");
    }

    public LifecycleStatus seedStudent(UUID tenantId, UUID studentId, String status) {
        Instant now = clock.instant();
        LifecycleStatus row = new LifecycleStatus(tenantId, studentId, status, now, now);
        statuses.put(statusKey(tenantId, studentId), row);
        if (persistence != null) {
            Actor system = new Actor(tenantId, tenantId, "REGISTRAR", "seed");
            persistence.saveStatus(system, row);
        }
        return row;
    }

    /**
     * Returns the student's lifecycle status.
     * A student may open their own missing status as ACTIVE. A registrar reads an existing status.
     *
     * @throws LifecycleException 403 when the caller is outside the student or registrar catalogue
     * @throws LifecycleException 404 when a registrar asks for a student with no status
     */
    public LifecycleStatus getStatus(Actor actor, UUID studentId) {
        requireTenant(actor);
        forbidFaculty(actor);
        if (isStudent(actor) && !actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "VIEW_FORBIDDEN", "Students may only view their own lifecycle status");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "VIEW_FORBIDDEN", "Lifecycle status view requires R25 Student or R31 Registrar");
        }
        return requireOrOpenOwn(actor, studentId);
    }

    /**
     * Returns the status history for one student in the caller's tenant.
     *
     * @throws LifecycleException 403 when the student asks for another student's history
     */
    public List<HistoryEntry> listHistory(Actor actor, UUID studentId) {
        requireTenant(actor);
        forbidFaculty(actor);
        if (isStudent(actor) && !actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "VIEW_FORBIDDEN", "Students may only view their own lifecycle history");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "VIEW_FORBIDDEN", "Lifecycle history requires R25 Student or R31 Registrar");
        }
        String key = statusKey(actor.getTenantId(), studentId);
        List<HistoryEntry> rows = history.get(key);
        if (rows == null && persistence != null) {
            rows = persistence.findHistory(actor, studentId);
            history.put(key, new ArrayList<HistoryEntry>(rows));
        }
        if (rows == null) {
            return Collections.emptyList();
        }
        return new ArrayList<HistoryEntry>(rows);
    }

    public LifecycleRequest submitRequest(Actor actor, UUID studentId, String requestType, String reason) {
        return submitRequest(actor, studentId, requestType, reason, null);
    }

    /**
     * Submits leave, reinstatement, transfer, or withdrawal for the authenticated student.
     * Withdrawal requires {@code effectiveDate}. The request stays pending until a registrar decides it.
     *
     * @throws LifecycleException 403 when the caller is not the student who owns the record
     * @throws LifecycleException 422 when the type, current status, or effective date is not allowed
     */
    public LifecycleRequest submitRequest(Actor actor, UUID studentId, String requestType, String reason, LocalDate effectiveDate) {
        requireTenant(actor);
        forbidFaculty(actor);
        if (!isStudent(actor)) {
            throw reject(403, "REQUEST_FORBIDDEN", "Only catalogue role R25 Student may submit lifecycle requests");
        }
        if (!actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "REQUEST_FORBIDDEN", "Students may only submit requests for their own record");
        }
        requireOrOpenOwn(actor, studentId);
        requireText(requestType, "requestType");
        String normalizedType = requestType.trim().toUpperCase();
        if (!isKnownRequestType(normalizedType)) {
            throw reject(422, "REQUEST_TYPE_INVALID", "Request type must be LEAVE, REINSTATE, TRANSFER, or WITHDRAWAL");
        }
        if ("WITHDRAWAL".equals(normalizedType) && effectiveDate == null) {
            throw reject(422, "EFFECTIVE_DATE_REQUIRED", "Withdrawal requires an effective date");
        }
        if (effectiveDate != null && effectiveDate.isBefore(LocalDate.now(clock))) {
            throw reject(422, "EFFECTIVE_DATE_INVALID", "Effective date cannot be in the past");
        }
        LifecycleStatus current = requireStatus(actor.getTenantId(), studentId);
        String targetStatus = targetStatusForRequest(normalizedType, current.status);
        TransitionRule rule = findRule(actor.getTenantId(), current.status, targetStatus);
        if (rule != null && !rule.active) {
            throw reject(422, "TRANSITION_INACTIVE", "This lifecycle transition is not active");
        }
        Instant now = clock.instant();
        LifecycleRequest request = new LifecycleRequest(actor.getTenantId(), UUID.randomUUID(), studentId,
                normalizedType, "PENDING", reason, effectiveDate, null, null, now);
        requests.put(requestKey(actor.getTenantId(), request.id), request);
        if (persistence != null) {
            persistence.saveRequest(actor, request);
        }
        log(actor, "lifecycle.request.submitted", studentId);
        return request;
    }

    /**
     * Approves or rejects a pending lifecycle request.
     * Approval applies the matching status transition and writes history.
     *
     * @param decision {@code APPROVED} or {@code REJECTED}
     * @throws LifecycleException 403 when the caller is not a registrar
     * @throws LifecycleException 409 when the request was already decided
     */
    public LifecycleRequest decideRequest(Actor actor, UUID requestId, String decision) {
        requireRegistrar(actor);
        requireText(decision, "decision");
        String normalized = decision.trim().toUpperCase();
        if (!"APPROVED".equals(normalized) && !"REJECTED".equals(normalized)) {
            throw reject(422, "DECISION_INVALID", "Decision must be APPROVED or REJECTED");
        }
        LifecycleRequest request = requireRequest(actor.getTenantId(), requestId);
        if (!"PENDING".equals(request.status)) {
            throw reject(409, "REQUEST_ALREADY_DECIDED", "Request has already been decided");
        }

        LifecycleStatus current = null;
        String target = null;
        if ("APPROVED".equals(normalized)) {
            current = requireStatus(actor.getTenantId(), request.studentId);
            target = targetStatusForRequest(request.requestType, current.status);
            assertTransitionAllowed(actor.getTenantId(), current.status, target);
        }

        Instant now = clock.instant();
        request.status = normalized;
        request.decidedBy = actor.getUserId();
        request.decidedAt = now;
        if (persistence != null) {
            persistence.saveRequest(actor, request);
        }
        if ("APPROVED".equals(normalized) && current != null && target != null) {
            applyTransition(actor, current, target, "request.approved:" + request.requestType, request.id, request.effectiveDate);
        }
        log(actor, "lifecycle.request." + normalized.toLowerCase(), request.studentId);
        return request;
    }

    /**
     * Assigns a faculty advisor to a student. The link is in-memory only.
     *
     * @throws LifecycleException 403 when the caller is not a registrar
     * @throws LifecycleException 422 when either identifier is missing
     */
    public void assignAdvisor(Actor actor, UUID advisorUserId, UUID studentId) {
        requireRegistrar(actor);
        if (advisorUserId == null || studentId == null) {
            throw reject(422, "ADVISOR_INVALID", "Advisor and student are required");
        }
        LifecycleStatus status = requireOrOpenForStaff(actor, studentId);
        String key = advisorKey(actor.getTenantId(), advisorUserId);
        Set<UUID> advisees = adviseesByAdvisor.get(key);
        if (advisees == null) {
            advisees = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
            adviseesByAdvisor.put(key, advisees);
        }
        advisees.add(studentId);

        HistoryEntry entry = new HistoryEntry(actor.getTenantId(), UUID.randomUUID(), studentId, status.status,
                "advisor:" + advisorUserId, actor.getRole(), Instant.now());
        String historyKey = statusKey(actor.getTenantId(), studentId);
        List<HistoryEntry> rows = history.get(historyKey);
        if (rows == null) {
            rows = new CopyOnWriteArrayList<HistoryEntry>();
            history.put(historyKey, rows);
        }
        rows.add(entry);
        if (persistence != null) {
            persistence.appendHistory(actor, entry);
        }

        log(actor, "advisor:" + advisorUserId, studentId);
        emitLifecycleChanged(actor, studentId, status.status, status.status, "advisor:" + advisorUserId, null, Instant.now());
    }

    /**
     * Administratively transitions or reinstates a student's status.
     *
     * @throws LifecycleException 403 when caller is not a registrar
     * @throws LifecycleException 422 when student or target status is missing
     */
    public LifecycleStatus transitionStatus(Actor actor, UUID studentId, String targetStatus, String reason) {
        requireRegistrar(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student ID is required");
        }
        requireText(targetStatus, "targetStatus");
        String normalized = targetStatus.trim().toUpperCase();
        LifecycleStatus current = requireOrOpenForStaff(actor, studentId);
        applyTransition(actor, current, normalized, "administrative.transition:" + (reason == null || reason.trim().isEmpty() ? "REGISTRAR" : reason.trim()), null);
        log(actor, "lifecycle.status.transitioned", studentId);
        return current;
    }

    /**
     * Returns limited status for advisees assigned to the calling faculty member.
     * Queries committed PostgreSQL history for persisted assignments as well as runtime state.
     *
     * @throws LifecycleException 403 when the caller is not faculty or an advisor
     */
    public List<Map<String, String>> listAdvisees(Actor actor) {
        requireTenant(actor);
        if (!isAdvisor(actor)) {
            throw reject(403, "ADVISOR_FORBIDDEN", "Advisee status is limited to the assigned faculty advisor");
        }
        Set<UUID> adviseeIds = new LinkedHashSet<UUID>();
        if (persistence != null && actor.getUserId() != null) {
            try {
                adviseeIds.addAll(persistence.findAdviseesByAdvisor(actor, actor.getUserId()));
            } catch (Exception ex) {
                // fallback to runtime state if db lookup encountered non-fatal issue
            }
        }
        Set<UUID> inMem = adviseesByAdvisor.get(advisorKey(actor.getTenantId(), actor.getUserId()));
        if (inMem != null) {
            adviseeIds.addAll(inMem);
        }
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        for (UUID studentId : adviseeIds) {
            LifecycleStatus status = requireStatus(actor.getTenantId(), studentId);
            Map<String, String> row = new LinkedHashMap<String, String>();
            row.put("studentId", studentId.toString());
            row.put("status", status.status);
            row.put("effectiveFrom", status.effectiveFrom.toString());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Lists pending lifecycle requests submitted by advisees of the calling advisor / HOD.
     *
     * @throws LifecycleException 403 when the caller is not an advisor/faculty
     */
    public List<LifecycleRequest> listAdviseeRequests(Actor actor) {
        requireTenant(actor);
        if (!isAdvisor(actor)) {
            throw reject(403, "ADVISOR_FORBIDDEN", "Advisee requests are limited to the assigned faculty advisor or department head");
        }
        Set<UUID> adviseeIds = new LinkedHashSet<UUID>();
        if (persistence != null && actor.getUserId() != null) {
            try {
                adviseeIds.addAll(persistence.findAdviseesByAdvisor(actor, actor.getUserId()));
            } catch (Exception ignored) {
            }
        }
        Set<UUID> inMem = adviseesByAdvisor.get(advisorKey(actor.getTenantId(), actor.getUserId()));
        if (inMem != null) {
            adviseeIds.addAll(inMem);
        }
        List<LifecycleRequest> pending = new ArrayList<LifecycleRequest>();
        for (UUID studentId : adviseeIds) {
            List<LifecycleRequest> studentReqs = null;
            if (persistence != null) {
                try {
                    studentReqs = persistence.findRequestsByStudent(actor, studentId);
                } catch (Exception ignored) {
                }
            }
            if (studentReqs == null) {
                studentReqs = new ArrayList<LifecycleRequest>();
                for (LifecycleRequest r : requests.values()) {
                    if (r.tenantId.equals(actor.getTenantId()) && r.studentId.equals(studentId)) {
                        studentReqs.add(r);
                    }
                }
            }
            for (LifecycleRequest req : studentReqs) {
                if ("PENDING".equals(req.status)) {
                    pending.add(req);
                }
            }
        }
        return pending;
    }

    /**
     * Head of Department (HOD) or Faculty Advisor formal endorsement of a student leave petition.
     *
     * @throws LifecycleException 403 when the caller is not an advisor/faculty
     * @throws LifecycleException 409 when the request was already decided
     * @throws LifecycleException 422 when remarks are missing or too short
     */
    public Map<String, Object> endorseRequest(Actor actor, UUID requestId, String endorsement, String remarks) {
        requireTenant(actor);
        if (!isAdvisor(actor)) {
            throw reject(403, "ADVISOR_FORBIDDEN", "Departmental endorsement requires faculty advisor or department head authority");
        }
        requireText(endorsement, "endorsement");
        requireText(remarks, "remarks");
        String normRemarks = remarks.trim();
        if (normRemarks.length() < 5) {
            throw reject(422, "REMARKS_TOO_SHORT", "Departmental endorsement remarks must contain at least 5 characters");
        }
        String normEndorsement = endorsement.trim().toUpperCase();
        LifecycleRequest request = requireRequest(actor.getTenantId(), requestId);
        if (!"PENDING".equals(request.status)) {
            throw reject(409, "REQUEST_ALREADY_DECIDED", "Request has already been decided");
        }

        Instant now = clock.instant();
        String actionName = "hod.endorsement:" + normEndorsement;
        HistoryEntry entry = new HistoryEntry(actor.getTenantId(), UUID.randomUUID(), request.studentId,
                "PENDING", actionName, actor.getRole(), now);

        String historyKey = statusKey(actor.getTenantId(), request.studentId);
        List<HistoryEntry> rows = history.get(historyKey);
        if (rows == null) {
            rows = new CopyOnWriteArrayList<HistoryEntry>();
            history.put(historyKey, rows);
        }
        rows.add(entry);

        String endorsementSuffix = " [HOD Endorsement (" + normEndorsement + "): " + normRemarks + "]";
        if (request.reason == null) {
            request.reason = endorsementSuffix.trim();
        } else if (!request.reason.contains("HOD Endorsement")) {
            request.reason = request.reason + endorsementSuffix;
        }

        if (persistence != null) {
            persistence.appendHistory(actor, entry);
            persistence.saveRequest(actor, request);
        }

        emitLifecycleChanged(actor, request.studentId, "PENDING", "PENDING", actionName, request.id, now);
        log(actor, "lifecycle.request.endorsed:" + normEndorsement.toLowerCase(), request.studentId);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", request.id.toString());
        result.put("studentId", request.studentId.toString());
        result.put("requestType", request.requestType);
        result.put("status", request.status);
        result.put("endorsed", Boolean.TRUE);
        result.put("endorsement", normEndorsement);
        result.put("endorsedBy", actor.getUserId() == null ? "" : actor.getUserId().toString());
        result.put("remarks", normRemarks);
        result.put("endorsedAt", now.toString());
        return result;
    }

    /**
     * Counts students by lifecycle status for the registrar's tenant.
     *
     * @throws LifecycleException 403 when the caller is not a registrar
     */
    public Map<String, Object> lifecycleReport(Actor actor) {
        requireRegistrar(actor);
        hydrateStatuses(actor);
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        int students = 0;
        for (LifecycleStatus status : statuses.values()) {
            if (!status.tenantId.equals(actor.getTenantId())) {
                continue;
            }
            students++;
            Integer current = counts.get(status.status);
            counts.put(status.status, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
        }
        int pending = 0;
        for (LifecycleRequest request : listPendingRequests(actor)) {
            pending++;
        }
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("studentCount", Integer.valueOf(students));
        report.put("pendingRequests", Integer.valueOf(pending));
        report.put("byStatus", counts);
        return report;
    }

    /**
     * Lists pending lifecycle requests in the registrar's tenant.
     *
     * @throws LifecycleException 403 when the caller is not a registrar
     */
    public List<LifecycleRequest> listPendingRequests(Actor actor) {
        requireRegistrar(actor);
        if (persistence != null) {
            List<LifecycleRequest> fromDb = persistence.findPendingRequests(actor);
            for (LifecycleRequest loaded : fromDb) {
                requests.put(requestKey(loaded.tenantId, loaded.id), loaded);
            }
            return fromDb;
        }
        List<LifecycleRequest> pending = new ArrayList<LifecycleRequest>();
        for (LifecycleRequest request : requests.values()) {
            if (request.tenantId.equals(actor.getTenantId()) && "PENDING".equals(request.status)) {
                pending.add(request);
            }
        }
        return pending;
    }

    /**
     * Lists all lifecycle requests submitted by or for a student.
     *
     * @throws LifecycleException 403 when a student tries to view another student's requests
     */
    public List<LifecycleRequest> listStudentRequests(Actor actor, UUID studentId) {
        requireTenant(actor);
        forbidFaculty(actor);
        if (isStudent(actor) && !actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "REQUEST_FORBIDDEN", "Students may only view requests for their own record");
        }
        if (persistence != null) {
            List<LifecycleRequest> fromDb = persistence.findRequestsByStudent(actor, studentId);
            for (LifecycleRequest loaded : fromDb) {
                requests.put(requestKey(loaded.tenantId, loaded.id), loaded);
            }
            return fromDb;
        }
        List<LifecycleRequest> result = new ArrayList<LifecycleRequest>();
        for (LifecycleRequest request : requests.values()) {
            if (request.tenantId.equals(actor.getTenantId()) && request.studentId.equals(studentId)) {
                result.add(request);
            }
        }
        return result;
    }

    /**
     * Saves a tenant transition rule. An inactive rule blocks that status change.
     *
     * @throws LifecycleException 403 when the caller is not a registrar
     */
    public TransitionRule configureRule(Actor actor, String fromStatus, String toStatus, boolean requiresApproval, boolean active) {
        requireRegistrar(actor);
        requireText(fromStatus, "fromStatus");
        requireText(toStatus, "toStatus");
        String from = fromStatus.trim().toUpperCase();
        String to = toStatus.trim().toUpperCase();
        TransitionRule rule = new TransitionRule(actor.getTenantId(), UUID.randomUUID(), from, to, requiresApproval, active);
        rules.put(ruleKey(actor.getTenantId(), from, to), rule);
        if (persistence != null) {
            persistence.saveRule(actor, rule);
        }
        log(actor, "lifecycle.rule.configured", rule.id);
        return rule;
    }

    /**
     * Checks whether a student can be graduated.
     * The student must be ACTIVE and must not have a pending lifecycle request.
     *
     * @return a map with {@code eligible} and the list of {@code failures}
     * @throws LifecycleException 403 when the caller is not a registrar
     */
    public Map<String, Object> validateGraduation(Actor actor, UUID studentId, UUID programId) {
        requireRegistrar(actor);
        if (studentId == null || programId == null) {
            throw reject(422, "GRADUATION_INVALID", "Student and programme are required");
        }
        LifecycleStatus status = requireStatus(actor.getTenantId(), studentId);
        List<String> failures = new ArrayList<String>();
        if (!"ACTIVE".equals(status.status)) {
            failures.add("STUDENT_NOT_ACTIVE");
        }
        if (hasPendingRequest(actor, studentId)) {
            failures.add("PENDING_LIFECYCLE_REQUEST");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("studentId", studentId.toString());
        result.put("programId", programId.toString());
        result.put("eligible", failures.isEmpty());
        result.put("failures", failures);
        return result;
    }

    /**
     * Records graduation and moves an eligible student to GRADUATED.
     *
     * @throws LifecycleException 422 when validation fails or the date is missing
     */
    public GraduationRecord recordGraduation(Actor actor, UUID studentId, UUID programId, LocalDate graduatedOn, String notes) {
        requireRegistrar(actor);
        if (studentId == null || programId == null || graduatedOn == null) {
            throw reject(422, "GRADUATION_INVALID", "Student, programme, and graduation date are required");
        }
        Map<String, Object> validation = validateGraduation(actor, studentId, programId);
        if (!Boolean.TRUE.equals(validation.get("eligible"))) {
            throw reject(422, "GRADUATION_NOT_ELIGIBLE", "Graduation validation failed");
        }
        Instant now = clock.instant();
        GraduationRecord record = new GraduationRecord(actor.getTenantId(), UUID.randomUUID(), studentId, programId, graduatedOn, notes, now);
        graduations.put(graduationKey(actor.getTenantId(), studentId, programId), record);
        if (persistence != null) {
            persistence.saveGraduation(actor, record);
        }
        LifecycleStatus current = requireStatus(actor.getTenantId(), studentId);
        applyTransition(actor, current, "GRADUATED", "graduation.recorded", null);
        log(actor, "lifecycle.graduation.recorded", studentId);
        return record;
    }

    /**
     * Returns the limited status a parent may see for one linked student.
     * The path student id and {@code X-Linked-Student-Id} must match.
     *
     * @throws LifecycleException 403 when the caller is not a parent or the link does not match
     */
    public Map<String, String> parentViewLinkedStatus(Actor actor, UUID linkedStudentId, String headerLinkedStudentId) {
        requireTenant(actor);
        if (!isParent(actor)) {
            throw reject(403, "PARENT_FORBIDDEN", "Linked lifecycle view requires catalogue role R26 Parent");
        }
        if (linkedStudentId == null) {
            throw reject(422, "LINKED_STUDENT_REQUIRED", "Linked student id is required");
        }
        if (headerLinkedStudentId == null || headerLinkedStudentId.trim().isEmpty()) {
            throw reject(403, "LINK_HEADER_REQUIRED", "X-Linked-Student-Id header is required for parent view");
        }
        if (!linkedStudentId.toString().equalsIgnoreCase(headerLinkedStudentId.trim())) {
            throw reject(403, "LINK_MISMATCH", "Path student id must match X-Linked-Student-Id");
        }
        LifecycleStatus status = requireStatus(actor.getTenantId(), linkedStudentId);
        Map<String, String> view = new LinkedHashMap<String, String>();
        view.put("studentId", linkedStudentId.toString());
        view.put("status", status.status);
        view.put("effectiveFrom", status.effectiveFrom.toString());
        return view;
    }

    public List<Map<String, Object>> eventsForTenant(UUID tenantId) {
        List<Map<String, Object>> filtered = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> event : events) {
            if (tenantId.equals(event.get("tenantId"))) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    private void applyTransition(Actor actor, LifecycleStatus current, String targetStatus, String action, UUID requestId) {
        applyTransition(actor, current, targetStatus, action, requestId, null);
    }

    private void applyTransition(Actor actor, LifecycleStatus current, String targetStatus, String action, UUID requestId,
                                 LocalDate effectiveOn) {
        String previous = current.status;
        Instant now = clock.instant();
        current.status = targetStatus;
        current.effectiveFrom = effectiveOn == null ? now : effectiveOn.atStartOfDay(ZoneOffset.UTC).toInstant();
        current.updatedAt = now;
        if (persistence != null) {
            persistence.saveStatus(actor, current);
        }
        HistoryEntry entry = new HistoryEntry(actor.getTenantId(), UUID.randomUUID(), current.studentId,
                targetStatus, action, actor.getRole(), now);
        String historyKey = statusKey(actor.getTenantId(), current.studentId);
        List<HistoryEntry> rows = history.get(historyKey);
        if (rows == null) {
            rows = new ArrayList<HistoryEntry>();
            history.put(historyKey, rows);
        }
        rows.add(entry);
        if (persistence != null) {
            persistence.appendHistory(actor, entry);
        }
        emitLifecycleChanged(actor, current.studentId, previous, targetStatus, action, requestId, now);
    }

    private void emitLifecycleChanged(Actor actor, UUID studentId, String previous, String status, String action,
                                      UUID requestId, Instant occurredAt) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("tenantId", actor.getTenantId());
        event.put("studentId", studentId);
        event.put("previousStatus", previous);
        event.put("status", status);
        event.put("action", action);
        event.put("requestId", requestId == null ? null : requestId.toString());
        event.put("correlationId", actor.getCorrelationId());
        event.put("occurredAt", occurredAt.toString());
        events.add(event);
        intelligenceService.publishLifecycleChanged(event);
        logger.info("lifecycle transition studentId={} {}->{}", studentId, previous, status);
    }

    private String targetStatusForRequest(String requestType, String currentStatus) {
        if ("LEAVE".equals(requestType)) {
            if (!"ACTIVE".equals(currentStatus)) {
                throw reject(422, "TRANSITION_INVALID", "Leave is only allowed from ACTIVE status");
            }
            return "ON_LEAVE";
        }
        if ("REINSTATE".equals(requestType)) {
            if (!"ON_LEAVE".equals(currentStatus)) {
                throw reject(422, "TRANSITION_INVALID", "Reinstate is only allowed from ON_LEAVE status");
            }
            return "ACTIVE";
        }
        if ("TRANSFER".equals(requestType)) {
            if (!"ACTIVE".equals(currentStatus)) {
                throw reject(422, "TRANSITION_INVALID", "Transfer is only allowed from ACTIVE status");
            }
            return "TRANSFERRED";
        }
        if ("WITHDRAWAL".equals(requestType)) {
            if (!"ACTIVE".equals(currentStatus) && !"ON_LEAVE".equals(currentStatus)) {
                throw reject(422, "TRANSITION_INVALID", "Withdrawal is only allowed from ACTIVE or ON_LEAVE");
            }
            return "WITHDRAWN";
        }
        throw reject(422, "REQUEST_TYPE_INVALID", "Unknown request type");
    }

    private void assertTransitionAllowed(UUID tenantId, String fromStatus, String toStatus) {
        TransitionRule rule = findRule(tenantId, fromStatus, toStatus);
        if (rule != null && !rule.active) {
            throw reject(422, "TRANSITION_INACTIVE", "Lifecycle transition is inactive");
        }
    }

    private TransitionRule findRule(UUID tenantId, String fromStatus, String toStatus) {
        TransitionRule rule = rules.get(ruleKey(tenantId, fromStatus, toStatus));
        if (rule == null) {
            rule = defaultRule(tenantId, fromStatus, toStatus);
        }
        return rule;
    }

    private TransitionRule defaultRule(UUID tenantId, String fromStatus, String toStatus) {
        boolean active = true;
        boolean requiresApproval = true;
        return new TransitionRule(tenantId, UUID.nameUUIDFromBytes((tenantId + fromStatus + toStatus).getBytes()),
                fromStatus, toStatus, requiresApproval, active);
    }

    private boolean hasPendingRequest(Actor actor, UUID studentId) {
        if (persistence != null) {
            for (LifecycleRequest request : persistence.findPendingRequests(actor)) {
                if (studentId.equals(request.studentId)) {
                    return true;
                }
            }
            return false;
        }
        for (LifecycleRequest request : requests.values()) {
            if (request.tenantId.equals(actor.getTenantId())
                    && request.studentId.equals(studentId)
                    && "PENDING".equals(request.status)) {
                return true;
            }
        }
        return false;
    }

    private LifecycleStatus requireStatus(UUID tenantId, UUID studentId) {
        if (tenantId == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
        if (persistence != null) {
            Actor lookup = new Actor(tenantId, tenantId, "REGISTRAR", "persistence-load");
            LifecycleStatus status = persistence.findStatus(lookup, studentId);
            if (status != null) {
                statuses.put(statusKey(tenantId, studentId), status);
                return status;
            }
        }
        LifecycleStatus status = statuses.get(statusKey(tenantId, studentId));
        if (status != null) {
            return status;
        }
        throw reject(404, "STATUS_NOT_FOUND", "Lifecycle status was not found");
    }

    private LifecycleRequest requireRequest(UUID tenantId, UUID requestId) {
        if (persistence != null) {
            Actor lookup = new Actor(tenantId, tenantId, "REGISTRAR", "persistence-load");
            LifecycleRequest request = persistence.findRequest(lookup, requestId);
            if (request != null) {
                requests.put(requestKey(tenantId, requestId), request);
                return request;
            }
        }
        LifecycleRequest request = requests.get(requestKey(tenantId, requestId));
        if (request != null) {
            if (!request.tenantId.equals(tenantId)) {
                throw reject(404, "REQUEST_NOT_FOUND", "Lifecycle request was not found");
            }
            return request;
        }
        throw reject(404, "REQUEST_NOT_FOUND", "Lifecycle request was not found");
    }

    private void requireRegistrar(Actor actor) {
        requireTenant(actor);
        if (!isRegistrar(actor)) {
            throw reject(403, "REGISTRAR_FORBIDDEN", "STM-04 configuration and approval require catalogue role R31 Registrar");
        }
    }

    private void forbidFaculty(Actor actor) {
        if (isFaculty(actor)) {
            throw reject(403, "FACULTY_FORBIDDEN", "Faculty may not access student lifecycle records");
        }
    }

    private void requireTenant(Actor actor) {
        if (actor == null || actor.getTenantId() == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
    }

    private void hydrateStatuses(Actor actor) {
        if (persistence == null) {
            return;
        }
        for (LifecycleStatus loaded : persistence.findAllStatuses(actor)) {
            statuses.put(statusKey(loaded.tenantId, loaded.studentId), loaded);
        }
    }

    private LifecycleStatus requireOrOpenForStaff(Actor actor, UUID studentId) {
        try {
            return requireStatus(actor.getTenantId(), studentId);
        } catch (LifecycleException ex) {
            if (ex.getStatus() == 404) {
                return seedStudent(actor.getTenantId(), studentId, "ACTIVE");
            }
            throw ex;
        }
    }

    private boolean isAdvisor(Actor actor) {
        String role = actor.getRole();
        if (role == null) {
            return false;
        }
        return "FACULTY".equalsIgnoreCase(role) || "ADVISOR".equalsIgnoreCase(role) || "R10".equalsIgnoreCase(role) || "TEACHER".equalsIgnoreCase(role);
    }

    private static String advisorKey(UUID tenantId, UUID advisorUserId) {
        return tenantId + ":" + advisorUserId;
    }

    private LifecycleStatus requireOrOpenOwn(Actor actor, UUID studentId) {
        try {
            return requireStatus(actor.getTenantId(), studentId);
        } catch (LifecycleException ex) {
            if (ex.getStatus() == 404 && isStudent(actor) && actor.subjectStudentId().equals(studentId)) {
                return seedStudent(actor.getTenantId(), studentId, "ACTIVE");
            }
            throw ex;
        }
    }

    private boolean isRegistrar(Actor actor) {
        String role = actor.getRole();
        return "R31".equals(role) || "REGISTRAR".equals(role)
                || "COLLEGE_ADMIN".equals(role) || "INSTITUTION_ADMIN".equals(role);
    }

    private boolean isStudent(Actor actor) {
        return "R25".equals(actor.getRole()) || "STUDENT".equals(actor.getRole());
    }

    private boolean isParent(Actor actor) {
        return "R26".equals(actor.getRole()) || "PARENT".equals(actor.getRole());
    }

    private boolean isFaculty(Actor actor) {
        return "R10".equals(actor.getRole()) || "FACULTY".equals(actor.getRole());
    }

    private static boolean isKnownRequestType(String type) {
        return "LEAVE".equals(type) || "REINSTATE".equals(type) || "TRANSFER".equals(type) || "WITHDRAWAL".equals(type);
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
    private LifecycleException reject(int status, String code, String message) {
        if (status >= 500) {
            logger.error("stm04 rejected status={} code={} message={}", status, code, message);
        } else {
            logger.warn("stm04 rejected status={} code={} message={}", status, code, message);
        }
        return new LifecycleException(status, code, message);
    }

    private void log(Actor actor, String action, UUID subjectId) {
        logger.info("stm04 action={} tenantId={} subjectId={} correlationId={}",
                action, actor.getTenantId(), subjectId, actor.getCorrelationId());
        logger.audit(AuditEvent.builder()
                .action(action)
                .tenantId(actor.getTenantId() == null ? null : actor.getTenantId().toString())
                .principalId(actor.getUserId() == null ? null : actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("LIFECYCLE")
                .resourceId(subjectId == null ? null : subjectId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description(action)
                .build());
    }

    private static String statusKey(UUID tenantId, UUID studentId) {
        return tenantId + ":" + studentId;
    }

    private static String requestKey(UUID tenantId, UUID requestId) {
        return tenantId + ":" + requestId;
    }

    private static String ruleKey(UUID tenantId, String from, String to) {
        return tenantId + ":" + from + "->" + to;
    }

    private static String graduationKey(UUID tenantId, UUID studentId, UUID programId) {
        return tenantId + ":" + studentId + ":" + programId;
    }

    public static final class LifecycleStatus {
        public final UUID tenantId;
        public final UUID studentId;
        public String status;
        public Instant effectiveFrom;
        public Instant updatedAt;

        public LifecycleStatus(UUID tenantId, UUID studentId, String status, Instant effectiveFrom, Instant updatedAt) {
            this.tenantId = tenantId;
            this.studentId = studentId;
            this.status = status;
            this.effectiveFrom = effectiveFrom;
            this.updatedAt = updatedAt;
        }
    }

    public static final class HistoryEntry {
        public final UUID tenantId;
        public final UUID id;
        public final UUID studentId;
        public final String status;
        public final String action;
        public final String actorRole;
        public final Instant occurredAt;

        public HistoryEntry(UUID tenantId, UUID id, UUID studentId, String status, String action, String actorRole, Instant occurredAt) {
            this.tenantId = tenantId;
            this.id = id;
            this.studentId = studentId;
            this.status = status;
            this.action = action;
            this.actorRole = actorRole;
            this.occurredAt = occurredAt;
        }
    }

    public static final class LifecycleRequest {
        public final UUID tenantId;
        public final UUID id;
        public final UUID studentId;
        public final String requestType;
        public String status;
        public String reason;
        public final LocalDate effectiveDate;
        public UUID decidedBy;
        public Instant decidedAt;
        public final Instant createdAt;

        public LifecycleRequest(UUID tenantId, UUID id, UUID studentId, String requestType, String status, String reason,
                                LocalDate effectiveDate, UUID decidedBy, Instant decidedAt, Instant createdAt) {
            this.tenantId = tenantId;
            this.id = id;
            this.studentId = studentId;
            this.requestType = requestType;
            this.status = status;
            this.reason = reason;
            this.effectiveDate = effectiveDate;
            this.decidedBy = decidedBy;
            this.decidedAt = decidedAt;
            this.createdAt = createdAt;
        }
    }

    public static final class TransitionRule {
        public final UUID tenantId;
        public final UUID id;
        public final String fromStatus;
        public final String toStatus;
        public final boolean requiresApproval;
        public final boolean active;

        public TransitionRule(UUID tenantId, UUID id, String fromStatus, String toStatus, boolean requiresApproval, boolean active) {
            this.tenantId = tenantId;
            this.id = id;
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.requiresApproval = requiresApproval;
            this.active = active;
        }
    }

    public static final class GraduationRecord {
        public final UUID tenantId;
        public final UUID id;
        public final UUID studentId;
        public final UUID programId;
        public final LocalDate graduatedOn;
        public final String notes;
        public final Instant recordedAt;

        public GraduationRecord(UUID tenantId, UUID id, UUID studentId, UUID programId, LocalDate graduatedOn, String notes, Instant recordedAt) {
            this.tenantId = tenantId;
            this.id = id;
            this.studentId = studentId;
            this.programId = programId;
            this.graduatedOn = graduatedOn;
            this.notes = notes;
            this.recordedAt = recordedAt;
        }
    }
}
