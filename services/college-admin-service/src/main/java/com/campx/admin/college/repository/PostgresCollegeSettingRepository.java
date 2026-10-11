package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.CollegeSetting;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code cfg.setting_values}.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresCollegeSettingRepository implements CollegeSettingRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresCollegeSettingRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DatabaseConnectionManager connectionManager;

    public PostgresCollegeSettingRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresCollegeSettingRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public CollegeSetting storeSetting(UserSecurityContext context, CollegeSetting setting) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (setting == null) {
            throw new CollegeMalformedPayloadException("Setting payload cannot be null");
        }
        if (setting.getKey() == null || setting.getKey().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'key' is required");
        }

        String key = setting.getKey().trim();

        return context.executeInTransaction(connectionManager, conn -> {
            UUID collegeId = CollegeResolver.resolveCollegeId(conn, context.getTenantId());

            UUID settingId = null;
            int currentVersion = 1;

            String selectSql = "SELECT id, row_version FROM cfg.setting_values " +
                    "WHERE tenant_id = ? AND setting_key = ? AND college_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, key);
                ps.setObject(3, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        settingId = (UUID) rs.getObject("id");
                        currentVersion = rs.getInt("row_version");
                    }
                }
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("value", setting.getValue() != null ? setting.getValue() : "");
            map.put("secret", setting.isSecret());
            map.put("dataType", setting.getDataType() != null ? setting.getDataType() : "STRING");
            String jsonValue;
            try {
                jsonValue = objectMapper.writeValueAsString(map);
            } catch (Exception e) {
                throw new CollegeMalformedPayloadException("Failed to serialize setting JSON: " + e.getMessage());
            }

            if (settingId != null) {
                // Update
                int nextVersion = currentVersion + 1;
                String updateSql = "UPDATE cfg.setting_values SET value = ?::jsonb, is_overridden = true, " +
                        "row_version = ?, updated_at = now() WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, jsonValue);
                    ps.setInt(2, nextVersion);
                    ps.setObject(3, settingId);
                    ps.setObject(4, context.getTenantId());
                    ps.executeUpdate();
                }
                setting.setId(settingId.toString());
                setting.setVersion(nextVersion);
            } else {
                // Insert
                settingId = (setting.getId() != null && !setting.getId().trim().isEmpty())
                        ? UUID.fromString(setting.getId().trim()) : UUID.randomUUID();
                String insertSql = "INSERT INTO cfg.setting_values (id, tenant_id, setting_key, scope, college_id, " +
                        "value, is_overridden, created_at, updated_at, row_version) " +
                        "VALUES (?, ?, ?, 'COLLEGE', ?, ?::jsonb, true, now(), now(), 1)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, settingId);
                    ps.setObject(2, context.getTenantId());
                    ps.setString(3, key);
                    ps.setObject(4, collegeId);
                    ps.setString(5, jsonValue);
                    ps.executeUpdate();
                }
                setting.setId(settingId.toString());
                setting.setVersion(1);
            }

            if (setting.getEffectiveFrom() == 0) {
                setting.setEffectiveFrom(System.currentTimeMillis());
            }

            logger.info("Persisted college setting [{}] in cfg.setting_values (version={})", key, setting.getVersion());
            return setting;
        });
    }

    @Override
    public List<CollegeSetting> getCollegeSettings(UserSecurityContext context, boolean effectiveOnly) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<CollegeSetting> settings = new ArrayList<>();
            String sql = "SELECT id, setting_key, value ->> 'value' AS val_str, value ->> 'dataType' AS data_type, " +
                    "(value ->> 'secret')::boolean AS is_sec, row_version FROM cfg.setting_values " +
                    "WHERE tenant_id = ? AND scope = 'COLLEGE' AND deleted_at IS NULL ORDER BY setting_key ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        CollegeSetting s = new CollegeSetting();
                        s.setId(rs.getString("id"));
                        s.setKey(rs.getString("setting_key"));
                        s.setVersion(rs.getInt("row_version"));

                        boolean isSecret = Boolean.TRUE.equals(rs.getObject("is_sec"));
                        s.setSecret(isSecret);
                        String val = rs.getString("val_str");
                        s.setValue(isSecret ? "********" : (val != null ? val : ""));
                        s.setDataType(rs.getString("data_type"));
                        settings.add(s);
                    }
                }
            }
            return settings;
        });
    }

    @Override
    public Optional<CollegeSetting> findByKey(UserSecurityContext context, String key) {
        if (context == null || context.getTenantId() == null || key == null) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, setting_key, value ->> 'value' AS val_str, value ->> 'dataType' AS data_type, " +
                    "(value ->> 'secret')::boolean AS is_sec, row_version FROM cfg.setting_values " +
                    "WHERE tenant_id = ? AND setting_key = ? AND scope = 'COLLEGE' AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, key.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        CollegeSetting s = new CollegeSetting();
                        s.setId(rs.getString("id"));
                        s.setKey(rs.getString("setting_key"));
                        s.setVersion(rs.getInt("row_version"));

                        boolean isSecret = Boolean.TRUE.equals(rs.getObject("is_sec"));
                        s.setSecret(isSecret);
                        String val = rs.getString("val_str");
                        s.setValue(isSecret ? "********" : (val != null ? val : ""));
                        s.setDataType(rs.getString("data_type"));
                        return Optional.of(s);
                    }
                }
            }
            return Optional.empty();
        });
    }
}

