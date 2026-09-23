package com.campx.admin.college.controller;

import com.campx.admin.college.exception.*;
import com.campx.admin.college.model.ErrorResponse;
import com.campx.admin.college.model.CollegeModels.*;
import com.campx.admin.college.service.CollegeAdminDomainService;
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
 * HTTP REST Controller for ADM-02: College Admin Service (College Operational Tier).
 * <p>
 * Routes incoming HTTP requests matching {@code /api/v1/college-admin/**} to domain service handlers.
 * Extracts correlation headers ({@code X-Trace-Id}, {@code X-Tenant-Id}, {@code X-User-Id}, {@code X-User-Role}),
 * coordinates execution flow tracing via {@link FlowTracker}, and maps domain exceptions to RFC 7807 responses.
 *
 * @see CollegeAdminDomainService
 * @see ErrorResponse
 */
public class CollegeAdminController implements HttpHandler {

    /**
     * Structured logger instance for HTTP request dispatching.
     */
    private static final CampXLogger logger = CampXLoggerFactory.getLogger(CollegeAdminController.class);

    /**
     * Backing domain business service providing operations and transactional logic.
     */
    private final CollegeAdminDomainService domainService;

    /**
     * Constructs a {@code CollegeAdminController} with the specified domain business service.
     *
     * @param domainService the backing domain service
     */
    public CollegeAdminController(CollegeAdminDomainService domainService) {
        this.domainService = domainService;
    }

    /**
     * Dispatches incoming HTTP requests to their appropriate operational handler endpoints,
     * maintaining distributed trace context and mapping domain exceptions to RFC 7807 errors.
     *
     * @param exchange the encapsulated HTTP request and response
     * @throws IOException if network I/O errors occur during transmission
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

        logger.info("[CollegeAdminService] Incoming [{}] {}", method, path);

        FlowTracker flow = logger.flow("CollegeAdminRequest", method + " " + path);
        try {
            // 1. Profile Endpoint
            if (path.equals("/api/v1/college-admin/profile")) {
                if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetProfile");
                    handleGetProfile(exchange);
                    return;
                } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateProfile");
                    handleUpdateProfile(exchange);
                    return;
                }
            }

            // 2. Departments Endpoint
            if (path.equals("/api/v1/college-admin/departments")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateDepartment");
                    handleCreateDepartment(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDepartments");
                    handleListDepartments(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/departments/")) {
                String depId = path.substring("/api/v1/college-admin/departments/".length());
                if ("DELETE".equalsIgnoreCase(method)) {
                    flow.step("handleRetireDepartment");
                    handleRetireDepartment(exchange, depId);
                    return;
                }
            }

            // 3. Programs Endpoint
            if (path.equals("/api/v1/college-admin/programs")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateProgram");
                    handleCreateProgram(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListPrograms");
                    handleListPrograms(exchange);
                    return;
                }
            }

            // 4. Data Imports Endpoint
            if (path.equals("/api/v1/college-admin/imports") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleSubmitImport");
                handleSubmitImport(exchange);
                return;
            } else if (path.startsWith("/api/v1/college-admin/imports/") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetImport");
                String id = path.substring("/api/v1/college-admin/imports/".length());
                handleGetImport(exchange, id);
                return;
            }

            // 5. Governance Documents Endpoint
            if (path.equals("/api/v1/college-admin/documents")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleRegisterDocument");
                    handleRegisterDocument(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDocuments");
                    handleListDocuments(exchange);
                    return;
                }
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/submit$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleApproveDocument");
                String[] parts = path.split("/");
                String docId = parts[parts.length - 2];
                handleApproveDocument(exchange, docId);
                return;
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/versions$")) {
                String[] parts = path.split("/");
                String docId = parts[parts.length - 2];
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateDocumentVersion");
                    handleCreateDocumentVersion(exchange, docId);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDocumentVersions");
                    handleListDocumentVersions(exchange, docId);
                    return;
                }
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/versions/[0-9]+/publish$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handlePublishDocumentVersion");
                String[] parts = path.split("/");
                String docId = parts[parts.length - 4];
                int versionNo = Integer.parseInt(parts[parts.length - 2]);
                handlePublishDocumentVersion(exchange, docId, versionNo);
                return;
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/permissions$")) {
                String[] parts = path.split("/");
                String docId = parts[parts.length - 2];
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleGrantDocumentPermission");
                    handleGrantDocumentPermission(exchange, docId);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListDocumentPermissions");
                    handleListDocumentPermissions(exchange, docId);
                    return;
                }
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/permissions/[^/]+$") && "DELETE".equalsIgnoreCase(method)) {
                flow.step("handleRevokeDocumentPermission");
                String[] parts = path.split("/");
                String docId = parts[parts.length - 3];
                String permId = parts[parts.length - 1];
                handleRevokeDocumentPermission(exchange, docId, permId);
                return;
            } else if (path.matches("^/api/v1/college-admin/documents/[^/]+/submit-version$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleSubmitDocumentForApproval");
                String[] parts = path.split("/");
                String docId = parts[parts.length - 2];
                handleSubmitDocumentForApproval(exchange, docId);
                return;
            } else if (path.matches("^/api/v1/college-admin/documents/approvals/[^/]+/decide$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleDecideDocumentApproval");
                String[] parts = path.split("/");
                String approvalId = parts[parts.length - 2];
                handleDecideDocumentApproval(exchange, approvalId);
                return;
            }

            // 6. Audit Trail Endpoint
            if (path.equals("/api/v1/college-admin/audit-logs") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleGetAuditLogs");
                handleGetAuditLogs(exchange);
                return;
            }

            // =====================================================================
            // Phase 1: College RBAC & Identity Endpoints (User Story Lines 12–16)
            // =====================================================================

            // 7. College Users Endpoint
            if (path.equals("/api/v1/college-admin/users")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCollegeUser");
                    handleCreateCollegeUser(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegeUsers");
                    handleListCollegeUsers(exchange);
                    return;
                }
            }

            // 8. College Roles Endpoint
            if (path.equals("/api/v1/college-admin/roles")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCollegeRole");
                    handleCreateCollegeRole(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegeRoles");
                    handleListCollegeRoles(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/roles/")) {
                String roleId = path.substring("/api/v1/college-admin/roles/".length());
                if ("DELETE".equalsIgnoreCase(method)) {
                    flow.step("handleDeleteCollegeRole");
                    handleDeleteCollegeRole(exchange, roleId);
                    return;
                }
            }

            // 9. College Permissions Endpoint
            if (path.equals("/api/v1/college-admin/permissions")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCollegePermission");
                    handleCreateCollegePermission(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegePermissions");
                    handleListCollegePermissions(exchange);
                    return;
                }
            }

            // 10. College Role Bindings Endpoint
            if (path.equals("/api/v1/college-admin/role-bindings")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCollegeRoleBinding");
                    handleCreateCollegeRoleBinding(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegeRoleBindings");
                    handleListCollegeRoleBindings(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/role-bindings/")) {
                String bindingId = path.substring("/api/v1/college-admin/role-bindings/".length());
                if ("DELETE".equalsIgnoreCase(method)) {
                    flow.step("handleRevokeCollegeRoleBinding");
                    handleRevokeCollegeRoleBinding(exchange, bindingId);
                    return;
                }
            }

            // 11. College Access Reviews Endpoint
            if (path.equals("/api/v1/college-admin/access/reviews")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateCollegeAccessReview");
                    handleCreateCollegeAccessReview(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegeAccessReviews");
                    handleListCollegeAccessReviews(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/access/reviews/")) {
                String reviewId = path.substring("/api/v1/college-admin/access/reviews/".length());
                if ("PUT".equalsIgnoreCase(method)) {
                    flow.step("handleCompleteCollegeAccessReview");
                    handleCompleteCollegeAccessReview(exchange, reviewId);
                    return;
                }
            }

            // =====================================================================
            // Phase 2: Transactional Reliability Layer Endpoints (User Story Lines 35–38)
            // =====================================================================

            // 12. College Outbox Events Endpoint
            if (path.equals("/api/v1/college-admin/events/outbox") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListCollegeOutboxEvents");
                handleListCollegeOutboxEvents(exchange);
                return;
            }

            // 13. College Inbox Events Endpoint
            if (path.equals("/api/v1/college-admin/events/inbox")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleProcessCollegeInboxEvent");
                    handleProcessCollegeInboxEvent(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegeInboxEvents");
                    handleListCollegeInboxEvents(exchange);
                    return;
                }
            }

            // 14. College Dead-Letter Events Endpoint
            if (path.equals("/api/v1/college-admin/events/dead-letter") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListCollegeDeadLetterEvents");
                handleListCollegeDeadLetterEvents(exchange);
                return;
            } else if (path.matches("^/api/v1/college-admin/events/dead-letter/[^/]+/replay$") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleReplayCollegeDeadLetterEvent");
                String[] parts = path.split("/");
                String deadLetterId = parts[parts.length - 2];
                handleReplayCollegeDeadLetterEvent(exchange, deadLetterId);
                return;
            }

            // 15. College Idempotency Records Endpoint
            if (path.equals("/api/v1/college-admin/idempotency-records") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListCollegeIdempotencyRecords");
                handleListCollegeIdempotencyRecords(exchange);
                return;
            }

            // =====================================================================
            // Phase 3: Configuration & Governance Endpoints (User Story Lines 8–10)
            // =====================================================================

            // 16. College Settings Endpoint
            if (path.equals("/api/v1/college-admin/settings")) {
                if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                    flow.step("handleSetCollegeSetting");
                    handleSetCollegeSetting(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetCollegeSettings");
                    handleGetCollegeSettings(exchange);
                    return;
                }
            }

            // 17. Feature Overrides Endpoint
            if (path.equals("/api/v1/college-admin/feature-overrides")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateFeatureOverride");
                    handleCreateFeatureOverride(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListFeatureOverrides");
                    handleListFeatureOverrides(exchange);
                    return;
                }
            }

            // 18. Local Policies Endpoint
            if (path.equals("/api/v1/college-admin/policies")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateLocalPolicy");
                    handleCreateLocalPolicy(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListLocalPolicies");
                    handleListLocalPolicies(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/policies/") && path.endsWith("/publish") && "POST".equalsIgnoreCase(method)) {
                flow.step("handlePublishLocalPolicy");
                String policyCode = path.substring("/api/v1/college-admin/policies/".length(), path.length() - "/publish".length());
                handlePublishLocalPolicy(exchange, policyCode);
                return;
            } else if (path.startsWith("/api/v1/college-admin/policies/") && path.endsWith("/approve") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleApproveLocalPolicy");
                String policyCode = path.substring("/api/v1/college-admin/policies/".length(), path.length() - "/approve".length());
                handleApproveLocalPolicy(exchange, policyCode);
                return;
            }

            // =====================================================================
            // Phase 6: Calendar, Reporting & Workflow Endpoints (User Stories 6, 21-24, 31-32)
            // =====================================================================

            // 19. Calendar Configuration Endpoint (CSV Line 6)
            if (path.equals("/api/v1/college-admin/calendar/config")) {
                if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                    flow.step("handleUpdateCalendarConfig");
                    handleUpdateCalendarConfig(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetCalendarConfig");
                    handleGetCalendarConfig(exchange);
                    return;
                }
            }

            // 20. Report Schedules Endpoint (check before /reports/{code})
            if (path.equals("/api/v1/college-admin/reports/schedules")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleScheduleReport");
                    handleScheduleReport(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListReportSchedules");
                    handleListReportSchedules(exchange);
                    return;
                }
            }

            // 21. Report Definitions & Execution Endpoints (CSV Lines 21-22)
            if (path.equals("/api/v1/college-admin/reports")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateReportDefinition");
                    handleCreateReportDefinition(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListReportDefinitions");
                    handleListReportDefinitions(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/reports/") && path.endsWith("/execute") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleExecuteReport");
                String code = path.substring("/api/v1/college-admin/reports/".length(), path.length() - "/execute".length());
                handleExecuteReport(exchange, code);
                return;
            } else if (path.startsWith("/api/v1/college-admin/reports/") && path.endsWith("/runs") && "GET".equalsIgnoreCase(method)) {
                flow.step("handleListReportRuns");
                String code = path.substring("/api/v1/college-admin/reports/".length(), path.length() - "/runs".length());
                handleListReportRuns(exchange, code);
                return;
            }

            // 22. Dashboard Snapshots Endpoint (CSV Line 24)
            if (path.equals("/api/v1/college-admin/dashboards/snapshots")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleCreateDashboardSnapshot");
                    handleCreateDashboardSnapshot(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetDashboardSnapshots");
                    handleGetDashboardSnapshots(exchange);
                    return;
                }
            }

            // 23. Approval Requests Endpoint (CSV Line 31)
            if (path.equals("/api/v1/college-admin/approvals")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleSubmitApprovalRequest");
                    handleSubmitApprovalRequest(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListApprovalRequests");
                    handleListApprovalRequests(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/approvals/") && path.endsWith("/decide") && "POST".equalsIgnoreCase(method)) {
                flow.step("handleDecideApprovalRequest");
                String reqId = path.substring("/api/v1/college-admin/approvals/".length(), path.length() - "/decide".length());
                handleDecideApprovalRequest(exchange, reqId);
                return;
            }

            // 24. College Workflows Endpoint (CSV Line 32)
            if (path.equals("/api/v1/college-admin/workflows")) {
                if ("POST".equalsIgnoreCase(method)) {
                    flow.step("handleStartCollegeWorkflow");
                    handleStartCollegeWorkflow(exchange);
                    return;
                } else if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleListCollegeWorkflows");
                    handleListCollegeWorkflows(exchange);
                    return;
                }
            } else if (path.startsWith("/api/v1/college-admin/workflows/")) {
                String wfId = path.substring("/api/v1/college-admin/workflows/".length());
                if ("GET".equalsIgnoreCase(method)) {
                    flow.step("handleGetCollegeWorkflow");
                    handleGetCollegeWorkflow(exchange, wfId);
                    return;
                } else if ("PUT".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method)) {
                    flow.step("handleTransitionCollegeWorkflow");
                    handleTransitionCollegeWorkflow(exchange, wfId);
                    return;
                }
            }

            // Not found
            logger.warn("[CollegeAdminService] Route not found: [{}] {}", method, path);
            sendError(exchange, 404, "Not Found", "ADM02_ROUTE_NOT_FOUND", "Resource not found in College Admin Service: " + path, path);
        } catch (CollegeResourceNotFoundException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Resource not found [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Not Found", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeResourceConflictException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Conflict [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Conflict", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeLifecycleException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Unprocessable entity [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Unprocessable Entity", e.getErrorCode(), e.getMessage(), path);
        } catch (DocumentGovernanceException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Document governance error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeMalformedPayloadException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Malformed payload [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Bad Request", e.getErrorCode(), e.getMessage(), path);
        } catch (CollegeAdminException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] College admin error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, e.getStatus(), "Client Error", e.getErrorCode(), e.getMessage(), path);
        } catch (IllegalArgumentException | IllegalStateException e) {
            flow.markFailed(e);
            logger.warn("[CollegeAdminService] Validation error [{} {}]: {}", method, path, e.getMessage());
            sendError(exchange, 400, "Bad Request", "ADM02_VALIDATION_ERROR", e.getMessage(), path);
        } catch (Exception e) {
            flow.markFailed(e);
            logger.error("[CollegeAdminService] Internal server error [{} {}]: {}", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error", "ADM02_INTERNAL_SERVER_ERROR", "An unexpected server error occurred: " + escape(e.getMessage()), path);
        } finally {
            if (flow != null) {
                flow.close();
            }
            LogContext.clear();
        }
    }

    private void handleGetProfile(HttpExchange exchange) throws IOException {
        CollegeProfile p = domainService.getProfile();
        String json = "{"
                + "\"code\":\"" + p.getCollegeCode() + "\","
                + "\"name\":\"" + escape(p.getLegalName()) + "\","
                + "\"displayName\":\"" + escape(p.getDisplayName()) + "\","
                + "\"status\":\"" + p.getStatus() + "\","
                + "\"version\":" + p.getCurrentVersion()
                + "}";
        sendJson(exchange, 200, json);
    }

    private void handleUpdateProfile(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeProfile update = new CollegeProfile();
        update.setDisplayName(extract(body, "displayName", null));
        update.setAddress(extract(body, "address", null));
        update.setStatus(extract(body, "status", null));

        CollegeProfile updated = domainService.updateProfile(update);
        sendJson(exchange, 200, "{\"status\":\"UPDATED\",\"version\":" + updated.getCurrentVersion() + "}");
    }

    private void handleCreateDepartment(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Department d = new Department();
        d.setDepartmentCode(extract(body, "departmentCode", null));
        d.setName(extract(body, "name", null));
        d.setHeadUserId(extract(body, "headUserId", "FACULTY_HOD"));

        Department created = domainService.createDepartment(d);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + created.getDepartmentCode() + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleRetireDepartment(HttpExchange exchange, String depId) throws IOException {
        domainService.retireDepartment(depId);
        sendJson(exchange, 200, "{\"status\":\"RETIRED\",\"id\":\"" + depId + "\"}");
    }

    private void handleListDepartments(HttpExchange exchange) throws IOException {
        List<Department> list = domainService.listDepartments();
        StringBuilder sb = new StringBuilder("{\"departments\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Department d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"code\":\"").append(d.getDepartmentCode())
              .append("\",\"name\":\"").append(escape(d.getName())).append("\",\"status\":\"").append(d.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateProgram(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        Program p = new Program();
        p.setProgramCode(extract(body, "programCode", null));
        p.setName(extract(body, "name", null));
        p.setDepartmentId(extract(body, "departmentId", null));
        String dur = extract(body, "durationYears", "4");
        try {
            p.setDurationYears(Integer.parseInt(dur));
        } catch (NumberFormatException e) {
            throw new CollegeMalformedPayloadException("Field 'durationYears' must be a valid integer");
        }

        Program created = domainService.createProgram(p);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"code\":\"" + created.getProgramCode() + "\",\"published\":true}");
    }

    private void handleListPrograms(HttpExchange exchange) throws IOException {
        List<Program> list = domainService.listPrograms();
        StringBuilder sb = new StringBuilder("{\"programs\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Program p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"code\":\"").append(p.getProgramCode())
              .append("\",\"name\":\"").append(escape(p.getName())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSubmitImport(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        DataImportJob job = new DataImportJob();
        job.setEntityType(extract(body, "entityType", "STUDENT"));
        job.setFileRef(extract(body, "fileRef", "s3://imports/students.csv"));
        job.setMode(extract(body, "mode", "UPSERT"));
        job.setIdempotencyKey(extract(body, "idempotencyKey", exchange.getRequestHeaders().getFirst("Idempotency-Key")));

        DataImportJob executed = domainService.submitImportJob(job);
        sendJson(exchange, 200, "{\"importId\":\"" + executed.getImportId() + "\",\"status\":\"" + executed.getStatus() + "\",\"processed\":" + executed.getProcessedRows() + ",\"failed\":" + executed.getFailedRows() + "}");
    }

    private void handleGetImport(HttpExchange exchange, String id) throws IOException {
        DataImportJob job = domainService.getImportJob(id);
        if (job == null) {
            throw new CollegeResourceNotFoundException("Data Import Job", id);
        }
        sendJson(exchange, 200, "{\"id\":\"" + job.getImportId() + "\",\"status\":\"" + job.getStatus() + "\",\"processed\":" + job.getProcessedRows() + "}");
    }

    private void handleRegisterDocument(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        GovernanceDocument doc = new GovernanceDocument();
        doc.setDocumentType(extract(body, "documentType", "POLICY"));
        doc.setTitle(extract(body, "title", "Campus Policy"));
        doc.setOwnerId(extract(body, "ownerId", "ADMIN_01"));
        doc.setClassification(extract(body, "classification", null));

        GovernanceDocument registered = domainService.registerDocument(doc);
        sendJson(exchange, 201, "{\"id\":\"" + registered.getId() + "\",\"checksum\":\"" + registered.getChecksum() + "\",\"status\":\"" + registered.getStatus() + "\"}");
    }

    private void handleApproveDocument(HttpExchange exchange, String docId) throws IOException {
        GovernanceDocument approved = domainService.submitDocumentApproval(docId, "DEAN_OFFICE");
        sendJson(exchange, 200, "{\"id\":\"" + approved.getId() + "\",\"status\":\"" + approved.getStatus() + "\"}");
    }

    private void handleListDocuments(HttpExchange exchange) throws IOException {
        List<GovernanceDocument> list = domainService.listDocuments();
        StringBuilder sb = new StringBuilder("{\"documents\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            GovernanceDocument d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"title\":\"").append(escape(d.getTitle()))
              .append("\",\"status\":\"").append(d.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateDocumentVersion(HttpExchange exchange, String docId) throws IOException {
        String body = readBody(exchange);
        String objectRef = extract(body, "objectRef", null);
        String createdBy = extract(body, "createdBy", "DOCUMENT_AUTHOR");
        DocumentVersion version = domainService.createDocumentVersion(docId, objectRef, createdBy);
        sendJson(exchange, 201, "{\"id\":\"" + version.getId() + "\",\"documentId\":\"" + version.getDocumentId()
                + "\",\"versionNo\":" + version.getVersionNo()
                + ",\"checksum\":\"" + version.getChecksum()
                + "\",\"status\":\"" + version.getStatus() + "\"}");
    }

    private void handleListDocumentVersions(HttpExchange exchange, String docId) throws IOException {
        List<DocumentVersion> list = domainService.listDocumentVersions(docId);
        StringBuilder sb = new StringBuilder("{\"versions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DocumentVersion v = list.get(i);
            sb.append("{\"id\":\"").append(v.getId()).append("\",\"versionNo\":").append(v.getVersionNo())
              .append(",\"status\":\"").append(v.getStatus()).append("\"")
              .append(",\"checksum\":\"").append(v.getChecksum()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handlePublishDocumentVersion(HttpExchange exchange, String docId, int versionNo) throws IOException {
        DocumentVersion v = domainService.publishDocumentVersion(docId, versionNo);
        sendJson(exchange, 200, "{\"id\":\"" + v.getId() + "\",\"versionNo\":" + v.getVersionNo()
                + ",\"status\":\"" + v.getStatus() + "\"}");
    }

    private void handleGrantDocumentPermission(HttpExchange exchange, String docId) throws IOException {
        String body = readBody(exchange);
        DocumentPermission perm = new DocumentPermission();
        perm.setDocumentId(docId);
        perm.setPrincipalType(extract(body, "principalType", "USER"));
        perm.setPrincipalId(extract(body, "principalId", null));
        perm.setPermission(extract(body, "permission", "VIEW"));
        String toStr = extract(body, "effectiveTo", "0");
        perm.setEffectiveTo(Long.parseLong(toStr));
        perm.setGrantedBy(extract(body, "grantedBy", "DOC_ADMIN"));
        DocumentPermission granted = domainService.grantDocumentPermission(perm);
        sendJson(exchange, 201, "{\"id\":\"" + granted.getId() + "\",\"documentId\":\"" + granted.getDocumentId()
                + "\",\"principalId\":\"" + escape(granted.getPrincipalId())
                + "\",\"permission\":\"" + granted.getPermission()
                + "\",\"effectiveTo\":" + granted.getEffectiveTo() + "}");
    }

    private void handleListDocumentPermissions(HttpExchange exchange, String docId) throws IOException {
        List<DocumentPermission> list = domainService.listDocumentPermissions(docId);
        StringBuilder sb = new StringBuilder("{\"permissions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DocumentPermission p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"principalId\":\"").append(escape(p.getPrincipalId()))
              .append("\",\"permission\":\"").append(p.getPermission())
              .append("\",\"effectiveTo\":").append(p.getEffectiveTo()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleRevokeDocumentPermission(HttpExchange exchange, String docId, String permissionId) throws IOException {
        DocumentPermission revoked = domainService.revokeDocumentPermission(docId, permissionId);
        sendJson(exchange, 200, "{\"id\":\"" + revoked.getId() + "\",\"status\":\"REVOKED\",\"effectiveTo\":" + revoked.getEffectiveTo() + "}");
    }

    private void handleSubmitDocumentForApproval(HttpExchange exchange, String docId) throws IOException {
        String body = readBody(exchange);
        String vnoStr = extract(body, "versionNo", "1");
        int versionNo = Integer.parseInt(vnoStr);
        String submittedBy = extract(body, "submittedBy", "DOCUMENT_AUTHOR");
        String approverId = extract(body, "approverId", "COLLEGE_DEAN");
        DocumentApproval approval = domainService.submitDocumentForApproval(docId, versionNo, submittedBy, approverId);
        sendJson(exchange, 201, "{\"id\":\"" + approval.getId() + "\",\"documentId\":\"" + approval.getDocumentId()
                + "\",\"versionNo\":" + approval.getVersionNo()
                + ",\"decision\":\"" + approval.getDecision() + "\"}");
    }

    private void handleDecideDocumentApproval(HttpExchange exchange, String approvalId) throws IOException {
        String body = readBody(exchange);
        String approverId = extract(body, "approverId", "COLLEGE_DEAN");
        String decision = extract(body, "decision", "APPROVED");
        String comments = extract(body, "comments", "");
        DocumentApproval decided = domainService.decideDocumentApproval(approvalId, approverId, decision, comments);
        sendJson(exchange, 200, "{\"id\":\"" + decided.getId() + "\",\"decision\":\"" + decided.getDecision()
                + "\",\"approverId\":\"" + escape(decided.getApproverId()) + "\"}");
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
    // Phase 1: College RBAC & Identity Handler Methods (User Story Lines 12–16)
    // =========================================================================

    private void handleCreateCollegeUser(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeUser user = new CollegeUser();
        user.setUserId(extract(body, "userId", null));
        user.setDisplayName(extract(body, "displayName", "College Admin"));
        user.setEmployeeRef(extract(body, "employeeRef", null));
        user.setDepartmentId(extract(body, "departmentId", null));

        CollegeUser created = domainService.registerCollegeUser(user);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"userId\":\"" + created.getUserId()
                + "\",\"displayName\":\"" + escape(created.getDisplayName()) + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListCollegeUsers(HttpExchange exchange) throws IOException {
        List<CollegeUser> list = domainService.listCollegeUsers();
        StringBuilder sb = new StringBuilder("{\"users\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegeUser u = list.get(i);
            sb.append("{\"id\":\"").append(u.getId()).append("\",\"userId\":\"").append(u.getUserId())
              .append("\",\"displayName\":\"").append(escape(u.getDisplayName()))
              .append("\",\"status\":\"").append(u.getStatus()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateCollegeRole(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeRole role = new CollegeRole();
        role.setRoleCode(extract(body, "roleCode", null));
        role.setName(extract(body, "name", null));
        role.setProtectedSystemRole("true".equalsIgnoreCase(extract(body, "protectedSystemRole", "false")));
        String permsStr = extract(body, "permissions", null);
        if (permsStr != null && !permsStr.isEmpty()) {
            role.setPermissions(java.util.Arrays.asList(permsStr.split(",")));
        }

        CollegeRole created = domainService.createCollegeRole(role);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"roleCode\":\"" + created.getRoleCode()
                + "\",\"version\":" + created.getVersion() + "}");
    }

    private void handleListCollegeRoles(HttpExchange exchange) throws IOException {
        List<CollegeRole> list = domainService.listCollegeRoles();
        StringBuilder sb = new StringBuilder("{\"roles\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegeRole r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"roleCode\":\"").append(r.getRoleCode())
              .append("\",\"name\":\"").append(escape(r.getName()))
              .append("\",\"protected\":").append(r.isProtectedSystemRole())
              .append(",\"version\":").append(r.getVersion()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleDeleteCollegeRole(HttpExchange exchange, String roleId) throws IOException {
        domainService.deleteCollegeRole(roleId);
        sendJson(exchange, 200, "{\"status\":\"DELETED\",\"roleId\":\"" + roleId + "\"}");
    }

    private void handleCreateCollegePermission(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegePermission perm = new CollegePermission();
        perm.setPermissionCode(extract(body, "permissionCode", null));
        perm.setResource(extract(body, "resource", null));
        perm.setAction(extract(body, "action", null));
        perm.setDescription(extract(body, "description", null));

        CollegePermission created = domainService.createCollegePermission(perm);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"permissionCode\":\"" + created.getPermissionCode()
                + "\",\"resource\":\"" + created.getResource() + "\"}");
    }

    private void handleListCollegePermissions(HttpExchange exchange) throws IOException {
        List<CollegePermission> list = domainService.listCollegePermissions();
        StringBuilder sb = new StringBuilder("{\"permissions\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegePermission p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"code\":\"").append(p.getPermissionCode())
              .append("\",\"resource\":\"").append(p.getResource()).append("\",\"action\":\"").append(p.getAction())
              .append("\",\"assignedToRoles\":").append(p.getAssignedToRoles()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateCollegeRoleBinding(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeRoleBinding binding = new CollegeRoleBinding();
        binding.setPrincipalId(extract(body, "principalId", null));
        binding.setRoleId(extract(body, "roleId", null));
        binding.setScopeType(extract(body, "scopeType", "COLLEGE"));
        binding.setScopeId(extract(body, "scopeId", null));

        CollegeRoleBinding created = domainService.createCollegeRoleBinding(binding);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"principalId\":\"" + created.getPrincipalId()
                + "\",\"roleId\":\"" + created.getRoleId() + "\",\"scopeType\":\"" + created.getScopeType() + "\"}");
    }

    private void handleListCollegeRoleBindings(HttpExchange exchange) throws IOException {
        List<CollegeRoleBinding> list = domainService.listCollegeRoleBindings();
        StringBuilder sb = new StringBuilder("{\"bindings\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegeRoleBinding b = list.get(i);
            sb.append("{\"id\":\"").append(b.getId()).append("\",\"principalId\":\"").append(b.getPrincipalId())
              .append("\",\"roleId\":\"").append(b.getRoleId()).append("\",\"scopeType\":\"").append(b.getScopeType())
              .append("\",\"grantedBy\":\"").append(b.getGrantedBy()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleRevokeCollegeRoleBinding(HttpExchange exchange, String bindingId) throws IOException {
        domainService.revokeCollegeRoleBinding(bindingId);
        sendJson(exchange, 200, "{\"status\":\"REVOKED\",\"bindingId\":\"" + bindingId + "\"}");
    }

    private void handleCreateCollegeAccessReview(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeAccessReview review = new CollegeAccessReview();
        review.setPrincipalId(extract(body, "principalId", null));
        review.setRoleBindingId(extract(body, "roleBindingId", null));
        String dueAtStr = extract(body, "dueAt", null);
        if (dueAtStr != null) {
            try { review.setDueAt(Long.parseLong(dueAtStr)); } catch (NumberFormatException ignored) { review.setDueAt(System.currentTimeMillis() + 604800000L); }
        } else {
            review.setDueAt(System.currentTimeMillis() + 604800000L);
        }

        CollegeAccessReview created = domainService.createCollegeAccessReview(review);
        sendJson(exchange, 201, "{\"reviewId\":\"" + created.getReviewId() + "\",\"principalId\":\"" + created.getPrincipalId()
                + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListCollegeAccessReviews(HttpExchange exchange) throws IOException {
        List<CollegeAccessReview> list = domainService.listCollegeAccessReviews();
        StringBuilder sb = new StringBuilder("{\"reviews\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegeAccessReview r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"reviewId\":\"").append(r.getReviewId())
              .append("\",\"principalId\":\"").append(r.getPrincipalId())
              .append("\",\"status\":\"").append(r.getStatus())
              .append("\",\"decision\":\"").append(r.getDecision() != null ? r.getDecision() : "PENDING").append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCompleteCollegeAccessReview(HttpExchange exchange, String reviewId) throws IOException {
        String body = readBody(exchange);
        String reviewerId = extract(body, "reviewerId", null);
        String decision = extract(body, "decision", "CERTIFY");
        CollegeAccessReview completed = domainService.completeCollegeAccessReview(reviewId, reviewerId, decision);
        sendJson(exchange, 200, "{\"reviewId\":\"" + completed.getReviewId() + "\",\"decision\":\"" + completed.getDecision()
                + "\",\"status\":\"" + completed.getStatus() + "\"}");
    }

    // =========================================================================
    // Phase 2: Transactional Reliability Layer Handlers
    // =========================================================================

    private void handleListCollegeOutboxEvents(HttpExchange exchange) throws IOException {
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

    private void handleProcessCollegeInboxEvent(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String eventId = extract(body, "eventId", null);
        String sourceService = extract(body, "sourceService", "ACD-07");
        String consumerGroup = extract(body, "consumerGroup", "ADM-02");
        String payload = extract(body, "payload", "{}");

        InboxEvent processed = domainService.processInboxEvent(eventId, sourceService, consumerGroup, payload);
        sendJson(exchange, 200, "{\"id\":\"" + processed.getId() + "\",\"eventId\":\"" + processed.getEventId()
                + "\",\"status\":\"" + processed.getStatus() + "\"}");
    }

    private void handleListCollegeInboxEvents(HttpExchange exchange) throws IOException {
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

    private void handleListCollegeDeadLetterEvents(HttpExchange exchange) throws IOException {
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

    private void handleReplayCollegeDeadLetterEvent(HttpExchange exchange, String id) throws IOException {
        DeadLetterEvent replayed = domainService.replayDeadLetterEvent(id);
        sendJson(exchange, 200, "{\"id\":\"" + replayed.getId() + "\",\"disposition\":\"" + replayed.getDisposition()
                + "\",\"status\":\"REPLAYED\"}");
    }

    private void handleListCollegeIdempotencyRecords(HttpExchange exchange) throws IOException {
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
    // Phase 3: Configuration & Governance Handlers (User Story Lines 8–10)
    // =========================================================================

    private void handleSetCollegeSetting(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CollegeSetting s = new CollegeSetting();
        s.setKey(extract(body, "key", null));
        s.setValue(extract(body, "value", null));
        s.setDataType(extract(body, "dataType", "STRING"));
        s.setSecret("true".equalsIgnoreCase(extract(body, "isSecret", "false")));
        String effFrom = extract(body, "effectiveFrom", null);
        if (effFrom != null) {
            try { s.setEffectiveFrom(Long.parseLong(effFrom)); } catch (NumberFormatException ignored) {}
        }
        String effTo = extract(body, "effectiveTo", null);
        if (effTo != null) {
            try { s.setEffectiveTo(Long.parseLong(effTo)); } catch (NumberFormatException ignored) {}
        }

        CollegeSetting saved = domainService.setCollegeSetting(s);
        sendJson(exchange, 200, "{\"id\":\"" + saved.getId() + "\",\"key\":\"" + saved.getKey()
                + "\",\"version\":" + saved.getVersion() + ",\"status\":\"SAVED\"}");
    }

    private void handleGetCollegeSettings(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        boolean effectiveOnly = query != null && query.contains("effectiveOnly=true");
        List<CollegeSetting> list = domainService.getCollegeSettings(effectiveOnly);
        StringBuilder sb = new StringBuilder("{\"settings\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegeSetting s = list.get(i);
            sb.append("{\"key\":\"").append(s.getKey()).append("\",\"value\":\"").append(escape(s.getValue()))
              .append("\",\"secret\":").append(s.isSecret()).append(",\"version\":").append(s.getVersion()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateFeatureOverride(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String flagKey = extract(body, "flagKey", null);
        String overrideValue = extract(body, "overrideValue", "true");
        String reason = extract(body, "reason", "College requirement");

        FeatureOverride fo = domainService.overrideFeatureFlag(flagKey, overrideValue, reason);
        sendJson(exchange, 201, "{\"id\":\"" + fo.getId() + "\",\"flagKey\":\"" + fo.getFlagKey()
                + "\",\"overrideValue\":\"" + fo.getOverrideValue() + "\",\"status\":\"APPLIED\"}");
    }

    private void handleListFeatureOverrides(HttpExchange exchange) throws IOException {
        List<FeatureOverride> list = domainService.listFeatureOverrides();
        StringBuilder sb = new StringBuilder("{\"overrides\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            FeatureOverride fo = list.get(i);
            sb.append("{\"id\":\"").append(fo.getId()).append("\",\"flagKey\":\"").append(fo.getFlagKey())
              .append("\",\"overrideValue\":\"").append(escape(fo.getOverrideValue()))
              .append("\",\"reason\":\"").append(escape(fo.getReason())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateLocalPolicy(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        LocalPolicy policy = new LocalPolicy();
        policy.setPolicyCode(extract(body, "policyCode", null));
        String rulesStr = extract(body, "rules", null);
        if (rulesStr != null && !rulesStr.isEmpty()) {
            policy.setRules(Arrays.asList(rulesStr.split(",")));
        }

        LocalPolicy created = domainService.createLocalPolicy(policy);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"policyCode\":\"" + created.getPolicyCode()
                + "\",\"version\":" + created.getVersion() + ",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListLocalPolicies(HttpExchange exchange) throws IOException {
        List<LocalPolicy> list = domainService.listLocalPolicies();
        StringBuilder sb = new StringBuilder("{\"policies\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            LocalPolicy p = list.get(i);
            sb.append("{\"id\":\"").append(p.getId()).append("\",\"policyCode\":\"").append(p.getPolicyCode())
              .append("\",\"status\":\"").append(p.getStatus()).append("\",\"version\":").append(p.getVersion())
              .append(",\"approvedBy\":\"").append(p.getApprovedBy() != null ? p.getApprovedBy() : "").append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleApproveLocalPolicy(HttpExchange exchange, String policyCode) throws IOException {
        String body = readBody(exchange);
        String approvedBy = extract(body, "approvedBy", LogContext.getUserId() != null ? LogContext.getUserId() : "COLLEGE_DIRECTOR");
        LocalPolicy approved = domainService.approveLocalPolicy(policyCode, approvedBy);
        sendJson(exchange, 200, "{\"policyCode\":\"" + approved.getPolicyCode() + "\",\"status\":\"" + approved.getStatus()
                + "\",\"approvedBy\":\"" + approved.getApprovedBy() + "\"}");
    }

    private void handlePublishLocalPolicy(HttpExchange exchange, String policyCode) throws IOException {
        LocalPolicy published = domainService.publishLocalPolicy(policyCode);
        sendJson(exchange, 200, "{\"policyCode\":\"" + published.getPolicyCode() + "\",\"status\":\"" + published.getStatus()
                + "\",\"version\":" + published.getVersion() + ",\"publishedAt\":" + published.getPublishedAt() + "}");
    }

    // =========================================================================
    // Phase 6: Calendar, Reporting & Workflow Handlers
    // =========================================================================

    private void handleUpdateCalendarConfig(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CalendarConfiguration cfg = new CalendarConfiguration();
        cfg.setSourceCalendarId(extract(body, "sourceCalendarId", null));
        cfg.setSyncMode(extract(body, "syncMode", "AUTOMATIC"));
        cfg.setLocalRules(extract(body, "localRules", "DEFAULT_SCHEDULE"));
        CalendarConfiguration updated = domainService.updateCalendarConfiguration(cfg);
        sendJson(exchange, 200, "{\"id\":\"" + updated.getId() + "\",\"syncMode\":\"" + updated.getSyncMode()
                + "\",\"sourceCalendarId\":\"" + escape(updated.getSourceCalendarId()) + "\"}");
    }

    private void handleGetCalendarConfig(HttpExchange exchange) throws IOException {
        CalendarConfiguration cfg = domainService.getCalendarConfiguration();
        sendJson(exchange, 200, "{\"id\":\"" + cfg.getId() + "\",\"syncMode\":\"" + cfg.getSyncMode()
                + "\",\"sourceCalendarId\":\"" + escape(cfg.getSourceCalendarId())
                + "\",\"localRules\":\"" + escape(cfg.getLocalRules()) + "\"}");
    }

    private void handleCreateReportDefinition(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        ReportDefinition def = new ReportDefinition();
        def.setReportCode(extract(body, "reportCode", null));
        def.setReportName(extract(body, "reportName", "Institutional Report"));
        def.setQuerySpec(extract(body, "querySpec", "SELECT * FROM metrics"));
        def.setDataClass(extract(body, "dataClass", "INTERNAL"));
        ReportDefinition created = domainService.createReportDefinition(def);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"reportCode\":\"" + created.getReportCode()
                + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleListReportDefinitions(HttpExchange exchange) throws IOException {
        List<ReportDefinition> list = domainService.listReportDefinitions();
        StringBuilder sb = new StringBuilder("{\"reports\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            ReportDefinition d = list.get(i);
            sb.append("{\"id\":\"").append(d.getId()).append("\",\"reportCode\":\"").append(escape(d.getReportCode()))
              .append("\",\"reportName\":\"").append(escape(d.getReportName())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleExecuteReport(HttpExchange exchange, String reportCode) throws IOException {
        String body = readBody(exchange);
        String requestedBy = extract(body, "requestedBy", "DEAN");
        String filters = extract(body, "filters", "{}");
        ReportRun run = domainService.executeReport(reportCode, requestedBy, filters);
        sendJson(exchange, 200, "{\"runId\":\"" + run.getId() + "\",\"reportCode\":\"" + run.getReportCode()
                + "\",\"status\":\"" + run.getStatus() + "\",\"rowCount\":" + run.getRowCount()
                + ",\"outputRef\":\"" + escape(run.getOutputRef()) + "\"}");
    }

    private void handleListReportRuns(HttpExchange exchange, String reportCode) throws IOException {
        List<ReportRun> list = domainService.listReportRuns(reportCode);
        StringBuilder sb = new StringBuilder("{\"runs\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            ReportRun r = list.get(i);
            sb.append("{\"runId\":\"").append(r.getId()).append("\",\"reportCode\":\"").append(r.getReportCode())
              .append("\",\"status\":\"").append(r.getStatus()).append("\",\"rowCount\":").append(r.getRowCount()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleScheduleReport(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        ReportSchedule sched = new ReportSchedule();
        sched.setReportCode(extract(body, "reportCode", null));
        sched.setCronExpression(extract(body, "cronExpression", "0 0 * * *"));
        sched.setTimezone(extract(body, "timezone", "UTC"));
        ReportSchedule created = domainService.scheduleReport(sched);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"reportCode\":\"" + created.getReportCode()
                + "\",\"cronExpression\":\"" + escape(created.getCronExpression()) + "\"}");
    }

    private void handleListReportSchedules(HttpExchange exchange) throws IOException {
        List<ReportSchedule> list = domainService.listReportSchedules();
        StringBuilder sb = new StringBuilder("{\"schedules\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            ReportSchedule s = list.get(i);
            sb.append("{\"id\":\"").append(s.getId()).append("\",\"reportCode\":\"").append(escape(s.getReportCode()))
              .append("\",\"cron\":\"").append(escape(s.getCronExpression())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleCreateDashboardSnapshot(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        DashboardSnapshot snap = new DashboardSnapshot();
        snap.setDashboardCode(extract(body, "dashboardCode", null));
        snap.setPeriod(extract(body, "period", "2026-Q3"));
        snap.setMetricsJson(extract(body, "metrics", "{}"));
        DashboardSnapshot created = domainService.createDashboardSnapshot(snap);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"dashboardCode\":\"" + created.getDashboardCode()
                + "\",\"period\":\"" + escape(created.getPeriod()) + "\"}");
    }

    private void handleGetDashboardSnapshots(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String code = getQueryParam(query, "dashboardCode");
        List<DashboardSnapshot> list = domainService.getDashboardSnapshots(code);
        StringBuilder sb = new StringBuilder("{\"snapshots\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            DashboardSnapshot s = list.get(i);
            sb.append("{\"id\":\"").append(s.getId()).append("\",\"period\":\"").append(escape(s.getPeriod()))
              .append("\",\"metrics\":").append(s.getMetricsJson()).append("}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleSubmitApprovalRequest(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        ApprovalRequest req = new ApprovalRequest();
        req.setRequestType(extract(body, "requestType", null));
        req.setSubjectType(extract(body, "subjectType", "COLLEGE_ACTION"));
        req.setSubjectId(extract(body, "subjectId", null));
        req.setSubmittedBy(extract(body, "submittedBy", "REGISTRAR"));
        ApprovalRequest created = domainService.submitApprovalRequest(req);
        sendJson(exchange, 201, "{\"id\":\"" + created.getId() + "\",\"requestType\":\"" + created.getRequestType()
                + "\",\"status\":\"" + created.getStatus() + "\"}");
    }

    private void handleDecideApprovalRequest(HttpExchange exchange, String reqId) throws IOException {
        String body = readBody(exchange);
        String approverId = extract(body, "approverId", "PRINCIPAL");
        String decision = extract(body, "decision", "APPROVE");
        String notes = extract(body, "notes", "Approved by board");
        ApprovalRequest decided = domainService.decideApprovalRequest(reqId, approverId, decision, notes);
        sendJson(exchange, 200, "{\"id\":\"" + decided.getId() + "\",\"status\":\"" + decided.getStatus()
                + "\",\"decidedBy\":\"" + escape(decided.getDecidedBy()) + "\"}");
    }

    private void handleListApprovalRequests(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String status = getQueryParam(query, "status");
        List<ApprovalRequest> list = domainService.listApprovalRequests(status);
        StringBuilder sb = new StringBuilder("{\"approvals\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            ApprovalRequest r = list.get(i);
            sb.append("{\"id\":\"").append(r.getId()).append("\",\"type\":\"").append(escape(r.getRequestType()))
              .append("\",\"status\":\"").append(r.getStatus()).append("\",\"submittedBy\":\"").append(escape(r.getSubmittedBy())).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleStartCollegeWorkflow(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String type = extract(body, "workflowType", null);
        String subject = extract(body, "subject", "College Administrative Flow");
        CollegeWorkflowInstance wf = domainService.startCollegeWorkflow(type, subject);
        sendJson(exchange, 201, "{\"id\":\"" + wf.getId() + "\",\"workflowType\":\"" + wf.getWorkflowType()
                + "\",\"status\":\"" + wf.getCurrentState() + "\"}");
    }

    private void handleTransitionCollegeWorkflow(HttpExchange exchange, String wfId) throws IOException {
        String body = readBody(exchange);
        String targetState = extract(body, "status", "RUNNING");
        String step = extract(body, "stepName", null);
        String compensation = extract(body, "compensationAction", null);
        CollegeWorkflowInstance wf = domainService.transitionCollegeWorkflow(wfId, targetState, step, compensation);
        sendJson(exchange, 200, "{\"id\":\"" + wf.getId() + "\",\"status\":\"" + wf.getCurrentState() + "\"}");
    }

    private void handleGetCollegeWorkflow(HttpExchange exchange, String wfId) throws IOException {
        CollegeWorkflowInstance wf = domainService.getCollegeWorkflow(wfId);
        sendJson(exchange, 200, "{\"id\":\"" + wf.getId() + "\",\"workflowType\":\"" + wf.getWorkflowType()
                + "\",\"status\":\"" + wf.getCurrentState() + "\",\"startedAt\":" + wf.getStartedAt() + "}");
    }

    private void handleListCollegeWorkflows(HttpExchange exchange) throws IOException {
        List<CollegeWorkflowInstance> list = domainService.listCollegeWorkflows();
        StringBuilder sb = new StringBuilder("{\"workflows\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            CollegeWorkflowInstance w = list.get(i);
            sb.append("{\"id\":\"").append(w.getId()).append("\",\"workflowType\":\"").append(w.getWorkflowType())
              .append("\",\"status\":\"").append(w.getCurrentState()).append("\"}");
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
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

    private String extract(String json, String key, String defaultValue) {
        if (json == null || json.isEmpty()) return defaultValue;

        Pattern pObj = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*(\\{.*?\\}|\\[.*?\\])", Pattern.DOTALL);
        Matcher mObj = pObj.matcher(json);
        if (mObj.find()) return mObj.group(1);

        Pattern pStr = Pattern.compile("(?i)\"" + key + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher mStr = pStr.matcher(json);
        if (mStr.find()) {
            return mStr.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }

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
