import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { EnrollmentClient } from './enrollment-client';

export const dynamic = 'force-dynamic';

export default async function StudentEnrollmentPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'student') {
    redirect('/access-denied?reason=forbidden');
  }

  const { tenantId, userId } = resolution.session;
  if (!tenantId || !userId) {
    redirect('/access-denied?reason=forbidden');
  }

  // Client loads via BFF `/api/student/enrollment` with Bearer JWT (no direct gateway SSR).
  return <EnrollmentClient />;
}
