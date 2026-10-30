package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.DataClassification;
import com.campx.admin.institute.model.InstituteModels.DataRetentionPolicy;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link DataGovernanceRepository} backed by:
 * {@code cfg.data_retention_policies} and {@code cfg.data_classifications}.
 * Enforces platform-level RLS policies and audit trail consistency.
 */
public class PostgresDataGovernanceRepository implements DataGovernanceRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresDataGovernanceRepository.class);

    private final DatabaseConnectionManager connectionManager;

    private static final Set<String> VALID_ACTIONS = new HashSet<>(Arrays.asList("ARCHIVE", "DELETE", "ANONYMIZE"));
    private static final Set<String> VALID_SENSITIVITIES = new HashSet<>(Arrays.asList("LOW", "MEDIUM", "HIGH", "RESTRICTED"));

    public PostgresDataGovernanceRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresDataGovernanceRepository(DatabaseConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("DatabaseConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
    }

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
    }

    @Override
    public DataRetentionPolicy createRetentionPolicy(UserSecurityContext context, DataRetentionPolicy policy) {
        checkContext(context);
        if (policy == null) throw new MalformedPayloadException("DataRetentionPolicy cannot be null");
        if (policy.getPolicyCode() == null || policy.getPolicyCode().trim().isEmpty()) {
            throw new MalformedPayloadException("policyCode is required");
        }
        if (policy.getRetentionDays() <= 0) {
            throw new MalformedPayloadException("retentionDays must be > 0");
        }
        String action = policy.getAction() != null ? policy.getAction().trim().toUpperCase(Locale.ROOT) : "ARCHIVE";
        if (!VALID_ACTIONS.contains(action)) {
            throw new MalformedPayloadException("Invalid action: " + action);
        }

        UUID policyId = (policy.getId() != null && !policy.getId().trim().isEmpty())
                ? UUID.fromString(policy.getId().trim()) : UUID.randomUUID();
        String code = policy.getPolicyCode().trim().toUpperCase(Locale.ROOT);
        String entityType = policy.getEntityType() != null ? policy.getEntityType().trim() : "STUDENT_RECORD";

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO cfg.data_retention_policies " +
                    "(id, policy_code, entity_type, retention_days, action, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, policy_code, entity_type, retention_days, action, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, policyId);
                ps.setString(2, code);
                ps.setString(3, entityType);
                ps.setInt(4, policy.getRetentionDays());
                ps.setString(5, action);
                ps.setObject(6, context.getUserId());
                ps.setObject(7, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        DataRetentionPolicy persisted = mapPolicyRow(rs);
                        logger.info("Persisted retention policy [{}] code [{}]", persisted.getId(), persisted.getPolicyCode());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("Policy code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist data retention policy record");
        });
    }

    @Override
    public Optional<DataRetentionPolicy> findRetentionPolicyById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, policy_code, entity_type, retention_days, action, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM cfg.data_retention_policies WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapPolicyRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<DataRetentionPolicy> findRetentionPolicyByCode(UserSecurityContext context, String policyCode) {
        checkContext(context);
        if (policyCode == null || policyCode.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, policy_code, entity_type, retention_days, action, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM cfg.data_retention_policies WHERE policy_code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, policyCode.trim().toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapPolicyRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<DataRetentionPolicy> listRetentionPolicies(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, policy_code, entity_type, retention_days, action, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM cfg.data_retention_policies WHERE deleted_at IS NULL ORDER BY policy_code";
            List<DataRetentionPolicy> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapPolicyRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public DataRetentionPolicy updateRetentionPolicy(UserSecurityContext context, DataRetentionPolicy policy) {
        checkContext(context);
        if (policy == null || policy.getId() == null) throw new MalformedPayloadException("Policy ID is required");

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE cfg.data_retention_policies SET " +
                    "entity_type = COALESCE(?, entity_type), " +
                    "retention_days = COALESCE(?, retention_days), " +
                    "action = COALESCE(?, action), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND deleted_at IS NULL " +
                    "RETURNING id, policy_code, entity_type, retention_days, action, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, policy.getEntityType());
                if (policy.getRetentionDays() > 0) {
                    ps.setInt(2, policy.getRetentionDays());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, policy.getAction());
                ps.setObject(4, context.getUserId());
                ps.setObject(5, UUID.fromString(policy.getId().trim()));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapPolicyRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("DataRetentionPolicy", policy.getId());
        });
    }

    @Override
    public void deleteRetentionPolicy(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE cfg.data_retention_policies SET deleted_at = now(), updated_at = now(), updated_by = ? WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("DataRetentionPolicy", id);
                }
            }
            return null;
        });
    }

    @Override
    public DataClassification createClassification(UserSecurityContext context, DataClassification classification) {
        checkContext(context);
        if (classification == null) throw new MalformedPayloadException("DataClassification cannot be null");
        if (classification.getClassificationCode() == null || classification.getClassificationCode().trim().isEmpty()) {
            throw new MalformedPayloadException("classificationCode is required");
        }
        String sens = classification.getSensitivityLevel() != null
                ? classification.getSensitivityLevel().trim().toUpperCase(Locale.ROOT) : "LOW";
        if (!VALID_SENSITIVITIES.contains(sens)) {
            throw new MalformedPayloadException("Invalid sensitivityLevel: " + sens);
        }

        UUID classId = (classification.getId() != null && !classification.getId().trim().isEmpty())
                ? UUID.fromString(classification.getId().trim()) : UUID.randomUUID();
        String code = classification.getClassificationCode().trim().toUpperCase(Locale.ROOT);

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO cfg.data_classifications " +
                    "(id, classification_code, sensitivity_level, encryption_required, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, classification_code, sensitivity_level, encryption_required, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, classId);
                ps.setString(2, code);
                ps.setString(3, sens);
                ps.setBoolean(4, classification.isEncryptionRequired());
                ps.setObject(5, context.getUserId());
                ps.setObject(6, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        DataClassification persisted = mapClassificationRow(rs);
                        logger.info("Persisted classification [{}] code [{}]", persisted.getId(), persisted.getClassificationCode());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("Classification code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist data classification record");
        });
    }

    @Override
    public Optional<DataClassification> findClassificationById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, classification_code, sensitivity_level, encryption_required, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM cfg.data_classifications WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapClassificationRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<DataClassification> findClassificationByCode(UserSecurityContext context, String classificationCode) {
        checkContext(context);
        if (classificationCode == null || classificationCode.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, classification_code, sensitivity_level, encryption_required, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM cfg.data_classifications WHERE classification_code = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, classificationCode.trim().toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapClassificationRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<DataClassification> listClassifications(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, classification_code, sensitivity_level, encryption_required, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM cfg.data_classifications WHERE deleted_at IS NULL ORDER BY classification_code";
            List<DataClassification> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapClassificationRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public DataClassification updateClassification(UserSecurityContext context, DataClassification classification) {
        checkContext(context);
        if (classification == null || classification.getId() == null) throw new MalformedPayloadException("Classification ID is required");

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE cfg.data_classifications SET " +
                    "sensitivity_level = COALESCE(?, sensitivity_level), " +
                    "encryption_required = COALESCE(?, encryption_required), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND deleted_at IS NULL " +
                    "RETURNING id, classification_code, sensitivity_level, encryption_required, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, classification.getSensitivityLevel());
                ps.setBoolean(2, classification.isEncryptionRequired());
                ps.setObject(3, context.getUserId());
                ps.setObject(4, UUID.fromString(classification.getId().trim()));

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapClassificationRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("DataClassification", classification.getId());
        });
    }

    @Override
    public void deleteClassification(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE cfg.data_classifications SET deleted_at = now(), updated_at = now(), updated_by = ? WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("DataClassification", id);
                }
            }
            return null;
        });
    }

    private DataRetentionPolicy mapPolicyRow(ResultSet rs) throws SQLException {
        DataRetentionPolicy p = new DataRetentionPolicy();
        p.setId(rs.getString("id"));
        p.setPolicyCode(rs.getString("policy_code"));
        p.setEntityType(rs.getString("entity_type"));
        p.setRetentionDays(rs.getInt("retention_days"));
        p.setAction(rs.getString("action"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        p.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        p.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        p.setCreatedBy(rs.getString("created_by"));
        p.setUpdatedBy(rs.getString("updated_by"));
        p.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        p.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return p;
    }

    private DataClassification mapClassificationRow(ResultSet rs) throws SQLException {
        DataClassification c = new DataClassification();
        c.setId(rs.getString("id"));
        c.setClassificationCode(rs.getString("classification_code"));
        c.setSensitivityLevel(rs.getString("sensitivity_level"));
        c.setEncryptionRequired(rs.getBoolean("encryption_required"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        c.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        c.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        c.setCreatedBy(rs.getString("created_by"));
        c.setUpdatedBy(rs.getString("updated_by"));
        c.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        c.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return c;
    }
}
