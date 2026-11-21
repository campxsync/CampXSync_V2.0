package com.campx.iadm.billing.service;

import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-04 — entitlement check is the CADM/all-services contract.
 * <p>
 * Read-only domain surface; {@link CampXLogger} is available for future mutation audit (Agreement &#167;3.2).
 */
public class Iadm04DomainService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(Iadm04DomainService.class);

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health(UserSecurityContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "IADM-04");
        out.put("status", "UP");
        return out;
    }

    public Map<String, Object> checkEntitlement(UserSecurityContext ctx, UUID tenantId, String key) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "adm01.manage");
            } catch (Exception e) {
                try { ctx.checkPermission(conn, "iadm04.view"); }
                catch (Exception e2) { ctx.checkPlatformAdmin(conn); }
            }
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT allowed, limit_value, used_value, deny_reason FROM plat.check_entitlement(?, ?)")) {
                ps.setObject(1, tid);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    if (!rs.next()) {
                        out.put("allowed", false);
                        out.put("denyReason", "UNKNOWN_ENTITLEMENT");
                        return out;
                    }
                    out.put("allowed", rs.getBoolean(1));
                    out.put("limitValue", rs.getBigDecimal(2));
                    out.put("usedValue", rs.getBigDecimal(3));
                    out.put("denyReason", rs.getString(4));
                    out.put("entitlementKey", key);
                    out.put("tenantId", tid.toString());
                    return out;
                }
            }
        });
    }

    @SuppressWarnings("unused")
    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-04 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}
