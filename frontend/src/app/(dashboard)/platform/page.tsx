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
    OFFBOARDED: 0,
  };

  try {
    const res = await fetchFromGateway('/api/v1/academics/analytics/dashboard?role=platform_admin', {
      method: 'GET',
    });

    if (res.ok) {
      const data = await res.json();
      institutes = Array.isArray(data.institutes) ? data.institutes : [];
      tenantCount = Number(data.tenantCount) || institutes.length;
      roleTemplateCount = Number(data.roleTemplateCount) || 0;
      if (data.statusCounts) {
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
