package com.campx.student;

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
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-10-US-012 — Configure dashboard widgets (durable portal preferences).
 */
public class Stm10Us012DashboardWidgetsTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private UserSecurityContext studentContext;
    private UserSecurityContext otherStudentContext;
    private StudentProfileResponse student;
    private StudentProfileResponse otherStudent;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        service = new StudentDomainService(studentRepo, personRepo, new InMemoryGuardianshipRepository());

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "system_admin", false);
        UUID studentUserId = UUID.randomUUID();
        studentContext = new UserSecurityContext(studentUserId, tenantId, collegeId, null, "student", false);
        UUID otherUserId = UUID.randomUUID();
        otherStudentContext = new UserSecurityContext(otherUserId, tenantId, collegeId, null, "student", false);

        student = enroll(studentRepo, "Widget", "One", "widget.one@institution.edu", studentUserId);
        otherStudent = enroll(studentRepo, "Widget", "Two", "widget.two@institution.edu", otherUserId);
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
    public void stm10Us012_widgetOrderAndHiddenPersistAcrossReload() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        PortalPreferencesDto.DashboardWidgetsConfig widgets = new PortalPreferencesDto.DashboardWidgetsConfig();
        widgets.setOrder(Arrays.asList("kpis", "profile", "courses", "documents", "notifications"));
        widgets.setHidden(Collections.singletonList("notifications"));
        prefs.setWidgets(widgets);

        service.savePortalPreferences(studentContext, student.getId(), prefs);
        PortalPreferencesDto reloaded = service.getPortalPreferences(studentContext, student.getId());

        assertEquals(Arrays.asList("kpis", "profile", "courses", "documents", "notifications"),
                reloaded.getWidgets().getOrder());
        assertTrue(reloaded.getWidgets().getHidden().contains("notifications"));
        assertFalse(reloaded.getWidgets().getHidden().contains("kpis"));
    }

    @Test
    public void stm10Us012_otherStudentDoesNotReceiveWidgetLayout() {
        PortalPreferencesDto prefs = new PortalPreferencesDto();
        PortalPreferencesDto.DashboardWidgetsConfig widgets = new PortalPreferencesDto.DashboardWidgetsConfig();
        widgets.setOrder(Arrays.asList("documents", "profile"));
        widgets.setHidden(Collections.singletonList("kpis"));
        prefs.setWidgets(widgets);
        service.savePortalPreferences(studentContext, student.getId(), prefs);

        PortalPreferencesDto other = service.getPortalPreferences(otherStudentContext, otherStudent.getId());
        assertFalse(other.getWidgets().getHidden().contains("kpis"));
        assertNotEquals(Arrays.asList("documents", "profile"), other.getWidgets().getOrder());
    }
}
