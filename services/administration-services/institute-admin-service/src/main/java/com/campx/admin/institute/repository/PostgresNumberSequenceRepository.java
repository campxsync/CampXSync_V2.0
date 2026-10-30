package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.NumberSequence;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link NumberSequenceRepository} backed by {@code core.number_sequences}.
 * Enforces atomic numbering, RLS tenant boundaries, and audit logging.
 */
public class PostgresNumberSequenceRepository implements NumberSequenceRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresNumberSequenceRepository.class);

    private final DatabaseConnectionManager connectionManager;

    private static final Set<String> VALID_RESET_POLICIES = new HashSet<>(Arrays.asList(
            "NEVER", "YEARLY", "MONTHLY", "ACADEMIC_YEAR"));

    public PostgresNumberSequenceRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresNumberSequenceRepository(DatabaseConnectionManager connectionManager) {
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
    public NumberSequence createSequence(UserSecurityContext context, NumberSequence seq) {
        checkContext(context);
        if (seq == null) throw new MalformedPayloadException("NumberSequence payload cannot be null");
        if (seq.getScopeKey() == null || seq.getScopeKey().trim().isEmpty()) {
            throw new MalformedPayloadException("scopeKey is required");
        }
        if (seq.getNextValue() < 0) {
            throw new MalformedPayloadException("nextValue cannot be negative");
        }
        String policy = seq.getResetPolicy() != null ? seq.getResetPolicy().trim().toUpperCase(Locale.ROOT) : "NEVER";
        if (!VALID_RESET_POLICIES.contains(policy)) {
            throw new MalformedPayloadException("Invalid resetPolicy: " + policy);
        }

        UUID seqId = (seq.getId() != null && !seq.getId().trim().isEmpty())
                ? UUID.fromString(seq.getId().trim()) : UUID.randomUUID();
        String scope = seq.getScopeKey().trim().toUpperCase(Locale.ROOT);
        UUID collegeId = (seq.getCollegeId() != null && !seq.getCollegeId().trim().isEmpty())
                ? UUID.fromString(seq.getCollegeId().trim()) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.number_sequences " +
                    "(id, tenant_id, scope_key, prefix, suffix, next_value, padding, reset_policy, last_reset_on, required_permission, college_id, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, scope_key, prefix, suffix, next_value, padding, reset_policy, last_reset_on, required_permission, college_id, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, seqId);
                ps.setObject(2, context.getTenantId());
                ps.setString(3, scope);
                ps.setString(4, seq.getPrefix());
                ps.setString(5, seq.getSuffix());
                ps.setLong(6, seq.getNextValue() >= 0 ? seq.getNextValue() : 1);
                ps.setShort(7, seq.getPadding() > 0 ? seq.getPadding() : 6);
                ps.setString(8, policy);
                if (seq.getLastResetOn() != null) {
                    ps.setDate(9, new java.sql.Date(seq.getLastResetOn()));
                } else {
                    ps.setNull(9, Types.DATE);
                }
                ps.setString(10, seq.getRequiredPermission());
                ps.setObject(11, collegeId);
                ps.setObject(12, context.getUserId());
                ps.setObject(13, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        NumberSequence persisted = mapRow(rs);
                        logger.info("Persisted number sequence [{}] scope [{}] in tenant [{}]",
                                persisted.getId(), persisted.getScopeKey(), persisted.getTenantId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("Sequence already exists for scope: " + scope);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist number sequence record");
        });
    }

    @Override
    public Optional<NumberSequence> findById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, scope_key, prefix, suffix, next_value, padding, reset_policy, last_reset_on, required_permission, college_id, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.number_sequences WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                ps.setObject(2, context.getTenantId());
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
    public Optional<NumberSequence> findByScope(UserSecurityContext context, String scopeKey, String collegeId) {
        checkContext(context);
        if (scopeKey == null || scopeKey.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            UUID cid = (collegeId != null && !collegeId.trim().isEmpty()) ? UUID.fromString(collegeId.trim()) : null;
            String sql = "SELECT id, tenant_id, scope_key, prefix, suffix, next_value, padding, reset_policy, last_reset_on, required_permission, college_id, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.number_sequences WHERE scope_key = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    (cid == null ? "AND college_id IS NULL" : "AND college_id = ?");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, scopeKey.trim().toUpperCase(Locale.ROOT));
                ps.setObject(2, context.getTenantId());
                if (cid != null) {
                    ps.setObject(3, cid);
                }
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
    public List<NumberSequence> listSequences(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, scope_key, prefix, suffix, next_value, padding, reset_policy, last_reset_on, required_permission, college_id, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.number_sequences WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY scope_key";
            List<NumberSequence> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
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
    public NumberSequence updateSequence(UserSecurityContext context, NumberSequence seq) {
        checkContext(context);
        if (seq == null || seq.getId() == null) {
            throw new MalformedPayloadException("Sequence ID is required for update");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.number_sequences SET " +
                    "prefix = COALESCE(?, prefix), " +
                    "suffix = COALESCE(?, suffix), " +
                    "next_value = COALESCE(?, next_value), " +
                    "padding = COALESCE(?, padding), " +
                    "reset_policy = COALESCE(?, reset_policy), " +
                    "required_permission = COALESCE(?, required_permission), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, scope_key, prefix, suffix, next_value, padding, reset_policy, last_reset_on, required_permission, college_id, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, seq.getPrefix());
                ps.setString(2, seq.getSuffix());
                if (seq.getNextValue() >= 0) {
                    ps.setLong(3, seq.getNextValue());
                } else {
                    ps.setNull(3, Types.BIGINT);
                }
                if (seq.getPadding() > 0) {
                    ps.setShort(4, seq.getPadding());
                } else {
                    ps.setNull(4, Types.SMALLINT);
                }
                ps.setString(5, seq.getResetPolicy());
                ps.setString(6, seq.getRequiredPermission());
                ps.setObject(7, context.getUserId());
                ps.setObject(8, UUID.fromString(seq.getId().trim()));
                ps.setObject(9, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("NumberSequence", seq.getId());
        });
    }

    @Override
    public void deleteSequence(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.number_sequences SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("NumberSequence", id);
                }
            }
            return null;
        });
    }

    @Override
    public String generateNextNumber(UserSecurityContext context, String scopeKey, String collegeId) {
        checkContext(context);
        if (scopeKey == null || scopeKey.trim().isEmpty()) {
            throw new MalformedPayloadException("scopeKey is required");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            UUID cid = (collegeId != null && !collegeId.trim().isEmpty()) ? UUID.fromString(collegeId.trim()) : null;
            String sql = "UPDATE core.number_sequences " +
                    "SET next_value = next_value + 1, updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE scope_key = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    (cid == null ? "AND college_id IS NULL " : "AND college_id = ? ") +
                    "RETURNING (next_value - 1) AS allocated_value, prefix, suffix, padding";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setString(2, scopeKey.trim().toUpperCase(Locale.ROOT));
                ps.setObject(3, context.getTenantId());
                if (cid != null) {
                    ps.setObject(4, cid);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long allocated = rs.getLong("allocated_value");
                        String prefix = rs.getString("prefix");
                        String suffix = rs.getString("suffix");
                        short padding = rs.getShort("padding");

                        String pre = prefix != null ? prefix : "";
                        String suf = suffix != null ? suffix : "";
                        short pad = padding > 0 ? padding : 6;
                        String padded = String.format("%0" + pad + "d", allocated);

                        return pre + padded + suf;
                    }
                }
            }
            throw new ResourceNotFoundException("NumberSequence", scopeKey);
        });
    }

    private NumberSequence mapRow(ResultSet rs) throws SQLException {
        NumberSequence s = new NumberSequence();
        s.setId(rs.getString("id"));
        s.setTenantId(rs.getString("tenant_id"));
        s.setScopeKey(rs.getString("scope_key"));
        s.setPrefix(rs.getString("prefix"));
        s.setSuffix(rs.getString("suffix"));
        s.setNextValue(rs.getLong("next_value"));
        s.setPadding(rs.getShort("padding"));
        s.setResetPolicy(rs.getString("reset_policy"));
        java.sql.Date resetDate = rs.getDate("last_reset_on");
        s.setLastResetOn(resetDate != null ? resetDate.getTime() : null);
        s.setRequiredPermission(rs.getString("required_permission"));
        s.setCollegeId(rs.getString("college_id"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        s.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        s.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        s.setCreatedBy(rs.getString("created_by"));
        s.setUpdatedBy(rs.getString("updated_by"));
        s.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        s.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return s;
    }
}
