package com.campx.admissions.repository;

import com.campx.admissions.model.ApplicationDocumentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationDocumentRepository {
    ApplicationDocumentEntity save(ApplicationDocumentEntity document);

    Optional<ApplicationDocumentEntity> findById(UUID tenantId, UUID id);

    Optional<ApplicationDocumentEntity> findByDocumentId(UUID tenantId, UUID documentId);

    List<ApplicationDocumentEntity> findByApplication(UUID tenantId, UUID applicationId);

    void delete(UUID tenantId, UUID id);
}
