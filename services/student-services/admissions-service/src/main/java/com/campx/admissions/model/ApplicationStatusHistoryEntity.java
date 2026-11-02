package com.campx.admissions.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ApplicationStatusHistoryEntity {

    private UUID tenantId;
    private UUID id;
    private UUID applicationId;
    private ApplicationStatus fromStatus;
    private ApplicationStatus toStatus;
    private String transitionAction;
    private String transitionReason;
    private UUID actorId;
    private String actorRole;
    private String correlationId;
    private OffsetDateTime createdAt;

    public ApplicationStatusHistoryEntity() {
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(UUID applicationId) {
        this.applicationId = applicationId;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ApplicationStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ApplicationStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getTransitionAction() {
        return transitionAction;
    }

    public void setTransitionAction(String transitionAction) {
        this.transitionAction = transitionAction;
    }

    public String getTransitionReason() {
        return transitionReason;
    }

    public void setTransitionReason(String transitionReason) {
        this.transitionReason = transitionReason;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
