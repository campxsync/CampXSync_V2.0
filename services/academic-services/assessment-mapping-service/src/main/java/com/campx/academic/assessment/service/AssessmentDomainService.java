package com.campx.academic.assessment.service;

import com.campx.academic.assessment.exception.*;
import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain application service orchestrating business capabilities across all 70 ACD-09 user stories.
 */
public class AssessmentDomainService {

    private static final CampXLogger log = CampXLoggerFactory.getLogger(AssessmentDomainService.class);

    // 7 MongoDB Collections (in-memory persistent collections)
    private final Map<String, AssessmentStructure> structures = new ConcurrentHashMap<>();
    private final Map<String, AssessmentComponent> components = new ConcurrentHashMap<>();
    private final Map<String, OutcomeMapping> mappings = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();

    // Subsystems & engines
    private final AssessmentValidationEngine validationEngine = new AssessmentValidationEngine();
    private final WeightageValidationEngine weightageEngine = new WeightageValidationEngine();
    private final OutcomeMappingEngine outcomeEngine = new OutcomeMappingEngine();
    private final AssessmentVersionManager versionManager = new AssessmentVersionManager();
    private final AuditAdapter auditAdapter = new AuditAdapter();
    private final MetricsCollector metricsCollector = new MetricsCollector();
    private final OutboxPublisher outboxPublisher = new OutboxPublisher(metricsCollector);
    private final SubjectReferenceClient subjectClient = new SubjectReferenceClient();
    private final CurriculumReferenceClient curriculumClient = new CurriculumReferenceClient();
    private final CourseReferenceClient courseClient = new CourseReferenceClient();
    private final AssessmentQueryService queryService;
    private final RateLimiter rateLimiter = new RateLimiter(600); // 600 req/min

    public AssessmentDomainService() {
        this.queryService = new AssessmentQueryService(structures, components, mappings, auditAdapter);
    }

    // ==========================================
    // ASSESSMENT STRUCTURE OPERATIONS
    // ==========================================

    public AssessmentStructure createAssessment(CreateAssessmentRequest req, String tenantId,
                                               String userId, String userRole,
                                               String idempotencyKey, String correlationId) {
        long start = System.currentTimeMillis();
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");

        // Idempotency check (US-043)
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            IdempotencyRecord rec = idempotencyRecords.get(idempotencyKey.trim());
            if (rec != null) {
                if (rec.getStatusCode() == 201) {
                    AssessmentStructure cached = structures.get(rec.getResponseBody());
                    if (cached != null) return cached;
                } else {
                    throw new AssessmentIdempotencyConflictException("Conflicting idempotent request for key: " + idempotencyKey);
                }
            }
        }

        validationEngine.validateCreateAssessment(req, tenantId);

        // Academic context validation (US-005, US-058)
        if (!subjectClient.validateSubject(req.subjectId)) {
            throw new AssessmentValidationException("Referenced subject ID '" + req.subjectId + "' does not exist or is invalid.");
        }
        if (!courseClient.validateCourse(req.courseId)) {
            throw new AssessmentValidationException("Referenced course ID '" + req.courseId + "' does not exist or is invalid.");
        }
        if (req.curriculumId != null && !req.curriculumId.trim().isEmpty() && !curriculumClient.validateCurriculum(req.curriculumId)) {
            throw new AssessmentValidationException("Referenced curriculum ID '" + req.curriculumId + "' does not exist or is invalid.");
        }

        // Unique tenant+assessmentCode+academicYear+subjectId check (US-003)
        for (AssessmentStructure s : structures.values()) {
            if (s.getTenantId().equals(tenantId)
                    && s.getAssessmentCode().equalsIgnoreCase(req.assessmentCode.trim())
                    && s.getSubjectId().equalsIgnoreCase(req.subjectId.trim())
                    && s.getAcademicYear().equalsIgnoreCase(req.academicYear.trim())) {
                throw new AssessmentDuplicateException(
                        "Assessment with code '" + req.assessmentCode + "' for subject '" + req.subjectId
                                + "' in academic year '" + req.academicYear + "' already exists.");
            }
        }

