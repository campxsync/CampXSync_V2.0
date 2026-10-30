package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.Subscription;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double implementation of {@link SubscriptionRepository}.
 */
public class InMemorySubscriptionRepository implements SubscriptionRepository {

    private final Map<String, Subscription> store = new ConcurrentHashMap<>();

    @Override
    public Subscription createSubscription(UserSecurityContext context, Subscription sub) {
        if (sub == null) {
            throw new MalformedPayloadException("Subscription payload cannot be null");
        }
        if (sub.getPlanId() == null || sub.getPlanId().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'planId' is required");
        }

        if (sub.getId() == null || sub.getId().trim().isEmpty()) {
            sub.setId(UUID.randomUUID().toString());
        }
        if (context != null && context.getTenantId() != null) {
            sub.setTenantId(context.getTenantId().toString());
        }
        if (sub.getStatus() == null) {
            sub.setStatus("ACTIVE");
        }
        sub.setCreatedAt(System.currentTimeMillis());
        sub.setUpdatedAt(sub.getCreatedAt());
        sub.setRowVersion(1);

        store.put(sub.getId(), sub);
        return sub;
    }

    @Override
    public Subscription getSubscriptionById(UserSecurityContext context, String id) {
        if (id == null) return null;
        return store.get(id);
    }

    @Override
    public Subscription getActiveSubscription(UserSecurityContext context) {
        String tenantIdStr = context != null && context.getTenantId() != null ? context.getTenantId().toString() : null;
        for (Subscription sub : store.values()) {
            if ("ACTIVE".equalsIgnoreCase(sub.getStatus())) {
                if (tenantIdStr == null || tenantIdStr.equals(sub.getTenantId())) {
                    return sub;
                }
            }
        }
        return null;
    }

    @Override
    public List<Subscription> listSubscriptions(UserSecurityContext context) {
        String tenantIdStr = context != null && context.getTenantId() != null ? context.getTenantId().toString() : null;
        List<Subscription> list = new ArrayList<>();
        for (Subscription sub : store.values()) {
            if (tenantIdStr == null || tenantIdStr.equals(sub.getTenantId())) {
                list.add(sub);
            }
        }
        return list;
    }

    @Override
    public Subscription updateSubscription(UserSecurityContext context, Subscription sub) {
        if (sub == null || sub.getId() == null) {
            throw new MalformedPayloadException("Subscription ID is required");
        }
        Subscription existing = store.get(sub.getId());
        if (existing == null) {
            throw new ResourceNotFoundException("Subscription", sub.getId());
        }
        existing.setStatus(sub.getStatus());
        existing.setAutoRenew(sub.isAutoRenew());
        existing.setEndDate(sub.getEndDate());
        existing.setUpdatedAt(System.currentTimeMillis());
        existing.setRowVersion(existing.getRowVersion() + 1);
        return existing;
    }

    @Override
    public void cancelSubscription(UserSecurityContext context, String id) {
        if (id != null) {
            Subscription existing = store.get(id);
            if (existing != null) {
                existing.setStatus("CANCELLED");
            }
        }
    }
}
