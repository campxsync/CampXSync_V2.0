package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.DocumentVersion;
import com.campx.admin.college.model.CollegeModels.GovernanceDocument;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing governance documents in {@code doc.documents}
 * and immutable versions in {@code doc.document_versions}.
 */
public interface GovernanceDocumentRepository {

    /**
     * Creates a new governance document record.
     *
     * @param context  caller security context
     * @param document document entity to persist
     * @return persisted document entity
     */
    GovernanceDocument createDocument(UserSecurityContext context, GovernanceDocument document);

    /**
     * Finds a document by identifier.
     *
     * @param context caller security context
     * @param id      document UUID
     * @return optional containing document if found
     */
    Optional<GovernanceDocument> findById(UserSecurityContext context, String id);

    /**
     * Lists documents within the caller's tenant boundary.
     *
     * @param context caller security context
     * @return list of documents
     */
    List<GovernanceDocument> listDocuments(UserSecurityContext context);

    /**
     * Updates an existing document record.
     *
     * @param context  caller security context
     * @param document document entity to update
     * @return updated document entity
     */
    GovernanceDocument updateDocument(UserSecurityContext context, GovernanceDocument document);

    /**
     * Appends an immutable document version snapshot.
     *
     * @param context caller security context
     * @param version version entity
     * @return persisted version entity
     */
    DocumentVersion createVersion(UserSecurityContext context, DocumentVersion version);

    /**
     * Lists immutable versions for a document.
     *
     * @param context    caller security context
     * @param documentId document UUID
     * @return list of version entities
     */
    List<DocumentVersion> listVersions(UserSecurityContext context, String documentId);
}
