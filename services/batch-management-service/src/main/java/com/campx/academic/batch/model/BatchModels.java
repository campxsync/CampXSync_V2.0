package com.campx.academic.batch.model;

import java.util.*;

/**
 * Enterprise domain models, entities, technical collection schemas, and response envelopes
 * for the ACD-04 Batch Management Service.
 */
public final class BatchModels {

    private BatchModels() {}

    // =========================================================================
    // 1. Enums
    // =========================================================================

    public enum BatchStatus {
        DRAFT,
        OPEN,
        ACTIVE,
        PENDING_SPLIT_APPROVAL,
        PENDING_MERGE_APPROVAL,
        CLOSED,
        ARCHIVED
    }

    public enum SectionStatus {
        ACTIVE,
        INACTIVE
    }

    public enum MembershipStatus {
        ACTIVE,
        ENDED
    }

    public enum MembershipType {
        REGULAR,
        LATERAL_ENTRY,
        TRANSFER,
        REPEAT,
        AUDIT
    }

    public enum OverrideStatus {
        ACTIVE,
        EXPIRED,
        REVOKED
    }

    public enum DataClassification {
        L1_PUBLIC,
        L2_INTERNAL,
        L3_CONFIDENTIAL,
        L4_RESTRICTED
    }

    public enum SplitMergeDecision {
        APPROVED,
        REJECTED
    }

    public enum SplitMergeRequestType {
        SPLIT,
        MERGE
    }

    // =========================================================================
    // 2. Aggregate Root: Batch
    // =========================================================================

    public static class Batch {
        private String id;
        private String tenantId = "TENANT-001";
        private String institutionId = "INST-001";
        private String campusId;
        private String batchCode;
        private String name;
        private String courseId;
        private String curriculumId;
        private String departmentId;
        private String academicYear;
        private int semesterNo;
        private int capacity;
        private int rosterCount = 0;
        private BatchStatus status = BatchStatus.DRAFT;
        private BatchStatus priorStatus;
        private long version = 1L; // Optimistic concurrency lock (BR-11)
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private boolean courseActive = true;
        private String splitSourceBatchId;
        private List<String> mergeSourceBatchIds = new ArrayList<>();
        private List<BatchSection> sections = new ArrayList<>();

