import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { FacultyAttendanceView } from './faculty-attendance-view';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

interface PageProps {
  searchParams?: Promise<{
    slotId?: string;
    sessionId?: string;
  }>;
}

export default async function FacultyAttendancePage(props: PageProps) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (
    !resolution.success ||
    (resolution.session.role !== 'faculty' && resolution.session.role !== 'college_admin')
  ) {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  const userId = resolution.session.userId;
  const searchParams = props.searchParams ? await props.searchParams : {};
  const initialSlotId = searchParams.slotId;
  const initialSessionId = searchParams.sessionId;

  let slots: any[] = [];
  let students: any[] = [];
  let employeeId: string | undefined = userId;

  try {
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role === 'faculty' ? 'FACULTY' : 'COLLEGE_ADMIN',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    // 1. Fetch faculty timetable slots via ACD-10 Reporting & Analytics Service
    const scheduleUrl = new URL('/api/v1/academics/analytics/dashboard', GATEWAY_BASE_URL);
    scheduleUrl.searchParams.set('role', 'FACULTY');

    const scheduleRes = await fetch(scheduleUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (scheduleRes.ok) {
      const data = await scheduleRes.json();
      const rawSlots = Array.isArray(data.allSlots)
        ? data.allSlots
        : (Array.isArray(data.todaySlots) ? data.todaySlots : []);

      slots = rawSlots.map((s: any) => ({
        id: s.id,
        day_of_week: s.dayOfWeek ?? s.day_of_week ?? 1,
        start_time: s.startTime || s.start_time || '09:00:00',
        end_time: s.endTime || s.end_time || '10:00:00',
        subject_name: s.subjectName || s.subject_name || 'Academic Class',
        subject_code: s.subjectCode || s.subject_code || 'SUBJ',
        section_id: s.sectionId || s.section_id || '00000000-0000-0000-0000-000000000000',
        section_code: s.sectionName || s.section_code || 'A',
        employee_id: s.employeeId || s.employee_id || userId,
      }));

      if (slots.length > 0 && slots[0].employee_id) {
        employeeId = slots[0].employee_id;
      }
    }

    // 2. Fetch student roster via STU-01 Student Domain Service
    const stuUrl = new URL('/api/v1/students', GATEWAY_BASE_URL);
    const stuRes = await fetch(stuUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (stuRes.ok) {
      const data = await stuRes.json();
      const items = Array.isArray(data.items) ? data.items : [];
      students = items.map((s: any) => ({
        id: s.id,
        enrollment_no: s.enrollmentNo || s.enrollment_no || 'STU-000',
        display_name: s.displayName || s.display_name || s.enrollmentNo || 'Unnamed Student',
        primary_email: s.primaryEmail || s.primary_email || '',
      }));
    }
  } catch (err) {
    console.error('Error fetching faculty timetable slots or students via gateway:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Faculty Attendance Capture
        </h1>
        <p className="text-xs text-[#475569] mt-1">
          Select your class period, take student attendance, and submit finalized sessions to the institutional audit trail.
        </p>
      </div>

      <FacultyAttendanceView 
        slots={slots} 
        students={students} 
        employeeId={employeeId} 
        initialSlotId={initialSlotId}
        initialSessionId={initialSessionId}
      />
    </div>
  );
}
