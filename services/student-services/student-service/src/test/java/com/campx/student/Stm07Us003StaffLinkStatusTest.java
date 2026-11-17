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
}
