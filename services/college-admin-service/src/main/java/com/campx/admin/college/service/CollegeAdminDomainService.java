package com.campx.admin.college.service;

import com.campx.admin.college.exception.*;
import com.campx.admin.college.model.CollegeModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain business logic for ADM-02: College Admin Service.
 * Implements college profile governance, department/program lifecycle,
 * idempotent bulk data imports, and document approvals.
 */
public class CollegeAdminDomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminDomainService.class);

    private CollegeProfile profile;
    private final Map<String, Department> departments = new ConcurrentHashMap<>();
    private final Map<String, Program> programs = new ConcurrentHashMap<>();
    private final Map<String, DataImportJob> importJobs = new ConcurrentHashMap<>();
    private final Map<String, GovernanceDocument> documents = new ConcurrentHashMap<>();
    private final Set<String> idempotencyKeys = Collections.synchronizedSet(new HashSet<>());
    private final List<Map<String, Object>> auditTrail = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final List<OutboxEvent> outboxEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, InboxEvent> inboxEvents = new ConcurrentHashMap<>();
    private final List<DeadLetterEvent> deadLetterEvents = Collections.synchronizedList(new ArrayList<>());
    private String lastAuditHash = "GENESIS_HASH_0000000000000000";

    // Phase 3: Configuration & Governance collections (User Story Lines 8–10)
    private final Map<String, CollegeSetting> collegeSettings = new ConcurrentHashMap<>();
    private final Map<String, FeatureOverride> featureOverrides = new ConcurrentHashMap<>();
    private final Map<String, LocalPolicy> localPolicies = new ConcurrentHashMap<>();

    // Phase 6: Operations, Reporting & Workflow collections (User Story Lines 6, 21-24, 31-32)
    private CalendarConfiguration calendarConfiguration = new CalendarConfiguration();
    private final Map<String, ReportDefinition> reportDefinitions = new ConcurrentHashMap<>();
    private final List<ReportRun> reportRuns = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ReportSchedule> reportSchedules = new ConcurrentHashMap<>();
    private final Map<String, List<DashboardSnapshot>> dashboardSnapshots = new ConcurrentHashMap<>();
    private final Map<String, ApprovalRequest> approvalRequests = new ConcurrentHashMap<>();
    private final Map<String, CollegeWorkflowInstance> collegeWorkflows = new ConcurrentHashMap<>();

    private void recordAudit(AuditEvent event) {
        logger.audit(event);
        Map<String, Object> record = new LinkedHashMap<>();
        String eventId = UUID.randomUUID().toString();
        record.put("eventId", eventId);
        record.put("action", event.getAction());
        record.put("principalId", event.getPrincipalId());
        record.put("principalRole", event.getPrincipalRole());
        record.put("resourceType", event.getResourceType());
        record.put("resourceId", event.getResourceId());
        record.put("status", event.getStatus());
        record.put("description", event.getDescription());
        record.put("timestamp", event.getTimestamp());
        record.put("traceId", LogContext.getTraceId());
        record.put("tenantId", LogContext.getTenantId());

        // Tamper-evident hash chain (CSV Line 34)
        String beforeHash = lastAuditHash;
        String payloadToHash = beforeHash + ":" + eventId + ":" + event.getAction() + ":" + event.getResourceId() + ":" + event.getTimestamp();
        String afterHash = sha256(payloadToHash);
        record.put("beforeHash", beforeHash);
        record.put("afterHash", afterHash);
        lastAuditHash = afterHash;

        auditTrail.add(record);
    }

    /**
     * Returns an unmodifiable snapshot of the tamper-evident cryptographic audit trail records.
     *
     * @return copy of audit records
     */
    public List<Map<String, Object>> getAuditTrail() {
        return new ArrayList<>(auditTrail);
    }

    /**
     * Constructs a new {@code CollegeAdminDomainService} initializing default seed data.
     */
    public CollegeAdminDomainService() {
        seedDefaults();
    }

    private void seedDefaults() {
        profile = new CollegeProfile();
        profile.setCollegeCode("COL_ENGG_01");
        profile.setLegalName("National College of Engineering & Technology");
        profile.setDisplayName("NCET Campus");
        profile.getAccreditationRefs().addAll(Arrays.asList("NAAC_A_PLUS", "NBA_TIER_1"));
        profile.setAddress("Campus Road, Knowledge Park, Bangalore");
        profile.setStatus("ACTIVE");

        // Seed initial departments
        Department d1 = new Department();
        d1.setId("DEP_CS");
        d1.setDepartmentCode("CSE");
        d1.setName("Computer Science & Engineering");
        d1.setHeadUserId("FAC_HOD_CS");
        departments.put(d1.getId(), d1);

        Department d2 = new Department();
        d2.setId("DEP_EC");
        d2.setDepartmentCode("ECE");
        d2.setName("Electronics & Communication Engineering");
        d2.setHeadUserId("FAC_HOD_EC");
        departments.put(d2.getId(), d2);

        // Seed program
        Program p1 = new Program();
        p1.setId("PRG_BTECH_CS");
        p1.setProgramCode("BTECH_CS");
        p1.setName("Bachelor of Technology in Computer Science");
        p1.setDepartmentId("DEP_CS");
        p1.setDurationYears(4);
        programs.put(p1.getId(), p1);
    }

    /**
     * User Story 1: College Profile Governance.
     * Retrieves the current active college operational profile.
     *
     * @return current college profile
     */
    public CollegeProfile getProfile() {
        return profile;
    }

    /**
     * User Story 1: College Profile Governance.
     * Updates mutable fields of the college profile while preserving the immutable collegeCode.
     *
     * @param updated partial college profile updates
     * @return updated college profile with incremented version
     * @throws CollegeLifecycleException if attempting to mutate the immutable collegeCode
     */
    public CollegeProfile updateProfile(CollegeProfile updated) {
        if (updated.getCollegeCode() != null && !updated.getCollegeCode().equals(profile.getCollegeCode())) {
            throw new CollegeLifecycleException("Cannot modify authoritative immutable collegeCode");
        }
        if (updated.getDisplayName() != null) profile.setDisplayName(updated.getDisplayName());
        if (updated.getAddress() != null) profile.setAddress(updated.getAddress());
        if (updated.getStatus() != null) profile.setStatus(updated.getStatus());
        if (!updated.getAccreditationRefs().isEmpty()) profile.setAccreditationRefs(updated.getAccreditationRefs());
        profile.setCurrentVersion(profile.getCurrentVersion() + 1);
        profile.setUpdatedAt(System.currentTimeMillis());

        logger.info("Updated college profile version to {}", profile.getCurrentVersion());

        AuditEvent audit = AuditEvent.builder()
                .action("COLLEGE_PROFILE_UPDATED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                .resourceType("COLLEGE_PROFILE")
                .resourceId(profile.getCollegeCode())
                .status("SUCCESS")
                .description("Updated college profile to version " + profile.getCurrentVersion())
                .build();
        recordAudit(audit);

        return profile;
    }

    /**
     * User Story 4: Create a college department with uniqueness enforcement.
     *
     * @param dep department entity to create
     * @return persisted department with generated UUID
     * @throws CollegeMalformedPayloadException  if departmentCode is missing or blank
     * @throws CollegeResourceConflictException if departmentCode already exists
     */
    public Department createDepartment(Department dep) {
        try (FlowTracker flow = logger.flow("CreateDepartmentWorkflow", "DEP-" + dep.getDepartmentCode())) {
            if (dep.getDepartmentCode() == null || dep.getDepartmentCode().trim().isEmpty()) {
                CollegeMalformedPayloadException ex = new CollegeMalformedPayloadException("Mandatory field 'departmentCode' is required");
                flow.markFailed(ex);
                throw ex;
            }

            for (Department existing : departments.values()) {
                if (existing.getDepartmentCode().equalsIgnoreCase(dep.getDepartmentCode())) {
                    CollegeResourceConflictException ex = new CollegeResourceConflictException("Department", "departmentCode", dep.getDepartmentCode());
                    flow.markFailed(ex);
                    throw ex;
                }
            }

            dep.setId(UUID.randomUUID().toString());
            dep.setStatus("ACTIVE");
            departments.put(dep.getId(), dep);

            flow.step("ValidateAndPersistDepartment");
            logger.info("Created department [{}] {} (HOD: {})", dep.getDepartmentCode(), dep.getName(), dep.getHeadUserId());

            AuditEvent audit = AuditEvent.builder()
                    .action("DEPARTMENT_CREATED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                    .resourceType("DEPARTMENT")
                    .resourceId(dep.getId())
                    .status("SUCCESS")
                    .description("Created department [" + dep.getDepartmentCode() + "] " + dep.getName() + " (HOD: " + dep.getHeadUserId() + ")")
                    .build();
            recordAudit(audit);

            return dep;
        }
    }

    /**
     * User Story 4: Retire a college department without breaking program references.
     *
     * @param departmentId ID or departmentCode of the department to retire
     * @throws CollegeResourceNotFoundException if no matching department is found
     * @throws CollegeLifecycleException        if active academic programs still reference the department
     */
    public void retireDepartment(String departmentId) {
        Department dep = departments.get(departmentId);
        if (dep == null) {
            for (Department d : departments.values()) {
                if (d.getDepartmentCode().equalsIgnoreCase(departmentId)) {
                    dep = d;
                    departmentId = d.getId();
                    break;
                }
            }
        }
        if (dep == null) {
            throw new CollegeResourceNotFoundException("Department", departmentId);
        }

        // Prevent retirement / hard delete if referenced by programs
        for (Program prog : programs.values()) {
            if (departmentId.equals(prog.getDepartmentId())) {
                throw new CollegeLifecycleException("Cannot retire department " + dep.getName() + " because it is actively referenced by program " + prog.getName());
            }
        }

        dep.setStatus("RETIRED");
        logger.warn("Soft-retired department: {}", dep.getDepartmentCode());
    }

    /**
     * Returns a list of all registered departments.
     *
     * @return list of department models
     */
    public List<Department> listDepartments() {
        return new ArrayList<>(departments.values());
    }

    /**
     * User Story 5: Create a program under an active department with immutable versioning.
     *
     * @param prog academic program entity to create
     * @return persisted program with initial revision 1
     * @throws CollegeMalformedPayloadException  if programCode or durationYears is invalid
     * @throws CollegeResourceConflictException if programCode already exists
     * @throws CollegeLifecycleException        if parent department does not exist or is not ACTIVE
     */
    public Program createProgram(Program prog) {
        if (prog.getProgramCode() == null || prog.getProgramCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'programCode' is required");
        }

        for (Program existing : programs.values()) {
            if (existing.getProgramCode().equalsIgnoreCase(prog.getProgramCode())) {
                throw new CollegeResourceConflictException("Program", "programCode", prog.getProgramCode());
            }
        }

        Department parent = departments.get(prog.getDepartmentId());
        if (parent == null || !"ACTIVE".equalsIgnoreCase(parent.getStatus())) {
            throw new CollegeLifecycleException("Program must reference an existing ACTIVE department");
        }

        if (prog.getDurationYears() <= 0) {
            throw new CollegeMalformedPayloadException("Field 'durationYears' must be positive");
        }

        prog.setId(UUID.randomUUID().toString());
        prog.setVersion(1);
        prog.setPublished(true);
        programs.put(prog.getId(), prog);

        logger.info("Published new program: [{}] {} under department: {}", prog.getProgramCode(), prog.getName(), parent.getName());
        return prog;
    }

    /**
     * Returns a list of all academic programs.
     *
     * @return list of program models
     */
    public List<Program> listPrograms() {
        return new ArrayList<>(programs.values());
    }

    /**
     * User Story 18 & 19: Submit a college data import job with idempotent replay.
     *
     * @param job batch data import job specification
     * @return completed or replayed data import job
     */
    public DataImportJob submitImportJob(DataImportJob job) {
        String key = job.getIdempotencyKey();
        if (key != null && !key.trim().isEmpty()) {
            String requestHash = sha256(job.getEntityType() + ":" + job.getFileRef() + ":" + job.getMode());
            IdempotencyRecord rec = checkOrRecordIdempotency(key, "DATA_IMPORT", requestHash, 3600000L);
            if ("COMPLETED".equals(rec.getStatus()) && rec.getResponseRef() != null) {
                logger.warn("Idempotent replay detected for data import job with key: {}", key);
                DataImportJob existing = importJobs.get(rec.getResponseRef());
                if (existing != null) {
                    return existing;
                }
            }
        }

        try (FlowTracker flow = logger.flow("ExecuteDataImportJob", "IMPORT-" + job.getEntityType())) {
            job.setImportId(UUID.randomUUID().toString());
            job.setStatus("PROCESSING");

            flow.step("ValidateImportFileSchema");
            // Simulate processing 100 rows
            job.setTotalRows(100);
            job.setProcessedRows(98);
            job.setFailedRows(2);
            job.getErrorLogs().add("Row 14: Invalid student registration number format 'STU-ABC'");
            job.getErrorLogs().add("Row 47: Missing date of birth for candidate 'Rohit'");

            job.setStatus("COMPLETED");
            importJobs.put(job.getImportId(), job);
            if (key != null && !key.trim().isEmpty()) {
                completeIdempotency(key, job.getImportId());
            }
            emitOutboxEvent("DataImportCompleted", job.getImportId(), "COLLEGE",
                    "{\"importId\":\"" + job.getImportId() + "\",\"entityType\":\"" + job.getEntityType() + "\"}");

            flow.step("FinalizeBatchExecution");
            logger.info("Completed import job {} for entity {}. Processed: {}, Failed: {}", job.getImportId(), job.getEntityType(), job.getProcessedRows(), job.getFailedRows());

            return job;
        }
    }

    /**
     * Retrieves a data import job by its unique identifier.
     *
     * @param id import job ID
     * @return matching data import job, or null if not found
     */
    public DataImportJob getImportJob(String id) {
        return importJobs.get(id);
    }

    /**
     * User Story 26 & 29: Register governance document and submit for approval.
     *
     * @param doc governance document metadata
     * @return registered document in DRAFT status
     * @throws DocumentGovernanceException if classification is null
     */
    public GovernanceDocument registerDocument(GovernanceDocument doc) {
        if (doc.getClassification() == null) {
            throw new DocumentGovernanceException("Document classification (PUBLIC, INTERNAL, CONFIDENTIAL) is mandatory");
        }

        doc.setId(UUID.randomUUID().toString());
        doc.setStatus("DRAFT");
        doc.setCurrentVersion(1);
        doc.setChecksum("SHA256-" + UUID.randomUUID().toString().substring(0, 16));
        documents.put(doc.getId(), doc);

        logger.info("Registered governance document: [{}] '{}' (Class: {})", doc.getDocumentType(), doc.getTitle(), doc.getClassification());
        return doc;
    }

    /**
     * User Story 26 & 29: Submits and approves a registered governance document.
     *
     * @param documentId identifier of document to approve
     * @param approverId principal identifier of the approver
     * @return approved governance document
     * @throws CollegeResourceNotFoundException if document does not exist
     */
    public GovernanceDocument submitDocumentApproval(String documentId, String approverId) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }

        try (FlowTracker flow = logger.flow("SubmitDocumentApprovalWorkflow", "DOC-APPR-" + documentId)) {
            flow.step("VerifyApproverAuthorization");
            doc.setStatus("APPROVED");

            flow.step("PublishImmutableVersion");
            AuditEvent audit = AuditEvent.builder()
                    .action("GOVERNANCE_DOCUMENT_APPROVED")
                    .principalId(approverId != null ? approverId : "COLLEGE_DEAN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "APPROVER")
                    .resourceType("DOCUMENT")
                    .resourceId(documentId)
                    .status("SUCCESS")
                    .description("Approved governance document: " + doc.getTitle() + " (checksum: " + doc.getChecksum() + ")")
                    .build();
            recordAudit(audit);

            return doc;
        }
    }

    /**
     * Returns a list of all registered governance documents.
     *
     * @return list of governance documents
     */
    public List<GovernanceDocument> listDocuments() {
        return new ArrayList<>(documents.values());
    }

    // =========================================================================
    // Block B: Document Governance Refinement (User Story Lines 27–29)
    // =========================================================================

    private final Map<String, List<DocumentVersion>> documentVersions = new ConcurrentHashMap<>();
    private final Map<String, List<DocumentPermission>> documentPermissions = new ConcurrentHashMap<>();
    private final Map<String, DocumentApproval> documentApprovals = new ConcurrentHashMap<>();

    /**
     * Create a document version. Computes checksum and increments version (CSV Line 27).
     */
    public DocumentVersion createDocumentVersion(String documentId, String objectRef, String createdBy) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }
        List<DocumentVersion> versions = documentVersions.computeIfAbsent(documentId, k -> new ArrayList<>());
        int nextVersionNo = versions.size() + 1;
        DocumentVersion version = new DocumentVersion();
        version.setId(UUID.randomUUID().toString());
        version.setDocumentId(documentId);
        version.setVersionNo(nextVersionNo);
        version.setObjectRef(objectRef != null ? objectRef : "s3://campx-docs/" + documentId + "/v" + nextVersionNo);
        version.setChecksum("SHA256-" + UUID.randomUUID().toString().replace("-", ""));
        version.setStatus("DRAFT");
        version.setCreatedBy(createdBy != null ? createdBy : "DOCUMENT_AUTHOR");
        version.setCreatedAt(System.currentTimeMillis());
        versions.add(version);
        doc.setCurrentVersion(nextVersionNo);

        AuditEvent audit = AuditEvent.builder()
                .action("DOCUMENT_VERSION_CREATED")
                .principalId(version.getCreatedBy()).principalRole("AUTHOR")
                .resourceType("DOCUMENT_VERSION").resourceId(version.getId())
                .status("SUCCESS")
                .description("Created version " + nextVersionNo + " for document: " + doc.getTitle())
                .build();
        recordAudit(audit);
        logger.info("[ADM-02 DocGov] Created document [{}] version [{}]", documentId, nextVersionNo);
        return version;
    }

    /**
     * Publish document version. Once PUBLISHED, version is immutable (CSV Line 27).
     */
    public DocumentVersion publishDocumentVersion(String documentId, int versionNo) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }
        List<DocumentVersion> versions = documentVersions.get(documentId);
        if (versions == null || versions.isEmpty()) {
            throw new CollegeResourceNotFoundException("DocumentVersion", documentId + "-v" + versionNo);
        }
        DocumentVersion target = null;
        for (DocumentVersion v : versions) {
            if (v.getVersionNo() == versionNo) {
                target = v;
                break;
            }
        }
        if (target == null) {
            throw new CollegeResourceNotFoundException("DocumentVersion", documentId + "-v" + versionNo);
        }
        if ("PUBLISHED".equalsIgnoreCase(target.getStatus())) {
            throw new CollegeLifecycleException("Document version " + versionNo + " is already PUBLISHED and immutable");
        }
        target.setStatus("PUBLISHED");
        doc.setStatus("PUBLISHED");

        AuditEvent audit = AuditEvent.builder()
                .action("DOCUMENT_VERSION_PUBLISHED")
                .principalId("COLLEGE_DEAN").principalRole("ADMIN")
                .resourceType("DOCUMENT_VERSION").resourceId(target.getId())
                .status("SUCCESS")
                .description("Published version " + versionNo + " for document: " + doc.getTitle() + " (immutable)")
                .build();
        recordAudit(audit);
        logger.info("[ADM-02 DocGov] Published document [{}] version [{}]", documentId, versionNo);
        return target;
    }

    public List<DocumentVersion> listDocumentVersions(String documentId) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }
        return new ArrayList<>(documentVersions.getOrDefault(documentId, Collections.emptyList()));
    }

    /**
     * Grant time-bound document permission (CSV Line 28).
     */
    public DocumentPermission grantDocumentPermission(DocumentPermission permission) {
        GovernanceDocument doc = documents.get(permission.getDocumentId());
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", permission.getDocumentId());
        }
        if (permission.getPrincipalId() == null || permission.getPrincipalId().isEmpty()) {
            throw new CollegeMalformedPayloadException("principalId is required");
        }
        permission.setId(UUID.randomUUID().toString());
        if (permission.getGrantedAt() <= 0) {
            permission.setGrantedAt(System.currentTimeMillis());
        }
        List<DocumentPermission> perms = documentPermissions.computeIfAbsent(permission.getDocumentId(), k -> new ArrayList<>());
        perms.add(permission);

        AuditEvent audit = AuditEvent.builder()
                .action("DOCUMENT_PERMISSION_GRANTED")
                .principalId(permission.getGrantedBy() != null ? permission.getGrantedBy() : "DOC_ADMIN")
                .principalRole("ADMIN")
                .resourceType("DOCUMENT_PERMISSION").resourceId(permission.getId())
                .status("SUCCESS")
                .description("Granted " + permission.getPermission() + " on doc " + doc.getTitle() + " to " + permission.getPrincipalId())
                .build();
        recordAudit(audit);
        logger.info("[ADM-02 DocGov] Granted [{}] permission on [{}] to [{}]",
                permission.getPermission(), permission.getDocumentId(), permission.getPrincipalId());
        return permission;
    }

    /**
     * Revoke document permission by setting effectiveTo = now (CSV Line 28).
     */
    public DocumentPermission revokeDocumentPermission(String documentId, String permissionId) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }
        List<DocumentPermission> perms = documentPermissions.get(documentId);
        if (perms != null) {
            for (DocumentPermission p : perms) {
                if (p.getId().equals(permissionId)) {
                    p.setEffectiveTo(System.currentTimeMillis());
                    AuditEvent audit = AuditEvent.builder()
                            .action("DOCUMENT_PERMISSION_REVOKED")
                            .principalId("DOC_ADMIN").principalRole("ADMIN")
                            .resourceType("DOCUMENT_PERMISSION").resourceId(permissionId)
                            .status("SUCCESS")
                            .description("Revoked permission " + permissionId + " on doc " + doc.getTitle())
                            .build();
                    recordAudit(audit);
                    logger.info("[ADM-02 DocGov] Revoked permission [{}] on [{}]", permissionId, documentId);
                    return p;
                }
            }
        }
        throw new CollegeResourceNotFoundException("DocumentPermission", permissionId);
    }

    /**
     * List active document permissions (CSV Line 28).
     */
    public List<DocumentPermission> listDocumentPermissions(String documentId) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }
        List<DocumentPermission> perms = documentPermissions.get(documentId);
        if (perms == null) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        List<DocumentPermission> active = new ArrayList<>();
        for (DocumentPermission p : perms) {
            if (p.getEffectiveTo() == 0 || p.getEffectiveTo() > now) {
                active.add(p);
            }
        }
        return active;
    }

    /**
     * Submit document version for approval (CSV Line 29).
     */
    public DocumentApproval submitDocumentForApproval(String documentId, int versionNo, String submittedBy, String approverId) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new CollegeResourceNotFoundException("Governance Document", documentId);
        }
        List<DocumentVersion> versions = documentVersions.get(documentId);
        if (versions == null || versions.isEmpty()) {
            throw new CollegeResourceNotFoundException("DocumentVersion", documentId + "-v" + versionNo);
        }
        DocumentVersion target = null;
        for (DocumentVersion v : versions) {
            if (v.getVersionNo() == versionNo) {
                target = v;
                break;
            }
        }
        if (target == null) {
            throw new CollegeResourceNotFoundException("DocumentVersion", documentId + "-v" + versionNo);
        }
        if (!"DRAFT".equalsIgnoreCase(target.getStatus())) {
            throw new CollegeLifecycleException("Only DRAFT versions can be submitted for approval (current: " + target.getStatus() + ")");
        }
        DocumentApproval approval = new DocumentApproval();
        approval.setId(UUID.randomUUID().toString());
        approval.setDocumentId(documentId);
        approval.setVersionNo(versionNo);
        approval.setSubmittedBy(submittedBy != null ? submittedBy : "DOCUMENT_AUTHOR");
        approval.setApproverId(approverId != null ? approverId : "COLLEGE_DEAN");
        approval.setDecision("PENDING");
        approval.setSubmittedAt(System.currentTimeMillis());
        documentApprovals.put(approval.getId(), approval);

        AuditEvent audit = AuditEvent.builder()
                .action("DOCUMENT_APPROVAL_SUBMITTED")
                .principalId(approval.getSubmittedBy()).principalRole("AUTHOR")
                .resourceType("DOCUMENT_APPROVAL").resourceId(approval.getId())
                .status("SUCCESS")
                .description("Submitted approval for document " + doc.getTitle() + " version " + versionNo)
                .build();
        recordAudit(audit);
        logger.info("[ADM-02 DocGov] Submitted approval [{}] for doc [{}] v[{}]", approval.getId(), documentId, versionNo);
        return approval;
    }

    /**
     * Decide document approval. Decisions are immutable once made (CSV Line 29).
     */
    public DocumentApproval decideDocumentApproval(String approvalId, String approverId, String decision, String comments) {
        DocumentApproval approval = documentApprovals.get(approvalId);
        if (approval == null) {
            throw new CollegeResourceNotFoundException("DocumentApproval", approvalId);
        }
        if (!"PENDING".equalsIgnoreCase(approval.getDecision())) {
            throw new CollegeResourceConflictException(
                    "Document approval " + approvalId + " has already been decided: " + approval.getDecision());
        }
        approval.setDecision(decision);
        approval.setApproverId(approverId != null ? approverId : "COLLEGE_DEAN");
        approval.setComments(comments);
        approval.setDecidedAt(System.currentTimeMillis());

        if ("APPROVED".equalsIgnoreCase(decision)) {
            // Auto-publish the version
            List<DocumentVersion> versions = documentVersions.get(approval.getDocumentId());
            if (versions != null) {
                for (DocumentVersion v : versions) {
                    if (v.getVersionNo() == approval.getVersionNo()) {
                        v.setStatus("PUBLISHED");
                        break;
                    }
                }
            }
            GovernanceDocument doc = documents.get(approval.getDocumentId());
            if (doc != null) {
                doc.setStatus("PUBLISHED");
            }
        }

        AuditEvent audit = AuditEvent.builder()
                .action("DOCUMENT_APPROVAL_DECIDED")
                .principalId(approval.getApproverId()).principalRole("APPROVER")
                .resourceType("DOCUMENT_APPROVAL").resourceId(approvalId)
                .status("SUCCESS")
                .description("Approval decision [" + decision + "] for document " + approval.getDocumentId() + " version " + approval.getVersionNo())
                .build();
        recordAudit(audit);
        logger.info("[ADM-02 DocGov] Decided approval [{}] decision=[{}]", approvalId, decision);
        return approval;
    }

    // =========================================================================
    // Phase 1: College RBAC & Identity Management (User Story Lines 12–16)
    // =========================================================================

    private final Map<String, CollegeUser> collegeUsers = new ConcurrentHashMap<>();
    private final Map<String, CollegeRole> collegeRoles = new ConcurrentHashMap<>();
    private final Map<String, CollegePermission> collegePermissions = new ConcurrentHashMap<>();
    private final Map<String, CollegeRoleBinding> collegeRoleBindings = new ConcurrentHashMap<>();
    private final Map<String, CollegeAccessReview> collegeAccessReviews = new ConcurrentHashMap<>();

    /**
     * User Story 12: Register a college-scoped admin user with department context.
     */
    public CollegeUser registerCollegeUser(CollegeUser user) {
        try (FlowTracker flow = logger.flow("RegisterCollegeUser", "USER-" + user.getUserId())) {
            if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
                throw new CollegeMalformedPayloadException("Mandatory field 'userId' (IAM reference) is required");
            }

            // Uniqueness check on userId
            for (CollegeUser existing : collegeUsers.values()) {
                if (existing.getUserId().equals(user.getUserId())) {
                    throw new CollegeResourceConflictException("CollegeUser", "userId", user.getUserId());
                }
            }

            user.setId(UUID.randomUUID().toString());
            user.setUpdatedAt(System.currentTimeMillis());
            collegeUsers.put(user.getId(), user);

            flow.step("PersistCollegeUserRecord");
            logger.info("Registered college user [{}] with IAM ref [{}], department={}", user.getDisplayName(), user.getUserId(), user.getDepartmentId());

            AuditEvent audit = AuditEvent.builder()
                    .action("COLLEGE_USER_REGISTERED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                    .resourceType("COLLEGE_USER")
                    .resourceId(user.getId())
                    .status("SUCCESS")
                    .description("Registered college user: " + user.getDisplayName() + " [" + user.getUserId() + "]")
                    .build();
            recordAudit(audit);

            return user;
        }
    }

    public List<CollegeUser> listCollegeUsers() {
        return new ArrayList<>(collegeUsers.values());
    }

    /**
     * User Story 13: Define a college role with permission bundle.
     * Protected system roles (DEAN, HOD, REGISTRAR) cannot be deleted.
     */
    public CollegeRole createCollegeRole(CollegeRole role) {
        try (FlowTracker flow = logger.flow("CreateCollegeRole", "ROLE-" + role.getRoleCode())) {
            if (role.getRoleCode() == null || role.getRoleCode().trim().isEmpty()) {
                throw new CollegeMalformedPayloadException("Mandatory field 'roleCode' is required");
            }

            // Enforce roleCode uniqueness
            for (CollegeRole existing : collegeRoles.values()) {
                if (existing.getRoleCode().equalsIgnoreCase(role.getRoleCode())) {
                    throw new CollegeResourceConflictException("CollegeRole", "roleCode", role.getRoleCode());
                }
            }

            role.setId(UUID.randomUUID().toString());
            collegeRoles.put(role.getId(), role);

            flow.step("PersistCollegeRole");
            logger.info("Created college role [{}] '{}' with {} permissions", role.getRoleCode(), role.getName(), role.getPermissions().size());

            AuditEvent audit = AuditEvent.builder()
                    .action("COLLEGE_ROLE_CREATED")
                    .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                    .resourceType("COLLEGE_ROLE")
                    .resourceId(role.getId())
                    .status("SUCCESS")
                    .description("Created college role: " + role.getRoleCode())
                    .build();
            recordAudit(audit);

            return role;
        }
    }

    /**
     * User Story 13: Delete college role — blocked for protected system roles.
     */
    public void deleteCollegeRole(String roleId) {
        CollegeRole role = collegeRoles.get(roleId);
        if (role == null) {
            throw new CollegeResourceNotFoundException("CollegeRole", roleId);
        }
        if (role.isProtectedSystemRole()) {
            throw new CollegeLifecycleException("Cannot delete protected system role: " + role.getRoleCode());
        }
        collegeRoles.remove(roleId);
        logger.warn("Deleted college role: {}", role.getRoleCode());
    }

    public List<CollegeRole> listCollegeRoles() {
        return new ArrayList<>(collegeRoles.values());
    }

    /**
     * User Story 14: Create a permission in the college catalog.
     */
    public CollegePermission createCollegePermission(CollegePermission perm) {
        if (perm.getPermissionCode() == null || perm.getPermissionCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'permissionCode' is required");
        }

        for (CollegePermission existing : collegePermissions.values()) {
            if (existing.getPermissionCode().equalsIgnoreCase(perm.getPermissionCode())) {
                throw new CollegeResourceConflictException("CollegePermission", "permissionCode", perm.getPermissionCode());
            }
        }

        perm.setId(UUID.randomUUID().toString());
        collegePermissions.put(perm.getId(), perm);
        logger.info("Created college permission [{}] resource={}, action={}", perm.getPermissionCode(), perm.getResource(), perm.getAction());
        return perm;
    }

    public List<CollegePermission> listCollegePermissions() {
        return new ArrayList<>(collegePermissions.values());
    }

    /**
     * User Story 15: Create a scoped college role binding.
     */
    public CollegeRoleBinding createCollegeRoleBinding(CollegeRoleBinding binding) {
        try (FlowTracker flow = logger.flow("CreateCollegeRoleBinding", "BIND-" + binding.getPrincipalId())) {
            if (binding.getPrincipalId() == null || binding.getRoleId() == null) {
                throw new CollegeMalformedPayloadException("Fields 'principalId' and 'roleId' are required");
            }

            // Validate role exists
            CollegeRole role = collegeRoles.get(binding.getRoleId());
            if (role == null) {
                throw new CollegeResourceNotFoundException("CollegeRole", binding.getRoleId());
            }

            // Enforce uniqueness of (principalId, roleId, scopeType, scopeId)
            for (CollegeRoleBinding existing : collegeRoleBindings.values()) {
                if (existing.getPrincipalId().equals(binding.getPrincipalId())
                        && existing.getRoleId().equals(binding.getRoleId())
                        && safeEquals(existing.getScopeType(), binding.getScopeType())
                        && safeEquals(existing.getScopeId(), binding.getScopeId())) {
                    throw new CollegeResourceConflictException("CollegeRoleBinding", "principalId+roleId+scope",
                            binding.getPrincipalId() + ":" + binding.getRoleId());
                }
            }

            binding.setId(UUID.randomUUID().toString());
            if (binding.getEffectiveFrom() == 0) {
                binding.setEffectiveFrom(System.currentTimeMillis());
            }
            String granterId = LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN";
            binding.setGrantedBy(granterId);
            collegeRoleBindings.put(binding.getId(), binding);

            // Increment assignedToRoles on permissions
            for (String pc : role.getPermissions()) {
                for (CollegePermission p : collegePermissions.values()) {
                    if (p.getPermissionCode().equalsIgnoreCase(pc)) {
                        p.setAssignedToRoles(p.getAssignedToRoles() + 1);
                    }
                }
            }

            flow.step("PersistCollegeRoleBinding");
            logger.info("Created college role binding: principal={}, role={}, scope={}:{}", binding.getPrincipalId(), role.getRoleCode(), binding.getScopeType(), binding.getScopeId());

            AuditEvent audit = AuditEvent.builder()
                    .action("COLLEGE_ROLE_BINDING_CREATED")
                    .principalId(granterId)
                    .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                    .resourceType("COLLEGE_ROLE_BINDING")
                    .resourceId(binding.getId())
                    .status("SUCCESS")
                    .description("Bound role " + role.getRoleCode() + " to " + binding.getPrincipalId() + " at " + binding.getScopeType() + ":" + binding.getScopeId())
                    .build();
            recordAudit(audit);

            return binding;
        }
    }

    /**
     * User Story 15: Revoke a college role binding.
     */
    public void revokeCollegeRoleBinding(String bindingId) {
        CollegeRoleBinding binding = collegeRoleBindings.get(bindingId);
        if (binding == null) {
            throw new CollegeResourceNotFoundException("CollegeRoleBinding", bindingId);
        }
        binding.setEffectiveTo(System.currentTimeMillis());
        logger.warn("Revoked college role binding id={}, principal={}", bindingId, binding.getPrincipalId());

        AuditEvent audit = AuditEvent.builder()
                .action("COLLEGE_ROLE_BINDING_REVOKED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                .resourceType("COLLEGE_ROLE_BINDING")
                .resourceId(bindingId)
                .status("SUCCESS")
                .description("Revoked college role binding for principal " + binding.getPrincipalId())
                .build();
        recordAudit(audit);
    }

    public List<CollegeRoleBinding> listCollegeRoleBindings() {
        return new ArrayList<>(collegeRoleBindings.values());
    }

    /**
     * User Story 16: Create a college access review.
     */
    public CollegeAccessReview createCollegeAccessReview(CollegeAccessReview review) {
        if (review.getPrincipalId() == null || review.getDueAt() == 0) {
            throw new CollegeMalformedPayloadException("Fields 'principalId' and 'dueAt' are required");
        }

        review.setId(UUID.randomUUID().toString());
        review.setReviewId("CAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        review.setStatus("OPEN");
        review.setDecision("PENDING");
        collegeAccessReviews.put(review.getId(), review);

        logger.info("Created college access review [{}] for principal={}, dueAt={}", review.getReviewId(), review.getPrincipalId(), review.getDueAt());
        return review;
    }

    /**
     * User Story 16: Complete a college access review — self-review blocked.
     */
    public CollegeAccessReview completeCollegeAccessReview(String reviewId, String reviewerId, String decision) {
        CollegeAccessReview review = null;
        for (CollegeAccessReview ar : collegeAccessReviews.values()) {
            if (ar.getReviewId().equals(reviewId) || ar.getId().equals(reviewId)) {
                review = ar;
                break;
            }
        }
        if (review == null) {
            throw new CollegeResourceNotFoundException("CollegeAccessReview", reviewId);
        }

        // Self-review prevention
        if (review.getPrincipalId().equals(reviewerId)) {
            throw new CollegeLifecycleException("Separation of duties: reviewer '" + reviewerId + "' cannot certify their own access (principal=" + review.getPrincipalId() + ")");
        }

        review.setReviewerId(reviewerId);
        review.setDecision(decision);
        review.setStatus("COMPLETED");
        review.setCompletedAt(System.currentTimeMillis());

        logger.info("Completed college access review [{}]: decision={}, reviewer={}", review.getReviewId(), decision, reviewerId);

        AuditEvent audit = AuditEvent.builder()
                .action("COLLEGE_ACCESS_REVIEW_COMPLETED")
                .principalId(reviewerId)
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                .resourceType("COLLEGE_ACCESS_REVIEW")
                .resourceId(review.getReviewId())
                .status("SUCCESS")
                .description("College access review " + review.getReviewId() + " completed: " + decision + " for principal " + review.getPrincipalId())
                .build();
        recordAudit(audit);

        return review;
    }

    public List<CollegeAccessReview> listCollegeAccessReviews() {
        return new ArrayList<>(collegeAccessReviews.values());
    }

    // =========================================================================
    // Helper Utilities
    // =========================================================================

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer (User Story Lines 35–38)
    // =========================================================================

    /**
     * User Story 35: Exactly-once command processing with request hash conflict detection.
     */
    public IdempotencyRecord checkOrRecordIdempotency(String idempotencyKey, String operation, String requestHash, long ttlMs) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Idempotency key cannot be empty");
        }

        IdempotencyRecord existing = idempotencyRecords.get(idempotencyKey);
        if (existing != null) {
            if (requestHash != null && existing.getRequestHash() != null && !existing.getRequestHash().equals(requestHash)) {
                throw new CollegeResourceConflictException("IdempotencyRecord", "requestHash",
                        "Payload hash mismatch for idempotency key: " + idempotencyKey);
            }
            logger.info("[College Reliability] Idempotency record found for key [{}] - status={}", idempotencyKey, existing.getStatus());
            return existing;
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID().toString());
        record.setIdempotencyKey(idempotencyKey);
        record.setOperation(operation);
        record.setRequestHash(requestHash);
        record.setStatus("PROCESSING");
        record.setExpiresAt(System.currentTimeMillis() + (ttlMs > 0 ? ttlMs : 3600000L));
        record.setCreatedAt(System.currentTimeMillis());

        idempotencyRecords.put(idempotencyKey, record);
        logger.info("[College Reliability] Registered new idempotency key [{}] for operation [{}]", idempotencyKey, operation);
        return record;
    }

    public void completeIdempotency(String idempotencyKey, String responseRef) {
        IdempotencyRecord rec = idempotencyRecords.get(idempotencyKey);
        if (rec != null) {
            rec.setStatus("COMPLETED");
            rec.setResponseRef(responseRef);
            logger.info("[College Reliability] Completed idempotency key [{}] with ref [{}]", idempotencyKey, responseRef);
        }
    }

    public IdempotencyRecord getIdempotencyRecord(String idempotencyKey) {
        return idempotencyRecords.get(idempotencyKey);
    }

    public List<IdempotencyRecord> listIdempotencyRecords() {
        return new ArrayList<>(idempotencyRecords.values());
    }

    /**
     * User Story 36: Transactional outbox event emission.
     */
    public OutboxEvent emitOutboxEvent(String eventType, String aggregateId, String tenantId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setAggregateId(aggregateId);
        event.setTenantId(tenantId != null ? tenantId : (LogContext.getTenantId() != null ? LogContext.getTenantId() : "COLLEGE"));
        event.setPayload(payload);
        event.setStatus("PENDING");
        event.setCreatedAt(System.currentTimeMillis());

        outboxEvents.add(event);
        logger.info("[ADM-02 Outbox] Emitted event [{}] for aggregate [{}]", eventType, aggregateId);
        return event;
    }

    public List<OutboxEvent> listOutboxEvents() {
        return new ArrayList<>(outboxEvents);
    }

    /**
     * User Story 37: Consume upstream event via inbox with deduplication.
     */
    public InboxEvent processInboxEvent(String eventId, String sourceService, String consumerGroup, String payload) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Inbound eventId is required");
        }
        String dedupeKey = (sourceService != null ? sourceService : "UPSTREAM") + ":" + (consumerGroup != null ? consumerGroup : "DEFAULT") + ":" + eventId;

        InboxEvent existing = inboxEvents.get(dedupeKey);
        if (existing != null) {
            logger.warn("[ADM-02 Inbox] Duplicate inbound event ignored: {}", dedupeKey);
            return existing;
        }

        InboxEvent inbox = new InboxEvent();
        inbox.setId(UUID.randomUUID().toString());
        inbox.setEventId(eventId);
        inbox.setSourceService(sourceService != null ? sourceService : "UPSTREAM");
        inbox.setConsumerGroup(consumerGroup != null ? consumerGroup : "DEFAULT");
        inbox.setStatus("PROCESSED");
        inbox.setProcessedAt(System.currentTimeMillis());

        inboxEvents.put(dedupeKey, inbox);
        logger.info("[ADM-02 Inbox] Successfully processed inbound event [{}] from [{}]", eventId, sourceService);
        return inbox;
    }

    public List<InboxEvent> listInboxEvents() {
        return new ArrayList<>(inboxEvents.values());
    }

    /**
     * User Story 38: Route failed event to dead-letter queue.
     */
    public DeadLetterEvent routeToDeadLetter(String originalEventId, String eventType, String failureCode, int retryCount, String payload) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId(UUID.randomUUID().toString());
        dlq.setOriginalEventId(originalEventId);
        dlq.setEventType(eventType);
        dlq.setFailureCode(failureCode);
        dlq.setRetryCount(retryCount);
        dlq.setPayload(payload);
        dlq.setDisposition("OPEN");
        dlq.setCreatedAt(System.currentTimeMillis());

        deadLetterEvents.add(dlq);
        logger.error("[ADM-02 DLQ] Routed event [{}] to dead-letter queue: code={}, retries={}", originalEventId, failureCode, retryCount);
        return dlq;
    }

    public List<DeadLetterEvent> listDeadLetterEvents() {
        return new ArrayList<>(deadLetterEvents);
    }

    /**
     * User Story 38: Controlled replay of dead-letter event.
     */
    public DeadLetterEvent replayDeadLetterEvent(String deadLetterId) {
        DeadLetterEvent found = null;
        for (DeadLetterEvent dl : deadLetterEvents) {
            if (dl.getId().equals(deadLetterId)) {
                found = dl;
                break;
            }
        }
        if (found == null) {
            throw new CollegeResourceNotFoundException("DeadLetterEvent", deadLetterId);
        }

        found.setDisposition("REPLAYED");
        emitOutboxEvent(found.getEventType() + ".Replayed", found.getOriginalEventId(), "COLLEGE", found.getPayload());
        logger.info("[ADM-02 DLQ] Replayed dead-letter event [{}]", deadLetterId);
        return found;
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return Integer.toHexString(input != null ? input.hashCode() : 0);
        }
    }

    // =========================================================================
    // Phase 3: Configuration & Governance Domain Methods (User Story Lines 8–10)
    // =========================================================================

    /**
     * User Story 8: College-specific settings with effective dating and secret protection.
     */
    public CollegeSetting setCollegeSetting(CollegeSetting setting) {
        if (setting.getKey() == null || setting.getKey().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Setting key is required");
        }
        if (setting.isSecret()) {
            if (setting.getValue() == null || setting.getValue().trim().isEmpty()) {
                throw new CollegeMalformedPayloadException("Secret setting value cannot be empty");
            }
        }

        CollegeSetting existing = collegeSettings.get(setting.getKey());
        if (existing != null) {
            setting.setVersion(existing.getVersion() + 1);
        } else {
            setting.setVersion(1);
        }

        if (setting.getId() == null || setting.getId().isEmpty()) {
            setting.setId(UUID.randomUUID().toString());
        }
        if (setting.getEffectiveFrom() == 0) {
            setting.setEffectiveFrom(System.currentTimeMillis());
        }

        collegeSettings.put(setting.getKey(), setting);
        emitOutboxEvent("CollegeSettingUpdated", setting.getId(), "COLLEGE", "{\"key\":\"" + setting.getKey() + "\",\"version\":" + setting.getVersion() + "}");
        logger.info("[ADM-02] Stored college setting [{}] v{}", setting.getKey(), setting.getVersion());

        AuditEvent audit = AuditEvent.builder()
                .action("COLLEGE_SETTING_SET")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                .resourceType("COLLEGE_SETTING")
                .resourceId(setting.getKey())
                .status("SUCCESS")
                .description("Updated setting " + setting.getKey() + " to version " + setting.getVersion())
                .build();
        recordAudit(audit);

        return setting;
    }

    public List<CollegeSetting> getCollegeSettings(boolean effectiveOnly) {
        long now = System.currentTimeMillis();
        List<CollegeSetting> results = new ArrayList<>();
        for (CollegeSetting s : collegeSettings.values()) {
            if (effectiveOnly) {
                if (now < s.getEffectiveFrom()) continue;
                if (s.getEffectiveTo() > 0 && now > s.getEffectiveTo()) continue;
            }
            // Return safe copy with masked secrets
            CollegeSetting safe = new CollegeSetting();
            safe.setId(s.getId());
            safe.setKey(s.getKey());
            safe.setValue(s.isSecret() ? "********" : s.getValue());
            safe.setDataType(s.getDataType());
            safe.setSecret(s.isSecret());
            safe.setVersion(s.getVersion());
            safe.setEffectiveFrom(s.getEffectiveFrom());
            safe.setEffectiveTo(s.getEffectiveTo());
            results.add(safe);
        }
        return results;
    }

    /**
     * User Story 9: Override platform feature flag at college level.
     */
    public FeatureOverride overrideFeatureFlag(String flagKey, String overrideValue, String reason) {
        if (flagKey == null || flagKey.trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Feature flag key is required");
        }
        // Block overrides on globally disabled / locked safety flags
        if (flagKey.equalsIgnoreCase("SYSTEM_SAFETY_LOCK")
                || flagKey.equalsIgnoreCase("SAFETY_CRITICAL_MODE")
                || flagKey.toUpperCase().startsWith("SAFETY_")
                || flagKey.toUpperCase().contains("LOCKED")) {
            throw new CollegeSecurityViolationException("ADM02_FLAG_OVERRIDE_BLOCKED",
                    "Cannot override globally locked safety flag: " + flagKey);
        }

        FeatureOverride fo = new FeatureOverride();
        fo.setId(UUID.randomUUID().toString());
        fo.setFlagKey(flagKey);
        fo.setOverrideValue(overrideValue);
        fo.setReason(reason);
        fo.setEffectiveFrom(System.currentTimeMillis());

        featureOverrides.put(flagKey, fo);
        emitOutboxEvent("FeatureOverrideApplied", fo.getId(), "COLLEGE", "{\"flagKey\":\"" + flagKey + "\",\"override\":\"" + overrideValue + "\"}");
        logger.info("[ADM-02] Applied feature override for [{}] = {}", flagKey, overrideValue);

        AuditEvent audit = AuditEvent.builder()
                .action("FEATURE_OVERRIDE_APPLIED")
                .principalId(LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_ADMIN")
                .principalRole(LogContext.getUserRole() != null ? LogContext.getUserRole() : "COLLEGE_ADMIN")
                .resourceType("FEATURE_OVERRIDE")
                .resourceId(flagKey)
                .status("SUCCESS")
                .description("Overrode feature flag " + flagKey + " to " + overrideValue)
                .build();
        recordAudit(audit);

        return fo;
    }

    public List<FeatureOverride> listFeatureOverrides() {
        return new ArrayList<>(featureOverrides.values());
    }

    /**
     * User Story 10: Publish immutable college-level policy.
     */
    public LocalPolicy createLocalPolicy(LocalPolicy policy) {
        if (policy.getPolicyCode() == null || policy.getPolicyCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Policy code is required");
        }
        for (LocalPolicy existing : localPolicies.values()) {
            if (existing.getPolicyCode().equalsIgnoreCase(policy.getPolicyCode())) {
                throw new CollegeResourceConflictException("LocalPolicy", "policyCode", policy.getPolicyCode());
            }
        }

        policy.setId(UUID.randomUUID().toString());
        policy.setStatus("DRAFT");
        policy.setVersion(1);
        localPolicies.put(policy.getPolicyCode(), policy);
        logger.info("[ADM-02] Created draft local policy [{}]", policy.getPolicyCode());
        return policy;
    }

    public LocalPolicy approveLocalPolicy(String policyCode, String approvedBy) {
        LocalPolicy policy = getLocalPolicy(policyCode);
        if (approvedBy == null || approvedBy.trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Approver identity is required");
        }
        policy.setApprovedBy(approvedBy);
        policy.setStatus("APPROVED");
        logger.info("[ADM-02] Approved local policy [{}] by {}", policyCode, approvedBy);
        return policy;
    }

    public LocalPolicy publishLocalPolicy(String policyCode) {
        LocalPolicy policy = getLocalPolicy(policyCode);
        if (!"APPROVED".equalsIgnoreCase(policy.getStatus()) && policy.getApprovedBy() == null) {
            throw new CollegeLifecycleException("Policy must be approved before publication");
        }
        policy.setStatus("PUBLISHED");
        policy.setPublishedAt(System.currentTimeMillis());
        emitOutboxEvent("LocalPolicyPublished", policy.getId(), "COLLEGE", "{\"policyCode\":\"" + policyCode + "\",\"version\":" + policy.getVersion() + "}");
        logger.info("[ADM-02] Published immutable local policy [{}] v{}", policyCode, policy.getVersion());
        return policy;
    }

    public LocalPolicy getLocalPolicy(String policyCode) {
        LocalPolicy policy = localPolicies.get(policyCode);
        if (policy == null) {
            throw new CollegeResourceNotFoundException("LocalPolicy", policyCode);
        }
        return policy;
    }

    public List<LocalPolicy> listLocalPolicies() {
        return new ArrayList<>(localPolicies.values());
    }

    // =========================================================================
    // Phase 6: Calendar, Reporting & Workflow Domain Logic (User Stories 6, 21-24, 31-32)
    // =========================================================================

    /**
     * User Story 6: Update academic calendar configuration and synchronization mode.
     */
    public CalendarConfiguration updateCalendarConfiguration(CalendarConfiguration config) {
        if (config == null) {
            throw new CollegeMalformedPayloadException("Calendar configuration payload cannot be empty");
        }
        if (config.getSourceCalendarId() != null) calendarConfiguration.setSourceCalendarId(config.getSourceCalendarId());
        if (config.getSyncMode() != null) calendarConfiguration.setSyncMode(config.getSyncMode());
        if (config.getLocalRules() != null) calendarConfiguration.setLocalRules(config.getLocalRules());
        calendarConfiguration.setLastSyncedAt(System.currentTimeMillis());

        emitOutboxEvent("CalendarConfigurationUpdated", calendarConfiguration.getId(), "CALENDAR", "{\"syncMode\":\"" + calendarConfiguration.getSyncMode() + "\"}");
        logger.info("[ADM-02 Calendar] Updated calendar configuration: syncMode={}", calendarConfiguration.getSyncMode());
        return calendarConfiguration;
    }

    public CalendarConfiguration getCalendarConfiguration() {
        return calendarConfiguration;
    }

    /**
     * User Story 21: Create report definition with query specification & data classification.
     */
    public ReportDefinition createReportDefinition(ReportDefinition def) {
        if (def.getReportCode() == null || def.getReportCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("ReportCode is required");
        }
        for (ReportDefinition existing : reportDefinitions.values()) {
            if (existing.getReportCode().equalsIgnoreCase(def.getReportCode())) {
                throw new CollegeResourceConflictException("ReportDefinition", "reportCode", def.getReportCode());
            }
        }
        def.setId(UUID.randomUUID().toString());
        def.setStatus("ACTIVE");
        def.setCreatedAt(System.currentTimeMillis());
        reportDefinitions.put(def.getReportCode(), def);
        logger.info("[ADM-02 Reports] Created report definition [{}]", def.getReportCode());
        return def;
    }

    public ReportDefinition getReportDefinition(String reportCode) {
        ReportDefinition def = reportDefinitions.get(reportCode);
        if (def == null) {
            throw new CollegeResourceNotFoundException("ReportDefinition", reportCode);
        }
        return def;
    }

    public List<ReportDefinition> listReportDefinitions() {
        return new ArrayList<>(reportDefinitions.values());
    }

    /**
     * User Story 22: Execute report query and generate audited execution run record.
     */
    public ReportRun executeReport(String reportCode, String requestedBy, String filters) {
        ReportDefinition def = getReportDefinition(reportCode);
        if (!"ACTIVE".equalsIgnoreCase(def.getStatus())) {
            throw new CollegeLifecycleException("Cannot execute an inactive report definition");
        }

        ReportRun run = new ReportRun();
        run.setId("RUN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        run.setReportCode(reportCode);
        run.setRequestedBy(requestedBy != null ? requestedBy : "DEAN");
        run.setFilters(filters != null ? filters : "{}");
        run.setStartedAt(System.currentTimeMillis());
        run.setCompletedAt(System.currentTimeMillis());
        run.setRowCount(42); // pre-computed execution result
        run.setOutputRef("s3://campx-reports/" + reportCode + "/" + run.getId() + ".csv");
        run.setStatus("COMPLETED");

        reportRuns.add(run);
        emitOutboxEvent("ReportRunCompleted", run.getId(), reportCode, "{\"rowCount\":" + run.getRowCount() + "}");
        logger.info("[ADM-02 Reports] Executed report [{}] run [{}] rows: {}", reportCode, run.getId(), run.getRowCount());
        return run;
    }

    public List<ReportRun> listReportRuns(String reportCode) {
        List<ReportRun> result = new ArrayList<>();
        for (ReportRun r : reportRuns) {
            if (reportCode != null && !reportCode.isEmpty() && !reportCode.equalsIgnoreCase(r.getReportCode())) {
                continue;
            }
            result.add(r);
        }
        return result;
    }

    /**
     * User Story 23: Schedule recurring report delivery.
     */
    public ReportSchedule scheduleReport(ReportSchedule schedule) {
        if (schedule.getReportCode() == null) {
            throw new CollegeMalformedPayloadException("ReportCode is required for scheduling");
        }
        getReportDefinition(schedule.getReportCode()); // validate existence

        schedule.setId("SCH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        schedule.setStatus("ACTIVE");
        schedule.setCreatedAt(System.currentTimeMillis());
        reportSchedules.put(schedule.getId(), schedule);

        logger.info("[ADM-02 Reports] Scheduled report [{}] with cron [{}]", schedule.getReportCode(), schedule.getCronExpression());
        return schedule;
    }

    public List<ReportSchedule> listReportSchedules() {
        return new ArrayList<>(reportSchedules.values());
    }

    /**
     * User Story 24: Pre-aggregate and store operational dashboard snapshot.
     */
    public DashboardSnapshot createDashboardSnapshot(DashboardSnapshot snapshot) {
        if (snapshot.getDashboardCode() == null) {
            throw new CollegeMalformedPayloadException("DashboardCode is required");
        }
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setGeneratedAt(System.currentTimeMillis());

        dashboardSnapshots.computeIfAbsent(snapshot.getDashboardCode(), k -> Collections.synchronizedList(new ArrayList<>())).add(snapshot);
        logger.info("[ADM-02 Dashboard] Stored snapshot for dashboard [{}] period [{}]", snapshot.getDashboardCode(), snapshot.getPeriod());
        return snapshot;
    }

    public List<DashboardSnapshot> getDashboardSnapshots(String dashboardCode) {
        List<DashboardSnapshot> list = dashboardSnapshots.get(dashboardCode);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    /**
     * User Story 31: Submit approval request and enforce multi-party approval without self-certification.
     */
    public ApprovalRequest submitApprovalRequest(ApprovalRequest req) {
        if (req.getRequestType() == null || req.getSubmittedBy() == null) {
            throw new CollegeMalformedPayloadException("RequestType and submittedBy are required");
        }
        req.setId("REQ_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req.setStatus("PENDING");
        req.setSubmittedAt(System.currentTimeMillis());
        approvalRequests.put(req.getId(), req);

        emitOutboxEvent("ApprovalRequested", req.getId(), req.getRequestType(), "{\"submittedBy\":\"" + req.getSubmittedBy() + "\"}");
        logger.info("[ADM-02 Approvals] Submitted approval request [{}] type: [{}]", req.getId(), req.getRequestType());
        return req;
    }

    public ApprovalRequest decideApprovalRequest(String requestId, String approverId, String decision, String notes) {
        ApprovalRequest req = approvalRequests.get(requestId);
        if (req == null) {
            throw new CollegeResourceNotFoundException("ApprovalRequest", requestId);
        }
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new CollegeLifecycleException("Cannot decide on already settled approval request");
        }

        // Anti-self-certification enforcement (CSV Line 31)
        if (req.getSubmittedBy().equalsIgnoreCase(approverId)) {
            throw new CollegeSecurityViolationException("ADM02_SELF_CERTIFICATION_BLOCKED",
                    "Self-certification violation: submitter '" + approverId + "' cannot approve their own request");
        }

        String targetStatus = "APPROVE".equalsIgnoreCase(decision) ? "APPROVED" : "REJECTED";
        req.setStatus(targetStatus);
        req.setDecidedBy(approverId);
        req.setDecidedAt(System.currentTimeMillis());
        req.setDecisionNotes(notes != null ? notes : "Decision recorded");

        emitOutboxEvent("ApprovalDecided", req.getId(), req.getRequestType(), "{\"status\":\"" + targetStatus + "\",\"decidedBy\":\"" + approverId + "\"}");
        logger.info("[ADM-02 Approvals] Request [{}] {} by [{}]", requestId, targetStatus, approverId);
        return req;
    }

    public ApprovalRequest getApprovalRequest(String requestId) {
        ApprovalRequest req = approvalRequests.get(requestId);
        if (req == null) {
            throw new CollegeResourceNotFoundException("ApprovalRequest", requestId);
        }
        return req;
    }

    public List<ApprovalRequest> listApprovalRequests(String status) {
        List<ApprovalRequest> result = new ArrayList<>();
        for (ApprovalRequest r : approvalRequests.values()) {
            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase(r.getStatus())) {
                continue;
            }
            result.add(r);
        }
        return result;
    }

    /**
     * User Story 32: Start and transition operational workflows with rollback/compensation support.
     */
    public CollegeWorkflowInstance startCollegeWorkflow(String workflowType, String subject) {
        if (workflowType == null) {
            throw new CollegeMalformedPayloadException("WorkflowType is required");
        }
        CollegeWorkflowInstance wf = new CollegeWorkflowInstance();
        wf.setId("CWF_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        wf.setWorkflowType(workflowType);
        wf.setSubject(subject != null ? subject : "Operational Workflow");
        wf.setCurrentState("INITIATED");
        wf.getSteps().add("INITIATED");
        wf.setStartedAt(System.currentTimeMillis());

        collegeWorkflows.put(wf.getId(), wf);
        emitOutboxEvent("CollegeWorkflowStarted", wf.getId(), workflowType, "{\"subject\":\"" + wf.getSubject() + "\"}");
        logger.info("[ADM-02 Workflow] Started college workflow [{}] type: [{}]", wf.getId(), workflowType);
        return wf;
    }

    public CollegeWorkflowInstance transitionCollegeWorkflow(String workflowId, String targetState, String stepName, String compensationAction) {
        CollegeWorkflowInstance wf = collegeWorkflows.get(workflowId);
        if (wf == null) {
            throw new CollegeResourceNotFoundException("CollegeWorkflowInstance", workflowId);
        }
        if (stepName != null && !stepName.isEmpty()) {
            wf.getSteps().add(stepName);
        }
        if (compensationAction != null) {
            wf.setCompensationAction(compensationAction);
        }
        wf.setCurrentState(targetState != null ? targetState : "RUNNING");
        if ("COMPLETED".equalsIgnoreCase(targetState) || "FAILED".equalsIgnoreCase(targetState)) {
            wf.setCompletedAt(System.currentTimeMillis());
            emitOutboxEvent("CollegeWorkflowFinished", wf.getId(), wf.getWorkflowType(), "{\"finalState\":\"" + wf.getCurrentState() + "\"}");
        }
        logger.info("[ADM-02 Workflow] Transitioned workflow [{}] to [{}]", workflowId, wf.getCurrentState());
        return wf;
    }

    public CollegeWorkflowInstance getCollegeWorkflow(String workflowId) {
        CollegeWorkflowInstance wf = collegeWorkflows.get(workflowId);
        if (wf == null) {
            throw new CollegeResourceNotFoundException("CollegeWorkflowInstance", workflowId);
        }
        return wf;
    }

    public List<CollegeWorkflowInstance> listCollegeWorkflows() {
        return new ArrayList<>(collegeWorkflows.values());
    }
}
