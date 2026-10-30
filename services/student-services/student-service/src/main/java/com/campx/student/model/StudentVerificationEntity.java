package com.campx.student.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing verification steps for a student profile (STM01-US-013, STM01-US-014).
 * Verification lifecycle is distinct from academic lifecycle state.
 */
public class StudentVerificationEntity {

    private UUID id;
    private UUID tenantId;
    private UUID studentId;
    private String verificationType; // e.g. "IDENTITY", "ACADEMIC", "ADDRESS", "DOCUMENT"
    private String status = "PENDING"; // 'PENDING', 'VERIFIED', 'REJECTED', 'CORRECTION_REQUIRED'
    private String evidenceReference;
    private UUID verifiedBy;
    private OffsetDateTime verifiedAt;
    private LocalDate validTo; // Verification expiry (STM01-US-047)
    private String reason;
    private int rowVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public StudentVerificationEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String verificationType) { this.verificationType = verificationType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; }

    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }

    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(OffsetDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isExpired() {
        return validTo != null && validTo.isBefore(LocalDate.now());
    }
}
