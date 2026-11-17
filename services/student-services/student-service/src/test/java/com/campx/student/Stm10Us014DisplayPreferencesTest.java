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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** STM-10-US-014 — Configure language and display preferences. */
public class Stm10Us014DisplayPreferencesTest {

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
        student = enroll(studentRepo, "Disp", "One", "disp.one@institution.edu", studentUserId);
        otherStudent = enroll(studentRepo, "Disp", "Two", "disp.two@institution.edu", otherUserId);
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
    public void stm10Us014_languageAndDisplayPersistAcrossReload() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        PortalPreferencesDto.DisplayPreferences display = new PortalPreferencesDto.DisplayPreferences();
        display.setTheme("dark");
        display.setLocale("hi-IN");
        display.setTimezone("Asia/Kolkata");
        display.setTableDensity("compact");
        Map<String, Boolean> access = new HashMap<>();
        access.put("reducedMotion", true);
        display.setAccessibility(access);
        prefs.setDisplay(display);

        service.savePortalPreferences(studentContext, student.getId(), prefs);
        PortalPreferencesDto reloaded = service.getPortalPreferences(studentContext, student.getId());
        assertEquals("dark", reloaded.getDisplay().getTheme());
        assertEquals("hi-IN", reloaded.getDisplay().getLocale());
        assertEquals("compact", reloaded.getDisplay().getTableDensity());
        assertEquals(Boolean.TRUE, reloaded.getDisplay().getAccessibility().get("reducedMotion"));
    }

    @Test
    public void stm10Us014_otherStudentKeepsDefaultDisplay() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        PortalPreferencesDto.DisplayPreferences display = new PortalPreferencesDto.DisplayPreferences();
        display.setTheme("dark");
        display.setLocale("ta-IN");
        prefs.setDisplay(display);
        prefs.setShortcuts(Collections.<PortalPreferencesDto.ShortcutItem>emptyList());
        service.savePortalPreferences(studentContext, student.getId(), prefs);

        PortalPreferencesDto other = service.getPortalPreferences(otherStudentContext, otherStudent.getId());
        assertEquals("light", other.getDisplay().getTheme());
        assertEquals("en-IN", other.getDisplay().getLocale());
    }

    @Test(expected = StudentValidationException.class)
    public void stm10Us014_invalidLocaleRejected() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        PortalPreferencesDto.DisplayPreferences display = new PortalPreferencesDto.DisplayPreferences();
        display.setLocale("!!!bad!!!");
        prefs.setDisplay(display);
        service.savePortalPreferences(studentContext, student.getId(), prefs);
    }
}
