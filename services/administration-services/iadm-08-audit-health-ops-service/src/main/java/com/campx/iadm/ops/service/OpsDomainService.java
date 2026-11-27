package com.campx.iadm.ops.service;

import com.campx.iadm.ops.client.HttpJsonClient;
import com.campx.iadm.ops.client.Iadm05PolicyClient;
import com.campx.iadm.ops.config.DatabaseConnectionManager;
import com.campx.iadm.ops.exception.InstituteAdminException;
import com.campx.iadm.ops.exception.UserProfileAccessDeniedException;
import com.campx.iadm.ops.security.OpsPermissionMatrix;
import com.campx.iadm.ops.security.UserSecurityContext;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-08 Audit, Health & Operations Oversight domain service.
 * Plain language: read shared audit and on-request health; record every read; store nothing.
 */
public class OpsDomainService {

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();
    private final Iadm05PolicyClient policyClient;
    private final HttpJsonClient iadm07Client;

    public OpsDomainService() {
        this(System.getenv("IADM05_BASE_URL") != null
                        ? System.getenv("IADM05_BASE_URL")
                        : "http://127.0.0.1:8103/api/v1/iadm/05",
                System.getenv("IADM07_BASE_URL") != null
                        ? System.getenv("IADM07_BASE_URL")
                        : "http://127.0.0.1:8105/api/v1/iadm/07");
    }