        AssessmentStructure structure = new AssessmentStructure();
        structure.setTenantId(tenantId);
        structure.setInstitutionId(req.institutionId != null ? req.institutionId : "INST-DEFAULT");
        structure.setDepartmentId(req.departmentId != null ? req.departmentId : "DEPT-DEFAULT");
        structure.setSubjectId(req.subjectId.trim());
        structure.setCourseId(req.courseId.trim());
        structure.setCurriculumId(req.curriculumId != null ? req.curriculumId.trim() : "");
        structure.setAcademicYear(req.academicYear.trim());
        structure.setTermId(req.termId.trim());
        structure.setAssessmentCode(req.assessmentCode.trim());
        structure.setAssessmentName(req.assessmentName.trim());
        structure.setAssessmentType(AssessmentType.valueOf(req.assessmentType.toUpperCase().trim()));
        structure.setTotalMarks(req.totalMarks);
        structure.setTotalWeightage(req.totalWeightage != null ? req.totalWeightage : 100.0);
        structure.setStatus(AssessmentStatus.DRAFT);
        structure.setCurrentVersion(1);
        structure.setEffectiveVersion(0);
        if (req.programIds != null) {
            structure.setProgramIds(new ArrayList<>(req.programIds));
        }
        structure.setCreatedBy(userId);
        structure.setUpdatedBy(userId);

        structures.put(structure.getId(), structure);

        // Audit log (US-034)
        auditAdapter.record(structure.getId(), tenantId, 1, AuditAction.CREATE,
                null, AssessmentStatus.DRAFT, userId, "Initial assessment structure created",
                null, correlationId, "Created assessment " + structure.getAssessmentCode());

        // Outbox event (US-036, US-042)
        outboxPublisher.publish(structure.getId(), "AssessmentStructureCreated", structure.toJson(), correlationId);

