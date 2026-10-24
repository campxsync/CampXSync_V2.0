package com.campx.academic.analytics.repository;

import com.campx.academic.analytics.security.UserSecurityContext;

import java.time.LocalDate;
import java.util.*;

/**
 * In-memory fallback implementation of AnalyticsDashboardRepository for unit testing.
 */
public class InMemoryAnalyticsDashboardRepository implements AnalyticsDashboardRepository {

    private int studentCount = 120;
    private int facultyCount = 15;
    private int sessionCount = 45;
    private int totalRecords = 1200;
    private int presentRecords = 984;

    public InMemoryAnalyticsDashboardRepository() {}

    public void setMetrics(int studentCount, int facultyCount, int sessionCount, int totalRecords, int presentRecords) {
        this.studentCount = studentCount;
        this.facultyCount = facultyCount;
        this.sessionCount = sessionCount;
        this.totalRecords = totalRecords;
        this.presentRecords = presentRecords;
    }

    @Override
    public Map<String, Object> getCollegeAdminDashboard(UserSecurityContext context, String period) {
        Map<String, Object> result = new LinkedHashMap<>();

        double overallPercentage = totalRecords > 0 ? Math.round(((double) presentRecords / totalRecords) * 1000.0) / 10.0 : 0.0;

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("studentCount", studentCount);
        kpis.put("facultyCount", facultyCount);
        kpis.put("sessionCount", sessionCount);
        kpis.put("totalRecords", totalRecords);
        kpis.put("presentRecords", presentRecords);
        kpis.put("overallPercentage", overallPercentage);
        result.put("kpis", kpis);

        // Shortage students (< 75%)
        List<Map<String, Object>> shortage = new ArrayList<>();
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("studentId", "STU-003");
        s1.put("enrollmentNo", "ENR-2026-003");
        s1.put("displayName", "Charlie Student");
        s1.put("totalSessions", 10);
        s1.put("attended", 6);
        s1.put("percentage", 60.0);
        shortage.add(s1);
        result.put("shortageStudents", shortage);
        result.put("shortageCount", shortage.size());

        // Trend
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("dateStr", d.toString());
            day.put("displayDate", d.getDayOfMonth() + " " + d.getMonth().name().substring(0, 3));
            day.put("totalRecords", 150);
            day.put("presentRecords", 125);
            day.put("percentage", 83.3);
            trend.add(day);
        }
        result.put("trend", trend);

        // Batch breakdown
        List<Map<String, Object>> batchBreakdown = new ArrayList<>();
        Map<String, Object> b1 = new LinkedHashMap<>();
        b1.put("sectionCode", "SEC-A");
        b1.put("subjectCode", "CS501");
        b1.put("subjectName", "Operating Systems");
        b1.put("sessionsHeld", 12);
        b1.put("totalRecords", 360);
        b1.put("presentRecords", 300);
        b1.put("percentage", 83.3);
        batchBreakdown.add(b1);
        result.put("batchBreakdown", batchBreakdown);

        // Missed sessions
        result.put("missedSessions", Collections.emptyList());
        result.put("recentSessions", Collections.emptyList());
        result.put("period", period != null ? period : "2026-27");
        result.put("asOf", java.time.Instant.now().toString());

        return result;
    }

    @Override
    public Map<String, Object> getFacultyDashboard(UserSecurityContext context) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> slots = new ArrayList<>();
        Map<String, Object> slot1 = new LinkedHashMap<>();
        slot1.put("id", UUID.randomUUID().toString());
        slot1.put("dayOfWeek", LocalDate.now().getDayOfWeek().getValue());
        slot1.put("startTime", "09:00");
        slot1.put("endTime", "10:00");
        slot1.put("subjectName", "Computer Networks");
        slot1.put("subjectCode", "CS601");
        slot1.put("sectionId", UUID.randomUUID().toString());
        slot1.put("sectionName", "SEC-A");
        slot1.put("todaySessionId", null);
        slot1.put("todaySessionStatus", "OPEN");
        slot1.put("attendanceState", "OPEN");
        slots.add(slot1);

        result.put("todaySlots", slots);
        result.put("upcomingSlots", Collections.emptyList());
        result.put("allSlots", slots);
        result.put("submittedCount", 5);
        result.put("openCount", 1);
        result.put("asOf", java.time.Instant.now().toString());

        return result;
    }

    @Override
    public Map<String, Object> getHodDashboard(UserSecurityContext context, String departmentId) {
        Map<String, Object> result = getCollegeAdminDashboard(context, null);
        result.put("departmentId", departmentId != null ? departmentId : "DEP-CS");
        return result;
    }

    @Override
    public Map<String, Object> getPlatformAdminDashboard(UserSecurityContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantCount", 3);
        result.put("institutes", Collections.emptyList());
        Map<String, Integer> statusCounts = new HashMap<>();
        statusCounts.put("ACTIVE", 3);
        result.put("statusCounts", statusCounts);
        result.put("roleTemplateCount", 30);
        result.put("asOf", java.time.Instant.now().toString());
        return result;
    }
}
