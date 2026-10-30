package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.UsageMetric;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.usage_metrics} table (ADM-01 Item 14).
 * Enforces multi-tenant kernel RLS via {@link UserSecurityContext}.
 */
public class PostgresUsageMetricRepository implements UsageMetricRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresUsageMetricRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresUsageMetricRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresUsageMetricRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public UsageMetric recordUsageMetric(UserSecurityContext context, UsageMetric metric) {
        if (metric == null) {
            throw new MalformedPayloadException("Usage metric payload cannot be null");
        }
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Security context with valid tenantId required for usage metric persistence");
        }

        UUID tenantUuid = context.getTenantId();
        if (metric.getTenantId() != null && !metric.getTenantId().trim().isEmpty()) {
            try {
                UUID payloadTenant = UUID.fromString(metric.getTenantId().trim());
                if (!payloadTenant.equals(tenantUuid)) {
                    throw new SecurityViolationException("Cannot record usage metric for cross-tenant target: " + payloadTenant);
                }
            } catch (IllegalArgumentException e) {
                throw new MalformedPayloadException("Invalid tenantId UUID format: " + metric.getTenantId());
            }
        }

        if (metric.getMetricType() == null || metric.getMetricType().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'metricType' is required");
        }

        UUID parsedMetricUuid;
        if (metric.getId() != null && !metric.getId().trim().isEmpty()) {
            try {
                parsedMetricUuid = UUID.fromString(metric.getId().trim());
            } catch (IllegalArgumentException e) {
                parsedMetricUuid = UUID.randomUUID();
            }
        } else {
            parsedMetricUuid = UUID.randomUUID();
        }
        final UUID finalMetricUuid = parsedMetricUuid;

        // Determine recorded_on date
        java.sql.Date parsedRecDate;
        if (metric.getRecordedOn() != null && !metric.getRecordedOn().trim().isEmpty()) {
            try {
                parsedRecDate = java.sql.Date.valueOf(metric.getRecordedOn().trim());
            } catch (IllegalArgumentException e) {
                parsedRecDate = new java.sql.Date(System.currentTimeMillis());
            }
        } else if (metric.getPeriod() != null && metric.getPeriod().trim().length() == 7) {
            try {
                parsedRecDate = java.sql.Date.valueOf(metric.getPeriod().trim() + "-01");
            } catch (IllegalArgumentException e) {
                parsedRecDate = new java.sql.Date(System.currentTimeMillis());
            }
        } else {
            parsedRecDate = new java.sql.Date(System.currentTimeMillis());
        }
        final java.sql.Date finalRecDate = parsedRecDate;

        final String metricName = metric.getMetricType().trim();
        final String dimension = metric.getDimension() != null ? metric.getDimension().trim() : metric.getPeriod();
        final BigDecimal metricVal = BigDecimal.valueOf(metric.getValue());
        final UUID userId = context.getUserId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO plat.usage_metrics " +
                    "(id, tenant_id, metric_name, dimension, metric_value, recorded_on, created_by, updated_by, row_version, deleted_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, NULL) " +
                    "RETURNING id, tenant_id, metric_name, dimension, metric_value, recorded_on, created_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, finalMetricUuid);
                ps.setObject(2, tenantUuid);
                ps.setString(3, metricName);
                if (dimension != null && !dimension.isEmpty()) {
                    ps.setString(4, dimension);
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                ps.setBigDecimal(5, metricVal);
                ps.setDate(6, finalRecDate);
                if (userId != null) {
                    ps.setObject(7, userId);
                    ps.setObject(8, userId);
                } else {
                    ps.setNull(7, Types.OTHER);
                    ps.setNull(8, Types.OTHER);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UsageMetric res = mapRow(rs);
                        logger.info("Persisted usage metric [{}] for tenant [{}] in plat.usage_metrics", res.getMetricType(), tenantUuid);
                        return res;
                    }
                }
            }
            throw new RuntimeException("Failed to persist usage metric into plat.usage_metrics");
        });
    }

    @Override
    public List<UsageMetric> findByTenantAndPeriod(UserSecurityContext context, UUID tenantId, String period) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Security context required to query usage metrics");
        }
        if (tenantId == null) {
            tenantId = context.getTenantId();
        }
        if (!context.getTenantId().equals(tenantId)) {
            throw new SecurityViolationException("Cross-tenant access violation: querying " + tenantId + " from " + context.getTenantId());
        }

        final UUID tid = tenantId;
        return context.executeInTransaction(connectionManager, conn -> {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT id, tenant_id, metric_name, dimension, metric_value, recorded_on, created_at ")
              .append("FROM plat.usage_metrics ")
              .append("WHERE tenant_id = ? AND deleted_at IS NULL ");

            boolean hasPeriod = period != null && !period.trim().isEmpty();
            if (hasPeriod) {
                sb.append("AND (to_char(recorded_on, 'YYYY-MM') = ? OR dimension = ?) ");
            }
            sb.append("ORDER BY recorded_on DESC, created_at DESC");

            List<UsageMetric> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                ps.setObject(1, tid);
                if (hasPeriod) {
                    ps.setString(2, period.trim());
                    ps.setString(3, period.trim());
                }

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
    public List<UsageMetric> findByTenant(UserSecurityContext context, UUID tenantId) {
        return findByTenantAndPeriod(context, tenantId, null);
    }

    @Override
    public Optional<UsageMetric> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Security context required to query usage metric by id");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, metric_name, dimension, metric_value, recorded_on, created_at " +
                    "FROM plat.usage_metrics " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
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
    public void deleteById(UserSecurityContext context, UUID id) {
        if (id == null) return;
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Security context required to delete usage metric");
        }

        context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.usage_metrics SET deleted_at = now(), updated_at = now() " +
                    "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                ps.setObject(2, context.getTenantId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private static UsageMetric mapRow(ResultSet rs) throws SQLException {
        UsageMetric m = new UsageMetric();
        m.setId(rs.getString("id"));
        m.setTenantId(rs.getString("tenant_id"));
        m.setMetricType(rs.getString("metric_name"));
        m.setDimension(rs.getString("dimension"));
        BigDecimal val = rs.getBigDecimal("metric_value");
        m.setValue(val != null ? val.longValue() : 0L);

        java.sql.Date rec = rs.getDate("recorded_on");
        if (rec != null) {
            m.setRecordedOn(rec.toString());
            m.setPeriod(rec.toString().length() >= 7 ? rec.toString().substring(0, 7) : rec.toString());
        }
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) {
            m.setCalculatedAt(ca.getTime());
        }
        return m;
    }
}
