package com.campx.student.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Unit-test fake for {@link IamStudentRolePort}.
 */
public final class FakeIamStudentRolePort implements IamStudentRolePort {

    private final Set<String> assignments = new HashSet<String>();
    private boolean failNext;

    public void failNext() {
        this.failNext = true;
    }

    public boolean hasAssignment(UUID tenantId, UUID userId) {
        return assignments.contains(key(tenantId, userId));
    }

    public void clear() {
        assignments.clear();
        failNext = false;
    }

    @Override
    public boolean assignStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId, UUID collegeId) {
        if (failNext) {
            failNext = false;
            return false;
        }
        if (tenantId == null || actorUserId == null || targetUserId == null) {
            return false;
        }
        if (actorUserId.equals(targetUserId)) {
            return false;
        }
        assignments.add(key(tenantId, targetUserId));
        return true;
    }

    @Override
    public boolean revokeStudentRole(UUID tenantId, UUID actorUserId, UUID targetUserId) {
        if (failNext) {
            failNext = false;
            return false;
        }
        if (tenantId == null || actorUserId == null || targetUserId == null) {
            return false;
        }
        if (actorUserId.equals(targetUserId)) {
            return false;
        }
        return assignments.remove(key(tenantId, targetUserId));
    }

    private static String key(UUID tenantId, UUID userId) {
        return tenantId + "|" + userId;
    }
}
