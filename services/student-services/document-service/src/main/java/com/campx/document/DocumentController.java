package com.campx.document;

import com.campx.document.DocumentDomainService.ConfigureDocumentTypeRequest;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.DocumentDomainService.UpdateDocumentTypeRequest;
import com.campx.document.DocumentDomainService.StageDocumentRequirement;
import com.campx.document.DocumentDomainService.DefineStageRequirementRequest;
import com.campx.document.DocumentDomainService.UpdateStageRequirementRequest;
import com.campx.document.DocumentDomainService.StudentDocument;
import com.campx.document.DocumentDomainService.DocumentVersion;
import com.campx.document.DocumentDomainService.UploadStudentDocumentRequest;
import com.campx.document.DocumentDomainService.StudentChecklistItem;
import com.campx.document.security.GatewayHmacVerifier;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter for STM-06 document routes.
 * Maps endpoints, validates incoming Gateway signatures, and serializes RFC 7807 problem details.
 */
public class DocumentController {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DocumentController.class);
    private final DocumentDomainService domain;
    private final GatewayHmacVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public DocumentController(DocumentDomainService domain, GatewayHmacVerifier verifier) {
        this.domain = domain;
        this.verifier = verifier;
    }

    public Response handle(String method, String path, Map<String, String> headers, String body) {
        String correlationId = header(headers, "x-trace-id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = header(headers, "x-correlation-id");
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString().replace("-", "");
        }
        com.campx.logger.context.LogContext.setTraceId(correlationId);
        String tenantHeader = header(headers, "x-tenant-id");
        if (tenantHeader != null && !tenantHeader.trim().isEmpty()) {
            com.campx.logger.context.LogContext.setTenantId(tenantHeader.trim());
        }
        String userHeader = header(headers, "x-user-id");
        if (userHeader != null && !userHeader.trim().isEmpty()) {
            com.campx.logger.context.LogContext.setUserId(userHeader.trim());
        }
        String roleHeader = header(headers, "x-user-role");
        if (roleHeader != null && !roleHeader.trim().isEmpty()) {
            com.campx.logger.context.LogContext.setUserRole(roleHeader.trim());
        }
        com.campx.logger.context.LogContext.put(com.campx.logger.context.LogContext.KEY_SERVICE, "document-service");

        FlowTracker tracker = logger.flow((method == null ? "REQUEST" : method) + " " + path, correlationId);
        try {
            if ("GET".equals(method) && ("/health".equals(path) || "/api/v1/documents/health".equals(path))) {
                ObjectNode health = mapper.createObjectNode();
                health.put("status", "UP");
                health.put("service", "document-service");
                health.put("module", "STM-06");
                return json(200, health, correlationId);
            }

            verifier.verify(method, path, headers, body);
            Actor actor = actor(headers);
            String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
            JsonNode json = body == null || body.trim().isEmpty() ? mapper.createObjectNode() : mapper.readTree(body);

            // POST /api/v1/documents/types - Configure Document Type (STM-06-US-001)
            if ("POST".equals(method) && "/api/v1/documents/types".equals(normalized)) {
                ConfigureDocumentTypeRequest req = new ConfigureDocumentTypeRequest();
                req.code = text(json, "code");
                req.name = text(json, "name");
                req.category = text(json, "category");
                req.module = text(json, "module");
                req.defaultClassification = text(json, "defaultClassification");
                if (json.has("requiresSignature")) {
                    req.requiresSignature = json.get("requiresSignature").asBoolean();
                }
                req.retentionPolicyCode = text(json, "retentionPolicyCode");
                if (json.has("isActive")) {
                    req.isActive = json.get("isActive").asBoolean();
                }

                DocumentType created = domain.configureDocumentType(actor, req);
                return json(201, documentTypeNode(created), correlationId);
            }

            // GET /api/v1/documents/types - List Document Types
            if ("GET".equals(method) && "/api/v1/documents/types".equals(normalized)) {
                String category = header(headers, "x-query-category");
                String activeOnlyStr = header(headers, "x-query-active-only");
                Boolean activeOnly = activeOnlyStr == null ? null : Boolean.valueOf(activeOnlyStr);

                List<DocumentType> list = domain.listDocumentTypes(actor, category, activeOnly);
                return json(200, documentTypeListNode(list), correlationId);
            }

            // GET /api/v1/documents/types/{id} - Get Document Type
            if ("GET".equals(method) && normalized.startsWith("/api/v1/documents/types/")) {
                String idPart = normalized.substring("/api/v1/documents/types/".length());
                UUID id = UUID.fromString(idPart);
                DocumentType dt = domain.getDocumentType(actor, id);
                return json(200, documentTypeNode(dt), correlationId);
            }

            // PUT /api/v1/documents/types/{id} - Update Document Type
            if ("PUT".equals(method) && normalized.startsWith("/api/v1/documents/types/")) {
                String idPart = normalized.substring("/api/v1/documents/types/".length());
                UUID id = UUID.fromString(idPart);
                UpdateDocumentTypeRequest req = new UpdateDocumentTypeRequest();
                req.name = text(json, "name");
                req.category = text(json, "category");
                req.module = text(json, "module");
                req.defaultClassification = text(json, "defaultClassification");
                if (json.has("requiresSignature")) {
                    req.requiresSignature = json.get("requiresSignature").asBoolean();
                }
                req.retentionPolicyCode = text(json, "retentionPolicyCode");
                if (json.has("isActive")) {
                    req.isActive = json.get("isActive").asBoolean();
                }

                DocumentType updated = domain.updateDocumentType(actor, id, req);
                return json(200, documentTypeNode(updated), correlationId);
            }

            // POST /api/v1/documents/requirements - Define Stage Requirement (STM-06-US-002)
            if ("POST".equals(method) && "/api/v1/documents/requirements".equals(normalized)) {
                DefineStageRequirementRequest req = new DefineStageRequirementRequest();
                String progIdStr = text(json, "programId");
                if (progIdStr != null && !progIdStr.trim().isEmpty()) {
                    req.programId = UUID.fromString(progIdStr.trim());
                }
                req.academicStage = text(json, "academicStage");
                String docTypeIdStr = text(json, "documentTypeId");
                if (docTypeIdStr != null && !docTypeIdStr.trim().isEmpty()) {
                    req.documentTypeId = UUID.fromString(docTypeIdStr.trim());
                }
                if (json.has("isMandatory")) {
                    req.isMandatory = json.get("isMandatory").asBoolean();
                }
                if (json.has("minCount")) {
                    req.minCount = json.get("minCount").asInt();
                }
                if (json.has("maxCount") && !json.get("maxCount").isNull()) {
                    req.maxCount = json.get("maxCount").asInt();
                }
                if (json.has("submissionDeadlineDays") && !json.get("submissionDeadlineDays").isNull()) {
                    req.submissionDeadlineDays = json.get("submissionDeadlineDays").asInt();
                }
                req.description = text(json, "description");
                if (json.has("isActive")) {
                    req.isActive = json.get("isActive").asBoolean();
                }

                StageDocumentRequirement created = domain.defineStageRequirement(actor, req);
                return json(201, stageRequirementNode(created), correlationId);
            }

            // GET /api/v1/documents/requirements - List Stage Requirements
            if ("GET".equals(method) && "/api/v1/documents/requirements".equals(normalized)) {
                String progIdStr = header(headers, "x-query-program-id");
                UUID programId = (progIdStr != null && !progIdStr.trim().isEmpty()) ? UUID.fromString(progIdStr.trim()) : null;
                String stage = header(headers, "x-query-stage");
                String activeOnlyStr = header(headers, "x-query-active-only");
                Boolean activeOnly = activeOnlyStr == null ? null : Boolean.valueOf(activeOnlyStr);

                List<StageDocumentRequirement> list = domain.listStageRequirements(actor, programId, stage, activeOnly);
                return json(200, stageRequirementListNode(list), correlationId);
            }

            // GET /api/v1/documents/requirements/{id} - Get Stage Requirement
            if ("GET".equals(method) && normalized.startsWith("/api/v1/documents/requirements/")) {
                String idPart = normalized.substring("/api/v1/documents/requirements/".length());
                UUID id = UUID.fromString(idPart);
                StageDocumentRequirement req = domain.getStageRequirement(actor, id);
                return json(200, stageRequirementNode(req), correlationId);
            }

            // PUT /api/v1/documents/requirements/{id} - Update Stage Requirement
            if ("PUT".equals(method) && normalized.startsWith("/api/v1/documents/requirements/")) {
                String idPart = normalized.substring("/api/v1/documents/requirements/".length());
                UUID id = UUID.fromString(idPart);
                UpdateStageRequirementRequest req = new UpdateStageRequirementRequest();
                if (json.has("isMandatory")) {
                    req.isMandatory = json.get("isMandatory").asBoolean();
                }
                if (json.has("minCount")) {
                    req.minCount = json.get("minCount").asInt();
                }
                if (json.has("maxCount") && !json.get("maxCount").isNull()) {
                    req.maxCount = json.get("maxCount").asInt();
                }
                if (json.has("submissionDeadlineDays") && !json.get("submissionDeadlineDays").isNull()) {
                    req.submissionDeadlineDays = json.get("submissionDeadlineDays").asInt();
                }
                req.description = text(json, "description");
                if (json.has("isActive")) {
                    req.isActive = json.get("isActive").asBoolean();
                }

                StageDocumentRequirement updated = domain.updateStageRequirement(actor, id, req);
                return json(200, stageRequirementNode(updated), correlationId);
            }

            // DELETE /api/v1/documents/requirements/{id} - Delete Stage Requirement
            if ("DELETE".equals(method) && normalized.startsWith("/api/v1/documents/requirements/")) {
                String idPart = normalized.substring("/api/v1/documents/requirements/".length());
                UUID id = UUID.fromString(idPart);
                domain.deleteStageRequirement(actor, id);
                ObjectNode resp = mapper.createObjectNode();
                resp.put("deleted", true);
                resp.put("id", id.toString());
                return json(200, resp, correlationId);
            }

            // POST /api/v1/documents/submissions (or /api/v1/documents/upload) - Upload Student Document (STM-06-US-003)
            if ("POST".equals(method) && ("/api/v1/documents/submissions".equals(normalized) || "/api/v1/documents/upload".equals(normalized))) {
                UploadStudentDocumentRequest req = new UploadStudentDocumentRequest();
                String studentIdStr = text(json, "studentId");
                if (studentIdStr != null && !studentIdStr.trim().isEmpty()) {
                    req.studentId = UUID.fromString(studentIdStr.trim());
                }
                String docTypeIdStr = text(json, "documentTypeId");
                if (docTypeIdStr != null && !docTypeIdStr.trim().isEmpty()) {
                    req.documentTypeId = UUID.fromString(docTypeIdStr.trim());
                }
                String stageReqIdStr = text(json, "stageRequirementId");
                if (stageReqIdStr != null && !stageReqIdStr.trim().isEmpty()) {
                    req.stageRequirementId = UUID.fromString(stageReqIdStr.trim());
                }
                req.title = text(json, "title");
                req.mimeType = text(json, "mimeType");
                if (json.has("sizeBytes")) {
                    req.sizeBytes = json.get("sizeBytes").asLong();
                }
                req.fileName = text(json, "fileName");
                req.changeNote = text(json, "changeNote");
                req.checksumSha256 = text(json, "checksumSha256");

                StudentDocument uploaded = domain.uploadStudentDocument(actor, req);
                return json(201, studentDocumentNode(uploaded), correlationId);
            }

            // GET /api/v1/documents/submissions/checklist - Get Student Checklist (STM-06-US-003)
            if ("GET".equals(method) && "/api/v1/documents/submissions/checklist".equals(normalized)) {
                String studentIdStr = header(headers, "x-query-student-id");
                UUID studentId = (studentIdStr != null && !studentIdStr.trim().isEmpty()) ? UUID.fromString(studentIdStr.trim()) : null;
                String stage = header(headers, "x-query-stage");
                String progIdStr = header(headers, "x-query-program-id");
                UUID programId = (progIdStr != null && !progIdStr.trim().isEmpty()) ? UUID.fromString(progIdStr.trim()) : null;

                List<StudentChecklistItem> checklist = domain.getStudentChecklist(actor, studentId, stage, programId);
                return json(200, studentChecklistNode(checklist), correlationId);
            }

            // GET /api/v1/documents/submissions - List Student Submissions (STM-06-US-003)
            if ("GET".equals(method) && "/api/v1/documents/submissions".equals(normalized)) {
                String studentIdStr = header(headers, "x-query-student-id");
                UUID studentId = (studentIdStr != null && !studentIdStr.trim().isEmpty()) ? UUID.fromString(studentIdStr.trim()) : null;
                String status = header(headers, "x-query-status");

                List<StudentDocument> list = domain.listStudentDocuments(actor, studentId, status);
                return json(200, studentDocumentListNode(list), correlationId);
            }

            // GET /api/v1/documents/verifications - Get Verification Queue (STM-06-US-004)
            if ("GET".equals(method) && ("/api/v1/documents/verifications".equals(normalized) || "/api/v1/documents/verifications/queue".equals(normalized))) {
                String status = header(headers, "x-query-status");
                List<DocumentDomainService.VerificationQueueItem> queue = domain.getVerificationQueue(actor, status);
                return json(200, verificationQueueListNode(queue), correlationId);
            }

            // POST /api/v1/documents/verifications/{id}/decision - Review Decision (STM-06-US-004)
            if ("POST".equals(method) && (normalized.startsWith("/api/v1/documents/verifications/") && normalized.endsWith("/decision")
                    || normalized.startsWith("/api/v1/documents/submissions/") && normalized.endsWith("/decision"))) {
                String idPart;
                if (normalized.startsWith("/api/v1/documents/verifications/")) {
                    idPart = normalized.substring("/api/v1/documents/verifications/".length(), normalized.length() - "/decision".length());
                } else {
                    idPart = normalized.substring("/api/v1/documents/submissions/".length(), normalized.length() - "/decision".length());
                }
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.VerificationDecisionRequest req = new DocumentDomainService.VerificationDecisionRequest();
                req.decision = text(json, "decision");
                req.verificationCode = text(json, "verificationCode");
                req.rejectionReason = text(json, "rejectionReason");
                req.verificationNotes = text(json, "verificationNotes");

                DocumentDomainService.DocumentVerificationRecord record = domain.verifyDocument(actor, documentId, req);
                return json(200, verificationDecisionNode(record), correlationId);
            }

            // POST /api/v1/documents/submissions/{id}/resubmit (or /api/v1/documents/{id}/resubmit) - Resubmit Student Document (STM-06-US-005)
            if ("POST".equals(method) && ((normalized.startsWith("/api/v1/documents/submissions/") && normalized.endsWith("/resubmit"))
                    || (normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/resubmit")))) {
                String idPart;
                if (normalized.startsWith("/api/v1/documents/submissions/")) {
                    idPart = normalized.substring("/api/v1/documents/submissions/".length(), normalized.length() - "/resubmit".length());
                } else {
                    idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/resubmit".length());
                }
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.ResubmitDocumentRequest req = new DocumentDomainService.ResubmitDocumentRequest();
                req.storagePath = text(json, "storagePath");
                req.fileName = text(json, "fileName");
                req.mimeType = text(json, "mimeType");
                if (json.has("sizeBytes")) {
                    req.sizeBytes = json.get("sizeBytes").asLong();
                }
                req.checksumSha256 = text(json, "checksumSha256");
                req.changeNote = text(json, "changeNote");

                StudentDocument resubmitted = domain.resubmitStudentDocument(actor, documentId, req);
                return json(200, studentDocumentNode(resubmitted), correlationId);
            }

            // GET /api/v1/documents/submissions/{id}/verification (or /api/v1/documents/{id}/verification) - Get Latest Verification (STM-06-US-005)
            if ("GET".equals(method) && ((normalized.startsWith("/api/v1/documents/submissions/") && normalized.endsWith("/verification"))
                    || (normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/verification")))) {
                String idPart;
                if (normalized.startsWith("/api/v1/documents/submissions/")) {
                    idPart = normalized.substring("/api/v1/documents/submissions/".length(), normalized.length() - "/verification".length());
                } else {
                    idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/verification".length());
                }
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.DocumentVerificationRecord record = domain.getLatestVerification(actor, documentId);
                return json(200, verificationDecisionNode(record), correlationId);
            }

            // POST /api/v1/documents/{id}/access-log (or /api/v1/documents/submissions/{id}/access-log) - Record Access Log (STM-06-US-006)
            if ("POST".equals(method) && ((normalized.startsWith("/api/v1/documents/submissions/") && normalized.endsWith("/access-log"))
                    || (normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/access-log")))) {
                String idPart;
                if (normalized.startsWith("/api/v1/documents/submissions/")) {
                    idPart = normalized.substring("/api/v1/documents/submissions/".length(), normalized.length() - "/access-log".length());
                } else {
                    idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/access-log".length());
                }
                UUID documentId = UUID.fromString(idPart);
                String channel = text(json, "channel");
                if (channel == null) {
                    channel = "DOWNLOAD";
                }
                String clientIp = text(json, "clientIp");
                if (clientIp == null) {
                    clientIp = header(headers, "x-forwarded-for");
                }
                if (clientIp == null) {
                    clientIp = header(headers, "x-real-ip");
                }
                if (clientIp == null) {
                    clientIp = "127.0.0.1";
                }
                DocumentDomainService.DocumentAccessLog accessLog = domain.recordAccessLog(actor, documentId, channel, clientIp);
                return json(201, accessLogNode(accessLog), correlationId);
            }

            // GET /api/v1/documents/{id}/audit-trail (or /api/v1/documents/submissions/{id}/audit-trail) - Get Audit Trail (STM-06-US-006)
            if ("GET".equals(method) && ((normalized.startsWith("/api/v1/documents/submissions/") && normalized.endsWith("/audit-trail"))
                    || (normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/audit-trail")))) {
                String idPart;
                if (normalized.startsWith("/api/v1/documents/submissions/")) {
                    idPart = normalized.substring("/api/v1/documents/submissions/".length(), normalized.length() - "/audit-trail".length());
                } else {
                    idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/audit-trail".length());
                }
                UUID documentId = UUID.fromString(idPart);
                List<DocumentDomainService.DocumentAccessLog> logs = domain.getDocumentAuditTrail(actor, documentId);
                return json(200, accessLogListNode(logs), correlationId);
            }

            // POST /api/v1/documents/{id}/access-grants — STM-06-US-012
            if ("POST".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/access-grants")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/access-grants".length());
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.GrantDocumentAccessRequest req = new DocumentDomainService.GrantDocumentAccessRequest();
                String granteeIdText = text(json, "granteeId");
                if (granteeIdText != null && !granteeIdText.trim().isEmpty()) {
                    req.granteeId = UUID.fromString(granteeIdText.trim());
                }
                req.granteeType = text(json, "granteeType");
                req.purpose = text(json, "purpose");
                DocumentDomainService.DocumentLink link = domain.grantDocumentAccess(actor, documentId, req);
                return json(201, documentLinkNode(link), correlationId);
            }

            // DELETE /api/v1/documents/{id}/access-grants/{granteeId} — STM-06-US-012 revoke
            if ("DELETE".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.contains("/access-grants/")) {
                String rest = normalized.substring("/api/v1/documents/".length());
                int idx = rest.indexOf("/access-grants/");
                UUID documentId = UUID.fromString(rest.substring(0, idx));
                UUID granteeId = UUID.fromString(rest.substring(idx + "/access-grants/".length()));
                String granteeType = text(json, "granteeType");
                if (granteeType == null) {
                    granteeType = header(headers, "x-grantee-type");
                }
                DocumentDomainService.DocumentLink link = domain.revokeDocumentAccess(actor, documentId, granteeId, granteeType);
                return json(200, documentLinkNode(link), correlationId);
            }

            // GET /api/v1/documents/{id}/access-grants — STM-06-US-012
            if ("GET".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/access-grants")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/access-grants".length());
                UUID documentId = UUID.fromString(idPart);
                List<DocumentDomainService.DocumentLink> links = domain.listDocumentAccessGrants(actor, documentId);
                return json(200, documentLinkListNode(links), correlationId);
            }

            // POST /api/v1/documents/{id}/sharing-consent — STM-06-US-013
            if ("POST".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/sharing-consent")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/sharing-consent".length());
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.SharingConsentRequest req = new DocumentDomainService.SharingConsentRequest();
                req.purpose = text(json, "purpose");
                DocumentDomainService.DocumentLink link = domain.recordSharingConsent(actor, documentId, req);
                return json(201, documentLinkNode(link), correlationId);
            }

            // DELETE /api/v1/documents/{id}/sharing-consent — STM-06-US-013 withdraw
            if ("DELETE".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/sharing-consent")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/sharing-consent".length());
                UUID documentId = UUID.fromString(idPart);
                String purpose = text(json, "purpose");
                if (purpose == null) {
                    purpose = header(headers, "x-consent-purpose");
                }
                DocumentDomainService.DocumentLink link = domain.withdrawSharingConsent(actor, documentId, purpose);
                return json(200, documentLinkNode(link), correlationId);
            }

            // GET /api/v1/documents/{id}/sharing-consent — STM-06-US-013
            if ("GET".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/sharing-consent")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/sharing-consent".length());
                UUID documentId = UUID.fromString(idPart);
                List<DocumentDomainService.DocumentLink> links = domain.listSharingConsents(actor, documentId);
                return json(200, documentLinkListNode(links), correlationId);
            }

            // POST /api/v1/documents/{id}/signed-url — STM-06-EN-003 issue signed download URL
            if ("POST".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/signed-url")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/signed-url".length());
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.SignedDownloadUrl signed = domain.issueSignedDownloadUrl(actor, documentId);
                return json(200, signedDownloadNode(signed), correlationId);
            }

            // GET /api/v1/documents/{id}/security-scans — STM-06-EN-003
            if ("GET".equals(method) && normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/security-scans")) {
                String idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/security-scans".length());
                UUID documentId = UUID.fromString(idPart);
                List<DocumentDomainService.DocumentLink> links = domain.listSecurityScans(actor, documentId);
                return json(200, documentLinkListNode(links), correlationId);
            }

            // GET /api/v1/documents/download?token= — STM-06-EN-003 resolve signed download
            if ("GET".equals(method) && "/api/v1/documents/download".equals(normalized)) {
                String token = header(headers, "x-query-token");
                if (token == null || token.trim().isEmpty()) {
                    token = text(json, "token");
                }
                DocumentDomainService.SignedDownloadUrl resolved = domain.resolveSignedDownload(actor, token);
                return json(200, signedDownloadNode(resolved), correlationId);
            }

            // GET /api/v1/documents/{id}/retention (or /api/v1/documents/submissions/{id}/retention) - Get Retention & Expiry Info (STM-06-US-006)
            if ("GET".equals(method) && ((normalized.startsWith("/api/v1/documents/submissions/") && normalized.endsWith("/retention"))
                    || (normalized.startsWith("/api/v1/documents/") && normalized.endsWith("/retention")))) {
                String idPart;
                if (normalized.startsWith("/api/v1/documents/submissions/")) {
                    idPart = normalized.substring("/api/v1/documents/submissions/".length(), normalized.length() - "/retention".length());
                } else {
                    idPart = normalized.substring("/api/v1/documents/".length(), normalized.length() - "/retention".length());
                }
                UUID documentId = UUID.fromString(idPart);
                DocumentDomainService.RetentionInfo info = domain.getRetentionInfo(actor, documentId);
                return json(200, retentionInfoNode(info), correlationId);
            }

            return problem(404, "ROUTE_NOT_FOUND", "No route found for " + method + " " + path, correlationId, path);
        } catch (DocumentException e) {
            return problem(e.getStatusCode(), e.getCode(), e.getMessage(), correlationId, path);
        } catch (IllegalArgumentException e) {
            logger.warn("Document request rejected due to invalid argument: {}", e.getMessage());
            return problem(422, "INVALID_ARGUMENT", e.getMessage(), correlationId, path);
        } catch (IllegalStateException e) {
            logger.warn("Document request conflict state: {}", e.getMessage());
            return problem(409, "STATE_CONFLICT", e.getMessage(), correlationId, path);
        } catch (Exception e) {
            logger.error("Unhandled document request failure", e);
            return problem(500, "INTERNAL_ERROR", "The document service could not complete the request", correlationId, path);
        } finally {
            if (tracker != null) {
                tracker.close();
            }
            com.campx.logger.context.LogContext.clear();
        }
    }

    private ObjectNode documentTypeNode(DocumentType dt) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", dt.id.toString());
        node.put("tenantId", dt.tenantId.toString());
        node.put("code", dt.code);
        node.put("name", dt.name);
        node.put("category", dt.category);
        node.put("module", dt.module);
        node.put("defaultClassification", dt.defaultClassification);
        node.put("requiresSignature", dt.requiresSignature);
        node.put("retentionPolicyCode", dt.retentionPolicyCode);
        node.put("isActive", dt.isActive);
        node.put("createdAt", dt.createdAt != null ? dt.createdAt.toString() : null);
        node.put("updatedAt", dt.updatedAt != null ? dt.updatedAt.toString() : null);
        node.put("createdBy", dt.createdBy != null ? dt.createdBy.toString() : null);
        node.put("updatedBy", dt.updatedBy != null ? dt.updatedBy.toString() : null);
        node.put("rowVersion", dt.rowVersion);
        return node;
    }

    private ObjectNode documentTypeListNode(List<DocumentType> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (DocumentType dt : list) {
            arr.add(documentTypeNode(dt));
        }
        node.put("total", list.size());
        return node;
    }

    private ObjectNode stageRequirementNode(StageDocumentRequirement req) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", req.id.toString());
        node.put("tenantId", req.tenantId.toString());
        node.put("programId", req.programId != null ? req.programId.toString() : null);
        node.put("programCode", req.programCode);
        node.put("programName", req.programName);
        node.put("academicStage", req.academicStage);
        node.put("documentTypeId", req.documentTypeId.toString());
        node.put("documentTypeCode", req.documentTypeCode);
        node.put("documentTypeName", req.documentTypeName);
        node.put("isMandatory", req.isMandatory);
        node.put("minCount", req.minCount);
        if (req.maxCount != null) {
            node.put("maxCount", req.maxCount);
        } else {
            node.putNull("maxCount");
        }
        if (req.submissionDeadlineDays != null) {
            node.put("submissionDeadlineDays", req.submissionDeadlineDays);
        } else {
            node.putNull("submissionDeadlineDays");
        }
        node.put("description", req.description);
        node.put("isActive", req.isActive);
        node.put("createdAt", req.createdAt != null ? req.createdAt.toString() : null);
        node.put("updatedAt", req.updatedAt != null ? req.updatedAt.toString() : null);
        node.put("createdBy", req.createdBy != null ? req.createdBy.toString() : null);
        node.put("updatedBy", req.updatedBy != null ? req.updatedBy.toString() : null);
        node.put("rowVersion", req.rowVersion);
        return node;
    }

    private ObjectNode stageRequirementListNode(List<StageDocumentRequirement> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (StageDocumentRequirement req : list) {
            arr.add(stageRequirementNode(req));
        }
        node.put("total", list.size());
        return node;
    }

    private ObjectNode studentDocumentNode(StudentDocument doc) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", doc.id != null ? doc.id.toString() : null);
        node.put("tenantId", doc.tenantId != null ? doc.tenantId.toString() : null);
        node.put("collegeId", doc.collegeId != null ? doc.collegeId.toString() : null);
        node.put("documentTypeId", doc.documentTypeId != null ? doc.documentTypeId.toString() : null);
        node.put("documentTypeCode", doc.documentTypeCode);
        node.put("documentTypeName", doc.documentTypeName);
        node.put("title", doc.title);
        node.put("classification", doc.classification);
        node.put("status", doc.status);
        node.put("ownerId", doc.ownerId != null ? doc.ownerId.toString() : null);
        node.put("storageBucket", doc.storageBucket);
        node.put("storagePath", doc.storagePath);
        node.put("mimeType", doc.mimeType);
        node.put("sizeBytes", doc.sizeBytes);
        node.put("checksumSha256", doc.checksumSha256);
        node.put("currentVersionNo", doc.currentVersionNo);
        node.put("createdAt", doc.createdAt != null ? doc.createdAt.toString() : null);
        node.put("updatedAt", doc.updatedAt != null ? doc.updatedAt.toString() : null);
        node.put("createdBy", doc.createdBy != null ? doc.createdBy.toString() : null);
        node.put("updatedBy", doc.updatedBy != null ? doc.updatedBy.toString() : null);
        node.put("rowVersion", doc.rowVersion);
        node.put("latestVerificationResult", doc.latestVerificationResult);
        node.put("latestVerificationCode", doc.latestVerificationCode);
        node.put("latestVerifiedAt", doc.latestVerifiedAt != null ? doc.latestVerifiedAt.toString() : null);
        node.put("expiresAt", doc.expiresAt != null ? doc.expiresAt.toString() : null);
        node.put("retentionPolicyCode", doc.retentionPolicyCode);
        return node;
    }

    private ObjectNode studentDocumentListNode(List<StudentDocument> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (StudentDocument doc : list) {
            arr.add(studentDocumentNode(doc));
        }
        node.put("total", list.size());
        return node;
    }

    private ObjectNode studentChecklistNode(List<StudentChecklistItem> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (StudentChecklistItem item : list) {
            ObjectNode itemNode = mapper.createObjectNode();
            itemNode.set("requirement", stageRequirementNode(item.requirement));
            itemNode.put("status", item.status);
            ArrayNode docsArr = itemNode.putArray("submittedDocuments");
            for (StudentDocument d : item.submittedDocuments) {
                docsArr.add(studentDocumentNode(d));
            }
            arr.add(itemNode);
        }
        node.put("total", list.size());
        return node;
    }

    private ObjectNode verificationQueueNode(DocumentDomainService.VerificationQueueItem item) {
        ObjectNode node = mapper.createObjectNode();
        node.put("documentId", item.documentId != null ? item.documentId.toString() : null);
        node.put("tenantId", item.tenantId != null ? item.tenantId.toString() : null);
        node.put("collegeId", item.collegeId != null ? item.collegeId.toString() : null);
        node.put("studentId", item.studentId != null ? item.studentId.toString() : null);
        node.put("studentName", item.studentName);
        node.put("documentTypeId", item.documentTypeId != null ? item.documentTypeId.toString() : null);
        node.put("documentTypeCode", item.documentTypeCode);
        node.put("documentTypeName", item.documentTypeName);
        node.put("title", item.title);
        node.put("classification", item.classification);
        node.put("status", item.status);
        node.put("mimeType", item.mimeType);
        node.put("sizeBytes", item.sizeBytes);
        node.put("checksumSha256", item.checksumSha256);
        node.put("storagePath", item.storagePath);
        node.put("currentVersionNo", item.currentVersionNo);
        node.put("submittedAt", item.submittedAt != null ? item.submittedAt.toString() : null);
        node.put("latestVerificationResult", item.latestVerificationResult);
        node.put("latestVerificationCode", item.latestVerificationCode);
        node.put("latestVerifiedAt", item.latestVerifiedAt != null ? item.latestVerifiedAt.toString() : null);
        return node;
    }

    private ObjectNode verificationQueueListNode(List<DocumentDomainService.VerificationQueueItem> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (DocumentDomainService.VerificationQueueItem item : list) {
            arr.add(verificationQueueNode(item));
        }
        node.put("total", list.size());
        return node;
    }

    private ObjectNode verificationDecisionNode(DocumentDomainService.DocumentVerificationRecord record) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", record.id.toString());
        node.put("tenantId", record.tenantId.toString());
        node.put("documentId", record.documentId.toString());
        node.put("entityType", record.entityType);
        node.put("entityId", record.entityId.toString());
        node.put("verificationCode", record.verificationCode);
        node.put("result", record.result);
        node.put("notes", record.notes);
        node.put("verifiedAt", record.verifiedAt != null ? record.verifiedAt.toString() : null);
        node.put("createdBy", record.createdBy != null ? record.createdBy.toString() : null);
        return node;
    }

    private Response json(int status, ObjectNode node, String correlationId) {
        node.put("correlationId", correlationId == null ? "" : correlationId);
        return new Response(status, node.toString());
    }

    private Response problem(int status, String code, String message, String correlationId, String path) {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp", System.currentTimeMillis());
        node.put("status", status);
        String errTitle = statusTitle(status);
        node.put("error", errTitle);
        node.put("title", errTitle);
        node.put("code", code);
        node.put("errorCode", code);
        node.put("message", message != null ? message : errTitle);
        node.put("path", path != null ? path : "");
        node.put("traceId", correlationId == null ? "" : correlationId);
        node.put("correlationId", correlationId == null ? "" : correlationId);
        return new Response(status, node.toString());
    }

    private static String statusTitle(int status) {
        switch (status) {
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return status >= 500 ? "Internal Server Error" : "Error";
        }
    }

    private ObjectNode accessLogNode(DocumentDomainService.DocumentAccessLog log) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", log.id != null ? log.id.toString() : null);
        node.put("tenantId", log.tenantId != null ? log.tenantId.toString() : null);
        node.put("documentId", log.documentId != null ? log.documentId.toString() : null);
        node.put("channel", log.channel);
        node.put("status", log.status);
        node.put("clientIp", log.clientIp);
        node.put("deliveredAt", log.deliveredAt != null ? log.deliveredAt.toString() : null);
        node.put("createdAt", log.createdAt != null ? log.createdAt.toString() : null);
        node.put("createdBy", log.createdBy != null ? log.createdBy.toString() : null);
        return node;
    }

    private ObjectNode accessLogListNode(List<DocumentDomainService.DocumentAccessLog> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (DocumentDomainService.DocumentAccessLog item : list) {
            arr.add(accessLogNode(item));
        }
        node.put("total", list.size());
        return node;
    }

    private ObjectNode retentionInfoNode(DocumentDomainService.RetentionInfo info) {
        ObjectNode node = mapper.createObjectNode();
        node.put("retentionPolicyCode", info.retentionPolicyCode);
        node.put("retentionCutoff", info.retentionCutoff != null ? info.retentionCutoff.toString() : null);
        node.put("isExpired", info.isExpired);
        node.put("isExpiringSoon", info.isExpiringSoon);
        node.put("expiresAt", info.expiresAt != null ? info.expiresAt.toString() : null);
        return node;
    }

    private ObjectNode signedDownloadNode(DocumentDomainService.SignedDownloadUrl signed) {
        ObjectNode node = mapper.createObjectNode();
        node.put("documentId", signed.documentId != null ? signed.documentId.toString() : null);
        node.put("storageBucket", signed.storageBucket);
        node.put("storagePath", signed.storagePath);
        node.put("mimeType", signed.mimeType);
        node.put("sizeBytes", signed.sizeBytes);
        node.put("token", signed.token);
        node.put("expiresAt", signed.expiresAt != null ? signed.expiresAt.toString() : null);
        node.put("downloadPath", signed.downloadPath);
        node.put("scanStatus", signed.scanStatus);
        node.put("malwareLiveEndpoint", signed.malwareLiveEndpoint);
        return node;
    }

    private ObjectNode documentLinkNode(DocumentDomainService.DocumentLink link) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", link.id != null ? link.id.toString() : null);
        node.put("tenantId", link.tenantId != null ? link.tenantId.toString() : null);
        node.put("documentId", link.documentId != null ? link.documentId.toString() : null);
        node.put("entityType", link.entityType);
        node.put("entityId", link.entityId != null ? link.entityId.toString() : null);
        node.put("relation", link.relation);
        node.put("createdAt", link.createdAt != null ? link.createdAt.toString() : null);
        node.put("createdBy", link.createdBy != null ? link.createdBy.toString() : null);
        return node;
    }

    private ObjectNode documentLinkListNode(List<DocumentDomainService.DocumentLink> list) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = node.putArray("items");
        for (DocumentDomainService.DocumentLink item : list) {
            arr.add(documentLinkNode(item));
        }
        node.put("total", list.size());
        return node;
    }

    private Actor actor(Map<String, String> headers) {
        String tenant = header(headers, "x-tenant-id");
        String user = header(headers, "x-user-id");
        String role = header(headers, "x-user-role");
        String email = header(headers, "x-user-email");
        String correlationId = header(headers, "x-correlation-id");
        if (tenant == null || user == null) {
            throw new DocumentException(401, "IDENTITY_REQUIRED", "Tenant and user identity are required in request headers");
        }
        return new Actor(UUID.fromString(tenant), UUID.fromString(user), role == null ? "STUDENT" : role, email, correlationId);
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        String value = headers.get(name);
        return value == null ? headers.get(name.toLowerCase()) : value;
    }

    private static String text(JsonNode json, String field) {
        if (json == null || json.path(field).isMissingNode() || json.path(field).isNull()) {
            return null;
        }
        String value = json.path(field).asText(null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static final class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
