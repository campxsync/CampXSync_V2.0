package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository {
    ApplicationStatusHistoryEntity save(ApplicationStatusHistoryEntity history);

    List<ApplicationStatusHistoryEntity> findByApplication(UUID tenantId, UUID applicationId);
}
