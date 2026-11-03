package com.campx.admissions.controller;

import com.campx.admissions.exception.*;
import com.campx.admissions.logging.AdmissionsLoggingService;
import com.campx.admissions.model.dto.*;
import com.campx.admissions.security.GatewayHmacVerifier;
import com.campx.admissions.security.UserSecurityContext;
import com.campx.admissions.service.ApplicationDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Controller handling admissions REST routing, RFC 7807 problem details, and security contexts.
 */
public class ApplicationController {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ApplicationController.class);

    private final ApplicationDomainService domainService;
    private final GatewayHmacVerifier hmacVerifier;
    private final ObjectMapper objectMapper;
    private final AdmissionsLoggingService loggingService;
    private final AdmissionsExceptionHandler exceptionHandler;

    public static class HttpResponse {
        public final int statusCode;
        public final String body;
        public final Map<String, String> headers = new HashMap<>();

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers.put("Content-Type", "application/json");
        }
    }

    public ApplicationController(ApplicationDomainService domainService) {
        this(domainService, new GatewayHmacVerifier(System.getenv("CAMPX_GATEWAY_SECRET")));
    }

    public ApplicationController(ApplicationDomainService domainService, GatewayHmacVerifier hmacVerifier) {
        this.domainService = domainService;
        this.hmacVerifier = hmacVerifier;
        this.objectMapper = new ObjectMapper();
        try {
            Class<?> clazz = Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            this.objectMapper.registerModule((com.fasterxml.jackson.databind.Module) clazz.getDeclaredConstructor().newInstance());
            this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        } catch (Throwable ignored) {}
        this.loggingService = new AdmissionsLoggingService();
        this.exceptionHandler = new AdmissionsExceptionHandler(this.objectMapper, this.loggingService);
    }

    public HttpResponse handleRequest(String method, String path, Map<String, String> headers, String body) {
        long startTime = System.currentTimeMillis();
        String correlationId = headers.get("x-correlation-id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        if ("GET".equalsIgnoreCase(method) && (path.endsWith("/actuator/health") || path.equals("/health"))) {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("service", "admissions-service");
            health.put("port", 8094);
            return ok(health);
        }
        if ("GET".equalsIgnoreCase(method) && path.endsWith("/metrics")) {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("service", "admissions-service");
            metrics.put("activeThreads", Thread.activeCount());
            metrics.put("jvmMemoryUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            return ok(metrics);
        }

        String timestamp = headers.get("x-gateway-timestamp");
        String signature = headers.get("x-gateway-signature");

        try {
            // 1. Verify Gateway HMAC Boundary
            if (hmacVerifier != null && timestamp != null && signature != null) {
                hmacVerifier.verify(method, path, timestamp, signature, body);
            }

            // 2. Extract User Security Context
            String userId = headers.get("x-user-id");
            String tenantId = headers.get("x-tenant-id");
            String collegeId = headers.get("x-college-id");
            String deptId = headers.get("x-department-id");
            String role = headers.get("x-user-role");

            // Allow public intake discovery without tenant header if path is public
            if (tenantId == null && path.endsWith("/intakes/active")) {
                tenantId = "00000000-0000-0000-0000-000000000000";
            }

            loggingService.logRequest(method, path, correlationId, tenantId, userId, role);
            UserSecurityContext context = UserSecurityContext.fromHeaders(userId, tenantId, collegeId, deptId, role);

            // 3. Dispatch to route handlers
            HttpResponse response;
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/intakes/active")) {
                List<IntakeResponse> intakes = domainService.listActiveIntakes(context.getTenantId());
                response = ok(intakes);
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/intakes")) {
                ConfigureIntakeRequest req = objectMapper.readValue(body, ConfigureIntakeRequest.class);
                IntakeResponse created = domainService.configureIntake(context, req);
                response = created(created);
            } else if ("GET".equalsIgnoreCase(method) && path.contains("/intakes/")) {
                UUID intakeId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
                response = ok(domainService.getIntake(context.getTenantId(), intakeId));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/applications")) {
                CreateApplicationRequest req = objectMapper.readValue(body, CreateApplicationRequest.class);
                ApplicationResponse created = domainService.createDraftApplication(context, req);
                response = created(created);
            } else if ("PATCH".equalsIgnoreCase(method) && path.contains("/applications/")) {
                UUID appId = extractApplicationId(path);
                Integer expectedVersion = parseVersionHeader(headers.get("if-match"));
                UpdateApplicationRequest req = objectMapper.readValue(body, UpdateApplicationRequest.class);
                response = ok(domainService.updateDraftApplication(context, appId, req, expectedVersion));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/submit")) {
                UUID appId = extractApplicationId(path);
                SubmitApplicationRequest req = (body != null && !body.trim().isEmpty())
                        ? objectMapper.readValue(body, SubmitApplicationRequest.class)
                        : new SubmitApplicationRequest();
                response = ok(domainService.submitApplication(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/return")) {
                UUID appId = extractApplicationId(path);
                String reason = extractReasonFromBody(body);
                response = ok(domainService.returnForInformation(context, appId, reason));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/resubmit")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.resubmitApplication(context, appId));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/withdraw")) {
                UUID appId = extractApplicationId(path);
                WithdrawApplicationRequest req = (body != null && !body.trim().isEmpty())
                        ? objectMapper.readValue(body, WithdrawApplicationRequest.class)
                        : new WithdrawApplicationRequest();
                response = ok(domainService.withdrawApplication(context, appId, req));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/documents")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getApplicationDocuments(context, appId));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/documents")) {
                UUID appId = extractApplicationId(path);
                AttachDocumentRequest req = objectMapper.readValue(body, AttachDocumentRequest.class);
                response = created(domainService.attachDocument(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.contains("/documents/") && path.endsWith("/verify")) {
                UUID appId = extractApplicationId(path);
                UUID docId = extractDocumentId(path);
                VerifyDocumentRequest req = objectMapper.readValue(body, VerifyDocumentRequest.class);
                response = ok(domainService.verifyDocument(context, appId, docId, req));
            } else if ("DELETE".equalsIgnoreCase(method) && path.contains("/documents/")) {
                UUID appId = extractApplicationId(path);
                UUID docId = extractDocumentId(path);
                domainService.deleteDocument(context, appId, docId);
                response = ok(Collections.singletonMap("deleted", true));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/assign")) {
                UUID appId = extractApplicationId(path);
                AssignReviewerRequest req = objectMapper.readValue(body, AssignReviewerRequest.class);
                domainService.assignReviewer(context, appId, req);
                response = ok(Collections.singletonMap("assigned", true));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/reviews")) {
                UUID appId = extractApplicationId(path);
                SubmitReviewRequest req = objectMapper.readValue(body, SubmitReviewRequest.class);
                response = ok(domainService.submitReview(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/decision")) {
                UUID appId = extractApplicationId(path);
                RecordDecisionRequest req = objectMapper.readValue(body, RecordDecisionRequest.class);
                response = ok(domainService.recordDecision(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/offers")) {
                UUID appId = extractApplicationId(path);
                IssueOfferRequest req = objectMapper.readValue(body, IssueOfferRequest.class);
                response = created(domainService.issueOffer(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/offers/accept")) {
                UUID appId = extractApplicationId(path);
                AcceptOfferRequest req = (body != null && !body.trim().isEmpty())
                        ? objectMapper.readValue(body, AcceptOfferRequest.class)
                        : new AcceptOfferRequest();
                response = ok(domainService.acceptOffer(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/offers/decline")) {
                UUID appId = extractApplicationId(path);
                DeclineOfferRequest req = (body != null && !body.trim().isEmpty())
                        ? objectMapper.readValue(body, DeclineOfferRequest.class)
                        : new DeclineOfferRequest();
                response = ok(domainService.declineOffer(context, appId, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/payment-intent")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.createPaymentIntent(context, appId));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/payment-confirm")) {
                PaymentWebhookRequest req = objectMapper.readValue(body, PaymentWebhookRequest.class);
                response = ok(domainService.receivePaymentConfirmation(context, req));
            } else if ("POST".equalsIgnoreCase(method) && path.endsWith("/handover")) {
                UUID appId = extractApplicationId(path);
                String idempKey = headers.get("idempotency-key");
                response = ok(domainService.triggerHandover(context, appId, idempKey));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/reports/funnel")) {
                String intakeParam = headers.get("x-intake-id");
                UUID intakeId = (intakeParam != null) ? UUID.fromString(intakeParam) : UUID.randomUUID();
                response = ok(domainService.getFunnelReport(context, intakeId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/intelligence/funnel")) {
                String intakeParam = headers.get("x-intake-id");
                UUID intakeId = (intakeParam != null) ? UUID.fromString(intakeParam) : null;
                response = ok(domainService.getIntelligenceService().calculateAndPublishFunnelMetrics(context.getTenantId(), intakeId, correlationId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/intelligence/demand")) {
                response = ok(domainService.getIntelligenceService().calculateAndPublishDemandMetrics(context.getTenantId(), correlationId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/intelligence/operations")) {
                response = ok(domainService.getIntelligenceService().calculateAndPublishOperationsMetrics(context.getTenantId(), correlationId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/intelligence/decisions")) {
                response = ok(domainService.getIntelligenceService().calculateAndPublishDecisionMetrics(context.getTenantId(), correlationId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/intelligence/conversions")) {
                response = ok(domainService.getIntelligenceService().calculateAndPublishConversionMetrics(context.getTenantId(), correlationId));
            } else if ("GET".equalsIgnoreCase(method) && (path.endsWith("/applications") || path.endsWith("/applications/"))) {
                String intakeParam = headers.get("x-intake-id");
                UUID intakeId = (intakeParam != null && !intakeParam.trim().isEmpty()) ? UUID.fromString(intakeParam.trim()) : null;
                String statusParam = headers.get("x-status");
                com.campx.admissions.model.ApplicationStatus status = (statusParam != null && !statusParam.trim().isEmpty())
                        ? com.campx.admissions.model.ApplicationStatus.valueOf(statusParam.trim().toUpperCase()) : null;
                response = ok(domainService.listApplications(context, intakeId, status));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/reviews")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getApplicationReviews(context, appId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/decision")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getApplicationDecision(context, appId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/offer")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getApplicationOffer(context, appId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/handover")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getHandover(context, appId));
            } else if ("GET".equalsIgnoreCase(method) && path.endsWith("/history")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getStatusHistory(context, appId));
            } else if ("GET".equalsIgnoreCase(method) && path.matches(".*/applications/[a-f0-9\\-]+$")) {
                UUID appId = extractApplicationId(path);
                response = ok(domainService.getApplication(context, appId));
            } else {
                throw new AdmissionsNotFoundException("The requested admissions route was not found: " + path);
            }

            response.headers.put("X-Correlation-Id", correlationId);
            long duration = System.currentTimeMillis() - startTime;
            loggingService.logResponse(method, path, response.statusCode, duration, correlationId);
            return response;

        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            HttpResponse errorResp = exceptionHandler.handleException(e, method, path, correlationId);
            errorResp.headers.put("X-Correlation-Id", correlationId);
            loggingService.logResponse(method, path, errorResp.statusCode, duration, correlationId);
            return errorResp;
        }
    }

    private UUID extractApplicationId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("applications".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                try {
                    return UUID.fromString(parts[i + 1]);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        throw new AdmissionsValidationException("Invalid or missing application ID in URI path");
    }

    private UUID extractDocumentId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("documents".equalsIgnoreCase(parts[i]) && i + 1 < parts.length) {
                try {
                    return UUID.fromString(parts[i + 1]);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        throw new AdmissionsValidationException("Invalid or missing document ID in URI path");
    }

    private Integer parseVersionHeader(String ifMatch) {
        if (ifMatch == null || ifMatch.trim().isEmpty()) return null;
        try {
            String clean = ifMatch.replace("\"", "").replace("W/", "").trim();
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractReasonFromBody(String body) {
        if (body == null || body.trim().isEmpty()) return "Information requested";
        try {
            Map<?, ?> map = objectMapper.readValue(body, Map.class);
            Object r = map.get("reason");
            return r != null ? r.toString() : "Information requested";
        } catch (Exception e) {
            return "Information requested";
        }
    }

    private HttpResponse ok(Object bodyObj) {
        try {
            return new HttpResponse(200, objectMapper.writeValueAsString(bodyObj));
        } catch (Exception e) {
            return new HttpResponse(500, "{\"error\":\"serialization_failed\"}");
        }
    }

    private HttpResponse created(Object bodyObj) {
        try {
            return new HttpResponse(201, objectMapper.writeValueAsString(bodyObj));
        } catch (Exception e) {
            return new HttpResponse(500, "{\"error\":\"serialization_failed\"}");
        }
    }

    private HttpResponse error(int status, String code, String message) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://campx.com/errors/" + code.toLowerCase());
        problem.put("title", code);
        problem.put("status", status);
        problem.put("detail", message);
        problem.put("timestamp", OffsetDateTime.now().toString());
        problem.put("correlationId", UUID.randomUUID().toString());

        try {
            return new HttpResponse(status, objectMapper.writeValueAsString(problem));
        } catch (Exception e) {
            return new HttpResponse(status, "{\"status\":" + status + ",\"detail\":\"" + message + "\"}");
        }
    }
}
