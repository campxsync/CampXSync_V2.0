package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionHandoverEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAdmissionHandoverRepository implements AdmissionHandoverRepository {

    private final Map<String, AdmissionHandoverEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public AdmissionHandoverEntity save(AdmissionHandoverEntity handover) {
        if (handover.getId() == null) {
            handover.setId(UUID.randomUUID());
        }
        store.put(key(handover.getTenantId(), handover.getId()), handover);
        return handover;
    }

    @Override
    public Optional<AdmissionHandoverEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)));
    }

    @Override
    public Optional<AdmissionHandoverEntity> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return store.values().stream()
                .filter(h -> h.getTenantId().equals(tenantId) && h.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public Optional<AdmissionHandoverEntity> findByApplication(UUID tenantId, UUID applicationId) {
        return store.values().stream()
                .filter(h -> h.getTenantId().equals(tenantId) && h.getApplicationId().equals(applicationId))
                .findFirst();
    }

    @Override
    public List<AdmissionHandoverEntity> findPendingOrFailed(UUID tenantId) {
        return store.values().stream()
                .filter(h -> h.getTenantId().equals(tenantId)
                        && ("PENDING".equals(h.getEnrolmentStatus()) || "FAILED".equals(h.getEnrolmentStatus())))
                .collect(Collectors.toList());
    }

    @Override
    public List<AdmissionHandoverEntity> findByTenantId(UUID tenantId) {
        return store.values().stream()
                .filter(h -> h.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }
}
