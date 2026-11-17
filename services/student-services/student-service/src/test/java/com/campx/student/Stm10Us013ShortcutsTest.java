package com.campx.student;

import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.PortalPreferencesDto;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

/** STM-10-US-013 — Save shortcuts. */
public class Stm10Us013ShortcutsTest {

    private StudentDomainService service;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private UserSecurityContext otherStudentContext;
    private StudentProfileResponse student;
    private StudentProfileResponse otherStudent;
    private UUID collegeId;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        service = new StudentDomainService(studentRepo, personRepo, new InMemoryGuardianshipRepository());
        UUID tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        UUID studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);
        UUID otherUserId = UUID.randomUUID();
        otherStudentContext = new UserSecurityContext(otherUserId, tenantId, collegeId, null, "student", false);
        student = enroll(studentRepo, "Short", "One", "short.one@institution.edu", studentUserId);
        otherStudent = enroll(studentRepo, "Short", "Two", "short.two@institution.edu", otherUserId);
    }

    private StudentProfileResponse enroll(InMemoryStudentRepository studentRepo, String first, String last,
                                          String email, UUID iamUserId) {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName(first);
        req.setLastName(last);
        req.setPrimaryEmail(email);
        req.setCollegeId(collegeId);
        StudentProfileResponse created = service.enrollStudent(staffContext, req);
        StudentTestSupport.markVerified(studentRepo, staffContext, created.getId());
        service.linkIamAccount(staffContext, created.getId(), iamUserId);
        return service.getStudentById(staffContext, created.getId());
    }

    @Test
    public void stm10Us013_shortcutsPersistAcrossReload() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        prefs.setShortcuts(Collections.singletonList(
                new PortalPreferencesDto.ShortcutItem("docs", "Documents", "/student/documents")));
        service.savePortalPreferences(studentContext, student.getId(), prefs);
        PortalPreferencesDto reloaded = service.getPortalPreferences(studentContext, student.getId());
        assertEquals(1, reloaded.getShortcuts().size());
        assertEquals("/student/documents", reloaded.getShortcuts().get(0).getPath());
    }

    @Test
    public void stm10Us013_otherStudentDoesNotSeeShortcuts() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        prefs.setShortcuts(Collections.singletonList(
                new PortalPreferencesDto.ShortcutItem("life", "Lifecycle", "/student/lifecycle")));
        service.savePortalPreferences(studentContext, student.getId(), prefs);
        PortalPreferencesDto other = service.getPortalPreferences(otherStudentContext, otherStudent.getId());
        assertTrue(other.getShortcuts() == null || other.getShortcuts().isEmpty());
    }

    @Test(expected = StudentValidationException.class)
    public void stm10Us013_externalShortcutPathRejected() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        prefs.setShortcuts(Collections.singletonList(
                new PortalPreferencesDto.ShortcutItem("x", "Bad", "https://evil.example")));
        service.savePortalPreferences(studentContext, student.getId(), prefs);
    }
}
