package com.campx.admin.institute.service;

import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.repository.InMemoryStudentRoleAssignmentRepository;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * STM-07-US-008 / US-009 — IAM student role assign/revoke unit tests.
 */
public class StudentRoleAssignmentServiceTest {

    private InMemoryStudentRoleAssignmentRepository repo;
    private StudentRoleAssignmentService service;
    private final List<String> auditActions = new ArrayList<String>();
    private UUID tenantId;
    private UUID operatorId;
    private UUID studentUserId;
    private UUID studentRoleId;
    private UserSecurityContext operator;

    @Before
    public void setUp() {
        repo = new InMemoryStudentRoleAssignmentRepository();
        auditActions.clear();
        service = new StudentRoleAssignmentService(repo, new StudentRoleAssignmentService.AuditSink() {
            @Override
            public void logRoleChange(UserSecurityContext context, String action, UUID targetUserId,
                                      UUID assignmentId, String detail) {
                auditActions.add(action);
            }
        });
        tenantId = UUID.randomUUID();
        operatorId = UUID.randomUUID();
        studentUserId = UUID.randomUUID();
        studentRoleId = UUID.randomUUID();
        repo.seedStudentRole(tenantId, studentRoleId);
        operator = new UserSecurityContext(operatorId, tenantId);
    }

    @Test
    public void stm07Us008_assignsR25StudentRole() {
        StudentRoleAssignmentService.RoleChangeResult result =
                service.assignStudentRole(operator, studentUserId.toString(), "R25", null);
        assertTrue(result.toJson().contains("\"status\":\"ASSIGNED\""));
        assertTrue(repo.hasActiveAssignment(tenantId, studentUserId));
        assertTrue(auditActions.contains("STUDENT_ROLE_ASSIGNED"));
    }

    @Test
    public void stm07Us008_rejectsNonStudentRole() {
        try {
            service.assignStudentRole(operator, studentUserId.toString(), "FACULTY", null);
            fail("Expected SecurityViolationException");
        } catch (SecurityViolationException expected) {
            assertTrue(expected.getMessage().contains("R25"));
        }
    }

    @Test
    public void stm07Us008_rejectsSelfAssignment() {
        try {
            service.assignStudentRole(operator, operatorId.toString(), "R25", null);
            fail("Expected SecurityViolationException");
        } catch (SecurityViolationException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("self"));
        }
    }

    @Test
    public void stm07Us008_rejectsDuplicateAssignment() {
        service.assignStudentRole(operator, studentUserId.toString(), "STUDENT", null);
        try {
            service.assignStudentRole(operator, studentUserId.toString(), "R25", null);
            fail("Expected ResourceConflictException");
        } catch (ResourceConflictException expected) {
            // ok
        }
    }

    @Test
    public void stm07Us009_revokesStudentRole() {
        service.assignStudentRole(operator, studentUserId.toString(), "R25", null);
        StudentRoleAssignmentService.RoleChangeResult result =
                service.revokeStudentRole(operator, studentUserId.toString(), "R25");
        assertTrue(result.toJson().contains("\"status\":\"REVOKED\""));
        assertFalse(repo.hasActiveAssignment(tenantId, studentUserId));
        assertTrue(auditActions.contains("STUDENT_ROLE_REVOKED"));
    }

    @Test
    public void stm07Us009_revokeMissingAssignmentNotFound() {
        try {
            service.revokeStudentRole(operator, studentUserId.toString(), "R25");
            fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // ok
        }
    }
}
