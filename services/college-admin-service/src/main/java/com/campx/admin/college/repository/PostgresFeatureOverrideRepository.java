package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.FeatureOverride;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code cfg.feature_overrides}.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresFeatureOverrideRepository implements FeatureOverrideRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresFeatureOverrideRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresFeatureOverrideRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresFeatureOverrideRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public FeatureOverride overrideFeatureFlag(UserSecurityContext context, FeatureOverride override) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (override == null) {
            throw new CollegeMalformedPayloadException("Feature override payload cannot be null");
        }
        if (override.getFlagKey() == null || override.getFlagKey().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'flagKey' is required");
        }

        String flagKey = override.getFlagKey().trim();
        boolean enabled = "true".equalsIgnoreCase(override.getOverrideValue()) ||
                "ENABLED".equalsIgnoreCase(override.getOverrideValue()) ||
                "1".equals(override.getOverrideValue());

        return context.executeInTransaction(connectionManager, conn -> {
            UUID flagId = resolveOrCreateFeatureFlag(conn, flagKey);
            UUID collegeId = CollegeResolver.resolveCollegeId(conn, context.getTenantId());

            UUID overrideId = null;
            int currentVersion = 1;

            String selectSql = "SELECT id, row_version FROM cfg.feature_overrides " +
                    "WHERE tenant_id = ? AND flag_id = ? AND college_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setObject(1, context.getTenantId());
                ps.setObject(2, flagId);
                ps.setObject(3, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        overrideId = (UUID) rs.getObject("id");
                        currentVersion = rs.getInt("row_version");
                    }
                }
            }

            if (overrideId != null) {
                // Update
                int nextVersion = currentVersion + 1;
                String updateSql = "UPDATE cfg.feature_overrides SET enabled = ?, row_version = ?, updated_at = now() " +
                        "WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setBoolean(1, enabled);
                    ps.setInt(2, nextVersion);
                    ps.setObject(3, overrideId);
                    ps.setObject(4, context.getTenantId());
                    ps.executeUpdate();
                }
                override.setId(overrideId.toString());
            } else {
                // Insert
                overrideId = (override.getId() != null && !override.getId().trim().isEmpty())
                        ? UUID.fromString(override.getId().trim()) : UUID.randomUUID();
                String insertSql = "INSERT INTO cfg.feature_overrides (id, tenant_id, flag_id, college_id, enabled, created_at, updated_at, row_version) " +
                        "VALUES (?, ?, ?, ?, ?, now(), now(), 1)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, overrideId);
                    ps.setObject(2, context.getTenantId());
                    ps.setObject(3, flagId);
                    ps.setObject(4, collegeId);
                    ps.setBoolean(5, enabled);
                    ps.executeUpdate();
                }
                override.setId(overrideId.toString());
            }

            if (override.getEffectiveFrom() == 0) {
                override.setEffectiveFrom(System.currentTimeMillis());
            }

            logger.info("Persisted feature override [{}] in cfg.feature_overrides", flagKey);
            return override;
        });
    }

    @Override
    public List<FeatureOverride> listFeatureOverrides(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<FeatureOverride> list = new ArrayList<>();
            String sql = "SELECT fo.id, ff.flag_key, fo.enabled, fo.row_version " +
                    "FROM cfg.feature_overrides fo " +
                    "JOIN cfg.feature_flags ff ON fo.flag_id = ff.id " +
                    "WHERE fo.tenant_id = ? AND fo.deleted_at IS NULL ORDER BY ff.flag_key ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        FeatureOverride fo = new FeatureOverride();
                        fo.setId(rs.getString("id"));
                        fo.setFlagKey(rs.getString("flag_key"));
                        fo.setOverrideValue(rs.getBoolean("enabled") ? "true" : "false");
                        list.add(fo);
                    }
                }
            }
            return list;
        });
    }

    @Override
    public Optional<FeatureOverride> findByFlagKey(UserSecurityContext context, String flagKey) {
        if (context == null || context.getTenantId() == null || flagKey == null) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT fo.id, ff.flag_key, fo.enabled, fo.row_version " +
                    "FROM cfg.feature_overrides fo " +
                    "JOIN cfg.feature_flags ff ON fo.flag_id = ff.id " +
                    "WHERE fo.tenant_id = ? AND ff.flag_key = ? AND fo.deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                ps.setString(2, flagKey.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        FeatureOverride fo = new FeatureOverride();
                        fo.setId(rs.getString("id"));
                        fo.setFlagKey(rs.getString("flag_key"));
                        fo.setOverrideValue(rs.getBoolean("enabled") ? "true" : "false");
                        return Optional.of(fo);
                    }
                }
            }
            return Optional.empty();
        });
    }

    private UUID resolveOrCreateFeatureFlag(Connection conn, String flagKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM cfg.feature_flags WHERE flag_key = ? AND deleted_at IS NULL")) {
            ps.setString(1, flagKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
        }
        UUID flagId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO cfg.feature_flags (id, flag_key, name, enabled, created_at, updated_at, row_version) " +
                        "VALUES (?, ?, ?, true, now(), now(), 1)")) {
            ps.setObject(1, flagId);
            ps.setString(2, flagKey);
            ps.setString(3, flagKey);
            ps.executeUpdate();
        }
        return flagId;
    }
}

