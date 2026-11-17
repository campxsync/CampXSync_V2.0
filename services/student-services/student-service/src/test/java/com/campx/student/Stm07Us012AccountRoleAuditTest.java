package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.AccountRoleAuditEntry;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-07-US-012 / B3-D6 — audit account link / R25 role changes (R01–R03 only).
 */
public class Stm07Us012AccountRoleAuditTest {

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
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "platform_admin", false);
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Priya");
        req.setLastName("Sen");
        req.setPrimaryEmail("priya.sen@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        StudentTestSupport.markVerified(studentRepo, iamOperator, student.getId());
        student = service.getStudentById(iamOperator, student.getId());

        IamLinkageRequest link = new IamLinkageRequest();
        link.setIamUserId(UUID.randomUUID());
        service.linkIamAccount(iamOperator, student.getId(), link);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        service.revokeApprovedStudentRole(iamOperator, student.getId());

        UpdateStudentStatusRequest statusReq = new UpdateStudentStatusRequest();
        statusReq.setStatus(StudentStatus.SUSPENDED);
        statusReq.setReason("Audit revoke path");
        service.transitionLifecycle(iamOperator, student.getId(), statusReq);
    }

    @Test
    public void stm07Us012_iamOperatorSeesFilteredAccountRoleAudit() {
        List<AccountRoleAuditEntry> rows = service.listAccountAndRoleAuditHistory(iamOperator, 50);
        assertFalse(rows.isEmpty());
        Set<String> actions = new HashSet<String>();
        for (AccountRoleAuditEntry row : rows) {
            actions.add(row.getAction());
            assertFalse("STUDENT_ENROLLED".equals(row.getAction()));
            assertFalse("STATUS_CHANGED".equals(row.getAction()));
        }
        assertTrue(actions.contains("IAM_LINKED"));
        assertTrue(actions.contains("STUDENT_ROLE_ASSIGNED"));
        assertTrue(actions.contains("STUDENT_ROLE_REVOKED"));
        assertTrue(actions.contains("IAM_ACCESS_REVOKED"));
    }

    @Test
    public void stm07Us012_r03CanReadAudit() {
        UserSecurityContext r03 =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r03", false);
        List<AccountRoleAuditEntry> rows = service.listAccountAndRoleAuditHistory(r03, 20);
        assertFalse(rows.isEmpty());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us012_registrarDeniedEvenWithDelegation() {
        UserSecurityContext registrar =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        service.listAccountAndRoleAuditHistory(registrar, 20);
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us012_facultyDeniedAudit() {
        UserSecurityContext faculty =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);
        service.listAccountAndRoleAuditHistory(faculty, 20);
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us012_collegeAdminDeniedAudit() {
        UserSecurityContext admin =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);
        service.listAccountAndRoleAuditHistory(admin, 20);
    }
}
