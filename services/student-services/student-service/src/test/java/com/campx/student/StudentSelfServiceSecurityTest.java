package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.exception.StudentNotFoundException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Verifies Student Self-Service and Tenant/Role Security:
 * STM01-US-003, STM01-US-030, STM01-US-031, STM01-US-046
 */
public class StudentSelfServiceSecurityTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);
    }

    @Test
    public void testStudentCanAccessOwnProfileViaGetMyProfile() {
        UUID studentUserId = UUID.randomUUID();

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Aarav");
        req.setLastName("Sharma");
        req.setPrimaryEmail("aarav.sharma@institution.edu");
        req.setCollegeId(collegeId);

        StudentProfileResponse created = service.enrollStudent(staffContext, req);
        assertNotNull(created.getId());

        // Link student to IAM user ID
        service.linkIamAccount(staffContext, created.getId(), studentUserId);

        // Student's own security context
        UserSecurityContext studentContext = new UserSecurityContext(
                studentUserId, tenantId, collegeId, null, "student", false
        );

        StudentProfileResponse myProfile = service.getMyProfile(studentContext);
        assertNotNull(myProfile);
        assertEquals(created.getId(), myProfile.getId());
        assertEquals("Aarav Sharma", myProfile.getDisplayName());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testStudentCannotAccessAnotherStudentsProfileById() {
        UUID student1UserId = UUID.randomUUID();
        UUID student2UserId = UUID.randomUUID();

        EnrollStudentRequest req1 = new EnrollStudentRequest();
        req1.setFirstName("Student");
        req1.setLastName("One");
        req1.setPrimaryEmail("s1@institution.edu");
        req1.setCollegeId(collegeId);
        StudentProfileResponse p1 = service.enrollStudent(staffContext, req1);
        service.linkIamAccount(staffContext, p1.getId(), student1UserId);

        EnrollStudentRequest req2 = new EnrollStudentRequest();
        req2.setFirstName("Student");
        req2.setLastName("Two");
        req2.setPrimaryEmail("s2@institution.edu");
        req2.setCollegeId(collegeId);
        StudentProfileResponse p2 = service.enrollStudent(staffContext, req2);
        service.linkIamAccount(staffContext, p2.getId(), student2UserId);

        // Student 1 tries to access Student 2's profile by ID directly
        UserSecurityContext student1Context = new UserSecurityContext(
                student1UserId, tenantId, collegeId, null, "student", false
        );

        service.getStudentProfile(student1Context, p2.getId());
    }

    @Test(expected = StudentNotFoundException.class)
    public void testCrossTenantAccessFailsClosedWith404NotFound() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Cross");
        req.setLastName("Tenant");
        req.setPrimaryEmail("cross@institution.edu");
        req.setCollegeId(collegeId);
        StudentProfileResponse created = service.enrollStudent(staffContext, req);

        // Staff in different tenant attempts access
        UUID otherTenantId = UUID.randomUUID();
        UserSecurityContext otherStaffContext = new UserSecurityContext(
                UUID.randomUUID(), otherTenantId, UUID.randomUUID(), null, "college_admin", false
        );

        // Inaccessible record does not reveal existence (fails closed)
        service.getStudentProfile(otherStaffContext, created.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testUnauthenticatedOrMissingTenantRejected() {
        UserSecurityContext invalidContext = new UserSecurityContext(
                UUID.randomUUID(), null, null, null, "student", false
        );

        service.getMyProfile(invalidContext);
    }
}
