package com.campx.academic.attendance.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Relational entity mapping to PostgreSQL table {@code acd.attendance_records}.
 */
public class AttendanceRecordEntity {

    private UUID id;
    private UUID tenantId;
    private UUID sessionId;
    private UUID studentId;
    private String status; // 'PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'LEAVE', 'EXCUSED'
    private String method = "MANUAL"; // 'MANUAL', 'QR', 'BIOMETRIC', 'GEOFENCE', 'IMPORT'
    private UUID markedBy;
    private OffsetDateTime markedAt;
    private String remarks;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private int rowVersion = 1;

    public AttendanceRecordEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public UUID getMarkedBy() { return markedBy; }
    public void setMarkedBy(UUID markedBy) { this.markedBy = markedBy; }

    public OffsetDateTime getMarkedAt() { return markedAt; }
    public void setMarkedAt(OffsetDateTime markedAt) { this.markedAt = markedAt; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

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
}
