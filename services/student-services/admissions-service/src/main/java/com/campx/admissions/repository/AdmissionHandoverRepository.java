package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionHandoverEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionHandoverRepository {
    AdmissionHandoverEntity save(AdmissionHandoverEntity handover);

    Optional<AdmissionHandoverEntity> findById(UUID tenantId, UUID id);

    Optional<AdmissionHandoverEntity> findByIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<AdmissionHandoverEntity> findByApplication(UUID tenantId, UUID applicationId);

    List<AdmissionHandoverEntity> findPendingOrFailed(UUID tenantId);

    List<AdmissionHandoverEntity> findByTenantId(UUID tenantId);
}
