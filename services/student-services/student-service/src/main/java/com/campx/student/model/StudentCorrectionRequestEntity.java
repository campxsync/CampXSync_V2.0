package com.campx.student.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a student profile correction request (STM01-US-010, STM01-US-011).
 * Allows students to request corrections to protected profile fields (legal name, DOB, etc.)
 * subject to review and segregation of duties.
 */
public class StudentCorrectionRequestEntity {

    private UUID id;
    private UUID tenantId;
    private UUID studentId;
    private UUID personId;
    private UUID requestedBy;
    private String targetField; // e.g. "legalName", "dateOfBirth", "gender", "primaryEmail"
    private String oldValue;
    private String newValue;
    private String reason;
    private UUID evidenceDocumentId;
    private String status = "PENDING"; // 'PENDING', 'APPROVED', 'REJECTED'
    private UUID decidedBy;
    private String decisionReason;
    private OffsetDateTime decidedAt;
    private int rowVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public StudentCorrectionRequestEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public UUID getPersonId() { return personId; }
    public void setPersonId(UUID personId) { this.personId = personId; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getEvidenceDocumentId() { return evidenceDocumentId; }
    public void setEvidenceDocumentId(UUID evidenceDocumentId) { this.evidenceDocumentId = evidenceDocumentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID decidedBy) { this.decidedBy = decidedBy; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }

    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
