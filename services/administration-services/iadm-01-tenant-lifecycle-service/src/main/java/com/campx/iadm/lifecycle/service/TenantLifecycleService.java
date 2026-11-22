package com.campx.iadm.lifecycle.service;

import com.campx.iadm.lifecycle.auth.SupabaseAuthAdminClient;
import com.campx.iadm.lifecycle.config.DatabaseConnectionManager;
import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.iadm.lifecycle.exception.UserProfileAccessDeniedException;
import com.campx.iadm.lifecycle.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.api.AuditEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-01 Institute &amp; Tenant Lifecycle domain service.
 * <p>
 * Permission keys: adm01.manage / adm01.write / adm01.view (never role names). Successful mutations
 * are audited through {@link CampXLogger} per Agreement &#167;3.2.
 */
public class TenantLifecycleService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TenantLifecycleService.class);
    private static final List<String> FIRST_R03_STEPS = Arrays.asList("INVITE_LOGIN", "CREATE_PROFILE", "APPOINT_R03");

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();
    private final SupabaseAuthAdminClient authAdminClient;

    public TenantLifecycleService() {
        this(new SupabaseAuthAdminClient());
    }

    public TenantLifecycleService(SupabaseAuthAdminClient authAdminClient) {
        this.authAdminClient = authAdminClient == null ? new SupabaseAuthAdminClient() : authAdminClient;
    }

    /**
     * US-001: Register institute in ONBOARDING; seed first-R03 onboarding steps; durable audit.
     */
    public Map<String, Object> registerInstitute(UserSecurityContext ctx, String code, String name, String region) {
        return registerInstitute(ctx, code, name, region, null, null);
    }

    public Map<String, Object> registerInstitute(UserSecurityContext ctx, String code, String name, String region,
                                                 String instituteType, String planReference) {
        return ctx.executeInTransaction(db, conn -> {
            requirePlatformInstituteManage(conn, ctx);
            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "code and name are required");
            }
            try (PreparedStatement dup = conn.prepareStatement(
                    "SELECT 1 FROM core.tenants WHERE code = ? AND deleted_at IS NULL")) {
                dup.setString(1, code.trim());
                try (ResultSet rs = dup.executeQuery()) {
                    if (rs.next()) {
                        throw new InstituteAdminException(409, "IADM01_DUPLICATE_CODE",
                                "An institute with this code already exists");
                    }
                }
            }
            UUID id = UUID.randomUUID();
            String profileJson = "{\"type\":\"" + escape(instituteType == null ? "" : instituteType)
                    + "\",\"planReference\":\"" + escape(planReference == null ? "" : planReference)
                    + "\",\"region\":\"" + escape(region == null ? "" : region) + "\"}";
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO core.tenants (id, code, name, status, profile, created_by) "
                            + "VALUES (?, ?, ?, 'ONBOARDING', ?::jsonb, ?)")) {
                ins.setObject(1, id);
                ins.setString(2, code.trim());
                ins.setString(3, name.trim());
                ins.setString(4, profileJson);
                ins.setObject(5, ctx.getUserId());
                ins.executeUpdate();
            }
            String correlation = UUID.randomUUID().toString();
            for (String step : FIRST_R03_STEPS) {
                seedOnboardingStep(conn, id, step, "PENDING", correlation, ctx.getUserId());
            }
            appendEvent(conn, id, "CREATE", ctx.getUserId(), "Institute registered",
                    "{\"code\":\"" + escape(code) + "\",\"status\":\"ONBOARDING\"}");
            appendDurableAudit(conn, ctx, id, "IADM01_INSTITUTE_CREATE", "TENANT", id.toString(),
                    "Institute registered in ONBOARDING");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", id.toString());
            out.put("instituteId", id.toString());
            out.put("code", code.trim());
            out.put("name", name.trim());
            out.put("status", "ONBOARDING");
            out.put("region", region);
            out.put("notificationDeferred", Boolean.TRUE);
            out.put("notificationMessage", "Action completed; notification delivery is delayed.");
            auditMutation(ctx, "IADM01_INSTITUTE_CREATE", "TENANT", id.toString());
            return out;
        });
    }

    /**
     * US-016 / US-024: list institutes (platform-wide for R01/R02; own only for R03).
     */
    public Map<String, Object> listInstitutes(UserSecurityContext ctx, String statusFilter, String search) {
        return ctx.executeInTransaction(db, conn -> {
            boolean platform;
            try {
                ctx.checkPlatformAdmin(conn);
                platform = true;
            } catch (UserProfileAccessDeniedException ex) {
                try {
                    ctx.checkPermission(conn, "adm01.view");
                    platform = false;
                } catch (UserProfileAccessDeniedException ex2) {
                    throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                            "You are not authorized for this action.");
                }
            }
            StringBuilder sql = new StringBuilder(
                    "SELECT t.id, t.code, t.name, t.status, t.created_at, "
                            + "(SELECT count(*) FROM core.colleges c WHERE c.tenant_id = t.id AND c.deleted_at IS NULL) AS college_count "
                            + "FROM core.tenants t WHERE t.deleted_at IS NULL ");
            if (!platform) {
                sql.append("AND t.id = ? ");
            }
            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                sql.append("AND upper(t.status) = upper(?) ");
            }
            if (search != null && !search.trim().isEmpty()) {
                sql.append("AND (t.code ILIKE ? OR t.name ILIKE ?) ");
            }
            sql.append("ORDER BY t.created_at DESC LIMIT 200");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int i = 1;
                if (!platform) {
                    ps.setObject(i++, ctx.getTenantId());
                }
                if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                    ps.setString(i++, statusFilter.trim());
                }
                if (search != null && !search.trim().isEmpty()) {
                    String like = "%" + search.trim() + "%";
                    ps.setString(i++, like);
                    ps.setString(i, like);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("instituteId", rs.getObject("id").toString());
                        row.put("tenantId", rs.getObject("id").toString());
                        row.put("code", rs.getString("code"));
                        row.put("name", rs.getString("name"));
                        row.put("status", rs.getString("status"));
                        row.put("collegeCount", rs.getInt("college_count"));
                        Timestamp created = rs.getTimestamp("created_at");
                        row.put("createdAt", created == null ? null : created.toInstant().toString());
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("institutes", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> getSummary(UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            Map<String, Object> out = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT upper(status) AS status, count(*) AS cnt FROM core.tenants WHERE deleted_at IS NULL GROUP BY 1");
                 ResultSet rs = ps.executeQuery()) {
                Map<String, Integer> byStatus = new LinkedHashMap<>();
                byStatus.put("ONBOARDING", 0);
                byStatus.put("ACTIVE", 0);
                byStatus.put("SUSPENDED", 0);
                byStatus.put("ARCHIVED", 0);
                while (rs.next()) {
                    String st = rs.getString("status");
                    byStatus.put(st, rs.getInt("cnt"));
                }
                out.put("institutesByStatus", byStatus);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM core.colleges WHERE deleted_at IS NULL");
                 ResultSet rs = ps.executeQuery()) {
                out.put("colleges", rs.next() ? rs.getInt(1) : 0);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM plat.institute_lifecycle_requests WHERE status = 'PENDING' AND deleted_at IS NULL");
                 ResultSet rs = ps.executeQuery()) {
                out.put("pendingLifecycleRequests", rs.next() ? rs.getInt(1) : 0);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(DISTINCT tenant_id) FROM plat.iadm01_onboarding_steps "
                            + "WHERE deleted_at IS NULL AND status IN ('PENDING','IN_PROGRESS','FAILED','RETRYABLE') "
                            + "AND workflow_kind = 'FIRST_R03'");
                 ResultSet rs = ps.executeQuery()) {
                out.put("onboardingInProgress", rs.next() ? rs.getInt(1) : 0);
            }
            return out;
        });
    }

    public Map<String, Object> getOnboarding(UserSecurityContext ctx, UUID instituteId) {
        return ctx.executeInTransaction(db, conn -> {
            requirePlatformOrOwnView(conn, ctx, instituteId);
            List<Map<String, Object>> steps = loadOnboardingSteps(conn, instituteId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("instituteId", instituteId.toString());
            out.put("steps", steps);
            out.put("resumeStep", firstIncompleteStep(steps));
            return out;
        });
    }

    /**
     * US-002/003/004/013: start or resume first Institution Admin onboarding.
     * Order: INVITE_LOGIN → CREATE_PROFILE → APPOINT_R03. Retry resumes at failed step only.
     */
    public Map<String, Object> resumeFirstAdminOnboarding(UserSecurityContext ctx, UUID instituteId,
                                                         String email, String displayName, String correlationId,
                                                         boolean simulateInviteFailure) {
        if (instituteId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "instituteId is required");
        }
        // Auth invite is outside the DB transaction (external HTTP); DB steps wrap their own tx.
        Map<String, Object> inviteResult = null;
        String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        // Ensure steps exist
        ctx.executeInTransaction(db, conn -> {
            requirePlatformInstituteManage(conn, ctx);
            ensureOnboardingSteps(conn, instituteId, corr, ctx.getUserId());
            return null;
        });

        Map<String, Object> status = getOnboarding(ctx, instituteId);
        String resume = (String) status.get("resumeStep");
        if (resume == null) {
            Map<String, Object> done = new LinkedHashMap<>(status);
            done.put("complete", Boolean.TRUE);
            done.put("message", "First Institution Admin onboarding is already complete.");
            return done;
        }

        if ("INVITE_LOGIN".equals(resume)) {
            if (simulateInviteFailure) {
                markStep(ctx, instituteId, "INVITE_LOGIN", "FAILED", corr,
                        "Simulated Auth failure for demo retry", null);
                throw new InstituteAdminException(502, "IADM01_AUTH_INVITE_FAILED",
                        "Invitation could not be sent. Retry invitation.");
            }
            inviteResult = authAdminClient.inviteOrCreateUser(email, displayName);
            UUID authUserId = UUID.fromString(inviteResult.get("userId").toString());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authUserId", authUserId.toString());
            payload.put("email", email);
            payload.put("displayName", displayName);
            payload.put("invitationStatus", inviteResult.get("invitationStatus"));
            markStep(ctx, instituteId, "INVITE_LOGIN", "SUCCEEDED", corr, null, payload);
            appendLifecycleAndAudit(ctx, instituteId, "ONBOARDING_STEP", "IADM01_R03_ONBOARDING_STARTED",
                    "First Institution Admin login invited/linked");
            resume = "CREATE_PROFILE";
        }

        // Load auth user from succeeded INVITE_LOGIN payload if not just invited
        UUID authUserId = inviteResult == null ? null : UUID.fromString(inviteResult.get("userId").toString());
        if (authUserId == null) {
            authUserId = loadAuthUserFromStep(ctx, instituteId);
        }
        String resolvedEmail = email;
        String resolvedName = displayName;
        if (resolvedEmail == null || resolvedEmail.trim().isEmpty()) {
            resolvedEmail = loadEmailFromStep(ctx, instituteId);
        }
        if (resolvedName == null || resolvedName.trim().isEmpty()) {
            resolvedName = resolvedEmail;
        }

        if ("CREATE_PROFILE".equals(resume)) {
            try {
                createInstituteProfile(ctx, instituteId, authUserId, resolvedEmail, resolvedName, corr);
                appendLifecycleAndAudit(ctx, instituteId, "ONBOARDING_STEP", "IADM01_R03_PROFILE_CREATED",
                        "Institute profile created for first Institution Admin");
                resume = "APPOINT_R03";
            } catch (InstituteAdminException ex) {
                markStep(ctx, instituteId, "CREATE_PROFILE", "FAILED", corr, ex.getMessage(), null);
                throw new InstituteAdminException(ex.getStatus(), ex.getErrorCode(),
                        "Login exists; institute profile is incomplete.");
            }
        }

        if ("APPOINT_R03".equals(resume)) {
            if (isSecondApproverRequired(ctx) && authUserId != null && authUserId.equals(ctx.getUserId())) {
                throw new InstituteAdminException(403, "IADM01_SELF_APPROVE",
                        "When second-approver is required, the requester cannot approve their own request.");
            }
            try {
                appointR03(ctx, instituteId, authUserId, corr);
                appendLifecycleAndAudit(ctx, instituteId, "APPOINT", "IADM01_R03_APPOINTED",
                        "Institution Admin (R03) appointed");
                // Move institute ONBOARDING → ACTIVE when first R03 is appointed
                ctx.executeInTransaction(db, conn -> {
                    requirePlatformInstituteManage(conn, ctx);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE core.tenants SET status = 'ACTIVE', onboarded_at = coalesce(onboarded_at, now()), "
                                    + "updated_at = now(), updated_by = ? WHERE id = ? AND upper(status) = 'ONBOARDING'")) {
                        ps.setObject(1, ctx.getUserId());
                        ps.setObject(2, instituteId);
                        ps.executeUpdate();
                    }
                    return null;
                });
            } catch (InstituteAdminException ex) {
                markStep(ctx, instituteId, "APPOINT_R03", "FAILED", corr, ex.getMessage(), null);
                throw new InstituteAdminException(ex.getStatus(), ex.getErrorCode(),
                        "Profile created; Institution Admin assignment is pending.");
            }
        }

        Map<String, Object> out = getOnboarding(ctx, instituteId);
        out.put("complete", firstIncompleteStep((List<Map<String, Object>>) out.get("steps")) == null);
        out.put("notificationDeferred", Boolean.TRUE);
        out.put("notificationMessage", "Action completed; notification delivery is delayed.");
        if (inviteResult != null) {
            out.put("invitation", inviteResult);
        }
        return out;
    }

    /** TECH-007: second-approver extension point (default OFF). */
    public boolean isSecondApproverRequired(UserSecurityContext ctx) {
        Boolean sys = Boolean.getBoolean("campx.iadm01.r03.secondApproverRequired");
        if (sys) {
            return true;
        }
        try {
            return ctx.executeInTransaction(db, conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT enabled FROM plat.iadm01_feature_flags WHERE flag_key = 'R03_SECOND_APPROVER_REQUIRED'");
                     ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            });
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getInstitute(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (UserProfileAccessDeniedException ex) {
                ctx.checkPlatformAdmin(conn);
            }
            UUID requested = tenantId != null ? tenantId : ctx.getTenantId();
            // IADM-01-US-005: Institution Admin may only view own institute (platform admins may cross-read)
            if (!ctx.isPlatformScope() && ctx.getTenantId() != null && requested != null
                    && !requested.equals(ctx.getTenantId())) {
                boolean platform = false;
                try {
                    ctx.checkPlatformAdmin(conn);
                    platform = true;
                } catch (UserProfileAccessDeniedException ignored) {
                    // not platform
                }
                if (!platform) {
                    throw new InstituteAdminException(403, "IADM01_OWN_TENANT_ONLY",
                            "Institution Admin may only view own institute profile");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, code, name, status, created_at, updated_at FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, requested);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("tenantId", rs.getObject("id").toString());
                    out.put("code", rs.getString("code"));
                    out.put("name", rs.getString("name"));
                    out.put("status", rs.getString("status"));
                    out.put("createdAt", rs.getTimestamp("created_at"));
                    out.put("updatedAt", rs.getTimestamp("updated_at"));
                    return out;
                }
            }
        });
    }

    public Map<String, Object> listColleges(UserSecurityContext ctx, UUID instituteId, String statusFilter) {
        return ctx.executeInTransaction(db, conn -> {
            UUID tid = instituteId != null ? instituteId : ctx.getTenantId();
            requirePlatformOrOwnView(conn, ctx, tid);
            StringBuilder sql = new StringBuilder(
                    "SELECT id, code, name, status, provisioning_status, created_at FROM core.colleges "
                            + "WHERE tenant_id = ? AND deleted_at IS NULL ");
            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                sql.append("AND upper(status) = upper(?) ");
            }
            sql.append("ORDER BY created_at DESC LIMIT 200");
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setObject(1, tid);
                if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                    ps.setString(2, statusFilter.trim());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("collegeId", rs.getObject("id").toString());
                        row.put("code", rs.getString("code"));
                        row.put("name", rs.getString("name"));
                        row.put("status", rs.getString("status"));
                        row.put("provisioningStatus", rs.getString("provisioning_status"));
                        Timestamp created = rs.getTimestamp("created_at");
                        row.put("createdAt", created == null ? null : created.toInstant().toString());
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("colleges", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> onboardCollege(UserSecurityContext ctx, String collegeCode, String collegeName,
                                              UUID firstCollegeAdminUserId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.manage");
            UUID tenantId = ctx.getTenantId();
            if (tenantId == null) throw new InstituteAdminException(400, "IADM01_VALIDATION", "tenant required");
            if (collegeCode == null || collegeCode.trim().isEmpty() || collegeName == null || collegeName.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "collegeCode and collegeName are required");
            }
            // SEC-11 / FINDING-IADM-ARCH-01: first College Admin (R04) must be Auth-linked
            if (firstCollegeAdminUserId == null) {
                throw new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                        "First College Admin userId is required and must exist in auth.users");
            }
            assertAuthLinkedUser(conn, firstCollegeAdminUserId);
            assertTenantActive(conn, tenantId);
            UUID collegeId = UUID.randomUUID();
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO core.colleges (id, tenant_id, code, name, status, provisioning_status, created_by) " +
                            "VALUES (?, ?, ?, ?, 'ONBOARDING', 'PENDING', ?)")) {
                ins.setObject(1, collegeId);
                ins.setObject(2, tenantId);
                ins.setString(3, collegeCode.trim());
                ins.setString(4, collegeName.trim());
                ins.setObject(5, ctx.getUserId());
                ins.executeUpdate();
            }
            UUID roleId = lookupRole(conn, tenantId, "COLLEGE_ADMIN");
            try (PreparedStatement ra = conn.prepareStatement(
                    "INSERT INTO iam.role_assignments (tenant_id, user_id, role_id, college_id, created_by) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ra.setObject(1, tenantId);
                ra.setObject(2, firstCollegeAdminUserId);
                ra.setObject(3, roleId);
                ra.setObject(4, collegeId);
                ra.setObject(5, ctx.getUserId());
                ra.executeUpdate();
            }
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE core.colleges SET status = 'PENDING_HANDOVER', updated_at = now(), updated_by = ? WHERE id = ?")) {
                up.setObject(1, ctx.getUserId());
                up.setObject(2, collegeId);
                up.executeUpdate();
            }
            UUID handoverId = insertHandover(conn, tenantId, collegeId, firstCollegeAdminUserId, ctx.getUserId(), "PENDING");
            appendEvent(conn, tenantId, "COLLEGE_CREATE", ctx.getUserId(), "College registered",
                    "{\"collegeId\":\"" + collegeId + "\"}");
            appendDurableAudit(conn, ctx, tenantId, "IADM01_COLLEGE_CREATE", "COLLEGE", collegeId.toString(),
                    "College registered");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("tenantId", tenantId.toString());
            out.put("status", "PENDING_HANDOVER");
            out.put("handoverId", handoverId != null ? handoverId.toString() : null);
            out.put("firstCollegeAdminUserId", firstCollegeAdminUserId != null ? firstCollegeAdminUserId.toString() : null);
            auditMutation(ctx, "IADM01_COLLEGE_CREATE", "COLLEGE", collegeId.toString());
            return out;
        });
    }

    public Map<String, Object> completeHandover(UserSecurityContext ctx, UUID collegeId, String notes) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.manage");
            UUID tenantId = ctx.getTenantId();
            // CADM not built: record INTERIM acknowledgements for both receivers (FINDING-IADM01-CADM-01)
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.college_cadm_handovers SET status = 'COMPLETED', notes = ?, "
                            + "cadm01_ack_at = coalesce(cadm01_ack_at, now()), cadm01_interim = true, "
                            + "cadm02_ack_at = coalesce(cadm02_ack_at, now()), cadm02_interim = true, "
                            + "handed_over_at = coalesce(handed_over_at, now()), "
                            + "updated_at = now(), updated_by = ? "
                            + "WHERE tenant_id = ? AND college_id = ? AND deleted_at IS NULL "
                            + "RETURNING id, first_college_admin_user_id")) {
                ps.setString(1, notes);
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, tenantId);
                ps.setObject(4, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_HANDOVER_NOT_FOUND", "No handover record for college");
                    }
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE core.colleges SET status = 'ACTIVE', updated_at = now(), updated_by = ? WHERE id = ?")) {
                        up.setObject(1, ctx.getUserId());
                        up.setObject(2, collegeId);
                        up.executeUpdate();
                    }
                    appendEvent(conn, tenantId, "HANDOVER", ctx.getUserId(),
                            "Handover completed (CADM INTERIM acks)",
                            "{\"collegeId\":\"" + collegeId + "\",\"r03CollegeUserAdmin\":\"REVOKED\",\"interim\":true}");
                    appendDurableAudit(conn, ctx, tenantId, "IADM01_CADM_HANDOVER", "COLLEGE", collegeId.toString(),
                            "CADM handover completed with interim acknowledgements");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("handoverId", rs.getObject("id").toString());
                    out.put("collegeId", collegeId.toString());
                    Object admin = rs.getObject("first_college_admin_user_id");
                    out.put("firstCollegeAdminUserId", admin == null ? null : admin.toString());
                    out.put("status", "COMPLETED");
                    out.put("cadm01Interim", Boolean.TRUE);
                    out.put("cadm02Interim", Boolean.TRUE);
                    out.put("r03CollegeUserAdmin", "REVOKED");
                    auditMutation(ctx, "IADM01_CADM_HANDOVER", "COLLEGE", collegeId.toString());
                    return out;
                }
            }
        });
    }

    /**
     * US-009 read-back: whether Institution Admin may still assign college-scoped users.
     */
    public Map<String, Object> collegeUserAdminBoundary(UserSecurityContext ctx, UUID collegeId) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.view");
            boolean handedOver = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM plat.college_cadm_handovers "
                            + "WHERE college_id = ? AND status = 'COMPLETED' AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    handedOver = rs.next();
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("handedOver", handedOver);
            out.put("r03CollegeUserAdminAllowed", !handedOver);
            return out;
        });
    }

    public Map<String, Object> suspendOrReactivate(UserSecurityContext ctx, UUID tenantId, String action, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED", "Reason is required");
            }
            boolean suspending = "suspend".equalsIgnoreCase(action);
            String status = suspending ? "SUSPENDED" : "ACTIVE";
            String event = suspending ? "SUSPEND" : "REACTIVATE";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE core.tenants SET status = ?, updated_at = now(), updated_by = ? WHERE id = ? AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, tenantId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                }
            }
            // IADM-01-US-006: record session block so suspended tenants fail closed on subsequent ops
            try (PreparedStatement blk = conn.prepareStatement(
                    "INSERT INTO plat.tenant_session_blocks (tenant_id, blocked, reason, actor_id, created_by) VALUES (?,?,?,?,?)")) {
                blk.setObject(1, tenantId);
                blk.setBoolean(2, suspending);
                blk.setString(3, reason);
                blk.setObject(4, ctx.getUserId());
                blk.setObject(5, ctx.getUserId());
                blk.executeUpdate();
            }
            appendEvent(conn, tenantId, event, ctx.getUserId(), reason,
                    "{\"sessionsBlocked\":" + suspending + "}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tenantId.toString());
            out.put("status", status);
            out.put("reason", reason);
            out.put("sessionsBlocked", suspending);
            auditMutation(ctx, action, "TENANT", tenantId.toString());
            return out;
        });
    }

    public Map<String, Object> createLifecycleRequest(UserSecurityContext ctx, String requestType, String reason) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPermission(conn, "adm01.write");
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED", "Reason is required");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.institute_lifecycle_requests " +
                            "(id, tenant_id, request_type, requested_by, status, reason, created_by) " +
                            "VALUES (?, ?, ?, ?, 'PENDING', ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, ctx.getTenantId());
                ps.setString(3, requestType);
                ps.setObject(4, ctx.getUserId());
                ps.setString(5, reason);
                ps.setObject(6, ctx.getUserId());
                ps.executeUpdate();
            }
            String eventType = "EXPORT".equals(requestType) ? "EXPORT_REQUEST" : "DECOMMISSION_REQUEST";
            if ("SUSPEND".equals(requestType)) eventType = "DECOMMISSION_REQUEST";
            appendEvent(conn, ctx.getTenantId(), eventType, ctx.getUserId(), reason, "{\"requestId\":\"" + id + "\"}");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("status", "PENDING");
            out.put("requestType", requestType);
            auditMutation(ctx, "createLifecycleRequest", "LIFECYCLE_REQUEST", id.toString());
            return out;
        });
    }

    /** Platform Operator inbox: pending (and recent) institute lifecycle requests. */
    public Map<String, Object> listLifecycleRequests(UserSecurityContext ctx, String statusFilter) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            List<Map<String, Object>> items = new ArrayList<>();
            String sql = "SELECT id, tenant_id, request_type, status, reason, requested_by, decided_by, decided_at, created_at " +
                    "FROM plat.institute_lifecycle_requests WHERE deleted_at IS NULL ";
            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                sql += "AND status = ? ";
            }
            sql += "ORDER BY created_at DESC LIMIT 100";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                    ps.setString(1, statusFilter.trim().toUpperCase());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("requestId", rs.getObject(1).toString());
                        row.put("tenantId", rs.getObject(2).toString());
                        row.put("requestType", rs.getString(3));
                        row.put("status", rs.getString(4));
                        row.put("reason", rs.getString(5));
                        row.put("requestedBy", rs.getObject(6) == null ? null : rs.getObject(6).toString());
                        row.put("decidedBy", rs.getObject(7) == null ? null : rs.getObject(7).toString());
                        Timestamp decided = rs.getTimestamp(8);
                        row.put("decidedAt", decided == null ? null : decided.toInstant().toString());
                        Timestamp created = rs.getTimestamp(9);
                        row.put("createdAt", created == null ? null : created.toInstant().toString());
                        items.add(row);
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requests", items);
            out.put("count", items.size());
            return out;
        });
    }

    public Map<String, Object> decideLifecycleRequest(UserSecurityContext ctx, UUID requestId, String decision, String note) {
        return ctx.executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            String status = "APPROVE".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision) ? "APPROVED" : "REJECTED";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.institute_lifecycle_requests SET status = ?, decided_by = ?, decided_at = now(), " +
                            "decision_note = ?, updated_at = now(), updated_by = ? " +
                            "WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL " +
                            "RETURNING tenant_id, request_type")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note);
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_REQUEST_NOT_FOUND", "Pending request not found");
                    }
                    UUID tenantId = (UUID) rs.getObject("tenant_id");
                    String type = rs.getString("request_type");
                    if ("APPROVED".equals(status) && "SUSPEND".equals(type)) {
                        try (PreparedStatement up = conn.prepareStatement(
                                "UPDATE core.tenants SET status = 'SUSPENDED', updated_at = now(), updated_by = ? WHERE id = ?")) {
                            up.setObject(1, ctx.getUserId());
                            up.setObject(2, tenantId);
                            up.executeUpdate();
                        }
                        appendEvent(conn, tenantId, "SUSPEND", ctx.getUserId(), note == null ? "Approved suspend request" : note, "{}");
                    }
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("requestId", requestId.toString());
                    out.put("status", status);
                    out.put("tenantId", tenantId.toString());
                    auditMutation(ctx, "decideLifecycleRequest", "LIFECYCLE_REQUEST", requestId.toString());
                    return out;
                }
            }
        });
    }

    public List<Map<String, Object>> lifecycleHistory(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            // IADM-01-US-008: Auditor (R28) via adm01.view; platform admin fallback
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (UserProfileAccessDeniedException ex) {
                ctx.checkPlatformAdmin(conn);
            }
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, event_type, actor_id, reason, payload, created_at " +
                            "FROM plat.institute_lifecycle_events WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 200")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getObject("id").toString());
                        row.put("eventType", rs.getString("event_type"));
                        Object actor = rs.getObject("actor_id");
                        row.put("actorId", actor != null ? actor.toString() : null);
                        row.put("reason", rs.getString("reason"));
                        row.put("payload", rs.getString("payload"));
                        Timestamp ts = rs.getTimestamp("created_at");
                        row.put("createdAt", ts != null ? ts.toInstant().toString() : null);
                        rows.add(row);
                    }
                }
            }
            return rows;
        });
    }

    private void requirePlatformInstituteManage(Connection conn, UserSecurityContext ctx) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return;
        } catch (UserProfileAccessDeniedException ignored) {
            // fall through to permission key
        }
        try {
            ctx.checkPermission(conn, "adm01.manage");
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                    "You are not authorized for this action.");
        }
    }

    private void requirePlatformOrOwnView(Connection conn, UserSecurityContext ctx, UUID instituteId) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return;
        } catch (UserProfileAccessDeniedException ignored) {
        }
        try {
            ctx.checkPermission(conn, "adm01.view");
        } catch (UserProfileAccessDeniedException ex) {
            throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                    "You are not authorized for this action.");
        }
        if (ctx.getTenantId() != null && instituteId != null && !instituteId.equals(ctx.getTenantId())) {
            throw new InstituteAdminException(403, "IADM01_OWN_TENANT_ONLY",
                    "Institution Admin may only view own institute");
        }
    }

    private static void seedOnboardingStep(Connection conn, UUID tenantId, String stepKey, String status,
                                           String correlationId, UUID actor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.iadm01_onboarding_steps "
                        + "(tenant_id, workflow_kind, step_key, status, correlation_id, created_by, updated_by) "
                        + "VALUES (?, 'FIRST_R03', ?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, stepKey);
            ps.setString(3, status);
            ps.setString(4, correlationId);
            ps.setObject(5, actor);
            ps.setObject(6, actor);
            try {
                ps.executeUpdate();
            } catch (SQLException e) {
                // ignore duplicate from partial unique index
                if (!"23505".equals(e.getSQLState())) {
                    throw e;
                }
            }
        }
    }

    private static void ensureOnboardingSteps(Connection conn, UUID tenantId, String correlationId, UUID actor)
            throws SQLException {
        for (String step : FIRST_R03_STEPS) {
            boolean exists;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM plat.iadm01_onboarding_steps WHERE tenant_id = ? AND workflow_kind = 'FIRST_R03' "
                            + "AND step_key = ? AND college_id IS NULL AND deleted_at IS NULL")) {
                ps.setObject(1, tenantId);
                ps.setString(2, step);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                seedOnboardingStep(conn, tenantId, step, "PENDING", correlationId, actor);
            }
        }
    }

    private void markStep(UserSecurityContext ctx, UUID instituteId, String stepKey, String status,
                          String correlationId, String lastError, Map<String, Object> payload) {
        ctx.executeInTransaction(db, conn -> {
            requirePlatformInstituteManage(conn, ctx);
            String payloadJson = payload == null ? "{}" : toSimpleJson(payload);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.iadm01_onboarding_steps SET status = ?, correlation_id = ?, last_error = ?, "
                            + "payload = CASE WHEN ?::text = '{}' THEN payload ELSE ?::jsonb END, "
                            + "updated_at = now(), updated_by = ? "
                            + "WHERE tenant_id = ? AND workflow_kind = 'FIRST_R03' AND step_key = ? "
                            + "AND college_id IS NULL AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setString(2, correlationId);
                ps.setString(3, lastError);
                ps.setString(4, payloadJson);
                ps.setString(5, payloadJson);
                ps.setObject(6, ctx.getUserId());
                ps.setObject(7, instituteId);
                ps.setString(8, stepKey);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private List<Map<String, Object>> loadOnboardingSteps(Connection conn, UUID instituteId) throws SQLException {
        List<Map<String, Object>> steps = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT step_key, status, correlation_id, last_error, payload::text, updated_at "
                        + "FROM plat.iadm01_onboarding_steps WHERE tenant_id = ? AND workflow_kind = 'FIRST_R03' "
                        + "AND college_id IS NULL AND deleted_at IS NULL")) {
            ps.setObject(1, instituteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("stepKey", rs.getString("step_key"));
                    row.put("status", rs.getString("status"));
                    row.put("correlationId", rs.getString("correlation_id"));
                    row.put("lastError", rs.getString("last_error"));
                    row.put("payload", rs.getString("payload"));
                    Timestamp ts = rs.getTimestamp("updated_at");
                    row.put("updatedAt", ts == null ? null : ts.toInstant().toString());
                    steps.add(row);
                }
            }
        }
        // order by FIRST_R03_STEPS
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String key : FIRST_R03_STEPS) {
            for (Map<String, Object> s : steps) {
                if (key.equals(s.get("stepKey"))) {
                    ordered.add(s);
                }
            }
        }
        return ordered;
    }

    private static String firstIncompleteStep(List<Map<String, Object>> steps) {
        if (steps == null) {
            return "INVITE_LOGIN";
        }
        for (String key : FIRST_R03_STEPS) {
            Map<String, Object> found = null;
            for (Map<String, Object> s : steps) {
                if (key.equals(s.get("stepKey"))) {
                    found = s;
                    break;
                }
            }
            if (found == null) {
                return key;
            }
            String st = String.valueOf(found.get("status"));
            if (!"SUCCEEDED".equals(st)) {
                return key;
            }
        }
        return null;
    }

    private UUID loadAuthUserFromStep(UserSecurityContext ctx, UUID instituteId) {
        return ctx.executeInTransaction(db, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT payload->>'authUserId' FROM plat.iadm01_onboarding_steps "
                            + "WHERE tenant_id = ? AND step_key = 'INVITE_LOGIN' AND status = 'SUCCEEDED' "
                            + "AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, instituteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getString(1) == null) {
                        throw new InstituteAdminException(409, "IADM01_LOGIN_REQUIRED",
                                "Login step must succeed before profile creation.");
                    }
                    return UUID.fromString(rs.getString(1));
                }
            }
        });
    }

    private String loadEmailFromStep(UserSecurityContext ctx, UUID instituteId) {
        return ctx.executeInTransaction(db, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT payload->>'email' FROM plat.iadm01_onboarding_steps "
                            + "WHERE tenant_id = ? AND step_key = 'INVITE_LOGIN' AND status = 'SUCCEEDED' "
                            + "AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, instituteId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    private void createInstituteProfile(UserSecurityContext ctx, UUID instituteId, UUID authUserId,
                                        String email, String displayName, String correlationId) {
        // Platform-only SECURITY DEFINER helper — RLS on core.persons requires core.pii.write in-tenant.
        ctx.executeInTransaction(db, conn -> {
            requirePlatformInstituteManage(conn, ctx);
            markStepInConn(conn, instituteId, "CREATE_PROFILE", "IN_PROGRESS", correlationId, null, null, ctx.getUserId());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.iadm01_create_first_admin_profile(?, ?, ?, ?)")) {
                ps.setObject(1, instituteId);
                ps.setObject(2, authUserId);
                ps.setString(3, email);
                ps.setString(4, displayName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new InstituteAdminException(500, "IADM01_PROFILE_FAILED",
                                "Login exists; institute profile is incomplete.");
                    }
                }
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("IADM01_FORBIDDEN")) {
                    throw new InstituteAdminException(403, "IADM01_FORBIDDEN", "You are not authorized for this action.");
                }
                if (msg.contains("IADM01_AUTH_USER_REQUIRED")) {
                    throw new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                            "Target must be a linked Supabase Auth user");
                }
                throw new InstituteAdminException(500, "IADM01_PROFILE_FAILED",
                        "Login exists; institute profile is incomplete.");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authUserId", authUserId.toString());
            markStepInConn(conn, instituteId, "CREATE_PROFILE", "SUCCEEDED", correlationId, null, payload, ctx.getUserId());
            return null;
        });
    }

    private void appointR03(UserSecurityContext ctx, UUID instituteId, UUID authUserId, String correlationId) {
        UserSecurityContext scoped = ctx.withTenant(instituteId);
        scoped.executeInTransaction(db, conn -> {
            requirePlatformInstituteManage(conn, scoped);
            markStepInConn(conn, instituteId, "APPOINT_R03", "IN_PROGRESS", correlationId, null, null, scoped.getUserId());
            assertAuthLinkedUser(conn, authUserId);
            // Spoof regression: p_assigned_by ignored; auth.uid() from session claims is authoritative (K1)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.assign_institution_admin(?, ?, ?)")) {
                ps.setObject(1, instituteId);
                ps.setObject(2, authUserId);
                ps.setObject(3, scoped.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(500, "IADM01_APPOINT_FAILED",
                                "Profile created; Institution Admin assignment is pending.");
                    }
                    String result = rs.getString(1);
                    if (result != null && result.toUpperCase().startsWith("REFUSED")) {
                        throw new InstituteAdminException(403, "IADM01_APPOINT_REFUSED",
                                result);
                    }
                }
            }
            markStepInConn(conn, instituteId, "APPOINT_R03", "SUCCEEDED", correlationId, null, null, scoped.getUserId());
            appendDurableAudit(conn, scoped, instituteId, "IADM01_R03_APPOINTED", "TENANT", instituteId.toString(),
                    "Institution Admin appointed");
            return null;
        });
    }

    private void appendLifecycleAndAudit(UserSecurityContext ctx, UUID instituteId, String eventType,
                                         String auditAction, String reason) {
        ctx.executeInTransaction(db, conn -> {
            appendEvent(conn, instituteId, eventType, ctx.getUserId(), reason, "{}");
            appendDurableAudit(conn, ctx, instituteId, auditAction, "TENANT", instituteId.toString(), reason);
            return null;
        });
    }

    private static void markStepInConn(Connection conn, UUID instituteId, String stepKey, String status,
                                       String correlationId, String lastError, Map<String, Object> payload, UUID actor)
            throws SQLException {
        String payloadJson = payload == null ? "{}" : toSimpleJson(payload);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.iadm01_onboarding_steps SET status = ?, correlation_id = ?, last_error = ?, "
                        + "payload = CASE WHEN ?::text = '{}' THEN payload ELSE ?::jsonb END, "
                        + "updated_at = now(), updated_by = ? "
                        + "WHERE tenant_id = ? AND workflow_kind = 'FIRST_R03' AND step_key = ? "
                        + "AND college_id IS NULL AND deleted_at IS NULL")) {
            ps.setString(1, status);
            ps.setString(2, correlationId);
            ps.setString(3, lastError);
            ps.setString(4, payloadJson);
            ps.setString(5, payloadJson);
            ps.setObject(6, actor);
            ps.setObject(7, instituteId);
            ps.setString(8, stepKey);
            ps.executeUpdate();
        }
    }

    private static void assertAuthLinkedUser(Connection conn, UUID userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT plat.is_auth_linked_user(?)")) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                            "Target must be a linked Supabase Auth user");
                }
            }
        }
    }

    private static void assertTenantActive(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                if ("SUSPENDED".equalsIgnoreCase(rs.getString("status"))) {
                    throw new InstituteAdminException(403, "IADM01_SUSPENDED", "Institute is suspended");
                }
            }
        }
    }

    private static UUID lookupRole(Connection conn, UUID tenantId, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM iam.roles WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new InstituteAdminException(500, "IADM01_ROLE_MISSING", code + " role missing");
                return (UUID) rs.getObject(1);
            }
        }
    }

    private static UUID insertHandover(Connection conn, UUID tenantId, UUID collegeId, UUID adminId, UUID by, String status)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.college_cadm_handovers "
                        + "(id, tenant_id, college_id, first_college_admin_user_id, handed_over_by, status, "
                        + "cadm01_interim, cadm02_interim, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, true, true, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, collegeId);
            ps.setObject(4, adminId);
            ps.setObject(5, by);
            ps.setString(6, status);
            ps.setObject(7, by);
            ps.executeUpdate();
        }
        return id;
    }

    private static void appendEvent(Connection conn, UUID tenantId, String type, UUID actor, String reason, String payloadJson)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.institute_lifecycle_events (tenant_id, event_type, actor_id, reason, payload, created_by) " +
                        "VALUES (?, ?, ?, ?, ?::jsonb, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, type);
            ps.setObject(3, actor);
            ps.setString(4, reason);
            ps.setString(5, payloadJson == null ? "{}" : payloadJson);
            ps.setObject(6, actor);
            ps.executeUpdate();
        }
    }

    /**
     * TECH-003: durable shared-audit write via {@code audit.log_event} (SECURITY DEFINER).
     * Plain language: inserts into {@code audit.events} without fighting RLS on direct INSERT.
     * Success is returned only after this call succeeds on the same transaction connection.
     */
    private static void appendDurableAudit(Connection conn, UserSecurityContext ctx, UUID tenantId,
                                           String action, String resourceType, String resourceId, String detail)
            throws SQLException {
        String detailJson = "{\"message\":\"" + escape(detail) + "\"}";
        UUID resourceUuid = null;
        if (resourceId != null && !resourceId.trim().isEmpty()) {
            try {
                resourceUuid = UUID.fromString(resourceId.trim());
            } catch (IllegalArgumentException ignored) {
                resourceUuid = null;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT audit.log_event(?, ?, ?, ?, ?, 'SUCCESS', 'INFO', NULL, ?::jsonb, NULL)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ctx.getUserId());
            ps.setString(3, action);
            ps.setString(4, resourceType);
            ps.setObject(5, resourceUuid);
            ps.setString(6, detailJson);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject(1) == null) {
                    throw new SQLException("audit.log_event returned no id — mutation cannot succeed without durable audit");
                }
            }
        }
    }

    private static String toSimpleJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"");
            sb.append(escape(e.getValue() == null ? "" : e.getValue().toString())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void auditMutation(UserSecurityContext ctx, String action, String resourceType, String resourceId) {
        String tenant = ctx.getTenantId() == null ? null : ctx.getTenantId().toString();
        String user = ctx.getUserId() == null ? null : ctx.getUserId().toString();
        logger.info("IADM-01 action={} tenantId={} resourceType={} resourceId={}", action, tenant, resourceType, resourceId);
        logger.audit(AuditEvent.builder().action(action).tenantId(tenant).principalId(user)
                .resourceType(resourceType).resourceId(resourceId == null ? "N/A" : resourceId)
                .status("SUCCESS").severity("INFO").description(action).build());
    }
}
