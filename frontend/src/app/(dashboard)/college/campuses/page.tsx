import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { CampusesManager } from './campuses-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeCampusesPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') redirect('/access-denied?reason=forbidden');
  const tenantId = resolution.session.tenantId;
  const collegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  const colleges = await query(
    `SELECT id, code, name, affiliating_university FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR id = $2) ORDER BY code`,
    [tenantId, collegeId]
  );
  const campuses = await query(
    `SELECT c.id, c.code, c.name, c.status, c.is_primary, col.code AS college_code
     FROM core.campuses c JOIN core.colleges col ON col.id = c.college_id
     WHERE c.tenant_id = $1 AND c.deleted_at IS NULL ORDER BY c.code`,
    [tenantId]
  );
  return <CampusesManager colleges={colleges.rows} campuses={campuses.rows} />;
}
