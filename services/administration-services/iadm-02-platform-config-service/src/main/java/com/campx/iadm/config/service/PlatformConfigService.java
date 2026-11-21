package com.campx.iadm.config.service;

import com.campx.iadm.config.config.DatabaseConnectionManager;
import com.campx.iadm.config.exception.InstituteAdminException;
import com.campx.iadm.config.security.UserSecurityContext;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-02 Platform Configuration & Policy.
 * Keys: adm05.manage / adm05.write / adm05.view.
 */
public class PlatformConfigService {
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
}
