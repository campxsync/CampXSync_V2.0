package com.campx.admin.institute.repository;

import com.campx.admin.institute.security.UserSecurityContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory student role assignment store for unit tests.
 */
public class InMemoryStudentRoleAssignmentRepository implements StudentRoleAssignmentRepository {

    private final Map<UUID, UUID> tenantStudentRoleIds = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, AssignmentRow> assignments = new ConcurrentHashMap<UUID, AssignmentRow>();

    public void seedStudentRole(UUID tenantId, UUID roleId) {
        if (tenantId != null && roleId != null) {
            tenantStudentRoleIds.put(tenantId, roleId);
        }
    }

    public boolean hasActiveAssignment(UUID tenantId, UUID userId) {
        for (AssignmentRow row : assignments.values()) {
            if (!row.deleted
                    && row.tenantId.equals(tenantId)
                    && row.userId.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    public int activeCount() {
        int n = 0;
        for (AssignmentRow row : assignments.values()) {
            if (!row.deleted) {
                n++;
            }
        }
        return n;
    }

    @Override
    public Optional<UUID> findStudentRoleId(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantStudentRoleIds.get(context.getTenantId()));
    }

    @Override
    public Optional<UUID> findActiveAssignmentId(UserSecurityContext context, UUID userId, UUID roleId) {
        if (context == null || context.getTenantId() == null || userId == null || roleId == null) {
            return Optional.empty();
        }
        for (Map.Entry<UUID, AssignmentRow> e : assignments.entrySet()) {
            AssignmentRow row = e.getValue();
            if (!row.deleted
                    && row.tenantId.equals(context.getTenantId())
                    && row.userId.equals(userId)
                    && row.roleId.equals(roleId)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    @Override
    public UUID insertAssignment(UserSecurityContext context, UUID userId, UUID roleId, UUID collegeId) {
        UUID id = UUID.randomUUID();
        assignments.put(id, new AssignmentRow(context.getTenantId(), userId, roleId, collegeId, false));
        return id;
    }

    @Override
    public boolean softDeleteAssignment(UserSecurityContext context, UUID assignmentId) {
        AssignmentRow row = assignments.get(assignmentId);
        if (row == null || row.deleted) {
            return false;
        }
        if (context != null && context.getTenantId() != null && !row.tenantId.equals(context.getTenantId())) {
            return false;
        }
        row.deleted = true;
        return true;
    }

    private static final class AssignmentRow {
        final UUID tenantId;
        final UUID userId;
        final UUID roleId;
        final UUID collegeId;
        boolean deleted;

        AssignmentRow(UUID tenantId, UUID userId, UUID roleId, UUID collegeId, boolean deleted) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.roleId = roleId;
            this.collegeId = collegeId;
            this.deleted = deleted;
        }
    }
}
