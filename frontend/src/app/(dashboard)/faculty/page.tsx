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
      const rawToday = Array.isArray(data.todaySlots) ? data.todaySlots : [];
      const rawUpcoming = Array.isArray(data.upcomingSlots) ? data.upcomingSlots : [];
      const rawAll = Array.isArray(data.allSlots) ? data.allSlots : [];
      const rawMissed = Array.isArray(data.missedSessions) ? data.missedSessions : [];

      const mapSlot = (s: any) => ({
        id: s.id || '',
        day_of_week: s.dayOfWeek ?? s.day_of_week ?? 1,
        dayOfWeek: s.dayOfWeek ?? s.day_of_week ?? 1,
        start_time: s.startTime || s.start_time || '09:00',
        startTime: s.startTime || s.start_time || '09:00',
        end_time: s.endTime || s.end_time || '10:00',
        endTime: s.endTime || s.end_time || '10:00',
        subject_name: s.subjectName || s.subject_name || 'Academic Class',
        subjectName: s.subjectName || s.subject_name || 'Academic Class',
        subject_code: s.subjectCode || s.subject_code || 'GEN',
        subjectCode: s.subjectCode || s.subject_code || 'GEN',
        section_id: s.sectionId || s.section_id || '',
        sectionId: s.sectionId || s.section_id || '',
        section_name: s.sectionName || s.section_name || 'All Sections',
        sectionName: s.sectionName || s.section_name || 'All Sections',
        todaySessionId: s.todaySessionId || s.today_session_id || null,
        todaySessionStatus: s.todaySessionStatus || s.today_session_status || 'DRAFT',
        attendanceState: s.attendanceState || (s.todaySessionStatus === 'CLOSED' || s.today_session_status === 'CLOSED' ? 'SUBMITTED' : s.todaySessionStatus === 'OPEN' || s.today_session_status === 'OPEN' ? 'OPEN' : 'DRAFT'),
      });

      const mapMissedSession = (ms: any) => ({
        id: ms.id || '',
        session_date: ms.sessionDate || ms.session_date || todayDateStr,
        start_time: ms.startTime || ms.start_time || '09:00',
        startTime: ms.startTime || ms.start_time || '09:00',
        end_time: ms.endTime || ms.end_time || '10:00',
        endTime: ms.endTime || ms.end_time || '10:00',
        status: ms.status || 'OPEN',
        subject_name: ms.subjectName || ms.subject_name || 'Academic Class',
        subjectName: ms.subjectName || ms.subject_name || 'Academic Class',
        subject_code: ms.subjectCode || ms.subject_code || 'GEN',
        subjectCode: ms.subjectCode || ms.subject_code || 'GEN',
        section_name: ms.sectionName || ms.section_name || 'Section A',
        sectionName: ms.sectionName || ms.section_name || 'Section A',
        timetable_entry_id: ms.timetableEntryId || ms.timetable_entry_id || ms.id || '',
      });

      todaySlots = rawToday.map(mapSlot);
      upcomingSlots = rawUpcoming.map(mapSlot);
      allAssignedSlots = rawAll.map(mapSlot);
      missedSessions = rawMissed.map(mapMissedSession);
      submittedCount = Number(data.submittedCount) || 0;
      openCount = Number(data.openCount) || 0;
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
