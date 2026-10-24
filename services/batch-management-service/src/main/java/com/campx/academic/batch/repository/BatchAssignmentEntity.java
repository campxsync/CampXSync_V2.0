package com.campx.academic.batch.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Authoritative relational entity mapping directly to table {@code acd.batch_students}.
 */
public class BatchAssignmentEntity {

    private UUID id;
    private UUID tenantId;
    private UUID batchId;
    private UUID studentId;
    private String rollNo;
    private LocalDate joinedOn;
    private String status = "ACTIVE";
    private int rowVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    // Enriched read-back fields for caller convenience
    private String studentName;
    private String enrollmentNo;
    private String batchName;
    private String batchCode;
    private UUID collegeId;

    public BatchAssignmentEntity() {}

    public BatchAssignmentEntity(UUID id, UUID tenantId, UUID batchId, UUID studentId, String rollNo) {
        this.id = id;
        this.tenantId = tenantId;
        this.batchId = batchId;
        this.studentId = studentId;
        this.rollNo = rollNo;
        this.status = "ACTIVE";
        this.joinedOn = LocalDate.now();
        this.rowVersion = 1;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }
    public LocalDate getJoinedOn() { return joinedOn; }
    public void setJoinedOn(LocalDate joinedOn) { this.joinedOn = joinedOn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }
    public String getBatchName() { return batchName; }
    public void setBatchName(String batchName) { this.batchName = batchName; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    @Override
    public String toString() {
        return "BatchAssignmentEntity{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", batchId=" + batchId +
                ", studentId=" + studentId +
                ", rollNo='" + rollNo + '\'' +
                ", status='" + status + '\'' +
                ", rowVersion=" + rowVersion +
                '}';
    }
}
