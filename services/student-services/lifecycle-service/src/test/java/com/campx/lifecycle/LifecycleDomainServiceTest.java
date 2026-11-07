package com.campx.lifecycle;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
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
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
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
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
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
        LifecycleDomainService.LifecycleRequest transfer = service.submitRequest(studentActor, student, "TRANSFER", "Move to another programme");
        service.decideRequest(registrar, transfer.id, "APPROVED");
        assertEquals("TRANSFERRED", service.getStatus(studentActor, student).status);

        service.seedStudent(tenant, otherStudent, "ON_LEAVE");
        Actor other = new Actor(tenant, otherStudent, "STUDENT", "corr-other");
        LifecycleDomainService.LifecycleRequest reinstate = service.submitRequest(other, otherStudent, "REINSTATE", "Returning");
        service.decideRequest(registrar, reinstate.id, "APPROVED");
        assertEquals("ACTIVE", service.getStatus(other, otherStudent).status);

        LifecycleDomainService.LifecycleRequest withdrawal = service.submitRequest(other, otherStudent, "WITHDRAWAL", "Personal",
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
        LifecycleDomainService.LifecycleRequest pending = service.submitRequest(studentActor, student, "LEAVE", "Medical");
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
}
