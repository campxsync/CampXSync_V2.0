package com.campx.iadm.config.service;

import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-02 Platform Configuration &amp; Policy.
 * <p>
 * Keys: adm05.manage / adm05.write / adm05.view. Mutations emit structured audit events via
 * {@link CampXLogger} (Agreement &#167;3.2).
 */
public class PlatformConfigService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PlatformConfigService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> upsertDefinition(UserSecurityContext ctx, String key, String dataType,
                                                String defaultValueJson, boolean platformLocked, boolean delegable,
                                                String[] allowedScopes) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "adm05.manage");
            } catch (RuntimeException denied) {
                ctx.checkPlatformAdmin(conn);
            }
            if (allowedScopes == null || allowedScopes.length == 0) {
                throw new InstituteAdminException(400, "IADM02_AMBIGUOUS_SCOPE",
                        "allowed_scopes required; missing scope fails closed");
            }
            String scopesLiteral = "{" + String.join(",", allowedScopes) + "}";
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cfg.setting_definitions (setting_key, data_type, default_value, platform_locked, delegable_to_college, allowed_scopes, created_by) " +
                            "VALUES (?, ?, ?::jsonb, ?, ?, ?::text[], ?) " +
                            "ON CONFLICT (setting_key) WHERE deleted_at IS NULL DO UPDATE SET " +
                            "data_type = EXCLUDED.data_type, default_value = EXCLUDED.default_value, " +
                            "platform_locked = EXCLUDED.platform_locked, delegable_to_college = EXCLUDED.delegable_to_college, " +
                            "allowed_scopes = EXCLUDED.allowed_scopes, updated_at = now(), updated_by = EXCLUDED.created_by " +
                            "RETURNING id")) {
                // unique index is partial — use upsert via delete-check if ON CONFLICT unsupported on partial
                // Fallback path: try update first
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE cfg.setting_definitions SET data_type = ?, default_value = ?::jsonb, platform_locked = ?, " +
                            "delegable_to_college = ?, allowed_scopes = ?::text[], updated_at = now(), updated_by = ? " +
                            "WHERE setting_key = ? AND deleted_at IS NULL")) {
                upd.setString(1, dataType);
                upd.setString(2, defaultValueJson == null ? "null" : defaultValueJson);
                upd.setBoolean(3, platformLocked);
                upd.setBoolean(4, delegable);
                upd.setString(5, scopesLiteral);
                upd.setObject(6, ctx.getUserId());
                upd.setString(7, key);
                int n = upd.executeUpdate();
                if (n == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO cfg.setting_definitions (setting_key, data_type, default_value, platform_locked, delegable_to_college, allowed_scopes, created_by) " +
                                    "VALUES (?, ?, ?::jsonb, ?, ?, ?::text[], ?)")) {
                        ins.setString(1, key);
                        ins.setString(2, dataType);
                        ins.setString(3, defaultValueJson == null ? "null" : defaultValueJson);
                        ins.setBoolean(4, platformLocked);
                        ins.setBoolean(5, delegable);
                        ins.setString(6, scopesLiteral);
                        ins.setObject(7, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("settingKey", key);
            out.put("platformLocked", platformLocked);
            out.put("delegableToCollege", delegable);
            auditMutation(ctx, "upsertDefinition", "SETTING_DEFINITION", key);
            return out;
        });
    }

    public Map<String, Object> setInstituteValue(UserSecurityContext ctx, String key, String valueJson) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.write");
            // Reject non-allow-listed / locked; validate JSON type vs catalogue data_type
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT platform_locked, allowed_scopes, data_type FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
                chk.setString(1, key);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM02_NOT_FOUND", "Setting not in catalogue");
                    if (rs.getBoolean("platform_locked")) {
                        throw new InstituteAdminException(403, "IADM02_LOCKED", "Platform-locked setting cannot be overridden");
                    }
                    String scopes = rs.getString("allowed_scopes");
                    if (scopes == null || !scopes.contains("INSTITUTE")) {
                        throw new InstituteAdminException(403, "IADM02_NOT_ALLOWLISTED", "Setting not allow-listed for institute");
                    }
                    validateValueJson(rs.getString("data_type"), valueJson);
                }
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE cfg.setting_values SET value = ?::jsonb, updated_at = now(), updated_by = ? " +
                            "WHERE tenant_id = ? AND setting_key = ? AND scope = 'INSTITUTE' AND college_id IS NULL AND deleted_at IS NULL")) {
                upd.setString(1, valueJson);
                upd.setObject(2, ctx.getUserId());
                upd.setObject(3, ctx.getTenantId());
                upd.setString(4, key);
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO cfg.setting_values (tenant_id, setting_key, scope, value, created_by) VALUES (?, ?, 'INSTITUTE', ?::jsonb, ?)")) {
                        ins.setObject(1, ctx.getTenantId());
                        ins.setString(2, key);
                        ins.setString(3, valueJson);
                        ins.setObject(4, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("settingKey", key);
            out.put("scope", "INSTITUTE");
            out.put("value", valueJson);
            auditMutation(ctx, "setInstituteValue", "SETTING_VALUE", key);
            return out;
        });
    }

    public Map<String, Object> delegateSetting(UserSecurityContext ctx, String key, UUID collegeId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.write");
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT delegable_to_college, platform_locked FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
                chk.setString(1, key);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM02_NOT_FOUND", "Setting not in catalogue");
                    if (rs.getBoolean("platform_locked")) {
                        throw new InstituteAdminException(403, "IADM02_LOCKED", "Cannot delegate a locked setting");
                    }
                    if (!rs.getBoolean("delegable_to_college")) {
                        throw new InstituteAdminException(403, "IADM02_NOT_DELEGABLE", "Setting is not flagged delegable_to_college");
                    }
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO cfg.setting_delegations (id, tenant_id, college_id, setting_key, delegated_by, active, created_by) " +
                            "VALUES (?, ?, ?, ?, ?, true, ?)")) {
                ins.setObject(1, id);
                ins.setObject(2, ctx.getTenantId());
                ins.setObject(3, collegeId);
                ins.setString(4, key);
                ins.setObject(5, ctx.getUserId());
                ins.setObject(6, ctx.getUserId());
                ins.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("delegationId", id.toString());
            out.put("settingKey", key);
            out.put("collegeId", collegeId.toString());
            out.put("active", true);
            auditMutation(ctx, "delegateSetting", "SETTING_DELEGATION", id.toString());
            return out;
        });
    }

    public Map<String, Object> resolveEffective(UserSecurityContext ctx, UUID tenantId, UUID collegeId, String key) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.view");
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT setting_key, effective_value::text, source, platform_locked, delegated, denied, deny_reason " +
                            "FROM cfg.resolve_effective_setting(?, ?, ?)")) {
                ps.setObject(1, tid);
                ps.setObject(2, collegeId);
                ps.setString(3, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM02_RESOLVE", "No resolution row");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("settingKey", rs.getString("setting_key"));
                    out.put("effectiveValue", rs.getString("effective_value"));
                    out.put("source", rs.getString("source"));
                    out.put("platformLocked", rs.getBoolean("platform_locked"));
                    out.put("delegated", rs.getBoolean("delegated"));
                    out.put("denied", rs.getBoolean("denied"));
                    out.put("denyReason", rs.getString("deny_reason"));
                    return out;
                }
            }
        });
    }

    public Map<String, Object> listDefinitions(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT setting_key, data_type, platform_locked, delegable_to_college, allowed_scopes " +
                            "FROM cfg.setting_definitions WHERE deleted_at IS NULL ORDER BY setting_key LIMIT 500")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("settingKey", rs.getString("setting_key"));
                        row.put("dataType", rs.getString("data_type"));
                        row.put("platformLocked", rs.getBoolean("platform_locked"));
                        row.put("delegableToCollege", rs.getBoolean("delegable_to_college"));
                        Array scopes = rs.getArray("allowed_scopes");
                        row.put("allowedScopes", scopes == null
                                ? Collections.emptyList()
                                : Arrays.asList((String[]) scopes.getArray()));
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

    public Map<String, Object> setCollegeValue(UserSecurityContext ctx, String key, UUID collegeId, String valueJson) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.write");
            if (collegeId == null) {
                throw new InstituteAdminException(400, "IADM02_COLLEGE_REQUIRED", "collegeId required");
            }
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT platform_locked, delegable_to_college, allowed_scopes, data_type " +
                            "FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
                chk.setString(1, key);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM02_NOT_FOUND", "Setting not in catalogue");
                    if (rs.getBoolean("platform_locked")) {
                        throw new InstituteAdminException(403, "IADM02_LOCKED", "Platform-locked setting cannot be overridden");
                    }
                    if (!rs.getBoolean("delegable_to_college")) {
                        throw new InstituteAdminException(403, "IADM02_NOT_DELEGABLE", "Setting is not flagged delegable_to_college");
                    }
                    String scopes = rs.getString("allowed_scopes");
                    if (scopes == null || !scopes.contains("COLLEGE")) {
                        throw new InstituteAdminException(403, "IADM02_NOT_ALLOWLISTED", "Setting not allow-listed for college");
                    }
                    validateValueJson(rs.getString("data_type"), valueJson);
                }
            }
            try (PreparedStatement del = conn.prepareStatement(
                    "SELECT 1 FROM cfg.setting_delegations WHERE tenant_id = ? AND college_id = ? AND setting_key = ? " +
                            "AND active = true AND deleted_at IS NULL LIMIT 1")) {
                del.setObject(1, ctx.getTenantId());
                del.setObject(2, collegeId);
                del.setString(3, key);
                try (ResultSet rs = del.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(403, "IADM02_NOT_DELEGATED",
                                "College value requires an active setting delegation");
                    }
                }
            }
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE cfg.setting_values SET value = ?::jsonb, updated_at = now(), updated_by = ? " +
                            "WHERE tenant_id = ? AND setting_key = ? AND scope = 'COLLEGE' AND college_id = ? AND deleted_at IS NULL")) {
                upd.setString(1, valueJson);
                upd.setObject(2, ctx.getUserId());
                upd.setObject(3, ctx.getTenantId());
                upd.setString(4, key);
                upd.setObject(5, collegeId);
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO cfg.setting_values (tenant_id, setting_key, scope, college_id, value, created_by) " +
                                    "VALUES (?, ?, 'COLLEGE', ?, ?::jsonb, ?)")) {
                        ins.setObject(1, ctx.getTenantId());
                        ins.setString(2, key);
                        ins.setObject(3, collegeId);
                        ins.setString(4, valueJson);
                        ins.setObject(5, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("settingKey", key);
            out.put("scope", "COLLEGE");
            out.put("collegeId", collegeId.toString());
            auditMutation(ctx, "setCollegeValue", "SETTING_VALUE", key);
            return out;
        });
    }

    /**
     * Prefer {@code cfg.setting_value_versions}; fall back to {@code cfg.v_setting_history}.
     */
    public Map<String, Object> settingHistory(UserSecurityContext ctx, String key) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.view");
            List<Map<String, Object>> items = loadVersionHistory(conn, ctx.getTenantId(), key);
            String source = "cfg.setting_value_versions";
            if (items.isEmpty()) {
                items = loadViewHistory(conn, ctx.getTenantId(), key);
                source = "cfg.v_setting_history";
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("settingKey", key);
            out.put("source", source);
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    /**
     * Soft-rollback: restore {@code cfg.setting_values.value} from a prior version row.
     * Trigger on setting_values appends a new version row.
     */
    public Map<String, Object> rollbackSetting(UserSecurityContext ctx, String key, String versionId) {
        return ctx.executeInTransaction(db, conn -> {
            requireConfigWrite(ctx, conn);
            if (versionId == null || versionId.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM02_VERSION_REQUIRED", "versionId required");
            }
            UUID verId;
            try {
                verId = UUID.fromString(versionId.trim());
            } catch (IllegalArgumentException ex) {
                throw new InstituteAdminException(400, "IADM02_VERSION_INVALID", "versionId must be a UUID");
            }
            String scope;
            UUID collegeId;
            String priorValue;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT scope, college_id, value::text FROM cfg.setting_value_versions " +
                            "WHERE id = ? AND tenant_id = ? AND setting_key = ?")) {
                ps.setObject(1, verId);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM02_VERSION_NOT_FOUND",
                                "Setting version not found for tenant/key");
                    }
                    scope = rs.getString(1);
                    collegeId = (UUID) rs.getObject(2);
                    priorValue = rs.getString(3);
                }
            }
            int updated;
            if (collegeId == null) {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE cfg.setting_values SET value = ?::jsonb, updated_at = now(), updated_by = ? " +
                                "WHERE tenant_id = ? AND setting_key = ? AND scope = ? AND college_id IS NULL AND deleted_at IS NULL")) {
                    upd.setString(1, priorValue);
                    upd.setObject(2, ctx.getUserId());
                    upd.setObject(3, ctx.getTenantId());
                    upd.setString(4, key);
                    upd.setString(5, scope);
                    updated = upd.executeUpdate();
                }
            } else {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE cfg.setting_values SET value = ?::jsonb, updated_at = now(), updated_by = ? " +
                                "WHERE tenant_id = ? AND setting_key = ? AND scope = ? AND college_id = ? AND deleted_at IS NULL")) {
                    upd.setString(1, priorValue);
                    upd.setObject(2, ctx.getUserId());
                    upd.setObject(3, ctx.getTenantId());
                    upd.setString(4, key);
                    upd.setString(5, scope);
                    upd.setObject(6, collegeId);
                    updated = upd.executeUpdate();
                }
            }
            if (updated == 0) {
                throw new InstituteAdminException(404, "IADM02_VALUE_NOT_FOUND",
                        "Current setting value row not found for rollback target");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("settingKey", key);
            out.put("rolledBackToVersionId", verId.toString());
            out.put("scope", scope);
            out.put("collegeId", collegeId == null ? null : collegeId.toString());
            out.put("value", priorValue);
            auditMutation(ctx, "rollbackSetting", "SETTING_VALUE", key);
            return out;
        });
    }

    public Map<String, Object> listDelegations(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, setting_key, college_id::text, active, delegated_by::text, delegated_at::text " +
                            "FROM cfg.setting_delegations WHERE tenant_id = ? AND deleted_at IS NULL " +
                            "ORDER BY delegated_at DESC NULLS LAST LIMIT 200")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("delegationId", rs.getString(1));
                        row.put("settingKey", rs.getString(2));
                        row.put("collegeId", rs.getString(3));
                        row.put("active", rs.getBoolean(4));
                        row.put("delegatedBy", rs.getString(5));
                        row.put("delegatedAt", rs.getString(6));
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

    public Map<String, Object> revokeDelegation(UserSecurityContext ctx, UUID delegationId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.write");
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE cfg.setting_delegations SET active = false, updated_at = now(), updated_by = ?, deleted_at = now() " +
                            "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL AND active = true")) {
                ps.setObject(1, ctx.getUserId());
                ps.setObject(2, delegationId);
                ps.setObject(3, ctx.getTenantId());
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM02_DELEGATION_NOT_FOUND", "Active setting delegation not found");
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("delegationId", delegationId.toString());
            out.put("active", false);
            out.put("status", "REVOKED");
            auditMutation(ctx, "revokeDelegation", "SETTING_DELEGATION", delegationId.toString());
            return out;
        });
    }

    static void validateValueJson(String dataType, String valueJson) {
        if (valueJson == null || valueJson.trim().isEmpty() || "null".equalsIgnoreCase(valueJson.trim())) {
            throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH", "value required and must match data_type");
        }
        String v = valueJson.trim();
        String dt = dataType == null ? "STRING" : dataType.trim().toUpperCase();
        switch (dt) {
            case "BOOLEAN":
                if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                    throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH",
                            "BOOLEAN setting requires true/false JSON literal");
                }
                break;
            case "NUMBER":
                if (v.startsWith("\"")) {
                    throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH",
                            "NUMBER setting requires a JSON number, not a string");
                }
                try {
                    Double.parseDouble(v);
                } catch (NumberFormatException ex) {
                    throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH",
                            "NUMBER setting requires a JSON number");
                }
                break;
            case "STRING":
                if (v.length() < 2 || !v.startsWith("\"") || !v.endsWith("\"")) {
                    throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH",
                            "STRING setting requires a JSON string");
                }
                break;
            case "JSON":
                if (!(v.startsWith("{") || v.startsWith("["))) {
                    throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH",
                            "JSON setting requires a JSON object or array");
                }
                break;
            default:
                throw new InstituteAdminException(400, "IADM02_TYPE_MISMATCH",
                        "Unsupported data_type: " + dt);
        }
    }

    private void requireConfigWrite(UserSecurityContext ctx, Connection conn) throws java.sql.SQLException {
        try {
            ctx.checkPermission(conn, "adm05.write");
        } catch (RuntimeException writeDenied) {
            try {
                ctx.checkPermission(conn, "adm05.manage");
            } catch (RuntimeException manageDenied) {
                ctx.checkPlatformAdmin(conn);
            }
        }
    }

    private static List<Map<String, Object>> loadVersionHistory(Connection conn, UUID tenantId, String key)
            throws java.sql.SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id::text, version_no, value::text, scope, college_id::text, changed_by::text, created_at::text " +
                        "FROM cfg.setting_value_versions WHERE tenant_id = ? AND setting_key = ? " +
                        "ORDER BY created_at DESC, version_no DESC LIMIT 100")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("versionId", rs.getString(1));
                    row.put("versionNo", rs.getInt(2));
                    row.put("value", rs.getString(3));
                    row.put("scope", rs.getString(4));
                    row.put("collegeId", rs.getString(5));
                    row.put("changedBy", rs.getString(6));
                    row.put("createdAt", rs.getString(7));
                    items.add(row);
                }
            }
        }
        return items;
    }

    private static List<Map<String, Object>> loadViewHistory(Connection conn, UUID tenantId, String key)
            throws java.sql.SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT record_id::text, action, old_data::text, new_data::text, actor_id::text, created_at::text " +
                        "FROM cfg.v_setting_history WHERE tenant_id = ? " +
                        "AND (COALESCE(new_data->>'setting_key', '') = ? OR COALESCE(old_data->>'setting_key', '') = ?) " +
                        "ORDER BY created_at DESC NULLS LAST LIMIT 100")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            ps.setString(3, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("versionId", rs.getString(1));
                    row.put("action", rs.getString(2));
                    row.put("oldData", rs.getString(3));
                    row.put("newData", rs.getString(4));
                    row.put("actorId", rs.getString(5));
                    row.put("createdAt", rs.getString(6));
                    items.add(row);
                }
            }
        } catch (Exception ignored) {
            // view may be unavailable — return empty
        }
        return items;
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-02 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}
