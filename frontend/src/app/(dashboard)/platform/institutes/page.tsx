import { redirect } from 'next/navigation';
import Link from 'next/link';
import { Building2, Plus, Calendar, ShieldCheck, ExternalLink } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

export const dynamic = 'force-dynamic';

export default async function InstitutesRegistryPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  let institutes: any[] = [];
  try {
    const res = await query(`
      SELECT id, name, code, status, created_at, updated_at
      FROM core.tenants
      WHERE deleted_at IS NULL
      ORDER BY created_at DESC
    `);
    institutes = res.rows || [];
  } catch (err) {
    console.error('Failed to load institutes:', err);
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Institutes & Tenancies Registry
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Authoritative registry of all provisioned institutions, university campuses, and multi-tenant nodes.
          </p>
        </div>

        <Link href="/platform/institutes/new">
          <Button variant="primary" size="sm">
            <Plus className="mr-1.5 h-4 w-4" />
            Onboard New Institute
          </Button>
        </Link>
      </div>

      {/* Registry Table */}
      <Card>
        <CardHeader>
          <CardTitle>Registered Institutional Tenants ({institutes.length})</CardTitle>
          <CardDescription>
            Live tenancy records queried from core.tenants in staging database
          </CardDescription>
        </CardHeader>
        <CardContent>
          {institutes.length === 0 ? (
            <div className="text-center py-10">
              <Building2 className="mx-auto h-10 w-10 text-[#6B7688]/50" />
              <p className="mt-2 text-sm font-semibold text-[#0B1F3A]">No Institutes Found</p>
              <p className="text-xs text-[#6B7688] mt-1">
                Begin by onboarding your first institutional tenant.
              </p>
              <Link href="/platform/institutes/new" className="inline-block mt-4">
                <Button variant="primary" size="sm">
                  Launch Onboarding Wizard
                </Button>
              </Link>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Institute Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Tenant Code</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Created Date</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Tenant UUID</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {institutes.map((inst) => (
                    <tr key={inst.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3.5 font-medium text-[#0B1F3A]">
                        <div className="flex items-center gap-2">
                          <Building2 className="h-4 w-4 text-[#1F6FEB]" />
                          <span>{inst.name || 'Unnamed Tenant'}</span>
                        </div>
                      </td>
                      <td className="py-3.5 font-mono text-[#1B2433]">{inst.code}</td>
                      <td className="py-3.5">
                        <Badge status={inst.status || 'ACTIVE'} />
                      </td>
                      <td className="py-3.5 text-[#6B7688]">
                        {formatDate(inst.created_at)}
                      </td>
                      <td className="py-3.5 font-mono text-[#6B7688] text-[11px]">{inst.id}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
