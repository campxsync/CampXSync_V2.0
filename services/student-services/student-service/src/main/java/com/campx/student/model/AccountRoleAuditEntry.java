package com.campx.student.model;

import java.util.UUID;

/**
 * STM-07-US-012 — read-only projection of account link / R25 role audit events.
 */
public class AccountRoleAuditEntry {

    private final UUID id;
    private final String action;
    private final UUID studentId;
    private final UUID actorUserId;
    private final String eventTime;
    private final String detailJson;

    public AccountRoleAuditEntry(UUID id, String action, UUID studentId, UUID actorUserId,
                                 String eventTime, String detailJson) {
        this.id = id;
        this.action = action;
        this.studentId = studentId;
        this.actorUserId = actorUserId;
        this.eventTime = eventTime;
        this.detailJson = detailJson;
    }

    public UUID getId() { return id; }
    public String getAction() { return action; }
    public UUID getStudentId() { return studentId; }
    public UUID getActorUserId() { return actorUserId; }
    public String getEventTime() { return eventTime; }
    public String getDetailJson() { return detailJson; }
}
