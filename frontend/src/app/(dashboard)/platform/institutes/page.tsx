import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { InstitutesRegistryClient, InstituteRecord } from './institutes-registry-client';
import { fetchFromGateway } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

export default async function InstitutesRegistryPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let institutes: InstituteRecord[] = [];
  try {
    const res = await fetchFromGateway('/api/v1/academics/analytics/dashboard?role=platform_admin', {
      method: 'GET',
    });

    if (res.ok) {
      const data = await res.json();
      institutes = Array.isArray(data.institutes) ? data.institutes : [];
    }
  } catch (err) {
    console.error('Failed to load institutes via API gateway:', err);
  }

  return <InstitutesRegistryClient initialInstitutes={institutes} />;
}
