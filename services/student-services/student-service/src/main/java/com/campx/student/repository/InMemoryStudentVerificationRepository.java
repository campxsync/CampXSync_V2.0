package com.campx.student.repository;

import com.campx.student.model.StudentVerificationEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory repository for student verification records (STM01-US-013, STM01-US-014).
 */
public class InMemoryStudentVerificationRepository implements StudentVerificationRepository {

    // Key: tenantId + ":" + id
    private final Map<String, StudentVerificationEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public StudentVerificationEntity save(UserSecurityContext context, StudentVerificationEntity verification) {
        if (verification == null) throw new IllegalArgumentException("Verification cannot be null");
        UUID tenantId = context.getTenantId();
        verification.setTenantId(tenantId);

        if (verification.getId() == null) {
            verification.setId(UUID.randomUUID());
            verification.setCreatedAt(OffsetDateTime.now());
        }
        verification.setUpdatedAt(OffsetDateTime.now());
        verification.setRowVersion(verification.getRowVersion() + 1);

        store.put(key(tenantId, verification.getId()), verification);
        return verification;
    }

    @Override
    public Optional<StudentVerificationEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        StudentVerificationEntity entity = store.get(key(context.getTenantId(), id));
        return Optional.ofNullable(entity);
    }

    @Override
    public List<StudentVerificationEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (studentId == null) return Collections.emptyList();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && studentId.equals(v.getStudentId()))
                .sorted(Comparator.comparing(StudentVerificationEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<StudentVerificationEntity> findLatestByStudentId(UserSecurityContext context, UUID studentId) {
        List<StudentVerificationEntity> list = findByStudentId(context, studentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
