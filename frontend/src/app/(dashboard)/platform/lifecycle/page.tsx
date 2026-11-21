import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway } from '@/lib/api/gateway-client';
import { LifecycleInboxClient, LifecycleRequest } from './lifecycle-inbox-client';

export const dynamic = 'force-dynamic';

export default async function PlatformLifecyclePage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let requests: LifecycleRequest[] = [];
  let loadError: string | null = null;
  try {
    const res = await fetchFromGateway('/api/v1/iadm/01/lifecycle-requests?status=PENDING', { method: 'GET' });
    if (res.ok) {
      const data = await res.json();
      requests = Array.isArray(data.requests) ? data.requests : [];
    } else {
      loadError = `Lifecycle list failed (${res.status})`;
    }
  } catch (e) {
    loadError = e instanceof Error ? e.message : 'Load failed';
  }

  return <LifecycleInboxClient initial={requests} loadError={loadError} />;
}
