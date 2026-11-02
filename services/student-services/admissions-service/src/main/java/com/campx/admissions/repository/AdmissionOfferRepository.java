package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionOfferEntity;
import com.campx.admissions.model.OfferStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionOfferRepository {
    AdmissionOfferEntity save(AdmissionOfferEntity offer);

    Optional<AdmissionOfferEntity> findById(UUID tenantId, UUID id);

    Optional<AdmissionOfferEntity> findByOfferCode(UUID tenantId, String offerCode);

    Optional<AdmissionOfferEntity> findByApplication(UUID tenantId, UUID applicationId);

    List<AdmissionOfferEntity> findExpiredOffers(UUID tenantId, OffsetDateTime cutoff);

    List<AdmissionOfferEntity> findByStatus(UUID tenantId, OfferStatus status);

    List<AdmissionOfferEntity> findByTenantId(UUID tenantId);
}
