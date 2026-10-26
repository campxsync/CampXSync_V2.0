import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { TimetableGrid } from './timetable-grid';
import { query } from '@/lib/db';

export const dynamic = 'force-dynamic';

export default async function CollegeTimetablePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  const assignedCollegeId = resolution.session.assignedCollegeId;
  if (!tenantId) {
    redirect('/access-denied?reason=forbidden');
  }
  let entries: any[] = [];
  let subjects: any[] = [];
  let faculty: any[] = [];
  let sections: any[] = [];
  let activeVersionId: string | undefined;
  let timetableId: string | undefined;

  try {
    // 1. Fetch active timetable and published version
    const ttRes = await query(`
      SELECT id, name, status 
      FROM acd.timetables 
      WHERE tenant_id = $1 AND deleted_at IS NULL
        AND ($2::uuid IS NULL OR college_id = $2)
      LIMIT 1
    `, [tenantId, assignedCollegeId]);

    if (ttRes.rows.length > 0) {
      timetableId = ttRes.rows[0].id;
      const verRes = await query(`
        SELECT id, version_no, status 
        FROM acd.timetable_versions 
        WHERE timetable_id = $1 AND deleted_at IS NULL 
        ORDER BY version_no DESC 
        LIMIT 1
      `, [timetableId]);

      if (verRes.rows.length > 0) {
        activeVersionId = verRes.rows[0].id;
      }
    }

    // 2. Query entries if version exists
    if (activeVersionId) {
      const entryRes = await query(`
        SELECT 
          tte.id,
          tte.day_of_week,
          tte.start_time,
          tte.end_time,
          tte.entry_type,
          sub.name as subject_name,
          sub.code as subject_code,
          p.display_name as faculty_name,
          sec.section_code
        FROM acd.timetable_entries tte
        LEFT JOIN acd.subjects sub ON sub.id = tte.subject_id
        LEFT JOIN hrm.employees emp ON emp.id = tte.employee_id
        LEFT JOIN core.persons p ON p.id = emp.person_id
        LEFT JOIN acd.course_sections sec ON sec.id = tte.section_id
        WHERE tte.timetable_version_id = $1 AND tte.tenant_id = $2 AND tte.deleted_at IS NULL
      `, [activeVersionId, tenantId]);

      entries = entryRes.rows.map((e: any) => ({
        id: e.id,
        day_of_week: e.day_of_week,
        start_time: e.start_time,
        end_time: e.end_time,
        entry_type: e.entry_type,
        subject_name: e.subject_name,
        subject_code: e.subject_code,
        faculty_name: e.faculty_name,
        section_code: e.section_code,
      }));
    }

    // 3. Query subjects
    const subRes = await query(`
      SELECT id, name, code 
      FROM acd.subjects 
      WHERE tenant_id = $1 AND deleted_at IS NULL
        AND (
          $2::uuid IS NULL
          OR department_id IN (
            SELECT id FROM core.departments
            WHERE tenant_id = $1 AND college_id = $2 AND deleted_at IS NULL
          )
        )
      ORDER BY name
    `, [tenantId, assignedCollegeId]);
    subjects = subRes.rows;

    // 4. Query faculty
    const facRes = await query(`
      SELECT emp.id, emp.employee_no, p.display_name 
      FROM hrm.employees emp
      LEFT JOIN core.persons p ON p.id = emp.person_id
      WHERE emp.tenant_id = $1 AND emp.deleted_at IS NULL
        AND ($2::uuid IS NULL OR emp.college_id = $2)
      ORDER BY emp.created_at
    `, [tenantId, assignedCollegeId]);
    faculty = facRes.rows.map((f: any) => ({
      id: f.id,
      employee_no: f.employee_no,
      display_name: f.display_name || f.employee_no,
    }));

    // 5. Query sections
    const secRes = await query(`
      SELECT sec.id, sec.section_code, b.name as batch_name 
      FROM acd.course_sections sec
      LEFT JOIN acd.batches b ON b.id = sec.batch_id
      WHERE sec.tenant_id = $1 AND sec.deleted_at IS NULL
        AND ($2::uuid IS NULL OR b.college_id = $2)
    `, [tenantId, assignedCollegeId]);
    sections = secRes.rows.map((s: any) => ({
      id: s.id,
      section_code: s.section_code,
      batch_name: s.batch_name,
    }));
  } catch (err) {
    console.error('Error querying timetable data:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Academic Timetable Master
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Weekly course schedules, section allocations, and instructional faculty slot governance.
        </p>
      </div>

      <TimetableGrid
        timetableId={timetableId}
        timetableVersionId={activeVersionId}
        entries={entries}
        subjects={subjects}
        faculty={faculty}
        sections={sections}
      />
    </div>
  );
}
