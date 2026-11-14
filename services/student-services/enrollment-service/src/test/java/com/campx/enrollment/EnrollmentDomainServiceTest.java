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

    // --- STM-03-US-001 Convert approved admission into enrolment ---

    @Test
    public void stm03Us001_registrarOpensEnrolmentFromHandoverAndEmitsEvent() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        UUID applicationId = UUID.randomUUID();
        EnrollmentDomainService.Enrolment opened = service.openFromHandover(registrar, applicationId, student, program, term.id);
        assertEquals("PENDING", opened.status);
        assertEquals(applicationId, opened.applicationId);
        assertEquals(student, opened.studentId);
        assertEquals(program, opened.programId);
        assertEquals(term.id, opened.termId);
        assertEquals(1, opened.history.size());
        assertEquals("handover", opened.history.get(0).get("action"));
        assertEquals(1, service.eventsForTenant(tenant).size());
        assertEquals(EnrollmentDomainService.ENROLMENT_OPENED, service.eventsForTenant(tenant).get(0).get("eventType"));
        assertEquals(opened.id, service.eventsForTenant(tenant).get(0).get("enrolmentId"));
    }

    @Test
    public void stm03Us001_sameApplicationIdIsIdempotent() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        UUID applicationId = UUID.randomUUID();
        EnrollmentDomainService.Enrolment first = service.openFromHandover(registrar, applicationId, student, program, term.id);
        EnrollmentDomainService.Enrolment second = service.openFromHandover(registrar, applicationId, student, program, term.id);
        assertEquals(first.id, second.id);
        assertEquals(1, service.eventsForTenant(tenant).size());
    }

    @Test
    public void stm03Us001_duplicateStudentTermIsRejected() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        service.openFromHandover(registrar, UUID.randomUUID(), student, program, term.id);
        try {
            service.openFromHandover(registrar, UUID.randomUUID(), student, program, term.id);
            fail("expected duplicate");
        } catch (EnrollmentException ex) {
            assertEquals(409, ex.getStatus());
            assertEquals("DUPLICATE_ENROLMENT", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_missingIdentifiersRejected() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        try {
            service.openFromHandover(registrar, null, student, program, term.id);
            fail("expected invalid");
        } catch (EnrollmentException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("HANDOVER_INVALID", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_studentCannotOpenHandover() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        try {
            service.openFromHandover(studentActor, UUID.randomUUID(), student, program, term.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_facultyCannotOpenHandover() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        try {
            service.openFromHandover(facultyActor, UUID.randomUUID(), student, program, term.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_departmentStaffCannotOpenWithoutCatalogueGrant() {
        // ROLE-CATALOGUE GAP: matrix labels Department Staff; R01–R30 grant not invented here.
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        try {
            service.openFromHandover(departmentStaff, UUID.randomUUID(), student, program, term.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_missingTenantIsUnauthorized() {
        Actor bare = new Actor(null, UUID.randomUUID(), "REGISTRAR", "corr-bare");
        try {
            service.openFromHandover(bare, UUID.randomUUID(), student, program, UUID.randomUUID());
            fail("expected unauthorized");
        } catch (EnrollmentException ex) {
            assertEquals(401, ex.getStatus());
            assertEquals("TENANT_REQUIRED", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_crossTenantTermNotVisible() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        Actor outsider = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr-x");
        try {
            service.openFromHandover(outsider, UUID.randomUUID(), student, program, term.id);
            fail("expected term not found");
        } catch (EnrollmentException ex) {
            assertEquals(404, ex.getStatus());
            assertEquals("TERM_NOT_FOUND", ex.getCode());
        }
    }

    @Test
    public void stm03Us001_unauthenticatedHandoverHttpRejected() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier("test-secret"));
        String body = "{\"applicationId\":\"" + UUID.randomUUID() + "\",\"studentId\":\"" + student
                + "\",\"programId\":\"" + program + "\",\"termId\":\"" + term.id + "\"}";
        EnrollmentController.Response response = controller.handle("POST", "/api/v1/enrollment/handovers",
                Collections.<String, String>emptyMap(), body);
        assertEquals(401, response.status);
    }

    // --- STM-03-US-002 View enrolment details ---

    @Test
    public void stm03Us002_studentCanViewOwnEnrolmentDetailsWithTerm() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd semester");
        EnrollmentDomainService.Enrolment opened = service.openFromHandover(registrar, UUID.randomUUID(), student, program, term.id);
        EnrollmentDomainService.Enrolment viewed = service.getEnrolment(studentActor, opened.id);
        assertEquals(opened.id, viewed.id);
        assertEquals(program, viewed.programId);
        assertEquals(term.id, viewed.termId);
        assertEquals("PENDING", viewed.status);
        EnrollmentDomainService.Term resolved = service.resolveTermForEnrolment(studentActor, viewed);
        assertEquals("2026", resolved.yearCode);
        assertEquals("ODD", resolved.termCode);
        assertEquals("Odd semester", resolved.name);
    }

    @Test
    public void stm03Us002_registrarCanViewEnrolmentDetails() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentDomainService.Enrolment viewed = service.getEnrolment(registrar, enrolment.id);
        assertEquals(enrolment.id, viewed.id);
        assertEquals(enrolment.programId, viewed.programId);
    }

    @Test
    public void stm03Us002_facultyCannotViewEnrolmentById() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.getEnrolment(facultyActor, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ENROLMENT_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us002_departmentStaffCannotViewEnrolmentById() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.getEnrolment(departmentStaff, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ENROLMENT_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us002_missingTenantUnauthorized() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        Actor bare = new Actor(null, student, "STUDENT", "corr-bare");
        try {
            service.getEnrolment(bare, enrolment.id);
            fail("expected unauthorized");
        } catch (EnrollmentException ex) {
            assertEquals(401, ex.getStatus());
            assertEquals("TENANT_REQUIRED", ex.getCode());
        }
    }

    @Test
    public void stm03Us002_unauthenticatedGetHttpRejected() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier("test-secret"));
        EnrollmentController.Response response = controller.handle("GET",
                "/api/v1/enrollment/enrolments/" + enrolment.id,
                Collections.<String, String>emptyMap(), "");
        assertEquals(401, response.status);
    }

    @Test
    public void stm03Us002_httpReturnsTermLabelsForOwner() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd semester");
        EnrollmentDomainService.Enrolment opened = service.openFromHandover(registrar, UUID.randomUUID(), student, program, term.id);
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier(null));
        java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
        headers.put("x-tenant-id", tenant.toString());
        headers.put("x-user-id", student.toString());
        headers.put("x-user-role", "STUDENT");
        headers.put("x-correlation-id", "corr-view");
        EnrollmentController.Response response = controller.handle("GET",
                "/api/v1/enrollment/enrolments/" + opened.id, headers, "");
        assertEquals(200, response.status);
        assertTrue(response.body.contains("\"yearCode\":\"2026\""));
        assertTrue(response.body.contains("\"termCode\":\"ODD\""));
        assertTrue(response.body.contains("\"termName\":\"Odd semester\""));
        assertTrue(response.body.contains("\"programId\":\"" + program + "\""));
    }

    // --- STM-03-US-003 Validate enrolment eligibility ---

    @Test
    public void stm03Us003_registrarMarksPendingEligibleWhenWindowOpen() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        assertEquals("PENDING", enrolment.status);
        List<String> failures = service.evaluateEligibility(registrar, enrolment.id);
        assertTrue(failures.isEmpty());
        EnrollmentDomainService.Enrolment viewed = service.getEnrolment(registrar, enrolment.id);
        assertEquals("ELIGIBLE", viewed.status);
    }

    @Test
    public void stm03Us003_windowClosedReturnsFailureWithoutStatusChange() {
        EnrollmentDomainService.Term term = service.seedTerm(tenant, "2026", "ODD", "Odd");
        service.seedWindow(tenant, term.id, NOW.plusSeconds(3600), NOW.plusSeconds(7200), 2, 6);
        EnrollmentDomainService.Enrolment enrolment = service.seedHandover(tenant, UUID.randomUUID(), student, program, term.id);
        List<String> failures = service.evaluateEligibility(registrar, enrolment.id);
        assertEquals(1, failures.size());
        assertEquals("WINDOW_CLOSED", failures.get(0));
        assertEquals("PENDING", service.getEnrolment(registrar, enrolment.id).status);
    }

    @Test
    public void stm03Us003_rejectedEnrolmentIsNotEligible() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        service.updateStatus(registrar, enrolment.id, "REJECTED");
        List<String> failures = service.evaluateEligibility(registrar, enrolment.id);
        assertTrue(failures.contains("ENROLMENT_NOT_ACTIVE"));
    }

    @Test
    public void stm03Us003_studentCannotSelfValidateEligibility() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.evaluateEligibility(studentActor, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us003_facultyCannotValidateEligibility() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.evaluateEligibility(facultyActor, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us003_departmentStaffCannotValidateEligibility() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.evaluateEligibility(departmentStaff, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us003_crossTenantEligibilityFailsClosed() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        Actor outsider = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr");
        try {
            service.evaluateEligibility(outsider, enrolment.id);
            fail("expected not found");
        } catch (EnrollmentException ex) {
            assertEquals(404, ex.getStatus());
        }
    }

    @Test
    public void stm03Us003_unauthenticatedEligibilityHttpRejected() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier("test-secret"));
        EnrollmentController.Response response = controller.handle("POST",
                "/api/v1/enrollment/enrolments/" + enrolment.id + "/eligibility",
                Collections.<String, String>emptyMap(), "{}");
        assertEquals(401, response.status);
    }

    @Test
    public void stm03Us003_httpReturnsEligiblePayloadForRegistrar() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier(null));
        java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
        headers.put("x-tenant-id", tenant.toString());
        headers.put("x-user-id", registrar.getUserId().toString());
        headers.put("x-user-role", "REGISTRAR");
        headers.put("x-correlation-id", "corr-elig");
        EnrollmentController.Response response = controller.handle("POST",
                "/api/v1/enrollment/enrolments/" + enrolment.id + "/eligibility", headers, "{}");
        assertEquals(200, response.status);
        assertTrue(response.body.contains("\"eligible\":true"));
        assertTrue(response.body.contains("\"status\":\"ELIGIBLE\""));
    }

    // --- STM-03-US-004 Update enrolment status ---

    @Test
    public void stm03Us004_registrarStatusChangeIsTraceableInHistory() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentDomainService.Enrolment suspended = service.updateStatus(registrar, enrolment.id, "SUSPENDED");
        assertEquals("SUSPENDED", suspended.status);
        List<Map<String, String>> history = service.listHistory(registrar, enrolment.id);
        assertTrue(history.size() >= 1);
        Map<String, String> last = history.get(history.size() - 1);
        assertEquals("SUSPENDED", last.get("status"));
        assertEquals("status", last.get("action"));
        assertEquals("REGISTRAR", last.get("actorRole"));
        assertTrue(last.get("at") != null && last.get("at").length() > 0);
    }

    @Test
    public void stm03Us004_invalidStatusRejected() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(registrar, enrolment.id, "ENROLLED");
            fail("expected invalid");
        } catch (EnrollmentException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("STATUS_INVALID", ex.getCode());
        }
    }

    @Test
    public void stm03Us004_studentCannotSelfUpdateStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(studentActor, enrolment.id, "ELIGIBLE");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us004_facultyCannotUpdateStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(facultyActor, enrolment.id, "SUSPENDED");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us004_departmentStaffCannotUpdateStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(departmentStaff, enrolment.id, "REJECTED");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us004_crossTenantStatusUpdateFailsClosed() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        Actor outsider = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr");
        try {
            service.updateStatus(outsider, enrolment.id, "SUSPENDED");
            fail("expected not found");
        } catch (EnrollmentException ex) {
            assertEquals(404, ex.getStatus());
        }
    }

    @Test
    public void stm03Us004_unauthenticatedStatusHttpRejected() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier("test-secret"));
        EnrollmentController.Response response = controller.handle("POST",
                "/api/v1/enrollment/enrolments/" + enrolment.id + "/status",
                Collections.<String, String>emptyMap(), "{\"status\":\"SUSPENDED\"}");
        assertEquals(401, response.status);
    }

    @Test
    public void stm03Us004_httpReturnsUpdatedStatusAndHistoryActorRole() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier(null));
        java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
        headers.put("x-tenant-id", tenant.toString());
        headers.put("x-user-id", registrar.getUserId().toString());
        headers.put("x-user-role", "REGISTRAR");
        headers.put("x-correlation-id", "corr-status");
        EnrollmentController.Response response = controller.handle("POST",
                "/api/v1/enrollment/enrolments/" + enrolment.id + "/status",
                headers, "{\"status\":\"REJECTED\"}");
        assertEquals(200, response.status);
        assertTrue(response.body.contains("\"status\":\"REJECTED\""));
        assertTrue(response.body.contains("\"actorRole\":\"REGISTRAR\""));
        assertTrue(response.body.contains("\"action\":\"status\""));
    }

    // --- STM-03-US-005 View enrolment history ---

    @Test
    public void stm03Us005_studentCanViewOwnEnrolmentHistory() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        service.updateStatus(registrar, enrolment.id, "ELIGIBLE");
        List<Map<String, String>> history = service.listHistory(studentActor, enrolment.id);
        assertTrue(history.size() >= 1);
        Map<String, String> last = history.get(history.size() - 1);
        assertEquals("ELIGIBLE", last.get("status"));
        assertEquals("status", last.get("action"));
        assertEquals("REGISTRAR", last.get("actorRole"));
    }

    @Test
    public void stm03Us005_studentCannotViewAnotherStudentsHistory() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        service.updateStatus(registrar, enrolment.id, "ELIGIBLE");
        Actor other = new Actor(tenant, otherStudent, "STUDENT", "corr");
        try {
            service.listHistory(other, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ENROLMENT_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us005_facultyCannotViewEnrolmentHistory() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.listHistory(facultyActor, enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("ENROLMENT_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void stm03Us005_registrarCanViewEnrolmentHistory() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        service.updateStatus(registrar, enrolment.id, "SUSPENDED");
        List<Map<String, String>> history = service.listHistory(registrar, enrolment.id);
        assertTrue(history.size() >= 1);
        assertEquals("SUSPENDED", history.get(history.size() - 1).get("status"));
    }

    @Test
    public void stm03Us005_crossTenantHistoryFailsClosed() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        Actor outsider = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr");
        try {
            service.listHistory(outsider, enrolment.id);
            fail("expected not found");
        } catch (EnrollmentException ex) {
            assertEquals(404, ex.getStatus());
        }
    }

    @Test
    public void stm03Us005_unauthenticatedHistoryHttpRejected() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier("test-secret"));
        EnrollmentController.Response response = controller.handle("GET",
                "/api/v1/enrollment/enrolments/" + enrolment.id + "/history",
                Collections.<String, String>emptyMap(), "");
        assertEquals(401, response.status);
    }

    @Test
    public void stm03Us005_httpReturnsHistoryPayloadForOwner() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        service.updateStatus(registrar, enrolment.id, "ELIGIBLE");
        EnrollmentController controller = new EnrollmentController(service, new com.campx.enrollment.security.GatewayHmacVerifier(null));
        java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
        headers.put("x-tenant-id", tenant.toString());
        headers.put("x-user-id", student.toString());
        headers.put("x-user-role", "STUDENT");
        headers.put("x-correlation-id", "corr-hist");
        EnrollmentController.Response response = controller.handle("GET",
                "/api/v1/enrollment/enrolments/" + enrolment.id + "/history", headers, "");
        assertEquals(200, response.status);
        assertTrue(response.body.contains("\"history\""));
        assertTrue(response.body.contains("\"status\":\"ELIGIBLE\""));
        assertTrue(response.body.contains("\"action\":\"status\""));
        assertTrue(response.body.contains("\"actorRole\":\"REGISTRAR\""));
    }

    // --- STM-03-US-006 Select programme and term courses ---

    @Test
    public void stm03Us006_studentListsProgrammeTermCoursesViaCatalogue() {
        Fixture fixture = fixture(20, 3, 6);
        java.util.List<java.util.Map<String, Object>> catalogue = service.listRegistrationCatalogue(studentActor, fixture.enrolment.id);
        assertEquals(2, catalogue.size());
        assertTrue(catalogue.get(0).containsKey("code"));
        assertTrue(catalogue.get(0).containsKey("sections"));
    }

    @Test
    public void stm03Us006_facultyCannotListStudentCatalogue() {
        Fixture fixture = fixture(20, 3, 6);
        try {
            service.listRegistrationCatalogue(facultyActor, fixture.enrolment.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    // --- STM-03-US-007 Select available course sections ---

    @Test
    public void stm03Us007_studentListsSectionsForCourse() {
        Fixture fixture = fixture(20, 3, 6);
        assertEquals(1, service.listSectionsForCourse(studentActor, fixture.enrolment.id, fixture.core.id).size());
        assertEquals(fixture.coreSection.id, service.listSectionsForCourse(studentActor, fixture.enrolment.id, fixture.core.id).get(0).id);
    }

    @Test
    public void stm03Us007_otherStudentCannotListSections() {
        Fixture fixture = fixture(20, 3, 6);
        Actor other = new Actor(tenant, otherStudent, "STUDENT", "corr");
        try {
            service.listSectionsForCourse(other, fixture.enrolment.id, fixture.core.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    // --- STM-03-US-008 Enforce course prerequisites ---

    @Test
    public void stm03Us008_prerequisiteEnforcedOnRegister() {
        Fixture fixture = fixture(4, 2, 8);
        try {
            service.register(studentActor, fixture.enrolment.id, Arrays.asList(
                    new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id),
                    new EnrollmentDomainService.Selection(fixture.advanced.id, fixture.advancedSection.id)), "idem-prereq");
            fail("expected prerequisite");
        } catch (EnrollmentException ex) {
            assertEquals("PREREQUISITE_NOT_CLEARED", ex.getCode());
        }
    }

    @Test
    public void stm03Us008_clearedPrerequisiteAllowsRegister() {
        Fixture fixture = fixture(4, 2, 8);
        service.recordClearedCourse(registrar, student, fixture.prerequisite);
        EnrollmentDomainService.Enrolment enrolled = service.register(studentActor, fixture.enrolment.id, Arrays.asList(
                new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id),
                new EnrollmentDomainService.Selection(fixture.advanced.id, fixture.advancedSection.id)), "idem-prereq-ok");
        assertEquals("ENROLLED", enrolled.status);
    }

    // --- STM-03-US-009 Enforce section capacity ---

    @Test
    public void stm03Us009_sectionCapacityBlocksRegistration() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-cap-a");
        EnrollmentDomainService.Enrolment second = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor secondStudent = new Actor(tenant, otherStudent, "STUDENT", "corr");
        try {
            service.register(secondStudent, second.id,
                    Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-cap-b");
            fail("expected full");
        } catch (EnrollmentException ex) {
            assertEquals("SECTION_FULL", ex.getCode());
        }
    }

    // --- STM-03-US-010 Remove course during permitted period ---

    @Test
    public void stm03Us010_studentDropsElectiveInsideWindow() {
        Fixture fixture = fixture(20, 3, 9);
        service.recordClearedCourse(registrar, student, fixture.prerequisite);
        EnrollmentDomainService.Enrolment enrolled = service.register(studentActor, fixture.enrolment.id, Arrays.asList(
                new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id),
                new EnrollmentDomainService.Selection(fixture.advanced.id, fixture.advancedSection.id)), "idem-drop");
        EnrollmentDomainService.Enrolment dropped = service.drop(studentActor, enrolled.id, fixture.advanced.id);
        boolean stillHasAdvanced = false;
        for (EnrollmentDomainService.Selection sel : dropped.selections) {
            if (sel.courseId.equals(fixture.advanced.id)) {
                stillHasAdvanced = true;
            }
        }
        assertTrue(!stillHasAdvanced);
    }

    @Test
    public void stm03Us010_studentCannotDropOutsideWindow() {
        Fixture fixture = fixture(20, 3, 9);
        service.recordClearedCourse(registrar, student, fixture.prerequisite);
        EnrollmentDomainService.Enrolment enrolled = service.register(studentActor, fixture.enrolment.id, Arrays.asList(
                new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id),
                new EnrollmentDomainService.Selection(fixture.advanced.id, fixture.advancedSection.id)), "idem-drop-closed");
        service.seedWindow(tenant, fixture.term.id, NOW.minusSeconds(7200), NOW.minusSeconds(3600), 3, 9);
        try {
            service.drop(studentActor, enrolled.id, fixture.advanced.id);
            fail("expected closed");
        } catch (EnrollmentException ex) {
            assertEquals("WINDOW_CLOSED", ex.getCode());
        }
    }

    @Test
    public void stm03Us010_facultyCannotDropStudentCourse() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-drop-fac");
        try {
            service.drop(facultyActor, fixture.enrolment.id, fixture.core.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("DROP_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-011 Allocate students to sections ---

    @Test
    public void stm03Us011_hodAllocatesSection() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-alloc");
        EnrollmentDomainService.Section alternate = service.seedSection(tenant, fixture.core.id, 20, faculty);
        EnrollmentDomainService.Enrolment moved = service.allocateSection(hod, fixture.enrolment.id, fixture.core.id, alternate.id);
        assertEquals(alternate.id, moved.selections.get(0).sectionId);
    }

    @Test
    public void stm03Us011_departmentStaffCannotAllocate() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-alloc-ds");
        EnrollmentDomainService.Section alternate = service.seedSection(tenant, fixture.core.id, 20, faculty);
        try {
            service.allocateSection(departmentStaff, fixture.enrolment.id, fixture.core.id, alternate.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("ALLOCATE_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-012 Join full-section waitlist ---

    @Test
    public void stm03Us012_studentJoinsFullSectionWaitlist() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-wl-full");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "R25", "corr-wl2");
        assertEquals(1, service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id));
    }

    @Test
    public void stm03Us012_cannotJoinWhenSeatsRemain() {
        Fixture fixture = fixture(20, 3, 6);
        try {
            service.joinWaitlist(studentActor, fixture.enrolment.id, fixture.coreSection.id);
            fail("expected available");
        } catch (EnrollmentException ex) {
            assertEquals("SECTION_AVAILABLE", ex.getCode());
        }
    }

    // --- STM-03-US-013 Promote eligible waitlisted students ---

    @Test
    public void stm03Us013_registrarPromotesWaitlist() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-prom");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "R25", "corr-prom");
        service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id);
        fixture.coreSection.enrolledCount = 0;
        assertEquals(0, service.promoteWaitlist(registrar, fixture.coreSection.id));
    }

    @Test
    public void stm03Us013_studentCannotPromoteWaitlist() {
        Fixture fixture = fixture(1, 3, 6);
        try {
            service.promoteWaitlist(studentActor, fixture.coreSection.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-014 View waitlist position and status ---

    @Test
    public void stm03Us014_studentViewsWaitlistPosition() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-pos2");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "R25", "corr-pos2");
        service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id);
        assertEquals(1, service.waitlistPosition(waitingStudent, fixture.coreSection.id));
    }

    @Test
    public void stm03Us014_facultyCannotViewWaitlistPosition() {
        Fixture fixture = fixture(1, 3, 6);
        try {
            service.waitlistPosition(facultyActor, fixture.coreSection.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("WAITLIST_VIEW_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-015 Configure registration windows ---

    @Test
    public void stm03Us015_registrarConfiguresWindow() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        EnrollmentDomainService.Window window = service.createWindow(registrar, term.id, NOW.minusSeconds(60), NOW.plusSeconds(3600), 3, 24);
        assertEquals(term.id, window.termId);
        assertEquals(3, window.minCredits);
        assertEquals(24, window.maxCredits);
    }

    @Test
    public void stm03Us015_studentCannotConfigureWindow() {
        EnrollmentDomainService.Term term = service.createTerm(registrar, "2026", "ODD", "Odd");
        try {
            service.createWindow(studentActor, term.id, NOW.minusSeconds(60), NOW.plusSeconds(3600), 3, 24);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-016 Configure academic years and terms ---

    @Test
    public void stm03Us016_registrarCreatesAcademicTerm() {
        EnrollmentDomainService.Term term = service.createTerm(registrarCoded, "2027", "EVEN", "Even 2027");
        assertEquals("2027", term.yearCode);
        assertEquals("EVEN", term.termCode);
    }

    @Test
    public void stm03Us016_facultyCannotCreateTerm() {
        try {
            service.createTerm(facultyActor, "2027", "ODD", "Odd");
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("CONFIG_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-017 View enrolment change history ---

    @Test
    public void stm03Us017_studentViewsChangeHistoryAfterStatusUpdate() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        service.updateStatus(registrar, enrolment.id, "SUSPENDED");
        List<Map<String, String>> history = service.listHistory(studentActor, enrolment.id);
        assertTrue(history.size() >= 1);
        assertEquals("SUSPENDED", history.get(history.size() - 1).get("status"));
        assertEquals("status", history.get(history.size() - 1).get("action"));
    }

    // --- STM-03-US-018 Receive enrolment confirmations and alerts ---

    @Test
    public void stm03Us018_studentReceivesInServiceAlertsAfterWaitlist() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-alert");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "R25", "corr-alert");
        service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id);
        List<Map<String, Object>> alerts = service.listAlerts(waitingStudent);
        assertTrue(alerts.size() >= 1);
        assertEquals("enrolment.waitlist_joined.v1", alerts.get(alerts.size() - 1).get("eventType"));
    }

    @Test
    public void stm03Us018_facultyCannotListStudentAlerts() {
        try {
            service.listAlerts(facultyActor);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("ALERTS_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-US-019 View my assigned course roster ---

    @Test
    public void stm03Us019_facultyViewsAssignedRosterOnly() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-roster");
        assertEquals(1, service.roster(facultyActor, fixture.coreSection.id).size());
        assertEquals(student, service.roster(facultyActor, fixture.coreSection.id).get(0));
        assertTrue(service.listAssignedRosters(facultyActor).size() >= 1);
    }

    @Test
    public void stm03Us019_unassignedFacultyDeniedRoster() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-roster2");
        Actor otherFaculty = new Actor(tenant, UUID.randomUUID(), "FACULTY", "corr");
        try {
            service.roster(otherFaculty, fixture.coreSection.id);
            fail("expected forbidden");
        } catch (EnrollmentException ex) {
            assertEquals("ROSTER_FORBIDDEN", ex.getCode());
        }
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

    // --- STM-03-EN-006 intelligence events / in-service alerts / logging ---

    @Test
    public void stm03En006_registrationEmitsVersionedIntelligenceEvent() {
        Fixture fixture = fixture(20, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-en006");
        List<Map<String, Object>> events = service.eventsForTenant(tenant);
        assertTrue(events.size() >= 1);
        assertEquals("student.enrolled.v1", events.get(0).get("eventType"));
        assertEquals("1.0.0", events.get(0).get("schemaVersion"));
    }

    @Test
    public void stm03En006_inServiceAlertsVisibleToStudentOnly() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-en006-a");
        EnrollmentDomainService.Enrolment waiting = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor waitingStudent = new Actor(tenant, otherStudent, "STUDENT", "corr-en006");
        service.joinWaitlist(waitingStudent, waiting.id, fixture.coreSection.id);
        assertTrue(service.listAlerts(waitingStudent).size() >= 1);
        try {
            service.listAlerts(facultyActor);
            fail("faculty must not read student alerts");
        } catch (EnrollmentException ex) {
            assertEquals("ALERTS_FORBIDDEN", ex.getCode());
        }
    }

    // --- STM-03-EN-003 concurrent seats / idempotent registration ---

    @Test
    public void stm03En003_idempotentRegisterReturnsSameResultWithoutDoubleCount() {
        Fixture fixture = fixture(20, 3, 6);
        EnrollmentDomainService.Enrolment first = service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-en003");
        EnrollmentDomainService.Enrolment second = service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-en003");
        assertEquals(first.id, second.id);
        assertEquals("ENROLLED", second.status);
        assertEquals(1, service.eventsForTenant(tenant).size());
    }

    @Test
    public void stm03En003_capacityExhaustedBlocksSecondSeatAllocation() {
        Fixture fixture = fixture(1, 3, 6);
        service.register(studentActor, fixture.enrolment.id,
                Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-en003-a");
        EnrollmentDomainService.Enrolment second = service.seedHandover(tenant, UUID.randomUUID(), otherStudent, program, fixture.term.id);
        Actor secondStudent = new Actor(tenant, otherStudent, "STUDENT", "corr-en003");
        try {
            service.register(secondStudent, second.id,
                    Arrays.asList(new EnrollmentDomainService.Selection(fixture.core.id, fixture.coreSection.id)), "idem-en003-b");
            fail("second seat must be blocked when capacity is 1");
        } catch (EnrollmentException ex) {
            assertEquals(409, ex.getStatus());
            assertEquals("SECTION_FULL", ex.getCode());
        }
    }

    // --- STM-03-EN-002 tenant isolation / authorization regression ---

    @Test
    public void stm03En002_crossTenantGetEnrolmentFailsClosed() {
        EnrollmentDomainService.Enrolment enrolment = enrolledCore();
        Actor outsider = new Actor(otherTenant, UUID.randomUUID(), "REGISTRAR", "corr-en002");
        try {
            service.getEnrolment(outsider, enrolment.id);
            fail("cross-tenant get must fail closed");
        } catch (EnrollmentException ex) {
            assertEquals(404, ex.getStatus());
        }
    }

    @Test
    public void stm03En002_wrongRoleCannotMutateStatus() {
        EnrollmentDomainService.Enrolment enrolment = opened();
        try {
            service.updateStatus(studentActor, enrolment.id, "SUSPENDED");
            fail("student must not mutate enrolment status");
        } catch (EnrollmentException ex) {
            assertEquals(403, ex.getStatus());
        }
    }

    @Test
    public void stm03En002_unauthenticatedActorRejected() {
        EnrollmentDomainService.Enrolment enrolment = enrolledCore();
        Actor anon = new Actor(null, null, null, "corr-anon");
        try {
            service.getEnrolment(anon, enrolment.id);
            fail("missing tenant must be rejected");
        } catch (EnrollmentException ex) {
            assertTrue(ex.getStatus() == 401 || ex.getStatus() == 403);
        }
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
