import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { CollegeDetailClient } from './college-detail-client';

export const dynamic = 'force-dynamic';

export default async function CollegeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    redirect('/login');
  }
  if (resolution.session.role !== 'institution_admin' && resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }
  const { id } = await params;
  return <CollegeDetailClient collegeId={id} />;
}