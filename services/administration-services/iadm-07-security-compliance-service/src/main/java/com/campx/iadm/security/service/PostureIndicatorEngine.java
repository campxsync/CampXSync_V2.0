package com.campx.iadm.security.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DEMO ONLY posture indicators from sec.* registers (IADM-07-US-029).
 * Plain language: nine summary indicators; stale or unreadable data is never shown as healthy zero.
 */
public final class PostureIndicatorEngine {

    /** DEMO ONLY default stale threshold: 7 days (10080 minutes). */
    public static final int DOCUMENTED_DEMO_DEFAULT_MINUTES = 10080;

    private static volatile int staleThresholdMinutes = DOCUMENTED_DEMO_DEFAULT_MINUTES;

    private PostureIndicatorEngine() {
    }

    public static int getStaleThresholdMinutes() {
        return staleThresholdMinutes;
    }

    public static void setStaleThresholdMinutes(int minutes) {
        if (minutes < 1) {
            minutes = DOCUMENTED_DEMO_DEFAULT_MINUTES;
        }
        staleThresholdMinutes = minutes;
    }

    public static boolean isStale(Instant updatedAt) {
        if (updatedAt == null) {
            return true;
        }
        long minutes = Duration.between(updatedAt, Instant.now()).toMinutes();
        return minutes > staleThresholdMinutes;
    }

    /**
     * Compute all nine indicators for a scope. {@code instituteId} null = platform-wide.
     * {@code summaryOnly} strips detail payloads (College Admin).
     */
    public static Map<String, Object> computeAll(Connection conn, UUID instituteId, boolean summaryOnly)
            throws SQLException {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("staleThresholdMinutes", Integer.valueOf(staleThresholdMinutes));
        out.put("label", "DEMO ONLY stale threshold — not a production SLA");

        List<Map<String, Object>> indicators = new ArrayList<Map<String, Object>>();
        indicators.add(openFindingsBySeverity(conn, instituteId));
        indicators.add(overdueRemediations(conn, instituteId));
        indicators.add(controlsByAssessmentStatus(conn, instituteId, summaryOnly));
        indicators.add(pendingExceptions(conn, instituteId));
        indicators.add(expiringExceptions(conn, instituteId));
        indicators.add(openAlerts(conn, instituteId));
        indicators.add(evidencePending(conn, instituteId));
        indicators.add(standardsDueForReview(conn, instituteId));
        indicators.add(staleSourceIndicator(conn, instituteId));

        out.put("indicators", indicators);
        return out;
    }

    private static Map<String, Object> openFindingsBySeverity(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT severity, COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd "
                + "FROM sec.findings WHERE status <> 'RESOLVED' "
                + (instituteId != null ? "AND (institute_id = ? OR assigned_institute_id = ?) " : "")
                + "GROUP BY severity ORDER BY severity";
        return aggregateIndicator(conn, "openFindingsBySeverity", sql, instituteId, instituteId, true);
    }

    private static Map<String, Object> overdueRemediations(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd FROM sec.findings "
                + "WHERE status NOT IN ('RESOLVED','OPEN') AND target_date IS NOT NULL "
                + "AND target_date < CURRENT_DATE "
                + (instituteId != null ? "AND (institute_id = ? OR assigned_institute_id = ?) " : "");
        return countIndicator(conn, "overdueRemediations", sql, instituteId, instituteId);
    }

    private static Map<String, Object> controlsByAssessmentStatus(Connection conn, UUID instituteId, boolean summaryOnly)
            throws SQLException {
        String sql = "SELECT status, COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd "
                + "FROM sec.control_assessments "
                + (instituteId != null ? "WHERE institute_id = ? " : "")
                + "GROUP BY status ORDER BY status";
        Map<String, Object> ind = aggregateIndicator(conn, "controlsByAssessmentStatus", sql, instituteId, null, true);
        if (summaryOnly) {
            ind.remove("breakdown");
        }
        return ind;
    }

    private static Map<String, Object> pendingExceptions(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd FROM sec.exceptions "
                + "WHERE status = 'REQUESTED' "
                + (instituteId != null ? "AND institute_id = ? " : "");
        return countIndicator(conn, "pendingExceptions", sql, instituteId, null);
    }

    private static Map<String, Object> expiringExceptions(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd FROM sec.exceptions "
                + "WHERE status IN ('REQUESTED','APPROVED') AND valid_to <= (now() + interval '14 days') "
                + (instituteId != null ? "AND institute_id = ? " : "");
        return countIndicator(conn, "expiringExceptions", sql, instituteId, null);
    }

    private static Map<String, Object> openAlerts(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd FROM sec.alerts "
                + "WHERE status <> 'RESOLVED' "
                + (instituteId != null ? "AND institute_id = ? " : "");
        return countIndicator(conn, "openAlerts", sql, instituteId, null);
    }

