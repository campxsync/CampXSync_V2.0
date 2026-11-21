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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Domain service for IADM-05 Data Governance &amp; Privacy (DSR and legal holds).
 * <p>
 * Permission-key checks, tenant-scoped PostgreSQL access, and {@link CampXLogger} audit events
 * per Agreement §3.2. Fail closed on missing authority. Never log tokens, passwords, or JWTs.
 */
public class Iadm05DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm05DomainService.class);
    private static final Set<String> VALID_SENSITIVITIES =
            new HashSet<>(Arrays.asList("LOW", "MEDIUM", "HIGH", "RESTRICTED"));

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
     * IADM-05-US-001: institute retention on {@code plat.institute_retention_policies}
     * with floor from {@code plat.retention_minimums}.
     */
    public Map<String, Object> listRetention(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, policy_code, entity_type, retention_days, action " +
                            "FROM plat.institute_retention_policies " +
                            "WHERE tenant_id=? AND deleted_at IS NULL ORDER BY policy_code LIMIT 100")) {
                ps.setObject(1, ctx.getTenantId());
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
            out.put("source", "plat.institute_retention_policies");
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
            if (days <= 0) {
                throw new InstituteAdminException(400, "IADM05_RETENTION_DAYS", "retentionDays must be > 0");
            }
            String act = action == null || action.isEmpty() ? "ARCHIVE" : action.trim().toUpperCase();
            if (!("ARCHIVE".equals(act) || "DELETE".equals(act) || "ANONYMIZE".equals(act))) {
                throw new InstituteAdminException(400, "IADM05_RETENTION_ACTION",
                        "action must be ARCHIVE, DELETE, or ANONYMIZE");
            }
            String entity = entityType == null || entityType.isEmpty() ? "GENERIC" : entityType.trim();
            int minDays = 0;
            try (PreparedStatement min = conn.prepareStatement(
                    "SELECT min_days FROM plat.retention_minimums WHERE entity_type=? AND deleted_at IS NULL")) {
                min.setString(1, entity);
                try (ResultSet rs = min.executeQuery()) {
                    if (rs.next()) minDays = rs.getInt(1);
                }
            }
            if (minDays == 0) {
                try (PreparedStatement min = conn.prepareStatement(
                        "SELECT min_days FROM plat.retention_minimums WHERE entity_type='GENERIC' AND deleted_at IS NULL")) {
                    try (ResultSet rs = min.executeQuery()) {
                        if (rs.next()) minDays = rs.getInt(1);
                    }
                }
            }
            if (minDays > 0 && days < minDays) {
                throw new InstituteAdminException(400, "IADM05_BELOW_MINIMUM",
                        "retentionDays " + days + " is below platform minimum " + minDays + " for " + entity);
            }
            String code = policyCode.trim();
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE plat.institute_retention_policies SET entity_type=?, retention_days=?, action=?, " +
                            "updated_at=now(), updated_by=? " +
                            "WHERE tenant_id=? AND policy_code=? AND deleted_at IS NULL")) {
                upd.setString(1, entity);
                upd.setInt(2, days);
                upd.setString(3, act);
                upd.setObject(4, ctx.getUserId());
                upd.setObject(5, ctx.getTenantId());
                upd.setString(6, code);
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO plat.institute_retention_policies " +
                                    "(tenant_id, policy_code, entity_type, retention_days, action, created_by) " +
                                    "VALUES (?,?,?,?,?,?)")) {
                        ins.setObject(1, ctx.getTenantId());
                        ins.setString(2, code);
                        ins.setString(3, entity);
                        ins.setInt(4, days);
                        ins.setString(5, act);
                        ins.setObject(6, ctx.getUserId());
                        ins.executeUpdate();
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("policyCode", code);
            out.put("entityType", entity);
            out.put("retentionDays", days);
            out.put("action", act);
            out.put("platformMinimumDays", minDays);
            auditMutation(ctx, "iadm05.retention.upsert", "RETENTION_POLICY", code);
            return out;
        });
    }

    /**
     * Privacy notices proxy via {@code cfg.data_classifications}
     * (columns: classification_code, sensitivity_level, encryption_required — no name/description).
     */
    public Map<String, Object> listPrivacyNotices(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.view");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id::text, classification_code, sensitivity_level, encryption_required, created_at::text " +
                            "FROM cfg.data_classifications WHERE deleted_at IS NULL " +
                            "ORDER BY classification_code LIMIT 100")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        String code = rs.getString(2);
                        String sens = rs.getString(3);
                        row.put("id", rs.getString(1));
                        row.put("classificationCode", code);
                        row.put("name", code);
                        row.put("description", sens);
                        row.put("sensitivityLevel", sens);
                        row.put("encryptionRequired", rs.getBoolean(4));
                        row.put("createdAt", rs.getString(5));
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("items", items);
            out.put("count", items.size());
            out.put("source", "cfg.data_classifications");
            return out;
        });
    }

    public Map<String, Object> createPrivacyNotice(UserSecurityContext ctx, String classificationCode,
                                                   String name, String description, String sensitivityLevel,
                                                   Boolean encryptionRequired) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.write");
            if (classificationCode == null || classificationCode.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM05_NOTICE_CODE", "classificationCode required");
            }
            String code = classificationCode.trim().toUpperCase(Locale.ROOT);
            String sens = sensitivityLevel;
            if (sens == null || sens.trim().isEmpty()) {
                if (name != null && VALID_SENSITIVITIES.contains(name.trim().toUpperCase(Locale.ROOT))) {
                    sens = name.trim().toUpperCase(Locale.ROOT);
                } else if (description != null && VALID_SENSITIVITIES.contains(description.trim().toUpperCase(Locale.ROOT))) {
                    sens = description.trim().toUpperCase(Locale.ROOT);
                } else {
                    sens = "MEDIUM";
                }
            } else {
                sens = sens.trim().toUpperCase(Locale.ROOT);
            }
            if (!VALID_SENSITIVITIES.contains(sens)) {
                throw new InstituteAdminException(400, "IADM05_SENSITIVITY",
                        "sensitivityLevel must be LOW|MEDIUM|HIGH|RESTRICTED");
            }
            boolean encrypt = encryptionRequired != null && encryptionRequired;
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cfg.data_classifications " +
                            "(id, classification_code, sensitivity_level, encryption_required, created_by, updated_by, row_version) " +
                            "VALUES (?,?,?,?,?,?,1)")) {
                ps.setObject(1, id);
                ps.setString(2, code);
                ps.setString(3, sens);
                ps.setBoolean(4, encrypt);
                ps.setObject(5, ctx.getUserId());
                ps.setObject(6, ctx.getUserId());
                ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new InstituteAdminException(409, "IADM05_NOTICE_EXISTS", "classificationCode exists");
                }
                throw e;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id.toString());
            out.put("classificationCode", code);
            out.put("name", name == null || name.isEmpty() ? code : name);
            out.put("description", description == null || description.isEmpty() ? sens : description);
            out.put("sensitivityLevel", sens);
            out.put("encryptionRequired", encrypt);
            auditMutation(ctx, "iadm05.notice.create", "DATA_CLASSIFICATION", code);
            return out;
        });
    }

    public Map<String, Object> executeDsr(UserSecurityContext ctx, UUID dsrId, UUID callerCollegeId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "iadm05.execute");
            String requestType;
            String status;
            UUID assignedCollegeId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT request_type, status, assigned_college_id " +
                            "FROM plat.data_subject_requests " +
                            "WHERE id=? AND tenant_id=? AND deleted_at IS NULL")) {
                ps.setObject(1, dsrId);
                ps.setObject(2, ctx.getTenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM05_DSR_NOT_FOUND", "DSR not found");
                    }
                    requestType = rs.getString(1);
                    status = rs.getString(2);
                    assignedCollegeId = (UUID) rs.getObject(3);
                }
            }
            if (assignedCollegeId != null) {
                if (callerCollegeId == null || !assignedCollegeId.equals(callerCollegeId)) {
                    throw new InstituteAdminException(403, "IADM05_COLLEGE_SCOPE",
                            "DSR assigned to a different college");
                }
            }
            boolean erasure = "ERASURE".equalsIgnoreCase(requestType) || "ERASE".equalsIgnoreCase(requestType)
                    || "DELETE".equalsIgnoreCase(requestType);
            if (erasure) {
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
            // US-005 thin: OPEN/ON_HOLD -> IN_PROGRESS (domain instruct note); else COMPLETED
            String next;
            String executionNote;
            if ("OPEN".equalsIgnoreCase(status) || "ON_HOLD".equalsIgnoreCase(status)) {
                next = "IN_PROGRESS";
                executionNote = "DOMAIN_INSTRUCT:" + requestType + ":handlers_notified";
            } else if ("IN_PROGRESS".equalsIgnoreCase(status)) {
                next = "COMPLETED";
                executionNote = "DOMAIN_TRACK:" + requestType + ":completed";
            } else {
                next = "COMPLETED";
                executionNote = "DOMAIN_TRACK:" + requestType + ":already_" + status;
            }
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
            out.put("previousStatus", status);
            out.put("status", next);
            out.put("executionNote", executionNote);
            out.put("assignedCollegeId", assignedCollegeId == null ? null : assignedCollegeId.toString());
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