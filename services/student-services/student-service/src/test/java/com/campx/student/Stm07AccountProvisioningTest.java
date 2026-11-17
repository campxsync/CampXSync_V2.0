package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.*;
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
 * STM-07-US-001 / US-002 / US-004 / US-008 / US-009 / US-010 / US-013 + Path C STM-07.ACCOUNT.A hardening.
 */
public class Stm07AccountProvisioningTest {

    private InMemoryPersonRepository personRepo;
    private InMemoryStudentRepository studentRepo;
    private InMemoryGuardianshipRepository guardianshipRepo;
    private FakeDelegationPort delegations;
    private FakeIamStudentRolePort rolePort;
    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext iamOperator;
    private UserSecurityContext studentContext;
    private UserSecurityContext guardianContext;
    private UserSecurityContext facultyContext;
    private UUID studentUserId;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        personRepo = new InMemoryPersonRepository();
        studentRepo = new InMemoryStudentRepository(personRepo);
        guardianshipRepo = new InMemoryGuardianshipRepository();
        delegations = new FakeDelegationPort();
        rolePort = new FakeIamStudentRolePort();
        service = new StudentDomainService(
                studentRepo, personRepo, guardianshipRepo,
                new com.campx.student.repository.InMemoryCorrectionRequestRepository(),
                new com.campx.student.repository.InMemoryStudentVerificationRepository(),
                new com.campx.student.repository.InMemoryPersonIdentifierRepository(),
                new com.campx.student.repository.InMemoryStudentDocumentRepository(),
                new com.campx.student.repository.InMemoryStudentAddressRepository(),
                new com.campx.student.service.StudentEventPublisher.InMemory(),
                new com.campx.student.repository.InMemoryAuditLogRepository(),
                delegations,
                rolePort);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);
        guardianContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "guardian", false);
        facultyContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "faculty", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Asha");
        req.setLastName("Rao");
        req.setPrimaryEmail("asha.rao@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
        markStudentVerified(student.getId());
        student = service.getStudentById(iamOperator, student.getId());
    }

    private void markStudentVerified(UUID studentId) {
        com.campx.student.model.StudentEntity entity = studentRepo.findById(iamOperator, studentId)
                .orElseThrow(() -> new IllegalStateException("student missing"));
        entity.setVerificationStatus("VERIFIED");
        studentRepo.save(iamOperator, entity);
    }

    private UserSecurityContext registrar() {
        return new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);
    }

    private UserSecurityContext collegeAdmin() {
        return new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);
    }

    @Test
    public void stm07Us002_eligibleActiveStudentPasses() {
        ProvisioningEligibilityResult result =
                service.evaluateProvisioningEligibility(iamOperator, student.getId(), studentUserId);
        assertTrue(result.isEligible());
        assertTrue(result.getFailures().isEmpty());
        assertEquals("NONE", result.getLinkStatus());
        assertEquals("VERIFIED", result.getVerificationStatus());
    }

    @Test
    public void stm07Us001_unverifiedStudentFailsWithNamedCode() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Unverified");
        req.setLastName("Student");
        req.setPrimaryEmail("unverified.student@institution.edu");
        req.setCollegeId(collegeId);
        StudentProfileResponse unverified = service.enrollStudent(iamOperator, req);
        assertEquals("UNVERIFIED", unverified.getVerificationStatus());

        ProvisioningEligibilityResult result =
                service.evaluateProvisioningEligibility(iamOperator, unverified.getId(), studentUserId);
        assertFalse(result.isEligible());
        assertTrue(result.getFailures().contains("VERIFICATION_NOT_VERIFIED"));

        try {
            service.linkIamAccount(iamOperator, unverified.getId(), UUID.randomUUID());
            fail("Expected StudentValidationException for unverified student");
        } catch (StudentValidationException expected) {
            assertTrue(expected.getMessage().contains("VERIFICATION_NOT_VERIFIED"));
        }
    }

    @Test
    public void stm07Us002_suspendedStudentNotEligible() {
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.SUSPENDED);
        status.setReason("Conduct");
        service.transitionLifecycle(iamOperator, student.getId(), status);

        ProvisioningEligibilityResult result =
                service.evaluateProvisioningEligibility(iamOperator, student.getId(), studentUserId);
        assertFalse(result.isEligible());
        assertTrue(result.getFailures().contains("STUDENT_NOT_ACTIVE"));
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us002_studentDeniedEligibilityCheck() {
        service.evaluateProvisioningEligibility(studentContext, student.getId(), studentUserId);
    }

    @Test
    public void stm07Us001_linkRequiresEligibilityAndPersistsStatus() {
        StudentProfileResponse linked = service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        assertEquals(studentUserId, linked.getIamUserId());

        AccountLinkStatusResponse status = service.getAccountLinkStatus(iamOperator, student.getId());
        assertEquals("ACTIVE", status.getLinkStatus());
        assertEquals(studentUserId, status.getLinkedIamUserId());
        assertTrue(status.isPortalAccessActive());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us001_cannotLinkOtherUserWhenAlreadyLinked() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.linkIamAccount(iamOperator, student.getId(), UUID.randomUUID());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us001_cannotLinkIamUserAlreadyUsedElsewhere() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);

        EnrollStudentRequest req2 = new EnrollStudentRequest();
        req2.setFirstName("Other");
        req2.setLastName("Student");
        req2.setPrimaryEmail("other.student@institution.edu");
        req2.setCollegeId(collegeId);
        StudentProfileResponse other = service.enrollStudent(iamOperator, req2);
        markStudentVerified(other.getId());

        service.linkIamAccount(iamOperator, other.getId(), studentUserId);
    }

    @Test
    public void stm07Us013_studentCanViewOwnLinkStatus() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        AccountLinkStatusResponse status = service.getAccountLinkStatus(studentContext, student.getId());
        assertEquals("ACTIVE", status.getLinkStatus());
        assertTrue(status.isPortalAccessActive());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us013_studentCannotViewOtherLinkStatus() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        UserSecurityContext otherStudent =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "student", false);
        service.getAccountLinkStatus(otherStudent, student.getId());
    }

    @Test
    public void stm07Us004_provisioningAllowedWhenEligible() {
        service.requestAccountProvisioning(iamOperator, student.getId());
        AccountLinkStatusResponse status = service.getAccountLinkStatus(iamOperator, student.getId());
        assertEquals(1L, status.getProvisioningRequestCount());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us004_provisioningDeniedWhenAlreadyLinked() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.requestAccountProvisioning(iamOperator, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us004_studentCannotProvision() {
        service.requestAccountProvisioning(studentContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us004_guardianCannotProvision() {
        service.requestAccountProvisioning(guardianContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Hardening_facultyDeniedLink() {
        service.linkIamAccount(facultyContext, student.getId(), studentUserId);
    }

    @Test
    public void stm07Hardening_iamOperatorAllowedWithoutDelegation() {
        StudentProfileResponse linked = service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        assertEquals(studentUserId, linked.getIamUserId());
    }

    @Test
    public void stm07Hardening_registrarAllowedWithDelegation() {
        UserSecurityContext registrar = registrar();
        delegations.grant(tenantId, registrar.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        StudentProfileResponse linked = service.linkIamAccount(registrar, student.getId(), studentUserId);
        assertEquals(studentUserId, linked.getIamUserId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Hardening_registrarDeniedWithoutDelegation() {
        service.linkIamAccount(registrar(), student.getId(), studentUserId);
    }

    @Test
    public void stm07Hardening_collegeAdminAllowedWithDelegation() {
        UserSecurityContext admin = collegeAdmin();
        delegations.grant(tenantId, admin.getUserId(), StudentDomainService.DELEGATION_ACCOUNT_KEY);
        StudentProfileResponse linked = service.linkIamAccount(admin, student.getId(), studentUserId);
        assertEquals(studentUserId, linked.getIamUserId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Hardening_collegeAdminDeniedWithoutDelegation() {
        service.linkIamAccount(collegeAdmin(), student.getId(), studentUserId);
    }

    @Test
    public void stm07Us008_assignsR25WhenLinkedAndActive() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        assertTrue(rolePort.hasAssignment(tenantId, studentUserId));
    }

    @Test
    public void stm07Us008_platformAdminR01CanAssign() {
        UserSecurityContext platformAdmin =
                new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "r01", false);
        service.linkIamAccount(platformAdmin, student.getId(), studentUserId);
        service.assignApprovedStudentRole(platformAdmin, student.getId());
        assertTrue(rolePort.hasAssignment(tenantId, studentUserId));
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us008_requiresLinkBeforeAssign() {
        service.assignApprovedStudentRole(iamOperator, student.getId());
    }

    @Test(expected = StudentValidationException.class)
    public void stm07Us008_requiresActiveStatus() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.GRADUATED);
        status.setReason("Completed");
        service.transitionLifecycle(iamOperator, student.getId(), status);
        // D2: GRADUATED keeps the link but R25 may only be assigned while ACTIVE
        service.assignApprovedStudentRole(iamOperator, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us008_facultyDeniedAssign() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(facultyContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us008_studentDeniedAssign() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(studentContext, student.getId());
    }

    @Test
    public void stm07Us009_revokesR25() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        service.revokeApprovedStudentRole(iamOperator, student.getId());
        assertFalse(rolePort.hasAssignment(tenantId, studentUserId));
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm07Us009_facultyDeniedRevoke() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        service.revokeApprovedStudentRole(facultyContext, student.getId());
    }

    @Test
    public void stm07Us010_suspendRevokesActiveLink() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.SUSPENDED);
        status.setReason("Access review");
        StudentProfileResponse after = service.transitionLifecycle(iamOperator, student.getId(), status);
        assertNull(after.getIamUserId());
        assertFalse(rolePort.hasAssignment(tenantId, studentUserId));
        AccountLinkStatusResponse link = service.getAccountLinkStatus(iamOperator, student.getId());
        assertEquals("NONE", link.getLinkStatus());
        assertFalse(link.isPortalAccessActive());
    }

    @Test
    public void stm07Us010_graduatedDoesNotAutoRevoke() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.GRADUATED);
        status.setReason("Completed programme");
        StudentProfileResponse after = service.transitionLifecycle(iamOperator, student.getId(), status);
        assertEquals(studentUserId, after.getIamUserId());
        assertTrue(rolePort.hasAssignment(tenantId, studentUserId));
    }

    @Test
    public void stm07Us010_detainedRevokesActiveLink() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.DETAINED);
        status.setReason("Academic hold");
        StudentProfileResponse after = service.transitionLifecycle(iamOperator, student.getId(), status);
        assertNull(after.getIamUserId());
        assertEquals("NONE", service.getAccountLinkStatus(iamOperator, student.getId()).getLinkStatus());
    }

    @Test
    public void stm07Us010_withdrawnRevokesActiveLink() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        UpdateStudentStatusRequest status = new UpdateStudentStatusRequest();
        status.setStatus(StudentStatus.WITHDRAWN);
        status.setReason("Left programme");
        StudentProfileResponse after = service.transitionLifecycle(iamOperator, student.getId(), status);
        assertNull(after.getIamUserId());
        assertEquals("NONE", service.getAccountLinkStatus(iamOperator, student.getId()).getLinkStatus());
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
