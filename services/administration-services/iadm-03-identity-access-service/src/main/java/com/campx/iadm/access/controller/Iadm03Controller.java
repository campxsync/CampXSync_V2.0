package com.campx.iadm.access.controller;

import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GatewayHmacVerifier;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.iadm.access.service.AccessGovernanceService;
import com.campx.iadm.access.service.AccessRequestGovernanceService;
import com.campx.iadm.access.service.AccessReviewGovernanceService;
import com.campx.iadm.access.service.AuditEvidenceService;
import com.campx.iadm.access.service.CatalogChangeGovernanceService;
import com.campx.iadm.access.service.CatalogueReadService;
import com.campx.iadm.access.service.DelegationGovernanceService;
import com.campx.iadm.access.service.DurableAuditWriter;
import com.campx.iadm.access.service.GovernanceReadService;
import com.campx.iadm.access.service.GrantGovernanceService;
import com.campx.iadm.access.service.HandoverStatusService;
import com.campx.iadm.access.service.ProfileGovernanceService;
import com.campx.iadm.access.service.RecordOnlyGrantWorkflowService;
import com.campx.iadm.access.service.ServiceRegistrationService;
import com.campx.iadm.access.service.V2AccessReviewService;
import com.campx.iadm.access.service.V2DelegationService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP ingress for IADM-03 (port 8101) under /api/v1/iadm/03.
 */
