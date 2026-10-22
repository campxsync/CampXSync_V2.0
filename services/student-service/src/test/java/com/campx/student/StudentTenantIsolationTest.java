package com.campx.student;

import com.campx.student.exception.StudentNotFoundException;
import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.*;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tenant isolation test verifying that cross-tenant queries, lifecycle modifications,
 * and roster searches fail closed and prevent data leakage across institutions.
 */
public class StudentTenantIsolationTest {

    private StudentDomainService service;
    private UserSecurityContext tenantAContext;
    private UserSecurityContext tenantBContext;
    private UUID tenantAId;
    private UUID tenantBId;

    @Before
    public void setUp() {
        tenantAId = UUID.randomUUID();
        tenantBId = UUID.randomUUID();

        tenantAContext = new UserSecurityContext(UUID.randomUUID(), tenantAId, UUID.randomUUID(), null, "college_admin", false);
        tenantBContext = new UserSecurityContext(UUID.randomUUID(), tenantBId, UUID.randomUUID(), null, "college_admin", false);

        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);
    }

    @Test
    public void testCrossTenantRosterIsStrictlyIsolated() {
        // Enroll in Tenant A
        EnrollStudentRequest reqA = new EnrollStudentRequest();
        reqA.setEnrollmentNo("A_STUDENT_01");
        reqA.setFirstName("Alice");
        reqA.setLastName("TenantA");
        reqA.setPrimaryEmail("alice@tenanta.edu");
        reqA.setCollegeId(tenantAContext.getCollegeId());
        service.enrollStudent(tenantAContext, reqA);

        // Enroll in Tenant B
        EnrollStudentRequest reqB = new EnrollStudentRequest();
        reqB.setEnrollmentNo("B_STUDENT_01");
        reqB.setFirstName("Bob");
        reqB.setLastName("TenantB");
        reqB.setPrimaryEmail("bob@tenantb.edu");
        reqB.setCollegeId(tenantBContext.getCollegeId());
        service.enrollStudent(tenantBContext, reqB);

        // Query Tenant A roster
        CursorPagedResult<StudentRosterItem> rosterA = service.getRoster(tenantAContext, new StudentRosterQuery());
        assertEquals(1, rosterA.getItems().size());
        assertEquals("A_STUDENT_01", rosterA.getItems().get(0).getEnrollmentNo());

        // Query Tenant B roster
        CursorPagedResult<StudentRosterItem> rosterB = service.getRoster(tenantBContext, new StudentRosterQuery());
        assertEquals(1, rosterB.getItems().size());
        assertEquals("B_STUDENT_01", rosterB.getItems().get(0).getEnrollmentNo());
    }

    @Test(expected = StudentNotFoundException.class)
    public void testTenantACannotReadTenantBStudentById() {
        EnrollStudentRequest reqB = new EnrollStudentRequest();
        reqB.setEnrollmentNo("SECRET_B");
        reqB.setFirstName("Bob");
        reqB.setLastName("TenantB");
        reqB.setPrimaryEmail("bob.secret@tenantb.edu");
        reqB.setCollegeId(tenantBContext.getCollegeId());
        StudentProfileResponse respB = service.enrollStudent(tenantBContext, reqB);

        // Tenant A attempts to fetch Tenant B's student -> Must throw NotFoundException (fail-closed)
        service.getStudentById(tenantAContext, respB.getId());
    }

    @Test(expected = StudentNotFoundException.class)
    public void testTenantACannotTransitionTenantBStudent() {
        EnrollStudentRequest reqB = new EnrollStudentRequest();
        reqB.setEnrollmentNo("TARGET_B");
        reqB.setFirstName("Bob");
        reqB.setLastName("TenantB");
        reqB.setPrimaryEmail("bob.target@tenantb.edu");
        reqB.setCollegeId(tenantBContext.getCollegeId());
        StudentProfileResponse respB = service.enrollStudent(tenantBContext, reqB);

        // Tenant A attempts to suspend Tenant B's student -> Must throw NotFoundException
        service.transitionLifecycle(tenantAContext, respB.getId(),
                new UpdateStudentStatusRequest(StudentStatus.SUSPENDED, "Unauthorized cross-tenant action"));
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testNullTenantContextFailsClosed() {
        UserSecurityContext.fromHeaders(UUID.randomUUID().toString(), null, null, "college_admin");
    }
}
