package com.campx.student.model;

import java.util.UUID;

/**
 * Minimized, privacy-safe egress projection for downstream and intelligence services (STM01-US-044).
 * Excludes sensitive personal data, phone numbers, email, addresses, and document paths.
 */
public class StudentProjectionResponse {

    private UUID studentId;
    private UUID tenantId;
    private UUID collegeId;
    private UUID programId;
    private String enrollmentNo;
    private String displayName;
    private StudentStatus status;
    private String verificationStatus;

    public StudentProjectionResponse() {}

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCollegeId() { return collegeId; }
    public void setCollegeId(UUID collegeId) { this.collegeId = collegeId; }

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public String getEnrollmentNo() { return enrollmentNo; }
    public void setEnrollmentNo(String enrollmentNo) { this.enrollmentNo = enrollmentNo; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
}
