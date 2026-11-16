package com.campx.student.security;

import java.util.UUID;

/**
 * Fail-closed stub when IAM base URL is unset.
 */
public final class FailClosedIamStudentRolePort implements IamStudentRolePort {

    @Override
    public boolean assignStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId, UUID collegeId) {
        return false;
    }

    @Override
    public boolean revokeStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId) {
        return false;
    }
}
