package com.campx.student;

import com.campx.student.model.*;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.security.UserSecurityContext;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Unit test suite verifying cursor-paginated roster search, sorting by enrollment_no,
 * and text search filtering.
 */
public class StudentRosterSearchTest {

    private StudentDomainService service;
    private UserSecurityContext context;
    private UUID tenantId;
    private UUID collegeId;
    private UUID programCS;
    private UUID programEC;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        programCS = UUID.randomUUID();
        programEC = UUID.randomUUID();
        context = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        // Seed 4 students
        enroll("CS2026001", "Aarav", "Sharma", "aarav@campx.edu", programCS);
        enroll("CS2026002", "Bhavna", "Patel", "bhavna@campx.edu", programCS);
        enroll("EC2026003", "Chetan", "Reddy", "chetan@campx.edu", programEC);
        enroll("EC2026004", "Divya", "Iyer", "divya@campx.edu", programEC);
    }

    private void enroll(String roll, String first, String last, String email, UUID programId) {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setEnrollmentNo(roll);
        req.setFirstName(first);
        req.setLastName(last);
        req.setPrimaryEmail(email);
        req.setCollegeId(collegeId);
        req.setProgramId(programId);
        service.enrollStudent(context, req);
    }

    @Test
    public void testCursorPaginationReturnsFirstPage() {
        StudentRosterQuery query = new StudentRosterQuery();
        query.setLimit(2);

        CursorPagedResult<StudentRosterItem> result = service.getRoster(context, query);
        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextCursor());

        assertEquals("CS2026001", result.getItems().get(0).getEnrollmentNo());
        assertEquals("CS2026002", result.getItems().get(1).getEnrollmentNo());

        // Fetch Second Page
        StudentRosterQuery page2Query = new StudentRosterQuery();
        page2Query.setLimit(2);
        page2Query.setCursor(result.getNextCursor());

        CursorPagedResult<StudentRosterItem> page2 = service.getRoster(context, page2Query);
        assertNotNull(page2);
        assertEquals(2, page2.getItems().size());
        assertFalse(page2.isHasMore());
        assertEquals("EC2026003", page2.getItems().get(0).getEnrollmentNo());
        assertEquals("EC2026004", page2.getItems().get(1).getEnrollmentNo());
    }

    @Test
    public void testProgramFilterReturnsOnlyMatchingStudents() {
        StudentRosterQuery query = new StudentRosterQuery();
        query.setProgramId(programCS);

        CursorPagedResult<StudentRosterItem> result = service.getRoster(context, query);
        assertEquals(2, result.getItems().size());
        for (StudentRosterItem item : result.getItems()) {
            assertEquals(programCS, item.getProgramId());
        }
    }

    @Test
    public void testTextSearchMatchesDisplayNameOrEnrollment() {
        StudentRosterQuery query = new StudentRosterQuery();
        query.setSearch("Bhavna");

        CursorPagedResult<StudentRosterItem> result = service.getRoster(context, query);
        assertEquals(1, result.getItems().size());
        assertEquals("CS2026002", result.getItems().get(0).getEnrollmentNo());
        assertEquals("Bhavna Patel", result.getItems().get(0).getDisplayName());

        // Search by partial roll
        StudentRosterQuery rollQuery = new StudentRosterQuery();
        rollQuery.setSearch("EC2026");
        CursorPagedResult<StudentRosterItem> rollResult = service.getRoster(context, rollQuery);
        assertEquals(2, rollResult.getItems().size());
    }
}
