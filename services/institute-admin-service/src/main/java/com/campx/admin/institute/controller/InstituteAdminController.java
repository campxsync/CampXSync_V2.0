package com.campx.admin.institute.controller;

import com.campx.admin.institute.exception.*;
import com.campx.admin.institute.model.ErrorResponse;
import com.campx.admin.institute.model.InstituteModels.*;
import com.campx.admin.institute.model.InstituteModels.Calendar;
import com.campx.admin.institute.service.InstituteAdminDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.context.LogContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.campx.admin.institute.model.UserProfileModels.*;
import static com.campx.admin.institute.model.UserProfileModels.*;
import com.campx.admin.institute.repository.UserProfileRepository;
import com.campx.admin.institute.security.UserSecurityContext;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
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
    private final UserProfileRepository userProfileRepository;
    private final com.campx.admin.institute.security.GatewayHmacVerifier gatewayHmacVerifier;

    /**
     * Initializes the controller with default UserProfileRepository and GatewayHmacVerifier.
     *
     * @param domainService domain service instance
     */
    public InstituteAdminController(InstituteAdminDomainService domainService) {
        this(domainService, new UserProfileRepository(), new com.campx.admin.institute.security.GatewayHmacVerifier());
    }

    /**
     * Initializes the controller with custom UserProfileRepository (for testing and DI).
     *
     * @param domainService         domain service instance
     * @param userProfileRepository user profile repository instance
     */
    public InstituteAdminController(InstituteAdminDomainService domainService, UserProfileRepository userProfileRepository) {
        this(domainService, userProfileRepository, new com.campx.admin.institute.security.GatewayHmacVerifier());
    }

    /**
     * Initializes the controller with custom UserProfileRepository and custom GatewayHmacVerifier.
     *
     * @param domainService         domain service instance
     * @param userProfileRepository user profile repository instance
     * @param gatewayHmacVerifier   HMAC verifier instance
     */
    public InstituteAdminController(InstituteAdminDomainService domainService, UserProfileRepository userProfileRepository,
                                    com.campx.admin.institute.security.GatewayHmacVerifier gatewayHmacVerifier) {
        this.domainService = domainService;
        this.userProfileRepository = userProfileRepository != null ? userProfileRepository : new UserProfileRepository();
        this.gatewayHmacVerifier = gatewayHmacVerifier != null ? gatewayHmacVerifier : new com.campx.admin.institute.security.GatewayHmacVerifier();
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
        String methodOverride = exchange.getRequestHeaders().getFirst("X-HTTP-Method-Override");
        if (methodOverride != null && !methodOverride.trim().isEmpty()) {
            method = methodOverride.trim().toUpperCase(Locale.ROOT);
        }

        // Cache raw request body bytes for both HMAC verification and downstream handlers
        byte[] bodyBytes = readRequestBodyBytes(exchange);
        exchange.setAttribute("campx.request.body", bodyBytes);

        // Platform Trust Boundary: Verify Gateway HMAC signature before extracting identity or processing endpoints
        com.campx.admin.institute.security.GatewayHmacVerifier.VerificationResult authResult =
                gatewayHmacVerifier.verify(exchange, method, path, bodyBytes);
        if (!authResult.isSuccess()) {
            sendError(exchange, authResult.getStatus(), authResult.getError(), authResult.getErrorCode(), authResult.getMessage(), path);
            return;
        }

        // 1. Trace & Tenant Correlation Context
        String traceId = exchange.getRequestHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = LogContext.initTraceId();
        } else {
            LogContext.setTraceId(traceId);
        }
        String tenantId = authResult.getTenantId();
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            LogContext.setTenantId(tenantId);
        }
        String userId = authResult.getUserId();
        if (userId != null && !userId.trim().isEmpty()) {
            LogContext.setUserId(userId);
        }
        String userRole = authResult.getUserRole();
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

            // 7. User Profile Management (ADM-01 Step 1)
            if (path.equals("/api/v1/admin/users")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateUser");
                    handleCreateUser(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListUsers");
                    handleListUsers(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/users/")) {
                if (path.endsWith("/status") && "PATCH".equalsIgnoreCase(method)) {
                    String idStr = path.substring("/api/v1/admin/users/".length(), path.length() - "/status".length());
                    flow.step("handleUpdateUserStatus");
                    handleUpdateUserStatus(exchange, idStr);
                    return;
                }

                String idStr = path.substring("/api/v1/admin/users/".length());
                if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetUserById");
                    handleGetUserById(exchange, idStr);
                    return;
                } else if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateUser");
                    handleUpdateUser(exchange, idStr);
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

            // 31. Academic Calendars (Item 23)
            if (path.equals("/api/v1/admin/calendars")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCalendar");
                    handleCreateCalendar(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCalendars");
                    handleListCalendars(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/calendars/") && path.endsWith("/events")) {
                String calendarId = path.substring("/api/v1/admin/calendars/".length(), path.length() - "/events".length());
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCalendarEvent");
                    handleCreateCalendarEvent(exchange, calendarId);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCalendarEvents");
                    handleListCalendarEvents(exchange, calendarId);
                    return;
                }
            } else if (path.startsWith("/api/v1/admin/calendars/")) {
                String calendarId = path.substring("/api/v1/admin/calendars/".length());
                if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetCalendar");
                    handleGetCalendar(exchange, calendarId);
                    return;
                }
            }

            // 32. Number Sequences (Item 24)
            if (path.equals("/api/v1/admin/number-sequences")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateNumberSequence");
                    handleCreateNumberSequence(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListNumberSequences");
                    handleListNumberSequences(exchange);
                    return;
                }
            } else if (path.equals("/api/v1/admin/number-sequences/next") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleGenerateNextNumber");
                handleGenerateNextNumber(exchange);
                return;
            }

            // 33. Reference Lookups (Item 25)
            if (path.equals("/api/v1/admin/lookups/types")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateLookupType");
                    handleCreateLookupType(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListLookupTypes");
                    handleListLookupTypes(exchange);
                    return;
                }
            } else if (path.equals("/api/v1/admin/lookups/values")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateLookupValue");
                    handleCreateLookupValue(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListLookupValues");
                    handleListLookupValues(exchange);
                    return;
                }
            }

            // 34. Access Events & Audit Change Log (Items 26 & 27)
            if (path.equals("/api/v1/admin/audit/access-events") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListAccessEvents");
                handleListAccessEvents(exchange);
                return;
            }
            if (path.equals("/api/v1/admin/audit/change-log") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListChangeLogs");
                handleListChangeLogs(exchange);
                return;
            }

            // Not found
            logger.warn("[InstituteAdminService] Route not found: [{}] {}", method, path);
            sendError(exchange, 404, "Not Found", "ADM01_ROUTE_NOT_FOUND", "Resource not found in Institute Admin Service: " + path, path);
        } catch (UserProfileNotFoundException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] User profile not found [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Not Found", e.getErrorCode(), e.getMessage(), path);
        } catch (UserProfileConflictException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] User profile conflict [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Conflict", e.getErrorCode(), e.getMessage(), path);
        } catch (UserProfileAccessDeniedException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] User profile access denied [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Forbidden", e.getErrorCode(), e.getMessage(), path);
        } catch (InvalidStatusTransitionException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Invalid status transition [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Unprocessable Entity", e.getErrorCode(), e.getMessage(), path);
        } catch (InvalidUserReferenceException e) {
            flow.markFailed(e);
            logger.warn("[InstituteAdminService] Invalid reference [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
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
            String activeTraceId = LogContext.getTraceId() != null ? LogContext.getTraceId() : traceId;
            sendError(exchange, 500, "Internal Server Error", "ADM01_INTERNAL_SERVER_ERROR",
                    "An unexpected internal error occurred. Reference trace ID: " + (activeTraceId != null ? activeTraceId : ""), path);
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

        UserSecurityContext context = extractPlatformSecurityContext(exchange);
        Institute created = domainService.registerInstitute(context, inst);
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
        UserSecurityContext context = extractPlatformSecurityContext(exchange);
        List<Institute> list = domainService.listInstitutes(context);
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
        if (id == null || id.trim().isEmpty()) {
            throw new InstituteNotFoundException("Institute", id);
        }
        // Safe UUID format check: safely translate invalid UUID input into InstituteNotFoundException/HTTP 404
        try {
            UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteNotFoundException("Institute", id);
        }

        String body = readBody(exchange);
        Institute update = new Institute();
        update.setDisplayName(extract(body, "displayName", null));
        update.setStatus(extract(body, "status", null));
        update.setTimezone(extract(body, "timezone", null));

        UserSecurityContext context = extractPlatformSecurityContext(exchange);
        Integer expectedVersion = extractRowVersion(exchange, body);

        Institute updated = domainService.updateInstitute(context, id, update, expectedVersion);
        sendJson(exchange, 200, "{\"status\":\"UPDATED\",\"version\":" + updated.getVersion() + ",\"instituteId\":\"" + id + "\"}");
    }

    private void handleRegisterCollege(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        College c = new College();
        c.setCollegeCode(extract(body, "collegeCode", null));
        c.setName(extract(body, "name", null));
        c.setInstituteId(extract(body, "instituteId", null));

        UserSecurityContext context = extractPlatformSecurityContext(exchange);
        College registered = domainService.registerCollege(context, c);
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
        String tenantId = LogContext.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = exchange.getRequestHeaders().getFirst(com.campx.logger.security.GatewayHmacProtocol.HEADER_TENANT_ID);
        }
        List<Map<String, Object>> logs = (tenantId != null && !tenantId.trim().isEmpty())
                ? domainService.getAuditTrailForTenant(tenantId.trim())
                : domainService.getAuditTrail();
        StringBuilder sb = new StringBuilder("{\"auditLogs\":[");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> log = logs.get(i);
            sb.append("{");
            sb.append("\"eventId\":\"").append(escape(log.get("eventId"))).append("\",");
            sb.append("\"action\":\"").append(escape(log.get("action"))).append("\",");
            sb.append("\"principalId\":\"").append(escape(log.get("principalId"))).append("\",");
            sb.append("\"principalRole\":\"").append(escape(log.get("principalRole"))).append("\",");
            sb.append("\"resourceType\":\"").append(escape(log.get("resourceType"))).append("\",");
            sb.append("\"resourceId\":\"").append(escape(log.get("resourceId"))).append("\",");
            sb.append("\"status\":\"").append(escape(log.get("status"))).append("\",");
            sb.append("\"description\":\"").append(escape(log.get("description"))).append("\",");
            sb.append("\"traceId\":\"").append(escape(log.get("traceId"))).append("\",");
            sb.append("\"timestamp\":").append(log.get("timestamp"));
            sb.append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 1: RBAC & Identity Handler Methods (User Story Lines 17–21)
    // =========================================================================

    private static final Set<String> FORBIDDEN_CREATE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "tenant_id", "tenantid", "created_at", "createdat", "created_by", "createdby",
            "updated_at", "updatedat", "updated_by", "updatedby", "deleted_at", "deletedat",
            "row_version", "rowversion"
    )));

    private static final Set<String> FORBIDDEN_UPDATE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "id", "userid", "user_id", "tenant_id", "tenantid", "created_at", "createdat",
            "created_by", "createdby", "updated_at", "updatedat", "updated_by", "updatedby",
            "deleted_at", "deletedat", "status"
    )));

    private void validateNoForbiddenFields(String json, Set<String> forbiddenKeys) {
        if (json == null || json.isEmpty()) return;
        for (String key : forbiddenKeys) {
            Pattern p = Pattern.compile("(?i)\"" + Pattern.quote(key) + "\"\\s*:");
            if (p.matcher(json).find()) {
                throw new SecurityViolationException("Client cannot supply server-managed or immutable field: '" + key + "'");
            }
        }
    }

    private UserSecurityContext extractSecurityContext(HttpExchange exchange) {
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
        return UserSecurityContext.fromHeaders(userId, tenantId);
    }

    private UserSecurityContext extractPlatformSecurityContext(HttpExchange exchange) {
        String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
        String tenantId = exchange.getRequestHeaders().getFirst("X-Tenant-Id");
        if (userId == null || userId.trim().isEmpty()) {
            userId = LogContext.getUserId();
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = LogContext.getTenantId();
        }
        return UserSecurityContext.fromPlatformHeaders(userId, tenantId);
    }

    private Integer extractRowVersion(HttpExchange exchange, String body) {
        String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
        if (ifMatch != null && !ifMatch.trim().isEmpty()) {
            String clean = ifMatch.trim().replace("\"", "").replace("W/", "").trim();
            try {
                return Integer.parseInt(clean);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid If-Match header value (must be integer): " + ifMatch);
            }
        }
        String bodyVal = extract(body, "row_version", null);
        if (bodyVal == null) {
            bodyVal = extract(body, "rowVersion", null);
        }
        if (bodyVal != null && !bodyVal.trim().isEmpty()) {
            try {
                return Integer.parseInt(bodyVal.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid row_version format (must be integer): " + bodyVal);
            }
        }
        return null;
    }

    private String extractJsonObject(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        Pattern p = Pattern.compile("(?i)\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\{)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        int start = m.start(1);
        int depth = 0;
        boolean inQuotes = false;
        boolean escapeNext = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            if (c == '\\') {
                escapeNext = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private Map<String, String> parseQueryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return params;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            int idx = pair.indexOf('=');
            try {
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                    String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    params.put(key, value);
                } else if (idx < 0) {
                    String key = URLDecoder.decode(pair, "UTF-8");
                    params.put(key, "");
                }
            } catch (Exception ignored) {
            }
        }
        return params;
    }

    private void handleCreateUser(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body.isEmpty()) {
            throw new MalformedPayloadException("Request body cannot be empty");
        }

        validateNoForbiddenFields(body, FORBIDDEN_CREATE_FIELDS);

        UserSecurityContext ctx = extractSecurityContext(exchange);

        CreateUserProfileRequest req = new CreateUserProfileRequest();
        req.setId(extract(body, "id", null));
        req.setEmail(extract(body, "email", null));
        String fullName = extract(body, "fullName", null);
        if (fullName == null) fullName = extract(body, "full_name", null);
        req.setFullName(fullName);
        String status = extract(body, "status", "ACTIVE");
        if ("LOCKED".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status)) {
            throw new SecurityViolationException("Initial user creation cannot specify status '" + status + "'. Allowed: ACTIVE, INACTIVE");
        }
        req.setStatus(status);
        String collegeId = extract(body, "collegeId", null);
        if (collegeId == null) collegeId = extract(body, "college_id", null);
        req.setCollegeId(collegeId);
        String departmentId = extract(body, "departmentId", null);
        if (departmentId == null) departmentId = extract(body, "department_id", null);
        req.setDepartmentId(departmentId);
        String personId = extract(body, "personId", null);
        if (personId == null) personId = extract(body, "person_id", null);
        req.setPersonId(personId);

        String preferences = extractJsonObject(body, "preferences");
        if (preferences == null) {
            preferences = extract(body, "preferences", null);
        }
        req.setPreferences(preferences);

        UserProfile created = userProfileRepository.createUser(ctx, req);
        exchange.getResponseHeaders().set("ETag", "\"" + created.getRowVersion() + "\"");
        exchange.getResponseHeaders().set("Location", "/api/v1/admin/users/" + created.getId());
        sendJson(exchange, 201, created.toJson());
    }

    private void handleListUsers(HttpExchange exchange) throws IOException {
        UserSecurityContext ctx = extractSecurityContext(exchange);
        Map<String, String> queryParams = parseQueryParams(exchange);

        UserProfileFilter filter = new UserProfileFilter();
        if (queryParams.containsKey("status")) {
            filter.setStatus(queryParams.get("status"));
        }
        if (queryParams.containsKey("college_id")) {
            filter.setCollegeId(queryParams.get("college_id"));
        } else if (queryParams.containsKey("collegeId")) {
            filter.setCollegeId(queryParams.get("collegeId"));
        }
        if (queryParams.containsKey("department_id")) {
            filter.setDepartmentId(queryParams.get("department_id"));
        } else if (queryParams.containsKey("departmentId")) {
            filter.setDepartmentId(queryParams.get("departmentId"));
        }
        if (queryParams.containsKey("q")) {
            filter.setQuery(queryParams.get("q"));
        }
        if (queryParams.containsKey("sort")) {
            String sort = queryParams.get("sort");
            if (sort != null && !sort.trim().isEmpty()) {
                if (!SORT_FIELD_WHITELIST.containsKey(sort.trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Invalid sort field: '" + sort + "'. Whitelisted fields: " + SORT_FIELD_WHITELIST.keySet());
                }
                filter.setSortField(sort.trim());
            }
        }
        if (queryParams.containsKey("order")) {
            String order = queryParams.get("order");
            if (order != null && !order.trim().isEmpty()) {
                if (!"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
                    throw new IllegalArgumentException("Invalid sort order: '" + order + "'. Allowed: ASC, DESC");
                }
                filter.setSortOrder(order.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (queryParams.containsKey("page")) {
            try {
                int page = Integer.parseInt(queryParams.get("page"));
                if (page < 1) {
                    throw new IllegalArgumentException("Page number must be >= 1");
                }
                filter.setPage(page);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid page number parameter: " + queryParams.get("page"));
            }
        }
        if (queryParams.containsKey("limit")) {
            try {
                int limit = Integer.parseInt(queryParams.get("limit"));
                if (limit < 1 || limit > 100) {
                    throw new IllegalArgumentException("Page limit must be between 1 and 100");
                }
                filter.setLimit(limit);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid limit parameter: " + queryParams.get("limit"));
            }
        }

        UserProfilePage page = userProfileRepository.listUsers(ctx, filter);
        sendJson(exchange, 200, page.toJson());
    }

    private void handleGetUserById(HttpExchange exchange, String idStr) throws IOException {
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required user ID path parameter");
        }
        UUID userId;
        try {
            userId = UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format (must be UUID): " + idStr);
        }

        UserSecurityContext ctx = extractSecurityContext(exchange);
        UserProfile profile = userProfileRepository.getUserById(ctx, userId);
        exchange.getResponseHeaders().set("ETag", "\"" + profile.getRowVersion() + "\"");
        sendJson(exchange, 200, profile.toJson());
    }

    private void handleUpdateUser(HttpExchange exchange, String idStr) throws IOException {
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required user ID path parameter");
        }
        UUID userId;
        try {
            userId = UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format (must be UUID): " + idStr);
        }

        String body = readBody(exchange);
        if (body.isEmpty()) {
            throw new MalformedPayloadException("Request body cannot be empty");
        }

        validateNoForbiddenFields(body, FORBIDDEN_UPDATE_FIELDS);

        Integer rowVersion = extractRowVersion(exchange, body);
        if (rowVersion == null) {
            throw new MalformedPayloadException("Missing required optimistic lock field 'row_version' (or If-Match header)");
        }

        UserSecurityContext ctx = extractSecurityContext(exchange);

        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setRowVersion(rowVersion);
        String fullName = extract(body, "fullName", null);
        if (fullName == null) fullName = extract(body, "full_name", null);
        req.setFullName(fullName);
        req.setEmail(extract(body, "email", null));
        req.setUsername(extract(body, "username", null));
        String collegeId = extract(body, "collegeId", null);
        if (collegeId == null) collegeId = extract(body, "college_id", null);
        req.setCollegeId(collegeId);
        String departmentId = extract(body, "departmentId", null);
        if (departmentId == null) departmentId = extract(body, "department_id", null);
        req.setDepartmentId(departmentId);
        String personId = extract(body, "personId", null);
        if (personId == null) personId = extract(body, "person_id", null);
        req.setPersonId(personId);

        String preferences = extractJsonObject(body, "preferences");
        if (preferences == null) {
            preferences = extract(body, "preferences", null);
        }
        req.setPreferences(preferences);

        UserProfile updated = userProfileRepository.updateUser(ctx, userId, req);
        exchange.getResponseHeaders().set("ETag", "\"" + updated.getRowVersion() + "\"");
        sendJson(exchange, 200, updated.toJson());
    }

    private void handleUpdateUserStatus(HttpExchange exchange, String idStr) throws IOException {
        if (idStr == null || idStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required user ID path parameter");
        }
        UUID userId;
        try {
            userId = UUID.fromString(idStr.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID format (must be UUID): " + idStr);
        }

        String body = readBody(exchange);
        if (body.isEmpty()) {
            throw new MalformedPayloadException("Request body cannot be empty");
        }

        String status = extract(body, "status", null);
        if (status == null || status.trim().isEmpty()) {
            throw new MalformedPayloadException("Missing required field 'status'");
        }

        Integer rowVersion = extractRowVersion(exchange, body);
        if (rowVersion == null) {
            throw new MalformedPayloadException("Missing required optimistic lock field 'row_version' (or If-Match header)");
        }

        String rawReason = extract(body, "reason", null);
        String reason = null;
        if (rawReason != null) {
            // Strip CR and LF to prevent log injection and sanitize
            reason = rawReason.replace("\r", " ").replace("\n", " ").trim();
            if (reason.length() > 500) {
                throw new SecurityViolationException("Field 'reason' exceeds maximum allowed length of 500 characters");
            }
        }

        UserSecurityContext ctx = extractSecurityContext(exchange);

        // Note: The 'reason' field is an administrative audit annotation that is logged
        // with actor and trace context. It is not persisted in iam.user_profiles.
        logger.info("[ADM-01 StatusTransition] Actor: {} transitioning user: {} to status: {} in tenant: {} | traceId: {} | reason: {}",
                ctx.getUserId(), userId, status, ctx.getTenantId(), LogContext.getTraceId(), reason != null ? reason : "NONE");

        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        req.setStatus(status);
        req.setRowVersion(rowVersion);
        req.setReason(reason);

        UserProfile updated = userProfileRepository.updateUserStatus(ctx, userId, req);
        exchange.getResponseHeaders().set("ETag", "\"" + updated.getRowVersion() + "\"");
        sendJson(exchange, 200, updated.toJson());
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
        String tenantId = LogContext.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = exchange.getRequestHeaders().getFirst(com.campx.logger.security.GatewayHmacProtocol.HEADER_TENANT_ID);
        }
        if (tenantId != null) {
            tenantId = tenantId.trim();
        }

        Map<String, String> queryParams = parseQueryParams(exchange);
        List<Map<String, Object>> results;

        boolean hasCorrelationId = queryParams.containsKey("correlationId");
        boolean hasActorId = queryParams.containsKey("actorId");
        boolean hasFromDate = queryParams.containsKey("fromDate");
        boolean hasToDate = queryParams.containsKey("toDate");

        String correlationId = null;
        if (hasCorrelationId) {
            correlationId = queryParams.get("correlationId");
            if (correlationId == null || correlationId.trim().isEmpty()) {
                throw new IllegalArgumentException("Query parameter 'correlationId' cannot be empty");
            }
            correlationId = correlationId.trim();
            if (correlationId.length() > 256) {
                throw new IllegalArgumentException("Query parameter 'correlationId' exceeds maximum length of 256 characters");
            }
        }

        String actorId = null;
        if (hasActorId) {
            actorId = queryParams.get("actorId");
            if (actorId == null || actorId.trim().isEmpty()) {
                throw new IllegalArgumentException("Query parameter 'actorId' cannot be empty");
            }
            actorId = actorId.trim();
            if (actorId.length() > 256) {
                throw new IllegalArgumentException("Query parameter 'actorId' exceeds maximum length of 256 characters");
            }
        }

        long fromDate = 0L;
        if (hasFromDate) {
            String fromStr = queryParams.get("fromDate");
            if (fromStr == null || fromStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Query parameter 'fromDate' cannot be empty");
            }
            try {
                fromDate = Long.parseLong(fromStr.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid non-numeric parameter 'fromDate': " + fromStr);
            }
            if (fromDate < 0) {
                throw new IllegalArgumentException("Parameter 'fromDate' must be non-negative");
            }
        }

        long toDate = Long.MAX_VALUE;
        if (hasToDate) {
            String toStr = queryParams.get("toDate");
            if (toStr == null || toStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Query parameter 'toDate' cannot be empty");
            }
            try {
                toDate = Long.parseLong(toStr.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid non-numeric parameter 'toDate': " + toStr);
            }
            if (toDate < 0) {
                throw new IllegalArgumentException("Parameter 'toDate' must be non-negative");
            }
        }

        if (fromDate > toDate) {
            throw new IllegalArgumentException("Parameter 'fromDate' cannot be greater than 'toDate'");
        }

        if (hasCorrelationId) {
            results = domainService.searchAuditByCorrelationId(tenantId, correlationId);
        } else if (hasActorId) {
            results = domainService.searchAuditByActor(tenantId, actorId, fromDate, toDate);
        } else if (hasFromDate || hasToDate) {
            results = domainService.searchAuditByDateRange(tenantId, fromDate, toDate);
        } else {
            results = domainService.getAuditTrailForTenant(tenantId);
        }

        StringBuilder sb = new StringBuilder("{\"auditEntries\":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append(",");
            Map<String, Object> entry = results.get(i);
            sb.append("{\"eventId\":\"").append(escape(entry.get("eventId"))).append("\"")
              .append(",\"action\":\"").append(escape(entry.get("action"))).append("\"")
              .append(",\"principalId\":\"").append(escape(entry.get("principalId"))).append("\"")
              .append(",\"resourceType\":\"").append(escape(entry.get("resourceType"))).append("\"")
              .append(",\"resourceId\":\"").append(escape(entry.get("resourceId"))).append("\"")
              .append(",\"traceId\":\"").append(escape(entry.get("traceId"))).append("\"}");
        }
        sb.append("],\"count\":").append(results.size()).append("}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 8: Academic Calendars Handlers (Item 23)
    // =========================================================================

    private void handleCreateCalendar(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Calendar calendar = new Calendar();
        calendar.setCode(extract(body, "code", extract(body, "calendarCode", null)));
        calendar.setName(extract(body, "name", null));
        calendar.setCalendarType(extract(body, "calendarType", "ACADEMIC"));
        calendar.setCollegeId(extract(body, "collegeId", null));
        calendar.setAcademicYearId(extract(body, "academicYearId", null));
        String status = extract(body, "status", "DRAFT");
        calendar.setStatus(status);
        Calendar created = domainService.createCalendar(calendar);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + escape(created.getCode())
                + "\",\"name\":\"" + escape(created.getName()) + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListCalendars(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String collegeId = getQueryParam(query, "collegeId");
        List<Calendar> list = collegeId != null && !collegeId.trim().isEmpty()
                ? domainService.listCalendarsByCollege(collegeId)
                : domainService.listCalendars();
        StringBuilder sb = new StringBuilder("{\"calendars\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Calendar c = list.get(i);
            sb.append("{\"id\":\"").append(c.getId()).append("\",\"code\":\"").append(escape(c.getCode()))
              .append("\",\"name\":\"").append(escape(c.getName()))
              .append("\",\"collegeId\":\"").append(escape(c.getCollegeId()))
              .append("\",\"status\":\"").append(c.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGetCalendar(HttpExchange exchange, String calendarId) throws IOException {
        Calendar c = domainService.getCalendar(calendarId);
        sendJson(exchange, 200, "{\"id\":\"" + c.getId() + "\",\"code\":\"" + escape(c.getCode())
                + "\",\"name\":\"" + escape(c.getName()) + "\",\"collegeId\":\"" + escape(c.getCollegeId())
                + "\",\"status\":\"" + c.getStatus() + "\"}");
    }

    private void handleCreateCalendarEvent(HttpExchange exchange, String calendarId) throws IOException {
        String body = readBody(exchange);
        CalendarEvent event = new CalendarEvent();
        event.setCalendarId(calendarId);
        event.setEventType(extract(body, "eventType", "GENERAL"));
        event.setTitle(extract(body, "title", null));
        event.setDescription(extract(body, "description", ""));
        String holidayStr = extract(body, "isHoliday", "false");
        event.setHoliday(Boolean.parseBoolean(holidayStr));
        String startStr = extract(body, "startDate", null);
        if (startStr != null) {
            try { event.setStartDate(Long.parseLong(startStr)); } catch (NumberFormatException ignored) {}
        }
        String endStr = extract(body, "endDate", null);
        if (endStr != null) {
            try { event.setEndDate(Long.parseLong(endStr)); } catch (NumberFormatException ignored) {}
        }
        CalendarEvent created = domainService.createCalendarEvent(event);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"calendarId\":\"" + created.getCalendarId()
                + "\",\"title\":\"" + escape(created.getTitle()) + "\",\"eventType\":\"" + escape(created.getEventType()) + "\"}");
    }

    private void handleListCalendarEvents(HttpExchange exchange, String calendarId) throws IOException {
        List<CalendarEvent> list = domainService.listCalendarEvents(calendarId);
        StringBuilder sb = new StringBuilder("{\"events\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CalendarEvent e = list.get(i);
            sb.append("{\"id\":\"").append(e.getId()).append("\",\"calendarId\":\"").append(e.getCalendarId())
              .append("\",\"title\":\"").append(escape(e.getTitle()))
              .append("\",\"eventType\":\"").append(escape(e.getEventType()))
              .append("\",\"isHoliday\":").append(e.isHoliday()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 9: Number Sequences Handlers (Item 24)
    // =========================================================================

    private void handleCreateNumberSequence(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey(extract(body, "scopeKey", null));
        seq.setPrefix(extract(body, "prefix", ""));
        seq.setSuffix(extract(body, "suffix", ""));
        String nextValStr = extract(body, "nextValue", "1");
        try { seq.setNextValue(Long.parseLong(nextValStr)); } catch (NumberFormatException ignored) {}
        String padStr = extract(body, "padding", "6");
        try { seq.setPadding(Short.parseShort(padStr)); } catch (NumberFormatException ignored) {}
        seq.setResetPolicy(extract(body, "resetPolicy", "NEVER"));
        seq.setCollegeId(extract(body, "collegeId", null));
        NumberSequence created = domainService.createNumberSequence(seq);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"scopeKey\":\"" + escape(created.getScopeKey())
                + "\",\"prefix\":\"" + escape(created.getPrefix()) + "\",\"nextValue\":" + created.getNextValue() + "}");
    }

    private void handleListNumberSequences(HttpExchange exchange) throws IOException {
        List<NumberSequence> list = domainService.listNumberSequences();
        StringBuilder sb = new StringBuilder("{\"sequences\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            NumberSequence s = list.get(i);
            sb.append("{\"id\":\"").append(s.getId()).append("\",\"scopeKey\":\"").append(escape(s.getScopeKey()))
              .append("\",\"prefix\":\"").append(escape(s.getPrefix()))
              .append("\",\"nextValue\":").append(s.getNextValue())
              .append(",\"padding\":").append(s.getPadding()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleGenerateNextNumber(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String scopeKey = extract(body, "scopeKey", null);
        String collegeId = extract(body, "collegeId", null);
        String nextNumber = domainService.generateNextNumber(scopeKey, collegeId);
        sendJson(exchange, 200, "{\"scopeKey\":\"" + escape(scopeKey) + "\",\"generatedNumber\":\"" + escape(nextNumber) + "\"}");
    }

    // =========================================================================
    // Phase 10: Reference Lookups Handlers (Item 25)
    // =========================================================================

    private void handleCreateLookupType(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        LookupType lt = new LookupType();
        lt.setCode(extract(body, "code", null));
        lt.setName(extract(body, "name", null));
        lt.setDescription(extract(body, "description", ""));
        LookupType created = domainService.createLookupType(lt);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + escape(created.getCode())
                + "\",\"name\":\"" + escape(created.getName()) + "\"}");
    }

    private void handleListLookupTypes(HttpExchange exchange) throws IOException {
        List<LookupType> list = domainService.listLookupTypes();
        StringBuilder sb = new StringBuilder("{\"lookupTypes\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            LookupType t = list.get(i);
            sb.append("{\"id\":\"").append(t.getId()).append("\",\"code\":\"").append(escape(t.getCode()))
              .append("\",\"name\":\"").append(escape(t.getName())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateLookupValue(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        LookupValue lv = new LookupValue();
        lv.setLookupTypeId(extract(body, "lookupTypeId", null));
        lv.setCode(extract(body, "code", null));
        lv.setLabel(extract(body, "label", null));
        LookupValue created = domainService.createLookupValue(lv);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + escape(created.getCode())
                + "\",\"label\":\"" + escape(created.getLabel()) + "\"}");
    }

    private void handleListLookupValues(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String typeId = getQueryParam(query, "lookupTypeId");
        List<LookupValue> list = domainService.listLookupValues(typeId);
        StringBuilder sb = new StringBuilder("{\"lookupValues\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            LookupValue v = list.get(i);
            sb.append("{\"id\":\"").append(v.getId()).append("\",\"lookupTypeId\":\"").append(v.getLookupTypeId())
              .append("\",\"code\":\"").append(escape(v.getCode()))
              .append("\",\"label\":\"").append(escape(v.getLabel())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    // =========================================================================
    // Phase 11: Access Events & Audit Change Log Handlers (Items 26 & 27)
    // =========================================================================

    private void handleListAccessEvents(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String limitStr = getQueryParam(query, "limit");
        int limit = 50;
        if (limitStr != null) {
            try { limit = Integer.parseInt(limitStr); } catch (NumberFormatException ignored) {}
        }
        List<AccessEvent> list = domainService.listRecentAccessEvents(limit);
        StringBuilder sb = new StringBuilder("{\"accessEvents\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            AccessEvent ae = list.get(i);
            sb.append("{\"id\":\"").append(ae.getId()).append("\",\"principalId\":\"").append(escape(ae.getPrincipalId()))
              .append("\",\"resourceType\":\"").append(escape(ae.getResourceType()))
              .append("\",\"resourceId\":\"").append(escape(ae.getResourceId()))
              .append("\",\"accessType\":\"").append(escape(ae.getAccessType()))
              .append("\",\"ip\":\"").append(escape(ae.getIp()))
              .append("\",\"createdAt\":").append(ae.getCreatedAt()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleListChangeLogs(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String limitStr = getQueryParam(query, "limit");
        int limit = 50;
        if (limitStr != null) {
            try { limit = Integer.parseInt(limitStr); } catch (NumberFormatException ignored) {}
        }
        List<AuditChangeLog> list = domainService.listRecentChangeLogs(limit);
        StringBuilder sb = new StringBuilder("{\"changeLogs\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            AuditChangeLog cl = list.get(i);
            sb.append("{\"id\":\"").append(cl.getId()).append("\",\"tableSchema\":\"").append(escape(cl.getTableSchema()))
              .append("\",\"tableName\":\"").append(escape(cl.getTableName()))
              .append("\",\"recordId\":\"").append(escape(cl.getRecordId()))
              .append("\",\"action\":\"").append(escape(cl.getAction()))
              .append("\",\"actorId\":\"").append(escape(cl.getActorId()))
              .append("\",\"createdAt\":").append(cl.getCreatedAt()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private byte[] readRequestBodyBytes(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        byte[] cached = (byte[]) exchange.getAttribute("campx.request.body");
        if (cached != null) {
            return new String(cached, StandardCharsets.UTF_8).trim();
        }
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

    private String escape(Object obj) {
        if (obj == null) return "";
        return escape(obj.toString());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
