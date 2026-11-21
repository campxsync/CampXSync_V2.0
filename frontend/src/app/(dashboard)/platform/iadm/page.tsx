import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { IadmDemoClient } from './iadm-demo-client';

export const dynamic = 'force-dynamic';

export default async function PlatformIadmDemoPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/api/auth/test-session?role=platform_admin&returnTo=/platform/iadm');
  }

  return <IadmDemoClient />;
}
