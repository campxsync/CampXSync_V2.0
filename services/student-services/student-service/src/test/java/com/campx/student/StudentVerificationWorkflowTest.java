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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests for Verification Initiation, Decisions, and Expiry Tracking.
 * Covers: STM01-US-013, STM01-US-014, STM01-US-047
 */
public class StudentVerificationWorkflowTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private StudentProfileResponse student;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "verifier", false);

        UUID studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Meera");
        req.setLastName("Nair");
        req.setPrimaryEmail("meera.nair@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
    }

    @Test
    public void testVerifierCanInitiateVerification() {
        InitiateVerificationRequest req = new InitiateVerificationRequest();
        req.setVerificationType("ACADEMIC_CREDENTIALS");
        req.setEvidenceReference("TRANSCRIPT_DOC_123");

        StudentVerificationEntity result = service.initiateVerification(staffContext, student.getId(), req);
        assertNotNull(result.getId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("ACADEMIC_CREDENTIALS", result.getVerificationType());
        assertFalse(result.isExpired());

        List<StudentVerificationEntity> history = service.getVerificationHistory(staffContext, student.getId());
        assertEquals(1, history.size());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testStudentCannotInitiateVerification() {
        InitiateVerificationRequest req = new InitiateVerificationRequest();
        req.setVerificationType("IDENTITY_DOCUMENTS");

        service.initiateVerification(studentContext, student.getId(), req);
    }

    @Test
    public void testVerifierCanApproveVerificationWithExpiry() {
        InitiateVerificationRequest initReq = new InitiateVerificationRequest();
        initReq.setVerificationType("IDENTITY_DOCUMENTS");
        initReq.setEvidenceReference("PASSPORT_DOC_999");

        StudentVerificationEntity initiated = service.initiateVerification(staffContext, student.getId(), initReq);

        DecideVerificationRequest decideReq = new DecideVerificationRequest();
        decideReq.setStatus("VERIFIED");
        decideReq.setReason("Passport and marksheet verified");
        decideReq.setValidTo(LocalDate.now().plusMonths(6).toString());

        StudentVerificationEntity decided = service.decideVerification(staffContext, initiated.getId(), decideReq);
        assertEquals("VERIFIED", decided.getStatus());
        assertEquals(staffContext.getUserId(), decided.getVerifiedBy());
        assertNotNull(decided.getVerifiedAt());
        assertNotNull(decided.getValidTo());
        assertFalse(decided.isExpired());

        // Student's overall status should be VERIFIED
        StudentProfileResponse profile = service.getStudentProfile(staffContext, student.getId());
        assertEquals("VERIFIED", profile.getVerificationStatus());
    }

    @Test
    public void testVerifierCanRejectVerification() {
        InitiateVerificationRequest initReq = new InitiateVerificationRequest();
        initReq.setVerificationType("FINANCIAL_DOCUMENTS");

        StudentVerificationEntity initiated = service.initiateVerification(staffContext, student.getId(), initReq);

        DecideVerificationRequest decideReq = new DecideVerificationRequest();
        decideReq.setStatus("REJECTED");
        decideReq.setReason("Invalid income certificate");

        StudentVerificationEntity decided = service.decideVerification(staffContext, initiated.getId(), decideReq);
        assertEquals("REJECTED", decided.getStatus());

        StudentProfileResponse profile = service.getStudentProfile(staffContext, student.getId());
        assertEquals("REJECTED", profile.getVerificationStatus());
    }

    @Test
    public void testVerificationExpiryDetection() {
        StudentVerificationEntity entity = new StudentVerificationEntity();
        entity.setStatus("VERIFIED");
        entity.setValidTo(LocalDate.now().minusDays(1)); // Expired yesterday

        assertTrue(entity.isExpired());
    }
}
