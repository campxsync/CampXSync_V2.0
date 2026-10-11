package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.SubscriptionPlan;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.subscription_plans} table (ADM-01 Item 8).
 * Executes platform operations subject to RLS and role switching.
 */
public class PostgresSubscriptionPlanRepository implements SubscriptionPlanRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresSubscriptionPlanRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresSubscriptionPlanRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresSubscriptionPlanRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public SubscriptionPlan createPlan(UserSecurityContext context, SubscriptionPlan plan) {
        if (plan == null) {
            throw new MalformedPayloadException("Subscription plan payload cannot be null");
        }
        if (plan.getPlanCode() == null || plan.getPlanCode().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'planCode' is required");
        }
        if (plan.getName() == null || plan.getName().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'name' is required");
        }
        if (plan.getPrice() < 0) {
            throw new MalformedPayloadException("Plan price cannot be negative");
        }

        String code = plan.getPlanCode().trim().toUpperCase(Locale.ROOT);
        String name = plan.getName().trim();
        String cycle = (plan.getBillingCycle() != null && !plan.getBillingCycle().trim().isEmpty())
                ? plan.getBillingCycle().trim().toUpperCase(Locale.ROOT) : "MONTHLY";
        if (!"MONTHLY".equals(cycle) && !"QUARTERLY".equals(cycle) && !"ANNUALLY".equals(cycle)) {
            cycle = "MONTHLY";
        }
        String currency = (plan.getCurrencyCode() != null && !plan.getCurrencyCode().trim().isEmpty())
                ? plan.getCurrencyCode().trim().toUpperCase(Locale.ROOT) : "INR";
        if (currency.length() > 3) {
            currency = currency.substring(0, 3);
        }

        UUID planUuid;
        if (plan.getId() != null && !plan.getId().trim().isEmpty()) {
            try {
                planUuid = UUID.fromString(plan.getId().trim());
            } catch (IllegalArgumentException e) {
                planUuid = UUID.randomUUID();
            }
        } else {
            planUuid = UUID.randomUUID();
        }

        UUID finalPlanId = planUuid;
        String finalCycle = cycle;
        String finalCurrency = currency;
        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            // Check duplicate code
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM plat.subscription_plans WHERE plan_code = ? AND deleted_at IS NULL")) {
                checkPs.setString(1, code);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new ResourceConflictException("SubscriptionPlan", "planCode", code);
                    }
                }
            }

            String insertSql = "INSERT INTO plat.subscription_plans ("
                    + "id, plan_code, name, billing_cycle, price, currency_code, is_published, created_by, row_version, created_at, updated_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) "
                    + "RETURNING id, plan_code, name, billing_cycle, price, currency_code, is_published, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalPlanId);
                ps.setString(2, code);
                ps.setString(3, name);
                ps.setString(4, finalCycle);
                ps.setBigDecimal(5, BigDecimal.valueOf(plan.getPrice()));
                ps.setString(6, finalCurrency);
                ps.setBoolean(7, plan.isPublished());
                ps.setObject(8, effectiveContext.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new RuntimeException("Failed to persist subscription plan: no row returned");
        });
    }

    @Override
    public SubscriptionPlan getPlanById(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return null;

        UUID planUuid;
        try {
            planUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, plan_code, name, billing_cycle, price, currency_code, is_published, row_version, created_at, updated_at "
                    + "FROM plat.subscription_plans WHERE id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, planUuid);
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
    public SubscriptionPlan getPlanByCode(UserSecurityContext context, String planCode) {
        if (planCode == null || planCode.trim().isEmpty()) return null;

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, plan_code, name, billing_cycle, price, currency_code, is_published, row_version, created_at, updated_at "
                    + "FROM plat.subscription_plans WHERE plan_code = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, planCode.trim().toUpperCase(Locale.ROOT));
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
    public List<SubscriptionPlan> listPlans(UserSecurityContext context) {
        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            List<SubscriptionPlan> list = new ArrayList<>();
            String sql = "SELECT id, plan_code, name, billing_cycle, price, currency_code, is_published, row_version, created_at, updated_at "
                    + "FROM plat.subscription_plans WHERE deleted_at IS NULL ORDER BY price ASC";
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
    public SubscriptionPlan updatePlan(UserSecurityContext context, SubscriptionPlan plan) {
        if (plan == null || plan.getId() == null) {
            throw new MalformedPayloadException("Plan ID is required");
        }

        UUID planUuid;
        try {
            planUuid = UUID.fromString(plan.getId().trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid plan ID format: " + plan.getId());
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        return effectiveContext.executeInTransaction(connectionManager, conn -> {
            String updateSql = "UPDATE plat.subscription_plans SET "
                    + "name = ?, price = ?, billing_cycle = ?, is_published = ?, updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                    + "WHERE id = ? AND deleted_at IS NULL "
                    + "RETURNING id, plan_code, name, billing_cycle, price, currency_code, is_published, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, plan.getName());
                ps.setBigDecimal(2, BigDecimal.valueOf(plan.getPrice()));
                ps.setString(3, plan.getBillingCycle());
                ps.setBoolean(4, plan.isPublished());
                ps.setObject(5, effectiveContext.getUserId());
                ps.setObject(6, planUuid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("SubscriptionPlan", plan.getId());
        });
    }

    @Override
    public void deletePlan(UserSecurityContext context, String id) {
        if (id == null) return;
        UUID planUuid;
        try {
            planUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return;
        }

        UserSecurityContext effectiveContext = context != null ? context : UserSecurityContext.forPlatformAdmin(UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880"));

        effectiveContext.executeInTransaction(connectionManager, conn -> {
            String deleteSql = "UPDATE plat.subscription_plans SET deleted_at = now(), is_published = false WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setObject(1, planUuid);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private SubscriptionPlan mapRow(ResultSet rs) throws SQLException {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(rs.getString("id"));
        p.setPlanCode(rs.getString("plan_code"));
        p.setName(rs.getString("name"));
        p.setBillingCycle(rs.getString("billing_cycle"));
        BigDecimal price = rs.getBigDecimal("price");
        p.setPrice(price != null ? price.doubleValue() : 0.0);
        p.setCurrencyCode(rs.getString("currency_code"));
        p.setPublished(rs.getBoolean("is_published"));
        p.setRowVersion(rs.getInt("row_version"));
        Timestamp ct = rs.getTimestamp("created_at");
        p.setCreatedAt(ct != null ? ct.getTime() : System.currentTimeMillis());
        Timestamp ut = rs.getTimestamp("updated_at");
        p.setUpdatedAt(ut != null ? ut.getTime() : p.getCreatedAt());
        return p;
    }
}
