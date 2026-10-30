package com.campx.student.repository;

import com.campx.student.model.StudentCorrectionRequestEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory repository for profile correction requests (STM01-US-010, STM01-US-011).
 */
public class InMemoryCorrectionRequestRepository implements CorrectionRequestRepository {

    // Key: tenantId + ":" + id
    private final Map<String, StudentCorrectionRequestEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public StudentCorrectionRequestEntity save(UserSecurityContext context, StudentCorrectionRequestEntity request) {
        if (request == null) throw new IllegalArgumentException("Correction request cannot be null");
        UUID tenantId = context.getTenantId();
        request.setTenantId(tenantId);

        if (request.getId() == null) {
            request.setId(UUID.randomUUID());
            request.setCreatedAt(OffsetDateTime.now());
        }
        request.setUpdatedAt(OffsetDateTime.now());
        request.setRowVersion(request.getRowVersion() + 1);

        store.put(key(tenantId, request.getId()), request);
        return request;
    }

    @Override
    public Optional<StudentCorrectionRequestEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        StudentCorrectionRequestEntity entity = store.get(key(context.getTenantId(), id));
        return Optional.ofNullable(entity);
    }

    @Override
    public List<StudentCorrectionRequestEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (studentId == null) return Collections.emptyList();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(r -> tenantId.equals(r.getTenantId()) && studentId.equals(r.getStudentId()))
                .sorted(Comparator.comparing(StudentCorrectionRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public List<StudentCorrectionRequestEntity> findPendingByTenant(UserSecurityContext context) {
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(r -> tenantId.equals(r.getTenantId()) && "PENDING".equalsIgnoreCase(r.getStatus()))
                .sorted(Comparator.comparing(StudentCorrectionRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }
}
