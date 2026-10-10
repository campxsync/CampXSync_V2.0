package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.InstituteModels.GlobalSetting;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code cfg.setting_values} table (ADM-01 Item 4).
 * Operates under the authenticated security context with Row-Level Security kernel enforcement.
 */
public class PostgresGlobalSettingRepository implements GlobalSettingRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresGlobalSettingRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresGlobalSettingRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresGlobalSettingRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public GlobalSetting saveSetting(UserSecurityContext context, GlobalSetting setting) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (setting == null || setting.getKey() == null || setting.getKey().trim().isEmpty()) {
            throw new MalformedPayloadException("Setting and setting key cannot be null");
        }

        UUID tenantId = context.getTenantId();
        String key = setting.getKey().trim();
        String scope = "INSTITUTE";
        String val = setting.getValue() != null ? setting.getValue() : "";
        String jsonVal = "\"" + val.replace("\"", "\\\"") + "\"";

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO cfg.setting_values ("
                    + "id, tenant_id, setting_key, scope, value, is_overridden"
                    + ") VALUES (gen_random_uuid(), ?, ?, ?, ?::jsonb, false) "
                    + "ON CONFLICT (tenant_id, setting_key) WHERE ((deleted_at IS NULL) AND (college_id IS NULL)) DO UPDATE SET "
                    + "value = EXCLUDED.value, updated_at = now(), row_version = cfg.setting_values.row_version + 1 "
                    + "RETURNING setting_key, scope, value::text";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setString(2, key);
                ps.setString(3, scope);
                ps.setString(4, jsonVal);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return setting;
                    }
                    throw new SQLException("Save into cfg.setting_values returned no rows");
                }
            }
        });
    }

    @Override
    public Optional<GlobalSetting> getSettingByKey(UserSecurityContext context, String key) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (key == null || key.trim().isEmpty()) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT setting_key, scope, value::text "
                    + "FROM cfg.setting_values "
                    + "WHERE setting_key = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, key.trim());
                ps.setObject(2, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        GlobalSetting setting = new GlobalSetting();
                        setting.setKey(rs.getString("setting_key"));
                        setting.setScope(rs.getString("scope"));
                        String rawVal = rs.getString("value");
                        if (rawVal != null && rawVal.startsWith("\"") && rawVal.endsWith("\"") && rawVal.length() >= 2) {
                            rawVal = rawVal.substring(1, rawVal.length() - 1);
                        }
                        setting.setValue(rawVal);
                        return Optional.of(setting);
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<GlobalSetting> listSettings(UserSecurityContext context) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT setting_key, scope, value::text "
                    + "FROM cfg.setting_values "
                    + "WHERE tenant_id = ? AND deleted_at IS NULL "
                    + "ORDER BY setting_key ASC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    List<GlobalSetting> list = new ArrayList<>();
                    while (rs.next()) {
                        GlobalSetting setting = new GlobalSetting();
                        setting.setKey(rs.getString("setting_key"));
                        setting.setScope(rs.getString("scope"));
                        String rawVal = rs.getString("value");
                        if (rawVal != null && rawVal.startsWith("\"") && rawVal.endsWith("\"") && rawVal.length() >= 2) {
                            rawVal = rawVal.substring(1, rawVal.length() - 1);
                        }
                        setting.setValue(rawVal);
                        list.add(setting);
                    }
                    return list;
                }
            }
        });
    }
}
