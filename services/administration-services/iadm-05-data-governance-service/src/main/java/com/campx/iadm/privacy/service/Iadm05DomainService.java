package com.campx.iadm.privacy.service;

import com.campx.iadm.privacy.config.DatabaseConnectionManager;
import com.campx.iadm.privacy.exception.InstituteAdminException;
import com.campx.iadm.privacy.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain service for IADM-05 Data Governance &amp; Privacy (DSR and legal holds).
 * <p>
 * Permission-key checks, tenant-scoped PostgreSQL access, and {@link CampXLogger} audit events
 * per Agreement §3.2. Fail closed on missing authority. Never log tokens, passwords, or JWTs.
 */
public class Iadm05DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm05DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        logger.debug("IADM-05 health probe tenantId={}", ctx.getTenantId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-05");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> createDsr(UserSecurityContext ctx, String type, String subjectRef) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.write");
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.data_subject_requests (id, tenant_id, request_type, subject_ref, status, created_by) " +
                            "VALUES (?,?,?,?,'OPEN',?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, type);
                ps.setString(4, subjectRef);
                ps.setObject(5, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "OPEN");
            auditMutation(ctx, "iadm05.dsr.create", "DATA_SUBJECT_REQUEST", id.toString());
            return out;
        });
    }

    public Map<String, Object> placeLegalHold(UserSecurityContext ctx, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.write");
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM05_REASON", "Reason required");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.legal_holds (id, tenant_id, reason, active, created_by) VALUES (?,?,?,true,?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, reason);
                ps.setObject(4, ctx.getUserId());
                ps.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("holdId", id.toString());
            out.put("active", true);
            auditMutation(ctx, "iadm05.legal_hold.place", "LEGAL_HOLD", id.toString());
            return out;
        });
    }

    public Map<String, Object> listDsr(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.view");
            logger.debug("IADM-05 listDsr tenantId={}", ctx.getTenantId());
            int count = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM plat.data_subject_requests WHERE tenant_id=? AND deleted_at IS NULL")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) count = rs.getInt(1);
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("count", count);
            return out;
        });
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-05 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}