package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.*;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests for Profile Correction Requests, Approval Workflows, and Segregation of Duties.
 * Covers: STM01-US-010, STM01-US-011, STM01-US-012, STM01-US-037
 */
public class StudentCorrectionWorkflowTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private StudentProfileResponse student;
    private UUID studentUserId;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "registrar", false);

        studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Pooja");
        req.setLastName("Hegde");
        req.setPrimaryEmail("pooja.hegde@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
    }

    @Test
    public void testStudentCanSubmitCorrectionRequestForProtectedField() {
        SubmitCorrectionRequest req = new SubmitCorrectionRequest();
        req.setTargetField("firstName");
        req.setProposedValue("Pooja Shri");
        req.setReason("Official gazette correction");

        StudentCorrectionRequestEntity result = service.submitCorrectionRequest(studentContext, student.getId(), req);
        assertNotNull(result.getId());
        assertEquals(student.getId(), result.getStudentId());
        assertEquals("firstName", result.getTargetField());
        assertEquals("Pooja", result.getOldValue());
        assertEquals("Pooja Shri", result.getNewValue());
        assertEquals("PENDING", result.getStatus());

        List<StudentCorrectionRequestEntity> list = service.getCorrectionRequests(studentContext, student.getId());
        assertEquals(1, list.size());
    }

    @Test(expected = StudentValidationException.class)
    public void testCorrectionRequestMissingReasonRejected() {
        SubmitCorrectionRequest req = new SubmitCorrectionRequest();
        req.setTargetField("lastName");
        req.setProposedValue("Rao");
        // missing reason
        service.submitCorrectionRequest(studentContext, student.getId(), req);
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testSegregationOfDutiesSubmitterCannotApproveOwnRequest() {
        SubmitCorrectionRequest req = new SubmitCorrectionRequest();
        req.setTargetField("firstName");
        req.setProposedValue("Pooja Shri");
        req.setReason("Official gazette correction");

        StudentCorrectionRequestEntity created = service.submitCorrectionRequest(studentContext, student.getId(), req);

        // Submitter attempts to approve
        DecideCorrectionRequest decision = new DecideCorrectionRequest();
        decision.setDecision("APPROVED");
        decision.setReason("Self-approving");

        service.decideCorrectionRequest(studentContext, created.getId(), decision);
    }

    @Test
    public void testRegistrarCanApproveAndApplyCorrection() {
        SubmitCorrectionRequest req = new SubmitCorrectionRequest();
        req.setTargetField("firstName");
        req.setProposedValue("Pooja Lakshmi");
        req.setReason("Official gazette certificate attached");

        StudentCorrectionRequestEntity created = service.submitCorrectionRequest(studentContext, student.getId(), req);

        DecideCorrectionRequest decision = new DecideCorrectionRequest();
        decision.setDecision("APPROVED");
        decision.setReason("Verified against gazette document");

        StudentCorrectionRequestEntity decided = service.decideCorrectionRequest(staffContext, created.getId(), decision);
        assertEquals("APPROVED", decided.getStatus());
        assertEquals("Verified against gazette document", decided.getDecisionReason());

        // Verify that profile was actually updated
        StudentProfileResponse updatedProfile = service.getStudentProfile(staffContext, student.getId());
        assertEquals("Pooja Lakshmi", updatedProfile.getFirstName());
    }

    @Test
    public void testRegistrarCanRejectCorrectionRequest() {
        SubmitCorrectionRequest req = new SubmitCorrectionRequest();
        req.setTargetField("firstName");
        req.setProposedValue("InvalidName");
        req.setReason("Mistake");

        StudentCorrectionRequestEntity created = service.submitCorrectionRequest(studentContext, student.getId(), req);

        DecideCorrectionRequest decision = new DecideCorrectionRequest();
        decision.setDecision("REJECTED");
        decision.setReason("Insufficient documentary proof");

        StudentCorrectionRequestEntity decided = service.decideCorrectionRequest(staffContext, created.getId(), decision);
        assertEquals("REJECTED", decided.getStatus());

        // Profile remains unchanged
        StudentProfileResponse profile = service.getStudentProfile(staffContext, student.getId());
        assertEquals("Pooja", profile.getFirstName());
    }
}