public class Iadm03Controller implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm03Controller.class);

    private final AccessGovernanceService service;
    private final ProfileGovernanceService profiles;
    private final GrantGovernanceService grants;
    private final DelegationGovernanceService delegations;
    private final AccessRequestGovernanceService accessRequests;
    private final CatalogChangeGovernanceService catalogChanges;
    private final GovernanceReadService governanceRead;
    private final AccessReviewGovernanceService accessReviews;
    private final AuditEvidenceService auditEvidence = new AuditEvidenceService();
    private final CatalogueReadService catalogueRead = new CatalogueReadService();
    private final RecordOnlyGrantWorkflowService grantWorkflow = new RecordOnlyGrantWorkflowService();
    private final V2AccessReviewService v2Reviews = new V2AccessReviewService();
    private final V2DelegationService v2Delegations = new V2DelegationService();
    private final ServiceRegistrationService serviceRegistrations = new ServiceRegistrationService();
    private final HandoverStatusService handoverStatus = new HandoverStatusService();
    private final GatewayHmacVerifier hmacVerifier = new GatewayHmacVerifier();

    public Iadm03Controller(AccessGovernanceService service) {
        this(service, new ProfileGovernanceService(), new GrantGovernanceService(),
                new DelegationGovernanceService(), new AccessRequestGovernanceService(),
                new CatalogChangeGovernanceService(), new GovernanceReadService(),
                new AccessReviewGovernanceService());
    }

    public Iadm03Controller(AccessGovernanceService service, ProfileGovernanceService profiles) {
        this(service, profiles, new GrantGovernanceService(), new DelegationGovernanceService(),
                new AccessRequestGovernanceService(), new CatalogChangeGovernanceService(),
                new GovernanceReadService(), new AccessReviewGovernanceService());
    }

    public Iadm03Controller(AccessGovernanceService service, ProfileGovernanceService profiles,
                            GrantGovernanceService grants) {
        this(service, profiles, grants, new DelegationGovernanceService(), new AccessRequestGovernanceService(),
                new CatalogChangeGovernanceService(), new GovernanceReadService(),
                new AccessReviewGovernanceService());
    }

    public Iadm03Controller(AccessGovernanceService service, ProfileGovernanceService profiles,
                            GrantGovernanceService grants, DelegationGovernanceService delegations) {
        this(service, profiles, grants, delegations, new AccessRequestGovernanceService(),
                new CatalogChangeGovernanceService(), new GovernanceReadService(),
                new AccessReviewGovernanceService());
    }

    public Iadm03Controller(AccessGovernanceService service, ProfileGovernanceService profiles,
                            GrantGovernanceService grants, DelegationGovernanceService delegations,
                            AccessRequestGovernanceService accessRequests) {
        this(service, profiles, grants, delegations, accessRequests,
                new CatalogChangeGovernanceService(), new GovernanceReadService(),
                new AccessReviewGovernanceService());
    }

    public Iadm03Controller(AccessGovernanceService service, ProfileGovernanceService profiles,
                            GrantGovernanceService grants, DelegationGovernanceService delegations,
                            AccessRequestGovernanceService accessRequests,
                            CatalogChangeGovernanceService catalogChanges,
                            GovernanceReadService governanceRead) {
        this(service, profiles, grants, delegations, accessRequests, catalogChanges, governanceRead,
                new AccessReviewGovernanceService());
    }

    public Iadm03Controller(AccessGovernanceService service, ProfileGovernanceService profiles,
                            GrantGovernanceService grants, DelegationGovernanceService delegations,
                            AccessRequestGovernanceService accessRequests,
                            CatalogChangeGovernanceService catalogChanges,
                            GovernanceReadService governanceRead,
                            AccessReviewGovernanceService accessReviews) {
        this.service = service;
        this.profiles = profiles == null ? new ProfileGovernanceService() : profiles;
        this.grants = grants == null ? new GrantGovernanceService() : grants;
        this.delegations = delegations == null ? new DelegationGovernanceService() : delegations;
        this.accessRequests = accessRequests == null ? new AccessRequestGovernanceService() : accessRequests;
        this.catalogChanges = catalogChanges == null ? new CatalogChangeGovernanceService() : catalogChanges;
        this.governanceRead = governanceRead == null ? new GovernanceReadService() : governanceRead;
        this.accessReviews = accessReviews == null ? new AccessReviewGovernanceService() : accessReviews;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PATCH,OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String correlationId = header(exchange, "X-Correlation-Id");
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = header(exchange, "X-Trace-Id");
        }
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        LogContext.put(LogContext.KEY_SERVICE, "IADM-03");
        LogContext.put(LogContext.KEY_TRACE_ID, correlationId);

        byte[] bodyBytes = readAll(exchange.getRequestBody());
        FlowTracker tracker = logger.flow(method + " " + path, correlationId);
        try {
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/health") || path.endsWith("/healthz"))) {
                sendJson(exchange, 200, toJson(service.health()));
                return;
            }
            // Pure ceiling evaluation + claims contract (no tenant data) — usable for smoke without HMAC
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/ceilings/evaluate")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String grantor = extract(body, "grantor");
                String target = extract(body, "targetRole");
                boolean self = "true".equalsIgnoreCase(extract(body, "selfGrant"));
                String deny = GrantorCeiling.denyReason(grantor, target, self);
                Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
                out.put("allowed", Boolean.valueOf(deny == null));
                out.put("denyReason", deny);
                sendJson(exchange, 200, toJson(out));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/claims/contract")) {
                sendJson(exchange, 200, toJson(governanceRead.claimsContract()));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/governance/contract")) {
                // Public metadata twin of claims contract (US-031)
                sendJson(exchange, 200, toJson(governanceRead.claimsContract()));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/notifications/contract")) {
                // US-034 — audit vs best-effort notification
                sendJson(exchange, 200, toJson(accessReviews.notificationContract()));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/denials/r27")) {
                // US-035 — parked R27 create/elevate; denial only (no HMAC needed for ceiling probe)
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String grantor = extract(body, "grantor");
                if (grantor == null || grantor.trim().isEmpty()) {
                    grantor = "R01";
                }
                String deny = GrantorCeiling.denyReason(grantor, "API_CLIENT", false);
                if (deny == null) {
                    deny = "API Client (R27) creation/elevation is parked; denial only.";
                }
                Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
                out.put("allowed", Boolean.FALSE);
                out.put("targetRole", "API_CLIENT");
                out.put("adm03Family", "DORMANT");
                out.put("denyReason", deny);
                sendJson(exchange, 403, toJson(out));
                return;
            }

            GatewayHmacVerifier.VerificationResult auth = hmacVerifier.verify(exchange, method, path, bodyBytes);
            if (!auth.isSuccess()) {
                logger.warn("IADM-03 auth failed path={} code={} correlationId={}",
                        path, auth.getErrorCode(), correlationId);
                sendJson(exchange, auth.getStatus(), problem(auth.getErrorCode(), auth.getMessage()));
                return;
            }
            if (auth.getUserId() != null && !auth.getUserId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_USER_ID, auth.getUserId().trim());
            }
            if (auth.getTenantId() != null && !auth.getTenantId().trim().isEmpty()) {
                LogContext.put(LogContext.KEY_TENANT_ID, auth.getTenantId().trim());
            }
            tracker.step("authenticated");

            UserSecurityContext ctx = UserSecurityContext.fromPlatformHeaders(auth.getUserId(), auth.getTenantId());

            // US-013 / US-020: DEV-only audit-disabled fault toggle
            if ("true".equalsIgnoreCase(header(exchange, "X-Demo-Audit-Disabled"))) {
                DurableAuditWriter.setDemoAuditDisabled(true);
            }

            // US-033 — Auditor (R28) may read; every mutation is denied with a clear message.
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                service.assertNotAuditorMutation(ctx);
            }

            // S1: Computed-on-request permission-key register (US-001, US-021, US-012)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/register") || path.endsWith("/register/"))) {
                sendJson(exchange, 200, toJson(catalogueRead.getRegister(
                        ctx,
                        queryParam(exchange, "service"),
                        queryParam(exchange, "status"),
                        queryParam(exchange, "role"),
                        queryParam(exchange, "q"),
                        correlationId)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.contains("/register/") && path.endsWith("/holders")) {
                int idx = path.indexOf("/register/");
                String rest = path.substring(idx + "/register/".length());
                String keyCode = rest.substring(0, rest.length() - "/holders".length());
                UUID tenantFilter = parseUuidOptional(queryParam(exchange, "tenantId"));
                sendJson(exchange, 200, toJson(catalogueRead.getKeyHolders(ctx, keyCode, tenantFilter, correlationId)));
                return;
            }
            // S1: Computed-on-request R01-R39 role-to-permission matrix (US-002, US-021, US-012)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/matrix") || path.endsWith("/matrix/"))) {
                sendJson(exchange, 200, toJson(catalogueRead.getRoleMatrix(ctx, correlationId)));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/audit-evidence")
                    || path.endsWith("/audit-evidence/")
                    || path.endsWith("/evidence")
                    || path.endsWith("/evidence/"))) {
                int limit = 50;
                String limRaw = queryParam(exchange, "limit");
                if (limRaw != null && !limRaw.trim().isEmpty()) {
                    try {
                        limit = Integer.parseInt(limRaw.trim());
                    } catch (NumberFormatException ignored) {
                        limit = 50;
                    }
                }
                sendJson(exchange, 200, toJson(auditEvidence.listEvidence(ctx, limit)));
                return;
            }

            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/roles") || path.endsWith("/roles/"))) {
                sendJson(exchange, 200, toJson(service.listRoleCatalogue(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/permissions") || path.endsWith("/permissions/"))) {
                String q = queryParam(exchange, "q");
                sendJson(exchange, 200, toJson(service.searchPermissions(ctx, q)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/overview") || path.endsWith("/overview/"))) {
                sendJson(exchange, 200, toJson(profiles.overview(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && matchesProfilesList(path)) {
                sendJson(exchange, 200, toJson(profiles.listProfiles(ctx, queryParam(exchange, "status"))));
                return;
            }
            UUID profileId = extractProfileId(path);
            if ("GET".equalsIgnoreCase(method) && profileId != null && path.contains("/access")) {
                sendJson(exchange, 200, toJson(profiles.getProfileAccess(ctx, profileId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/profiles/invite")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String email = extract(body, "email");
                String displayName = extract(body, "displayName");
                String targetRole = extract(body, "targetRole");
                String tenantRaw = extract(body, "tenantId");
                UUID tenantId = null;
                if (tenantRaw != null && !tenantRaw.trim().isEmpty()) {
                    try {
                        tenantId = UUID.fromString(tenantRaw.trim());
                    } catch (IllegalArgumentException ex) {
                        throw new InstituteAdminException(400, "IADM03_VALIDATION", "tenantId must be a UUID");
                    }
                }
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(profiles.inviteOrCreateProfile(
                        ctx, email, displayName, targetRole, tenantId, key, correlationId)));
                return;
            }
            if (("PATCH".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))
                    && profileId != null && path.endsWith("/status")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String status = extract(body, "status");
                String reason = extract(body, "reason");
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(profiles.updateProfileStatus(ctx, profileId, status, reason, key)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/idempotency/begin")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                String op = extract(body, "operation");
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(service.beginIdempotent(ctx, op == null ? "MUTATION" : op, key, correlationId)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/access-requests") || path.endsWith("/access-requests/"))) {
                sendJson(exchange, 200, toJson(accessRequests.list(ctx, queryParam(exchange, "status"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/access-requests")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID subject = parseUuidOptional(extract(body, "subjectUserId"));
                UUID collegeId = parseUuidOptional(extract(body, "collegeId"));
                UUID tenantId = parseUuidOptional(extract(body, "tenantId"));
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(accessRequests.submit(
                        ctx, subject, extract(body, "requestedRole"), collegeId, tenantId,
                        extract(body, "justification"), extract(body, "validFrom"), extract(body, "validTo"),
                        key, correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/access-requests/") && path.endsWith("/decide")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID requestId = parseUuidOptional(extract(body, "requestId"));
                if (requestId == null) {
                    int i = path.indexOf("/access-requests/");
                    String rest = path.substring(i + "/access-requests/".length());
                    int slash = rest.indexOf('/');
                    requestId = parseUuid(slash < 0 ? rest : rest.substring(0, slash), "requestId");
                }
                sendJson(exchange, 200, toJson(accessRequests.decide(
                        ctx, requestId, extract(body, "decision"), extract(body, "reason"))));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.endsWith("/delegations/contract")) {
                sendJson(exchange, 200, toJson(delegations.delegationContract()));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && !path.contains("/v2/") && (path.endsWith("/delegations") || path.endsWith("/delegations/"))) {
                sendJson(exchange, 200, toJson(delegations.listDelegations(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && !path.contains("/v2/") && path.endsWith("/delegations")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID toUser = parseUuid(extract(body, "toUserId"), "toUserId");
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(delegations.createDelegation(
                        ctx, toUser, extract(body, "roleCode"),
                        extract(body, "validFrom"), extract(body, "validTo"),
                        extract(body, "reason"), key, correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && !path.contains("/v2/") && path.contains("/delegations/") && path.endsWith("/revoke")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID delegationId = parseUuidOptional(extract(body, "delegationId"));
                if (delegationId == null) {
                    int i = path.indexOf("/delegations/");
                    String rest = path.substring(i + "/delegations/".length());
                    int slash = rest.indexOf('/');
                    String idPart = slash < 0 ? rest : rest.substring(0, slash);
                    delegationId = parseUuid(idPart, "delegationId");
                }
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(delegations.revokeDelegation(
                        ctx, delegationId, extract(body, "reason"), key, correlationId)));
                return;
            }
            // S2: Record-only grant workflow (US-022, US-003..007, US-013)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/grant-requests") || path.endsWith("/grant-requests/"))) {
                UUID tenantFilter = parseUuidOptional(queryParam(exchange, "tenantId"));
                sendJson(exchange, 200, toJson(grantWorkflow.listRequests(
                        ctx, tenantFilter, queryParam(exchange, "status"), correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/grant-requests")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID subject = parseUuid(extract(body, "subjectUserId"), "subjectUserId");
                UUID scopeId = parseUuidOptional(extract(body, "scopeId"));
                UUID tenantId = parseUuidOptional(extract(body, "tenantId"));
                UUID collegeId = parseUuidOptional(extract(body, "collegeId"));
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(grantWorkflow.submitRequest(
                        ctx, subject, extract(body, "targetKind"), extract(body, "targetCode"),
                        extract(body, "scopeType"), scopeId, tenantId, collegeId,
                        extract(body, "validTo"), extract(body, "reason"), key, correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/grant-requests/") && path.endsWith("/validate")) {
                UUID reqId = extractPathUuid(path, "/grant-requests/");
                sendJson(exchange, 200, toJson(grantWorkflow.validateRequest(ctx, reqId, correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/grant-requests/") && path.endsWith("/decision")) {
                UUID reqId = extractPathUuid(path, "/grant-requests/");
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(grantWorkflow.decideRequest(
                        ctx, reqId, extract(body, "decision"), extract(body, "reason"), correlationId)));
                return;
            }

            // S3: Access review campaigns (US-008)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/v2/access-reviews") || path.endsWith("/v2/access-reviews/"))) {
                UUID cid = parseUuidOptional(queryParam(exchange, "campaignId"));
                sendJson(exchange, 200, toJson(v2Reviews.listCampaigns(ctx, cid, correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/v2/access-reviews")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID scopeId = parseUuidOptional(extract(body, "scopeId"));
                sendJson(exchange, 200, toJson(v2Reviews.createCampaign(
                        ctx, extract(body, "title"), extract(body, "scopeType"), scopeId,
                        extract(body, "dueAt"), correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/v2/access-reviews/") && path.endsWith("/decision")) {
                UUID cid = extractPathUuid(path, "/v2/access-reviews/");
                UUID itemId = extractPathUuid(path, "/items/");
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(v2Reviews.decideItem(
                        ctx, cid, itemId, extract(body, "decision"), extract(body, "reason"),
                        extract(body, "exceptionOwner"), extract(body, "exceptionExpiresAt"), correlationId)));
                return;
            }

            // S3: Delegations (US-009, US-010)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/v2/delegations") || path.endsWith("/v2/delegations/"))) {
                sendJson(exchange, 200, toJson(v2Delegations.listDelegations(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/v2/delegations")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID delegate = parseUuid(extract(body, "delegateUserId"), "delegateUserId");
                UUID scopeId = parseUuidOptional(extract(body, "scopeId"));
                sendJson(exchange, 200, toJson(v2Delegations.createDelegation(
                        ctx, delegate, extract(body, "allowedAction"), extract(body, "targetService"),
                        extract(body, "scopeType"), scopeId, extract(body, "startsAt"),
                        extract(body, "expiresAt"), extract(body, "reason"), correlationId)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && path.contains("/v2/delegations/") && path.endsWith("/status")) {
                UUID did = extractPathUuid(path, "/v2/delegations/");
                sendJson(exchange, 200, toJson(v2Delegations.getDelegationStatus(ctx, did)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/v2/delegations/") && path.endsWith("/revoke")) {
                UUID did = extractPathUuid(path, "/v2/delegations/");
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(v2Delegations.revokeDelegation(
                        ctx, did, extract(body, "reason"), correlationId)));
                return;
            }

            // S3: Service permission registrations (US-011)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/service-registrations") || path.endsWith("/service-registrations/"))) {
                sendJson(exchange, 200, toJson(serviceRegistrations.list(ctx)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/service-registrations")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(serviceRegistrations.submit(
                        ctx, extract(body, "serviceCode"), extract(body, "keyManifest"),
                        extract(body, "roleGrantProposal"), extract(body, "migrationRef"),
                        extract(body, "rollbackRef"), extract(body, "testEvidenceRef"),
                        extract(body, "auditEvidenceRef"), correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/service-registrations/") && path.endsWith("/validate")) {
                UUID regId = extractPathUuid(path, "/service-registrations/");
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(serviceRegistrations.validate(ctx, regId, extract(body, "notes"), correlationId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.contains("/service-registrations/") && path.endsWith("/decision")) {
                UUID regId = extractPathUuid(path, "/service-registrations/");
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(serviceRegistrations.decide(
                        ctx, regId, extract(body, "decision"), extract(body, "reason"), correlationId)));
                return;
            }

            // S4: First-admin handover status read-only (US-014)
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/handover/first-admin") || path.endsWith("/handover/first-admin/"))) {
                UUID tenantFilter = parseUuidOptional(queryParam(exchange, "tenantId"));
                sendJson(exchange, 200, toJson(handoverStatus.getHandoverStatus(ctx, tenantFilter, correlationId)));
                return;
            }

            // Boundary 3 (V2.0 record-only): no apply-grant or catalogue-mutation endpoints exist.
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/governance/snapshot")
                    || path.endsWith("/governance/snapshot/"))) {
                sendJson(exchange, 200, toJson(governanceRead.snapshot(ctx)));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && (path.endsWith("/access-reviews")
                    || path.endsWith("/access-reviews/"))) {
                UUID tenantFilter = parseUuidOptional(queryParam(exchange, "tenantId"));
                sendJson(exchange, 200, toJson(accessReviews.list(ctx, tenantFilter)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && path.endsWith("/access-reviews")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID tenantId = parseUuidOptional(extract(body, "tenantId"));
                String key = firstNonEmpty(extract(body, "idempotencyKey"), header(exchange, "Idempotency-Key"));
                sendJson(exchange, 200, toJson(accessReviews.start(
                        ctx, tenantId, extract(body, "cycleName"), key, correlationId)));
                return;
            }
            UUID reviewId = extractReviewId(path);
            if ("GET".equalsIgnoreCase(method) && reviewId != null
                    && !path.contains("/items/") && !path.endsWith("/complete")) {
                sendJson(exchange, 200, toJson(accessReviews.get(ctx, reviewId)));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && reviewId != null && path.contains("/items/")
                    && path.endsWith("/decide")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                UUID itemId = parseUuidOptional(extract(body, "itemId"));
                if (itemId == null) {
                    int i = path.indexOf("/items/");
                    String rest = path.substring(i + "/items/".length());
                    int slash = rest.indexOf('/');
                    itemId = parseUuid(slash < 0 ? rest : rest.substring(0, slash), "itemId");
                }
                sendJson(exchange, 200, toJson(accessReviews.decideItem(
                        ctx, reviewId, itemId, extract(body, "decision"), extract(body, "note"))));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && reviewId != null && path.endsWith("/complete")) {
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                sendJson(exchange, 200, toJson(accessReviews.complete(ctx, reviewId, extract(body, "reason"))));
                return;
            }

            sendJson(exchange, 404, problem("IADM03_NOT_FOUND", "No IADM-03 route for " + path));
        } catch (UserProfileAccessDeniedException ex) {
            sendJson(exchange, 403, problem("IADM03_FORBIDDEN", ex.getMessage()));
        } catch (InstituteAdminException ex) {
            sendJson(exchange, ex.getStatus(), problem(ex.getErrorCode(), ex.getMessage()));
        } catch (Exception ex) {
            logger.error("IADM-03 unhandled path={} err={}", path, ex.getMessage(), ex);
            sendJson(exchange, 500, problem("IADM03_INTERNAL", "Unexpected server error"));
        } finally {
            DurableAuditWriter.clearDemoAuditDisabled();
            tracker.close();
            LogContext.clear();
        }
    }

    private static boolean matchesProfilesList(String path) {
        if (path == null) {
            return false;
        }
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return p.endsWith("/profiles");
    }

    private static UUID extractPathUuid(String path, String marker) {
        int i = path.indexOf(marker);
        if (i < 0) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "Missing identifier in path");
        }
        String rest = path.substring(i + marker.length());
        int slash = rest.indexOf('/');
        return parseUuid(slash < 0 ? rest : rest.substring(0, slash), "id");
    }

    private static UUID parseUuid(String raw, String field) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", field + " is required");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", field + " must be a UUID");
        }
    }

    private static UUID parseUuidOptional(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "Invalid UUID");
        }
    }

    private static UUID extractReviewId(String path) {
        if (path == null) {
            return null;
        }
        String marker = "/access-reviews/";
        int i = path.indexOf(marker);
        if (i < 0) {
            return null;
        }
        String rest = path.substring(i + marker.length());
        int slash = rest.indexOf('/');
        String idPart = slash < 0 ? rest : rest.substring(0, slash);
        if (idPart.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(idPart);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static UUID extractProfileId(String path) {
        if (path == null) {
            return null;
        }
        String marker = "/profiles/";
        int i = path.indexOf(marker);
        if (i < 0) {
            return null;
        }
        String rest = path.substring(i + marker.length());
        int slash = rest.indexOf('/');
        String idPart = slash < 0 ? rest : rest.substring(0, slash);
        if (idPart.isEmpty() || "invite".equalsIgnoreCase(idPart)) {
            return null;
        }
        try {
            return UUID.fromString(idPart);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq > 0 && name.equals(part.substring(0, eq))) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                } catch (Exception ex) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return null;
    }

    private static String extract(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) {
                return null;
            }
            return json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && ",}] \n\r\t".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Service", "IADM-03");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String problem(String code, String message) {
        return "{\"type\":\"about:blank\",\"title\":\"" + escape(code) + "\",\"detail\":\""
                + escape(message) + "\",\"errorCode\":\"" + escape(code) + "\"}";
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            appendValue(sb, e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v.toString());
        } else if (v instanceof Map) {
            sb.append(toJson((Map<String, Object>) v));
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendValue(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
