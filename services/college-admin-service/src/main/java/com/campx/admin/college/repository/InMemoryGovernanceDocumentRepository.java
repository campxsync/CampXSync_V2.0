package com.campx.admin.college.repository;

import com.campx.admin.college.model.CollegeModels.DocumentVersion;
import com.campx.admin.college.model.CollegeModels.GovernanceDocument;
import com.campx.admin.college.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, tenant-partitioned in-memory test double for {@link GovernanceDocumentRepository}.
 * Guarantees that governance documents and versions for Tenant A are strictly invisible to Tenant B.
 */
public class InMemoryGovernanceDocumentRepository implements GovernanceDocumentRepository {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, Map<String, GovernanceDocument>> documentsByTenant = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<DocumentVersion>>> versionsByTenant = new ConcurrentHashMap<>();

    private UUID resolveTenantKey(UserSecurityContext context) {
        return (context != null && context.getTenantId() != null)
                ? context.getTenantId()
                : DEFAULT_TENANT_ID;
    }

    @Override
    public GovernanceDocument createDocument(UserSecurityContext context, GovernanceDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (document.getId() == null || document.getId().isEmpty()) {
            document.setId(UUID.randomUUID().toString());
        }
        if (document.getStatus() == null || document.getStatus().isEmpty()) {
            document.setStatus("DRAFT");
        }
        if (document.getCurrentVersion() <= 0) {
            document.setCurrentVersion(1);
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, GovernanceDocument> tenantDocs = documentsByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantDocs.put(document.getId(), document);
        return document;
    }

    @Override
    public Optional<GovernanceDocument> findById(UserSecurityContext context, String id) {
        if (id == null) return Optional.empty();
        UUID tenantKey = resolveTenantKey(context);
        Map<String, GovernanceDocument> tenantDocs = documentsByTenant.get(tenantKey);
        if (tenantDocs == null) return Optional.empty();
        return Optional.ofNullable(tenantDocs.get(id));
    }

    @Override
    public List<GovernanceDocument> listDocuments(UserSecurityContext context) {
        UUID tenantKey = resolveTenantKey(context);
        Map<String, GovernanceDocument> tenantDocs = documentsByTenant.get(tenantKey);
        if (tenantDocs == null || tenantDocs.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tenantDocs.values());
    }

    @Override
    public GovernanceDocument updateDocument(UserSecurityContext context, GovernanceDocument document) {
        if (document == null || document.getId() == null) {
            throw new IllegalArgumentException("Document and id cannot be null");
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, GovernanceDocument> tenantDocs = documentsByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantDocs.put(document.getId(), document);
        return document;
    }

    @Override
    public DocumentVersion createVersion(UserSecurityContext context, DocumentVersion version) {
        if (version == null || version.getDocumentId() == null) {
            throw new IllegalArgumentException("Version and documentId cannot be null");
        }
        if (version.getId() == null || version.getId().isEmpty()) {
            version.setId(UUID.randomUUID().toString());
        }
        UUID tenantKey = resolveTenantKey(context);
        Map<String, List<DocumentVersion>> tenantVersions = versionsByTenant.computeIfAbsent(tenantKey, k -> new ConcurrentHashMap<>());
        tenantVersions.computeIfAbsent(version.getDocumentId(), k -> new ArrayList<>()).add(version);

        Map<String, GovernanceDocument> tenantDocs = documentsByTenant.get(tenantKey);
        if (tenantDocs != null) {
            GovernanceDocument doc = tenantDocs.get(version.getDocumentId());
            if (doc != null && version.getVersionNo() > doc.getCurrentVersion()) {
                doc.setCurrentVersion(version.getVersionNo());
            }
        }
        return version;
    }

    @Override
    public List<DocumentVersion> listVersions(UserSecurityContext context, String documentId) {
        if (documentId == null) return Collections.emptyList();
        UUID tenantKey = resolveTenantKey(context);
        Map<String, List<DocumentVersion>> tenantVersions = versionsByTenant.get(tenantKey);
        if (tenantVersions == null) return Collections.emptyList();
        List<DocumentVersion> list = tenantVersions.get(documentId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }
}
