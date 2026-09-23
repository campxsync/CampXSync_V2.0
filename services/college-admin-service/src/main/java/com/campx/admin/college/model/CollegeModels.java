package com.campx.admin.college.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain models for ADM-02: College Admin Service (College Operational Tier).
 * Encompasses operational models including profile configurations, departments, programs,
 * bulk data ingestion jobs, governance documents, and enterprise lifecycle entities.
 */
public final class CollegeModels {

    /**
     * Private constructor to prevent direct instantiation of utility model container.
     */
    private CollegeModels() {}

    /**
     * ADM02_college_profile — Root operational profile representing a constituent college within an institute.
     */
    public static class CollegeProfile {
        private String collegeCode;
        private String legalName;
        private String displayName;
        private List<String> accreditationRefs = new ArrayList<>();
        private String address;
        private String status = "ACTIVE"; // ACTIVE, SUSPENDED
        private int currentVersion = 1;
        private long updatedAt = System.currentTimeMillis();

        /**
         * Returns the unique college code.
         *
         * @return college code string
         */
        public String getCollegeCode() { return collegeCode; }

        /**
         * Sets the unique college code.
         *
         * @param collegeCode the code to set
         */
        public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }

        /**
         * Returns the official legal registered name of the college.
         *
         * @return legal name
         */
        public String getLegalName() { return legalName; }

        /**
         * Sets the legal name of the college.
         *
         * @param legalName legal registered name
         */
        public void setLegalName(String legalName) { this.legalName = legalName; }

        /**
         * Returns the display or friendly name of the college campus.
         *
         * @return display name
         */
        public String getDisplayName() { return displayName; }

        /**
         * Sets the display name of the college campus.
         *
         * @param displayName friendly display name
         */
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        /**
         * Returns the list of external accreditation reference identifiers (e.g. NAAC, NBA).
         *
         * @return list of accreditation reference codes
         */
        public List<String> getAccreditationRefs() { return accreditationRefs; }

        /**
         * Sets the list of accreditation reference identifiers.
         *
         * @param accreditationRefs list of accreditation references
         */
        public void setAccreditationRefs(List<String> accreditationRefs) { this.accreditationRefs = accreditationRefs; }

        /**
         * Returns the physical campus address.
         *
         * @return address string
         */
        public String getAddress() { return address; }

        /**
         * Sets the physical campus address.
         *
         * @param address campus address
         */
        public void setAddress(String address) { this.address = address; }

        /**
         * Returns the current operational status of the college (e.g., "ACTIVE", "SUSPENDED").
         *
         * @return status string
         */
        public String getStatus() { return status; }

        /**
         * Sets the operational status of the college.
         *
         * @param status operational status
         */
        public void setStatus(String status) { this.status = status; }

        /**
         * Returns the optimistic locking schema version for this profile.
         *
         * @return current version integer
         */
        public int getCurrentVersion() { return currentVersion; }

        /**
         * Sets the optimistic locking schema version for this profile.
         *
         * @param currentVersion version integer
         */
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

        /**
         * Returns the epoch timestamp in milliseconds when this profile was last updated.
         *
         * @return updated epoch millis
         */
        public long getUpdatedAt() { return updatedAt; }

        /**
         * Sets the epoch timestamp in milliseconds when this profile was last updated.
         *
         * @param updatedAt epoch timestamp millis
         */
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * ADM02_departments — Academic and administrative department entity under a college.
     */
    public static class Department {
        private String id;
        private String departmentCode;
        private String name;
        private String headUserId;
        private String status = "ACTIVE"; // ACTIVE, RETIRED
        private long createdAt = System.currentTimeMillis();

        /**
         * Returns the primary department identifier.
         *
         * @return department ID
         */
        public String getId() { return id; }

        /**
         * Sets the primary department identifier.
         *
         * @param id department ID
         */
        public void setId(String id) { this.id = id; }

