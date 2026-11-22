import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { ProfileClient } from './profile-client';

export const dynamic = 'force-dynamic';

/** IADM01-US-018 — institute profile for R03. */
export default async function InstitutionProfilePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    redirect('/login');
  }
  if (resolution.session.role !== 'institution_admin' && resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }
  return <ProfileClient />;
}