package com.campx.admissions;

import com.campx.admissions.controller.ApplicationController;
import com.campx.admissions.model.dto.*;
import com.campx.admissions.repository.*;
import com.campx.admissions.service.ApplicationDomainService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.Assert.*;

public class ApplicationControllerTest {

    private ApplicationController controller;
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID applicantId;
    private UUID officerId;
    private UUID reviewerId;
    private UUID approverId;
    private UUID programId;

    private Map<String, String> officerHeaders;
    private Map<String, String> applicantHeaders;
    private Map<String, String> reviewerHeaders;
    private Map<String, String> approverHeaders;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ApplicationRepository appRepo = new InMemoryApplicationRepository();
        AdmissionIntakeRepository intakeRepo = new InMemoryAdmissionIntakeRepository();
        EligibilityRuleRepository ruleRepo = new InMemoryEligibilityRuleRepository();
        ApplicationDocumentRepository docRepo = new InMemoryApplicationDocumentRepository();
        ApplicationReviewRepository reviewRepo = new InMemoryApplicationReviewRepository();
        AdmissionDecisionRepository decisionRepo = new InMemoryAdmissionDecisionRepository();
        AdmissionOfferRepository offerRepo = new InMemoryAdmissionOfferRepository();
        AdmissionHandoverRepository handoverRepo = new InMemoryAdmissionHandoverRepository();
        StatusHistoryRepository historyRepo = new InMemoryStatusHistoryRepository();

        ApplicationDomainService domainService = new ApplicationDomainService(
                appRepo, intakeRepo, ruleRepo, docRepo, reviewRepo, decisionRepo, offerRepo, handoverRepo, historyRepo, null
        );

        controller = new ApplicationController(domainService, null); // bypass HMAC in direct controller tests

        tenantId = UUID.randomUUID();
        applicantId = UUID.randomUUID();
        officerId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        approverId = UUID.randomUUID();
        programId = UUID.randomUUID();

        officerHeaders = new HashMap<>();
        officerHeaders.put("x-tenant-id", tenantId.toString());
        officerHeaders.put("x-user-id", officerId.toString());
        officerHeaders.put("x-user-role", "ADMISSIONS_OFFICER");

        applicantHeaders = new HashMap<>();
        applicantHeaders.put("x-tenant-id", tenantId.toString());
        applicantHeaders.put("x-user-id", applicantId.toString());
        applicantHeaders.put("x-user-role", "APPLICANT");

        reviewerHeaders = new HashMap<>();
        reviewerHeaders.put("x-tenant-id", tenantId.toString());
        reviewerHeaders.put("x-user-id", reviewerId.toString());
        reviewerHeaders.put("x-user-role", "FACULTY_REVIEWER");

