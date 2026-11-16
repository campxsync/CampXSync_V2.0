package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.exception.StudentValidationException;
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
 * STM-07-US-001 / US-002 / US-004 / US-010 / US-013 account provisioning tests.
 */
public class Stm07AccountProvisioningTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private UserSecurityContext guardianContext;
    private UUID studentUserId;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();
        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);
        guardianContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "guardian", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Asha");
        req.setLastName("Rao");
        req.setPrimaryEmail("asha.rao@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
    }

    @Test
    public void stm07Us002_eligibleActiveStudentPasses() {
        ProvisioningEligibilityResult result =
                service.evaluateProvisioningEligibility(staffContext, student.getId(), studentUserId);
        assertTrue(result.isEligible());
        assertTrue(result.getFailures().isEmpty());
        assertEquals("NONE", result.getLinkStatus());
    }

    @Test
    public void stm07Us002_suspendedStudentNotEligible() {
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.SUSPENDED);
        status.setReason("Conduct");
        service.transitionLifecycle(staffContext, student.getId(), status);

        ProvisioningEligibilityResult result =
                service.evaluateProvisioningEligibility(staffContext, student.getId(), studentUserId);
        assertFalse(result.isEligible());
        assertTrue(result.getFailures().contains("STUDENT_NOT_ACTIVE"));
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us002_studentDeniedEligibilityCheck() {
        service.evaluateProvisioningEligibility(studentContext, student.getId(), studentUserId);
    }

    @Test
    public void stm07Us001_linkRequiresEligibilityAndPersistsStatus() {
        StudentProfileResponse linked = service.linkIamAccount(staffContext, student.getId(), studentUserId);
        assertEquals(studentUserId, linked.getIamUserId());

        AccountLinkStatusResponse status = service.getAccountLinkStatus(staffContext, student.getId());
        assertEquals("ACTIVE", status.getLinkStatus());
        assertEquals(studentUserId, status.getLinkedIamUserId());
        assertTrue(status.isPortalAccessActive());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us001_cannotLinkOtherUserWhenAlreadyLinked() {
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
        service.linkIamAccount(staffContext, student.getId(), UUID.randomUUID());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us001_cannotLinkIamUserAlreadyUsedElsewhere() {
        service.linkIamAccount(staffContext, student.getId(), studentUserId);

        EnrollStudentRequest req2 = new EnrollStudentRequest();
        req2.setFirstName("Other");
        req2.setLastName("Student");
        req2.setPrimaryEmail("other.student@institution.edu");
        req2.setCollegeId(collegeId);
        StudentProfileResponse other = service.enrollStudent(staffContext, req2);

        service.linkIamAccount(staffContext, other.getId(), studentUserId);
    }

    @Test
    public void stm07Us013_studentCanViewOwnLinkStatus() {
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
        AccountLinkStatusResponse status = service.getAccountLinkStatus(studentContext, student.getId());
        assertEquals("ACTIVE", status.getLinkStatus());
        assertTrue(status.isPortalAccessActive());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us013_studentCannotViewOtherLinkStatus() {
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
        UserSecurityContext otherStudent =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "student", false);
        service.getAccountLinkStatus(otherStudent, student.getId());
    }

    @Test
    public void stm07Us004_provisioningAllowedWhenEligible() {
        service.requestAccountProvisioning(staffContext, student.getId());
        AccountLinkStatusResponse status = service.getAccountLinkStatus(staffContext, student.getId());
        assertEquals(1L, status.getProvisioningRequestCount());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us004_provisioningDeniedWhenAlreadyLinked() {
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
        service.requestAccountProvisioning(staffContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us004_studentCannotProvision() {
        service.requestAccountProvisioning(studentContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us004_guardianCannotProvision() {
        service.requestAccountProvisioning(guardianContext, student.getId());
    }

    @Test
    public void stm07Us010_suspendRevokesActiveLink() {
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.SUSPENDED);
        status.setReason("Access review");
        StudentProfileResponse after = service.transitionLifecycle(staffContext, student.getId(), status);
        assertNull(after.getIamUserId());
        AccountLinkStatusResponse link = service.getAccountLinkStatus(staffContext, student.getId());
        assertEquals("NONE", link.getLinkStatus());
        assertFalse(link.isPortalAccessActive());
    }

    @Test
    public void stm07Us002_wrongTenantIsolation() {
        UUID otherTenant = UUID.randomUUID();
        UserSecurityContext otherStaff =
                new UserSecurityContext(UUID.randomUUID(), otherTenant, collegeId, null, "system_admin", false);
        try {
            service.evaluateProvisioningEligibility(otherStaff, student.getId(), studentUserId);
            fail("Expected not found or empty for cross-tenant");
        } catch (Exception expected) {
            assertTrue(expected.getMessage() != null);
        }
    }
}
