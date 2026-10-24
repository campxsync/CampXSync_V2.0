import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { AttendanceManager } from './attendance-manager';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function CollegeAttendancePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  let sessions: any[] = [];

  try {
    const gatewayUrl = new URL('/api/v1/academics/attendance/sessions', GATEWAY_BASE_URL);
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': 'COLLEGE_ADMIN',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const res = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (res.ok) {
      const data = await res.json();
      const rawList = Array.isArray(data.data) ? data.data : (Array.isArray(data) ? data : []);
      sessions = rawList.map((s: any) => ({
        id: s.id,
        session_date: s.sessionDate || s.attendanceDate || s.session_date,
        start_time: s.startTime || s.start_time,
        end_time: s.endTime || s.end_time,
        status: s.status,
        locked_at: s.lockedAt || s.locked_at,
        subject_name: s.subjectName || s.subject_name || 'Class Session',
        subject_code: s.subjectCode || s.subject_code || 'GEN',
        section_code: s.sectionCode || s.section_code || s.sectionName || 'Section A',
        faculty_name: s.facultyName || s.faculty_name || 'Assigned Instructor',
        total_records: Number(s.totalRecords || s.total_records || s.totalStudentsMarked || s.total_students_marked) || 0,
        present_count: Number(s.presentCount || s.present_count) || 0,
      }));
    }
  } catch (err) {
    console.error('Error querying attendance sessions via API gateway:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Institutional Attendance Oversight
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Monitor class attendance submissions, track shortage metrics, and inspect audited attendance records.
        </p>
      </div>

      <AttendanceManager sessions={sessions} />
    </div>
  );
}
