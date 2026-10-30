import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { DashboardClientLayout } from './layout-client';

export const dynamic = 'force-dynamic';

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    if (resolution.reason === 'unauthenticated') {
      redirect('/login');
    }
    redirect(`/access-denied?reason=${resolution.reason}`);
  }

  return (
    <DashboardClientLayout session={resolution.session}>
      {children}
    </DashboardClientLayout>
  );
}
