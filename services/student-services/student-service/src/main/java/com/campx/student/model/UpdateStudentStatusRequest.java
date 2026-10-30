package com.campx.student.model;

/**
 * Ingress DTO for updating student lifecycle status.
 */
public class UpdateStudentStatusRequest {

    private StudentStatus status;
    private String reason;

    public UpdateStudentStatusRequest() {}

    public UpdateStudentStatusRequest(StudentStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public StudentStatus getStatus() { return status; }
    public void setStatus(StudentStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
