package com.campx.academic.calendar.service;

import com.campx.academic.calendar.exception.*;
import com.campx.academic.calendar.model.CalendarModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core Domain Service for ACD-07 Academic Calendar Service.
 * Implements business rules for calendar lifecycle, terms, holidays, overrides,
 * validation, approval, publication, effective-date resolution, outbox and DLQ.
 */
public class CalendarDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CalendarDomainService.class);

    // =========================================================================
    // Core Domain State Stores (Thread-Safe Concurrent Storage)
    // =========================================================================

    private final Map<String, AcademicCalendar> calendars = new ConcurrentHashMap<>();
    private final Map<String, CalendarTerm> terms = new ConcurrentHashMap<>();
    private final Map<String, CalendarEvent> events = new ConcurrentHashMap<>();
    private final List<CalendarHistory> historyLogs = new CopyOnWriteArrayList<>();

    // Compound unique indexes
    // tenantId:institutionId:campusId:academicYear:calendarCode -> calendarId
    private final Map<String, String> calendarUniqueIndex = new ConcurrentHashMap<>();
    // tenantId:calendarId:termCode -> termId
    private final Map<String, String> termCodeUniqueIndex = new ConcurrentHashMap<>();
    // tenantId:calendarId:sequenceNo -> termId
    private final Map<String, String> termSeqUniqueIndex = new ConcurrentHashMap<>();
    // tenantId:calendarId:eventCode -> eventId
    private final Map<String, String> eventCodeUniqueIndex = new ConcurrentHashMap<>();
    // tenantId:campusId:academicYear -> current published calendarId
    private final Map<String, String> currentPublishedIndex = new ConcurrentHashMap<>();

    // Technical collections
    private final Map<String, OutboxEvent> outbox = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotency = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterQueue = new CopyOnWriteArrayList<>();
    private final List<CalendarAuditLog> auditLogs = new CopyOnWriteArrayList<>();
    private final Map<String, ApiKeyRecord> apiKeys = new ConcurrentHashMap<>();

    // Sequence generators
    private final AtomicLong calendarSeq = new AtomicLong(1000);
    private final AtomicLong termSeq = new AtomicLong(2000);
    private final AtomicLong eventSeq = new AtomicLong(3000);
    private final AtomicLong outboxSeq = new AtomicLong(5000);
    private final AtomicLong historySeq = new AtomicLong(6000);
    private final AtomicLong dlqSeq = new AtomicLong(7000);
    private final AtomicLong auditSeq = new AtomicLong(8000);

    private final CalendarValidationEngine validationEngine;
    private boolean failClosedOnDependencyOutage = false;

    public CalendarDomainService() {
        this(new CalendarValidationEngine());
    }

    public CalendarDomainService(CalendarValidationEngine validationEngine) {
        this.validationEngine = validationEngine != null ? validationEngine : new CalendarValidationEngine();
        seedDefaultApiKeys();
    }

    private void seedDefaultApiKeys() {
        ApiKeyRecord key1 = new ApiKeyRecord("KEY-READ-01", "CAMPX-CAL-SECRET-01", "TENANT-001", "READ");
        apiKeys.put(key1.getApiKey(), key1);
        ApiKeyRecord key2 = new ApiKeyRecord("KEY-ADMIN-01", "CAMPX-CAL-ADMIN-01", "TENANT-001", "READ_WRITE");
        apiKeys.put(key2.getApiKey(), key2);
    }

    // =========================================================================
    // 1. Calendar Setup & Management (US-001 to US-006)
    // =========================================================================

    public AcademicCalendar createCalendar(CreateCalendarRequest req, String tenantId, String userId, String userRole) {
        if (failClosedOnDependencyOutage) {
            throw new CalendarDependencyUnavailableException("Configuration service unavailable. Calendar setup blocked.");
        }
        if (req == null) {
            throw new CalendarBadRequestException("Request body cannot be null");
        }
        if (req.calendarCode == null || req.calendarCode.trim().isEmpty()) {
            throw new CalendarBadRequestException("calendarCode is mandatory (FR-01)");
        }
        if (req.name == null || req.name.trim().isEmpty()) {
            throw new CalendarBadRequestException("name is mandatory");
        }
        if (req.academicYear == null || req.academicYear.trim().isEmpty()) {
            throw new CalendarBadRequestException("academicYear is mandatory");
        }

        String tId = (tenantId != null && !tenantId.trim().isEmpty()) ? tenantId : "TENANT-001";
        String instId = (req.institutionId != null) ? req.institutionId : "INST-001";
        String campId = (req.campusId != null) ? req.campusId : "CAMPUS-001";
        String code = req.calendarCode.trim().toUpperCase();
        String year = req.academicYear.trim();

        // Enforce duplicate prevention (US-002)
        String uniqueKey = tId + ":" + instId + ":" + campId + ":" + year + ":" + code;
        if (calendarUniqueIndex.containsKey(uniqueKey)) {
            throw new DuplicateCalendarException("Calendar with code '" + code + "' already exists for year " + year + " in campus " + campId);
        }

        String calId = "CAL-" + calendarSeq.incrementAndGet();
        AcademicCalendar cal = new AcademicCalendar(calId, tId, instId, campId, year, code, req.name.trim(), req.timezone);
        cal.setDescription(req.description);
        cal.setEffectiveFrom(req.effectiveFrom);
        cal.setEffectiveTo(req.effectiveTo);
        if (req.policyConfig != null) {
            cal.setPolicyConfig(new HashMap<>(req.policyConfig));
        }
        cal.setCreatedBy(userId);
        cal.setUpdatedBy(userId);

        calendars.put(calId, cal);
        calendarUniqueIndex.put(uniqueKey, calId);

        recordHistory(calId, tId, "CREATE_CALENDAR", null, CalendarStatus.DRAFT, userId, userRole, "Calendar draft created", 1);
        recordAudit(tId, "CALENDAR_CREATED", userId, userRole, calId, "Created calendar draft " + code);
        publishOutboxEvent("AcademicCalendarCreated", calId, serializeCalendar(cal), tId);

        logger.info("[ACD-07] Created academic calendar {} with code {}", calId, code);
        return cal;
    }

    public AcademicCalendar getCalendar(String calendarId, String tenantId) {
        AcademicCalendar cal = calendars.get(calendarId);
        if (cal == null || (tenantId != null && !tenantId.equalsIgnoreCase(cal.getTenantId()))) {
            throw new CalendarNotFoundException("Academic calendar not found: " + calendarId);
        }
        return cal;
    }

    public List<AcademicCalendar> listCalendars(String tenantId, String campusId, String academicYear, CalendarStatus status) {
        String tId = tenantId != null ? tenantId : "TENANT-001";
        return calendars.values().stream()
                .filter(c -> tId.equalsIgnoreCase(c.getTenantId()))
                .filter(c -> campusId == null || campusId.equalsIgnoreCase(c.getCampusId()))
                .filter(c -> academicYear == null || academicYear.equalsIgnoreCase(c.getAcademicYear()))
                .filter(c -> status == null || status == c.getStatus())
                .sorted(Comparator.comparing(AcademicCalendar::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public AcademicCalendar updateCalendar(String calendarId, UpdateCalendarRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        // Immutability check (US-004, US-029)
        if (cal.getStatus() == CalendarStatus.PUBLISHED || cal.getStatus() == CalendarStatus.SUPERSEDED || cal.getStatus() == CalendarStatus.CANCELLED) {
            throw new InvalidCalendarStateException("Cannot modify calendar in state " + cal.getStatus() + ". Published versions are immutable.");
        }

        // Optimistic concurrency check (US-004, US-061)
        if (req.expectedVersion != null && req.expectedVersion != cal.getCurrentVersion()) {
            throw new CalendarVersionConflictException("Calendar version conflict: expected " + req.expectedVersion +
                    " but current version is " + cal.getCurrentVersion());
        }

        if (req.name != null && !req.name.trim().isEmpty()) cal.setName(req.name.trim());
        if (req.description != null) cal.setDescription(req.description.trim());
        if (req.timezone != null && !req.timezone.trim().isEmpty()) cal.setTimezone(req.timezone.trim());
        if (req.effectiveFrom != null) cal.setEffectiveFrom(req.effectiveFrom.trim());
        if (req.effectiveTo != null) cal.setEffectiveTo(req.effectiveTo.trim());
        if (req.policyConfig != null) cal.setPolicyConfig(new HashMap<>(req.policyConfig));

        cal.setUpdatedBy(userId);
        cal.setUpdatedAt(System.currentTimeMillis());

        recordHistory(calendarId, tenantId, "UPDATE_CALENDAR", cal.getStatus(), cal.getStatus(), userId, userRole, "Updated calendar draft", cal.getCurrentVersion());
        recordAudit(tenantId, "CALENDAR_UPDATED", userId, userRole, calendarId, "Updated calendar draft properties");
        return cal;
    }

    public AcademicCalendar cancelCalendar(String calendarId, String tenantId, String userId, String userRole, String reason) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        if (cal.getStatus() != CalendarStatus.DRAFT && cal.getStatus() != CalendarStatus.REJECTED) {
            throw new InvalidCalendarStateException("Only DRAFT or REJECTED calendars can be cancelled. Current state: " + cal.getStatus());
        }

        CalendarStatus fromStatus = cal.getStatus();
        cal.setStatus(CalendarStatus.CANCELLED);
        cal.setUpdatedBy(userId);
        cal.setUpdatedAt(System.currentTimeMillis());

        recordHistory(calendarId, tenantId, "CANCEL_CALENDAR", fromStatus, CalendarStatus.CANCELLED, userId, userRole,
                reason != null ? reason : "Cancelled by user", cal.getCurrentVersion());
        recordAudit(tenantId, "CALENDAR_CANCELLED", userId, userRole, calendarId, "Cancelled calendar: " + reason);
        return cal;
    }

    // =========================================================================
    // 2. Term Management (US-007 to US-011)
    // =========================================================================

    public CalendarTerm addTerm(String calendarId, AddTermRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        if (cal.getStatus() != CalendarStatus.DRAFT && cal.getStatus() != CalendarStatus.REJECTED) {
            throw new InvalidCalendarStateException("Terms can only be added to DRAFT or REJECTED calendars");
        }

        if (req == null || req.termCode == null || req.termCode.trim().isEmpty()) {
            throw new CalendarBadRequestException("termCode is required");
        }
        if (req.name == null || req.name.trim().isEmpty()) {
            throw new CalendarBadRequestException("term name is required");
        }
        if (req.startDate == null || req.endDate == null) {
            throw new CalendarBadRequestException("term startDate and endDate are required");
        }

        String tCode = req.termCode.trim().toUpperCase();
        String termKey = tenantId + ":" + calendarId + ":" + tCode;
        if (termCodeUniqueIndex.containsKey(termKey)) {
            throw new CalendarValidationException("Term code '" + tCode + "' already exists in calendar " + calendarId);
        }

        String seqKey = tenantId + ":" + calendarId + ":" + req.sequenceNo;
        if (termSeqUniqueIndex.containsKey(seqKey)) {
            throw new CalendarValidationException("Term sequenceNo " + req.sequenceNo + " already exists in calendar " + calendarId);
        }

        String termId = "TERM-" + termSeq.incrementAndGet();
        CalendarTerm term = new CalendarTerm(termId, tenantId, calendarId, tCode, req.name.trim(),
                req.sequenceNo, req.startDate.trim(), req.endDate.trim(),
                req.instructionalStartDate, req.instructionalEndDate);

        // Validate term overlaps against existing terms (US-008)
        List<CalendarTerm> existingTerms = getCalendarTerms(calendarId, tenantId);
        validationEngine.validateTermOverlap(term, existingTerms);

        terms.put(termId, term);
        termCodeUniqueIndex.put(termKey, termId);
        termSeqUniqueIndex.put(seqKey, termId);

        cal.setUpdatedAt(System.currentTimeMillis());
        recordHistory(calendarId, tenantId, "ADD_TERM", cal.getStatus(), cal.getStatus(), userId, userRole, "Added term " + tCode, cal.getCurrentVersion());
        recordAudit(tenantId, "TERM_ADDED", userId, userRole, termId, "Added term " + tCode + " to calendar " + calendarId);
        return term;
    }

    public CalendarTerm getTerm(String termId, String tenantId) {
        CalendarTerm t = terms.get(termId);
        if (t == null || (tenantId != null && !tenantId.equalsIgnoreCase(t.getTenantId()))) {
            throw new TermNotFoundException("Term not found: " + termId);
        }
        return t;
    }

    public List<CalendarTerm> getCalendarTerms(String calendarId, String tenantId) {
        String tId = tenantId != null ? tenantId : "TENANT-001";
        return terms.values().stream()
                .filter(t -> tId.equalsIgnoreCase(t.getTenantId()) && calendarId.equals(t.getCalendarId()))
                .sorted(Comparator.comparingInt(CalendarTerm::getSequenceNo))
                .collect(Collectors.toList());
    }

    public CalendarTerm updateTerm(String termId, UpdateTermRequest req, String tenantId, String userId, String userRole) {
        CalendarTerm term = getTerm(termId, tenantId);
        AcademicCalendar cal = getCalendar(term.getCalendarId(), tenantId);

        if (cal.getStatus() == CalendarStatus.PUBLISHED || cal.getStatus() == CalendarStatus.SUPERSEDED || cal.getStatus() == CalendarStatus.CANCELLED) {
            throw new InvalidCalendarStateException("Cannot modify terms of a published or cancelled calendar");
        }

        if (req.expectedVersion != null && req.expectedVersion != term.getVersion()) {
            throw new CalendarVersionConflictException("Term version conflict: expected " + req.expectedVersion +
                    " but current version is " + term.getVersion());
        }

        if (req.name != null && !req.name.trim().isEmpty()) term.setName(req.name.trim());
        if (req.startDate != null) term.setStartDate(req.startDate.trim());
        if (req.endDate != null) term.setEndDate(req.endDate.trim());
        if (req.instructionalStartDate != null) term.setInstructionalStartDate(req.instructionalStartDate.trim());
        if (req.instructionalEndDate != null) term.setInstructionalEndDate(req.instructionalEndDate.trim());

        // Revalidate overlap
        List<CalendarTerm> otherTerms = getCalendarTerms(term.getCalendarId(), tenantId);
        validationEngine.validateTermOverlap(term, otherTerms);

        term.setVersion(term.getVersion() + 1);
        term.setUpdatedAt(System.currentTimeMillis());

        recordAudit(tenantId, "TERM_UPDATED", userId, userRole, termId, "Updated term " + term.getTermCode());
        return term;
    }

    public CalendarTerm deactivateTerm(String termId, String tenantId, String userId, String userRole) {
        CalendarTerm term = getTerm(termId, tenantId);
        AcademicCalendar cal = getCalendar(term.getCalendarId(), tenantId);

        if (cal.getStatus() == CalendarStatus.PUBLISHED || cal.getStatus() == CalendarStatus.SUPERSEDED) {
            throw new InvalidCalendarStateException("Cannot deactivate terms in an immutable published calendar");
        }

        term.setStatus(TermStatus.INACTIVE);
        term.setUpdatedAt(System.currentTimeMillis());

        recordAudit(tenantId, "TERM_DEACTIVATED", userId, userRole, termId, "Deactivated term " + term.getTermCode());
        return term;
    }

    // =========================================================================
    // 3. Holiday & Event Management (US-012 to US-018)
    // =========================================================================

    public CalendarEvent addEvent(String calendarId, AddEventRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        if (cal.getStatus() == CalendarStatus.PUBLISHED || cal.getStatus() == CalendarStatus.SUPERSEDED || cal.getStatus() == CalendarStatus.CANCELLED) {
            throw new InvalidCalendarStateException("Cannot add events to a published or cancelled calendar directly");
        }

        if (req == null || req.eventCode == null || req.eventCode.trim().isEmpty()) {
            throw new CalendarBadRequestException("eventCode is required");
        }
        if (req.title == null || req.title.trim().isEmpty()) {
            throw new CalendarBadRequestException("event title is required");
        }
        if (req.startDate == null) {
            throw new CalendarBadRequestException("event startDate is required");
        }

        String eCode = req.eventCode.trim().toUpperCase();
        String eventKey = tenantId + ":" + calendarId + ":" + eCode;
        if (eventCodeUniqueIndex.containsKey(eventKey)) {
            throw new CalendarValidationException("Event code '" + eCode + "' already exists in calendar " + calendarId);
        }

        EventType type = EventType.valueOf(req.eventType != null ? req.eventType.toUpperCase() : "ACADEMIC");
        WorkingDayImpact impact = WorkingDayImpact.valueOf(req.workingDayImpact != null ? req.workingDayImpact.toUpperCase() : "NO_IMPACT");
        if (type == EventType.HOLIDAY) {
            impact = WorkingDayImpact.NON_WORKING;
        }

        String eventId = "EVT-" + eventSeq.incrementAndGet();
        CalendarEvent event = new CalendarEvent(eventId, tenantId, calendarId, req.termId, eCode,
                type, req.title.trim(), req.description, req.startDate.trim(), req.endDate != null ? req.endDate.trim() : req.startDate.trim(),
                req.allDay, impact);
        if (req.category != null) event.setCategory(req.category.trim());

        events.put(eventId, event);
        eventCodeUniqueIndex.put(eventKey, eventId);

        cal.setUpdatedAt(System.currentTimeMillis());

        if (type == EventType.HOLIDAY) {
            publishOutboxEvent("HolidayDeclared", eventId, serializeEvent(event), tenantId);
        } else {
            publishOutboxEvent("AcademicEventScheduled", eventId, serializeEvent(event), tenantId);
        }

        recordHistory(calendarId, tenantId, "ADD_EVENT", cal.getStatus(), cal.getStatus(), userId, userRole, "Added event " + eCode, cal.getCurrentVersion());
        recordAudit(tenantId, "EVENT_ADDED", userId, userRole, eventId, "Added event " + eCode + " (" + type + ")");
        return event;
    }

    public CalendarEvent getEvent(String eventId, String tenantId) {
        CalendarEvent e = events.get(eventId);
        if (e == null || (tenantId != null && !tenantId.equalsIgnoreCase(e.getTenantId()))) {
            throw new EventNotFoundException("Calendar event not found: " + eventId);
        }
        return e;
    }

    public List<CalendarEvent> getCalendarEvents(String calendarId, String tenantId, String fromDate, String toDate, String eventType, String termId) {
        String tId = tenantId != null ? tenantId : "TENANT-001";
        return events.values().stream()
                .filter(e -> tId.equalsIgnoreCase(e.getTenantId()) && calendarId.equals(e.getCalendarId()))
                .filter(e -> e.getStatus() == TermStatus.ACTIVE)
                .filter(e -> termId == null || termId.equals(e.getTermId()))
                .filter(e -> eventType == null || e.getEventType().name().equalsIgnoreCase(eventType))
                .filter(e -> fromDate == null || e.getEndDate().compareTo(fromDate) >= 0)
                .filter(e -> toDate == null || e.getStartDate().compareTo(toDate) <= 0)
                .sorted(Comparator.comparing(CalendarEvent::getStartDate))
                .collect(Collectors.toList());
    }

    public CalendarEvent updateEvent(String eventId, UpdateEventRequest req, String tenantId, String userId, String userRole) {
        CalendarEvent event = getEvent(eventId, tenantId);
        AcademicCalendar cal = getCalendar(event.getCalendarId(), tenantId);

        if (cal.getStatus() == CalendarStatus.PUBLISHED || cal.getStatus() == CalendarStatus.SUPERSEDED) {
            throw new InvalidCalendarStateException("Cannot modify events in an immutable published calendar");
        }

        if (req.expectedVersion != null && req.expectedVersion != event.getVersion()) {
            throw new CalendarVersionConflictException("Event version conflict: expected " + req.expectedVersion +
                    " but current version is " + event.getVersion());
        }

        if (req.title != null && !req.title.trim().isEmpty()) event.setTitle(req.title.trim());
        if (req.description != null) event.setDescription(req.description.trim());
        if (req.startDate != null) event.setStartDate(req.startDate.trim());
        if (req.endDate != null) event.setEndDate(req.endDate.trim());
        if (req.allDay != null) event.setAllDay(req.allDay);
        if (req.workingDayImpact != null) event.setWorkingDayImpact(WorkingDayImpact.valueOf(req.workingDayImpact.toUpperCase()));
        if (req.category != null) event.setCategory(req.category.trim());

        event.setVersion(event.getVersion() + 1);
        event.setUpdatedAt(System.currentTimeMillis());

        recordAudit(tenantId, "EVENT_UPDATED", userId, userRole, eventId, "Updated event " + event.getEventCode());
        return event;
    }

    public CalendarEvent deactivateEvent(String eventId, String tenantId, String userId, String userRole) {
        CalendarEvent event = getEvent(eventId, tenantId);
        AcademicCalendar cal = getCalendar(event.getCalendarId(), tenantId);

        if (cal.getStatus() == CalendarStatus.PUBLISHED || cal.getStatus() == CalendarStatus.SUPERSEDED) {
            throw new InvalidCalendarStateException("Cannot deactivate events in an immutable published calendar");
        }

        event.setStatus(TermStatus.INACTIVE);
        event.setUpdatedAt(System.currentTimeMillis());

        recordAudit(tenantId, "EVENT_DEACTIVATED", userId, userRole, eventId, "Deactivated event " + event.getEventCode());
        return event;
    }

    // =========================================================================
    // 4. Validation Engine & Change Impact Analysis (US-019 to US-022)
    // =========================================================================

    public ValidationReport validateCalendar(String calendarId, String tenantId) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);
        List<CalendarTerm> calTerms = getCalendarTerms(calendarId, tenantId);
        List<CalendarEvent> calEvents = getCalendarEvents(calendarId, tenantId, null, null, null, null);
        return validationEngine.validateCalendar(cal, calTerms, calEvents);
    }

    public Map<String, Object> analyzeImpact(String calendarId, String tenantId) {
        AcademicCalendar proposed = getCalendar(calendarId, tenantId);
        AcademicCalendar current = getCurrentPublishedCalendar(proposed.getCampusId(), proposed.getAcademicYear(), tenantId);

        List<CalendarTerm> propTerms = getCalendarTerms(calendarId, tenantId);
        List<CalendarEvent> propEvents = getCalendarEvents(calendarId, tenantId, null, null, null, null);

        List<CalendarTerm> pubTerms = current != null ? getCalendarTerms(current.getId(), tenantId) : Collections.emptyList();
        List<CalendarEvent> pubEvents = current != null ? getCalendarEvents(current.getId(), tenantId, null, null, null, null) : Collections.emptyList();

        return validationEngine.analyzeImpact(proposed, current, propTerms, pubTerms, propEvents, pubEvents);
    }

    // =========================================================================
    // 5. Governance: Submit, Approval & Publication Pipeline (US-023 to US-029)
    // =========================================================================

    public AcademicCalendar submitCalendar(String calendarId, SubmitCalendarRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        if (cal.getStatus() != CalendarStatus.DRAFT && cal.getStatus() != CalendarStatus.REJECTED) {
            throw new InvalidCalendarStateException("Only DRAFT or REJECTED calendars can be submitted. Current: " + cal.getStatus());
        }

        // Must pass validation without blocking errors (US-023)
        ValidationReport report = validateCalendar(calendarId, tenantId);
        if (!report.valid || report.blockingCount > 0) {
            throw new CalendarValidationException("Calendar cannot be submitted with " + report.blockingCount + " blocking validation errors.");
        }

        CalendarStatus fromStatus = cal.getStatus();
        cal.setStatus(CalendarStatus.SUBMITTED);
        cal.setWorkflowRef("WF-SUB-" + System.currentTimeMillis());
        cal.setUpdatedBy(userId);
        cal.setUpdatedAt(System.currentTimeMillis());

        recordHistory(calendarId, tenantId, "SUBMIT_CALENDAR", fromStatus, CalendarStatus.SUBMITTED, userId, userRole,
                req != null ? req.reason : "Submitted for registrar approval", cal.getCurrentVersion());
        recordAudit(tenantId, "CALENDAR_SUBMITTED", userId, userRole, calendarId, "Submitted calendar for approval");
        return cal;
    }

    public AcademicCalendar decideApproval(String calendarId, ApprovalRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        if (cal.getStatus() != CalendarStatus.SUBMITTED) {
            throw new InvalidCalendarStateException("Only SUBMITTED calendars can be approved or rejected. Current: " + cal.getStatus());
        }

        if (req == null || req.decision == null) {
            throw new CalendarBadRequestException("decision (APPROVED or REJECTED) is mandatory");
        }

        String decision = req.decision.trim().toUpperCase();
        CalendarStatus toStatus;
        if ("APPROVED".equals(decision)) {
            toStatus = CalendarStatus.APPROVED;
            cal.setApprovedBy(userId);
            cal.setApprovedAt(System.currentTimeMillis());
            cal.setRejectionReason(null);
        } else if ("REJECTED".equals(decision)) {
            toStatus = CalendarStatus.REJECTED;
            cal.setRejectionReason(req.reason != null ? req.reason : "Rejected by registrar");
        } else {
            throw new CalendarBadRequestException("Invalid decision: " + decision + ". Allowed values: APPROVED, REJECTED");
        }

        cal.setStatus(toStatus);
        cal.setUpdatedBy(userId);
        cal.setUpdatedAt(System.currentTimeMillis());

        recordHistory(calendarId, tenantId, "APPROVAL_DECISION", CalendarStatus.SUBMITTED, toStatus, userId, userRole,
                req.reason != null ? req.reason : decision, cal.getCurrentVersion());
        recordAudit(tenantId, "CALENDAR_DECISION_" + decision, userId, userRole, calendarId, "Calendar decided: " + decision);
        return cal;
    }

    public synchronized AcademicCalendar publishCalendar(String calendarId, PublishCalendarRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        // Publication prerequisite: Must be APPROVED (US-026, FR-06)
        if (cal.getStatus() != CalendarStatus.APPROVED) {
            throw new InvalidCalendarStateException("Only APPROVED calendars can be published. Current state: " + cal.getStatus());
        }

        // Optimistic concurrency check (US-026)
        if (req != null && req.expectedVersion != null && req.expectedVersion != cal.getCurrentVersion()) {
            throw new CalendarVersionConflictException("Publication version conflict: expected " + req.expectedVersion +
                    " but current version is " + cal.getCurrentVersion());
        }

        // Validate complete calendar
        ValidationReport report = validateCalendar(calendarId, tenantId);
        if (!report.valid || report.blockingCount > 0) {
            throw new CalendarValidationException("Cannot publish calendar with " + report.blockingCount + " blocking errors");
        }

        if (req != null && req.effectiveFrom != null) cal.setEffectiveFrom(req.effectiveFrom.trim());
        if (req != null && req.effectiveTo != null) cal.setEffectiveTo(req.effectiveTo.trim());

        String currentKey = cal.getTenantId() + ":" + cal.getCampusId() + ":" + cal.getAcademicYear();
        String previousCurrentId = currentPublishedIndex.get(currentKey);

        // Supersede previous published calendar atomically (US-027, FR-08)
        if (previousCurrentId != null && !previousCurrentId.equals(calendarId)) {
            AcademicCalendar prev = calendars.get(previousCurrentId);
            if (prev != null && prev.getStatus() == CalendarStatus.PUBLISHED) {
                prev.setStatus(CalendarStatus.SUPERSEDED);
                prev.setUpdatedAt(System.currentTimeMillis());
                recordHistory(prev.getId(), tenantId, "CALENDAR_SUPERSEDED", CalendarStatus.PUBLISHED, CalendarStatus.SUPERSEDED,
                        userId, userRole, "Superseded by newly published calendar " + calendarId, prev.getCurrentVersion());
                recordAudit(tenantId, "CALENDAR_SUPERSEDED", userId, userRole, prev.getId(), "Superseded by " + calendarId);
            }
        }

        // Mark as PUBLISHED
        cal.setStatus(CalendarStatus.PUBLISHED);
        cal.setPublishedBy(userId);
        cal.setPublishedAt(System.currentTimeMillis());
        cal.setUpdatedBy(userId);
        cal.setUpdatedAt(System.currentTimeMillis());

        currentPublishedIndex.put(currentKey, calendarId);

        publishOutboxEvent("AcademicCalendarPublished", calendarId, serializeCalendar(cal), tenantId);
        recordHistory(calendarId, tenantId, "PUBLISH_CALENDAR", CalendarStatus.APPROVED, CalendarStatus.PUBLISHED,
                userId, userRole, "Published as authoritative academic calendar", cal.getCurrentVersion());
        recordAudit(tenantId, "CALENDAR_PUBLISHED", userId, userRole, calendarId, "Published calendar version " + cal.getCurrentVersion());

        logger.info("[ACD-07] Published academic calendar {} version {} for campus {} year {}",
                calendarId, cal.getCurrentVersion(), cal.getCampusId(), cal.getAcademicYear());
        return cal;
    }

    public synchronized AcademicCalendar cloneVersion(String calendarId, CloneVersionRequest req, String tenantId, String userId, String userRole) {
        AcademicCalendar source = getCalendar(calendarId, tenantId);

        if (source.getStatus() != CalendarStatus.PUBLISHED && source.getStatus() != CalendarStatus.SUPERSEDED) {
            throw new InvalidCalendarStateException("Can only create new version from a PUBLISHED or SUPERSEDED calendar");
        }

        int nextVersion = source.getCurrentVersion() + 1;
        String newCalId = "CAL-" + calendarSeq.incrementAndGet();

        AcademicCalendar newCal = new AcademicCalendar(source, nextVersion, newCalId);
        newCal.setCreatedBy(userId);
        newCal.setUpdatedBy(userId);

        calendars.put(newCalId, newCal);

        // Clone terms
        List<CalendarTerm> sourceTerms = getCalendarTerms(calendarId, tenantId);
        for (CalendarTerm st : sourceTerms) {
            String newTermId = "TERM-" + termSeq.incrementAndGet();
            CalendarTerm nt = new CalendarTerm(st, newCalId, newTermId);
            terms.put(newTermId, nt);
            termCodeUniqueIndex.put(tenantId + ":" + newCalId + ":" + nt.getTermCode(), newTermId);
            termSeqUniqueIndex.put(tenantId + ":" + newCalId + ":" + nt.getSequenceNo(), newTermId);
        }

        // Clone events
        List<CalendarEvent> sourceEvents = getCalendarEvents(calendarId, tenantId, null, null, null, null);
        for (CalendarEvent se : sourceEvents) {
            String newEventId = "EVT-" + eventSeq.incrementAndGet();
            CalendarEvent ne = new CalendarEvent(se, newCalId, newEventId);
            events.put(newEventId, ne);
            eventCodeUniqueIndex.put(tenantId + ":" + newCalId + ":" + ne.getEventCode(), newEventId);
        }

        recordHistory(newCalId, tenantId, "CLONE_VERSION", null, CalendarStatus.DRAFT, userId, userRole,
                req != null ? req.reason : "Created new draft version from " + calendarId, nextVersion);
        recordAudit(tenantId, "CALENDAR_VERSION_CLONED", userId, userRole, newCalId, "Cloned version " + nextVersion + " from " + calendarId);
        return newCal;
    }

    // =========================================================================
    // 6. Effective Calendar & Effective Date Resolution (US-030 to US-032)
    // =========================================================================

    public AcademicCalendar getCurrentPublishedCalendar(String campusId, String academicYear, String tenantId) {
        String tId = tenantId != null ? tenantId : "TENANT-001";
        String campId = campusId != null ? campusId : "CAMPUS-001";
        String year = academicYear != null ? academicYear : "2026-2027";
        String key = tId + ":" + campId + ":" + year;

        String calId = currentPublishedIndex.get(key);
        if (calId != null) {
            AcademicCalendar c = calendars.get(calId);
            if (c != null && c.getStatus() == CalendarStatus.PUBLISHED) {
                return c;
            }
        }

        // Fallback: search for any PUBLISHED matching
        return calendars.values().stream()
                .filter(c -> tId.equalsIgnoreCase(c.getTenantId()))
                .filter(c -> campId.equalsIgnoreCase(c.getCampusId()))
                .filter(c -> year.equalsIgnoreCase(c.getAcademicYear()))
                .filter(c -> c.getStatus() == CalendarStatus.PUBLISHED)
                .findFirst()
                .orElse(null);
    }

    public EffectiveDateResolution resolveEffectiveDate(String calendarId, String operatingDate, String tenantId) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);

        EffectiveDateResolution res = new EffectiveDateResolution();
        res.operatingDate = operatingDate;
        res.calendarId = calendarId;
        res.calendarVersion = cal.getCurrentVersion();

        // 1. Find Term
        List<CalendarTerm> calTerms = getCalendarTerms(calendarId, tenantId);
        CalendarTerm matchedTerm = null;
        for (CalendarTerm t : calTerms) {
            if (t.getStatus() == TermStatus.ACTIVE && operatingDate.compareTo(t.getStartDate()) >= 0 && operatingDate.compareTo(t.getEndDate()) <= 0) {
                matchedTerm = t;
                break;
            }
        }

        if (matchedTerm != null) {
            res.termId = matchedTerm.getId();
            res.termCode = matchedTerm.getTermCode();
            boolean isInst = (matchedTerm.getInstructionalStartDate() != null && matchedTerm.getInstructionalEndDate() != null
                    && operatingDate.compareTo(matchedTerm.getInstructionalStartDate()) >= 0
                    && operatingDate.compareTo(matchedTerm.getInstructionalEndDate()) <= 0);
            res.isInstructionalDay = isInst;
        } else {
            res.isInstructionalDay = false;
        }

        // 2. Find Events for Date
        List<CalendarEvent> dayEvents = getCalendarEvents(calendarId, tenantId, operatingDate, operatingDate, null, null);
        CalendarEvent holiday = null;
        CalendarEvent override = null;

        for (CalendarEvent ev : dayEvents) {
            if (ev.getEventType() == EventType.HOLIDAY) {
                holiday = ev;
            } else if (ev.getEventType() == EventType.WORKING_DAY_OVERRIDE) {
                override = ev;
            }
        }

        // 3. Precedence: Working Day Override > Holiday > Default Term Day
        if (override != null) {
            if (override.getWorkingDayImpact() == WorkingDayImpact.WORKING) {
                res.workingDayStatus = "OVERRIDE_WORKING";
                res.effectivePrecedenceReason = "Working day override: " + override.getTitle();
            } else {
                res.workingDayStatus = "OVERRIDE_NON_WORKING";
                res.effectivePrecedenceReason = "Non-working override: " + override.getTitle();
            }
        } else if (holiday != null) {
            res.isHoliday = true;
            res.holidayTitle = holiday.getTitle();
            res.workingDayStatus = "NON_WORKING";
            res.effectivePrecedenceReason = "Declared institutional holiday: " + holiday.getTitle();
        } else if (matchedTerm != null && res.isInstructionalDay) {
            res.workingDayStatus = "WORKING";
            res.effectivePrecedenceReason = "Standard instructional term day";
        } else {
            res.workingDayStatus = "NON_INSTRUCTIONAL";
            res.effectivePrecedenceReason = "Outside instructional term dates";
        }

        return res;
    }

    // =========================================================================
    // 7. History, Reporting, Views & Analytics (US-033 to US-035, US-050 to US-052)
    // =========================================================================

    public List<CalendarHistory> getCalendarHistory(String calendarId, String tenantId) {
        String tId = tenantId != null ? tenantId : "TENANT-001";
        return historyLogs.stream()
                .filter(h -> tId.equalsIgnoreCase(h.getTenantId()) && calendarId.equals(h.getCalendarId()))
                .sorted(Comparator.comparingLong(CalendarHistory::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public CalendarAnalytics getCalendarAnalytics(String calendarId, String tenantId) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);
        List<CalendarTerm> calTerms = getCalendarTerms(calendarId, tenantId);
        List<CalendarEvent> calEvents = getCalendarEvents(calendarId, tenantId, null, null, null, null);

        CalendarAnalytics an = new CalendarAnalytics();
        an.calendarId = calendarId;
        an.totalTerms = calTerms.size();
        an.totalEvents = calEvents.size();
        an.totalHolidays = (int) calEvents.stream().filter(e -> e.getEventType() == EventType.HOLIDAY).count();
        an.workingDayOverrides = (int) calEvents.stream().filter(e -> e.getEventType() == EventType.WORKING_DAY_OVERRIDE).count();

        // Compute approximate instructional days
        int totalInstDays = 0;
        for (CalendarTerm t : calTerms) {
            if (t.getInstructionalStartDate() != null && t.getInstructionalEndDate() != null) {
                try {
                    LocalDate s = LocalDate.parse(t.getInstructionalStartDate());
                    LocalDate e = LocalDate.parse(t.getInstructionalEndDate());
                    totalInstDays += (int) ChronoUnit.DAYS.between(s, e) + 1;
                } catch (Exception ignored) {}
            }
        }
        an.totalInstructionalDays = Math.max(0, totalInstDays - an.totalHolidays);
        return an;
    }

    public Map<String, Object> exportCalendar(String calendarId, String format, String tenantId, String userId, String userRole) {
        AcademicCalendar cal = getCalendar(calendarId, tenantId);
        List<CalendarTerm> calTerms = getCalendarTerms(calendarId, tenantId);
        List<CalendarEvent> calEvents = getCalendarEvents(calendarId, tenantId, null, null, null, null);

        recordAudit(tenantId, "CALENDAR_EXPORTED", userId, userRole, calendarId, "Exported format: " + format);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("calendarId", cal.getId());
        export.put("calendarCode", cal.getCalendarCode());
        export.put("academicYear", cal.getAcademicYear());
        export.put("format", format != null ? format.toUpperCase() : "JSON");
        export.put("exportedAt", System.currentTimeMillis());
        export.put("exportedBy", userId);
        export.put("calendar", cal);
        export.put("terms", calTerms);
        export.put("events", calEvents);
        return export;
    }

    // =========================================================================
    // 8. Event Consumption, Idempotency & DLQ (US-041 to US-048)
    // =========================================================================

    public boolean consumeEvent(Map<String, Object> event) {
        if (event == null || !event.containsKey("eventId")) {
            return false;
        }
        String eventId = String.valueOf(event.get("eventId"));
        String eventType = String.valueOf(event.get("eventType"));

        // Idempotent deduplication (US-046)
        if (idempotency.containsKey("CONSUME:" + eventId)) {
            logger.info("[ACD-07] Duplicate event skipped: {}", eventId);
            return true;
        }

        try {
            if ("CampusUpdated".equalsIgnoreCase(eventType)) {
                // US-047: refresh referenced campus context
                logger.info("[ACD-07] Consumed CampusUpdated event: {}", eventId);
            } else if ("InstitutionConfigurationChanged".equalsIgnoreCase(eventType)) {
                // US-048: refresh institutional calendar policies
                logger.info("[ACD-07] Consumed InstitutionConfigurationChanged event: {}", eventId);
            } else {
                logger.warn("[ACD-07] Unrecognized external event type: {}", eventType);
            }

            idempotency.put("CONSUME:" + eventId, new IdempotencyRecord("CONSUME:" + eventId, "SYSTEM", eventId, 200, "PROCESSED"));
            return true;
        } catch (Exception ex) {
            logger.error("[ACD-07] Error processing event {}: {}", eventId, ex.getMessage());
            DeadLetterEvent dlq = new DeadLetterEvent("DLQ-" + dlqSeq.incrementAndGet(), eventId, eventType,
                    String.valueOf(event), ex.getMessage(), 3);
            deadLetterQueue.add(dlq);
            return false;
        }
    }

    // =========================================================================
    // Internal Helper & Persistence Methods
    // =========================================================================

    private void recordHistory(String calendarId, String tenantId, String action,
                               CalendarStatus from, CalendarStatus to, String actorId, String actorRole, String reason, int version) {
        CalendarHistory h = new CalendarHistory("HIST-" + historySeq.incrementAndGet(), tenantId, calendarId, action,
                from, to, actorId, actorRole, reason, LogContext.getTraceId(), version);
        historyLogs.add(h);
    }

    private void recordAudit(String tenantId, String action, String actorId, String actorRole, String resId, String detail) {
        CalendarAuditLog log = new CalendarAuditLog("AUD-" + auditSeq.incrementAndGet(), tenantId, action,
                actorId, actorRole, resId, detail, LogContext.getTraceId());
        auditLogs.add(log);
    }

    private void publishOutboxEvent(String eventType, String aggregateId, String payload, String tenantId) {
        String eventId = "EVT-OUT-" + outboxSeq.incrementAndGet();
        OutboxEvent event = new OutboxEvent(eventId, aggregateId, eventType, payload, tenantId);
        outbox.put(eventId, event);
    }

    public ApiKeyRecord validateApiKey(String apiKey, String tenantId) {
        ApiKeyRecord rec = apiKeys.get(apiKey);
        if (rec != null && rec.isActive()) {
            if (tenantId == null || tenantId.equalsIgnoreCase(rec.getTenantId())) {
                return rec;
            }
        }
        return null;
    }

    // Helper JSON serializations
    private String serializeCalendar(AcademicCalendar c) {
        return "{\"calendarId\":\"" + c.getId() + "\",\"calendarCode\":\"" + c.getCalendarCode() +
                "\",\"academicYear\":\"" + c.getAcademicYear() + "\",\"status\":\"" + c.getStatus() +
                "\",\"version\":" + c.getCurrentVersion() + "}";
    }

    private String serializeEvent(CalendarEvent e) {
        return "{\"eventId\":\"" + e.getId() + "\",\"calendarId\":\"" + e.getCalendarId() +
                "\",\"eventCode\":\"" + e.getEventCode() + "\",\"eventType\":\"" + e.getEventType() +
                "\",\"title\":\"" + escape(e.getTitle()) + "\",\"startDate\":\"" + e.getStartDate() +
                "\",\"endDate\":\"" + e.getEndDate() + "\"}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }

    // Getters for Observability Metrics
    public int getCalendarCount() { return calendars.size(); }
    public int getTermCount() { return terms.size(); }
    public int getEventCount() { return events.size(); }
    public int getHolidayCount() {
        return (int) events.values().stream().filter(e -> e.getEventType() == EventType.HOLIDAY).count();
    }
    public int getPendingOutboxCount() {
        return (int) outbox.values().stream().filter(e -> "PENDING".equals(e.getStatus())).count();
    }
    public int getDlqCount() { return deadLetterQueue.size(); }

    public void setFailClosedOnDependencyOutage(boolean v) {
        this.failClosedOnDependencyOutage = v;
    }
}
