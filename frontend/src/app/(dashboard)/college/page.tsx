import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { CollegeDashboardView } from './college-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function CollegeAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, tenantName, userId, collegeId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  let studentCount = 0;
  let facultyCount = 0;
  let sessionCount = 0;
  let totalRecords = 0;
  let presentRecords = 0;
  let shortageCount = 0;
  let overallPercentage = 0;

  let trend: any[] = [];
  let batchBreakdown: any[] = [];
  let shortageStudents: any[] = [];
  let missedSessions: any[] = [];
  let recentSessions: any[] = [];
  let userPreferences = PRODUCT_DEFAULTS;

  try {
    // Cutover: Fetch authoritative metrics via API Gateway backed by Java Reporting & Analytics Service
    const gatewayUrl = new URL('/api/v1/academics/analytics/dashboard', GATEWAY_BASE_URL);
    gatewayUrl.searchParams.set('role', 'COLLEGE_ADMIN');

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': 'COLLEGE_ADMIN',
    };
    if (collegeId) {
      headers['X-College-Id'] = collegeId;
    }

    const gatewayResponse = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (gatewayResponse.ok) {
      const data = await gatewayResponse.json();
      if (data.kpis) {
        studentCount = Number(data.kpis.studentCount) || 0;
        facultyCount = Number(data.kpis.facultyCount) || 0;
        sessionCount = Number(data.kpis.sessionCount) || 0;
        totalRecords = Number(data.kpis.totalRecords) || 0;
        presentRecords = Number(data.kpis.presentRecords) || 0;
        overallPercentage = Number(data.kpis.overallPercentage) || 0;
      }
      shortageStudents = Array.isArray(data.shortageStudents) ? data.shortageStudents : [];
      shortageCount = Number(data.shortageCount) || shortageStudents.length;
      trend = Array.isArray(data.trend) ? data.trend : [];
      batchBreakdown = Array.isArray(data.batchBreakdown) ? data.batchBreakdown : [];
      missedSessions = Array.isArray(data.missedSessions) ? data.missedSessions : [];
      recentSessions = Array.isArray(data.recentSessions) ? data.recentSessions : [];
    } else {
      console.warn(`Gateway returned status ${gatewayResponse.status} for college admin dashboard`);
    }

    // Load User Preferences via Supabase Auth metadata or client
    const { data: profile } = await supabase
      .from('user_profiles')
      .select('preferences')
      .eq('id', userId)
      .maybeSingle();

    if (profile?.preferences) {
      userPreferences = sanitizePreferences(profile.preferences, 'college_admin', PRODUCT_DEFAULTS);
    }
  } catch (err) {
    console.error('College admin dashboard gateway fetch error:', err);
  }

  return (
    <CollegeDashboardView
      stats={{
        studentCount,
        facultyCount,
        sessionCount,
        totalRecords,
        presentRecords,
        overallAttendancePercentage: overallPercentage,
        shortageCount,
      }}
      trend={trend}
      batchBreakdown={batchBreakdown}
      shortageStudents={shortageStudents}
      missedSessions={missedSessions}
      recentSessions={recentSessions}
      tenantName={tenantName || 'Institutional Workspace'}
      userId={userId}
      tenantId={tenantId}
      initialPreferences={userPreferences}
    />
  );
}
