package com.campx.student.model;

import java.util.UUID;

/**
 * Service contract emitted by Student Domain Service to Academics Domain Service (ACD-04).
 * Strictly preserves domain boundaries: student-service does not directly mutate
 * {@code acd.batch_students}, but delegates cohort linking through this contract.
 */
public class BatchAssignmentContract {

    private final UUID tenantId;
    private final UUID collegeId;
    private final UUID studentId;
    private final UUID batchId;
    private final String rollNo;
    private final String action; // ASSIGN, TRANSFER, REMOVE

    public BatchAssignmentContract(UUID tenantId, UUID collegeId, UUID studentId, UUID batchId, String rollNo, String action) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId cannot be null");
        if (studentId == null) throw new IllegalArgumentException("studentId cannot be null");
        if (batchId == null) throw new IllegalArgumentException("batchId cannot be null");
        this.tenantId = tenantId;
        this.collegeId = collegeId;
        this.studentId = studentId;
        this.batchId = batchId;
        this.rollNo = rollNo;
        this.action = action != null ? action : "ASSIGN";
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getCollegeId() { return collegeId; }
    public UUID getStudentId() { return studentId; }
    public UUID getBatchId() { return batchId; }
    public String getRollNo() { return rollNo; }
    public String getAction() { return action; }

    @Override
    public String toString() {
        return "BatchAssignmentContract{" +
                "tenantId=" + tenantId +
                ", collegeId=" + collegeId +
                ", studentId=" + studentId +
                ", batchId=" + batchId +
                ", rollNo='" + rollNo + '\'' +
                ", action='" + action + '\'' +
                '}';
    }
}
