package com.campx.student.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity mapping directly to {@code stu.guardianships} in PostgreSQL.
 * Represents family/legal guardianship relationships.
 */
public class GuardianshipEntity {

    private UUID id;
    private UUID tenantId;
    private UUID studentId;
    private UUID guardianPersonId;
    private String relationship; // e.g. FATHER, MOTHER, LEGAL_GUARDIAN
    private boolean isPrimary = false;
    private boolean canViewAcademics = true;
    private boolean canViewFees = true;
    private boolean canPayFees = false;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status = "ACTIVE";
    private int rowVersion = 1;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private OffsetDateTime deletedAt;

    public GuardianshipEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public UUID getGuardianPersonId() { return guardianPersonId; }
    public void setGuardianPersonId(UUID guardianPersonId) { this.guardianPersonId = guardianPersonId; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }

    public boolean isCanViewAcademics() { return canViewAcademics; }
    public void setCanViewAcademics(boolean canViewAcademics) { this.canViewAcademics = canViewAcademics; }

    public boolean isCanViewFees() { return canViewFees; }
    public void setCanViewFees(boolean canViewFees) { this.canViewFees = canViewFees; }

    public boolean isCanPayFees() { return canPayFees; }
    public void setCanPayFees(boolean canPayFees) { this.canPayFees = canPayFees; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
