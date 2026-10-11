package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.model.InstituteModels.UsageMetric;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback / testing implementation of {@link UsageMetricRepository}.
 */
public class InMemoryUsageMetricRepository implements UsageMetricRepository {

    private final Map<String, UsageMetric> store = new ConcurrentHashMap<>();

    @Override
    public UsageMetric recordUsageMetric(UserSecurityContext context, UsageMetric metric) {
        if (metric == null) {
            throw new MalformedPayloadException("Usage metric payload cannot be null");
        }
        if (metric.getTenantId() == null || metric.getTenantId().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'tenantId' is required");
        }
        if (metric.getMetricType() == null || metric.getMetricType().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'metricType' is required");
        }

        if (metric.getId() == null || metric.getId().trim().isEmpty()) {
            metric.setId(UUID.randomUUID().toString());
        }
        if (metric.getPeriod() == null || metric.getPeriod().trim().isEmpty()) {
            metric.setPeriod("2026-10");
        }
        if (metric.getCalculatedAt() <= 0) {
            metric.setCalculatedAt(System.currentTimeMillis());
        }

        store.put(metric.getId(), metric);
        return metric;
    }

    @Override
    public List<UsageMetric> findByTenantAndPeriod(UserSecurityContext context, UUID tenantId, String period) {
        if (tenantId == null) return Collections.emptyList();
        String tid = tenantId.toString();
        List<UsageMetric> list = new ArrayList<>();
        for (UsageMetric m : store.values()) {
            if (tid.equalsIgnoreCase(m.getTenantId())) {
                if (period == null || period.trim().isEmpty() || period.equalsIgnoreCase(m.getPeriod())) {
                    list.add(m);
                }
            }
        }
        return list;
    }

    @Override
    public List<UsageMetric> findByTenant(UserSecurityContext context, UUID tenantId) {
        return findByTenantAndPeriod(context, tenantId, null);
    }

    @Override
    public Optional<UsageMetric> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        UsageMetric m = store.get(id.toString());
        return Optional.ofNullable(m);
    }

    @Override
    public void deleteById(UserSecurityContext context, UUID id) {
        if (id != null) {
            store.remove(id.toString());
        }
    }
}
