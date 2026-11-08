package com.campx.guardian;

import com.campx.guardian.intelligence.GuardianIntelligenceService;
import com.campx.guardian.persistence.GuardianPersistence;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STM-05 guardian authority.
 * <p>
 * A registrar, including a college or institution administrator, adds a parent or guardian
 * for a student in the same tenant. Students cannot create the link. The relationship is stored
 * in {@code stu.guardianships}. A successful add publishes {@code student.guardian.linked.v1}
 * for the Intelligence Layer.
 */
public class GuardianDomainService {

    public static final String GUARDIAN_LINKED = "student.guardian.linked.v1";
    public static final String GUARDIAN_UPDATED = "student.guardian.updated.v1";
    public static final String GUARDIAN_EVIDENCE_SUBMITTED = "student.guardian.evidence_submitted.v1";
    public static final String GUARDIAN_EVIDENCE_REVIEWED = "student.guardian.evidence_reviewed.v1";
    public static final String GUARDIAN_DEACTIVATED = "student.guardian.deactivated.v1";
    public static final String GUARDIAN_STATE_CHANGED = "student.guardian.state_changed.v1";
    public static final String EMERGENCY_CONTACT_CHANGED = "student.emergency_contact.changed.v1";
    public static final String GUARDIAN_ACCESS_GRANTED = "student.guardian.access_granted.v1";
    public static final String GUARDIAN_ACCESS_PERIOD_SET = "student.guardian.access_period_set.v1";
    public static final String GUARDIAN_ACCESS_EXPIRED = "student.guardian.access_expired.v1";
    public static final String GUARDIAN_ACCESS_REVOKED = "student.guardian.access_revoked.v1";
    public static final String GUARDIAN_AUTHORIZED_VIEW = "student.guardian.authorized_view.v1";
    public static final String GUARDIAN_HISTORY_REVIEWED = "student.guardian.history_reviewed.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    private static final java.util.Set<String> EVIDENCE_TYPES = new java.util.HashSet<String>(java.util.Arrays.asList(
            "BIRTH_CERTIFICATE", "AADHAAR", "PASSPORT", "COURT_ORDER"));

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GuardianDomainService.class);

    private final Clock clock;
    private final GuardianPersistence persistence;
    private final GuardianIntelligenceService intelligenceService;
    private final Map<String, List<GuardianLink>> links = new ConcurrentHashMap<String, List<GuardianLink>>();
    private final Map<UUID, EvidenceRecord> pendingEvidence = new ConcurrentHashMap<UUID, EvidenceRecord>();
    private final Map<UUID, Boolean> verifiedEvidence = new ConcurrentHashMap<UUID, Boolean>();
    private final Map<UUID, AccessWindow> accessWindows = new ConcurrentHashMap<UUID, AccessWindow>();
    private final Map<String, List<EmergencyContact>> emergencyContacts = new ConcurrentHashMap<String, List<EmergencyContact>>();
    private final List<HistoryEntry> historyEntries = Collections.synchronizedList(new ArrayList<HistoryEntry>());

    public GuardianDomainService() {
        this(Clock.systemUTC(), null, null);
    }

    public GuardianDomainService(Clock clock, GuardianPersistence persistence, GuardianIntelligenceService intelligenceService) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.persistence = persistence;
        this.intelligenceService = intelligenceService == null ? new GuardianIntelligenceService() : intelligenceService;
    }

    public boolean isPersistent() {
        return persistence != null;
    }

    /**
     * Adds a parent or guardian for a student (STM-05-US-001).
     *
     * @throws GuardianException 403 when the caller is not a registrar
     * @throws GuardianException 422 when the student, name, or relationship is missing
     */
    public GuardianLink addGuardian(Actor actor, UUID studentId, String firstName, String lastName,
                                    String relationship, String email, String phone, boolean primary) {
        requireRegistrar(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student record id is required");
        }
        requireText(firstName, "firstName");
        requireText(relationship, "relationship");
        Instant now = clock.instant();
        GuardianLink link = new GuardianLink(actor.getTenantId(), UUID.randomUUID(), studentId, UUID.randomUUID(),
                firstName.trim(), lastName == null ? "" : lastName.trim(), relationship.trim().toUpperCase(),
                email == null ? "" : email.trim(), phone == null ? "" : phone.trim(), primary, "ACTIVE", now);
        String key = statusKey(actor.getTenantId(), studentId);
        List<GuardianLink> rows = links.get(key);
        if (rows == null) {
            rows = Collections.synchronizedList(new ArrayList<GuardianLink>());
            links.put(key, rows);
        }
        rows.add(link);
        if (persistence != null) {
            persistence.saveGuardian(actor, link);
        }
        log(actor, "guardian.linked", link.id);
        intelligenceService.publishGuardianLinked(actor, link);
        return link;
    }

    /**
     * Updates the relationship and primary flag on an existing guardianship (STM-05-US-002).
     * Contact details stay on {@code core.persons}. This method does not write that table.
     *
     * @throws GuardianException 403 when the caller cannot update this student's guardian
     * @throws GuardianException 404 when the guardianship is not in the caller's tenant
     */
    public GuardianLink updateGuardian(Actor actor, UUID guardianshipId, String relationship, boolean primary) {
        requireTenant(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(relationship, "relationship");
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(current.studentId)) {
            throw reject(403, "GUARDIAN_UPDATE_FORBIDDEN", "Students may update only their own guardians");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "GUARDIAN_UPDATE_FORBIDDEN", "Updating a guardian requires the student or a registrar");
        }
        GuardianLink updated = new GuardianLink(current.tenantId, current.id, current.studentId, current.guardianPersonId,
                current.firstName, current.lastName, relationship.trim().toUpperCase(), current.email, current.phone,
                primary, current.status, clock.instant());
        replaceLink(current, updated);
        if (persistence != null) {
            persistence.updateGuardian(actor, updated);
        }
        log(actor, "guardian.updated", updated.id);
        intelligenceService.publishGuardianUpdated(actor, current, updated);
        return updated;
    }

    /**
     * Queues relationship evidence as a document reference (STM-05-US-003).
     * The file itself is not copied. The document row stays {@code SUBMITTED} for review.
     *
     * @throws GuardianException 422 when the document type is missing or not allowed
     * @throws GuardianException 404 when the guardianship is not in the caller's tenant
     */
    public EvidenceRecord submitEvidence(Actor actor, UUID guardianshipId, String documentType) {
        requireTenant(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(documentType, "documentType");
        String type = documentType.trim().toUpperCase().replace(' ', '_');
        if (!EVIDENCE_TYPES.contains(type)) {
            throw reject(422, "DOCUMENT_TYPE_UNSUPPORTED", "Document type is not accepted for guardian evidence");
        }
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(current.studentId)) {
            throw reject(403, "GUARDIAN_UPDATE_FORBIDDEN", "Students may update only their own guardians");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "GUARDIAN_UPDATE_FORBIDDEN", "Submitting guardian evidence requires the student or a registrar");
        }
        EvidenceRecord record = new EvidenceRecord(UUID.randomUUID(), UUID.randomUUID(), current.id, type, "SUBMITTED");
        if (persistence != null) {
            record = persistence.saveEvidence(actor, current, type);
        } else {
            pendingEvidence.put(current.id, record);
        }
        log(actor, "guardian.evidence_submitted", record.guardianshipId);
        recordHistory(actor, current, "CLAIM", "", type);
        intelligenceService.publishEvidenceSubmitted(actor, record);
        return record;
    }

    /**
     * Records a reviewer's verify or reject decision (STM-05-US-004).
     * The document status is stored as {@code APPROVED} for a verified claim and {@code REJECTED} otherwise.
     * The database status check does not allow the word VERIFIED. This decision does not grant student-data access.
     *
     * @throws GuardianException 403 when the reviewer is the guardian person, or is not a registrar
     * @throws GuardianException 404 when no submitted evidence is waiting
     */
    public ReviewDecision reviewEvidence(Actor actor, UUID guardianshipId, String decision, String reasonCode) {
        requireRegistrar(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(decision, "decision");
        requireText(reasonCode, "reasonCode");
        String normalizedDecision = decision.trim().toUpperCase();
        String normalizedReason = reasonCode.trim().toUpperCase();
        if (!"VERIFY".equals(normalizedDecision) && !"REJECT".equals(normalizedDecision)) {
            throw reject(422, "DECISION_INVALID", "Decision must be VERIFY or REJECT");
        }
        if (!"IDENTITY_CONFIRMED".equals(normalizedReason) && !"DOCUMENT_MISMATCH".equals(normalizedReason)
                && !"DOCUMENT_UNREADABLE".equals(normalizedReason) && !"RELATIONSHIP_NOT_PROVEN".equals(normalizedReason)) {
            throw reject(422, "REASON_INVALID", "Reason code is not accepted");
        }
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (actor.getUserId() != null && actor.getUserId().equals(current.guardianPersonId)) {
            throw reject(403, "REVIEWER_IS_PARTY", "A reviewer cannot decide a relationship they are party to");
        }
        String storedStatus = "VERIFY".equals(normalizedDecision) ? "APPROVED" : "REJECTED";
        String outcome = "VERIFY".equals(normalizedDecision) ? "VERIFIED" : "REJECTED";
        UUID documentId;
        if (persistence != null) {
            documentId = persistence.reviewEvidence(actor, current, storedStatus);
        } else {
            EvidenceRecord pending = pendingEvidence.remove(guardianshipId);
            if (pending == null) {
                throw reject(404, "EVIDENCE_NOT_PENDING", "No submitted guardian evidence is waiting for review");
            }
            documentId = pending.documentId;
            if ("VERIFY".equals(normalizedDecision)) {
                verifiedEvidence.put(guardianshipId, Boolean.TRUE);
            } else {
                verifiedEvidence.remove(guardianshipId);
            }
        }
        ReviewDecision result = new ReviewDecision(documentId, current.id, outcome, storedStatus, normalizedReason);
        logger.info("stm05 review outcome={} reasonCode={} guardianshipId={} reviewer={}",
                outcome, normalizedReason, current.id, actor.getUserId());
        log(actor, "guardian.evidence_reviewed", current.id);
        recordHistory(actor, current, "VERIFICATION", "SUBMITTED", storedStatus);
        intelligenceService.publishEvidenceReviewed(actor, result);
        return result;
    }

    /**
     * Ends an active guardianship without deleting the row (STM-05-US-005).
     * A student may end only their own relationship, and only with reason {@code STUDENT_REQUEST}.
     * A registrar may end it for a dispute. Academic, fee, and payment flags are cleared with the status change.
     * The notice is recorded on the audit and intelligence contract. No inbox delivery is claimed.
     *
     * @throws GuardianException 403 when the caller cannot end this relationship
     * @throws GuardianException 409 when the relationship is already ended
     * @throws GuardianException 422 when the relationship is not active or the reason does not match the caller
     */
    public Deactivation deactivateGuardian(Actor actor, UUID guardianshipId, String reasonCode) {
        requireTenant(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(reasonCode, "reasonCode");
        String normalizedReason = reasonCode.trim().toUpperCase();
        boolean studentCaller = isStudent(actor);
        boolean registrarCaller = isRegistrar(actor);
        if (!studentCaller && !registrarCaller) {
            throw reject(403, "GUARDIAN_DEACTIVATE_FORBIDDEN", "Ending a guardian relationship requires the student or a registrar");
        }
        if (studentCaller && !"STUDENT_REQUEST".equals(normalizedReason)) {
            throw reject(422, "REASON_NOT_ALLOWED", "A student deactivation must use reason STUDENT_REQUEST");
        }
        if (registrarCaller && !"DISPUTE".equals(normalizedReason)) {
            throw reject(422, "REASON_NOT_ALLOWED", "An institution deactivation must use reason DISPUTE");
        }
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (studentCaller && !actor.subjectStudentId().equals(current.studentId)) {
            throw reject(403, "GUARDIAN_DEACTIVATE_FORBIDDEN", "Students may end only their own guardian relationships");
        }
        if ("ENDED".equals(current.status)) {
            throw reject(409, "GUARDIAN_ALREADY_ENDED", "This guardian relationship is already ended");
        }
        if (!"ACTIVE".equals(current.status)) {
            throw reject(422, "GUARDIAN_NOT_ACTIVE", "Only an active guardian relationship can be ended");
        }
        GuardianLink ended = new GuardianLink(current.tenantId, current.id, current.studentId, current.guardianPersonId,
                current.firstName, current.lastName, current.relationship, current.email, current.phone,
                false, "ENDED", clock.instant());
        replaceLink(current, ended);
        if (persistence != null) {
            persistence.endGuardianship(actor, ended);
        }
        Deactivation result = new Deactivation(ended, normalizedReason, true, false, true);
        logger.info("stm05 deactivation reasonCode={} guardianshipId={} previousStatus=ACTIVE deleted=false",
                normalizedReason, ended.id);
        log(actor, "guardian.deactivated", ended.id);
        intelligenceService.publishGuardianDeactivated(actor, current, result);
        return result;
    }

    /**
     * Marks an active relationship disputed, suspended, or expired and opens a new re-verification (STM-05-US-006).
     * {@code DISPUTED} and {@code SUSPENDED} are stored as {@code SUSPENDED}. {@code EXPIRED} is stored as {@code ENDED}.
     * Those words are not legal status values. Access flags are cleared in the same write.
     * The new document is a separate {@code SUBMITTED} row. The previous evidence document is not edited.
     *
     * @throws GuardianException 403 when the caller is not a registrar, or is the guardian person
     * @throws GuardianException 422 when the relationship is not active or the state is not accepted
     */
    public StateChange markRelationshipState(Actor actor, UUID guardianshipId, String requestedState) {
        requireRegistrar(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(requestedState, "state");
        String normalizedState = requestedState.trim().toUpperCase();
        String storedStatus = storedStatusFor(normalizedState);
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (actor.getUserId() != null && actor.getUserId().equals(current.guardianPersonId)) {
            throw reject(403, "REVIEWER_IS_PARTY", "A reviewer cannot decide a relationship they are party to");
        }
        if (!"ACTIVE".equals(current.status)) {
            throw reject(422, "RELATIONSHIP_NOT_ACTIVE", "Only an active relationship can be marked disputed, suspended, or expired");
        }
        GuardianLink changed = new GuardianLink(current.tenantId, current.id, current.studentId, current.guardianPersonId,
                current.firstName, current.lastName, current.relationship, current.email, current.phone,
                false, storedStatus, clock.instant());
        replaceLink(current, changed);
        UUID documentId = UUID.randomUUID();
        if (persistence != null) {
            documentId = persistence.openReverification(actor, changed, normalizedState, storedStatus);
        }
        StateChange result = new StateChange(changed, normalizedState, storedStatus, documentId, true, false);
        logger.info("stm05 state requested={} storedStatus={} guardianshipId={} newDocumentId={} previousDocumentEdited=false",
                normalizedState, storedStatus, changed.id, documentId);
        log(actor, "guardian.state_changed", changed.id);
        intelligenceService.publishStateChanged(actor, current, result);
        return result;
    }

    /**
     * Grants selected information to a verified, active guardian (STM-05-US-010).
     * Allowed categories are academics, fee view, and fee payment. A blanket everything grant is refused.
     * The purpose and end date stay on the audit and intelligence contract. The flags are the existing guardianship columns.
     *
     * @throws GuardianException 403 when the caller is not a registrar, or is the guardian person
     * @throws GuardianException 422 when the relationship is not active and verified, or the category or purpose is not accepted
     */
    public AccessGrant grantAccess(Actor actor, UUID guardianshipId, List<String> categories, String purpose, String validTo) {
        requireRegistrar(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        requireText(purpose, "purpose");
        String normalizedPurpose = purpose.trim().toUpperCase();
        if (!"FAMILY_SUPPORT".equals(normalizedPurpose) && !"FEE_ASSISTANCE".equals(normalizedPurpose)
                && !"ACADEMIC_PROGRESS".equals(normalizedPurpose)) {
            throw reject(422, "PURPOSE_INVALID", "Purpose must be FAMILY_SUPPORT, FEE_ASSISTANCE, or ACADEMIC_PROGRESS");
        }
        boolean viewAcademics = false;
        boolean viewFees = false;
        boolean payFees = false;
        if (categories == null || categories.isEmpty()) {
            throw reject(422, "CATEGORY_REQUIRED", "Select at least one information category");
        }
        for (String category : categories) {
            String normalized = category == null ? "" : category.trim().toUpperCase();
            if ("ACADEMICS".equals(normalized)) {
                viewAcademics = true;
            } else if ("FEES".equals(normalized)) {
                viewFees = true;
            } else if ("FEE_PAYMENT".equals(normalized)) {
                payFees = true;
            } else if ("EVERYTHING".equals(normalized) || "ALL".equals(normalized) || "CAN_VIEW_EVERYTHING".equals(normalized)) {
                throw reject(422, "CATEGORY_UNSUPPORTED", "A blanket access grant is not accepted");
            } else {
                throw reject(422, "CATEGORY_UNSUPPORTED", "Information category is not accepted");
            }
        }
        if (!viewAcademics && !viewFees && !payFees) {
            throw reject(422, "CATEGORY_REQUIRED", "Select at least one information category");
        }
        java.time.LocalDate until = null;
        if (validTo != null && !validTo.trim().isEmpty()) {
            try {
                until = java.time.LocalDate.parse(validTo.trim());
            } catch (RuntimeException ex) {
                throw reject(422, "DATE_INVALID", "Access end date must be a calendar date");
            }
            if (until.isBefore(java.time.LocalDate.now(clock))) {
                throw reject(422, "DATE_INVALID", "Access end date cannot be in the past");
            }
        }
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (actor.getUserId() != null && actor.getUserId().equals(current.guardianPersonId)) {
            throw reject(403, "REVIEWER_IS_PARTY", "A reviewer cannot decide a relationship they are party to");
        }
        if (!"ACTIVE".equals(current.status)) {
            throw reject(422, "RELATIONSHIP_NOT_ACTIVE", "Access can be granted only on an active relationship");
        }
        boolean verified = persistence != null
                ? persistence.evidenceApproved(actor, current)
                : Boolean.TRUE.equals(verifiedEvidence.get(guardianshipId));
        if (!verified) {
            throw reject(422, "RELATIONSHIP_NOT_VERIFIED", "Access can be granted only after the relationship evidence is verified");
        }
        AccessWindow previous = persistence != null ? persistence.findAccess(actor, current) : accessWindows.get(current.id);
        if (persistence != null) {
            persistence.grantAccess(actor, current, viewAcademics, viewFees, payFees, until);
        } else {
            accessWindows.put(current.id, new AccessWindow(viewAcademics, viewFees, payFees, null, until));
        }
        recordHistory(actor, current, "GRANT", flagText(previous), flagText(viewAcademics, viewFees, payFees));
        AccessGrant grant = new AccessGrant(current, viewAcademics, viewFees, payFees, normalizedPurpose,
                until == null ? "" : until.toString(), false);
        logger.info("stm05 access granted guardianshipId={} academics={} fees={} feePayment={} purpose={} everything=false",
                current.id, viewAcademics, viewFees, payFees, normalizedPurpose);
        log(actor, "guardian.access_granted", current.id);
        intelligenceService.publishAccessGranted(actor, grant);
        return grant;
    }

    /**
     * Records the start and end of an access grant (STM-05-US-011).
     * The dates are the existing {@code valid_from} and {@code valid_to} columns.
     * Saving a period that has already elapsed does not by itself clear access. The next read does that.
     *
     * @throws GuardianException 403 when the caller is not the student or a registrar
     * @throws GuardianException 422 when the relationship is not active or the end date is before the start date
     */
    public AccessDecision setAccessPeriod(Actor actor, UUID guardianshipId, String validFrom, String validTo) {
        GuardianLink current = requirePeriodActor(actor, guardianshipId);
        if (!"ACTIVE".equals(current.status)) {
            throw reject(422, "RELATIONSHIP_NOT_ACTIVE", "An access period can be set only on an active relationship");
        }
        java.time.LocalDate from = parseDate(validFrom, "Access start date");
        java.time.LocalDate until = parseDate(validTo, "Access end date");
        if (until.isBefore(from)) {
            throw reject(422, "DATE_INVALID", "Access end date cannot be before the start date");
        }
        AccessWindow existing = persistence != null ? persistence.findAccess(actor, current) : accessWindows.get(current.id);
        boolean viewAcademics = existing != null && existing.viewAcademics;
        boolean viewFees = existing != null && existing.viewFees;
        boolean payFees = existing != null && existing.payFees;
        if (persistence != null) {
            persistence.saveAccessPeriod(actor, current, from, until);
        } else {
            accessWindows.put(current.id, new AccessWindow(viewAcademics, viewFees, payFees, from, until));
        }
        AccessDecision decision = new AccessDecision(current.id, current.status, "PERIOD_SET", viewAcademics, viewFees, payFees,
                from.toString(), until.toString(), false);
        logger.info("stm05 access period set guardianshipId={} validFrom={} validTo={}", current.id, from, until);
        log(actor, "guardian.access_period_set", current.id);
        intelligenceService.publishAccessPeriod(actor, decision);
        return decision;
    }

    /**
     * Revokes a guardian's current access (STM-05-US-012).
     * The relationship stays {@code ACTIVE}. The three access flags are cleared in the same write.
     * There is no consent-history table and no session cache in this service, so the revocation is the audit and intelligence record.
     *
     * @throws GuardianException 409 when no access flag is currently granted
     * @throws GuardianException 422 when the relationship is not active
     */
    public AccessDecision revokeAccess(Actor actor, UUID guardianshipId) {
        GuardianLink current = requirePeriodActor(actor, guardianshipId);
        if (!"ACTIVE".equals(current.status)) {
            throw reject(422, "RELATIONSHIP_NOT_ACTIVE", "Access can be revoked only on an active relationship");
        }
        AccessWindow window = persistence != null ? persistence.findAccess(actor, current) : accessWindows.get(current.id);
        boolean granted = window != null && (window.viewAcademics || window.viewFees || window.payFees);
        if (!granted) {
            throw reject(409, "ACCESS_ALREADY_REVOKED", "This guardian access is already revoked");
        }
        if (persistence != null) {
            persistence.revokeAccess(actor, current);
        } else {
            accessWindows.put(current.id, new AccessWindow(false, false, false, window.validFrom, window.validTo));
        }
        AccessDecision revoked = new AccessDecision(current.id, current.status, "REVOKED", false, false, false,
                dateText(window.validFrom), dateText(window.validTo), false);
        logger.info("stm05 access revoked guardianshipId={} relationshipStatus=ACTIVE sessionInvalidated=false", current.id);
        log(actor, "guardian.access_revoked", current.id);
        recordHistory(actor, current, "REVOCATION", flagText(window), "NONE");
        intelligenceService.publishAccessRevoked(actor, revoked);
        return revoked;
    }

    /**
     * Applies an elapsed end date. The guardianship stays {@code ACTIVE}. Access flags are cleared.
     * {@code EXPIRED} is the access result, because that word is not a legal guardianship status.
     * A second read does not grant the cleared access again.
     */
    public AccessDecision currentAccess(Actor actor, UUID guardianshipId) {
        GuardianLink current = requirePeriodActor(actor, guardianshipId);
        AccessWindow window = persistence != null ? persistence.findAccess(actor, current) : accessWindows.get(current.id);
        if (window == null) {
            window = new AccessWindow(false, false, false, null, null);
        }
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        if (!"ACTIVE".equals(current.status)) {
            return denied(current, current.status, window);
        }
        if (window.validTo != null && window.validTo.isBefore(today)) {
            if (window.viewAcademics || window.viewFees || window.payFees) {
                if (persistence != null) {
                    persistence.expireElapsedAccess(actor, current, today);
                } else {
                    accessWindows.put(current.id, new AccessWindow(false, false, false, window.validFrom, window.validTo));
                }
                AccessDecision expired = new AccessDecision(current.id, current.status, "EXPIRED", false, false, false,
                        dateText(window.validFrom), dateText(window.validTo), false);
                logger.info("stm05 access expired guardianshipId={} validTo={} relationshipStatus=ACTIVE flagsCleared=true",
                        current.id, window.validTo);
                log(actor, "guardian.access_expired", current.id);
                recordHistory(actor, current, "EXPIRY", flagText(window), "NONE");
                intelligenceService.publishAccessExpired(actor, expired);
                return expired;
            }
            return new AccessDecision(current.id, current.status, "EXPIRED", false, false, false,
                    dateText(window.validFrom), dateText(window.validTo), false);
        }
        if (window.validFrom != null && window.validFrom.isAfter(today)) {
            return new AccessDecision(current.id, current.status, "SCHEDULED", window.viewAcademics, window.viewFees, window.payFees,
                    dateText(window.validFrom), dateText(window.validTo), false);
        }
        boolean allowed = window.viewAcademics || window.viewFees || window.payFees;
        return new AccessDecision(current.id, current.status, allowed ? "ACTIVE" : "NONE",
                window.viewAcademics, window.viewFees, window.payFees, dateText(window.validFrom), dateText(window.validTo), allowed);
    }

    /**
     * Returns only the student-information categories on an active grant (STM-05-US-013).
     * Academic marks, fee balances, and reviewer notes stay out of this response.
     * A revoked, expired, scheduled, or inactive grant is denied.
     *
     * @throws GuardianException 403 when the caller is outside the student or registrar, or the grant is not usable
     */
    public AuthorizedView viewAuthorizedInformation(Actor actor, UUID guardianshipId) {
        GuardianLink current = requirePeriodActor(actor, guardianshipId);
        AccessWindow window = persistence != null ? persistence.findAccess(actor, current) : accessWindows.get(current.id);
        if (window == null) {
            window = new AccessWindow(false, false, false, null, null);
        }
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        boolean granted = window.viewAcademics || window.viewFees || window.payFees;
        boolean started = window.validFrom == null || !window.validFrom.isAfter(today);
        boolean open = window.validTo == null || !window.validTo.isBefore(today);
        if (!"ACTIVE".equals(current.status) || !granted || !started || !open) {
            throw reject(403, "GUARDIAN_VIEW_DENIED", "This guardian can view only information inside an active grant");
        }
        List<String> categories = new ArrayList<String>();
        if (window.viewAcademics) {
            categories.add("ACADEMICS");
        }
        if (window.viewFees) {
            categories.add("FEES");
        }
        if (window.payFees) {
            categories.add("FEE_PAYMENT");
        }
        AuthorizedView view = new AuthorizedView(current.id, current.studentId, categories,
                dateText(window.validFrom), dateText(window.validTo));
        logger.info("stm05 authorized view guardianshipId={} categories={} reviewerNotesIncluded=false sourceRecordsIncluded=false",
                current.id, categories);
        log(actor, "guardian.authorized_view", current.id);
        intelligenceService.publishAuthorizedView(actor, view);
        return view;
    }

    /**
     * Adds an emergency contact for a student (STM-05-US-007).
     * The contact is {@code ACTIVE}. It grants no academic, financial, or portal access.
     * There is no status column, so active means {@code deleted_at} is empty.
     *
     * @throws GuardianException 403 when the caller cannot maintain this student's contacts
     * @throws GuardianException 422 when the name, relation, or phone is not usable
     */
    public EmergencyContact addEmergencyContact(Actor actor, UUID studentId, String name, String relationship, String phone) {
        requireContactManager(actor, studentId);
        String contactName = requireContactName(name);
        String relation = requireContactRelation(relationship);
        String number = requirePhone(phone);
        EmergencyContact contact = new EmergencyContact(UUID.randomUUID(), studentId, contactName, relation, number, "ACTIVE");
        rememberContact(actor, contact);
        if (persistence != null) {
            contact = persistence.saveEmergencyContact(actor, contact);
        }
        logger.info("stm05 emergency contact added contactId={} studentId={} accessGranted=false", contact.id, studentId);
        log(actor, "emergency_contact.added", contact.id);
        intelligenceService.publishEmergencyContact(actor, contact, "ADDED");
        return contact;
    }

    /**
     * Changes the name, relation, or phone of an active emergency contact.
     *
     * @throws GuardianException 409 when the contact is already inactive
     */
    public EmergencyContact updateEmergencyContact(Actor actor, UUID contactId, UUID studentId, String name, String relationship, String phone) {
        requireContactManager(actor, studentId);
        if (contactId == null) {
            throw reject(422, "CONTACT_REQUIRED", "Contact id is required");
        }
        EmergencyContact current = findContact(actor, studentId, contactId);
        if (current == null) {
            throw reject(404, "CONTACT_NOT_FOUND", "Emergency contact was not found");
        }
        if (!"ACTIVE".equals(current.status)) {
            throw reject(409, "CONTACT_INACTIVE", "An inactive emergency contact cannot be changed");
        }
        EmergencyContact updated = new EmergencyContact(current.id, studentId, requireContactName(name),
                requireContactRelation(relationship), requirePhone(phone), "ACTIVE");
        replaceContact(actor, updated);
        if (persistence != null) {
            updated = persistence.updateEmergencyContact(actor, updated);
        }
        logger.info("stm05 emergency contact changed contactId={} studentId={} accessGranted=false", updated.id, studentId);
        log(actor, "emergency_contact.updated", updated.id);
        intelligenceService.publishEmergencyContact(actor, updated, "UPDATED");
        return updated;
    }

    /**
     * Marks an emergency contact inactive. The row stays. This is not a hard delete.
     */
    public EmergencyContact removeEmergencyContact(Actor actor, UUID contactId, UUID studentId) {
        requireContactManager(actor, studentId);
        if (contactId == null) {
            throw reject(422, "CONTACT_REQUIRED", "Contact id is required");
        }
        EmergencyContact current = findContact(actor, studentId, contactId);
        if (current == null) {
            throw reject(404, "CONTACT_NOT_FOUND", "Emergency contact was not found");
        }
        if (!"ACTIVE".equals(current.status)) {
            throw reject(409, "CONTACT_INACTIVE", "This emergency contact is already inactive");
        }
        EmergencyContact inactive = new EmergencyContact(current.id, studentId, current.name, current.relationship, current.phone, "INACTIVE");
        replaceContact(actor, inactive);
        if (persistence != null) {
            inactive = persistence.removeEmergencyContact(actor, inactive);
        }
        logger.info("stm05 emergency contact inactivated contactId={} studentId={} hardDeleted=false", inactive.id, studentId);
        log(actor, "emergency_contact.inactivated", inactive.id);
        intelligenceService.publishEmergencyContact(actor, inactive, "INACTIVATED");
        return inactive;
    }

    /**
     * Lists emergency contacts for one student, including inactive rows.
     */
    public List<EmergencyContact> listEmergencyContacts(Actor actor, UUID studentId) {
        requireContactManager(actor, studentId);
        if (persistence != null) {
            return persistence.findEmergencyContacts(actor, studentId);
        }
        List<EmergencyContact> rows = emergencyContacts.get(statusKey(actor.getTenantId(), studentId));
        if (rows == null) {
            return Collections.emptyList();
        }
        return new ArrayList<EmergencyContact>(rows);
    }

    private void requireContactManager(Actor actor, UUID studentId) {
        requireTenant(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student record id is required");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "EMERGENCY_CONTACT_FORBIDDEN", "Students may maintain only their own emergency contacts");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "EMERGENCY_CONTACT_FORBIDDEN", "Emergency contacts require the student or a registrar");
        }
    }

    private String requireContactName(String name) {
        requireText(name, "name");
        return name.trim();
    }

    private String requireContactRelation(String relationship) {
        requireText(relationship, "relationship");
        return relationship.trim();
    }

    private String requirePhone(String phone) {
        requireText(phone, "phone");
        String number = phone.trim().replace(" ", "").replace("-", "");
        if (!number.matches("\\+?[0-9]{8,15}")) {
            throw reject(422, "PHONE_INVALID", "Phone must be 8 to 15 digits");
        }
        return number;
    }

    private void rememberContact(Actor actor, EmergencyContact contact) {
        String key = statusKey(actor.getTenantId(), contact.studentId);
        List<EmergencyContact> rows = emergencyContacts.get(key);
        if (rows == null) {
            rows = Collections.synchronizedList(new ArrayList<EmergencyContact>());
            emergencyContacts.put(key, rows);
        }
        rows.add(contact);
    }

    private EmergencyContact findContact(Actor actor, UUID studentId, UUID contactId) {
        if (persistence != null) {
            return persistence.findEmergencyContact(actor, studentId, contactId);
        }
        List<EmergencyContact> rows = emergencyContacts.get(statusKey(actor.getTenantId(), studentId));
        if (rows == null) {
            return null;
        }
        for (EmergencyContact contact : rows) {
            if (contact.id.equals(contactId)) {
                return contact;
            }
        }
        return null;
    }

    private void replaceContact(Actor actor, EmergencyContact updated) {
        List<EmergencyContact> rows = emergencyContacts.get(statusKey(actor.getTenantId(), updated.studentId));
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(updated.id)) {
                rows.set(i, updated);
                return;
            }
        }
    }

    private String storedStatusFor(String requestedState) {
        if ("DISPUTED".equals(requestedState) || "SUSPENDED".equals(requestedState)) {
            return "SUSPENDED";
        }
        if ("EXPIRED".equals(requestedState)) {
            return "ENDED";
        }
        throw reject(422, "STATE_INVALID", "State must be DISPUTED, SUSPENDED, or EXPIRED");
    }

    /**
     * Lists guardians already linked to a student in the caller's tenant.
     * A student may list only their own record. A registrar may list any student in the tenant.
     *
     * @throws GuardianException 403 when a student asks for another student's guardians
     */
    public List<GuardianLink> listGuardians(Actor actor, UUID studentId) {
        requireTenant(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student record id is required");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(studentId)) {
            throw reject(403, "GUARDIAN_VIEW_FORBIDDEN", "Students may list only their own guardians");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "GUARDIAN_VIEW_FORBIDDEN", "Guardian view requires the student or a registrar");
        }
        if (persistence != null) {
            return persistence.findGuardians(actor, studentId);
        }
        List<GuardianLink> rows = links.get(statusKey(actor.getTenantId(), studentId));
        if (rows == null) {
            return Collections.emptyList();
        }
        return new ArrayList<GuardianLink>(rows);
    }

    /**
     * Lists relationship and consent changes for one student (STM-05-US-014).
     * The list is read-only. An auditor sees claims, verifications, grants, expiries, and revocations.
     *
     * @throws GuardianException 403 when the caller is not a registrar
     */
    public List<HistoryEntry> listRelationshipHistory(Actor actor, UUID studentId) {
        requireTenant(actor);
        if (studentId == null) {
            throw reject(422, "STUDENT_REQUIRED", "Student record id is required");
        }
        if (!isRegistrar(actor)) {
            throw reject(403, "HISTORY_FORBIDDEN", "Relationship history requires a registrar");
        }
        List<HistoryEntry> rows;
        if (persistence != null) {
            rows = persistence.findHistory(actor, studentId);
        } else {
            rows = new ArrayList<HistoryEntry>();
            for (HistoryEntry entry : historyEntries) {
                if (studentId.equals(entry.studentId)) {
                    rows.add(entry);
                }
            }
        }
        logger.info("stm05 relationship history reviewed studentId={} entries={} readOnly=true", studentId, rows.size());
        log(actor, "guardian.history_reviewed", studentId);
        intelligenceService.publishHistoryReviewed(actor, studentId, rows.size());
        return rows;
    }

    private void recordHistory(Actor actor, GuardianLink link, String kind, String oldValue, String newValue) {
        HistoryEntry entry = new HistoryEntry(UUID.randomUUID(), link.studentId, link.id, kind,
                actor.getUserId(), java.time.Instant.now(clock).toString(), oldValue, newValue, true);
        if (persistence != null) {
            persistence.appendHistory(actor, entry);
        } else {
            historyEntries.add(entry);
        }
    }

    private static String flagText(AccessWindow window) {
        if (window == null) {
            return "NONE";
        }
        return flagText(window.viewAcademics, window.viewFees, window.payFees);
    }

    private static String flagText(boolean viewAcademics, boolean viewFees, boolean payFees) {
        StringBuilder text = new StringBuilder();
        if (viewAcademics) {
            text.append("ACADEMICS");
        }
        if (viewFees) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append("FEES");
        }
        if (payFees) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append("FEE_PAYMENT");
        }
        return text.length() == 0 ? "NONE" : text.toString();
    }

    private GuardianLink requirePeriodActor(Actor actor, UUID guardianshipId) {
        requireTenant(actor);
        if (guardianshipId == null) {
            throw reject(422, "GUARDIAN_REQUIRED", "Guardian id is required");
        }
        GuardianLink current = findLink(actor, guardianshipId);
        if (current == null) {
            throw reject(404, "GUARDIAN_NOT_FOUND", "Guardian was not found");
        }
        if (isStudent(actor) && !actor.subjectStudentId().equals(current.studentId)) {
            throw reject(403, "ACCESS_PERIOD_FORBIDDEN", "Students may set access only for their own guardians");
        }
        if (!isStudent(actor) && !isRegistrar(actor)) {
            throw reject(403, "ACCESS_PERIOD_FORBIDDEN", "Setting an access period requires the student or a registrar");
        }
        return current;
    }

    private java.time.LocalDate parseDate(String value, String label) {
        requireText(value, label);
        try {
            return java.time.LocalDate.parse(value.trim());
        } catch (RuntimeException ex) {
            throw reject(422, "DATE_INVALID", label + " must be a calendar date");
        }
    }

    private static String dateText(java.time.LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static AccessDecision denied(GuardianLink current, String accessState, AccessWindow window) {
        return new AccessDecision(current.id, current.status, accessState, false, false, false,
                dateText(window.validFrom), dateText(window.validTo), false);
    }

    private void requireRegistrar(Actor actor) {
        requireTenant(actor);
        if (!isRegistrar(actor)) {
            throw reject(403, "REGISTRAR_FORBIDDEN", "Adding a guardian requires catalogue role R31 Registrar");
        }
    }

    private void requireTenant(Actor actor) {
        if (actor == null || actor.getTenantId() == null) {
            throw reject(401, "TENANT_REQUIRED", "Tenant context is required");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw reject(422, "FIELD_REQUIRED", field + " is required");
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

    private GuardianException reject(int status, String code, String message) {
        if (status >= 500) {
            logger.error("stm05 rejected status={} code={} message={}", status, code, message);
        } else {
            logger.warn("stm05 rejected status={} code={} message={}", status, code, message);
        }
        return new GuardianException(status, code, message);
    }

    private void log(Actor actor, String action, UUID resourceId) {
        logger.info("stm05 action={} tenantId={} correlationId={} resourceId={}",
                action, actor.getTenantId(), actor.getCorrelationId(), resourceId);
        logger.audit(AuditEvent.builder()
                .action(action)
                .tenantId(actor.getTenantId().toString())
                .principalId(actor.getUserId() == null ? null : actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("GUARDIAN")
                .resourceId(resourceId == null ? null : resourceId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description(action)
                .build());
    }

    private GuardianLink findLink(Actor actor, UUID guardianshipId) {
        if (persistence != null) {
            return persistence.findGuardian(actor, guardianshipId);
        }
        for (List<GuardianLink> rows : links.values()) {
            for (GuardianLink link : rows) {
                if (link.id.equals(guardianshipId) && link.tenantId.equals(actor.getTenantId())) {
                    return link;
                }
            }
        }
        return null;
    }

    private void replaceLink(GuardianLink current, GuardianLink updated) {
        List<GuardianLink> rows = links.get(statusKey(current.tenantId, current.studentId));
        if (rows == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(current.id)) {
                rows.set(i, updated);
                return;
            }
        }
    }

    private static String statusKey(UUID tenantId, UUID studentId) {
        return tenantId + ":" + studentId;
    }

    /**
     * One emergency contact. {@code INACTIVE} means the row remains with {@code deleted_at} set. It grants no student-data access.
     */
    public static final class EmergencyContact {
        public final UUID id;
        public final UUID studentId;
        public final String name;
        public final String relationship;
        public final String phone;
        public final String status;

        public EmergencyContact(UUID id, UUID studentId, String name, String relationship, String phone, String status) {
            this.id = id;
            this.studentId = studentId;
            this.name = name;
            this.relationship = relationship;
            this.phone = phone;
            this.status = status;
        }
    }

    /**
     * A reviewer state change. {@code requestedState} is the reviewer's word. {@code storedStatus} is the legal row status.
     * {@code previousDocumentEdited} stays false because re-verification is a new document.
     */
    public static final class StateChange {
        public final GuardianLink link;
        public final String requestedState;
        public final String storedStatus;
        public final UUID documentId;
        public final boolean accessRevoked;
        public final boolean previousDocumentEdited;

        public StateChange(GuardianLink link, String requestedState, String storedStatus, UUID documentId,
                           boolean accessRevoked, boolean previousDocumentEdited) {
            this.link = link;
            this.requestedState = requestedState;
            this.storedStatus = storedStatus;
            this.documentId = documentId;
            this.accessRevoked = accessRevoked;
            this.previousDocumentEdited = previousDocumentEdited;
        }
    }

    /**
     * Stored access flags and period. {@code EXPIRED} is not written to the guardianship status.
     */
    public static final class AccessWindow {
        public final boolean viewAcademics;
        public final boolean viewFees;
        public final boolean payFees;
        public final java.time.LocalDate validFrom;
        public final java.time.LocalDate validTo;

        public AccessWindow(boolean viewAcademics, boolean viewFees, boolean payFees,
                            java.time.LocalDate validFrom, java.time.LocalDate validTo) {
            this.viewAcademics = viewAcademics;
            this.viewFees = viewFees;
            this.payFees = payFees;
            this.validFrom = validFrom;
            this.validTo = validTo;
        }
    }

    /**
     * Categories a guardian may see. Denied categories are absent. Reviewer notes and source records are never included.
     */
    public static final class AuthorizedView {
        public final UUID guardianshipId;
        public final UUID studentId;
        public final List<String> categories;
        public final String validFrom;
        public final String validTo;
        public final boolean reviewerNotesIncluded;
        public final boolean sourceRecordsIncluded;

        public AuthorizedView(UUID guardianshipId, UUID studentId, List<String> categories, String validFrom, String validTo) {
            this.guardianshipId = guardianshipId;
            this.studentId = studentId;
            this.categories = categories;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.reviewerNotesIncluded = false;
            this.sourceRecordsIncluded = false;
        }
    }

    /**
     * One read-only relationship or consent change. {@code oldValue} and {@code newValue} are the category or status words.
     */
    public static final class HistoryEntry {
        public final UUID id;
        public final UUID studentId;
        public final UUID guardianshipId;
        public final String kind;
        public final UUID actorId;
        public final String occurredAt;
        public final String oldValue;
        public final String newValue;
        public final boolean readOnly;

        public HistoryEntry(UUID id, UUID studentId, UUID guardianshipId, String kind, UUID actorId,
                            String occurredAt, String oldValue, String newValue, boolean readOnly) {
            this.id = id;
            this.studentId = studentId;
            this.guardianshipId = guardianshipId;
            this.kind = kind;
            this.actorId = actorId;
            this.occurredAt = occurredAt;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.readOnly = readOnly;
        }
    }

    /**
     * Result of setting or reading an access period. {@code accessAllowed} is false once the end date has passed.
     */
    public static final class AccessDecision {
        public final UUID guardianshipId;
        public final String relationshipStatus;
        public final String accessState;
        public final boolean viewAcademics;
        public final boolean viewFees;
        public final boolean payFees;
        public final String validFrom;
        public final String validTo;
        public final boolean accessAllowed;

        public AccessDecision(UUID guardianshipId, String relationshipStatus, String accessState, boolean viewAcademics,
                              boolean viewFees, boolean payFees, String validFrom, String validTo, boolean accessAllowed) {
            this.guardianshipId = guardianshipId;
            this.relationshipStatus = relationshipStatus;
            this.accessState = accessState;
            this.viewAcademics = viewAcademics;
            this.viewFees = viewFees;
            this.payFees = payFees;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.accessAllowed = accessAllowed;
        }
    }

    /**
     * Selected access on an active, verified guardianship. {@code everythingGranted} stays false.
     */
    public static final class AccessGrant {
        public final GuardianLink link;
        public final boolean viewAcademics;
        public final boolean viewFees;
        public final boolean payFees;
        public final String purpose;
        public final String validTo;
        public final boolean everythingGranted;

        public AccessGrant(GuardianLink link, boolean viewAcademics, boolean viewFees, boolean payFees,
                           String purpose, String validTo, boolean everythingGranted) {
            this.link = link;
            this.viewAcademics = viewAcademics;
            this.viewFees = viewFees;
            this.payFees = payFees;
            this.purpose = purpose;
            this.validTo = validTo;
            this.everythingGranted = everythingGranted;
        }
    }

    /**
     * Ended relationship. The row remains. Access flags are cleared in the same write. The notice is recorded, not delivered to an inbox.
     */
    public static final class Deactivation {
        public final GuardianLink link;
        public final String reasonCode;
        public final boolean accessRevoked;
        public final boolean hardDeleted;
        public final boolean noticeRecorded;

        public Deactivation(GuardianLink link, String reasonCode, boolean accessRevoked, boolean hardDeleted, boolean noticeRecorded) {
            this.link = link;
            this.reasonCode = reasonCode;
            this.accessRevoked = accessRevoked;
            this.hardDeleted = hardDeleted;
            this.noticeRecorded = noticeRecorded;
        }
    }

    /**
     * Review outcome. {@code decision} is VERIFIED or REJECTED. {@code storedStatus} is the value written to {@code doc.documents}.
     */
    public static final class ReviewDecision {
        public final UUID documentId;
        public final UUID guardianshipId;
        public final String decision;
        public final String storedStatus;
        public final String reasonCode;

        public ReviewDecision(UUID documentId, UUID guardianshipId, String decision, String storedStatus, String reasonCode) {
            this.documentId = documentId;
            this.guardianshipId = guardianshipId;
            this.decision = decision;
            this.storedStatus = storedStatus;
            this.reasonCode = reasonCode;
        }
    }

    /**
     * A document reference queued for guardian-relationship review. No file bytes are stored here.
     */
    public static final class EvidenceRecord {
        public final UUID documentId;
        public final UUID linkId;
        public final UUID guardianshipId;
        public final String documentType;
        public final String status;

        public EvidenceRecord(UUID documentId, UUID linkId, UUID guardianshipId, String documentType, String status) {
            this.documentId = documentId;
            this.linkId = linkId;
            this.guardianshipId = guardianshipId;
            this.documentType = documentType;
            this.status = status;
        }
    }

    /**
     * One guardian relationship for a student.
     */
    public static final class GuardianLink {
        public final UUID tenantId;
        public final UUID id;
        public final UUID studentId;
        public final UUID guardianPersonId;
        public final String firstName;
        public final String lastName;
        public final String relationship;
        public final String email;
        public final String phone;
        public final boolean primary;
        public final String status;
        public final Instant linkedAt;

        public GuardianLink(UUID tenantId, UUID id, UUID studentId, UUID guardianPersonId, String firstName,
                            String lastName, String relationship, String email, String phone, boolean primary,
                            String status, Instant linkedAt) {
            this.tenantId = tenantId;
            this.id = id;
            this.studentId = studentId;
            this.guardianPersonId = guardianPersonId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.relationship = relationship;
            this.email = email;
            this.phone = phone;
            this.primary = primary;
            this.status = status;
            this.linkedAt = linkedAt;
        }
    }
}
