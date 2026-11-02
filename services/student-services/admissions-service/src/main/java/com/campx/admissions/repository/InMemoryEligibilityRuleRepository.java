package com.campx.admissions.repository;

import com.campx.admissions.model.EligibilityRuleEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryEligibilityRuleRepository implements EligibilityRuleRepository {

    private final Map<String, EligibilityRuleEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public EligibilityRuleEntity save(EligibilityRuleEntity rule) {
        if (rule.getId() == null) {
            rule.setId(UUID.randomUUID());
        }
        store.put(key(rule.getTenantId(), rule.getId()), rule);
        return rule;
    }

    @Override
    public Optional<EligibilityRuleEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)));
    }

    @Override
    public Optional<EligibilityRuleEntity> findLatestActiveByIntake(UUID tenantId, UUID intakeId) {
        return store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId)
                        && r.getIntakeId().equals(intakeId)
                        && r.isActive())
                .max(Comparator.comparing(EligibilityRuleEntity::getCreatedAt));
    }

    @Override
    public List<EligibilityRuleEntity> findAllByIntake(UUID tenantId, UUID intakeId) {
        return store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId)
                        && r.getIntakeId().equals(intakeId))
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }
}
