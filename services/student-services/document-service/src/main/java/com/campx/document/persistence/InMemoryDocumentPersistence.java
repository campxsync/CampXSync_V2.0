package com.campx.document.persistence;

import com.campx.document.Actor;
import com.campx.document.DocumentDomainService;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.DocumentVersion;
import com.campx.document.DocumentDomainService.DocumentLink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory persistence for fast isolated unit tests.
 */
public class InMemoryDocumentPersistence implements DocumentPersistence {

    private final Map<UUID, DocumentType> storage = new ConcurrentHashMap<UUID, DocumentType>();
    private final Map<UUID, StageDocumentRequirement> reqStorage = new ConcurrentHashMap<UUID, StageDocumentRequirement>();
    private final Map<UUID, DocumentDomainService.StudentDocument> docStorage = new ConcurrentHashMap<UUID, DocumentDomainService.StudentDocument>();
    private final Map<UUID, List<DocumentDomainService.DocumentVersion>> verStorage = new ConcurrentHashMap<UUID, List<DocumentDomainService.DocumentVersion>>();
    private final Map<UUID, List<DocumentDomainService.DocumentLink>> linkStorage = new ConcurrentHashMap<UUID, List<DocumentDomainService.DocumentLink>>();
    private final Map<UUID, List<DocumentDomainService.DocumentVerificationRecord>> verifStorage = new ConcurrentHashMap<UUID, List<DocumentDomainService.DocumentVerificationRecord>>();
    private final Map<UUID, List<DocumentDomainService.DocumentAccessLog>> accessStorage = new ConcurrentHashMap<UUID, List<DocumentDomainService.DocumentAccessLog>>();

    @Override
    public void saveDocumentType(Actor actor, DocumentType type) {
        storage.put(type.id, type);
    }

    @Override
    public DocumentType findDocumentTypeByCode(Actor actor, String code) {
        for (DocumentType dt : storage.values()) {
            if (dt.tenantId.equals(actor.getTenantId()) && dt.code.equalsIgnoreCase(code)) {
                return dt;
            }
        }
        return null;
    }

    @Override
    public DocumentType findDocumentTypeById(Actor actor, UUID id) {
        DocumentType dt = storage.get(id);
        if (dt != null && dt.tenantId.equals(actor.getTenantId())) {
            return dt;
        }
        return null;
    }