    public OpsDomainService(String iadm05Base, String iadm07Base) {
        this.policyClient = new Iadm05PolicyClient(iadm05Base);
        this.iadm07Client = new HttpJsonClient(iadm07Base, 2000, 5000);
    }

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "UP");
        out.put("service", "iadm-08-audit-health-ops-service");
        out.put("port", Integer.valueOf(8106));
        out.put("prefix", "/api/v1/iadm/08");
        out.put("maxWindowDays", Integer.valueOf(OpsQueryLimits.getMaxWindowDays()));
        out.put("pageSize", Integer.valueOf(OpsQueryLimits.getPageSize()));
        out.put("persistence", "none");
        return out;
    }

    public Map<String, Object> limits() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("maxWindowDays", Integer.valueOf(OpsQueryLimits.getMaxWindowDays()));
        out.put("pageSize", Integer.valueOf(OpsQueryLimits.getPageSize()));
        out.put("demoOnlyDefaults", Boolean.TRUE);
        out.put("safeFields", new String[]{
                "eventId", "eventTime", "actor", "action", "target", "outcome", "correlationId", "institute"
        });
        return out;
    }

    public Map<String, Object> setLimits(UserSecurityContext ctx, Map<String, Object> body) {
        // DEMO ONLY runtime adjustment for headed demos — not a settings platform
        requirePlatformOperator(ctx);
        if (body != null && body.get("maxWindowDays") != null) {
            OpsQueryLimits.setMaxWindowDays(Integer.parseInt(String.valueOf(body.get("maxWindowDays"))));
        }
        if (body != null && body.get("pageSize") != null) {
            OpsQueryLimits.setPageSize(Integer.parseInt(String.valueOf(body.get("pageSize"))));
        }
        return limits();
    }

    public Map<String, Object> searchAudit(UserSecurityContext ctx, Map<String, String> headers,
                                           String fromIso, String toIso, String actionLike,
                                           String tenantFilter, String denialOnly, String page) {
        final Instant[] range = parseMandatoryRange(fromIso, toIso);
        final int limit = OpsQueryLimits.getPageSize();
        final int offset = Math.max(0, parseInt(page, 0) * limit);
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_AUDIT_PLATFORM, OpsPermissionMatrix.KEY_AUDIT_INSTITUTE);
                Map<String, Object> policy = gatePolicy(headers);
                UUID scopeTenant = resolveAuditScope(conn, ctx, tenantFilter);
                boolean instituteOnly = !isPlatformWideAudit(conn);
                if (instituteOnly) {
                    if (ctx.getTenantId() == null) {
                        throw new InstituteAdminException(403, "IADM08_SCOPE_REQUIRED",
                                "Institute scope could not be verified.");
                    }
                    scopeTenant = ctx.getTenantId();
                }
                String filter = "from=" + range[0] + ";to=" + range[1]
                        + ";actionLike=" + nullToEmpty(actionLike)
                        + ";tenant=" + (scopeTenant == null ? "platform" : scopeTenant)
                        + ";denialOnly=" + nullToEmpty(denialOnly)
                        + ";page=" + offset / limit;
                DurableAuditWriter.append(conn, ctx, scopeTenant != null ? scopeTenant : ctx.getTenantId(),
                        "IADM08_AUDIT_SEARCH", "audit_search", null, filter);
                elevateAuditReadIfPlatform(conn);

                StringBuilder sql = new StringBuilder(
                        "SELECT id, event_time, principal_id, action, resource_type, resource_id, "
                                + "status, correlation_id, tenant_id "
                                + "FROM audit.events WHERE event_time >= ? AND event_time < ? ");
                List<Object> params = new ArrayList<Object>();
                params.add(Timestamp.from(range[0]));
                params.add(Timestamp.from(range[1]));
                if (scopeTenant != null) {
                    sql.append("AND tenant_id = ? ");
                    params.add(scopeTenant);
                } else if (instituteOnly) {
                    sql.append("AND tenant_id = ? ");
                    params.add(ctx.getTenantId());
                }
                // R03: hide unattributed events (tenant_id IS NULL)
                if (instituteOnly) {
                    sql.append("AND tenant_id IS NOT NULL ");
                }
                if (actionLike != null && !actionLike.trim().isEmpty()) {
                    sql.append("AND action ILIKE ? ");
                    params.add("%" + actionLike.trim() + "%");
                }
                if ("true".equalsIgnoreCase(denialOnly)) {
                    sql.append("AND (action ILIKE '%DENY%' OR action ILIKE '%DENIED%') ");
                }
                sql.append("ORDER BY event_time DESC LIMIT ? OFFSET ?");
                params.add(Integer.valueOf(limit));
                params.add(Integer.valueOf(offset));

                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapSafeEvent(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("pageSize", Integer.valueOf(limit));
                out.put("page", Integer.valueOf(offset / limit));
                out.put("maxWindowDays", Integer.valueOf(OpsQueryLimits.getMaxWindowDays()));
                out.put("policy", policyBrief(policy));
                out.put("scope", scopeTenant == null ? "platform" : scopeTenant.toString());
                return out;
            }
        });
    }

    public Map<String, Object> auditDetail(UserSecurityContext ctx, Map<String, String> headers, String eventId) {
        final UUID id = parseUuid(eventId, "eventId");
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_AUDIT_PLATFORM, OpsPermissionMatrix.KEY_AUDIT_INSTITUTE);
                Map<String, Object> policy = gatePolicy(headers);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM08_AUDIT_DETAIL", "audit_event", id.toString(), "eventId=" + id);
                elevateAuditReadIfPlatform(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, event_time, principal_id, action, resource_type, resource_id, "
                                + "status, correlation_id, tenant_id FROM audit.events WHERE id = ?")) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InstituteAdminException(404, "IADM08_NOT_FOUND", "Audit event not found");
                        }
                        Map<String, Object> row = mapSafeEvent(rs);
                        enforceRowScope(conn, ctx, row);
                        row.put("policy", policyBrief(policy));
                        return row;
                    }
                }
            }
        });
    }

    public Map<String, Object> activityTimeline(UserSecurityContext ctx, Map<String, String> headers,
                                                String actorId, String fromIso, String toIso) {
        final UUID actor = parseUuid(actorId, "actorId");
        final Instant[] range = parseMandatoryRange(fromIso, toIso);
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_AUDIT_PLATFORM, OpsPermissionMatrix.KEY_AUDIT_INSTITUTE);
                Map<String, Object> policy = gatePolicy(headers);
                boolean instituteOnly = !isPlatformWideAudit(conn);
                String filter = "actor=" + actor + ";from=" + range[0] + ";to=" + range[1];
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM08_AUDIT_TIMELINE", "audit_timeline", actor.toString(), filter);
                elevateAuditReadIfPlatform(conn);
                String sql = "SELECT id, event_time, principal_id, action, resource_type, resource_id, "
                        + "status, correlation_id, tenant_id FROM audit.events "
                        + "WHERE principal_id = ? AND event_time >= ? AND event_time < ? ";
                if (instituteOnly) {
                    sql += "AND tenant_id = ? AND tenant_id IS NOT NULL ";
                }
                sql += "ORDER BY event_time DESC LIMIT ?";
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int i = 1;
                    ps.setObject(i++, actor);
                    ps.setTimestamp(i++, Timestamp.from(range[0]));
                    ps.setTimestamp(i++, Timestamp.from(range[1]));
                    if (instituteOnly) {
                        ps.setObject(i++, ctx.getTenantId());
                    }
                    ps.setInt(i, OpsQueryLimits.getPageSize());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapSafeEvent(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("actorId", actor.toString());
                out.put("items", items);
                out.put("policy", policyBrief(policy));
                return out;
            }
        });
    }

    public Map<String, Object> healthOverview(UserSecurityContext ctx, Map<String, String> headers) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_HEALTH);
                gatePolicy(headers);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM08_HEALTH_OVERVIEW", "health_overview", null,
                        "targets=" + HealthTargetInventory.all().size());
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                for (Map<String, String> t : HealthTargetInventory.all()) {
                    items.add(probeHealth(t));
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("inventorySource", HealthTargetInventory.SOURCE_LABEL);
                out.put("items", items);
                out.put("stored", Boolean.FALSE);
                return out;
            }
        });
    }

    public Map<String, Object> healthDetail(UserSecurityContext ctx, Map<String, String> headers, String targetId) {
        final Map<String, String> target = HealthTargetInventory.find(targetId);
        if (target == null) {
            throw new InstituteAdminException(404, "IADM08_NOT_FOUND",
                    "Unknown health target; client-supplied addresses are never called.");
        }
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_HEALTH);
                gatePolicy(headers);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM08_HEALTH_DETAIL", "health_detail", target.get("id"),
                        "target=" + target.get("id"));
                Map<String, Object> row = probeHealth(target);
                row.put("stored", Boolean.FALSE);
                return row;
            }
        });
    }

    public Map<String, Object> alerts(UserSecurityContext ctx, Map<String, String> headers, String tenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_ALERTS);
                gatePolicy(headers);
                // R03 always binds to trusted claims institute; only R01/R02/R28 may select.
                boolean canSelect = canSelectAlertsInstitute(conn);
                String scopedTenant = OpsAlertsScope.resolve(canSelect, ctx.getTenantId(), tenantId);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM08_ALERTS_READ", "alerts", null,
                        "tenant=" + nullToEmpty(scopedTenant));
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("acknowledgeSupported", Boolean.FALSE);
                out.put("localAlertState", Boolean.FALSE);
                out.put("scopedTenantId", scopedTenant);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                if (!OpsOutageFlags.isIadm07Disabled()) {
                    try {
                        Map<String, String> fwd = new LinkedHashMap<String, String>();
                        if (headers != null) {
                            fwd.putAll(headers);
                        }
                        String path = "/alerts";
                        if (scopedTenant != null && !scopedTenant.isEmpty()) {
                            path = "/alerts?tenantId=" + url(scopedTenant);
                        }
                        String raw = iadm07Client.get(path, fwd);
                        items.add(refItem("iadm07-alerts", "IADM-07 security alerts (read-only)", raw));
                        out.put("iadm07AlertsIncluded", Boolean.TRUE);
                    } catch (Exception ex) {
                        out.put("iadm07AlertsIncluded", Boolean.FALSE);
                        out.put("iadm07AlertsError", "IADM-07 alerts unavailable; omitted");
                    }
                } else {
                    out.put("iadm07AlertsIncluded", Boolean.FALSE);
                    out.put("iadm07AlertsError", "DEMO ONLY outage: IADM-07 disabled");
                }
                out.put("items", items);
                out.put("operationalNote", "Operational alerts are read-only; no acknowledge or escalation in V1.");
                return out;
            }
        });
    }

    public Map<String, Object> iadm07EvidenceLink(UserSecurityContext ctx, Map<String, String> headers, String kind) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_ALERTS, OpsPermissionMatrix.KEY_AUDIT_PLATFORM);
                gatePolicy(headers);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                        "IADM08_ALERTS_READ", "iadm07_link", kind, "kind=" + nullToEmpty(kind));
                String path;
                if ("findings".equalsIgnoreCase(kind)) {
                    path = "/findings";
                } else if ("risks".equalsIgnoreCase(kind)) {
                    path = "/risks";
                } else {
                    path = "/alerts";
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("owner", "IADM-07");
                out.put("kind", kind == null ? "alerts" : kind);
                out.put("mutation", Boolean.FALSE);
                try {
                    String raw = iadm07Client.get(path, headers);
                    out.put("available", Boolean.TRUE);
                    out.put("preview", raw == null ? "" : (raw.length() > 500 ? raw.substring(0, 500) : raw));
                } catch (Exception ex) {
                    out.put("available", Boolean.FALSE);
                    out.put("detail", "IADM-07 evidence unavailable");
                }
                return out;
            }
        });
    }

    public Map<String, Object> slaPlaceholder(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_HEALTH, OpsPermissionMatrix.KEY_AUDIT_PLATFORM);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("sla", "not available");
                out.put("uptime", "not available");
                out.put("message", "SLA measurements are not available.");
                return out;
            }
        });
    }

    public Map<String, Object> outageFlags(UserSecurityContext ctx, Map<String, Object> body) {
        requirePlatformOperator(ctx);
        if (body != null) {
            if (body.containsKey("auditDisabled")) {
                OpsOutageFlags.setAuditDisabled(Boolean.parseBoolean(String.valueOf(body.get("auditDisabled"))));
            }
            if (body.containsKey("healthDisabled")) {
                OpsOutageFlags.setHealthDisabled(Boolean.parseBoolean(String.valueOf(body.get("healthDisabled"))));
            }
            if (body.containsKey("iadm05Disabled")) {
                OpsOutageFlags.setIadm05Disabled(Boolean.parseBoolean(String.valueOf(body.get("iadm05Disabled"))));
            }
            if (body.containsKey("iadm07Disabled")) {
                OpsOutageFlags.setIadm07Disabled(Boolean.parseBoolean(String.valueOf(body.get("iadm07Disabled"))));
            }
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("demoOnly", Boolean.TRUE);
        out.put("auditDisabled", Boolean.valueOf(OpsOutageFlags.isAuditDisabled()));
        out.put("healthDisabled", Boolean.valueOf(OpsOutageFlags.isHealthDisabled()));
        out.put("iadm05Disabled", Boolean.valueOf(OpsOutageFlags.isIadm05Disabled()));
        out.put("iadm07Disabled", Boolean.valueOf(OpsOutageFlags.isIadm07Disabled()));
        return out;
    }

    public Map<String, Object> healthInventory(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireAny(conn, OpsPermissionMatrix.KEY_HEALTH);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("inventorySource", HealthTargetInventory.SOURCE_LABEL);
                out.put("targets", HealthTargetInventory.all());
                out.put("clientSuppliedAddressesAccepted", Boolean.FALSE);
                return out;
            }
        });
    }

    // ---- helpers ----

    private Map<String, Object> probeHealth(Map<String, String> target) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", target.get("id"));
        row.put("name", target.get("name"));
        row.put("inventorySource", target.get("inventorySource"));
        if (OpsOutageFlags.isHealthDisabled()) {
            row.put("status", "Unavailable");
            row.put("detail", "DEMO ONLY health source disabled");
            row.put("httpStatus", Integer.valueOf(0));
            return row;
        }
        String url = target.get("url");
        long started = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            String body = readBody(conn, code);
            long elapsed = System.currentTimeMillis() - started;
            row.put("httpStatus", Integer.valueOf(code));
            row.put("latencyMs", Long.valueOf(elapsed));
            if (code >= 200 && code < 300) {
                String lower = body == null ? "" : body.toLowerCase();
                if (lower.contains("degrad")) {
                    row.put("status", "Degraded");
                } else {
                    row.put("status", "Healthy");
                }
            } else {
                row.put("status", "Unavailable");
            }
            // Stale only if source supplies its own timestamp — look for common fields
            String srcTs = extractJsonField(body, "timestamp");
            if (srcTs == null) {
                srcTs = extractJsonField(body, "checkedAt");
            }
            if (srcTs != null) {
                row.put("sourceTimestamp", srcTs);
                row.put("statusNote", "Source timestamp present; Stale evaluation left to UI if age exceeds threshold");
            }
            row.put("bodyPreview", body == null ? "" : (body.length() > 200 ? body.substring(0, 200) : body));
        } catch (java.net.SocketTimeoutException te) {
            row.put("status", "Unknown");
            row.put("detail", "timeout or no answer");
            row.put("httpStatus", Integer.valueOf(0));
        } catch (Exception ex) {
            row.put("status", "Unavailable");
            row.put("detail", "error or refused");
            row.put("httpStatus", Integer.valueOf(0));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        // Never present Unavailable/Unknown as Healthy
        Object st = row.get("status");
        if (st == null || "0".equals(String.valueOf(st))) {
            row.put("status", "Unknown");
        }
        return row;
    }

    private static String readBody(HttpURLConnection conn, int code) {
        try {
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                return "";
            }
            byte[] buf = new byte[4096];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractJsonField(String raw, String key) {
        if (raw == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int i = raw.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = raw.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
            start++;
        }
        if (start >= raw.length()) {
            return null;
        }
        if (raw.charAt(start) == '"') {
            int end = raw.indexOf('"', start + 1);
            return end < 0 ? null : raw.substring(start + 1, end);
        }
        return null;
    }

    private Map<String, Object> gatePolicy(Map<String, String> headers) {
        return policyClient.resolveOperationsPolicy(headers);
    }

    private static Map<String, Object> policyBrief(Map<String, Object> policy) {
        Map<String, Object> brief = new LinkedHashMap<String, Object>();
        if (policy == null) {
            return brief;
        }
        if (policy.get("code") != null) {
            brief.put("code", policy.get("code"));
        }
        if (policy.get("versionNo") != null) {
            brief.put("versionNo", policy.get("versionNo"));
        }
        if (policy.get("policyId") != null) {
            brief.put("policyId", policy.get("policyId"));
        }
        return brief;
    }

    /**
     * Public API contract entry for time-window validation (Fix Part A).
     * Over-max windows are rejected with IADM08_RANGE_TOO_LARGE (never truncated).
     */
    public Instant[] validateMandatoryRange(String fromIso, String toIso) {
        return parseMandatoryRange(fromIso, toIso);
    }

    private Instant[] parseMandatoryRange(String fromIso, String toIso) {
        if (fromIso == null || fromIso.trim().isEmpty() || toIso == null || toIso.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM08_RANGE_REQUIRED",
                    "A time range is mandatory. Narrow the date range or filters.");
        }
        Instant from;
        Instant to;
        try {
            from = Instant.parse(fromIso.trim());
            to = Instant.parse(toIso.trim());
        } catch (Exception e) {
            throw new InstituteAdminException(400, "IADM08_RANGE_INVALID",
                    "Time range must be ISO-8601 instants.");
        }
        if (!to.isAfter(from)) {
            throw new InstituteAdminException(400, "IADM08_RANGE_INVALID",
                    "End time must be after start time.");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > OpsQueryLimits.getMaxWindowDays()) {
            throw new InstituteAdminException(400, "IADM08_RANGE_TOO_LARGE",
                    "Narrow the date range or filters. Maximum window is "
                            + OpsQueryLimits.getMaxWindowDays() + " days.");
        }
        return new Instant[]{from, to};
    }

    private UUID resolveAuditScope(Connection conn, UserSecurityContext ctx, String tenantFilter)
            throws SQLException, InstituteAdminException {
        if (tenantFilter == null || tenantFilter.trim().isEmpty()) {
            return null;
        }
        UUID requested = parseUuid(tenantFilter, "tenantId");
        if (!isPlatformWideAudit(conn)) {
            if (ctx.getTenantId() == null || !ctx.getTenantId().equals(requested)) {
                throw new InstituteAdminException(403, "IADM08_SCOPE_DENIED",
                        "Institute scope could not be verified.");
            }
        }
        return requested;
    }

    private void enforceRowScope(Connection conn, UserSecurityContext ctx, Map<String, Object> row)
            throws SQLException, InstituteAdminException {
        if (isPlatformWideAudit(conn)) {
            return;
        }
        Object institute = row.get("institute");
        if (institute == null || institute.toString().isEmpty()) {
            throw new InstituteAdminException(403, "IADM08_SCOPE_DENIED",
                    "Unattributed audit events are not shown for institute scope.");
        }
        if (ctx.getTenantId() == null || !ctx.getTenantId().toString().equals(institute.toString())) {
            throw new InstituteAdminException(403, "IADM08_SCOPE_DENIED",
                    "Institute scope could not be verified.");
        }
    }

    private static Map<String, Object> mapSafeEvent(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("eventId", rs.getObject("id") == null ? null : rs.getObject("id").toString());
        Timestamp ts = rs.getTimestamp("event_time");
        m.put("eventTime", ts == null ? null : ts.toInstant().toString());
        m.put("actor", rs.getObject("principal_id") == null ? null : rs.getObject("principal_id").toString());
        m.put("action", rs.getString("action"));
        String rt = rs.getString("resource_type");
        Object rid = rs.getObject("resource_id");
        m.put("target", (rt == null ? "" : rt) + (rid == null ? "" : (":" + rid)));
        m.put("outcome", rs.getString("status"));
        m.put("correlationId", rs.getString("correlation_id"));
        m.put("institute", rs.getObject("tenant_id") == null ? null : rs.getObject("tenant_id").toString());
        return m;
    }

    private void requireAny(Connection conn, String... keys) throws SQLException, InstituteAdminException {
        if (isSuperAdmin(conn) || isSystemAdmin(conn)) {
            return;
        }
        for (String k : keys) {
            if (hasPermission(conn, k)) {
                return;
            }
        }
        throw new UserProfileAccessDeniedException("You are not authorized for this view.");
    }

    private boolean isPlatformWideAudit(Connection conn) throws SQLException {
        return isSuperAdmin(conn) || isSystemAdmin(conn)
                || hasPermission(conn, OpsPermissionMatrix.KEY_AUDIT_PLATFORM);
    }

    /** R01 / R02 / R28 may select an institute on alerts; R03 may not. */
    private boolean canSelectAlertsInstitute(Connection conn) throws SQLException {
        return isPlatformWideAudit(conn);
    }

    /**
     * Platform operators need temporary role reset to read cross-tenant shared audit
     * (same pattern as IADM-07 listAuditEvidence). Institute-scoped callers stay under RLS.
     */
    private void elevateAuditReadIfPlatform(Connection conn) throws SQLException {
        // R01/R02 platform admins and R28 platform-wide auditors need cross-tenant audit visibility.
        if (isPlatformWideAudit(conn)) {
            try (Statement elevate = conn.createStatement()) {
                elevate.execute("RESET ROLE");
            }
        }
    }

    private boolean isSuperAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.platform_admins p WHERE p.user_id = auth.uid() "
                        + "AND p.role_code = 'SUPER_ADMIN' AND p.deleted_at IS NULL LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private boolean isSystemAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.platform_admins p WHERE p.user_id = auth.uid() "
                        + "AND p.role_code = 'SYSTEM_ADMIN' AND p.status = 'ACTIVE' AND p.deleted_at IS NULL LIMIT 1");
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

    private void requirePlatformOperator(UserSecurityContext ctx) {
        if (ctx == null || ctx.getUserId() == null) {
            throw new UserProfileAccessDeniedException("You are not authorized for this view.");
        }
    }

    private static Map<String, Object> refItem(String id, String title, String raw) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("title", title);
        m.put("source", "IADM-07");
        m.put("preview", raw == null ? "" : (raw.length() > 400 ? raw.substring(0, 400) : raw));
        return m;
    }

    private static UUID parseUuid(String raw, String field) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM08_BAD_REQUEST", field + " is required");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new InstituteAdminException(400, "IADM08_BAD_REQUEST", field + " must be a UUID");
        }
    }

    private static int parseInt(String raw, int dflt) {
        if (raw == null || raw.trim().isEmpty()) {
            return dflt;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String url(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
