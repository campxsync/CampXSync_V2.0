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
                service.assignStudentRole(operator, "R02", studentUserId.toString(), "R25", null);
        assertTrue(result.toJson().contains("\"status\":\"ASSIGNED\""));
        assertTrue(repo.hasActiveAssignment(tenantId, studentUserId));
        assertTrue(auditActions.contains("STUDENT_ROLE_ASSIGNED"));
    }

    @Test
    public void stm07Us008_r01R02R03CallersAllowed() {
        service.assignStudentRole(operator, "R01", studentUserId.toString(), "R25", null);
        service.revokeStudentRole(operator, "PLATFORM_ADMIN", studentUserId.toString(), "R25");

        UUID u2 = UUID.randomUUID();
        service.assignStudentRole(operator, "SYSTEM_ADMIN", u2.toString(), "STUDENT", null);
        service.revokeStudentRole(operator, "R02", u2.toString(), "R25");

        UUID u3 = UUID.randomUUID();
        service.assignStudentRole(operator, "R03", u3.toString(), "R25", null);
        service.revokeStudentRole(operator, "INSTITUTION_ADMIN", u3.toString(), "R25");
    }

    @Test
    public void stm07Us008_rejectsNonOperatorRoles() {
        String[] denied = {"FACULTY", "STUDENT", "R25", "R31", "COLLEGE_ADMIN", "REGISTRAR", ""};
        for (String role : denied) {
            try {
                service.assignStudentRole(operator, role, studentUserId.toString(), "R25", null);
                fail("Expected SecurityViolationException for role " + role);
            } catch (SecurityViolationException expected) {
                assertTrue(expected.getMessage().contains("R01/R02/R03"));
            }
        }
        try {
            service.assignStudentRole(operator, null, studentUserId.toString(), "R25", null);
            fail("Expected SecurityViolationException for null role");
        } catch (SecurityViolationException expected) {
            assertTrue(expected.getMessage().contains("R01/R02/R03"));
        }
    }

    @Test
    public void stm07Us008_rejectsNonStudentRole() {
        try {
            service.assignStudentRole(operator, "R02", studentUserId.toString(), "FACULTY", null);
            fail("Expected SecurityViolationException");
        } catch (SecurityViolationException expected) {
            assertTrue(expected.getMessage().contains("R25"));
        }
    }

    @Test
    public void stm07Us008_rejectsSelfAssignment() {
        try {
            service.assignStudentRole(operator, "R02", operatorId.toString(), "R25", null);
            fail("Expected SecurityViolationException");
        } catch (SecurityViolationException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("self"));
        }
    }

    @Test
    public void stm07Us008_wrongTenantDenied() {
        UUID otherTenant = UUID.randomUUID();
        UserSecurityContext foreign = new UserSecurityContext(operatorId, otherTenant);
        try {
            service.assignStudentRole(foreign, "R02", studentUserId.toString(), "R25", null);
            fail("Expected ResourceNotFoundException for wrong tenant");
        } catch (ResourceNotFoundException expected) {
            assertTrue(expected.getMessage() != null);
        }
        assertFalse(repo.hasActiveAssignment(tenantId, studentUserId));
        assertFalse(repo.hasActiveAssignment(otherTenant, studentUserId));
    }

    @Test
    public void stm07Us008_rejectsDuplicateAssignment() {
        service.assignStudentRole(operator, "R02", studentUserId.toString(), "STUDENT", null);
        try {
            service.assignStudentRole(operator, "R02", studentUserId.toString(), "R25", null);
            fail("Expected ResourceConflictException");
        } catch (ResourceConflictException expected) {
            // ok
        }
    }

    @Test
    public void stm07Us009_revokesStudentRole() {
        service.assignStudentRole(operator, "R02", studentUserId.toString(), "R25", null);
        StudentRoleAssignmentService.RoleChangeResult result =
                service.revokeStudentRole(operator, "R02", studentUserId.toString(), "R25");
        assertTrue(result.toJson().contains("\"status\":\"REVOKED\""));
        assertFalse(repo.hasActiveAssignment(tenantId, studentUserId));
        assertTrue(auditActions.contains("STUDENT_ROLE_REVOKED"));
    }

    @Test
    public void stm07Us009_revokeMissingAssignmentNotFound() {
        try {
            service.revokeStudentRole(operator, "R02", studentUserId.toString(), "R25");
            fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // ok
        }
    }
}
