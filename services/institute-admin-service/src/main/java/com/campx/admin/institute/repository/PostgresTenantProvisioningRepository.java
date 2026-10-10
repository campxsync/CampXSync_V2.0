package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code plat.tenant_provisioning} table (ADM-01 Item 3).
 * Operates under the authenticated security context with Row-Level Security kernel enforcement.
 */
public class PostgresTenantProvisioningRepository implements TenantProvisioningRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresTenantProvisioningRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresTenantProvisioningRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresTenantProvisioningRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public TenantProvisioning createProvisioningJob(UserSecurityContext context, TenantProvisioning job) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (job == null) {
            throw new MalformedPayloadException("Provisioning job cannot be null");
        }

        UUID jobId;
        if (job.getProvisioningId() != null && !job.getProvisioningId().trim().isEmpty()) {
            try {
                jobId = UUID.fromString(job.getProvisioningId().trim());
            } catch (IllegalArgumentException e) {
                jobId = UUID.randomUUID();
            }
        } else {
            jobId = UUID.randomUUID();
        }

        UUID tenantId;
        if (job.getTenantId() != null && !job.getTenantId().trim().isEmpty()) {
            try {
                tenantId = UUID.fromString(job.getTenantId().trim());
            } catch (IllegalArgumentException e) {
                tenantId = context.getTenantId();
            }
        } else {
            tenantId = context.getTenantId();
        }

        UUID finalJobId = jobId;
        UUID finalTenantId = tenantId;
        String step = "INITIAL_VALIDATION";
        String status = job.getProvisioningStatus() != null ? job.getProvisioningStatus().trim().toUpperCase() : "REQUESTED";
        String idempotencyKey = job.getIdempotencyKey();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO plat.tenant_provisioning ("
                    + "id, tenant_id, current_step, attempt, status, idempotency_key, requested_by"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "RETURNING id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, finalJobId);
                ps.setObject(2, finalTenantId);
                ps.setString(3, step);
                ps.setInt(4, 1);
                ps.setString(5, status);
                ps.setString(6, idempotencyKey);
                ps.setObject(7, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    throw new SQLException("Insert into plat.tenant_provisioning returned no rows");
                }
            }
        });
    }

    @Override
    public Optional<TenantProvisioning> getProvisioningJobById(UserSecurityContext context, UUID id) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, created_at, updated_at "
                    + "FROM plat.tenant_provisioning "
                    + "WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public List<TenantProvisioning> listProvisioningJobs(UserSecurityContext context, UUID tenantId) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, created_at, updated_at "
                    + "FROM plat.tenant_provisioning "
                    + "WHERE (?::uuid IS NULL OR tenant_id = ?) AND deleted_at IS NULL "
                    + "ORDER BY created_at DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, tenantId);

                try (ResultSet rs = ps.executeQuery()) {
                    List<TenantProvisioning> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                    return list;
                }
            }
        });
    }

    @Override
    public TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String status, String currentStep) {
        if (context == null) {
            throw new UserProfileAccessDeniedException("Missing required security context");
        }
        if (id == null) {
            throw new MalformedPayloadException("Job ID cannot be null");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.tenant_provisioning "
                    + "SET status = ?, current_step = COALESCE(?, current_step), updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setString(2, currentStep);
                ps.setObject(3, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    throw new InstituteNotFoundException("ProvisioningJob", id.toString());
                }
            }
        });
    }

    private TenantProvisioning mapRow(ResultSet rs) throws SQLException {
        TenantProvisioning job = new TenantProvisioning();
        job.setProvisioningId(rs.getString("id"));
        job.setTenantId(rs.getString("tenant_id"));
        job.setProvisioningStatus(rs.getString("status"));
        job.setIdempotencyKey(rs.getString("idempotency_key"));
        job.setRequestedBy(rs.getString("requested_by"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            // requestedAt
        }
        return job;
    }
}
