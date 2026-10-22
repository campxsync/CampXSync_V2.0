package com.campx.student.repository;

import com.campx.student.model.GuardianshipEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link GuardianshipRepository} for isolated unit and mock testing.
 * Strictly enforces tenant isolation boundaries.
 */
public class InMemoryGuardianshipRepository implements GuardianshipRepository {

    // Key: tenantId + ":" + guardianshipId
    private final Map<String, GuardianshipEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public GuardianshipEntity save(UserSecurityContext context, GuardianshipEntity guardianship) {
        if (guardianship == null) throw new IllegalArgumentException("Guardianship cannot be null");
        UUID tenantId = context.getTenantId();
        guardianship.setTenantId(tenantId);

        if (guardianship.getId() == null) {
            guardianship.setId(UUID.randomUUID());
            guardianship.setCreatedAt(OffsetDateTime.now());
            guardianship.setCreatedBy(context.getUserId());
        }
        guardianship.setUpdatedAt(OffsetDateTime.now());
        guardianship.setUpdatedBy(context.getUserId());

        store.put(key(tenantId, guardianship.getId()), guardianship);
        return guardianship;
    }

    @Override
    public List<GuardianshipEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (studentId == null) return java.util.Collections.emptyList();
        UUID tenantId = context.getTenantId();

        return store.values().stream()
                .filter(g -> tenantId.equals(g.getTenantId())
                        && studentId.equals(g.getStudentId())
                        && g.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<GuardianshipEntity> findPrimaryByStudentId(UserSecurityContext context, UUID studentId) {
        return findByStudentId(context, studentId).stream()
                .filter(GuardianshipEntity::isPrimary)
                .findFirst();
    }
}
