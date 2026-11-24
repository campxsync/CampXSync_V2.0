package com.campx.iadm.access.service;

import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IADM-03 US-031 — consumer / gateway governance read (not on every request path).
 * Runtime authorization stays DB + iam.has_permission / verified claims — never role-name fallback.
 */
public class GovernanceReadService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GovernanceReadService.class);
    private static final String CLAIMS_NOTE =
            "Authoritative checks use DB + iam.has_permission immediately. Gateway identity cache may lag up to 5 minutes (US-021 DEV SLA).";

    private final DatabaseConnectionManager db;

    public GovernanceReadService() {
        this(DatabaseConnectionManager.getInstance());
    }

    public GovernanceReadService(DatabaseConnectionManager db) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
    }

    /** Unauthenticated contract — safe metadata only (also exposed as /claims/contract). */
    public Map<String, Object> claimsContract() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("authoritative", "DB + iam.has_permission in service transaction");
        out.put("gatewayIdentityCacheTtlSeconds", Integer.valueOf(300));
        out.put("revocationDelaySecondsMax", Integer.valueOf(300));
        out.put("iadm03OnRequestPath", Boolean.FALSE);
        out.put("roleNameFallback", Boolean.FALSE);
        out.put("note", CLAIMS_NOTE);
        return out;
    }

    /**
     * Authenticated snapshot for gateway/consumers holding iam.governance.read (or platform).
     */
    public Map<String, Object> snapshot(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requireGovernanceRead(conn, ctx);

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("service", "IADM-03");
                out.put("runtimePath", "NOT_PER_REQUEST");
                out.put("claims", claimsContract());
                out.put("ceilings", ceilingMatrix());
                out.put("delegationActiveCheck", delegationActiveCheckContract());
                out.put("permissionKeys", Arrays.asList(AccessPermissionMatrix.NEW_KEYS));
                out.put("adm03Family", "DORMANT");
                out.put("effectiveAccessNote",
                        "Consumers authorize from verified gateway identity + iam.has_permission. "
                                + "Do not call IADM-03 on every request. With IADM-03 stopped, "
                                + "already-issued authority continues until claims/cache expiry; nobody gains extra access.");

                List<Map<String, Object>> sampleCeilings = new ArrayList<Map<String, Object>>();
                sampleCeilings.add(eval("R03", "FACULTY", false));
                sampleCeilings.add(eval("R03", "INSTITUTION_ADMIN", false));
                sampleCeilings.add(eval("R01", "INSTITUTION_ADMIN", false));
                sampleCeilings.add(eval("R02", "COLLEGE_ADMIN", false));
                sampleCeilings.add(eval("R04", "FACULTY", false));
                out.put("ceilingSamples", sampleCeilings);

                int activeDelegations = 0;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM iam.delegations "
                                + "WHERE deleted_at IS NULL AND status = 'ACTIVE' "
                                + "AND (valid_to IS NULL OR valid_to >= current_date)")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            activeDelegations = rs.getInt(1);
                        }
                    }
                } catch (SQLException ex) {
                    logger.warn("IADM-03 governance snapshot delegation count unavailable: {}", ex.getMessage());
                }
                out.put("activeDelegationCountHint", Integer.valueOf(activeDelegations));
                return out;
            }
        });
    }

    private Map<String, Object> eval(String grantor, String target, boolean self) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        String deny = GrantorCeiling.denyReason(grantor, target, self);
        row.put("grantor", grantor);
        row.put("targetRole", target);
        row.put("selfGrant", Boolean.valueOf(self));
        row.put("allowed", Boolean.valueOf(deny == null));
        row.put("denyReason", deny);
        return row;
    }

    private Map<String, Object> ceilingMatrix() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("source", "RBAC v3.0 §21.1 encoded in GrantorCeiling");
        m.put("r01MayAssign", Arrays.asList("SYSTEM_ADMIN", "INSTITUTION_ADMIN"));
        m.put("r02Limitation", "Platform technical identities only");
        m.put("r03CannotAssign", Arrays.asList("SUPER_ADMIN", "SYSTEM_ADMIN", "INSTITUTION_ADMIN", "STUDENT", "PARENT", "APPLICANT", "API_CLIENT"));
        m.put("r04Mode", "REQUEST_ONLY");
        m.put("r27", "PARKED denial only");
        return m;
    }

    private Map<String, Object> delegationActiveCheckContract() {
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("mechanism", "Path C active-check on institute-admin");
        d.put("urlHint", "GET /api/v1/admin/internal/delegations/active-check");
        d.put("baseEnv", "CAMPX_IAM_DELEGATION_BASE_URL");
        d.put("defaultBase", "http://127.0.0.1:8081");
        d.put("requiresIadm03PerRequest", Boolean.FALSE);
        d.put("permissionExample", "STM-07.ACCOUNT.A");
        return d;
    }

    private void requireGovernanceRead(Connection conn, UserSecurityContext ctx) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return;
        } catch (UserProfileAccessDeniedException ignored) {
            // fall through
        }
        try {
            ctx.checkPermission(conn, "iam.governance.read");
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "You are not authorized for this action (iam.governance.read).");
        }
    }
}