        /**
         * Returns the unique departmental alphanumeric code (e.g., "CSE", "MECH").
         *
         * @return department code
         */
        public String getDepartmentCode() { return departmentCode; }

        /**
         * Sets the departmental alphanumeric code.
         *
         * @param departmentCode department code
         */
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }

        /**
         * Returns the descriptive name of the department.
         *
         * @return department name
         */
        public String getName() { return name; }

        /**
         * Sets the descriptive name of the department.
         *
         * @param name department name
         */
        public void setName(String name) { this.name = name; }

        /**
         * Returns the user identifier of the department head (HOD).
         *
         * @return head user ID
         */
        public String getHeadUserId() { return headUserId; }

        /**
         * Sets the user identifier of the department head.
         *
         * @param headUserId user ID of the head
         */
        public void setHeadUserId(String headUserId) { this.headUserId = headUserId; }

        /**
         * Returns the operational lifecycle status ("ACTIVE", "RETIRED").
         *
         * @return lifecycle status
         */
        public String getStatus() { return status; }

        /**
         * Sets the operational lifecycle status.
         *
         * @param status lifecycle status
         */
        public void setStatus(String status) { this.status = status; }

        /**
         * Returns the creation epoch timestamp in milliseconds.
         *
         * @return creation epoch millis
         */
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM02_programs — Degree or diploma granting academic program hosted by a department.
     */
    public static class Program {
        private String id;
        private String programCode;
        private String name;
        private String departmentId;
        private int durationYears;
        private int version = 1;
        private boolean published = true;

        /**
         * Returns the primary program identifier.
         *
         * @return program ID
         */
        public String getId() { return id; }

        /**
         * Sets the primary program identifier.
         *
         * @param id program ID
         */
        public void setId(String id) { this.id = id; }

        /**
         * Returns the unique alphanumeric program code (e.g., "BTECH_CS").
         *
         * @return program code
         */
        public String getProgramCode() { return programCode; }

        /**
         * Sets the program code.
         *
         * @param programCode program code
         */
        public void setProgramCode(String programCode) { this.programCode = programCode; }

        /**
         * Returns the descriptive program title.
         *
         * @return program name
         */
        public String getName() { return name; }

        /**
         * Sets the descriptive program title.
         *
         * @param name program title
         */
        public void setName(String name) { this.name = name; }

        /**
         * Returns the foreign key identifier of the parent department.
         *
         * @return parent department ID
         */
        public String getDepartmentId() { return departmentId; }

        /**
         * Sets the parent department identifier.
         *
         * @param departmentId parent department ID
         */
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

        /**
         * Returns the standard duration of the degree program in years.
         *
         * @return duration in years
         */
        public int getDurationYears() { return durationYears; }

        /**
         * Sets the program duration in years.
         *
         * @param durationYears duration in years
         */
        public void setDurationYears(int durationYears) { this.durationYears = durationYears; }

        /**
         * Returns the curriculum revision version of the program.
         *
         * @return revision version integer
         */
        public int getVersion() { return version; }

        /**
         * Sets the curriculum revision version.
         *
         * @param version version integer
         */
        public void setVersion(int version) { this.version = version; }

        /**
         * Indicates whether the program is published and available for student enrollment.
         *
         * @return {@code true} if published, {@code false} otherwise
         */
        public boolean isPublished() { return published; }

        /**
         * Sets whether the program is published.
         *
         * @param published published flag
         */
        public void setPublished(boolean published) { this.published = published; }
    }

    /**
     * ADM02_data_import_jobs — Bulk asynchronous data ingestion tracker for student, faculty, and department records.
     */
    public static class DataImportJob {
        private String importId;
        private String entityType; // STUDENT, FACULTY, DEPARTMENT
        private String fileRef;
        private String mode; // INSERT, UPSERT
        private String idempotencyKey;
        private String status = "PENDING"; // PENDING -> PROCESSING -> COMPLETED / FAILED
        private int totalRows = 0;
        private int processedRows = 0;
        private int failedRows = 0;
        private List<String> errorLogs = new ArrayList<>();
        private long createdAt = System.currentTimeMillis();

