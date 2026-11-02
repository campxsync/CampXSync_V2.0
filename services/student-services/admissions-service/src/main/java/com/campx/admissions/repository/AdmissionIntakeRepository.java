package com.campx.admissions.repository;

import com.campx.admissions.model.AdmissionIntakeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionIntakeRepository {
    AdmissionIntakeEntity save(AdmissionIntakeEntity intake);

    Optional<AdmissionIntakeEntity> findById(UUID tenantId, UUID id);

    Optional<AdmissionIntakeEntity> findByCode(UUID tenantId, UUID academicYearId, String intakeCode);

    List<AdmissionIntakeEntity> findActiveIntakes(UUID tenantId);

    List<AdmissionIntakeEntity> findByProgram(UUID tenantId, UUID programId);

    List<AdmissionIntakeEntity> findByTenantId(UUID tenantId);

    boolean existsByCode(UUID tenantId, UUID academicYearId, String intakeCode);
}
