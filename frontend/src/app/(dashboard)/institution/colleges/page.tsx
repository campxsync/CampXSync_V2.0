import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { CollegesClient } from './colleges-client';

export const dynamic = 'force-dynamic';

/** IADM01-US-019 — colleges list for Institution Admin. */
export default async function InstitutionCollegesPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    redirect('/login');
  }
  if (resolution.session.role !== 'institution_admin' && resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }
  return <CollegesClient />;
}