        // Store Idempotency
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            IdempotencyRecord rec = new IdempotencyRecord();
            rec.setKey(idempotencyKey.trim());
            rec.setTenantId(tenantId);
            rec.setStatusCode(201);
            rec.setResponseBody(structure.getId());
            idempotencyRecords.put(rec.getKey(), rec);
        }

        metricsCollector.recordStructureCreation();
        metricsCollector.recordRequest(System.currentTimeMillis() - start);

        return structure;
    }

    public AssessmentStructure updateAssessment(String assessmentId, UpdateAssessmentRequest req,
                                               String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        versionManager.checkDraftEditable(s);
        versionManager.checkOptimisticLock(s, req.expectedVersion);

        if (req.assessmentName != null && !req.assessmentName.trim().isEmpty()) {
            s.setAssessmentName(req.assessmentName.trim());
        }
        if (req.assessmentType != null && !req.assessmentType.trim().isEmpty()) {
            s.setAssessmentType(AssessmentType.valueOf(req.assessmentType.toUpperCase().trim()));
        }
        if (req.totalMarks != null && req.totalMarks > 0) {
            s.setTotalMarks(req.totalMarks);
        }
        if (req.totalWeightage != null && req.totalWeightage > 0) {
            s.setTotalWeightage(req.totalWeightage);
        }
        if (req.reviewNotes != null) {
            s.setReviewNotes(req.reviewNotes);
        }

        s.setUpdatedAt(Instant.now().toString());
        s.setUpdatedBy(userId);
        s.setCurrentVersion(s.getCurrentVersion() + 1);

        auditAdapter.record(s.getId(), tenantId, s.getCurrentVersion(), AuditAction.UPDATE,
                s.getStatus(), s.getStatus(), userId, "Assessment structure updated",
                null, correlationId, "Updated name/marks/type");

        return s;
    }

    // ==========================================
    // COMPONENT OPERATIONS
    // ==========================================

    public AssessmentComponent addComponent(String assessmentId, CreateComponentRequest req,
                                            String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        versionManager.checkDraftEditable(s);

        List<AssessmentComponent> existing = queryService.getComponents(assessmentId);
        validationEngine.validateComponent(req, existing);

        AssessmentComponent component = new AssessmentComponent();
        component.setAssessmentId(assessmentId);
        component.setTenantId(tenantId);
        component.setComponentCode(req.componentCode.trim());
        component.setComponentName(req.componentName.trim());
        component.setComponentType(ComponentType.valueOf(req.componentType.toUpperCase().trim()));
        component.setSequenceNo(req.sequenceNo);
        component.setMaxMarks(req.maxMarks);
        component.setPassingMarks(req.passingMarks != null ? req.passingMarks : 0.0);
        component.setWeightage(req.weightage);
        if (req.evaluationMethod != null && !req.evaluationMethod.trim().isEmpty()) {
            component.setEvaluationMethod(EvaluationMethod.valueOf(req.evaluationMethod.toUpperCase().trim()));
        }
        component.setRubricRef(req.rubricRef != null ? req.rubricRef.trim() : "");
        if (req.attemptPolicy != null && !req.attemptPolicy.trim().isEmpty()) {
            component.setAttemptPolicy(AttemptPolicy.valueOf(req.attemptPolicy.toUpperCase().trim()));
        }
        component.setMaxAttempts(req.maxAttempts != null && req.maxAttempts > 0 ? req.maxAttempts : 1);
        component.setCreatedBy(userId);
        component.setUpdatedBy(userId);

        components.put(component.getId(), component);

        s.setUpdatedAt(Instant.now().toString());
        s.setUpdatedBy(userId);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.COMPONENT_ADD,
                s.getStatus(), s.getStatus(), userId, "Added component " + component.getComponentCode(),
                null, correlationId, component.toJson());

        metricsCollector.recordComponentOperation();
        return component;
    }

    public AssessmentComponent updateComponent(String assessmentId, String componentId, UpdateComponentRequest req,
                                               String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        versionManager.checkDraftEditable(s);

        AssessmentComponent c = components.get(componentId);
        if (c == null || !c.getAssessmentId().equals(assessmentId) || !c.getTenantId().equals(tenantId)) {
            throw new AssessmentNotFoundException("Component not found: " + componentId);
        }

        if (req.expectedVersion != null && c.getVersion() != req.expectedVersion) {
            throw new AssessmentVersionConflictException("Component version conflict: expected " + req.expectedVersion + " but found " + c.getVersion());
        }

        List<AssessmentComponent> all = queryService.getComponents(assessmentId);
        validationEngine.validateUpdateComponent(req, c, all);

        if (req.componentName != null) c.setComponentName(req.componentName.trim());
        if (req.componentType != null) c.setComponentType(ComponentType.valueOf(req.componentType.toUpperCase().trim()));
        if (req.sequenceNo != null) c.setSequenceNo(req.sequenceNo);
        if (req.maxMarks != null) c.setMaxMarks(req.maxMarks);
        if (req.passingMarks != null) c.setPassingMarks(req.passingMarks);
        if (req.weightage != null) c.setWeightage(req.weightage);
        if (req.evaluationMethod != null) c.setEvaluationMethod(EvaluationMethod.valueOf(req.evaluationMethod.toUpperCase().trim()));
        if (req.rubricRef != null) c.setRubricRef(req.rubricRef.trim());
        if (req.attemptPolicy != null) c.setAttemptPolicy(AttemptPolicy.valueOf(req.attemptPolicy.toUpperCase().trim()));
        if (req.maxAttempts != null) c.setMaxAttempts(req.maxAttempts);

        c.setVersion(c.getVersion() + 1);
        c.setUpdatedAt(Instant.now().toString());
        c.setUpdatedBy(userId);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.COMPONENT_UPDATE,
                s.getStatus(), s.getStatus(), userId, "Updated component " + c.getComponentCode(),
                null, correlationId, c.toJson());

        metricsCollector.recordComponentOperation();
        return c;
    }

    public void deleteComponent(String assessmentId, String componentId,
                                String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        versionManager.checkDraftEditable(s);

        AssessmentComponent c = components.get(componentId);
        if (c == null || !c.getAssessmentId().equals(assessmentId) || !c.getTenantId().equals(tenantId)) {
            throw new AssessmentNotFoundException("Component not found: " + componentId);
        }

        components.remove(componentId);

        // Also clean up associated outcome mappings
        List<String> mappingIdsToRemove = new ArrayList<>();
        for (OutcomeMapping m : mappings.values()) {
            if (componentId.equals(m.getComponentId())) {
                mappingIdsToRemove.add(m.getId());
            }
        }
        for (String mId : mappingIdsToRemove) {
            mappings.remove(mId);
        }

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.COMPONENT_DELETE,
                s.getStatus(), s.getStatus(), userId, "Deleted component " + c.getComponentCode(),
                null, correlationId, "Removed component and associated outcome mappings");

        metricsCollector.recordComponentOperation();
    }

    // ==========================================
    // OUTCOME MAPPING OPERATIONS
    // ==========================================

    public OutcomeMapping addOutcomeMapping(String assessmentId, CreateOutcomeMappingRequest req,
                                            String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        versionManager.checkDraftEditable(s);

        List<AssessmentComponent> comps = queryService.getComponents(assessmentId);
        List<OutcomeMapping> existing = queryService.getOutcomeMappings(assessmentId, null, null);

        outcomeEngine.validateMapping(req, s, comps, existing);

        OutcomeMapping mapping = new OutcomeMapping();
        mapping.setAssessmentId(assessmentId);
        mapping.setComponentId(req.componentId != null ? req.componentId.trim() : "");
        mapping.setTenantId(tenantId);
        mapping.setOutcomeType(OutcomeType.valueOf(req.outcomeType.toUpperCase().trim()));
        mapping.setOutcomeCode(req.outcomeCode.trim());
        if (req.mappingLevel != null && !req.mappingLevel.trim().isEmpty()) {
            mapping.setMappingLevel(MappingLevel.valueOf(req.mappingLevel.toUpperCase().trim()));
        }
        mapping.setWeight(req.weight != null ? req.weight : 1.0);
        mapping.setAttainmentPolicyRef(req.attainmentPolicyRef != null ? req.attainmentPolicyRef.trim() : "");
        mapping.setCreatedBy(userId);
        mapping.setUpdatedBy(userId);

        mappings.put(mapping.getId(), mapping);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.MAPPING_ADD,
                s.getStatus(), s.getStatus(), userId, "Added outcome mapping " + mapping.getOutcomeCode(),
                null, correlationId, mapping.toJson());

        // Emit AssessmentMappingUpdated event (US-037)
        outboxPublisher.publish(assessmentId, "AssessmentMappingUpdated", mapping.toJson(), correlationId);

        metricsCollector.recordOutcomeMappingOperation();
        return mapping;
    }

    public void deleteOutcomeMapping(String assessmentId, String mappingId,
                                     String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        versionManager.checkDraftEditable(s);

        OutcomeMapping m = mappings.get(mappingId);
        if (m == null || !m.getAssessmentId().equals(assessmentId) || !m.getTenantId().equals(tenantId)) {
            throw new AssessmentNotFoundException("Outcome mapping not found: " + mappingId);
        }

        mappings.remove(mappingId);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.MAPPING_DELETE,
                s.getStatus(), s.getStatus(), userId, "Deleted outcome mapping " + m.getOutcomeCode(),
                null, correlationId, "Removed mapping ID: " + mappingId);

        metricsCollector.recordOutcomeMappingOperation();
    }

    // ==========================================
    // VALIDATION & LIFECYCLE OPERATIONS
    // ==========================================

    public AssessmentValidationResult validateAssessment(String assessmentId, String tenantId, String correlationId) {
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);
        List<AssessmentComponent> comps = queryService.getComponents(assessmentId);
        List<OutcomeMapping> maps = queryService.getOutcomeMappings(assessmentId, null, null);

        AssessmentValidationResult result = weightageEngine.reconcile(s, comps, maps);

        // Outcome mapping completeness check (US-068)
        List<String> unmapped = outcomeEngine.detectUnmappedComponents(comps, maps);
        if (!unmapped.isEmpty()) {
            result.warnings.add("Components without outcome mappings: " + String.join(", ", unmapped));
        }

        if (!result.valid) {
            metricsCollector.recordValidationFailure();
            metricsCollector.recordWeightageConflict();
        }

        return result;
    }

    public AssessmentStructure submitForReview(String assessmentId, String tenantId,
                                               String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);

        if (s.getStatus() != AssessmentStatus.DRAFT) {
            throw new AssessmentValidationException("Only DRAFT assessments can be submitted for review. Current status: " + s.getStatus());
        }

        // Validate weightage and marks prior to review submission
        List<AssessmentComponent> comps = queryService.getComponents(assessmentId);
        List<OutcomeMapping> maps = queryService.getOutcomeMappings(assessmentId, null, null);
        weightageEngine.validateForPublish(s, comps, maps);

        s.setStatus(AssessmentStatus.REVIEW);
        s.setUpdatedAt(Instant.now().toString());
        s.setUpdatedBy(userId);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.SUBMIT_REVIEW,
                AssessmentStatus.DRAFT, AssessmentStatus.REVIEW, userId, "Submitted assessment for review",
                null, correlationId, "Moved to REVIEW");

        return s;
    }

    public AssessmentStructure approveAssessment(String assessmentId, ApproveAssessmentRequest req,
                                                String tenantId, String userId, String userRole, String correlationId) {
        // Enforce DEPT_HEAD or ACADEMIC_ADMIN role for approval (US-029, US-064)
        checkRbac(userRole, "ACADEMIC_ADMIN", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);

        if (s.getStatus() != AssessmentStatus.REVIEW) {
            throw new AssessmentValidationException("Only assessments in REVIEW status can be approved. Current status: " + s.getStatus());
        }

        String approvalRef = req != null && req.approvalRef != null ? req.approvalRef.trim() : "APP-" + UUID.randomUUID().toString().substring(0, 8);
        s.setStatus(AssessmentStatus.APPROVED);
        s.setApprovalRef(approvalRef);
        s.setReviewNotes(req != null && req.comments != null ? req.comments.trim() : "");
        s.setUpdatedAt(Instant.now().toString());
        s.setUpdatedBy(userId);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.APPROVE,
                AssessmentStatus.REVIEW, AssessmentStatus.APPROVED, userId, "Approved assessment structure",
                approvalRef, correlationId, "Approval granted");

        return s;
    }

    public AssessmentStructure publishAssessment(String assessmentId, PublishAssessmentRequest req,
                                                 String tenantId, String userId, String userRole,
                                                 String idempotencyKey, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);

        // Optimistic lock check (US-032)
        if (req != null && req.expectedVersion != null) {
            versionManager.checkOptimisticLock(s, req.expectedVersion);
        }

        // Must be in APPROVED status to publish (US-030)
        if (s.getStatus() != AssessmentStatus.APPROVED) {
            throw new AssessmentPublishForbiddenException("Cannot publish assessment in " + s.getStatus() + " status. Assessment must be APPROVED first.");
        }

        // Validate weightage, marks, components
        List<AssessmentComponent> comps = queryService.getComponents(assessmentId);
        List<OutcomeMapping> maps = queryService.getOutcomeMappings(assessmentId, null, null);
        weightageEngine.validateForPublish(s, comps, maps);

        // Enforce single effective published version per subject context (US-054)
        versionManager.enforceSingleEffectiveVersion(s, structures.values());

        s.setStatus(AssessmentStatus.PUBLISHED);
        s.setEffectiveVersion(s.getCurrentVersion());
        s.setPublishedAt(Instant.now().toString());
        s.setUpdatedAt(s.getPublishedAt());
        s.setUpdatedBy(userId);
        if (req != null && req.approvalRef != null && !req.approvalRef.trim().isEmpty()) {
            s.setApprovalRef(req.approvalRef.trim());
        }

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.PUBLISH,
                AssessmentStatus.APPROVED, AssessmentStatus.PUBLISHED, userId,
                req != null && req.reason != null ? req.reason : "Published authoritative assessment structure",
                s.getApprovalRef(), correlationId, "Published effectiveVersion " + s.getEffectiveVersion());

        // Emit AssessmentMappingPublished event (US-038)
        outboxPublisher.publish(assessmentId, "AssessmentMappingPublished", s.toJson(), correlationId);

        metricsCollector.recordPublication();
        return s;
    }

    public AssessmentStructure retireAssessment(String assessmentId, String reason,
                                                String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "DEPT_HEAD");
        AssessmentStructure s = getStructureOrThrow(assessmentId, tenantId);

        if (s.getStatus() != AssessmentStatus.PUBLISHED) {
            throw new AssessmentValidationException("Only PUBLISHED assessments can be retired. Current status: " + s.getStatus());
        }

        s.setStatus(AssessmentStatus.RETIRED);
        s.setEffectiveVersion(0);
        s.setRetiredAt(Instant.now().toString());
        s.setUpdatedAt(s.getRetiredAt());
        s.setUpdatedBy(userId);

        auditAdapter.record(assessmentId, tenantId, s.getCurrentVersion(), AuditAction.RETIRE,
                AssessmentStatus.PUBLISHED, AssessmentStatus.RETIRED, userId,
                reason != null ? reason : "Retiring obsolete assessment definition",
                s.getApprovalRef(), correlationId, "Assessment retired");

        outboxPublisher.publish(assessmentId, "AssessmentStructureRetired", s.toJson(), correlationId);

        metricsCollector.recordRetirement();
        return s;
    }

    // ==========================================
    // TEMPLATE CLONING (US-069)
    // ==========================================

    public AssessmentStructure cloneFromTemplate(String templateAssessmentId, CloneTemplateRequest req,
                                                 String tenantId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "FACULTY", "DEPT_HEAD");
        AssessmentStructure template = getStructureOrThrow(templateAssessmentId, tenantId);

        if (req == null || req.newAssessmentCode == null || req.newAssessmentCode.trim().isEmpty()) {
            throw new AssessmentValidationException("New assessment code is required for template clone.");
        }

        CreateAssessmentRequest createReq = new CreateAssessmentRequest();
        createReq.institutionId = template.getInstitutionId();
        createReq.departmentId = template.getDepartmentId();
        createReq.subjectId = req.subjectId != null ? req.subjectId : template.getSubjectId();
        createReq.courseId = req.courseId != null ? req.courseId : template.getCourseId();
        createReq.curriculumId = req.curriculumId != null ? req.curriculumId : template.getCurriculumId();
        createReq.academicYear = req.academicYear != null ? req.academicYear : template.getAcademicYear();
        createReq.termId = req.termId != null ? req.termId : template.getTermId();
        createReq.assessmentCode = req.newAssessmentCode.trim();
        createReq.assessmentName = req.newAssessmentName != null ? req.newAssessmentName.trim() : template.getAssessmentName() + " (Copy)";
        createReq.assessmentType = template.getAssessmentType().name();
        createReq.totalMarks = template.getTotalMarks();
        createReq.totalWeightage = template.getTotalWeightage();
        createReq.programIds = template.getProgramIds();

        AssessmentStructure cloned = createAssessment(createReq, tenantId, userId, userRole, null, correlationId);
        cloned.setTemplateRef(template.getId());

        // Clone components
        List<AssessmentComponent> templateComps = queryService.getComponents(templateAssessmentId);
        for (AssessmentComponent tc : templateComps) {
            CreateComponentRequest compReq = new CreateComponentRequest();
            compReq.componentCode = tc.getComponentCode();
            compReq.componentName = tc.getComponentName();
            compReq.componentType = tc.getComponentType().name();
            compReq.sequenceNo = tc.getSequenceNo();
            compReq.maxMarks = tc.getMaxMarks();
            compReq.passingMarks = tc.getPassingMarks();
            compReq.weightage = tc.getWeightage();
            compReq.evaluationMethod = tc.getEvaluationMethod().name();
            compReq.rubricRef = tc.getRubricRef();
            compReq.attemptPolicy = tc.getAttemptPolicy().name();
            compReq.maxAttempts = tc.getMaxAttempts();
            addComponent(cloned.getId(), compReq, tenantId, userId, userRole, correlationId);
        }

        auditAdapter.record(cloned.getId(), tenantId, 1, AuditAction.CLONE,
                null, AssessmentStatus.DRAFT, userId, "Cloned from template " + template.getAssessmentCode(),
                null, correlationId, "Cloned " + templateComps.size() + " components");

        return cloned;
    }

    // ==========================================
    // EVENT CONSUMPTION & DLQ REPLAY (US-039..041, US-046, US-056)
    // ==========================================

    public void consumeExternalEvent(ExternalEventPayload event, String correlationId) {
        if (event == null || event.eventType == null) {
            throw new AssessmentValidationException("External event payload and eventType are required.");
        }

        log.info("Consuming event: " + event.eventType + ", id=" + event.eventId + ", corr=" + correlationId);

        if ("SubjectPublished".equalsIgnoreCase(event.eventType)) {
            // US-039: register/activate subject context
            if (event.subjectId != null) {
                subjectClient.registerSubject(event.subjectId);
            }
        } else if ("CurriculumPublished".equalsIgnoreCase(event.eventType)) {
            // US-040, US-056: identify affected assessment structures and mark reviewNeeded
            if (event.curriculumId != null) {
                curriculumClient.registerCurriculum(event.curriculumId);
                for (AssessmentStructure s : structures.values()) {
                    if (event.curriculumId.equalsIgnoreCase(s.getCurriculumId())) {
                        s.setReviewNeeded(true);
                        s.setReviewNotes("Flagged review-needed due to CurriculumPublished: " + event.curriculumId);
                        auditAdapter.record(s.getId(), s.getTenantId(), s.getCurrentVersion(), AuditAction.EVENT_CONSUMED,
                                s.getStatus(), s.getStatus(), "SYSTEM", "Marked review-needed from CurriculumPublished",
                                null, correlationId, "Curriculum: " + event.curriculumId);
                    }
                }
            }
        } else if ("CourseUpdated".equalsIgnoreCase(event.eventType)) {
            // US-041: refresh course context
            if (event.courseId != null) {
                courseClient.registerCourse(event.courseId);
                for (AssessmentStructure s : structures.values()) {
                    if (event.courseId.equalsIgnoreCase(s.getCourseId())) {
                        auditAdapter.record(s.getId(), s.getTenantId(), s.getCurrentVersion(), AuditAction.EVENT_CONSUMED,
                                s.getStatus(), s.getStatus(), "SYSTEM", "Course context refreshed from CourseUpdated",
                                null, correlationId, "Course: " + event.courseId);
                    }
                }
            }
        }
    }

    public boolean replayDlqEvent(String dlqEventId, String userId, String userRole, String correlationId) {
        checkRbac(userRole, "ACADEMIC_ADMIN", "SYSTEM_OPERATOR", "DEPT_HEAD");
        return outboxPublisher.replayDeadLetterEvent(dlqEventId);
    }

    // ==========================================
    // RBAC & SECURITY HELPER (US-047, US-063, US-064)
    // ==========================================

    public void checkRbac(String userRole, String... allowedRoles) {
        if (userRole == null || userRole.trim().isEmpty()) {
            auditAdapter.recordAuthFailure(null, null, "ANONYMOUS", "Missing user role header", null);
            throw new AssessmentUnauthorizedException("Authentication required. Missing user role.");
        }
        for (String allowed : allowedRoles) {
            if (allowed.equalsIgnoreCase(userRole.trim())) {
                return;
            }
        }
        auditAdapter.recordAuthFailure(null, null, userRole, "Role " + userRole + " is not authorized", null);
        throw new AssessmentForbiddenException("Access denied for role: " + userRole);
    }

    public AssessmentStructure getStructureOrThrow(String assessmentId, String tenantId) {
        AssessmentStructure s = structures.get(assessmentId);
        if (s == null) {
            throw new AssessmentNotFoundException("Assessment structure not found: " + assessmentId);
        }
        if (tenantId != null && !tenantId.equals(s.getTenantId())) {
            throw new AssessmentForbiddenException("Access denied: Tenant mismatch.");
        }
        return s;
    }

    // Getters for subsystems
    public AssessmentQueryService getQueryService() { return queryService; }
    public AuditAdapter getAuditAdapter() { return auditAdapter; }
    public MetricsCollector getMetricsCollector() { return metricsCollector; }
    public OutboxPublisher getOutboxPublisher() { return outboxPublisher; }
    public RateLimiter getRateLimiter() { return rateLimiter; }
    public SubjectReferenceClient getSubjectClient() { return subjectClient; }
    public CurriculumReferenceClient getCurriculumClient() { return curriculumClient; }
    public CourseReferenceClient getCourseClient() { return courseClient; }
}
