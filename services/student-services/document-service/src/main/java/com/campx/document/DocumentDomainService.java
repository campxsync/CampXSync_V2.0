package com.campx.document;

import com.campx.document.intelligence.DocumentIntelligenceService;
import com.campx.document.persistence.DocumentPersistence;
import com.campx.document.security.ControlledMalwareScanAdapter;
import com.campx.document.security.SignedDownloadTokenService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service for Student Document Management (STM-06).
 * Enforces business rules, RBAC, tenant isolation, audit logging, and intelligence publication.
 */
public class DocumentDomainService {

    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String DOCUMENT_TYPE_CONFIGURED = "student.document_type.configured.v1";
    public static final String DOCUMENT_REQUIREMENT_DEFINED = "student.document_requirement.defined.v1";
    public static final String DOCUMENT_UPLOADED = "student.document.uploaded.v1";
    public static final String DOCUMENT_VERIFIED = "student.document.verified.v1";
    public static final String DOCUMENT_ACCESSED = "student.document.accessed.v1";

    public static final Set<String> ALLOWED_MIME_TYPES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("application/pdf", "image/jpeg", "image/png", "image/webp")));
    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L; // 10MB
    public static final long SIGNED_URL_TTL_SECONDS = 300L;

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DocumentDomainService.class);
    private static final Set<String> ALLOWED_CLASSIFICATIONS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED")));
    public static final Set<String> ALLOWED_STAGES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("ADMISSION", "ENROLLMENT", "ANNUAL_PROGRESSION", "EXAMINATION", "GRADUATION")));
    private static final Set<String> BLOCKED_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("exe", "bat", "cmd", "com", "dll", "scr", "js", "msi", "sh", "ps1", "vbs")));

    private final DocumentPersistence persistence;
    private final DocumentIntelligenceService intelligenceService;
    private final ControlledMalwareScanAdapter malwareScanAdapter;
    private final SignedDownloadTokenService signedDownloadTokenService;

    public DocumentDomainService(DocumentPersistence persistence, DocumentIntelligenceService intelligenceService) {
        this(persistence, intelligenceService, new ControlledMalwareScanAdapter(),
                new SignedDownloadTokenService(null));
    }

    public DocumentDomainService(DocumentPersistence persistence,
                                 DocumentIntelligenceService intelligenceService,
                                 ControlledMalwareScanAdapter malwareScanAdapter,
                                 SignedDownloadTokenService signedDownloadTokenService) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.intelligenceService = intelligenceService == null ? new DocumentIntelligenceService() : intelligenceService;
        this.malwareScanAdapter = malwareScanAdapter == null ? new ControlledMalwareScanAdapter() : malwareScanAdapter;
        this.signedDownloadTokenService = signedDownloadTokenService == null
                ? new SignedDownloadTokenService(null) : signedDownloadTokenService;
    }

    /**
     * Entity representing a document type configuration in doc.document_types.
     */
    public static class DocumentType {
        public final UUID id;
        public final UUID tenantId;
        public final String code;
        public final String name;
        public final String category;
        public final String module;
        public final String defaultClassification;
        public final boolean requiresSignature;
        public final String retentionPolicyCode;
        public final boolean isActive;
        public final Instant createdAt;
        public final Instant updatedAt;
        public final UUID createdBy;
        public final UUID updatedBy;
        public final int rowVersion;

        public DocumentType(UUID id, UUID tenantId, String code, String name, String category,
                            String module, String defaultClassification, boolean requiresSignature,
                            String retentionPolicyCode, boolean isActive, Instant createdAt,
                            Instant updatedAt, UUID createdBy, UUID updatedBy, int rowVersion) {
            this.id = id;
            this.tenantId = tenantId;
            this.code = code;
            this.name = name;
            this.category = category;
            this.module = module;
            this.defaultClassification = defaultClassification;
            this.requiresSignature = requiresSignature;
            this.retentionPolicyCode = retentionPolicyCode;
            this.isActive = isActive;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
            this.rowVersion = rowVersion;
        }
    }

    /**
     * Request payload to configure a new document type (STM-06-US-001).
     */
    public static class ConfigureDocumentTypeRequest {
        public String code;
        public String name;
        public String category;
        public String module;
        public String defaultClassification;
        public Boolean requiresSignature;
        public String retentionPolicyCode;
        public Boolean isActive;
    }

    /**
     * Request payload to update an existing document type.
     */
    public static class UpdateDocumentTypeRequest {
        public String name;
        public String category;
        public String module;
        public String defaultClassification;
        public Boolean requiresSignature;
        public String retentionPolicyCode;
        public Boolean isActive;
    }

    /**
     * Entity representing a stage/programme document requirement in doc.stage_document_requirements (STM-06-US-002).
     */
    public static class StageDocumentRequirement {
        public final UUID id;
        public final UUID tenantId;
        public final UUID programId;
        public final String programCode;
        public final String programName;
        public final String academicStage;
        public final UUID documentTypeId;
        public final String documentTypeCode;
        public final String documentTypeName;
        public final boolean isMandatory;
        public final int minCount;
        public final Integer maxCount;
        public final Integer submissionDeadlineDays;
        public final String description;
        public final boolean isActive;
        public final Instant createdAt;
        public final Instant updatedAt;
        public final UUID createdBy;
        public final UUID updatedBy;
        public final int rowVersion;

        public StageDocumentRequirement(UUID id, UUID tenantId, UUID programId, String programCode,
                                        String programName, String academicStage, UUID documentTypeId,
                                        String documentTypeCode, String documentTypeName, boolean isMandatory,
                                        int minCount, Integer maxCount, Integer submissionDeadlineDays,
                                        String description, boolean isActive, Instant createdAt,
                                        Instant updatedAt, UUID createdBy, UUID updatedBy, int rowVersion) {
            this.id = id;
            this.tenantId = tenantId;
            this.programId = programId;
            this.programCode = programCode;
            this.programName = programName;
            this.academicStage = academicStage;
            this.documentTypeId = documentTypeId;
            this.documentTypeCode = documentTypeCode;
            this.documentTypeName = documentTypeName;
            this.isMandatory = isMandatory;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.submissionDeadlineDays = submissionDeadlineDays;
            this.description = description;
            this.isActive = isActive;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
            this.rowVersion = rowVersion;
        }
    }

    /**
     * Request payload to define a stage document requirement (STM-06-US-002).
     */
    public static class DefineStageRequirementRequest {
        public UUID programId;
        public String academicStage;
        public UUID documentTypeId;
        public Boolean isMandatory;
        public Integer minCount;
        public Integer maxCount;
        public Integer submissionDeadlineDays;
        public String description;
        public Boolean isActive;
    }

    /**
     * Request payload to update an existing stage document requirement.
     */
    public static class UpdateStageRequirementRequest {
        public Boolean isMandatory;
        public Integer minCount;
        public Integer maxCount;
        public Integer submissionDeadlineDays;
        public String description;
        public Boolean isActive;
    }

    /**
     * Entity representing a student document instance in doc.documents (STM-06-US-003).
     */
    public static class StudentDocument {
        public final UUID id;
        public final UUID tenantId;
        public final UUID collegeId;
        public final UUID documentTypeId;
        public final String documentTypeCode;
        public final String documentTypeName;
        public final String title;
        public final String classification;
        public final String status;
        public final UUID ownerId;
        public final String storageBucket;
        public final String storagePath;
        public final String mimeType;
        public final long sizeBytes;
        public final String checksumSha256;
        public final int currentVersionNo;
        public final Instant createdAt;
        public final Instant updatedAt;
        public final UUID createdBy;
        public final UUID updatedBy;
        public final int rowVersion;
        public final String latestVerificationResult;
        public final String latestVerificationCode;
        public final Instant latestVerifiedAt;
        public final Instant expiresAt;
        public final String retentionPolicyCode;

        public StudentDocument(UUID id, UUID tenantId, UUID collegeId, UUID documentTypeId,
                               String documentTypeCode, String documentTypeName, String title,
                               String classification, String status, UUID ownerId,
                               String storageBucket, String storagePath, String mimeType,
                               long sizeBytes, String checksumSha256, int currentVersionNo,
                               Instant createdAt, Instant updatedAt, UUID createdBy,
                               UUID updatedBy, int rowVersion) {
            this(id, tenantId, collegeId, documentTypeId, documentTypeCode, documentTypeName,
                 title, classification, status, ownerId, storageBucket, storagePath, mimeType,
                 sizeBytes, checksumSha256, currentVersionNo, createdAt, updatedAt, createdBy,
                 updatedBy, rowVersion, null, null, null, null, null);
        }

        public StudentDocument(UUID id, UUID tenantId, UUID collegeId, UUID documentTypeId,
                               String documentTypeCode, String documentTypeName, String title,
                               String classification, String status, UUID ownerId,
                               String storageBucket, String storagePath, String mimeType,
                               long sizeBytes, String checksumSha256, int currentVersionNo,
                               Instant createdAt, Instant updatedAt, UUID createdBy,
                               UUID updatedBy, int rowVersion,
                               String latestVerificationResult, String latestVerificationCode,
                               Instant latestVerifiedAt) {
            this(id, tenantId, collegeId, documentTypeId, documentTypeCode, documentTypeName,
                 title, classification, status, ownerId, storageBucket, storagePath, mimeType,
                 sizeBytes, checksumSha256, currentVersionNo, createdAt, updatedAt, createdBy,
                 updatedBy, rowVersion, latestVerificationResult, latestVerificationCode,
                 latestVerifiedAt, null, null);
        }

        public StudentDocument(UUID id, UUID tenantId, UUID collegeId, UUID documentTypeId,
                               String documentTypeCode, String documentTypeName, String title,
                               String classification, String status, UUID ownerId,
                               String storageBucket, String storagePath, String mimeType,
                               long sizeBytes, String checksumSha256, int currentVersionNo,
                               Instant createdAt, Instant updatedAt, UUID createdBy,
                               UUID updatedBy, int rowVersion,
                               String latestVerificationResult, String latestVerificationCode,
                               Instant latestVerifiedAt, Instant expiresAt, String retentionPolicyCode) {
            this.id = id;
            this.tenantId = tenantId;
            this.collegeId = collegeId;
            this.documentTypeId = documentTypeId;
            this.documentTypeCode = documentTypeCode;
            this.documentTypeName = documentTypeName;
            this.title = title;
            this.classification = classification;
            this.status = status;
            this.ownerId = ownerId;
            this.storageBucket = storageBucket;
            this.storagePath = storagePath;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.checksumSha256 = checksumSha256;
            this.currentVersionNo = currentVersionNo;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
            this.rowVersion = rowVersion;
            this.latestVerificationResult = latestVerificationResult;
            this.latestVerificationCode = latestVerificationCode;
            this.latestVerifiedAt = latestVerifiedAt;
            this.expiresAt = expiresAt;
            this.retentionPolicyCode = retentionPolicyCode;
        }
    }

    /**
     * Entity representing regulatory access log in doc.document_deliveries (STM-06-US-006).
     */
    public static class DocumentAccessLog {
        public final UUID id;
        public final UUID tenantId;
        public final UUID documentId;
        public final String channel;
        public final String status;
        public final String clientIp;
        public final Instant deliveredAt;
        public final Instant createdAt;
        public final UUID createdBy;

        public DocumentAccessLog(UUID id, UUID tenantId, UUID documentId, String channel,
                                 String status, String clientIp, Instant deliveredAt,
                                 Instant createdAt, UUID createdBy) {
            this.id = id;
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.channel = channel;
            this.status = status;
            this.clientIp = clientIp;
            this.deliveredAt = deliveredAt;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }
    }

    /**
     * Retention calculation details for a document (STM-06-US-006).
     */
    public static class RetentionInfo {
        public final String retentionPolicyCode;
        public final Instant retentionCutoff;
        public final boolean isExpired;
        public final boolean isExpiringSoon;
        public final Instant expiresAt;

        public RetentionInfo(String retentionPolicyCode, Instant retentionCutoff, boolean isExpired, boolean isExpiringSoon, Instant expiresAt) {
            this.retentionPolicyCode = retentionPolicyCode;
            this.retentionCutoff = retentionCutoff;
            this.isExpired = isExpired;
            this.isExpiringSoon = isExpiringSoon;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Entity representing a document version in doc.document_versions (STM-06-US-003).
     */
    public static class DocumentVersion {
        public final UUID id;
        public final UUID tenantId;
        public final UUID documentId;
        public final int versionNo;
        public final String storagePath;
        public final long sizeBytes;
        public final String checksumSha256;
        public final String changeNote;
        public final Instant createdAt;
        public final UUID createdBy;

        public DocumentVersion(UUID id, UUID tenantId, UUID documentId, int versionNo,
                               String storagePath, long sizeBytes, String checksumSha256,
                               String changeNote, Instant createdAt, UUID createdBy) {
            this.id = id;
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.versionNo = versionNo;
            this.storagePath = storagePath;
            this.sizeBytes = sizeBytes;
            this.checksumSha256 = checksumSha256;
            this.changeNote = changeNote;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }
    }

    /**
     * Entity representing a document link in doc.document_links (STM-06-US-003).
     */
    public static class DocumentLink {
        public final UUID id;
        public final UUID tenantId;
        public final UUID documentId;
        public final String entityType;
        public final UUID entityId;
        public final String relation;
        public final Instant createdAt;
        public final UUID createdBy;

        public DocumentLink(UUID id, UUID tenantId, UUID documentId, String entityType,
                            UUID entityId, String relation, Instant createdAt, UUID createdBy) {
            this.id = id;
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.relation = relation;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }
    }

    /**
     * Request payload to upload a student document instance (STM-06-US-003).
     */
    public static class UploadStudentDocumentRequest {
        public UUID studentId;
        public UUID collegeId;
        public UUID documentTypeId;
        public UUID stageRequirementId;
        public String title;
        public String mimeType;
        public Long sizeBytes;
        public String fileName;
        public String changeNote;
        public String checksumSha256;
        public Instant expiresAt;
    }

    /**
     * Checklist item associating a stage requirement with student submissions.
     */
    public static class StudentChecklistItem {
        public final StageDocumentRequirement requirement;
        public final List<StudentDocument> submittedDocuments;
        public final String status; // PENDING, SUBMITTED

        public StudentChecklistItem(StageDocumentRequirement requirement,
                                    List<StudentDocument> submittedDocuments,
                                    String status) {
            this.requirement = requirement;
            this.submittedDocuments = submittedDocuments != null ? submittedDocuments : Collections.<StudentDocument>emptyList();
            this.status = status;
        }
    }

    /**
     * Entity representing a document verification record in doc.verifications (STM-06-US-004).
     */
    public static class DocumentVerificationRecord {
        public final UUID id;
        public final UUID tenantId;
        public final UUID documentId;
        public final String entityType;
        public final UUID entityId;
        public final String verificationCode;
        public final String result;
        public final String notes;
        public final Instant verifiedAt;
        public final Instant createdAt;
        public final UUID createdBy;

        public DocumentVerificationRecord(UUID id, UUID tenantId, UUID documentId, String entityType,
                                          UUID entityId, String verificationCode, String result,
                                          String notes, Instant verifiedAt, Instant createdAt, UUID createdBy) {
            this.id = id;
            this.tenantId = tenantId;
            this.documentId = documentId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.verificationCode = verificationCode;
            this.result = result;
            this.notes = notes;
            this.verifiedAt = verifiedAt;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
        }
    }

    /**
     * Request payload to record a verification decision for a document (STM-06-US-004).
     */
    public static class VerificationDecisionRequest {
        public String decision; // APPROVE or REJECT
        public String verificationCode;
        public String rejectionReason;
        public String verificationNotes;
    }

    /**
     * Queue item for institutional officers reviewing student submissions (STM-06-US-004).
     */
    public static class VerificationQueueItem {
        public final UUID documentId;
        public final UUID tenantId;
        public final UUID collegeId;
        public final UUID studentId;
        public final String studentName;
        public final UUID documentTypeId;
        public final String documentTypeCode;
        public final String documentTypeName;
        public final String title;
        public final String classification;
        public final String status;
        public final String mimeType;
        public final long sizeBytes;
        public final String checksumSha256;
        public final String storagePath;
        public final int currentVersionNo;
        public final Instant submittedAt;
        public final String latestVerificationResult;
        public final String latestVerificationCode;
        public final Instant latestVerifiedAt;

        public VerificationQueueItem(UUID documentId, UUID tenantId, UUID collegeId, UUID studentId,
                                     String studentName, UUID documentTypeId, String documentTypeCode,
                                     String documentTypeName, String title, String classification,
                                     String status, String mimeType, long sizeBytes, String checksumSha256,
                                     String storagePath, int currentVersionNo, Instant submittedAt,
                                     String latestVerificationResult, String latestVerificationCode,
                                     Instant latestVerifiedAt) {
            this.documentId = documentId;
            this.tenantId = tenantId;
            this.collegeId = collegeId;
            this.studentId = studentId;
            this.studentName = studentName;
            this.documentTypeId = documentTypeId;
            this.documentTypeCode = documentTypeCode;
            this.documentTypeName = documentTypeName;
            this.title = title;
            this.classification = classification;
            this.status = status;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.checksumSha256 = checksumSha256;
            this.storagePath = storagePath;
            this.currentVersionNo = currentVersionNo;
            this.submittedAt = submittedAt;
            this.latestVerificationResult = latestVerificationResult;
            this.latestVerificationCode = latestVerificationCode;
            this.latestVerifiedAt = latestVerifiedAt;
        }
    }

    /**
     * Request payload to resubmit a rejected student document with an updated version (STM-06-US-005).
     */
    public static class ResubmitDocumentRequest {
        public String storagePath;
        public Long sizeBytes;
        public String mimeType;
        public String fileName;
        public String checksumSha256;
        public String changeNote;
    }

    /**
     * STM-06-US-001: Configure document types.
     * Accessible by College Admin / Registrar.
     */
    public DocumentType configureDocumentType(Actor actor, ConfigureDocumentTypeRequest req) {
        requireCaller(actor);
        requireAdmin(actor);

        if (req == null) {
            throw new DocumentException(422, "PAYLOAD_REQUIRED", "Document type configuration payload is required");
        }

        String code = req.code == null ? "" : req.code.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new DocumentException(422, "DOCUMENT_TYPE_CODE_REQUIRED", "Document type code is required");
        }
        if (code.length() > 50) {
            throw new DocumentException(422, "DOCUMENT_TYPE_CODE_TOO_LONG", "Document type code must not exceed 50 characters");
        }
        if (!code.matches("^[A-Z0-9_-]+$")) {
            throw new DocumentException(422, "INVALID_DOCUMENT_TYPE_CODE", "Code must contain only uppercase letters, numbers, hyphens, and underscores");
        }

        String name = req.name == null ? "" : req.name.trim();
        if (name.isEmpty() || name.length() < 2 || name.length() > 150) {
            throw new DocumentException(422, "DOCUMENT_TYPE_NAME_INVALID", "Document type name must be between 2 and 150 characters");
        }

        String classification = req.defaultClassification == null ? "INTERNAL" : req.defaultClassification.trim().toUpperCase();
        if (!ALLOWED_CLASSIFICATIONS.contains(classification)) {
            throw new DocumentException(422, "INVALID_CLASSIFICATION",
                    "Classification must be one of: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED");
        }

        String category = req.category == null ? "GENERAL" : req.category.trim().toUpperCase();
        String module = req.module == null || req.module.trim().isEmpty() ? "STUDENT" : req.module.trim().toUpperCase();
        boolean requiresSignature = req.requiresSignature != null && req.requiresSignature;
        String retentionPolicyCode = req.retentionPolicyCode == null || req.retentionPolicyCode.trim().isEmpty()
                ? "RET_STANDARD" : req.retentionPolicyCode.trim().toUpperCase();
        boolean isActive = req.isActive == null || req.isActive;

        FlowTracker tracker = logger.flow("configure-document-type", actor.getCorrelationId());
        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        LogContext.setUserRole(actor.getRole());

        try {
            logger.info("Attempting to configure document type: tenantId={} code='{}' name='{}'",
                    actor.getTenantId(), code, name);

            // Check duplicate code in same tenant
            DocumentType existing = persistence.findDocumentTypeByCode(actor, code);
            if (existing != null) {
                logger.warn("Duplicate document type code rejected: tenantId={} code='{}'", actor.getTenantId(), code);
                throw new DocumentException(409, "DUPLICATE_DOCUMENT_TYPE_CODE",
                        "A document type with code '" + code + "' already exists in this institution");
            }

            Instant now = Instant.now();
            UUID docTypeId = UUID.randomUUID();
            DocumentType docType = new DocumentType(
                    docTypeId,
                    actor.getTenantId(),
                    code,
                    name,
                    category,
                    module,
                    classification,
                    requiresSignature,
                    retentionPolicyCode,
                    isActive,
                    now,
                    now,
                    actor.getUserId(),
                    null,
                    1
            );

            persistence.saveDocumentType(actor, docType);
            logger.info("Successfully configured document type: id={} code='{}'", docTypeId, code);

            AuditEvent audit = AuditEvent.builder()
                    .action("document_type.configured")
                    .tenantId(actor.getTenantId().toString())
                    .principalId(actor.getUserId() != null ? actor.getUserId().toString() : null)
                    .principalRole(actor.getRole())
                    .resourceType("DOCUMENT_TYPE")
                    .resourceId(docTypeId.toString())
                    .correlationId(actor.getCorrelationId())
                    .status("SUCCESS")
                    .severity("INFO")
                    .description("Configured document type code=" + code + " name=" + name)
                    .build();
            logger.audit(audit);

            intelligenceService.publishDocumentTypeConfigured(actor, docType);
            return docType;
        } finally {
            if (tracker != null) {
                tracker.close();
            }
            LogContext.clear();
        }
    }

    /**
     * Lists configured document types for the institution.
     */
    public List<DocumentType> listDocumentTypes(Actor actor, String category, Boolean activeOnly) {
        requireCaller(actor);
        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        try {
            return persistence.listDocumentTypes(actor, category, activeOnly);
        } finally {
            LogContext.clear();
        }
    }

    /**
     * Retrieves a single document type by ID.
     */
    public DocumentType getDocumentType(Actor actor, UUID id) {
        requireCaller(actor);
        if (id == null) {
            throw new DocumentException(422, "DOCUMENT_TYPE_ID_REQUIRED", "Document type ID is required");
        }
        DocumentType dt = persistence.findDocumentTypeById(actor, id);
        if (dt == null) {
            throw new DocumentException(404, "DOCUMENT_TYPE_NOT_FOUND", "Document type not found");
        }
        return dt;
    }

    /**
     * Updates an existing document type.
     */
    public DocumentType updateDocumentType(Actor actor, UUID id, UpdateDocumentTypeRequest req) {
        requireCaller(actor);
        requireAdmin(actor);

        if (id == null) {
            throw new DocumentException(422, "DOCUMENT_TYPE_ID_REQUIRED", "Document type ID is required");
        }
        if (req == null) {
            throw new DocumentException(422, "PAYLOAD_REQUIRED", "Update payload is required");
        }

        DocumentType existing = persistence.findDocumentTypeById(actor, id);
        if (existing == null) {
            throw new DocumentException(404, "DOCUMENT_TYPE_NOT_FOUND", "Document type not found");
        }

        String name = req.name == null || req.name.trim().isEmpty() ? existing.name : req.name.trim();
        String category = req.category == null ? existing.category : req.category.trim().toUpperCase();
        String module = req.module == null ? existing.module : req.module.trim().toUpperCase();
        String classification = req.defaultClassification == null ? existing.defaultClassification : req.defaultClassification.trim().toUpperCase();
        if (!ALLOWED_CLASSIFICATIONS.contains(classification)) {
            throw new DocumentException(422, "INVALID_CLASSIFICATION",
                    "Classification must be one of: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED");
        }

        boolean requiresSig = req.requiresSignature != null ? req.requiresSignature : existing.requiresSignature;
        String retention = req.retentionPolicyCode == null ? existing.retentionPolicyCode : req.retentionPolicyCode.trim().toUpperCase();
        boolean active = req.isActive != null ? req.isActive : existing.isActive;

        DocumentType updated = new DocumentType(
                existing.id,
                existing.tenantId,
                existing.code,
                name,
                category,
                module,
                classification,
                requiresSig,
                retention,
                active,
                existing.createdAt,
                Instant.now(),
                existing.createdBy,
                actor.getUserId(),
                existing.rowVersion + 1
        );

        persistence.updateDocumentType(actor, updated);

        AuditEvent audit = AuditEvent.builder()
                .action("document_type.updated")
                .tenantId(actor.getTenantId().toString())
                .principalId(actor.getUserId() != null ? actor.getUserId().toString() : null)
                .principalRole(actor.getRole())
                .resourceType("DOCUMENT_TYPE")
                .resourceId(id.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Updated document type code=" + existing.code)
                .build();
        logger.audit(audit);

        intelligenceService.publishDocumentTypeConfigured(actor, updated);
        return updated;
    }

    /**
     * STM-06-US-002: Define mandatory and optional document requirements per academic stage and programme.
     * Accessible by College Admin / Registrar.
     */
    public StageDocumentRequirement defineStageRequirement(Actor actor, DefineStageRequirementRequest req) {
        requireCaller(actor);
        requireAdmin(actor);

        if (req == null) {
            throw new DocumentException(422, "PAYLOAD_REQUIRED", "Stage document requirement payload is required");
        }

        if (req.academicStage == null || req.academicStage.trim().isEmpty()) {
            throw new DocumentException(422, "ACADEMIC_STAGE_REQUIRED", "Academic stage is required");
        }
        String stage = req.academicStage.trim().toUpperCase();
        if (!ALLOWED_STAGES.contains(stage)) {
            throw new DocumentException(422, "INVALID_ACADEMIC_STAGE",
                    "Academic stage must be one of: ADMISSION, ENROLLMENT, ANNUAL_PROGRESSION, EXAMINATION, GRADUATION");
        }

        if (req.documentTypeId == null) {
            throw new DocumentException(422, "DOCUMENT_TYPE_ID_REQUIRED", "Document type ID is required");
        }

        DocumentType docType = persistence.findDocumentTypeById(actor, req.documentTypeId);
        if (docType == null) {
            throw new DocumentException(404, "DOCUMENT_TYPE_NOT_FOUND", "Referenced document type does not exist in this institution");
        }

        int minCount = req.minCount == null ? 1 : req.minCount;
        if (minCount < 0) {
            throw new DocumentException(422, "INVALID_MIN_COUNT", "Minimum count cannot be negative");
        }
        Integer maxCount = req.maxCount == null ? Integer.valueOf(minCount > 0 ? minCount : 1) : req.maxCount;
        if (maxCount != null && maxCount < minCount) {
            throw new DocumentException(422, "INVALID_MAX_COUNT", "Maximum count cannot be less than minimum count");
        }

        FlowTracker tracker = logger.flow("define-stage-requirement", actor.getCorrelationId());
        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        LogContext.setUserRole(actor.getRole());

        try {
            logger.info("Attempting to define stage requirement: tenantId={} programId={} stage={} docTypeId={}",
                    actor.getTenantId(), req.programId, stage, req.documentTypeId);

            StageDocumentRequirement existing = persistence.findStageRequirement(actor, req.programId, stage, req.documentTypeId);
            if (existing != null) {
                logger.warn("Duplicate stage requirement rejected: tenantId={} programId={} stage={} docTypeId={}",
                        actor.getTenantId(), req.programId, stage, req.documentTypeId);
                throw new DocumentException(409, "DUPLICATE_STAGE_REQUIREMENT",
                        "A requirement for document type '" + docType.code + "' already exists for stage " + stage
                                + (req.programId != null ? " and program " + req.programId : " across the institution"));
            }

            Instant now = Instant.now();
            UUID reqId = UUID.randomUUID();
            boolean isMandatory = req.isMandatory == null || req.isMandatory;
            boolean isActive = req.isActive == null || req.isActive;

            StageDocumentRequirement entity = new StageDocumentRequirement(
                    reqId,
                    actor.getTenantId(),
                    req.programId,
                    null,
                    null,
                    stage,
                    req.documentTypeId,
                    docType.code,
                    docType.name,
                    isMandatory,
                    minCount,
                    maxCount,
                    req.submissionDeadlineDays,
                    req.description,
                    isActive,
                    now,
                    now,
                    actor.getUserId(),
                    null,
                    1
            );

            persistence.saveStageRequirement(actor, entity);
            logger.info("Successfully defined stage document requirement: id={} stage={} docType='{}' mandatory={}",
                    reqId, stage, docType.code, isMandatory);

            AuditEvent audit = AuditEvent.builder()
                    .action("document_requirement.defined")
                    .tenantId(actor.getTenantId().toString())
                    .principalId(actor.getUserId() != null ? actor.getUserId().toString() : null)
                    .principalRole(actor.getRole())
                    .resourceType("STAGE_DOCUMENT_REQUIREMENT")
                    .resourceId(reqId.toString())
                    .correlationId(actor.getCorrelationId())
                    .status("SUCCESS")
                    .severity("INFO")
                    .description("Defined document requirement: stage=" + stage + " docType=" + docType.code + " mandatory=" + isMandatory)
                    .build();
            logger.audit(audit);

            intelligenceService.publishStageRequirementDefined(actor, entity);
            return entity;
        } finally {
            if (tracker != null) {
                tracker.close();
            }
            LogContext.clear();
        }
    }

    /**
     * Lists stage document requirements for the institution with optional stage, program, and active filters.
     */
    public List<StageDocumentRequirement> listStageRequirements(Actor actor, UUID programId, String stage, Boolean activeOnly) {
        requireCaller(actor);
        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        try {
            String normalizedStage = stage == null || stage.trim().isEmpty() ? null : stage.trim().toUpperCase();
            return persistence.listStageRequirements(actor, programId, normalizedStage, activeOnly);
        } finally {
            LogContext.clear();
        }
    }

    /**
     * Retrieves a single stage document requirement by ID.
     */
    public StageDocumentRequirement getStageRequirement(Actor actor, UUID id) {
        requireCaller(actor);
        if (id == null) {
            throw new DocumentException(422, "REQUIREMENT_ID_REQUIRED", "Stage requirement ID is required");
        }
        StageDocumentRequirement req = persistence.findStageRequirementById(actor, id);
        if (req == null) {
            throw new DocumentException(404, "REQUIREMENT_NOT_FOUND", "Stage document requirement not found");
        }
        return req;
    }

    /**
     * Updates an existing stage document requirement.
     */
    public StageDocumentRequirement updateStageRequirement(Actor actor, UUID id, UpdateStageRequirementRequest req) {
        requireCaller(actor);
        requireAdmin(actor);

        if (id == null) {
            throw new DocumentException(422, "REQUIREMENT_ID_REQUIRED", "Stage requirement ID is required");
        }
        if (req == null) {
            throw new DocumentException(422, "PAYLOAD_REQUIRED", "Update payload is required");
        }

        StageDocumentRequirement existing = persistence.findStageRequirementById(actor, id);
        if (existing == null) {
            throw new DocumentException(404, "REQUIREMENT_NOT_FOUND", "Stage document requirement not found");
        }

        boolean isMandatory = req.isMandatory != null ? req.isMandatory : existing.isMandatory;
        int minCount = req.minCount != null ? req.minCount : existing.minCount;
        if (minCount < 0) {
            throw new DocumentException(422, "INVALID_MIN_COUNT", "Minimum count cannot be negative");
        }
        Integer maxCount = req.maxCount != null ? req.maxCount : existing.maxCount;
        if (maxCount != null && maxCount < minCount) {
            throw new DocumentException(422, "INVALID_MAX_COUNT", "Maximum count cannot be less than minimum count");
        }
        Integer deadline = req.submissionDeadlineDays != null ? req.submissionDeadlineDays : existing.submissionDeadlineDays;
        String desc = req.description != null ? req.description : existing.description;
        boolean active = req.isActive != null ? req.isActive : existing.isActive;

        StageDocumentRequirement updated = new StageDocumentRequirement(
                existing.id,
                existing.tenantId,
                existing.programId,
                existing.programCode,
                existing.programName,
                existing.academicStage,
                existing.documentTypeId,
                existing.documentTypeCode,
                existing.documentTypeName,
                isMandatory,
                minCount,
                maxCount,
                deadline,
                desc,
                active,
                existing.createdAt,
                Instant.now(),
                existing.createdBy,
                actor.getUserId(),
                existing.rowVersion + 1
        );

        persistence.updateStageRequirement(actor, updated);

        AuditEvent audit = AuditEvent.builder()
                .action("document_requirement.updated")
                .tenantId(actor.getTenantId().toString())
                .principalId(actor.getUserId() != null ? actor.getUserId().toString() : null)
                .principalRole(actor.getRole())
                .resourceType("STAGE_DOCUMENT_REQUIREMENT")
                .resourceId(id.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Updated stage requirement id=" + id)
                .build();
        logger.audit(audit);

        intelligenceService.publishStageRequirementDefined(actor, updated);
        return updated;
    }

    /**
     * Soft-deletes a stage document requirement.
     */
    public void deleteStageRequirement(Actor actor, UUID id) {
        requireCaller(actor);
        requireAdmin(actor);

        if (id == null) {
            throw new DocumentException(422, "REQUIREMENT_ID_REQUIRED", "Stage requirement ID is required");
        }

        StageDocumentRequirement existing = persistence.findStageRequirementById(actor, id);
        if (existing == null) {
            throw new DocumentException(404, "REQUIREMENT_NOT_FOUND", "Stage document requirement not found");
        }

        persistence.deleteStageRequirement(actor, id);

        AuditEvent audit = AuditEvent.builder()
                .action("document_requirement.deleted")
                .tenantId(actor.getTenantId().toString())
                .principalId(actor.getUserId() != null ? actor.getUserId().toString() : null)
                .principalRole(actor.getRole())
                .resourceType("STAGE_DOCUMENT_REQUIREMENT")
                .resourceId(id.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Deleted stage requirement id=" + id)
                .build();
        logger.audit(audit);
    }

    /**
     * STM-06-US-003: Student uploads documents against stage requirements.
     * Accessible by Student for own profile, or College Admin / Registrar on behalf of student.
     */
    public StudentDocument uploadStudentDocument(Actor actor, UploadStudentDocumentRequest req) {
        requireCaller(actor);
        if (req == null) {
            throw new DocumentException(422, "PAYLOAD_REQUIRED", "Document upload payload is required");
        }

        UUID targetStudentId;
        if (actor.isStudent()) {
            targetStudentId = actor.getUserId();
        } else if (actor.isCollegeAdmin() || actor.isRegistrar()) {
            targetStudentId = req.studentId != null ? req.studentId : actor.getUserId();
        } else {
            throw new DocumentException(403, "FORBIDDEN", "Only students or authorized administrators may upload documents");
        }

        if (req.documentTypeId == null) {
            throw new DocumentException(422, "DOCUMENT_TYPE_REQUIRED", "Document type ID is required");
        }
        DocumentType dt = persistence.findDocumentTypeById(actor, req.documentTypeId);
        if (dt == null || !dt.isActive) {
            throw new DocumentException(404, "DOCUMENT_TYPE_NOT_FOUND", "Active document type not found for ID: " + req.documentTypeId);
        }

        if (req.stageRequirementId != null) {
            StageDocumentRequirement sdr = persistence.findStageRequirementById(actor, req.stageRequirementId);
            if (sdr == null || !sdr.isActive) {
                throw new DocumentException(404, "STAGE_REQUIREMENT_NOT_FOUND", "Active stage requirement not found for ID: " + req.stageRequirementId);
            }
            if (!sdr.documentTypeId.equals(req.documentTypeId)) {
                throw new DocumentException(422, "REQUIREMENT_TYPE_MISMATCH", "Selected document type does not match the stage requirement");
            }
        }

        if (req.title == null || req.title.trim().length() < 2 || req.title.trim().length() > 200) {
            throw new DocumentException(422, "INVALID_TITLE", "Document title must be between 2 and 200 characters");
        }

        String mimeType = validateSecureUpload(req.mimeType, req.sizeBytes, req.fileName);
        String fileName = sanitizeFileName(req.fileName, mimeType);
        String checksum = req.checksumSha256 != null && !req.checksumSha256.trim().isEmpty()
                ? req.checksumSha256.trim() : null;

        ControlledMalwareScanAdapter.ScanResult scan =
                malwareScanAdapter.scan(fileName, mimeType, checksum, req.sizeBytes);
        if (!scan.clean) {
            throw new DocumentException(422, "MALWARE_DETECTED",
                    "Upload quarantined by malware scan (liveEndpoint=" + scan.liveEndpoint + ")");
        }

        UUID docId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID linkId = UUID.randomUUID();
        Instant now = Instant.now();

        String storagePath = "tenants/" + actor.getTenantId() + "/students/" + targetStudentId + "/" + docId + "/v1-" + fileName;
        if (checksum == null || checksum.isEmpty()) {
            checksum = "sha256:simulated-" + docId;
        }

        UUID targetCollegeId = req.collegeId != null ? req.collegeId : UUID.fromString("a0000000-0000-0000-0000-000000000002");

        StudentDocument doc = new StudentDocument(
                docId,
                actor.getTenantId(),
                targetCollegeId,
                req.documentTypeId,
                dt.code,
                dt.name,
                req.title.trim(),
                dt.defaultClassification,
                "SUBMITTED",
                targetStudentId,
                "student-documents",
                storagePath,
                mimeType,
                req.sizeBytes,
                checksum,
                1,
                now,
                now,
                actor.getUserId(),
                actor.getUserId(),
                1,
                null,
                null,
                null,
                req.expiresAt,
                dt.retentionPolicyCode != null ? dt.retentionPolicyCode : "RET_PERMANENT"
        );

        DocumentVersion version = new DocumentVersion(
                versionId,
                actor.getTenantId(),
                docId,
                1,
                storagePath,
                req.sizeBytes,
                checksum,
                req.changeNote != null ? req.changeNote.trim() : "Initial stage submission",
                now,
                actor.getUserId()
        );

        DocumentLink link = new DocumentLink(
                linkId,
                actor.getTenantId(),
                docId,
                "STUDENT",
                targetStudentId,
                "STAGE_REQUIREMENT_SUBMISSION",
                now,
                actor.getUserId()
        );

        persistence.saveStudentDocument(actor, doc, version, link);

        DocumentLink scanLink = new DocumentLink(
                UUID.randomUUID(),
                actor.getTenantId(),
                docId,
                "SECURITY_SCAN",
                actor.getUserId(),
                "MALWARE_SCAN_PASSED",
                now,
                actor.getUserId()
        );
        persistence.saveDocumentLink(actor, scanLink);

        AuditEvent audit = AuditEvent.builder()
                .action("document.uploaded")
                .tenantId(actor.getTenantId().toString())
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(docId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Student document uploaded: docId=" + docId + ", docType=" + dt.code
                        + ", owner=" + targetStudentId + ", malwareScan=" + scan.verdict)
                .build();
        logger.audit(audit);

        intelligenceService.publishDocumentUploaded(actor, doc, version);
        return doc;
    }

    public List<StudentDocument> listStudentDocuments(Actor actor, UUID studentId, String status) {
        requireCaller(actor);
        UUID targetStudentId;
        if (actor.isStudent()) {
            if (studentId != null && !studentId.equals(actor.getUserId())) {
                throw new DocumentException(403, "FORBIDDEN", "Students cannot view other students' documents");
            }
            targetStudentId = actor.getUserId();
        } else if (actor.isCollegeAdmin() || actor.isRegistrar()) {
            targetStudentId = studentId != null ? studentId : actor.getUserId();
        } else {
            throw new DocumentException(403, "FORBIDDEN", "Unauthorized access to student documents");
        }

        return persistence.listStudentDocuments(actor, targetStudentId, status);
    }

    public List<StudentChecklistItem> getStudentChecklist(Actor actor, UUID studentId, String stage, UUID programId) {
        requireCaller(actor);
        UUID targetStudentId = actor.isStudent() ? actor.getUserId() : (studentId != null ? studentId : actor.getUserId());
        List<StageDocumentRequirement> requirements = persistence.listStageRequirements(actor, programId, stage, true);
        List<StudentDocument> uploadedDocs = persistence.listStudentDocuments(actor, targetStudentId, null);

        List<StudentChecklistItem> checklist = new java.util.ArrayList<StudentChecklistItem>();
        for (StageDocumentRequirement req : requirements) {
            List<StudentDocument> matching = new java.util.ArrayList<StudentDocument>();
            boolean hasApproved = false;
            boolean hasSubmitted = false;
            boolean hasRejected = false;
            for (StudentDocument doc : uploadedDocs) {
                if (doc.documentTypeId.equals(req.documentTypeId)) {
                    matching.add(doc);
                    if ("APPROVED".equalsIgnoreCase(doc.status)) {
                        hasApproved = true;
                    } else if ("SUBMITTED".equalsIgnoreCase(doc.status)) {
                        hasSubmitted = true;
                    } else if ("REJECTED".equalsIgnoreCase(doc.status)) {
                        hasRejected = true;
                    }
                }
            }
            String status = "PENDING";
            if (hasApproved) {
                status = "VERIFIED";
            } else if (hasSubmitted) {
                status = "SUBMITTED";
            } else if (hasRejected) {
                status = "REJECTED";
            }
            checklist.add(new StudentChecklistItem(req, matching, status));
        }
        return checklist;
    }

    /**
     * STM-06-US-004: Institutional officer retrieves the verification queue of submitted documents.
     */
    public List<VerificationQueueItem> getVerificationQueue(Actor actor, String statusFilter) {
        requireCaller(actor);
        requireAdmin(actor);

        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        try {
            return persistence.getVerificationQueue(actor, statusFilter);
        } finally {
            LogContext.clear();
        }
    }

    /**
     * STM-06-US-004: Institutional officer verifies and approves or rejects a student document.
     */
    public DocumentVerificationRecord verifyDocument(Actor actor, UUID documentId, VerificationDecisionRequest req) {
        requireCaller(actor);
        requireAdmin(actor);

        if (documentId == null) {
            throw new DocumentException(422, "DOCUMENT_ID_REQUIRED", "Document ID is required for verification");
        }
        if (req == null || req.decision == null || req.decision.trim().isEmpty()) {
            throw new DocumentException(422, "DECISION_REQUIRED", "Verification decision (APPROVE or REJECT) is required");
        }

        String rawDecision = req.decision.trim().toUpperCase();
        if (!"APPROVE".equals(rawDecision) && !"REJECT".equals(rawDecision)) {
            throw new DocumentException(422, "INVALID_DECISION", "Decision must be either 'APPROVE' or 'REJECT'");
        }

        String notes = req.rejectionReason != null && !req.rejectionReason.trim().isEmpty()
                ? req.rejectionReason.trim()
                : (req.verificationNotes != null ? req.verificationNotes.trim() : "");

        if ("REJECT".equals(rawDecision) && (notes.isEmpty() || notes.length() < 3)) {
            throw new DocumentException(422, "REJECTION_REASON_REQUIRED", "Rejection reason (minimum 3 characters) is required when rejecting a document");
        }

        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }

        String newStatus;
        String verificationResult;
        String defaultPrefix;
        if ("APPROVE".equals(rawDecision)) {
            newStatus = "APPROVED";
            verificationResult = "VALID";
            defaultPrefix = "VERIFIED-";
        } else {
            newStatus = "REJECTED";
            verificationResult = "INVALID";
            defaultPrefix = "REJECTED-";
        }

        String code = req.verificationCode != null && !req.verificationCode.trim().isEmpty()
                ? req.verificationCode.trim()
                : defaultPrefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        UUID verificationId = UUID.randomUUID();
        Instant now = Instant.now();

        DocumentVerificationRecord verification = new DocumentVerificationRecord(
                verificationId,
                actor.getTenantId(),
                doc.id,
                "STUDENT",
                doc.ownerId,
                code,
                verificationResult,
                notes,
                now,
                now,
                actor.getUserId()
        );

        FlowTracker tracker = logger.flow("verify-student-document", actor.getCorrelationId());
        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        LogContext.setUserRole(actor.getRole());

        try {
            persistence.recordVerificationDecision(actor, doc.id, newStatus, verification);

            logger.info("Recorded document verification: docId={} decision={} status={} result={}",
                    doc.id, rawDecision, newStatus, verificationResult);

            AuditEvent audit = AuditEvent.builder()
                    .action("document.verified")
                    .tenantId(actor.getTenantId().toString())
                    .principalId(actor.getUserId().toString())
                    .principalRole(actor.getRole())
                    .resourceType("STUDENT_DOCUMENT")
                    .resourceId(doc.id.toString())
                    .correlationId(actor.getCorrelationId())
                    .status("SUCCESS")
                    .severity("INFO")
                    .description("Document verification decision=" + rawDecision + ", status=" + newStatus + (notes.isEmpty() ? "" : ", reason=" + notes))
                    .build();
            logger.audit(audit);

            intelligenceService.publishDocumentVerified(actor, doc, verification, rawDecision, newStatus);
            return verification;
        } finally {
            if (tracker != null) {
                tracker.close();
            }
            LogContext.clear();
        }
    }

    /**
     * STM-06-US-005: Student resubmits a rejected document with an updated file version.
     * Enforces ownership/role, rejected status, mime-type/size/checksum/note validation,
     * increments version_no, resets status to SUBMITTED, and emits student.document.uploaded.v1.
     */
    public StudentDocument resubmitStudentDocument(Actor actor, UUID documentId, ResubmitDocumentRequest req) {
        requireCaller(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required for resubmission");
        }
        if (req == null) {
            throw new DocumentException(422, "MISSING_PAYLOAD", "Resubmission payload is required");
        }

        StudentDocument existing = persistence.findStudentDocumentById(actor, documentId);
        if (existing == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found in caller tenant: " + documentId);
        }

        // Ownership or Admin authorization check
        boolean isOwner = actor.getUserId() != null && actor.getUserId().equals(existing.ownerId);
        boolean isAdmin = actor.isCollegeAdmin() || actor.isRegistrar();
        if (!isOwner && !isAdmin) {
            logger.warn("Security violation: Caller {} is not owner of doc {}", actor.getUserId(), documentId);
            throw new DocumentException(403, "FORBIDDEN", "Only the document owner or an authorized administrator can resubmit this document");
        }

        // Status guardrails: cannot resubmit if already APPROVED
        if ("APPROVED".equalsIgnoreCase(existing.status)) {
            throw new DocumentException(409, "DOCUMENT_ALREADY_APPROVED", "Cannot resubmit a document that has already been verified and approved");
        }

        // File validation + malware scan (STM-06-EN-003)
        String secureMime = validateSecureUpload(req.mimeType, req.sizeBytes, req.fileName);
        req.mimeType = secureMime;
        String resubmitName = sanitizeFileName(req.fileName, secureMime);
        ControlledMalwareScanAdapter.ScanResult rescan =
                malwareScanAdapter.scan(resubmitName, secureMime, req.checksumSha256, req.sizeBytes);
        if (!rescan.clean) {
            throw new DocumentException(422, "MALWARE_DETECTED",
                    "Resubmission quarantined by malware scan (liveEndpoint=" + rescan.liveEndpoint + ")");
        }
        if (req.checksumSha256 == null || req.checksumSha256.trim().isEmpty()) {
            throw new DocumentException(422, "MISSING_CHECKSUM", "File SHA-256 checksum is required");
        }
        if (req.changeNote == null || req.changeNote.trim().length() < 3) {
            throw new DocumentException(422, "INVALID_CHANGE_NOTE", "Change note explaining resubmission must be at least 3 characters");
        }

        int nextVersion = existing.currentVersionNo + 1;
        String storagePath = req.storagePath;
        if (storagePath == null || storagePath.trim().isEmpty()) {
            String safeFileName = req.fileName != null ? req.fileName.replaceAll("[^a-zA-Z0-9._-]", "_") : "document.pdf";
            storagePath = "tenants/" + actor.getTenantId() + "/docs/students/" + existing.ownerId + "/v" + nextVersion + "_" + safeFileName;
        }

        DocumentVersion newVersion = new DocumentVersion(
                UUID.randomUUID(),
                actor.getTenantId(),
                existing.id,
                nextVersion,
                storagePath.trim(),
                req.sizeBytes,
                req.checksumSha256.trim(),
                req.changeNote.trim(),
                Instant.now(),
                actor.getUserId()
        );

        FlowTracker tracker = logger.flow("resubmit-student-document", actor.getCorrelationId());
        LogContext.setTenantId(actor.getTenantId().toString());
        LogContext.setUserId(actor.getUserId().toString());
        LogContext.setUserRole(actor.getRole());

        try {
            persistence.resubmitStudentDocument(actor, existing.id, newVersion, req.mimeType.trim());

            StudentDocument updated = persistence.findStudentDocumentById(actor, existing.id);
            logger.info("Resubmitted student document: docId={} version={} status=SUBMITTED",
                    existing.id, nextVersion);

            AuditEvent audit = AuditEvent.builder()
                    .action("document.resubmitted")
                    .tenantId(actor.getTenantId().toString())
                    .principalId(actor.getUserId().toString())
                    .principalRole(actor.getRole())
                    .resourceType("STUDENT_DOCUMENT")
                    .resourceId(existing.id.toString())
                    .correlationId(actor.getCorrelationId())
                    .status("SUCCESS")
                    .severity("INFO")
                    .description("Student resubmitted document version v" + nextVersion + ": note=" + req.changeNote.trim())
                    .build();
            logger.audit(audit);

            intelligenceService.publishDocumentUploaded(actor, updated, newVersion);
            return updated;
        } finally {
            if (tracker != null) {
                tracker.close();
            }
            LogContext.clear();
        }
    }

    /**
     * STM-06-US-005: Retrieves the latest verification decision and feedback for a student document.
     */
    public DocumentVerificationRecord getLatestVerification(Actor actor, UUID documentId) {
        requireCaller(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        DocumentVerificationRecord record = persistence.findLatestVerificationByDocumentId(actor, documentId);
        if (record == null) {
            throw new DocumentException(404, "VERIFICATION_NOT_FOUND", "No verification record found for document: " + documentId);
        }
        return record;
    }

    /**
     * STM-06-US-006: Record regulatory access log when a document is viewed or downloaded.
     */
    public DocumentAccessLog recordAccessLog(Actor actor, UUID documentId, String channel, String clientIp) {
        requireCaller(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        if (actor.isStudent() && !actor.getUserId().equals(doc.ownerId)) {
            throw new DocumentException(403, "FORBIDDEN", "Unauthorized access to document");
        }
        String safeChannel = (channel == null || channel.trim().isEmpty()) ? "DOWNLOAD" : channel.trim().toUpperCase();
        DocumentAccessLog log = persistence.recordAccessLog(actor, documentId, safeChannel, "DOWNLOADED", clientIp);

        // Audit & Intelligence
        AuditEvent audit = AuditEvent.builder()
                .tenantId(actor.getTenantId().toString())
                .action("document.accessed")
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(doc.id.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Document accessed via channel: " + safeChannel)
                .build();
        logger.audit(audit);

        intelligenceService.publishDocumentAccessed(actor, doc, safeChannel);
        return log;
    }

    /**
     * STM-06-US-006: Retrieves regulatory access audit trail for a document.
     */
    public List<DocumentAccessLog> getDocumentAuditTrail(Actor actor, UUID documentId) {
        requireCaller(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        if (actor.isStudent() && !actor.getUserId().equals(doc.ownerId)) {
            throw new DocumentException(403, "FORBIDDEN", "Unauthorized access to document audit trail");
        }
        return persistence.listAccessLogs(actor, documentId);
    }

    /**
     * STM-06-US-006: Computes retention and expiration status for a student document.
     */
    public RetentionInfo computeRetentionInfo(StudentDocument doc) {
        String policy = doc.retentionPolicyCode != null ? doc.retentionPolicyCode : "RET_PERMANENT";
        Instant cutoff = null;
        Instant now = Instant.now();
        if ("RET_7_YEARS".equalsIgnoreCase(policy)) {
            cutoff = doc.createdAt.plus(7 * 365, ChronoUnit.DAYS);
        } else if ("RET_1_YEAR".equalsIgnoreCase(policy)) {
            cutoff = doc.createdAt.plus(365, ChronoUnit.DAYS);
        } else if ("RET_10_YEARS".equalsIgnoreCase(policy)) {
            cutoff = doc.createdAt.plus(10 * 365, ChronoUnit.DAYS);
        }
        boolean isExpired = doc.expiresAt != null && now.isAfter(doc.expiresAt);
        boolean isExpiringSoon = doc.expiresAt != null && !isExpired && now.plus(30, ChronoUnit.DAYS).isAfter(doc.expiresAt);
        return new RetentionInfo(policy, cutoff, isExpired, isExpiringSoon, doc.expiresAt);
    }

    /**
     * STM-06-US-006: Retrieves retention and expiration details for a document.
     */
    public RetentionInfo getRetentionInfo(Actor actor, UUID documentId) {
        requireCaller(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        if (actor.isStudent() && !actor.getUserId().equals(doc.ownerId)) {
            throw new DocumentException(403, "FORBIDDEN", "Unauthorized access to document retention");
        }
        return computeRetentionInfo(doc);
    }

    // --- STM-06-US-012 document access permissions (append-only via doc.document_links) ---

    public static class GrantDocumentAccessRequest {
        public UUID granteeId;
        public String granteeType; // STAFF | EXTERNAL_AGENCY
        public String purpose;
    }

    public DocumentLink grantDocumentAccess(Actor actor, UUID documentId, GrantDocumentAccessRequest req) {
        requireCaller(actor);
        requireAdmin(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        if (req == null || req.granteeId == null) {
            throw new DocumentException(422, "GRANTEE_REQUIRED", "granteeId is required");
        }
        String granteeType = req.granteeType == null ? "" : req.granteeType.trim().toUpperCase();
        if (!"STAFF".equals(granteeType) && !"EXTERNAL_AGENCY".equals(granteeType)) {
            throw new DocumentException(422, "INVALID_GRANTEE_TYPE", "granteeType must be STAFF or EXTERNAL_AGENCY");
        }
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        Instant now = Instant.now();
        DocumentLink link = new DocumentLink(UUID.randomUUID(), actor.getTenantId(), documentId, granteeType,
                req.granteeId, "ACCESS_GRANTED", now, actor.getUserId());
        persistence.saveDocumentLink(actor, link);
        AuditEvent audit = AuditEvent.builder()
                .tenantId(actor.getTenantId().toString())
                .action("document.access_granted")
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(documentId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Access granted to " + granteeType + " " + req.granteeId
                        + (req.purpose != null ? (" purpose=" + req.purpose) : ""))
                .build();
        logger.audit(audit);
        return link;
    }

    public DocumentLink revokeDocumentAccess(Actor actor, UUID documentId, UUID granteeId, String granteeType) {
        requireCaller(actor);
        requireAdmin(actor);
        if (documentId == null || granteeId == null) {
            throw new DocumentException(422, "MISSING_FIELDS", "documentId and granteeId are required");
        }
        String type = granteeType == null ? "STAFF" : granteeType.trim().toUpperCase();
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        boolean hadActive = false;
        for (DocumentLink existing : persistence.listDocumentLinks(actor, documentId)) {
            if (type.equals(existing.entityType) && granteeId.equals(existing.entityId)) {
                if ("ACCESS_GRANTED".equals(existing.relation)) {
                    hadActive = true;
                    break;
                }
                if ("ACCESS_REVOKED".equals(existing.relation)) {
                    break;
                }
            }
        }
        if (!hadActive) {
            throw new DocumentException(404, "ACCESS_GRANT_NOT_FOUND", "No active access grant found for grantee");
        }
        Instant now = Instant.now();
        DocumentLink link = new DocumentLink(UUID.randomUUID(), actor.getTenantId(), documentId, type,
                granteeId, "ACCESS_REVOKED", now, actor.getUserId());
        persistence.saveDocumentLink(actor, link);
        AuditEvent audit = AuditEvent.builder()
                .tenantId(actor.getTenantId().toString())
                .action("document.access_revoked")
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(documentId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Access revoked for " + type + " " + granteeId)
                .build();
        logger.audit(audit);
        return link;
    }

    public List<DocumentLink> listDocumentAccessGrants(Actor actor, UUID documentId) {
        requireCaller(actor);
        StudentDocument doc = requireDocumentReadable(actor, documentId);
        List<DocumentLink> all = persistence.listDocumentLinks(actor, documentId);
        List<DocumentLink> grants = new ArrayList<DocumentLink>();
        for (DocumentLink link : all) {
            if ("ACCESS_GRANTED".equals(link.relation) || "ACCESS_REVOKED".equals(link.relation)) {
                grants.add(link);
            }
        }
        return grants;
    }

    // --- STM-06-US-013 document-sharing consent ---

    public static class SharingConsentRequest {
        public String purpose;
    }

    public DocumentLink recordSharingConsent(Actor actor, UUID documentId, SharingConsentRequest req) {
        requireCaller(actor);
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        if (req == null || req.purpose == null || req.purpose.trim().isEmpty()) {
            throw new DocumentException(422, "PURPOSE_REQUIRED", "Sharing purpose is required");
        }
        String purpose = req.purpose.trim().toUpperCase().replace(' ', '_');
        if (purpose.length() > 64) {
            purpose = purpose.substring(0, 64);
        }
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        boolean ownerOrAdmin = (actor.isStudent() && actor.getUserId().equals(doc.ownerId))
                || actor.isCollegeAdmin() || actor.isRegistrar();
        if (!ownerOrAdmin) {
            throw new DocumentException(403, "FORBIDDEN", "Only document owner or registrar may record sharing consent");
        }
        Instant now = Instant.now();
        DocumentLink link = new DocumentLink(UUID.randomUUID(), actor.getTenantId(), documentId,
                "PURPOSE:" + purpose, actor.getUserId(), "SHARE_CONSENT", now, actor.getUserId());
        persistence.saveDocumentLink(actor, link);
        AuditEvent audit = AuditEvent.builder()
                .tenantId(actor.getTenantId().toString())
                .action("document.sharing_consent_recorded")
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(documentId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Sharing consent recorded purpose=" + purpose)
                .build();
        logger.audit(audit);
        return link;
    }

    public DocumentLink withdrawSharingConsent(Actor actor, UUID documentId, String purpose) {
        requireCaller(actor);
        if (documentId == null || purpose == null || purpose.trim().isEmpty()) {
            throw new DocumentException(422, "MISSING_FIELDS", "documentId and purpose are required");
        }
        String normalized = purpose.trim().toUpperCase().replace(' ', '_');
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        String entityType = "PURPOSE:" + normalized;
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        boolean ownerOrAdmin = (actor.isStudent() && actor.getUserId().equals(doc.ownerId))
                || actor.isCollegeAdmin() || actor.isRegistrar();
        if (!ownerOrAdmin) {
            throw new DocumentException(403, "FORBIDDEN", "Only document owner or registrar may withdraw sharing consent");
        }
        boolean hadActive = false;
        for (DocumentLink existing : persistence.listDocumentLinks(actor, documentId)) {
            if (entityType.equals(existing.entityType) && actor.getUserId().equals(existing.entityId)) {
                if ("SHARE_CONSENT".equals(existing.relation)) {
                    hadActive = true;
                    break;
                }
                if ("SHARE_CONSENT_WITHDRAWN".equals(existing.relation)) {
                    break;
                }
            }
        }
        // Also allow withdraw of any active consent for purpose when registrar acts
        if (!hadActive && (actor.isCollegeAdmin() || actor.isRegistrar())) {
            for (DocumentLink existing : persistence.listDocumentLinks(actor, documentId)) {
                if (entityType.equals(existing.entityType) && "SHARE_CONSENT".equals(existing.relation)) {
                    hadActive = true;
                    break;
                }
            }
        }
        if (!hadActive) {
            throw new DocumentException(404, "CONSENT_NOT_FOUND", "No active sharing consent found for purpose");
        }
        Instant now = Instant.now();
        DocumentLink link = new DocumentLink(UUID.randomUUID(), actor.getTenantId(), documentId,
                entityType, actor.getUserId(), "SHARE_CONSENT_WITHDRAWN", now, actor.getUserId());
        persistence.saveDocumentLink(actor, link);
        AuditEvent audit = AuditEvent.builder()
                .tenantId(actor.getTenantId().toString())
                .action("document.sharing_consent_withdrawn")
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(documentId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Sharing consent withdrawn purpose=" + normalized)
                .build();
        logger.audit(audit);
        return link;
    }

    public List<DocumentLink> listSharingConsents(Actor actor, UUID documentId) {
        requireCaller(actor);
        requireDocumentReadable(actor, documentId);
        List<DocumentLink> all = persistence.listDocumentLinks(actor, documentId);
        List<DocumentLink> consents = new ArrayList<DocumentLink>();
        for (DocumentLink link : all) {
            if ("SHARE_CONSENT".equals(link.relation) || "SHARE_CONSENT_WITHDRAWN".equals(link.relation)) {
                consents.add(link);
            }
        }
        return consents;
    }

    // --- STM-06-EN-003 secure upload/download, validation, malware scan, signed URL ---

    public static class SignedDownloadUrl {
        public final UUID documentId;
        public final String storageBucket;
        public final String storagePath;
        public final String mimeType;
        public final long sizeBytes;
        public final String token;
        public final Instant expiresAt;
        public final String downloadPath;
        public final String scanStatus;
        public final boolean malwareLiveEndpoint;

        public SignedDownloadUrl(UUID documentId, String storageBucket, String storagePath, String mimeType,
                                 long sizeBytes, String token, Instant expiresAt, String downloadPath,
                                 String scanStatus, boolean malwareLiveEndpoint) {
            this.documentId = documentId;
            this.storageBucket = storageBucket;
            this.storagePath = storagePath;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.token = token;
            this.expiresAt = expiresAt;
            this.downloadPath = downloadPath;
            this.scanStatus = scanStatus;
            this.malwareLiveEndpoint = malwareLiveEndpoint;
        }
    }

    public SignedDownloadUrl issueSignedDownloadUrl(Actor actor, UUID documentId) {
        requireCaller(actor);
        StudentDocument doc = requireDocumentReadable(actor, documentId);
        String scanStatus = latestScanStatus(actor, documentId);
        if ("MALWARE_SCAN_QUARANTINED".equals(scanStatus)) {
            throw new DocumentException(403, "DOCUMENT_QUARANTINED", "Quarantined documents cannot be downloaded");
        }
        long exp = Instant.now().getEpochSecond() + SIGNED_URL_TTL_SECONDS;
        String token = signedDownloadTokenService.issue(doc.id, actor.getTenantId(), actor.getUserId(), exp);
        Instant expiresAt = Instant.ofEpochSecond(exp);
        String downloadPath = "/api/v1/documents/download?token=" + token;
        AuditEvent audit = AuditEvent.builder()
                .tenantId(actor.getTenantId().toString())
                .action("document.signed_url_issued")
                .principalId(actor.getUserId().toString())
                .principalRole(actor.getRole())
                .resourceType("STUDENT_DOCUMENT")
                .resourceId(documentId.toString())
                .correlationId(actor.getCorrelationId())
                .status("SUCCESS")
                .severity("INFO")
                .description("Signed download URL issued expiresAt=" + expiresAt + " scanStatus=" + scanStatus)
                .build();
        logger.audit(audit);
        return new SignedDownloadUrl(doc.id, doc.storageBucket, doc.storagePath, doc.mimeType, doc.sizeBytes,
                token, expiresAt, downloadPath, scanStatus, malwareScanAdapter.isLiveEndpointAvailable());
    }

    public SignedDownloadUrl resolveSignedDownload(Actor actor, String token) {
        requireCaller(actor);
        SignedDownloadTokenService.ParsedToken parsed;
        try {
            parsed = signedDownloadTokenService.parseAndVerify(token);
        } catch (IllegalArgumentException ex) {
            throw new DocumentException(403, "SIGNED_URL_INVALID", ex.getMessage());
        }
        if (Instant.now().getEpochSecond() > parsed.expiresAtEpochSec) {
            throw new DocumentException(403, "SIGNED_URL_EXPIRED", "Signed download URL has expired");
        }
        if (!actor.getTenantId().equals(parsed.tenantId) || !actor.getUserId().equals(parsed.userId)) {
            throw new DocumentException(403, "SIGNED_URL_ACTOR_MISMATCH", "Signed URL is not valid for this caller");
        }
        StudentDocument doc = requireDocumentReadable(actor, parsed.documentId);
        String scanStatus = latestScanStatus(actor, parsed.documentId);
        String downloadPath = "/api/v1/documents/download?token=" + token;
        return new SignedDownloadUrl(doc.id, doc.storageBucket, doc.storagePath, doc.mimeType, doc.sizeBytes,
                token, Instant.ofEpochSecond(parsed.expiresAtEpochSec), downloadPath, scanStatus,
                malwareScanAdapter.isLiveEndpointAvailable());
    }

    public List<DocumentLink> listSecurityScans(Actor actor, UUID documentId) {
        requireCaller(actor);
        requireDocumentReadable(actor, documentId);
        List<DocumentLink> all = persistence.listDocumentLinks(actor, documentId);
        List<DocumentLink> scans = new ArrayList<DocumentLink>();
        for (DocumentLink link : all) {
            if ("MALWARE_SCAN_PASSED".equals(link.relation) || "MALWARE_SCAN_QUARANTINED".equals(link.relation)) {
                scans.add(link);
            }
        }
        return scans;
    }

    private String latestScanStatus(Actor actor, UUID documentId) {
        List<DocumentLink> scans = listSecurityScans(actor, documentId);
        if (scans.isEmpty()) {
            return "SCAN_PENDING";
        }
        return scans.get(scans.size() - 1).relation;
    }

    private String validateSecureUpload(String mimeType, Long sizeBytes, String fileName) {
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.trim().toLowerCase(Locale.ROOT))) {
            throw new DocumentException(422, "UNSUPPORTED_MIME_TYPE",
                    "MIME type '" + mimeType + "' is not supported. Allowed: PDF, JPEG, PNG, WEBP");
        }
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new DocumentException(422, "INVALID_FILE_SIZE", "File size must be greater than 0 bytes");
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new DocumentException(422, "FILE_TOO_LARGE",
                    "File size (" + sizeBytes + " bytes) exceeds maximum limit of " + MAX_FILE_SIZE_BYTES + " bytes (10MB)");
        }
        sanitizeFileName(fileName, mimeType.trim().toLowerCase(Locale.ROOT));
        return mimeType.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String fileName, String mimeType) {
        String name = (fileName == null || fileName.trim().isEmpty()) ? defaultFileNameForMime(mimeType) : fileName.trim();
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains("\0")) {
            throw new DocumentException(422, "INVALID_FILE_NAME", "File name must not contain path separators or traversal");
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new DocumentException(422, "INVALID_FILE_EXTENSION", "File name must include a valid extension");
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSIONS.contains(ext)) {
            throw new DocumentException(422, "BLOCKED_FILE_EXTENSION", "Executable or script extensions are not allowed");
        }
        if (!extensionMatchesMime(ext, mimeType)) {
            throw new DocumentException(422, "MIME_EXTENSION_MISMATCH",
                    "File extension ." + ext + " does not match MIME type " + mimeType);
        }
        return name;
    }

    private static String defaultFileNameForMime(String mimeType) {
        if ("image/jpeg".equals(mimeType)) {
            return "document.jpg";
        }
        if ("image/png".equals(mimeType)) {
            return "document.png";
        }
        if ("image/webp".equals(mimeType)) {
            return "document.webp";
        }
        return "document.pdf";
    }

    private static boolean extensionMatchesMime(String ext, String mimeType) {
        if ("application/pdf".equals(mimeType)) {
            return "pdf".equals(ext);
        }
        if ("image/jpeg".equals(mimeType)) {
            return "jpg".equals(ext) || "jpeg".equals(ext);
        }
        if ("image/png".equals(mimeType)) {
            return "png".equals(ext);
        }
        if ("image/webp".equals(mimeType)) {
            return "webp".equals(ext);
        }
        return false;
    }

    private StudentDocument requireDocumentReadable(Actor actor, UUID documentId) {
        if (documentId == null) {
            throw new DocumentException(422, "MISSING_DOCUMENT_ID", "Document ID is required");
        }
        StudentDocument doc = persistence.findStudentDocumentById(actor, documentId);
        if (doc == null) {
            throw new DocumentException(404, "DOCUMENT_NOT_FOUND", "Document not found: " + documentId);
        }
        if (actor.isStudent() && !actor.getUserId().equals(doc.ownerId)) {
            throw new DocumentException(403, "FORBIDDEN", "Unauthorized access to document");
        }
        return doc;
    }

    private void requireCaller(Actor actor) {
        if (actor == null || actor.getTenantId() == null || actor.getUserId() == null) {
            throw new DocumentException(401, "UNAUTHENTICATED", "Authenticated caller identity required");
        }
    }

    private void requireAdmin(Actor actor) {
        if (!actor.isCollegeAdmin() && !actor.isRegistrar()) {
            logger.warn("Unauthorized document type configuration attempt by role={}", actor.getRole());
            throw new DocumentException(403, "FORBIDDEN",
                    "College Admin or Registrar role required to configure document types");
        }
    }
}
