import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { PlatformDashboardView } from './platform-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';
import { fetchFromGateway } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export default async function PlatformAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { userId } = resolution.session;

  // Load aggregate counts via authoritative API Gateway (ACD-10 / Java Reporting & Analytics)
  let tenantCount = 0;
  let totalUserCount = 0;
  let roleTemplateCount = 0;
  let institutes: any[] = [];
  let recentAuditEvents: any[] = [];
  let userPreferences = PRODUCT_DEFAULTS;

  const statusCounts: Record<string, number> = {
    ACTIVE: 0,
    ONBOARDING: 0,
    SUSPENDED: 0,
    OFFBOARDED: 0, // legacy prop name; UI maps ARCHIVED into this slot until dashboard widget rename
    ARCHIVED: 0,
  };
  let iadmSummaryUnavailable = false;

  try {
    // IADM01-US-024: institute lifecycle counters from IADM-01 (never invent zeros on failure)
    try {
      const summaryRes = await fetchFromGateway('/api/v1/iadm/01/institutes/summary', {
        method: 'GET',
        requireAuth: true,
      });
      if (summaryRes.ok) {
        const summary = await summaryRes.json();
        const by = summary.institutesByStatus || {};
        statusCounts.ACTIVE = Number(by.ACTIVE) || 0;
        statusCounts.ONBOARDING = Number(by.ONBOARDING) || 0;
        statusCounts.SUSPENDED = Number(by.SUSPENDED) || 0;
        statusCounts.ARCHIVED = Number(by.ARCHIVED) || 0;
        statusCounts.OFFBOARDED = statusCounts.ARCHIVED;
        tenantCount =
          statusCounts.ACTIVE +
          statusCounts.ONBOARDING +
          statusCounts.SUSPENDED +
          statusCounts.ARCHIVED;
      } else {
        iadmSummaryUnavailable = true;
      }
    } catch {
      iadmSummaryUnavailable = true;
    }

    const res = await fetchFromGateway('/api/v1/academics/analytics/dashboard?role=platform_admin', {
      method: 'GET',
    });

    if (res.ok) {
      const data = await res.json();
      institutes = Array.isArray(data.institutes) ? data.institutes : [];
      if (!tenantCount) {
        tenantCount = Number(data.tenantCount) || institutes.length;
      }
      roleTemplateCount = Number(data.roleTemplateCount) || 0;
      if (!iadmSummaryUnavailable && data.statusCounts && tenantCount === 0) {
        Object.assign(statusCounts, data.statusCounts);
      }
      totalUserCount = institutes.reduce(
        (acc: number, curr: any) => acc + (Number(curr.userCount ?? curr.user_count) || 0),
        0
      );
    }

    // Preferences live on ADM-01; soft-fail if that service is not in the local stack.
    try {
      const prefRes = await fetchFromGateway('/api/v1/user/preferences?role=platform_admin', {
        method: 'GET',
        requireAuth: true,
      });
      if (prefRes.ok) {
        const prefData = await prefRes.json();
        if (prefData.preferences) {
          userPreferences = sanitizePreferences(prefData.preferences, 'platform_admin', PRODUCT_DEFAULTS);
        }
      }
    } catch {
      // ADM-01 optional for H-3 local stack
    }
  } catch (e) {
    console.error('Platform aggregate query failed via gateway:', e);
  }

  return (
    <PlatformDashboardView
      tenantCount={tenantCount}
      totalUserCount={totalUserCount}
      roleTemplateCount={roleTemplateCount}
      statusCounts={statusCounts as any}
      institutes={institutes}
      recentAuditEvents={recentAuditEvents}
      userId={userId}
      initialPreferences={userPreferences}
    />
  );
}
