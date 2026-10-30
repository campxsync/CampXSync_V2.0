package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.InstituteModels.GlobalPolicy;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC repository for {@code cfg.policies} table (ADM-01 Item 6).
 * Follows RLS kernel enforcement: read is open, insert/update requires platform admin.
 */
public class PostgresGlobalPolicyRepository implements GlobalPolicyRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresGlobalPolicyRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresGlobalPolicyRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresGlobalPolicyRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public GlobalPolicy savePolicy(UserSecurityContext context, GlobalPolicy policy) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (policy == null || policy.getPolicyCode() == null || policy.getPolicyCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Policy and policyCode cannot be null");
        }

        String code = policy.getPolicyCode().trim();
        String name = policy.getPolicyCode();
        String category = policy.getPolicyType() != null ? policy.getPolicyType() : "SECURITY";
        String enforcementMode = "ENFORCE";
        String rulesJson = "{\"rules\":[]}";
        if (policy.getRules() != null && !policy.getRules().isEmpty()) {
            StringBuilder sb = new StringBuilder("{\"rules\":[");
            for (int i = 0; i < policy.getRules().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(policy.getRules().get(i).replace("\"", "\\\"")).append("\"");
            }
            sb.append("]}");
            rulesJson = sb.toString();
        }

        String finalRules = rulesJson;
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO cfg.policies ("
                    + "id, policy_code, name, category, rules, enforcement_mode, created_at, updated_at, row_version"
                    + ") VALUES (gen_random_uuid(), ?, ?, ?, ?::jsonb, ?, now(), now(), 1) "
                    + "ON CONFLICT (policy_code) WHERE deleted_at IS NULL DO UPDATE SET "
                    + "name = EXCLUDED.name, category = EXCLUDED.category, rules = EXCLUDED.rules, "
                    + "enforcement_mode = EXCLUDED.enforcement_mode, updated_at = now(), row_version = cfg.policies.row_version + 1 "
                    + "RETURNING id, policy_code, name, category, rules::text, enforcement_mode, created_at, updated_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setString(2, name);
                ps.setString(3, category);
                ps.setString(4, finalRules);
                ps.setString(5, enforcementMode);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        policy.setId(rs.getString("id"));
                        policy.setPolicyCode(rs.getString("policy_code"));
                        policy.setPolicyType(rs.getString("category"));
                        policy.setStatus("ACTIVE");
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) policy.setCreatedAt(cat.getTime());
                        logger.info("Persisted policy [{}] in cfg.policies", code);
                        return policy;
                    }
                    throw new SQLException("Save into cfg.policies returned no rows");
                }
            }
        });
    }

    @Override
    public Optional<GlobalPolicy> getPolicyByCode(UserSecurityContext context, String policyCode) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (policyCode == null || policyCode.trim().isEmpty()) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, policy_code, name, category, rules::text, enforcement_mode, created_at, updated_at, row_version "
                    + "FROM cfg.policies WHERE policy_code = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, policyCode.trim());
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
    public List<GlobalPolicy> listPolicies(UserSecurityContext context) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<GlobalPolicy> list = new ArrayList<>();
            String sql = "SELECT id, policy_code, name, category, rules::text, enforcement_mode, created_at, updated_at, row_version "
                    + "FROM cfg.policies WHERE deleted_at IS NULL ORDER BY policy_code ASC";

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
    public void deletePolicy(UserSecurityContext context, String policyCode) {
        if (context == null || policyCode == null || policyCode.trim().isEmpty()) {
            return;
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM cfg.policies WHERE policy_code = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, policyCode.trim());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private GlobalPolicy mapRow(ResultSet rs) throws SQLException {
        GlobalPolicy p = new GlobalPolicy();
        p.setId(rs.getString("id"));
        p.setPolicyCode(rs.getString("policy_code"));
        p.setPolicyType(rs.getString("category"));
        p.setStatus(rs.getString("enforcement_mode"));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) p.setCreatedAt(cat.getTime());
        return p;
    }
}
