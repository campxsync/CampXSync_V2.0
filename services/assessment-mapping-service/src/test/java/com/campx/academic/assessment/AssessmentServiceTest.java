package com.campx.academic.assessment;

import com.campx.academic.assessment.exception.*;
import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.academic.assessment.service.AssessmentDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests verifying business rules across ACD-09 Assessment Mapping Service.
 */
public class AssessmentServiceTest {

    private AssessmentDomainService service;
    private static final String TENANT = "TENANT-001";
    private static final String USER_ADMIN = "admin-1";
    private static final String ROLE_ADMIN = "ACADEMIC_ADMIN";
    private static final String ROLE_FACULTY = "FACULTY";
    private static final String ROLE_HOD = "DEPT_HEAD";

    @Before
    public void setUp() {
        service = new AssessmentDomainService();
    }

    private CreateAssessmentRequest createValidRequest(String code) {
        CreateAssessmentRequest req = new CreateAssessmentRequest();
        req.assessmentCode = code;
        req.assessmentName = "Midterm and Continuous Evaluation for CS101";
        req.subjectId = "SUB-CS101";
        req.courseId = "COURSE-CS-BS";
        req.curriculumId = "CURR-2026-CS";
        req.academicYear = "2026-2027";
        req.termId = "TERM-1";
        req.assessmentType = "INTERNAL";
        req.totalMarks = 100.0;
        req.totalWeightage = 100.0;
        return req;
    }

    @Test
    public void testCreateAssessmentStructure_Success() {
        CreateAssessmentRequest req = createValidRequest("ASM-CS101-01");
        AssessmentStructure s = service.createAssessment(req, TENANT, USER_ADMIN, ROLE_ADMIN, "IDEMP-001", "TRACE-01");

        assertNotNull(s);
        assertNotNull(s.getId());
        assertEquals("ASM-CS101-01", s.getAssessmentCode());
        assertEquals(AssessmentStatus.DRAFT, s.getStatus());
        assertEquals(1, s.getCurrentVersion());
        assertEquals(0, s.getEffectiveVersion());
        assertEquals(100.0, s.getTotalMarks(), 0.001);
        assertEquals(100.0, s.getTotalWeightage(), 0.001);

        // Verify outbox event emitted
        assertEquals(1, service.getOutboxPublisher().getOutboxEvents().size());
        assertEquals("AssessmentStructureCreated", service.getOutboxPublisher().getOutboxEvents().get(0).getEventType());

        // Verify audit log
        List<AssessmentHistory> hist = service.getAuditAdapter().getHistoryByAssessment(s.getId());
        assertEquals(1, hist.size());
        assertEquals(AuditAction.CREATE, hist.get(0).getAction());
    }

    @Test(expected = AssessmentDuplicateException.class)
    public void testCreateAssessment_DuplicateCode_ThrowsConflict() {
        CreateAssessmentRequest req1 = createValidRequest("ASM-DUP-01");
        service.createAssessment(req1, TENANT, USER_ADMIN, ROLE_ADMIN, null, "TRACE-01");

        CreateAssessmentRequest req2 = createValidRequest("ASM-DUP-01");
        service.createAssessment(req2, TENANT, USER_ADMIN, ROLE_ADMIN, null, "TRACE-02");
    }

    @Test(expected = AssessmentValidationException.class)
    public void testCreateAssessment_InvalidSubject_ThrowsValidation() {
        CreateAssessmentRequest req = createValidRequest("ASM-BAD-SUB");
        req.subjectId = "INVALID-SUBJECT-XYZ";
        service.createAssessment(req, TENANT, USER_ADMIN, ROLE_ADMIN, null, "TRACE-01");
    }

    @Test
    public void testIdempotency_SameKeyReturnsCached() {
        CreateAssessmentRequest req = createValidRequest("ASM-IDEMP-01");
        AssessmentStructure first = service.createAssessment(req, TENANT, USER_ADMIN, ROLE_ADMIN, "IDEMP-KEY-99", "TRACE-01");
        AssessmentStructure second = service.createAssessment(req, TENANT, USER_ADMIN, ROLE_ADMIN, "IDEMP-KEY-99", "TRACE-02");

        assertEquals(first.getId(), second.getId());
    }

