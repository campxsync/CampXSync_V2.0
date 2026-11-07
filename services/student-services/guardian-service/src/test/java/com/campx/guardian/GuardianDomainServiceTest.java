package com.campx.guardian;

import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GuardianDomainServiceTest {

    private GuardianDomainService service;
    private UUID tenant;
    private UUID student;
    private Actor registrar;
    private Actor studentActor;

    @Before
    public void setUp() {
        service = new GuardianDomainService();
        tenant = UUID.randomUUID();
        student = UUID.randomUUID();
        registrar = new Actor(tenant, UUID.randomUUID(), "COLLEGE_ADMIN", "corr-reg");
        studentActor = new Actor(tenant, student, "STUDENT", "corr-stu");
    }

    @Test
    public void registrarAddsGuardianAndStudentCanListOwn() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother",
                "asha@example.com", "9000000000", true);
        assertEquals("MOTHER", link.relationship);
        assertEquals("ACTIVE", link.status);
        assertTrue(link.primary);
        assertEquals(1, service.listGuardians(studentActor, student).size());
        assertEquals("Asha", service.listGuardians(registrar, student).get(0).firstName);
    }

    @Test
    public void studentCannotAddGuardian() {
        try {
            service.addGuardian(studentActor, student, "Asha", "Rao", "mother", null, null, false);
            fail("Students must not add guardians");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("REGISTRAR_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void missingNameIsRejected() {
        try {
            service.addGuardian(registrar, student, " ", "Rao", "father", null, null, false);
            fail("A blank name must be rejected");
        } catch (GuardianException ex) {
            assertEquals(422, ex.getStatus());
            assertEquals("FIELD_REQUIRED", ex.getCode());
        }
    }

    @Test
    public void registrarUpdatesRelationshipAndPrimaryFlag() {
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, student, "Asha", "Rao", "mother",
                "asha@example.com", "9000000000", true);
        GuardianDomainService.GuardianLink updated = service.updateGuardian(registrar, link.id, "aunt", false);
        assertEquals("AUNT", updated.relationship);
        assertTrue(!updated.primary);
        assertEquals("Asha", updated.firstName);
        assertEquals("AUNT", service.listGuardians(registrar, student).get(0).relationship);
    }

    @Test
    public void studentCannotUpdateAnotherStudentsGuardian() {
        UUID other = UUID.randomUUID();
        GuardianDomainService.GuardianLink link = service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, true);
        try {
            service.updateGuardian(studentActor, link.id, "uncle", true);
            fail("A student must not update another student's guardian");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_UPDATE_FORBIDDEN", ex.getCode());
        }
    }

    @Test
    public void studentCannotListAnotherStudentsGuardians() {
        UUID other = UUID.randomUUID();
        service.addGuardian(registrar, other, "Ravi", "Das", "father", null, null, false);
        try {
            service.listGuardians(studentActor, other);
            fail("A student must not list another student's guardians");
        } catch (GuardianException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("GUARDIAN_VIEW_FORBIDDEN", ex.getCode());
        }
    }
}
