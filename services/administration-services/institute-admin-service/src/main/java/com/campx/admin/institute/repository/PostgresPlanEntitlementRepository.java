package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.PlanEntitlement;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.plan_entitlements} table (ADM-01 Item 11).
 * Executes platform operations subject to RLS and platform administrator permissions.
 */
public class PostgresPlanEntitlementRepository implements PlanEntitlementRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresPlanEntitlementRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresPlanEntitlementRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresPlanEntitlementRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public PlanEntitlement createEntitlement(UserSecurityContext context, PlanEntitlement entitlement) {
        if (entitlement == null) {
            throw new MalformedPayloadException("Plan entitlement payload cannot be null");
        }
        if (entitlement.getPlanId() == null || entitlement.getPlanId().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'planId' is required");
        }
        if (entitlement.getEntitlementKey() == null || entitlement.getEntitlementKey().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'entitlementKey' is required");
        }

        UUID planUuid;
        try {
            planUuid = UUID.fromString(entitlement.getPlanId().trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid planId UUID format: " + entitlement.getPlanId());
        }

        UUID entUuid;
        if (entitlement.getId() != null && !entitlement.getId().trim().isEmpty()) {
            try {
                entUuid = UUID.fromString(entitlement.getId().trim());
            } catch (IllegalArgumentException e) {
                entUuid = UUID.randomUUID();
            }
        } else {
            entUuid = UUID.randomUUID();
        }

        String key = entitlement.getEntitlementKey().trim().toUpperCase(Locale.ROOT);
        UUID finalEntId = entUuid;
        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            // Check for duplicate key under the same plan
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM plat.plan_entitlements WHERE plan_id = ? AND entitlement_key = ? AND deleted_at IS NULL")) {
                checkPs.setObject(1, planUuid);
                checkPs.setString(2, key);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new ResourceConflictException("PlanEntitlement", "entitlementKey", key);
                    }
                }
            }

            String insertSql = "INSERT INTO plat.plan_entitlements ("
                    + "id, plan_id, entitlement_key, limit_value, is_enabled, created_by, row_version, created_at, updated_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, 1, now(), now()) "
                    + "RETURNING id, plan_id, entitlement_key, limit_value, is_enabled, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalEntId);
                ps.setObject(2, planUuid);
                ps.setString(3, key);
                if (entitlement.getLimitValue() != null) {
                    ps.setLong(4, entitlement.getLimitValue());
                } else {
                    ps.setNull(4, Types.BIGINT);
                }
                ps.setBoolean(5, entitlement.isEnabled());
                ps.setObject(6, effectiveContext.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new RuntimeException("Failed to persist plan entitlement: no row returned");
        });
    }

    @Override
    public PlanEntitlement getEntitlementById(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return null;

        UUID entUuid;
        try {
            entUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, plan_id, entitlement_key, limit_value, is_enabled, row_version, created_at, updated_at "
                    + "FROM plat.plan_entitlements WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, entUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<PlanEntitlement> listEntitlementsByPlan(UserSecurityContext context, String planId) {
        if (planId == null || planId.trim().isEmpty()) return Collections.emptyList();

        UUID planUuid;
        try {
            planUuid = UUID.fromString(planId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, plan_id, entitlement_key, limit_value, is_enabled, row_version, created_at, updated_at "
                    + "FROM plat.plan_entitlements WHERE plan_id = ? AND deleted_at IS NULL ORDER BY entitlement_key ASC";
            List<PlanEntitlement> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, planUuid);
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
    public PlanEntitlement updateEntitlement(UserSecurityContext context, PlanEntitlement entitlement) {
        if (entitlement == null || entitlement.getId() == null || entitlement.getId().trim().isEmpty()) {
            throw new MalformedPayloadException("Plan entitlement and ID required for update");
        }

        UUID entUuid;
        try {
            entUuid = UUID.fromString(entitlement.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid entitlement ID format: " + entitlement.getId());
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String updateSql = "UPDATE plat.plan_entitlements SET "
                    + "limit_value = ?, is_enabled = ?, updated_by = ?, updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND deleted_at IS NULL "
                    + "RETURNING id, plan_id, entitlement_key, limit_value, is_enabled, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                if (entitlement.getLimitValue() != null) {
                    ps.setLong(1, entitlement.getLimitValue());
                } else {
                    ps.setNull(1, Types.BIGINT);
                }
                ps.setBoolean(2, entitlement.isEnabled());
                ps.setObject(3, effectiveContext.getUserId());
                ps.setObject(4, entUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("PlanEntitlement", entitlement.getId());
        });
    }

    @Override
    public boolean deleteEntitlement(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return false;

        UUID entUuid;
        try {
            entUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.plan_entitlements SET "
                    + "deleted_at = now(), updated_by = ?, updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, effectiveContext.getUserId());
                ps.setObject(2, entUuid);
                int affected = ps.executeUpdate();
                return affected > 0;
            }
        });
    }

    private PlanEntitlement mapRow(ResultSet rs) throws SQLException {
        PlanEntitlement ent = new PlanEntitlement();
        Object idObj = rs.getObject("id");
        ent.setId(idObj != null ? idObj.toString() : null);

        Object planIdObj = rs.getObject("plan_id");
        ent.setPlanId(planIdObj != null ? planIdObj.toString() : null);

        ent.setEntitlementKey(rs.getString("entitlement_key"));

        long limitVal = rs.getLong("limit_value");
        if (rs.wasNull()) {
            ent.setLimitValue(null);
        } else {
            ent.setLimitValue(limitVal);
        }

        ent.setEnabled(rs.getBoolean("is_enabled"));
        ent.setRowVersion(rs.getInt("row_version"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        ent.setCreatedAt(createdAt != null ? createdAt.getTime() : System.currentTimeMillis());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        ent.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : System.currentTimeMillis());

        return ent;
    }
}
