package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.PlatformHealth;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.service_health} table (ADM-01 Item 15).
 * Enforces kernel RLS with platform administrator credentials.
 */
public class PostgresServiceHealthRepository implements ServiceHealthRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresServiceHealthRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresServiceHealthRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresServiceHealthRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public PlatformHealth recordHeartbeat(UserSecurityContext context, PlatformHealth health) {
        if (health == null) {
            throw new MalformedPayloadException("PlatformHealth payload cannot be null");
        }
        if (context == null) {
            throw new SecurityViolationException("Security context required for recording platform health");
        }

        String rawServiceName = health.getComponent();
        if (rawServiceName == null || rawServiceName.trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'component' (serviceName) is required");
        }
        final String serviceName = rawServiceName.trim().toUpperCase(Locale.ROOT);

        UUID parsedId;
        if (health.getId() != null && !health.getId().trim().isEmpty()) {
            try {
                parsedId = UUID.fromString(health.getId().trim());
            } catch (IllegalArgumentException e) {
                parsedId = UUID.randomUUID();
            }
        } else {
            parsedId = UUID.randomUUID();
        }
        final UUID finalId = parsedId;

        final String status = health.getStatus() != null && !health.getStatus().trim().isEmpty()
                ? health.getStatus().trim().toUpperCase(Locale.ROOT) : "HEALTHY";
        final int latency = (int) health.getLatencyMs();
        final UUID userId = context.getUserId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO plat.service_health " +
                    "(id, service_name, status, latency_ms, last_heartbeat, created_by, updated_by, row_version, deleted_at) " +
                    "VALUES (?, ?, ?, ?, now(), ?, ?, 1, NULL) " +
                    "ON CONFLICT (service_name) WHERE (deleted_at IS NULL) " +
                    "DO UPDATE SET status = EXCLUDED.status, " +
                    "              latency_ms = EXCLUDED.latency_ms, " +
                    "              last_heartbeat = EXCLUDED.last_heartbeat, " +
                    "              updated_at = now(), " +
                    "              updated_by = EXCLUDED.updated_by " +
                    "RETURNING id, service_name, status, latency_ms, last_heartbeat, created_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, finalId);
                ps.setString(2, serviceName);
                ps.setString(3, status);
                ps.setInt(4, latency);
                if (userId != null) {
                    ps.setObject(5, userId);
                    ps.setObject(6, userId);
                } else {
                    ps.setNull(5, Types.OTHER);
                    ps.setNull(6, Types.OTHER);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlatformHealth res = mapRow(rs);
                        logger.info("Recorded heartbeat for service [{}] -> status={} latency={}ms in plat.service_health",
                                serviceName, status, latency);
                        return res;
                    }
                }
            }
            throw new RuntimeException("Failed to upsert service health into plat.service_health");
        });
    }

    @Override
    public Optional<PlatformHealth> findByServiceName(UserSecurityContext context, String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return Optional.empty();
        }
        if (context == null) {
            throw new SecurityViolationException("Security context required to query service health");
        }

        final String targetName = serviceName.trim().toUpperCase(Locale.ROOT);
        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, service_name, status, latency_ms, last_heartbeat, created_at " +
                    "FROM plat.service_health " +
                    "WHERE service_name = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetName);
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
    public List<PlatformHealth> listAll(UserSecurityContext context) {
        if (context == null) {
            throw new SecurityViolationException("Security context required to list service health");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, service_name, status, latency_ms, last_heartbeat, created_at " +
                    "FROM plat.service_health " +
                    "WHERE deleted_at IS NULL " +
                    "ORDER BY service_name ASC";

            List<PlatformHealth> list = new ArrayList<>();
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

    private static PlatformHealth mapRow(ResultSet rs) throws SQLException {
        PlatformHealth h = new PlatformHealth();
        h.setId(rs.getString("id"));
        h.setComponent(rs.getString("service_name"));
        h.setStatus(rs.getString("status"));
        h.setLatencyMs(rs.getInt("latency_ms"));
        Timestamp ts = rs.getTimestamp("last_heartbeat");
        if (ts != null) {
            h.setObservedAt(ts.getTime());
        }
        return h;
    }
}
