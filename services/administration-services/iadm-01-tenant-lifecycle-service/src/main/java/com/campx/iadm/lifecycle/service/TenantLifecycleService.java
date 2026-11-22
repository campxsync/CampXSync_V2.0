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
    private static final List<String> FIRST_R04_STEPS = Arrays.asList("INVITE_LOGIN", "CREATE_PROFILE", "APPOINT_R04");

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
    private static final java.util.Set<String> ALLOWED_INSTITUTE_TYPES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "UNIVERSITY", "DEEMED_UNIVERSITY", "AUTONOMOUS_COLLEGE",
                    "AFFILIATED_COLLEGE_GROUP", "POLYTECHNIC", "SCHOOL_GROUP", "OTHER"));
    private static final java.util.Set<String> ALLOWED_PLAN_REFERENCES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "ENTERPRISE", "GROWTH", "STANDARD", "TRIAL_30_DAYS", "CUSTOM", "TRIAL"));

    public Map<String, Object> registerInstitute(UserSecurityContext ctx, String code, String name, String contact) {
        return registerInstitute(ctx, code, name, contact, null, null);
    }

    /**
     * US-001: register institute. {@code contact} is the primary outreach email/phone (story field "contact").
     * Plan reference is a link code only — IADM-04 owns entitlements.
     */
    public Map<String, Object> registerInstitute(UserSecurityContext ctx, String code, String name, String contact,
                                                 String instituteType, String planReference) {
        return ctx.executeInTransaction(db, conn -> {
            requirePlatformInstituteManage(conn, ctx);
            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "code and name are required");
            }
            if (contact == null || contact.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION",
                        "Primary contact (email or phone) is required");
            }
            if (instituteType == null || instituteType.trim().isEmpty()
                    || !ALLOWED_INSTITUTE_TYPES.contains(instituteType.trim().toUpperCase())) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION",
                        "Select a valid institute type");
            }
            String typeNorm = instituteType.trim().toUpperCase();
            String planNorm = planReference == null ? "" : planReference.trim().toUpperCase();
            if ("TRIAL".equals(planNorm)) {
                planNorm = "TRIAL_30_DAYS";
            }
            if (planNorm.isEmpty() || !ALLOWED_PLAN_REFERENCES.contains(planNorm)) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION",
                        "Select a valid plan reference");
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
            String contactTrim = contact.trim();
            String profileJson = "{\"type\":\"" + escape(typeNorm)
                    + "\",\"planReference\":\"" + escape(planNorm)
                    + "\",\"contact\":\"" + escape(contactTrim) + "\"}";
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
            out.put("type", typeNorm);
            out.put("planReference", planNorm);
            out.put("contact", contactTrim);
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
            inviteResult = authAdminClient.inviteOrCreateUser(email, displayName, emailLookup(ctx));
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
                if ("IADM01_PROFILE_OTHER_TENANT".equals(ex.getErrorCode())) {
                    throw ex;
                }
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
            String statusArg = (statusFilter == null || statusFilter.trim().isEmpty()) ? null : statusFilter.trim();
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, code, name, status, provisioning_status, profile::text, created_at, handover_status "
                            + "FROM plat.iadm01_list_colleges(?, ?)")) {
                ps.setObject(1, tid);
                ps.setString(2, statusArg);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapCollegeListRow(rs));
                    }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("instituteId", tid.toString());
            out.put("colleges", items);
            out.put("count", items.size());
            return out;
        });
    }

    /**
     * US-006: Register college under institute; seed FIRST_R04 onboarding steps (no R04 yet).
     */
    public Map<String, Object> createCollege(UserSecurityContext ctx, UUID instituteId, String code, String name,
                                             String campusCode, String location) {
        if (instituteId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "instituteId is required");
        }
        UserSecurityContext scoped = ctx.withTenant(instituteId);
        return scoped.executeInTransaction(db, conn -> {
            requireCollegeManage(conn, scoped, instituteId);
            if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "code and name are required");
            }
            assertTenantActive(conn, instituteId);
            assertInstituteReadyForColleges(conn, instituteId);
            try (PreparedStatement dup = conn.prepareStatement(
                    "SELECT 1 FROM core.colleges WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL")) {
                dup.setObject(1, instituteId);
                dup.setString(2, code.trim());
                try (ResultSet rs = dup.executeQuery()) {
                    if (rs.next()) {
                        throw new InstituteAdminException(409, "IADM01_DUPLICATE_CODE",
                                "A college with this code already exists");
                    }
                }
            }
            String campus = campusCode == null ? "" : campusCode.trim();
            String loc = location == null ? "" : location.trim();
            String correlation = UUID.randomUUID().toString();
            UUID collegeId;
            try (PreparedStatement ins = conn.prepareStatement(
                    "SELECT plat.iadm01_create_college(?, ?, ?, ?, ?)")) {
                ins.setObject(1, instituteId);
                ins.setString(2, code.trim());
                ins.setString(3, name.trim());
                ins.setString(4, campus);
                ins.setString(5, loc);
                try (ResultSet rs = ins.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new InstituteAdminException(500, "IADM01_COLLEGE_CREATE_FAILED",
                                "College could not be registered");
                    }
                    collegeId = (UUID) rs.getObject(1);
                }
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("IADM01_DUPLICATE_CODE")) {
                    throw new InstituteAdminException(409, "IADM01_DUPLICATE_CODE",
                            "A college with this code already exists");
                }
                if (msg.contains("IADM01_FORBIDDEN")) {
                    throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                            "You are not authorized for this action.");
                }
                throw new InstituteAdminException(500, "IADM01_COLLEGE_CREATE_FAILED",
                        "College could not be registered");
            }
            for (String step : FIRST_R04_STEPS) {
                seedCollegeOnboardingStep(conn, instituteId, collegeId, step, "PENDING", correlation, ctx.getUserId());
            }
            appendEvent(conn, instituteId, "COLLEGE_CREATE", ctx.getUserId(), "College registered",
                    "{\"collegeId\":\"" + collegeId + "\",\"status\":\"ONBOARDING\"}");
            appendDurableAudit(conn, ctx, instituteId, "IADM01_COLLEGE_CREATE", "COLLEGE", collegeId.toString(),
                    "College registered in ONBOARDING");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("instituteId", instituteId.toString());
            out.put("tenantId", instituteId.toString());
            out.put("code", code.trim());
            out.put("name", name.trim());
            out.put("status", "ONBOARDING");
            out.put("campusCode", campus.isEmpty() ? null : campus);
            out.put("location", loc.isEmpty() ? null : loc);
            auditMutation(ctx, "IADM01_COLLEGE_CREATE", "COLLEGE", collegeId.toString());
            return out;
        });
    }

    /**
     * @deprecated Legacy onboard path; delegates to {@link #createCollege} without first-admin provisioning.
     */
    public Map<String, Object> onboardCollege(UserSecurityContext ctx, String collegeCode, String collegeName,
                                              UUID firstCollegeAdminUserId) {
        UUID instituteId = ctx.getTenantId();
        if (instituteId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "tenant required");
        }
        Map<String, Object> created = createCollege(ctx, instituteId, collegeCode, collegeName, null, null);
        created.put("deprecated", Boolean.TRUE);
        created.put("message", "Use POST /institutes/{id}/colleges and first-admin onboarding instead.");
        if (firstCollegeAdminUserId != null) {
            created.put("ignoredFirstCollegeAdminUserId", firstCollegeAdminUserId.toString());
        }
        return created;
    }

    /** US-020 read: college detail with handover and first-admin onboarding summary. */
    public Map<String, Object> getCollege(UserSecurityContext ctx, UUID collegeId) {
        if (collegeId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "collegeId is required");
        }
        return ctx.executeInTransaction(db, conn -> {
            CollegeRow row = loadCollegeRow(conn, collegeId);
            requirePlatformOrOwnView(conn, ctx, row.tenantId);
            Map<String, Object> out = mapCollegeDetail(row);
            out.put("collegeId", collegeId.toString());
            out.put("instituteId", row.tenantId.toString());
            out.put("tenantId", row.tenantId.toString());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, status, first_college_admin_user_id, cadm01_interim, cadm02_interim, handed_over_at "
                            + "FROM plat.college_cadm_handovers "
                            + "WHERE college_id = ? AND tenant_id = ? AND deleted_at IS NULL "
                            + "ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, collegeId);
                ps.setObject(2, row.tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out.put("handoverId", rs.getObject("id").toString());
                        out.put("handoverStatus", rs.getString("status"));
                        Object admin = rs.getObject("first_college_admin_user_id");
                        out.put("firstCollegeAdminUserId", admin == null ? null : admin.toString());
                        out.put("cadm01Interim", rs.getBoolean("cadm01_interim"));
                        out.put("cadm02Interim", rs.getBoolean("cadm02_interim"));
                        Timestamp ho = rs.getTimestamp("handed_over_at");
                        out.put("handedOverAt", ho == null ? null : ho.toInstant().toString());
                    }
                }
            }
            List<Map<String, Object>> steps = loadCollegeOnboardingSteps(conn, row.tenantId, collegeId);
            out.put("firstAdminOnboarding", steps);
            out.put("firstAdminResumeStep", firstIncompleteStep(steps, FIRST_R04_STEPS));
            return out;
        });
    }

    /** US-020 update: identity fields until handover completes; code is immutable. */
    public Map<String, Object> updateCollege(UserSecurityContext ctx, UUID collegeId, String name,
                                             String campusCode, String location) {
        if (collegeId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "collegeId is required");
        }
        return ctx.executeInTransaction(db, conn -> {
            CollegeRow row = loadCollegeRow(conn, collegeId);
            requireCollegeManage(conn, ctx, row.tenantId);
            if (isHandoverCompleted(conn, row.tenantId, collegeId)) {
                throw new InstituteAdminException(409, "IADM01_HANDOVER_COMPLETE",
                        "This college has been handed over; profile changes belong to College Administration.");
            }
            if (name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "name is required");
            }
            String campus = campusCode == null ? "" : campusCode.trim();
            String loc = location == null ? "" : location.trim();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.iadm01_update_college_identity(?, ?, ?, ?)")) {
                ps.setObject(1, collegeId);
                ps.setString(2, name.trim());
                ps.setString(3, campus);
                ps.setString(4, loc);
                ps.executeQuery();
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("IADM01_HANDED_OVER")) {
                    throw new InstituteAdminException(409, "IADM01_HANDOVER_COMPLETE",
                            "This college has been handed over; profile changes belong to College Administration.");
                }
                if (msg.contains("IADM01_FORBIDDEN")) {
                    throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                            "You are not authorized for this action.");
                }
                throw new InstituteAdminException(500, "IADM01_COLLEGE_UPDATE_FAILED",
                        "College identity could not be updated");
            }
            appendEvent(conn, row.tenantId, "COLLEGE_UPDATE", ctx.getUserId(), "College identity updated",
                    "{\"collegeId\":\"" + collegeId + "\"}");
            appendDurableAudit(conn, ctx, row.tenantId, "IADM01_COLLEGE_UPDATE", "COLLEGE", collegeId.toString(),
                    "College identity updated");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("instituteId", row.tenantId.toString());
            out.put("name", name.trim());
            out.put("campusCode", campus.isEmpty() ? null : campus);
            out.put("location", loc.isEmpty() ? null : loc);
            auditMutation(ctx, "IADM01_COLLEGE_UPDATE", "COLLEGE", collegeId.toString());
            return out;
        });
    }

    /** US-018: institute identity (name, contact, location); code/status/plan immutable. */
    public Map<String, Object> updateInstitute(UserSecurityContext ctx, UUID instituteId, String name,
                                               String contact, String location) {
        if (instituteId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "instituteId is required");
        }
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPlatformAdmin(conn);
            } catch (UserProfileAccessDeniedException ignored) {
                requireCollegeManage(conn, ctx, instituteId);
            }
            if (name == null || name.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "name is required");
            }
            String contactTrim = contact == null ? "" : contact.trim();
            String loc = location == null ? "" : location.trim();
            String profilePatch = "{\"contact\":\"" + escape(contactTrim)
                    + "\",\"location\":\"" + escape(loc) + "\"}";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE core.tenants SET name = ?, profile = coalesce(profile, '{}'::jsonb) || ?::jsonb, "
                            + "updated_at = now(), updated_by = ? "
                            + "WHERE id = ? AND deleted_at IS NULL "
                            + "RETURNING code, status, profile::text")) {
                ps.setString(1, name.trim());
                ps.setString(2, profilePatch);
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, instituteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                    }
                    appendDurableAudit(conn, ctx, instituteId, "IADM01_INSTITUTE_UPDATE", "TENANT",
                            instituteId.toString(), "Institute identity updated");
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("instituteId", instituteId.toString());
                    out.put("tenantId", instituteId.toString());
                    out.put("code", rs.getString("code"));
                    out.put("name", name.trim());
                    out.put("status", rs.getString("status"));
                    out.put("contact", contactTrim.isEmpty() ? null : contactTrim);
                    out.put("location", loc.isEmpty() ? null : loc);
                    auditMutation(ctx, "IADM01_INSTITUTE_UPDATE", "TENANT", instituteId.toString());
                    return out;
                }
            }
        });
    }

    public Map<String, Object> getCollegeOnboarding(UserSecurityContext ctx, UUID collegeId) {
        if (collegeId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "collegeId is required");
        }
        return ctx.executeInTransaction(db, conn -> {
            CollegeRow row = loadCollegeRow(conn, collegeId);
            requirePlatformOrOwnView(conn, ctx, row.tenantId);
            List<Map<String, Object>> steps = loadCollegeOnboardingSteps(conn, row.tenantId, collegeId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("collegeId", collegeId.toString());
            out.put("instituteId", row.tenantId.toString());
            out.put("steps", steps);
            out.put("resumeStep", firstIncompleteStep(steps, FIRST_R04_STEPS));
            return out;
        });
    }

    /**
     * US-007: invite → profile → appoint first College Admin (R04) for one college.
     */
    public Map<String, Object> resumeFirstCollegeAdminOnboarding(UserSecurityContext ctx, UUID collegeId,
                                                                 String email, String displayName, String correlationId,
                                                                 boolean simulateInviteFailure) {
        if (collegeId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "collegeId is required");
        }
        Map<String, Object> inviteResult = null;
        String corr = correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId.trim();

        UUID instituteId = ctx.executeInTransaction(db, conn -> {
            CollegeRow row = loadCollegeRow(conn, collegeId);
            requireCollegeManage(conn, ctx, row.tenantId);
            ensureCollegeOnboardingSteps(conn, row.tenantId, collegeId, corr, ctx.getUserId());
            return row.tenantId;
        });

        Map<String, Object> status = getCollegeOnboarding(ctx, collegeId);
        String resume = (String) status.get("resumeStep");
        if (resume == null) {
            Map<String, Object> done = new LinkedHashMap<>(status);
            done.put("complete", Boolean.TRUE);
            done.put("message", "First College Admin onboarding is already complete.");
            return done;
        }

        if ("INVITE_LOGIN".equals(resume)) {
            if (simulateInviteFailure) {
                markCollegeStep(ctx, instituteId, collegeId, "INVITE_LOGIN", "FAILED", corr,
                        "Simulated Auth failure for demo retry", null);
                throw new InstituteAdminException(502, "IADM01_AUTH_INVITE_FAILED",
                        "Invitation could not be sent. Retry invitation.");
            }
            inviteResult = authAdminClient.inviteOrCreateUser(email, displayName, emailLookup(ctx));
            UUID authUserId = UUID.fromString(inviteResult.get("userId").toString());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authUserId", authUserId.toString());
            payload.put("email", email);
            payload.put("displayName", displayName);
            payload.put("invitationStatus", inviteResult.get("invitationStatus"));
            markCollegeStep(ctx, instituteId, collegeId, "INVITE_LOGIN", "SUCCEEDED", corr, null, payload);
            appendCollegeLifecycleAndAudit(ctx, instituteId, collegeId, "ONBOARDING_STEP",
                    "IADM01_R04_ONBOARDING_STARTED", "First College Admin login invited/linked");
            resume = "CREATE_PROFILE";
        }

        UUID authUserId = inviteResult == null ? null : UUID.fromString(inviteResult.get("userId").toString());
        if (authUserId == null) {
            authUserId = loadAuthUserFromCollegeStep(ctx, instituteId, collegeId);
        }
        String resolvedEmail = email;
        String resolvedName = displayName;
        if (resolvedEmail == null || resolvedEmail.trim().isEmpty()) {
            resolvedEmail = loadEmailFromCollegeStep(ctx, instituteId, collegeId);
        }
        if (resolvedName == null || resolvedName.trim().isEmpty()) {
            resolvedName = resolvedEmail;
        }

        if ("CREATE_PROFILE".equals(resume)) {
            try {
                createCollegeAdminProfile(ctx, instituteId, collegeId, authUserId, resolvedEmail, resolvedName, corr);
                resume = "APPOINT_R04";
            } catch (InstituteAdminException ex) {
                markCollegeStep(ctx, instituteId, collegeId, "CREATE_PROFILE", "FAILED", corr, ex.getMessage(), null);
                throw new InstituteAdminException(ex.getStatus(), ex.getErrorCode(),
                        "Login exists; college admin profile is incomplete.");
            }
        }

        if ("APPOINT_R04".equals(resume)) {
            try {
                appointR04(ctx, instituteId, collegeId, authUserId, corr);
                appendCollegeLifecycleAndAudit(ctx, instituteId, collegeId, "R04_APPOINT",
                        "IADM01_R04_APPOINTED", "College Admin (R04) appointed");
            } catch (InstituteAdminException ex) {
                markCollegeStep(ctx, instituteId, collegeId, "APPOINT_R04", "FAILED", corr, ex.getMessage(), null);
                throw new InstituteAdminException(ex.getStatus(), ex.getErrorCode(),
                        "Profile created; College Admin assignment is pending.");
            }
        }

        Map<String, Object> out = getCollegeOnboarding(ctx, collegeId);
        out.put("complete", firstIncompleteStep((List<Map<String, Object>>) out.get("steps"), FIRST_R04_STEPS) == null);
        out.put("notificationDeferred", Boolean.TRUE);
        out.put("notificationMessage", "Action completed; notification delivery is delayed.");
        if (inviteResult != null) {
            out.put("invitation", inviteResult);
        }
        return out;
    }

    public Map<String, Object> completeHandover(UserSecurityContext ctx, UUID collegeId, String notes) {
        return ctx.executeInTransaction(db, conn -> {
            CollegeRow row = loadCollegeRow(conn, collegeId);
            requireCollegeManage(conn, ctx, row.tenantId);
            if (!"PENDING_HANDOVER".equalsIgnoreCase(row.status)) {
                throw new InstituteAdminException(409, "IADM01_HANDOVER_NOT_READY",
                        "Handover is allowed only when the college is in PENDING_HANDOVER with a provisioned College Admin.");
            }
            UUID provisionedAdmin = null;
            UUID handoverId = null;
            try (PreparedStatement find = conn.prepareStatement(
                    "SELECT id, first_college_admin_user_id, status FROM plat.college_cadm_handovers "
                            + "WHERE tenant_id = ? AND college_id = ? AND deleted_at IS NULL "
                            + "ORDER BY created_at DESC LIMIT 1")) {
                find.setObject(1, row.tenantId);
                find.setObject(2, collegeId);
                try (ResultSet frs = find.executeQuery()) {
                    if (!frs.next()) {
                        throw new InstituteAdminException(404, "IADM01_HANDOVER_NOT_FOUND",
                                "No handover record for college");
                    }
                    handoverId = (UUID) frs.getObject("id");
                    provisionedAdmin = (UUID) frs.getObject("first_college_admin_user_id");
                    String hoSt = frs.getString("status");
                    if (provisionedAdmin == null) {
                        throw new InstituteAdminException(409, "IADM01_R04_REQUIRED",
                                "First College Admin must be provisioned before handover.");
                    }
                    if ("COMPLETED".equalsIgnoreCase(hoSt)) {
                        throw new InstituteAdminException(409, "IADM01_HANDOVER_COMPLETE",
                                "Handover for this college is already complete.");
                    }
                }
            }
            // CADM not built: record INTERIM acknowledgements for both receivers (FINDING-IADM01-CADM-01)
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.college_cadm_handovers SET status = 'COMPLETED', notes = ?, "
                            + "first_college_admin_user_id = coalesce(first_college_admin_user_id, ?), "
                            + "cadm01_ack_at = coalesce(cadm01_ack_at, now()), cadm01_interim = true, "
                            + "cadm02_ack_at = coalesce(cadm02_ack_at, now()), cadm02_interim = true, "
                            + "handed_over_at = coalesce(handed_over_at, now()), "
                            + "updated_at = now(), updated_by = ? "
                            + "WHERE id = ? AND deleted_at IS NULL "
                            + "RETURNING id, first_college_admin_user_id")) {
                ps.setString(1, notes);
                ps.setObject(2, provisionedAdmin);
                ps.setObject(3, ctx.getUserId());
                ps.setObject(4, handoverId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_HANDOVER_NOT_FOUND", "No handover record for college");
                    }
                    try (PreparedStatement up = conn.prepareStatement(
                            "SELECT plat.iadm01_set_college_status(?, 'ACTIVE')")) {
                        up.setObject(1, collegeId);
                        up.executeQuery();
                    }
                    appendEvent(conn, row.tenantId, "HANDOVER", ctx.getUserId(),
                            "Handover completed (CADM INTERIM acks)",
                            "{\"collegeId\":\"" + collegeId + "\",\"r03CollegeUserAdmin\":\"REVOKED\",\"interim\":true}");
                    appendDurableAudit(conn, ctx, row.tenantId, "IADM01_CADM_HANDOVER", "COLLEGE", collegeId.toString(),
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
        return ctx.withTenant(tenantId).executeInTransaction(db, conn -> {
            ctx.checkPlatformAdmin(conn);
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED", "Reason is required");
            }
            String current = loadTenantStatus(conn, tenantId);
            boolean suspending = "suspend".equalsIgnoreCase(action);
            boolean archiving = "archive".equalsIgnoreCase(action);
            String status;
            String event;
            String auditAction;
            if (archiving) {
                // ACTIVE/SUSPENDED = normal archive; ONBOARDING = abandon incomplete duplicate/onboarding.
                if (!"ACTIVE".equalsIgnoreCase(current)
                        && !"SUSPENDED".equalsIgnoreCase(current)
                        && !"ONBOARDING".equalsIgnoreCase(current)) {
                    throw new InstituteAdminException(409, "IADM01_INVALID_TRANSITION",
                            "Archive is allowed only from ACTIVE, SUSPENDED, or ONBOARDING.");
                }
                status = "ARCHIVED";
                event = "ARCHIVE";
                auditAction = "IADM01_INSTITUTE_ARCHIVED";
            } else if (suspending) {
                if (!"ACTIVE".equalsIgnoreCase(current)) {
                    throw new InstituteAdminException(409, "IADM01_INVALID_TRANSITION",
                            "Suspend is allowed only from ACTIVE.");
                }
                status = "SUSPENDED";
                event = "SUSPEND";
                auditAction = "IADM01_INSTITUTE_SUSPENDED";
            } else {
                if (!"SUSPENDED".equalsIgnoreCase(current)) {
                    throw new InstituteAdminException(409, "IADM01_INVALID_TRANSITION",
                            "Reactivate is allowed only from SUSPENDED.");
                }
                status = "ACTIVE";
                event = "REACTIVATE";
                auditAction = "IADM01_INSTITUTE_REACTIVATED";
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE core.tenants SET status = ?, updated_at = now(), updated_by = ? WHERE id = ? AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setObject(3, tenantId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                }
            }
            // Session fail-closed uses plat.is_tenant_suspended(status) — no separate session_blocks table in DEV.
            appendEvent(conn, tenantId, event, ctx.getUserId(), reason,
                    "{\"sessionsBlocked\":" + suspending + ",\"from\":\"" + current + "\"}");
            appendDurableAudit(conn, ctx, tenantId, auditAction, "TENANT", tenantId.toString(), reason);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tenantId.toString());
            out.put("instituteId", tenantId.toString());
            out.put("status", status);
            out.put("previousStatus", current);
            out.put("reason", reason);
            out.put("sessionsBlocked", suspending);
            if (suspending) {
                out.put("message", "This institute is suspended. Contact the platform administrator.");
            }
            auditMutation(ctx, auditAction, "TENANT", tenantId.toString());
            return out;
        });
    }

    /**
     * US-010 / US-022 / US-023: R03 (or platform support) requests suspend/archive/decommission/full export.
     * Optional collegeId scopes the request to a college (US-022).
     */
    public Map<String, Object> createLifecycleRequest(UserSecurityContext ctx, UUID instituteId, String requestType,
                                                      String reason, UUID collegeId) {
        if (instituteId == null) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION", "instituteId is required");
        }
        String type = requestType == null ? "" : requestType.trim().toUpperCase();
        if ("EXPORT".equals(type)) {
            type = "FULL_EXPORT";
        }
        if (!"SUSPEND".equals(type) && !"ARCHIVE".equals(type) && !"DECOMMISSION".equals(type) && !"FULL_EXPORT".equals(type)) {
            throw new InstituteAdminException(400, "IADM01_VALIDATION",
                    "requestType must be SUSPEND, ARCHIVE, DECOMMISSION, or FULL_EXPORT");
        }
        final String normalizedType = type;
        return ctx.withTenant(instituteId).executeInTransaction(db, conn -> {
            requireLifecycleRequestCreate(conn, ctx, instituteId);
            if (reason == null || reason.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED", "Reason is required");
            }
            if (collegeId != null) {
                CollegeRow college = loadCollegeRow(conn, collegeId);
                if (!instituteId.equals(college.tenantId)) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "College not found in this institute");
                }
                if (!"SUSPEND".equals(normalizedType) && !"ARCHIVE".equals(normalizedType)) {
                    throw new InstituteAdminException(400, "IADM01_VALIDATION",
                            "College lifecycle requests support SUSPEND or ARCHIVE only");
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO plat.institute_lifecycle_requests "
                            + "(id, tenant_id, college_id, request_type, requested_by, status, reason, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, instituteId);
                ps.setObject(3, collegeId);
                ps.setString(4, normalizedType);
                ps.setObject(5, ctx.getUserId());
                ps.setString(6, reason.trim());
                ps.setObject(7, ctx.getUserId());
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new InstituteAdminException(500, "IADM01_REQUEST_FAILED",
                        "Lifecycle request could not be stored");
            }
            // Event types constrained by ck_institute_lifecycle_events_type
            String eventType = "FULL_EXPORT".equals(normalizedType) ? "EXPORT_REQUEST" : "DECOMMISSION_REQUEST";
            appendEvent(conn, instituteId, eventType, ctx.getUserId(), reason.trim(),
                    "{\"requestId\":\"" + id + "\",\"requestType\":\"" + normalizedType + "\""
                            + (collegeId == null ? "" : ",\"collegeId\":\"" + collegeId + "\"") + "}");
            appendDurableAudit(conn, ctx, instituteId, "IADM01_LIFECYCLE_REQUESTED", "LIFECYCLE_REQUEST", id.toString(),
                    normalizedType + " requested");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", id.toString());
            out.put("instituteId", instituteId.toString());
            out.put("tenantId", instituteId.toString());
            out.put("collegeId", collegeId == null ? null : collegeId.toString());
            out.put("status", "PENDING");
            out.put("requestType", normalizedType);
            out.put("reason", reason.trim());
            auditMutation(ctx, "IADM01_LIFECYCLE_REQUESTED", "LIFECYCLE_REQUEST", id.toString());
            return out;
        });
    }

    /** Platform inbox or R03 own-institute list. */
    public Map<String, Object> listLifecycleRequests(UserSecurityContext ctx, UUID instituteId, String statusFilter) {
        return ctx.executeInTransaction(db, conn -> {
            boolean platform = false;
            try {
                ctx.checkPlatformAdmin(conn);
                platform = true;
            } catch (UserProfileAccessDeniedException ignored) {
                platform = false;
            }
            UUID scope = instituteId;
            if (!platform) {
                if (scope == null) {
                    scope = ctx.getTenantId();
                }
                if (scope == null) {
                    throw new InstituteAdminException(400, "IADM01_VALIDATION", "instituteId is required");
                }
                requirePlatformOrOwnView(conn, ctx, scope);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT id, tenant_id, college_id, request_type, status, reason, requested_by, decided_by, "
                            + "decided_at, decision_note, created_at "
                            + "FROM plat.institute_lifecycle_requests WHERE deleted_at IS NULL ");
            if (!platform || scope != null) {
                sql.append("AND tenant_id = ? ");
            }
            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                sql.append("AND status = ? ");
            }
            sql.append("ORDER BY created_at DESC LIMIT 100");
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int idx = 1;
                if (!platform || scope != null) {
                    ps.setObject(idx++, scope);
                }
                if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                    ps.setString(idx, statusFilter.trim().toUpperCase());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapLifecycleRequestRow(rs));
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
            if (note == null || note.trim().isEmpty()) {
                throw new InstituteAdminException(400, "IADM01_REASON_REQUIRED",
                        "A decision reason is required");
            }
            boolean approve = "APPROVE".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision);
            boolean reject = "REJECT".equalsIgnoreCase(decision) || "REJECTED".equalsIgnoreCase(decision);
            if (!approve && !reject) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "decision must be APPROVE or REJECT");
            }
            UUID tenantId;
            UUID collegeId;
            String type;
            UUID requestedBy;
            try (PreparedStatement load = conn.prepareStatement(
                    "SELECT tenant_id, college_id, request_type, requested_by, status "
                            + "FROM plat.institute_lifecycle_requests WHERE id = ? AND deleted_at IS NULL")) {
                load.setObject(1, requestId);
                try (ResultSet rs = load.executeQuery()) {
                    if (!rs.next()) {
                        throw new InstituteAdminException(404, "IADM01_REQUEST_NOT_FOUND", "Request not found");
                    }
                    if (!"PENDING".equalsIgnoreCase(rs.getString("status"))) {
                        throw new InstituteAdminException(409, "IADM01_ALREADY_DECIDED",
                                "This request was already decided");
                    }
                    tenantId = (UUID) rs.getObject("tenant_id");
                    collegeId = (UUID) rs.getObject("college_id");
                    type = rs.getString("request_type");
                    requestedBy = (UUID) rs.getObject("requested_by");
                }
            }
            if (requestedBy != null && requestedBy.equals(ctx.getUserId())) {
                throw new InstituteAdminException(403, "IADM01_SELF_DECISION",
                        "You cannot decide your own lifecycle request");
            }
            String status;
            if (!approve) {
                status = "REJECTED";
            } else if ("FULL_EXPORT".equalsIgnoreCase(type)) {
                status = "APPROVED_AWAITING_EXECUTION";
            } else {
                status = "APPROVED";
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.institute_lifecycle_requests SET status = ?, decided_by = ?, decided_at = now(), "
                            + "decision_note = ?, updated_at = now(), updated_by = ? "
                            + "WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL")) {
                ps.setString(1, status);
                ps.setObject(2, ctx.getUserId());
                ps.setString(3, note.trim());
                ps.setObject(4, ctx.getUserId());
                ps.setObject(5, requestId);
                if (ps.executeUpdate() == 0) {
                    throw new InstituteAdminException(409, "IADM01_ALREADY_DECIDED",
                            "This request was already decided");
                }
            }
            if ("APPROVED".equals(status)) {
                applyApprovedLifecycle(conn, ctx, tenantId, collegeId, type, note.trim());
            }
            appendDurableAudit(conn, ctx, tenantId, "IADM01_LIFECYCLE_DECIDED", "LIFECYCLE_REQUEST", requestId.toString(),
                    status + ": " + note.trim());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("requestId", requestId.toString());
            out.put("status", status);
            out.put("tenantId", tenantId.toString());
            out.put("instituteId", tenantId.toString());
            out.put("collegeId", collegeId == null ? null : collegeId.toString());
            out.put("requestType", type);
            out.put("decisionNote", note.trim());
            if ("APPROVED_AWAITING_EXECUTION".equals(status)) {
                out.put("executionDeferred", Boolean.TRUE);
                out.put("message", "Export approved; execution is deferred and not available in IADM-01.");
            }
            auditMutation(ctx, "IADM01_LIFECYCLE_DECIDED", "LIFECYCLE_REQUEST", requestId.toString());
            return out;
        });
    }

    public List<Map<String, Object>> lifecycleHistory(UserSecurityContext ctx, UUID tenantId) {
        Map<String, Object> bundle = lifecycleHistoryBundle(ctx, tenantId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) bundle.get("items");
        return items == null ? new ArrayList<>() : items;
    }

    /** US-012 / US-025: lifecycle events + durable audit rows for one institute. */
    public Map<String, Object> lifecycleHistoryBundle(UserSecurityContext ctx, UUID tenantId) {
        return ctx.executeInTransaction(db, conn -> {
            try {
                ctx.checkPermission(conn, "adm01.view");
            } catch (UserProfileAccessDeniedException ex) {
                ctx.checkPlatformAdmin(conn);
            }
            UUID tid = tenantId != null ? tenantId : ctx.getTenantId();
            if (tid == null) {
                throw new InstituteAdminException(400, "IADM01_VALIDATION", "instituteId is required");
            }
            requirePlatformOrOwnView(conn, ctx, tid);
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, event_type, actor_id, reason, payload, created_at "
                            + "FROM plat.institute_lifecycle_events WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 200")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getObject("id").toString());
                        row.put("source", "lifecycle_event");
                        row.put("eventType", rs.getString("event_type"));
                        Object actor = rs.getObject("actor_id");
                        row.put("actorId", actor != null ? actor.toString() : null);
                        row.put("reason", rs.getString("reason"));
                        row.put("payload", rs.getString("payload"));
                        Timestamp ts = rs.getTimestamp("created_at");
                        row.put("createdAt", ts != null ? ts.toInstant().toString() : null);
                        items.add(row);
                    }
                }
            }
            List<Map<String, Object>> auditRows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, action, principal_id, resource_type, resource_id, status, detail, created_at "
                            + "FROM audit.events WHERE tenant_id = ? AND action LIKE 'IADM01_%' "
                            + "ORDER BY created_at DESC LIMIT 100")) {
                ps.setObject(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getObject("id").toString());
                        row.put("source", "audit_event");
                        row.put("eventType", rs.getString("action"));
                        Object actor = rs.getObject("principal_id");
                        row.put("actorId", actor != null ? actor.toString() : null);
                        row.put("reason", rs.getString("detail"));
                        row.put("resourceType", rs.getString("resource_type"));
                        Object rid = rs.getObject("resource_id");
                        row.put("resourceId", rid == null ? null : rid.toString());
                        row.put("status", rs.getString("status"));
                        Timestamp ts = rs.getTimestamp("created_at");
                        row.put("createdAt", ts != null ? ts.toInstant().toString() : null);
                        auditRows.add(row);
                        items.add(row);
                    }
                }
            } catch (SQLException ignored) {
                // audit.events may be RLS-restricted; lifecycle_events remain authoritative for timeline
            }
            items.sort((a, b) -> {
                String ca = String.valueOf(a.get("createdAt"));
                String cb = String.valueOf(b.get("createdAt"));
                return cb.compareTo(ca);
            });
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("instituteId", tid.toString());
            out.put("items", items);
            out.put("lifecycleEventCount", items.stream().filter(i -> "lifecycle_event".equals(i.get("source"))).count());
            out.put("auditEventCount", auditRows.size());
            out.put("count", items.size());
            return out;
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

    private static final class CollegeRow {
        final UUID tenantId;
        final String code;
        final String name;
        final String status;
        final String provisioningStatus;
        final String profileJson;

        CollegeRow(UUID tenantId, String code, String name, String status, String provisioningStatus, String profileJson) {
            this.tenantId = tenantId;
            this.code = code;
            this.name = name;
            this.status = status;
            this.provisioningStatus = provisioningStatus;
            this.profileJson = profileJson;
        }
    }

    private void requireCollegeManage(Connection conn, UserSecurityContext ctx, UUID instituteId) throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return;
        } catch (UserProfileAccessDeniedException ignored) {
            // fall through
        }
        boolean allowed = false;
        try {
            ctx.checkPermission(conn, "adm06.manage");
            allowed = true;
        } catch (UserProfileAccessDeniedException ignored) {
            // try adm01.manage
        }
        if (!allowed) {
            try {
                ctx.checkPermission(conn, "adm01.manage");
                allowed = true;
            } catch (UserProfileAccessDeniedException ex) {
                throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                        "You are not authorized for this action.");
            }
        }
        if (!ctx.isPlatformScope() && ctx.getTenantId() != null && instituteId != null
                && !instituteId.equals(ctx.getTenantId())) {
            throw new InstituteAdminException(403, "IADM01_OWN_TENANT_ONLY",
                    "Institution Admin may only manage own institute");
        }
    }

    private static void assertInstituteReadyForColleges(Connection conn, UUID instituteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, instituteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                }
                String st = rs.getString("status");
                if (!"ACTIVE".equalsIgnoreCase(st)) {
                    throw new InstituteAdminException(409, "IADM01_INSTITUTE_NOT_READY",
                            "Register colleges only after the first Institution Admin is appointed.");
                }
            }
        }
    }

    private static CollegeRow loadCollegeRow(Connection conn, UUID collegeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tenant_id, code, name, status, provisioning_status, profile::text "
                        + "FROM plat.iadm01_get_college(?)")) {
            ps.setObject(1, collegeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "College not found");
                }
                return new CollegeRow(
                        (UUID) rs.getObject("tenant_id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getString("provisioning_status"),
                        rs.getString("profile"));
            }
        }
    }

    private static boolean isHandoverCompleted(Connection conn, UUID tenantId, UUID collegeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.college_cadm_handovers "
                        + "WHERE tenant_id = ? AND college_id = ? AND status = 'COMPLETED' AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, collegeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Map<String, Object> mapCollegeListRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("collegeId", rs.getObject("id").toString());
        row.put("code", rs.getString("code"));
        row.put("name", rs.getString("name"));
        row.put("status", rs.getString("status"));
        row.put("provisioningStatus", rs.getString("provisioning_status"));
        putProfileFields(row, rs.getString("profile"));
        String ho = rs.getString("handover_status");
        row.put("handoverStatus", ho);
        Timestamp created = rs.getTimestamp("created_at");
        row.put("createdAt", created == null ? null : created.toInstant().toString());
        return row;
    }

    private static Map<String, Object> mapCollegeDetail(CollegeRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", row.code);
        out.put("name", row.name);
        out.put("status", row.status);
        out.put("provisioningStatus", row.provisioningStatus);
        putProfileFields(out, row.profileJson);
        return out;
    }

    private static void putProfileFields(Map<String, Object> target, String profileJson) {
        if (profileJson == null || profileJson.trim().isEmpty()) {
            return;
        }
        String campus = jsonField(profileJson, "campusCode");
        String location = jsonField(profileJson, "location");
        if (campus != null && !campus.isEmpty()) {
            target.put("campusCode", campus);
        }
        if (location != null && !location.isEmpty()) {
            target.put("location", location);
        }
    }

    private static String jsonField(String json, String key) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
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

    private void requireLifecycleRequestCreate(Connection conn, UserSecurityContext ctx, UUID instituteId)
            throws SQLException {
        try {
            ctx.checkPlatformAdmin(conn);
            return;
        } catch (UserProfileAccessDeniedException ignored) {
        }
        try {
            ctx.checkPermission(conn, "adm01.write");
        } catch (UserProfileAccessDeniedException ex) {
            try {
                ctx.checkPermission(conn, "adm01.manage");
            } catch (UserProfileAccessDeniedException ex2) {
                throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                        "You are not authorized for this action.");
            }
        }
        if (ctx.getTenantId() != null && !instituteId.equals(ctx.getTenantId())) {
            throw new InstituteAdminException(403, "IADM01_OWN_TENANT_ONLY",
                    "Institution Admin may only request lifecycle changes for own institute");
        }
    }

    private static String loadTenantStatus(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InstituteAdminException(404, "IADM01_NOT_FOUND", "Institute not found");
                }
                return rs.getString(1);
            }
        }
    }

    private static Map<String, Object> mapLifecycleRequestRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("requestId", rs.getObject("id").toString());
        row.put("tenantId", rs.getObject("tenant_id").toString());
        row.put("instituteId", rs.getObject("tenant_id").toString());
        Object college = rs.getObject("college_id");
        row.put("collegeId", college == null ? null : college.toString());
        row.put("requestType", rs.getString("request_type"));
        row.put("status", rs.getString("status"));
        row.put("reason", rs.getString("reason"));
        Object requestedBy = rs.getObject("requested_by");
        row.put("requestedBy", requestedBy == null ? null : requestedBy.toString());
        Object decidedBy = rs.getObject("decided_by");
        row.put("decidedBy", decidedBy == null ? null : decidedBy.toString());
        Timestamp decided = rs.getTimestamp("decided_at");
        row.put("decidedAt", decided == null ? null : decided.toInstant().toString());
        try {
            row.put("decisionNote", rs.getString("decision_note"));
        } catch (SQLException ignored) {
            /* optional column in some queries */
        }
        Timestamp created = rs.getTimestamp("created_at");
        row.put("createdAt", created == null ? null : created.toInstant().toString());
        return row;
    }

    private void applyApprovedLifecycle(Connection conn, UserSecurityContext ctx, UUID tenantId, UUID collegeId,
                                        String type, String note) throws SQLException {
        if (collegeId != null) {
            String target = "SUSPEND".equalsIgnoreCase(type) ? "SUSPENDED" : "ARCHIVED";
            try (PreparedStatement up = conn.prepareStatement(
                    "SELECT plat.iadm01_set_college_status(?, ?)")) {
                up.setObject(1, collegeId);
                up.setString(2, target);
                up.executeQuery();
            }
            appendEvent(conn, tenantId, "STATUS_CHANGE", ctx.getUserId(), note,
                    "{\"collegeId\":\"" + collegeId + "\",\"status\":\"" + target + "\",\"requestType\":\"" + type + "\"}");
            appendDurableAudit(conn, ctx, tenantId, "IADM01_COLLEGE_LIFECYCLE_" + type, "COLLEGE",
                    collegeId.toString(), note);
            return;
        }
        if ("SUSPEND".equalsIgnoreCase(type)) {
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE core.tenants SET status = 'SUSPENDED', updated_at = now(), updated_by = ? WHERE id = ?")) {
                up.setObject(1, ctx.getUserId());
                up.setObject(2, tenantId);
                up.executeUpdate();
            }
            appendEvent(conn, tenantId, "SUSPEND", ctx.getUserId(), note, "{\"sessionsBlocked\":true}");
            appendDurableAudit(conn, ctx, tenantId, "IADM01_INSTITUTE_SUSPENDED", "TENANT", tenantId.toString(), note);
        } else if ("ARCHIVE".equalsIgnoreCase(type) || "DECOMMISSION".equalsIgnoreCase(type)) {
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE core.tenants SET status = 'ARCHIVED', updated_at = now(), updated_by = ? WHERE id = ?")) {
                up.setObject(1, ctx.getUserId());
                up.setObject(2, tenantId);
                up.executeUpdate();
            }
            appendEvent(conn, tenantId, "ARCHIVE", ctx.getUserId(), note, "{}");
            appendDurableAudit(conn, ctx, tenantId, "IADM01_INSTITUTE_ARCHIVED", "TENANT", tenantId.toString(), note);
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

    private static void seedCollegeOnboardingStep(Connection conn, UUID tenantId, UUID collegeId, String stepKey,
                                                  String status, String correlationId, UUID actor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO plat.iadm01_onboarding_steps "
                        + "(tenant_id, college_id, workflow_kind, step_key, status, correlation_id, created_by, updated_by) "
                        + "VALUES (?, ?, 'FIRST_R04', ?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, collegeId);
            ps.setString(3, stepKey);
            ps.setString(4, status);
            ps.setString(5, correlationId);
            ps.setObject(6, actor);
            ps.setObject(7, actor);
            try {
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!"23505".equals(e.getSQLState())) {
                    throw e;
                }
            }
        }
    }

    private static void ensureCollegeOnboardingSteps(Connection conn, UUID tenantId, UUID collegeId,
                                                     String correlationId, UUID actor) throws SQLException {
        for (String step : FIRST_R04_STEPS) {
            boolean exists;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM plat.iadm01_onboarding_steps WHERE tenant_id = ? AND workflow_kind = 'FIRST_R04' "
                            + "AND step_key = ? AND college_id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, tenantId);
                ps.setString(2, step);
                ps.setObject(3, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                seedCollegeOnboardingStep(conn, tenantId, collegeId, step, "PENDING", correlationId, actor);
            }
        }
    }

    /**
     * Resolve an existing Auth user id by email when the Auth Admin list filter misses them.
     * Uses SECURITY DEFINER helper {@code plat.iadm01_auth_user_id_by_email} (no secrets).
     */
    private SupabaseAuthAdminClient.AuthUserIdLookup emailLookup(UserSecurityContext ctx) {
        return email -> ctx.executeInTransaction(db, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.iadm01_auth_user_id_by_email(?)")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Object id = rs.getObject(1);
                    return id == null ? null : id.toString();
                }
            }
        });
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
        return firstIncompleteStep(steps, FIRST_R03_STEPS);
    }

    private static String firstIncompleteStep(List<Map<String, Object>> steps, List<String> orderedKeys) {
        if (steps == null) {
            return orderedKeys.isEmpty() ? null : orderedKeys.get(0);
        }
        for (String key : orderedKeys) {
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
                throw mapProfileSqlFailure(ex);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authUserId", authUserId.toString());
            markStepInConn(conn, instituteId, "CREATE_PROFILE", "SUCCEEDED", correlationId, null, payload, ctx.getUserId());
            return null;
        });
    }

    private static InstituteAdminException mapProfileSqlFailure(SQLException ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        String sqlState = ex.getSQLState() == null ? "" : ex.getSQLState();
        logger.warn("IADM-01 profile SQL failure sqlState={} msgSnippet={}",
                sqlState, msg.length() > 180 ? msg.substring(0, 180) : msg);
        if (msg.contains("IADM01_FORBIDDEN")) {
            return new InstituteAdminException(403, "IADM01_FORBIDDEN", "You are not authorized for this action.");
        }
        if (msg.contains("IADM01_AUTH_USER_REQUIRED")) {
            return new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                    "Target must be a linked Supabase Auth user");
        }
        if (msg.contains("IADM01_VALIDATION")) {
            return new InstituteAdminException(400, "IADM01_VALIDATION", "tenant and auth user are required");
        }
        if (msg.contains("IADM01_PROFILE_OTHER_TENANT")
                || "23505".equals(sqlState)
                || msg.toLowerCase().contains("duplicate")
                || msg.toLowerCase().contains("unique")) {
            return new InstituteAdminException(409, "IADM01_PROFILE_OTHER_TENANT",
                    "This login is already Institution Admin on another institute. Open that ACTIVE institute, or invite a different email here.");
        }
        return new InstituteAdminException(500, "IADM01_PROFILE_FAILED",
                "Login exists; institute profile is incomplete.");
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

    private void markCollegeStep(UserSecurityContext ctx, UUID instituteId, UUID collegeId, String stepKey,
                                 String status, String correlationId, String lastError, Map<String, Object> payload) {
        UserSecurityContext scoped = ctx.withTenant(instituteId);
        scoped.executeInTransaction(db, conn -> {
            requireCollegeManage(conn, scoped, instituteId);
            markCollegeStepInConn(conn, instituteId, collegeId, stepKey, status, correlationId,
                    lastError, payload, scoped.getUserId());
            return null;
        });
    }

    private List<Map<String, Object>> loadCollegeOnboardingSteps(Connection conn, UUID tenantId, UUID collegeId)
            throws SQLException {
        List<Map<String, Object>> steps = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT step_key, status, correlation_id, last_error, payload::text, updated_at "
                        + "FROM plat.iadm01_onboarding_steps WHERE tenant_id = ? AND workflow_kind = 'FIRST_R04' "
                        + "AND college_id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, collegeId);
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
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String key : FIRST_R04_STEPS) {
            for (Map<String, Object> s : steps) {
                if (key.equals(s.get("stepKey"))) {
                    ordered.add(s);
                }
            }
        }
        return ordered;
    }

    private UUID loadAuthUserFromCollegeStep(UserSecurityContext ctx, UUID instituteId, UUID collegeId) {
        return ctx.withTenant(instituteId).executeInTransaction(db, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT payload->>'authUserId' FROM plat.iadm01_onboarding_steps "
                            + "WHERE tenant_id = ? AND college_id = ? AND workflow_kind = 'FIRST_R04' "
                            + "AND step_key = 'INVITE_LOGIN' AND status = 'SUCCEEDED' "
                            + "AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, instituteId);
                ps.setObject(2, collegeId);
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

    private String loadEmailFromCollegeStep(UserSecurityContext ctx, UUID instituteId, UUID collegeId) {
        return ctx.withTenant(instituteId).executeInTransaction(db, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT payload->>'email' FROM plat.iadm01_onboarding_steps "
                            + "WHERE tenant_id = ? AND college_id = ? AND workflow_kind = 'FIRST_R04' "
                            + "AND step_key = 'INVITE_LOGIN' AND status = 'SUCCEEDED' "
                            + "AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, instituteId);
                ps.setObject(2, collegeId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    private void createCollegeAdminProfile(UserSecurityContext ctx, UUID instituteId, UUID collegeId, UUID authUserId,
                                           String email, String displayName, String correlationId) {
        UserSecurityContext scoped = ctx.withTenant(instituteId);
        scoped.executeInTransaction(db, conn -> {
            requireCollegeManage(conn, scoped, instituteId);
            markCollegeStepInConn(conn, instituteId, collegeId, "CREATE_PROFILE", "IN_PROGRESS",
                    correlationId, null, null, scoped.getUserId());
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.iadm01_create_institute_user_profile(?, ?, ?, ?)")) {
                ps.setObject(1, instituteId);
                ps.setObject(2, authUserId);
                ps.setObject(3, email);
                ps.setObject(4, displayName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new InstituteAdminException(500, "IADM01_PROFILE_FAILED",
                                "Login exists; college admin profile is incomplete.");
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
                        "Login exists; college admin profile is incomplete.");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("authUserId", authUserId.toString());
            markCollegeStepInConn(conn, instituteId, collegeId, "CREATE_PROFILE", "SUCCEEDED",
                    correlationId, null, payload, scoped.getUserId());
            return null;
        });
    }

    private void appointR04(UserSecurityContext ctx, UUID instituteId, UUID collegeId, UUID authUserId,
                            String correlationId) {
        UserSecurityContext scoped = ctx.withTenant(instituteId);
        scoped.executeInTransaction(db, conn -> {
            requireCollegeManage(conn, scoped, instituteId);
            markCollegeStepInConn(conn, instituteId, collegeId, "APPOINT_R04", "IN_PROGRESS",
                    correlationId, null, null, scoped.getUserId());
            assertAuthLinkedUser(conn, authUserId);
            // Spoof regression: p_assigned_by ignored; auth.uid() from session claims is authoritative
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT plat.assign_college_admin(?, ?, ?, ?)")) {
                ps.setObject(1, instituteId);
                ps.setObject(2, collegeId);
                ps.setObject(3, authUserId);
                ps.setObject(4, scoped.getUserId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        throw new InstituteAdminException(500, "IADM01_APPOINT_FAILED",
                                "Profile created; College Admin assignment is pending.");
                    }
                }
            } catch (InstituteAdminException ex) {
                throw ex;
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("Access Denied") || msg.contains("IADM01_FORBIDDEN")) {
                    throw new InstituteAdminException(403, "IADM01_FORBIDDEN",
                            "You are not authorized for this action.");
                }
                if (msg.contains("COLLEGE_ADMIN role not found")) {
                    throw new InstituteAdminException(500, "IADM01_ROLE_MISSING",
                            "COLLEGE_ADMIN role missing");
                }
                throw new InstituteAdminException(500, "IADM01_APPOINT_FAILED",
                        "Profile created; College Admin assignment is pending.");
            }
            markCollegeStepInConn(conn, instituteId, collegeId, "APPOINT_R04", "SUCCEEDED",
                    correlationId, null, null, scoped.getUserId());
            try (PreparedStatement up = conn.prepareStatement(
                    "SELECT plat.iadm01_set_college_status(?, 'PENDING_HANDOVER')")) {
                up.setObject(1, collegeId);
                up.executeQuery();
            }
            ensureHandoverPending(conn, instituteId, collegeId, authUserId, scoped.getUserId());
            appendDurableAudit(conn, scoped, instituteId, "IADM01_R04_APPOINTED", "COLLEGE", collegeId.toString(),
                    "College Admin appointed");
            return null;
        });
    }

    private static void ensureHandoverPending(Connection conn, UUID tenantId, UUID collegeId, UUID adminId, UUID by)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM plat.college_cadm_handovers "
                        + "WHERE tenant_id = ? AND college_id = ? AND deleted_at IS NULL LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, collegeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    insertHandover(conn, tenantId, collegeId, adminId, by, "PENDING");
                }
            }
        }
    }

    private void appendCollegeLifecycleAndAudit(UserSecurityContext ctx, UUID instituteId, UUID collegeId,
                                                String eventType, String auditAction, String reason) {
        ctx.withTenant(instituteId).executeInTransaction(db, conn -> {
            appendEvent(conn, instituteId, eventType, ctx.getUserId(), reason,
                    "{\"collegeId\":\"" + collegeId + "\"}");
            appendDurableAudit(conn, ctx, instituteId, auditAction, "COLLEGE", collegeId.toString(), reason);
            return null;
        });
    }

    private static void markCollegeStepInConn(Connection conn, UUID instituteId, UUID collegeId, String stepKey,
                                              String status, String correlationId, String lastError,
                                              Map<String, Object> payload, UUID actor) throws SQLException {
        String payloadJson = payload == null ? "{}" : toSimpleJson(payload);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE plat.iadm01_onboarding_steps SET status = ?, correlation_id = ?, last_error = ?, "
                        + "payload = CASE WHEN ?::text = '{}' THEN payload ELSE ?::jsonb END, "
                        + "updated_at = now(), updated_by = ? "
                        + "WHERE tenant_id = ? AND college_id = ? AND workflow_kind = 'FIRST_R04' AND step_key = ? "
                        + "AND deleted_at IS NULL")) {
            ps.setString(1, status);
            ps.setString(2, correlationId);
            ps.setString(3, lastError);
            ps.setString(4, payloadJson);
            ps.setString(5, payloadJson);
            ps.setObject(6, actor);
            ps.setObject(7, instituteId);
            ps.setObject(8, collegeId);
            ps.setString(9, stepKey);
            ps.executeUpdate();
        }
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
