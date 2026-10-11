package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.LookupType;
import com.campx.admin.institute.model.InstituteModels.LookupValue;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL implementation of {@link LookupRepository} backed by:
 * {@code core.lookup_types} and {@code core.lookup_values}.
 * Enforces tenant boundary isolation and RLS policies.
 */
public class PostgresLookupRepository implements LookupRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresLookupRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresLookupRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresLookupRepository(DatabaseConnectionManager connectionManager) {
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
    public LookupType createType(UserSecurityContext context, LookupType type) {
        checkContext(context);
        if (type == null) throw new MalformedPayloadException("LookupType cannot be null");
        if (type.getCode() == null || type.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupType code is required");
        }
        if (type.getName() == null || type.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupType name is required");
        }

        UUID typeId = (type.getId() != null && !type.getId().trim().isEmpty())
                ? UUID.fromString(type.getId().trim()) : UUID.randomUUID();
        String code = type.getCode().trim().toUpperCase(Locale.ROOT);

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.lookup_types " +
                    "(id, tenant_id, code, name, is_system, description, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, code, name, is_system, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, typeId);
                ps.setObject(2, context.getTenantId());
                ps.setString(3, code);
                ps.setString(4, type.getName().trim());
                ps.setBoolean(5, type.isSystem());
                ps.setString(6, type.getDescription());
                ps.setObject(7, context.getUserId());
                ps.setObject(8, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        LookupType persisted = mapTypeRow(rs);
                        logger.info("Persisted lookup type [{}] code [{}] in tenant [{}]",
                                persisted.getId(), persisted.getCode(), persisted.getTenantId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("LookupType code already exists: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist lookup type record");
        });
    }

    @Override
    public Optional<LookupType> findTypeById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, is_system, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.lookup_types WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapTypeRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<LookupType> findTypeByCode(UserSecurityContext context, String code) {
        checkContext(context);
        if (code == null || code.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, is_system, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.lookup_types WHERE code = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapTypeRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<LookupType> listTypes(UserSecurityContext context) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, code, name, is_system, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.lookup_types WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY code";
            List<LookupType> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapTypeRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public LookupType updateType(UserSecurityContext context, LookupType type) {
        checkContext(context);
        if (type == null || type.getId() == null) throw new MalformedPayloadException("LookupType ID is required");

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.lookup_types SET " +
                    "name = COALESCE(?, name), " +
                    "description = COALESCE(?, description), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, code, name, is_system, description, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, type.getName());
                ps.setString(2, type.getDescription());
                ps.setObject(3, context.getUserId());
                ps.setObject(4, UUID.fromString(type.getId().trim()));
                ps.setObject(5, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapTypeRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("LookupType", type.getId());
        });
    }

    @Override
    public void deleteType(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            UUID tid = UUID.fromString(id.trim());
            // Soft delete lookup values
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE core.lookup_values SET deleted_at = now(), updated_at = now(), updated_by = ? WHERE lookup_type_id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, tid);
                ps.setObject(3, context.getTenantId());
                ps.executeUpdate();
            }

