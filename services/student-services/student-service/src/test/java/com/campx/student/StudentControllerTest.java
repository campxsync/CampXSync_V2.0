package com.campx.student;

import com.campx.student.controller.StudentController;
import com.campx.student.model.StudentStatus;
import com.campx.student.repository.InMemoryGuardianshipRepository;
import com.campx.student.repository.InMemoryPersonRepository;
import com.campx.student.repository.InMemoryStudentRepository;
import com.campx.student.service.StudentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Controller test suite verifying HTTP request mapping, status codes, and error formatting.
 */
public class StudentControllerTest {

    private StudentController controller;
    private Map<String, String> validHeaders;
    private UUID tenantId;
    private UUID collegeId;

    @Before
    public void setUp() {
        tenantId = UUID.randomUUID();
        collegeId = UUID.randomUUID();

        InMemoryPersonRepository personRepo = new InMemoryPersonRepository();
        InMemoryStudentRepository studentRepo = new InMemoryStudentRepository(personRepo);
        InMemoryGuardianshipRepository guardianshipRepo = new InMemoryGuardianshipRepository();
        StudentDomainService service = new StudentDomainService(studentRepo, personRepo, guardianshipRepo);

        controller = new StudentController(service);

        validHeaders = new HashMap<>();
        validHeaders.put("X-User-Id", UUID.randomUUID().toString());
        validHeaders.put("X-Tenant-Id", tenantId.toString());
        validHeaders.put("X-College-Id", collegeId.toString());
        validHeaders.put("X-User-Role", "college_admin");
    }

    @Test
    public void testEnrollStudentReturns201Created() {
        String json = "{"
                + "\"firstName\":\"Kavya\","
                + "\"lastName\":\"Menon\","
                + "\"primaryEmail\":\"kavya.menon@campx.edu\","
                + "\"enrollmentNo\":\"CTRL202601\","
                + "\"collegeId\":\"" + collegeId + "\""
                + "}";

        StudentController.HttpResponse response = controller.enrollStudent(validHeaders, json);
        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("CTRL202601"));
        assertTrue(response.getBody().contains("Kavya Menon"));
    }

    @Test
    public void testDuplicateEnrollmentReturns409Conflict() {
        String json = "{"
                + "\"firstName\":\"Kavya\","
                + "\"lastName\":\"Menon\","
                + "\"primaryEmail\":\"kavya.dup@campx.edu\","
                + "\"enrollmentNo\":\"CTRL_DUP\","
                + "\"collegeId\":\"" + collegeId + "\""
                + "}";

        StudentController.HttpResponse res1 = controller.enrollStudent(validHeaders, json);
        assertEquals(201, res1.getStatusCode());

        StudentController.HttpResponse res2 = controller.enrollStudent(validHeaders, json);
        assertEquals(409, res2.getStatusCode());
        assertTrue(res2.getBody().contains("https://campxsync.edu/errors/conflict"));
    }

    @Test
    public void testInvalidLifecycleReturns422UnprocessableEntity() {
        String enrollJson = "{"
                + "\"firstName\":\"Test\","
                + "\"lastName\":\"Student\","
                + "\"primaryEmail\":\"lifecycle.test@campx.edu\","
                + "\"enrollmentNo\":\"CTRL_LC\","
                + "\"collegeId\":\"" + collegeId + "\""
                + "}";

        StudentController.HttpResponse enrollRes = controller.enrollStudent(validHeaders, enrollJson);
        assertEquals(201, enrollRes.getStatusCode());

        // Extract student ID from body (or use simple substring)
        int idIdx = enrollRes.getBody().indexOf("\"id\":\"") + 6;
        String studentId = enrollRes.getBody().substring(idIdx, idIdx + 36);

        // Attempt invalid jump from ACTIVE to ALUMNI
        String patchJson = "{\"status\":\"ALUMNI\",\"reason\":\"Premature jump\"}";
        StudentController.HttpResponse patchRes = controller.updateStatus(validHeaders, studentId, patchJson);

        assertEquals(422, patchRes.getStatusCode());
        assertTrue(patchRes.getBody().contains("https://campxsync.edu/errors/unprocessable-entity"));
    }

    @Test
    public void testMissingTenantHeaderReturns403Forbidden() {
        Map<String, String> noTenantHeaders = new HashMap<>();
        noTenantHeaders.put("X-User-Id", UUID.randomUUID().toString());

        StudentController.HttpResponse response = controller.getRoster(noTenantHeaders, new HashMap<>());
        assertEquals(403, response.getStatusCode());
        assertTrue(response.getBody().contains("https://campxsync.edu/errors/forbidden"));
    }

    @Test
    public void testDeleteStudentSuccess() {
        String enrollJson = "{"
                + "\"firstName\":\"Delete\","
                + "\"lastName\":\"Me\","
                + "\"primaryEmail\":\"delete.me@campx.edu\","
                + "\"enrollmentNo\":\"CTRL_DEL\","
                + "\"collegeId\":\"" + collegeId + "\""
                + "}";

        StudentController.HttpResponse enrollRes = controller.enrollStudent(validHeaders, enrollJson);
        assertEquals(201, enrollRes.getStatusCode());

        int idIdx = enrollRes.getBody().indexOf("\"id\":\"") + 6;
        String studentId = enrollRes.getBody().substring(idIdx, idIdx + 36);

        StudentController.HttpResponse delRes = controller.deleteStudent(validHeaders, studentId);
        assertEquals(200, delRes.getStatusCode());
        assertTrue(delRes.getBody().contains("\"success\":true"));

        // Second delete should return 404 because student is deactivated
        StudentController.HttpResponse delAgain = controller.deleteStudent(validHeaders, studentId);
        assertEquals(404, delAgain.getStatusCode());
    }

    @Test
    public void testDeleteStudentNotFoundReturns404() {
        StudentController.HttpResponse delRes = controller.deleteStudent(validHeaders, UUID.randomUUID().toString());
        assertEquals(404, delRes.getStatusCode());
        assertTrue(delRes.getBody().contains("https://campxsync.edu/errors/not-found"));
    }
}
