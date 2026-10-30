package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.InstituteNotFoundException;
import com.campx.admin.institute.exception.InvalidStatusTransitionException;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.exception.UserProfileAccessDeniedException;
import com.campx.admin.institute.exception.UserProfileConflictException;
import com.campx.admin.institute.model.InstituteModels.TenantProvisioning;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC repository for {@code plat.tenant_provisioning} table (ADM-01 Item 3 / MVP-ADM01-04).
 * Operates under the authenticated security context with Row-Level Security kernel enforcement.
 * Enforces:
 * <ul>
 *   <li>Platform-admin-only mutations and retry.</li>
 *   <li>Conditional updates with expected status in the WHERE clause.</li>
 *   <li>Zero lost-race silent successes (conflict returned on lost race).</li>
 *   <li>Single transition table via {@link TenantProvisioningStateMachine}.</li>
 *   <li>Soft-delete respect and tenant isolation.</li>
 * </ul>
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

    private void checkContext(UserSecurityContext context) {
        if (context == null || context.getUserId() == null) {
            throw new SecurityViolationException("Missing required security context: userId");
        }
    }

    private void checkPlatformAdminContext(UserSecurityContext context) {
        checkContext(context);
        if (!context.isPlatformScope()) {
            throw new UserProfileAccessDeniedException("Platform administrator privileges required for tenant provisioning operations");
        }
    }

    @Override
    public TenantProvisioning createProvisioningJob(UserSecurityContext context, TenantProvisioning job) {
        checkPlatformAdminContext(context);
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
        String step = (job.getCurrentStep() != null && !job.getCurrentStep().trim().isEmpty())
                ? job.getCurrentStep().trim()
                : "INITIAL_VALIDATION";
        String status = (job.getProvisioningStatus() != null && !job.getProvisioningStatus().trim().isEmpty())
                ? TenantProvisioningStateMachine.normalizeStatus(job.getProvisioningStatus())
                : TenantProvisioningStateMachine.STATUS_REQUESTED;
        String idempotencyKey = job.getIdempotencyKey();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO plat.tenant_provisioning ("
                    + "id, tenant_id, current_step, attempt, status, idempotency_key, requested_by"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "RETURNING id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, finalJobId);
                ps.setObject(2, finalTenantId);
                ps.setString(3, step);
                ps.setInt(4, job.getAttempt() > 0 ? job.getAttempt() : 1);
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
        checkContext(context);
        if (id == null) {
            return Optional.empty();
        }

        return context.executeInTransaction(connectionManager, conn -> {
            String sql;
            if (context.isPlatformScope()) {
                sql = "SELECT id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version "
                        + "FROM plat.tenant_provisioning "
                        + "WHERE id = ? AND deleted_at IS NULL";
            } else {
                sql = "SELECT id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version "
                        + "FROM plat.tenant_provisioning "
                        + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, id);
                if (!context.isPlatformScope()) {
                    ps.setObject(2, context.getTenantId());
                }
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
        checkContext(context);

        return context.executeInTransaction(connectionManager, conn -> {
            String sql;
            UUID effectiveTenantId;

            if (!context.isPlatformScope()) {
                effectiveTenantId = context.getTenantId();
                sql = "SELECT id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version "
                        + "FROM plat.tenant_provisioning "
                        + "WHERE tenant_id = ? AND deleted_at IS NULL "
                        + "ORDER BY created_at DESC";
            } else {
                effectiveTenantId = tenantId;
                sql = "SELECT id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version "
                        + "FROM plat.tenant_provisioning "
                        + "WHERE (?::uuid IS NULL OR tenant_id = ?) AND deleted_at IS NULL "
                        + "ORDER BY created_at DESC";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (!context.isPlatformScope()) {
                    ps.setObject(1, effectiveTenantId);
                } else {
                    ps.setObject(1, effectiveTenantId);
                    ps.setObject(2, effectiveTenantId);
                }

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
        return updateProvisioningStatus(context, id, null, status, currentStep);
    }

    @Override
    public TenantProvisioning updateProvisioningStatus(UserSecurityContext context, UUID id, String expectedCurrentStatus, String newStatus, String currentStep) {
        checkPlatformAdminContext(context);
        if (id == null) {
            throw new MalformedPayloadException("Job ID cannot be null");
        }
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new MalformedPayloadException("New status cannot be null or empty");
        }

        String targetNorm = TenantProvisioningStateMachine.normalizeStatus(newStatus);

        return context.executeInTransaction(connectionManager, conn -> {
            // Determine expected current status if not explicitly supplied
            String effectiveExpected = expectedCurrentStatus;
            if (effectiveExpected == null || effectiveExpected.trim().isEmpty()) {
                String selectSql = "SELECT status, deleted_at FROM plat.tenant_provisioning WHERE id = ?";
                try (PreparedStatement psSel = conn.prepareStatement(selectSql)) {
                    psSel.setObject(1, id);
                    try (ResultSet rsSel = psSel.executeQuery()) {
                        if (!rsSel.next() || rsSel.getTimestamp("deleted_at") != null) {
                            throw new InstituteNotFoundException("ProvisioningJob", id.toString());
                        }
                        effectiveExpected = rsSel.getString("status");
                    }
                }
            }

            // Validate transition through single state machine
            TenantProvisioningStateMachine.validateTransition(effectiveExpected, targetNorm, false);

            // Conditional update enforcing expected current status in the WHERE clause
            String updateSql = "UPDATE plat.tenant_provisioning "
                    + "SET status = ?, current_step = COALESCE(?, current_step), updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                    + "WHERE id = ? AND status = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, targetNorm);
                ps.setString(2, currentStep);
                ps.setObject(3, context.getUserId());
                ps.setObject(4, id);
                ps.setString(5, effectiveExpected);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }

            // Zero rows returned: check if missing or lost race
            String checkSql = "SELECT status, deleted_at FROM plat.tenant_provisioning WHERE id = ?";
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setObject(1, id);
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (!rsCheck.next() || rsCheck.getTimestamp("deleted_at") != null) {
                        throw new InstituteNotFoundException("ProvisioningJob", id.toString());
                    }
                    String actualStatus = rsCheck.getString("status");
                    throw new UserProfileConflictException("ADM01_PROVISIONING_CONFLICT",
                            "Conditional update conflict: provisioning job " + id + " has status '" + actualStatus + "', expected '" + effectiveExpected + "'");
                }
            }
        });
    }

    @Override
    public TenantProvisioning retryProvisioningJob(UserSecurityContext context, UUID id) {
        checkPlatformAdminContext(context);
        if (id == null) {
            throw new MalformedPayloadException("Job ID cannot be null");
        }

        return context.executeInTransaction(connectionManager, conn -> {
            // Validate transition FAILED -> IN_PROGRESS for retry
            TenantProvisioningStateMachine.validateTransition(TenantProvisioningStateMachine.STATUS_FAILED, TenantProvisioningStateMachine.STATUS_IN_PROGRESS, true);

            // Fetch prior record for failure reason and attempt
            String selectSql = "SELECT status, attempt, failure_reason, deleted_at FROM plat.tenant_provisioning WHERE id = ?";
            String priorFailureReason = null;
            int priorAttempt = 1;
            try (PreparedStatement psSel = conn.prepareStatement(selectSql)) {
                psSel.setObject(1, id);
                try (ResultSet rsSel = psSel.executeQuery()) {
                    if (!rsSel.next() || rsSel.getTimestamp("deleted_at") != null) {
                        throw new InstituteNotFoundException("ProvisioningJob", id.toString());
                    }
                    String actualStatus = rsSel.getString("status");
                    if (TenantProvisioningStateMachine.STATUS_COMPLETED.equalsIgnoreCase(actualStatus)) {
                        throw new InvalidStatusTransitionException(
                                "State 'COMPLETED' is terminal and immutable. No further transitions are permitted");
                    }
                    if (TenantProvisioningStateMachine.STATUS_IN_PROGRESS.equalsIgnoreCase(actualStatus)) {
                        throw new UserProfileConflictException("ADM01_PROVISIONING_CONFLICT",
                                "Concurrent double-retry conflict for provisioning job: " + id);
                    }
                    if (!TenantProvisioningStateMachine.STATUS_FAILED.equalsIgnoreCase(actualStatus)) {
                        throw new InvalidStatusTransitionException(actualStatus, TenantProvisioningStateMachine.STATUS_IN_PROGRESS);
                    }
                    priorAttempt = rsSel.getInt("attempt");
                    priorFailureReason = rsSel.getString("failure_reason");
                }
            }

            // Conditional update: only succeeds if current status is FAILED
            String updateSql = "UPDATE plat.tenant_provisioning "
                    + "SET status = 'IN_PROGRESS', current_step = 'RETRY_INITIATED', attempt = attempt + 1, "
                    + "updated_at = now(), updated_by = ?, row_version = row_version + 1 "
                    + "WHERE id = ? AND status = 'FAILED' AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, current_step, attempt, status, idempotency_key, requested_by, failure_reason, created_at, updated_at, deleted_at, row_version";

            TenantProvisioning updated;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        updated = mapRow(rs);
                    } else {
                        // Lost race in concurrent double-retry
                        throw new UserProfileConflictException("ADM01_PROVISIONING_CONFLICT",
                                "Concurrent double-retry conflict for provisioning job: " + id);
                    }
                }
            }

            // Record retry via existing audit mechanism
            AuditEvent audit = AuditEvent.builder()
                    .action("TENANT_PROVISIONING_RETRIED")
                    .principalId(context.getUserId().toString())
                    .principalRole("PLATFORM_ADMIN")
                    .resourceType("TENANT_PROVISIONING")
                    .resourceId(id.toString())
                    .status("SUCCESS")
                    .description(String.format("Retried tenant provisioning job: %s (attempt %d, prior failure reason: '%s')",
                            id, updated.getAttempt(), priorFailureReason != null ? priorFailureReason : "N/A"))
                    .build();
            logger.audit(audit);

            return updated;
        });
    }

    private TenantProvisioning mapRow(ResultSet rs) throws SQLException {
        TenantProvisioning job = new TenantProvisioning();
        job.setProvisioningId(rs.getString("id"));
        job.setTenantId(rs.getString("tenant_id"));
        job.setCurrentStep(rs.getString("current_step"));
        job.setAttempt(rs.getInt("attempt"));
        job.setProvisioningStatus(rs.getString("status"));
        job.setIdempotencyKey(rs.getString("idempotency_key"));
        job.setRequestedBy(rs.getString("requested_by"));
        job.setFailureReason(rs.getString("failure_reason"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            job.setRequestedAt(createdAt.getTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            job.setUpdatedAt(updatedAt.getTime());
        }

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) {
            job.setDeletedAt(deletedAt.getTime());
        }

        job.setRowVersion(rs.getInt("row_version"));
        return job;
    }
}
