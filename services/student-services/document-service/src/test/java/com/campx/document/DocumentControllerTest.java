package com.campx.document;

import com.campx.document.DocumentDomainService.ConfigureDocumentTypeRequest;
import com.campx.document.DocumentDomainService.DocumentType;
import com.campx.document.intelligence.DocumentIntelligenceService;
import com.campx.document.persistence.InMemoryDocumentPersistence;
import com.campx.document.security.GatewayHmacVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * HTTP controller tests for STM-06 routes.
 */
public class DocumentControllerTest {

    private DocumentController controller;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID adminId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @Before
    public void setUp() {
        InMemoryDocumentPersistence persistence = new InMemoryDocumentPersistence();
        DocumentIntelligenceService intelligenceService = new DocumentIntelligenceService();
        DocumentDomainService domain = new DocumentDomainService(persistence, intelligenceService);
        controller = new DocumentController(domain, new GatewayHmacVerifier(null));
    }

    private Map<String, String> adminHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-tenant-id", tenantId.toString());
        headers.put("x-user-id", adminId.toString());
        headers.put("x-user-role", "COLLEGE_ADMIN");
        headers.put("x-user-email", "admin@campx.edu");
        headers.put("x-correlation-id", "test-corr-123");
        return headers;
    }

    private Map<String, String> studentHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-tenant-id", tenantId.toString());
        headers.put("x-user-id", studentId.toString());
        headers.put("x-user-role", "STUDENT");
        headers.put("x-user-email", "student@campx.edu");
        headers.put("x-correlation-id", "test-corr-456");
        return headers;
    }

    @Test
    public void healthCheckReturns200() throws Exception {
        DocumentController.Response resp = controller.handle("GET", "/health", null, null);
        assertEquals(200, resp.status);
        JsonNode json = mapper.readTree(resp.body);
        assertEquals("UP", json.get("status").asText());
        assertEquals("document-service", json.get("service").asText());
    }

    @Test
    public void postDocumentTypesConfiguresNewType() throws Exception {
        String payload = "{\"code\":\"DOC-PASSPORT\",\"name\":\"Passport Copy\",\"category\":\"IDENTIFICATION\",\"defaultClassification\":\"RESTRICTED\",\"requiresSignature\":false,\"retentionPolicyCode\":\"RET_10_YEARS\"}";
        DocumentController.Response resp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), payload);
        assertEquals(201, resp.status);

        JsonNode json = mapper.readTree(resp.body);
        assertNotNull(json.get("id").asText());
        assertEquals("DOC-PASSPORT", json.get("code").asText());
        assertEquals("Passport Copy", json.get("name").asText());
        assertEquals("IDENTIFICATION", json.get("category").asText());
        assertEquals("RESTRICTED", json.get("defaultClassification").asText());
        assertEquals(1, json.get("rowVersion").asInt());
    }

    @Test
    public void duplicateDocumentTypeCodeReturns409() throws Exception {
        String payload = "{\"code\":\"DOC-DUPLICATE\",\"name\":\"Original\",\"category\":\"GENERAL\"}";
        DocumentController.Response resp1 = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), payload);
        assertEquals(201, resp1.status);

        DocumentController.Response resp2 = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), payload);
        assertEquals(409, resp2.status);
        JsonNode json = mapper.readTree(resp2.body);
        assertEquals("DUPLICATE_DOCUMENT_TYPE_CODE", json.get("code").asText());
    }

    @Test
    public void studentPostingDocumentTypeReturns403() throws Exception {
        String payload = "{\"code\":\"DOC-ILLEGAL\",\"name\":\"Illegal\",\"category\":\"GENERAL\"}";
        DocumentController.Response resp = controller.handle("POST", "/api/v1/documents/types", studentHeaders(), payload);
        assertEquals(403, resp.status);
        JsonNode json = mapper.readTree(resp.body);
        assertEquals("FORBIDDEN", json.get("code").asText());
    }

    @Test
    public void getDocumentTypesListsConfiguredTypes() throws Exception {
        String payload = "{\"code\":\"DOC-LIST-1\",\"name\":\"List Doc 1\",\"category\":\"ACADEMIC\"}";
        controller.handle("POST", "/api/v1/documents/types", adminHeaders(), payload);

        DocumentController.Response resp = controller.handle("GET", "/api/v1/documents/types", adminHeaders(), null);
        assertEquals(200, resp.status);
        JsonNode json = mapper.readTree(resp.body);
        assertTrue(json.get("total").asInt() >= 1);
        assertTrue(json.get("items").isArray());
    }

    @Test
    public void putDocumentTypeUpdatesProperties() throws Exception {
        String payload = "{\"code\":\"DOC-EDIT\",\"name\":\"Initial Name\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response createResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), payload);
        assertEquals(201, createResp.status);
        String id = mapper.readTree(createResp.body).get("id").asText();

        String updatePayload = "{\"name\":\"Updated Name\",\"category\":\"FINANCIAL\",\"defaultClassification\":\"CONFIDENTIAL\",\"isActive\":true}";
        DocumentController.Response updateResp = controller.handle("PUT", "/api/v1/documents/types/" + id, adminHeaders(), updatePayload);
        assertEquals(200, updateResp.status);
        JsonNode updatedJson = mapper.readTree(updateResp.body);
        assertEquals("Updated Name", updatedJson.get("name").asText());
        assertEquals("FINANCIAL", updatedJson.get("category").asText());
        assertEquals("CONFIDENTIAL", updatedJson.get("defaultClassification").asText());
        assertEquals(2, updatedJson.get("rowVersion").asInt());
    }

    @Test
    public void missingTenantIdentityReturns401() throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-user-id", adminId.toString());
        DocumentController.Response resp = controller.handle("POST", "/api/v1/documents/types", headers, "{}");
        assertEquals(401, resp.status);
    }

    // ==========================================
    // STM-06-US-002: Requirements HTTP Endpoints
    // ==========================================

    @Test
    public void postRequirementEndpointReturns201AndProblemOnDuplicate() throws Exception {
        String dtPayload = "{\"code\":\"DOC-REQ-TEST\",\"name\":\"Requirement Test Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        assertEquals(201, dtResp.status);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String reqPayload = "{\"academicStage\":\"ADMISSION\",\"documentTypeId\":\"" + dtId + "\",\"isMandatory\":true,\"minCount\":1,\"maxCount\":2,\"submissionDeadlineDays\":10}";
        DocumentController.Response resp1 = controller.handle("POST", "/api/v1/documents/requirements", adminHeaders(), reqPayload);
        assertEquals(201, resp1.status);
        JsonNode json1 = mapper.readTree(resp1.body);
        assertNotNull(json1.get("id").asText());
        assertEquals("ADMISSION", json1.get("academicStage").asText());
        assertEquals("DOC-REQ-TEST", json1.get("documentTypeCode").asText());
        assertTrue(json1.get("isMandatory").asBoolean());
        assertEquals(1, json1.get("minCount").asInt());
        assertEquals(2, json1.get("maxCount").asInt());
        assertEquals(10, json1.get("submissionDeadlineDays").asInt());

        // Duplicate POST returns 409
        DocumentController.Response resp2 = controller.handle("POST", "/api/v1/documents/requirements", adminHeaders(), reqPayload);
        assertEquals(409, resp2.status);
        JsonNode json2 = mapper.readTree(resp2.body);
        assertEquals("DUPLICATE_STAGE_REQUIREMENT", json2.get("code").asText());
    }

    @Test
    public void getRequirementsEndpointListsAndFilters() throws Exception {
        String dtPayload = "{\"code\":\"DOC-REQ-LIST\",\"name\":\"Req List Doc\",\"category\":\"IDENTIFICATION\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String reqPayload = "{\"academicStage\":\"ENROLLMENT\",\"documentTypeId\":\"" + dtId + "\",\"isMandatory\":false}";
        controller.handle("POST", "/api/v1/documents/requirements", adminHeaders(), reqPayload);

        Map<String, String> headers = adminHeaders();
        headers.put("x-query-stage", "ENROLLMENT");
        DocumentController.Response listResp = controller.handle("GET", "/api/v1/documents/requirements", headers, null);
        assertEquals(200, listResp.status);
        JsonNode json = mapper.readTree(listResp.body);
        assertTrue(json.get("total").asInt() >= 1);
        assertTrue(json.get("items").isArray());
    }

    @Test
    public void putRequirementEndpointUpdatesProperties() throws Exception {
        String dtPayload = "{\"code\":\"DOC-REQ-UPDATE\",\"name\":\"Update Req Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String reqPayload = "{\"academicStage\":\"EXAMINATION\",\"documentTypeId\":\"" + dtId + "\",\"isMandatory\":true}";
        DocumentController.Response createResp = controller.handle("POST", "/api/v1/documents/requirements", adminHeaders(), reqPayload);
        String reqId = mapper.readTree(createResp.body).get("id").asText();

        String updatePayload = "{\"isMandatory\":false,\"minCount\":2,\"maxCount\":5,\"description\":\"Updated rule\"}";
        DocumentController.Response updateResp = controller.handle("PUT", "/api/v1/documents/requirements/" + reqId, adminHeaders(), updatePayload);
        assertEquals(200, updateResp.status);
        JsonNode updatedJson = mapper.readTree(updateResp.body);
        assertFalse(updatedJson.get("isMandatory").asBoolean());
        assertEquals(2, updatedJson.get("minCount").asInt());
        assertEquals(5, updatedJson.get("maxCount").asInt());
        assertEquals("Updated rule", updatedJson.get("description").asText());
    }

    @Test
    public void deleteRequirementEndpointReturns200() throws Exception {
        String dtPayload = "{\"code\":\"DOC-REQ-DEL\",\"name\":\"Delete Req Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String reqPayload = "{\"academicStage\":\"GRADUATION\",\"documentTypeId\":\"" + dtId + "\"}";
        DocumentController.Response createResp = controller.handle("POST", "/api/v1/documents/requirements", adminHeaders(), reqPayload);
        String reqId = mapper.readTree(createResp.body).get("id").asText();

        DocumentController.Response delResp = controller.handle("DELETE", "/api/v1/documents/requirements/" + reqId, adminHeaders(), null);
        assertEquals(200, delResp.status);
        JsonNode delJson = mapper.readTree(delResp.body);
        assertTrue(delJson.get("deleted").asBoolean());
    }

    @Test
    public void studentPostingRequirementReturns403() throws Exception {
        String payload = "{\"academicStage\":\"ADMISSION\",\"documentTypeId\":\"" + UUID.randomUUID() + "\"}";
        DocumentController.Response resp = controller.handle("POST", "/api/v1/documents/requirements", studentHeaders(), payload);
        assertEquals(403, resp.status);
    }

    // ==========================================
    // STM-06-US-003: Student Upload Endpoints
    // ==========================================

    @Test
    public void studentCanUploadDocumentViaSubmissionsEndpoint() throws Exception {
        // Configure doc type first as admin
        String dtPayload = "{\"code\":\"DOC-SUBMIT-1\",\"name\":\"Submission Test Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        assertEquals(201, dtResp.status);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        // Student uploads document
        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"12th Grade Marksheet\",\"mimeType\":\"application/pdf\",\"sizeBytes\":1048576,\"fileName\":\"marksheet.pdf\",\"changeNote\":\"Original copy\"}";
        DocumentController.Response uploadResp = controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);
        assertEquals(201, uploadResp.status);

        JsonNode json = mapper.readTree(uploadResp.body);
        assertNotNull(json.get("id").asText());
        assertEquals(tenantId.toString(), json.get("tenantId").asText());
        assertEquals(studentId.toString(), json.get("ownerId").asText());
        assertEquals("12th Grade Marksheet", json.get("title").asText());
        assertEquals("SUBMITTED", json.get("status").asText());
        assertEquals("application/pdf", json.get("mimeType").asText());
        assertEquals(1, json.get("currentVersionNo").asInt());
    }

    @Test
    public void studentCanGetSubmissionsAndChecklist() throws Exception {
        // Configure type and requirement
        String dtPayload = "{\"code\":\"DOC-CHK-TEST\",\"name\":\"Checklist Target Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String reqPayload = "{\"academicStage\":\"ADMISSION\",\"documentTypeId\":\"" + dtId + "\",\"isMandatory\":true}";
        controller.handle("POST", "/api/v1/documents/requirements", adminHeaders(), reqPayload);

        // Upload
        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Attested Certificate\",\"mimeType\":\"image/png\",\"sizeBytes\":512000}";
        controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);

        // Get submissions list
        DocumentController.Response listResp = controller.handle("GET", "/api/v1/documents/submissions", studentHeaders(), null);
        assertEquals(200, listResp.status);
        JsonNode listJson = mapper.readTree(listResp.body);
        assertTrue(listJson.get("total").asInt() >= 1);

        // Get checklist
        Map<String, String> chkHeaders = studentHeaders();
        chkHeaders.put("x-query-stage", "ADMISSION");
        DocumentController.Response chkResp = controller.handle("GET", "/api/v1/documents/submissions/checklist", chkHeaders, null);
        assertEquals(200, chkResp.status);
        JsonNode chkJson = mapper.readTree(chkResp.body);
        assertTrue(chkJson.get("total").asInt() >= 1);
        assertEquals("SUBMITTED", chkJson.get("items").get(0).get("status").asText());
    }

    @Test
    public void invalidMimeTypeUploadReturns422() throws Exception {
        String dtPayload = "{\"code\":\"DOC-BAD-MIME\",\"name\":\"Mime Test Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Bad Binary\",\"mimeType\":\"application/x-msdownload\",\"sizeBytes\":1000}";
        DocumentController.Response uploadResp = controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);
        assertEquals(422, uploadResp.status);
        JsonNode json = mapper.readTree(uploadResp.body);
        assertEquals("UNSUPPORTED_MIME_TYPE", json.get("code").asText());
    }

    // ==========================================
    // STM-06-US-004: Verification Endpoints
    // ==========================================

    @Test
    public void officerCanGetVerificationQueueViaEndpoint() throws Exception {
        String dtPayload = "{\"code\":\"DOC-QUEUE-API\",\"name\":\"Queue API Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Doc for Queue API\",\"mimeType\":\"application/pdf\",\"sizeBytes\":2048}";
        controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);

        DocumentController.Response queueResp = controller.handle("GET", "/api/v1/documents/verifications", adminHeaders(), null);
        assertEquals(200, queueResp.status);
        JsonNode qJson = mapper.readTree(queueResp.body);
        assertTrue(qJson.get("total").asInt() >= 1);
    }

    @Test
    public void officerCanApproveDocumentViaDecisionEndpoint() throws Exception {
        String dtPayload = "{\"code\":\"DOC-DEC-APP\",\"name\":\"Decision Approve Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Doc To Approve API\",\"mimeType\":\"application/pdf\",\"sizeBytes\":2048}";
        DocumentController.Response uploadResp = controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);
        String docId = mapper.readTree(uploadResp.body).get("id").asText();

        String decisionPayload = "{\"decision\":\"APPROVE\",\"verificationNotes\":\"Approved after inspection\"}";
        DocumentController.Response decResp = controller.handle("POST", "/api/v1/documents/verifications/" + docId + "/decision", adminHeaders(), decisionPayload);
        assertEquals(200, decResp.status);

        JsonNode decJson = mapper.readTree(decResp.body);
        assertEquals("VALID", decJson.get("result").asText());
        assertEquals(docId, decJson.get("documentId").asText());
    }

    @Test
    public void officerCanRejectDocumentViaDecisionEndpoint() throws Exception {
        String dtPayload = "{\"code\":\"DOC-DEC-REJ\",\"name\":\"Decision Reject Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Doc To Reject API\",\"mimeType\":\"application/pdf\",\"sizeBytes\":2048}";
        DocumentController.Response uploadResp = controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);
        String docId = mapper.readTree(uploadResp.body).get("id").asText();

        String decisionPayload = "{\"decision\":\"REJECT\",\"rejectionReason\":\"Blurry image scan\"}";
        DocumentController.Response decResp = controller.handle("POST", "/api/v1/documents/verifications/" + docId + "/decision", adminHeaders(), decisionPayload);
        assertEquals(200, decResp.status);

        JsonNode decJson = mapper.readTree(decResp.body);
        assertEquals("INVALID", decJson.get("result").asText());
        assertEquals("Blurry image scan", decJson.get("notes").asText());
    }

    @Test
    public void rejectWithoutReasonReturns422() throws Exception {
        String dtPayload = "{\"code\":\"DOC-DEC-ERR\",\"name\":\"Decision Err Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Doc To Fail Reject\",\"mimeType\":\"application/pdf\",\"sizeBytes\":2048}";
        DocumentController.Response uploadResp = controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);
        String docId = mapper.readTree(uploadResp.body).get("id").asText();

        String decisionPayload = "{\"decision\":\"REJECT\",\"rejectionReason\":\"\"}";
        DocumentController.Response decResp = controller.handle("POST", "/api/v1/documents/verifications/" + docId + "/decision", adminHeaders(), decisionPayload);
        assertEquals(422, decResp.status);
        JsonNode decJson = mapper.readTree(decResp.body);
        assertEquals("REJECTION_REASON_REQUIRED", decJson.get("code").asText());
    }

    @Test
    public void studentCallingDecisionReturns403() throws Exception {
        String decisionPayload = "{\"decision\":\"APPROVE\"}";
        DocumentController.Response decResp = controller.handle("POST", "/api/v1/documents/verifications/" + UUID.randomUUID() + "/decision", studentHeaders(), decisionPayload);
        assertEquals(403, decResp.status);
        JsonNode decJson = mapper.readTree(decResp.body);
        assertEquals("FORBIDDEN", decJson.get("code").asText());
    }

    @Test
    public void studentCanResubmitRejectedDocumentViaEndpoint() throws Exception {
        String dtPayload = "{\"code\":\"DOC-RESUB-API\",\"name\":\"Resubmit API Doc\",\"category\":\"ACADEMIC\"}";
        DocumentController.Response dtResp = controller.handle("POST", "/api/v1/documents/types", adminHeaders(), dtPayload);
        String dtId = mapper.readTree(dtResp.body).get("id").asText();

        String uploadPayload = "{\"documentTypeId\":\"" + dtId + "\",\"title\":\"Original Form\",\"mimeType\":\"application/pdf\",\"sizeBytes\":2048}";
        DocumentController.Response uploadResp = controller.handle("POST", "/api/v1/documents/submissions", studentHeaders(), uploadPayload);
        String docId = mapper.readTree(uploadResp.body).get("id").asText();

        // Reject it
        String decPayload = "{\"decision\":\"REJECT\",\"rejectionReason\":\"Page 2 was truncated\"}";
        controller.handle("POST", "/api/v1/documents/verifications/" + docId + "/decision", adminHeaders(), decPayload);

        // Resubmit v2
        String resubmitPayload = "{\"fileName\":\"form_complete.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":4096,\"checksumSha256\":\"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\",\"changeNote\":\"Added missing page 2\"}";
        DocumentController.Response resubmitResp = controller.handle("POST", "/api/v1/documents/submissions/" + docId + "/resubmit", studentHeaders(), resubmitPayload);
        assertEquals(200, resubmitResp.status);

        JsonNode resJson = mapper.readTree(resubmitResp.body);
        assertEquals(docId, resJson.get("id").asText());
        assertEquals("SUBMITTED", resJson.get("status").asText());
        assertEquals(2, resJson.get("currentVersionNo").asInt());
        assertEquals(4096, resJson.get("sizeBytes").asLong());

        // Check GET verification details
        DocumentController.Response verifResp = controller.handle("GET", "/api/v1/documents/submissions/" + docId + "/verification", studentHeaders(), "");
        assertEquals(200, verifResp.status);
        JsonNode vJson = mapper.readTree(verifResp.body);
        assertEquals("INVALID", vJson.get("result").asText());
        assertEquals("Page 2 was truncated", vJson.get("notes").asText());
    }
}

