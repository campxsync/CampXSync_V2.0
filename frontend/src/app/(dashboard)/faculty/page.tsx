import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { FacultyDashboardView } from './faculty-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function FacultyDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (
    !resolution.success ||
    (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')
  ) {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, displayName, userId, collegeId } = resolution.session;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }

  // Compute current date and day of week
  const today = new Date();
  const todayDateStr = today.toISOString().split('T')[0];
  const jsDay = today.getDay(); // 0 is Sunday, 1 is Monday, ...
  const currentIsoDay = jsDay === 0 ? 7 : jsDay;
  const dayNames = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

  let allAssignedSlots: any[] = [];
  let todaySlots: any[] = [];
  let upcomingSlots: any[] = [];
  let missedSessions: any[] = [];
  let submittedCount = 0;
  let openCount = 0;
  let userPreferences = PRODUCT_DEFAULTS;

  try {
    // Cutover: Fetch authoritative metrics via API Gateway backed by Java Reporting & Analytics Service
    const gatewayUrl = new URL('/api/v1/academics/analytics/dashboard', GATEWAY_BASE_URL);
    gatewayUrl.searchParams.set('role', 'FACULTY');

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role === 'faculty' ? 'FACULTY' : 'COLLEGE_ADMIN',
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
      todaySlots = Array.isArray(data.todaySlots) ? data.todaySlots : [];
      upcomingSlots = Array.isArray(data.upcomingSlots) ? data.upcomingSlots : [];
      allAssignedSlots = Array.isArray(data.allSlots) ? data.allSlots : [];
      submittedCount = Number(data.submittedCount) || 0;
      openCount = Number(data.openCount) || 0;
      missedSessions = Array.isArray(data.missedSessions) ? data.missedSessions : [];
    } else {
      console.warn(`Gateway returned status ${gatewayResponse.status} for faculty dashboard`);
    }

    // Load User Preferences via Supabase Auth metadata or client
    const { data: profile } = await supabase
      .from('user_profiles')
      .select('preferences')
      .eq('id', userId)
      .maybeSingle();

    if (profile?.preferences) {
      userPreferences = sanitizePreferences(profile.preferences, 'faculty', PRODUCT_DEFAULTS);
    }
  } catch (err) {
    console.error('Faculty dashboard gateway fetch error:', err);
  }

  return (
    <FacultyDashboardView
      displayName={displayName || 'Faculty Member'}
      userId={userId}
      tenantId={tenantId}
      todayDateStr={todayDateStr}
      dayName={dayNames[currentIsoDay] || 'Today'}
      todaySlots={todaySlots}
      allAssignedSlots={allAssignedSlots}
      upcomingSlots={upcomingSlots}
      missedSessions={missedSessions}
      openCount={openCount}
      submittedCount={submittedCount}
      initialPreferences={userPreferences}
    />
  );
}
