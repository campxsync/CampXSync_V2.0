package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationDocumentEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryApplicationDocumentRepository implements ApplicationDocumentRepository {

    private final Map<String, ApplicationDocumentEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public ApplicationDocumentEntity save(ApplicationDocumentEntity document) {
        if (document.getId() == null) {
            document.setId(UUID.randomUUID());
        }
        store.put(key(document.getTenantId(), document.getId()), document);
        return document;
    }

    @Override
    public Optional<ApplicationDocumentEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)));
    }

    @Override
    public Optional<ApplicationDocumentEntity> findByDocumentId(UUID tenantId, UUID documentId) {
        return store.values().stream()
                .filter(d -> d.getTenantId().equals(tenantId) && d.getDocumentId().equals(documentId))
                .findFirst();
    }

    @Override
    public List<ApplicationDocumentEntity> findByApplication(UUID tenantId, UUID applicationId) {
        return store.values().stream()
                .filter(d -> d.getTenantId().equals(tenantId) && d.getApplicationId().equals(applicationId))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(UUID tenantId, UUID id) {
        store.remove(key(tenantId, id));
    }

    public void clear() {
        store.clear();
    }
}
