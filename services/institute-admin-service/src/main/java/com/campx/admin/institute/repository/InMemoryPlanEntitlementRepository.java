package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.PlanEntitlement;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double implementation of {@link PlanEntitlementRepository}.
 */
public class InMemoryPlanEntitlementRepository implements PlanEntitlementRepository {

    private final Map<String, PlanEntitlement> store = new ConcurrentHashMap<>();

    @Override
    public PlanEntitlement createEntitlement(UserSecurityContext context, PlanEntitlement entitlement) {
        if (entitlement == null) {
            throw new MalformedPayloadException("Plan entitlement payload cannot be null");
        }
        if (entitlement.getPlanId() == null || entitlement.getPlanId().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'planId' is required");
        }
        if (entitlement.getEntitlementKey() == null || entitlement.getEntitlementKey().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'entitlementKey' is required");
        }

        String planId = entitlement.getPlanId().trim();
        String key = entitlement.getEntitlementKey().trim().toUpperCase(Locale.ROOT);

        for (PlanEntitlement existing : store.values()) {
            if (planId.equalsIgnoreCase(existing.getPlanId()) && key.equalsIgnoreCase(existing.getEntitlementKey())) {
                throw new ResourceConflictException("PlanEntitlement", "entitlementKey", key);
            }
        }

        if (entitlement.getId() == null || entitlement.getId().trim().isEmpty()) {
            entitlement.setId(UUID.randomUUID().toString());
        }
        entitlement.setPlanId(planId);
        entitlement.setEntitlementKey(key);
        entitlement.setCreatedAt(System.currentTimeMillis());
        entitlement.setUpdatedAt(entitlement.getCreatedAt());
        entitlement.setRowVersion(1);

        store.put(entitlement.getId(), entitlement);
        return entitlement;
    }

    @Override
    public PlanEntitlement getEntitlementById(UserSecurityContext context, String id) {
        if (id == null) return null;
        return store.get(id);
    }

    @Override
    public List<PlanEntitlement> listEntitlementsByPlan(UserSecurityContext context, String planId) {
        if (planId == null) return Collections.emptyList();
        List<PlanEntitlement> result = new ArrayList<>();
        for (PlanEntitlement e : store.values()) {
            if (planId.equalsIgnoreCase(e.getPlanId())) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public PlanEntitlement updateEntitlement(UserSecurityContext context, PlanEntitlement entitlement) {
        if (entitlement == null || entitlement.getId() == null) {
            throw new MalformedPayloadException("Plan entitlement and ID required for update");
        }
        PlanEntitlement existing = store.get(entitlement.getId());
        if (existing == null) {
            throw new ResourceNotFoundException("PlanEntitlement", entitlement.getId());
        }

        existing.setLimitValue(entitlement.getLimitValue());
        existing.setEnabled(entitlement.isEnabled());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setRowVersion(existing.getRowVersion() + 1);
        return existing;
    }

    @Override
    public boolean deleteEntitlement(UserSecurityContext context, String id) {
        if (id == null) return false;
        return store.remove(id) != null;
    }
}
