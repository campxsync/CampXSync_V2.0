package com.campx.academic.timetable.port;

import com.campx.academic.timetable.security.UserSecurityContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test double: maps actor userId → batchId within a tenant. */
public class FakeStudentBatchMembershipPort implements StudentBatchMembershipPort {

    private final Map<String, UUID> membership = new ConcurrentHashMap<>();

    public void put(UUID tenantId, UUID userId, UUID batchId) {
        membership.put(key(tenantId, userId), batchId);
    }

    public void clear() {
        membership.clear();
    }

    @Override
    public Optional<UUID> findActiveBatchIdForActor(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null || context.getUserId() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(membership.get(key(context.getTenantId(), context.getUserId())));
    }

    private static String key(UUID tenantId, UUID userId) {
        return tenantId + ":" + userId;
    }
}
