package com.campx.academic.assessment.model;

import java.time.Instant;
import java.util.*;

/**
 * Domain entities, enums, and DTOs for the ACD-09 Assessment Mapping Service.
 * Implements the 7 MongoDB collections defined in the ACD-09 Architecture Specification:
 * 1. assessment_structures
 * 2. assessment_components
 * 3. outcome_mappings
 * 4. assessment_history
 * 5. outbox_events
 * 6. idempotency_records
 * 7. dead_letter_events
 */
public class AssessmentModels {

    // ==========================================
    // ENUMS
    // ==========================================

    public enum AssessmentType {
        INTERNAL,
        EXTERNAL,
        PRACTICAL,
        CONTINUOUS,
        PROJECT,
        VIVA,
        LAB
    }

    public enum AssessmentStatus {
        DRAFT,
        REVIEW,
        APPROVED,
        PUBLISHED,
        RETIRED,
        REJECTED,
        CANCELLED
    }

    public enum ComponentType {
        TEST,
        ASSIGNMENT,
        PRACTICAL,
        MIDTERM,
        ENDTERM,
        PROJECT,
        QUIZ,
        VIVA,
        LAB_WORK
    }

    public enum EvaluationMethod {
        MANUAL,
        RUBRIC,
        AUTOMATED,
        HYBRID
    }

    public enum AttemptPolicy {
        SINGLE,
        MULTIPLE,
        BEST_OF,
        AVERAGE
    }

    public enum OutcomeType {
        CO,
        PO,
        LO,
        PSO
    }

