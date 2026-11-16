package com.campx.admin.institute.repository;

import com.campx.admin.institute.security.UserSecurityContext;

import java.util.Optional;
import java.util.UUID;

/**
 * STM-07-US-008/009 — persistence for tenant-scoped student role (R25 / STUDENT) assignments
 * against existing {@code iam.role_assignments} (no new tables).
 */
public interface StudentRoleAssignmentRepository {

    /**
     * Resolves the tenant catalogue role id for the approved student role (code {@code STUDENT}).
     */
    Optional<UUID> findStudentRoleId(UserSecurityContext context);

    /**
     * Returns the active (non-deleted) assignment id for user + student role in tenant, if any.
     */
    Optional<UUID> findActiveAssignmentId(UserSecurityContext context, UUID userId, UUID roleId);

    /**
     * Inserts a new assignment row. Returns assignment id.
     */
    UUID insertAssignment(UserSecurityContext context, UUID userId, UUID roleId, UUID collegeId);

    /**
     * Soft-deletes an active assignment ({@code deleted_at = now()}). Returns true if a row was updated.
     */
    boolean softDeleteAssignment(UserSecurityContext context, UUID assignmentId);
}
