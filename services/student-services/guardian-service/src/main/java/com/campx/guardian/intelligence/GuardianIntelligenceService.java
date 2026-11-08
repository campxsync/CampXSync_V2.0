package com.campx.guardian.intelligence;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianDomainService;
import com.campx.guardian.GuardianDomainService.EvidenceRecord;
import com.campx.guardian.GuardianDomainService.GuardianLink;
import com.campx.guardian.GuardianDomainService.Deactivation;
import com.campx.guardian.GuardianDomainService.EmergencyContact;
import com.campx.guardian.GuardianDomainService.AccessDecision;
import com.campx.guardian.GuardianDomainService.AuthorizedView;
import com.campx.guardian.GuardianDomainService.HistoryEntry;
import com.campx.guardian.GuardianDomainService.AccessGrant;
import com.campx.guardian.GuardianDomainService.StateChange;
import com.campx.guardian.GuardianDomainService.ReviewDecision;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the versioned guardian JSON contract. Live delivery stays false until the Intelligence endpoint is configured.
 */
public class GuardianIntelligenceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GuardianIntelligenceService.class);
    private static final String SERVICE_NAME = "guardian-service";
    private final ControlledIntelligenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GuardianIntelligenceService() {
        this(new ControlledIntelligenceAdapter());
    }

    public GuardianIntelligenceService(ControlledIntelligenceAdapter adapter) {
        this.adapter = adapter == null ? new ControlledIntelligenceAdapter() : adapter;
    }

    /**
     * Emits the before and after relationship for STM-05-US-002. There is no relationship-history table yet.
     */
    public void publishGuardianUpdated(Actor actor, GuardianLink before, GuardianLink after) {
        if (actor == null || before == null || after == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", after.id.toString());
            payload.put("studentId", after.studentId.toString());
            payload.put("beforeRelationship", before.relationship);
            payload.put("afterRelationship", after.relationship);
            payload.put("beforePrimary", before.primary);
            payload.put("afterPrimary", after.primary);
            payload.put("status", after.status);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_UPDATED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(after.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.updated.v1-" + actor.getTenantId() + "-" + after.id + "-" + after.linkedAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_UPDATED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian update event", ex);
        }
    }

    public void publishEmergencyContact(Actor actor, EmergencyContact contact, String action) {
        if (actor == null || contact == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("contactId", contact.id.toString());
            payload.put("studentId", contact.studentId.toString());
            payload.put("action", action);
            payload.put("status", contact.status);
            payload.put("academicAccess", false);
            payload.put("financialAccess", false);
            payload.put("portalAccess", false);
            payload.put("hardDeleted", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.EMERGENCY_CONTACT_CHANGED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_EMERGENCY_CONTACT")
                    .aggregateId(contact.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.emergency_contact.changed.v1-" + contact.id + "-" + action + "-" + contact.phone)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.EMERGENCY_CONTACT_CHANGED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 emergency contact event", ex);
        }
    }

    public void publishAccessPeriod(Actor actor, AccessDecision decision) {
        publishAccessDecision(actor, decision, GuardianDomainService.GUARDIAN_ACCESS_PERIOD_SET, "period");
    }

    public void publishAccessRevoked(Actor actor, AccessDecision decision) {
        publishAccessDecision(actor, decision, GuardianDomainService.GUARDIAN_ACCESS_REVOKED, "revoked");
    }

    public void publishAuthorizedView(Actor actor, AuthorizedView view) {
        if (actor == null || view == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", view.guardianshipId.toString());
            payload.put("studentId", view.studentId.toString());
            payload.put("categories", view.categories);
            payload.put("validFrom", view.validFrom);
            payload.put("validTo", view.validTo);
            payload.put("reviewerNotesIncluded", false);
            payload.put("sourceRecordsIncluded", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_AUTHORIZED_VIEW)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(view.guardianshipId.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.authorized_view.v1-" + view.guardianshipId + "-" + view.categories)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_AUTHORIZED_VIEW, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 authorized view event", ex);
        }
    }

    public void publishHistoryReviewed(Actor actor, java.util.UUID studentId, int entryCount) {
        if (actor == null || studentId == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("studentId", studentId.toString());
            payload.put("entryCount", entryCount);
            payload.put("readOnly", true);
            payload.put("exportScope", "TENANT");
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_HISTORY_REVIEWED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(studentId.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.history_reviewed.v1-" + studentId + "-" + actor.getCorrelationId())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_HISTORY_REVIEWED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 relationship history event", ex);
        }
    }

    public void publishAccessExpired(Actor actor, AccessDecision decision) {
        publishAccessDecision(actor, decision, GuardianDomainService.GUARDIAN_ACCESS_EXPIRED, "expired");
    }

    private void publishAccessDecision(Actor actor, AccessDecision decision, String eventType, String action) {
        if (actor == null || decision == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", decision.guardianshipId.toString());
            payload.put("relationshipStatus", decision.relationshipStatus);
            payload.put("accessState", decision.accessState);
            payload.put("viewAcademics", decision.viewAcademics);
            payload.put("viewFees", decision.viewFees);
            payload.put("payFees", decision.payFees);
            payload.put("validFrom", decision.validFrom);
            payload.put("validTo", decision.validTo);
            payload.put("accessAllowed", decision.accessAllowed);
            payload.put("sessionInvalidated", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(eventType)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(decision.guardianshipId.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-" + eventType + "-" + decision.guardianshipId + "-" + decision.validTo + "-" + action)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}", eventType, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian access period event", ex);
        }
    }

    public void publishAccessGranted(Actor actor, AccessGrant grant) {
        if (actor == null || grant == null || grant.link == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", grant.link.id.toString());
            payload.put("studentId", grant.link.studentId.toString());
            payload.put("viewAcademics", grant.viewAcademics);
            payload.put("viewFees", grant.viewFees);
            payload.put("payFees", grant.payFees);
            payload.put("purpose", grant.purpose);
            payload.put("validTo", grant.validTo);
            payload.put("everythingGranted", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_ACCESS_GRANTED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(grant.link.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.access_granted.v1-" + grant.link.id + "-" + grant.purpose + "-" + grant.validTo)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_ACCESS_GRANTED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian access event", ex);
        }
    }

    public void publishStateChanged(Actor actor, GuardianLink before, StateChange result) {
        if (actor == null || before == null || result == null || result.link == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", result.link.id.toString());
            payload.put("studentId", result.link.studentId.toString());
            payload.put("previousStatus", before.status);
            payload.put("requestedState", result.requestedState);
            payload.put("storedStatus", result.storedStatus);
            payload.put("reverificationDocumentId", result.documentId.toString());
            payload.put("accessRevoked", result.accessRevoked);
            payload.put("previousDocumentEdited", result.previousDocumentEdited);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_STATE_CHANGED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(result.link.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.state_changed.v1-" + result.documentId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_STATE_CHANGED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian state event", ex);
        }
    }

    public void publishGuardianDeactivated(Actor actor, GuardianLink before, Deactivation result) {
        if (actor == null || before == null || result == null || result.link == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", result.link.id.toString());
            payload.put("studentId", result.link.studentId.toString());
            payload.put("previousStatus", before.status);
            payload.put("status", result.link.status);
            payload.put("reasonCode", result.reasonCode);
            payload.put("accessRevoked", result.accessRevoked);
            payload.put("hardDeleted", result.hardDeleted);
            payload.put("noticeRecorded", result.noticeRecorded);
            payload.put("noticeDelivered", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_DEACTIVATED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(result.link.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.deactivated.v1-" + result.link.id + "-" + result.link.linkedAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_DEACTIVATED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian deactivation event", ex);
        }
    }

    public void publishEvidenceReviewed(Actor actor, ReviewDecision decision) {
        if (actor == null || decision == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", decision.guardianshipId.toString());
            payload.put("documentId", decision.documentId.toString());
            payload.put("decision", decision.decision);
            payload.put("storedStatus", decision.storedStatus);
            payload.put("reasonCode", decision.reasonCode);
            payload.put("accessGranted", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_EVIDENCE_REVIEWED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(decision.guardianshipId.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.evidence_reviewed.v1-" + decision.documentId + "-" + decision.storedStatus)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_EVIDENCE_REVIEWED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian review event", ex);
        }
    }

    public void publishEvidenceSubmitted(Actor actor, EvidenceRecord record) {
        if (actor == null || record == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", record.guardianshipId.toString());
            payload.put("documentId", record.documentId.toString());
            payload.put("documentLinkId", record.linkId.toString());
            payload.put("documentType", record.documentType);
            payload.put("status", record.status);
            payload.put("fileStored", false);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_EVIDENCE_SUBMITTED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(record.guardianshipId.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.evidence_submitted.v1-" + record.linkId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_EVIDENCE_SUBMITTED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian evidence event", ex);
        }
    }

    public void publishGuardianLinked(Actor actor, GuardianLink link) {
        if (actor == null || link == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", link.id.toString());
            payload.put("studentId", link.studentId.toString());
            payload.put("guardianPersonId", link.guardianPersonId.toString());
            payload.put("relationship", link.relationship);
            payload.put("primary", link.primary);
            payload.put("status", link.status);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_LINKED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(link.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.linked.v1-" + actor.getTenantId() + "-" + link.id)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_LINKED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 intelligence event", ex);
        }
    }
}
