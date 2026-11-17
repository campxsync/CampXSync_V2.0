package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.repository.InMemoryAuditLogRepository;
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
 * STM-07-EN-006 — security readiness negatives for account setup (no production claim).
 */
public class Stm07En006SecurityReadinessTest {

    private FakeDelegationPort delegations;
    private InMemoryAuditLogRepository audit;
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
        audit = new InMemoryAuditLogRepository();
        service = new StudentDomainService(
                studentRepo, personRepo, new InMemoryGuardianshipRepository(),
                new com.campx.student.repository.InMemoryCorrectionRequestRepository(),
                new com.campx.student.repository.InMemoryStudentVerificationRepository(),
                new com.campx.student.repository.InMemoryPersonIdentifierRepository(),
                new com.campx.student.repository.InMemoryStudentDocumentRepository(),
                new com.campx.student.repository.InMemoryStudentAddressRepository(),
                new com.campx.student.service.StudentEventPublisher.InMemory(),
                audit,
                delegations,
                new FakeIamStudentRolePort());
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r01", false);
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Ready");
        req.setLastName("Check");
        req.setPrimaryEmail("ready.check@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        StudentTestSupport.markVerified(studentRepo, iamOperator, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07En006_facultyDeniedProvisioning() {
        UserSecurityContext faculty =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);
        service.requestAccountProvisioning(faculty, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07En006_studentDeniedProvisioning() {
        UserSecurityContext studentCtx =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "student", false);
        service.requestAccountProvisioning(studentCtx, student.getId());
    }

    @Test
    public void stm07En006_privilegedProvisioningIsAudited() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        assertFalse(audit.getAuditRecords().isEmpty());
        boolean found = false;
        for (com.campx.platform.audit.AuditRecord r : audit.getAuditRecords()) {
            if ("IAM_PROVISIONING_REQUESTED".equals(r.getAction())) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07En006_registrarWithoutDelegationDeniedAuditList() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);
        service.listAccountAndRoleAuditHistory(registrar, 20);
    }
}
