package com.campx.document.intelligence;

import com.campx.document.Actor;
import com.campx.document.DocumentDomainService;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the versioned document JSON contract to the CampXSync Intelligence Layer.
 * Live delivery stays false until the Intelligence endpoint is configured.
 */
public class DocumentIntelligenceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DocumentIntelligenceService.class);
    private static final String SERVICE_NAME = "document-service";
    private final ControlledIntelligenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentIntelligenceService() {
        this(new ControlledIntelligenceAdapter());
    }

    public DocumentIntelligenceService(ControlledIntelligenceAdapter adapter) {
        this.adapter = adapter == null ? new ControlledIntelligenceAdapter() : adapter;
    }

    /**
     * Emits student.document_type.configured.v1 when a document type is created or updated.
     */
    public void publishDocumentTypeConfigured(Actor actor, DocumentType documentType) {
        if (actor == null || documentType == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("documentTypeId", documentType.id != null ? documentType.id.toString() : null);
            payload.put("tenantId", documentType.tenantId != null ? documentType.tenantId.toString() : null);
            payload.put("code", documentType.code);
            payload.put("name", documentType.name);
            payload.put("category", documentType.category);
            payload.put("module", documentType.module);
            payload.put("defaultClassification", documentType.defaultClassification);
            payload.put("requiresSignature", documentType.requiresSignature);
            payload.put("retentionPolicyCode", documentType.retentionPolicyCode);
            payload.put("isActive", documentType.isActive);
            payload.put("configuredBy", actor.getUserId() != null ? actor.getUserId().toString() : null);
            payload.put("configuredRole", actor.getRole());

            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(DocumentDomainService.DOCUMENT_TYPE_CONFIGURED)
                    .schemaVersion(DocumentDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_DOCUMENT_TYPE")
                    .aggregateId(documentType.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-doc-type-" + actor.getTenantId() + "-" + documentType.id + "-" + documentType.updatedAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();

            adapter.publish(event);
            logger.info("Published intelligence event: eventType={} documentTypeId={} code={}",
                    DocumentDomainService.DOCUMENT_TYPE_CONFIGURED, documentType.id, documentType.code);
        } catch (Exception e) {
            logger.error("Failed to publish document type configured event", e);
        }
    }

    /**
     * Emits student.document_requirement.defined.v1 when a stage document requirement is created or updated.
     */
    public void publishStageRequirementDefined(Actor actor, DocumentDomainService.StageDocumentRequirement req) {
        if (actor == null || req == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("requirementId", req.id != null ? req.id.toString() : null);
            payload.put("tenantId", req.tenantId != null ? req.tenantId.toString() : null);
            payload.put("programId", req.programId != null ? req.programId.toString() : null);
            payload.put("programCode", req.programCode);
            payload.put("programName", req.programName);
            payload.put("academicStage", req.academicStage);
            payload.put("documentTypeId", req.documentTypeId != null ? req.documentTypeId.toString() : null);
            payload.put("documentTypeCode", req.documentTypeCode);
            payload.put("documentTypeName", req.documentTypeName);
            payload.put("isMandatory", req.isMandatory);
            payload.put("minCount", req.minCount);
            payload.put("maxCount", req.maxCount);
            payload.put("submissionDeadlineDays", req.submissionDeadlineDays);
            payload.put("description", req.description);
            payload.put("isActive", req.isActive);
            payload.put("definedBy", actor.getUserId() != null ? actor.getUserId().toString() : null);
            payload.put("definedRole", actor.getRole());

            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(DocumentDomainService.DOCUMENT_REQUIREMENT_DEFINED)
                    .schemaVersion(DocumentDomainService.SCHEMA_VERSION)
                    .aggregateType("STAGE_DOCUMENT_REQUIREMENT")
                    .aggregateId(req.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-doc-req-" + actor.getTenantId() + "-" + req.id + "-" + req.updatedAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();

            adapter.publish(event);
            logger.info("Published intelligence event: eventType={} requirementId={} stage={}",
                    DocumentDomainService.DOCUMENT_REQUIREMENT_DEFINED, req.id, req.academicStage);
        } catch (Exception e) {
            logger.error("Failed to publish document requirement defined event", e);
        }
    }

    /**
     * Emits student.document.uploaded.v1 when a student document is uploaded (STM-06-US-003).
     */
    public void publishDocumentUploaded(Actor actor, DocumentDomainService.StudentDocument doc, DocumentDomainService.DocumentVersion version) {
        if (actor == null || doc == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("documentId", doc.id != null ? doc.id.toString() : null);
            payload.put("tenantId", doc.tenantId != null ? doc.tenantId.toString() : null);
            payload.put("documentTypeId", doc.documentTypeId != null ? doc.documentTypeId.toString() : null);
            payload.put("documentTypeCode", doc.documentTypeCode);
            payload.put("documentTypeName", doc.documentTypeName);
            payload.put("title", doc.title);
            payload.put("classification", doc.classification);
            payload.put("status", doc.status);
            payload.put("studentId", doc.ownerId != null ? doc.ownerId.toString() : null);
            payload.put("currentVersionNo", doc.currentVersionNo);
            payload.put("mimeType", doc.mimeType);
            payload.put("sizeBytes", doc.sizeBytes);
            payload.put("checksumSha256", doc.checksumSha256);
            payload.put("storagePath", doc.storagePath);
            payload.put("uploadedBy", actor.getUserId() != null ? actor.getUserId().toString() : null);
            payload.put("uploadedRole", actor.getRole());

            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(DocumentDomainService.DOCUMENT_UPLOADED)
                    .schemaVersion(DocumentDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_DOCUMENT")
                    .aggregateId(doc.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-doc-up-" + actor.getTenantId() + "-" + doc.id + "-" + doc.createdAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();

            adapter.publish(event);
            logger.info("Published intelligence event: eventType={} documentId={} studentId={}",
                    DocumentDomainService.DOCUMENT_UPLOADED, doc.id, doc.ownerId);
        } catch (Exception e) {
            logger.error("Failed to publish document uploaded event", e);
        }
    }

    /**
     * Emits student.document.verified.v1 when an officer verifies or rejects a document (STM-06-US-004).
     */
    public void publishDocumentVerified(Actor actor, DocumentDomainService.StudentDocument doc,
                                        DocumentDomainService.DocumentVerificationRecord verification,
                                        String decision, String newStatus) {
        if (actor == null || doc == null || verification == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("verificationId", verification.id != null ? verification.id.toString() : null);
            payload.put("tenantId", doc.tenantId != null ? doc.tenantId.toString() : null);
            payload.put("documentId", doc.id != null ? doc.id.toString() : null);
            payload.put("studentId", doc.ownerId != null ? doc.ownerId.toString() : null);
            payload.put("documentTypeId", doc.documentTypeId != null ? doc.documentTypeId.toString() : null);
            payload.put("documentTypeCode", doc.documentTypeCode);
            payload.put("documentTypeName", doc.documentTypeName);
            payload.put("title", doc.title);
            payload.put("decision", decision);
            payload.put("status", newStatus);
            payload.put("result", verification.result);
            payload.put("verificationCode", verification.verificationCode);
            payload.put("notes", verification.notes);
            payload.put("verifiedBy", actor.getUserId() != null ? actor.getUserId().toString() : null);
            payload.put("verifiedRole", actor.getRole());
            payload.put("verifiedAt", verification.verifiedAt != null ? verification.verifiedAt.toString() : null);

            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(DocumentDomainService.DOCUMENT_VERIFIED)
                    .schemaVersion(DocumentDomainService.SCHEMA_VERSION)
                    .aggregateType("DOCUMENT_VERIFICATION")
                    .aggregateId(verification.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-doc-verif-" + actor.getTenantId() + "-" + verification.id + "-" + verification.verifiedAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();

            adapter.publish(event);
            logger.info("Published intelligence event: eventType={} documentId={} decision={} result={}",
                    DocumentDomainService.DOCUMENT_VERIFIED, doc.id, decision, verification.result);
        } catch (Exception e) {
            logger.error("Failed to publish document verified event", e);
        }
    }

    /**
     * Emits student.document.accessed.v1 when a document is accessed or downloaded (STM-06-US-006).
     */
    public void publishDocumentAccessed(Actor actor, DocumentDomainService.StudentDocument doc, String channel) {
        if (actor == null || doc == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("tenantId", doc.tenantId.toString());
            payload.put("documentId", doc.id.toString());
            payload.put("studentId", doc.ownerId != null ? doc.ownerId.toString() : null);
            payload.put("channel", channel);
            payload.put("accessedBy", actor.getUserId().toString());
            payload.put("accessedRole", actor.getRole());
            payload.put("accessedAt", java.time.Instant.now().toString());

            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(DocumentDomainService.DOCUMENT_ACCESSED)
                    .schemaVersion(DocumentDomainService.SCHEMA_VERSION)
                    .aggregateType("DOCUMENT_ACCESS")
                    .aggregateId(doc.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-doc-access-" + actor.getTenantId() + "-" + doc.id + "-" + System.currentTimeMillis())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();

            adapter.publish(event);
            logger.info("Published intelligence event: eventType={} documentId={} channel={}",
                    DocumentDomainService.DOCUMENT_ACCESSED, doc.id, channel);
        } catch (Exception e) {
            logger.error("Failed to publish document accessed event", e);
        }
    }
}
