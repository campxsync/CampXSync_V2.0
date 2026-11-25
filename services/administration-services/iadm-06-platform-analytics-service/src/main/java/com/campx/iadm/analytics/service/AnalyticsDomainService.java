package com.campx.iadm.analytics.service;

import com.campx.iadm.analytics.client.Iadm05PolicyClient;
import com.campx.iadm.analytics.client.SourceAggregateClient;
import com.campx.iadm.analytics.config.DatabaseConnectionManager;
import com.campx.iadm.analytics.exception.InstituteAdminException;
import com.campx.iadm.analytics.exception.UserProfileAccessDeniedException;
import com.campx.iadm.analytics.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-06 domain orchestration — catalogue, aggregates, suppression, export metadata.
 * Plain language: compute on request from owner contracts; store only catalogue + export metadata.
 */
public class AnalyticsDomainService {

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();
    private final Iadm05PolicyClient policyClient;
    private final SourceAggregateClient sourceClient;
    private volatile int suppressionThreshold = 1; // DEMO ONLY runtime

    public AnalyticsDomainService() {
        String i05 = System.getProperty("campx.iadm05.baseUrl",
                System.getenv().getOrDefault("CAMPX_IADM05_BASE_URL", "http://127.0.0.1:8103/api/v1/iadm/05"));
        String i01 = System.getProperty("campx.iadm01.baseUrl",
                System.getenv().getOrDefault("CAMPX_IADM01_BASE_URL", "http://127.0.0.1:8099/api/v1/iadm/01"));
        String i04 = System.getProperty("campx.iadm04.baseUrl",
                System.getenv().getOrDefault("CAMPX_IADM04_BASE_URL", "http://127.0.0.1:8102/api/v1/iadm/04"));
        this.policyClient = new Iadm05PolicyClient(i05);
        this.sourceClient = new SourceAggregateClient(i01, i04);
        SuppressionEngine.setThreshold(suppressionThreshold);
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "UP");
        out.put("service", "IADM-06 Platform Analytics & Usage Insights");
        out.put("port", Integer.valueOf(8104));
        out.put("prefix", "/api/v1/iadm/06");
        out.put("queryTime", java.time.Instant.now().toString());
        return out;
    }

    public Map<String, Object> getOutageFlags(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.platform.view", "iadm06.catalogue.view");
                return AnalyticsOutageFlags.snapshot();
            }
        });
    }

    public Map<String, Object> setOutageFlags(UserSecurityContext ctx, Boolean audit, Boolean s01,
                                              Boolean s04, Boolean s05) {
        return setOutageFlags(ctx, audit, s01, s04, s05, null);
    }

    public Map<String, Object> setOutageFlags(UserSecurityContext ctx, Boolean audit, Boolean s01,
                                              Boolean s04, Boolean s05, Boolean exportFailOnce) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireSuperOrSystem(conn);
                AnalyticsOutageFlags.set(audit, s01, s04, s05, exportFailOnce);
                return AnalyticsOutageFlags.snapshot();
            }
        });
    }

    public Map<String, Object> getSuppressionSettings(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.platform.view", "iadm06.institute.view");
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("threshold", Integer.valueOf(SuppressionEngine.getThreshold()));
                m.put("label", "DEMO ONLY — not a privacy standard");
                m.put("documentedDefault", Integer.valueOf(SuppressionEngine.DOCUMENTED_DEMO_DEFAULT));
                m.put("productionThreshold", "OWNER-OPEN");
                return m;
            }
        });
    }

    public Map<String, Object> setSuppressionThreshold(UserSecurityContext ctx, int value) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireSuperOrSystem(conn);
                SuppressionEngine.setThreshold(value);
                suppressionThreshold = value;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("threshold", Integer.valueOf(SuppressionEngine.getThreshold()));
                m.put("label", "DEMO ONLY — not a privacy standard");
                m.put("documentedDefault", Integer.valueOf(SuppressionEngine.DOCUMENTED_DEMO_DEFAULT));
                m.put("productionThreshold", "OWNER-OPEN");
                return m;
            }
        });
    }

    /** Read-only metric catalogue (US-010 / US-023). */
    public Map<String, Object> listCatalogue(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.catalogue.view");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT metric_code, metric_name, plain_definition, source_owner, grain, "
                                + "time_windows, visibility_roles, small_group_rule, catalogue_version, demo_label "
                                + "FROM anl.metric_catalogue ORDER BY metric_code");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("metricCode", rs.getString("metric_code"));
                        row.put("metricName", rs.getString("metric_name"));
                        row.put("plainDefinition", rs.getString("plain_definition"));
                        row.put("sourceOwner", rs.getString("source_owner"));
                        row.put("grain", rs.getString("grain"));
                        row.put("timeWindows", rs.getString("time_windows"));
                        row.put("visibilityRoles", rs.getString("visibility_roles"));
                        row.put("smallGroupRule", rs.getString("small_group_rule"));
                        row.put("catalogueVersion", rs.getString("catalogue_version"));
                        row.put("demoLabel", rs.getString("demo_label"));
                        items.add(row);
                    }
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM06_CATALOGUE_VIEW", "metric_catalogue", "v1.0", "catalogue.view");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("catalogueVersion", "1.0");
                out.put("readOnly", Boolean.TRUE);
                out.put("queryTime", java.time.Instant.now().toString());
                return out;
            }
        });
    }

    /** Platform overview M01/M02/M03/M04/M07/M08 (US-001 / US-013). */
    public Map<String, Object> platformOverview(UserSecurityContext ctx, String window,
                                                Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.platform.view");
                Map<String, Object> policy = policyClient.resolveDataHandling(forwardHeaders);
                Map<String, Object> out = composeFromSources(ctx, conn, true, null, window, forwardHeaders,
                        new String[]{"M01", "M02", "M03", "M04", "M07", "M08"});
                out.put("policyResolution", policy);
                DurableAuditWriter.append(conn, ctx, null,
                        "IADM06_PLATFORM_VIEW", "analytics_overview", "platform",
                        "policy=" + String.valueOf(policy.get("code")));
                return out;
            }
        });
    }

    /** Own-institute overview (US-003). */
    public Map<String, Object> instituteOverview(UserSecurityContext ctx, String tenantId,
                                                 String window, Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.institute.view", "iadm06.platform.view");
                UUID scope = resolveOwnScope(conn, ctx, tenantId);
                Map<String, Object> policy = policyClient.resolveDataHandling(forwardHeaders);
                Map<String, Object> out = composeFromSources(ctx, conn, false, scope.toString(), window,
                        forwardHeaders, new String[]{"M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10"});
                out.put("policyResolution", policy);
                out.put("scopeTenantId", scope.toString());
                DurableAuditWriter.append(conn, ctx, scope,
                        "IADM06_INSTITUTE_VIEW", "analytics_overview", scope.toString(),
                        "policy=" + String.valueOf(policy.get("code")));
                return out;
            }
        });
    }

    /** Institute comparison (US-002). */
    public Map<String, Object> compareInstitutes(UserSecurityContext ctx, String window,
                                                 Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.platform.view");
                Map<String, Object> policy = policyClient.resolveDataHandling(forwardHeaders);
                Map<String, Object> out = composeFromSources(ctx, conn, true, null, window, forwardHeaders,
                        new String[]{"M03", "M06", "M08", "M09"});
                out.put("policyResolution", policy);
                out.put("comparison", Boolean.TRUE);
                DurableAuditWriter.append(conn, ctx, null,
                        "IADM06_PLATFORM_VIEW", "analytics_comparison", "platform",
                        "policy=" + String.valueOf(policy.get("code")));
                return out;
            }
        });
    }

    /** Entitlement insight (US-004 / US-011). */
    public Map<String, Object> entitlementInsight(UserSecurityContext ctx, String tenantId,
                                                  Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.entitlement.view");
                boolean platform = isSuperAdmin(conn) || isSystemAdmin(conn)
                        || hasPermission(conn, "iadm06.platform.view");
                UUID scope = null;
                if (!platform) {
                    scope = resolveOwnScope(conn, ctx, tenantId);
                } else if (tenantId != null && !tenantId.trim().isEmpty()) {
                    scope = UUID.fromString(tenantId.trim());
                }
                Map<String, Object> policy = policyClient.resolveDataHandling(forwardHeaders);
                Map<String, Object> out = composeFromSources(ctx, conn, platform && scope == null,
                        scope == null ? null : scope.toString(), null, forwardHeaders,
                        new String[]{"M07", "M08", "M09"});
                out.put("policyResolution", policy);
                DurableAuditWriter.append(conn, ctx, scope,
                        "IADM06_ENTITLEMENT_VIEW", "entitlement_insight",
                        scope == null ? "platform" : scope.toString(),
                        "policy=" + String.valueOf(policy.get("code")));
                return out;
            }
        });
    }

    /** Trend explorer (US-005). */
    public Map<String, Object> trends(UserSecurityContext ctx, String tenantId, String window,
                                      Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.platform.view", "iadm06.institute.view");
                boolean platform = isSuperAdmin(conn) || isSystemAdmin(conn)
                        || hasPermission(conn, "iadm06.platform.view");
                UUID scope = null;
                if (!platform || (tenantId != null && !hasPermission(conn, "iadm06.platform.view")
                        && !isSuperAdmin(conn) && !isSystemAdmin(conn))) {
                    scope = resolveOwnScope(conn, ctx, tenantId);
                    platform = false;
                } else if (!platform) {
                    scope = resolveOwnScope(conn, ctx, tenantId);
                }
                Map<String, Object> policy = policyClient.resolveDataHandling(forwardHeaders);
                Map<String, Object> out = composeFromSources(ctx, conn, platform, scope == null ? null : scope.toString(),
                        window == null ? "month" : window, forwardHeaders,
                        new String[]{"M05", "M06", "M10"});
                out.put("policyResolution", policy);
                DurableAuditWriter.append(conn, ctx, scope,
                        "IADM06_PLATFORM_VIEW", "analytics_trends",
                        scope == null ? "platform" : scope.toString(), "trends");
                return out;
            }
        });
    }

    /** Request aggregate export (US-006 / US-007 / US-026). */
    public Map<String, Object> requestExport(UserSecurityContext ctx, Map<String, Object> body,
                                             Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.export.request");
                // Auditor must never reach here if keys correct; belt-and-braces:
                if (hasPermission(conn, "iadm06.export.request") == false
                        && !isSuperAdmin(conn) && !isSystemAdmin(conn)) {
                    throw new UserProfileAccessDeniedException("Export denied");
                }
                Map<String, Object> exportPolicy = policyClient.resolveExportPolicy(forwardHeaders);
                boolean platform = isSuperAdmin(conn) || isSystemAdmin(conn)
                        || hasPermission(conn, "iadm06.platform.view");
                String scopeType = platform && (body == null || !"INSTITUTE".equals(String.valueOf(body.get("scopeType"))))
                        ? "PLATFORM" : "INSTITUTE";
                UUID scopeTenant = null;
                if ("INSTITUTE".equals(scopeType)) {
                    String tid = body == null ? null : str(body, "tenantId");
                    scopeTenant = resolveOwnScope(conn, ctx, tid);
                    if (!platform && ctx.getTenantId() != null && !ctx.getTenantId().equals(scopeTenant)) {
                        throw new UserProfileAccessDeniedException("Out-of-scope institute export refused");
                    }
                }
                UUID id = UUID.randomUUID();
                UUID corr = UUID.randomUUID();
                String filterJson = body == null ? "{}" : toSimpleJson(body);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO anl.export_requests "
                                + "(id, requester_user_id, scope_type, scope_tenant_id, filter_snapshot, "
                                + "resolved_policy_id, resolved_policy_code, resolved_policy_version, "
                                + "policy_outcome, status, audit_correlation_id, demo_label) "
                                + "VALUES (?,?,?,?,?::jsonb,?,?,?,?, 'REQUESTED', ?, 'DEMO ONLY')")) {
                    ps.setObject(1, id);
                    ps.setObject(2, ctx.getUserId());
                    ps.setString(3, scopeType);
                    if (scopeTenant == null) {
                        ps.setNull(4, Types.OTHER);
                    } else {
                        ps.setObject(4, scopeTenant);
                    }
                    ps.setString(5, filterJson);
                    Object pid = exportPolicy.get("policyId");
                    if (pid == null) {
                        ps.setNull(6, Types.OTHER);
                    } else {
                        try {
                            ps.setObject(6, UUID.fromString(String.valueOf(pid)));
                        } catch (Exception e) {
                            ps.setNull(6, Types.OTHER);
                        }
                    }
                    ps.setString(7, String.valueOf(exportPolicy.get("code")));
                    Object vn = exportPolicy.get("versionNo");
                    if (vn instanceof Number) {
                        ps.setInt(8, ((Number) vn).intValue());
                    } else {
                        ps.setNull(8, Types.INTEGER);
                    }
                    ps.setString(9, "RESOLVED");
                    ps.setObject(10, corr);
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, scopeTenant,
                        "IADM06_EXPORT_REQUEST", "export_request", id.toString(),
                        "scope=" + scopeType);
                // Process synchronously for V1 stream-on-request
                processExport(conn, ctx, id, scopeType, scopeTenant, windowOf(body), forwardHeaders);
                return loadExport(conn, id);
            }
        });
    }

    public Map<String, Object> listExports(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.export.view", "iadm06.export.request");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, scope_type, scope_tenant_id, status, resolved_policy_code, "
                        + "resolved_policy_version, created_at, completed_at, audit_correlation_id "
                        + "FROM anl.export_requests WHERE requester_user_id = ? ORDER BY created_at DESC LIMIT 50";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, ctx.getUserId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapExportRow(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("note", "No file payload stored — metadata only");
                return out;
            }
        });
    }

    public Map<String, Object> getExport(UserSecurityContext ctx, UUID exportId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.export.view", "iadm06.export.request");
                Map<String, Object> row = loadExport(conn, exportId);
                if (row == null) {
                    throw new InstituteAdminException(404, "IADM06_NOT_FOUND", "Export request not found");
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM06_EXPORT_RESULT", "export_request", exportId.toString(),
                        "status=" + row.get("status"));
                return row;
            }
        });
    }

    public Map<String, Object> retryExport(UserSecurityContext ctx, UUID exportId,
                                           Map<String, String> forwardHeaders) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm06.export.request");
                Map<String, Object> row = loadExport(conn, exportId);
                if (row == null) {
                    throw new InstituteAdminException(404, "IADM06_NOT_FOUND", "Export request not found");
                }
                if ("COMPLETED".equals(String.valueOf(row.get("status")))) {
                    return row; // never duplicate completed
                }
                policyClient.resolveExportPolicy(forwardHeaders);
                String scopeType = String.valueOf(row.get("scopeType"));
                UUID scopeTenant = row.get("scopeTenantId") == null ? null
                        : UUID.fromString(String.valueOf(row.get("scopeTenantId")));
                processExport(conn, ctx, exportId, scopeType, scopeTenant, null, forwardHeaders);
                return loadExport(conn, exportId);
            }
        });
    }

    /** Build CSV text for a completed export (streamed; not stored). */
    public String buildExportCsv(UserSecurityContext ctx, UUID exportId, Map<String, String> forwardHeaders) {
        Map<String, Object> meta = getExport(ctx, exportId);
        if (!"COMPLETED".equals(String.valueOf(meta.get("status")))
                && !"PROCESSING".equals(String.valueOf(meta.get("status")))) {
            if ("FAILED".equals(String.valueOf(meta.get("status")))) {
                throw new InstituteAdminException(409, "IADM06_EXPORT_FAILED",
                        "Export could not be completed");
            }
        }
        boolean platform = "PLATFORM".equals(String.valueOf(meta.get("scopeType")));
        String tenant = meta.get("scopeTenantId") == null ? null : String.valueOf(meta.get("scopeTenantId"));
        Map<String, Object> figures = platform
                ? platformOverview(ctx, null, forwardHeaders)
                : instituteOverview(ctx, tenant, null, forwardHeaders);
        StringBuilder csv = new StringBuilder("metric,group,value,label\n");
        appendCsvMetrics(csv, figures);
        return csv.toString();
    }

    // ----------------- internals -----------------

    private void processExport(Connection conn, UserSecurityContext ctx, UUID id, String scopeType,
                               UUID scopeTenant, String window, Map<String, String> headers)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE anl.export_requests SET status = 'PROCESSING', updated_at = now() WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
        if (AnalyticsOutageFlags.consumeExportFailOnce()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE anl.export_requests SET status = 'FAILED', error_message = ?, updated_at = now() "
                            + "WHERE id = ?")) {
                ps.setString(1, "DEMO ONLY simulated export failure");
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            DurableAuditWriter.append(conn, ctx, scopeTenant,
                    "IADM06_EXPORT_RESULT", "export_request", id.toString(), "FAILED");
            return;
        }
        try {
            // Regeneration is on-the-fly at download; mark COMPLETED after successful policy+scope check
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE anl.export_requests SET status = 'COMPLETED', completed_at = now(), "
                            + "updated_at = now(), error_message = NULL WHERE id = ?")) {
                ps.setObject(1, id);
                ps.executeUpdate();
            }
            DurableAuditWriter.append(conn, ctx, scopeTenant,
                    "IADM06_EXPORT_RESULT", "export_request", id.toString(), "COMPLETED");
        } catch (Exception ex) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE anl.export_requests SET status = 'FAILED', error_message = ?, updated_at = now() "
                            + "WHERE id = ?")) {
                ps.setString(1, "Export processing failed");
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            if (!AnalyticsOutageFlags.isAuditDisabled()) {
                DurableAuditWriter.append(conn, ctx, scopeTenant,
                        "IADM06_EXPORT_RESULT", "export_request", id.toString(), "FAILED");
            }
        }
    }

    private Map<String, Object> composeFromSources(UserSecurityContext ctx, Connection conn,
                                                   boolean platformScope, String tenantId, String window,
                                                   Map<String, String> headers, String[] metricCodes) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("queryTime", java.time.Instant.now().toString());
        out.put("refreshMeans", "query time");
        out.put("platformScope", Boolean.valueOf(platformScope));
        out.put("suppressionThreshold", Integer.valueOf(SuppressionEngine.getThreshold()));
        out.put("suppressionLabel", "DEMO ONLY — not a privacy standard");

        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        String raw01 = null;
        String raw04 = null;
        try {
            raw01 = sourceClient.fetchIadm01Aggregates(window, tenantId, headers);
            out.put("iadm01Status", "OK");
        } catch (InstituteAdminException ex) {
            out.put("iadm01Status", "UNAVAILABLE");
            out.put("iadm01Message", "Analytics source is temporarily unavailable");
        }
        try {
            raw04 = sourceClient.fetchIadm04Aggregates(window, tenantId, headers);
            out.put("iadm04Status", "OK");
            if (raw04 != null && raw04.contains("DEMO")) {
                out.put("demoValuesLabel", "DEMO values");
            }
        } catch (InstituteAdminException ex) {
            out.put("iadm04Status", "UNAVAILABLE");
            out.put("iadm04Message", "Analytics source is temporarily unavailable");
        }

        for (int i = 0; i < metricCodes.length; i++) {
            String code = metricCodes[i];
            metrics.put(code, buildMetric(code, raw01, raw04, platformScope, tenantId));
        }
        out.put("metrics", metrics);

        boolean anyData = false;
        for (Object v : metrics.values()) {
            if (v instanceof Map && !"UNAVAILABLE".equals(((Map<?, ?>) v).get("status"))
                    && !"NO_DATA".equals(((Map<?, ?>) v).get("status"))) {
                anyData = true;
                break;
            }
        }
        if (!anyData && "UNAVAILABLE".equals(out.get("iadm01Status"))
                && "UNAVAILABLE".equals(out.get("iadm04Status"))) {
            out.put("emptyState", "Analytics source is temporarily unavailable");
        } else if (!anyData) {
            out.put("emptyState", "No analytics available for this scope");
        }
        return out;
    }

    private Map<String, Object> buildMetric(String code, String raw01, String raw04,
                                            boolean platformScope, String tenantId) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("metricCode", code);
        boolean from01 = code.compareTo("M07") < 0;
        String raw = from01 ? raw01 : raw04;
        if (raw == null) {
            m.put("status", "UNAVAILABLE");
            m.put("message", "Analytics source is temporarily unavailable");
            return m;
        }
        List<Map<String, Object>> groups = extractGroups(code, raw, platformScope, tenantId);
        if (groups.isEmpty()) {
            m.put("status", "NO_DATA");
            m.put("message", "No analytics available for this scope");
            return m;
        }
        if (!from01) {
            m.put("demoValuesLabel", "DEMO values");
        }
        Map<String, Object> suppressed = SuppressionEngine.suppressGroups(groups, platformScope);
        m.put("status", "OK");
        m.putAll(suppressed);
        return m;
    }

    private List<Map<String, Object>> extractGroups(String code, String raw, boolean platformScope,
                                                    String tenantId) {
        List<Map<String, Object>> groups = new ArrayList<Map<String, Object>>();
        // Prefer structured arrays emitted by additive analytics-aggregates endpoints.
        String arrayKey;
        if ("M01".equals(code) || "M04".equals(code)) {
            arrayKey = "M01".equals(code) ? "institutesByStatus" : "collegesByStatus";
        } else if ("M02".equals(code)) {
            Map<String, Object> g = new LinkedHashMap<String, Object>();
            g.put("key", platformScope ? "platform" : "own-institute");
            g.put("count", extractInt(raw, "totalInstitutes", platformScope ? 0 : 1));
            groups.add(g);
            return groups;
        } else if ("M03".equals(code)) {
            arrayKey = "collegesPerInstitute";
        } else if ("M05".equals(code) || "M06".equals(code)) {
            arrayKey = "M05".equals(code) ? "institutesCreated" : "collegesCreated";
        } else if ("M07".equals(code)) {
            arrayKey = "subscriptionsByPlan";
        } else if ("M08".equals(code)) {
            arrayKey = "subscriptionsByState";
        } else if ("M09".equals(code)) {
            arrayKey = "entitlementConsumption";
        } else if ("M10".equals(code)) {
            arrayKey = "subscriptionStateTrend";
        } else {
            arrayKey = code;
        }
        parseObjectArray(raw, arrayKey, groups);
        return groups;
    }

    private void parseObjectArray(String raw, String key, List<Map<String, Object>> groups) {
        String needle = "\"" + key + "\"";
        int i = raw.indexOf(needle);
        if (i < 0) {
            return;
        }
        int lb = raw.indexOf('[', i);
        if (lb < 0) {
            return;
        }
        int depth = 0;
        int end = lb;
        for (int p = lb; p < raw.length(); p++) {
            char c = raw.charAt(p);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    end = p;
                    break;
                }
            }
        }
        String arr = raw.substring(lb + 1, end);
        String[] objs = arr.split("\\},\\s*\\{");
        for (int o = 0; o < objs.length; o++) {
            String obj = objs[o].replace("{", "").replace("}", "");
            if (obj.trim().isEmpty()) {
                continue;
            }
            Map<String, Object> g = new LinkedHashMap<String, Object>();
            String k = extractQuoted(obj, "key");
            if (k == null) {
                k = extractQuoted(obj, "status");
            }
            if (k == null) {
                k = extractQuoted(obj, "planCode");
            }
            if (k == null) {
                k = extractQuoted(obj, "entitlementKey");
            }
            if (k == null) {
                k = extractQuoted(obj, "bucket");
            }
            if (k == null) {
                k = "group" + o;
            }
            g.put("key", k);
            int count = extractInt("{" + obj + "}", "count", extractInt("{" + obj + "}", "used", 0));
            g.put("count", Integer.valueOf(count));
            String limit = extractQuoted(obj, "limit");
            if (limit != null) {
                g.put("limit", limit);
            }
            groups.add(g);
        }
    }

    private static String extractQuoted(String raw, String key) {
        String needle = "\"" + key + "\"";
        int i = raw.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = raw.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int q1 = raw.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = raw.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return raw.substring(q1 + 1, q2);
    }

    private static int extractInt(String raw, String key, int dflt) {
        String needle = "\"" + key + "\"";
        int i = raw.indexOf(needle);
        if (i < 0) {
            return dflt;
        }
        int colon = raw.indexOf(':', i + needle.length());
        if (colon < 0) {
            return dflt;
        }
        int start = colon + 1;
        while (start < raw.length() && !Character.isDigit(raw.charAt(start)) && raw.charAt(start) != '-') {
            start++;
        }
        int end = start;
        while (end < raw.length() && (Character.isDigit(raw.charAt(end)) || raw.charAt(end) == '.')) {
            end++;
        }
        if (end <= start) {
            return dflt;
        }
        try {
            return (int) Double.parseDouble(raw.substring(start, end));
        } catch (Exception e) {
            return dflt;
        }
    }

    private Map<String, Object> loadExport(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, scope_type, scope_tenant_id, status, resolved_policy_code, resolved_policy_version, "
                        + "policy_outcome, created_at, completed_at, audit_correlation_id, error_message, "
                        + "filter_snapshot::text "
                        + "FROM anl.export_requests WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = mapExportRow(rs);
                m.put("policyOutcome", rs.getString("policy_outcome"));
                m.put("errorMessage", rs.getString("error_message"));
                m.put("filterSnapshot", rs.getString("filter_snapshot"));
                m.put("filePayloadStored", Boolean.FALSE);
                return m;
            }
        }
    }

    private Map<String, Object> mapExportRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("scopeType", rs.getString("scope_type"));
        Object st = rs.getObject("scope_tenant_id");
        m.put("scopeTenantId", st == null ? null : st.toString());
        m.put("status", rs.getString("status"));
        m.put("resolvedPolicyCode", rs.getString("resolved_policy_code"));
        m.put("resolvedPolicyVersion", rs.getObject("resolved_policy_version"));
        m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        if (rs.getTimestamp("completed_at") != null) {
            m.put("completedAt", rs.getTimestamp("completed_at").toInstant().toString());
        }
        Object corr = rs.getObject("audit_correlation_id");
        m.put("auditCorrelationId", corr == null ? null : corr.toString());
        return m;
    }

    private UUID resolveOwnScope(Connection conn, UserSecurityContext ctx, String requested)
            throws SQLException {
        if (isSuperAdmin(conn) || isSystemAdmin(conn) || hasPermission(conn, "iadm06.platform.view")) {
            if (requested != null && !requested.trim().isEmpty()) {
                return UUID.fromString(requested.trim());
            }
        }
        if (ctx.getTenantId() == null) {
            throw new InstituteAdminException(403, "IADM06_SCOPE_REQUIRED",
                    "Institute context could not be verified");
        }
        if (requested != null && !requested.trim().isEmpty()) {
            UUID req = UUID.fromString(requested.trim());
            if (!req.equals(ctx.getTenantId()) && !isSuperAdmin(conn) && !isSystemAdmin(conn)
                    && !hasPermission(conn, "iadm06.platform.view")) {
                throw new UserProfileAccessDeniedException(
                        "Requesting another institute's data is refused");
            }
            if (!isSuperAdmin(conn) && !isSystemAdmin(conn) && !hasPermission(conn, "iadm06.platform.view")) {
                return ctx.getTenantId();
            }
            return req;
        }
        return ctx.getTenantId();
    }

    private void requireAny(Connection conn, String... keys) throws SQLException {
        if (isSuperAdmin(conn) || isSystemAdmin(conn)) {
            // R01 Super Admin / R02 System Admin: all implemented keys; manage reserved
            // (iam.has_permission does not always resolve plat.platform_admins SYSTEM_ADMIN)
            for (int i = 0; i < keys.length; i++) {
                if ("iadm06.catalogue.manage".equals(keys[i])) {
                    throw new UserProfileAccessDeniedException("Manage catalogue is reserved and not implemented");
                }
            }
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            if (hasPermission(conn, keys[i])) {
                return;
            }
        }
        throw new UserProfileAccessDeniedException("Missing required IADM-06 permission");
    }

    private void requireSuperOrSystem(Connection conn) throws SQLException {
        if (isSuperAdmin(conn) || isSystemAdmin(conn) || hasPermission(conn, "iadm06.platform.view")) {
            return;
        }
        throw new UserProfileAccessDeniedException("Platform operator required");
    }

    private boolean isSuperAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.platform_admins p "
                        + "WHERE p.user_id = auth.uid() AND p.role_code = 'SUPER_ADMIN' "
                        + "AND p.deleted_at IS NULL LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private boolean isSystemAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.platform_admins p "
                        + "WHERE p.user_id = auth.uid() AND p.role_code = 'SYSTEM_ADMIN' "
                        + "AND p.status = 'ACTIVE' AND p.deleted_at IS NULL LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private boolean hasPermission(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return "";
        }
        return String.valueOf(body.get(key)).trim();
    }

    private static String windowOf(Map<String, Object> body) {
        String w = str(body, "window");
        return w.isEmpty() ? null : w;
    }

    private static String toSimpleJson(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : body.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey().replace("\"", "")).append("\":\"")
                    .append(String.valueOf(e.getValue()).replace("\"", "").replace("\\", ""))
                    .append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendCsvMetrics(StringBuilder csv, Map<String, Object> figures) {
        Object metricsObj = figures.get("metrics");
        if (!(metricsObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) metricsObj;
        String demo = figures.get("demoValuesLabel") == null ? "" : String.valueOf(figures.get("demoValuesLabel"));
        for (Map.Entry<String, Object> e : metrics.entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) e.getValue();
            Object groupsObj = m.get("groups");
            if (groupsObj instanceof List) {
                List<?> groups = (List<?>) groupsObj;
                for (int i = 0; i < groups.size(); i++) {
                    if (!(groups.get(i) instanceof Map)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> g = (Map<String, Object>) groups.get(i);
                    csv.append(e.getKey()).append(',')
                            .append(g.get("key")).append(',')
                            .append(g.get("count")).append(',')
                            .append(demo.replace(",", " ")).append('\n');
                }
            }
            Object withheld = m.get("withheldGroups");
            if (withheld instanceof List) {
                List<?> w = (List<?>) withheld;
                for (int i = 0; i < w.size(); i++) {
                    if (!(w.get(i) instanceof Map)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> g = (Map<String, Object>) w.get(i);
                    csv.append(e.getKey()).append(',')
                            .append(g.get("key")).append(',')
                            .append("WITHHELD").append(',')
                            .append(String.valueOf(g.get("reason")).replace(",", " ")).append('\n');
                }
            }
        }
    }
}