    private static Map<String, Object> evidencePending(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd FROM sec.control_assessments "
                + "WHERE status = 'EVIDENCE_PENDING' "
                + (instituteId != null ? "AND institute_id = ? " : "");
        return countIndicator(conn, "evidencePending", sql, instituteId, null);
    }

    private static Map<String, Object> standardsDueForReview(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT COUNT(*)::int AS cnt, MAX(updated_at) AS last_upd FROM sec.security_standards "
                + "WHERE review_due_at IS NOT NULL AND review_due_at <= (now() + interval '30 days') "
                + "AND status = 'PUBLISHED'";
        return countIndicator(conn, "standardsDueForReview", sql, null, null);
    }

    private static Map<String, Object> staleSourceIndicator(Connection conn, UUID instituteId) throws SQLException {
        String sql = "SELECT correlation_id, updated_at, status FROM sec.signals "
                + (instituteId != null ? "WHERE institute_id = ? " : "")
                + "ORDER BY updated_at ASC NULLS FIRST LIMIT 1";
        Map<String, Object> ind = new LinkedHashMap<String, Object>();
        ind.put("code", "staleSourceIndicator");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (instituteId != null) {
                ps.setObject(1, instituteId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    ind.put("status", "UNAVAILABLE");
                    ind.put("message", "No signal source data");
                    return ind;
                }
                Timestamp ts = rs.getTimestamp("updated_at");
                Instant updated = ts == null ? null : ts.toInstant();
                boolean stale = isStale(updated);
                ind.put("correlationId", rs.getString("correlation_id"));
                ind.put("signalStatus", rs.getString("status"));
                ind.put("status", stale ? "STALE" : "CURRENT");
                ind.put("updatedAt", updated == null ? null : updated.toString());
                return ind;
            }
        } catch (SQLException ex) {
            ind.put("status", "UNAVAILABLE");
            ind.put("message", "Signal register could not be read");
            return ind;
        }
    }

    private static Map<String, Object> countIndicator(Connection conn, String code, String sql,
                                                        UUID p1, UUID p2) throws SQLException {
        Map<String, Object> ind = new LinkedHashMap<String, Object>();
        ind.put("code", code);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindScope(ps, p1, p2);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    ind.put("status", "UNAVAILABLE");
                    return ind;
                }
                int cnt = rs.getInt("cnt");
                Timestamp ts = rs.getTimestamp("last_upd");
                Instant updated = ts == null ? null : ts.toInstant();
                if (rs.wasNull() && ts == null) {
                    ind.put("status", "UNAVAILABLE");
                    return ind;
                }
                ind.put("count", Integer.valueOf(cnt));
                ind.put("status", isStale(updated) ? "STALE" : "CURRENT");
                ind.put("updatedAt", updated == null ? null : updated.toString());
                return ind;
            }
        } catch (SQLException ex) {
            ind.put("status", "UNAVAILABLE");
            return ind;
        }
    }

    private static Map<String, Object> aggregateIndicator(Connection conn, String code, String sql,
                                                            UUID p1, UUID p2, boolean allowEmptyAsZero)
            throws SQLException {
        Map<String, Object> ind = new LinkedHashMap<String, Object>();
        ind.put("code", code);
        Map<String, Integer> breakdown = new LinkedHashMap<String, Integer>();
        Instant latest = null;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindScope(ps, p1, p2);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    String key = rs.getString(1);
                    breakdown.put(key, Integer.valueOf(rs.getInt("cnt")));
                    Timestamp ts = rs.getTimestamp("last_upd");
                    if (ts != null) {
                        Instant u = ts.toInstant();
                        if (latest == null || u.isAfter(latest)) {
                            latest = u;
                        }
                    }
                }
                if (!any && !allowEmptyAsZero) {
                    ind.put("status", "UNAVAILABLE");
                    return ind;
                }
                ind.put("breakdown", breakdown);
                int total = 0;
                for (Integer v : breakdown.values()) {
                    total += v.intValue();
                }
                ind.put("count", Integer.valueOf(total));
                if (latest == null) {
                    ind.put("status", "UNAVAILABLE");
                } else {
                    ind.put("status", isStale(latest) ? "STALE" : "CURRENT");
                    ind.put("updatedAt", latest.toString());
                }
                return ind;
            }
        } catch (SQLException ex) {
            ind.put("status", "UNAVAILABLE");
            return ind;
        }
    }

    private static void bindScope(PreparedStatement ps, UUID p1, UUID p2) throws SQLException {
        int idx = 1;
        if (p1 != null) {
            ps.setObject(idx++, p1);
        }
        if (p2 != null) {
            ps.setObject(idx++, p2);
        }
    }
}