            // Soft delete lookup type
            String sql = "UPDATE core.lookup_types SET deleted_at = now(), updated_at = now(), updated_by = ? WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, tid);
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("LookupType", id);
                }
            }
            return null;
        });
    }

    @Override
    public LookupValue createValue(UserSecurityContext context, LookupValue value) {
        checkContext(context);
        if (value == null) throw new MalformedPayloadException("LookupValue cannot be null");
        if (value.getLookupTypeId() == null || value.getLookupTypeId().trim().isEmpty()) {
            throw new MalformedPayloadException("lookupTypeId is required");
        }
        if (value.getCode() == null || value.getCode().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupValue code is required");
        }
        if (value.getLabel() == null || value.getLabel().trim().isEmpty()) {
            throw new MalformedPayloadException("LookupValue label is required");
        }

        UUID valId = (value.getId() != null && !value.getId().trim().isEmpty())
                ? UUID.fromString(value.getId().trim()) : UUID.randomUUID();
        UUID typeId = UUID.fromString(value.getLookupTypeId().trim());
        String code = value.getCode().trim().toUpperCase(Locale.ROOT);

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO core.lookup_values " +
                    "(id, tenant_id, lookup_type_id, code, label, sort_order, is_active, attrs, created_at, updated_at, created_by, updated_by, row_version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), ?, ?, 1) " +
                    "RETURNING id, tenant_id, lookup_type_id, code, label, sort_order, is_active, attrs, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, valId);
                ps.setObject(2, context.getTenantId());
                ps.setObject(3, typeId);
                ps.setString(4, code);
                ps.setString(5, value.getLabel().trim());
                ps.setInt(6, value.getSortOrder());
                ps.setBoolean(7, value.isActive());
                ps.setString(8, value.getAttrs() != null ? value.getAttrs() : "{}");
                ps.setObject(9, context.getUserId());
                ps.setObject(10, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        LookupValue persisted = mapValueRow(rs);
                        logger.info("Persisted lookup value [{}] code [{}] in type [{}]",
                                persisted.getId(), persisted.getCode(), persisted.getLookupTypeId());
                        return persisted;
                    }
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new MalformedPayloadException("LookupValue code already exists for type: " + code);
                }
                throw e;
            }
            throw new MalformedPayloadException("Failed to persist lookup value record");
        });
    }

    @Override
    public Optional<LookupValue> findValueById(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, lookup_type_id, code, label, sort_order, is_active, attrs, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.lookup_values WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(id.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapValueRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<LookupValue> findValueByCode(UserSecurityContext context, String lookupTypeId, String code) {
        checkContext(context);
        if (lookupTypeId == null || code == null) return Optional.empty();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, lookup_type_id, code, label, sort_order, is_active, attrs, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.lookup_values WHERE lookup_type_id = ? AND code = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(lookupTypeId.trim()));
                ps.setString(2, code.trim().toUpperCase(Locale.ROOT));
                ps.setObject(3, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapValueRow(rs));
                    }
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<LookupValue> listValuesByType(UserSecurityContext context, String lookupTypeId) {
        checkContext(context);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, lookup_type_id, code, label, sort_order, is_active, attrs, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                    "FROM core.lookup_values WHERE lookup_type_id = ? AND tenant_id = ? AND deleted_at IS NULL ORDER BY sort_order, code";
            List<LookupValue> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(lookupTypeId.trim()));
                ps.setObject(2, context.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapValueRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public LookupValue updateValue(UserSecurityContext context, LookupValue value) {
        checkContext(context);
        if (value == null || value.getId() == null) throw new MalformedPayloadException("LookupValue ID is required");

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.lookup_values SET " +
                    "label = COALESCE(?, label), " +
                    "sort_order = COALESCE(?, sort_order), " +
                    "is_active = COALESCE(?, is_active), " +
                    "attrs = COALESCE(?::jsonb, attrs), " +
                    "updated_at = now(), updated_by = ?, row_version = row_version + 1 " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_id, lookup_type_id, code, label, sort_order, is_active, attrs, created_at, updated_at, created_by, updated_by, row_version, deleted_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, value.getLabel());
                ps.setInt(2, value.getSortOrder());
                ps.setBoolean(3, value.isActive());
                ps.setString(4, value.getAttrs());
                ps.setObject(5, context.getUserId());
                ps.setObject(6, UUID.fromString(value.getId().trim()));
                ps.setObject(7, context.getTenantId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapValueRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("LookupValue", value.getId());
        });
    }

    @Override
    public void deleteValue(UserSecurityContext context, String id) {
        checkContext(context);
        if (id == null || id.trim().isEmpty()) return;

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE core.lookup_values SET deleted_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, UUID.fromString(id.trim()));
                ps.setObject(3, context.getTenantId());
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    throw new ResourceNotFoundException("LookupValue", id);
                }
            }
            return null;
        });
    }

    private LookupType mapTypeRow(ResultSet rs) throws SQLException {
        LookupType t = new LookupType();
        t.setId(rs.getString("id"));
        t.setTenantId(rs.getString("tenant_id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setSystem(rs.getBoolean("is_system"));
        t.setDescription(rs.getString("description"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        t.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        t.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        t.setCreatedBy(rs.getString("created_by"));
        t.setUpdatedBy(rs.getString("updated_by"));
        t.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        t.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return t;
    }

    private LookupValue mapValueRow(ResultSet rs) throws SQLException {
        LookupValue v = new LookupValue();
        v.setId(rs.getString("id"));
        v.setTenantId(rs.getString("tenant_id"));
        v.setLookupTypeId(rs.getString("lookup_type_id"));
        v.setCode(rs.getString("code"));
        v.setLabel(rs.getString("label"));
        v.setSortOrder(rs.getInt("sort_order"));
        v.setActive(rs.getBoolean("is_active"));
        v.setAttrs(rs.getString("attrs"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        v.setCreatedAt(createdTs != null ? createdTs.getTime() : 0L);
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        v.setUpdatedAt(updatedTs != null ? updatedTs.getTime() : 0L);
        v.setCreatedBy(rs.getString("created_by"));
        v.setUpdatedBy(rs.getString("updated_by"));
        v.setRowVersion(rs.getInt("row_version"));
        Timestamp delTs = rs.getTimestamp("deleted_at");
        v.setDeletedAt(delTs != null ? delTs.getTime() : null);
        return v;
    }
}
