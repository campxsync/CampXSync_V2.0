import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { CommunicationsManager } from './communications-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeCommunicationsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') redirect('/access-denied?reason=forbidden');
  const tenantId = resolution.session.tenantId;
  const collegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');
  const colleges = await query(`SELECT id, code, name FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR id = $2) ORDER BY code`, [tenantId, collegeId]);
  const faculty = await query(
    `SELECT e.id, e.employee_no FROM hrm.employees e WHERE e.tenant_id = $1 AND e.deleted_at IS NULL AND e.person_id IS NOT NULL ORDER BY e.employee_no LIMIT 1`,
    [tenantId]
  );
  const messages = await query(
    `SELECT id, subject, channel, status FROM cam.messages WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY created_at DESC`,
    [tenantId]
  );
  return <CommunicationsManager colleges={colleges.rows} messages={messages.rows} recipientId={faculty.rows[0]?.id || ''} />;
}
