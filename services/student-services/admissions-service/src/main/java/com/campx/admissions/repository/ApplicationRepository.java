package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.ApplicationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {
    ApplicationEntity save(ApplicationEntity application);

    Optional<ApplicationEntity> findById(UUID tenantId, UUID id);

    Optional<ApplicationEntity> findByApplicationNumber(UUID tenantId, UUID intakeId, String applicationNumber);

    Optional<ApplicationEntity> findActiveByApplicant(UUID tenantId, UUID intakeId, UUID applicantPersonId);

    List<ApplicationEntity> findByIntake(UUID tenantId, UUID intakeId);

    List<ApplicationEntity> findByTenantId(UUID tenantId);

    List<ApplicationEntity> findByApplicant(UUID tenantId, UUID applicantPersonId);

    List<ApplicationEntity> findByStatus(UUID tenantId, ApplicationStatus status);

    List<ApplicationEntity> findByReviewer(UUID tenantId, UUID reviewerId);

    List<ApplicationEntity> findByDepartment(UUID tenantId, UUID departmentId);

    boolean existsByApplicationNumber(UUID tenantId, UUID intakeId, String applicationNumber);
}
