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
        // IADM-02 cutover: platform policy mutations go through IADM-02 only.
        throw new UserProfileAccessDeniedException(
                "cfg.policies writes via institute-admin are retired; use IADM-02 policy drafts.");
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
