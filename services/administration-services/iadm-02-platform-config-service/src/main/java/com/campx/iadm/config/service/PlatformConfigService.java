package com.campx.iadm.config.service;

import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.Array;
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
            ctx.checkPermission(conn, "adm05.manage");
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
            // Reject non-allow-listed / locked
            try (PreparedStatement chk = conn.prepareStatement(
                    "SELECT platform_locked, allowed_scopes FROM cfg.setting_definitions WHERE setting_key = ? AND deleted_at IS NULL")) {
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
                    "SELECT platform_locked, delegable_to_college, allowed_scopes " +
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
     * History from {@code cfg.v_setting_history} (audit.change_log). No {@code cfg.setting_value_versions} table in DEV.
     */
    public Map<String, Object> settingHistory(UserSecurityContext ctx, String key) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT record_id::text, action, old_data::text, new_data::text, actor_id::text, created_at::text " +
                            "FROM cfg.v_setting_history WHERE tenant_id = ? " +
                            "AND (COALESCE(new_data->>'setting_key', '') = ? OR COALESCE(old_data->>'setting_key', '') = ?) " +
                            "ORDER BY created_at DESC NULLS LAST LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
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
            } catch (Exception ex) {
                // Fallback: current setting_values rows for this key (updated snapshots)
                items.clear();
                try (PreparedStatement fb = conn.prepareStatement(
                        "SELECT id::text, scope, college_id::text, value::text, updated_at::text, updated_by::text " +
                                "FROM cfg.setting_values WHERE tenant_id = ? AND setting_key = ? AND deleted_at IS NULL " +
                                "ORDER BY updated_at DESC NULLS LAST LIMIT 100")) {
                    fb.setObject(1, ctx.getTenantId());
                    fb.setString(2, key);
                    try (ResultSet rs = fb.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("versionId", rs.getString(1));
                            row.put("scope", rs.getString(2));
                            row.put("collegeId", rs.getString(3));
                            row.put("value", rs.getString(4));
                            row.put("updatedAt", rs.getString(5));
                            row.put("updatedBy", rs.getString(6));
                            items.add(row);
                        }
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("settingKey", key);
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    /** Soft rollback unavailable — {@code cfg.setting_value_versions} does not exist in DEV migrations. */
    public Map<String, Object> rollbackSetting(UserSecurityContext ctx, String key, String versionId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm05.write");
            throw new InstituteAdminException(501, "IADM02_ROLLBACK_UNAVAILABLE",
                    "cfg.setting_value_versions not present; soft rollback unavailable");
        });
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
