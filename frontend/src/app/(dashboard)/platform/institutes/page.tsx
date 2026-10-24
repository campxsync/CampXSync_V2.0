import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { InstitutesRegistryClient, InstituteRecord } from './institutes-registry-client';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export default async function InstitutesRegistryPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { userId } = resolution.session;

  let institutes: InstituteRecord[] = [];
  try {
    const gatewayUrl = new URL('/api/v1/academics/analytics/dashboard', GATEWAY_BASE_URL);
    gatewayUrl.searchParams.set('role', 'PLATFORM_ADMIN');

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': userId || '00000000-0000-0000-0000-000000000001',
      'X-User-Role': 'PLATFORM_ADMIN',
    };

    const res = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
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
