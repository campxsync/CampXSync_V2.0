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
            if ("GET".equals(method) && normalized.startsWith("/api/v1/guardians/students/")) {
                UUID studentId = UUID.fromString(normalized.substring("/api/v1/guardians/students/".length()));
                return json(200, listNode(domain.listGuardians(actor, studentId)), correlationId);
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

    private ObjectNode listNode(List<GuardianDomainService.GuardianLink> links) {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode array = node.putArray("guardians");
        for (GuardianDomainService.GuardianLink link : links) {
            array.add(linkNode(link));
        }
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