    public enum MappingLevel {
        LOW,
        MEDIUM,
        HIGH,
        DIRECT,
        INDIRECT
    }

    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE,
        COMPONENT_ADD,
        COMPONENT_UPDATE,
        COMPONENT_DELETE,
        MAPPING_ADD,
        MAPPING_UPDATE,
        MAPPING_DELETE,
        STATUS_CHANGE,
        SUBMIT_REVIEW,
        APPROVE,
        PUBLISH,
        RETIRE,
        EVENT_CONSUMED,
        CLONE,
        AUTH_FAILURE
    }

    // ==========================================
    // 1. ASSESSMENT STRUCTURE (Aggregate Root)
    // ==========================================

    public static class AssessmentStructure {
        private String id;
        private String tenantId;
        private String institutionId;
        private String departmentId;
        private String subjectId;
        private String courseId;
        private String curriculumId;
        private String academicYear;
        private String termId;
        private String assessmentCode;
        private String assessmentName;
        private AssessmentType assessmentType;
        private double totalMarks;
        private double totalWeightage = 100.0;
        private AssessmentStatus status = AssessmentStatus.DRAFT;
        private int currentVersion = 1;
        private int effectiveVersion = 0;
        private String approvalRef;
        private String reviewNotes;
        private boolean reviewNeeded = false;
        private String templateRef;
        private List<String> programIds = new ArrayList<>();
        private String createdAt;
        private String updatedAt;
        private String publishedAt;
        private String retiredAt;
        private String createdBy;
        private String updatedBy;

        public AssessmentStructure() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = Instant.now().toString();
            this.updatedAt = this.createdAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }

        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }

        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }

        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

        public String getTermId() { return termId; }
        public void setTermId(String termId) { this.termId = termId; }

        public String getAssessmentCode() { return assessmentCode; }
        public void setAssessmentCode(String assessmentCode) { this.assessmentCode = assessmentCode; }

        public String getAssessmentName() { return assessmentName; }
        public void setAssessmentName(String assessmentName) { this.assessmentName = assessmentName; }

        public AssessmentType getAssessmentType() { return assessmentType; }
        public void setAssessmentType(AssessmentType assessmentType) { this.assessmentType = assessmentType; }

        public double getTotalMarks() { return totalMarks; }
        public void setTotalMarks(double totalMarks) { this.totalMarks = totalMarks; }

        public double getTotalWeightage() { return totalWeightage; }
        public void setTotalWeightage(double totalWeightage) { this.totalWeightage = totalWeightage; }

        public AssessmentStatus getStatus() { return status; }
        public void setStatus(AssessmentStatus status) { this.status = status; }

        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

        public int getEffectiveVersion() { return effectiveVersion; }
        public void setEffectiveVersion(int effectiveVersion) { this.effectiveVersion = effectiveVersion; }

        public String getApprovalRef() { return approvalRef; }
        public void setApprovalRef(String approvalRef) { this.approvalRef = approvalRef; }

        public String getReviewNotes() { return reviewNotes; }
        public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

        public boolean isReviewNeeded() { return reviewNeeded; }
        public void setReviewNeeded(boolean reviewNeeded) { this.reviewNeeded = reviewNeeded; }

        public String getTemplateRef() { return templateRef; }
        public void setTemplateRef(String templateRef) { this.templateRef = templateRef; }

        public List<String> getProgramIds() { return programIds; }
        public void setProgramIds(List<String> programIds) { this.programIds = programIds; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public String getPublishedAt() { return publishedAt; }
        public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

        public String getRetiredAt() { return retiredAt; }
        public void setRetiredAt(String retiredAt) { this.retiredAt = retiredAt; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(escape(id)).append("\",");
            sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
            sb.append("\"institutionId\":\"").append(escape(institutionId)).append("\",");
            sb.append("\"departmentId\":\"").append(escape(departmentId)).append("\",");
            sb.append("\"subjectId\":\"").append(escape(subjectId)).append("\",");
            sb.append("\"courseId\":\"").append(escape(courseId)).append("\",");
            sb.append("\"curriculumId\":\"").append(escape(curriculumId)).append("\",");
            sb.append("\"academicYear\":\"").append(escape(academicYear)).append("\",");
            sb.append("\"termId\":\"").append(escape(termId)).append("\",");
            sb.append("\"assessmentCode\":\"").append(escape(assessmentCode)).append("\",");
            sb.append("\"assessmentName\":\"").append(escape(assessmentName)).append("\",");
            sb.append("\"assessmentType\":\"").append(assessmentType != null ? assessmentType.name() : "").append("\",");
            sb.append("\"totalMarks\":").append(totalMarks).append(",");
            sb.append("\"totalWeightage\":").append(totalWeightage).append(",");
            sb.append("\"status\":\"").append(status != null ? status.name() : "").append("\",");
            sb.append("\"currentVersion\":").append(currentVersion).append(",");
            sb.append("\"effectiveVersion\":").append(effectiveVersion).append(",");
            sb.append("\"approvalRef\":\"").append(escape(approvalRef)).append("\",");
            sb.append("\"reviewNotes\":\"").append(escape(reviewNotes)).append("\",");
            sb.append("\"reviewNeeded\":").append(reviewNeeded).append(",");
            sb.append("\"templateRef\":\"").append(escape(templateRef)).append("\",");
            sb.append("\"programIds\":[");
            for (int i = 0; i < programIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(programIds.get(i))).append("\"");
            }
            sb.append("],");
            sb.append("\"createdAt\":\"").append(escape(createdAt)).append("\",");
            sb.append("\"updatedAt\":\"").append(escape(updatedAt)).append("\",");
            sb.append("\"publishedAt\":\"").append(escape(publishedAt)).append("\",");
            sb.append("\"retiredAt\":\"").append(escape(retiredAt)).append("\",");
            sb.append("\"createdBy\":\"").append(escape(createdBy)).append("\",");
            sb.append("\"updatedBy\":\"").append(escape(updatedBy)).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    // ==========================================
    // 2. ASSESSMENT COMPONENT
    // ==========================================

    public static class AssessmentComponent {
        private String id;
        private String assessmentId;
        private String tenantId;
        private String componentCode;
        private String componentName;
        private ComponentType componentType;
        private int sequenceNo;
        private double maxMarks;
        private double passingMarks;
        private double weightage;
        private EvaluationMethod evaluationMethod = EvaluationMethod.MANUAL;
        private String rubricRef;
        private AttemptPolicy attemptPolicy = AttemptPolicy.SINGLE;
        private int maxAttempts = 1;
        private int version = 1;
        private String createdAt;
        private String updatedAt;
        private String createdBy;
        private String updatedBy;

        public AssessmentComponent() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = Instant.now().toString();
            this.updatedAt = this.createdAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAssessmentId() { return assessmentId; }
        public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getComponentCode() { return componentCode; }
        public void setComponentCode(String componentCode) { this.componentCode = componentCode; }

        public String getComponentName() { return componentName; }
        public void setComponentName(String componentName) { this.componentName = componentName; }

        public ComponentType getComponentType() { return componentType; }
        public void setComponentType(ComponentType componentType) { this.componentType = componentType; }

        public int getSequenceNo() { return sequenceNo; }
        public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }

        public double getMaxMarks() { return maxMarks; }
        public void setMaxMarks(double maxMarks) { this.maxMarks = maxMarks; }

        public double getPassingMarks() { return passingMarks; }
        public void setPassingMarks(double passingMarks) { this.passingMarks = passingMarks; }

        public double getWeightage() { return weightage; }
        public void setWeightage(double weightage) { this.weightage = weightage; }

        public EvaluationMethod getEvaluationMethod() { return evaluationMethod; }
        public void setEvaluationMethod(EvaluationMethod evaluationMethod) { this.evaluationMethod = evaluationMethod; }

        public String getRubricRef() { return rubricRef; }
        public void setRubricRef(String rubricRef) { this.rubricRef = rubricRef; }

        public AttemptPolicy getAttemptPolicy() { return attemptPolicy; }
        public void setAttemptPolicy(AttemptPolicy attemptPolicy) { this.attemptPolicy = attemptPolicy; }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(escape(id)).append("\",");
            sb.append("\"assessmentId\":\"").append(escape(assessmentId)).append("\",");
            sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
            sb.append("\"componentCode\":\"").append(escape(componentCode)).append("\",");
            sb.append("\"componentName\":\"").append(escape(componentName)).append("\",");
            sb.append("\"componentType\":\"").append(componentType != null ? componentType.name() : "").append("\",");
            sb.append("\"sequenceNo\":").append(sequenceNo).append(",");
            sb.append("\"maxMarks\":").append(maxMarks).append(",");
            sb.append("\"passingMarks\":").append(passingMarks).append(",");
            sb.append("\"weightage\":").append(weightage).append(",");
            sb.append("\"evaluationMethod\":\"").append(evaluationMethod != null ? evaluationMethod.name() : "").append("\",");
            sb.append("\"rubricRef\":\"").append(escape(rubricRef)).append("\",");
            sb.append("\"attemptPolicy\":\"").append(attemptPolicy != null ? attemptPolicy.name() : "").append("\",");
            sb.append("\"maxAttempts\":").append(maxAttempts).append(",");
            sb.append("\"version\":").append(version).append(",");
            sb.append("\"createdAt\":\"").append(escape(createdAt)).append("\",");
            sb.append("\"updatedAt\":\"").append(escape(updatedAt)).append("\",");
            sb.append("\"createdBy\":\"").append(escape(createdBy)).append("\",");
            sb.append("\"updatedBy\":\"").append(escape(updatedBy)).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    // ==========================================
    // 3. OUTCOME MAPPING
    // ==========================================

    public static class OutcomeMapping {
        private String id;
        private String assessmentId;
        private String componentId; // Optional: empty/null means assessment-level mapping
        private String tenantId;
        private OutcomeType outcomeType;
        private String outcomeCode;
        private MappingLevel mappingLevel = MappingLevel.DIRECT;
        private double weight = 1.0;
        private String attainmentPolicyRef;
        private String createdAt;
        private String updatedAt;
        private String createdBy;
        private String updatedBy;

        public OutcomeMapping() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = Instant.now().toString();
            this.updatedAt = this.createdAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAssessmentId() { return assessmentId; }
        public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

        public String getComponentId() { return componentId; }
        public void setComponentId(String componentId) { this.componentId = componentId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public OutcomeType getOutcomeType() { return outcomeType; }
        public void setOutcomeType(OutcomeType outcomeType) { this.outcomeType = outcomeType; }

        public String getOutcomeCode() { return outcomeCode; }
        public void setOutcomeCode(String outcomeCode) { this.outcomeCode = outcomeCode; }

        public MappingLevel getMappingLevel() { return mappingLevel; }
        public void setMappingLevel(MappingLevel mappingLevel) { this.mappingLevel = mappingLevel; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public String getAttainmentPolicyRef() { return attainmentPolicyRef; }
        public void setAttainmentPolicyRef(String attainmentPolicyRef) { this.attainmentPolicyRef = attainmentPolicyRef; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(escape(id)).append("\",");
            sb.append("\"assessmentId\":\"").append(escape(assessmentId)).append("\",");
            sb.append("\"componentId\":\"").append(escape(componentId)).append("\",");
            sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
            sb.append("\"outcomeType\":\"").append(outcomeType != null ? outcomeType.name() : "").append("\",");
            sb.append("\"outcomeCode\":\"").append(escape(outcomeCode)).append("\",");
            sb.append("\"mappingLevel\":\"").append(mappingLevel != null ? mappingLevel.name() : "").append("\",");
            sb.append("\"weight\":").append(weight).append(",");
            sb.append("\"attainmentPolicyRef\":\"").append(escape(attainmentPolicyRef)).append("\",");
            sb.append("\"createdAt\":\"").append(escape(createdAt)).append("\",");
            sb.append("\"updatedAt\":\"").append(escape(updatedAt)).append("\",");
            sb.append("\"createdBy\":\"").append(escape(createdBy)).append("\",");
            sb.append("\"updatedBy\":\"").append(escape(updatedBy)).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    // ==========================================
    // 4. ASSESSMENT HISTORY (Audit Trail)
    // ==========================================

    public static class AssessmentHistory {
        private String id;
        private String assessmentId;
        private String tenantId;
        private int version;
        private AuditAction action;
        private AssessmentStatus fromStatus;
        private AssessmentStatus toStatus;
        private String changedBy;
        private String changedAt;
        private String reason;
        private String approvalRef;
        private String correlationId;
        private String diffSummary;

        public AssessmentHistory() {
            this.id = UUID.randomUUID().toString();
            this.changedAt = Instant.now().toString();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAssessmentId() { return assessmentId; }
        public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public AuditAction getAction() { return action; }
        public void setAction(AuditAction action) { this.action = action; }

        public AssessmentStatus getFromStatus() { return fromStatus; }
        public void setFromStatus(AssessmentStatus fromStatus) { this.fromStatus = fromStatus; }

        public AssessmentStatus getToStatus() { return toStatus; }
        public void setToStatus(AssessmentStatus toStatus) { this.toStatus = toStatus; }

        public String getChangedBy() { return changedBy; }
        public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

        public String getChangedAt() { return changedAt; }
        public void setChangedAt(String changedAt) { this.changedAt = changedAt; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getApprovalRef() { return approvalRef; }
        public void setApprovalRef(String approvalRef) { this.approvalRef = approvalRef; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getDiffSummary() { return diffSummary; }
        public void setDiffSummary(String diffSummary) { this.diffSummary = diffSummary; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(escape(id)).append("\",");
            sb.append("\"assessmentId\":\"").append(escape(assessmentId)).append("\",");
            sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
            sb.append("\"version\":").append(version).append(",");
            sb.append("\"action\":\"").append(action != null ? action.name() : "").append("\",");
            sb.append("\"fromStatus\":\"").append(fromStatus != null ? fromStatus.name() : "").append("\",");
            sb.append("\"toStatus\":\"").append(toStatus != null ? toStatus.name() : "").append("\",");
            sb.append("\"changedBy\":\"").append(escape(changedBy)).append("\",");
            sb.append("\"changedAt\":\"").append(escape(changedAt)).append("\",");
            sb.append("\"reason\":\"").append(escape(reason)).append("\",");
            sb.append("\"approvalRef\":\"").append(escape(approvalRef)).append("\",");
            sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
            sb.append("\"diffSummary\":\"").append(escape(diffSummary)).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    // ==========================================
    // 5. OUTBOX EVENT
    // ==========================================

    public static class OutboxEvent {
        private String id;
        private String aggregateType = "AssessmentStructure";
        private String aggregateId;
        private String eventType;
        private String payload;
        private String status = "PENDING";
        private int attempts = 0;
        private String correlationId;
        private String createdAt;
        private String sentAt;

        public OutboxEvent() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = Instant.now().toString();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAggregateType() { return aggregateType; }
        public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getSentAt() { return sentAt; }
        public void setSentAt(String sentAt) { this.sentAt = sentAt; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(escape(id)).append("\",");
            sb.append("\"aggregateType\":\"").append(escape(aggregateType)).append("\",");
            sb.append("\"aggregateId\":\"").append(escape(aggregateId)).append("\",");
            sb.append("\"eventType\":\"").append(escape(eventType)).append("\",");
            sb.append("\"payload\":").append(payload != null && payload.trim().startsWith("{") ? payload : "\"" + escape(payload) + "\"").append(",");
            sb.append("\"status\":\"").append(escape(status)).append("\",");
            sb.append("\"attempts\":").append(attempts).append(",");
            sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
            sb.append("\"createdAt\":\"").append(escape(createdAt)).append("\",");
            sb.append("\"sentAt\":\"").append(escape(sentAt)).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    // ==========================================
    // 6. IDEMPOTENCY RECORD
    // ==========================================

    public static class IdempotencyRecord {
        private String key;
        private String tenantId;
        private String requestHash;
        private int statusCode;
        private String responseBody;
        private String createdAt;
        private String expiresAt;

        public IdempotencyRecord() {
            this.createdAt = Instant.now().toString();
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    }

    // ==========================================
    // 7. DEAD LETTER EVENT
    // ==========================================

    public static class DeadLetterEvent {
        private String id;
        private String eventId;
        private String aggregateId;
        private String eventType;
        private String payload;
        private String failureReason;
        private int attempts;
        private int replayCount = 0;
        private String lastAttemptAt;
        private String correlationId;
        private String status = "PENDING_REVIEW";

        public DeadLetterEvent() {
            this.id = UUID.randomUUID().toString();
            this.lastAttemptAt = Instant.now().toString();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }

        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }

        public int getReplayCount() { return replayCount; }
        public void setReplayCount(int replayCount) { this.replayCount = replayCount; }

        public String getLastAttemptAt() { return lastAttemptAt; }
        public void setLastAttemptAt(String lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(escape(id)).append("\",");
            sb.append("\"eventId\":\"").append(escape(eventId)).append("\",");
            sb.append("\"aggregateId\":\"").append(escape(aggregateId)).append("\",");
            sb.append("\"eventType\":\"").append(escape(eventType)).append("\",");
            sb.append("\"failureReason\":\"").append(escape(failureReason)).append("\",");
            sb.append("\"attempts\":").append(attempts).append(",");
            sb.append("\"replayCount\":").append(replayCount).append(",");
            sb.append("\"lastAttemptAt\":\"").append(escape(lastAttemptAt)).append("\",");
            sb.append("\"correlationId\":\"").append(escape(correlationId)).append("\",");
            sb.append("\"status\":\"").append(escape(status)).append("\"");
            sb.append("}");
            return sb.toString();
        }
    }

    // ==========================================
    // DTOs & REQUEST / RESPONSE MODELS
    // ==========================================

    public static class CreateAssessmentRequest {
        public String institutionId;
        public String departmentId;
        public String subjectId;
        public String courseId;
        public String curriculumId;
        public String academicYear;
        public String termId;
        public String assessmentCode;
        public String assessmentName;
        public String assessmentType;
        public Double totalMarks;
        public Double totalWeightage;
        public List<String> programIds;
    }

    public static class UpdateAssessmentRequest {
        public String assessmentName;
        public String assessmentType;
        public Double totalMarks;
        public Double totalWeightage;
        public Integer expectedVersion;
        public String reviewNotes;
    }

    public static class CreateComponentRequest {
        public String componentCode;
        public String componentName;
        public String componentType;
        public Integer sequenceNo;
        public Double maxMarks;
        public Double passingMarks;
        public Double weightage;
        public String evaluationMethod;
        public String rubricRef;
        public String attemptPolicy;
        public Integer maxAttempts;
    }

    public static class UpdateComponentRequest {
        public String componentName;
        public String componentType;
        public Integer sequenceNo;
        public Double maxMarks;
        public Double passingMarks;
        public Double weightage;
        public String evaluationMethod;
        public String rubricRef;
        public String attemptPolicy;
        public Integer maxAttempts;
        public Integer expectedVersion;
    }

    public static class CreateOutcomeMappingRequest {
        public String componentId; // Optional: null or empty if assessment-level
        public String outcomeType;
        public String outcomeCode;
        public String mappingLevel;
        public Double weight;
        public String attainmentPolicyRef;
    }

    public static class PublishAssessmentRequest {
        public Integer expectedVersion;
        public String approvalRef;
        public String reason;
    }

    public static class ApproveAssessmentRequest {
        public String approvalRef;
        public String comments;
    }

    public static class CloneTemplateRequest {
        public String newAssessmentCode;
        public String newAssessmentName;
        public String subjectId;
        public String courseId;
        public String curriculumId;
        public String academicYear;
        public String termId;
    }

    public static class DlqReplayRequest {
        public String dlqEventId;
    }

    public static class ExternalEventPayload {
        public String eventId;
        public String eventType;
        public String subjectId;
        public String curriculumId;
        public String courseId;
        public String tenantId;
        public String correlationId;
    }

    public static class AssessmentValidationResult {
        public boolean valid;
        public String assessmentId;
        public double totalComponentWeightage;
        public double expectedWeightage;
        public double totalComponentMarks;
        public double expectedTotalMarks;
        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"valid\":").append(valid).append(",");
            sb.append("\"assessmentId\":\"").append(escape(assessmentId)).append("\",");
            sb.append("\"totalComponentWeightage\":").append(totalComponentWeightage).append(",");
            sb.append("\"expectedWeightage\":").append(expectedWeightage).append(",");
            sb.append("\"totalComponentMarks\":").append(totalComponentMarks).append(",");
            sb.append("\"expectedTotalMarks\":").append(expectedTotalMarks).append(",");
            sb.append("\"errors\":[");
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(errors.get(i))).append("\"");
            }
            sb.append("],");
            sb.append("\"warnings\":[");
            for (int i = 0; i < warnings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(warnings.get(i))).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class AssessmentCoverageAnalytics {
        public String tenantId;
        public int totalAssessments;
        public int publishedAssessments;
        public int draftAssessments;
        public int totalComponents;
        public int totalOutcomeMappings;
        public int uniqueOutcomesMapped;
        public Map<String, Integer> componentDistribution = new LinkedHashMap<>();
        public Map<String, Integer> outcomeTypeDistribution = new LinkedHashMap<>();
        public List<String> unmappedComponents = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"tenantId\":\"").append(escape(tenantId)).append("\",");
            sb.append("\"totalAssessments\":").append(totalAssessments).append(",");
            sb.append("\"publishedAssessments\":").append(publishedAssessments).append(",");
            sb.append("\"draftAssessments\":").append(draftAssessments).append(",");
            sb.append("\"totalComponents\":").append(totalComponents).append(",");
            sb.append("\"totalOutcomeMappings\":").append(totalOutcomeMappings).append(",");
            sb.append("\"uniqueOutcomesMapped\":").append(uniqueOutcomesMapped).append(",");
            sb.append("\"componentDistribution\":{");
            int i = 0;
            for (Map.Entry<String, Integer> e : componentDistribution.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":").append(e.getValue());
            }
            sb.append("},");
            sb.append("\"outcomeTypeDistribution\":{");
            int j = 0;
            for (Map.Entry<String, Integer> e : outcomeTypeDistribution.entrySet()) {
                if (j++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":").append(e.getValue());
            }
            sb.append("},");
            sb.append("\"unmappedComponents\":[");
            for (int k = 0; k < unmappedComponents.size(); k++) {
                if (k > 0) sb.append(",");
                sb.append("\"").append(escape(unmappedComponents.get(k))).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class AssessmentBalanceInsights {
        public String assessmentId;
        public String assessmentCode;
        public double internalVsExternalRatio;
        public double continuousWeightage;
        public double examWeightage;
        public int totalAssessmentsOrComponents;
        public boolean isBalanced;
        public List<String> insights = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"assessmentId\":\"").append(escape(assessmentId)).append("\",");
            sb.append("\"assessmentCode\":\"").append(escape(assessmentCode)).append("\",");
            sb.append("\"internalVsExternalRatio\":").append(internalVsExternalRatio).append(",");
            sb.append("\"continuousWeightage\":").append(continuousWeightage).append(",");
            sb.append("\"examWeightage\":").append(examWeightage).append(",");
            sb.append("\"totalAssessmentsOrComponents\":").append(totalAssessmentsOrComponents).append(",");
            sb.append("\"isBalanced\":").append(isBalanced).append(",");
            sb.append("\"insights\":[");
            for (int i = 0; i < insights.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(insights.get(i))).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class SubjectAssessmentMapResponse {
        public AssessmentStructure structure;
        public List<AssessmentComponent> components = new ArrayList<>();
        public List<OutcomeMapping> mappings = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"structure\":").append(structure != null ? structure.toJson() : "null").append(",");
            sb.append("\"components\":[");
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(components.get(i).toJson());
            }
            sb.append("],");
            sb.append("\"mappings\":[");
            for (int i = 0; i < mappings.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(mappings.get(i).toJson());
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class AssessmentQueryFilter {
        public String subjectId;
        public String courseId;
        public String termId;
        public String academicYear;
        public String status;
        public String assessmentType;
        public int page = 1;
        public int size = 20;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
