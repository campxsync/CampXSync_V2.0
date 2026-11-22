import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { InstitutesRegistryClient, InstituteRecord } from './institutes-registry-client';
import { fetchFromGateway } from '@/lib/api/gateway-client';

export const dynamic = 'force-dynamic';

/**
 * Institutes & Tenancies list — IADM01-US-016.
 * Plain language: loads from IADM-01 via gateway; failed call shows unavailable, never a silent empty success.
 */
export default async function InstitutesRegistryPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let institutes: InstituteRecord[] = [];
  let loadError: string | null = null;

  try {
    const res = await fetchFromGateway('/api/v1/iadm/01/institutes', {
      method: 'GET',
      requireAuth: true,
    });

    if (res.ok) {
      const data = await res.json();
      const rows = Array.isArray(data.institutes) ? data.institutes : [];
      institutes = rows.map(
        (row: {
          instituteId?: string;
          tenantId?: string;
          id?: string;
          name?: string;
          code?: string;
          status?: string;
          createdAt?: string;
          created_at?: string;
          collegeCount?: number;
          college_count?: number;
        }) => ({
          id: row.instituteId || row.tenantId || row.id || '',
          name: row.name || '',
          code: row.code || '',
          status: row.status || '',
          created_at: row.createdAt || row.created_at || '',
          updated_at: '',
          college_count: Number(row.collegeCount ?? row.college_count) || 0,
          user_count: 0,
        })
      );
    } else {
      loadError = 'unavailable';
    }
  } catch (err) {
    console.error('Failed to load institutes via IADM-01:', err);
    loadError = 'unavailable';
  }

  return <InstitutesRegistryClient initialInstitutes={institutes} loadError={loadError} />;
}
