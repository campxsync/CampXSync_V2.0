package com.campx.academic.analytics.repository;

import com.campx.academic.analytics.security.UserSecurityContext;

import java.util.Map;

/**
 * Repository interface defining role-specific dashboard read models over authoritative data.
 */
public interface AnalyticsDashboardRepository {

    /**
     * Retrieves aggregated metrics, shortage students, 14-day trend, batch breakdown,
     * and missed/open sessions for College Admin view.
     */
    Map<String, Object> getCollegeAdminDashboard(UserSecurityContext context, String period);

    /**
     * Retrieves assigned slots for today, upcoming schedule, and session counts for Faculty view.
     */
    Map<String, Object> getFacultyDashboard(UserSecurityContext context);

    /**
     * Retrieves department-scoped attendance and faculty submission metrics for HoD view.
     */
    Map<String, Object> getHodDashboard(UserSecurityContext context, String departmentId);

    /**
     * Retrieves cross-tenant high-level institution counts for Platform Admin view.
     */
    Map<String, Object> getPlatformAdminDashboard(UserSecurityContext context);
}
