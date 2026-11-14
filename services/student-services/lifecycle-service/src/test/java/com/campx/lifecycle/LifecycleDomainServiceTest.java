package com.campx.lifecycle;

import com.campx.lifecycle.intelligence.LifecycleIntelligenceService;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LifecycleDomainServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private LifecycleDomainService service;
    private UUID tenant;
    private UUID student;
    private UUID otherStudent;
    private UUID program;
    private Actor registrar;
    private Actor studentActor;
    private Actor parentActor;
    private Actor facultyActor;

    @Before
    public void setUp() {
        service = new LifecycleDomainService(Clock.fixed(NOW, ZoneOffset.UTC));
        tenant = UUID.randomUUID();
        student = UUID.randomUUID();
        otherStudent = UUID.randomUUID();
        program = UUID.randomUUID();
        registrar = new Actor(tenant, UUID.randomUUID(), "REGISTRAR", "corr-reg");
        studentActor = new Actor(tenant, student, "STUDENT", "corr-stu");
        parentActor = new Actor(tenant, UUID.randomUUID(), "PARENT", "corr-par");
        facultyActor = new Actor(tenant, UUID.randomUUID(), "FACULTY", "corr-fac");
        service.seedStudent(tenant, student);
        service.seedStudent(tenant, otherStudent);
    }

    @Test
    public void studentCanViewOwnStatus() {
        LifecycleDomainService.LifecycleStatus status = service.getStatus(studentActor, student);
        assertEquals("ACTIVE", status.status);
    }

    @Test
    public void studentCannotApproveRequest() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "Medical");
        try {
            service.decideRequest(studentActor, pending.id, "APPROVED");
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REGISTRAR_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void registrarApprovesLeave() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "Medical");
        LifecycleDomainService.LifecycleRequest decided = service.decideRequest(registrar, pending.id, "APPROVED");
        assertEquals("APPROVED", decided.status);
        LifecycleDomainService.LifecycleStatus status = service.getStatus(studentActor, student);
        assertEquals("ON_LEAVE", status.status);
        assertEquals(1, service.eventsForTenant(tenant).size());
        assertEquals("ON_LEAVE", service.eventsForTenant(tenant).get(0).get("status"));
        assertEquals("ACTIVE", service.eventsForTenant(tenant).get(0).get("previousStatus"));
    }

    @Test
    public void parentLinkedViewReturnsLimitedStatus() {
        java.util.Map<String, String> view = service.parentViewLinkedStatus(parentActor, student, student.toString());
        assertEquals(student.toString(), view.get("studentId"));
        assertEquals("ACTIVE", view.get("status"));
    }

    @Test
    public void transferWithdrawalAndReinstatementFollowApproval() {
        LifecycleDomainService.LifecycleRequest transfer = service.submitRequest(studentActor, student, "TRANSFER",
                "Move to another programme");
        service.decideRequest(registrar, transfer.id, "APPROVED");
        assertEquals("TRANSFERRED", service.getStatus(studentActor, student).status);

        service.seedStudent(tenant, otherStudent, "ON_LEAVE");
        Actor other = new Actor(tenant, otherStudent, "STUDENT", "corr-other");
        LifecycleDomainService.LifecycleRequest reinstate = service.submitRequest(other, otherStudent, "REINSTATE",
                "Returning");
        service.decideRequest(registrar, reinstate.id, "APPROVED");
        assertEquals("ACTIVE", service.getStatus(other, otherStudent).status);

        LifecycleDomainService.LifecycleRequest withdrawal = service.submitRequest(other, otherStudent, "WITHDRAWAL",
                "Personal",
                LocalDate.parse("2026-12-01"));
        service.decideRequest(registrar, withdrawal.id, "APPROVED");
        assertEquals("WITHDRAWN", service.getStatus(other, otherStudent).status);
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), service.getStatus(other, otherStudent).effectiveFrom);
    }

    @Test
    public void withdrawalRequiresEffectiveDate() {
        try {
            service.submitRequest(studentActor, student, "WITHDRAWAL", "Personal");
            fail("Withdrawal without an effective date must be rejected");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("EFFECTIVE_DATE_REQUIRED", ex.getCode());
        }
    }

    @Test
    public void leaveEffectiveDateCannotBeInPast() {
        try {
            service.submitRequest(studentActor, student, "LEAVE", "Medical leave", LocalDate.parse("2026-11-01"));
            fail("Effective date in the past must be rejected");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("EFFECTIVE_DATE_INVALID", ex.getCode());
        }
    }

    @Test
    public void registrarReportCountsStatusesAndAdvisorSeesOnlyAdvisees() {
        java.util.Map<String, Object> report = service.lifecycleReport(registrar);
        assertEquals(Integer.valueOf(2), report.get("studentCount"));
        service.assignAdvisor(registrar, facultyActor.getUserId(), student);
        java.util.List<java.util.Map<String, String>> advisees = service.listAdvisees(facultyActor);
        assertEquals(1, advisees.size());
        assertEquals("ACTIVE", advisees.get(0).get("status"));
    }

    @Test
    public void registrarRecordsGraduationOnlyWhenTheStudentIsActive() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "Medical");
        java.util.Map<String, Object> blocked = service.validateGraduation(registrar, student, program);
        assertEquals(Boolean.FALSE, blocked.get("eligible"));
        try {
            service.recordGraduation(registrar, student, program, LocalDate.parse("2026-11-07"), "Too soon");
            fail("A pending lifecycle request must block graduation");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("GRADUATION_NOT_ELIGIBLE", ex.getCode());
        }
        service.decideRequest(registrar, pending.id, "REJECTED");
        java.util.Map<String, Object> eligible = service.validateGraduation(registrar, student, program);
        assertEquals(Boolean.TRUE, eligible.get("eligible"));
        LifecycleDomainService.GraduationRecord record = service.recordGraduation(registrar, student, program,
                LocalDate.parse("2026-11-07"), "Completed the programme");
        assertEquals(LocalDate.parse("2026-11-07"), record.graduatedOn);
        assertEquals("GRADUATED", service.getStatus(registrar, student).status);

        service.seedStudent(tenant, otherStudent, "WITHDRAWN");
        assertEquals(Boolean.FALSE, service.validateGraduation(registrar, otherStudent, program).get("eligible"));
        try {
            service.validateGraduation(studentActor, student, program);
            fail("Students must not validate graduation");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    @Test
    public void facultyForbidden() {
        try {
            service.getStatus(facultyActor, student);
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("FACULTY_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void advisorCanListAdviseeRequestsAndEndorsePendingPetition() {
        service.assignAdvisor(registrar, facultyActor.getUserId(), student);
        LifecycleDomainService.LifecycleRequest req = service.submitRequest(studentActor, student, "LEAVE",
                "Medical leave for testing", LocalDate.parse("2026-12-10"));

        java.util.List<LifecycleDomainService.LifecycleRequest> adviseeReqs = service.listAdviseeRequests(facultyActor);
        assertEquals(1, adviseeReqs.size());
        assertEquals(req.id, adviseeReqs.get(0).id);

        java.util.Map<String, Object> endorsement = service.endorseRequest(facultyActor, req.id, "RECOMMENDED",
                "Student discussed medical recovery plan with HOD");
        assertEquals(Boolean.TRUE, endorsement.get("endorsed"));
        assertEquals("RECOMMENDED", endorsement.get("endorsement"));

        // Remarks guardrail (< 5 chars rejected)
        try {
            service.endorseRequest(facultyActor, req.id, "RECOMMENDED", "bad");
            fail("Remarks with < 5 characters must be rejected");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("REMARKS_TOO_SHORT", ex.getCode());
        }

        // Student cannot endorse
        try {
            service.endorseRequest(studentActor, req.id, "RECOMMENDED", "Self recommendation");
            fail("Students must not endorse petitions");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ADVISOR_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-04-US-001 View current lifecycle status ---

    @Test
    public void stm04Us001_studentViewsOwnStatus() {
        assertEquals("ACTIVE", service.getStatus(studentActor, student).status);
    }

    @Test
    public void stm04Us001_studentCannotViewAnotherStatus() {
        try {
            service.getStatus(studentActor, otherStudent);
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("VIEW_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-04-US-002 Update lifecycle status through authorized workflow ---

    @Test
    public void stm04Us002_registrarAdministrativeTransition() {
        LifecycleDomainService.LifecycleStatus updated = service.transitionStatus(registrar, student, "ON_LEAVE", "Admin leave");
        assertEquals("ON_LEAVE", updated.status);
    }

    @Test
    public void stm04Us002_studentCannotAdministrativelyTransition() {
        try {
            service.transitionStatus(studentActor, student, "ON_LEAVE", "self");
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    // --- STM-04-US-003 Review lifecycle status changes / US-004 history ---

    @Test
    public void stm04Us003_registrarReviewsPendingRequests() {
        service.submitRequest(studentActor, student, "LEAVE", "Medical");
        List<LifecycleDomainService.LifecycleRequest> pending = service.listPendingRequests(registrar);
        assertTrue(pending.size() >= 1);
        assertEquals("PENDING", pending.get(0).status);
    }

    @Test
    public void stm04Us004_studentViewsOwnHistoryAfterLeaveApproval() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
        service.decideRequest(registrar, pending.id, "APPROVED");
        List<LifecycleDomainService.HistoryEntry> history = service.listHistory(studentActor, student);
        assertTrue(history.size() >= 1);
    }

    @Test
    public void stm04Us004_crossStudentHistoryForbidden() {
        try {
            service.listHistory(studentActor, otherStudent);
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    // --- STM-04-US-005 Submit leave-of-absence ---

    @Test
    public void stm04Us005_studentSubmitsLeaveRequest() {
        LifecycleDomainService.LifecycleRequest req = service.submitRequest(studentActor, student, "LEAVE", "Medical");
        assertEquals("LEAVE", req.requestType);
        assertEquals("PENDING", req.status);
    }

    // --- STM-04-US-006 Approve or reject leave ---

    @Test
    public void stm04Us006_registrarRejectsLeave() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
        LifecycleDomainService.LifecycleRequest decided = service.decideRequest(registrar, pending.id, "REJECTED");
        assertEquals("REJECTED", decided.status);
        assertEquals("ACTIVE", service.getStatus(studentActor, student).status);
    }

    // --- STM-04-US-007 / US-008 Reinstatement ---

    @Test
    public void stm04Us007_008_studentRequestsReinstatementAndRegistrarApproves() {
        service.seedStudent(tenant, student, "ON_LEAVE");
        LifecycleDomainService.LifecycleRequest reinstate = service.submitRequest(studentActor, student, "REINSTATE", "Ready");
        service.decideRequest(registrar, reinstate.id, "APPROVED");
        assertEquals("ACTIVE", service.getStatus(studentActor, student).status);
    }

    // --- STM-04-US-009 / US-010 Transfer ---

    @Test
    public void stm04Us009_010_transferRequestAndApproval() {
        LifecycleDomainService.LifecycleRequest transfer = service.submitRequest(studentActor, student, "TRANSFER", "Programme move");
        service.decideRequest(registrar, transfer.id, "APPROVED");
        assertEquals("TRANSFERRED", service.getStatus(studentActor, student).status);
    }

    // --- STM-04-US-011 / US-019 Withdrawal ---

    @Test
    public void stm04Us011_019_withdrawalWithEffectiveDateApproved() {
        LifecycleDomainService.LifecycleRequest withdrawal = service.submitRequest(studentActor, student, "WITHDRAWAL",
                "Personal", LocalDate.parse("2026-12-15"));
        service.decideRequest(registrar, withdrawal.id, "APPROVED");
        assertEquals("WITHDRAWN", service.getStatus(studentActor, student).status);
    }

    // --- STM-04-US-012 / US-013 Graduation ---

    @Test
    public void stm04Us012_013_validateAndRecordGraduation() {
        Map<String, Object> eligible = service.validateGraduation(registrar, student, program);
        assertEquals(Boolean.TRUE, eligible.get("eligible"));
        LifecycleDomainService.GraduationRecord record = service.recordGraduation(registrar, student, program,
                LocalDate.parse("2026-11-07"), "Completed");
        assertEquals("GRADUATED", service.getStatus(registrar, student).status);
        assertEquals(LocalDate.parse("2026-11-07"), record.graduatedOn);
    }

    // --- STM-04-US-014 Configure transition rules ---

    @Test
    public void stm04Us014_registrarConfiguresTransitionRule() {
        LifecycleDomainService.TransitionRule rule = service.configureRule(registrar, "ACTIVE", "ON_LEAVE", true, true);
        assertEquals("ACTIVE", rule.fromStatus);
        assertEquals("ON_LEAVE", rule.toStatus);
        assertTrue(rule.active);
    }

    @Test
    public void stm04Us014_studentCannotConfigureRules() {
        try {
            service.configureRule(studentActor, "ACTIVE", "ON_LEAVE", true, true);
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    // --- STM-04-US-015 Downstream events ---

    @Test
    public void stm04Us015_approvalEmitsLifecycleChangedEvent() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
        service.decideRequest(registrar, pending.id, "APPROVED");
        assertTrue(service.eventsForTenant(tenant).size() >= 1);
        assertEquals("ON_LEAVE", service.eventsForTenant(tenant).get(0).get("status"));
    }

    // --- STM-04-US-016 Lifecycle reports ---

    @Test
    public void stm04Us016_registrarViewsLifecycleReport() {
        Map<String, Object> report = service.lifecycleReport(registrar);
        assertTrue(((Number) report.get("studentCount")).intValue() >= 1);
    }

    @Test
    public void stm04Us016_studentCannotViewReport() {
        try {
            service.lifecycleReport(studentActor);
            fail("expected forbidden");
        } catch (LifecycleException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    // --- STM-04-US-017 Guardian limited status ---

    @Test
    public void stm04Us017_parentViewsLimitedLinkedStatus() {
        Map<String, String> view = service.parentViewLinkedStatus(parentActor, student, student.toString());
        assertEquals("ACTIVE", view.get("status"));
        assertEquals(student.toString(), view.get("studentId"));
    }

    // --- STM-04-US-018 Advisee limited status ---

    @Test
    public void stm04Us018_advisorViewsAdviseeLimitedStatus() {
        service.assignAdvisor(registrar, facultyActor.getUserId(), student);
        List<Map<String, String>> advisees = service.listAdvisees(facultyActor);
        assertEquals(1, advisees.size());
        assertEquals("ACTIVE", advisees.get(0).get("status"));
    }

    // --- STM-04-EN-002 Transition validation, transactional integrity guards ---

    @Test
    public void stm04En002_pastEffectiveDateRejected() {
        try {
            service.submitRequest(studentActor, student, "LEAVE", "Medical", LocalDate.parse("2026-11-01"));
            fail("Past effective date must be rejected");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("EFFECTIVE_DATE_INVALID", ex.getCode());
        }
    }

    @Test
    public void stm04En002_inactiveTransitionRuleBlocksAdminTransition() {
        service.configureRule(registrar, "ACTIVE", "ON_LEAVE", true, false);
        try {
            service.transitionStatus(registrar, student, "ON_LEAVE", "blocked by inactive rule");
            fail("Inactive transition rule must block administrative transition");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("TRANSITION_INACTIVE", ex.getCode());
        }
        assertEquals("ACTIVE", service.getStatus(studentActor, student).status);
    }

    @Test
    public void stm04En002_inactiveTransitionRuleBlocksRequestSubmit() {
        service.configureRule(registrar, "ACTIVE", "ON_LEAVE", true, false);
        try {
            service.submitRequest(studentActor, student, "LEAVE", "Medical");
            fail("Inactive transition rule must block leave submission");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("TRANSITION_INACTIVE", ex.getCode());
        }
    }

    @Test
    public void stm04En002_alreadyDecidedRequestReturns409() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
        service.decideRequest(registrar, pending.id, "APPROVED");
        try {
            service.decideRequest(registrar, pending.id, "REJECTED");
            fail("Second decision must conflict");
        } catch (LifecycleException ex) {
            assertEquals(409, ex.getStatus());
            assertEquals("REQUEST_ALREADY_DECIDED", ex.getCode());
        }
        assertEquals("ON_LEAVE", service.getStatus(studentActor, student).status);
    }

    @Test
    public void stm04En002_leaveOnlyAllowedFromActive() {
        service.seedStudent(tenant, student, "ON_LEAVE");
        try {
            service.submitRequest(studentActor, student, "LEAVE", "Already on leave");
            fail("Leave from non-ACTIVE must be rejected");
        } catch (LifecycleException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("TRANSITION_INVALID", ex.getCode());
        }
    }

    // --- STM-04-EN-003 Approval workflow + intelligence notifications (no Kafka invent) ---

    @Test
    public void stm04En003_endorseThenApproveEmitsLifecycleChangedEvent() {
        service.assignAdvisor(registrar, facultyActor.getUserId(), student);
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "Medical leave for EN-003 workflow");
        Map<String, Object> endorsement = service.endorseRequest(facultyActor, pending.id, "RECOMMENDED",
                "Department cleared orthopedic recovery leave request");
        assertEquals(Boolean.TRUE, endorsement.get("endorsed"));

        LifecycleDomainService.LifecycleRequest decided = service.decideRequest(registrar, pending.id, "APPROVED");
        assertEquals("APPROVED", decided.status);
        assertEquals("ON_LEAVE", service.getStatus(studentActor, student).status);

        List<Map<String, Object>> events = service.eventsForTenant(tenant);
        assertTrue(events.size() >= 1);
        Map<String, Object> latest = events.get(events.size() - 1);
        assertEquals("ON_LEAVE", latest.get("status"));
        assertEquals("ACTIVE", latest.get("previousStatus"));
        assertTrue(String.valueOf(latest.get("action")).contains("request.approved"));
        assertEquals(pending.id.toString(), latest.get("requestId"));
    }

    @Test
    public void stm04En003_rejectDoesNotEmitStatusTransitionEvent() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "Will be rejected");
        int before = service.eventsForTenant(tenant).size();
        service.decideRequest(registrar, pending.id, "REJECTED");
        assertEquals("ACTIVE", service.getStatus(studentActor, student).status);
        assertEquals("Reject must not emit lifecycle.changed transition", before, service.eventsForTenant(tenant).size());
    }

    @Test
    public void stm04En003_intelligenceServicePublishesThroughAdapter() {
        LifecycleIntelligenceService intel = new LifecycleIntelligenceService();
        Map<String, Object> domainEvent = new java.util.LinkedHashMap<String, Object>();
        domainEvent.put("tenantId", tenant);
        domainEvent.put("studentId", student);
        domainEvent.put("previousStatus", "ACTIVE");
        domainEvent.put("status", "ON_LEAVE");
        domainEvent.put("action", "request.approved:LEAVE");
        domainEvent.put("requestId", UUID.randomUUID().toString());
        domainEvent.put("correlationId", "corr-en003");
        domainEvent.put("occurredAt", NOW.toString());
        intel.publishLifecycleChanged(domainEvent);
        assertTrue(intel.getAdapter() != null);
    }

    // --- STM-04-EN-006 Audit history, tests, logging, operational readiness ---

    @Test
    public void stm04En006_approvalWritesImmutableHistoryEntry() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "History audit leave");
        service.decideRequest(registrar, pending.id, "APPROVED");
        List<LifecycleDomainService.HistoryEntry> rows = service.listHistory(studentActor, student);
        assertTrue(rows.size() >= 1);
        LifecycleDomainService.HistoryEntry last = rows.get(rows.size() - 1);
        assertEquals("ON_LEAVE", last.status);
        assertTrue(last.action.contains("request.approved"));
        assertEquals("REGISTRAR", last.actorRole);
    }

    @Test
    public void stm04En006_crossTenantHistoryIsolation() {
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE",
                "Tenant isolation leave");
        service.decideRequest(registrar, pending.id, "APPROVED");
        assertTrue(service.listHistory(studentActor, student).size() >= 1);

        UUID otherTenant = UUID.randomUUID();
        Actor foreignRegistrar = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr-foreign");
        // In-memory keys are tenant-scoped; foreign registrar must not see this tenant's history rows.
        assertEquals(0, service.listHistory(foreignRegistrar, student).size());
    }
}
