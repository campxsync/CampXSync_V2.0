import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway } from '@/lib/api/gateway-client';
import { InstituteDetailClient, InstituteDetail } from './institute-detail-client';

export const dynamic = 'force-dynamic';

export default async function InstituteDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { id } = await params;
  let detail: InstituteDetail | null = null;
  let loadError: string | null = null;

  try {
    const res = await fetchFromGateway(`/api/v1/iadm/01/institutes/${id}`, { method: 'GET' });
    if (res.ok) {
      const data = await res.json();
      detail = {
        tenantId: data.tenantId || id,
        code: data.code || '',
        name: data.name || '',
        status: data.status || '',
        createdAt: data.createdAt,
        updatedAt: data.updatedAt,
      };
    } else {
      loadError = `Failed to load institute (${res.status})`;
    }
  } catch (e) {
    loadError = e instanceof Error ? e.message : 'Load failed';
  }

  return <InstituteDetailClient initial={detail} loadError={loadError} />;
}
