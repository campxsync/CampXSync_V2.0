package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionDecisionEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAdmissionDecisionRepository implements AdmissionDecisionRepository {

    private final Map<String, AdmissionDecisionEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public AdmissionDecisionEntity save(AdmissionDecisionEntity decision) {
        if (decision.getId() == null) {
            decision.setId(UUID.randomUUID());
        }
        store.put(key(decision.getTenantId(), decision.getId()), decision);
        return decision;
    }

    @Override
    public Optional<AdmissionDecisionEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)));
    }

    @Override
    public Optional<AdmissionDecisionEntity> findByApplication(UUID tenantId, UUID applicationId) {
        return store.values().stream()
                .filter(d -> d.getTenantId().equals(tenantId) && d.getApplicationId().equals(applicationId))
                .findFirst();
    }

    @Override
    public List<AdmissionDecisionEntity> findByTenantId(UUID tenantId) {
        List<AdmissionDecisionEntity> list = new ArrayList<>();
        for (AdmissionDecisionEntity d : store.values()) {
            if (d.getTenantId().equals(tenantId)) {
                list.add(d);
            }
        }
        return list;
    }

    public void clear() {
        store.clear();
    }
}
