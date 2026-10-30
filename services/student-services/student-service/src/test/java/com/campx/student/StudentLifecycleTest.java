package com.campx.student;

import com.campx.student.exception.StudentLifecycleException;
import com.campx.student.model.*;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit test suite verifying the Student Lifecycle State Machine and transition rules.
 */
public class StudentLifecycleTest {

    private StudentDomainService service;
    private UserSecurityContext context;
    private UUID tenantId;
    private UUID collegeId;
    private UUID programId;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        programId = UUID.randomUUID();
        context = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);
    }

    private StudentProfileResponse createActiveStudent(String enrollmentNo, String email) {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Arjun");
        req.setLastName("Verma");
        req.setPrimaryEmail(email);
        req.setEnrollmentNo(enrollmentNo);
        req.setCollegeId(collegeId);
        req.setProgramId(programId);
        req.setAdmissionDate(LocalDate.of(2026, 8, 1));
        return service.enrollStudent(context, req);
    }

    @Test
    public void testActiveToSuspendedTransitionPermitted() {
        StudentProfileResponse student = createActiveStudent("ENR001", "arjun1@campx.edu");
        assertEquals(StudentStatus.ACTIVE, student.getStatus());

        StudentProfileResponse updated = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.SUSPENDED, "Pending disciplinary review"));
        assertEquals(StudentStatus.SUSPENDED, updated.getStatus());
    }

    @Test
    public void testSuspendedToActiveReinstatementPermitted() {
        StudentProfileResponse student = createActiveStudent("ENR002", "arjun2@campx.edu");

        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.SUSPENDED, "Administrative hold"));
        StudentProfileResponse reinstated = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ACTIVE, "Reinstated by dean"));

        assertEquals(StudentStatus.ACTIVE, reinstated.getStatus());
    }

    @Test
    public void testActiveToDetainedAndBackPermitted() {
        StudentProfileResponse student = createActiveStudent("ENR003", "arjun3@campx.edu");

        StudentProfileResponse detained = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.DETAINED, "Academic performance hold"));
        assertEquals(StudentStatus.DETAINED, detained.getStatus());

        StudentProfileResponse cleared = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ACTIVE, "Remedial courses cleared"));
        assertEquals(StudentStatus.ACTIVE, cleared.getStatus());
    }

    @Test
    public void testActiveToGraduatedToAlumniPermitted() {
        StudentProfileResponse student = createActiveStudent("ENR004", "arjun4@campx.edu");

        StudentProfileResponse grad = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.GRADUATED, "Credits completed"));
        assertEquals(StudentStatus.GRADUATED, grad.getStatus());

        StudentProfileResponse alumni = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ALUMNI, "Convocation conferred"));
        assertEquals(StudentStatus.ALUMNI, alumni.getStatus());
    }

    @Test
    public void testActiveToWithdrawnPermitted() {
        StudentProfileResponse student = createActiveStudent("ENR005", "arjun5@campx.edu");

        StudentProfileResponse withdrawn = service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.WITHDRAWN, "Transferred to external university"));
        assertEquals(StudentStatus.WITHDRAWN, withdrawn.getStatus());
    }

    @Test(expected = StudentLifecycleException.class)
    public void testWithdrawnIsTerminalAndCannotReactivate() {
        StudentProfileResponse student = createActiveStudent("ENR006", "arjun6@campx.edu");

        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.WITHDRAWN, "Voluntary exit"));

        // Terminal state: must throw exception
        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ACTIVE, "Attempted reactivation"));
    }

    @Test(expected = StudentLifecycleException.class)
    public void testAlumniIsTerminalAndCannotReactivate() {
        StudentProfileResponse student = createActiveStudent("ENR007", "arjun7@campx.edu");

        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.GRADUATED, "Completed"));
        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ALUMNI, "Alumni"));

        // Terminal state: must throw exception
        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ACTIVE, "Attempted reactivation"));
    }

    @Test(expected = StudentLifecycleException.class)
    public void testActiveCannotJumpDirectlyToAlumni() {
        StudentProfileResponse student = createActiveStudent("ENR008", "arjun8@campx.edu");

        // Illegal: Must transition to GRADUATED before ALUMNI
        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ALUMNI, "Premature alumni status"));
    }

    @Test(expected = StudentLifecycleException.class)
    public void testSameStatusTransitionRejected() {
        StudentProfileResponse student = createActiveStudent("ENR009", "arjun9@campx.edu");

        // Cannot transition from ACTIVE to ACTIVE
        service.transitionLifecycle(context, student.getId(),
                new UpdateStudentStatusRequest(StudentStatus.ACTIVE, "No-op transition"));
    }
}