        /**
         * Returns the primary import job identifier.
         *
         * @return import job ID
         */
        public String getImportId() { return importId; }

        /**
         * Sets the primary import job identifier.
         *
         * @param importId import job ID
         */
        public void setImportId(String importId) { this.importId = importId; }

        /**
         * Returns the target entity type being imported (e.g. "STUDENT", "FACULTY", "DEPARTMENT").
         *
         * @return entity type
         */
        public String getEntityType() { return entityType; }

        /**
         * Sets the target entity type being imported.
         *
         * @param entityType entity type string
         */
        public void setEntityType(String entityType) { this.entityType = entityType; }

        /**
         * Returns the storage reference or URI of the uploaded batch file.
         *
         * @return file storage reference
         */
        public String getFileRef() { return fileRef; }

        /**
         * Sets the storage reference of the uploaded batch file.
         *
         * @param fileRef file storage reference
         */
        public void setFileRef(String fileRef) { this.fileRef = fileRef; }

        /**
         * Returns the import write mode ("INSERT", "UPSERT").
         *
         * @return import mode
         */
        public String getMode() { return mode; }

        /**
         * Sets the import write mode.
         *
         * @param mode import mode
         */
        public void setMode(String mode) { this.mode = mode; }

        /**
         * Returns the deduplication idempotency key for this batch ingestion.
         *
         * @return idempotency key
         */
        public String getIdempotencyKey() { return idempotencyKey; }

        /**
         * Sets the deduplication idempotency key.
         *
         * @param idempotencyKey idempotency key
         */
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

        /**
         * Returns the processing lifecycle state ("PENDING", "PROCESSING", "COMPLETED", "FAILED").
         *
         * @return job status
         */
        public String getStatus() { return status; }

        /**
         * Sets the processing lifecycle state.
         *
         * @param status job status
         */
        public void setStatus(String status) { this.status = status; }

        /**
         * Returns the total row count discovered in the source batch file.
         *
         * @return total row count
         */
        public int getTotalRows() { return totalRows; }

        /**
         * Sets the total row count.
         *
         * @param totalRows total rows
         */
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

        /**
         * Returns the count of successfully processed rows.
         *
         * @return processed row count
         */
        public int getProcessedRows() { return processedRows; }

        /**
         * Sets the count of successfully processed rows.
         *
         * @param processedRows processed rows count
         */
        public void setProcessedRows(int processedRows) { this.processedRows = processedRows; }

        /**
         * Returns the count of rows that encountered validation or processing errors.
         *
         * @return failed row count
         */
        public int getFailedRows() { return failedRows; }

        /**
         * Sets the count of failed rows.
         *
         * @param failedRows failed rows count
         */
        public void setFailedRows(int failedRows) { this.failedRows = failedRows; }

        /**
         * Returns row-level validation and error log messages.
         *
         * @return list of error log strings
         */
        public List<String> getErrorLogs() { return errorLogs; }

        /**
         * Returns the job creation epoch timestamp in milliseconds.
         *
         * @return creation epoch millis
         */
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM02_governance_documents — College policy, syllabus, and accreditation governance document metadata.
     */
    public static class GovernanceDocument {
        private String id;
        private String documentType; // POLICY, SYLLABUS, ACCREDITATION_REPORT
        private String title;
        private String ownerId;
        private String classification; // PUBLIC, INTERNAL, CONFIDENTIAL
        private int currentVersion = 1;
        private String status = "DRAFT"; // DRAFT -> SUBMITTED -> APPROVED / REJECTED -> PUBLISHED
        private String checksum;

        /**
         * Returns the unique document identifier.
         *
         * @return document ID
         */
        public String getId() { return id; }

        /**
         * Sets the unique document identifier.
         *
         * @param id document ID
         */
        public void setId(String id) { this.id = id; }

