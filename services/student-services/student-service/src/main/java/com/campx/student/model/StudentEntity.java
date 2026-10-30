package com.campx.student.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity mapping directly to {@code stu.students} in PostgreSQL.
 * Represents the institutional academic anchor for a student.
 */
public class StudentEntity {

    private UUID id;
    private UUID tenantId;
    private UUID personId;
    private UUID collegeId;
    private UUID programId;
    private String enrollmentNo;
    private String admissionNo;
    private LocalDate admissionDate;
    private UUID admissionAcademicYearId;
    private StudentStatus status = StudentStatus.ACTIVE;
    private String verificationStatus = "UNVERIFIED";
    private LocalDate verificationExpiry;
    private UUID iamUserId;
    private String preferences;
    private int rowVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime deletedAt;

    public StudentEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }

    public LocalDate getAdmissionDate() { return admissionDate; }
    public void setAdmissionDate(LocalDate admissionDate) { this.admissionDate = admissionDate; }

    public UUID getAdmissionAcademicYearId() { return admissionAcademicYearId; }
    public void setAdmissionAcademicYearId(UUID admissionAcademicYearId) { this.admissionAcademicYearId = admissionAcademicYearId; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public LocalDate getVerificationExpiry() { return verificationExpiry; }
    public void setVerificationExpiry(LocalDate verificationExpiry) { this.verificationExpiry = verificationExpiry; }

    public UUID getIamUserId() { return iamUserId; }
    public void setIamUserId(UUID iamUserId) { this.iamUserId = iamUserId; }

    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }

    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
