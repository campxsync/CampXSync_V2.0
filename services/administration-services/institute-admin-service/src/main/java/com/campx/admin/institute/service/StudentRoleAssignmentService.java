package com.campx.admin.institute.service;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.repository.InMemoryStudentRoleAssignmentRepository;
import com.campx.admin.institute.repository.PostgresStudentRoleAssignmentRepository;
import com.campx.admin.institute.repository.StudentRoleAssignmentRepository;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * STM-07-US-008/009 — assign/revoke approved student role R25 (catalogue code {@code STUDENT})
 * via existing {@code iam.role_assignments}. Tenant-scoped, audited, no self-assignment.
 */
public class StudentRoleAssignmentService {

    public static final String ASSIGN_PATH = "/api/v1/admin/internal/roles/student-assign";
    public static final String REVOKE_PATH = "/api/v1/admin/internal/roles/student-revoke";
    /** Canonical catalogue role code for R25. */
    public static final String STUDENT_ROLE_CODE = "STUDENT";
    public static final String APPROVED_ROLE_R25 = "R25";

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentRoleAssignmentService.class);

    private final StudentRoleAssignmentRepository repository;
    private final AuditSink auditSink;

    public interface AuditSink {
        void logRoleChange(UserSecurityContext context, String action, UUID targetUserId,
                           UUID assignmentId, String detail);
    }

    public StudentRoleAssignmentService() {
        this(resolveDefaultRepository(), new LoggingAuditSink());
    }

    public StudentRoleAssignmentService(StudentRoleAssignmentRepository repository) {
        this(repository, new LoggingAuditSink());
    }

    public StudentRoleAssignmentService(StudentRoleAssignmentRepository repository, AuditSink auditSink) {
        this.repository = repository == null ? resolveDefaultRepository() : repository;
        this.auditSink = auditSink == null ? new LoggingAuditSink() : auditSink;
    }

    public StudentRoleAssignmentRepository getRepository() {
        return repository;
    }

    public RoleChangeResult assignStudentRole(UserSecurityContext context, String targetUserId,
                                              String roleCode, String collegeId) {
        UUID target = parseUser(targetUserId);
        assertApprovedRole(roleCode);
        assertContext(context);
        assertNoSelfAssignment(context, target);

        Optional<UUID> roleIdOpt = repository.findStudentRoleId(context);
        if (!roleIdOpt.isPresent()) {
            throw new ResourceNotFoundException("StudentRole", STUDENT_ROLE_CODE);
        }
        UUID roleId = roleIdOpt.get();
        Optional<UUID> existing = repository.findActiveAssignmentId(context, target, roleId);
        if (existing.isPresent()) {
            throw new ResourceConflictException("StudentRoleAssignment",
                    "Active assignment already exists for user " + target);
        }

        UUID collegeUuid = parseOptionalUuid(collegeId);
        UUID assignmentId = repository.insertAssignment(context, target, roleId, collegeUuid);
        auditSink.logRoleChange(context, "STUDENT_ROLE_ASSIGNED", target, assignmentId,
                "Assigned R25/STUDENT role");
        logger.info("Assigned student role tenant={} user={} assignmentId={}",
                context.getTenantId(), target, assignmentId);
        return new RoleChangeResult(true, "ASSIGNED", assignmentId.toString(), target.toString(),
                APPROVED_ROLE_R25, STUDENT_ROLE_CODE);
    }

    public RoleChangeResult revokeStudentRole(UserSecurityContext context, String targetUserId,
                                              String roleCode) {
        UUID target = parseUser(targetUserId);
        assertApprovedRole(roleCode);
        assertContext(context);
        assertNoSelfAssignment(context, target);

        Optional<UUID> roleIdOpt = repository.findStudentRoleId(context);
        if (!roleIdOpt.isPresent()) {
            throw new ResourceNotFoundException("StudentRole", STUDENT_ROLE_CODE);
        }
        UUID roleId = roleIdOpt.get();
        Optional<UUID> existing = repository.findActiveAssignmentId(context, target, roleId);
        if (!existing.isPresent()) {
            throw new ResourceNotFoundException("StudentRoleAssignment", target.toString());
        }
        UUID assignmentId = existing.get();
        boolean removed = repository.softDeleteAssignment(context, assignmentId);
        if (!removed) {
            throw new ResourceNotFoundException("StudentRoleAssignment", assignmentId.toString());
        }
        auditSink.logRoleChange(context, "STUDENT_ROLE_REVOKED", target, assignmentId,
                "Revoked R25/STUDENT role");
        logger.info("Revoked student role tenant={} user={} assignmentId={}",
                context.getTenantId(), target, assignmentId);
        return new RoleChangeResult(true, "REVOKED", assignmentId.toString(), target.toString(),
                APPROVED_ROLE_R25, STUDENT_ROLE_CODE);
    }

    public static boolean isApprovedStudentRoleCode(String roleCode) {
        if (roleCode == null || roleCode.trim().isEmpty()) {
            return false;
        }
        String c = roleCode.trim().toUpperCase(Locale.ROOT);
        return APPROVED_ROLE_R25.equals(c) || STUDENT_ROLE_CODE.equals(c);
    }

    private static void assertApprovedRole(String roleCode) {
        if (!isApprovedStudentRoleCode(roleCode)) {
            throw new SecurityViolationException(
                    "Only approved student role R25 (STUDENT) may be assigned or revoked via this endpoint");
        }
    }

    private static void assertContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null || context.getUserId() == null) {
            throw new SecurityViolationException("Missing security context for student role change");
        }
    }

    private static void assertNoSelfAssignment(UserSecurityContext context, UUID target) {
        if (context.getUserId().equals(target)) {
            throw new SecurityViolationException("Self-assignment of student role is not allowed");
        }
    }

    private static UUID parseUser(String targetUserId) {
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            throw new MalformedPayloadException("userId is required");
        }
        try {
            return UUID.fromString(targetUserId.trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("userId must be a valid UUID");
        }
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("collegeId must be a valid UUID when provided");
        }
    }

    private static boolean isPostgresMode() {
        String mode = System.getProperty("campx.persistence.mode");
        if ("postgres".equalsIgnoreCase(mode) || "true".equalsIgnoreCase(System.getProperty("campx.db.enabled"))) {
            return true;
        }
        return "postgres".equalsIgnoreCase(System.getenv("CAMPX_PERSISTENCE_MODE"));
    }

    private static StudentRoleAssignmentRepository resolveDefaultRepository() {
        if (isPostgresMode()) {
            return new PostgresStudentRoleAssignmentRepository();
        }
        return new InMemoryStudentRoleAssignmentRepository();
    }

    public static final class RoleChangeResult {
        private final boolean success;
        private final String status;
        private final String assignmentId;
        private final String userId;
        private final String roleCode;
        private final String catalogueCode;

        public RoleChangeResult(boolean success, String status, String assignmentId, String userId,
                                String roleCode, String catalogueCode) {
            this.success = success;
            this.status = status;
            this.assignmentId = assignmentId;
            this.userId = userId;
            this.roleCode = roleCode;
            this.catalogueCode = catalogueCode;
        }

        public String toJson() {
            return "{"
                    + "\"success\":" + success + ","
                    + "\"status\":\"" + status + "\","
                    + "\"assignmentId\":\"" + assignmentId + "\","
                    + "\"userId\":\"" + userId + "\","
                    + "\"roleCode\":\"" + roleCode + "\","
                    + "\"catalogueCode\":\"" + catalogueCode + "\""
                    + "}";
        }
    }

    private static final class LoggingAuditSink implements AuditSink {
        @Override
        public void logRoleChange(UserSecurityContext context, String action, UUID targetUserId,
                                  UUID assignmentId, String detail) {
            AuditEvent audit = AuditEvent.builder()
                    .action(action)
                    .principalId(context != null && context.getUserId() != null
                            ? context.getUserId().toString() : "unknown")
                    .principalRole("IAM_OPERATOR")
                    .principalType("USER")
                    .tenantId(context != null && context.getTenantId() != null
                            ? context.getTenantId().toString() : null)
                    .resourceType("ROLE_ASSIGNMENT")
                    .resourceId(assignmentId != null ? assignmentId.toString() : "N/A")
                    .status("SUCCESS")
                    .description(detail + " targetUserId=" + targetUserId)
                    .build();
            logger.audit(audit);
        }
    }
}
