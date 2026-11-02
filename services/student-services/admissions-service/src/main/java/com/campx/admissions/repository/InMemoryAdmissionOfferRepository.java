package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionOfferEntity;
import com.campx.admissions.model.OfferStatus;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAdmissionOfferRepository implements AdmissionOfferRepository {

    private final Map<String, AdmissionOfferEntity> store = new ConcurrentHashMap<>();

    private String key(UUID tenantId, UUID id) {
        return tenantId.toString() + ":" + id.toString();
    }

    @Override
    public AdmissionOfferEntity save(AdmissionOfferEntity offer) {
        if (offer.getId() == null) {
            offer.setId(UUID.randomUUID());
        }
        store.put(key(offer.getTenantId(), offer.getId()), offer);
        return offer;
    }

    @Override
    public Optional<AdmissionOfferEntity> findById(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(key(tenantId, id)));
    }

    @Override
    public Optional<AdmissionOfferEntity> findByOfferCode(UUID tenantId, String offerCode) {
        return store.values().stream()
                .filter(o -> o.getTenantId().equals(tenantId) && o.getOfferCode().equalsIgnoreCase(offerCode))
                .findFirst();
    }

    @Override
    public Optional<AdmissionOfferEntity> findByApplication(UUID tenantId, UUID applicationId) {
        return store.values().stream()
                .filter(o -> o.getTenantId().equals(tenantId) && o.getApplicationId().equals(applicationId))
                .findFirst();
    }

    @Override
    public List<AdmissionOfferEntity> findExpiredOffers(UUID tenantId, OffsetDateTime cutoff) {
        return store.values().stream()
                .filter(o -> o.getTenantId().equals(tenantId)
                        && o.getStatus() == OfferStatus.ISSUED
                        && o.isExpired(cutoff))
                .collect(Collectors.toList());
    }

    @Override
    public List<AdmissionOfferEntity> findByStatus(UUID tenantId, OfferStatus status) {
        return store.values().stream()
                .filter(o -> o.getTenantId().equals(tenantId) && o.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<AdmissionOfferEntity> findByTenantId(UUID tenantId) {
        return store.values().stream()
                .filter(o -> o.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }
}
