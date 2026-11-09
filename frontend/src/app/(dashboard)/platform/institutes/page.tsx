import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { InstitutesRegistryClient, InstituteRecord } from './institutes-registry-client';

export const dynamic = 'force-dynamic';

export default async function InstitutesRegistryPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let institutes: InstituteRecord[] = [];
  try {
    const res = await query(`
      SELECT 
        t.id, 
        t.name, 
        t.code, 
        t.status, 
        t.created_at, 
        t.updated_at,
        count(DISTINCT c.id)::int as college_count,
        count(up.id)::int as user_count
      FROM core.tenants t
      LEFT JOIN core.colleges c ON c.tenant_id = t.id AND c.deleted_at IS NULL
      LEFT JOIN iam.user_profiles up ON up.tenant_id = t.id AND up.deleted_at IS NULL
      WHERE t.deleted_at IS NULL
      GROUP BY t.id, t.name, t.code, t.status, t.created_at, t.updated_at
      ORDER BY t.created_at DESC
    `);
    institutes = res.rows || [];
  } catch (err) {
    console.error('Failed to load institutes:', err);
  }

  return <InstitutesRegistryClient initialInstitutes={institutes} />;
}
