package com.campx.academic.calendar.controller;

import com.campx.academic.calendar.exception.*;
import com.campx.academic.calendar.model.CalendarModels.*;
import com.campx.academic.calendar.model.ErrorResponse;
import com.campx.academic.calendar.service.CalendarDomainService;
import com.campx.academic.calendar.service.MetricsCollector;
import com.campx.academic.calendar.service.RateLimiter;
import com.campx.academic.calendar.service.StructuredLogEntry;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified HTTP REST Controller for ACD-07 Academic Calendar Service.
 * Implements endpoints, RBAC authorization, rate limiting, and structured audit logging.
 */
public class CalendarController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CalendarController.class);

    private final CalendarDomainService domainService;
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();
    private final RateLimiter rateLimiter = new RateLimiter(500, 100);

    // Regex path patterns
    private static final Pattern pCalendarItem = Pattern.compile("^/calendars/([^/]+)$");
    private static final Pattern pCalendarTerms = Pattern.compile("^/calendars/([^/]+)/terms$");
    private static final Pattern pTermItem = Pattern.compile("^/terms/([^/]+)$");
    private static final Pattern pCalendarEvents = Pattern.compile("^/calendars/([^/]+)/events$");
    private static final Pattern pEventItem = Pattern.compile("^/events/([^/]+)$");
    private static final Pattern pValidate = Pattern.compile("^/calendars/([^/]+)/validate$");
    private static final Pattern pImpact = Pattern.compile("^/calendars/([^/]+)/impact$");
    private static final Pattern pSubmit = Pattern.compile("^/calendars/([^/]+)/submit$");
    private static final Pattern pApproval = Pattern.compile("^/calendars/([^/]+)/approval$");
    private static final Pattern pPublish = Pattern.compile("^/calendars/([^/]+)/publish$");
    private static final Pattern pVersions = Pattern.compile("^/calendars/([^/]+)/versions$");
    private static final Pattern pEffectiveDate = Pattern.compile("^/calendars/([^/]+)/effective-date$");
    private static final Pattern pHistory = Pattern.compile("^/calendars/([^/]+)/history$");
    private static final Pattern pAnalytics = Pattern.compile("^/calendars/([^/]+)/analytics$");
    private static final Pattern pExport = Pattern.compile("^/calendars/([^/]+)/export$");

    public CalendarController(CalendarDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long startTime = System.currentTimeMillis();
        String fullPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // Normalize path by stripping prefixes
        String path = fullPath
                .replace("/api/v1/academics/calendars", "/calendars")
                .replace("/api/v1/calendars", "/calendars")
                .replace("/v1/calendars", "/calendars")
                .replace("/api/v1/academics/terms", "/terms")
                .replace("/v1/calendar-terms", "/terms")
                .replace("/api/v1/academics/events", "/events")
                .replace("/v1/calendar-events", "/events");

        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        }
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        String finalOutcome = "SUCCESS";
        int finalResponseCode = 200;
        String currentTenantId = "TENANT-001";
        String currentUserId = "admin-1";

        try {
            LogContext.setTraceId(traceId);

            String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                LogContext.setTenantId(tenantId);
            } else {
                tenantId = "TENANT-001";
            }
            currentTenantId = tenantId;

            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.trim().isEmpty()) {
                LogContext.setUserId(userId);
            } else {
                userId = "admin-1";
            }
            currentUserId = userId;

            String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
            if (userRole != null && !userRole.trim().isEmpty()) {
                LogContext.setUserRole(userRole);
            } else {
                userRole = "ACADEMIC_ADMIN";
            }

            // External API Key check (US-036, US-037)
            String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                ApiKeyRecord keyRecord = domainService.validateApiKey(apiKey.trim(), tenantId);
                if (keyRecord == null) {
                    finalOutcome = "UNAUTHORIZED";
                    finalResponseCode = 401;
                    sendError(exchange, 401, "ACD_CALENDAR_UNAUTHORIZED", "Invalid, revoked, or expired API Key", null, fullPath);
                    return;
                }
                userRole = "EXTERNAL_API";
                userId = "api-consumer-" + keyRecord.getKeyId();
                currentUserId = userId;
                LogContext.setUserRole(userRole);
                LogContext.setUserId(userId);

                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                    finalOutcome = "FORBIDDEN";
                    finalResponseCode = 403;
                    sendError(exchange, 403, "ACD_CALENDAR_FORBIDDEN", "External API consumers have read-only access", null, fullPath);
                    return;
                }
            }

            LogContext.setService("ACD-07-AcademicCalendarService");
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
            exchange.getResponseHeaders().set("X-Correlation-Id", traceId);

            // Operational Endpoints
            if ("/actuator/health".equals(fullPath) || fullPath.endsWith("/health")) {
                sendJson(exchange, 200, "{\"status\":\"UP\",\"service\":\"ACD-07-AcademicCalendarService\"}");
                return;
            }
            if ("/metrics".equals(fullPath) || fullPath.endsWith("/metrics")) {
                String prom = metricsCollector.toPrometheusFormat(domainService);
                byte[] bytes = prom.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            // Rate Limiter Check (US-038)
            RateLimiter.RateLimitResult rateCheck = rateLimiter.tryAcquire(tenantId + ":" + userId);
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateCheck.getLimit()));
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateCheck.getRemaining()));
            if (!rateCheck.isAllowed()) {
                finalOutcome = "RATE_LIMITED";
                finalResponseCode = 429;
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateCheck.getRetryAfterSeconds()));
                metricsCollector.recordError("ACD_CALENDAR_RATE_LIMIT_EXCEEDED");
                metricsCollector.recordRequest(method, fullPath, 429, System.currentTimeMillis() - startTime);
                sendError(exchange, 429, "ACD_CALENDAR_RATE_LIMIT_EXCEEDED", "Too many requests. Limit exceeded.", null, fullPath);
                return;
            }

            // RBAC Check (US-037)
            if (!isAuthorized(method, path, userRole, userId)) {
                finalOutcome = "FORBIDDEN";
                finalResponseCode = 403;
                metricsCollector.recordError("ACD_CALENDAR_FORBIDDEN");
                sendError(exchange, 403, "ACD_CALENDAR_FORBIDDEN",
                        "Role '" + userRole + "' is not permitted to execute " + method + " " + fullPath, null, fullPath);
                return;
            }

            // Read Request Body
            String requestBody = readBody(exchange);

            // Dispatch Request
            String responseJson;
            int responseCode = 200;

            // Route 1: POST /calendars (Create)
            if ("/calendars".equals(path) && "POST".equalsIgnoreCase(method)) {
                CreateCalendarRequest req = parseCreateCalendarRequest(requestBody);
                AcademicCalendar cal = domainService.createCalendar(req, tenantId, userId, userRole);
                responseJson = buildSuccessEnvelope(cal, "Academic calendar draft created successfully", traceId);
                responseCode = 201;
            }
            // Route 2: GET /calendars (List)
            else if ("/calendars".equals(path) && "GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                String campId = q.get("campusId");
                String year = q.get("academicYear");
                CalendarStatus st = q.get("status") != null ? CalendarStatus.valueOf(q.get("status").toUpperCase()) : null;
                List<AcademicCalendar> list = domainService.listCalendars(tenantId, campId, year, st);
                responseJson = buildSuccessEnvelope(list, "Calendars retrieved", traceId);
                responseCode = 200;
            }
            // Route 3: GET /calendars/current (Current Published)
            else if ("/calendars/current".equals(path) && "GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                String campId = q.get("campusId");
                String year = q.get("academicYear");
                AcademicCalendar cur = domainService.getCurrentPublishedCalendar(campId, year, tenantId);
                if (cur == null) {
                    throw new CalendarNotFoundException("No current published calendar found for campus " + campId + " year " + year);
                }
                responseJson = buildSuccessEnvelope(cur, "Current published calendar retrieved", traceId);
                responseCode = 200;
            }
            // Route 4: External Event Ingestion: POST /events
            else if ("/events".equals(path) && "POST".equalsIgnoreCase(method)) {
                Map<String, Object> eventMap = parseSimpleJsonMap(requestBody);
                boolean consumed = domainService.consumeEvent(eventMap);
                responseJson = "{\"success\":" + consumed + ",\"status\":\"" + (consumed ? "EVENT_PROCESSED" : "FAILED") + "\"}";
                responseCode = consumed ? 200 : 400;
            }
            // Pattern Routes
            else {
                responseJson = dispatchPatternRoutes(method, path, requestBody, tenantId, userId, userRole, traceId, exchange);
            }

            finalResponseCode = responseCode;
            metricsCollector.recordRequest(method, fullPath, responseCode, System.currentTimeMillis() - startTime);
            sendJson(exchange, responseCode, responseJson);

        } catch (CalendarException ce) {
            finalOutcome = "FAILURE";
            finalResponseCode = ce.getStatusCode();
            metricsCollector.recordError(ce.getErrorCode());
            metricsCollector.recordRequest(method, fullPath, ce.getStatusCode(), System.currentTimeMillis() - startTime);
            sendError(exchange, ce.getStatusCode(), ce.getErrorCode(), ce.getMessage(), null, fullPath);
        } catch (Exception ex) {
            finalOutcome = "SERVER_ERROR";
            finalResponseCode = 500;
            logger.error("Unhandled calendar exception on [{}]: {}", fullPath, ex.getMessage(), ex);
            metricsCollector.recordError("ACD_CALENDAR_INTERNAL_ERROR");
            metricsCollector.recordRequest(method, fullPath, 500, System.currentTimeMillis() - startTime);
            sendError(exchange, 500, "ACD_CALENDAR_INTERNAL_ERROR", "Internal server error: " + ex.getMessage(), null, fullPath);
        } finally {
            // Guarantee 100% structured audit logging
            long duration = System.currentTimeMillis() - startTime;
            StructuredLogEntry auditEntry = StructuredLogEntry.create(currentTenantId, traceId, traceId,
                    currentUserId, method + " " + fullPath, finalOutcome, duration);
            logger.info(auditEntry.toJson());
            LogContext.clear();
        }
    }

    private String dispatchPatternRoutes(String method, String path, String body, String tenantId,
                                         String userId, String userRole, String traceId, HttpExchange exchange) {
        Matcher m;

        // /calendars/{id}/terms (POST: add term, GET: list terms)
        if ((m = pCalendarTerms.matcher(path)).matches()) {
            String calId = m.group(1);
            if ("POST".equalsIgnoreCase(method)) {
                AddTermRequest req = parseAddTermRequest(body);
                CalendarTerm term = domainService.addTerm(calId, req, tenantId, userId, userRole);
                return buildSuccessEnvelope(term, "Term added to calendar", traceId);
            } else if ("GET".equalsIgnoreCase(method)) {
                List<CalendarTerm> terms = domainService.getCalendarTerms(calId, tenantId);
                return buildSuccessEnvelope(terms, "Calendar terms retrieved", traceId);
            }
        }

        // /terms/{id} (GET, PUT, DELETE)
        if ((m = pTermItem.matcher(path)).matches()) {
            String termId = m.group(1);
            if ("GET".equalsIgnoreCase(method)) {
                CalendarTerm term = domainService.getTerm(termId, tenantId);
                return buildSuccessEnvelope(term, "Term details retrieved", traceId);
            } else if ("PUT".equalsIgnoreCase(method)) {
                UpdateTermRequest req = parseUpdateTermRequest(body);
                CalendarTerm updated = domainService.updateTerm(termId, req, tenantId, userId, userRole);
                return buildSuccessEnvelope(updated, "Term updated successfully", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                CalendarTerm deact = domainService.deactivateTerm(termId, tenantId, userId, userRole);
                return buildSuccessEnvelope(deact, "Term deactivated", traceId);
            }
        }

        // /calendars/{id}/events (POST: add event, GET: list events)
        if ((m = pCalendarEvents.matcher(path)).matches()) {
            String calId = m.group(1);
            if ("POST".equalsIgnoreCase(method)) {
                AddEventRequest req = parseAddEventRequest(body);
                CalendarEvent event = domainService.addEvent(calId, req, tenantId, userId, userRole);
                return buildSuccessEnvelope(event, "Event added to calendar", traceId);
            } else if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                List<CalendarEvent> evList = domainService.getCalendarEvents(calId, tenantId, q.get("fromDate"), q.get("toDate"), q.get("eventType"), q.get("termId"));
                return buildSuccessEnvelope(evList, "Calendar events retrieved", traceId);
            }
        }

        // /events/{id} (GET, PUT, DELETE)
        if ((m = pEventItem.matcher(path)).matches()) {
            String eventId = m.group(1);
            if ("GET".equalsIgnoreCase(method)) {
                CalendarEvent event = domainService.getEvent(eventId, tenantId);
                return buildSuccessEnvelope(event, "Event details retrieved", traceId);
            } else if ("PUT".equalsIgnoreCase(method)) {
                UpdateEventRequest req = parseUpdateEventRequest(body);
                CalendarEvent updated = domainService.updateEvent(eventId, req, tenantId, userId, userRole);
                return buildSuccessEnvelope(updated, "Event updated", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                CalendarEvent deact = domainService.deactivateEvent(eventId, tenantId, userId, userRole);
                return buildSuccessEnvelope(deact, "Event deactivated", traceId);
            }
        }

        // /calendars/{id}/validate (POST: validate)
        if ((m = pValidate.matcher(path)).matches() && "POST".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            ValidationReport report = domainService.validateCalendar(calId, tenantId);
            return buildSuccessEnvelope(report, "Calendar validation completed", traceId);
        }

        // /calendars/{id}/impact (GET: change impact analysis)
        if ((m = pImpact.matcher(path)).matches() && "GET".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            Map<String, Object> impact = domainService.analyzeImpact(calId, tenantId);
            return buildSuccessEnvelope(impact, "Change impact analysis generated", traceId);
        }

        // /calendars/{id}/submit (POST: submit)
        if ((m = pSubmit.matcher(path)).matches() && "POST".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            SubmitCalendarRequest req = parseSubmitRequest(body);
            AcademicCalendar sub = domainService.submitCalendar(calId, req, tenantId, userId, userRole);
            return buildSuccessEnvelope(sub, "Calendar submitted for approval", traceId);
        }

        // /calendars/{id}/approval (POST: decide)
        if ((m = pApproval.matcher(path)).matches() && "POST".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            ApprovalRequest req = parseApprovalRequest(body);
            AcademicCalendar decided = domainService.decideApproval(calId, req, tenantId, userId, userRole);
            return buildSuccessEnvelope(decided, "Approval decision recorded", traceId);
        }

        // /calendars/{id}/publish (POST: publish)
        if ((m = pPublish.matcher(path)).matches() && "POST".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            PublishCalendarRequest req = parsePublishRequest(body);
            AcademicCalendar pub = domainService.publishCalendar(calId, req, tenantId, userId, userRole);
            return buildSuccessEnvelope(pub, "Calendar published successfully", traceId);
        }

        // /calendars/{id}/versions (POST: clone new version)
        if ((m = pVersions.matcher(path)).matches() && "POST".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            CloneVersionRequest req = parseCloneRequest(body);
            AcademicCalendar cloned = domainService.cloneVersion(calId, req, tenantId, userId, userRole);
            return buildSuccessEnvelope(cloned, "New calendar version draft cloned", traceId);
        }

        // /calendars/{id}/effective-date (GET: date resolution)
        if ((m = pEffectiveDate.matcher(path)).matches() && "GET".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
            String date = q.get("date");
            if (date == null || date.trim().isEmpty()) {
                throw new CalendarBadRequestException("date query parameter is required (YYYY-MM-DD)");
            }
            EffectiveDateResolution res = domainService.resolveEffectiveDate(calId, date.trim(), tenantId);
            return buildSuccessEnvelope(res, "Effective date status resolved", traceId);
        }

        // /calendars/{id}/history (GET: history audit)
        if ((m = pHistory.matcher(path)).matches() && "GET".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            List<CalendarHistory> hist = domainService.getCalendarHistory(calId, tenantId);
            return buildSuccessEnvelope(hist, "Calendar history retrieved", traceId);
        }

        // /calendars/{id}/analytics (GET: analytics)
        if ((m = pAnalytics.matcher(path)).matches() && "GET".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            CalendarAnalytics analytics = domainService.getCalendarAnalytics(calId, tenantId);
            return buildSuccessEnvelope(analytics, "Calendar analytics retrieved", traceId);
        }

        // /calendars/{id}/export (GET: export)
        if ((m = pExport.matcher(path)).matches() && "GET".equalsIgnoreCase(method)) {
            String calId = m.group(1);
            Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
            Map<String, Object> exp = domainService.exportCalendar(calId, q.get("format"), tenantId, userId, userRole);
            return buildSuccessEnvelope(exp, "Calendar exported", traceId);
        }

        // /calendars/{id} (GET: detail, PUT: update, DELETE: cancel)
        if ((m = pCalendarItem.matcher(path)).matches()) {
            String calId = m.group(1);
            if ("GET".equalsIgnoreCase(method)) {
                AcademicCalendar cal = domainService.getCalendar(calId, tenantId);
                return buildSuccessEnvelope(cal, "Calendar retrieved", traceId);
            } else if ("PUT".equalsIgnoreCase(method)) {
                UpdateCalendarRequest req = parseUpdateCalendarRequest(body);
                AcademicCalendar updated = domainService.updateCalendar(calId, req, tenantId, userId, userRole);
                return buildSuccessEnvelope(updated, "Calendar updated", traceId);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
                AcademicCalendar cancelled = domainService.cancelCalendar(calId, tenantId, userId, userRole, q.get("reason"));
                return buildSuccessEnvelope(cancelled, "Calendar cancelled", traceId);
            }
        }

        throw new CalendarBadRequestException("Unrecognized endpoint: " + method + " " + path);
    }

    // =========================================================================
    // RBAC Authorization Matrix (US-037)
    // =========================================================================

    private boolean isAuthorized(String method, String path, String userRole, String userId) {
        if (userRole == null || userRole.trim().isEmpty()) {
            return false;
        }
        String role = userRole.toUpperCase();

        if ("SUPER_ADMIN".equals(role) || "ACADEMIC_ADMIN".equals(role) || "ADMIN".equals(role)) {
            return true;
        }
        if ("REGISTRAR".equals(role)) {
            return true; // can approve, publish, clone, view
        }
        if ("MANAGEMENT".equals(role) || "DEAN".equals(role) || "HOD".equals(role)) {
            return "GET".equalsIgnoreCase(method); // read-only management access
        }
        if ("FACULTY".equals(role) || "STUDENT".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }
        if ("EXTERNAL_API".equals(role) || "SYSTEM".equals(role)) {
            return "GET".equalsIgnoreCase(method);
        }
        return false;
    }

    // =========================================================================
    // Request Parsers
    // =========================================================================

    private CreateCalendarRequest parseCreateCalendarRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        CreateCalendarRequest req = new CreateCalendarRequest();
        req.calendarCode = m.get("calendarCode");
        req.name = m.get("name");
        req.description = m.get("description");
        req.timezone = m.get("timezone");
        req.academicYear = m.get("academicYear");
        req.campusId = m.get("campusId");
        req.institutionId = m.get("institutionId");
        req.effectiveFrom = m.get("effectiveFrom");
        req.effectiveTo = m.get("effectiveTo");
        return req;
    }

    private UpdateCalendarRequest parseUpdateCalendarRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        UpdateCalendarRequest req = new UpdateCalendarRequest();
        req.name = m.get("name");
        req.description = m.get("description");
        req.timezone = m.get("timezone");
        req.effectiveFrom = m.get("effectiveFrom");
        req.effectiveTo = m.get("effectiveTo");
        if (m.containsKey("expectedVersion")) {
            req.expectedVersion = Integer.parseInt(m.get("expectedVersion"));
        }
        return req;
    }

    private AddTermRequest parseAddTermRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        AddTermRequest req = new AddTermRequest();
        req.termCode = m.get("termCode");
        req.name = m.get("name");
        if (m.containsKey("sequenceNo")) req.sequenceNo = Integer.parseInt(m.get("sequenceNo"));
        req.startDate = m.get("startDate");
        req.endDate = m.get("endDate");
        req.instructionalStartDate = m.get("instructionalStartDate");
        req.instructionalEndDate = m.get("instructionalEndDate");
        return req;
    }

    private UpdateTermRequest parseUpdateTermRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        UpdateTermRequest req = new UpdateTermRequest();
        req.name = m.get("name");
        req.startDate = m.get("startDate");
        req.endDate = m.get("endDate");
        req.instructionalStartDate = m.get("instructionalStartDate");
        req.instructionalEndDate = m.get("instructionalEndDate");
        if (m.containsKey("expectedVersion")) req.expectedVersion = Integer.parseInt(m.get("expectedVersion"));
        return req;
    }

    private AddEventRequest parseAddEventRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        AddEventRequest req = new AddEventRequest();
        req.termId = m.get("termId");
        req.eventCode = m.get("eventCode");
        req.eventType = m.get("eventType");
        req.title = m.get("title");
        req.description = m.get("description");
        req.startDate = m.get("startDate");
        req.endDate = m.get("endDate");
        if (m.containsKey("allDay")) req.allDay = Boolean.parseBoolean(m.get("allDay"));
        req.workingDayImpact = m.get("workingDayImpact");
        req.category = m.get("category");
        return req;
    }

    private UpdateEventRequest parseUpdateEventRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        UpdateEventRequest req = new UpdateEventRequest();
        req.title = m.get("title");
        req.description = m.get("description");
        req.startDate = m.get("startDate");
        req.endDate = m.get("endDate");
        if (m.containsKey("allDay")) req.allDay = Boolean.parseBoolean(m.get("allDay"));
        req.workingDayImpact = m.get("workingDayImpact");
        req.category = m.get("category");
        if (m.containsKey("expectedVersion")) req.expectedVersion = Integer.parseInt(m.get("expectedVersion"));
        return req;
    }

    private SubmitCalendarRequest parseSubmitRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        SubmitCalendarRequest req = new SubmitCalendarRequest();
        req.reason = m.get("reason");
        return req;
    }

    private ApprovalRequest parseApprovalRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        ApprovalRequest req = new ApprovalRequest();
        req.decision = m.get("decision");
        req.reason = m.get("reason");
        return req;
    }

    private PublishCalendarRequest parsePublishRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        PublishCalendarRequest req = new PublishCalendarRequest();
        if (m.containsKey("expectedVersion")) req.expectedVersion = Integer.parseInt(m.get("expectedVersion"));
        req.effectiveFrom = m.get("effectiveFrom");
        req.effectiveTo = m.get("effectiveTo");
        return req;
    }

    private CloneVersionRequest parseCloneRequest(String body) {
        Map<String, String> m = parseSimpleJson(body);
        CloneVersionRequest req = new CloneVersionRequest();
        req.reason = m.get("reason");
        return req;
    }

    // =========================================================================
    // Response Serialization Helpers
    // =========================================================================

    private String buildSuccessEnvelope(Object data, String message, String traceId) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"success\":true,");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"requestId\":\"").append(traceId).append("\",");
        sb.append("\"correlationId\":\"").append(traceId).append("\",");
        sb.append("\"data\":").append(serializeAny(data));
        sb.append("}");
        return sb.toString();
    }

    private String serializeAny(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof AcademicCalendar) {
            AcademicCalendar c = (AcademicCalendar) obj;
            return "{\"id\":\"" + c.getId() + "\",\"calendarCode\":\"" + c.getCalendarCode() +
                    "\",\"name\":\"" + escape(c.getName()) + "\",\"academicYear\":\"" + c.getAcademicYear() +
                    "\",\"campusId\":\"" + c.getCampusId() + "\",\"institutionId\":\"" + c.getInstitutionId() +
                    "\",\"status\":\"" + c.getStatus() + "\",\"currentVersion\":" + c.getCurrentVersion() +
                    ",\"effectiveFrom\":\"" + c.getEffectiveFrom() + "\",\"effectiveTo\":\"" + c.getEffectiveTo() + "\"}";
        }
        if (obj instanceof CalendarTerm) {
            CalendarTerm t = (CalendarTerm) obj;
            return "{\"id\":\"" + t.getId() + "\",\"calendarId\":\"" + t.getCalendarId() +
                    "\",\"termCode\":\"" + t.getTermCode() + "\",\"name\":\"" + escape(t.getName()) +
                    "\",\"sequenceNo\":" + t.getSequenceNo() + ",\"startDate\":\"" + t.getStartDate() +
                    "\",\"endDate\":\"" + t.getEndDate() + "\",\"status\":\"" + t.getStatus() +
                    "\",\"version\":" + t.getVersion() + "}";
        }
        if (obj instanceof CalendarEvent) {
            CalendarEvent e = (CalendarEvent) obj;
            return "{\"id\":\"" + e.getId() + "\",\"calendarId\":\"" + e.getCalendarId() +
                    "\",\"eventCode\":\"" + e.getEventCode() + "\",\"eventType\":\"" + e.getEventType() +
                    "\",\"title\":\"" + escape(e.getTitle()) + "\",\"startDate\":\"" + e.getStartDate() +
                    "\",\"endDate\":\"" + e.getEndDate() + "\",\"workingDayImpact\":\"" + e.getWorkingDayImpact() +
                    "\",\"version\":" + e.getVersion() + "}";
        }
        if (obj instanceof CalendarHistory) {
            CalendarHistory h = (CalendarHistory) obj;
            return "{\"id\":\"" + h.getId() + "\",\"calendarId\":\"" + h.getCalendarId() +
                    "\",\"action\":\"" + h.getAction() + "\",\"statusFrom\":\"" + h.getStatusFrom() +
                    "\",\"statusTo\":\"" + h.getStatusTo() + "\",\"actorId\":\"" + h.getActorId() +
                    "\",\"timestamp\":" + h.getTimestamp() + ",\"version\":" + h.getVersion() + "}";
        }
        if (obj instanceof ValidationReport) {
            ValidationReport vr = (ValidationReport) obj;
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"valid\":").append(vr.valid).append(",");
            sb.append("\"blockingCount\":").append(vr.blockingCount).append(",");
            sb.append("\"warningCount\":").append(vr.warningCount).append(",");
            sb.append("\"issues\":[");
            for (int i = 0; i < vr.issues.size(); i++) {
                if (i > 0) sb.append(",");
                ValidationIssue is = vr.issues.get(i);
                sb.append("{\"severity\":\"").append(is.severity).append("\",\"ruleCode\":\"").append(is.ruleCode)
                        .append("\",\"message\":\"").append(escape(is.message)).append("\"}");
            }
            sb.append("]}");
            return sb.toString();
        }
        if (obj instanceof EffectiveDateResolution) {
            EffectiveDateResolution r = (EffectiveDateResolution) obj;
            return "{\"operatingDate\":\"" + r.operatingDate + "\",\"calendarId\":\"" + r.calendarId +
                    "\",\"calendarVersion\":" + r.calendarVersion + ",\"termId\":\"" + r.termId +
                    "\",\"termCode\":\"" + r.termCode + "\",\"isInstructionalDay\":" + r.isInstructionalDay +
                    ",\"isHoliday\":" + r.isHoliday + ",\"holidayTitle\":\"" + escape(r.holidayTitle) +
                    "\",\"workingDayStatus\":\"" + r.workingDayStatus + "\",\"precedenceReason\":\"" + escape(r.effectivePrecedenceReason) + "\"}";
        }
        if (obj instanceof CalendarAnalytics) {
            CalendarAnalytics a = (CalendarAnalytics) obj;
            return "{\"calendarId\":\"" + a.calendarId + "\",\"totalTerms\":" + a.totalTerms +
                    ",\"totalEvents\":" + a.totalEvents + ",\"totalHolidays\":" + a.totalHolidays +
                    ",\"totalInstructionalDays\":" + a.totalInstructionalDays +
                    ",\"workingDayOverrides\":" + a.workingDayOverrides + "}";
        }
        if (obj instanceof List) {
            List<?> l = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeAny(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(escape(String.valueOf(e.getKey()))).append("\":");
                sb.append(serializeAny(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(obj)) + "\"";
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String errorCode, String detail,
                           List<ErrorResponse.InvalidParam> invalidParams, String instance) throws IOException {
        ErrorResponse err = new ErrorResponse("urn:campx:error:academic:calendar:" + errorCode.toLowerCase().replace('_', '-'),
                status == 404 ? "Not Found" : (status == 409 ? "Conflict" : (status == 422 ? "Unprocessable Entity" : "Error")),
                status, detail, instance, errorCode);
        if (invalidParams != null) err.setInvalidParams(invalidParams);

        String json = "{\"type\":\"" + err.getType() + "\",\"title\":\"" + err.getTitle() +
                "\",\"status\":" + err.getStatus() + ",\"detail\":\"" + escape(err.getDetail()) +
                "\",\"instance\":\"" + escape(err.getInstance()) + "\",\"errorCode\":\"" + err.getErrorCode() +
                "\",\"code\":\"" + err.getCode() + "\",\"timestamp\":\"" + err.getTimestamp() + "\"}";

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        }
    }

    private Map<String, String> parseSimpleJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("{")) return map;
        String content = json.trim().replaceAll("^\\{", "").replaceAll("\\}$", "").trim();
        if (content.isEmpty()) return map;

        String[] tokens = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String token : tokens) {
            String[] kv = token.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String val = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, val);
            }
        }
        return map;
    }

    private Map<String, Object> parseSimpleJsonMap(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty() || !json.contains("{")) return map;
        Map<String, String> raw = parseSimpleJson(json);
        map.putAll(raw);
        return map;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    params.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                            URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            } catch (Exception ignored) {}
        }
        return params;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
