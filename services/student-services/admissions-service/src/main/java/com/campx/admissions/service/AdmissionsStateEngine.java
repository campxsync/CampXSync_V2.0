package com.campx.admissions.service;

import com.campx.admissions.exception.AdmissionsConflictException;
import com.campx.admissions.exception.AdmissionsLifecycleException;
import com.campx.admissions.model.ApplicationEntity;
import com.campx.admissions.model.ApplicationStatus;
import com.campx.admissions.model.ApplicationStatusHistoryEntity;
import com.campx.admissions.repository.StatusHistoryRepository;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Authoritative Server-Side State Machine & Transition Engine for STM-02 (EN-004 / ADR D-02).
 */
public class AdmissionsStateEngine {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsStateEngine.class);

    // Allowed transition graph: fromStatus -> set of allowed toStatus
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITION_GRAPH = new EnumMap<>(ApplicationStatus.class);

    static {
        TRANSITION_GRAPH.put(ApplicationStatus.DRAFT, new HashSet<>(Arrays.asList(
                ApplicationStatus.SUBMITTED, ApplicationStatus.WITHDRAWN
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.SUBMITTED, new HashSet<>(Arrays.asList(
                ApplicationStatus.RETURNED_FOR_INFORMATION, ApplicationStatus.ELIGIBLE, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.RETURNED_FOR_INFORMATION, new HashSet<>(Arrays.asList(
                ApplicationStatus.SUBMITTED, ApplicationStatus.WITHDRAWN
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.ELIGIBLE, new HashSet<>(Arrays.asList(
                ApplicationStatus.UNDER_REVIEW, ApplicationStatus.OFFERED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.UNDER_REVIEW, new HashSet<>(Arrays.asList(
                ApplicationStatus.OFFERED, ApplicationStatus.REJECTED, ApplicationStatus.RETURNED_FOR_INFORMATION, ApplicationStatus.WITHDRAWN
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.OFFERED, new HashSet<>(Arrays.asList(
                ApplicationStatus.ACCEPTED, ApplicationStatus.DECLINED, ApplicationStatus.EXPIRED, ApplicationStatus.WITHDRAWN
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.ACCEPTED, new HashSet<>(Arrays.asList(
                ApplicationStatus.HANDOVER_PENDING, ApplicationStatus.HANDED_OVER
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.HANDOVER_PENDING, new HashSet<>(Arrays.asList(
                ApplicationStatus.HANDED_OVER
        )));
        TRANSITION_GRAPH.put(ApplicationStatus.DECLINED, Collections.emptySet());
        TRANSITION_GRAPH.put(ApplicationStatus.EXPIRED, Collections.emptySet());
        TRANSITION_GRAPH.put(ApplicationStatus.REJECTED, Collections.emptySet());
        TRANSITION_GRAPH.put(ApplicationStatus.WITHDRAWN, Collections.emptySet());
        TRANSITION_GRAPH.put(ApplicationStatus.HANDED_OVER, Collections.emptySet());
    }

    private final StatusHistoryRepository historyRepository;
    private final AdmissionsEventPublisher eventPublisher;

    public AdmissionsStateEngine(StatusHistoryRepository historyRepository, AdmissionsEventPublisher eventPublisher) {
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    public void transition(UserSecurityContext context, ApplicationEntity application, ApplicationStatus targetStatus, String action, String reason, String correlationId) {
        ApplicationStatus currentStatus = application.getStatus();

        if (currentStatus == targetStatus) {
            return; // Idempotent no-op
        }

        Set<ApplicationStatus> allowedTargets = TRANSITION_GRAPH.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowedTargets.contains(targetStatus)) {
            throw new AdmissionsLifecycleException("Illegal state transition from " + currentStatus + " to " + targetStatus + " via action '" + action + "'");
        }

        // Apply transition
        application.setStatus(targetStatus);
        application.setUpdatedAt(OffsetDateTime.now());
        application.setRowVersion(application.getRowVersion() + 1);

        // Record status history atomically
        ApplicationStatusHistoryEntity history = new ApplicationStatusHistoryEntity();
        history.setTenantId(application.getTenantId());
        history.setApplicationId(application.getId());
        history.setFromStatus(currentStatus);
        history.setToStatus(targetStatus);
        history.setTransitionAction(action);
        history.setTransitionReason(reason);
        history.setActorId(context.getUserId());
        history.setActorRole(context.getRole());
        history.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
        history.setCreatedAt(OffsetDateTime.now());

        if (historyRepository != null) {
            historyRepository.save(history);
        }

        // Emit outbox event
        if (eventPublisher != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("applicationId", application.getId().toString());
            payload.put("applicationNumber", application.getApplicationNumber());
            payload.put("fromStatus", currentStatus.name());
            payload.put("toStatus", targetStatus.name());
            payload.put("action", action);
            payload.put("reason", reason);

            String eventType = "admissions.application.status_changed.v1";
            if (targetStatus == ApplicationStatus.SUBMITTED) eventType = "admissions.application.submitted.v1";
            else if (targetStatus == ApplicationStatus.OFFERED) eventType = "admissions.offer.issued.v1";
            else if (targetStatus == ApplicationStatus.ACCEPTED) eventType = "admissions.offer.accepted.v1";
            else if (targetStatus == ApplicationStatus.HANDED_OVER) eventType = "admissions.enrolment.handover_completed.v1";

            eventPublisher.publishEvent(null, application.getTenantId(), application.getId(), eventType, payload, context.getRole(), correlationId);
        }

        logger.info("Application {} transitioned from {} to {} by {}", application.getApplicationNumber(), currentStatus, targetStatus, context.getUserId());
    }

    public static boolean isTransitionAllowed(ApplicationStatus from, ApplicationStatus to) {
        Set<ApplicationStatus> allowed = TRANSITION_GRAPH.get(from);
        return allowed != null && allowed.contains(to);
    }
}
