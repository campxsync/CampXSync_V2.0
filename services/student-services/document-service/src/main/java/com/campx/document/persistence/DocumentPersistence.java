package com.campx.document.persistence;

import com.campx.document.Actor;
import com.campx.document.DocumentDomainService;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.DocumentVersion;
import com.campx.document.DocumentDomainService.DocumentLink;

import java.util.List;
import java.util.UUID;

/**
 * Contract for student document catalog and persistence.
 */
public interface DocumentPersistence {

    /**
     * Saves a new document type to doc.document_types.
     */
    void saveDocumentType(Actor actor, DocumentType type);

    /**
     * Finds a document type by its unique code within the tenant.
     */
    DocumentType findDocumentTypeByCode(Actor actor, String code);

    /**
     * Finds a document type by its primary key id within the tenant.
     */
    DocumentType findDocumentTypeById(Actor actor, UUID id);

    /**
     * Lists all document types for the caller's tenant, with optional category/status filters.
     */
    List<DocumentType> listDocumentTypes(Actor actor, String category, Boolean activeOnly);

    /**
     * Updates an existing document type in doc.document_types.
     */
    void updateDocumentType(Actor actor, DocumentType type);

    /**
     * Saves a new stage document requirement to doc.stage_document_requirements (STM-06-US-002).
     */
    void saveStageRequirement(Actor actor, StageDocumentRequirement requirement);

    /**
     * Finds a stage requirement by its primary key ID within the tenant.
     */
    StageDocumentRequirement findStageRequirementById(Actor actor, UUID id);

    /**
     * Finds a requirement for a specific program (or null), stage, and document type within the tenant.
     */
    StageDocumentRequirement findStageRequirement(Actor actor, UUID programId, String stage, UUID documentTypeId);

    /**
     * Lists stage document requirements for the tenant with optional filters.
     */
    List<StageDocumentRequirement> listStageRequirements(Actor actor, UUID programId, String stage, Boolean activeOnly);

    /**
     * Updates an existing stage document requirement.
     */
    void updateStageRequirement(Actor actor, StageDocumentRequirement requirement);

    /**
     * Deletes (soft-deletes) a stage document requirement.
     */
    void deleteStageRequirement(Actor actor, UUID id);

    /**
     * Atomically saves a new student document, its version 1 record, and entity link (STM-06-US-003).
     */
    void saveStudentDocument(Actor actor, DocumentDomainService.StudentDocument document,
                             DocumentDomainService.DocumentVersion version,
                             DocumentDomainService.DocumentLink link);

    /**
     * Finds a student document by its primary key id within the tenant.
     */
    DocumentDomainService.StudentDocument findStudentDocumentById(Actor actor, UUID id);

    /**
     * Lists documents owned by a student within the tenant, with optional status filter.
     */
    List<DocumentDomainService.StudentDocument> listStudentDocuments(Actor actor, UUID studentId, String status);

    /**
     * Lists all version records for a document within the tenant.
     */
    List<DocumentDomainService.DocumentVersion> listDocumentVersions(Actor actor, UUID documentId);

    /**
     * Retrieves the institutional verification queue of student submissions (STM-06-US-004).
     */
    List<DocumentDomainService.VerificationQueueItem> getVerificationQueue(Actor actor, String statusFilter);

    /**
     * Atomically updates document status and persists a verification decision in doc.verifications (STM-06-US-004).
     */
    void recordVerificationDecision(Actor actor, UUID documentId, String newStatus, DocumentDomainService.DocumentVerificationRecord verification);

    /**
     * Retrieves the latest verification record for a document within the tenant.
     */
    DocumentDomainService.DocumentVerificationRecord findLatestVerificationByDocumentId(Actor actor, UUID documentId);

    /**
     * Atomically appends a new document version and updates the student document back to SUBMITTED (STM-06-US-005).
     */
    void resubmitStudentDocument(Actor actor, UUID documentId, DocumentDomainService.DocumentVersion newVersion, String newMimeType);

    /**
     * Records an immutable access/download log in doc.document_deliveries (STM-06-US-006).
     */
    DocumentDomainService.DocumentAccessLog recordAccessLog(Actor actor, UUID documentId, String channel, String status, String clientIp);

    /**
     * Retrieves the regulatory access audit trail for a document (STM-06-US-006).
     */
    List<DocumentDomainService.DocumentAccessLog> listAccessLogs(Actor actor, UUID documentId);

    /**
     * Appends a document link row (access grant/revoke or sharing consent) to doc.document_links.
     */
    void saveDocumentLink(Actor actor, DocumentDomainService.DocumentLink link);

    /**
     * Lists document links for a document within the tenant, newest first.
     */
    List<DocumentDomainService.DocumentLink> listDocumentLinks(Actor actor, UUID documentId);
}

