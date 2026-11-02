package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationReviewEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryApplicationReviewRepository implements ApplicationReviewRepository {

    private final Map<String, ApplicationReviewEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public ApplicationReviewEntity save(ApplicationReviewEntity review) {
        if (review.getId() == null) {
            review.setId(UUID.randomUUID());
        }
        store.put(key(review.getTenantId(), review.getId()), review);
        return review;
    }

    @Override
    public Optional<ApplicationReviewEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)));
    }

    @Override
    public Optional<ApplicationReviewEntity> findByApplicationAndReviewer(UUID tenantId, UUID applicationId,
            UUID reviewerId) {
        return store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId)
                        && r.getApplicationId().equals(applicationId)
                        && r.getReviewerId().equals(reviewerId))
                .findFirst();
    }

    @Override
    public List<ApplicationReviewEntity> findByApplication(UUID tenantId, UUID applicationId) {
        return store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getApplicationId().equals(applicationId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ApplicationReviewEntity> findByReviewer(UUID tenantId, UUID reviewerId) {
        return store.values().stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.getReviewerId().equals(reviewerId))
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }
}
