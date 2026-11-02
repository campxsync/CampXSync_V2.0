package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationReviewEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationReviewRepository {
    ApplicationReviewEntity save(ApplicationReviewEntity review);

    Optional<ApplicationReviewEntity> findById(UUID tenantId, UUID id);

    Optional<ApplicationReviewEntity> findByApplicationAndReviewer(UUID tenantId, UUID applicationId, UUID reviewerId);

    List<ApplicationReviewEntity> findByApplication(UUID tenantId, UUID applicationId);

    List<ApplicationReviewEntity> findByReviewer(UUID tenantId, UUID reviewerId);
}
