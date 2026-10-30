import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { CurriculumManager, CurriculumRecord, ProgramOption, SubjectOption } from './curriculum-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeCurriculumPage() {
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

  let programs: ProgramOption[] = [];
  let subjects: SubjectOption[] = [];
  let curricula: CurriculumRecord[] = [];

  try {
    const programRes = await query(
      `SELECT id, name, code
       FROM acd.programs
       WHERE tenant_id = $1 AND deleted_at IS NULL
         AND ($2::uuid IS NULL OR college_id = $2)
       ORDER BY name`,
      [tenantId, assignedCollegeId]
    );
    programs = programRes.rows;

    const subjectRes = await query(
      `SELECT id, code, name
       FROM acd.subjects
       WHERE tenant_id = $1 AND deleted_at IS NULL
       ORDER BY code`,
      [tenantId]
    );
    subjects = subjectRes.rows;

    const curriculumRes = await query(
      `SELECT
         c.id,
         c.code,
         c.name,
         c.regulation_year,
         c.status,
         p.name AS program_name,
         p.code AS program_code,
         v.id AS version_id,
         v.version_no,
         v.status AS version_status,
         v.effective_from::text AS effective_from,
         v.total_credits
       FROM acd.curricula c
       JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
       LEFT JOIN acd.curriculum_versions v
         ON v.curriculum_id = c.id AND v.tenant_id = c.tenant_id AND v.deleted_at IS NULL
       WHERE c.tenant_id = $1 AND c.deleted_at IS NULL
         AND ($2::uuid IS NULL OR p.college_id = $2)
       ORDER BY c.code, v.version_no DESC`,
      [tenantId, assignedCollegeId]
    );

    const versionIds = curriculumRes.rows.map((row) => row.version_id).filter(Boolean);
    const subjectMap = new Map<string, CurriculumRecord['subjects']>();
    if (versionIds.length > 0) {
      const placedRes = await query(
        `SELECT
           cs.curriculum_version_id,
           cs.term_no,
           cs.category,
           cs.credits,
           s.code AS subject_code,
           s.name AS subject_name
         FROM acd.curriculum_subjects cs
         JOIN acd.subjects s ON s.id = cs.subject_id AND s.tenant_id = cs.tenant_id
         WHERE cs.tenant_id = $1
           AND cs.deleted_at IS NULL
           AND cs.curriculum_version_id = ANY($2::uuid[])
         ORDER BY cs.term_no, s.code`,
        [tenantId, versionIds]
      );
      for (const row of placedRes.rows) {
        const list = subjectMap.get(row.curriculum_version_id) || [];
        list.push({
          termNo: row.term_no,
          category: row.category,
          credits: row.credits === null ? null : Number(row.credits),
          subjectCode: row.subject_code,
          subjectName: row.subject_name,
        });
        subjectMap.set(row.curriculum_version_id, list);
      }
    }

    const seen = new Set<string>();
    for (const row of curriculumRes.rows) {
      if (seen.has(row.id)) continue;
      seen.add(row.id);
      curricula.push({
        id: row.id,
        code: row.code,
        name: row.name,
        regulationYear: row.regulation_year,
        status: row.status,
        programName: row.program_name,
        programCode: row.program_code,
        versionId: row.version_id,
        versionNo: row.version_no,
        versionStatus: row.version_status,
        effectiveFrom: row.effective_from,
        totalCredits: row.total_credits === null ? null : Number(row.total_credits),
        subjects: row.version_id ? subjectMap.get(row.version_id) || [] : [],
      });
    }
  } catch (err) {
    console.error('Error loading curricula:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
          Curriculum
        </h1>
        <p className="text-xs text-[#6B7688] mt-1">
          Define the semester subject structure and credit scheme for a degree program. Publishing a version locks it.
        </p>
      </div>
      <CurriculumManager programs={programs} subjects={subjects} curricula={curricula} />
    </div>
  );
}
