package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.Subscription;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.subscriptions} table (ADM-01 Item 9).
 * Enforces tenant isolation, plan validation, and RLS policies.
 */
public class PostgresSubscriptionRepository implements SubscriptionRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresSubscriptionRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresSubscriptionRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresSubscriptionRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Subscription createSubscription(UserSecurityContext context, Subscription sub) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        if (sub == null) {
            throw new MalformedPayloadException("Subscription payload cannot be null");
        }
        if (sub.getPlanId() == null || sub.getPlanId().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'planId' is required");
        }

        UUID tenantUuid = context.getTenantId();
        UUID userUuid = context.getUserId();

        UUID planUuid;
        try {
            planUuid = UUID.fromString(sub.getPlanId().trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid planId UUID format: " + sub.getPlanId());
        }

        UUID subUuid;
        if (sub.getId() != null && !sub.getId().trim().isEmpty()) {
            try {
                subUuid = UUID.fromString(sub.getId().trim());
            } catch (IllegalArgumentException e) {
                subUuid = UUID.randomUUID();
            }
        } else {
            subUuid = UUID.randomUUID();
        }

        UUID finalSubId = subUuid;
        String status = (sub.getStatus() != null && !sub.getStatus().trim().isEmpty())
                ? sub.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!"TRIAL".equals(status) && !"ACTIVE".equals(status) && !"PAST_DUE".equals(status)
                && !"SUSPENDED".equals(status) && !"CANCELLED".equals(status)) {
            status = "ACTIVE";
        }

        String finalStatus = status;

        return context.executeInTransaction(connectionManager, conn -> {
            // Validate plan exists in plat.subscription_plans
            try (PreparedStatement checkPlan = conn.prepareStatement(
                    "SELECT id FROM plat.subscription_plans WHERE id = ? AND deleted_at IS NULL")) {
                checkPlan.setObject(1, planUuid);
                try (ResultSet rs = checkPlan.executeQuery()) {
                    if (!rs.next()) {
                        throw new ResourceNotFoundException("SubscriptionPlan", planUuid.toString());
                    }
                }
            }

            java.sql.Date startDate = sub.getStartDate() > 0
                    ? new java.sql.Date(sub.getStartDate()) : new java.sql.Date(System.currentTimeMillis());
            java.sql.Date endDate = sub.getEndDate() > 0
                    ? new java.sql.Date(sub.getEndDate()) : new java.sql.Date(System.currentTimeMillis() + 31536000000L);

            String insertSql = "INSERT INTO plat.subscriptions ("
                    + "id, tenant_id, plan_id, start_date, end_date, auto_renew, status, created_by, row_version, created_at, updated_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) "
                    + "RETURNING id, tenant_id, plan_id, start_date, end_date, auto_renew, status, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalSubId);
                ps.setObject(2, tenantUuid);
                ps.setObject(3, planUuid);
                ps.setDate(4, startDate);
                ps.setDate(5, endDate);
                ps.setBoolean(6, sub.isAutoRenew());
                ps.setString(7, finalStatus);
                ps.setObject(8, userUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new RuntimeException("Failed to persist subscription: no row returned");
        });
    }

    @Override
    public Subscription getSubscriptionById(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null || id.trim().isEmpty()) {
            return null;
        }

        UUID subUuid;
        try {
            subUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        UUID tenantUuid = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, plan_id, start_date, end_date, auto_renew, status, row_version, created_at, updated_at "
                    + "FROM plat.subscriptions WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, subUuid);
                ps.setObject(2, tenantUuid);
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
    public Subscription getActiveSubscription(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            return null;
        }

        UUID tenantUuid = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, plan_id, start_date, end_date, auto_renew, status, row_version, created_at, updated_at "
                    + "FROM plat.subscriptions WHERE tenant_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantUuid);
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
    public List<Subscription> listSubscriptions(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            return Collections.emptyList();
        }

        UUID tenantUuid = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            List<Subscription> list = new ArrayList<>();
            String sql = "SELECT id, tenant_id, plan_id, start_date, end_date, auto_renew, status, row_version, created_at, updated_at "
                    + "FROM plat.subscriptions WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantUuid);
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
    public Subscription updateSubscription(UserSecurityContext context, Subscription sub) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        if (sub == null || sub.getId() == null) {
            throw new MalformedPayloadException("Subscription ID is required");
        }

        UUID subUuid;
        try {
            subUuid = UUID.fromString(sub.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid subscription ID: " + sub.getId());
        }

        UUID tenantUuid = context.getTenantId();
        UUID userUuid = context.getUserId();

        return context.executeInTransaction(connectionManager, conn -> {
            java.sql.Date endDate = sub.getEndDate() > 0 ? new java.sql.Date(sub.getEndDate()) : null;

            String updateSql = "UPDATE plat.subscriptions SET "
                    + "status = ?, auto_renew = ?, end_date = COALESCE(?, end_date), updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, plan_id, start_date, end_date, auto_renew, status, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, sub.getStatus());
                ps.setBoolean(2, sub.isAutoRenew());
                ps.setDate(3, endDate);
                ps.setObject(4, userUuid);
                ps.setObject(5, subUuid);
                ps.setObject(6, tenantUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("Subscription", sub.getId());
        });
    }

    @Override
    public void cancelSubscription(UserSecurityContext context, String id) {
        if (context == null || context.getTenantId() == null || id == null) {
            return;
        }

        UUID subUuid;
        try {
            subUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        UUID tenantUuid = context.getTenantId();
        UUID userUuid = context.getUserId();

        context.executeInTransaction(connectionManager, conn -> {
            String cancelSql = "UPDATE plat.subscriptions SET status = 'CANCELLED', auto_renew = false, updated_at = now(), updated_by = ? "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(cancelSql)) {
                ps.setObject(1, userUuid);
                ps.setObject(2, subUuid);
                ps.setObject(3, tenantUuid);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private Subscription mapRow(ResultSet rs) throws SQLException {
        Subscription s = new Subscription();
        s.setId(rs.getString("id"));
        s.setTenantId(rs.getString("tenant_id"));
        s.setPlanId(rs.getString("plan_id"));
        java.sql.Date sd = rs.getDate("start_date");
        s.setStartDate(sd != null ? sd.getTime() : System.currentTimeMillis());
        java.sql.Date ed = rs.getDate("end_date");
        s.setEndDate(ed != null ? ed.getTime() : 0);
        s.setAutoRenew(rs.getBoolean("auto_renew"));
        s.setStatus(rs.getString("status"));
        s.setRowVersion(rs.getInt("row_version"));
        Timestamp ct = rs.getTimestamp("created_at");
        s.setCreatedAt(ct != null ? ct.getTime() : System.currentTimeMillis());
        Timestamp ut = rs.getTimestamp("updated_at");
        s.setUpdatedAt(ut != null ? ut.getTime() : s.getCreatedAt());
        return s;
    }
}
