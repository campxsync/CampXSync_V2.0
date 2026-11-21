import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway } from '@/lib/api/gateway-client';
import { IdentityAccessClient, QuorumRequest, BreakGlassRequest, BreakGlassSession } from './identity-access-client';

export const dynamic = 'force-dynamic';

export default async function PlatformIdentityPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let quorum: QuorumRequest[] = [];
  let breakGlassRequests: BreakGlassRequest[] = [];
  let breakGlassSessions: BreakGlassSession[] = [];
  let loadError: string | null = null;

  try {
    const [quorumRes, bgRes] = await Promise.all([
      fetchFromGateway('/api/v1/iadm/03/institution-admin-quorum-requests', { method: 'GET' }),
      fetchFromGateway('/api/v1/iadm/03/break-glass', { method: 'GET' }),
    ]);

    if (quorumRes.ok) {
      const data = await quorumRes.json();
      quorum = Array.isArray(data.requests)
        ? data.requests
        : Array.isArray(data.items)
          ? data.items
          : Array.isArray(data)
            ? data
            : [];
    } else {
      loadError = `Quorum list failed (${quorumRes.status})`;
    }

    if (bgRes.ok) {
      const data = await bgRes.json();
      breakGlassRequests = Array.isArray(data.requests) ? data.requests : [];
      breakGlassSessions = Array.isArray(data.sessions) ? data.sessions : [];
    } else {
      loadError = (loadError ? `${loadError}; ` : '') + `Break-glass list failed (${bgRes.status})`;
    }
  } catch (err) {
    loadError = err instanceof Error ? err.message : 'Failed to load identity desk';
  }

  return (
    <IdentityAccessClient
      initialQuorum={quorum}
      initialBreakGlassRequests={breakGlassRequests}
      initialBreakGlassSessions={breakGlassSessions}
      loadError={loadError}
    />
  );
}
