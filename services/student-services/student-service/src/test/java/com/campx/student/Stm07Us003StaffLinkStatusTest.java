package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.AccountLinkStatusResponse;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.FakeDelegationPort;
import com.campx.student.security.FakeIamStudentRolePort;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-07-US-003 — Staff view of account link status (B3-D5).
 */
public class Stm07Us003StaffLinkStatusTest {

    private InMemoryStudentRepository studentRepo;
    private FakeDelegationPort delegations;
    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext iamOperator;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        studentRepo = new InMemoryStudentRepository(personRepo);
        delegations = new FakeDelegationPort();
        service = new StudentDomainService(
                studentRepo, personRepo, new InMemoryGuardianshipRepository(),
                new com.campx.student.repository.InMemoryCorrectionRequestRepository(),
                new com.campx.student.repository.InMemoryStudentVerificationRepository(),
                new com.campx.student.repository.InMemoryPersonIdentifierRepository(),
                new com.campx.student.repository.InMemoryStudentDocumentRepository(),
                new com.campx.student.repository.InMemoryStudentAddressRepository(),
                new com.campx.student.service.StudentEventPublisher.InMemory(),
                new com.campx.student.repository.InMemoryAuditLogRepository(),
                delegations,
                new FakeIamStudentRolePort());
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Meera");
        req.setLastName("Nair");
        req.setPrimaryEmail("meera.nair@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        StudentTestSupport.markVerified(studentRepo, iamOperator, student.getId());
        student = service.getStudentById(iamOperator, student.getId());
    }

    @Test
    public void stm07Us003_iamOperatorCanViewStaffLinkStatus() {
        AccountLinkStatusResponse status = service.getAccountLinkStatus(iamOperator, student.getId());
        assertEquals("NONE", status.getLinkStatus());
        assertEquals("VERIFIED", status.getVerificationStatus());
    }

    @Test
    public void stm07Us003_r01CanViewStaffLinkStatus() {
        UserSecurityContext r01 =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r01", false);
        AccountLinkStatusResponse status = service.getAccountLinkStatus(r01, student.getId());
        assertEquals(student.getId(), status.getStudentId());
    }

    @Test
    public void stm07Us003_registrarWithDelegationCanView() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        AccountLinkStatusResponse status = service.getAccountLinkStatus(registrar, student.getId());
        assertEquals("NONE", status.getLinkStatus());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us003_registrarWithoutDelegationDenied() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r31", false);
        service.getAccountLinkStatus(registrar, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us003_facultyDeniedStaffLinkStatus() {
        UserSecurityContext faculty =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);
        service.getAccountLinkStatus(faculty, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us003_collegeAdminWithoutBeingOperatorDenied() {
        UserSecurityContext admin =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);
        delegations.grant(tenantId, admin.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        service.getAccountLinkStatus(admin, student.getId());
    }

    @Test
    public void stm07D13_delegatedRegistrarSucceedsForLinkStatusAndEligibility() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r31", false);
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);

        AccountLinkStatusResponse linkStatus = service.getAccountLinkStatus(registrar, student.getId());
        assertNotNull(linkStatus);
        assertEquals(student.getId(), linkStatus.getStudentId());
        assertEquals("NONE", linkStatus.getLinkStatus());
        assertEquals("VERIFIED", linkStatus.getVerificationStatus());

        com.campx.student.model.ProvisioningEligibilityResult eligibility =
                service.evaluateProvisioningEligibility(registrar, student.getId(), null);
        assertNotNull(eligibility);
        assertEquals(student.getId(), eligibility.getStudentId());
        assertTrue(eligibility.isEligible());
        assertEquals("VERIFIED", eligibility.getVerificationStatus());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07D13_registrarWithoutDelegationDeniedForEligibility() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r31", false);
        service.evaluateProvisioningEligibility(registrar, student.getId(), null);
    }

    @Test(expected = com.campx.student.exception.StudentNotFoundException.class)
    public void stm07D13_registrarCannotReadAnotherCollegeStudent() {
        UUID otherCollegeId = UUID.randomUUID();
        UserSecurityContext registrarOtherCollege =
                new UserSecurityContext(UUID.randomUUID(), tenantId, otherCollegeId, null, "r31", false);
        delegations.grant(tenantId, registrarOtherCollege.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);

        // Student belongs to collegeId, registrar is scoped to otherCollegeId -> must return 404 Not Found
        service.getAccountLinkStatus(registrarOtherCollege, student.getId());
    }

    @Test
    public void stm07D13_noExtraStudentFieldsReturned() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r31", false);
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);

        AccountLinkStatusResponse resp = service.getAccountLinkStatus(registrar, student.getId());
        // Verify only minimal fields exist in class definition — no personal/demographic/academic getters
        java.lang.reflect.Method[] methods = AccountLinkStatusResponse.class.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            String name = m.getName().toLowerCase();
            assertFalse("Should not expose personal name", name.contains("firstname") || name.contains("lastname"));
            assertFalse("Should not expose email", name.contains("email"));
            assertFalse("Should not expose phone", name.contains("phone"));
            assertFalse("Should not expose address", name.contains("address"));
            assertFalse("Should not expose gpa", name.contains("gpa") || name.contains("grade"));
        }
    }
}
