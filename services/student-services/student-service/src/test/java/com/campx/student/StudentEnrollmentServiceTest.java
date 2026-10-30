package com.campx.student;

import com.campx.student.exception.StudentConflictException;
import com.campx.student.exception.StudentValidationException;
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
 * Unit test suite verifying Student Enrollment logic, uniqueness checks,
 * and composite aggregate creation.
 */
public class StudentEnrollmentServiceTest {

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

    @Test
    public void testSuccessfulStudentEnrollmentWithGuardian() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Rohan");
        req.setMiddleName("K");
        req.setLastName("Deshmukh");
        req.setPrimaryEmail("rohan.deshmukh@institution.edu");
        req.setPrimaryPhone("+919123456780");
        req.setGender(Gender.M);
        req.setDateOfBirth(LocalDate.of(2005, 4, 12));
        req.setEnrollmentNo("CS2026101");
        req.setAdmissionNo("ADM-2026-001");
        req.setAdmissionDate(LocalDate.of(2026, 8, 1));
        req.setCollegeId(collegeId);
        req.setProgramId(programId);

        EnrollStudentRequest.GuardianRequest gReq = new EnrollStudentRequest.GuardianRequest();
        gReq.setFirstName("Suresh");
        gReq.setLastName("Deshmukh");
        gReq.setRelationship("FATHER");
        gReq.setPrimaryEmail("suresh.deshmukh@example.com");
        gReq.setPrimaryPhone("+919123456781");
        gReq.setPrimary(true);
        req.setGuardian(gReq);

        StudentProfileResponse resp = service.enrollStudent(context, req);

        assertNotNull(resp.getId());
        assertNotNull(resp.getPersonId());
        assertEquals("CS2026101", resp.getEnrollmentNo());
        assertEquals("ADM-2026-001", resp.getAdmissionNo());
        assertEquals("Rohan K Deshmukh", resp.getDisplayName());
        assertEquals("rohan.deshmukh@institution.edu", resp.getPrimaryEmail());
        assertEquals(StudentStatus.ACTIVE, resp.getStatus());
        assertEquals(collegeId, resp.getCollegeId());
        assertEquals(programId, resp.getProgramId());

        assertEquals(1, resp.getGuardians().size());
        StudentProfileResponse.GuardianSummary gSummary = resp.getGuardians().get(0);
        assertEquals("Suresh Deshmukh", gSummary.getDisplayName());
        assertEquals("FATHER", gSummary.getRelationship());
        assertTrue(gSummary.isPrimary());
    }

    @Test(expected = StudentConflictException.class)
    public void testDuplicateEnrollmentNoInSameTenantRejected() {
        EnrollStudentRequest req1 = new EnrollStudentRequest();
        req1.setFirstName("Student");
        req1.setLastName("One");
        req1.setPrimaryEmail("s1@institution.edu");
        req1.setEnrollmentNo("DUPLICATE_ROLL");
        req1.setCollegeId(collegeId);
        service.enrollStudent(context, req1);

        EnrollStudentRequest req2 = new EnrollStudentRequest();
        req2.setFirstName("Student");
        req2.setLastName("Two");
        req2.setPrimaryEmail("s2@institution.edu");
        req2.setEnrollmentNo("DUPLICATE_ROLL"); // same roll in same tenant
        req2.setCollegeId(collegeId);

        service.enrollStudent(context, req2);
    }

    @Test(expected = StudentConflictException.class)
    public void testDuplicateEmailInSameTenantRejected() {
        EnrollStudentRequest req1 = new EnrollStudentRequest();
        req1.setFirstName("User");
        req1.setLastName("Alpha");
        req1.setPrimaryEmail("same.email@institution.edu");
        req1.setEnrollmentNo("ENR_A");
        req1.setCollegeId(collegeId);
        service.enrollStudent(context, req1);

        EnrollStudentRequest req2 = new EnrollStudentRequest();
        req2.setFirstName("User");
        req2.setLastName("Beta");
        req2.setPrimaryEmail("same.email@institution.edu"); // same email in same tenant
        req2.setEnrollmentNo("ENR_B");
        req2.setCollegeId(collegeId);

        service.enrollStudent(context, req2);
    }

    @Test
    public void testSameEnrollmentNoInDifferentTenantAllowed() {
        EnrollStudentRequest req1 = new EnrollStudentRequest();
        req1.setFirstName("Student");
        req1.setLastName("One");
        req1.setPrimaryEmail("s1@tenant-a.edu");
        req1.setEnrollmentNo("CROSS_TENANT_ROLL");
        req1.setCollegeId(collegeId);
        StudentProfileResponse respA = service.enrollStudent(context, req1);
        assertNotNull(respA.getId());

        // Different Tenant
        UUID otherTenantId = UUID.randomUUID();
        UserSecurityContext otherContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId, UUID.randomUUID(), null, "college_admin", false);

        EnrollStudentRequest req2 = new EnrollStudentRequest();
        req2.setFirstName("Student");
        req2.setLastName("Two");
        req2.setPrimaryEmail("s2@tenant-b.edu");
        req2.setEnrollmentNo("CROSS_TENANT_ROLL"); // same roll number, but in tenant B
        req2.setCollegeId(otherContext.getCollegeId());

        StudentProfileResponse respB = service.enrollStudent(otherContext, req2);
        assertNotNull(respB.getId());
        assertNotEquals(respA.getId(), respB.getId());
    }

    @Test(expected = StudentValidationException.class)
    public void testMissingFirstNameRejected() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setLastName("Doe");
        req.setPrimaryEmail("doe@campx.edu");
        req.setEnrollmentNo("ENR_MISSING_NAME");
        req.setCollegeId(collegeId);
        service.enrollStudent(context, req);
    }

    @Test(expected = StudentValidationException.class)
    public void testInvalidEmailRejected() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setPrimaryEmail("not-an-email");
        req.setEnrollmentNo("ENR_INVALID_EMAIL");
        req.setCollegeId(collegeId);
        service.enrollStudent(context, req);
    }

    @Test(expected = StudentValidationException.class)
    public void testShortEnrollmentNoRejected() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setPrimaryEmail("john@campx.edu");
        req.setEnrollmentNo("AB"); // < 3 characters
        req.setCollegeId(collegeId);
        service.enrollStudent(context, req);
    }
}
