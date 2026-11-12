package com.campx.guardian;

import com.campx.guardian.security.GatewayHmacVerifier;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter for STM-05 guardian routes.
 * Unexpected failures are logged and returned without internal detail.
 */
public class GuardianController {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GuardianController.class);
    private final GuardianDomainService domain;
    private final GatewayHmacVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public GuardianController(GuardianDomainService domain, GatewayHmacVerifier verifier) {
        this.domain = domain;
        this.verifier = verifier;
    }

    /**
     * Dispatches one guardian request.
     */
    public Response handle(String method, String path, Map<String, String> headers, String body) {
        String correlationId = header(headers, "x-correlation-id");
        FlowTracker tracker = logger.flow((method == null ? "REQUEST" : method) + " " + path, correlationId);
        try {
            verifier.verify(method, path, header(headers, "x-gateway-timestamp"), header(headers, "x-gateway-signature"), body);
            Actor actor = actor(headers, correlationId);
            String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
            JsonNode json = body == null || body.trim().isEmpty() ? mapper.createObjectNode() : mapper.readTree(body);
            if ("GET".equals(method) && normalized.startsWith("/api/v1/guardians/students/") && normalized.endsWith("/emergency-contacts")) {
                String studentPart = normalized.substring("/api/v1/guardians/students/".length(), normalized.length() - "/emergency-contacts".length());
                return json(200, emergencyListNode(domain.listEmergencyContacts(actor, UUID.fromString(studentPart))), correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/guardians/students/") && normalized.endsWith("/relationship-history")) {
                String studentPart = normalized.substring("/api/v1/guardians/students/".length(), normalized.length() - "/relationship-history".length());
                UUID studentId = UUID.fromString(studentPart);
                return json(200, historyNode(studentId, domain.listRelationshipHistory(actor, studentId)), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/students/") && normalized.endsWith("/emergency-contacts")) {
                String studentPart = normalized.substring("/api/v1/guardians/students/".length(), normalized.length() - "/emergency-contacts".length());
                GuardianDomainService.EmergencyContact saved = domain.addEmergencyContact(actor, UUID.fromString(studentPart),
                        text(json, "name"), text(json, "relationship"), text(json, "phone"));
                return json(201, emergencyNode(saved), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/emergency-contacts/") && normalized.endsWith("/remove")) {
                String contactPart = normalized.substring("/api/v1/guardians/emergency-contacts/".length(), normalized.length() - "/remove".length());
                GuardianDomainService.EmergencyContact removed = domain.removeEmergencyContact(actor, UUID.fromString(contactPart), uuid(json, "studentId"));
                return json(200, emergencyNode(removed), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/emergency-contacts/")) {
                String contactPart = normalized.substring("/api/v1/guardians/emergency-contacts/".length());
                if (!contactPart.contains("/")) {
                    GuardianDomainService.EmergencyContact changed = domain.updateEmergencyContact(actor, UUID.fromString(contactPart),
                            uuid(json, "studentId"), text(json, "name"), text(json, "relationship"), text(json, "phone"));
                    return json(200, emergencyNode(changed), correlationId);
                }
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/guardians/persons/") && normalized.endsWith("/students")) {
                String personPart = normalized.substring("/api/v1/guardians/persons/".length(), normalized.length() - "/students".length());
                return json(200, listNode(domain.listStudentsForGuardian(actor, UUID.fromString(personPart))), correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/guardians/students/")) {
                UUID studentId = UUID.fromString(normalized.substring("/api/v1/guardians/students/".length()));
                return json(200, listNode(domain.listGuardians(actor, studentId)), correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/students")) {
                String viewId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/students".length());
                return json(200, authorizedViewNode(domain.viewAuthorizedInformation(actor, UUID.fromString(viewId))), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/review")) {
                String reviewId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/review".length());
                GuardianDomainService.ReviewDecision decision = domain.reviewEvidence(actor, UUID.fromString(reviewId),
                        text(json, "decision"), text(json, "reasonCode"));
                ObjectNode reviewNode = mapper.createObjectNode();
                reviewNode.put("documentId", decision.documentId.toString());
                reviewNode.put("guardianshipId", decision.guardianshipId.toString());
                reviewNode.put("decision", decision.decision);
                reviewNode.put("storedStatus", decision.storedStatus);
                reviewNode.put("reasonCode", decision.reasonCode);
                reviewNode.put("accessGranted", false);
                return json(200, reviewNode, correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/deactivate")) {
                String deactivateId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/deactivate".length());
                GuardianDomainService.Deactivation ended = domain.deactivateGuardian(actor, UUID.fromString(deactivateId), text(json, "reasonCode"));
                ObjectNode endedNode = linkNode(ended.link);
                endedNode.put("reasonCode", ended.reasonCode);
                endedNode.put("accessRevoked", ended.accessRevoked);
                endedNode.put("hardDeleted", ended.hardDeleted);
                endedNode.put("noticeRecorded", ended.noticeRecorded);
                endedNode.put("noticeDelivered", false);
                return json(200, endedNode, correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/state")) {
                String stateId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/state".length());
                GuardianDomainService.StateChange change = domain.markRelationshipState(actor, UUID.fromString(stateId), text(json, "state"));
                ObjectNode stateNode = linkNode(change.link);
                stateNode.put("requestedState", change.requestedState);
                stateNode.put("storedStatus", change.storedStatus);
                stateNode.put("reverificationDocumentId", change.documentId.toString());
                stateNode.put("accessRevoked", change.accessRevoked);
                stateNode.put("previousDocumentEdited", change.previousDocumentEdited);
                return json(200, stateNode, correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/period")) {
                String periodId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/period".length());
                GuardianDomainService.AccessDecision period = domain.setAccessPeriod(actor, UUID.fromString(periodId),
                        text(json, "validFrom"), text(json, "validTo"));
                return json(200, accessDecisionNode(period), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/access-check")) {
                String checkId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/access-check".length());
                GuardianDomainService.AccessDecision checked = domain.currentAccess(actor, UUID.fromString(checkId));
                return json(200, accessDecisionNode(checked), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/revoke")) {
                String revokeId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/revoke".length());
                GuardianDomainService.AccessDecision revoked = domain.revokeAccess(actor, UUID.fromString(revokeId));
                return json(200, accessDecisionNode(revoked), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/access")) {
                String accessId = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/access".length());
                java.util.ArrayList<String> categories = new java.util.ArrayList<String>();
                JsonNode categoryNode = json.get("categories");
                if (categoryNode != null && categoryNode.isArray()) {
                    for (JsonNode item : categoryNode) {
                        categories.add(item.asText());
                    }
                }
                GuardianDomainService.AccessGrant grant = domain.grantAccess(actor, UUID.fromString(accessId),
                        categories, text(json, "purpose"), text(json, "validTo"));
                ObjectNode accessNode = mapper.createObjectNode();
                accessNode.put("guardianshipId", grant.link.id.toString());
                accessNode.put("viewAcademics", grant.viewAcademics);
                accessNode.put("viewFees", grant.viewFees);
                accessNode.put("payFees", grant.payFees);
                accessNode.put("purpose", grant.purpose);
                accessNode.put("validTo", grant.validTo);
                accessNode.put("everythingGranted", false);
                return json(200, accessNode, correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/guardians/") && normalized.endsWith("/evidence")) {
                String idPart = normalized.substring("/api/v1/guardians/".length(), normalized.length() - "/evidence".length());
                GuardianDomainService.EvidenceRecord record = domain.submitEvidence(actor, UUID.fromString(idPart), text(json, "documentType"));
                ObjectNode node = mapper.createObjectNode();
                node.put("documentId", record.documentId.toString());
                node.put("documentLinkId", record.linkId.toString());
                node.put("guardianshipId", record.guardianshipId.toString());
                node.put("documentType", record.documentType);
                node.put("status", record.status);
                node.put("fileStored", false);
                return json(201, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/guardians/link-existing".equals(normalized)) {
                GuardianDomainService.GuardianLink link = domain.linkExistingGuardian(actor,
                        uuid(json, "studentId"),
                        uuid(json, "guardianPersonId"),
                        text(json, "relationship"),
                        json.path("primary").asBoolean(false));
                return json(201, linkNode(link), correlationId);
            }
            if (("POST".equals(method) || "PATCH".equals(method)) && normalized.startsWith("/api/v1/guardians/")) {
                String idPart = normalized.substring("/api/v1/guardians/".length());
                if (!idPart.contains("/")) {
                    GuardianDomainService.GuardianLink link = domain.updateGuardian(actor, UUID.fromString(idPart),
                            text(json, "relationship"), json.path("primary").asBoolean(false));
                    return json(200, linkNode(link), correlationId);
                }
            }
            if ("POST".equals(method) && "/api/v1/guardians".equals(normalized)) {
                GuardianDomainService.GuardianLink link = domain.addGuardian(actor, uuid(json, "studentId"),
                        text(json, "firstName"), text(json, "lastName"), text(json, "relationship"),
                        text(json, "email"), text(json, "phone"), json.path("primary").asBoolean(false));
                return json(201, linkNode(link), correlationId);
            }
            return problem(404, "NOT_FOUND", "Route was not found", correlationId);
        } catch (GuardianException ex) {
            return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), correlationId);
        } catch (RuntimeException ex) {
            tracker.markFailed(ex);
            logger.error("Guardian request could not be processed correlationId=" + correlationId, ex);
            return problem(400, "REQUEST_INVALID", "Request could not be processed", correlationId);
        } catch (Exception ex) {
            tracker.markFailed(ex);
            logger.error("Guardian request failed correlationId=" + correlationId, ex);
            return problem(500, "UNEXPECTED", "The guardian service could not complete the request", correlationId);
        } finally {
            tracker.close();
        }
    }

    private ObjectNode historyNode(UUID studentId, List<GuardianDomainService.HistoryEntry> entries) {
        ObjectNode node = mapper.createObjectNode();
        node.put("studentId", studentId.toString());
        node.put("readOnly", true);
        node.put("exportScope", "TENANT");
        ArrayNode array = node.putArray("entries");
        for (GuardianDomainService.HistoryEntry entry : entries) {
            ObjectNode row = array.addObject();
            row.put("id", entry.id.toString());
            row.put("guardianshipId", entry.guardianshipId.toString());
            row.put("kind", entry.kind);
            row.put("actorId", entry.actorId == null ? "" : entry.actorId.toString());
            row.put("occurredAt", entry.occurredAt);
            row.put("oldValue", entry.oldValue);
            row.put("newValue", entry.newValue);
            row.put("readOnly", true);
        }
        return node;
    }

    private ObjectNode emergencyListNode(List<GuardianDomainService.EmergencyContact> contacts) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode array = node.putArray("contacts");
        for (GuardianDomainService.EmergencyContact contact : contacts) {
            array.add(emergencyNode(contact));
        }
        return node;
    }

    private ObjectNode emergencyNode(GuardianDomainService.EmergencyContact contact) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", contact.id.toString());
        node.put("studentId", contact.studentId.toString());
        node.put("name", contact.name);
        node.put("relationship", contact.relationship);
        node.put("phone", contact.phone);
        node.put("status", contact.status);
        node.put("academicAccess", false);
        node.put("financialAccess", false);
        node.put("portalAccess", false);
        node.put("hardDeleted", false);
        return node;
    }

    private ObjectNode listNode(List<GuardianDomainService.GuardianLink> links) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode array = node.putArray("guardians");
        for (GuardianDomainService.GuardianLink link : links) {
            array.add(linkNode(link));
        }
        return node;
    }

    private ObjectNode authorizedViewNode(GuardianDomainService.AuthorizedView view) {
        ObjectNode node = mapper.createObjectNode();
        node.put("guardianshipId", view.guardianshipId.toString());
        node.put("studentId", view.studentId.toString());
        ArrayNode categories = node.putArray("categories");
        for (String category : view.categories) {
            categories.add(category);
        }
        node.put("validFrom", view.validFrom);
        node.put("validTo", view.validTo);
        node.put("reviewerNotesIncluded", false);
        node.put("sourceRecordsIncluded", false);
        return node;
    }

    private ObjectNode accessDecisionNode(GuardianDomainService.AccessDecision decision) {
        ObjectNode node = mapper.createObjectNode();
        node.put("guardianshipId", decision.guardianshipId.toString());
        node.put("relationshipStatus", decision.relationshipStatus);
        node.put("accessState", decision.accessState);
        node.put("viewAcademics", decision.viewAcademics);
        node.put("viewFees", decision.viewFees);
        node.put("payFees", decision.payFees);
        node.put("validFrom", decision.validFrom);
        node.put("validTo", decision.validTo);
        node.put("accessAllowed", decision.accessAllowed);
        return node;
    }

    private ObjectNode linkNode(GuardianDomainService.GuardianLink link) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", link.id.toString());
        node.put("studentId", link.studentId.toString());
        node.put("guardianPersonId", link.guardianPersonId.toString());
        node.put("firstName", link.firstName);
        node.put("lastName", link.lastName);
        node.put("relationship", link.relationship);
        node.put("email", link.email);
        node.put("phone", link.phone);
        node.put("primary", link.primary);
        node.put("status", link.status);
        node.put("linkedAt", link.linkedAt.toString());
        return node;
    }

    private Response json(int status, ObjectNode node, String correlationId) {
        node.put("correlationId", correlationId == null ? "" : correlationId);
        return new Response(status, node.toString());
    }

    private Response problem(int status, String code, String title, String correlationId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "https://campxsync.edu/errors/" + code.toLowerCase());
        node.put("title", title);
        node.put("status", status);
        node.put("code", code);
        node.put("correlationId", correlationId == null ? "" : correlationId);
        return new Response(status, node.toString());
    }

    private Actor actor(Map<String, String> headers, String correlationId) {
        String tenant = header(headers, "x-tenant-id");
        String user = header(headers, "x-user-id");
        if (tenant == null || user == null) {
            throw new GuardianException(401, "IDENTITY_REQUIRED", "Tenant and user identity are required");
        }
        UUID studentRecordId = null;
        String studentRecord = header(headers, "x-student-record-id");
        if (studentRecord != null && !studentRecord.trim().isEmpty()) {
            studentRecordId = UUID.fromString(studentRecord.trim());
        }
        return new Actor(UUID.fromString(tenant), UUID.fromString(user), studentRecordId, header(headers, "x-user-role"), correlationId);
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        String value = headers.get(name);
        return value == null ? headers.get(name.toLowerCase()) : value;
    }

    private static String text(JsonNode json, String field) {
        if (json == null || json.path(field).isMissingNode() || json.path(field).isNull()) {
            return null;
        }
        String value = json.path(field).asText(null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static UUID uuid(JsonNode json, String field) {
        String value = text(json, field);
        return value == null ? null : UUID.fromString(value);
    }

    public static final class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
