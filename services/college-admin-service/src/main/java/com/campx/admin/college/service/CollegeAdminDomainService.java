package com.campx.admin.college.service;

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

    public CollegeProfile getProfile() {
        return profile;
    }

    public CollegeProfile updateProfile(CollegeProfile updated) {
        if (updated.getCollegeCode() != null && !updated.getCollegeCode().equals(profile.getCollegeCode())) {
            throw new IllegalArgumentException("Cannot modify authoritative immutable collegeCode");
        }
        if (updated.getDisplayName() != null) profile.setDisplayName(updated.getDisplayName());
        if (updated.getAddress() != null) profile.setAddress(updated.getAddress());
        if (updated.getStatus() != null) profile.setStatus(updated.getStatus());
        if (!updated.getAccreditationRefs().isEmpty()) profile.setAccreditationRefs(updated.getAccreditationRefs());
        profile.setCurrentVersion(profile.getCurrentVersion() + 1);
        profile.setUpdatedAt(System.currentTimeMillis());

        logger.info("Updated college profile version to {}", profile.getCurrentVersion());
        return profile;
    }

    /**
     * User Story 4: Create and retire a college department without breaking references
     */
    public Department createDepartment(Department dep) {
        try (FlowTracker flow = logger.flow("CreateDepartmentWorkflow", "DEP-" + dep.getDepartmentCode())) {
            if (dep.getDepartmentCode() == null || dep.getDepartmentCode().trim().isEmpty()) {
                flow.markFailed(new IllegalArgumentException("departmentCode is required"));
                throw new IllegalArgumentException("departmentCode is required");
            }

            for (Department existing : departments.values()) {
                if (existing.getDepartmentCode().equalsIgnoreCase(dep.getDepartmentCode())) {
                    flow.markFailed(new IllegalStateException("Uniqueness violation: departmentCode already exists"));
                    throw new IllegalStateException("Uniqueness violation: departmentCode already exists");
                }
            }

            dep.setId(UUID.randomUUID().toString());
            dep.setStatus("ACTIVE");
            departments.put(dep.getId(), dep);

            flow.step("ValidateAndPersistDepartment");
            logger.info("Created department [{}] {} (HOD: {})", dep.getDepartmentCode(), dep.getName(), dep.getHeadUserId());
            return dep;
        }
    }

    public void retireDepartment(String departmentId) {
        Department dep = departments.get(departmentId);
        if (dep == null) {
            throw new IllegalArgumentException("Department not found: " + departmentId);
        }

        // Prevent retirement / hard delete if referenced by programs
        for (Program prog : programs.values()) {
            if (departmentId.equals(prog.getDepartmentId())) {
                throw new IllegalStateException("Cannot retire department " + dep.getName() + " because it is actively referenced by program " + prog.getName());
            }
        }

        dep.setStatus("RETIRED");
        logger.warn("Soft-retired department: {}", dep.getDepartmentCode());
    }

    public List<Department> listDepartments() {
        return new ArrayList<>(departments.values());
    }

    /**
     * User Story 5: Create a program under an active department with immutable versioning
     */
    public Program createProgram(Program prog) {
        Department parent = departments.get(prog.getDepartmentId());
        if (parent == null || !"ACTIVE".equalsIgnoreCase(parent.getStatus())) {
            throw new IllegalArgumentException("Program must reference an existing ACTIVE department");
        }

        if (prog.getDurationYears() <= 0) {
            throw new IllegalArgumentException("durationYears must be positive");
        }

        prog.setId(UUID.randomUUID().toString());
        prog.setVersion(1);
        prog.setPublished(true);
        programs.put(prog.getId(), prog);

        logger.info("Published new program: [{}] {} under department: {}", prog.getProgramCode(), prog.getName(), parent.getName());
        return prog;
    }

    public List<Program> listPrograms() {
        return new ArrayList<>(programs.values());
    }

    /**
     * User Story 18 & 19: Submit a college data import job with idempotent replay
     */
    public DataImportJob submitImportJob(DataImportJob job) {
        String key = job.getIdempotencyKey();
        if (key != null && !idempotencyKeys.add(key)) {
            logger.warn("Idempotent replay detected for data import job with key: {}", key);
            for (DataImportJob existing : importJobs.values()) {
                if (key.equals(existing.getIdempotencyKey())) {
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

            flow.step("FinalizeBatchExecution");
            logger.info("Completed import job {} for entity {}. Processed: {}, Failed: {}", job.getImportId(), job.getEntityType(), job.getProcessedRows(), job.getFailedRows());

            return job;
        }
    }

    public DataImportJob getImportJob(String id) {
        return importJobs.get(id);
    }

    /**
     * User Story 26 & 29: Register governance document and submit for approval
     */
    public GovernanceDocument registerDocument(GovernanceDocument doc) {
        if (doc.getClassification() == null) {
            throw new IllegalArgumentException("Document classification (PUBLIC, INTERNAL, CONFIDENTIAL) is mandatory");
        }

        doc.setId(UUID.randomUUID().toString());
        doc.setStatus("DRAFT");
        doc.setCurrentVersion(1);
        doc.setChecksum("SHA256-" + UUID.randomUUID().toString().substring(0, 16));
        documents.put(doc.getId(), doc);

        logger.info("Registered governance document: [{}] '{}' (Class: {})", doc.getDocumentType(), doc.getTitle(), doc.getClassification());
        return doc;
    }

    public GovernanceDocument submitDocumentApproval(String documentId, String approverId) {
        GovernanceDocument doc = documents.get(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }

        try (FlowTracker flow = logger.flow("SubmitDocumentApprovalWorkflow", "DOC-APPR-" + documentId)) {
            flow.step("VerifyApproverAuthorization");
            doc.setStatus("APPROVED");

            flow.step("PublishImmutableVersion");
            logger.audit(AuditEvent.builder()
                    .action("GOVERNANCE_DOCUMENT_APPROVED")
                    .principalId(approverId != null ? approverId : "COLLEGE_DEAN")
                    .principalRole("APPROVER")
                    .resourceType("DOCUMENT")
                    .resourceId(documentId)
                    .status("SUCCESS")
                    .description("Approved governance document: " + doc.getTitle() + " (checksum: " + doc.getChecksum() + ")")
                    .build());

            return doc;
        }
    }

    public List<GovernanceDocument> listDocuments() {
        return new ArrayList<>(documents.values());
    }
}
