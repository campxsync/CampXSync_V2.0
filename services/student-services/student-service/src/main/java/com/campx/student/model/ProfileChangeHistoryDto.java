package com.campx.student.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing an immutable profile change history record (STM01-US-025).
 * Encapsulates change type, affected fields, actor, timestamp, and sanitized summary.
 */
public class ProfileChangeHistoryDto {

    private UUID id;
    private UUID studentId;
    private String action; // e.g. "STUDENT_ENROLLED", "PROFILE_UPDATED", "STATUS_CHANGED", "IDENTIFIER_ADDED", "CORRECTION_APPLIED"
    private List<String> changedFields;
    private UUID actorId;
    private String timestamp;
    private String summary;

    public ProfileChangeHistoryDto() {}

    public ProfileChangeHistoryDto(UUID id, UUID studentId, String action, List<String> changedFields, UUID actorId, Instant timestamp, String summary) {
        this.id = id;
        this.studentId = studentId;
        this.action = action;
        this.changedFields = changedFields != null ? changedFields : Collections.emptyList();
        this.actorId = actorId;
        this.timestamp = timestamp != null ? timestamp.toString() : Instant.now().toString();
        this.summary = summary;
    }

    public ProfileChangeHistoryDto(UUID id, UUID studentId, String action, List<String> changedFields, UUID actorId, String timestamp, String summary) {
        this.id = id;
        this.studentId = studentId;
        this.action = action;
        this.changedFields = changedFields != null ? changedFields : Collections.emptyList();
        this.actorId = actorId;
        this.timestamp = timestamp != null ? timestamp : Instant.now().toString();
        this.summary = summary;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public List<String> getChangedFields() { return changedFields; }
    public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
