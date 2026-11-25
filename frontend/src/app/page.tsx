import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

export const dynamic = 'force-dynamic';

export default async function HomePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    if (resolution.reason === 'unauthenticated') {
      redirect('/login');
    }
    redirect(`/access-denied?reason=${resolution.reason}`);
  }

  const { role } = resolution.session;

  switch (role) {
    case 'platform_admin':
      redirect('/platform');
    case 'system_admin':
      redirect('/platform/identity/technical');
    case 'auditor':
      redirect('/platform/identity/evidence');
    case 'institution_admin':
      redirect('/institution');
    case 'college_admin':
      redirect('/college');
    case 'registrar':
      redirect('/college/lifecycle');
    case 'faculty':
      redirect('/faculty');
    case 'student':
      redirect('/student');
    default:
      redirect('/access-denied?reason=no_role_assigned');
  }
}
