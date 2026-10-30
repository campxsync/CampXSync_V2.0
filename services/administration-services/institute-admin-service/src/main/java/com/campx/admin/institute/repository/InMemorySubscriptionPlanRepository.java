package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.SubscriptionPlan;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double implementation of {@link SubscriptionPlanRepository}.
 */
public class InMemorySubscriptionPlanRepository implements SubscriptionPlanRepository {

    private final Map<String, SubscriptionPlan> store = new ConcurrentHashMap<>();

    public InMemorySubscriptionPlanRepository() {
        seedDefaults();
    }

    private void seedDefaults() {
        SubscriptionPlan standard = new SubscriptionPlan();
        standard.setId("PLAN_STD_MONTHLY");
        standard.setPlanCode("STANDARD_MONTHLY");
        standard.setName("Standard Campus Monthly");
        standard.setBillingCycle("MONTHLY");
        standard.setPrice(9999.0);
        standard.setCurrencyCode("INR");
        standard.setPublished(true);
        store.put(standard.getId(), standard);
    }

    @Override
    public SubscriptionPlan createPlan(UserSecurityContext context, SubscriptionPlan plan) {
        if (plan == null) {
            throw new MalformedPayloadException("Plan payload cannot be null");
        }
        if (plan.getPlanCode() == null || plan.getPlanCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'planCode' is required");
        }
        if (plan.getName() == null || plan.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'name' is required");
        }

        String code = plan.getPlanCode().trim().toUpperCase(Locale.ROOT);
        for (SubscriptionPlan existing : store.values()) {
            if (code.equalsIgnoreCase(existing.getPlanCode())) {
                throw new ResourceConflictException("SubscriptionPlan", "planCode", code);
            }
        }

        if (plan.getId() == null || plan.getId().trim().isEmpty()) {
            plan.setId(UUID.randomUUID().toString());
        }
        plan.setPlanCode(code);
        plan.setCreatedAt(System.currentTimeMillis());
        plan.setUpdatedAt(plan.getCreatedAt());
        plan.setRowVersion(1);

        store.put(plan.getId(), plan);
        return plan;
    }

    @Override
    public SubscriptionPlan getPlanById(UserSecurityContext context, String id) {
        if (id == null) return null;
        return store.get(id);
    }

    @Override
    public SubscriptionPlan getPlanByCode(UserSecurityContext context, String planCode) {
        if (planCode == null) return null;
        for (SubscriptionPlan p : store.values()) {
            if (planCode.equalsIgnoreCase(p.getPlanCode())) {
                return p;
            }
        }
        return null;
    }

    @Override
    public List<SubscriptionPlan> listPlans(UserSecurityContext context) {
        List<SubscriptionPlan> list = new ArrayList<>();
        for (SubscriptionPlan p : store.values()) {
            if (p.isPublished()) {
                list.add(p);
            }
        }
        return list;
    }

    @Override
    public SubscriptionPlan updatePlan(UserSecurityContext context, SubscriptionPlan plan) {
        if (plan == null || plan.getId() == null) {
            throw new MalformedPayloadException("Plan ID is required");
        }
        SubscriptionPlan existing = store.get(plan.getId());
        if (existing == null) {
            throw new ResourceNotFoundException("SubscriptionPlan", plan.getId());
        }
        existing.setName(plan.getName());
        existing.setPrice(plan.getPrice());
        existing.setBillingCycle(plan.getBillingCycle());
        existing.setPublished(plan.isPublished());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setRowVersion(existing.getRowVersion() + 1);
        return existing;
    }

    @Override
    public void deletePlan(UserSecurityContext context, String id) {
        if (id != null) {
            SubscriptionPlan existing = store.get(id);
            if (existing != null) {
                existing.setPublished(false);
            }
        }
    }
}
