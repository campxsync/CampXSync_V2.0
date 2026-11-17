package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-10-EN-001 — portal preferences persistence store (widgets / shortcuts / display).
 */
public class Stm10PortalPreferencesEn001Test {

    private StudentDomainService service;
    private InMemoryStudentRepository studentRepo;
    private UUID tenantId;
    private UUID collegeId;
    private UUID otherCollegeId;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private UserSecurityContext otherStudentContext;
    private UserSecurityContext crossCollegeStudentContext;
    private UUID studentUserId;
    private UUID otherStudentUserId;
    private StudentProfileResponse student;
    private StudentProfileResponse otherStudent;
    private StudentProfileResponse crossCollegeStudent;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        studentRepo = new InMemoryStudentRepository(personRepo);
        service = new StudentDomainService(studentRepo, personRepo, new InMemoryGuardianshipRepository());

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        otherCollegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);

        studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);
        otherStudentUserId = UUID.randomUUID();
        otherStudentContext = new UserSecurityContext(otherStudentUserId, tenantId, collegeId, null, "student", false);
        crossCollegeStudentContext = new UserSecurityContext(UUID.randomUUID(), tenantId, otherCollegeId, null, "student", false);

        student = enrollLinked("Ada", "Student", "ada.student@institution.edu", collegeId, studentUserId, studentContext);
        otherStudent = enrollLinked("Other", "Student", "other.student@institution.edu", collegeId, otherStudentUserId, otherStudentContext);
        crossCollegeStudent = enrollLinked("Cross", "College", "cross.college@institution.edu", otherCollegeId,
                crossCollegeStudentContext.getUserId(), crossCollegeStudentContext);
    }

    private StudentProfileResponse enrollLinked(String first, String last, String email, UUID college,
                                                UUID iamUserId, UserSecurityContext studentCtx) {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName(first);
        req.setLastName(last);
        req.setPrimaryEmail(email);
        req.setCollegeId(college);
        StudentProfileResponse created = service.enrollStudent(staffContext, req);
        StudentTestSupport.markVerified(studentRepo, staffContext, created.getId());
        service.linkIamAccount(staffContext, created.getId(), iamUserId);
        return service.getStudentById(staffContext, created.getId());
    }

    @Test
    public void stm10En001_saveAndReloadPortalPreferences() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        prefs.setWidgets(widgetConfig(Arrays.asList("profile", "courses", "kpis"), Arrays.asList("notifications")));
        prefs.setShortcuts(Collections.singletonList(
                new PortalPreferencesDto.ShortcutItem("docs", "My Documents", "/student/documents")));
        PortalPreferencesDto.DisplayPreferences display = new PortalPreferencesDto.DisplayPreferences();
        display.setTheme("dark");
        display.setLocale("hi-IN");
        display.setTimezone("Asia/Kolkata");
        display.setTableDensity("compact");
        Map<String, Boolean> access = new HashMap<>();
        access.put("highContrast", true);
        display.setAccessibility(access);
        prefs.setDisplay(display);

        PortalPreferencesDto saved = service.savePortalPreferences(studentContext, student.getId(), prefs);
        assertEquals("dark", saved.getDisplay().getTheme());
        assertEquals("hi-IN", saved.getDisplay().getLocale());
        assertEquals(1, saved.getShortcuts().size());
        assertTrue(saved.getWidgets().getHidden().contains("notifications"));

        PortalPreferencesDto reloaded = service.getPortalPreferences(studentContext, student.getId());
        assertEquals("dark", reloaded.getDisplay().getTheme());
        assertEquals("/student/documents", reloaded.getShortcuts().get(0).getPath());
        assertEquals(collegeId, reloaded.getCollegeId());
        assertEquals(student.getId(), reloaded.getStudentId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm10En001_studentCannotReadAnotherStudentsPreferences() {
        service.savePortalPreferences(studentContext, student.getId(), samplePrefs());
        service.getPortalPreferences(otherStudentContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm10En001_studentCannotWriteAnotherStudentsPreferences() {
        service.savePortalPreferences(otherStudentContext, student.getId(), samplePrefs());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm10En001_crossCollegeStudentDenied() {
        service.getPortalPreferences(crossCollegeStudentContext, student.getId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm10En001_unauthenticatedDenied() {
        UserSecurityContext anon = new UserSecurityContext(null, tenantId, collegeId, null, "student", false);
        service.getPortalPreferences(anon, student.getId());
    }

    @Test(expected = StudentValidationException.class)
    public void stm10En001_invalidWidgetRejected() {
        PortalPreferencesDto prefs = samplePrefs();
        prefs.setWidgets(widgetConfig(Collections.singletonList("not-a-real-widget"), Collections.<String>emptyList()));
        service.savePortalPreferences(studentContext, student.getId(), prefs);
    }

    @Test(expected = StudentValidationException.class)
    public void stm10En001_invalidThemeRejected() {
        PortalPreferencesDto prefs = samplePrefs();
        prefs.getDisplay().setTheme("neon");
        service.savePortalPreferences(studentContext, student.getId(), prefs);
    }

    @Test(expected = StudentValidationException.class)
    public void stm10En001_invalidShortcutPathRejected() {
        PortalPreferencesDto prefs = samplePrefs();
        prefs.setShortcuts(Collections.singletonList(
                new PortalPreferencesDto.ShortcutItem("x", "External", "https://evil.example")));
        service.savePortalPreferences(studentContext, student.getId(), prefs);
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void stm10En001_staffCannotUsePortalPreferencesStore() {
        service.getPortalPreferences(staffContext, student.getId());
    }

    @Test
    public void stm10En001_secondStudentDoesNotSeeFirstStudentsPreferences() {
        service.savePortalPreferences(studentContext, student.getId(), samplePrefs());
        PortalPreferencesDto other = service.getPortalPreferences(otherStudentContext, otherStudent.getId());
        assertTrue(other.getShortcuts() == null || other.getShortcuts().isEmpty());
        assertEquals("light", other.getDisplay().getTheme());
    }

    private PortalPreferencesDto samplePrefs() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        prefs.setWidgets(widgetConfig(Arrays.asList("profile", "courses"), Collections.<String>emptyList()));
        prefs.setShortcuts(Collections.singletonList(
                new PortalPreferencesDto.ShortcutItem("home", "Home", "/student")));
        PortalPreferencesDto.DisplayPreferences display = new PortalPreferencesDto.DisplayPreferences();
        display.setTheme("light");
        display.setLocale("en-IN");
        prefs.setDisplay(display);
        return prefs;
    }

    private PortalPreferencesDto.DashboardWidgetsConfig widgetConfig(java.util.List<String> order,
                                                                     java.util.List<String> hidden) {
        PortalPreferencesDto.DashboardWidgetsConfig cfg = new PortalPreferencesDto.DashboardWidgetsConfig();
        cfg.setOrder(order);
        cfg.setHidden(hidden);
        return cfg;
    }
}
