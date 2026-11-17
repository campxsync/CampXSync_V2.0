package com.campx.student;

import com.campx.student.model.AccountLinkStatusResponse;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.IamLinkageRequest;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.model.StudentStatus;
import com.campx.student.model.UpdateStudentStatusRequest;
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
 * STM-07-EN-005 — integration contracts that already exist inside student-service:
 * student record + portal link-status + lifecycle access cut (STM-01 / STM-10 / STM-04-via-student-status).
 */
public class Stm07En005IntegrationContractTest {

    private StudentDomainService service;
    private UserSecurityContext iamOperator;
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
        UUID tenantId = UUID.randomUUID();
        UUID collegeId = UUID.randomUUID();
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Integration");
        req.setLastName("Pilot");
        req.setPrimaryEmail("integration.pilot@institution.edu");
        req.setCollegeId(collegeId);
        req.setAdmissionNo("ADM-EN005-001");
        student = service.enrollStudent(iamOperator, req);
        StudentTestSupport.markVerified(studentRepo, iamOperator, student.getId());
        student = service.getStudentById(iamOperator, student.getId());
    }

    @Test
    public void stm07En005_studentRecordCarriesAdmissionNoForHandoverJoin() {
        assertEquals("ADM-EN005-001", student.getAdmissionNo());
        assertEquals(StudentStatus.ACTIVE, student.getStatus());
    }

    @Test
    public void stm07En005_portalLinkStatusActiveAfterVerifiedLink() {
        IamLinkageRequest link = new IamLinkageRequest();
        link.setIamUserId(UUID.randomUUID());
        service.linkIamAccount(iamOperator, student.getId(), link);
        AccountLinkStatusResponse status = service.getAccountLinkStatus(iamOperator, student.getId());
        assertEquals("ACTIVE", status.getLinkStatus());
        assertTrue(status.isPortalAccessActive());
    }

    @Test
    public void stm07En005_lifecycleSuspendCutsPortalAccessOnStudentStatusPath() {
        IamLinkageRequest link = new IamLinkageRequest();
        link.setIamUserId(UUID.randomUUID());
        service.linkIamAccount(iamOperator, student.getId(), link);
        UpdateStudentStatusRequest suspend = new UpdateStudentStatusRequest();
        suspend.setStatus(StudentStatus.SUSPENDED);
        suspend.setReason("EN-005 lifecycle cut");
        service.transitionLifecycle(iamOperator, student.getId(), suspend);
        AccountLinkStatusResponse status = service.getAccountLinkStatus(iamOperator, student.getId());
        assertEquals("NONE", status.getLinkStatus());
        assertFalse(status.isPortalAccessActive());
    }
}
