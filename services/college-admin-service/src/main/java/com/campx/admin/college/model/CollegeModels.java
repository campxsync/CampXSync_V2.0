package com.campx.admin.college.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain models for ADM-02: College Admin Service (College Operational Tier).
 */
public final class CollegeModels {

    public static class CollegeProfile {
        private String collegeCode;
        private String legalName;
        private String displayName;
        private List<String> accreditationRefs = new ArrayList<>();
        private String address;
        private String status = "ACTIVE"; // ACTIVE, SUSPENDED
        private int currentVersion = 1;
        private long updatedAt = System.currentTimeMillis();

        public String getCollegeCode() { return collegeCode; }
        public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
        public String getLegalName() { return legalName; }
        public void setLegalName(String legalName) { this.legalName = legalName; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public List<String> getAccreditationRefs() { return accreditationRefs; }
        public void setAccreditationRefs(List<String> accreditationRefs) { this.accreditationRefs = accreditationRefs; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class Department {
        private String id;
        private String departmentCode;
        private String name;
        private String headUserId;
        private String status = "ACTIVE"; // ACTIVE, RETIRED
        private long createdAt = System.currentTimeMillis();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDepartmentCode() { return departmentCode; }
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getHeadUserId() { return headUserId; }
        public void setHeadUserId(String headUserId) { this.headUserId = headUserId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getCreatedAt() { return createdAt; }
    }

    public static class Program {
        private String id;
        private String programCode;
        private String name;
        private String departmentId;
        private int durationYears;
        private int version = 1;
        private boolean published = true;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProgramCode() { return programCode; }
        public void setProgramCode(String programCode) { this.programCode = programCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDepartmentId() { return departmentId; }
        public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
        public int getDurationYears() { return durationYears; }
        public void setDurationYears(int durationYears) { this.durationYears = durationYears; }
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }
        public boolean isPublished() { return published; }
        public void setPublished(boolean published) { this.published = published; }
    }

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

        public String getImportId() { return importId; }
        public void setImportId(String importId) { this.importId = importId; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getFileRef() { return fileRef; }
        public void setFileRef(String fileRef) { this.fileRef = fileRef; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getProcessedRows() { return processedRows; }
        public void setProcessedRows(int processedRows) { this.processedRows = processedRows; }
        public int getFailedRows() { return failedRows; }
        public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
        public List<String> getErrorLogs() { return errorLogs; }
        public long getCreatedAt() { return createdAt; }
    }

    public static class GovernanceDocument {
        private String id;
        private String documentType; // POLICY, SYLLABUS, ACCREDITATION_REPORT
        private String title;
        private String ownerId;
        private String classification; // PUBLIC, INTERNAL, CONFIDENTIAL
        private int currentVersion = 1;
        private String status = "DRAFT"; // DRAFT -> SUBMITTED -> APPROVED / REJECTED -> PUBLISHED
        private String checksum;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public String getClassification() { return classification; }
        public void setClassification(String classification) { this.classification = classification; }
        public int getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(int currentVersion) { this.currentVersion = currentVersion; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getChecksum() { return checksum; }
        public void setChecksum(String checksum) { this.checksum = checksum; }
    }
}