        /**
         * Returns the governance document category ("POLICY", "SYLLABUS", "ACCREDITATION_REPORT").
         *
         * @return document type string
         */
        public String getDocumentType() { return documentType; }

        /**
         * Sets the governance document category.
         *
         * @param documentType document type string
         */
        public void setDocumentType(String documentType) { this.documentType = documentType; }

        /**
         * Returns the human-readable document title.
         *
         * @return document title
         */
        public String getTitle() { return title; }

        /**
         * Sets the human-readable document title.
         *
         * @param title document title
         */
        public void setTitle(String title) { this.title = title; }

        /**
         * Returns the principal ID of the document owner/author.
         *
         * @return owner user ID
         */
        public String getOwnerId() { return ownerId; }

        /**
         * Sets the principal ID of the document owner.
         *
         * @param ownerId owner user ID
         */
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

        /**
         * Returns the security classification level ("PUBLIC", "INTERNAL", "CONFIDENTIAL").
         *
         * @return classification string
         */
        public String getClassification() { return classification; }

        /**
         * Sets the security classification level.
         *
         * @param classification classification string
         */
        public void setClassification(String classification) { this.classification = classification; }

        /**
         * Returns the current approved version number.
         *
         * @return current version integer
         */
        public int getCurrentVersion() { return currentVersion; }

        /**
         * Sets the current approved version number.
         *
         * @param currentVersion version integer
         */
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }

        /**
         * Returns the governance approval status ("DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "PUBLISHED").
         *
         * @return document status
         */
        public String getStatus() { return status; }

        /**
         * Sets the governance approval status.
         *
         * @param status document status
         */
        public void setStatus(String status) { this.status = status; }

        /**
         * Returns the cryptographic integrity checksum of the document artifact.
         *
         * @return SHA-256 checksum string
         */
        public String getChecksum() { return checksum; }

        /**
         * Sets the cryptographic integrity checksum.
         *
         * @param checksum SHA-256 checksum string
         */
        public void setChecksum(String checksum) { this.checksum = checksum; }
    }

    // =========================================================================
    // Phase 1: College RBAC & Identity Models (User Story Lines 12–16)
    // =========================================================================

