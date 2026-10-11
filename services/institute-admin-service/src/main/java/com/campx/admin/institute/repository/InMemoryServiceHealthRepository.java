package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.model.InstituteModels.PlatformHealth;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback and test double implementation of {@link ServiceHealthRepository}.
 */
public class InMemoryServiceHealthRepository implements ServiceHealthRepository {

    private final Map<String, PlatformHealth> storeByService = new ConcurrentHashMap<>();

    @Override
    public PlatformHealth recordHeartbeat(UserSecurityContext context, PlatformHealth health) {
        if (health == null) {
            throw new MalformedPayloadException("PlatformHealth payload cannot be null");
        }
        String serviceName = health.getComponent();
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'component' (serviceName) is required");
        }
        serviceName = serviceName.trim().toUpperCase(Locale.ROOT);

        if (health.getId() == null || health.getId().trim().isEmpty()) {
            health.setId(UUID.randomUUID().toString());
        }
        health.setComponent(serviceName);
        if (health.getStatus() == null || health.getStatus().trim().isEmpty()) {
            health.setStatus("HEALTHY");
        }
        health.setObservedAt(System.currentTimeMillis());

        storeByService.put(serviceName, health);
        return health;
    }

    @Override
    public Optional<PlatformHealth> findByServiceName(UserSecurityContext context, String serviceName) {
        if (serviceName == null) return Optional.empty();
        PlatformHealth h = storeByService.get(serviceName.trim().toUpperCase(Locale.ROOT));
        return Optional.ofNullable(h);
    }

    @Override
    public List<PlatformHealth> listAll(UserSecurityContext context) {
        return new ArrayList<>(storeByService.values());
    }
}
