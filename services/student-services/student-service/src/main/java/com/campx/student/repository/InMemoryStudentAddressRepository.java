package com.campx.student.repository;

import com.campx.student.model.StudentAddressEntity;
import com.campx.student.security.UserSecurityContext;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory repository for student addresses (STM01-US-017).
 */
public class InMemoryStudentAddressRepository implements StudentAddressRepository {

    // Key: tenantId + ":" + id
    private final Map<String, StudentAddressEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public StudentAddressEntity save(UserSecurityContext context, StudentAddressEntity address) {
        if (address == null) throw new IllegalArgumentException("Address cannot be null");
        UUID tenantId = context.getTenantId();
        address.setTenantId(tenantId);

        if (address.getId() == null) {
            address.setId(UUID.randomUUID());
            address.setCreatedAt(OffsetDateTime.now());
            address.setCreatedBy(context.getUserId());
        }
        address.setUpdatedAt(OffsetDateTime.now());
        address.setUpdatedBy(context.getUserId());
        address.setRowVersion(address.getRowVersion() + 1);

        store.put(key(tenantId, address.getId()), address);
        return address;
    }

    @Override
    public Optional<StudentAddressEntity> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        StudentAddressEntity entity = store.get(key(context.getTenantId(), id));
        if (entity != null && entity.getDeletedAt() == null) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }
}
