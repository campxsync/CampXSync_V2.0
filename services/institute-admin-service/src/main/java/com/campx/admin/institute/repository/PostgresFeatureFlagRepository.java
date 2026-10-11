package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.InstituteModels.FeatureFlag;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC repository for {@code cfg.feature_flags} table (ADM-01 Item 5).
 * Follows RLS kernel enforcement: read is open, insert/update requires platform admin.
 */
public class PostgresFeatureFlagRepository implements FeatureFlagRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresFeatureFlagRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresFeatureFlagRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresFeatureFlagRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public FeatureFlag saveFlag(UserSecurityContext context, FeatureFlag flag) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (flag == null || flag.getFlagKey() == null || flag.getFlagKey().trim().isEmpty()) {
            throw new MalformedPayloadException("Feature flag and flagKey cannot be null");
        }

        String flagKey = flag.getFlagKey().trim();
        String name = flag.getFlagKey();
        String desc = flag.getDescription();
        boolean enabled = !"DISABLED".equalsIgnoreCase(flag.getStatus());
        short rollout = 100;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO cfg.feature_flags ("
                    + "id, flag_key, name, description, enabled, rollout_percentage, created_at, updated_at, row_version"
                    + ") VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, now(), now(), 1) "
                    + "ON CONFLICT (flag_key) WHERE deleted_at IS NULL DO UPDATE SET "
                    + "name = EXCLUDED.name, description = EXCLUDED.description, enabled = EXCLUDED.enabled, "
                    + "rollout_percentage = EXCLUDED.rollout_percentage, updated_at = now(), row_version = cfg.feature_flags.row_version + 1 "
                    + "RETURNING id, flag_key, name, description, enabled, rollout_percentage, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, flagKey);
                ps.setString(2, name);
                ps.setString(3, desc);
                ps.setBoolean(4, enabled);
                ps.setShort(5, rollout);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        flag.setId(rs.getString("id"));
                        flag.setFlagKey(rs.getString("flag_key"));
                        flag.setDescription(rs.getString("description"));
                        flag.setStatus(rs.getBoolean("enabled") ? "ACTIVE" : "DISABLED");
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) flag.setCreatedAt(cat.getTime());
                        logger.info("Persisted feature flag [{}] in cfg.feature_flags", flagKey);
                        return flag;
                    }
                    throw new SQLException("Save into cfg.feature_flags returned no rows");
                }
            }
        });
    }

    @Override
    public Optional<FeatureFlag> getFlagByKey(UserSecurityContext context, String flagKey) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (flagKey == null || flagKey.trim().isEmpty()) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, flag_key, name, description, enabled, rollout_percentage, created_at, updated_at, row_version "
                    + "FROM cfg.feature_flags WHERE flag_key = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, flagKey.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<FeatureFlag> listFlags(UserSecurityContext context) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<FeatureFlag> list = new ArrayList<>();
            String sql = "SELECT id, flag_key, name, description, enabled, rollout_percentage, created_at, updated_at, row_version "
                    + "FROM cfg.feature_flags WHERE deleted_at IS NULL ORDER BY flag_key ASC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public void deleteFlag(UserSecurityContext context, String flagKey) {
        if (context == null || flagKey == null || flagKey.trim().isEmpty()) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM cfg.feature_flags WHERE flag_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, flagKey.trim());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private FeatureFlag mapRow(ResultSet rs) throws SQLException {
        FeatureFlag flag = new FeatureFlag();
        flag.setId(rs.getString("id"));
        flag.setFlagKey(rs.getString("flag_key"));
        flag.setDescription(rs.getString("description"));
        flag.setStatus(rs.getBoolean("enabled") ? "ACTIVE" : "DISABLED");
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) flag.setCreatedAt(cat.getTime());
        return flag;
    }
}
