package com.campx.student.repository;

import com.campx.student.model.StudentDocumentEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory repository for student document metadata (STM01-US-020, STM01-US-021).
 */
public class InMemoryStudentDocumentRepository implements StudentDocumentRepository {

    // Key: tenantId + ":" + id
    private final Map<String, StudentDocumentEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public StudentDocumentEntity save(UserSecurityContext context, StudentDocumentEntity document) {
        if (document == null) throw new IllegalArgumentException("Document cannot be null");
        UUID tenantId = context.getTenantId();
        document.setTenantId(tenantId);

        if (document.getId() == null) {
            document.setId(UUID.randomUUID());
            document.setCreatedAt(OffsetDateTime.now());
            document.setCreatedBy(context.getUserId());
        }
        document.setUpdatedAt(OffsetDateTime.now());
        document.setUpdatedBy(context.getUserId());
        document.setRowVersion(document.getRowVersion() + 1);

        store.put(key(tenantId, document.getId()), document);
        return document;
    }

    @Override
    public Optional<StudentDocumentEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        StudentDocumentEntity entity = store.get(key(context.getTenantId(), id));
        return Optional.ofNullable(entity);
    }

    @Override
    public List<StudentDocumentEntity> findByStudentId(UserSecurityContext context, UUID studentId) {
        if (studentId == null) return Collections.emptyList();
        UUID tenantId = context.getTenantId();
        return store.values().stream()
                .filter(d -> tenantId.equals(d.getTenantId()) && studentId.equals(d.getStudentId()))
                .collect(Collectors.toList());
    }
}
