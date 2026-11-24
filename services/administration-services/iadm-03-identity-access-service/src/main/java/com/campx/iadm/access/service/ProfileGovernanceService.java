package com.campx.iadm.access.service;

import com.campx.iadm.access.auth.SupabaseAuthAdminClient;
import com.campx.iadm.access.config.DatabaseConnectionManager;
import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.exception.UserProfileAccessDeniedException;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * S1 profile / overview / subject-access reads and mutations (US-010…014).
 * Population default: R02/R03 institute-level administrators only (P1).
 */
public class ProfileGovernanceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(ProfileGovernanceService.class);

    private final DatabaseConnectionManager db;
    private final SupabaseAuthAdminClient authAdminClient;

    public ProfileGovernanceService() {
        this(DatabaseConnectionManager.getInstance(), new SupabaseAuthAdminClient());
    }

    public ProfileGovernanceService(DatabaseConnectionManager db, SupabaseAuthAdminClient authAdminClient) {
        this.db = db == null ? DatabaseConnectionManager.getInstance() : db;
        this.authAdminClient = authAdminClient == null ? new SupabaseAuthAdminClient() : authAdminClient;
    }

    public Map<String, Object> overview(final UserSecurityContext ctx) {
        final boolean[] platformHolder = new boolean[1];
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.access.overview.view");
                platformHolder[0] = isPlatformOperator(conn, ctx);
                return null;
            }
        });
        final boolean platform = platformHolder[0];
        // Platform cross-tenant reads: iam.user_profiles RLS has no platform SELECT policy yet
        // (S0 D2 — owner-approved migration pending). After permission check, pool role bypasses RLS.
        if (platform) {
            return withPoolConnection(new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                @Override
                public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                    return buildOverview(conn, null, true);
                }
            });
        }
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                return buildOverview(conn, ctx.getTenantId(), false);
            }
        });
    }

    private Map<String, Object> buildOverview(Connection conn, UUID tenantFilter, boolean platform)
            throws SQLException {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        int r03 = countAdminProfiles(conn, tenantFilter);
        int r02 = platform ? countSystemAdmins(conn) : 0;
        int activeR03 = countAdminProfilesByStatus(conn, tenantFilter, "ACTIVE");
        int inactiveR03 = countAdminProfilesByStatus(conn, tenantFilter, "INACTIVE");
        out.put("profileCount", Integer.valueOf(r03 + r02));
        out.put("activeProfiles", Integer.valueOf(activeR03));
        out.put("inactiveProfiles", Integer.valueOf(inactiveR03));
        out.put("systemAdminCount", Integer.valueOf(r02));
        out.put("institutionAdminCount", Integer.valueOf(r03));
        out.put("roleDistribution", roleDistribution(conn, tenantFilter));
        out.put("pendingRequests", Integer.valueOf(0)); // access_requests table not yet approved
        out.put("pendingRequestsNote", "Access-request store not created yet (S3)");
        out.put("expiringGrants", Integer.valueOf(countExpiringGrants(conn, tenantFilter)));
        out.put("openReviews", Integer.valueOf(countOpenReviews(conn, tenantFilter)));
        out.put("scope", platform ? "PLATFORM" : "INSTITUTE");
        if (platform) {
            out.put("rlsNote",
                    "Platform cross-tenant profile reads use pool-role interim until owner-approved iam.user_profiles platform SELECT policy.");
        }
        return out;
    }

    public Map<String, Object> listProfiles(final UserSecurityContext ctx, final String statusFilter) {
        final boolean[] platformHolder = new boolean[1];
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.profile.view");
                platformHolder[0] = isPlatformOperator(conn, ctx);
                return null;
            }
        });
        final boolean platform = platformHolder[0];
        if (platform) {
            return withPoolConnection(new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                @Override
                public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                    return buildProfileList(conn, null, statusFilter, true);
                }
            });
        }
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                return buildProfileList(conn, ctx.getTenantId(), statusFilter, false);
            }
        });
    }

    private Map<String, Object> buildProfileList(Connection conn, UUID tenantFilter, String statusFilter,
                                                 boolean platform) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT up.id, up.email, up.full_name, up.status, up.tenant_id, t.code AS tenant_code, ");
        sql.append("r.code AS role_code ");
        sql.append("FROM iam.user_profiles up ");
        sql.append("JOIN iam.role_assignments ra ON ra.user_id = up.id AND ra.deleted_at IS NULL ");
        sql.append("JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL ");
        sql.append("LEFT JOIN core.tenants t ON t.id = up.tenant_id AND t.deleted_at IS NULL ");
        sql.append("WHERE up.deleted_at IS NULL ");
        sql.append("AND upper(r.code) = 'INSTITUTION_ADMIN' ");
        if (tenantFilter != null) {
            sql.append("AND up.tenant_id = ? ");
        }
        if (statusFilter != null && !statusFilter.trim().isEmpty()
                && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
            sql.append("AND upper(up.status) = ? ");
        }
        sql.append("ORDER BY up.email");
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            if (tenantFilter != null) {
                ps.setObject(i++, tenantFilter);
            }
            if (statusFilter != null && !statusFilter.trim().isEmpty()
                    && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                ps.setString(i++, statusFilter.trim().toUpperCase(Locale.ROOT));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getObject("id").toString());
                    row.put("email", rs.getString("email"));
                    row.put("fullName", rs.getString("full_name"));
                    row.put("status", rs.getString("status"));
                    Object tid = rs.getObject("tenant_id");
                    row.put("tenantId", tid == null ? null : tid.toString());
                    row.put("tenantCode", rs.getString("tenant_code"));
                    row.put("roleCode", rs.getString("role_code"));
                    row.put("store", "IAM_PROFILE");
                    items.add(row);
                }
            }
        }
        if (platform) {
            StringBuilder paSql = new StringBuilder();
            paSql.append("SELECT user_id, full_name, role_code, status FROM plat.platform_admins ");
            paSql.append("WHERE deleted_at IS NULL AND upper(role_code) = 'SYSTEM_ADMIN' ");
            if (statusFilter != null && !statusFilter.trim().isEmpty()
                    && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                paSql.append("AND upper(status) = ? ");
            }
            paSql.append("ORDER BY full_name");
            try (PreparedStatement ps = conn.prepareStatement(paSql.toString())) {
                if (statusFilter != null && !statusFilter.trim().isEmpty()
                        && !"ALL".equalsIgnoreCase(statusFilter.trim())) {
                    ps.setString(1, statusFilter.trim().toUpperCase(Locale.ROOT));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        row.put("id", rs.getObject("user_id").toString());
                        row.put("email", null);
                        row.put("fullName", rs.getString("full_name"));
                        row.put("status", rs.getString("status"));
                        row.put("tenantId", null);
                        row.put("tenantCode", null);
                        row.put("roleCode", rs.getString("role_code"));
                        row.put("store", "PLATFORM_ADMIN");
                        items.add(row);
                    }
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("items", items);
        out.put("count", Integer.valueOf(items.size()));
        out.put("scope", platform ? "PLATFORM" : "INSTITUTE");
        return out;
    }

    private <T> T withPoolConnection(UserSecurityContext.TransactionCallback<T> action) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.execute(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    /* ignore */
                }
                if (e instanceof InstituteAdminException) {
                    throw (InstituteAdminException) e;
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Platform pool read failed: " + e.getMessage(), e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    /* ignore */
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection error: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getProfileAccess(final UserSecurityContext ctx, final UUID subjectId) {
        final boolean[] platformHolder = new boolean[1];
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.access.subject.view");
                platformHolder[0] = isPlatformOperator(conn, ctx);
                return null;
            }
        });
        final boolean platform = platformHolder[0];
        UserSecurityContext.TransactionCallback<Map<String, Object>> load =
                new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                    @Override
                    public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                        Map<String, Object> profile = loadProfile(conn, subjectId);
                        if (profile == null && platform) {
                            profile = loadPlatformAdmin(conn, subjectId);
                        }
                        if (profile == null) {
                            throw new InstituteAdminException(404, "IADM03_PROFILE_NOT_FOUND", "Profile not found.");
                        }
                        if (!platform) {
                            Object tid = profile.get("tenantId");
                            if (tid == null || ctx.getTenantId() == null
                                    || !ctx.getTenantId().toString().equals(tid.toString())) {
                                throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                                        "The requested institute is outside your permitted scope.");
                            }
                        }
                        List<Map<String, Object>> grants = listGrants(conn, subjectId);
                        if ("PLATFORM_ADMIN".equals(profile.get("store"))) {
                            Map<String, Object> synthetic = new LinkedHashMap<String, Object>();
                            synthetic.put("assignmentId", null);
                            synthetic.put("roleCode", profile.get("roleCode"));
                            synthetic.put("collegeId", null);
                            synthetic.put("departmentId", null);
                            synthetic.put("validFrom", null);
                            synthetic.put("validTo", null);
                            synthetic.put("grantedBy", null);
                            synthetic.put("tenantId", null);
                            synthetic.put("note", "Platform store (plat.platform_admins); not an iam.role_assignments row");
                            grants.add(0, synthetic);
                        }
                        Map<String, Object> out = new LinkedHashMap<String, Object>();
                        out.put("profile", profile);
                        out.put("grants", grants);
                        out.put("grantCount", Integer.valueOf(grants.size()));
                        return out;
                    }
                };
        if (platform) {
            return withPoolConnection(load);
        }
        return ctx.executeInTransaction(db, load);
    }

    public Map<String, Object> inviteOrCreateProfile(final UserSecurityContext ctx,
                                                     final String email,
                                                     final String displayName,
                                                     final String targetRole,
                                                     final UUID tenantId,
                                                     final String idempotencyKey,
                                                     final String correlationId) {
        if (email == null || email.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "email is required");
        }
        final String role = targetRole == null ? "INSTITUTION_ADMIN" : targetRole.trim().toUpperCase(Locale.ROOT);
        if (!"INSTITUTION_ADMIN".equals(role) && !"SYSTEM_ADMIN".equals(role)
                && !"R03".equals(role) && !"R02".equals(role)) {
            throw new InstituteAdminException(400, "IADM03_POPULATION",
                    "IADM-03 profile population is limited to System Admin (R02) and Institution Admin (R03).");
        }
        final String normalizedRole = ("R02".equals(role) || "SYSTEM_ADMIN".equals(role))
                ? "SYSTEM_ADMIN" : "INSTITUTION_ADMIN";
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();
        final String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        // Auth invite outside TX (external); then durable profile+audit in TX
        Map<String, Object> invite = authAdminClient.inviteOrCreateUser(
                email.trim().toLowerCase(Locale.ROOT),
                displayName == null || displayName.trim().isEmpty() ? email : displayName.trim());
        final UUID authUserId = UUID.fromString(invite.get("userId").toString());

        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.profile.invite");
                boolean platform = isPlatformOperator(conn, ctx);
                UUID scopeTenant = tenantId;
                if ("INSTITUTION_ADMIN".equals(normalizedRole)) {
                    if (scopeTenant == null) {
                        scopeTenant = ctx.getTenantId();
                    }
                    if (scopeTenant == null) {
                        throw new InstituteAdminException(400, "IADM03_VALIDATION",
                                "tenantId is required when inviting an Institution Admin.");
                    }
                    if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(scopeTenant))) {
                        throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                                "The requested institute is outside your permitted scope.");
                    }
                } else {
                    // R02 — platform technical; still create iam profile without tenant when platform operator
                    if (!platform) {
                        throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                                "Only a platform administrator may invite System Admin (R02).");
                    }
                }

                // Idempotency replay
                try (PreparedStatement find = conn.prepareStatement(
                        "SELECT result_payload::text, status FROM iam.governance_idempotency "
                                + "WHERE idempotency_key = ? AND deleted_at IS NULL")) {
                    find.setString(1, key);
                    try (ResultSet rs = find.executeQuery()) {
                        if (rs.next() && "SUCCEEDED".equalsIgnoreCase(rs.getString("status"))) {
                            Map<String, Object> replay = new LinkedHashMap<String, Object>();
                            replay.put("replay", Boolean.TRUE);
                            replay.put("resultPayload", rs.getString(1));
                            replay.put("idempotencyKey", key);
                            return replay;
                        }
                    }
                }

                final String display = displayName == null || displayName.trim().isEmpty()
                        ? email.trim() : displayName.trim();
                final String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

                if ("SYSTEM_ADMIN".equals(normalizedRole)) {
                    // Dual store: R02 lives in plat.platform_admins (tenant_id NOT NULL on iam.user_profiles).
                    Map<String, Object> existing = loadProfile(conn, authUserId);
                    if (existing != null) {
                        throw new InstituteAdminException(409, "IADM03_PROFILE_EXISTS",
                                "A profile already exists for this Auth subject. Open the existing profile.");
                    }
                    try (PreparedStatement findPa = conn.prepareStatement(
                            "SELECT user_id FROM plat.platform_admins "
                                    + "WHERE user_id = ? AND deleted_at IS NULL")) {
                        findPa.setObject(1, authUserId);
                        try (ResultSet rs = findPa.executeQuery()) {
                            if (rs.next()) {
                                throw new InstituteAdminException(409, "IADM03_PROFILE_EXISTS",
                                        "A System Admin already exists for this Auth subject.");
                            }
                        }
                    }
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO plat.platform_admins "
                                    + "(user_id, full_name, role_code, status, created_by, updated_by) "
                                    + "VALUES (?, ?, 'SYSTEM_ADMIN', 'ACTIVE', ?, ?)")) {
                        ins.setObject(1, authUserId);
                        ins.setString(2, display);
                        ins.setObject(3, ctx.getUserId());
                        ins.setObject(4, ctx.getUserId());
                        ins.executeUpdate();
                    } catch (SQLException ex) {
                        if ("23505".equals(ex.getSQLState())) {
                            throw new InstituteAdminException(409, "IADM03_PROFILE_EXISTS",
                                    "A System Admin already exists for this Auth subject.");
                        }
                        throw ex;
                    }
                    DurableAuditWriter.append(conn, ctx, ctx.getTenantId(),
                            "IADM03_PROFILE_INVITED", "PLATFORM_ADMIN", authUserId.toString(),
                            "System Admin (R02) invited/created in platform store; invitationStatus="
                                    + String.valueOf(invite.get("invitationStatus")));
                } else {
                    // INSTITUTION_ADMIN — reuse IADM-01 SECURITY DEFINER helper when platform; else RLS insert.
                    Map<String, Object> existing = loadProfile(conn, authUserId);
                    if (existing != null) {
                        throw new InstituteAdminException(409, "IADM03_PROFILE_EXISTS",
                                "A profile already exists for this Auth subject. Open the existing profile.");
                    }
                    if (platform) {
                        try (PreparedStatement fn = conn.prepareStatement(
                                "SELECT plat.iadm01_create_first_admin_profile(?, ?, ?, ?)")) {
                            fn.setObject(1, scopeTenant);
                            fn.setObject(2, authUserId);
                            fn.setString(3, normalizedEmail);
                            fn.setString(4, display);
                            fn.executeQuery();
                        } catch (SQLException ex) {
                            String msg = ex.getMessage() == null ? "" : ex.getMessage();
                            if (msg.contains("IADM01_AUTH_USER_REQUIRED") || msg.contains("AUTH_USER")) {
                                throw new InstituteAdminException(409, "IADM03_AUTH_LINK_REQUIRED",
                                        "Auth user must be linked before the institute profile can be created. Invitation was sent; retry after the invitee accepts.");
                            }
                            if ("23505".equals(ex.getSQLState()) || msg.toLowerCase(Locale.ROOT).contains("duplicate")) {
                                throw new InstituteAdminException(409, "IADM03_PROFILE_EXISTS",
                                        "A profile already exists for this email or Auth subject.");
                            }
                            throw ex;
                        }
                    } else {
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO iam.user_profiles "
                                        + "(id, username, email, full_name, status, created_by, updated_by) "
                                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)")) {
                            ins.setObject(1, authUserId);
                            ins.setString(2, normalizedEmail);
                            ins.setString(3, normalizedEmail);
                            ins.setString(4, display);
                            ins.setObject(5, ctx.getUserId());
                            ins.setObject(6, ctx.getUserId());
                            ins.executeUpdate();
                        } catch (SQLException ex) {
                            if ("23505".equals(ex.getSQLState()) || (ex.getMessage() != null
                                    && ex.getMessage().toLowerCase(Locale.ROOT).contains("duplicate"))) {
                                throw new InstituteAdminException(409, "IADM03_PROFILE_EXISTS",
                                        "A profile already exists for this email or Auth subject.");
                            }
                            throw ex;
                        }
                    }
                    DurableAuditWriter.append(conn, ctx, scopeTenant != null ? scopeTenant : ctx.getTenantId(),
                            "IADM03_PROFILE_INVITED", "USER_PROFILE", authUserId.toString(),
                            "Institution Admin profile invited/created; invitationStatus="
                                    + String.valueOf(invite.get("invitationStatus")));
                }

                try (PreparedStatement idem = conn.prepareStatement(
                        "INSERT INTO iam.governance_idempotency "
                                + "(idempotency_key, operation, status, result_payload, correlation_id, tenant_id, actor_user_id, created_by) "
                                + "VALUES (?, 'PROFILE_INVITE', 'SUCCEEDED', ?::jsonb, ?, ?, ?, ?) "
                                + "ON CONFLICT (idempotency_key) DO UPDATE SET status = 'SUCCEEDED', "
                                + "result_payload = EXCLUDED.result_payload, updated_at = now()")) {
                    String payload = "{\"profileId\":\"" + authUserId + "\",\"email\":\""
                            + email.trim().toLowerCase(Locale.ROOT) + "\",\"role\":\"" + normalizedRole + "\"}";
                    idem.setString(1, key);
                    idem.setString(2, payload);
                    idem.setString(3, corr);
                    idem.setObject(4, scopeTenant);
                    idem.setObject(5, ctx.getUserId());
                    idem.setObject(6, ctx.getUserId());
                    idem.executeUpdate();
                }

                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("profileId", authUserId.toString());
                out.put("email", email.trim().toLowerCase(Locale.ROOT));
                out.put("targetRole", normalizedRole);
                out.put("invitationStatus", invite.get("invitationStatus"));
                out.put("notificationDeferred", Boolean.TRUE);
                out.put("notificationMessage", "Invitation is sent by Supabase Auth; app notification is delayed.");
                out.put("idempotencyKey", key);
                out.put("correlationId", corr);
                out.put("replay", Boolean.FALSE);
                logger.info("IADM-03 profile invite profileId={} role={}", authUserId, normalizedRole);
                return out;
            }
        });
    }

    public Map<String, Object> updateProfileStatus(final UserSecurityContext ctx,
                                                    final UUID profileId,
                                                    final String newStatus,
                                                    final String reason,
                                                    final String idempotencyKey) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "A reason is required for status change.");
        }
        final String status = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION", "Status must be ACTIVE or INACTIVE.");
        }
        final String key = idempotencyKey == null || idempotencyKey.trim().isEmpty()
                ? UUID.randomUUID().toString() : idempotencyKey.trim();

        final boolean[] platformHolder = new boolean[1];
        ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Void>() {
            @Override
            public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.profile.status");
                platformHolder[0] = isPlatformOperator(conn, ctx);
                return null;
            }
        });
        final boolean platform = platformHolder[0];

        // Resolve profile tenant (platform may need pool read — no platform SELECT policy yet).
        final UUID[] tenantHolder = new UUID[1];
        if (platform) {
            withPoolConnection(new UserSecurityContext.TransactionCallback<Void>() {
                @Override
                public Void execute(Connection conn) throws SQLException, InstituteAdminException {
                    Map<String, Object> profile = loadProfile(conn, profileId);
                    if (profile == null) {
                        throw new InstituteAdminException(404, "IADM03_PROFILE_NOT_FOUND", "Profile not found.");
                    }
                    if (profile.get("tenantId") != null) {
                        tenantHolder[0] = UUID.fromString(profile.get("tenantId").toString());
                    }
                    return null;
                }
            });
        } else {
            tenantHolder[0] = ctx.getTenantId();
        }
        if (tenantHolder[0] == null) {
            throw new InstituteAdminException(400, "IADM03_VALIDATION",
                    "Profile has no institute scope; status change is only for institute administrator profiles.");
        }
        if (!platform && (ctx.getTenantId() == null || !ctx.getTenantId().equals(tenantHolder[0]))) {
            throw new InstituteAdminException(403, "IADM03_CROSS_INSTITUTE",
                    "The requested institute is outside your permitted scope.");
        }

        final UserSecurityContext scoped = ctx.withTenant(tenantHolder[0]);
        if (platform) {
            // Interim: pool UPDATE (no platform UPDATE RLS policy) then authenticated audit in same TX.
            return withPoolConnection(new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
                @Override
                public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE iam.user_profiles SET status = ?, updated_at = now(), updated_by = ? "
                                    + "WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setString(1, status);
                        ps.setObject(2, ctx.getUserId());
                        ps.setObject(3, profileId);
                        int n = ps.executeUpdate();
                        if (n == 0) {
                            throw new InstituteAdminException(404, "IADM03_PROFILE_NOT_FOUND", "Profile not found.");
                        }
                    }
                    scoped.applyClaimsAndRole(conn);
                    DurableAuditWriter.append(conn, scoped, tenantHolder[0],
                            "IADM03_PROFILE_STATUS", "USER_PROFILE", profileId.toString(),
                            "Status=" + status + "; reason=" + reason.trim());
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("profileId", profileId.toString());
                    out.put("status", status);
                    out.put("reason", reason.trim());
                    out.put("idempotencyKey", key);
                    out.put("claimsNote",
                            "Profile status is authoritative on next permission check; gateway identity cache may lag up to 5 minutes until invalidate (US-021).");
                    out.put("rlsNote",
                            "Platform status write used pool-role interim until owner-approved iam.user_profiles platform policies.");
                    return out;
                }
            });
        }
        return scoped.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE iam.user_profiles SET status = ?, updated_at = now(), updated_by = ? "
                                + "WHERE id = ? AND deleted_at IS NULL")) {
                    ps.setString(1, status);
                    ps.setObject(2, ctx.getUserId());
                    ps.setObject(3, profileId);
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        throw new InstituteAdminException(404, "IADM03_PROFILE_NOT_FOUND", "Profile not found.");
                    }
                }
                DurableAuditWriter.append(conn, scoped, tenantHolder[0],
                        "IADM03_PROFILE_STATUS", "USER_PROFILE", profileId.toString(),
                        "Status=" + status + "; reason=" + reason.trim());
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("profileId", profileId.toString());
                out.put("status", status);
                out.put("reason", reason.trim());
                out.put("idempotencyKey", key);
                out.put("claimsNote",
                        "Profile status is authoritative on next permission check; gateway identity cache may lag up to 5 minutes until invalidate (US-021).");
                return out;
            }
        });
    }

    public Map<String, Object> permissionMappings(final UserSecurityContext ctx, final String q) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            @Override
            public Map<String, Object> execute(Connection conn) throws SQLException, InstituteAdminException {
                requirePermission(conn, ctx, "iam.catalog.permissions.view");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String like = q == null || q.trim().isEmpty() ? "%" : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                String sql = "SELECT p.code, p.module, p.action, p.description, "
                        + "coalesce(string_agg(DISTINCT rtp.role_code, ',' ORDER BY rtp.role_code), '') AS mapped_roles "
                        + "FROM iam.permissions p "
                        + "LEFT JOIN iam.role_template_permissions rtp "
                        + "  ON rtp.permission_code = p.code AND rtp.deleted_at IS NULL "
                        + "WHERE p.deleted_at IS NULL "
                        + "AND (lower(p.code) LIKE ? OR lower(coalesce(p.description,'')) LIKE ? OR lower(coalesce(p.module,'')) LIKE ?) "
                        + "GROUP BY p.code, p.module, p.action, p.description "
                        + "ORDER BY p.code LIMIT 100";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("code", rs.getString("code"));
                            row.put("module", rs.getString("module"));
                            row.put("action", rs.getString("action"));
                            row.put("description", rs.getString("description"));
                            String mapped = rs.getString("mapped_roles");
                            List<String> roles = new ArrayList<String>();
                            if (mapped != null && !mapped.isEmpty()) {
                                for (String part : mapped.split(",")) {
                                    if (!part.trim().isEmpty()) {
                                        roles.add(part.trim());
                                    }
                                }
                            }
                            row.put("mappedRoles", roles);
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("count", Integer.valueOf(items.size()));
                out.put("query", q == null ? "" : q);
                return out;
            }
        });
    }

    private Map<String, Object> loadProfile(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, email, full_name, status, tenant_id FROM iam.user_profiles "
                        + "WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", rs.getObject("id").toString());
                row.put("email", rs.getString("email"));
                row.put("fullName", rs.getString("full_name"));
                row.put("status", rs.getString("status"));
                Object tid = rs.getObject("tenant_id");
                row.put("tenantId", tid == null ? null : tid.toString());
                row.put("store", "IAM_PROFILE");
                return row;
            }
        }
    }

    private Map<String, Object> loadPlatformAdmin(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT user_id, full_name, role_code, status FROM plat.platform_admins "
                        + "WHERE user_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("id", rs.getObject("user_id").toString());
                row.put("email", null);
                row.put("fullName", rs.getString("full_name"));
                row.put("status", rs.getString("status"));
                row.put("tenantId", null);
                row.put("roleCode", rs.getString("role_code"));
                row.put("store", "PLATFORM_ADMIN");
                return row;
            }
        }
    }

    private List<Map<String, Object>> listGrants(Connection conn, UUID userId) throws SQLException {
        List<Map<String, Object>> grants = new ArrayList<Map<String, Object>>();
        String sql = "SELECT ra.id, r.code AS role_code, ra.college_id, ra.department_id, "
                + "ra.valid_from, ra.valid_to, ra.granted_by, ra.tenant_id "
                + "FROM iam.role_assignments ra "
                + "JOIN iam.roles r ON r.id = ra.role_id "
                + "WHERE ra.user_id = ? AND ra.deleted_at IS NULL AND r.deleted_at IS NULL "
                + "ORDER BY r.code";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> g = new LinkedHashMap<String, Object>();
                    g.put("assignmentId", rs.getObject("id").toString());
                    g.put("roleCode", rs.getString("role_code"));
                    Object college = rs.getObject("college_id");
                    g.put("collegeId", college == null ? null : college.toString());
                    Object dept = rs.getObject("department_id");
                    g.put("departmentId", dept == null ? null : dept.toString());
                    g.put("validFrom", rs.getDate("valid_from") == null ? null : rs.getDate("valid_from").toString());
                    g.put("validTo", rs.getDate("valid_to") == null ? null : rs.getDate("valid_to").toString());
                    Object gb = rs.getObject("granted_by");
                    g.put("grantedBy", gb == null ? null : gb.toString());
                    Object tid = rs.getObject("tenant_id");
                    g.put("tenantId", tid == null ? null : tid.toString());
                    grants.add(g);
                }
            }
        }
        return grants;
    }

    private int countAdminProfiles(Connection conn, UUID tenantId) throws SQLException {
        String sql = "SELECT count(DISTINCT up.id) FROM iam.user_profiles up "
                + "JOIN iam.role_assignments ra ON ra.user_id = up.id AND ra.deleted_at IS NULL "
                + "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL "
                + "WHERE up.deleted_at IS NULL AND upper(r.code) = 'INSTITUTION_ADMIN' "
                + (tenantId == null ? "" : "AND up.tenant_id = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (tenantId != null) {
                ps.setObject(1, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countSystemAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM plat.platform_admins "
                        + "WHERE deleted_at IS NULL AND upper(role_code) = 'SYSTEM_ADMIN'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countAdminProfilesByStatus(Connection conn, UUID tenantId, String status) throws SQLException {
        String sql = "SELECT count(DISTINCT up.id) FROM iam.user_profiles up "
                + "JOIN iam.role_assignments ra ON ra.user_id = up.id AND ra.deleted_at IS NULL "
                + "JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL "
                + "WHERE up.deleted_at IS NULL AND upper(up.status) = ? "
                + "AND upper(r.code) = 'INSTITUTION_ADMIN' "
                + (tenantId == null ? "" : "AND up.tenant_id = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            if (tenantId != null) {
                ps.setObject(2, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<Map<String, Object>> roleDistribution(Connection conn, UUID tenantId) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        String sql = "SELECT r.code, count(*) AS cnt FROM iam.role_assignments ra "
                + "JOIN iam.roles r ON r.id = ra.role_id "
                + "WHERE ra.deleted_at IS NULL AND r.deleted_at IS NULL "
                + (tenantId == null ? "" : "AND ra.tenant_id = ? ")
                + "GROUP BY r.code ORDER BY cnt DESC LIMIT 20";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (tenantId != null) {
                ps.setObject(1, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("roleCode", rs.getString("code"));
                    row.put("count", Integer.valueOf(rs.getInt("cnt")));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private int countExpiringGrants(Connection conn, UUID tenantId) throws SQLException {
        String sql = "SELECT count(*) FROM iam.role_assignments "
                + "WHERE deleted_at IS NULL AND valid_to IS NOT NULL "
                + "AND valid_to <= (CURRENT_DATE + 30) AND valid_to >= CURRENT_DATE "
                + (tenantId == null ? "" : "AND tenant_id = ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (tenantId != null) {
                ps.setObject(1, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countOpenReviews(Connection conn, UUID tenantId) throws SQLException {
        try {
            String sql = "SELECT count(*) FROM iam.access_reviews "
                    + "WHERE deleted_at IS NULL AND upper(status) IN ('OPEN','IN_PROGRESS','PENDING') "
                    + (tenantId == null ? "" : "AND tenant_id = ?");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (tenantId != null) {
                    ps.setObject(1, tenantId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            logger.warn("IADM-03 openReviews partial failure: {}", ex.getMessage());
            return 0;
        }
    }

    private void requirePermission(Connection conn, UserSecurityContext ctx, String key) throws SQLException {
        if (isPlatformOperator(conn, ctx)) {
            return;
        }
        try {
            ctx.checkPermission(conn, key);
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM03_FORBIDDEN",
                    "You are not authorized for this action (" + key + ").");
        }
    }

    private boolean isPlatformOperator(Connection conn, UserSecurityContext ctx) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return true;
        } catch (UserProfileAccessDeniedException ex) {
            return false;
        }
    }
}
