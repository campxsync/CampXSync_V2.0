package com.campx.student.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight DTO for cursor-paginated roster search and listing.
 */
public class StudentRosterItem {

    private UUID id;
    private String enrollmentNo;
    private String admissionNo;
    private StudentStatus status;
    private UUID collegeId;
    private UUID programId;
    private String programName;
    private String programCode;
    private UUID batchId;
    private String batchName;
    private String batchCode;
    private String rollNo;
    private String displayName;
    private String firstName;
    private String lastName;
    private String primaryEmail;
    private String createdAt;

    public StudentRosterItem() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public String getProgramName() { return programName; }
    public void setProgramName(String programName) { this.programName = programName; }

    public String getProgramCode() { return programCode; }
    public void setProgramCode(String programCode) { this.programCode = programCode; }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public String getBatchName() { return batchName; }
    public void setBatchName(String batchName) { this.batchName = batchName; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPrimaryEmail() { return primaryEmail; }
    public void setPrimaryEmail(String primaryEmail) { this.primaryEmail = primaryEmail; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt != null ? createdAt.toString() : null;
    }
}
