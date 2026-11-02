package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionDecisionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionDecisionRepository {
    AdmissionDecisionEntity save(AdmissionDecisionEntity decision);

    Optional<AdmissionDecisionEntity> findById(UUID tenantId, UUID id);

    Optional<AdmissionDecisionEntity> findByApplication(UUID tenantId, UUID applicationId);

    List<AdmissionDecisionEntity> findByTenantId(UUID tenantId);
}
