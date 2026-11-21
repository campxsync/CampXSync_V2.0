package com.campx.iadm.securitygov.service;

import com.campx.iadm.securitygov.config.DatabaseConnectionManager;
import com.campx.iadm.securitygov.exception.InstituteAdminException;
import com.campx.iadm.securitygov.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain service for IADM-07 Security &amp; Compliance Governance (policies and findings).
 * <p>
 * Permission-key checks, tenant-scoped writes, and {@link CampXLogger} audit events per
 * Agreement §3.2. Never log tokens, passwords, or JWTs.
 */
public class Iadm07DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm07DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        logger.debug("IADM-07 health probe tenantId={}", ctx.getTenantId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-07");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> upsertPolicy(UserSecurityContext ctx, String key, String valueJson) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.write");
            if (key == null || key.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM07_POLICY_KEY", "policyKey required");
            }
            String value = valueJson == null || valueJson.trim().isEmpty() ? "{}" : valueJson.trim();
            enforcePlatformMinimum(conn, key.trim(), value);
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.institute_security_policies SET policy_value=?::jsonb, updated_at=now(), updated_by=? " +
                            "WHERE tenant_id=? AND policy_key=? AND deleted_at IS NULL")) {
                upd.setString(1, value);
                upd.setObject(2, ctx.getUserId());
                upd.setObject(3, ctx.getTenantId());
                upd.setString(4, key.trim());
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO plat.institute_security_policies (tenant_id, policy_key, policy_value, created_by) VALUES (?,?,?::jsonb,?)")) {
                        ins.setObject(1, ctx.getTenantId());
                        ins.setString(2, key.trim());
                        ins.setString(3, value);
                        ins.setObject(4, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("policyKey", key.trim());
            auditMutation(ctx, "iadm07.policy.upsert", "SECURITY_POLICY", key.trim());
            return out;
        });
    }

    /**
     * Rejects institute policy values below {@code plat.platform_security_minimums}.
     * Simple rules: {@code required:true} requires listed keys present; {@code minScore} numeric compare.
     */
    private void enforcePlatformMinimum(java.sql.Connection conn, String policyKey, String valueJson)
            throws java.sql.SQLException {
        String minJson = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT minimum_value::text FROM plat.platform_security_minimums " +
                        "WHERE policy_key=? AND deleted_at IS NULL LIMIT 1")) {
            ps.setString(1, policyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) minJson = rs.getString(1);
            }
        }
        if (minJson == null || minJson.trim().isEmpty()) return;

        boolean required = jsonBool(minJson, "required");
        if (required) {
            List<String> requiredKeys = jsonStringArray(minJson, "requiredKeys");
            if (requiredKeys.isEmpty()) {
                // keys in minimum besides meta must appear in institute value
                for (String k : jsonTopKeys(minJson)) {
                    if ("required".equals(k) || "minScore".equals(k) || "requiredKeys".equals(k)
                            || "description".equals(k)) continue;
                    requiredKeys.add(k);
                }
            }
            for (String rk : requiredKeys) {
                if (!jsonHasKey(valueJson, rk)) {
                    throw new InstituteAdminException(400, "IADM07_BELOW_MINIMUM",
                            "Policy missing required key: " + rk);
                }
            }
        }
        Double minScore = jsonNumber(minJson, "minScore");
        if (minScore != null) {
            Double actual = jsonNumber(valueJson, "minScore");
            if (actual == null || actual < minScore) {
                throw new InstituteAdminException(400, "IADM07_BELOW_MINIMUM",
                        "Policy minScore below platform minimum " + minScore);
            }
        }
        String minLevel = jsonString(minJson, "level");
        String actLevel = jsonString(valueJson, "level");
        if (minLevel != null && actLevel != null && !minLevel.equalsIgnoreCase(actLevel)
                && !levelMeetsOrExceeds(actLevel, minLevel)) {
            throw new InstituteAdminException(400, "IADM07_BELOW_MINIMUM",
                    "Policy level below platform minimum " + minLevel);
        }
    }

    private static boolean levelMeetsOrExceeds(String actual, String minimum) {
        List<String> order = Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL");
        int ai = order.indexOf(actual.toUpperCase(Locale.ROOT));
        int mi = order.indexOf(minimum.toUpperCase(Locale.ROOT));
        if (ai < 0 || mi < 0) return actual.equalsIgnoreCase(minimum);
        return ai >= mi;
    }

    private static boolean jsonBool(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        return m.find() && "true".equalsIgnoreCase(m.group(1));
    }

    private static Double jsonNumber(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private static String jsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static boolean jsonHasKey(String json, String key) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:").matcher(json).find();
    }

    private static List<String> jsonTopKeys(String json) {
        List<String> keys = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) keys.add(m.group(1));
        return keys;
    }

    private static List<String> jsonStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = p.matcher(json);
        if (!m.find()) return out;
        Matcher items = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (items.find()) out.add(items.group(1));
        return out;
    }

    public Map<String, Object> createFinding(UserSecurityContext ctx, String title, String severity) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.write");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.security_findings (id, tenant_id, title, severity, status, created_by) " +
                            "VALUES (?,?,?,?, 'OPEN', ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, title);
                ps.setString(4, severity == null ? "MEDIUM" : severity);
                ps.setObject(5, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("findingId", id.toString());
            out.put("status", "OPEN");
            auditMutation(ctx, "iadm07.finding.create", "SECURITY_FINDING", id.toString());
            return out;
        });
    }

    public Map<String, Object> listPolicies(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, policy_key, policy_value::text, updated_at::text " +
                            "FROM plat.institute_security_policies WHERE tenant_id=? AND deleted_at IS NULL " +
                            "ORDER BY policy_key LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("policyKey", rs.getString(2));
                        row.put("policyValue", rs.getString(3));
                        row.put("updatedAt", rs.getString(4));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> listFindings(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, title, severity, status, owner_user_id::text, created_at::text " +
                            "FROM plat.security_findings WHERE tenant_id=? AND deleted_at IS NULL " +
                            "ORDER BY created_at DESC NULLS LAST LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("title", rs.getString(2));
                        row.put("severity", rs.getString(3));
                        row.put("status", rs.getString(4));
                        row.put("ownerUserId", rs.getString(5));
                        row.put("createdAt", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> posture(UserSecurityContext ctx) {
        return postureInternal(ctx, null);
    }

    /** College-scoped finding counts for R04 ({@code college_id} on findings). */
    public Map<String, Object> postureByCollege(UserSecurityContext ctx, UUID collegeId) {
        if (collegeId == null) {
            throw new InstituteAdminException(400, "IADM07_COLLEGE_REQUIRED", "collegeId required");
        }
        return postureInternal(ctx, collegeId);
    }

    private Map<String, Object> postureInternal(UserSecurityContext ctx, UUID collegeId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm07.view");
            Map<String, Object> openBySeverity = new LinkedHashMap<>();
            openBySeverity.put("LOW", 0);
            openBySeverity.put("MEDIUM", 0);
            openBySeverity.put("HIGH", 0);
            openBySeverity.put("CRITICAL", 0);
            String sql = collegeId == null
                    ? "SELECT severity, count(*) FROM plat.security_findings " +
                    "WHERE tenant_id=? AND deleted_at IS NULL AND status='OPEN' GROUP BY severity"
                    : "SELECT severity, count(*) FROM plat.security_findings " +
                    "WHERE tenant_id=? AND college_id=? AND deleted_at IS NULL AND status='OPEN' GROUP BY severity";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, ctx.getTenantId());
                if (collegeId != null) ps.setObject(2, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        openBySeverity.put(rs.getString(1), rs.getInt(2));
                    }
                }
            }
            int policyCount = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM plat.institute_security_policies WHERE tenant_id=? AND deleted_at IS NULL")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) policyCount = rs.getInt(1);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("openFindingsBySeverity", openBySeverity);
            out.put("policyCount", policyCount);
            if (collegeId != null) out.put("collegeId", collegeId.toString());
            return out;
        });
    }

    public Map<String, Object> platformMinimums(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPlatformAdmin(conn);
            } catch (Exception e) {
                ctx.checkPermission(conn, "iadm07.view");
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, policy_key, minimum_value::text, description " +
                            "FROM plat.platform_security_minimums " +
                            "WHERE deleted_at IS NULL ORDER BY policy_key LIMIT 100")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("policyKey", rs.getString(2));
                        row.put("minimumValue", rs.getString(3));
                        row.put("description", rs.getString(4));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-07 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}