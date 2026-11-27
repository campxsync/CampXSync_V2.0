package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.UserSecurityContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-03-US-014 — Read-only first-admin handover status from existing
 * plat.college_cadm_handovers and plat.iadm01_onboarding_steps.
 * No new appointment mechanism; no emails or credentials.
 */
public class HandoverStatusService {

    public Map<String, Object> getHandoverStatus(UserSecurityContext ctx,
                                                 UUID requestedTenantId,
                                                 String correlationId) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                String persona = CatalogueReadService.resolveCallerPersona(conn, ctx);
                if (!AccessPermissionMatrix.allowsIadm03(persona, "iadm03.handover.view")) {
                    DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                            "IADM03_HANDOVER_DENIED", scopeLabel(ctx), "HANDOVER", null,
                            null, "403", correlationId, "Handover read denied for " + persona);
                    conn.commit();
                    throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                            "Role " + persona + " is not permitted to view handover status.");
                }

                UUID scopeTenant = null;
                if ("R03".equals(persona)) {
                    if (ctx.getTenantId() == null) {
                        throw new InstituteAdminException(403, "IADM03_SCOPE_REQUIRED",
                                "Institution Admin requires an institute scope.");
                    }
                    if (requestedTenantId != null && !requestedTenantId.equals(ctx.getTenantId())) {
                        DurableAuditWriter.appendWithRefs(conn, ctx, ctx.getTenantId(),
                                "IADM03_CROSS_TENANT_DENIED", "INSTITUTE:" + ctx.getTenantId(),
                                "HANDOVER", null, requestedTenantId.toString(), "403", correlationId,
                                "R03 cross-tenant handover read denied");
                        conn.commit();
                        throw new InstituteAdminException(403, "IADM03_CROSS_TENANT_FORBIDDEN",
                                "Institution Admin cannot inspect handover status of another institute.");
                    }
                    scopeTenant = ctx.getTenantId();
                } else if (requestedTenantId != null) {
                    scopeTenant = requestedTenantId;
                }

                StringBuilder sql = new StringBuilder(
                        "SELECT h.id, h.tenant_id, t.code AS tenant_code, t.name AS tenant_name, " +
                                "h.college_id, h.first_college_admin_user_id, h.handed_over_by, h.handed_over_at, " +
                                "h.status, h.cadm01_ack_at, h.cadm02_ack_at, h.cadm02_interim " +
                                "FROM plat.college_cadm_handovers h " +
                                "LEFT JOIN core.tenants t ON t.id = h.tenant_id " +
                                "WHERE h.deleted_at IS NULL ");
                if (scopeTenant != null) {
                    sql.append("AND h.tenant_id = ? ");
                }
                sql.append("ORDER BY h.handed_over_at DESC NULLS LAST LIMIT 100");

                List<Map<String, Object>> handovers = new ArrayList<Map<String, Object>>();
                try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    if (scopeTenant != null) {
                        ps.setObject(1, scopeTenant);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new LinkedHashMap<String, Object>();
                            m.put("id", String.valueOf(rs.getObject("id")));
                            m.put("tenantId", String.valueOf(rs.getObject("tenant_id")));
                            m.put("tenantCode", rs.getString("tenant_code"));
                            m.put("tenantName", rs.getString("tenant_name"));
                            m.put("collegeId", rs.getObject("college_id") == null ? null : String.valueOf(rs.getObject("college_id")));
                            m.put("firstAdminUserRef", rs.getObject("first_college_admin_user_id") == null ? null : String.valueOf(rs.getObject("first_college_admin_user_id")));
                            m.put("appointedByRef", rs.getObject("handed_over_by") == null ? null : String.valueOf(rs.getObject("handed_over_by")));
                            m.put("handedOverAt", rs.getObject("handed_over_at") == null ? null : String.valueOf(rs.getObject("handed_over_at")));
                            m.put("status", rs.getString("status"));
                            m.put("cadm02AcceptedAt", rs.getObject("cadm02_ack_at") == null ? null : String.valueOf(rs.getObject("cadm02_ack_at")));
                            m.put("cadm02Interim", Boolean.valueOf(rs.getBoolean("cadm02_interim")));
                            handovers.add(m);
                        }
                    }
                }

                UUID auditId = DurableAuditWriter.appendWithRefs(conn, ctx, scopeTenant,
                        "IADM03_HANDOVER_READ", scopeLabel(ctx), "HANDOVER", null,
                        "source=plat.college_cadm_handovers", "count=" + handovers.size(),
                        correlationId, "Read-only first-admin handover status");
                conn.commit();

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("readOnly", Boolean.TRUE);
                out.put("source", "plat.college_cadm_handovers");
                out.put("count", handovers.size());
                out.put("handovers", handovers);
                out.put("auditEventId", auditId.toString());
                return out;
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new InstituteAdminException(500, "IADM03_DB_ERROR", "Unable to read handover status.");
        }
    }

    private static String scopeLabel(UserSecurityContext ctx) {
        return ctx != null && ctx.getTenantId() != null ? "INSTITUTE:" + ctx.getTenantId() : "PLATFORM";
    }
}
