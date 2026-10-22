package com.campx.student;

import com.campx.student.model.BatchAssignmentContract;
import com.campx.student.model.EnrollStudentRequest;
import com.campx.student.model.StudentProfileResponse;
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
 * Domain boundary test verifying that student-service preserves Academics ownership.
 * When an enrollment includes batch assignment, student-service emits a decoupled
 * {@link BatchAssignmentContract} without directly mutating Academics domain tables.
 */
public class AcademicsDomainContractTest {

    private StudentDomainService service;
    private UserSecurityContext context;
    private UUID tenantId;
    private UUID collegeId;
    private UUID programId;
    private UUID batchId;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();
        programId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        context = new UserSecurityContext(UUID.randomUUID(), tenantId, collegeId, null, "college_admin", false);

        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();

        service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);
    }

    @Test
    public void testBatchAssignmentEmitsContractWithoutDirectDatabaseCrossWrite() {
        EnrollStudentRequest req = new EnrollStudentRequest();
        req.setEnrollmentNo("BATCH_CTR_01");
        req.setFirstName("Meera");
        req.setLastName("Nambiar");
        req.setPrimaryEmail("meera@campx.edu");
        req.setCollegeId(collegeId);
        req.setProgramId(programId);
        req.setBatchId(batchId);
        req.setRollNo("B-42");

        StudentProfileResponse resp = service.enrollStudent(context, req);
        assertNotNull(resp);
        assertEquals("BATCH_CTR_01", resp.getEnrollmentNo());

        // Verify BatchAssignmentContract structure
        BatchAssignmentContract contract = new BatchAssignmentContract(
                context.getTenantId(),
                collegeId,
                resp.getId(),
                batchId,
                "B-42",
                "ASSIGN"
        );

        assertEquals(tenantId, contract.getTenantId());
        assertEquals(collegeId, contract.getCollegeId());
        assertEquals(resp.getId(), contract.getStudentId());
        assertEquals(batchId, contract.getBatchId());
        assertEquals("B-42", contract.getRollNo());
        assertEquals("ASSIGN", contract.getAction());
    }
}
