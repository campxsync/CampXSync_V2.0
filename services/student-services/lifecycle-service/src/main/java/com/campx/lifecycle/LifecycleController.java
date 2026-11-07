package com.campx.lifecycle;

import com.campx.lifecycle.security.GatewayHmacVerifier;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter for STM-04 lifecycle routes.
 * <p>
 * Verifies the gateway signature when a secret is configured, resolves the trusted actor,
 * and maps {@link LifecycleException} to a problem response. Unexpected failures are logged
 * through {@link CampXLogger} and are not returned with internal detail.
 */
public class LifecycleController {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(LifecycleController.class);

    private final LifecycleDomainService domain;
    private final GatewayHmacVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public LifecycleController(LifecycleDomainService domain, GatewayHmacVerifier verifier) {
        this.domain = domain;
        this.verifier = verifier;
    }

    /**
     * Dispatches one lifecycle request.
     *
     * @param method HTTP method
     * @param path request path
     * @param headers trusted gateway headers
     * @param body JSON body, or empty
     * @return a JSON response or a problem response
     */
    public Response handle(String method, String path, Map<String, String> headers, String body) {
        String correlationId = header(headers, "x-correlation-id");
        FlowTracker tracker = logger.flow((method == null ? "REQUEST" : method) + " " + path, correlationId);
        try {
            verifier.verify(method, path, header(headers, "x-gateway-timestamp"), header(headers, "x-gateway-signature"), body);
            Actor actor = actor(headers, correlationId);
            String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
            JsonNode json = body == null || body.trim().isEmpty() ? mapper.createObjectNode() : mapper.readTree(body);

            if ("GET".equals(method) && normalized.startsWith("/api/v1/lifecycle/students/") && normalized.endsWith("/status")) {
                UUID studentId = UUID.fromString(extractSegment(normalized, "/api/v1/lifecycle/students/", "/status"));
                return json(200, statusNode(domain.getStatus(actor, studentId)), correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/lifecycle/students/") && normalized.endsWith("/history")) {
                UUID studentId = UUID.fromString(extractSegment(normalized, "/api/v1/lifecycle/students/", "/history"));
                List<LifecycleDomainService.HistoryEntry> rows = domain.listHistory(actor, studentId);
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("history");
                for (LifecycleDomainService.HistoryEntry row : rows) {
                    ObjectNode item = array.addObject();
                    item.put("status", row.status);
                    item.put("action", row.action);
                    item.put("actorRole", row.actorRole);
                    item.put("at", row.occurredAt.toString());
                }
                node.put("correlationId", correlationId == null ? "" : correlationId);
                return new Response(200, node.toString());
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/lifecycle/linked/") && normalized.endsWith("/status")) {
                UUID studentId = UUID.fromString(extractSegment(normalized, "/api/v1/lifecycle/linked/", "/status"));
                Map<String, String> view = domain.parentViewLinkedStatus(actor, studentId, header(headers, "x-linked-student-id"));
                ObjectNode node = mapper.createObjectNode();
                node.put("studentId", view.get("studentId"));
                node.put("status", view.get("status"));
                node.put("effectiveFrom", view.get("effectiveFrom"));
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && "/api/v1/lifecycle/reports/summary".equals(normalized)) {
                Map<String, Object> report = domain.lifecycleReport(actor);
                ObjectNode node = mapper.createObjectNode();
                node.put("studentCount", ((Integer) report.get("studentCount")).intValue());
                node.put("pendingRequests", ((Integer) report.get("pendingRequests")).intValue());
                ObjectNode byStatus = node.putObject("byStatus");
                @SuppressWarnings("unchecked")
                Map<String, Integer> counts = (Map<String, Integer>) report.get("byStatus");
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    byStatus.put(entry.getKey(), entry.getValue().intValue());
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && "/api/v1/lifecycle/advisees".equals(normalized)) {
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("advisees");
                for (Map<String, String> row : domain.listAdvisees(actor)) {
                    ObjectNode item = array.addObject();
                    item.put("studentId", row.get("studentId"));
                    item.put("status", row.get("status"));
                    item.put("effectiveFrom", row.get("effectiveFrom"));
                }
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/lifecycle/advisors".equals(normalized)) {
                domain.assignAdvisor(actor, uuid(json, "advisorUserId"), uuid(json, "studentId"));
                ObjectNode node = mapper.createObjectNode();
                node.put("advisorUserId", text(json, "advisorUserId"));
                node.put("studentId", text(json, "studentId"));
                node.put("assigned", true);
                return json(201, node, correlationId);
            }
            if ("GET".equals(method) && "/api/v1/lifecycle/requests".equals(normalized)) {
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("requests");
                for (LifecycleDomainService.LifecycleRequest request : domain.listPendingRequests(actor)) {
                    array.add(requestNode(request));
                }
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/lifecycle/requests".equals(normalized)) {
                UUID studentId = uuid(json, "studentId");
                if (studentId == null) {
                    studentId = actor.subjectStudentId();
                }
                LocalDate effectiveDate = null;
                String effectiveText = text(json, "effectiveDate");
                if (effectiveText != null && !effectiveText.trim().isEmpty()) {
                    effectiveDate = LocalDate.parse(effectiveText.trim());
                }
                LifecycleDomainService.LifecycleRequest request = domain.submitRequest(actor, studentId,
                        text(json, "requestType"), text(json, "reason"), effectiveDate);
                return json(201, requestNode(request), correlationId);
            }
            if ("POST".equals(method) && normalized.startsWith("/api/v1/lifecycle/requests/") && normalized.endsWith("/decide")) {
                String idPart = extractSegment(normalized, "/api/v1/lifecycle/requests/", "/decide");
                LifecycleDomainService.LifecycleRequest request = domain.decideRequest(actor, UUID.fromString(idPart), text(json, "decision"));
                return json(200, requestNode(request), correlationId);
            }
            if ("POST".equals(method) && "/api/v1/lifecycle/rules".equals(normalized)) {
                LifecycleDomainService.TransitionRule rule = domain.configureRule(actor, text(json, "fromStatus"), text(json, "toStatus"),
                        json.path("requiresApproval").asBoolean(true), json.path("active").asBoolean(true));
                return json(201, ruleNode(rule), correlationId);
            }
            if ("POST".equals(method) && "/api/v1/lifecycle/graduation/validate".equals(normalized)) {
                Map<String, Object> result = domain.validateGraduation(actor, uuid(json, "studentId"), uuid(json, "programId"));
                ObjectNode node = mapper.createObjectNode();
                node.put("studentId", String.valueOf(result.get("studentId")));
                node.put("programId", String.valueOf(result.get("programId")));
                node.put("eligible", Boolean.TRUE.equals(result.get("eligible")));
                ArrayNode failures = node.putArray("failures");
                @SuppressWarnings("unchecked")
                List<String> failureList = (List<String>) result.get("failures");
                if (failureList != null) {
                    for (String failure : failureList) {
                        failures.add(failure);
                    }
                }
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/lifecycle/graduation/record".equals(normalized)) {
                LifecycleDomainService.GraduationRecord record = domain.recordGraduation(actor, uuid(json, "studentId"),
                        uuid(json, "programId"), LocalDate.parse(text(json, "graduatedOn")), text(json, "notes"));
                return json(201, graduationNode(record), correlationId);
            }
            return problem(404, "NOT_FOUND", "Route was not found", correlationId);
        } catch (LifecycleException ex) {
            return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), correlationId);
        } catch (RuntimeException ex) {
            tracker.markFailed(ex);
            logger.error("Lifecycle request could not be processed correlationId=" + correlationId, ex);
            return problem(400, "REQUEST_INVALID", "Request could not be processed", correlationId);
        } catch (Exception ex) {
            tracker.markFailed(ex);
            logger.error("Lifecycle request failed correlationId=" + correlationId, ex);
            return problem(500, "UNEXPECTED", "The lifecycle service could not complete the request", correlationId);
        } finally {
            tracker.close();
        }
    }

    private Actor actor(Map<String, String> headers, String correlationId) {
        String tenant = header(headers, "x-tenant-id");
        String user = header(headers, "x-user-id");
        if (tenant == null || user == null) {
            throw new LifecycleException(401, "IDENTITY_REQUIRED", "Tenant and user identity are required");
        }
        UUID studentRecordId = null;
        String studentRecord = header(headers, "x-student-record-id");
        if (studentRecord != null && !studentRecord.trim().isEmpty()) {
            studentRecordId = UUID.fromString(studentRecord.trim());
        }
        return new Actor(UUID.fromString(tenant), UUID.fromString(user), studentRecordId, header(headers, "x-user-role"), correlationId);
    }

    private ObjectNode statusNode(LifecycleDomainService.LifecycleStatus status) {
        ObjectNode node = mapper.createObjectNode();
        node.put("studentId", status.studentId.toString());
        node.put("status", status.status);
        node.put("effectiveFrom", status.effectiveFrom.toString());
        node.put("updatedAt", status.updatedAt.toString());
        return node;
    }

    private ObjectNode requestNode(LifecycleDomainService.LifecycleRequest request) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", request.id.toString());
        node.put("studentId", request.studentId.toString());
        node.put("requestType", request.requestType);
        node.put("status", request.status);
        node.put("reason", request.reason == null ? "" : request.reason);
        if (request.effectiveDate != null) {
            node.put("effectiveDate", request.effectiveDate.toString());
        }
        if (request.decidedBy != null) {
            node.put("decidedBy", request.decidedBy.toString());
        }
        if (request.decidedAt != null) {
            node.put("decidedAt", request.decidedAt.toString());
        }
        return node;
    }

    private ObjectNode ruleNode(LifecycleDomainService.TransitionRule rule) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", rule.id.toString());
        node.put("fromStatus", rule.fromStatus);
        node.put("toStatus", rule.toStatus);
        node.put("requiresApproval", rule.requiresApproval);
        node.put("active", rule.active);
        return node;
    }

    private ObjectNode graduationNode(LifecycleDomainService.GraduationRecord record) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", record.id.toString());
        node.put("studentId", record.studentId.toString());
        node.put("programId", record.programId.toString());
        node.put("graduatedOn", record.graduatedOn.toString());
        node.put("notes", record.notes == null ? "" : record.notes);
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

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        String value = headers.get(name);
        if (value == null) {
            value = headers.get(name.toLowerCase());
        }
        return value;
    }

    private static String text(JsonNode json, String field) {
        if (json == null || json.path(field).isMissingNode() || json.path(field).isNull()) {
            return null;
        }
        return json.path(field).asText(null);
    }

    private static UUID uuid(JsonNode json, String field) {
        String value = text(json, field);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static String extractSegment(String normalized, String prefix, String suffix) {
        return normalized.substring(prefix.length(), normalized.length() - suffix.length());
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
