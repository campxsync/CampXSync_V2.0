import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { PlatformDashboardView } from './platform-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

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
  let roleTemplateCount = 30;
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
    const gatewayUrl = new URL('/api/v1/academics/analytics/dashboard', GATEWAY_BASE_URL);
    gatewayUrl.searchParams.set('role', 'PLATFORM_ADMIN');

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-User-Role': 'PLATFORM_ADMIN',
    };

    const res = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (res.ok) {
      const data = await res.json();
      institutes = Array.isArray(data.institutes) ? data.institutes : [];
      tenantCount = Number(data.tenantCount) || institutes.length;
      roleTemplateCount = Number(data.roleTemplateCount) || 30;
      if (data.statusCounts) {
        Object.assign(statusCounts, data.statusCounts);
      }
      totalUserCount = institutes.reduce((acc: number, curr: any) => acc + (Number(curr.userCount || curr.user_count) || 0), 0);
    }

    // Load preferences via Gateway
    const prefUrl = new URL('/api/v1/user/preferences?role=platform_admin', GATEWAY_BASE_URL);
    const prefRes = await fetch(prefUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    if (prefRes.ok) {
      const prefData = await prefRes.json();
      if (prefData.preferences) {
        userPreferences = sanitizePreferences(prefData.preferences, 'platform_admin', PRODUCT_DEFAULTS);
      }
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
