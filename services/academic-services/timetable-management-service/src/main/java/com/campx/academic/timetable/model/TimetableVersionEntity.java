package com.campx.academic.timetable.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity mapped to PostgreSQL table {@code acd.timetable_versions}.
 */
public class TimetableVersionEntity {

    private UUID id;
    private UUID tenantId;
    private UUID timetableId;
    private int versionNo;
    private String status;
    private OffsetDateTime publishedAt;
    private String changeNote;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private int rowVersion = 1;
    private OffsetDateTime deletedAt;

    public TimetableVersionEntity() {}

    public TimetableVersionEntity(UUID tenantId, UUID timetableId, int versionNo, String status, String changeNote) {
        this.tenantId = tenantId;
        this.timetableId = timetableId;
        this.versionNo = versionNo;
        this.status = status != null ? status : "DRAFT";
        this.changeNote = changeNote;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTimetableId() { return timetableId; }
    public void setTimetableId(UUID timetableId) { this.timetableId = timetableId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public int getRowVersion() { return rowVersion; }
    public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
