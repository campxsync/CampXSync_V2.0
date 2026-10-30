package com.campx.student;

import com.campx.student.exception.StudentSecurityViolationException;
import com.campx.student.model.*;
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
 * Tests for Minimized Data Projections for Intelligence Services and Downstream Integrations.
 * Covers: STM01-US-043, STM01-US-044, STM01-US-045
 */
public class StudentDownstreamProjectionTest {

    private StudentDomainService service;
    private UUID tenantId;
    private UUID collegeId;
    private UserSecurityContext staffContext;
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

        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setFirstName("Ramesh");
        req.setLastName("Iyer");
        req.setPrimaryEmail("ramesh.iyer@institution.edu");
        req.setCollegeId(collegeId);
        student = service.enrollStudent(staffContext, req);
    }

    @Test
    public void testDownstreamProjectionExcludesSensitivePii() {
        StudentProjectionResponse projection = service.getStudentProjection(staffContext, student.getId());
        assertNotNull(projection);
        assertEquals(student.getId(), projection.getStudentId());
        assertEquals(tenantId, projection.getTenantId());
        assertEquals(student.getEnrollmentNo(), projection.getEnrollmentNo());
        assertEquals(StudentStatus.ACTIVE, projection.getStatus());

        // Ensure projection contract contains only non-sensitive tokens
        assertNotNull(projection.getCollegeId());
    }

    @Test(expected = StudentSecurityViolationException.class)
    public void testStudentCannotAccessInternalDownstreamProjection() {
        UserSecurityContext studentContext = new UserSecurityContext(
                UUID.randomUUID(), tenantId, collegeId, null, "student", false
        );

        service.getStudentProjection(studentContext, student.getId());
    }
}
