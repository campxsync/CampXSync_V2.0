package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.OperationalAlert;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.alerts} table (ADM-01 Item 15).
 * Enforces kernel RLS with platform administrator credentials.
 */
public class PostgresOperationalAlertRepository implements OperationalAlertRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresOperationalAlertRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresOperationalAlertRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresOperationalAlertRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public OperationalAlert createAlert(UserSecurityContext context, OperationalAlert alert) {
        if (alert == null) {
            throw new MalformedPayloadException("OperationalAlert payload cannot be null");
        }
        if (context == null) {
            throw new SecurityViolationException("Security context required to create alert");
        }

        UUID parsedId;
        if (alert.getId() != null && !alert.getId().trim().isEmpty()) {
            try {
                parsedId = UUID.fromString(alert.getId().trim());
            } catch (IllegalArgumentException e) {
                parsedId = UUID.randomUUID();
            }
        } else {
            parsedId = UUID.randomUUID();
        }
        final UUID finalId = parsedId;

        String rawTitle = alert.getAlertCode() != null && !alert.getAlertCode().trim().isEmpty()
                ? alert.getAlertCode().trim() : alert.getMessage();
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            rawTitle = "ALERT_" + UUID.randomUUID().toString().substring(0, 8);
        }
        final String title = rawTitle;
        final String detail = alert.getMessage() != null ? alert.getMessage().trim() : "";
        final String severity = alert.getSeverity() != null && !alert.getSeverity().trim().isEmpty()
                ? alert.getSeverity().trim().toUpperCase(Locale.ROOT) : "INFO";
        final String rawStatus = alert.getStatus() != null && !alert.getStatus().trim().isEmpty()
                ? alert.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        final String status = "OPEN".equalsIgnoreCase(rawStatus) ? "ACTIVE" : rawStatus;

        final UUID tenantId = context.getTenantId();
        final UUID userId = context.getUserId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO plat.alerts (" +
                    "id, tenant_ref_id, severity, title, detail, status, created_by, updated_by, row_version, deleted_at" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, NULL) " +
                    "RETURNING id, tenant_ref_id, severity, title, detail, status, created_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, finalId);
                if (tenantId != null) {
                    ps.setObject(2, tenantId);
                } else {
                    ps.setNull(2, Types.OTHER);
                }
                ps.setString(3, severity);
                ps.setString(4, title);
                ps.setString(5, detail);
                ps.setString(6, status);
                if (userId != null) {
                    ps.setObject(7, userId);
                    ps.setObject(8, userId);
                } else {
                    ps.setNull(7, Types.OTHER);
                    ps.setNull(8, Types.OTHER);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        OperationalAlert res = mapRow(rs);
                        logger.info("Persisted alert [{}] severity={} in plat.alerts", title, severity);
                        return res;
                    }
                }
            }
            throw new RuntimeException("Failed to persist alert in plat.alerts");
        });
    }

    @Override
    public Optional<OperationalAlert> findById(UserSecurityContext context, UUID id) {
        if (id == null) return Optional.empty();
        if (context == null) {
            throw new SecurityViolationException("Security context required to find alert");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_ref_id, severity, title, detail, status, created_at " +
                    "FROM plat.alerts " +
                    "WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
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
    public List<OperationalAlert> listAlerts(UserSecurityContext context, String status) {
        if (context == null) {
            throw new SecurityViolationException("Security context required to list alerts");
        }

        final String targetStatus = (status != null && !status.trim().isEmpty())
                ? ("OPEN".equalsIgnoreCase(status.trim()) ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT)) : null;

        return context.executeInTransaction(connectionManager, conn -> {
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT id, tenant_ref_id, severity, title, detail, status, created_at ")
              .append("FROM plat.alerts ")
              .append("WHERE deleted_at IS NULL ");

            if (targetStatus != null) {
                sb.append("AND status = ? ");
            }
            sb.append("ORDER BY created_at DESC");

            List<OperationalAlert> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                if (targetStatus != null) {
                    ps.setString(1, targetStatus);
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
    public OperationalAlert acknowledgeAlert(UserSecurityContext context, UUID id) {
        if (id == null) throw new ResourceNotFoundException("OperationalAlert", "null");
        if (context == null) {
            throw new SecurityViolationException("Security context required to acknowledge alert");
        }

        final UUID ackUserId = context.getUserId();
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.alerts " +
                    "SET status = 'ACKNOWLEDGED', acknowledged_by = ?, updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_ref_id, severity, title, detail, status, created_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (ackUserId != null) {
                    ps.setObject(1, ackUserId);
                    ps.setObject(2, ackUserId);
                } else {
                    ps.setNull(1, Types.OTHER);
                    ps.setNull(2, Types.OTHER);
                }
                ps.setObject(3, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("OperationalAlert", id.toString());
        });
    }

    @Override
    public OperationalAlert resolveAlert(UserSecurityContext context, UUID id) {
        if (id == null) throw new ResourceNotFoundException("OperationalAlert", "null");
        if (context == null) {
            throw new SecurityViolationException("Security context required to resolve alert");
        }

        final UUID resolveUserId = context.getUserId();
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.alerts " +
                    "SET status = 'RESOLVED', resolved_at = now(), updated_at = now(), updated_by = ? " +
                    "WHERE id = ? AND deleted_at IS NULL " +
                    "RETURNING id, tenant_ref_id, severity, title, detail, status, created_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (resolveUserId != null) {
                    ps.setObject(1, resolveUserId);
                } else {
                    ps.setNull(1, Types.OTHER);
                }
                ps.setObject(2, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new ResourceNotFoundException("OperationalAlert", id.toString());
        });
    }

    private static OperationalAlert mapRow(ResultSet rs) throws SQLException {
        OperationalAlert a = new OperationalAlert();
        a.setId(rs.getString("id"));
        a.setAlertCode(rs.getString("title"));
        a.setMessage(rs.getString("detail"));
        a.setSeverity(rs.getString("severity"));
        a.setStatus(rs.getString("status"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) {
            a.setCreatedAt(ca.getTime());
        }
        return a;
    }
}
