import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { CollegeOption, ReadingRecord, ReadingsManager, SubjectOption } from './readings-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeReadingsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const tenantId = resolution.session.tenantId;
  const assignedCollegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  let colleges: CollegeOption[] = [];
  let subjects: SubjectOption[] = [];
  let readings: ReadingRecord[] = [];

  try {
    const collegeRes = await query(
      `SELECT id, name, code FROM core.colleges
       WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR id = $2)
       ORDER BY name`,
      [tenantId, assignedCollegeId]
    );
    colleges = collegeRes.rows;

    const subjectRes = await query(
      `SELECT id, code, name FROM acd.subjects
       WHERE tenant_id = $1 AND deleted_at IS NULL
       ORDER BY code`,
      [tenantId]
    );
    subjects = subjectRes.rows;

    const readingRes = await query(
      `SELECT r.id, r.title, r.resource_type, r.external_url, r.author, r.status,
              s.code AS subject_code, s.name AS subject_name
       FROM lms.resources r
       LEFT JOIN lms.resource_associations a
         ON a.resource_id = r.id AND a.tenant_id = r.tenant_id AND a.entity_type = 'SUBJECT'
       LEFT JOIN acd.subjects s ON s.id = a.entity_id AND s.tenant_id = r.tenant_id
       WHERE r.tenant_id = $1 AND r.deleted_at IS NULL
         AND ($2::uuid IS NULL OR r.college_id = $2 OR r.college_id IS NULL)
       ORDER BY r.created_at DESC`,
      [tenantId, assignedCollegeId]
    );

    const grouped = new Map<string, ReadingRecord>();
    for (const row of readingRes.rows) {
      const existing = grouped.get(row.id);
      const subjectLabel = row.subject_code ? `${row.subject_code} ${row.subject_name}` : null;
      if (existing) {
        if (subjectLabel && !existing.subjects.includes(subjectLabel)) existing.subjects.push(subjectLabel);
        continue;
      }
      grouped.set(row.id, {
        id: row.id,
        title: row.title,
        resourceType: row.resource_type,
        externalUrl: row.external_url,
        author: row.author,
        status: row.status,
        subjects: subjectLabel ? [subjectLabel] : [],
      });
    }
    readings = Array.from(grouped.values());
  } catch (err) {
    console.error('Error loading readings:', err);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">Readings</h1>
        <p className="mt-1 text-xs text-[#6B7688]">
          Record a reading for a subject by its address. Publishing makes that reading part of the subject record.
        </p>
      </div>
      <ReadingsManager colleges={colleges} subjects={subjects} readings={readings} />
    </div>
  );
}
