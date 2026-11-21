import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { fetchFromGateway } from '@/lib/api/gateway-client';
import { OpsAuditClient, OpsHealthRow, AuditEventRow, SelfCheckRow } from './ops-audit-client';

export const dynamic = 'force-dynamic';

export default async function PlatformOpsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let services: OpsHealthRow[] = [];
  let auditItems: AuditEventRow[] = [];
  let selfChecks: SelfCheckRow[] = [];
  let loadError: string | null = null;
  let auditScope: string | null = null;

  try {
    const [healthRes, auditRes, selfRes] = await Promise.all([
      fetchFromGateway('/api/v1/iadm/08/ops/health', { method: 'GET' }),
      fetchFromGateway('/api/v1/iadm/08/audit/search', { method: 'GET' }),
      fetchFromGateway('/api/v1/iadm/08/audit/self-check', { method: 'GET' }),
    ]);

    if (healthRes.ok) {
      const data = await healthRes.json();
      services = Array.isArray(data.services) ? data.services : [];
    } else {
      loadError = `Ops health failed (${healthRes.status})`;
    }

    if (auditRes.ok) {
      const data = await auditRes.json();
      auditItems = Array.isArray(data.items) ? data.items : [];
      auditScope = typeof data.scope === 'string' ? data.scope : null;
    } else {
      loadError = (loadError ? `${loadError}; ` : '') + `Audit search failed (${auditRes.status})`;
    }

    if (selfRes.ok) {
      const data = await selfRes.json();
      selfChecks = Array.isArray(data.checks) ? data.checks : [];
    } else {
      loadError = (loadError ? `${loadError}; ` : '') + `Self-check failed (${selfRes.status})`;
    }
  } catch (err) {
    loadError = err instanceof Error ? err.message : 'Failed to load ops desk';
  }

  return (
    <OpsAuditClient
      initialServices={services}
      initialAuditItems={auditItems}
      initialSelfChecks={selfChecks}
      initialAuditScope={auditScope}
      loadError={loadError}
    />
  );
}
