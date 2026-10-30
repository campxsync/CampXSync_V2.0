import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { HomeLanding } from '@/components/landing/home-landing';

export const dynamic = 'force-dynamic';

export default async function HomePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    if (resolution.reason === 'unauthenticated') {
      return <HomeLanding />;
    }
    redirect(`/access-denied?reason=${resolution.reason}`);
  }

  const { role } = resolution.session;

  switch (role) {
    case 'platform_admin':
      redirect('/platform');
    case 'college_admin':
      redirect('/college');
    case 'faculty':
      redirect('/faculty');
    case 'student':
      redirect('/student');
    default:
      redirect('/access-denied?reason=no_role_assigned');
  }
}
