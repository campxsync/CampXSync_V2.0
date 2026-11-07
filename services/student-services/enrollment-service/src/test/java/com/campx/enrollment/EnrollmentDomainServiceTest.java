package com.campx.enrollment;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EnrollmentDomainServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");
    private EnrollmentDomainService service;
    private UUID tenant;
    private UUID otherTenant;
    private UUID student;
    private UUID otherStudent;
    private UUID program;
    private UUID faculty;
    private Actor registrar;
    private Actor registrarCoded;
    private Actor departmentStaff;
    private Actor studentActor;
    private Actor facultyActor;
    private Actor hod;
    private Actor dean;

    @Before
    public void setUp() {
        service = new EnrollmentDomainService(Clock.fixed(NOW, ZoneOffset.UTC));
        tenant = UUID.randomUUID();
        otherTenant = UUID.randomUUID();
        student = UUID.randomUUID();
        otherStudent = UUID.randomUUID();
        program = UUID.randomUUID();
        faculty = UUID.randomUUID();
        registrar = new Actor(tenant, UUID.randomUUID(), "REGISTRAR", "corr-1");
        registrarCoded = new Actor(tenant, UUID.randomUUID(), "R31", "corr-r31");
        departmentStaff = new Actor(tenant, UUID.randomUUID(), "DEPARTMENT_STAFF", "corr-2");
        studentActor = new Actor(tenant, student, "STUDENT", "corr-3");
        facultyActor = new Actor(tenant, faculty, "FACULTY", "corr-4");
        hod = new Actor(tenant, UUID.randomUUID(), "HOD", "corr-5");
        dean = new Actor(tenant, UUID.randomUUID(), "R07", "corr-r07");
    }

    @Test
    public void canonicalStudentRoleCanRegister() {
        Fixture fixture = fixture(20, 3, 6);
        Actor coded = new Actor(tenant, student, "R25", "corr-r25");
        EnrollmentDomainService.Enrolment enrolled = service.register(coded, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-r25");
        assertEquals("ENROLLED", enrolled.status);
    }

    @Test
    public void registrarCanConfigureTerm() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        assertEquals("2026", term.yearCode);
        EnrollmentDomainService.Term coded = service.createTerm(registrarCoded, "2026", "EVEN", "Even");
        assertEquals("EVEN", coded.termCode);
    }

    @Test
    public void fullSectionWaitlistRecordsStudentPosition() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-a");
        EnrollmentDomainService.Enrolment second = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor secondStudent = new Actor(tenant, otherStudent, "R25", "corr");
        int position = service.joinWaitlist(secondStudent, second.id, fixture.coreSection.id);
        assertEquals(1, position);
    }

    @Test
    public void studentCannotReadAnotherEnrolment() {
        EnrollmentDomainService.Enrolment enrolment = enrolledCore();
        Actor other = new Actor(tenant, otherStudent, "STUDENT", "corr");
        try {
            service.getEnrolment(other, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    @Test
    public void crossTenantReadFailsClosed() {
        EnrollmentDomainService.Enrolment enrolment = enrolledCore();
        Actor outsider = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr");
        try {
            service.getEnrolment(outsider, enrolment.id);
            fail("expected not found");
        } catch (EnrollmentException ex) {
            assertEquals(404, ex.getStatus());
        }
    }

    @Test
    public void facultyCannotApproveStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(facultyActor, enrolment.id, "ELIGIBLE");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void departmentStaffCannotApproveStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(departmentStaff, enrolment.id, "SUSPENDED");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void registrarCanApproveStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentDomainService.Enrolment updated = service.updateStatus(registrar, enrolment.id, "SUSPENDED");
        assertEquals("SUSPENDED", updated.status);
        EnrollmentDomainService.Enrolment coded = service.updateStatus(registrarCoded, enrolment.id, "ELIGIBLE");
        assertEquals("ELIGIBLE", coded.status);
    }

    @Test
    public void registrationOutsideWindowIsRejected() {
        EnrollmentDomainService.Term term = service.seedTerm(tenant, "2026", "ODD", "Odd");
        service.seedWindow(tenant, term.id, NOW.plusSeconds(3600), NOW.plusSeconds(7200), 2, 6);
        EnrollmentDomainService.Enrolment enrolment = service.seedHandover(tenant, UUID.randomUUID(), student, program, term.id);
        try {
            service.register(studentActor, enrolment.id, Collections.<EnrollmentDomainService.Selection>emptyList(), null);
            fail("expected closed window");
        } catch (EnrollmentException ex) {
            assertEquals("WINDOW_CLOSED", ex.getCode());
        }
    }

    @Test
    public void prerequisiteBlocksRegistration() {
        Fixture fixture = fixture(4, 2, 8);
        try {
            service.register(studentActor, fixture.enrolment.id, Arrays.asList(
                    new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id),
                    new EnrollmentDomainService.Selection(fixture.advanced.id, fixture.advancedSection.id)), "idem-1");
            fail("expected prerequisite");
        } catch (EnrollmentException ex) {
            assertEquals("PREREQUISITE_NOT_CLEARED", ex.getCode());
        }
    }

    @Test
    public void capacityBlocksSecondStudent() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-a");
        EnrollmentDomainService.Enrolment second = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor secondStudent = new Actor(tenant, otherStudent, "STUDENT", "corr");
        try {
            service.register(secondStudent, second.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-b");
            fail("expected full");
        } catch (EnrollmentException ex) {
            assertEquals("SECTION_FULL", ex.getCode());
        }
    }

    @Test
    public void successfulRegistrationEmitsOneIntelligenceEvent() {
        Fixture fixture = fixture(20, 3, 6);
        EnrollmentDomainService.Enrolment enrolled = service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-ok");
        assertEquals("ENROLLED", enrolled.status);
        assertEquals(3, enrolled.totalCredits);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-ok");
        assertEquals(1, service.eventsForTenant(tenant).size());
        assertEquals("student.enrolled.v1", service.eventsForTenant(tenant).get(0).get("eventType"));
        assertEquals("1.0.0", service.eventsForTenant(tenant).get(0).get("schemaVersion"));
    }

    @Test
    public void facultySeesOnlyAssignedRoster() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-r");
        assertEquals(1, service.roster(facultyActor, fixture.coreSection.id).size());
        List<Map<String, Object>> assigned = service.listAssignedRosters(facultyActor);
        boolean coreListed = false;
        for (Map<String, Object> section : assigned) {
            if (fixture.coreSection.id.toString().equals(section.get("sectionId"))) {
                coreListed = fixture.core.code.equals(section.get("courseCode"))
                        && ((List<?>) section.get("studentIds")).size() == 1;
            }
        }
        assertTrue(coreListed);
        Actor otherFaculty = new Actor(tenant, UUID.randomUUID(), "FACULTY", "corr");
        assertEquals(0, service.listAssignedRosters(otherFaculty).size());
        try {
            service.roster(otherFaculty, fixture.coreSection.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("ROSTER_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void hodCanMoveSectionWhenCapacityAllows() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-h");
        EnrollmentDomainService.Section alternate = service.seedSection(tenant, fixture.core.id, 20, faculty);
        EnrollmentDomainService.Enrolment moved = service.allocateSection(hod, fixture.enrolment.id, fixture.core.id, alternate.id);
        assertEquals(alternate.id, moved.selections.get(0).sectionId);
        EnrollmentDomainService.Enrolment movedByDean = service.allocateSection(dean, fixture.enrolment.id, fixture.core.id, fixture.coreSection.id);
        assertEquals(fixture.coreSection.id, movedByDean.selections.get(0).sectionId);
    }

    @Test
    public void facultyCannotAllocateSection() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-fa");
        EnrollmentDomainService.Section alternate = service.seedSection(tenant, fixture.core.id, 20, faculty);
        try {
            service.allocateSection(facultyActor, fixture.enrolment.id, fixture.core.id, alternate.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("ALLOCATE_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void registrarCanPromoteWaitlist() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-wl");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "R25", "corr-wl");
        assertEquals(1, service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id));
        fixture.coreSection.enrolledCount = 0;
        assertEquals(0, service.promoteWaitlist(registrar, fixture.coreSection.id));
    }

    @Test
    public void studentCannotConfigureTerm() {
        try {
            service.createTerm(studentActor, "2026", "ODD", "Odd");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void studentCanListCoursesAndHistory() {
        Fixture fixture = fixture(20, 3, 6);
        assertEquals(2, service.listCoursesForEnrolment(studentActor, fixture.enrolment.id).size());
        assertEquals(1, service.listSectionsForCourse(studentActor, fixture.enrolment.id, fixture.core.id).size());
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-hist");
        assertTrue(service.listHistory(studentActor, fixture.enrolment.id).size() >= 1);
    }

    @Test
    public void studentCanListOwnEnrolmentsAndRegisterViaCatalogue() {
        Fixture fixture = fixture(20, 3, 6);
        java.util.List<EnrollmentDomainService.Enrolment> mine = service.listMyEnrolments(studentActor);
        assertEquals(1, mine.size());
        assertEquals(fixture.enrolment.id, mine.get(0).id);
        java.util.List<java.util.Map<String, Object>> catalogue = service.listRegistrationCatalogue(studentActor, fixture.enrolment.id);
        assertEquals(2, catalogue.size());
        EnrollmentDomainService.Enrolment enrolled = service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-self");
        assertEquals("ENROLLED", enrolled.status);
    }

    @Test
    public void studentCanViewWaitlistPosition() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-pos");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "R25", "corr-pos");
        assertEquals(1, service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id));
        assertEquals(1, service.waitlistPosition(waitingStudent, fixture.coreSection.id));
    }

    @Test
    public void missingGatewaySignatureIsRejectedWhenSecretConfigured() {
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier("test-secret"));
        EnrollmentController.Response response = controller.handle("POST", "/api/v1/enrollment/terms", Collections.<String, String>emptyMap(), "{}");
        assertEquals(401, response.status);
    }

    private EnrollmentDomainService.Term term() {
        EnrollmentDomainService.Term created = service.seedTerm(tenant, "2026", "ODD", "Odd");
        service.seedWindow(tenant, created.id, NOW.minusSeconds(60), NOW.plusSeconds(3600), 1, 30);
        return created;
    }

    private EnrollmentDomainService.Enrolment opened() {
        return service.seedHandover(tenant, UUID.randomUUID(), student, program, term().id);
    }

    private EnrollmentDomainService.Enrolment enrolledCore() {
        Fixture fixture = fixture(20, 3, 6);
        return service.register(studentActor, fixture.enrolment.id, Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-view");
    }

    @Test
    public void collegeAdminCanOpenTermAndStudentCanSwapSection() {
        Actor collegeAdmin = new Actor(tenant, UUID.randomUUID(), "COLLEGE_ADMIN", "corr-college");
        EnrollmentDomainService.Term term = service.createTerm(collegeAdmin, "2026", "ODD", "Odd");
        service.createWindow(collegeAdmin, term.id, NOW.minusSeconds(60), NOW.plusSeconds(3600), 3, 6);
        EnrollmentDomainService.Course core = service.addCourse(collegeAdmin, program, term.id, "CS101", 3, true, Collections.<UUID>emptyList());
        EnrollmentDomainService.Section first = service.addSection(collegeAdmin, core.id, 20, faculty);
        EnrollmentDomainService.Section second = service.addSection(collegeAdmin, core.id, 20, faculty);
        EnrollmentDomainService.Enrolment opened = service.openFromHandover(collegeAdmin, UUID.randomUUID(), student, program, term.id);
        EnrollmentDomainService.Enrolment enrolled = service.register(studentActor, opened.id,
                Arrays.asList(new EnrollmentDomainService.Selection(core.id, first.id)), "idem-swap");
        EnrollmentDomainService.Enrolment swapped = service.swapSection(studentActor, enrolled.id, core.id, second.id);
        assertEquals(second.id, swapped.selections.get(0).sectionId);
        assertEquals(1, service.listTerms(collegeAdmin).size());
    }

    private Fixture fixture(int capacity, int min, int max) {
        EnrollmentDomainService.Term created = service.seedTerm(tenant, "2026", "ODD", "Odd");
        service.seedWindow(tenant, created.id, NOW.minusSeconds(60), NOW.plusSeconds(3600), min, max);
        UUID prerequisite = UUID.randomUUID();
        EnrollmentDomainService.Course core = service.seedCourse(tenant, program, created.id, "CS101", 3, true, Collections.<UUID>emptyList());
        EnrollmentDomainService.Course advanced = service.seedCourse(tenant, program, created.id, "CS201", 3, false, Arrays.asList(prerequisite));
        EnrollmentDomainService.Section coreSection = service.seedSection(tenant, core.id, capacity, faculty);
        EnrollmentDomainService.Section advancedSection = service.seedSection(tenant, advanced.id, capacity, faculty);
        EnrollmentDomainService.Enrolment enrolment = service.seedHandover(tenant, UUID.randomUUID(), student, program, created.id);
        Fixture fixture = new Fixture();
        fixture.term = created;
        fixture.core = core;
        fixture.advanced = advanced;
        fixture.coreSection = coreSection;
        fixture.advancedSection = advancedSection;
        fixture.enrolment = enrolment;
        fixture.prerequisite = prerequisite;
        return fixture;
    }

    private static final class Fixture {
        EnrollmentDomainService.Term term;
        EnrollmentDomainService.Course core;
        EnrollmentDomainService.Course advanced;
        EnrollmentDomainService.Section coreSection;
        EnrollmentDomainService.Section advancedSection;
        EnrollmentDomainService.Enrolment enrolment;
        UUID prerequisite;
    }
}
