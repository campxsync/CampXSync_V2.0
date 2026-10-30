package com.campx.academic.analytics.repository;

import com.campx.academic.analytics.config.DatabaseConnectionManager;
import com.campx.academic.analytics.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * PostgreSQL implementation of AnalyticsDashboardRepository.
 * Executes optimized relational read queries against authoritative PostgreSQL tables
 * under explicit tenant RLS context.
 */
public class PostgresAnalyticsDashboardRepository implements AnalyticsDashboardRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresAnalyticsDashboardRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresAnalyticsDashboardRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager != null ? connectionManager : DatabaseConnectionManager.getInstance();
    }

    private void applyRlsContext(Connection conn, UserSecurityContext context) throws SQLException {
        if (context == null || context.getTenantId() == null) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            String claims = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                    context.getUserId() != null ? context.getUserId() : UUID.randomUUID(),
                    context.getTenantId(),
                    context.getTenantId());
            stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claims));
            stmt.execute(String.format("SELECT set_config('request.jwt.claim.tenant_id', '%s', true)", context.getTenantId()));
            if (context.getUserId() != null) {
                stmt.execute(String.format("SELECT set_config('request.jwt.claim.sub', '%s', true)", context.getUserId()));
            }
            stmt.execute("SELECT set_config('request.jwt.claim.role', 'authenticated', true)");
        }
    }

    @Override
    public Map<String, Object> getCollegeAdminDashboard(UserSecurityContext context, String period) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (context == null || context.getTenantId() == null) {
            throw new IllegalArgumentException("Tenant context is required for college admin dashboard");
        }
        UUID tenantId = context.getTenantId();

        try (Connection conn = connectionManager.getConnection()) {
            applyRlsContext(conn, context);

            // 1. Overall Headline KPIs
            int studentCount = 0;
            int facultyCount = 0;
            int sessionCount = 0;
            int totalRecords = 0;
            int presentRecords = 0;
            double overallPercentage = 0.0;

            String kpiSql = "SELECT " +
                    "(SELECT count(*)::int FROM stu.students WHERE tenant_id = ? AND deleted_at IS NULL) as student_count, " +
                    "(SELECT count(*)::int FROM hrm.employees WHERE tenant_id = ? AND deleted_at IS NULL) as faculty_count, " +
                    "(SELECT count(*)::int FROM acd.attendance_sessions WHERE tenant_id = ? AND deleted_at IS NULL) as session_count, " +
                    "(SELECT count(*)::int FROM acd.attendance_records WHERE tenant_id = ? AND deleted_at IS NULL) as total_records, " +
                    "(SELECT count(*)::int FROM acd.attendance_records WHERE tenant_id = ? AND status = 'PRESENT' AND deleted_at IS NULL) as present_records";

            try (PreparedStatement ps = conn.prepareStatement(kpiSql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, tenantId);
                ps.setObject(3, tenantId);
                ps.setObject(4, tenantId);
                ps.setObject(5, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        studentCount = rs.getInt("student_count");
                        facultyCount = rs.getInt("faculty_count");
                        sessionCount = rs.getInt("session_count");
                        totalRecords = rs.getInt("total_records");
                        presentRecords = rs.getInt("present_records");
                        overallPercentage = totalRecords > 0 ? Math.round(((double) presentRecords / totalRecords) * 1000.0) / 10.0 : 0.0;
                    }
                }
            }

            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("studentCount", studentCount);
            kpis.put("facultyCount", facultyCount);
            kpis.put("sessionCount", sessionCount);
            kpis.put("totalRecords", totalRecords);
            kpis.put("presentRecords", presentRecords);
            kpis.put("overallPercentage", overallPercentage);
            result.put("kpis", kpis);

            // 2. Shortage Students (< 75% Statutory Threshold)
            List<Map<String, Object>> shortageStudents = new ArrayList<>();
            String shortageSql = "SELECT " +
                    "s.id as student_id, " +
                    "s.enrollment_no, " +
                    "p.display_name, " +
                    "count(r.id)::int as total_sessions, " +
                    "count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::int as attended, " +
                    "ROUND((count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) * 100, 1) as percentage " +
                    "FROM stu.students s " +
                    "JOIN core.persons p ON p.id = s.person_id " +
                    "JOIN acd.attendance_records r ON r.student_id = s.id AND r.deleted_at IS NULL " +
                    "WHERE s.tenant_id = ? AND s.deleted_at IS NULL " +
                    "GROUP BY s.id, s.enrollment_no, p.display_name " +
                    "HAVING (count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) < 0.75 " +
                    "ORDER BY percentage ASC";

            try (PreparedStatement ps = conn.prepareStatement(shortageSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> stu = new LinkedHashMap<>();
                        stu.put("studentId", rs.getString("student_id"));
                        stu.put("enrollmentNo", rs.getString("enrollment_no"));
                        stu.put("displayName", rs.getString("display_name"));
                        stu.put("totalSessions", rs.getInt("total_sessions"));
                        stu.put("attended", rs.getInt("attended"));
                        stu.put("percentage", rs.getDouble("percentage"));
                        shortageStudents.add(stu);
                    }
                }
            }
            result.put("shortageStudents", shortageStudents);
            result.put("shortageCount", shortageStudents.size());

            // 3. 14-Day Attendance Trend
            List<Map<String, Object>> trend = new ArrayList<>();
            String trendSql = "SELECT " +
                    "TO_CHAR(s.session_date, 'YYYY-MM-DD') as date_str, " +
                    "TO_CHAR(s.session_date, 'DD Mon') as display_date, " +
                    "count(r.id)::int as total_records, " +
                    "count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::int as present_records, " +
                    "ROUND((count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) * 100, 1) as percentage " +
                    "FROM acd.attendance_sessions s " +
                    "JOIN acd.attendance_records r ON r.session_id = s.id AND r.deleted_at IS NULL " +
                    "WHERE s.tenant_id = ? AND s.deleted_at IS NULL " +
                    "GROUP BY s.session_date " +
                    "ORDER BY s.session_date ASC " +
                    "LIMIT 14";

            try (PreparedStatement ps = conn.prepareStatement(trendSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> day = new LinkedHashMap<>();
                        day.put("dateStr", rs.getString("date_str"));
                        day.put("displayDate", rs.getString("display_date"));
                        day.put("totalRecords", rs.getInt("total_records"));
                        day.put("presentRecords", rs.getInt("present_records"));
                        day.put("percentage", rs.getDouble("percentage"));
                        trend.add(day);
                    }
                }
            }
            result.put("trend", trend);

            // 4. Batch & Subject Distribution
            List<Map<String, Object>> batchBreakdown = new ArrayList<>();
            String batchSql = "SELECT " +
                    "sec.section_code, " +
                    "COALESCE(sub.code, c.code, 'N/A') as subject_code, " +
                    "COALESCE(sub.name, c.name, 'N/A') as subject_name, " +
                    "count(DISTINCT s.id)::int as sessions_held, " +
                    "count(r.id)::int as total_records, " +
                    "count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::int as present_records, " +
                    "ROUND((count(CASE WHEN r.status = 'PRESENT' THEN 1 END)::numeric / NULLIF(count(r.id), 0)) * 100, 1) as percentage " +
                    "FROM acd.attendance_sessions s " +
                    "JOIN acd.course_sections sec ON sec.id = s.section_id " +
                    "LEFT JOIN acd.course_offerings o ON o.id = sec.offering_id " +
                    "LEFT JOIN acd.courses c ON c.id = o.course_id " +
                    "LEFT JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id " +
                    "LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id " +
                    "LEFT JOIN acd.attendance_records r ON r.session_id = s.id AND r.deleted_at IS NULL " +
                    "WHERE s.tenant_id = ? AND s.deleted_at IS NULL " +
                    "GROUP BY sec.section_code, COALESCE(sub.code, c.code, 'N/A'), COALESCE(sub.name, c.name, 'N/A') " +
                    "ORDER BY sec.section_code, subject_code";

            try (PreparedStatement ps = conn.prepareStatement(batchSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("sectionCode", rs.getString("section_code"));
                        item.put("subjectCode", rs.getString("subject_code"));
                        item.put("subjectName", rs.getString("subject_name"));
                        item.put("sessionsHeld", rs.getInt("sessions_held"));
                        item.put("totalRecords", rs.getInt("total_records"));
                        item.put("presentRecords", rs.getInt("present_records"));
                        item.put("percentage", rs.getDouble("percentage"));
                        batchBreakdown.add(item);
                    }
                }
            }
            result.put("batchBreakdown", batchBreakdown);

            // 5. Missed & Pending Sessions (OPEN status)
            List<Map<String, Object>> missedSessions = new ArrayList<>();
            String missedSql = "SELECT " +
                    "s.id, " +
                    "TO_CHAR(s.session_date, 'YYYY-MM-DD') as session_date, " +
                    "s.start_time, " +
                    "s.end_time, " +
                    "s.status, " +
                    "COALESCE(sub.name, c.name, 'N/A') as subject_name, " +
                    "COALESCE(sub.code, c.code, 'N/A') as subject_code, " +
                    "sec.section_code, " +
                    "COALESCE(p.display_name, 'Unassigned') as faculty_name " +
                    "FROM acd.attendance_sessions s " +
                    "JOIN acd.course_sections sec ON sec.id = s.section_id " +
                    "LEFT JOIN acd.course_offerings o ON o.id = sec.offering_id " +
                    "LEFT JOIN acd.courses c ON c.id = o.course_id " +
                    "LEFT JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id " +
                    "LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id " +
                    "LEFT JOIN hrm.employees emp ON emp.id = s.employee_id " +
                    "LEFT JOIN core.persons p ON p.id = emp.person_id " +
                    "WHERE s.tenant_id = ? AND s.deleted_at IS NULL AND s.status = 'OPEN' " +
                    "ORDER BY s.session_date ASC";

            try (PreparedStatement ps = conn.prepareStatement(missedSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> sess = new LinkedHashMap<>();
                        sess.put("id", rs.getString("id"));
                        sess.put("sessionDate", rs.getString("session_date"));
                        sess.put("startTime", rs.getString("start_time"));
                        sess.put("endTime", rs.getString("end_time"));
                        sess.put("status", rs.getString("status"));
                        sess.put("subjectName", rs.getString("subject_name"));
                        sess.put("subjectCode", rs.getString("subject_code"));
                        sess.put("sectionCode", rs.getString("section_code"));
                        sess.put("facultyName", rs.getString("faculty_name"));
                        missedSessions.add(sess);
                    }
                }
            }
            result.put("missedSessions", missedSessions);

            // 6. Recent Sessions
            List<Map<String, Object>> recentSessions = new ArrayList<>();
            String recentSql = "SELECT " +
                    "s.id, " +
                    "TO_CHAR(s.session_date, 'YYYY-MM-DD') as session_date, " +
                    "s.start_time, " +
                    "s.end_time, " +
                    "s.status, " +
                    "COALESCE(sub.name, c.name, 'N/A') as subject_name, " +
                    "COALESCE(sub.code, c.code, 'N/A') as subject_code, " +
                    "sec.section_code, " +
                    "COALESCE(p.display_name, 'Unassigned') as faculty_name, " +
                    "(SELECT count(*) FROM acd.attendance_records r WHERE r.session_id = s.id AND r.deleted_at IS NULL)::int as total_records, " +
                    "(SELECT count(*) FROM acd.attendance_records r WHERE r.session_id = s.id AND r.status = 'PRESENT' AND r.deleted_at IS NULL)::int as present_count " +
                    "FROM acd.attendance_sessions s " +
                    "JOIN acd.course_sections sec ON sec.id = s.section_id " +
                    "LEFT JOIN acd.course_offerings o ON o.id = sec.offering_id " +
                    "LEFT JOIN acd.courses c ON c.id = o.course_id " +
                    "LEFT JOIN acd.timetable_entries tte ON tte.id = s.timetable_entry_id " +
                    "LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id " +
                    "LEFT JOIN hrm.employees emp ON emp.id = s.employee_id " +
                    "LEFT JOIN core.persons p ON p.id = emp.person_id " +
                    "WHERE s.tenant_id = ? AND s.deleted_at IS NULL " +
                    "ORDER BY s.session_date DESC " +
                    "LIMIT 6";

            try (PreparedStatement ps = conn.prepareStatement(recentSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> sess = new LinkedHashMap<>();
                        sess.put("id", rs.getString("id"));
                        sess.put("sessionDate", rs.getString("session_date"));
                        sess.put("startTime", rs.getString("start_time") != null ? rs.getString("start_time") : "09:00:00");
                        sess.put("endTime", rs.getString("end_time") != null ? rs.getString("end_time") : "10:00:00");
                        sess.put("status", rs.getString("status"));
                        sess.put("subjectName", rs.getString("subject_name"));
                        sess.put("subjectCode", rs.getString("subject_code"));
                        sess.put("sectionCode", rs.getString("section_code"));
                        sess.put("facultyName", rs.getString("faculty_name"));
                        sess.put("totalRecords", rs.getInt("total_records"));
                        sess.put("presentCount", rs.getInt("present_count"));
                        recentSessions.add(sess);
                    }
                }
            }
            result.put("recentSessions", recentSessions);

            result.put("period", period != null ? period : "2026-27");
            result.put("asOf", java.time.Instant.now().toString());

        } catch (SQLException e) {
            logger.error("Failed to fetch college admin dashboard: {}", e.getMessage(), e);
            throw new RuntimeException("Database error loading college admin dashboard: " + e.getMessage(), e);
        }

        return result;
    }

    @Override
    public Map<String, Object> getFacultyDashboard(UserSecurityContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (context == null || context.getTenantId() == null) {
            throw new IllegalArgumentException("Tenant context is required for faculty dashboard");
        }
        UUID tenantId = context.getTenantId();
        UUID userId = context.getUserId();

        try (Connection conn = connectionManager.getConnection()) {
            applyRlsContext(conn, context);

            // Determine current ISO day of week (1=Monday ... 7=Sunday)
            LocalDate today = LocalDate.now();
            int currentIsoDay = today.getDayOfWeek().getValue();

            // 1. Fetch timetable slots with today's session status joined
            List<Map<String, Object>> allAssignedSlots = new ArrayList<>();
            String slotsSql = "SELECT " +
                    "tte.id, " +
                    "tte.day_of_week, " +
                    "tte.start_time, " +
                    "tte.end_time, " +
                    "sub.name as subject_name, " +
                    "sub.code as subject_code, " +
                    "sec.id as section_id, " +
                    "sec.section_code as section_name, " +
                    "today_sess.id as today_session_id, " +
                    "today_sess.status as today_session_status " +
                    "FROM acd.timetable_entries tte " +
                    "JOIN acd.subjects sub ON sub.id = tte.subject_id " +
                    "LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id " +
                    "LEFT JOIN acd.attendance_sessions today_sess ON today_sess.timetable_entry_id = tte.id " +
                    "  AND today_sess.session_date = CURRENT_DATE " +
                    "  AND today_sess.deleted_at IS NULL " +
                    "WHERE tte.tenant_id = ? AND tte.deleted_at IS NULL " +
                    "ORDER BY tte.day_of_week ASC, tte.start_time ASC";

            try (PreparedStatement ps = conn.prepareStatement(slotsSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> slot = new LinkedHashMap<>();
                        slot.put("id", rs.getString("id"));
                        slot.put("dayOfWeek", rs.getInt("day_of_week"));
                        slot.put("startTime", rs.getString("start_time"));
                        slot.put("endTime", rs.getString("end_time"));
                        slot.put("subjectName", rs.getString("subject_name"));
                        slot.put("subjectCode", rs.getString("subject_code"));
                        slot.put("sectionId", rs.getString("section_id"));
                        slot.put("sectionName", rs.getString("section_name"));
                        slot.put("todaySessionId", rs.getString("today_session_id"));
                        String sessionStatus = rs.getString("today_session_status");
                        slot.put("todaySessionStatus", sessionStatus);

                        String attendanceState = "NOT_STARTED";
                        if ("CLOSED".equalsIgnoreCase(sessionStatus)) {
                            attendanceState = "SUBMITTED";
                        } else if ("OPEN".equalsIgnoreCase(sessionStatus)) {
                            attendanceState = "OPEN";
                        }
                        slot.put("attendanceState", attendanceState);

                        allAssignedSlots.add(slot);
                    }
                }
            }

            // Partition today's slots vs upcoming slots
            List<Map<String, Object>> todaySlots = new ArrayList<>();
            List<Map<String, Object>> upcomingSlots = new ArrayList<>();

            for (Map<String, Object> s : allAssignedSlots) {
                int day = (Integer) s.get("dayOfWeek");
                if (day == currentIsoDay) {
                    todaySlots.add(s);
                } else if (day > currentIsoDay) {
                    upcomingSlots.add(s);
                }
            }

            if (todaySlots.isEmpty() && !allAssignedSlots.isEmpty()) {
                todaySlots.addAll(allAssignedSlots.subList(0, Math.min(2, allAssignedSlots.size())));
            }

            result.put("todaySlots", todaySlots);
            result.put("upcomingSlots", upcomingSlots);
            result.put("allSlots", allAssignedSlots);

            // 2. Quick session counts
            int submittedCount = 0;
            int openCount = 0;
            String statsSql = "SELECT " +
                    "count(CASE WHEN status = 'CLOSED' THEN 1 END)::int as submitted_count, " +
                    "count(CASE WHEN status = 'OPEN' THEN 1 END)::int as open_count " +
                    "FROM acd.attendance_sessions " +
                    "WHERE tenant_id = ? AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(statsSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        submittedCount = rs.getInt("submitted_count");
                        openCount = rs.getInt("open_count");
                    }
                }
            }

            result.put("submittedCount", submittedCount);
            result.put("openCount", openCount);

            // 3. Faculty missed / overdue sessions
            List<Map<String, Object>> missedSessions = new ArrayList<>();
            String missedSql = "SELECT " +
                    "sess.id, " +
                    "sess.session_date, " +
                    "sess.start_time, " +
                    "sess.end_time, " +
                    "sess.status, " +
                    "COALESCE(sub.name, c.name, 'N/A') as subject_name, " +
                    "COALESCE(sub.code, c.code, 'N/A') as subject_code, " +
                    "sec.section_code as section_name, " +
                    "sess.timetable_entry_id " +
                    "FROM acd.attendance_sessions sess " +
                    "JOIN acd.course_sections sec ON sec.id = sess.section_id " +
                    "LEFT JOIN acd.course_offerings o ON o.id = sec.offering_id " +
                    "LEFT JOIN acd.courses c ON c.id = o.course_id " +
                    "LEFT JOIN acd.timetable_entries tte ON tte.id = sess.timetable_entry_id " +
                    "LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id " +
                    "WHERE sess.tenant_id = ? " +
                    "  AND sess.session_date < CURRENT_DATE " +
                    "  AND sess.status = 'OPEN' " +
                    "  AND sess.deleted_at IS NULL " +
                    "ORDER BY sess.session_date DESC " +
                    "LIMIT 5";

            try (PreparedStatement ps = conn.prepareStatement(missedSql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> ms = new LinkedHashMap<>();
                        ms.put("id", rs.getString("id"));
                        ms.put("session_date", rs.getString("session_date"));
                        ms.put("start_time", rs.getString("start_time"));
                        ms.put("end_time", rs.getString("end_time"));
                        ms.put("status", rs.getString("status"));
                        ms.put("subject_name", rs.getString("subject_name"));
                        ms.put("subject_code", rs.getString("subject_code"));
                        ms.put("section_name", rs.getString("section_name"));
                        ms.put("timetable_entry_id", rs.getString("timetable_entry_id"));
                        missedSessions.add(ms);
                    }
                }
            }
            result.put("missedSessions", missedSessions);
            result.put("asOf", java.time.Instant.now().toString());

        } catch (SQLException e) {
            logger.error("Failed to fetch faculty dashboard: {}", e.getMessage(), e);
            throw new RuntimeException("Database error loading faculty dashboard: " + e.getMessage(), e);
        }

        return result;
    }

    @Override
    public Map<String, Object> getHodDashboard(UserSecurityContext context, String departmentId) {
        Map<String, Object> result = getCollegeAdminDashboard(context, null);
        result.put("departmentId", departmentId != null ? departmentId : context.getDepartmentId());
        return result;
    }

    @Override
    public Map<String, Object> getPlatformAdminDashboard(UserSecurityContext context) {
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = connectionManager.getConnection()) {
            List<Map<String, Object>> institutes = new ArrayList<>();
            Map<String, Integer> statusCounts = new LinkedHashMap<>();
            statusCounts.put("ACTIVE", 0);
            statusCounts.put("ONBOARDING", 0);
            statusCounts.put("SUSPENDED", 0);
            statusCounts.put("OFFBOARDED", 0);

            String tenantSql = "SELECT " +
                    "t.id, t.name, t.code, t.status, t.created_at, " +
                    "count(DISTINCT c.id)::int as college_count, " +
                    "count(up.id)::int as user_count " +
                    "FROM core.tenants t " +
                    "LEFT JOIN core.colleges c ON c.tenant_id = t.id AND c.deleted_at IS NULL " +
                    "LEFT JOIN iam.user_profiles up ON up.tenant_id = t.id AND up.deleted_at IS NULL " +
                    "WHERE t.deleted_at IS NULL " +
                    "GROUP BY t.id, t.name, t.code, t.status, t.created_at " +
                    "ORDER BY t.created_at DESC";

            try (PreparedStatement ps = conn.prepareStatement(tenantSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> inst = new LinkedHashMap<>();
                    inst.put("id", rs.getString("id"));
                    inst.put("name", rs.getString("name"));
                    inst.put("code", rs.getString("code"));
                    String status = rs.getString("status") != null ? rs.getString("status").toUpperCase() : "ACTIVE";
                    inst.put("status", status);
                    inst.put("createdAt", rs.getString("created_at"));
                    inst.put("collegeCount", rs.getInt("college_count"));
                    inst.put("userCount", rs.getInt("user_count"));
                    institutes.add(inst);

                    statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
                }
            }

            result.put("institutes", institutes);
            result.put("tenantCount", institutes.size());
            result.put("statusCounts", statusCounts);
            result.put("roleTemplateCount", 30);
            result.put("asOf", java.time.Instant.now().toString());

        } catch (SQLException e) {
            logger.error("Failed to fetch platform admin dashboard: {}", e.getMessage(), e);
            throw new RuntimeException("Database error loading platform admin dashboard: " + e.getMessage(), e);
        }

        return result;
    }
}
