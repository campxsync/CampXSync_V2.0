package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.*;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests for Guardian Relationships, Consent Permissions, and Access Control.
 * Covers: STM01-US-018, STM01-US-019, STM01-US-048
 */
public class StudentGuardianAndConsentTest {

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
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        UUID studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Ananya");
        req.setLastName("Roy");
        req.setPrimaryEmail("ananya.roy@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
        service.linkIamAccount(staffContext, student.getId(), studentUserId);
    }

    @Test
    public void testLinkGuardianWithConsentFlags() {
        EnrollStudentRequest.GuardianRequest gReq = new EnrollStudentRequest.GuardianRequest();
        gReq.setFirstName("Sunil");
        gReq.setLastName("Roy");
        gReq.setRelationship("FATHER");
        gReq.setPrimary(true);
        gReq.setPrimaryEmail("sunil.roy@example.com");
        gReq.setPrimaryPhone("+919876543210");
        gReq.setCanViewAcademics(true);
        gReq.setCanViewFees(true);
        gReq.setCanPayFees(false);

        StudentProfileResponse resp = service.linkGuardian(staffContext, student.getId(), gReq);
        assertNotNull(resp);
        assertFalse(resp.getGuardians().isEmpty());
        StudentProfileResponse.GuardianSummary summary = resp.getGuardians().get(0);
        assertNotNull(summary.getGuardianshipId());
        assertEquals("Sunil Roy", summary.getDisplayName());
        assertEquals("FATHER", summary.getRelationship());
        assertTrue(summary.isPrimary());

        List<StudentProfileResponse.GuardianSummary> list = service.getGuardians(studentContext, student.getId());
        assertEquals(1, list.size());
        assertEquals("Sunil Roy", list.get(0).getDisplayName());
    }

    @Test
    public void testUpdateGuardianConsentPermissions() {
        EnrollStudentRequest.GuardianRequest gReq = new EnrollStudentRequest.GuardianRequest();
        gReq.setFirstName("Sunita");
        gReq.setLastName("Roy");
        gReq.setRelationship("MOTHER");
        gReq.setCanViewAcademics(false);
        gReq.setCanViewFees(false);
        gReq.setCanPayFees(false);

        StudentProfileResponse created = service.linkGuardian(staffContext, student.getId(), gReq);
        assertFalse(created.getGuardians().isEmpty());
        UUID gId = created.getGuardians().get(0).getGuardianshipId();

        // Update consent: grant view fees and pay fees
        EnrollStudentRequest.GuardianRequest updateReq = new EnrollStudentRequest.GuardianRequest();
        updateReq.setRelationship("MOTHER");
        updateReq.setCanViewAcademics(true);
        updateReq.setCanViewFees(true);
        updateReq.setCanPayFees(true);

        StudentProfileResponse updated = service.updateGuardian(
                staffContext, student.getId(), gId, updateReq
        );
        assertNotNull(updated);
        assertEquals("MOTHER", updated.getGuardians().get(0).getRelationship());
        assertTrue(updated.getGuardians().get(0).isCanViewAcademics());
        assertTrue(updated.getGuardians().get(0).isCanViewFees());
        assertTrue(updated.getGuardians().get(0).isCanPayFees());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testStudentCannotLinkGuardianDirectlyWithoutStaffPrivilege() {
        EnrollStudentRequest.GuardianRequest gReq = new EnrollStudentRequest.GuardianRequest();
        gReq.setFirstName("Unauthorized");
        gReq.setLastName("Guardian");

        // Student tries to link guardian directly
        service.linkGuardian(studentContext, student.getId(), gReq);
    }
}
