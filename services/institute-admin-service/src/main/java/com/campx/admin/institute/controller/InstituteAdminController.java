package com.campx.admin.institute.controller;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.ErrorResponse;
import com.campx.admin.institute.model.InstituteModels.*;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP REST Controller for ADM-01: Institute Admin Service (Platform Tier).
 * <p>
 * Routes incoming HTTP requests matching {@code /api/v1/admin/**} to domain service handlers.
 * Extracts correlation headers ({@code X-Trace-Id}, {@code X-Tenant-Id}, {@code X-User-Id}, {@code X-User-Role}),
 * coordinates execution flow tracing via {@link FlowTracker}, and maps domain exceptions to RFC 7807 responses.
 *
 * @see InstituteAdminDomainService
 * @see ErrorResponse
 */
public class InstituteAdminController implements HttpHandler {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(InstituteAdminController.class);
    private final InstituteAdminDomainService domainService;

    /**
     * Initializes the controller with the backing business domain service.
     *
     * @param domainService domain service instance
     */
    public InstituteAdminController(InstituteAdminDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * Dispatches incoming HTTP exchanges to endpoint handlers based on URI path and HTTP method.
     *
     * @param exchange HTTP request-response exchange
     * @throws IOException if network writing fails
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // 1. Trace & Tenant Correlation Context
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = LogContext.initTraceId();
        } else {
            LogContext.setTraceId(traceId);
        }
        String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            LogContext.setTenantId(tenantId);
        }
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            LogContext.setUserId(userId);
        }
        String userRole = exchange.getRequestHeaders().getFirst("X-User-Role");
        if (userRole != null && !userRole.trim().isEmpty()) {
            LogContext.setUserRole(userRole);
        }

        logger.info("[InstituteAdminService] Incoming [{}] {}", method, path);

        FlowTracker flow = logger.flow("InstituteAdminRequest", method + " " + path);
        try {
            // 1. Institutes Endpoint
            if (path.equals("/api/v1/admin/institutes")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateInstitute");
                    handleCreateInstitute(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListInstitutes");
                    handleListInstitutes(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/institutes/")) {
                String id = path.substring("/api/v1/admin/institutes/".length());
                if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateInstitute");
                    handleUpdateInstitute(exchange, id);
                    return;
                }
            }

            // 2. Colleges Registration Endpoint
            if (path.equals("/api/v1/admin/colleges") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleRegisterCollege");
                handleRegisterCollege(exchange);
                return;
            }

            // 3. Tenant Provisioning Endpoint
            if (path.matches("^/api/v1/admin/tenants/[^/]+/provision$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleProvisionTenant");
                handleProvisionTenant(exchange, path);
                return;
            }
            if (path.equals("/api/v1/admin/tenants/provisioning") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListProvisioning");
                handleListProvisioning(exchange);
                return;
            }

            // 4. Configuration Endpoint
            if (path.equals("/api/v1/admin/configuration")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleSetConfiguration");
                    handleSetConfiguration(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetConfiguration");
                    handleGetConfiguration(exchange);
                    return;
                }
            }

            // 5. Commercial Plans Endpoint
            if (path.equals("/api/v1/admin/billing/plans") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetPlans");
                handleGetPlans(exchange);
                return;
            }

            // 6. Platform Audit Logs Endpoint (CampX Logger Service Integration)
            if (path.equals("/api/v1/admin/audit-logs") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetAuditLogs");
                handleGetAuditLogs(exchange);
                return;
            }

            // =====================================================================
            // Phase 1: RBAC & Identity Endpoints (User Story Lines 17–21)
            // =====================================================================

            // 7. Admin Users Endpoint
            if (path.equals("/api/v1/admin/users")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateAdminUser");
                    handleCreateAdminUser(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListAdminUsers");
                    handleListAdminUsers(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/users/")) {
                String adminUserId = path.substring("/api/v1/admin/users/".length());
                if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateAdminUser");
                    handleUpdateAdminUser(exchange, adminUserId);
                    return;
                }
            }

            // 8. Platform Roles Endpoint
            if (path.equals("/api/v1/admin/roles")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateRole");
                    handleCreateRole(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListRoles");
                    handleListRoles(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/roles/")) {
                String roleId = path.substring("/api/v1/admin/roles/".length());
                if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateRole");
                    handleUpdateRole(exchange, roleId);
                    return;
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    flow.step("handleDeleteRole");
                    handleDeleteRole(exchange, roleId);
                    return;
                }
            }

            // 9. Permissions Endpoint
            if (path.equals("/api/v1/admin/permissions")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreatePermission");
                    handleCreatePermission(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListPermissions");
                    handleListPermissions(exchange);
                    return;
                }
            }

            // 10. Role Bindings Endpoint
            if (path.equals("/api/v1/admin/role-bindings")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateRoleBinding");
                    handleCreateRoleBinding(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListRoleBindings");
                    handleListRoleBindings(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/role-bindings/")) {
                String bindingId = path.substring("/api/v1/admin/role-bindings/".length());
                if ("DELETE".equalsIgnoreCase(method)) {
                    flow.step("handleRevokeRoleBinding");
                    handleRevokeRoleBinding(exchange, bindingId);
                    return;
                }
            }

            // 11. Access Reviews Endpoint
            if (path.equals("/api/v1/admin/access/reviews")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateAccessReview");
                    handleCreateAccessReview(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListAccessReviews");
                    handleListAccessReviews(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/access/reviews/")) {
                String reviewId = path.substring("/api/v1/admin/access/reviews/".length());
                if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleCompleteAccessReview");
                    handleCompleteAccessReview(exchange, reviewId);
                    return;
                }
            }

            // =====================================================================
            // Phase 2: Transactional Reliability Layer Endpoints (User Story Lines 40–43)
            // =====================================================================

            // 12. Outbox Events Endpoint
            if (path.equals("/api/v1/admin/events/outbox") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListOutboxEvents");
                handleListOutboxEvents(exchange);
                return;
            }

            // 13. Inbox Events Endpoint
            if (path.equals("/api/v1/admin/events/inbox")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleProcessInboxEvent");
                    handleProcessInboxEvent(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListInboxEvents");
                    handleListInboxEvents(exchange);
                    return;
                }
            }

            // 14. Dead-Letter Events Endpoint
            if (path.equals("/api/v1/admin/events/dead-letter") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListDeadLetterEvents");
                handleListDeadLetterEvents(exchange);
                return;
            } else if (path.matches("^/api/v1/admin/events/dead-letter/[^/]+/replay$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleReplayDeadLetterEvent");
                String[] parts = path.split("/");
                String deadLetterId = parts[parts.length - 2];
                handleReplayDeadLetterEvent(exchange, deadLetterId);
                return;
            }

            // 15. Idempotency Records Endpoint
            if (path.equals("/api/v1/admin/idempotency-records") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListIdempotencyRecords");
                handleListIdempotencyRecords(exchange);
                return;
            }

            // =====================================================================
            // Phase 3: Configuration & Policy Engine Endpoints (User Story Lines 13–15)
            // =====================================================================

            // 16. Feature Flags Endpoint
            if (path.equals("/api/v1/admin/feature-flags")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateFeatureFlag");
                    handleCreateFeatureFlag(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListFeatureFlags");
                    handleListFeatureFlags(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/feature-flags/") && path.endsWith("/overrides") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleAddFeatureFlagOverride");
                String flagKey = path.substring("/api/v1/admin/feature-flags/".length(), path.length() - "/overrides".length());
                handleAddFeatureFlagOverride(exchange, flagKey);
                return;
            } else if (path.startsWith("/api/v1/admin/feature-flags/") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetFeatureFlag");
                String flagKey = path.substring("/api/v1/admin/feature-flags/".length());
                handleGetFeatureFlag(exchange, flagKey);
                return;
            }

            // 17. Policies Endpoint
            if (path.equals("/api/v1/admin/policies")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateGlobalPolicy");
                    handleCreateGlobalPolicy(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListGlobalPolicies");
                    handleListGlobalPolicies(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/policies/") && path.endsWith("/publish") && "POST".equalsIgnoreCase(method)) {
                flow.step("handlePublishGlobalPolicy");
                String policyCode = path.substring("/api/v1/admin/policies/".length(), path.length() - "/publish".length());
                handlePublishGlobalPolicy(exchange, policyCode);
                return;
            } else if (path.startsWith("/api/v1/admin/policies/") && path.endsWith("/approve") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleApproveGlobalPolicy");
                String policyCode = path.substring("/api/v1/admin/policies/".length(), path.length() - "/approve".length());
                handleApproveGlobalPolicy(exchange, policyCode);
                return;
            }

            // 18. Configuration Versioning & Rollback
            if (path.equals("/api/v1/admin/configuration/snapshot") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleSnapshotConfiguration");
                handleSnapshotConfiguration(exchange);
                return;
            }
            if (path.equals("/api/v1/admin/configuration/versions") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListConfigurationVersions");
                handleListConfigurationVersions(exchange);
                return;
            }
            if (path.equals("/api/v1/admin/configuration/rollback") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleRollbackConfiguration");
                handleRollbackConfiguration(exchange);
                return;
            }

            // =====================================================================
            // Phase 4: Commercial & Billing Endpoints (User Story Lines 23–27)
            // =====================================================================

            // 19. Commercial Plans (POST, Publish)
            if (path.equals("/api/v1/admin/billing/plans") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleCreatePlan");
                handleCreatePlan(exchange);
                return;
            } else if (path.startsWith("/api/v1/admin/billing/plans/") && path.endsWith("/publish") && "POST".equalsIgnoreCase(method)) {
                flow.step("handlePublishPlan");
                String code = path.substring("/api/v1/admin/billing/plans/".length(), path.length() - "/publish".length());
                handlePublishPlan(exchange, code);
                return;
            }

            // 20. Subscriptions Endpoint
            if (path.equals("/api/v1/admin/billing/subscriptions")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateSubscription");
                    handleCreateSubscription(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListSubscriptions");
                    handleListSubscriptions(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/billing/subscriptions/") && "PUT".equalsIgnoreCase(method)) {
                flow.step("handleTransitionSubscription");
                String subId = path.substring("/api/v1/admin/billing/subscriptions/".length());
                handleTransitionSubscription(exchange, subId);
                return;
            }

            // 21. Invoices Endpoint
            if (path.equals("/api/v1/admin/billing/invoices")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleIssueInvoice");
                    handleIssueInvoice(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListInvoices");
                    handleListInvoices(exchange);
                    return;
                }
            }

            // 22. Billing Transactions Endpoint
            if (path.equals("/api/v1/admin/billing/transactions")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleReconcileTransaction");
                    handleReconcileTransaction(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListTransactions");
                    handleListTransactions(exchange);
                    return;
                }
            }

            // 23. Usage Metrics Endpoint
            if (path.equals("/api/v1/admin/usage")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleRecordUsage");
                    handleRecordUsage(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetUsage");
                    handleGetUsage(exchange);
                    return;
                }
            }

            // =====================================================================
            // Phase 6: Operations & Workflows Endpoints (User Story Lines 29, 30, 39)
            // =====================================================================

            // 24. Platform Health
            if (path.equals("/api/v1/admin/operations/health")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleRecordHealth");
                    handleRecordHealth(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetHealth");
                    handleGetHealth(exchange);
                    return;
                }
            }

            // 25. Operational Alerts
            if (path.equals("/api/v1/admin/operations/alerts")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleRaiseAlert");
                    handleRaiseAlert(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListAlerts");
                    handleListAlerts(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/operations/alerts/") && path.endsWith("/acknowledge") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleAcknowledgeAlert");
                String alertId = path.substring("/api/v1/admin/operations/alerts/".length(), path.length() - "/acknowledge".length());
                handleAcknowledgeAlert(exchange, alertId);
                return;
            } else if (path.startsWith("/api/v1/admin/operations/alerts/") && path.endsWith("/resolve") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleResolveAlert");
                String alertId = path.substring("/api/v1/admin/operations/alerts/".length(), path.length() - "/resolve".length());
                handleResolveAlert(exchange, alertId);
                return;
            }

            // 26. Administrative Workflows
            if (path.equals("/api/v1/admin/workflows")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleStartWorkflow");
                    handleStartWorkflow(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListWorkflows");
                    handleListWorkflows(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/workflows/")) {
                String wfId = path.substring("/api/v1/admin/workflows/".length());
                if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetWorkflow");
                    handleGetWorkflow(exchange, wfId);
                    return;
                } else if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleTransitionWorkflow");
                    handleTransitionWorkflow(exchange, wfId);
                    return;
                }
            }

            // =====================================================================
            // Phase 7: Data Governance & Audit Endpoints (User Story Lines 32–34, 37)
            // =====================================================================

            // 27. Data Retention Policies
            if (path.equals("/api/v1/admin/data-governance/retention")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateRetentionPolicy");
                    handleCreateRetentionPolicy(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListRetentionPolicies");
                    handleListRetentionPolicies(exchange);
                    return;
                }
            }

            // 28. Data Classifications
            if (path.equals("/api/v1/admin/data-governance/classifications")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateDataClassification");
                    handleCreateDataClassification(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDataClassifications");
                    handleListDataClassifications(exchange);
                    return;
                }
            }

            // 29. Export Requests
            if (path.equals("/api/v1/admin/exports")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateExportRequest");
                    handleCreateExportRequest(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListExportRequests");
                    handleListExportRequests(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/exports/") && path.endsWith("/approve") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleApproveExportRequest");
                String exportId = path.substring("/api/v1/admin/exports/".length(), path.length() - "/approve".length());
                handleApproveExportRequest(exchange, exportId);
                return;
            } else if (path.startsWith("/api/v1/admin/exports/") && path.endsWith("/complete") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleCompleteExport");
                String exportId = path.substring("/api/v1/admin/exports/".length(), path.length() - "/complete".length());
                handleCompleteExport(exchange, exportId);
                return;
            }

            // 30. Correlated Audit Search
            if (path.equals("/api/v1/admin/audit-logs/search") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleSearchAuditLogs");
                handleSearchAuditLogs(exchange);
                return;
            }

            // Not found
            logger.warn("[InstituteAdminService] Route not found: [{}] {}", method, path);
            sendError(exchange, 404, "Not Found", "ADM01_ROUTE_NOT_FOUND", "Resource not found in Institute Admin Service: " + path, path);
        } catch (InstituteNotFoundException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Resource not found [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Not Found", e.getErrorCode(), e.getMessage(), path);
        } catch (InstituteAlreadyExistsException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Conflict [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Conflict", e.getErrorCode(), e.getMessage(), path);
        } catch (InvalidTenantStateException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Unprocessable state [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Unprocessable Entity", e.getErrorCode(), e.getMessage(), path);
        } catch (SecurityViolationException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Security violation [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (MalformedPayloadException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Malformed payload [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (InstituteAdminException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Institute admin error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Client Error", e.getErrorCode(), e.getMessage(), path);
        } catch (IllegalArgumentException | IllegalStateException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Validation error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, 400, "Bad Request", "ADM01_VALIDATION_ERROR", e.getMessage(), path);
        } catch (Exception e) {
            flow.markFailed(e);
            logger.error("[InstituteAdminService] Internal server error [{} {}]: {}", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error", "ADM01_INTERNAL_SERVER_ERROR", "An unexpected server error occurred: " + escape(e.getMessage()), path);
        } finally {
            if (flow != null) {
                flow.close();
            }
            LogContext.clear();
        }
    }

    private void handleCreateInstitute(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Institute inst = new Institute();
        inst.setInstituteCode(extract(body, "instituteCode", null));
        inst.setLegalName(extract(body, "legalName", null));
        inst.setDisplayName(extract(body, "displayName", inst.getLegalName()));
        inst.setTimezone(extract(body, "timezone", "Asia/Kolkata"));
        inst.setLocale(extract(body, "locale", "en_IN"));
        inst.setDefaultCurrency(extract(body, "defaultCurrency", "INR"));

        Institute created = domainService.registerInstitute(inst);
        String resp = "{"
                + "\"id\":\"" + created.getId() + "\","
                + "\"instituteCode\":\"" + created.getInstituteCode() + "\","
                + "\"legalName\":\"" + escape(created.getLegalName()) + "\","
                + "\"displayName\":\"" + escape(created.getDisplayName()) + "\","
                + "\"status\":\"" + created.getStatus() + "\","
                + "\"version\":" + created.getVersion()
                + "}";
        sendJson(exchange, 201, resp);
    }

    private void handleListInstitutes(HttpExchange exchange) throws IOException {
        List<Institute> list = domainService.listInstitutes();
        StringBuilder sb = new StringBuilder("{\"institutes\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Institute it = list.get(i);
            sb.append("{\"id\":\"").append(it.getId()).append("\",\"code\":\"").append(it.getInstituteCode())
              .append("\",\"name\":\"").append(escape(it.getDisplayName())).append("\",\"status\":\"").append(it.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleUpdateInstitute(HttpExchange exchange, String id) throws IOException {
        String body = readBody(exchange);
        Institute update = new Institute();
        update.setDisplayName(extract(body, "displayName", null));
        update.setStatus(extract(body, "status", null));
        update.setTimezone(extract(body, "timezone", null));

        Institute updated = domainService.updateInstitute(id, update);
        sendJson(exchange, 200, "{\"status\":\"UPDATED\",\"version\":" + updated.getVersion() + ",\"instituteId\":\"" + id + "\"}");
    }

    private void handleRegisterCollege(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        College c = new College();
        c.setCollegeCode(extract(body, "collegeCode", null));
        c.setName(extract(body, "name", null));
        c.setInstituteId(extract(body, "instituteId", null));

        College registered = domainService.registerCollege(c);
        sendJson(exchange, 201, "{\"id\":\"" + registered.getId() + "\",\"collegeCode\":\"" + registered.getCollegeCode() + "\",\"status\":\"" + registered.getStatus() + "\"}");
    }

    private void handleProvisionTenant(HttpExchange exchange, String path) throws IOException {
        // extract tenantId from path: /api/v1/admin/tenants/{id}/provision
        String[] parts = path.split("/");
        String tenantId = parts[parts.length - 2];

        String body = readBody(exchange);
        TenantProvisioning req = new TenantProvisioning();
        req.setTenantId(tenantId);
        req.setTargetScope(extract(body, "targetScope", "CAMPUS_MAIN"));
        req.setPlanId(extract(body, "planId", "ENTERPRISE_CAMPUS_2026"));
        req.setIdempotencyKey(extract(body, "idempotencyKey", exchange.getRequestHeaders().getFirst("Idempotency-Key")));

        TenantProvisioning completed = domainService.provisionTenant(req);
        sendJson(exchange, 200, "{\"provisioningId\":\"" + completed.getProvisioningId() + "\",\"tenantId\":\"" + completed.getTenantId() + "\",\"status\":\"" + completed.getProvisioningStatus() + "\"}");
    }

    private void handleListProvisioning(HttpExchange exchange) throws IOException {
        List<TenantProvisioning> list = domainService.getProvisioningJobs();
        StringBuilder sb = new StringBuilder("{\"jobs\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            TenantProvisioning p = list.get(i);
            sb.append("{\"id\":\"").append(p.getProvisioningId()).append("\",\"tenant\":\"").append(p.getTenantId())
              .append("\",\"status\":\"").append(p.getProvisioningStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSetConfiguration(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        GlobalSetting s = new GlobalSetting();
        s.setKey(extract(body, "key", "config.key"));
        s.setValue(extract(body, "value", "config.value"));
        s.setDataType(extract(body, "dataType", "STRING"));
        s.setScope(extract(body, "scope", "GLOBAL"));
        s.setSecret("true".equalsIgnoreCase(extract(body, "isSecret", "false")));

        GlobalSetting saved = domainService.setGlobalSetting(s);
        sendJson(exchange, 200, "{\"status\":\"SAVED\",\"key\":\"" + saved.getKey() + "\"}");
    }

    private void handleGetConfiguration(HttpExchange exchange) throws IOException {
        List<GlobalSetting> list = domainService.getGlobalSettings();
        StringBuilder sb = new StringBuilder("{\"configuration\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            GlobalSetting s = list.get(i);
            sb.append("{\"key\":\"").append(s.getKey()).append("\",\"value\":\"").append(escape(s.getValue())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetPlans(HttpExchange exchange) throws IOException {
        List<CommercialPlan> plans = domainService.getCommercialPlans();
        StringBuilder sb = new StringBuilder("{\"plans\":[");
        for (int i = 0; i < plans.size(); i++) {
            if (i > 0) sb.append(",");
            CommercialPlan p = plans.get(i);
            sb.append("{\"code\":\"").append(p.getPlanCode()).append("\",\"name\":\"").append(escape(p.getName()))
              .append("\",\"price\":").append(p.getPrice()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetAuditLogs(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> logs = domainService.getAuditTrail();
        StringBuilder sb = new StringBuilder("{\"auditLogs\":[");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> log = logs.get(i);
            sb.append("{");
            sb.append("\"eventId\":\"").append(log.get("eventId")).append("\",");
            sb.append("\"action\":\"").append(log.get("action")).append("\",");
            sb.append("\"principalId\":\"").append(log.get("principalId")).append("\",");
            sb.append("\"principalRole\":\"").append(log.get("principalRole")).append("\",");
            sb.append("\"resourceType\":\"").append(log.get("resourceType")).append("\",");
            sb.append("\"resourceId\":\"").append(log.get("resourceId")).append("\",");
            sb.append("\"status\":\"").append(log.get("status")).append("\",");
            sb.append("\"description\":\"").append(escape((String) log.get("description"))).append("\",");
            sb.append("\"traceId\":\"").append(log.get("traceId") != null ? log.get("traceId") : "").append("\",");
            sb.append("\"timestamp\":").append(log.get("timestamp"));
            sb.append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 1: RBAC & Identity Handler Methods (User Story Lines 17–21)
    // =========================================================================

    private void handleCreateAdminUser(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        AdminUser user = new AdminUser();
        user.setUserId(extract(body, "userId", null));
        user.setDisplayName(extract(body, "displayName", "Platform Admin"));
        user.setEmail(extract(body, "email", null));

        AdminUser created = domainService.registerAdminUser(user);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"userId\":\"" + created.getUserId()
                + "\",\"displayName\":\"" + escape(created.getDisplayName()) + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListAdminUsers(HttpExchange exchange) throws IOException {
        List<AdminUser> list = domainService.listAdminUsers();
        StringBuilder sb = new StringBuilder("{\"users\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            AdminUser u = list.get(i);
            sb.append("{\"id\":\"").append(u.getId()).append("\",\"userId\":\"").append(u.getUserId())
              .append("\",\"displayName\":\"").append(escape(u.getDisplayName()))
              .append("\",\"status\":\"").append(u.getStatus())
              .append("\",\"riskState\":\"").append(u.getRiskState()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleUpdateAdminUser(HttpExchange exchange, String id) throws IOException {
        String body = readBody(exchange);
        String status = extract(body, "status", null);
        String riskState = extract(body, "riskState", null);
        AdminUser updated = domainService.updateAdminUserStatus(id, status, riskState);
        sendJson(exchange, 200, "{\"id\":\"" + updated.getId() + "\",\"status\":\"" + updated.getStatus()
                + "\",\"riskState\":\"" + updated.getRiskState() + "\"}");
    }

    private void handleCreateRole(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        PlatformRole role = new PlatformRole();
        role.setRoleCode(extract(body, "roleCode", null));
        role.setName(extract(body, "name", null));
        role.setScope(extract(body, "scope", "PLATFORM"));
        role.setProtectedSystemRole("true".equalsIgnoreCase(extract(body, "protectedSystemRole", "false")));

        // Extract permission codes from comma-separated string
        String permsStr = extract(body, "permissionCodes", null);
        if (permsStr != null && !permsStr.isEmpty()) {
            role.setPermissionCodes(java.util.Arrays.asList(permsStr.split(",")));
        }

        PlatformRole created = domainService.createRole(role);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"roleCode\":\"" + created.getRoleCode()
                + "\",\"scope\":\"" + created.getScope() + "\",\"version\":" + created.getVersion() + "}");
    }

    private void handleListRoles(HttpExchange exchange) throws IOException {
        List<PlatformRole> list = domainService.listRoles();
        StringBuilder sb = new StringBuilder("{\"roles\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            PlatformRole r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"roleCode\":\"").append(r.getRoleCode())
              .append("\",\"name\":\"").append(escape(r.getName())).append("\",\"scope\":\"").append(r.getScope())
              .append("\",\"protected\":").append(r.isProtectedSystemRole())
              .append(",\"version\":").append(r.getVersion()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleUpdateRole(HttpExchange exchange, String roleId) throws IOException {
        String body = readBody(exchange);
        String permsStr = extract(body, "permissionCodes", null);
        if (permsStr != null) {
            List<String> perms = java.util.Arrays.asList(permsStr.split(","));
            PlatformRole updated = domainService.updateRolePermissions(roleId, perms);
            sendJson(exchange, 200, "{\"id\":\"" + roleId + "\",\"version\":" + updated.getVersion() + "}");
        } else {
            sendJson(exchange, 200, "{\"status\":\"NO_CHANGE\"}");
        }
    }

    private void handleDeleteRole(HttpExchange exchange, String roleId) throws IOException {
        domainService.deleteRole(roleId);
        sendJson(exchange, 200, "{\"status\":\"DELETED\",\"roleId\":\"" + roleId + "\"}");
    }

    private void handleCreatePermission(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Permission perm = new Permission();
        perm.setPermissionCode(extract(body, "permissionCode", null));
        perm.setResource(extract(body, "resource", null));
        perm.setAction(extract(body, "action", null));
        perm.setDescription(extract(body, "description", null));

        Permission created = domainService.createPermission(perm);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"permissionCode\":\"" + created.getPermissionCode()
                + "\",\"resource\":\"" + created.getResource() + "\",\"action\":\"" + created.getAction() + "\"}");
    }

    private void handleListPermissions(HttpExchange exchange) throws IOException {
        List<Permission> list = domainService.listPermissions();
        StringBuilder sb = new StringBuilder("{\"permissions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Permission p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"code\":\"").append(p.getPermissionCode())
              .append("\",\"resource\":\"").append(p.getResource()).append("\",\"action\":\"").append(p.getAction())
              .append("\",\"usedByRoles\":").append(p.getUsedByRoles()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateRoleBinding(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        RoleBinding binding = new RoleBinding();
        binding.setTenantId(extract(body, "tenantId", null));
        binding.setPrincipalId(extract(body, "principalId", null));
        binding.setRoleId(extract(body, "roleId", null));
        binding.setScope(extract(body, "scope", "PLATFORM"));
        binding.setGrantedBy(extract(body, "grantedBy", null));

        RoleBinding created = domainService.createRoleBinding(binding);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"principalId\":\"" + created.getPrincipalId()
                + "\",\"roleId\":\"" + created.getRoleId() + "\",\"scope\":\"" + created.getScope() + "\"}");
    }

    private void handleListRoleBindings(HttpExchange exchange) throws IOException {
        List<RoleBinding> list = domainService.listRoleBindings();
        StringBuilder sb = new StringBuilder("{\"bindings\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            RoleBinding b = list.get(i);
            sb.append("{\"id\":\"").append(b.getId()).append("\",\"principalId\":\"").append(b.getPrincipalId())
              .append("\",\"roleId\":\"").append(b.getRoleId()).append("\",\"scope\":\"").append(b.getScope())
              .append("\",\"grantedBy\":\"").append(b.getGrantedBy()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleRevokeRoleBinding(HttpExchange exchange, String bindingId) throws IOException {
        domainService.revokeRoleBinding(bindingId);
        sendJson(exchange, 200, "{\"status\":\"REVOKED\",\"bindingId\":\"" + bindingId + "\"}");
    }

    private void handleCreateAccessReview(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        AccessReview review = new AccessReview();
        review.setPrincipalId(extract(body, "principalId", null));
        review.setTenantId(extract(body, "tenantId", null));
        review.setRoleBindingId(extract(body, "roleBindingId", null));
        String dueAtStr = extract(body, "dueAt", null);
        if (dueAtStr != null) {
            try { review.setDueAt(Long.parseLong(dueAtStr)); } catch (NumberFormatException ignored) { review.setDueAt(System.currentTimeMillis() + 604800000L); }
        } else {
            review.setDueAt(System.currentTimeMillis() + 604800000L); // Default 7 days
        }

        AccessReview created = domainService.createAccessReview(review);
        sendJson(exchange, 201, "{\"reviewId\":\"" + created.getReviewId() + "\",\"principalId\":\"" + created.getPrincipalId()
                + "\",\"status\":\"" + created.getStatus() + "\",\"dueAt\":" + created.getDueAt() + "}");
    }

    private void handleListAccessReviews(HttpExchange exchange) throws IOException {
        List<AccessReview> list = domainService.listAccessReviews();
        StringBuilder sb = new StringBuilder("{\"reviews\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            AccessReview r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"reviewId\":\"").append(r.getReviewId())
              .append("\",\"principalId\":\"").append(r.getPrincipalId())
              .append("\",\"status\":\"").append(r.getStatus())
              .append("\",\"decision\":\"").append(r.getDecision() != null ? r.getDecision() : "PENDING").append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCompleteAccessReview(HttpExchange exchange, String reviewId) throws IOException {
        String body = readBody(exchange);
        String reviewerId = extract(body, "reviewerId", null);
        String decision = extract(body, "decision", "CERTIFY");
        AccessReview completed = domainService.completeAccessReview(reviewId, reviewerId, decision);
        sendJson(exchange, 200, "{\"reviewId\":\"" + completed.getReviewId() + "\",\"decision\":\"" + completed.getDecision()
                + "\",\"status\":\"" + completed.getStatus() + "\",\"reviewerId\":\"" + completed.getReviewerId() + "\"}");
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Handlers
    // =========================================================================

    private void handleListOutboxEvents(HttpExchange exchange) throws IOException {
        List<OutboxEvent> list = domainService.listOutboxEvents();
        StringBuilder sb = new StringBuilder("{\"outbox\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            OutboxEvent e = list.get(i);
            sb.append("{\"id\":\"").append(e.getId()).append("\",\"eventType\":\"").append(e.getEventType())
              .append("\",\"aggregateId\":\"").append(e.getAggregateId())
              .append("\",\"status\":\"").append(e.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleProcessInboxEvent(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String eventId = extract(body, "eventId", null);
        String sourceService = extract(body, "sourceService", "ACD-01");
        String consumerGroup = extract(body, "consumerGroup", "ADM-01");
        String payload = extract(body, "payload", "{}");

        InboxEvent processed = domainService.processInboxEvent(eventId, sourceService, consumerGroup, payload);
        sendJson(exchange, 200, "{\"id\":\"" + processed.getId() + "\",\"eventId\":\"" + processed.getEventId()
                + "\",\"status\":\"" + processed.getStatus() + "\"}");
    }

    private void handleListInboxEvents(HttpExchange exchange) throws IOException {
        List<InboxEvent> list = domainService.listInboxEvents();
        StringBuilder sb = new StringBuilder("{\"inbox\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            InboxEvent e = list.get(i);
            sb.append("{\"id\":\"").append(e.getId()).append("\",\"eventId\":\"").append(e.getEventId())
              .append("\",\"sourceService\":\"").append(e.getSourceService())
              .append("\",\"status\":\"").append(e.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleListDeadLetterEvents(HttpExchange exchange) throws IOException {
        List<DeadLetterEvent> list = domainService.listDeadLetterEvents();
        StringBuilder sb = new StringBuilder("{\"deadLetterEvents\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DeadLetterEvent d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"originalEventId\":\"").append(d.getOriginalEventId())
              .append("\",\"eventType\":\"").append(d.getEventType())
              .append("\",\"failureCode\":\"").append(d.getFailureCode())
              .append("\",\"disposition\":\"").append(d.getDisposition()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleReplayDeadLetterEvent(HttpExchange exchange, String id) throws IOException {
        DeadLetterEvent replayed = domainService.replayDeadLetterEvent(id);
        sendJson(exchange, 200, "{\"id\":\"" + replayed.getId() + "\",\"disposition\":\"" + replayed.getDisposition()
                + "\",\"status\":\"REPLAYED\"}");
    }

    private void handleListIdempotencyRecords(HttpExchange exchange) throws IOException {
        List<IdempotencyRecord> list = domainService.listIdempotencyRecords();
        StringBuilder sb = new StringBuilder("{\"records\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            IdempotencyRecord r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"idempotencyKey\":\"").append(r.getIdempotencyKey())
              .append("\",\"operation\":\"").append(r.getOperation())
              .append("\",\"status\":\"").append(r.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 3: Configuration & Policy Engine Handlers (User Story Lines 13–15)
    // =========================================================================

    private void handleCreateFeatureFlag(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        FeatureFlag flag = new FeatureFlag();
        flag.setFlagKey(extract(body, "flagKey", null));
        flag.setDefaultValue(extract(body, "defaultValue", "false"));
        flag.setDescription(extract(body, "description", null));
        flag.setGloballyLocked("true".equalsIgnoreCase(extract(body, "globallyLocked", "false")));
        String rulesStr = extract(body, "targetingRules", null);
        if (rulesStr != null && !rulesStr.isEmpty()) {
            flag.setTargetingRules(Arrays.asList(rulesStr.split(",")));
        }

        FeatureFlag created = domainService.createFeatureFlag(flag);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"flagKey\":\"" + created.getFlagKey()
                + "\",\"defaultValue\":\"" + created.getDefaultValue() + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListFeatureFlags(HttpExchange exchange) throws IOException {
        List<FeatureFlag> list = domainService.listFeatureFlags();
        StringBuilder sb = new StringBuilder("{\"featureFlags\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            FeatureFlag f = list.get(i);
            sb.append("{\"id\":\"").append(f.getId()).append("\",\"flagKey\":\"").append(f.getFlagKey())
              .append("\",\"defaultValue\":\"").append(f.getDefaultValue())
              .append("\",\"globallyLocked\":").append(f.isGloballyLocked())
              .append(",\"status\":\"").append(f.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetFeatureFlag(HttpExchange exchange, String flagKey) throws IOException {
        FeatureFlag flag = domainService.getFeatureFlag(flagKey);
        sendJson(exchange, 200, "{\"id\":\"" + flag.getId() + "\",\"flagKey\":\"" + flag.getFlagKey()
                + "\",\"defaultValue\":\"" + flag.getDefaultValue() + "\",\"globallyLocked\":" + flag.isGloballyLocked()
                + ",\"status\":\"" + flag.getStatus() + "\",\"overridesCount\":" + flag.getTenantOverrides().size() + "}");
    }

    private void handleAddFeatureFlagOverride(HttpExchange exchange, String flagKey) throws IOException {
        String body = readBody(exchange);
        String tenantId = extract(body, "tenantId", null);
        String overrideValue = extract(body, "overrideValue", "true");
        String reason = extract(body, "reason", "Tenant specific override");

        FeatureFlag updated = domainService.addTenantOverride(flagKey, tenantId, overrideValue, reason);
        sendJson(exchange, 200, "{\"flagKey\":\"" + updated.getFlagKey() + "\",\"tenantId\":\"" + tenantId
                + "\",\"overrideValue\":\"" + overrideValue + "\",\"status\":\"OVERRIDDEN\"}");
    }

    private void handleCreateGlobalPolicy(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        GlobalPolicy policy = new GlobalPolicy();
        policy.setPolicyCode(extract(body, "policyCode", null));
        policy.setPolicyType(extract(body, "policyType", "GOVERNANCE"));
        String rulesStr = extract(body, "rules", null);
        if (rulesStr != null && !rulesStr.isEmpty()) {
            policy.setRules(Arrays.asList(rulesStr.split(",")));
        }

        GlobalPolicy created = domainService.createGlobalPolicy(policy);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"policyCode\":\"" + created.getPolicyCode()
                + "\",\"status\":\"" + created.getStatus() + "\",\"version\":" + created.getVersion() + "}");
    }

    private void handleListGlobalPolicies(HttpExchange exchange) throws IOException {
        List<GlobalPolicy> list = domainService.listGlobalPolicies();
        StringBuilder sb = new StringBuilder("{\"policies\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            GlobalPolicy p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"policyCode\":\"").append(p.getPolicyCode())
              .append("\",\"status\":\"").append(p.getStatus())
              .append("\",\"version\":").append(p.getVersion())
              .append(",\"approvedBy\":\"").append(p.getApprovedBy() != null ? p.getApprovedBy() : "")
              .append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleApproveGlobalPolicy(HttpExchange exchange, String policyCode) throws IOException {
        String body = readBody(exchange);
        String approvedBy = extract(body, "approvedBy", LogContext.getUserId() != null ? LogContext.getUserId() : "SECURITY_OFFICER");
        GlobalPolicy approved = domainService.approveGlobalPolicy(policyCode, approvedBy);
        sendJson(exchange, 200, "{\"policyCode\":\"" + approved.getPolicyCode() + "\",\"status\":\"" + approved.getStatus()
                + "\",\"approvedBy\":\"" + approved.getApprovedBy() + "\"}");
    }

    private void handlePublishGlobalPolicy(HttpExchange exchange, String policyCode) throws IOException {
        GlobalPolicy published = domainService.publishGlobalPolicy(policyCode);
        sendJson(exchange, 200, "{\"policyCode\":\"" + published.getPolicyCode() + "\",\"status\":\"" + published.getStatus()
                + "\",\"version\":" + published.getVersion() + ",\"publishedAt\":" + published.getPublishedAt() + "}");
    }

    private void handleSnapshotConfiguration(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String scope = extract(body, "scope", "GLOBAL");
        ConfigurationVersion cv = domainService.snapshotConfiguration(scope);
        sendJson(exchange, 201, "{\"id\":\"" + cv.getId() + "\",\"scope\":\"" + cv.getScope()
                + "\",\"version\":" + cv.getVersion() + ",\"checksum\":\"" + cv.getChecksum() + "\"}");
    }

    private void handleListConfigurationVersions(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String scope = "GLOBAL";
        if (query != null && query.contains("scope=")) {
            scope = query.replaceAll(".*scope=([^&]+).*", "$1");
        }
        List<ConfigurationVersion> list = domainService.listConfigurationVersions(scope);
        StringBuilder sb = new StringBuilder("{\"versions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            ConfigurationVersion v = list.get(i);
            sb.append("{\"id\":\"").append(v.getId()).append("\",\"version\":").append(v.getVersion())
              .append(",\"checksum\":\"").append(v.getChecksum())
              .append("\",\"status\":\"").append(v.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleRollbackConfiguration(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String scope = extract(body, "scope", "GLOBAL");
        int targetVersion = Integer.parseInt(extract(body, "targetVersion", "1"));
        ConfigurationVersion rolledBack = domainService.rollbackConfiguration(scope, targetVersion);
        sendJson(exchange, 200, "{\"id\":\"" + rolledBack.getId() + "\",\"scope\":\"" + rolledBack.getScope()
                + "\",\"version\":" + rolledBack.getVersion() + ",\"status\":\"" + rolledBack.getStatus() + "\"}");
    }

    // =========================================================================
    // Phase 4: Commercial & Billing Handlers (User Story Lines 23–27)
    // =========================================================================

    private void handleCreatePlan(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CommercialPlan plan = new CommercialPlan();
        plan.setPlanCode(extract(body, "planCode", null));
        plan.setName(extract(body, "name", "Commercial Plan"));
        plan.setBillingCycle(extract(body, "billingCycle", "MONTHLY"));
        try {
            plan.setPrice(Double.parseDouble(extract(body, "price", "0.0")));
        } catch (NumberFormatException ignored) {}
        plan.setCurrency(extract(body, "currency", "INR"));
        String entStr = extract(body, "entitlements", null);
        if (entStr != null && !entStr.isEmpty()) {
            plan.setEntitlements(Arrays.asList(entStr.split(",")));
        }

        CommercialPlan created = domainService.createCommercialPlan(plan);
        sendJson(exchange, 201, "{\"planCode\":\"" + created.getPlanCode() + "\",\"published\":" + created.isPublished() + ",\"status\":\"CREATED\"}");
    }

    private void handlePublishPlan(HttpExchange exchange, String planCode) throws IOException {
        CommercialPlan published = domainService.publishCommercialPlan(planCode);
        sendJson(exchange, 200, "{\"planCode\":\"" + published.getPlanCode() + "\",\"published\":true,\"status\":\"PUBLISHED\"}");
    }

    private void handleCreateSubscription(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Subscription sub = new Subscription();
        sub.setTenantId(extract(body, "tenantId", null));
        sub.setPlanId(extract(body, "planId", null));
        try {
            sub.setSeatCount(Integer.parseInt(extract(body, "seatCount", "50")));
        } catch (NumberFormatException ignored) {}

        Subscription created = domainService.createSubscription(sub);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"tenantId\":\"" + created.getTenantId()
                + "\",\"planId\":\"" + created.getPlanId() + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListSubscriptions(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String tenantId = null;
        if (query != null && query.contains("tenantId=")) {
            tenantId = query.replaceAll(".*tenantId=([^&]+).*", "$1");
        }
        List<Subscription> list = domainService.listSubscriptions(tenantId);
        StringBuilder sb = new StringBuilder("{\"subscriptions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Subscription s = list.get(i);
            sb.append("{\"id\":\"").append(s.getId()).append("\",\"tenantId\":\"").append(s.getTenantId())
              .append("\",\"planId\":\"").append(s.getPlanId()).append("\",\"status\":\"").append(s.getStatus())
              .append("\",\"seatCount\":").append(s.getSeatCount()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleTransitionSubscription(HttpExchange exchange, String subId) throws IOException {
        String body = readBody(exchange);
        String newStatus = extract(body, "status", "SUSPENDED");
        Subscription updated = domainService.transitionSubscription(subId, newStatus);
        sendJson(exchange, 200, "{\"id\":\"" + updated.getId() + "\",\"status\":\"" + updated.getStatus() + "\"}");
    }

    private void handleIssueInvoice(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String subscriptionId = extract(body, "subscriptionId", null);
        String billingPeriod = extract(body, "billingPeriod", "2026-09");
        Invoice inv = domainService.issueInvoice(subscriptionId, billingPeriod);
        sendJson(exchange, 201, "{\"id\":\"" + inv.getId() + "\",\"invoiceNo\":\"" + inv.getInvoiceNo()
                + "\",\"total\":" + inv.getTotal() + ",\"status\":\"" + inv.getStatus() + "\"}");
    }

    private void handleListInvoices(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String tenantId = null;
        if (query != null && query.contains("tenantId=")) {
            tenantId = query.replaceAll(".*tenantId=([^&]+).*", "$1");
        }
        List<Invoice> list = domainService.listInvoices(tenantId);
        StringBuilder sb = new StringBuilder("{\"invoices\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Invoice inv = list.get(i);
            sb.append("{\"id\":\"").append(inv.getId()).append("\",\"invoiceNo\":\"").append(inv.getInvoiceNo())
              .append("\",\"tenantId\":\"").append(inv.getTenantId()).append("\",\"total\":").append(inv.getTotal())
              .append(",\"status\":\"").append(inv.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleReconcileTransaction(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String gatewayTxnId = extract(body, "gatewayTransactionId", null);
        String invoiceId = extract(body, "invoiceId", null);
        double amount = 0.0;
        try {
            amount = Double.parseDouble(extract(body, "amount", "0.0"));
        } catch (NumberFormatException ignored) {}
        String currency = extract(body, "currency", "INR");
        String rawReference = extract(body, "rawReference", "WEBHOOK_PAYLOAD");

        BillingGatewayTransaction txn = domainService.reconcileGatewayTransaction(gatewayTxnId, invoiceId, amount, currency, rawReference);
        sendJson(exchange, 200, "{\"id\":\"" + txn.getId() + "\",\"gatewayTransactionId\":\"" + txn.getGatewayTransactionId()
                + "\",\"status\":\"" + txn.getStatus() + "\"}");
    }

    private void handleListTransactions(HttpExchange exchange) throws IOException {
        List<BillingGatewayTransaction> list = domainService.listBillingTransactions();
        StringBuilder sb = new StringBuilder("{\"transactions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            BillingGatewayTransaction t = list.get(i);
            sb.append("{\"id\":\"").append(t.getId()).append("\",\"gatewayTransactionId\":\"").append(t.getGatewayTransactionId())
              .append("\",\"amount\":").append(t.getAmount()).append(",\"status\":\"").append(t.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleRecordUsage(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String tenantId = extract(body, "tenantId", null);
        String metricType = extract(body, "metricType", "ACTIVE_USERS");
        String period = extract(body, "period", "2026-09");
        long value = 0;
        try {
            value = Long.parseLong(extract(body, "value", "0"));
        } catch (NumberFormatException ignored) {}

        UsageMetric m = domainService.recordUsageMetric(tenantId, metricType, period, value);
        sendJson(exchange, 201, "{\"id\":\"" + m.getId() + "\",\"metricType\":\"" + m.getMetricType()
                + "\",\"value\":" + m.getValue() + ",\"status\":\"RECORDED\"}");
    }

    private void handleGetUsage(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String tenantId = null;
        String period = null;
        if (query != null) {
            if (query.contains("tenantId=")) tenantId = query.replaceAll(".*tenantId=([^&]+).*", "$1");
            if (query.contains("period=")) period = query.replaceAll(".*period=([^&]+).*", "$1");
        }
        List<UsageMetric> list = domainService.getUsageMetrics(tenantId, period);
        StringBuilder sb = new StringBuilder("{\"usage\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            UsageMetric m = list.get(i);
            sb.append("{\"id\":\"").append(m.getId()).append("\",\"tenantId\":\"").append(m.getTenantId())
              .append("\",\"metricType\":\"").append(m.getMetricType()).append("\",\"value\":").append(m.getValue())
              .append(",\"period\":\"").append(m.getPeriod()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 6: Operations & Workflows Handlers
    // =========================================================================

    private void handleRecordHealth(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        PlatformHealth health = new PlatformHealth();
        health.setComponent(extract(body, "component", null));
        health.setRegion(extract(body, "region", "ap-south-1"));
        health.setStatus(extract(body, "status", "HEALTHY"));
        String latStr = extract(body, "latencyMs", "0");
        try { health.setLatencyMs(Long.parseLong(latStr)); } catch (Exception ignored) {}
        String errStr = extract(body, "errorRate", "0.0");
        try { health.setErrorRate(Double.parseDouble(errStr)); } catch (Exception ignored) {}

        PlatformHealth recorded = domainService.recordHealthSnapshot(health);
        sendJson(exchange, 201, "{\"id\":\"" + recorded.getId() + "\",\"component\":\"" + recorded.getComponent()
                + "\",\"status\":\"" + recorded.getStatus() + "\",\"latencyMs\":" + recorded.getLatencyMs() + "}");
    }

    private void handleGetHealth(HttpExchange exchange) throws IOException {
        List<PlatformHealth> list = domainService.getHealthSnapshots();
        StringBuilder sb = new StringBuilder("{\"snapshots\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            PlatformHealth h = list.get(i);
            sb.append("{\"id\":\"").append(h.getId()).append("\",\"component\":\"").append(escape(h.getComponent()))
              .append("\",\"status\":\"").append(h.getStatus()).append("\",\"latencyMs\":").append(h.getLatencyMs())
              .append(",\"observedAt\":").append(h.getObservedAt()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleRaiseAlert(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        OperationalAlert alert = new OperationalAlert();
        alert.setAlertCode(extract(body, "alertCode", null));
        alert.setSeverity(extract(body, "severity", "INFO"));
        alert.setSource(extract(body, "source", "SYSTEM"));
        alert.setScope(extract(body, "scope", "GLOBAL"));
        alert.setMessage(extract(body, "message", ""));
        alert.setDeduplicationKey(extract(body, "deduplicationKey", null));

        OperationalAlert created = domainService.raiseAlert(alert);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"alertCode\":\"" + created.getAlertCode()
                + "\",\"severity\":\"" + created.getSeverity() + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListAlerts(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String status = getQueryParam(query, "status");
        String severity = getQueryParam(query, "severity");
        List<OperationalAlert> list = domainService.listAlerts(status, severity);
        StringBuilder sb = new StringBuilder("{\"alerts\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            OperationalAlert a = list.get(i);
            sb.append("{\"id\":\"").append(a.getId()).append("\",\"alertCode\":\"").append(escape(a.getAlertCode()))
              .append("\",\"severity\":\"").append(a.getSeverity()).append("\",\"status\":\"").append(a.getStatus())
              .append("\",\"source\":\"").append(escape(a.getSource())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleAcknowledgeAlert(HttpExchange exchange, String alertId) throws IOException {
        String body = readBody(exchange);
        String assignee = extract(body, "assignee", "OPS_ONCALL");
        OperationalAlert alert = domainService.acknowledgeAlert(alertId, assignee);
        sendJson(exchange, 200, "{\"id\":\"" + alert.getId() + "\",\"status\":\"" + alert.getStatus()
                + "\",\"assignee\":\"" + escape(alert.getAssignee()) + "\"}");
    }

    private void handleResolveAlert(HttpExchange exchange, String alertId) throws IOException {
        String body = readBody(exchange);
        String notes = extract(body, "resolutionNotes", "Resolved");
        OperationalAlert alert = domainService.resolveAlert(alertId, notes);
        sendJson(exchange, 200, "{\"id\":\"" + alert.getId() + "\",\"status\":\"" + alert.getStatus()
                + "\",\"resolutionNotes\":\"" + escape(alert.getResolutionNotes()) + "\"}");
    }

    private void handleStartWorkflow(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String type = extract(body, "workflowType", null);
        String subject = extract(body, "subject", "Administrative Operation");
        String startedBy = extract(body, "startedBy", "SUPER_ADMIN");
        WorkflowInstance wf = domainService.startWorkflow(type, subject, startedBy);
        sendJson(exchange, 201, "{\"id\":\"" + wf.getId() + "\",\"workflowType\":\"" + wf.getWorkflowType()
                + "\",\"status\":\"" + wf.getCurrentState() + "\"}");
    }

    private void handleListWorkflows(HttpExchange exchange) throws IOException {
        List<WorkflowInstance> list = domainService.listWorkflows();
        StringBuilder sb = new StringBuilder("{\"workflows\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            WorkflowInstance w = list.get(i);
            sb.append("{\"id\":\"").append(w.getId()).append("\",\"workflowType\":\"").append(w.getWorkflowType())
              .append("\",\"status\":\"").append(w.getCurrentState()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetWorkflow(HttpExchange exchange, String workflowId) throws IOException {
        WorkflowInstance wf = domainService.getWorkflow(workflowId);
        sendJson(exchange, 200, "{\"id\":\"" + wf.getId() + "\",\"workflowType\":\"" + wf.getWorkflowType()
                + "\",\"status\":\"" + wf.getCurrentState() + "\",\"startedAt\":" + wf.getStartedAt() + "}");
    }

    private void handleTransitionWorkflow(HttpExchange exchange, String workflowId) throws IOException {
        String body = readBody(exchange);
        String targetState = extract(body, "status", "RUNNING");
        String stepName = extract(body, "stepName", null);
        WorkflowInstance wf = domainService.transitionWorkflow(workflowId, targetState, stepName);
        sendJson(exchange, 200, "{\"id\":\"" + wf.getId() + "\",\"status\":\"" + wf.getCurrentState() + "\"}");
    }

    // =========================================================================
    // Phase 7: Data Governance & Audit Handlers
    // =========================================================================

    private void handleCreateRetentionPolicy(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        DataRetentionPolicy policy = new DataRetentionPolicy();
        policy.setPolicyCode(extract(body, "policyCode", null));
        policy.setDataClass(extract(body, "dataClass", "INTERNAL"));
        policy.setRetentionDays(Integer.parseInt(extract(body, "retentionDays", "365")));
        policy.setArchiveAfterDays(Integer.parseInt(extract(body, "archiveAfterDays", "180")));
        policy.setLegalHoldBehavior(extract(body, "legalHoldBehavior", "SUSPEND_PURGE"));
        policy.setLegalMinimumDays(Integer.parseInt(extract(body, "legalMinimumDays", "90")));
        DataRetentionPolicy created = domainService.createRetentionPolicy(policy);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"policyCode\":\"" + escape(created.getPolicyCode())
                + "\",\"retentionDays\":" + created.getRetentionDays()
                + ",\"legalMinimumDays\":" + created.getLegalMinimumDays()
                + ",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListRetentionPolicies(HttpExchange exchange) throws IOException {
        List<DataRetentionPolicy> list = domainService.listRetentionPolicies();
        StringBuilder sb = new StringBuilder("{\"retentionPolicies\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DataRetentionPolicy p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"policyCode\":\"").append(escape(p.getPolicyCode()))
              .append("\",\"dataClass\":\"").append(escape(p.getDataClass()))
              .append("\",\"retentionDays\":").append(p.getRetentionDays())
              .append(",\"legalMinimumDays\":").append(p.getLegalMinimumDays())
              .append(",\"status\":\"").append(p.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateDataClassification(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        DataClassification classification = new DataClassification();
        classification.setClassCode(extract(body, "classCode", null));
        classification.setSensitivity(extract(body, "sensitivity", "MEDIUM"));
        classification.setHandlingRules(extract(body, "handlingRules", "STANDARD"));
        classification.setExportRules(extract(body, "exportRules", "APPROVAL_REQUIRED"));
        DataClassification created = domainService.createDataClassification(classification);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"classCode\":\"" + escape(created.getClassCode())
                + "\",\"sensitivity\":\"" + escape(created.getSensitivity())
                + "\",\"exportRules\":\"" + escape(created.getExportRules())
                + "\",\"version\":" + created.getVersion() + "}");
    }

    private void handleListDataClassifications(HttpExchange exchange) throws IOException {
        List<DataClassification> list = domainService.listDataClassifications();
        StringBuilder sb = new StringBuilder("{\"dataClassifications\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DataClassification dc = list.get(i);
            sb.append("{\"id\":\"").append(dc.getId()).append("\",\"classCode\":\"").append(escape(dc.getClassCode()))
              .append("\",\"sensitivity\":\"").append(escape(dc.getSensitivity()))
              .append("\",\"exportRules\":\"").append(escape(dc.getExportRules()))
              .append("\",\"version\":").append(dc.getVersion()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateExportRequest(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        ExportRequest request = new ExportRequest();
        request.setRequestedBy(extract(body, "requestedBy", null));
        request.setDataScope(extract(body, "dataScope", "TENANT:DEFAULT"));
        request.setDataClass(extract(body, "dataClass", null));
        request.setFormat(extract(body, "format", "CSV"));
        request.setPurpose(extract(body, "purpose", "COMPLIANCE_AUDIT"));
        String expiresAtStr = extract(body, "expiresAt", "0");
        request.setExpiresAt(Long.parseLong(expiresAtStr));
        ExportRequest created = domainService.createExportRequest(request);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"status\":\"" + created.getStatus()
                + "\",\"requestedBy\":\"" + escape(created.getRequestedBy())
                + "\",\"expiresAt\":" + created.getExpiresAt() + "}");
    }

    private void handleListExportRequests(HttpExchange exchange) throws IOException {
        List<ExportRequest> list = domainService.listExportRequests();
        StringBuilder sb = new StringBuilder("{\"exportRequests\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            ExportRequest er = list.get(i);
            sb.append("{\"id\":\"").append(er.getId()).append("\",\"status\":\"").append(er.getStatus())
              .append("\",\"requestedBy\":\"").append(escape(er.getRequestedBy()))
              .append("\",\"dataScope\":\"").append(escape(er.getDataScope())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleApproveExportRequest(HttpExchange exchange, String exportId) throws IOException {
        String body = readBody(exchange);
        String approvedBy = extract(body, "approvedBy", "DATA_GOVERNANCE_OFFICER");
        ExportRequest approved = domainService.approveExportRequest(exportId, approvedBy);
        sendJson(exchange, 200, "{\"id\":\"" + approved.getId() + "\",\"status\":\"" + approved.getStatus()
                + "\",\"approvedBy\":\"" + escape(approved.getApprovedBy()) + "\"}");
    }

    private void handleCompleteExport(HttpExchange exchange, String exportId) throws IOException {
        String body = readBody(exchange);
        String objectRef = extract(body, "objectRef", "s3://campx-exports/" + exportId);
        String checksum = extract(body, "checksum", "SHA256-" + UUID.randomUUID().toString().substring(0, 16));
        ExportRequest completed = domainService.completeExport(exportId, objectRef, checksum);
        sendJson(exchange, 200, "{\"id\":\"" + completed.getId() + "\",\"status\":\"" + completed.getStatus()
                + "\",\"objectRef\":\"" + escape(completed.getObjectRef())
                + "\",\"checksum\":\"" + escape(completed.getChecksum()) + "\"}");
    }

    private void handleSearchAuditLogs(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        List<Map<String, Object>> results;
        if (query != null && query.contains("correlationId=")) {
            String correlationId = query.split("correlationId=")[1].split("&")[0];
            results = domainService.searchAuditByCorrelationId(correlationId);
        } else if (query != null && query.contains("actorId=")) {
            String actorId = query.split("actorId=")[1].split("&")[0];
            long fromDate = 0;
            long toDate = Long.MAX_VALUE;
            if (query.contains("fromDate=")) {
                fromDate = Long.parseLong(query.split("fromDate=")[1].split("&")[0]);
            }
            if (query.contains("toDate=")) {
                toDate = Long.parseLong(query.split("toDate=")[1].split("&")[0]);
            }
            results = domainService.searchAuditByActor(actorId, fromDate, toDate);
        } else {
            results = domainService.getAuditTrail();
        }
        StringBuilder sb = new StringBuilder("{\"auditEntries\":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> entry = results.get(i);
            sb.append("{\"eventId\":\"").append(entry.getOrDefault("eventId", "")).append("\"")
              .append(",\"action\":\"").append(entry.getOrDefault("action", "")).append("\"")
              .append(",\"principalId\":\"").append(entry.getOrDefault("principalId", "")).append("\"")
              .append(",\"resourceType\":\"").append(entry.getOrDefault("resourceType", "")).append("\"")
              .append(",\"resourceId\":\"").append(entry.getOrDefault("resourceId", "")).append("\"")
              .append(",\"traceId\":\"").append(entry.getOrDefault("traceId", "")).append("\"}");
        }
        sb.append("],\"count\":" + results.size() + "}");
        sendJson(exchange, 200, sb.toString());
    }

    private String readBody(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String getQueryParam(String query, String key) {
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equalsIgnoreCase(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private String extract(String json, String key, String defaultValue) {
        if (json == null || json.isEmpty()) return defaultValue;
        Pattern p = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);

        Pattern pNum = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher mNum = pNum.matcher(json);
        if (mNum.find()) return mNum.group(1);

        Pattern pBool = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher mBool = pBool.matcher(json);
        if (mBool.find()) return mBool.group(1);

        return defaultValue;
    }

    private String escape(String s) {
        return s != null ? s.replace("\"", "\\\"") : "";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String traceId = LogContext.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String error, String errorCode, String message, String path) throws IOException {
        String traceId = LogContext.getTraceId();
        ErrorResponse err = new ErrorResponse(status, error, errorCode, message, path, traceId);
        byte[] bytes = err.toBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if (traceId != null && !traceId.isEmpty()) {
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
