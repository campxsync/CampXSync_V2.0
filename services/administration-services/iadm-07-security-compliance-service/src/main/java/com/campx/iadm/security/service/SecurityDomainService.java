package com.campx.iadm.security.service;

import com.campx.iadm.security.config.DatabaseConnectionManager;
import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.exception.MalformedPayloadException;
import com.campx.iadm.security.exception.ResourceConflictException;
import com.campx.iadm.security.exception.ResourceNotFoundException;
import com.campx.iadm.security.exception.UserProfileAccessDeniedException;
import com.campx.iadm.security.security.UserSecurityContext;
import com.campx.iadm.security.simulator.SignalIntakeSimulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IADM-07 domain orchestration — posture, registers, intake, audit evidence.
 * Plain language: security governance registers only; never enforces policy.
 */
public class SecurityDomainService {

    private final DatabaseConnectionManager db = DatabaseConnectionManager.getInstance();

    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "UP");
        out.put("service", "IADM-07 Security & Compliance Governance");
        out.put("port", Integer.valueOf(8105));
        out.put("prefix", "/api/v1/iadm/07");
        out.put("queryTime", Instant.now().toString());
        return out;
    }

    public Map<String, Object> postureOverview(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                if (hasSummaryOnly(conn)) {
                    throw new UserProfileAccessDeniedException(
                            "College administrators use summary posture only");
                }
                requireAny(conn, "iadm07.posture.view", "iadm07.register.read");
                UUID scope = resolveScope(conn, ctx, scopeTenantId, true);
                Map<String, Object> out = PostureIndicatorEngine.computeAll(conn, scope, false);
                out.put("scopeTenantId", scope == null ? null : scope.toString());
                out.put("view", "OVERVIEW");
                return out;
            }
        });
    }

    public Map<String, Object> summaryPosture(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.summary.view");
                UUID scope = ctx.getTenantId();
                if (scope == null) {
                    throw new InstituteAdminException(403, "IADM07_SCOPE_REQUIRED",
                            "Institute context required for summary posture");
                }
                Map<String, Object> indicators = PostureIndicatorEngine.computeAll(conn, scope, true);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("scopeTenantId", scope.toString());
                out.put("view", "SUMMARY");
                out.put("indicators", indicators.get("indicators"));
                out.put("publishedStandardsCount", Integer.valueOf(countPublishedStandards(conn)));
                return out;
            }
        });
    }

    public Map<String, Object> listStandards(final UserSecurityContext ctx, final String statusFilter) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                if (hasSummaryOnly(conn) && !hasPermission(conn, "iadm07.register.read")) {
                    requireAny(conn, "iadm07.summary.view");
                } else {
                    requireAny(conn, "iadm07.register.read", "iadm07.summary.view");
                }
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, code, title, version_no, status, author_user_id, approver_user_id, "
                        + "effective_at, review_due_at, updated_at "
                        + "FROM sec.security_standards WHERE (? IS NULL OR status = ?) "
                        + (hasSummaryOnly(conn) && !isSuperAdmin(conn) && !isSystemAdmin(conn)
                        ? " AND status = 'PUBLISHED' " : "")
                        + "ORDER BY code, version_no DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (statusFilter == null || statusFilter.trim().isEmpty()) {
                        ps.setNull(1, Types.VARCHAR);
                        ps.setNull(2, Types.VARCHAR);
                    } else {
                        ps.setString(1, statusFilter.trim().toUpperCase());
                        ps.setString(2, statusFilter.trim().toUpperCase());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapStandard(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                return out;
            }
        });
    }

    public Map<String, Object> proposeStandard(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.standard.propose");
                String code = str(body, "code");
                String title = str(body, "title");
                if (code == null || title == null) {
                    throw new MalformedPayloadException("code and title are required");
                }
                int nextVersion = 1;
                UUID supersedesId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, version_no, status FROM sec.security_standards "
                                + "WHERE code = ? ORDER BY version_no DESC LIMIT 1")) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            nextVersion = rs.getInt("version_no") + 1;
                            if ("PUBLISHED".equals(rs.getString("status"))) {
                                supersedesId = (UUID) rs.getObject("id");
                            }
                        }
                    }
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.security_standards "
                                + "(id, code, title, version_no, status, author_user_id, body_ref, supersedes_id) "
                                + "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setString(2, code);
                    ps.setString(3, title);
                    ps.setInt(4, nextVersion);
                    ps.setObject(5, ctx.getUserId());
                    ps.setString(6, str(body, "bodyRef"));
                    ps.setObject(7, supersedesId);
                    ps.executeUpdate();
                }
                // Change to a published standard: new Draft version; prior published becomes Superseded.
                if (supersedesId != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE sec.security_standards SET status = 'SUPERSEDED', updated_at = now() "
                                    + "WHERE id = ? AND status = 'PUBLISHED'")) {
                        ps.setObject(1, supersedesId);
                        ps.executeUpdate();
                    }
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM07_STANDARD_PROPOSE",
                        "security_standard", id.toString(),
                        "Proposed standard " + code + " v" + nextVersion);
                return loadStandard(conn, id);
            }
        });
    }

    public Map<String, Object> transitionStandard(final UserSecurityContext ctx, final UUID id,
                                                  final String toStatus, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                Map<String, Object> current = loadStandard(conn, id);
                String from = String.valueOf(current.get("status"));
                String to = toStatus == null ? null : toStatus.trim().toUpperCase();
                RegisterLifecycleEngine.assertTransition(RegisterLifecycleEngine.STANDARD, from, to);
                if ("APPROVED".equals(to) || "PUBLISHED".equals(to)) {
                    requirePlatformAdmin(conn);
                    UUID author = uuid(current.get("authorUserId"));
                    if (author != null && author.equals(ctx.getUserId())) {
                        throw new InstituteAdminException(403, "IADM07_SOD_VIOLATION",
                                "Approver must differ from author");
                    }
                } else {
                    requireAny(conn, "iadm07.standard.propose");
                }
                if ("APPROVED".equals(to)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE sec.security_standards SET status = ?, approver_user_id = ?, updated_at = now() "
                                    + "WHERE id = ?")) {
                        ps.setString(1, to);
                        ps.setObject(2, ctx.getUserId());
                        ps.setObject(3, id);
                        if (ps.executeUpdate() == 0) {
                            throw new ResourceNotFoundException("security_standard", id.toString());
                        }
                    }
                } else if ("PUBLISHED".equals(to)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE sec.security_standards SET status = ?, effective_at = ?, review_due_at = ?, "
                                    + "updated_at = now() WHERE id = ?")) {
                        ps.setString(1, to);
                        ps.setTimestamp(2, parseTs(body, "effectiveAt"));
                        ps.setTimestamp(3, parseTs(body, "reviewDueAt"));
                        ps.setObject(4, id);
                        if (ps.executeUpdate() == 0) {
                            throw new ResourceNotFoundException("security_standard", id.toString());
                        }
                    }
                } else {
                    updateStatus(conn, "sec.security_standards", id, to);
                }
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM07_STANDARD_" + to,
                        "security_standard", id.toString(), "Standard " + from + " → " + to);
                return loadStandard(conn, id);
            }
        });
    }

    public Map<String, Object> listFindings(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read");
                // Platform operators (R01/R02/R28) see all institutes when no explicit tenant filter
                UUID scope = resolveScope(conn, ctx, scopeTenantId, true);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, code, title, severity, status, institute_id, assigned_institute_id, "
                        + "target_date, updated_at FROM sec.findings "
                        + (scope != null ? "WHERE institute_id = ? OR assigned_institute_id = ? " : "")
                        + "ORDER BY updated_at DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (scope != null) {
                        ps.setObject(1, scope);
                        ps.setObject(2, scope);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapFinding(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                return out;
            }
        });
    }

    public Map<String, Object> createFinding(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String code = str(body, "code");
                String title = str(body, "title");
                if (code == null || title == null) {
                    throw new MalformedPayloadException("code and title are required");
                }
                UUID instituteId = resolveWriteInstituteId(conn, ctx, body);
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.findings (id, institute_id, code, title, severity, status, created_by) "
                                + "VALUES (?, ?, ?, ?, ?, 'OPEN', ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, code);
                    ps.setString(4, title);
                    ps.setString(5, str(body, "severity", "MEDIUM"));
                    ps.setObject(6, ctx.getUserId());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                        throw new ResourceConflictException("finding", "code", code);
                    }
                    throw ex;
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_FINDING_CREATE",
                        "finding", id.toString(), "Created finding " + code);
                return loadFinding(conn, id);
            }
        });
    }

    public Map<String, Object> transitionFinding(final UserSecurityContext ctx, final UUID id, final String toStatus) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                Map<String, Object> current = loadFinding(conn, id);
                String from = String.valueOf(current.get("status"));
                String to = toStatus == null ? null : toStatus.trim().toUpperCase();
                boolean platformOperator = isSuperAdmin(conn) || isSystemAdmin(conn);
                if (!platformOperator) {
                    requireAny(conn, "iadm07.register.write");
                    assertInstitutionFindingScope(ctx.getTenantId(),
                            uuid(current.get("instituteId")), uuid(current.get("assignedInstituteId")));
                    assertInstitutionFindingTransition(from, to);
                } else if (!"RESOLVED".equals(to)) {
                    requireAny(conn, "iadm07.register.write");
                }
                RegisterLifecycleEngine.assertTransition(RegisterLifecycleEngine.FINDING, from, to);
                if ("RESOLVED".equals(to)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE sec.findings SET status = ?, verified_by = ?, updated_at = now() WHERE id = ?")) {
                        ps.setString(1, to);
                        ps.setObject(2, ctx.getUserId());
                        ps.setObject(3, id);
                        ps.executeUpdate();
                    }
                } else {
                    updateStatus(conn, "sec.findings", id, to);
                }
                UUID scope = uuid(current.get("instituteId"));
                DurableAuditWriter.append(conn, ctx, scope, "IADM07_FINDING_" + to,
                        "finding", id.toString(), "Finding " + from + " → " + to);
                return loadFinding(conn, id);
            }
        });
    }

    public Map<String, Object> listRisks(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read");
                return listScoped(conn, "sec.risks",
                        "SELECT id, code, title, status, severity, institute_id, updated_at FROM sec.risks ",
                        scopeTenantId, ctx);
            }
        });
    }

    public Map<String, Object> createRisk(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String code = str(body, "code");
                String title = str(body, "title");
                if (code == null || title == null) {
                    throw new MalformedPayloadException("code and title are required");
                }
                UUID instituteId = resolveWriteInstituteId(conn, ctx, body);
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.risks (id, institute_id, code, title, status, severity, created_by) "
                                + "VALUES (?, ?, ?, ?, 'IDENTIFIED', ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, code);
                    ps.setString(4, title);
                    ps.setString(5, str(body, "severity", "MEDIUM"));
                    ps.setObject(6, ctx.getUserId());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                        throw new ResourceConflictException("risk", "code", code);
                    }
                    throw ex;
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_RISK_CREATE",
                        "risk", id.toString(), "Created risk " + code);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("code", code);
                out.put("status", "IDENTIFIED");
                return out;
            }
        });
    }

    public Map<String, Object> transitionRisk(final UserSecurityContext ctx, final UUID id, final String toStatus) {
        return transitionRegister(ctx, id, toStatus, RegisterLifecycleEngine.RISK, "risk", "sec.risks", "IADM07_RISK_");
    }

    public Map<String, Object> listAlerts(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read");
                return listScoped(conn, "sec.alerts",
                        "SELECT id, code, title, severity, status, institute_id, updated_at FROM sec.alerts ",
                        scopeTenantId, ctx);
            }
        });
    }

    public Map<String, Object> createAlert(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String code = str(body, "code");
                String title = str(body, "title");
                if (code == null || title == null) {
                    throw new MalformedPayloadException("code and title are required");
                }
                UUID instituteId = resolveWriteInstituteId(conn, ctx, body);
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.alerts (id, institute_id, code, title, severity, status) "
                                + "VALUES (?, ?, ?, ?, ?, 'OPEN')")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, code);
                    ps.setString(4, title);
                    ps.setString(5, str(body, "severity", "MEDIUM"));
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                        throw new ResourceConflictException("alert", "code", code);
                    }
                    throw ex;
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_ALERT_CREATE",
                        "alert", id.toString(), "Created alert " + code);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("code", code);
                out.put("status", "OPEN");
                return out;
            }
        });
    }

    public Map<String, Object> transitionAlert(final UserSecurityContext ctx, final UUID id, final String toStatus) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String from = loadStatus(conn, "sec.alerts", id);
                String to = toStatus == null ? null : toStatus.trim().toUpperCase();
                if (!isSuperAdmin(conn) && !isSystemAdmin(conn)) {
                    assertInstitutionAlertTransition(from, to);
                }
                RegisterLifecycleEngine.assertTransition(RegisterLifecycleEngine.ALERT, from, to);
                updateStatus(conn, "sec.alerts", id, to);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM07_ALERT_" + to,
                        "alert", id.toString(), "alert " + from + " → " + to);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("status", to);
                return out;
            }
        });
    }

    public Map<String, Object> listSignals(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read", "iadm07.signal.intake");
                return listScoped(conn, "sec.signals",
                        "SELECT id, correlation_id, source, severity, status, institute_id, updated_at "
                                + "FROM sec.signals ",
                        scopeTenantId, ctx);
            }
        });
    }

    public Map<String, Object> transitionSignal(final UserSecurityContext ctx, final UUID id, final String toStatus) {
        return transitionRegister(ctx, id, toStatus, RegisterLifecycleEngine.SIGNAL, "signal", "sec.signals",
                "IADM07_SIGNAL_");
    }

    public Map<String, Object> listExceptions(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read", "iadm07.exception.request");
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                UUID scope = resolveScope(conn, ctx, scopeTenantId, false);
                String sql = "SELECT id, code, rationale, status, valid_from, valid_to, institute_id, "
                        + "requester_user_id, updated_at FROM sec.exceptions "
                        + (scope != null ? "WHERE institute_id = ? " : "")
                        + "ORDER BY updated_at DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (scope != null) {
                        ps.setObject(1, scope);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            items.add(mapException(rs));
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                return out;
            }
        });
    }

    public Map<String, Object> requestException(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.exception.request");
                String code = str(body, "code");
                String rationale = str(body, "rationale");
                if (code == null || rationale == null) {
                    throw new MalformedPayloadException("code and rationale are required");
                }
                Timestamp[] window = parseRequiredValidityWindow(body);
                UUID instituteId = resolveWriteInstituteId(conn, ctx, body);
                if (instituteId == null) {
                    throw new InstituteAdminException(403, "IADM07_SCOPE_REQUIRED", "Institute context required");
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.exceptions (id, institute_id, code, rationale, status, valid_from, valid_to, "
                                + "requester_user_id) VALUES (?, ?, ?, ?, 'REQUESTED', ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, code);
                    ps.setString(4, rationale);
                    ps.setTimestamp(5, window[0]);
                    ps.setTimestamp(6, window[1]);
                    ps.setObject(7, ctx.getUserId());
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_EXCEPTION_REQUEST",
                        "exception", id.toString(), "Requested exception " + code);
                return loadException(conn, id);
            }
        });
    }

    public Map<String, Object> decideException(final UserSecurityContext ctx, final UUID id,
                                               final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requirePlatformAdmin(conn);
                String decision = str(body, "decision");
                if (decision == null) {
                    throw new MalformedPayloadException("decision is required");
                }
                Map<String, Object> current = loadException(conn, id);
                assertExceptionDecisionAllowed(uuid(current.get("requesterUserId")), ctx.getUserId());
                String from = String.valueOf(current.get("status"));
                String to = decision.trim().toUpperCase();
                if (!"APPROVED".equals(to) && !"REJECTED".equals(to)) {
                    throw new MalformedPayloadException("decision must be APPROVED or REJECTED");
                }
                RegisterLifecycleEngine.assertTransition(RegisterLifecycleEngine.EXCEPTION, from, to);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE sec.exceptions SET status = ?, decider_user_id = ?, decision_rationale = ?, "
                                + "updated_at = now() WHERE id = ?")) {
                    ps.setString(1, to);
                    ps.setObject(2, ctx.getUserId());
                    ps.setString(3, str(body, "rationale"));
                    ps.setObject(4, id);
                    ps.executeUpdate();
                }
                UUID scope = uuid(current.get("instituteId"));
                DurableAuditWriter.append(conn, ctx, scope, "IADM07_EXCEPTION_" + to,
                        "exception", id.toString(), "Exception decided " + to);
                return loadException(conn, id);
            }
        });
    }

    public Map<String, Object> listControlAssessments(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read");
                UUID scope = resolveScope(conn, ctx, scopeTenantId, false);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT ca.id, ca.control_id, c.code AS control_code, ca.status, ca.institute_id, "
                        + "ca.updated_at FROM sec.control_assessments ca "
                        + "JOIN sec.controls c ON c.id = ca.control_id "
                        + (scope != null ? "WHERE ca.institute_id = ? " : "")
                        + "ORDER BY ca.updated_at DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (scope != null) {
                        ps.setObject(1, scope);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getObject("id").toString());
                            row.put("controlId", rs.getObject("control_id").toString());
                            row.put("controlCode", rs.getString("control_code"));
                            row.put("status", rs.getString("status"));
                            row.put("instituteId", rs.getObject("institute_id") == null ? null
                                    : rs.getObject("institute_id").toString());
                            row.put("updatedAt", ts(rs.getTimestamp("updated_at")));
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                return out;
            }
        });
    }

    public Map<String, Object> transitionControlAssessment(final UserSecurityContext ctx, final UUID id,
                                                           final String toStatus) {
        return transitionRegister(ctx, id, toStatus, RegisterLifecycleEngine.CONTROL_ASSESSMENT,
                "control_assessment", "sec.control_assessments", "IADM07_CONTROL_");
    }

    public Map<String, Object> attachEvidenceRef(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String linkedType = str(body, "linkedType");
                String linkedId = str(body, "linkedId");
                String referenceAddress = str(body, "referenceAddress");
                String source = str(body, "source");
                if (linkedType == null || linkedId == null || referenceAddress == null || source == null) {
                    throw new MalformedPayloadException(
                            "linkedType, linkedId, referenceAddress and source are required");
                }
                UUID instituteId = resolveWriteInstituteId(conn, ctx, body);
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.evidence_refs (id, institute_id, linked_type, linked_id, "
                                + "reference_address, source, created_by) VALUES (?, ?, ?, ?::uuid, ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, linkedType.toUpperCase());
                    ps.setString(4, linkedId);
                    ps.setString(5, referenceAddress);
                    ps.setString(6, source);
                    ps.setObject(7, ctx.getUserId());
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_EVIDENCE_ATTACH",
                        "evidence_ref", id.toString(), "Evidence reference attached");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("linkedType", linkedType);
                out.put("linkedId", linkedId);
                out.put("referenceAddress", referenceAddress);
                return out;
            }
        });
    }

    public Map<String, Object> listEnforcement(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                denySummaryDetail(conn);
                requireAny(conn, "iadm07.register.read", "iadm07.report.view");
                UUID scope = resolveScope(conn, ctx, scopeTenantId, true);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                String sql = "SELECT id, control_id, institute_id, service_name, reported_status, report_source, "
                        + "reported_at, correlation_id FROM sec.enforcement_status "
                        + (scope != null ? "WHERE institute_id = ? " : "")
                        + "ORDER BY reported_at DESC LIMIT 200";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (scope != null) {
                        ps.setObject(1, scope);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getObject("id").toString());
                            row.put("serviceName", rs.getString("service_name"));
                            row.put("reportedStatus", rs.getString("reported_status"));
                            row.put("reportSource", rs.getString("report_source"));
                            row.put("reportedAt", ts(rs.getTimestamp("reported_at")));
                            row.put("correlationId", rs.getString("correlation_id"));
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                return out;
            }
        });
    }

    public Map<String, Object> signalIntake(final UserSecurityContext ctx, final Map<String, Object> body) {
        return intakeSignal(ctx, body, false);
    }

    public Map<String, Object> enforcementIntake(final UserSecurityContext ctx, final Map<String, Object> body) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.signal.intake", "iadm07.register.write");
                String correlationId = str(body, "correlationId");
                String serviceName = str(body, "serviceName");
                String reportedStatus = str(body, "reportedStatus");
                if (serviceName == null || reportedStatus == null) {
                    throw new MalformedPayloadException("serviceName and reportedStatus are required");
                }
                if (correlationId != null && !correlationId.trim().isEmpty()) {
                    Map<String, Object> dup = findEnforcementByCorrelation(conn, correlationId);
                    if (dup != null) {
                        Map<String, Object> out = new LinkedHashMap<String, Object>();
                        out.put("duplicate", Boolean.TRUE);
                        out.put("existing", dup);
                        return out;
                    }
                }
                UUID id = UUID.randomUUID();
                // Platform (R01/R02) or SERVICE (signal.intake without human register.write) may select;
                // Institution Admin (R03) and other humans are forced to trusted claims.
                boolean platform = isSuperAdmin(conn) || isSystemAdmin(conn);
                boolean serviceCaller = hasPermission(conn, "iadm07.signal.intake")
                        && !hasPermission(conn, "iadm07.register.write");
                Object rawInst = body.get("instituteId");
                UUID requested = (rawInst == null || String.valueOf(rawInst).trim().isEmpty())
                        ? null : uuid(rawInst);
                UUID instituteId = resolveWriteInstituteId(platform || serviceCaller, ctx.getTenantId(), requested);
                if (instituteId == null) {
                    throw new InstituteAdminException(403, "IADM07_SCOPE_REQUIRED",
                            "Institute context could not be verified");
                }
                if (!instituteExists(conn, instituteId)) {
                    throw new ResourceNotFoundException("institute", instituteId.toString());
                }
                UUID controlId = uuid(body.get("controlId"));
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.enforcement_status (id, institute_id, control_id, service_name, "
                                + "reported_status, report_source, correlation_id, demo_label) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setObject(3, controlId);
                    ps.setString(4, serviceName);
                    ps.setString(5, reportedStatus);
                    ps.setString(6, str(body, "reportSource", "SERVICE"));
                    ps.setString(7, correlationId);
                    ps.setString(8, str(body, "demoLabel", "DEMO ONLY"));
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_ENFORCEMENT_INTAKE",
                        "enforcement_status", id.toString(), "Enforcement report received");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("duplicate", Boolean.FALSE);
                return out;
            }
        });
    }

    public Map<String, Object> reportCounts(final UserSecurityContext ctx, final String scopeTenantId) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.report.view", "iadm07.summary.view", "iadm07.posture.view");
                // Platform operators (R01/R02/R28) may omit tenant for platform-wide counts;
                // R04 summary and R03 keep institute scoping via resolveScope.
                UUID scope = resolveScope(conn, ctx, scopeTenantId, true);
                Map<String, Object> byStatus = new LinkedHashMap<String, Object>();
                byStatus.put("findings", groupCounts(conn, "sec.findings", "status", scope, true));
                byStatus.put("risks", groupCounts(conn, "sec.risks", "status", scope, false));
                byStatus.put("alerts", groupCounts(conn, "sec.alerts", "status", scope, false));
                byStatus.put("signals", groupCounts(conn, "sec.signals", "status", scope, false));
                byStatus.put("exceptions", groupCounts(conn, "sec.exceptions", "status", scope, false));

                Map<String, Integer> findingsBySeverity = groupCountsWhere(conn,
                        "SELECT severity, COUNT(*)::int FROM sec.findings WHERE status <> 'RESOLVED'",
                        "GROUP BY severity ORDER BY severity", scope, true);

                Map<String, Integer> monthlyTrends = new LinkedHashMap<String, Integer>();
                monthlyTrends.put("findingsCreatedThisMonth", count(conn,
                        "SELECT COUNT(*) FROM sec.findings WHERE created_at >= date_trunc('month', now())",
                        scope, true));
                monthlyTrends.put("findingsResolvedThisMonth", count(conn,
                        "SELECT COUNT(*) FROM sec.findings WHERE status = 'RESOLVED' "
                                + "AND updated_at >= date_trunc('month', now())",
                        scope, true));
                monthlyTrends.put("alertsCreatedThisMonth", count(conn,
                        "SELECT COUNT(*) FROM sec.alerts WHERE created_at >= date_trunc('month', now())",
                        scope, false));
                monthlyTrends.put("alertsResolvedThisMonth", count(conn,
                        "SELECT COUNT(*) FROM sec.alerts WHERE status = 'RESOLVED' "
                                + "AND updated_at >= date_trunc('month', now())",
                        scope, false));

                Map<String, Instant> latestByRegister = new LinkedHashMap<String, Instant>();
                latestByRegister.put("findings", maxUpdatedAt(conn, "sec.findings", scope, true));
                latestByRegister.put("risks", maxUpdatedAt(conn, "sec.risks", scope, false));
                latestByRegister.put("alerts", maxUpdatedAt(conn, "sec.alerts", scope, false));
                latestByRegister.put("signals", maxUpdatedAt(conn, "sec.signals", scope, false));
                latestByRegister.put("exceptions", maxUpdatedAt(conn, "sec.exceptions", scope, false));

                return assembleReportCounts(
                        count(conn, "SELECT COUNT(*) FROM sec.findings WHERE status <> 'RESOLVED'", scope, true),
                        count(conn, "SELECT COUNT(*) FROM sec.risks WHERE status <> 'CLOSED'", scope, false),
                        count(conn, "SELECT COUNT(*) FROM sec.alerts WHERE status <> 'RESOLVED'", scope, false),
                        count(conn, "SELECT COUNT(*) FROM sec.signals WHERE status <> 'CLOSED'", scope, false),
                        count(conn, "SELECT COUNT(*) FROM sec.exceptions WHERE status = 'REQUESTED'", scope, false),
                        byStatus, findingsBySeverity, monthlyTrends, latestByRegister);
            }
        });
    }

    public static Map<String, Object> assembleReportCounts(
            int findingsOpen, int risksOpen, int alertsOpen, int signalsOpen, int exceptionsPending,
            Map<String, Object> byStatus, Map<String, Integer> findingsBySeverity,
            Map<String, Integer> monthlyTrends, Map<String, Instant> latestByRegister) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("findingsOpen", Integer.valueOf(findingsOpen));
        out.put("risksOpen", Integer.valueOf(risksOpen));
        out.put("alertsOpen", Integer.valueOf(alertsOpen));
        out.put("signalsOpen", Integer.valueOf(signalsOpen));
        out.put("exceptionsPending", Integer.valueOf(exceptionsPending));
        out.put("byStatus", byStatus);
        out.put("findingsBySeverity", findingsBySeverity);
        out.put("monthlyTrends", monthlyTrends);

        List<String> staleRegisters = new ArrayList<String>();
        List<String> unavailableRegisters = new ArrayList<String>();
        if (latestByRegister != null) {
            for (Map.Entry<String, Instant> e : latestByRegister.entrySet()) {
                if (e.getValue() == null) {
                    unavailableRegisters.add(e.getKey());
                } else if (PostureIndicatorEngine.isStale(e.getValue())) {
                    staleRegisters.add(e.getKey());
                }
            }
        }
        String freshnessStatus = !unavailableRegisters.isEmpty()
                ? "PARTIAL_OR_UNAVAILABLE"
                : (!staleRegisters.isEmpty() ? "STALE" : "CURRENT");
        Map<String, Object> freshness = new LinkedHashMap<String, Object>();
        freshness.put("status", freshnessStatus);
        freshness.put("staleThresholdMinutes", Integer.valueOf(PostureIndicatorEngine.getStaleThresholdMinutes()));
        freshness.put("staleRegisters", staleRegisters);
        freshness.put("unavailableRegisters", unavailableRegisters);
        out.put("freshness", freshness);
        return out;
    }

    public Map<String, Object> listDenialEvents(final UserSecurityContext ctx, final Integer limit) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                // Denial feed is platform-operator only (R01 / R02); R03/R04 must not see it.
                if (!isSuperAdmin(conn) && !isSystemAdmin(conn)) {
                    throw new UserProfileAccessDeniedException(
                            "Denial events are visible only to Super Admin and System Admin");
                }
                requireAny(conn, "iadm07.audit.view");
                int max = limit == null ? 50 : Math.min(Math.max(limit.intValue(), 1), 200);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (Statement elevate = conn.createStatement()) {
                    elevate.execute("RESET ROLE");
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, action, status, principal_id, tenant_id, event_time, correlation_id "
                                + "FROM audit.events "
                                + "WHERE status = 'DENIED' OR action LIKE '%\\_DENIED' ESCAPE '\\' "
                                + "ORDER BY event_time DESC LIMIT ?")) {
                    ps.setInt(1, max);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getObject("id").toString());
                            row.put("action", rs.getString("action"));
                            row.put("status", rs.getString("status"));
                            row.put("principalId", rs.getObject("principal_id") == null ? null
                                    : rs.getObject("principal_id").toString());
                            row.put("tenantId", rs.getObject("tenant_id") == null ? null
                                    : rs.getObject("tenant_id").toString());
                            row.put("eventTime", ts(rs.getTimestamp("event_time")));
                            row.put("correlationId", rs.getString("correlation_id"));
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                out.put("source", "audit.events");
                return out;
            }
        });
    }

    public Map<String, Object> listAuditEvidence(final UserSecurityContext ctx, final Integer limit) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.audit.view");
                int max = limit == null ? 100 : Math.min(limit.intValue(), 500);
                List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
                try (Statement elevate = conn.createStatement()) {
                    if (isSuperAdmin(conn) || isSystemAdmin(conn)) {
                        elevate.execute("RESET ROLE");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, action, principal_id, tenant_id, event_time, correlation_id "
                                + "FROM audit.events WHERE action LIKE 'IADM07_%' "
                                + "ORDER BY event_time DESC LIMIT ?")) {
                    ps.setInt(1, max);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<String, Object>();
                            row.put("id", rs.getObject("id").toString());
                            row.put("action", rs.getString("action"));
                            row.put("principalId", rs.getObject("principal_id") == null ? null
                                    : rs.getObject("principal_id").toString());
                            row.put("tenantId", rs.getObject("tenant_id") == null ? null
                                    : rs.getObject("tenant_id").toString());
                            row.put("eventTime", ts(rs.getTimestamp("event_time")));
                            row.put("correlationId", rs.getString("correlation_id"));
                            items.add(row);
                        }
                    }
                }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("items", items);
                return out;
            }
        });
    }

    public Map<String, Object> getOutageFlags(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.posture.view", "iadm07.register.read");
                return SecurityOutageFlags.snapshot();
            }
        });
    }

    public Map<String, Object> setOutageFlags(final UserSecurityContext ctx, final Boolean audit,
                                              final Boolean simulator, final Boolean notifications) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requirePlatformAdmin(conn);
                SecurityOutageFlags.set(audit, simulator, notifications);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM07_OUTAGE_FLAGS",
                        "settings", null, "Outage flags adjusted (DEMO ONLY)");
                return SecurityOutageFlags.snapshot();
            }
        });
    }

    public Map<String, Object> getStaleThreshold(final UserSecurityContext ctx) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.posture.view", "iadm07.register.read");
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("staleThresholdMinutes", Integer.valueOf(PostureIndicatorEngine.getStaleThresholdMinutes()));
                m.put("documentedDefault", Integer.valueOf(PostureIndicatorEngine.DOCUMENTED_DEMO_DEFAULT_MINUTES));
                m.put("label", "DEMO ONLY");
                return m;
            }
        });
    }

    public Map<String, Object> setStaleThreshold(final UserSecurityContext ctx, final int minutes) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                // Super Admin (R01) or System Admin (R02) may adjust DEMO ONLY stale threshold
                if (!isSuperAdmin(conn) && !isSystemAdmin(conn)) {
                    throw new UserProfileAccessDeniedException(
                            "Only Super Admin or System Admin may change the DEMO ONLY stale threshold");
                }
                PostureIndicatorEngine.setStaleThresholdMinutes(minutes);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), "IADM07_STALE_THRESHOLD",
                        "settings", null, "Stale threshold set to " + minutes);
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("staleThresholdMinutes", Integer.valueOf(PostureIndicatorEngine.getStaleThresholdMinutes()));
                m.put("label", "DEMO ONLY");
                return m;
            }
        });
    }

    private Map<String, Object> intakeSignal(final UserSecurityContext ctx, final Map<String, Object> body,
                                             final boolean fromSimulator) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                if (fromSimulator) {
                    // DEMO ONLY simulator: same intake path as /intake/signals with service-caller
                    // semantics (not the R01/R02 requireAny bypass).
                    SignalIntakeSimulator.assertEnabled();
                    assertServiceIntakeAccess(false, true);
                } else {
                    requireServiceIntake(conn);
                }
                String correlationId = str(body, "correlationId");
                String source = str(body, "source");
                String summary = str(body, "summary");
                if (correlationId == null || source == null || summary == null) {
                    throw new MalformedPayloadException("correlationId, source and summary are required");
                }
                Map<String, Object> existing = findSignalByCorrelation(conn, correlationId, false);
                if (existing != null) {
                    UUID existingId = uuid(existing.get("id"));
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE sec.signals SET conflict_flag = true, status = 'CONFLICT_REVIEW', "
                                    + "updated_at = now() WHERE id = ?")) {
                        ps.setObject(1, existingId);
                        ps.executeUpdate();
                    }
                    DurableAuditWriter.append(conn, ctx, uuid(existing.get("instituteId")),
                            "IADM07_SIGNAL_CONFLICT", "signal", existingId.toString(),
                            "Conflicting duplicate signal " + correlationId);
                    Map<String, Object> out = new LinkedHashMap<String, Object>();
                    out.put("duplicate", Boolean.TRUE);
                    out.put("conflict", Boolean.TRUE);
                    out.put("existing", existing);
                    return out;
                }
                UUID id = UUID.randomUUID();
                UUID instituteId = uuid(body.get("instituteId"));
                boolean demoOnly = Boolean.TRUE.equals(body.get("sourceDemoOnly"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("sourceDemoOnly")));
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sec.signals (id, institute_id, correlation_id, source, source_demo_only, "
                                + "severity, status, summary) VALUES (?, ?, ?, ?, ?, ?, 'NEW', ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, correlationId);
                    ps.setString(4, source);
                    ps.setBoolean(5, demoOnly);
                    ps.setString(6, str(body, "severity", "MEDIUM"));
                    ps.setString(7, summary);
                    ps.executeUpdate();
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_SIGNAL_INTAKE",
                        "signal", id.toString(), "Signal intake " + correlationId);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("duplicate", Boolean.FALSE);
                out.put("conflict", Boolean.FALSE);
                out.put("simulator", Boolean.valueOf(fromSimulator));
                return out;
            }
        });
    }

    public Map<String, Object> simulatorIntake(final UserSecurityContext ctx, final Map<String, Object> payload) {
        return intakeSignal(ctx, payload, true);
    }

    // --- helpers ---

    void requireServiceIntake(Connection conn) throws SQLException {
        boolean humanOperator = isSuperAdmin(conn) || isSystemAdmin(conn);
        boolean hasIntake = hasPermission(conn, "iadm07.signal.intake");
        assertServiceIntakeAccess(humanOperator, hasIntake);
    }

    public static void assertServiceIntakeAccess(boolean isHumanOperator, boolean hasSignalIntakePermission) {
        if (isHumanOperator) {
            throw new UserProfileAccessDeniedException(
                    "Signal intake is restricted to authenticated service callers");
        }
        if (!hasSignalIntakePermission) {
            throw new UserProfileAccessDeniedException("Missing required iadm07.signal.intake permission");
        }
    }

    public static UUID resolveWriteInstituteId(boolean isPlatformOperator, UUID ctxTenantId, UUID requestedInstituteId) {
        if (isPlatformOperator) {
            return requestedInstituteId != null ? requestedInstituteId : ctxTenantId;
        }
        if (ctxTenantId == null) {
            throw new InstituteAdminException(403, "IADM07_SCOPE_REQUIRED",
                    "Institute context could not be verified");
        }
        if (requestedInstituteId != null && !requestedInstituteId.equals(ctxTenantId)) {
            throw new UserProfileAccessDeniedException("Cross-institute write refused");
        }
        return ctxTenantId;
    }

    private boolean instituteExists(Connection conn, UUID instituteId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
            ps.setObject(1, instituteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private UUID resolveWriteInstituteId(Connection conn, UserSecurityContext ctx, Map<String, Object> body)
            throws SQLException {
        boolean platformOperator = isSuperAdmin(conn) || isSystemAdmin(conn);
        Object rawInst = body == null ? null : body.get("instituteId");
        UUID requested = null;
        if (rawInst != null && !String.valueOf(rawInst).trim().isEmpty()) {
            requested = uuid(rawInst);
            if (requested == null && !platformOperator) {
                throw new UserProfileAccessDeniedException("Cross-institute write refused");
            }
        }
        return resolveWriteInstituteId(platformOperator, ctx.getTenantId(), requested);
    }

    public static void assertExceptionDecisionAllowed(UUID requesterUserId, UUID deciderUserId) {
        if (requesterUserId != null && requesterUserId.equals(deciderUserId)) {
            throw new InstituteAdminException(403, "IADM07_SOD_VIOLATION",
                    "Decider must differ from requester");
        }
    }

    public static Timestamp[] parseRequiredValidityWindow(Map<String, Object> body) {
        String fromRaw = str(body, "validFrom");
        String toRaw = str(body, "validTo");
        if (fromRaw == null || toRaw == null) {
            throw new MalformedPayloadException("validFrom and validTo are required");
        }
        Instant fromInst = parseIsoOrDate(fromRaw);
        Instant toInst = parseIsoOrDate(toRaw);
        if (fromInst == null || toInst == null) {
            throw new MalformedPayloadException("validFrom and validTo must be valid ISO-8601 timestamps or dates");
        }
        if (!toInst.isAfter(fromInst)) {
            throw new MalformedPayloadException("validTo must be after validFrom");
        }
        return new Timestamp[]{Timestamp.from(fromInst), Timestamp.from(toInst)};
    }

    private static Instant parseIsoOrDate(String raw) {
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void assertInstitutionFindingScope(UUID ctxTenantId, UUID instituteId, UUID assignedInstituteId) {
        if (ctxTenantId == null) {
            throw new InstituteAdminException(403, "IADM07_SCOPE_REQUIRED",
                    "Institute context could not be verified");
        }
        boolean own = ctxTenantId.equals(instituteId) || ctxTenantId.equals(assignedInstituteId);
        if (!own) {
            throw new UserProfileAccessDeniedException("Cross-institute finding transition refused");
        }
    }

    public static void assertInstitutionFindingTransition(String from, String to) {
        if ("RESOLVED".equals(to)) {
            throw new UserProfileAccessDeniedException("Only platform operators may mark findings resolved");
        }
        boolean allowed = ("ASSIGNED".equals(from) && "REMEDIATION_IN_PROGRESS".equals(to))
                || ("REMEDIATION_IN_PROGRESS".equals(from) && "PENDING_VERIFICATION".equals(to));
        if (!allowed) {
            throw new UserProfileAccessDeniedException(
                    "Institution Admin may only move assigned findings from ASSIGNED to REMEDIATION_IN_PROGRESS or PENDING_VERIFICATION");
        }
    }

    public static void assertInstitutionAlertTransition(String from, String to) {
        if (!("OPEN".equals(from) && "ACKNOWLEDGED".equals(to))) {
            throw new UserProfileAccessDeniedException(
                    "Institution Admin may only acknowledge open alerts (OPEN to ACKNOWLEDGED)");
        }
    }

    void requireAny(Connection conn, String... keys) throws SQLException {
        if (isSuperAdmin(conn) || isSystemAdmin(conn)) {
            return;
        }
        for (int i = 0; i < keys.length; i++) {
            if (hasPermission(conn, keys[i])) {
                return;
            }
        }
        throw new UserProfileAccessDeniedException("Missing required IADM-07 permission");
    }

    void requirePlatformAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.is_platform_admin()")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new UserProfileAccessDeniedException(
                            "Platform administrator required for this action");
                }
            }
        }
    }

    private void denySummaryDetail(Connection conn) throws SQLException {
        if (hasSummaryOnly(conn) && !hasPermission(conn, "iadm07.register.read")
                && !isSuperAdmin(conn) && !isSystemAdmin(conn)) {
            throw new UserProfileAccessDeniedException(
                    "College administrators may only view summary posture and published standards");
        }
    }

    private boolean hasSummaryOnly(Connection conn) throws SQLException {
        return hasPermission(conn, "iadm07.summary.view")
                && !hasPermission(conn, "iadm07.register.read")
                && !isSuperAdmin(conn) && !isSystemAdmin(conn);
    }

    private UUID resolveScope(Connection conn, UserSecurityContext ctx, String requested, boolean allowPlatformNull)
            throws SQLException {
        boolean platformWideReader = isSuperAdmin(conn) || isSystemAdmin(conn)
                || (hasPermission(conn, "iadm07.audit.view")
                && hasPermission(conn, "iadm07.register.read")
                && !hasPermission(conn, "iadm07.register.write"));
        if (platformWideReader) {
            if (requested != null && !requested.trim().isEmpty()) {
                return UUID.fromString(requested.trim());
            }
            if (allowPlatformNull) {
                return null;
            }
        }
        if (ctx.getTenantId() == null) {
            throw new InstituteAdminException(403, "IADM07_SCOPE_REQUIRED",
                    "Institute context could not be verified");
        }
        if (requested != null && !requested.trim().isEmpty()) {
            UUID req = UUID.fromString(requested.trim());
            if (!req.equals(ctx.getTenantId()) && !platformWideReader) {
                throw new UserProfileAccessDeniedException("Cross-institute access refused");
            }
            return req;
        }
        return ctx.getTenantId();
    }

    private Map<String, Object> listScoped(Connection conn, String table, String selectPrefix,
                                           String scopeTenantId, UserSecurityContext ctx) throws SQLException {
        UUID scope = resolveScope(conn, ctx, scopeTenantId, true);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        String sql = selectPrefix + (scope != null ? " WHERE institute_id = ? " : "") + "ORDER BY updated_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (scope != null) {
                ps.setObject(1, scope);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("id", rs.getObject("id").toString());
                    if (hasColumn(rs, "code")) {
                        row.put("code", rs.getString("code"));
                    }
                    if (hasColumn(rs, "title")) {
                        row.put("title", rs.getString("title"));
                    }
                    if (hasColumn(rs, "correlation_id")) {
                        row.put("correlationId", rs.getString("correlation_id"));
                    }
                    if (hasColumn(rs, "source")) {
                        row.put("source", rs.getString("source"));
                    }
                    if (hasColumn(rs, "severity")) {
                        row.put("severity", rs.getString("severity"));
                    }
                    row.put("status", rs.getString("status"));
                    if (hasColumn(rs, "institute_id")) {
                        row.put("instituteId", rs.getObject("institute_id") == null ? null
                                : rs.getObject("institute_id").toString());
                    }
                    row.put("updatedAt", ts(rs.getTimestamp("updated_at")));
                    items.add(row);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("items", items);
        return out;
    }

    private boolean hasColumn(ResultSet rs, String col) throws SQLException {
        try {
            rs.findColumn(col);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private Map<String, Object> mutateRegister(final UserSecurityContext ctx, final Map<String, Object> body,
                                               final String entityType, final String table,
                                               final String registerKind, final String initialStatus) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String code = str(body, "code");
                String title = str(body, "title");
                if (code == null || title == null) {
                    throw new MalformedPayloadException("code and title are required");
                }
                UUID instituteId = uuid(body.get("instituteId"));
                if (instituteId == null) {
                    instituteId = ctx.getTenantId();
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + table + " (id, institute_id, code, title, status, created_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, id);
                    ps.setObject(2, instituteId);
                    ps.setString(3, code);
                    ps.setString(4, title);
                    ps.setString(5, initialStatus);
                    ps.setObject(6, ctx.getUserId());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                        throw new ResourceConflictException(entityType, "code", code);
                    }
                    throw ex;
                }
                DurableAuditWriter.append(conn, ctx, instituteId, "IADM07_" + entityType.toUpperCase() + "_CREATE",
                        entityType, id.toString(), "Created " + entityType);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("code", code);
                out.put("status", initialStatus);
                return out;
            }
        });
    }

    private Map<String, Object> transitionRegister(final UserSecurityContext ctx, final UUID id, final String toStatus,
                                                   final String registerKind, final String entityType,
                                                   final String table, final String auditPrefix) {
        return ctx.executeInTransaction(db, new UserSecurityContext.TransactionCallback<Map<String, Object>>() {
            public Map<String, Object> execute(Connection conn) throws SQLException {
                requireAny(conn, "iadm07.register.write");
                String from = loadStatus(conn, table, id);
                String to = toStatus == null ? null : toStatus.trim().toUpperCase();
                RegisterLifecycleEngine.assertTransition(registerKind, from, to);
                updateStatus(conn, table, id, to);
                DurableAuditWriter.append(conn, ctx, ctx.getTenantId(), auditPrefix + to,
                        entityType, id.toString(), entityType + " " + from + " → " + to);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("id", id.toString());
                out.put("status", to);
                return out;
            }
        });
    }

    private String loadStatus(Connection conn, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("register", id.toString());
                }
                return rs.getString("status");
            }
        }
    }

    private void updateStatus(Connection conn, String table, UUID id, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + table + " SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, id);
            if (ps.executeUpdate() == 0) {
                throw new ResourceNotFoundException("register", id.toString());
            }
        }
    }

    private Map<String, Object> loadStandard(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, code, title, version_no, status, author_user_id, approver_user_id, effective_at, "
                        + "review_due_at, updated_at FROM sec.security_standards WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("security_standard", id.toString());
                }
                return mapStandard(rs);
            }
        }
    }

    private Map<String, Object> mapStandard(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("code", rs.getString("code"));
        m.put("title", rs.getString("title"));
        m.put("versionNo", Integer.valueOf(rs.getInt("version_no")));
        m.put("status", rs.getString("status"));
        m.put("authorUserId", rs.getObject("author_user_id") == null ? null
                : rs.getObject("author_user_id").toString());
        m.put("approverUserId", rs.getObject("approver_user_id") == null ? null
                : rs.getObject("approver_user_id").toString());
        m.put("effectiveAt", ts(rs.getTimestamp("effective_at")));
        m.put("reviewDueAt", ts(rs.getTimestamp("review_due_at")));
        m.put("updatedAt", ts(rs.getTimestamp("updated_at")));
        return m;
    }

    private Map<String, Object> loadFinding(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, code, title, severity, status, institute_id, assigned_institute_id, target_date, "
                        + "updated_at FROM sec.findings WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("finding", id.toString());
                }
                return mapFinding(rs);
            }
        }
    }

    private Map<String, Object> mapFinding(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("code", rs.getString("code"));
        m.put("title", rs.getString("title"));
        m.put("severity", rs.getString("severity"));
        m.put("status", rs.getString("status"));
        m.put("instituteId", rs.getObject("institute_id") == null ? null : rs.getObject("institute_id").toString());
        m.put("assignedInstituteId", rs.getObject("assigned_institute_id") == null ? null
                : rs.getObject("assigned_institute_id").toString());
        m.put("targetDate", rs.getDate("target_date") == null ? null : rs.getDate("target_date").toString());
        m.put("updatedAt", ts(rs.getTimestamp("updated_at")));
        return m;
    }

    private Map<String, Object> loadException(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, code, rationale, status, valid_from, valid_to, institute_id, "
                        + "requester_user_id, updated_at FROM sec.exceptions WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ResourceNotFoundException("exception", id.toString());
                }
                return mapException(rs);
            }
        }
    }

    private Map<String, Object> mapException(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", rs.getObject("id").toString());
        m.put("code", rs.getString("code"));
        m.put("rationale", rs.getString("rationale"));
        String status = rs.getString("status");
        Timestamp validTo = rs.getTimestamp("valid_to");
        if (validTo != null && validTo.toInstant().isBefore(Instant.now())
                && !"REJECTED".equals(status)) {
            status = "EXPIRED";
        }
        m.put("status", status);
        m.put("validFrom", ts(rs.getTimestamp("valid_from")));
        m.put("validTo", ts(validTo));
        m.put("instituteId", rs.getObject("institute_id") == null ? null : rs.getObject("institute_id").toString());
        m.put("requesterUserId", rs.getObject("requester_user_id") == null ? null
                : rs.getObject("requester_user_id").toString());
        m.put("updatedAt", ts(rs.getTimestamp("updated_at")));
        return m;
    }

    private Map<String, Object> findSignalByCorrelation(Connection conn, String correlationId, boolean conflictOnly)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, institute_id, correlation_id, status, summary, updated_at FROM sec.signals "
                        + "WHERE correlation_id = ? AND conflict_flag = false LIMIT 1")) {
            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("instituteId", rs.getObject("institute_id") == null ? null
                        : rs.getObject("institute_id").toString());
                m.put("correlationId", rs.getString("correlation_id"));
                m.put("status", rs.getString("status"));
                m.put("summary", rs.getString("summary"));
                m.put("updatedAt", ts(rs.getTimestamp("updated_at")));
                return m;
            }
        }
    }

    private Map<String, Object> findEnforcementByCorrelation(Connection conn, String correlationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, correlation_id, service_name, reported_status FROM sec.enforcement_status "
                        + "WHERE correlation_id = ? LIMIT 1")) {
            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", rs.getObject("id").toString());
                m.put("correlationId", rs.getString("correlation_id"));
                m.put("serviceName", rs.getString("service_name"));
                m.put("reportedStatus", rs.getString("reported_status"));
                return m;
            }
        }
    }

    private int countPublishedStandards(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*)::int FROM sec.security_standards WHERE status = 'PUBLISHED'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Integer count(Connection conn, String sql, UUID scope) throws SQLException {
        return Integer.valueOf(count(conn, sql, scope, false));
    }

    private int count(Connection conn, String sql, UUID scope, boolean includeAssigned) throws SQLException {
        String q = sql + scopeFilter(scope, includeAssigned, " AND ");
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            if (scope != null) {
                ps.setObject(1, scope);
                if (includeAssigned) {
                    ps.setObject(2, scope);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Map<String, Integer> groupCounts(Connection conn, String table, String column,
                                             UUID scope, boolean includeAssigned) throws SQLException {
        return groupCountsWhere(conn, "SELECT " + column + ", COUNT(*)::int FROM " + table,
                "GROUP BY " + column + " ORDER BY " + column, scope, includeAssigned);
    }

    private Map<String, Integer> groupCountsWhere(Connection conn, String selectPrefix, String groupSuffix,
                                                  UUID scope, boolean includeAssigned) throws SQLException {
        boolean hasWhere = selectPrefix.toUpperCase().contains(" WHERE ");
        String q = selectPrefix + scopeFilter(scope, includeAssigned, hasWhere ? " AND " : " WHERE ") + " " + groupSuffix;
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            if (scope != null) {
                ps.setObject(1, scope);
                if (includeAssigned) {
                    ps.setObject(2, scope);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), Integer.valueOf(rs.getInt(2)));
                }
            }
        }
        return out;
    }

    private Instant maxUpdatedAt(Connection conn, String table, UUID scope, boolean includeAssigned)
            throws SQLException {
        String q = "SELECT MAX(updated_at) FROM " + table + scopeFilter(scope, includeAssigned, " WHERE ");
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            if (scope != null) {
                ps.setObject(1, scope);
                if (includeAssigned) {
                    ps.setObject(2, scope);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp t = rs.getTimestamp(1);
                    return t == null ? null : t.toInstant();
                }
            }
        } catch (SQLException ex) {
            return null;
        }
        return null;
    }

    private static String scopeFilter(UUID scope, boolean includeAssigned, String conj) {
        if (scope == null) {
            return "";
        }
        return includeAssigned
                ? conj + "(institute_id = ? OR assigned_institute_id = ?)"
                : conj + "institute_id = ?";
    }

    private void auditRead(Connection conn, UserSecurityContext ctx, UUID tenantId, String action,
                         String entityType, String entityId, String message) throws SQLException {
        DurableAuditWriter.append(conn, ctx, tenantId, action, entityType, entityId, message);
    }

    private boolean isSuperAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.platform_admins p WHERE p.user_id = auth.uid() "
                        + "AND p.role_code = 'SUPER_ADMIN' AND p.deleted_at IS NULL LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private boolean isSystemAdmin(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM plat.platform_admins p WHERE p.user_id = auth.uid() "
                        + "AND p.role_code = 'SYSTEM_ADMIN' AND p.status = 'ACTIVE' AND p.deleted_at IS NULL LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private boolean hasPermission(Connection conn, String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission(?)")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static String str(Map<String, Object> m, String key) {
        return str(m, key, null);
    }

    private static String str(Map<String, Object> m, String key, String def) {
        if (m == null) {
            return def;
        }
        Object v = m.get(key);
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(o).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String ts(Timestamp t) {
        return t == null ? null : t.toInstant().toString();
    }

    private static Timestamp parseTs(Map<String, Object> body, String key) {
        return parseTs(body, key, null);
    }

    private static Timestamp parseTs(Map<String, Object> body, String key, Timestamp def) {
        String v = str(body, key);
        if (v == null) {
            return def;
        }
        try {
            return Timestamp.from(Instant.parse(v));
        } catch (Exception ex) {
            return def;
        }
    }

    private static Timestamp nowTs() {
        return Timestamp.from(Instant.now());
    }

    private static Timestamp futureTs(int days) {
        return Timestamp.from(Instant.now().plusSeconds(days * 86400L));
    }
}