    @Override
    public List<DocumentType> listDocumentTypes(Actor actor, String category, Boolean activeOnly) {
        List<DocumentType> result = new ArrayList<DocumentType>();
        for (DocumentType dt : storage.values()) {
            if (!dt.tenantId.equals(actor.getTenantId())) {
                continue;
            }
            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase(dt.category)) {
                continue;
            }
            if (activeOnly != null && dt.isActive != activeOnly) {
                continue;
            }
            result.add(dt);
        }
        Collections.sort(result, new Comparator<DocumentType>() {
            @Override
            public int compare(DocumentType o1, DocumentType o2) {
                return o1.code.compareToIgnoreCase(o2.code);
            }
        });
        return result;
    }

    @Override
    public void updateDocumentType(Actor actor, DocumentType type) {
        storage.put(type.id, type);
    }

    @Override
    public void saveStageRequirement(Actor actor, StageDocumentRequirement requirement) {
        reqStorage.put(requirement.id, requirement);
    }

    @Override
    public StageDocumentRequirement findStageRequirementById(Actor actor, UUID id) {
        StageDocumentRequirement r = reqStorage.get(id);
        if (r != null && r.tenantId.equals(actor.getTenantId())) {
            return r;
        }
        return null;
    }

    @Override
    public StageDocumentRequirement findStageRequirement(Actor actor, UUID programId, String stage, UUID documentTypeId) {
        for (StageDocumentRequirement r : reqStorage.values()) {
            if (!r.tenantId.equals(actor.getTenantId())) {
                continue;
            }
            if (!r.academicStage.equalsIgnoreCase(stage)) {
                continue;
            }
            if (!r.documentTypeId.equals(documentTypeId)) {
                continue;
            }
            boolean progMatches = (programId == null && r.programId == null)
                    || (programId != null && programId.equals(r.programId));
            if (progMatches) {
                return r;
            }
        }
        return null;
    }

    @Override
    public List<StageDocumentRequirement> listStageRequirements(Actor actor, UUID programId, String stage, Boolean activeOnly) {
        List<StageDocumentRequirement> result = new ArrayList<StageDocumentRequirement>();
        for (StageDocumentRequirement r : reqStorage.values()) {
            if (!r.tenantId.equals(actor.getTenantId())) {
                continue;
            }
            if (programId != null && (r.programId == null || !programId.equals(r.programId))) {
                continue;
            }
            if (stage != null && !stage.trim().isEmpty() && !stage.equalsIgnoreCase(r.academicStage)) {
                continue;
            }
            if (activeOnly != null && r.isActive != activeOnly) {
                continue;
            }
            result.add(r);
        }
        Collections.sort(result, new Comparator<StageDocumentRequirement>() {
            @Override
            public int compare(StageDocumentRequirement o1, StageDocumentRequirement o2) {
                int stageComp = o1.academicStage.compareTo(o2.academicStage);
                if (stageComp != 0) return stageComp;
                return o1.documentTypeCode.compareToIgnoreCase(o2.documentTypeCode);
            }
        });
        return result;
    }

    @Override
    public void updateStageRequirement(Actor actor, StageDocumentRequirement requirement) {
        reqStorage.put(requirement.id, requirement);
    }

    @Override
    public void deleteStageRequirement(Actor actor, UUID id) {
        StageDocumentRequirement r = reqStorage.get(id);
        if (r != null && r.tenantId.equals(actor.getTenantId())) {
            reqStorage.remove(id);
        }
    }

    @Override
    public void saveStudentDocument(Actor actor, DocumentDomainService.StudentDocument document,
                                    DocumentDomainService.DocumentVersion version,
                                    DocumentDomainService.DocumentLink link) {
        docStorage.put(document.id, document);

        List<DocumentDomainService.DocumentVersion> versions = verStorage.get(document.id);
        if (versions == null) {
            versions = new ArrayList<DocumentDomainService.DocumentVersion>();
            verStorage.put(document.id, versions);
        }
        versions.add(version);

        List<DocumentDomainService.DocumentLink> links = linkStorage.get(document.id);
        if (links == null) {
            links = new ArrayList<DocumentDomainService.DocumentLink>();
            linkStorage.put(document.id, links);
        }
        links.add(link);
    }

    @Override
    public DocumentDomainService.StudentDocument findStudentDocumentById(Actor actor, UUID id) {
        DocumentDomainService.StudentDocument doc = docStorage.get(id);
        if (doc != null && doc.tenantId.equals(actor.getTenantId())) {
            return doc;
        }
        return null;
    }

    @Override
    public List<DocumentDomainService.StudentDocument> listStudentDocuments(Actor actor, UUID studentId, String status) {
        List<DocumentDomainService.StudentDocument> result = new ArrayList<DocumentDomainService.StudentDocument>();
        for (DocumentDomainService.StudentDocument doc : docStorage.values()) {
            if (!doc.tenantId.equals(actor.getTenantId())) {
                continue;
            }
            if (studentId != null && !doc.ownerId.equals(studentId)) {
                continue;
            }
            if (status != null && !status.trim().isEmpty() && !doc.status.equalsIgnoreCase(status.trim())) {
                continue;
            }
            result.add(doc);
        }
        Collections.sort(result, new Comparator<DocumentDomainService.StudentDocument>() {
            @Override
            public int compare(DocumentDomainService.StudentDocument o1, DocumentDomainService.StudentDocument o2) {
                return o2.createdAt.compareTo(o1.createdAt);
            }
        });
        return result;
    }

    @Override
    public List<DocumentDomainService.DocumentVersion> listDocumentVersions(Actor actor, UUID documentId) {
        List<DocumentDomainService.DocumentVersion> versions = verStorage.get(documentId);
        if (versions == null) {
            return Collections.emptyList();
        }
        List<DocumentDomainService.DocumentVersion> result = new ArrayList<DocumentDomainService.DocumentVersion>();
        for (DocumentDomainService.DocumentVersion v : versions) {
            if (v.tenantId.equals(actor.getTenantId())) {
                result.add(v);
            }
        }
        return result;
    }

    @Override
    public List<DocumentDomainService.VerificationQueueItem> getVerificationQueue(Actor actor, String statusFilter) {
        List<DocumentDomainService.VerificationQueueItem> result = new ArrayList<DocumentDomainService.VerificationQueueItem>();
        for (DocumentDomainService.StudentDocument doc : docStorage.values()) {
            if (!doc.tenantId.equals(actor.getTenantId())) {
                continue;
            }
            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                if (!doc.status.equalsIgnoreCase(statusFilter.trim())) {
                    continue;
                }
            }
            DocumentDomainService.DocumentVerificationRecord latestVerif = findLatestVerificationByDocumentId(actor, doc.id);
            DocumentDomainService.VerificationQueueItem item = new DocumentDomainService.VerificationQueueItem(
                    doc.id,
                    doc.tenantId,
                    doc.collegeId,
                    doc.ownerId,
                    "Student " + doc.ownerId.toString().substring(0, 8),
                    doc.documentTypeId,
                    doc.documentTypeCode,
                    doc.documentTypeName,
                    doc.title,
                    doc.classification,
                    doc.status,
                    doc.mimeType,
                    doc.sizeBytes,
                    doc.checksumSha256,
                    doc.storagePath,
                    doc.currentVersionNo,
                    doc.createdAt,
                    latestVerif != null ? latestVerif.result : null,
                    latestVerif != null ? latestVerif.verificationCode : null,
                    latestVerif != null ? latestVerif.verifiedAt : null
            );
            result.add(item);
        }
        Collections.sort(result, new Comparator<DocumentDomainService.VerificationQueueItem>() {
            @Override
            public int compare(DocumentDomainService.VerificationQueueItem o1, DocumentDomainService.VerificationQueueItem o2) {
                return o2.submittedAt.compareTo(o1.submittedAt);
            }
        });
        return result;
    }

    @Override
    public void recordVerificationDecision(Actor actor, UUID documentId, String newStatus, DocumentDomainService.DocumentVerificationRecord verification) {
        DocumentDomainService.StudentDocument existing = docStorage.get(documentId);
        if (existing != null && existing.tenantId.equals(actor.getTenantId())) {
            DocumentDomainService.StudentDocument updated = new DocumentDomainService.StudentDocument(
                    existing.id,
                    existing.tenantId,
                    existing.collegeId,
                    existing.documentTypeId,
                    existing.documentTypeCode,
                    existing.documentTypeName,
                    existing.title,
                    existing.classification,
                    newStatus,
                    existing.ownerId,
                    existing.storageBucket,
                    existing.storagePath,
                    existing.mimeType,
                    existing.sizeBytes,
                    existing.checksumSha256,
                    existing.currentVersionNo,
                    existing.createdAt,
                    Instant.now(),
                    existing.createdBy,
                    actor.getUserId(),
                    existing.rowVersion + 1
            );
            docStorage.put(documentId, updated);
        }

        List<DocumentDomainService.DocumentVerificationRecord> list = verifStorage.get(documentId);
        if (list == null) {
            list = new ArrayList<DocumentDomainService.DocumentVerificationRecord>();
            verifStorage.put(documentId, list);
        }
        list.add(verification);
    }

    @Override
    public DocumentDomainService.DocumentVerificationRecord findLatestVerificationByDocumentId(Actor actor, UUID documentId) {
        List<DocumentDomainService.DocumentVerificationRecord> list = verifStorage.get(documentId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        DocumentDomainService.DocumentVerificationRecord latest = null;
        for (DocumentDomainService.DocumentVerificationRecord r : list) {
            if (r.tenantId.equals(actor.getTenantId())) {
                if (latest == null || r.verifiedAt.isAfter(latest.verifiedAt)) {
                    latest = r;
                }
            }
        }
        return latest;
    }

    @Override
    public void resubmitStudentDocument(Actor actor, UUID documentId, DocumentDomainService.DocumentVersion newVersion, String newMimeType) {
        DocumentDomainService.StudentDocument existing = docStorage.get(documentId);
        if (existing != null && existing.tenantId.equals(actor.getTenantId())) {
            DocumentDomainService.StudentDocument updated = new DocumentDomainService.StudentDocument(
                    existing.id,
                    existing.tenantId,
                    existing.collegeId,
                    existing.documentTypeId,
                    existing.documentTypeCode,
                    existing.documentTypeName,
                    existing.title,
                    existing.classification,
                    "SUBMITTED",
                    existing.ownerId,
                    existing.storageBucket,
                    newVersion.storagePath,
                    newMimeType,
                    newVersion.sizeBytes,
                    newVersion.checksumSha256,
                    newVersion.versionNo,
                    existing.createdAt,
                    Instant.now(),
                    existing.createdBy,
                    actor.getUserId(),
                    existing.rowVersion + 1
            );
            docStorage.put(documentId, updated);
        }

        List<DocumentDomainService.DocumentVersion> list = verStorage.get(documentId);
        if (list == null) {
            list = new ArrayList<DocumentDomainService.DocumentVersion>();
            verStorage.put(documentId, list);
        }
        list.add(newVersion);
    }

    @Override
    public DocumentDomainService.DocumentAccessLog recordAccessLog(Actor actor, UUID documentId, String channel, String status, String clientIp) {
        DocumentDomainService.DocumentAccessLog log = new DocumentDomainService.DocumentAccessLog(
                UUID.randomUUID(),
                actor.getTenantId(),
                documentId,
                channel,
                status,
                clientIp,
                Instant.now(),
                Instant.now(),
                actor.getUserId()
        );
        List<DocumentDomainService.DocumentAccessLog> list = accessStorage.get(documentId);
        if (list == null) {
            list = new ArrayList<DocumentDomainService.DocumentAccessLog>();
            accessStorage.put(documentId, list);
        }
        list.add(log);
        return log;
    }

    @Override
    public void saveDocumentLink(Actor actor, DocumentDomainService.DocumentLink link) {
        List<DocumentDomainService.DocumentLink> links = linkStorage.get(link.documentId);
        if (links == null) {
            links = new ArrayList<DocumentDomainService.DocumentLink>();
            linkStorage.put(link.documentId, links);
        }
        links.add(link);
    }

    @Override
    public List<DocumentDomainService.DocumentLink> listDocumentLinks(Actor actor, UUID documentId) {
        List<DocumentDomainService.DocumentLink> links = linkStorage.get(documentId);
        if (links == null) {
            return Collections.emptyList();
        }
        List<DocumentDomainService.DocumentLink> result = new ArrayList<DocumentDomainService.DocumentLink>();
        for (DocumentDomainService.DocumentLink link : links) {
            if (link.tenantId.equals(actor.getTenantId())) {
                result.add(link);
            }
        }
        Collections.sort(result, new Comparator<DocumentDomainService.DocumentLink>() {
            @Override
            public int compare(DocumentDomainService.DocumentLink o1, DocumentDomainService.DocumentLink o2) {
                return o2.createdAt.compareTo(o1.createdAt);
            }
        });
        return result;
    }

    @Override
    public List<DocumentDomainService.DocumentAccessLog> listAccessLogs(Actor actor, UUID documentId) {
        List<DocumentDomainService.DocumentAccessLog> list = accessStorage.get(documentId);
        if (list == null) {
            return Collections.emptyList();
        }
        List<DocumentDomainService.DocumentAccessLog> result = new ArrayList<DocumentDomainService.DocumentAccessLog>();
        for (DocumentDomainService.DocumentAccessLog item : list) {
            if (item.tenantId.equals(actor.getTenantId())) {
                result.add(item);
            }
        }
        Collections.sort(result, new Comparator<DocumentDomainService.DocumentAccessLog>() {
            @Override
            public int compare(DocumentDomainService.DocumentAccessLog o1, DocumentDomainService.DocumentAccessLog o2) {
                return o2.deliveredAt.compareTo(o1.deliveredAt);
            }
        });
        return result;
    }
}

