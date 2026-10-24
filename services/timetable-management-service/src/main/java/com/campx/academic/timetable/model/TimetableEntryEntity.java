package com.campx.academic.timetable.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity mapped to PostgreSQL table {@code acd.timetable_entries}.
 */
public class TimetableEntryEntity {

    private UUID id;
    private UUID tenantId;
    private UUID timetableVersionId;
    private UUID sectionId;
    private UUID subjectId;
    private UUID employeeId;
    private UUID roomId;
    private int dayOfWeek; // 0=Sunday, 1=Monday, ..., 6=Saturday
    private LocalTime startTime;
    private LocalTime endTime;
    private String entryType = "LECTURE"; // 'LECTURE', 'LAB', 'TUTORIAL', 'SEMINAR', 'BREAK'
    private LocalDate validFrom;
    private LocalDate validTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private int rowVersion = 1;
    private OffsetDateTime deletedAt;

    public TimetableEntryEntity() {}

    public TimetableEntryEntity(UUID tenantId, UUID timetableVersionId, UUID sectionId, UUID subjectId,
                                UUID employeeId, UUID roomId, int dayOfWeek,
                                LocalTime startTime, LocalTime endTime, String entryType) {
        this.tenantId = tenantId;
        this.timetableVersionId = timetableVersionId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.employeeId = employeeId;
        this.roomId = roomId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.entryType = entryType != null ? entryType : "LECTURE";
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTimetableVersionId() { return timetableVersionId; }
    public void setTimetableVersionId(UUID timetableVersionId) { this.timetableVersionId = timetableVersionId; }
    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
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
