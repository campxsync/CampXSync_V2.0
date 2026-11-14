package com.campx.enrollment;

import com.campx.enrollment.security.GatewayHmacVerifier;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP adapter for STM-03 enrolment routes.
 * <p>
 * Verifies the gateway signature when a secret is configured, resolves the trusted actor,
 * and maps {@link EnrollmentException} to a problem response. Unexpected failures are logged
 * through {@link CampXLogger} and are not returned with internal detail.
 */
public class EnrollmentController {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(EnrollmentController.class);

    private final EnrollmentDomainService domain;
    private final GatewayHmacVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnrollmentController(EnrollmentDomainService domain, GatewayHmacVerifier verifier) {
        this.domain = domain;
        this.verifier = verifier;
    }

    /**
     * Dispatches one enrolment request.
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
            verifier.verify(method, path, headers, body);
            Actor actor = actor(headers, correlationId);
            String normalized = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
            JsonNode json = body == null || body.trim().isEmpty() ? mapper.createObjectNode() : mapper.readTree(body);
            if ("GET".equals(method) && "/api/v1/enrollment/alerts".equals(normalized)) {
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("alerts");
                for (java.util.Map<String, Object> alert : domain.listAlerts(actor)) {
                    ObjectNode item = array.addObject();
                    item.put("eventType", String.valueOf(alert.get("eventType")));
                    item.put("enrolmentId", alert.get("enrolmentId") == null ? "" : alert.get("enrolmentId").toString());
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && "/api/v1/enrollment/terms".equals(normalized)) {
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("terms");
                for (EnrollmentDomainService.Term term : domain.listTerms(actor)) {
                    array.add(termNode(term));
                }
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/terms".equals(normalized)) {
                EnrollmentDomainService.Term term = domain.createTerm(actor, text(json, "yearCode"), text(json, "termCode"), text(json, "name"));
                return json(201, termNode(term), correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/windows".equals(normalized)) {
                EnrollmentDomainService.Window window = domain.createWindow(actor, uuid(json, "termId"), Instant.parse(text(json, "opensAt")), Instant.parse(text(json, "closesAt")), json.path("minCredits").asInt(), json.path("maxCredits").asInt());
                ObjectNode node = mapper.createObjectNode();
                node.put("id", window.id.toString());
                node.put("termId", window.termId.toString());
                return json(201, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/handovers".equals(normalized)) {
                EnrollmentDomainService.Enrolment enrolment = domain.openFromHandover(actor, uuid(json, "applicationId"), uuid(json, "studentId"), uuid(json, "programId"), uuid(json, "termId"));
                return json(201, enrolmentNode(enrolment), correlationId);
            }
            if ("GET".equals(method) && "/api/v1/enrollment/enrolments".equals(normalized)) {
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("enrolments");
                for (EnrollmentDomainService.Enrolment enrolment : domain.listMyEnrolments(actor)) {
                    array.add(enrolmentNode(enrolment, actor));
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.endsWith("/catalogue") && normalized.startsWith("/api/v1/enrollment/enrolments/")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/catalogue".length());
                ObjectNode node = mapper.createObjectNode();
                node.put("enrolmentId", idPart);
                ArrayNode array = node.putArray("courses");
                for (java.util.Map<String, Object> course : domain.listRegistrationCatalogue(actor, UUID.fromString(idPart))) {
                    ObjectNode item = array.addObject();
                    item.put("id", course.get("id").toString());
                    item.put("code", String.valueOf(course.get("code")));
                    item.put("credits", ((Integer) course.get("credits")).intValue());
                    item.put("core", Boolean.TRUE.equals(course.get("core")));
                    ArrayNode sections = item.putArray("sections");
                    @SuppressWarnings("unchecked")
                    List<java.util.Map<String, Object>> sectionRows = (List<java.util.Map<String, Object>>) course.get("sections");
                    for (java.util.Map<String, Object> section : sectionRows) {
                        ObjectNode sectionNode = sections.addObject();
                        sectionNode.put("id", section.get("id").toString());
                        sectionNode.put("capacity", ((Integer) section.get("capacity")).intValue());
                        sectionNode.put("enrolledCount", ((Integer) section.get("enrolledCount")).intValue());
                        sectionNode.put("seatsAvailable", ((Integer) section.get("seatsAvailable")).intValue());
                    }
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/enrollment/enrolments/")
                    && normalized.indexOf('/', "/api/v1/enrollment/enrolments/".length()) < 0) {
                UUID id = UUID.fromString(normalized.substring("/api/v1/enrollment/enrolments/".length()));
                EnrollmentDomainService.Enrolment enrolment = domain.getEnrolment(actor, id);
                return json(200, enrolmentNode(enrolment, actor), correlationId);
            }
            if ("POST".equals(method) && normalized.endsWith("/swap")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/swap".length());
                return json(200, enrolmentNode(domain.swapSection(actor, UUID.fromString(idPart), uuid(json, "courseId"), uuid(json, "sectionId"))), correlationId);
            }
            if ("POST".equals(method) && normalized.endsWith("/register")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/register".length());
                List<EnrollmentDomainService.Selection> selections = new ArrayList<EnrollmentDomainService.Selection>();
                Iterator<JsonNode> iterator = json.path("selections").elements();
                while (iterator.hasNext()) {
                    JsonNode item = iterator.next();
                    selections.add(new EnrollmentDomainService.Selection(UUID.fromString(item.path("courseId").asText()), UUID.fromString(item.path("sectionId").asText())));
                }
                EnrollmentDomainService.Enrolment enrolment = domain.register(actor, UUID.fromString(idPart), selections, header(headers, "idempotency-key"));
                return json(200, enrolmentNode(enrolment), correlationId);
            }
            if ("POST".equals(method) && normalized.endsWith("/status")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/status".length());
                return json(200, enrolmentNode(domain.updateStatus(actor, UUID.fromString(idPart), text(json, "status"))), correlationId);
            }
            if ("POST".equals(method) && normalized.endsWith("/eligibility")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/eligibility".length());
                UUID enrolmentId = UUID.fromString(idPart);
                List<String> failures = domain.evaluateEligibility(actor, enrolmentId);
                EnrollmentDomainService.Enrolment enrolment = domain.getEnrolment(actor, enrolmentId);
                ObjectNode node = mapper.createObjectNode();
                node.put("enrolmentId", idPart);
                node.put("eligible", failures.isEmpty());
                node.put("status", enrolment.status);
                ArrayNode array = node.putArray("failures");
                for (String failure : failures) {
                    array.add(failure);
                }
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && normalized.endsWith("/drop")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/drop".length());
                return json(200, enrolmentNode(domain.drop(actor, UUID.fromString(idPart), uuid(json, "courseId"))), correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/allocations".equals(normalized)) {
                return json(200, enrolmentNode(domain.allocateSection(actor, uuid(json, "enrolmentId"), uuid(json, "courseId"), uuid(json, "sectionId"))), correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/waitlist/join".equals(normalized)) {
                int position = domain.joinWaitlist(actor, uuid(json, "enrolmentId"), uuid(json, "sectionId"));
                ObjectNode node = mapper.createObjectNode();
                node.put("position", position);
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/waitlist/promote".equals(normalized)) {
                int remaining = domain.promoteWaitlist(actor, uuid(json, "sectionId"));
                ObjectNode node = mapper.createObjectNode();
                node.put("remaining", remaining);
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && "/api/v1/enrollment/rosters".equals(normalized)) {
                ObjectNode node = mapper.createObjectNode();
                ArrayNode sections = node.putArray("sections");
                for (java.util.Map<String, Object> roster : domain.listAssignedRosters(actor)) {
                    ObjectNode item = sections.addObject();
                    item.put("sectionId", String.valueOf(roster.get("sectionId")));
                    item.put("courseCode", String.valueOf(roster.get("courseCode")));
                    item.put("capacity", ((Integer) roster.get("capacity")).intValue());
                    item.put("enrolledCount", ((Integer) roster.get("enrolledCount")).intValue());
                    ArrayNode students = item.putArray("studentIds");
                    @SuppressWarnings("unchecked")
                    List<String> studentIds = (List<String>) roster.get("studentIds");
                    for (String studentId : studentIds) {
                        students.add(studentId);
                    }
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/enrollment/sections/") && normalized.endsWith("/roster")) {
                String sectionPart = normalized.substring("/api/v1/enrollment/sections/".length(), normalized.length() - "/roster".length());
                List<UUID> roster = domain.roster(actor, UUID.fromString(sectionPart));
                ObjectNode node = mapper.createObjectNode();
                ArrayNode students = node.putArray("studentIds");
                for (UUID studentId : roster) {
                    students.add(studentId.toString());
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.startsWith("/api/v1/enrollment/sections/") && normalized.endsWith("/waitlist/position")) {
                String sectionPart = normalized.substring("/api/v1/enrollment/sections/".length(), normalized.length() - "/waitlist/position".length());
                ObjectNode node = mapper.createObjectNode();
                node.put("position", domain.waitlistPosition(actor, UUID.fromString(sectionPart)));
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.endsWith("/courses") && normalized.startsWith("/api/v1/enrollment/enrolments/")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/courses".length());
                List<EnrollmentDomainService.Course> courses = domain.listCoursesForEnrolment(actor, UUID.fromString(idPart));
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("courses");
                for (EnrollmentDomainService.Course course : courses) {
                    ObjectNode item = array.addObject();
                    item.put("id", course.id.toString());
                    item.put("code", course.code);
                    item.put("credits", course.credits);
                    item.put("core", course.core);
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.contains("/courses/") && normalized.endsWith("/sections")) {
                int enrolmentsIdx = "/api/v1/enrollment/enrolments/".length();
                int coursesIdx = normalized.indexOf("/courses/");
                String enrolmentId = normalized.substring(enrolmentsIdx, coursesIdx);
                String courseId = normalized.substring(coursesIdx + "/courses/".length(), normalized.length() - "/sections".length());
                List<EnrollmentDomainService.Section> sections = domain.listSectionsForCourse(actor, UUID.fromString(enrolmentId), UUID.fromString(courseId));
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("sections");
                for (EnrollmentDomainService.Section section : sections) {
                    ObjectNode item = array.addObject();
                    item.put("id", section.id.toString());
                    item.put("courseId", section.courseId.toString());
                    item.put("capacity", section.capacity);
                    item.put("enrolledCount", section.enrolledCount);
                    item.put("seatsAvailable", Math.max(0, section.capacity - section.enrolledCount));
                }
                return json(200, node, correlationId);
            }
            if ("GET".equals(method) && normalized.endsWith("/history") && normalized.startsWith("/api/v1/enrollment/enrolments/")) {
                String idPart = normalized.substring("/api/v1/enrollment/enrolments/".length(), normalized.length() - "/history".length());
                List<java.util.Map<String, String>> history = domain.listHistory(actor, UUID.fromString(idPart));
                ObjectNode node = mapper.createObjectNode();
                ArrayNode array = node.putArray("history");
                for (java.util.Map<String, String> row : history) {
                    ObjectNode item = array.addObject();
                    item.put("status", row.get("status"));
                    item.put("action", row.get("action"));
                    item.put("actorRole", row.get("actorRole"));
                    item.put("at", row.get("at"));
                }
                return json(200, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/courses".equals(normalized)) {
                EnrollmentDomainService.Course course = domain.addCourse(actor, uuid(json, "programId"), uuid(json, "termId"), text(json, "code"),
                        json.path("credits").asInt(), json.path("core").asBoolean(false), uuidList(json.path("prerequisites")));
                ObjectNode node = mapper.createObjectNode();
                node.put("id", course.id.toString());
                node.put("code", course.code);
                return json(201, node, correlationId);
            }
            if ("POST".equals(method) && "/api/v1/enrollment/sections".equals(normalized)) {
                EnrollmentDomainService.Section section = domain.addSection(actor, uuid(json, "courseId"), json.path("capacity").asInt(), uuid(json, "facultyUserId"));
                ObjectNode node = mapper.createObjectNode();
                node.put("id", section.id.toString());
                node.put("courseId", section.courseId.toString());
                return json(201, node, correlationId);
            }
            return problem(404, "NOT_FOUND", "Route was not found", correlationId);
        } catch (EnrollmentException ex) {
            return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), correlationId);
        } catch (RuntimeException ex) {
            tracker.markFailed(ex);
            logger.error("Enrolment request could not be processed correlationId=" + correlationId, ex);
            return problem(400, "REQUEST_INVALID", "Request could not be processed", correlationId);
        } catch (Exception ex) {
            tracker.markFailed(ex);
            logger.error("Enrolment request failed correlationId=" + correlationId, ex);
            return problem(500, "UNEXPECTED", "The enrolment service could not complete the request", correlationId);
        } finally {
            tracker.close();
        }
    }

    private Actor actor(Map<String, String> headers, String correlationId) {
        String tenant = header(headers, "x-tenant-id");
        String user = header(headers, "x-user-id");
        if (tenant == null || user == null) {
            throw new EnrollmentException(401, "IDENTITY_REQUIRED", "Tenant and user identity are required");
        }
        UUID studentRecordId = null;
        String studentRecord = header(headers, "x-student-record-id");
        if (studentRecord != null && !studentRecord.trim().isEmpty()) {
            studentRecordId = UUID.fromString(studentRecord.trim());
        }
        return new Actor(UUID.fromString(tenant), UUID.fromString(user), studentRecordId, header(headers, "x-user-role"), correlationId);
    }

    private ObjectNode enrolmentNode(EnrollmentDomainService.Enrolment enrolment) {
        return enrolmentNode(enrolment, null);
    }

    private ObjectNode enrolmentNode(EnrollmentDomainService.Enrolment enrolment, Actor actor) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", enrolment.id.toString());
        node.put("studentId", enrolment.studentId.toString());
        node.put("applicationId", enrolment.applicationId.toString());
        node.put("programId", enrolment.programId.toString());
        node.put("termId", enrolment.termId.toString());
        node.put("status", enrolment.status);
        node.put("totalCredits", enrolment.totalCredits);
        if (actor != null) {
            EnrollmentDomainService.Term term = domain.resolveTermForEnrolment(actor, enrolment);
            if (term != null) {
                node.put("yearCode", term.yearCode);
                node.put("termCode", term.termCode);
                node.put("termName", term.name);
            }
        }
        ArrayNode history = node.putArray("history");
        for (java.util.Map<String, String> row : enrolment.history) {
            ObjectNode item = history.addObject();
            item.put("status", row.get("status"));
            item.put("action", row.get("action"));
            if (row.get("actorRole") != null) {
                item.put("actorRole", row.get("actorRole"));
            }
            item.put("at", row.get("at"));
        }
        ArrayNode selections = node.putArray("selections");
        for (EnrollmentDomainService.Selection selection : enrolment.selections) {
            ObjectNode item = selections.addObject();
            item.put("courseId", selection.courseId.toString());
            item.put("sectionId", selection.sectionId.toString());
        }
        return node;
    }

    private ObjectNode termNode(EnrollmentDomainService.Term term) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", term.id.toString());
        node.put("yearCode", term.yearCode);
        node.put("termCode", term.termCode);
        node.put("name", term.name);
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
        return json.path(field).asText(null);
    }

    private static UUID uuid(JsonNode json, String field) {
        return UUID.fromString(json.path(field).asText());
    }

    private static List<UUID> uuidList(JsonNode node) {
        List<UUID> values = new ArrayList<UUID>();
        if (node == null || !node.isArray()) {
            return values;
        }
        Iterator<JsonNode> iterator = node.elements();
        while (iterator.hasNext()) {
            values.add(UUID.fromString(iterator.next().asText()));
        }
        return values;
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
