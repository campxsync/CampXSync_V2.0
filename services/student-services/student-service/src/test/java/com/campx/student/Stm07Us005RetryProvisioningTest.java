package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.IamLinkageRequest;
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

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-07-US-005 — Retry failed login setup (FAILED → REQUESTED).
 */
public class Stm07Us005RetryProvisioningTest {

    private InMemoryStudentRepository studentRepo;
    private FakeDelegationPort delegations;
    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext iamOperator;
    private StudentProfileResponse student;
    private UUID failedRequestId;

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
        req.setFirstName("Arjun");
        req.setLastName("Iyer");
        req.setPrimaryEmail("arjun.iyer@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        StudentTestSupport.markVerified(studentRepo, iamOperator, student.getId());
        student = service.getStudentById(iamOperator, student.getId());
        service.requestAccountProvisioning(iamOperator, student.getId());
        failedRequestId = service.listProvisioningRequests(iamOperator, student.getId()).get(0).getId();
        service.markProvisioningFailed(iamOperator, student.getId(), failedRequestId, "IAM_TIMEOUT");
    }

    @Test
    public void stm07Us005_iamOperatorRetriesFailedLoginSetup() {
        ProvisioningRequestRecord retried =
                service.retryFailedProvisioningRequest(iamOperator, student.getId(), failedRequestId);
        assertEquals("REQUESTED", retried.getRequestStatus());
        assertNull(retried.getFailureReason());
    }

    @Test
    public void stm07Us005_registrarWithDelegationCanRetry() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        ProvisioningRequestRecord retried =
                service.retryFailedProvisioningRequest(registrar, student.getId(), failedRequestId);
        assertEquals("REQUESTED", retried.getRequestStatus());
    }

    @Test
    public void stm07Us005_collegeAdminWithDelegationCanRetry() {
        UserSecurityContext admin =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);
        delegations.grant(tenantId, admin.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        ProvisioningRequestRecord retried =
                service.retryFailedProvisioningRequest(admin, student.getId(), failedRequestId);
        assertEquals("REQUESTED", retried.getRequestStatus());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us005_registrarWithoutDelegationDenied() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r31", false);
        service.retryFailedProvisioningRequest(registrar, student.getId(), failedRequestId);
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us005_facultyDeniedRetry() {
        UserSecurityContext faculty =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);
        service.retryFailedProvisioningRequest(faculty, student.getId(), failedRequestId);
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us005_cannotRetryWhenAlreadyLinked() {
        IamLinkageRequest link = new IamLinkageRequest();
        link.setIamUserId(UUID.randomUUID());
        service.linkIamAccount(iamOperator, student.getId(), link);
        service.retryFailedProvisioningRequest(iamOperator, student.getId(), failedRequestId);
    }
}
