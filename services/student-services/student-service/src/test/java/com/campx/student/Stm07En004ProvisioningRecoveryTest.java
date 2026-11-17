package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.exception.StudentValidationException;
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
 * STM-07-EN-004 — idempotent provisioning, retry of FAILED requests, reconciliation markers.
 */
public class Stm07En004ProvisioningRecoveryTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext iamOperator;
    private UserSecurityContext faculty;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        service = new StudentDomainService(
                studentRepo, personRepo, new InMemoryGuardianshipRepository(),
                new com.campx.student.repository.InMemoryCorrectionRequestRepository(),
                new com.campx.student.repository.InMemoryStudentVerificationRepository(),
                new com.campx.student.repository.InMemoryPersonIdentifierRepository(),
                new com.campx.student.repository.InMemoryStudentDocumentRepository(),
                new com.campx.student.repository.InMemoryStudentAddressRepository(),
                new com.campx.student.service.StudentEventPublisher.InMemory(),
                new com.campx.student.repository.InMemoryAuditLogRepository(),
                new FakeDelegationPort(),
                new FakeIamStudentRolePort());
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        faculty = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Kiran");
        req.setLastName("Das");
        req.setPrimaryEmail("kiran.das@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        com.campx.student.model.StudentEntity entity = studentRepo.findById(iamOperator, student.getId()).get();
        entity.setVerificationStatus("VERIFIED");
        studentRepo.save(iamOperator, entity);
        student = service.getStudentById(iamOperator, student.getId());
    }

    @Test
    public void stm07En004_idempotentProvisioningDoesNotDuplicateOpenRequest() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        service.requestAccountProvisioning(iamOperator, student.getId());
        List<ProvisioningRequestRecord> list = service.listProvisioningRequests(iamOperator, student.getId());
        assertEquals(1, list.size());
        assertEquals("REQUESTED", list.get(0).getRequestStatus());
    }

    @Test
    public void stm07En004_retryFailedRequestReopensRequested() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        UUID requestId = service.listProvisioningRequests(iamOperator, student.getId()).get(0).getId();
        service.markProvisioningFailed(iamOperator, student.getId(), requestId, "IAM_TIMEOUT");
        ProvisioningRequestRecord retried =
                service.retryFailedProvisioningRequest(iamOperator, student.getId(), requestId);
        assertEquals("REQUESTED", retried.getRequestStatus());
        assertNull(retried.getFailureReason());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07En004_cannotRetryNonFailedRequest() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        UUID requestId = service.listProvisioningRequests(iamOperator, student.getId()).get(0).getId();
        service.retryFailedProvisioningRequest(iamOperator, student.getId(), requestId);
    }

    @Test
    public void stm07En004_markCompletedForReconciliation() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        UUID requestId = service.listProvisioningRequests(iamOperator, student.getId()).get(0).getId();
        service.markProvisioningCompleted(iamOperator, student.getId(), requestId);
        assertEquals("COMPLETED",
                service.listProvisioningRequests(iamOperator, student.getId()).get(0).getRequestStatus());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07En004_facultyDeniedRetry() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        UUID requestId = service.listProvisioningRequests(iamOperator, student.getId()).get(0).getId();
        service.markProvisioningFailed(iamOperator, student.getId(), requestId, "IAM_TIMEOUT");
        service.retryFailedProvisioningRequest(faculty, student.getId(), requestId);
    }
}
