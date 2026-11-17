package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.ProvisioningRequestRecord;
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

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-07-US-006 — See past login requests (B3-D5 staff viewers).
 */
public class Stm07Us006ProvisioningHistoryTest {

    private FakeDelegationPort delegations;
    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext iamOperator;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
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
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "institution_admin", false);
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Neha");
        req.setLastName("Kapoor");
        req.setPrimaryEmail("neha.kapoor@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        StudentTestSupport.markVerified(studentRepo, iamOperator, student.getId());
        student = service.getStudentById(iamOperator, student.getId());
        service.requestAccountProvisioning(iamOperator, student.getId());
        UUID requestId = service.listProvisioningRequests(iamOperator, student.getId()).get(0).getId();
        service.markProvisioningFailed(iamOperator, student.getId(), requestId, "IAM_TIMEOUT");
        service.retryFailedProvisioningRequest(iamOperator, student.getId(), requestId);
    }

    @Test
    public void stm07Us006_iamOperatorSeesPastLoginRequests() {
        List<ProvisioningRequestRecord> history = service.listProvisioningRequests(iamOperator, student.getId());
        assertFalse(history.isEmpty());
        assertEquals("REQUESTED", history.get(0).getRequestStatus());
    }

    @Test
    public void stm07Us006_r02CanViewHistory() {
        UserSecurityContext r02 =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r02", false);
        List<ProvisioningRequestRecord> history = service.listProvisioningRequests(r02, student.getId());
        assertEquals(1, history.size());
    }

    @Test
    public void stm07Us006_registrarWithDelegationCanViewHistory() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        List<ProvisioningRequestRecord> history = service.listProvisioningRequests(registrar, student.getId());
        assertEquals(1, history.size());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us006_registrarWithoutDelegationDenied() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r31", false);
        service.listProvisioningRequests(registrar, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us006_facultyDeniedHistory() {
        UserSecurityContext faculty =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);
        service.listProvisioningRequests(faculty, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us006_collegeAdminDeniedEvenWithDelegation() {
        UserSecurityContext admin =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);
        delegations.grant(tenantId, admin.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        service.listProvisioningRequests(admin, student.getId());
    }
}