        approverHeaders = new HashMap<>();
        approverHeaders.put("x-tenant-id", tenantId.toString());
        approverHeaders.put("x-user-id", approverId.toString());
        approverHeaders.put("x-user-role", "ADMISSIONS_APPROVER");
    }

    @Test
    public void testHealthAndMetricsEndpoints() throws Exception {
        ApplicationController.HttpResponse healthResp = controller.handleRequest("GET", "/actuator/health", Collections.emptyMap(), "");
        assertEquals(200, healthResp.statusCode);
        JsonNode healthNode = objectMapper.readTree(healthResp.body);
        assertEquals("UP", healthNode.get("status").asText());
        assertEquals("admissions-service", healthNode.get("service").asText());

        ApplicationController.HttpResponse metricsResp = controller.handleRequest("GET", "/metrics", Collections.emptyMap(), "");
        assertEquals(200, metricsResp.statusCode);
        JsonNode metricsNode = objectMapper.readTree(metricsResp.body);
        assertEquals("admissions-service", metricsNode.get("service").asText());
        assertTrue(metricsNode.has("activeThreads"));
    }

    @Test
    public void testFullAdmissionsLifecycleThroughController() throws Exception {
        // 1. Configure Intake
        ConfigureIntakeRequest intakeReq = new ConfigureIntakeRequest();
        intakeReq.setIntakeCode("CSE-FALL-2026");
        intakeReq.setIntakeName("Computer Science Fall 2026");
        intakeReq.setCapacity(2);
        intakeReq.setApplicationWindowStart(OffsetDateTime.now().minusDays(1));
        intakeReq.setApplicationWindowEnd(OffsetDateTime.now().plusMonths(6));
        intakeReq.setFeeRequired(false);
        intakeReq.setFeeAmount(BigDecimal.ZERO);
        intakeReq.setCurrency("INR");

        ApplicationController.HttpResponse intakeResp = controller.handleRequest(
                "POST", "/api/v1/admissions/intakes", officerHeaders, objectMapper.writeValueAsString(intakeReq)
        );
        assertEquals(201, intakeResp.statusCode);
        JsonNode intakeNode = objectMapper.readTree(intakeResp.body);
        UUID intakeId = UUID.fromString(intakeNode.get("id").asText());

        // 2. Create Application Draft
        CreateApplicationRequest createReq = new CreateApplicationRequest();
        createReq.setIntakeId(intakeId);
        createReq.setApplicantData("{\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"dateOfBirth\":\"2004-05-10\",\"qualifyingPercentage\":88.5}");

        ApplicationController.HttpResponse createResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications", applicantHeaders, objectMapper.writeValueAsString(createReq)
        );
        assertEquals(201, createResp.statusCode);
        JsonNode appNode = objectMapper.readTree(createResp.body);
        UUID applicationId = UUID.fromString(appNode.get("id").asText());
        assertEquals("DRAFT", appNode.get("status").asText());

        // 3. Update Draft
        UpdateApplicationRequest updateReq = new UpdateApplicationRequest();
        updateReq.setApplicantData("{\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"dateOfBirth\":\"2004-05-10\",\"qualifyingPercentage\":88.5,\"phone\":\"+919876543210\"}");

        Map<String, String> updateHeaders = new HashMap<>(applicantHeaders);
        updateHeaders.put("if-match", "1");
        ApplicationController.HttpResponse updateResp = controller.handleRequest(
                "PATCH", "/api/v1/admissions/applications/" + applicationId, updateHeaders, objectMapper.writeValueAsString(updateReq)
        );
        assertEquals(200, updateResp.statusCode);

        // 4. Submit Application
        SubmitApplicationRequest submitReq = new SubmitApplicationRequest();
        submitReq.setDeclarationAccepted(true);
        ApplicationController.HttpResponse submitResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/submit", applicantHeaders, objectMapper.writeValueAsString(submitReq)
        );
        assertEquals(200, submitResp.statusCode);
        JsonNode submitNode = objectMapper.readTree(submitResp.body);
        assertEquals("ELIGIBLE", submitNode.get("status").asText());

        // 5. Attach Document
        AttachDocumentRequest docReq = new AttachDocumentRequest();
        docReq.setDocumentType("HIGH_SCHOOL_TRANSCRIPT");
        docReq.setStoragePath("s3://campx-docs/transcripts/alice-2026.pdf");
        docReq.setFileName("transcript.pdf");
        docReq.setMimeType("application/pdf");
        docReq.setFileSizeBytes(512000L);

        ApplicationController.HttpResponse docResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/documents", applicantHeaders, objectMapper.writeValueAsString(docReq)
        );
        assertEquals(201, docResp.statusCode);
        JsonNode docNode = objectMapper.readTree(docResp.body);
        UUID docId = UUID.fromString(docNode.get("id").asText());

        // 6. Verify Document (Staff)
        VerifyDocumentRequest verifyReq = new VerifyDocumentRequest();
        verifyReq.setStatus(com.campx.admissions.model.DocumentVerificationStatus.VERIFIED);

        ApplicationController.HttpResponse verifyResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/documents/" + docId + "/verify", officerHeaders, objectMapper.writeValueAsString(verifyReq)
        );
        assertEquals(200, verifyResp.statusCode);

        // 7. Assign Reviewer
        AssignReviewerRequest assignReq = new AssignReviewerRequest();
        assignReq.setReviewerId(reviewerId);
        ApplicationController.HttpResponse assignResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/assign", officerHeaders, objectMapper.writeValueAsString(assignReq)
        );
        assertEquals(200, assignResp.statusCode);

        // 8. Submit Review
        SubmitReviewRequest reviewReq = new SubmitReviewRequest();
        reviewReq.setRubricVersion("1.0.0");
        reviewReq.setRubricScores("{\"grades\":9.0,\"sop\":8.5}");
        reviewReq.setTotalScore(new BigDecimal("87.50"));
        reviewReq.setRecommendation(com.campx.admissions.model.ReviewRecommendation.RECOMMEND_OFFER);
        reviewReq.setApplicantFacingRemarks("Strong qualifications");

        ApplicationController.HttpResponse reviewResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/reviews", reviewerHeaders, objectMapper.writeValueAsString(reviewReq)
        );
        assertEquals(200, reviewResp.statusCode);

        // 9. Record Decision (Approver / Dean)
        RecordDecisionRequest decReq = new RecordDecisionRequest();
        decReq.setDecisionType(com.campx.admissions.model.DecisionType.OFFER_APPROVED);
        decReq.setDecisionNotes("Approved for admission offer");

        ApplicationController.HttpResponse decResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/decision", approverHeaders, objectMapper.writeValueAsString(decReq)
        );
        assertEquals(200, decResp.statusCode);

        // 10. Issue Offer
        IssueOfferRequest offerReq = new IssueOfferRequest();
        offerReq.setOfferTermsVersion("1.0.0");
        offerReq.setValidTo(OffsetDateTime.now().plusDays(14));

        ApplicationController.HttpResponse offerResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/offers", approverHeaders, objectMapper.writeValueAsString(offerReq)
        );
        assertEquals(201, offerResp.statusCode);

        // 11. Accept Offer (Applicant)
        AcceptOfferRequest acceptReq = new AcceptOfferRequest();
        acceptReq.setConfirmAcceptance(true);

        ApplicationController.HttpResponse acceptResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/offers/accept", applicantHeaders, objectMapper.writeValueAsString(acceptReq)
        );
        assertEquals(200, acceptResp.statusCode);

        // 12. Handover (Officer triggers STM-01 handover)
        Map<String, String> handoverHeaders = new HashMap<>(officerHeaders);
        handoverHeaders.put("idempotency-key", "handover-test-key-001");

        ApplicationController.HttpResponse handoverResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + applicationId + "/handover", handoverHeaders, ""
        );
        assertEquals(200, handoverResp.statusCode);
        JsonNode handoverNode = objectMapper.readTree(handoverResp.body);
        assertEquals("STUDENT_CREATED", handoverNode.get("enrolmentStatus").asText());
        assertNotNull(handoverNode.get("createdStudentId"));

        // 13. Check Conversion Funnel Report
        Map<String, String> funnelHeaders = new HashMap<>(officerHeaders);
        funnelHeaders.put("x-intake-id", intakeId.toString());

        ApplicationController.HttpResponse funnelResp = controller.handleRequest(
                "GET", "/api/v1/admissions/reports/funnel", funnelHeaders, ""
        );
        assertEquals(200, funnelResp.statusCode);
        JsonNode funnelNode = objectMapper.readTree(funnelResp.body);
        assertEquals(1, funnelNode.get("totalHandedOver").asInt());
    }

    @Test
    public void testMissingTenantHeaderReturnsSecurityViolation() {
        Map<String, String> noTenantHeaders = new HashMap<>();
        noTenantHeaders.put("x-user-id", UUID.randomUUID().toString());

        ApplicationController.HttpResponse resp = controller.handleRequest("GET", "/api/v1/admissions/applications/" + UUID.randomUUID(), noTenantHeaders, "");
        assertEquals(403, resp.statusCode);
        assertTrue(resp.body.contains("SECURITY_VIOLATION"));
    }

    @Test
    public void testIllegalDirectTransitionRejection() throws Exception {
        // Attempting to submit non-existent application returns 404
        ApplicationController.HttpResponse resp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + UUID.randomUUID() + "/submit", applicantHeaders, "{}"
        );
        assertEquals(404, resp.statusCode);
    }

    @Test
    public void testListApplicationsAndSubResourceEndpoints() throws Exception {
        // 1. Configure Intake
        ConfigureIntakeRequest intakeReq = new ConfigureIntakeRequest();
        intakeReq.setCollegeId(UUID.randomUUID());
        intakeReq.setProgramId(programId);
        intakeReq.setAcademicYearId(UUID.randomUUID());
        intakeReq.setIntakeName("Mechanical Engineering 2026");
        intakeReq.setIntakeCode("ME-2026-001");
        intakeReq.setApplicationWindowStart(OffsetDateTime.now().minusDays(1));
        intakeReq.setApplicationWindowEnd(OffsetDateTime.now().plusMonths(2));
        intakeReq.setCapacity(40);
        intakeReq.setFeeRequired(false);

        ApplicationController.HttpResponse intakeResp = controller.handleRequest(
                "POST", "/api/v1/admissions/intakes", officerHeaders, objectMapper.writeValueAsString(intakeReq)
        );
        assertEquals(201, intakeResp.statusCode);
        UUID intakeId = UUID.fromString(objectMapper.readTree(intakeResp.body).get("id").asText());

        // 2. Create Application
        CreateApplicationRequest appReq = new CreateApplicationRequest();
        appReq.setIntakeId(intakeId);
        appReq.setApplicantData("{\"fullName\":\"Listing Applicant\"}");

        ApplicationController.HttpResponse createResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications", applicantHeaders, objectMapper.writeValueAsString(appReq)
        );
        assertEquals(201, createResp.statusCode);
        UUID appId = UUID.fromString(objectMapper.readTree(createResp.body).get("id").asText());

        // 3. List Applications as Officer
        ApplicationController.HttpResponse listResp = controller.handleRequest(
                "GET", "/api/v1/admissions/applications", officerHeaders, ""
        );
        assertEquals(200, listResp.statusCode);
        JsonNode listNode = objectMapper.readTree(listResp.body);
        assertTrue(listNode.isArray());
        assertTrue(listNode.size() >= 1);

        // 4. Submit Application
        SubmitApplicationRequest subReq = new SubmitApplicationRequest();
        subReq.setDeclarationAccepted(true);
        subReq.setPrivacyConsentAccepted(true);
        ApplicationController.HttpResponse subResp = controller.handleRequest(
                "POST", "/api/v1/admissions/applications/" + appId + "/submit", applicantHeaders, objectMapper.writeValueAsString(subReq)
        );
        assertEquals(200, subResp.statusCode);

        // 5. Assign Reviewer and Submit Review
        AssignReviewerRequest assignReq = new AssignReviewerRequest();
        assignReq.setReviewerId(reviewerId);
        controller.handleRequest("POST", "/api/v1/admissions/applications/" + appId + "/assign", officerHeaders, objectMapper.writeValueAsString(assignReq));

        SubmitReviewRequest revReq = new SubmitReviewRequest();
        revReq.setTotalScore(new BigDecimal("88.50"));
        revReq.setRecommendation(com.campx.admissions.model.ReviewRecommendation.RECOMMEND_OFFER);
        revReq.setConfidentialComments("Exceptional listing test applicant");
        controller.handleRequest("POST", "/api/v1/admissions/applications/" + appId + "/reviews", reviewerHeaders, objectMapper.writeValueAsString(revReq));

        // 6. Query Reviews sub-resource
        ApplicationController.HttpResponse revResp = controller.handleRequest(
                "GET", "/api/v1/admissions/applications/" + appId + "/reviews", officerHeaders, ""
        );
        assertEquals(200, revResp.statusCode);
        JsonNode revArray = objectMapper.readTree(revResp.body);
        assertTrue(revArray.isArray());
        assertEquals(1, revArray.size());
        assertEquals("Exceptional listing test applicant", revArray.get(0).get("confidentialComments").asText());

        // 7. Record Decision
        RecordDecisionRequest decReq = new RecordDecisionRequest();
        decReq.setDecisionType(com.campx.admissions.model.DecisionType.OFFER_APPROVED);
        decReq.setDecisionNotes("Approved unconditionally");
        controller.handleRequest("POST", "/api/v1/admissions/applications/" + appId + "/decision", approverHeaders, objectMapper.writeValueAsString(decReq));

        ApplicationController.HttpResponse decResp = controller.handleRequest(
                "GET", "/api/v1/admissions/applications/" + appId + "/decision", officerHeaders, ""
        );
        assertEquals(200, decResp.statusCode);
        JsonNode decNode = objectMapper.readTree(decResp.body);
        assertEquals("OFFER_APPROVED", decNode.get("decisionType").asText());

        // 8. Issue Offer and Query Offer sub-resource
        IssueOfferRequest offerReq = new IssueOfferRequest();
        offerReq.setOfferTermsVersion("1.0.0");
        offerReq.setValidTo(OffsetDateTime.now().plusDays(21));
        controller.handleRequest("POST", "/api/v1/admissions/applications/" + appId + "/offers", approverHeaders, objectMapper.writeValueAsString(offerReq));

        ApplicationController.HttpResponse offResp = controller.handleRequest(
                "GET", "/api/v1/admissions/applications/" + appId + "/offer", applicantHeaders, ""
        );
        assertEquals(200, offResp.statusCode);
        JsonNode offNode = objectMapper.readTree(offResp.body);
        assertEquals("ISSUED", offNode.get("status").asText());

        // 9. Query History sub-resource
        ApplicationController.HttpResponse histResp = controller.handleRequest(
                "GET", "/api/v1/admissions/applications/" + appId + "/history", officerHeaders, ""
        );
        assertEquals(200, histResp.statusCode);
        JsonNode histArray = objectMapper.readTree(histResp.body);
        assertTrue(histArray.isArray());
        assertTrue(histArray.size() >= 2);
    }
}