    /**
     * ADM02_college_users — College-scoped administrative user context.
     * References employee record and department without storing credentials (CSV Line 12).
     */
    public static class CollegeUser {
        private String id;
        private String userId;          // IAM identity reference
        private String employeeRef;     // Link to HRM employee record
        private String departmentId;
        private String displayName;
        private String status = "ACTIVE"; // ACTIVE, SUSPENDED, DEACTIVATED
        private long createdAt = System.currentTimeMillis();
        private long updatedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getEmployeeRef() { return employeeRef; }
        public void setEmployeeRef(String employeeRef) { this.employeeRef = employeeRef; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    /**
     * ADM02_college_roles — College-scoped role definitions with permission bundles.
     * Protected system roles (Dean, HOD, Registrar) cannot be deleted (CSV Line 13).
     */
    public static class CollegeRole {
        private String id;
        private String roleCode;
        private String name;
        private List<String> permissions = new ArrayList<>();
        private boolean protectedSystemRole = false;
        private String status = "ACTIVE"; // ACTIVE, DEPRECATED
        private int version = 1;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }
        public boolean isProtectedSystemRole() { return protectedSystemRole; }
        public void setProtectedSystemRole(boolean protectedSystemRole) { this.protectedSystemRole = protectedSystemRole; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM02_college_permissions — College permission catalog.
     * Immutable once assigned to a role that has active bindings (CSV Line 14).
     */
    public static class CollegePermission {
        private String id;
        private String permissionCode;
        private String resource;
        private String action;
        private String description;
        private int assignedToRoles = 0;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPermissionCode() { return permissionCode; }
        public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getAssignedToRoles() { return assignedToRoles; }
        public void setAssignedToRoles(int assignedToRoles) { this.assignedToRoles = assignedToRoles; }
    }

    /**
     * ADM02_college_role_bindings — College-scoped, time-bound role grants.
     * Supports scope types: COLLEGE, DEPARTMENT, PROGRAM (CSV Line 15).
     */
    public static class CollegeRoleBinding {
        private String id;
        private String principalId;
        private String roleId;
        private String scopeType;       // COLLEGE, DEPARTMENT, PROGRAM
        private String scopeId;         // The specific department or program ID
        private long effectiveFrom;
        private long effectiveTo;       // 0 = indefinite
        private String grantedBy;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }
        public String getRoleId() { return roleId; }
        public void setRoleId(String roleId) { this.roleId = roleId; }
        public String getScopeType() { return scopeType; }
        public void setScopeType(String scopeType) { this.scopeType = scopeType; }
        public String getScopeId() { return scopeId; }
        public void setScopeId(String scopeId) { this.scopeId = scopeId; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getGrantedBy() { return grantedBy; }
        public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM02_college_access_reviews — College periodic access certification.
     * Self-review blocked; tracks due dates and overdue state (CSV Line 16).
     */
    public static class CollegeAccessReview {
        private String id;
        private String reviewId;
        private String principalId;
        private String roleBindingId;
        private String reviewerId;
        private String decision;        // CERTIFY, REVOKE, PENDING
        private String status = "OPEN"; // OPEN, COMPLETED, OVERDUE
        private long dueAt;
        private long completedAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReviewId() { return reviewId; }
        public void setReviewId(String reviewId) { this.reviewId = reviewId; }
        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }
        public String getRoleBindingId() { return roleBindingId; }
        public void setRoleBindingId(String roleBindingId) { this.roleBindingId = roleBindingId; }
        public String getReviewerId() { return reviewerId; }
        public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getDueAt() { return dueAt; }
        public void setDueAt(long dueAt) { this.dueAt = dueAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
        public long getCreatedAt() { return createdAt; }
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Models (User Story Lines 35–38)
    // =========================================================================

    /**
     * ADM02_idempotency_records — College-level command deduplication and request hash verification (CSV Line 35).
     */
    public static class IdempotencyRecord {
        private String id;
        private String idempotencyKey;
        private String operation;
        private String requestHash;
        private String responseRef;
        private String status = "PROCESSING"; // PROCESSING, COMPLETED, FAILED
        private long expiresAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
        public String getRequestHash() { return requestHash; }
        public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
        public String getResponseRef() { return responseRef; }
        public void setResponseRef(String responseRef) { this.responseRef = responseRef; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM02_outbox_events — College transactional outbox for reliable event publishing (CSV Line 36).
     */
    public static class OutboxEvent {
        private String id;
        private String eventType;
        private String aggregateId;
        private String tenantId;
        private String payload;
        private String status = "PENDING"; // PENDING, PUBLISHED, FAILED
        private int retryCount = 0;
        private long publishedAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getAggregateId() { return aggregateId; }
        public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(long publishedAt) { this.publishedAt = publishedAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM02_inbox_events — College event deduplication for upstream consumption (CSV Line 37).
     */
    public static class InboxEvent {
        private String id;
        private String eventId;
        private String sourceService;
        private String consumerGroup;
        private String status = "PROCESSED"; // PROCESSED, FAILED
        private long processedAt = System.currentTimeMillis();
        private String error;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        public String getSourceService() { return sourceService; }
        public void setSourceService(String sourceService) { this.sourceService = sourceService; }
        public String getConsumerGroup() { return consumerGroup; }
        public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getProcessedAt() { return processedAt; }
        public void setProcessedAt(long processedAt) { this.processedAt = processedAt; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    /**
     * ADM02_dead_letter_events — College failure observability and manual replay (CSV Line 38).
     */
    public static class DeadLetterEvent {
        private String id;
        private String originalEventId;
        private String eventType;
        private String failureCode;
        private int retryCount;
        private String payload;
        private String disposition = "OPEN"; // OPEN, REPLAYED, DISCARDED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOriginalEventId() { return originalEventId; }
        public void setOriginalEventId(String originalEventId) { this.originalEventId = originalEventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getDisposition() { return disposition; }
        public void setDisposition(String disposition) { this.disposition = disposition; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // =========================================================================
    // Phase 3: Configuration & Governance Models (User Story Lines 8–10)
    // =========================================================================

    /**
     * ADM02_college_settings — College-specific settings with effective dating and secret protection (CSV Line 8).
     */
    public static class CollegeSetting {
        private String id;
        private String key;
        private String value;
        private String dataType;        // STRING, INTEGER, BOOLEAN, JSON
        private boolean isSecret = false;
        private int version = 1;
        private long effectiveFrom;
        private long effectiveTo;       // 0 = indefinite
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public boolean isSecret() { return isSecret; }
        public void setSecret(boolean secret) { isSecret = secret; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM02_feature_overrides — College-level feature flag overrides (CSV Line 9).
     */
    public static class FeatureOverride {
        private String id;
        private String flagKey;
        private String overrideValue;
        private String reason;
        private long effectiveFrom;
        private long effectiveTo;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getFlagKey() { return flagKey; }
        public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
        public String getOverrideValue() { return overrideValue; }
        public void setOverrideValue(String overrideValue) { this.overrideValue = overrideValue; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public long getCreatedAt() { return createdAt; }
    }

    /**
     * ADM02_local_policies — Immutable college-level governance policies (CSV Line 10).
     */
    public static class LocalPolicy {
        private String id;
        private String policyCode;
        private List<String> rules = new ArrayList<>();
        private int version = 1;
        private String status = "DRAFT"; // DRAFT -> APPROVED -> PUBLISHED
        private String approvedBy;
        private long publishedAt;
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPolicyCode() { return policyCode; }
        public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }
        public List<String> getRules() { return rules; }
        public void setRules(List<String> rules) { this.rules = rules; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
        public long getPublishedAt() { return publishedAt; }
        public void setPublishedAt(long publishedAt) { this.publishedAt = publishedAt; }
        public long getCreatedAt() { return createdAt; }
    }

    // =========================================================================
    // Phase 6: Calendar, Reporting & Workflow Models (User Stories 6, 21-24, 31-32)
    // =========================================================================

    /**
     * ADM02_calendar_configurations — Academic calendar synchronization & local rule overrides (CSV Line 6).
     */
    public static class CalendarConfiguration {
        private String id;
        private String sourceCalendarId = "INST_CAL_DEFAULT";
        private String syncMode = "AUTOMATIC"; // AUTOMATIC, MANUAL
        private String localRules = "DEFAULT_SCHEDULE";
        private long lastSyncedAt = System.currentTimeMillis();
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSourceCalendarId() { return sourceCalendarId; }
        public void setSourceCalendarId(String sourceCalendarId) { this.sourceCalendarId = sourceCalendarId; }
        public String getSyncMode() { return syncMode; }
        public void setSyncMode(String syncMode) { this.syncMode = syncMode; }
        public String getLocalRules() { return localRules; }
        public void setLocalRules(String localRules) { this.localRules = localRules; }
        public long getLastSyncedAt() { return lastSyncedAt; }
        public void setLastSyncedAt(long lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM02_report_definitions — Institutional report specifications with permissions & classification (CSV Line 21).
     */
    public static class ReportDefinition {
        private String id;
        private String reportCode;
        private String reportName;
        private String querySpec = "SELECT * FROM metrics";
        private List<String> permissions = new ArrayList<>();
        private String dataClass = "INTERNAL"; // PUBLIC, INTERNAL, RESTRICTED
        private String status = "ACTIVE"; // ACTIVE, ARCHIVED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReportCode() { return reportCode; }
        public void setReportCode(String reportCode) { this.reportCode = reportCode; }
        public String getReportName() { return reportName; }
        public void setReportName(String reportName) { this.reportName = reportName; }
        public String getQuerySpec() { return querySpec; }
        public void setQuerySpec(String querySpec) { this.querySpec = querySpec; }
        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }
        public String getDataClass() { return dataClass; }
        public void setDataClass(String dataClass) { this.dataClass = dataClass; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM02_report_runs — Report execution instances with auditing & row count (CSV Line 22).
     */
    public static class ReportRun {
        private String id;
        private String reportCode;
        private String requestedBy;
        private String filters = "{}";
        private long startedAt = System.currentTimeMillis();
        private long completedAt;
        private int rowCount;
        private String outputRef;
        private String status = "COMPLETED"; // RUNNING, COMPLETED, FAILED

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReportCode() { return reportCode; }
        public void setReportCode(String reportCode) { this.reportCode = reportCode; }
        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
        public String getFilters() { return filters; }
        public void setFilters(String filters) { this.filters = filters; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }
        public String getOutputRef() { return outputRef; }
        public void setOutputRef(String outputRef) { this.outputRef = outputRef; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * ADM02_report_schedules — Automated scheduled report execution (CSV Line 23).
     */
    public static class ReportSchedule {
        private String id;
        private String reportCode;
        private String cronExpression = "0 0 * * *";
        private String timezone = "UTC";
        private List<String> recipients = new ArrayList<>();
        private long nextRunAt = System.currentTimeMillis() + 86400000L;
        private long lastRunAt;
        private String status = "ACTIVE"; // ACTIVE, PAUSED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReportCode() { return reportCode; }
        public void setReportCode(String reportCode) { this.reportCode = reportCode; }
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        public List<String> getRecipients() { return recipients; }
        public void setRecipients(List<String> recipients) { this.recipients = recipients; }
        public long getNextRunAt() { return nextRunAt; }
        public void setNextRunAt(long nextRunAt) { this.nextRunAt = nextRunAt; }
        public long getLastRunAt() { return lastRunAt; }
        public void setLastRunAt(long lastRunAt) { this.lastRunAt = lastRunAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * ADM02_dashboard_snapshots — Pre-aggregated operational metrics and KPIs (CSV Line 24).
     */
    public static class DashboardSnapshot {
        private String id;
        private String dashboardCode;
        private String period = "2026-Q3";
        private String metricsJson = "{}";
        private int sourceVersion = 1;
        private long generatedAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDashboardCode() { return dashboardCode; }
        public void setDashboardCode(String dashboardCode) { this.dashboardCode = dashboardCode; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public String getMetricsJson() { return metricsJson; }
        public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }
        public int getSourceVersion() { return sourceVersion; }
        public void setSourceVersion(int sourceVersion) { this.sourceVersion = sourceVersion; }
        public long getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(long generatedAt) { this.generatedAt = generatedAt; }
    }

    /**
     * ADM02_approval_requests — Multi-step approval chain with anti-self-certification enforcement (CSV Line 31).
     */
    public static class ApprovalRequest {
        private String id;
        private String requestType; // BUDGET_OVERRIDE, FACULTY_HIRE, EXAM_DATE_CHANGE
        private String subjectType;
        private String subjectId;
        private String submittedBy;
        private List<String> approverChain = new ArrayList<>();
        private String status = "PENDING"; // PENDING, APPROVED, REJECTED
        private long submittedAt = System.currentTimeMillis();
        private long decidedAt;
        private String decidedBy;
        private String decisionNotes;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public String getSubjectType() { return subjectType; }
        public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getSubmittedBy() { return submittedBy; }
        public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
        public List<String> getApproverChain() { return approverChain; }
        public void setApproverChain(List<String> approverChain) { this.approverChain = approverChain; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }
        public long getDecidedAt() { return decidedAt; }
        public void setDecidedAt(long decidedAt) { this.decidedAt = decidedAt; }
        public String getDecidedBy() { return decidedBy; }
        public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
        public String getDecisionNotes() { return decisionNotes; }
        public void setDecisionNotes(String decisionNotes) { this.decisionNotes = decisionNotes; }
    }

    /**
     * ADM02_college_workflow_instances — College operational workflow state machine with compensation (CSV Line 32).
     */
    public static class CollegeWorkflowInstance {
        private String id;
        private String workflowType; // SEMESTER_REGISTRATION, GRADING_WINDOW, BATCH_PROMOTION
        private String subject;
        private List<String> steps = new ArrayList<>();
        private String currentState = "INITIATED"; // INITIATED, RUNNING, COMPLETED, COMPENSATING, FAILED
        private String compensationAction;
        private long startedAt = System.currentTimeMillis();
        private long completedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getWorkflowType() { return workflowType; }
        public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public List<String> getSteps() { return steps; }
        public void setSteps(List<String> steps) { this.steps = steps; }
        public String getCurrentState() { return currentState; }
        public void setCurrentState(String currentState) { this.currentState = currentState; }
        public String getCompensationAction() { return compensationAction; }
        public void setCompensationAction(String compensationAction) { this.compensationAction = compensationAction; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public long getCompletedAt() { return completedAt; }
        public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }
    }

    /**
     * ADM02_document_versions — Document version tracking with checksums and immutable published state (CSV Line 27).
     */
    public static class DocumentVersion {
        private String id;
        private String documentId;          // FK -> GovernanceDocument.id
        private int versionNo;
        private String objectRef;           // S3/storage ref to version content
        private String checksum;            // SHA-256 computed on upload
        private String status = "DRAFT";    // DRAFT, PUBLISHED — published = immutable
        private long createdAt = System.currentTimeMillis();
        private String createdBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getObjectRef() { return objectRef; }
        public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    }

    /**
     * ADM02_document_permissions — Time-bound least-privilege document access grants (CSV Line 28).
     */
    public static class DocumentPermission {
        private String id;
        private String documentId;          // FK -> GovernanceDocument.id
        private String principalType;       // USER, ROLE, DEPARTMENT
        private String principalId;
        private String permission;          // VIEW, EDIT, APPROVE
        private long effectiveFrom = System.currentTimeMillis();
        private long effectiveTo;           // Time-bound access — 0 = indefinite
        private long grantedAt = System.currentTimeMillis();
        private String grantedBy;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public String getPrincipalType() { return principalType; }
        public void setPrincipalType(String principalType) { this.principalType = principalType; }
        public String getPrincipalId() { return principalId; }
        public void setPrincipalId(String principalId) { this.principalId = principalId; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public long getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(long effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public long getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(long effectiveTo) { this.effectiveTo = effectiveTo; }
        public long getGrantedAt() { return grantedAt; }
        public void setGrantedAt(long grantedAt) { this.grantedAt = grantedAt; }
        public String getGrantedBy() { return grantedBy; }
        public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    }

    /**
     * ADM02_document_approvals — Document version approval records with immutable decisions (CSV Line 29).
     */
    public static class DocumentApproval {
        private String id;
        private String documentId;          // FK -> GovernanceDocument.id
        private int versionNo;              // Which version is being approved
        private String submittedBy;
        private String approverId;
        private String decision = "PENDING"; // PENDING, APPROVED, REJECTED
        private String comments;
        private long submittedAt = System.currentTimeMillis();
        private long decidedAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public int getVersionNo() { return versionNo; }
        public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
        public String getSubmittedBy() { return submittedBy; }
        public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
        public String getApproverId() { return approverId; }
        public void setApproverId(String approverId) { this.approverId = approverId; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getComments() { return comments; }
        public void setComments(String comments) { this.comments = comments; }
        public long getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }
        public long getDecidedAt() { return decidedAt; }
        public void setDecidedAt(long decidedAt) { this.decidedAt = decidedAt; }
    }
}
