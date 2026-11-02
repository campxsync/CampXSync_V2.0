package com.campx.admissions.repository;

import com.campx.admissions.config.DatabaseConnectionManager;
import com.campx.admissions.model.ApplicationStatus;
import com.campx.admissions.model.ApplicationStatusHistoryEntity;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code stu.application_status_history} table.
 */
public class PostgresStatusHistoryRepository implements StatusHistoryRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresStatusHistoryRepository.class);
    private final DatabaseConnectionManager connectionManager;

    public PostgresStatusHistoryRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresStatusHistoryRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public ApplicationStatusHistoryEntity save(ApplicationStatusHistoryEntity history) {
        if (history == null) throw new IllegalArgumentException("History cannot be null");
        if (history.getTenantId() == null) throw new IllegalArgumentException("TenantId is required");

        if (history.getId() == null) {
            history.setId(UUID.randomUUID());
        }

        String sql = "INSERT INTO stu.application_status_history (" +
                "tenant_id, id, application_id, from_status, to_status, transition_action, " +
                "transition_reason, actor_id, actor_role, correlation_id, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setObject(idx++, history.getTenantId());
            ps.setObject(idx++, history.getId());
            ps.setObject(idx++, history.getApplicationId());
            ps.setString(idx++, history.getFromStatus() != null ? history.getFromStatus().name() : null);
            ps.setString(idx++, history.getToStatus() != null ? history.getToStatus().name() : null);
            ps.setString(idx++, history.getTransitionAction());
            ps.setString(idx++, history.getTransitionReason());
            ps.setObject(idx++, history.getActorId());
            ps.setString(idx++, history.getActorRole());
            ps.setString(idx++, history.getCorrelationId());
            ps.setObject(idx++, history.getCreatedAt() != null ? history.getCreatedAt() : OffsetDateTime.now());

            ps.executeUpdate();
            return history;

        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL save failed for application status history: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ApplicationStatusHistoryEntity> findByApplication(UUID tenantId, UUID applicationId) {
        String sql = "SELECT * FROM stu.application_status_history WHERE tenant_id = ? AND application_id = ? ORDER BY created_at ASC";
        List<ApplicationStatusHistoryEntity> results = new ArrayList<>();
        try (Connection conn = connectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setObject(1, tenantId);
            ps.setObject(2, applicationId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ApplicationStatusHistoryEntity h = new ApplicationStatusHistoryEntity();
                    h.setTenantId(rs.getObject("tenant_id", UUID.class));
                    h.setId(rs.getObject("id", UUID.class));
                    h.setApplicationId(rs.getObject("application_id", UUID.class));

                    String from = rs.getString("from_status");
                    if (from != null) h.setFromStatus(ApplicationStatus.valueOf(from));

                    String to = rs.getString("to_status");
                    if (to != null) h.setToStatus(ApplicationStatus.valueOf(to));

                    h.setTransitionAction(rs.getString("transition_action"));
                    h.setTransitionReason(rs.getString("transition_reason"));
                    h.setActorId(rs.getObject("actor_id", UUID.class));
                    h.setActorRole(rs.getString("actor_role"));
                    h.setCorrelationId(rs.getString("correlation_id"));
                    h.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));

                    results.add(h);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL query failed: " + e.getMessage(), e);
        }
        return results;
    }
}

