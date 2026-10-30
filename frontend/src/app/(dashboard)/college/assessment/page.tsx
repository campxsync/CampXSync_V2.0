import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { AssessmentManager, AssessmentScheme, SubjectOption, VersionOption } from './assessment-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeAssessmentPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  const assignedCollegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  let subjects: SubjectOption[] = [];
  let versions: VersionOption[] = [];
  let schemes: AssessmentScheme[] = [];

  try {
    const subjectRes = await query(
      `SELECT id, code, name FROM acd.subjects
       WHERE tenant_id = $1 AND deleted_at IS NULL
       ORDER BY code`,
      [tenantId]
    );
    subjects = subjectRes.rows;

    const versionRes = await query(
      `SELECT v.id, c.code AS curriculum_code, v.version_no, s.id AS subject_id, s.code AS subject_code
       FROM acd.curriculum_versions v
       JOIN acd.curricula c ON c.id = v.curriculum_id AND c.tenant_id = v.tenant_id AND c.deleted_at IS NULL
       JOIN acd.programs p ON p.id = c.program_id AND p.tenant_id = c.tenant_id
       JOIN acd.curriculum_subjects cs ON cs.curriculum_version_id = v.id AND cs.tenant_id = v.tenant_id AND cs.deleted_at IS NULL
       JOIN acd.subjects s ON s.id = cs.subject_id AND s.tenant_id = cs.tenant_id
       WHERE v.tenant_id = $1 AND v.deleted_at IS NULL AND v.status = 'ACTIVE'
         AND ($2::uuid IS NULL OR p.college_id = $2)
       ORDER BY c.code, s.code`,
      [tenantId, assignedCollegeId]
    );
    versions = versionRes.rows.map((row) => ({
      id: row.id,
      subjectId: row.subject_id,
      label: `${row.curriculum_code} v${row.version_no} · ${row.subject_code}`,
    }));

    const schemeRes = await query(
      `SELECT s.id, s.code, s.name, s.status, s.total_marks, s.pass_marks, sub.code AS subject_code, sub.name AS subject_name
       FROM acd.assessment_structures s
       LEFT JOIN acd.subjects sub ON sub.id = s.subject_id AND sub.tenant_id = s.tenant_id
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
       ORDER BY s.created_at DESC`,
      [tenantId]
    );
    const schemeIds = schemeRes.rows.map((row) => row.id);
    const componentsByScheme = new Map<string, AssessmentScheme['components']>();
    if (schemeIds.length > 0) {
      const componentRes = await query(
        `SELECT structure_id, code, name, component_type, max_marks, weightage_pct, is_external
         FROM acd.assessment_components
         WHERE tenant_id = $1 AND deleted_at IS NULL AND structure_id = ANY($2::uuid[])
         ORDER BY sequence`,
        [tenantId, schemeIds]
      );
      for (const row of componentRes.rows) {
        const list = componentsByScheme.get(row.structure_id) || [];
        list.push({
          code: row.code,
          name: row.name,
          componentType: row.component_type,
          maxMarks: Number(row.max_marks),
          weightagePct: row.weightage_pct === null ? null : Number(row.weightage_pct),
          isExternal: row.is_external,
        });
        componentsByScheme.set(row.structure_id, list);
      }
    }
    schemes = schemeRes.rows.map((row) => ({
      id: row.id,
      code: row.code,
      name: row.name,
      status: row.status,
      totalMarks: Number(row.total_marks),
      passMarks: row.pass_marks === null ? null : Number(row.pass_marks),
      subjectCode: row.subject_code,
      subjectName: row.subject_name,
      components: componentsByScheme.get(row.id) || [],
    }));
  } catch (err) {
    console.error('Error loading assessment schemes:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Assessment</h1>
        <p className="mt-1 text-xs text-[#6B7688]">
          Map the internal and end-semester components of a subject. Activating a scheme fixes those components. Marks and results are not entered here.
        </p>
      </div>
      <AssessmentManager subjects={subjects} versions={versions} schemes={schemes} />
    </div>
  );
}
