package com.campx.iadm.privacy.service;

import com.campx.iadm.privacy.config.DatabaseConnectionManager;
import com.campx.iadm.privacy.exception.InstituteAdminException;
import com.campx.iadm.privacy.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, request_type, subject_ref, status, due_date::text, created_at::text " +
                            "FROM plat.data_subject_requests WHERE tenant_id=? AND deleted_at IS NULL " +
                            "ORDER BY created_at DESC NULLS LAST LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("requestType", rs.getString(2));
                        row.put("subjectRef", rs.getString(3));
                        row.put("status", rs.getString(4));
                        row.put("dueDate", rs.getString(5));
                        row.put("createdAt", rs.getString(6));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            return out;
        });
    }

    /**
     * Thin CRUD on {@code cfg.data_retention_policies} — {@code plat.retention_policies} absent in DEV.
     */
    public Map<String, Object> listRetention(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, policy_code, entity_type, retention_days, action " +
                            "FROM cfg.data_retention_policies WHERE deleted_at IS NULL ORDER BY policy_code LIMIT 100")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString(1));
                        row.put("policyCode", rs.getString(2));
                        row.put("entityType", rs.getString(3));
                        row.put("retentionDays", rs.getInt(4));
                        row.put("action", rs.getString(5));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            out.put("source", "cfg.data_retention_policies");
            return out;
        });
    }

    public Map<String, Object> upsertRetention(UserSecurityContext ctx, String policyCode, String entityType,
                                              Integer retentionDays, String action) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.write");
            if (policyCode == null || policyCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM05_RETENTION_REQUIRED", "policyCode required");
            }
            int days = retentionDays == null ? 365 : retentionDays;
            String act = action == null || action.isEmpty() ? "ARCHIVE" : action;
            String entity = entityType == null || entityType.isEmpty() ? "GENERIC" : entityType;
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE cfg.data_retention_policies SET entity_type=?, retention_days=?, action=?, " +
                            "updated_at=now(), updated_by=? WHERE policy_code=? AND deleted_at IS NULL")) {
                upd.setString(1, entity);
                upd.setInt(2, days);
                upd.setString(3, act);
                upd.setObject(4, ctx.getUserId());
                upd.setString(5, policyCode.trim());
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO cfg.data_retention_policies (policy_code, entity_type, retention_days, action, created_by) " +
                                    "VALUES (?,?,?,?,?)")) {
                        ins.setString(1, policyCode.trim());
                        ins.setString(2, entity);
                        ins.setInt(3, days);
                        ins.setString(4, act);
                        ins.setObject(5, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("policyCode", policyCode.trim());
            out.put("entityType", entity);
            out.put("retentionDays", days);
            out.put("action", act);
            auditMutation(ctx, "iadm05.retention.upsert", "RETENTION_POLICY", policyCode.trim());
            return out;
        });
    }

    public Map<String, Object> executeDsr(UserSecurityContext ctx, UUID dsrId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.execute");
            String requestType;
            String status;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT request_type, status FROM plat.data_subject_requests " +
                            "WHERE id=? AND tenant_id=? AND deleted_at IS NULL")) {
                ps.setObject(1, dsrId);
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM05_DSR_NOT_FOUND", "DSR not found");
                    }
                    requestType = rs.getString(1);
                    status = rs.getString(2);
                }
            }
            if ("ERASURE".equalsIgnoreCase(requestType) || "ERASE".equalsIgnoreCase(requestType)) {
                try (PreparedStatement hold = conn.prepareStatement(
                        "SELECT 1 FROM plat.legal_holds WHERE tenant_id=? AND active=true AND deleted_at IS NULL LIMIT 1")) {
                    hold.setObject(1, ctx.getTenantId());
                    try (ResultSet rs = hold.executeQuery()) {
                        if (rs.next()) {
                            throw new InstituteAdminException(403, "IADM05_LEGAL_HOLD",
                                    "Active legal hold blocks erase-type DSR execution");
                        }
                    }
                }
            }
            String next = "OPEN".equalsIgnoreCase(status) || "ON_HOLD".equalsIgnoreCase(status)
                    ? "IN_PROGRESS" : "COMPLETED";
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.data_subject_requests SET status=?, updated_at=now(), updated_by=? " +
                            "WHERE id=? AND tenant_id=? AND deleted_at IS NULL")) {
                upd.setString(1, next);
                upd.setObject(2, ctx.getUserId());
                upd.setObject(3, dsrId);
                upd.setObject(4, ctx.getTenantId());
                upd.executeUpdate();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", dsrId.toString());
            out.put("requestType", requestType);
            out.put("status", next);
            auditMutation(ctx, "iadm05.dsr.execute", "DATA_SUBJECT_REQUEST", dsrId.toString());
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