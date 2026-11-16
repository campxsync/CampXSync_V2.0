package com.campx.student;

import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.FailClosedIamStudentRolePort;
import com.campx.student.security.FakeDelegationPort;
import com.campx.student.security.FakeIamStudentRolePort;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-07-EN-002 — existing IAM integration only; no duplicate auth / credential / role system.
 */
public class Stm07En002IamIntegrationGuardTest {

    private InMemoryPersonRepository personRepo;
    private InMemoryStudentRepository studentRepo;
    private FakeDelegationPort delegations;
    private FakeIamStudentRolePort rolePort;
    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext iamOperator;
    private UUID studentUserId;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        personRepo = new InMemoryPersonRepository();
        studentRepo = new InMemoryStudentRepository(personRepo);
        delegations = new FakeDelegationPort();
        rolePort = new FakeIamStudentRolePort();
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
                rolePort);
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        iamOperator = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        studentUserId = UUID.randomUUID();
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Ira");
        req.setLastName("Mehta");
        req.setPrimaryEmail("ira.mehta@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(iamOperator, req);
    }

    @Test
    public void stm07En002_roleAssignGoesThroughIamPortOnly() {
        service.linkIamAccount(iamOperator, student.getId(), studentUserId);
        service.assignApprovedStudentRole(iamOperator, student.getId());
        assertTrue(rolePort.hasAssignment(tenantId, studentUserId));
    }

    @Test(expected = StudentValidationException.class)
    public void stm07En002_failClosedWhenIamPortUnavailable() {
        StudentDomainService closed = new StudentDomainService(
                studentRepo, personRepo, new InMemoryGuardianshipRepository(),
                new com.campx.student.repository.InMemoryCorrectionRequestRepository(),
                new com.campx.student.repository.InMemoryStudentVerificationRepository(),
                new com.campx.student.repository.InMemoryPersonIdentifierRepository(),
                new com.campx.student.repository.InMemoryStudentDocumentRepository(),
                new com.campx.student.repository.InMemoryStudentAddressRepository(),
                new com.campx.student.service.StudentEventPublisher.InMemory(),
                new com.campx.student.repository.InMemoryAuditLogRepository(),
                delegations,
                new FailClosedIamStudentRolePort());
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Jay");
        req.setLastName("Shah");
        req.setPrimaryEmail("jay.shah@institution.edu");
        req.setCollegeId(collegeId);
        StudentProfileResponse other = closed.enrollStudent(iamOperator, req);
        UUID otherIam = UUID.randomUUID();
        closed.linkIamAccount(iamOperator, other.getId(), otherIam);
        closed.assignApprovedStudentRole(iamOperator, other.getId());
    }

    @Test
    public void stm07En002_domainServiceHasNoLocalCredentialOrRoleApis() {
        Method[] methods = StudentDomainService.class.getDeclaredMethods();
        for (Method m : methods) {
            String name = m.getName().toLowerCase();
            assertFalse("No password API expected: " + m.getName(), name.contains("password"));
            assertFalse("No credential mint API expected: " + m.getName(), name.contains("credential"));
            assertFalse("No auth.users mint API expected: " + m.getName(), name.contains("createauth"));
            assertFalse("No local iam schema writer expected: " + m.getName(), name.contains("writeroleassignment"));
        }
    }

    @Test
    public void stm07En002_noLocalRoleAssignmentFieldOnDomainService() throws Exception {
        for (Field f : StudentDomainService.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            assertFalse(name.contains("roleassignment") || name.contains("role_bindings"));
            // Port field is allowed; local maps of roles are not.
            if (name.contains("role") && !name.contains("port")) {
                fail("Unexpected local role field: " + f.getName());
            }
        }
        assertTrue(Arrays.stream(StudentDomainService.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("iamStudentRolePort")));
    }
}
