package com.campx.student.security;

import java.util.UUID;

/**
 * STM-07-US-008/009 — port to existing IAM (institute-admin) for R25 student role assign/revoke.
 * student-service must never write {@code iam.*} directly (Path B banned).
 */
public interface IamStudentRolePort {

    /**
     * Assigns approved student role R25 to {@code targetUserId} in {@code tenantId}.
     *
     * @return true when IAM confirms assignment
     */
    boolean assignStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId, UUID collegeId);

    /**
     * Revokes approved student role R25 from {@code targetUserId} in {@code tenantId}.
     *
     * @return true when IAM confirms revoke
     */
    boolean revokeStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId);
}
