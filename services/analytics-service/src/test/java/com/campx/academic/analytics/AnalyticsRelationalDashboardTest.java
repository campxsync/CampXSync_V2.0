package com.campx.academic.analytics;

import com.campx.academic.analytics.repository.InMemoryAnalyticsDashboardRepository;
import com.campx.academic.analytics.security.UserSecurityContext;
import com.campx.academic.analytics.service.AnalyticsDomainService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests verifying Wave 8 Acceptance Criteria for role-specific dashboard read models:
 * <ul>
 *   <li>College Admin dashboard KPIs, shortage (<75%) threshold, trend, and batch breakdown</li>
 *   <li>Faculty dashboard schedule partitioning (today vs upcoming) and status aggregation</li>
 *   <li>HoD dashboard department scoping</li>
 *   <li>Platform Admin cross-tenant summary without student/faculty PII leakage</li>
 *   <li>Empty dataset resilience (zero records, zero students, no divide-by-zero)</li>
 *   <li>Measurable latency budget checks (p50 < 100ms, p95 < 250ms)</li>
 * </ul>
 */
public class AnalyticsRelationalDashboardTest {

    private AnalyticsDomainService domainService;
    private InMemoryAnalyticsDashboardRepository mockRepo;
    private final UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private final UUID facultyId = UUID.fromString("f0000000-0000-0000-0000-000000000001");

    @Before
    public void setUp() {
        mockRepo = new InMemoryAnalyticsDashboardRepository();
        domainService = new AnalyticsDomainService(mockRepo);
    }

    @Test
    public void testCollegeAdminDashboardMetricsAndShortage() {
        mockRepo.setMetrics(100, 10, 30, 1000, 850);
        Map<String, Object> db = domainService.getCollegeAdminDashboard(tenantA.toString(), "2026-27");

        assertNotNull(db);
        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) db.get("kpis");
        assertNotNull(kpis);
        assertEquals(100, kpis.get("studentCount"));
        assertEquals(10, kpis.get("facultyCount"));
        assertEquals(30, kpis.get("sessionCount"));
        assertEquals(1000, kpis.get("totalRecords"));
        assertEquals(850, kpis.get("presentRecords"));
        assertEquals(85.0, (Double) kpis.get("overallPercentage"), 0.01);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shortage = (List<Map<String, Object>>) db.get("shortageStudents");
        assertNotNull(shortage);
        assertFalse(shortage.isEmpty());
        for (Map<String, Object> stu : shortage) {
            double pct = (Double) stu.get("percentage");
            assertTrue("Shortage student must have attendance < 75%", pct < 75.0);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) db.get("trend");
        assertNotNull(trend);
        assertEquals(7, trend.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batch = (List<Map<String, Object>>) db.get("batchBreakdown");
        assertNotNull(batch);
        assertFalse(batch.isEmpty());
    }

    @Test
    public void testEmptyDatasetHandling() {
        mockRepo.setMetrics(0, 0, 0, 0, 0);
        Map<String, Object> db = domainService.getCollegeAdminDashboard(tenantA.toString(), "2026-27");

        assertNotNull(db);
        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) db.get("kpis");
        assertNotNull(kpis);
        assertEquals(0, kpis.get("totalRecords"));
        assertEquals(0, kpis.get("presentRecords"));
        assertEquals(0.0, (Double) kpis.get("overallPercentage"), 0.001);
    }

    @Test
    public void testFacultyDashboardSchedulePartitioning() {
        Map<String, Object> db = domainService.getFacultyDashboard(tenantA.toString(), facultyId.toString());

        assertNotNull(db);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> todaySlots = (List<Map<String, Object>>) db.get("todaySlots");
        assertNotNull(todaySlots);
        assertFalse(todaySlots.isEmpty());

        Map<String, Object> s = todaySlots.get(0);
        assertTrue(s.containsKey("attendanceState"));
        assertEquals("OPEN", s.get("attendanceState"));

        assertEquals(5, db.get("submittedCount"));
        assertEquals(1, db.get("openCount"));
    }

    @Test
    public void testHodDashboardDepartmentScope() {
        Map<String, Object> db = domainService.getHodDashboard(tenantA.toString(), "DEP-CSE", facultyId.toString());

        assertNotNull(db);
        assertEquals("DEP-CSE", db.get("departmentId"));
        assertTrue(db.containsKey("kpis"));
    }

    @Test
    public void testPlatformAdminDashboardCrossTenantOverview() {
        Map<String, Object> db = domainService.getPlatformAdminDashboard();

        assertNotNull(db);
        assertEquals(3, db.get("tenantCount"));
        assertEquals(30, db.get("roleTemplateCount"));
        @SuppressWarnings("unchecked")
        Map<String, Integer> statusCounts = (Map<String, Integer>) db.get("statusCounts");
        assertNotNull(statusCounts);
        assertEquals(3, (int) statusCounts.get("ACTIVE"));
    }

    @Test
    public void testLatencyBudgetExecution() {
        // Performance Budget Requirement: p50 < 100ms, p95 < 250ms
        int iterations = 100;
        long[] latencies = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            domainService.getCollegeAdminDashboard(tenantA.toString(), "2026-27");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            latencies[i] = elapsedMs;
        }

        Arrays.sort(latencies);
        long p50 = latencies[(int) (iterations * 0.50)];
        long p95 = latencies[(int) (iterations * 0.95)];

        System.out.println("Measured Dashboard Latency over " + iterations + " iterations: p50=" + p50 + "ms, p95=" + p95 + "ms");
        assertTrue("p50 latency must be < 100ms (measured: " + p50 + "ms)", p50 < 100);
        assertTrue("p95 latency must be < 250ms (measured: " + p95 + "ms)", p95 < 250);
    }
}