    @Test
    public void testAddComponentsAndValidatePassingMarks() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-COMP-01"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        // Component 1: Quiz (maxMarks: 20, passingMarks: 8, weightage: 20%)
        CreateComponentRequest c1 = new CreateComponentRequest();
        c1.componentCode = "QUIZ-1";
        c1.componentName = "Weekly Quiz 1";
        c1.componentType = "QUIZ";
        c1.sequenceNo = 1;
        c1.maxMarks = 20.0;
        c1.passingMarks = 8.0;
        c1.weightage = 20.0;
        AssessmentComponent comp1 = service.addComponent(s.getId(), c1, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
        assertNotNull(comp1);

        // Component 2: Midterm (maxMarks: 80, passingMarks: 32, weightage: 80%)
        CreateComponentRequest c2 = new CreateComponentRequest();
        c2.componentCode = "MIDTERM";
        c2.componentName = "Mid-Semester Examination";
        c2.componentType = "MIDTERM";
        c2.sequenceNo = 2;
        c2.maxMarks = 80.0;
        c2.passingMarks = 32.0;
        c2.weightage = 80.0;
        AssessmentComponent comp2 = service.addComponent(s.getId(), c2, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
        assertNotNull(comp2);

        // Query components
        List<AssessmentComponent> comps = service.getQueryService().getComponents(s.getId());
        assertEquals(2, comps.size());
        assertEquals("QUIZ-1", comps.get(0).getComponentCode());
        assertEquals("MIDTERM", comps.get(1).getComponentCode());
    }

    @Test(expected = AssessmentValidationException.class)
    public void testAddComponent_PassingMarksExceedMaxMarks_ThrowsValidation() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-INVALID-MARKS"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        CreateComponentRequest c = new CreateComponentRequest();
        c.componentCode = "LAB-01";
        c.componentName = "Lab Exam";
        c.componentType = "PRACTICAL";
        c.sequenceNo = 1;
        c.maxMarks = 25.0;
        c.passingMarks = 30.0; // Invalid: passingMarks > maxMarks
        c.weightage = 25.0;

        service.addComponent(s.getId(), c, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
    }

    @Test
    public void testWeightageReconciliationEngine() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-RECONCILE"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        CreateComponentRequest c1 = new CreateComponentRequest();
        c1.componentCode = "C1";
        c1.componentName = "Comp 1";
        c1.componentType = "TEST";
        c1.sequenceNo = 1;
        c1.maxMarks = 30.0;
        c1.passingMarks = 12.0;
        c1.weightage = 30.0;
        service.addComponent(s.getId(), c1, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        // Validate before adding full 100% -> should report weight mismatch
        AssessmentValidationResult partial = service.validateAssessment(s.getId(), TENANT, "TR");
        assertFalse(partial.valid);
        assertTrue(partial.errors.get(0).contains("Total component weightage"));

        // Add remaining component to complete 100% and 100 marks
        CreateComponentRequest c2 = new CreateComponentRequest();
        c2.componentCode = "C2";
        c2.componentName = "Comp 2";
        c2.componentType = "MIDTERM";
        c2.sequenceNo = 2;
        c2.maxMarks = 70.0;
        c2.passingMarks = 28.0;
        c2.weightage = 70.0;
        service.addComponent(s.getId(), c2, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        AssessmentValidationResult complete = service.validateAssessment(s.getId(), TENANT, "TR");
        assertTrue(complete.valid);
        assertEquals(100.0, complete.totalComponentWeightage, 0.001);
        assertEquals(100.0, complete.totalComponentMarks, 0.001);
    }

    @Test
    public void testOutcomeMapping_ComponentLevelAndDuplicatePrevention() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-MAP-CO"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        CreateComponentRequest compReq = new CreateComponentRequest();
        compReq.componentCode = "TEST-1";
        compReq.componentName = "Class Test 1";
        compReq.componentType = "TEST";
        compReq.sequenceNo = 1;
        compReq.maxMarks = 100.0;
        compReq.passingMarks = 40.0;
        compReq.weightage = 100.0;
        AssessmentComponent comp = service.addComponent(s.getId(), compReq, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        // Map to CO1
        CreateOutcomeMappingRequest m1 = new CreateOutcomeMappingRequest();
        m1.componentId = comp.getId();
        m1.outcomeType = "CO";
        m1.outcomeCode = "CO1";
        m1.mappingLevel = "HIGH";
        m1.weight = 3.0;
        m1.attainmentPolicyRef = "POL-NBA-2026";
        OutcomeMapping mapped1 = service.addOutcomeMapping(s.getId(), m1, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        assertNotNull(mapped1);
        assertEquals("CO1", mapped1.getOutcomeCode());
        assertEquals(OutcomeType.CO, mapped1.getOutcomeType());

        // Map to PO1
        CreateOutcomeMappingRequest m2 = new CreateOutcomeMappingRequest();
        m2.componentId = comp.getId();
        m2.outcomeType = "PO";
        m2.outcomeCode = "PO1";
        m2.mappingLevel = "DIRECT";
        m2.weight = 2.0;
        service.addOutcomeMapping(s.getId(), m2, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        List<OutcomeMapping> list = service.getQueryService().getOutcomeMappings(s.getId(), comp.getId(), null);
        assertEquals(2, list.size());
    }

    @Test(expected = OutcomeMappingException.class)
    public void testOutcomeMapping_DuplicateCombination_ThrowsException() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-DUP-MAP"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        CreateOutcomeMappingRequest m1 = new CreateOutcomeMappingRequest();
        m1.outcomeType = "CO";
        m1.outcomeCode = "CO2";
        m1.mappingLevel = "MEDIUM";
        service.addOutcomeMapping(s.getId(), m1, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        // Attempt exact duplicate assessment-level mapping
        CreateOutcomeMappingRequest m2 = new CreateOutcomeMappingRequest();
        m2.outcomeType = "CO";
        m2.outcomeCode = "CO2";
        m2.mappingLevel = "LOW";
        service.addOutcomeMapping(s.getId(), m2, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
    }

    @Test
    public void testLifecycle_DraftToReviewToApprovedToPublishedToRetired() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-LIFE-01"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        // Add 100% component
        CreateComponentRequest compReq = new CreateComponentRequest();
        compReq.componentCode = "FINAL-EXAM";
        compReq.componentName = "Comprehensive Exam";
        compReq.componentType = "ENDTERM";
        compReq.sequenceNo = 1;
        compReq.maxMarks = 100.0;
        compReq.passingMarks = 40.0;
        compReq.weightage = 100.0;
        service.addComponent(s.getId(), compReq, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        // 1. Submit for review
        AssessmentStructure inReview = service.submitForReview(s.getId(), TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
        assertEquals(AssessmentStatus.REVIEW, inReview.getStatus());

        // 2. Approve by HOD
        ApproveAssessmentRequest appReq = new ApproveAssessmentRequest();
        appReq.approvalRef = "HOD-APPROVAL-2026-001";
        appReq.comments = "Curriculum alignment verified by HOD";
        AssessmentStructure approved = service.approveAssessment(s.getId(), appReq, TENANT, "hod-user", ROLE_HOD, "TR");
        assertEquals(AssessmentStatus.APPROVED, approved.getStatus());
        assertEquals("HOD-APPROVAL-2026-001", approved.getApprovalRef());

        // 3. Publish
        PublishAssessmentRequest pubReq = new PublishAssessmentRequest();
        pubReq.expectedVersion = 1;
        pubReq.reason = "Approved for academic year 2026-2027";
        AssessmentStructure published = service.publishAssessment(s.getId(), pubReq, TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");
        assertEquals(AssessmentStatus.PUBLISHED, published.getStatus());
        assertEquals(1, published.getEffectiveVersion());
        assertNotNull(published.getPublishedAt());

        // Check EXM / faculty query returns effective published structure
        SubjectAssessmentMapResponse mapResp = service.getQueryService().getEffectiveAssessmentForSubject(TENANT, "SUB-CS101", "2026-2027", "TERM-1");
        assertNotNull(mapResp);
        assertEquals(s.getId(), mapResp.structure.getId());
        assertEquals(1, mapResp.components.size());

        // 4. Retire
        AssessmentStructure retired = service.retireAssessment(s.getId(), "Superceded by revised curriculum", TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
        assertEquals(AssessmentStatus.RETIRED, retired.getStatus());
        assertEquals(0, retired.getEffectiveVersion());
    }

    @Test
    public void testTemplateClone_CopiesStructureAndComponents() {
        AssessmentStructure template = service.createAssessment(createValidRequest("TEMPLATE-MATH"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        CreateComponentRequest compReq = new CreateComponentRequest();
        compReq.componentCode = "MATH-T1";
        compReq.componentName = "Unit Test 1";
        compReq.componentType = "TEST";
        compReq.sequenceNo = 1;
        compReq.maxMarks = 100.0;
        compReq.passingMarks = 40.0;
        compReq.weightage = 100.0;
        service.addComponent(template.getId(), compReq, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        CloneTemplateRequest cloneReq = new CloneTemplateRequest();
        cloneReq.newAssessmentCode = "MATH-FALL-2026";
        cloneReq.newAssessmentName = "Math Fall 2026 Structure";
        cloneReq.academicYear = "2026-2027";
        cloneReq.termId = "TERM-1";
        cloneReq.subjectId = "SUB-MATH201";

        AssessmentStructure cloned = service.cloneFromTemplate(template.getId(), cloneReq, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");
        assertNotNull(cloned);
        assertEquals("MATH-FALL-2026", cloned.getAssessmentCode());
        assertEquals(template.getId(), cloned.getTemplateRef());

        List<AssessmentComponent> clonedComps = service.getQueryService().getComponents(cloned.getId());
        assertEquals(1, clonedComps.size());
        assertEquals("MATH-T1", clonedComps.get(0).getComponentCode());
    }

    @Test
    public void testExternalEventConsumption_CurriculumPublishedFlagsReview() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-CURR-TEST"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");
        assertFalse(s.isReviewNeeded());

        ExternalEventPayload ev = new ExternalEventPayload();
        ev.eventId = "EV-01";
        ev.eventType = "CurriculumPublished";
        ev.curriculumId = "CURR-2026-CS";

        service.consumeExternalEvent(ev, "TR-EV");
        assertTrue(s.isReviewNeeded());
        assertTrue(s.getReviewNotes().contains("CurriculumPublished"));
    }

    @Test
    public void testDlqReplay_EmitsNewOutboxEvent() {
        service.getOutboxPublisher().handlePoisonEvent("EV-POISON-1", "ASM-001", "AssessmentStructureCreated",
                "{\"payload\":\"test\"}", "Broker unavailable", "TR");

        assertEquals(1, service.getOutboxPublisher().getDeadLetterEvents().size());
        String dlqId = service.getOutboxPublisher().getDeadLetterEvents().get(0).getId();

        boolean replayed = service.replayDlqEvent(dlqId, USER_ADMIN, ROLE_ADMIN, "TR-REPLAY");
        assertTrue(replayed);
        assertEquals("REPLAYED", service.getOutboxPublisher().getDeadLetterEvent(dlqId).getStatus());
        assertEquals(1, service.getOutboxPublisher().getDeadLetterEvent(dlqId).getReplayCount());
    }

    @Test
    public void testCoverageAnalyticsAndBalanceInsights() {
        AssessmentStructure s = service.createAssessment(createValidRequest("ASM-ANALYTICS"), TENANT, USER_ADMIN, ROLE_ADMIN, null, "TR");

        CreateComponentRequest c1 = new CreateComponentRequest();
        c1.componentCode = "QUIZ";
        c1.componentName = "Quiz";
        c1.componentType = "QUIZ";
        c1.sequenceNo = 1;
        c1.maxMarks = 40.0;
        c1.passingMarks = 16.0;
        c1.weightage = 40.0;
        AssessmentComponent comp1 = service.addComponent(s.getId(), c1, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        CreateComponentRequest c2 = new CreateComponentRequest();
        c2.componentCode = "EXAM";
        c2.componentName = "Final Exam";
        c2.componentType = "ENDTERM";
        c2.sequenceNo = 2;
        c2.maxMarks = 60.0;
        c2.passingMarks = 24.0;
        c2.weightage = 60.0;
        service.addComponent(s.getId(), c2, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        // Map comp1
        CreateOutcomeMappingRequest m = new CreateOutcomeMappingRequest();
        m.componentId = comp1.getId();
        m.outcomeType = "CO";
        m.outcomeCode = "CO1";
        service.addOutcomeMapping(s.getId(), m, TENANT, USER_ADMIN, ROLE_ADMIN, "TR");

        AssessmentCoverageAnalytics analytics = service.getQueryService().getCoverageAnalytics(TENANT);
        assertEquals(1, analytics.totalAssessments);
        assertEquals(2, analytics.totalComponents);
        assertEquals(1, analytics.totalOutcomeMappings);
        assertEquals(1, analytics.unmappedComponents.size());
        assertEquals("EXAM", analytics.unmappedComponents.get(0));

        AssessmentBalanceInsights insights = service.getQueryService().getBalanceInsights(s.getId());
        assertEquals(40.0, insights.continuousWeightage, 0.001);
        assertEquals(60.0, insights.examWeightage, 0.001);
        assertTrue(insights.isBalanced);
    }
}
