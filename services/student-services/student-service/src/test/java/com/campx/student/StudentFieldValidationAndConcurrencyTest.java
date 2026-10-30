package com.campx.student;

import com.campx.student.exception.StudentConflictException;
import com.campx.student.exception.StudentValidationException;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.StudentProfileResponse;
import com.campx.student.model.UpdateStudentProfileRequest;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Tests for Field-level validation, allowlist updates, optimistic concurrency, and Unicode support.
 * Covers: STM01-US-004, STM01-US-005, STM01-US-033, STM01-US-036, STM01-US-038
 */
public class StudentFieldValidationAndConcurrencyTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
    private StudentProfileResponse baseStudent;

    @Before
    public void setUp() {
        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        staffContext = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Vikram");
        req.setLastName("Patil");
        req.setPrimaryEmail("vikram.patil@institution.edu");
        req.setCollegeId(collegeId);
        baseStudent = service.enrollStudent(staffContext, req);
    }

    @Test
    public void testPermittedFieldsUpdateSucceeds() {
        UpdateStudentProfileRequest req = new UpdateStudentProfileRequest();
        req.setPreferredName("Vicky");
        req.setBloodGroup("O+");
        req.setNationality("Indian");
        req.setMaritalStatus("SINGLE");

        StudentProfileResponse updated = service.updateStudentProfile(staffContext, baseStudent.getId(), req, null);
        assertNotNull(updated);
        assertEquals("Vicky", updated.getPreferredName());
        assertEquals("O+", updated.getBloodGroup());
        assertEquals("Indian", updated.getNationality());
        assertEquals("SINGLE", updated.getMaritalStatus());
        // Unmodified fields remain unchanged (partial update null semantics)
        assertEquals("Vikram", updated.getFirstName());
        assertEquals("Patil", updated.getLastName());
        assertEquals("vikram.patil@institution.edu", updated.getPrimaryEmail());
    }

    @Test(expected = StudentValidationException.class)
    public void testProtectedFieldModificationThrowsException() {
        UpdateStudentProfileRequest req = new UpdateStudentProfileRequest();
        req.setEnrollmentNo("HACKED_ROLL_123"); // Protected field!

        service.updateStudentProfile(staffContext, baseStudent.getId(), req, null);
    }

    @Test(expected = StudentConflictException.class)
    public void testOptimisticConcurrencyMismatchThrowsConflict() {
        int currentVersion = baseStudent.getRowVersion();
        UpdateStudentProfileRequest req = new UpdateStudentProfileRequest();
        req.setPreferredName("ConflictTest");

        // Client supplies outdated version via If-Match header
        service.updateStudentProfile(staffContext, baseStudent.getId(), req, String.valueOf(currentVersion - 1));
    }

    @Test
    public void testOptimisticConcurrencyMatchingVersionIncrementsVersion() {
        int currentVersion = baseStudent.getRowVersion();
        UpdateStudentProfileRequest req = new UpdateStudentProfileRequest();
        req.setPreferredName("MatchingVersion");

        // Match exact current version
        StudentProfileResponse updated = service.updateStudentProfile(staffContext, baseStudent.getId(), req, String.valueOf(currentVersion));
        assertEquals(currentVersion + 1, updated.getRowVersion());
    }

    @Test
    public void testUnicodeNamesAndMultilingualSupport() {
        EnrollStudentRequest unicodeReq = new EnrollStudentRequest();
        unicodeReq.setFirstName("आनंद");
        unicodeReq.setLastName("जोशी");
        unicodeReq.setPrimaryEmail("anand.joshi@institution.edu");
        unicodeReq.setCollegeId(collegeId);

        StudentProfileResponse unicodeStudent = service.enrollStudent(staffContext, unicodeReq);
        assertNotNull(unicodeStudent);
        assertEquals("आनंद", unicodeStudent.getFirstName());
        assertEquals("जोशी", unicodeStudent.getLastName());
        assertEquals("आनंद जोशी", unicodeStudent.getDisplayName());
    }
}