        public Batch() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getInstitutionId() { return institutionId; }
        public void setInstitutionId(String institutionId) { this.institutionId = institutionId; }
        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }
        public String getBatchCode() { return batchCode; }
        public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCourseId() { return courseId; }
        public void setCourseId(String courseId) { this.courseId = courseId; }
        public String getCurriculumId() { return curriculumId; }
        public void setCurriculumId(String curriculumId) { this.curriculumId = curriculumId; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
        public int getSemesterNo() { return semesterNo; }
        public void setSemesterNo(int semesterNo) { this.semesterNo = semesterNo; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRosterCount() { return rosterCount; }
        public void setRosterCount(int rosterCount) { this.rosterCount = rosterCount; }
        public BatchStatus getStatus() { return status; }
        public void setStatus(BatchStatus status) { this.status = status; }
        public BatchStatus getPriorStatus() { return priorStatus; }
        public void setPriorStatus(BatchStatus priorStatus) { this.priorStatus = priorStatus; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public boolean isCourseActive() { return courseActive; }
        public void setCourseActive(boolean courseActive) { this.courseActive = courseActive; }
        public String getSplitSourceBatchId() { return splitSourceBatchId; }
        public void setSplitSourceBatchId(String splitSourceBatchId) { this.splitSourceBatchId = splitSourceBatchId; }
        public List<String> getMergeSourceBatchIds() { return mergeSourceBatchIds; }
        public void setMergeSourceBatchIds(List<String> mergeSourceBatchIds) {
            this.mergeSourceBatchIds = mergeSourceBatchIds != null ? mergeSourceBatchIds : new ArrayList<>();
        }
        public List<BatchSection> getSections() { return sections; }
        public void setSections(List<BatchSection> sections) {
            this.sections = sections != null ? sections : new ArrayList<>();
        }

        public Batch copy() {
            Batch b = new Batch();
            b.id = this.id;
            b.tenantId = this.tenantId;
            b.institutionId = this.institutionId;
            b.campusId = this.campusId;
            b.batchCode = this.batchCode;
            b.name = this.name;
            b.courseId = this.courseId;
            b.curriculumId = this.curriculumId;
            b.departmentId = this.departmentId;
            b.academicYear = this.academicYear;
            b.semesterNo = this.semesterNo;
            b.capacity = this.capacity;
            b.rosterCount = this.rosterCount;
            b.status = this.status;
            b.priorStatus = this.priorStatus;
            b.version = this.version;
            b.createdAt = this.createdAt;
            b.updatedAt = this.updatedAt;
            b.createdBy = this.createdBy;
            b.updatedBy = this.updatedBy;
            b.courseActive = this.courseActive;
            b.splitSourceBatchId = this.splitSourceBatchId;
            b.mergeSourceBatchIds = new ArrayList<>(this.mergeSourceBatchIds);
            List<BatchSection> copiedSections = new ArrayList<>();
            for (BatchSection s : this.sections) {
                copiedSections.add(s.copy());
            }
            b.sections = copiedSections;
            return b;
        }
    }

    // =========================================================================
    // 3. BatchSection Sub-Entity
    // =========================================================================

    public static class BatchSection {
        private String id;
        private String batchId;
        private String sectionCode;
        private String sectionName;
        private int capacity;
        private String facultyId; // Logical reference to HRM (optional)
        private SectionStatus status = SectionStatus.ACTIVE;
        private long createdAt;
        private long updatedAt;

        public BatchSection() {}

        public BatchSection(String id, String batchId, String sectionCode, String sectionName, int capacity, String facultyId) {
            this.id = id;
            this.batchId = batchId;
            this.sectionCode = sectionCode;
            this.sectionName = sectionName;
            this.capacity = capacity;
            this.facultyId = facultyId;
            this.status = SectionStatus.ACTIVE;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getSectionCode() { return sectionCode; }
        public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public String getFacultyId() { return facultyId; }
        public void setFacultyId(String facultyId) { this.facultyId = facultyId; }
        public SectionStatus getStatus() { return status; }
        public void setStatus(SectionStatus status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

        public BatchSection copy() {
            BatchSection s = new BatchSection();
            s.id = this.id;
            s.batchId = this.batchId;
            s.sectionCode = this.sectionCode;
            s.sectionName = this.sectionName;
            s.capacity = this.capacity;
            s.facultyId = this.facultyId;
            s.status = this.status;
            s.createdAt = this.createdAt;
            s.updatedAt = this.updatedAt;
            return s;
        }
    }

    // =========================================================================
    // 4. BatchRoster: Enrolled Student Membership
    // =========================================================================

    public static class BatchRoster {
        private String id; // membershipId
        private String batchId;
        private String sectionId;
        private String studentId;
        private String tenantId = "TENANT-001";
        private String effectiveFrom;
        private String effectiveTo;
        private MembershipType membershipType = MembershipType.REGULAR;
        private MembershipStatus status = MembershipStatus.ACTIVE;
        private String membershipSource = "ACADEMIC_ADMIN"; // L3 Confidential
        private String notes; // L3 Confidential
        private long createdAt;
        private long updatedAt;
        private String createdBy;
        private String updatedBy;
        private long version = 1L;

        public BatchRoster() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getSectionId() { return sectionId; }
        public void setSectionId(String sectionId) { this.sectionId = sectionId; }
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public MembershipType getMembershipType() { return membershipType; }
        public void setMembershipType(MembershipType membershipType) { this.membershipType = membershipType; }
        public MembershipStatus getStatus() { return status; }
        public void setStatus(MembershipStatus status) { this.status = status; }
        public String getMembershipSource() { return membershipSource; }
        public void setMembershipSource(String membershipSource) { this.membershipSource = membershipSource; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
        public long getVersion() { return version; }
        public void setVersion(long version) { this.version = version; }

        public BatchRoster copy() {
            BatchRoster r = new BatchRoster();
            r.id = this.id;
            r.batchId = this.batchId;
            r.sectionId = this.sectionId;
            r.studentId = this.studentId;
            r.tenantId = this.tenantId;
            r.effectiveFrom = this.effectiveFrom;
            r.effectiveTo = this.effectiveTo;
            r.membershipType = this.membershipType;
            r.status = this.status;
            r.membershipSource = this.membershipSource;
            r.notes = this.notes;
            r.createdAt = this.createdAt;
            r.updatedAt = this.updatedAt;
            r.createdBy = this.createdBy;
            r.updatedBy = this.updatedBy;
            r.version = this.version;
            return r;
        }
    }

    // =========================================================================
    // 5. BatchCapacityOverride Entity
    // =========================================================================

    public static class BatchCapacityOverride {
        private String id; // overrideId
        private String batchId;
        private String tenantId = "TENANT-001";
        private int overrideCapacity;
        private String reason;
        private String approvedBy; // L3 Confidential
        private String effectiveFrom;
        private String effectiveTo;
        private OverrideStatus status = OverrideStatus.ACTIVE;
        private long createdAt;
        private long updatedAt;

        public BatchCapacityOverride() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public int getOverrideCapacity() { return overrideCapacity; }
        public void setOverrideCapacity(int overrideCapacity) { this.overrideCapacity = overrideCapacity; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public OverrideStatus getStatus() { return status; }
        public void setStatus(OverrideStatus status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

        public BatchCapacityOverride copy() {
            BatchCapacityOverride o = new BatchCapacityOverride();
            o.id = this.id;
            o.batchId = this.batchId;
            o.tenantId = this.tenantId;
            o.overrideCapacity = this.overrideCapacity;
            o.reason = this.reason;
            o.approvedBy = this.approvedBy;
            o.effectiveFrom = this.effectiveFrom;
            o.effectiveTo = this.effectiveTo;
            o.status = this.status;
            o.createdAt = this.createdAt;
            o.updatedAt = this.updatedAt;
            return o;
        }
    }

    // =========================================================================
    // 6. BatchHistory: Immutable Append-Only Audit Trail
    // =========================================================================

    public static class BatchHistory {
        private String id;
        private String batchId;
        private String tenantId = "TENANT-001";
        private String action; // CREATE, UPDATE, SECTION_ADD, FACULTY_ASSIGN, CAPACITY_UPDATE, OVERRIDE_GRANT, OVERRIDE_REVOKE, ROSTER_ADD, ROSTER_REMOVE, OPEN, CLOSE, REOPEN, ARCHIVE, SPLIT_REQUEST, MERGE_REQUEST, SPLIT_DECISION, MERGE_DECISION
        private String fromStatus;
        private String toStatus;
        private String actorId;
        private String actorRole;
        private long timestamp;
        private String beforeState;
        private String afterState;
        private String reason;
        private String correlationId;
        private String source = "ACD-04";

        public BatchHistory() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getBatchId() { return batchId; }
        public void setBatchId(String batchId) { this.batchId = batchId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }
        public String getActorId() { return actorId; }
        public void setActorId(String actorId) { this.actorId = actorId; }
        public String getActorRole() { return actorRole; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getBeforeState() { return beforeState; }
        public void setBeforeState(String beforeState) { this.beforeState = beforeState; }
        public String getAfterState() { return afterState; }
        public void setAfterState(String afterState) { this.afterState = afterState; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    // =========================================================================
    // 7. Split & Merge Request Models & Events
    // =========================================================================

    public static class ProposedSectionSplit {
        private String sectionCode;
        private String sectionName;
        private int capacity;
        private List<String> targetStudentIds = new ArrayList<>();

        public ProposedSectionSplit() {}

        public ProposedSectionSplit(String sectionCode, String sectionName, int capacity, List<String> targetStudentIds) {
            this.sectionCode = sectionCode;
            this.sectionName = sectionName;
            this.capacity = capacity;
            this.targetStudentIds = targetStudentIds != null ? targetStudentIds : new ArrayList<>();
        }

        public String getSectionCode() { return sectionCode; }
        public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }
        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public List<String> getTargetStudentIds() { return targetStudentIds; }
        public void setTargetStudentIds(List<String> targetStudentIds) {
            this.targetStudentIds = targetStudentIds != null ? targetStudentIds : new ArrayList<>();
        }
    }

    public static class BatchSplitRequest {
        private String requestId;
        private String requestType = "SPLIT";
        private String sourceBatchId;
        private String sourceBatchCode;
        private String departmentId;
        private String campusId;
        private String requestedBy;
        private long requestedAt;
        private String reason;
        private List<ProposedSectionSplit> proposedSections = new ArrayList<>();
        private String approverRole = "REGISTRAR";
        private String status = "PENDING"; // PENDING, APPROVED, REJECTED

        public BatchSplitRequest() {}

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public String getSourceBatchId() { return sourceBatchId; }
        public void setSourceBatchId(String sourceBatchId) { this.sourceBatchId = sourceBatchId; }
        public String getSourceBatchCode() { return sourceBatchCode; }
        public void setSourceBatchCode(String sourceBatchCode) { this.sourceBatchCode = sourceBatchCode; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public long getRequestedAt() { return requestedAt; }
        public void setRequestedAt(long requestedAt) { this.requestedAt = requestedAt; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<ProposedSectionSplit> getProposedSections() { return proposedSections; }
        public void setProposedSections(List<ProposedSectionSplit> proposedSections) {
            this.proposedSections = proposedSections != null ? proposedSections : new ArrayList<>();
        }
        public String getApproverRole() { return approverRole; }
        public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class BatchMergeRequest {
        private String requestId;
        private String requestType = "MERGE";
        private List<String> sourceBatchIds = new ArrayList<>();
        private List<String> sourceBatchDepartmentIds = new ArrayList<>();
        private String targetBatchId; // null if new batch created
        private String targetBatchCode;
        private String targetBatchName;
        private String departmentId;
        private String campusId;
        private String requestedBy;
        private long requestedAt;
        private String reason;
        private String approverRole = "REGISTRAR";
        private String status = "PENDING"; // PENDING, APPROVED, REJECTED

        public BatchMergeRequest() {}

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public List<String> getSourceBatchIds() { return sourceBatchIds; }
        public void setSourceBatchIds(List<String> sourceBatchIds) {
            this.sourceBatchIds = sourceBatchIds != null ? sourceBatchIds : new ArrayList<>();
        }
        public List<String> getSourceBatchDepartmentIds() { return sourceBatchDepartmentIds; }
        public void setSourceBatchDepartmentIds(List<String> sourceBatchDepartmentIds) {
            this.sourceBatchDepartmentIds = sourceBatchDepartmentIds != null ? sourceBatchDepartmentIds : new ArrayList<>();
        }
        public String getTargetBatchId() { return targetBatchId; }
        public void setTargetBatchId(String targetBatchId) { this.targetBatchId = targetBatchId; }
        public String getTargetBatchCode() { return targetBatchCode; }
        public void setTargetBatchCode(String targetBatchCode) { this.targetBatchCode = targetBatchCode; }
        public String getTargetBatchName() { return targetBatchName; }
        public void setTargetBatchName(String targetBatchName) { this.targetBatchName = targetBatchName; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getCampusId() { return campusId; }
        public void setCampusId(String campusId) { this.campusId = campusId; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public long getRequestedAt() { return requestedAt; }
        public void setRequestedAt(long requestedAt) { this.requestedAt = requestedAt; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getApproverRole() { return approverRole; }
        public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class RosterReassignmentItem {
        private String studentId;
        private String oldBatchId;
        private String oldSectionId;
        private String newBatchId;
        private String newSectionId;

        public RosterReassignmentItem() {}

        public RosterReassignmentItem(String studentId, String oldBatchId, String oldSectionId, String newBatchId, String newSectionId) {
            this.studentId = studentId;
            this.oldBatchId = oldBatchId;
            this.oldSectionId = oldSectionId;
            this.newBatchId = newBatchId;
            this.newSectionId = newSectionId;
        }

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getOldBatchId() { return oldBatchId; }
        public void setOldBatchId(String oldBatchId) { this.oldBatchId = oldBatchId; }
        public String getOldSectionId() { return oldSectionId; }
        public void setOldSectionId(String oldSectionId) { this.oldSectionId = oldSectionId; }
        public String getNewBatchId() { return newBatchId; }
        public void setNewBatchId(String newBatchId) { this.newBatchId = newBatchId; }
        public String getNewSectionId() { return newSectionId; }
        public void setNewSectionId(String newSectionId) { this.newSectionId = newSectionId; }
    }

    // =========================================================================
    // 8. Technical Collections: Outbox, Inbox, DLQ, Idempotency & API Keys
    // =========================================================================

    public static class OutboxEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source = "ACD-04";
        private String tenantId = "TENANT-001";
        private String aggregateId;
        private String payload;
        private String status = "PENDING"; // PENDING, PUBLISHED, FAILED
        private int attempts = 0;
        private long occurredAt;
        private String correlationId;

        public OutboxEvent() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public long getOccurredAt() { return occurredAt; }
        public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }

    public static class InboxEvent {
        private String id;
        private String eventId;
        private String eventType;
        private String source;
        private String tenantId = "TENANT-001";
        private String payload;
        private long processedAt;

        public InboxEvent() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public long getProcessedAt() { return processedAt; }
        public void setProcessedAt(long processedAt) { this.processedAt = processedAt; }
    }

    public static class DeadLetterEvent {
        private String id;
        private String eventId;
        private String source;
        private String errorReason;
        private String payload;
        private int attempts;
        private long createdAt;

        public DeadLetterEvent() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getErrorReason() { return errorReason; }
        public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public int getAttempts() { return attempts; }
        public void setAttempts(int attempts) { this.attempts = attempts; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    public static class IdempotencyRecord {
        private String id;
        private String tenantId;
        private String idempotencyKey;
        private String requestHash;
        private String operation;
        private int statusCode;
        private String responseBody;
        private long createdAt;
        private long expiresAt;

        public IdempotencyRecord() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class ApiKeyRecord {
        private String keyId;
        private String rawKey;
        private String hashedKey;
        private String tenantId;
        private List<String> scopes = new ArrayList<>();
        private long createdAt;
        private long expiresAt;
        private boolean active = true;

        public ApiKeyRecord() {}

        public ApiKeyRecord(String keyId, String rawKey, String tenantId, List<String> scopes, long expiresAt) {
            this.keyId = keyId;
            this.rawKey = rawKey;
            this.tenantId = tenantId;
            this.scopes = scopes != null ? scopes : new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = expiresAt;
            this.active = true;
        }

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getRawKey() { return rawKey; }
        public void setRawKey(String rawKey) { this.rawKey = rawKey; }
        public String getHashedKey() { return hashedKey; }
        public void setHashedKey(String hashedKey) { this.hashedKey = hashedKey; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public List<String> getScopes() { return scopes; }
        public void setScopes(List<String> scopes) { this.scopes = scopes; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    // =========================================================================
    // 9. Standard Success Response Envelope (§29.3)
    // =========================================================================

    public static class ApiResponse<T> {
        private boolean success = true;
        private T data;
        private ResponseMeta meta;

        public ApiResponse() {
            this.meta = new ResponseMeta();
        }

        public ApiResponse(T data, String requestId, String correlationId) {
            this.success = true;
            this.data = data;
            this.meta = new ResponseMeta(requestId, correlationId, System.currentTimeMillis());
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public ResponseMeta getMeta() { return meta; }
        public void setMeta(ResponseMeta meta) { this.meta = meta; }
    }

    public static class ResponseMeta {
        private String requestId;
        private String correlationId;
        private long timestamp;
        private Integer totalCount;
        private Integer page;
        private Integer pageSize;

        public ResponseMeta() {
            this.timestamp = System.currentTimeMillis();
        }

        public ResponseMeta(String requestId, String correlationId, long timestamp) {
            this.requestId = requestId;
            this.correlationId = correlationId;
            this.timestamp = timestamp;
        }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    }
}
