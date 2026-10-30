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

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests for Preferences, Accessibility, and IAM Identity Linkage.
 * Covers: STM01-US-023, STM01-US-024, STM01-US-025, STM01-US-040, STM01-US-041
 */
public class StudentPreferencesAndIamTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private UUID studentUserId;
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

        studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Neha");
        req.setLastName("Kapoor");
        req.setPrimaryEmail("neha.kapoor@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
    }

    @Test
    public void testIamAccountLinkageAndIdempotentRelink() {
        // Link student to IAM user ID
        StudentProfileResponse linked = service.linkIamAccount(staffContext, student.getId(), studentUserId);
        assertEquals(studentUserId, linked.getIamUserId());

        // Relinking with same user ID succeeds idempotently
        StudentProfileResponse relinked = service.linkIamAccount(staffContext, student.getId(), studentUserId);
        assertEquals(studentUserId, relinked.getIamUserId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testStudentCannotLinkOwnIamAccountWithoutPrivilege() {
        service.linkIamAccount(studentContext, student.getId(), UUID.randomUUID());
    }

    @Test
    public void testRequestAccountProvisioning() {
        IamLinkageRequest req = new IamLinkageRequest(studentUserId);

        service.requestAccountProvisioning(staffContext, student.getId(), req);
        // Provisioning event is published without error
    }

    @Test
    public void testUpdateStudentPreferencesAndAccessibility() {
        // First link student
        service.linkIamAccount(staffContext, student.getId(), studentUserId);

        StudentPreferencesDto prefs = new StudentPreferencesDto();
        prefs.setLocale("hi_IN");
        prefs.setTimezone("Asia/Kolkata");
        prefs.setCommunicationChannels(Arrays.asList("EMAIL", "SMS"));
        Map<String, Object> accessSettings = new HashMap<>();
        accessSettings.put("contrast", "HIGH");
        prefs.setAccessibilitySettings(accessSettings);

        StudentProfileResponse updated = service.updatePreferences(studentContext, student.getId(), prefs);
        assertNotNull(updated.getPreferences());
        assertEquals("hi_IN", updated.getPreferences().getLocale());
        assertEquals("Asia/Kolkata", updated.getPreferences().getTimezone());
        assertEquals("HIGH", updated.getPreferences().getAccessibilitySettings().get("contrast"));
    }
}
