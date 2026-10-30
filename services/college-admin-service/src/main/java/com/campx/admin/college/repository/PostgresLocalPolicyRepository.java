package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeMalformedPayloadException;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.exception.CollegeSecurityViolationException;
import com.campx.admin.college.model.CollegeModels.LocalPolicy;
import com.campx.admin.college.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code cfg.policies}.
 * Executes queries under caller's identity via 'authenticated' role and RLS kernel enforcement.
 */
public class PostgresLocalPolicyRepository implements LocalPolicyRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresLocalPolicyRepository.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final DatabaseConnectionManager connectionManager;

    public PostgresLocalPolicyRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresLocalPolicyRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public LocalPolicy createLocalPolicy(UserSecurityContext context, LocalPolicy policy) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (policy == null) {
            throw new CollegeMalformedPayloadException("Policy payload cannot be null");
        }
        if (policy.getPolicyCode() == null || policy.getPolicyCode().trim().isEmpty()) {
            throw new CollegeMalformedPayloadException("Mandatory field 'policyCode' is required");
        }

        String code = policy.getPolicyCode().trim().toUpperCase(Locale.ROOT);
        String status = policy.getStatus() != null ? policy.getStatus().trim() : "DRAFT";

        return context.executeInTransaction(connectionManager, conn -> {
            // Check for conflict
            String checkSql = "SELECT id FROM cfg.policies WHERE policy_code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new CollegeResourceConflictException("LocalPolicy", "policyCode", code);
                    }
                }
            }

            UUID id = (policy.getId() != null && !policy.getId().trim().isEmpty())
                    ? UUID.fromString(policy.getId().trim()) : UUID.randomUUID();

            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("status", status);
            rules.put("approvedBy", policy.getApprovedBy());
            rules.put("version", policy.getVersion() > 0 ? policy.getVersion() : 1);
            String rulesJson;
            try {
                rulesJson = objectMapper.writeValueAsString(rules);
            } catch (Exception e) {
                throw new CollegeMalformedPayloadException("Failed to serialize policy rules JSON: " + e.getMessage());
            }

            String insertSql = "INSERT INTO cfg.policies (id, policy_code, name, category, rules, enforcement_mode, " +
                    "created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, 'LOCAL_POLICY', ?::jsonb, 'ENFORCE', now(), now(), ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, id);
                ps.setString(2, code);
                ps.setString(3, code);
                ps.setString(4, rulesJson);
                ps.setInt(5, policy.getVersion() > 0 ? policy.getVersion() : 1);
                ps.executeUpdate();
            }

            policy.setId(id.toString());
            policy.setPolicyCode(code);
            policy.setStatus(status);
            policy.setVersion(policy.getVersion() > 0 ? policy.getVersion() : 1);

            logger.info("Persisted local policy [{}] in cfg.policies", code);
            return policy;
        });
    }

    @Override
    public LocalPolicy updateLocalPolicy(UserSecurityContext context, LocalPolicy policy) {
        if (context == null || context.getTenantId() == null) {
            throw new CollegeSecurityViolationException("Missing required security context: tenantId");
        }
        if (policy == null || policy.getPolicyCode() == null) {
            throw new CollegeMalformedPayloadException("Policy and policyCode must not be null");
        }

        String code = policy.getPolicyCode().trim().toUpperCase(Locale.ROOT);
        String status = policy.getStatus() != null ? policy.getStatus().trim() : "DRAFT";

        return context.executeInTransaction(connectionManager, conn -> {
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("status", status);
            rules.put("approvedBy", policy.getApprovedBy());
            rules.put("version", policy.getVersion() > 0 ? policy.getVersion() : 1);
            String rulesJson;
            try {
                rulesJson = objectMapper.writeValueAsString(rules);
            } catch (Exception e) {
                throw new CollegeMalformedPayloadException("Failed to serialize policy rules JSON: " + e.getMessage());
            }

            String updateSql = "UPDATE cfg.policies SET rules = ?::jsonb, row_version = row_version + 1, " +
                    "updated_at = now() WHERE policy_code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, rulesJson);
                ps.setString(2, code);
                int count = ps.executeUpdate();
                if (count == 0) {
                    return createLocalPolicy(context, policy);
                }
            }
            logger.info("Updated local policy [{}] in cfg.policies (status={})", code, status);
            return policy;
        });
    }

    @Override
    public Optional<LocalPolicy> findByCode(UserSecurityContext context, String policyCode) {
        if (context == null || context.getTenantId() == null || policyCode == null) {
            return Optional.empty();
        }

        String code = policyCode.trim().toUpperCase(Locale.ROOT);

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, policy_code, rules ->> 'status' AS status, rules ->> 'approvedBy' AS approved_by, " +
                    "row_version FROM cfg.policies WHERE policy_code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        LocalPolicy p = new LocalPolicy();
                        p.setId(rs.getString("id"));
                        p.setPolicyCode(rs.getString("policy_code"));
                        p.setVersion(rs.getInt("row_version"));

                        String st = rs.getString("status");
                        p.setStatus(st != null ? st : "DRAFT");
                        p.setApprovedBy(rs.getString("approved_by"));
                        return Optional.of(p);
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<LocalPolicy> listPolicies(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            List<LocalPolicy> list = new ArrayList<>();
            String sql = "SELECT id, policy_code, rules ->> 'status' AS status, rules ->> 'approvedBy' AS approved_by, " +
                    "row_version FROM cfg.policies WHERE deleted_at IS NULL ORDER BY policy_code ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalPolicy p = new LocalPolicy();
                        p.setId(rs.getString("id"));
                        p.setPolicyCode(rs.getString("policy_code"));
                        p.setVersion(rs.getInt("row_version"));

                        String st = rs.getString("status");
                        p.setStatus(st != null ? st : "DRAFT");
                        p.setApprovedBy(rs.getString("approved_by"));
                        list.add(p);
                    }
                }
            }
            return list;
        });
    }
}

