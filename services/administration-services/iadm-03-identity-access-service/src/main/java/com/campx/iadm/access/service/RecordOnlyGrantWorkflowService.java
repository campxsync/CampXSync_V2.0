package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03-US-022 / US-003 / US-004 / US-005 / US-006 / US-007 / US-013:
 * Record-only grant request workflow engine.
 * States: REQUESTED -> VALIDATED -> APPROVED/REJECTED -> RECORDED.
 * Never modifies iam.permissions, iam.role_permissions, iam.role_template_permissions, or iam.role_assignments.
 */
public class RecordOnlyGrantWorkflowService {

    public Map<String, Object> listRequests(UserSecurityContext ctx,
                                            UUID requestedTenantId,
                                            String statusFilter,
                                            String correlationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!("R01".equals(persona) || "R02".equals(persona) || "R03".equals(persona)
                        || "R04".equals(persona) || "R28".equals(persona))) {
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " cannot view grant requests.");
                }
                if ("R03".equals(persona) || "R04".equals(persona)) {
                    if (ctx.getTenantId() == null) {
                        throw new InstituteAdminException(403, "IADM03_SCOPE_REQUIRED",
                                "Authenticated institute scope is required.");
                    }
                    if (requestedTenantId != null && !requestedTenantId.equals(ctx.getTenantId())) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                                "IADM03_CROSS_TENANT_DENIED", "INSTITUTE:" + ctx.getTenantId(),
                                "GRANT_REQUEST", null, requestedTenantId.toString(), "403", correlationId,
                                "Cross-tenant grant request list denied");
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_CROSS_TENANT_FORBIDDEN",
                                "Cannot view grant requests of another institute.");
                    }
                }

                StringBuilder sql = new StringBuilder(
                        "SELECT id, tenant_id, college_id, requester_user_id, subject_user_id, " +
                                "target_kind, target_code, scope_type, scope_id, valid_from, valid_to, " +
                                "reason, status, validation_diff::text, validated_by, validated_at, " +
                                "decided_by, decided_at, decision_reason, recorded_at, audit_event_id, created_at " +
                                "FROM iadm03.grant_requests WHERE 1=1 ");
                List<Object> params = new ArrayList<Object>();
                if ("R03".equals(persona)) {
                    sql.append("AND tenant_id = ? ");
                    params.add(ctx.getTenantId());
                } else if ("R04".equals(persona)) {
                    sql.append("AND tenant_id = ? AND requester_user_id = ? ");
                    params.add(ctx.getTenantId());
                    params.add(ctx.getUserId());
                } else if (requestedTenantId != null) {
                    sql.append("AND tenant_id = ? ");
                    params.add(requestedTenantId);
                }
                if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                    sql.append("AND upper(status) = ? ");
                    params.add(statusFilter.trim().toUpperCase(Locale.ROOT));
                }
                sql.append("ORDER BY created_at DESC LIMIT 100");

                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapRow(rs));
                        }
                    }
                }
                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("count", items.size());
                out.put("recordOnly", Boolean.TRUE);
                out.put("items", items);
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to list grant requests.");
        }
    }

    public Map<String, Object> submitRequest(UserSecurityContext ctx,
                                             UUID subjectUserId,
                                             String targetKindRaw,
                                             String targetCodeRaw,
                                             String scopeTypeRaw,
                                             UUID scopeId,
                                             UUID requestedTenantId,
                                             UUID collegeId,
                                             String validToIso,
                                             String reason,
                                             String idempotencyKey,
                                             String correlationId) {
        if (subjectUserId == null || targetCodeRaw == null || targetCodeRaw.trim().isEmpty()
                || reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION",
                    "subjectUserId, targetCode, and reason are required.");
        }
        String targetKind = (targetKindRaw == null || targetKindRaw.trim().isEmpty())
                ? "ROLE" : targetKindRaw.trim().toUpperCase(Locale.ROOT);
        if (!"ROLE".equals(targetKind) && !"PERMISSION".equals(targetKind)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "targetKind must be ROLE or PERMISSION.");
        }
        String targetCode = targetCodeRaw.trim();
        String scopeType = (scopeTypeRaw == null || scopeTypeRaw.trim().isEmpty())
                ? (ctx.getTenantId() != null ? "INSTITUTE" : "PLATFORM")
                : scopeTypeRaw.trim().toUpperCase(Locale.ROOT);
        if (!"PLATFORM".equals(scopeType) && !"INSTITUTE".equals(scopeType) && !"COLLEGE".equals(scopeType)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "scopeType must be PLATFORM, INSTITUTE, or COLLEGE.");
        }

        Timestamp validToTs = null;
        if (validToIso != null && !validToIso.trim().isEmpty()) {
            try {
                validToTs = Timestamp.from(Instant.parse(validToIso.trim()));
            } catch (Exception ex) {
                throw new InstituteAdminException(400, "IADM03_VALIDATION", "validTo must be an ISO-8601 timestamp.");
            }
        }

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.grant.request.submit")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_GRANT_REQUEST_DENIED", scopeLabel(ctx), "GRANT_REQUEST", null,
                            targetCode, "403", correlationId, "Grant request submit denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " is not permitted to submit grant requests.");
                }

                // Self-grant refusal (US-007)
                if (subjectUserId.equals(ctx.getUserId())) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_SELF_GRANT_DENIED", scopeLabel(ctx), "GRANT_REQUEST", null,
                            targetCode, "403", correlationId, "Self-grant request refused");
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_SELF_GRANT_DENIED",
                            "Nobody may request or grant access to themselves.");
                }

                UUID effectiveTenantId = ctx.getTenantId();
                if ("R03".equals(persona) || "R04".equals(persona)) {
                    if (effectiveTenantId == null) {
                        throw new InstituteAdminException(403, "IADM03_SCOPE_REQUIRED",
                                "Authenticated institute scope is required.");
                    }
                    if ("PLATFORM".equals(scopeType)) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, effectiveTenantId,
                                "IADM03_SCOPE_ESCALATION_DENIED", "INSTITUTE:" + effectiveTenantId,
                                "GRANT_REQUEST", null, scopeType, "403", correlationId,
                                persona + " cannot request PLATFORM scope");
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_SCOPE_ESCALATION_DENIED",
                                persona + " cannot escalate to PLATFORM scope.");
                    }
                    if (requestedTenantId != null && !requestedTenantId.equals(effectiveTenantId)) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, effectiveTenantId,
                                "IADM03_CROSS_TENANT_DENIED", "INSTITUTE:" + effectiveTenantId,
                                "GRANT_REQUEST", null, requestedTenantId.toString(), "403", correlationId,
                                "Cross-tenant grant request denied");
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_CROSS_TENANT_FORBIDDEN",
                                "Cannot submit grant request for another institute.");
                    }
                } else if (requestedTenantId != null) {
                    effectiveTenantId = requestedTenantId;
                }

                // §21.1 ceiling & permission-count check for ROLE requests
                if ("ROLE".equals(targetKind)) {
                    String ceilingDeny = checkCeilingAndPermissionCount(conn, persona, targetCode);
                    if (ceilingDeny != null) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, effectiveTenantId,
                                "IADM03_CEILING_DENIED", scopeLabel(ctx), "GRANT_REQUEST", null,
                                targetCode, "403", correlationId, ceilingDeny);
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_CEILING_DENIED", ceilingDeny);
                    }
                }

                // Duplicate open request check
                if (hasDuplicateOpenRequest(conn, subjectUserId, targetKind, targetCode, scopeType, scopeId)) {
                    throw new InstituteAdminException(409, "IADM03_DUPLICATE_REQUEST",
                            "An open grant request already exists for this subject, target, and scope.");
                }

                UUID reqId = UUID.randomUUID();
                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, effectiveTenantId,
                        "IADM03_GRANT_REQUEST_SUBMITTED",
                        effectiveTenantId != null ? "INSTITUTE:" + effectiveTenantId : "PLATFORM",
                        "GRANT_REQUEST", reqId.toString(),
                        null, "REQUESTED:" + targetCode, correlationId,
                        "Grant request submitted (record-only)");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iadm03.grant_requests " +
                                "(id, tenant_id, college_id, requester_user_id, subject_user_id, target_kind, target_code, " +
                                "scope_type, scope_id, valid_to, reason, status, audit_event_id, correlation_id, idempotency_key) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED', ?, ?, ?)")) {
                    ps.setObject(1, reqId);
                    ps.setObject(2, effectiveTenantId);
                    ps.setObject(3, collegeId);
                    ps.setObject(4, ctx.getUserId());
                    ps.setObject(5, subjectUserId);
                    ps.setString(6, targetKind);
                    ps.setString(7, targetCode);
                    ps.setString(8, scopeType);
                    ps.setObject(9, scopeId);
                    ps.setTimestamp(10, validToTs);
                    ps.setString(11, reason.trim());
                    ps.setObject(12, auditId);
                    ps.setString(13, correlationId);
                    ps.setString(14, idempotencyKey);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", reqId.toString());
                out.put("status", "REQUESTED");
                out.put("targetKind", targetKind);
                out.put("targetCode", targetCode);
                out.put("scopeType", scopeType);
                out.put("recordedOnly", Boolean.TRUE);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to submit grant request.");
        }
    }

    public Map<String, Object> validateRequest(UserSecurityContext ctx, UUID requestId, String correlationId) {
        if (requestId == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "requestId is required.");
        }
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.grant.review")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_GRANT_VALIDATE_DENIED", scopeLabel(ctx), "GRANT_REQUEST", requestId.toString(),
                            null, "403", correlationId, "Grant validation denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " is not permitted to validate grant requests.");
                }

                Map<String, Object> req = loadRequest(conn, requestId);
                if (req == null) {
                    throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Grant request not found.");
                }
                String targetKind = String.valueOf(req.get("targetKind"));
                String targetCode = String.valueOf(req.get("targetCode"));
                boolean exists = checkTargetExists(conn, targetKind, targetCode);
                if (!exists) {
                    throw new InstituteAdminException(400, "IADM03_TARGET_UNKNOWN",
                            targetKind + " '" + targetCode + "' does not exist in the live catalogue.");
                }
                String owning = "PERMISSION".equals(targetKind)
                        ? CatalogueReadService.deriveOwningService(targetCode)
                        : "IAM-ROLE";
                int currentHolders = countCurrentHolders(conn, targetKind, targetCode);

                String diffJson = "{\"before\":{\"target\":\"" + escape(targetCode)
                        + "\",\"currentHolders\":" + currentHolders
                        + "},\"after\":{\"target\":\"" + escape(targetCode)
                        + "\",\"action\":\"RECORD_ONLY_NO_APPLY\",\"owningService\":\"" + escape(owning)
                        + "\"},\"catalogueTouched\":false}";

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, (UUID) req.get("tenantUuid"),
                        "IADM03_GRANT_REQUEST_VALIDATED", scopeLabel(ctx), "GRANT_REQUEST", requestId.toString(),
                        "REQUESTED", "VALIDATED", correlationId,
                        "Grant request validated and before/after diff prepared (catalogue untouched)");

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iadm03.grant_requests SET status = 'VALIDATED', validation_diff = ?::jsonb, " +
                                "validated_by = ?, validated_at = now(), updated_at = now() WHERE id = ?")) {
                    ps.setString(1, diffJson);
                    ps.setObject(2, ctx.getUserId());
                    ps.setObject(3, requestId);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", requestId.toString());
                out.put("status", "VALIDATED");
                out.put("owningService", owning);
                out.put("currentHolders", currentHolders);
                out.put("catalogueTouched", Boolean.FALSE);
                out.put("validationDiff", diffJson);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to validate grant request.");
        }
    }

    public Map<String, Object> decideRequest(UserSecurityContext ctx,
                                             UUID requestId,
                                             String decisionRaw,
                                             String reason,
                                             String correlationId) {
        if (requestId == null || decisionRaw == null || reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION",
                    "requestId, decision (APPROVE/REJECT), and reason are required.");
        }
        String d = decisionRaw.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(d) && !"APPROVED".equals(d) && !"REJECT".equals(d) && !"REJECTED".equals(d)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "decision must be APPROVE or REJECT.");
        }
        boolean approve = "APPROVE".equals(d) || "APPROVED".equals(d);

        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.grant.approve")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_GRANT_APPROVE_DENIED", scopeLabel(ctx), "GRANT_REQUEST", requestId.toString(),
                            null, "403", correlationId, "Grant approval denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Only Super Admin (R01) may approve or reject grant requests.");
                }

                Map<String, Object> req = loadRequest(conn, requestId);
                if (req == null) {
                    throw new InstituteAdminException(404, "IADM03_NOT_FOUND", "Grant request not found.");
                }
                UUID requester = (UUID) req.get("requesterUuid");
                UUID subject = (UUID) req.get("subjectUuid");
                if (ctx.getUserId().equals(requester)) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, (UUID) req.get("tenantUuid"),
                            "IADM03_SELF_APPROVAL_DENIED", scopeLabel(ctx), "GRANT_REQUEST", requestId.toString(),
                            "requester=" + requester, "403", correlationId, "Self-approval refused");
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_SELF_APPROVAL_DENIED",
                            "Requester cannot approve their own grant request.");
                }
                if (ctx.getUserId().equals(subject)) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, (UUID) req.get("tenantUuid"),
                            "IADM03_SELF_GRANT_DENIED", scopeLabel(ctx), "GRANT_REQUEST", requestId.toString(),
                            "subject=" + subject, "403", correlationId, "Approver cannot be the subject");
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_SELF_GRANT_DENIED",
                            "Approver cannot decide a grant for themselves.");
                }

                String finalStatus = approve ? "RECORDED" : "REJECTED";
                String decisionStatus = approve ? "APPROVED" : "REJECTED";
                String beforeState = "{\"status\":\"" + escape(String.valueOf(req.get("status"))) + "\"}";
                String afterState = "{\"status\":\"" + finalStatus + "\",\"statement\":\"recorded, not applied\"}";

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, (UUID) req.get("tenantUuid"),
                        approve ? "IADM03_GRANT_DECISION_RECORDED" : "IADM03_GRANT_REQUEST_REJECTED",
                        scopeLabel(ctx), "GRANT_REQUEST", requestId.toString(),
                        beforeState, afterState, correlationId,
                        approve ? "Approved and recorded (recorded, not applied)" : "Rejected");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iadm03.grant_decisions " +
                                "(tenant_id, request_id, approver_user_id, decision, reason, before_state, after_state, recorded_only, audit_event_id, correlation_id) " +
                                "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, true, ?, ?)")) {
                    ps.setObject(1, req.get("tenantUuid"));
                    ps.setObject(2, requestId);
                    ps.setObject(3, ctx.getUserId());
                    ps.setString(4, decisionStatus);
                    ps.setString(5, reason.trim());
                    ps.setString(6, beforeState);
                    ps.setString(7, afterState);
                    ps.setObject(8, auditId);
                    ps.setString(9, correlationId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iadm03.grant_requests SET status = ?, decided_by = ?, decided_at = now(), " +
                                "decision_reason = ?, recorded_at = CASE WHEN ? THEN now() ELSE NULL END, " +
                                "updated_at = now() WHERE id = ?")) {
                    ps.setString(1, finalStatus);
                    ps.setObject(2, ctx.getUserId());
                    ps.setString(3, reason.trim());
                    ps.setBoolean(4, approve);
                    ps.setObject(5, requestId);
                    ps.executeUpdate();
                }

                conn.commit();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", requestId.toString());
                out.put("status", finalStatus);
                out.put("decision", decisionStatus);
                out.put("statement", approve ? "recorded, not applied" : "rejected");
                out.put("recordedOnly", Boolean.TRUE);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to decide grant request.");
        }
    }

    private String checkCeilingAndPermissionCount(Connection conn, String persona, String targetRole) throws SQLException {
        // R04 may submit requests for roles within CADM ceiling; R03/R02/R01/R38 follow §21.1
        if ("R04".equals(persona)) {
            if (GrantorCeiling.isProhibitedForCollegeAdminTarget(targetRole)) {
                return "College Admin (R04) cannot request prohibited role " + targetRole + ".";
            }
            return null;
        }
        String deny = GrantorCeiling.denyReason(persona, targetRole, false);
        if (deny != null) {
            return deny;
        }
        // Check permission count ceiling (except R01 which is platform operator with 0 template rows)
        if (!"R01".equals(persona)) {
            int assignerCount = countTemplateKeys(conn, toTemplateCode(persona));
            int targetCount = countTemplateKeys(conn, toTemplateCode(targetRole));
            if (targetCount > assignerCount) {
                return "Assigner " + persona + " (" + assignerCount + " keys) cannot assign role "
                        + targetRole + " holding more permissions (" + targetCount + " keys).";
            }
        }
        return null;
    }

    private static String toTemplateCode(String role) {
        String r = AccessPermissionMatrix.normalizePersona(role);
        if ("R02".equals(r)) return "SYSTEM_ADMIN";
        if ("R03".equals(r)) return "INSTITUTION_ADMIN";
        if ("R04".equals(r)) return "COLLEGE_ADMIN";
        if ("R28".equals(r)) return "AUDITOR";
        if ("R38".equals(r)) return "IAM_OPERATOR";
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private int countTemplateKeys(Connection conn, String roleCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*)::int FROM iam.role_template_permissions WHERE upper(role_code) = ?")) {
            ps.setString(1, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private boolean hasDuplicateOpenRequest(Connection conn, UUID subject, String kind, String code,
                                            String scopeType, UUID scopeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM iadm03.grant_requests " +
                        "WHERE subject_user_id = ? AND target_kind = ? AND target_code = ? " +
                        "AND scope_type = ? AND COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::uuid) = COALESCE(?, '00000000-0000-0000-0000-000000000000'::uuid) " +
                        "AND status IN ('REQUESTED', 'VALIDATED') LIMIT 1")) {
            ps.setObject(1, subject);
            ps.setString(2, kind);
            ps.setString(3, code);
            ps.setString(4, scopeType);
            ps.setObject(5, scopeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean checkTargetExists(Connection conn, String kind, String code) throws SQLException {
        String sql = "PERMISSION".equals(kind)
                ? "SELECT 1 FROM iam.permissions WHERE code = ? AND deleted_at IS NULL LIMIT 1"
                : "SELECT 1 FROM iam.role_templates WHERE upper(code) = upper(?) LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int countCurrentHolders(Connection conn, String kind, String code) throws SQLException {
        String sql = "PERMISSION".equals(kind)
                ? "SELECT count(DISTINCT ra.user_id)::int FROM iam.role_assignments ra " +
                "JOIN iam.role_permissions rp ON rp.role_id = ra.role_id AND rp.tenant_id = ra.tenant_id " +
                "JOIN iam.permissions p ON p.id = rp.permission_id AND p.deleted_at IS NULL " +
                "WHERE ra.deleted_at IS NULL AND p.code = ?"
                : "SELECT count(DISTINCT ra.user_id)::int FROM iam.role_assignments ra " +
                "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL " +
                "WHERE ra.deleted_at IS NULL AND upper(r.code) = upper(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Map<String, Object> loadRequest(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, tenant_id, requester_user_id, subject_user_id, target_kind, target_code, status " +
                        "FROM iadm03.grant_requests WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id"));
                m.put("tenantUuid", rs.getObject("tenant_id"));
                m.put("requesterUuid", rs.getObject("requester_user_id"));
                m.put("subjectUuid", rs.getObject("subject_user_id"));
                m.put("targetKind", rs.getString("target_kind"));
                m.put("targetCode", rs.getString("target_code"));
                m.put("status", rs.getString("status"));
                return m;
            }
        }
    }

    private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", String.valueOf(rs.getObject("id")));
        m.put("tenantId", rs.getObject("tenant_id") == null ? null : String.valueOf(rs.getObject("tenant_id")));
        m.put("collegeId", rs.getObject("college_id") == null ? null : String.valueOf(rs.getObject("college_id")));
        m.put("requesterUserId", String.valueOf(rs.getObject("requester_user_id")));
        m.put("subjectUserId", String.valueOf(rs.getObject("subject_user_id")));
        m.put("targetKind", rs.getString("target_kind"));
        m.put("targetCode", rs.getString("target_code"));
        m.put("scopeType", rs.getString("scope_type"));
        m.put("scopeId", rs.getObject("scope_id") == null ? null : String.valueOf(rs.getObject("scope_id")));
        m.put("reason", rs.getString("reason"));
        m.put("status", rs.getString("status"));
        m.put("validationDiff", rs.getString("validation_diff"));
        m.put("decisionReason", rs.getString("decision_reason"));
        m.put("recordedAt", rs.getObject("recorded_at") == null ? null : String.valueOf(rs.getObject("recorded_at")));
        m.put("createdAt", String.valueOf(rs.getObject("created_at")));
        return m;
    }

    private static String scopeLabel(UserSecurityContext ctx) {
        return ctx != null && ctx.getTenantId() != null ? "INSTITUTE:" + ctx.getTenantId() : "PLATFORM";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
