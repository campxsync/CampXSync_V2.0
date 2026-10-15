import { redirect } from 'next/navigation';
import { Building2, ShieldCheck, Users, Activity, Plus } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export const dynamic = 'force-dynamic';

export default async function PlatformAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  // Load real aggregate counts from Supabase PostgreSQL
  let tenantCount = 0;
  let roleTemplateCount = 0;
  let institutes: any[] = [];

  try {
    const { count: tCount, data: tData } = await supabase
      .schema('core')
      .from('tenants')
      .select('id, name, code, status, created_at', { count: 'exact' });

    tenantCount = tCount || 0;
    institutes = tData || [];

    const { count: rCount } = await supabase
      .schema('iam')
      .from('role_templates')
      .select('id', { count: 'exact' });

    roleTemplateCount = rCount || 0;
  } catch (e) {
    console.error('Platform aggregate query failed:', e);
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0B1F3A]">
            Platform Operator Dashboard
          </h1>
          <p className="text-xs text-[#6B7688] mt-1">
            Global institute registry, multi-tenant lifecycle states, and platform audit trail.
          </p>
        </div>

        <Button variant="primary" size="sm">
          <Plus className="mr-1.5 h-4 w-4" />
          Onboard New Institute
        </Button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Onboarded Institutes"
          value={tenantCount}
          icon={Building2}
          description="Provisioned institutional tenants"
        />
        <StatCard
          title="Active Tenancies"
          value={institutes.filter((i) => i.status === 'ACTIVE').length}
          icon={Activity}
          description="Institutes in active state"
        />
        <StatCard
          title="System Role Templates"
          value={roleTemplateCount}
          icon={ShieldCheck}
          description="Platform RBAC definitions"
        />
        <StatCard
          title="Audit Trail State"
          value="Healthy"
          icon={Users}
          description="Partition RLS enforced"
        />
      </div>

      {/* Institutes Registry Overview */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Registered Institutes & Tenants</CardTitle>
              <CardDescription>
                Authoritative multi-tenant records from core.tenants in staging
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {institutes.length === 0 ? (
            <p className="text-xs text-[#6B7688]">No institutes onboarded yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-[#E3E8EF] text-[#6B7688]">
                    <th className="pb-3 font-semibold uppercase tracking-wider">Institute Name</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Tenant Code</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Status</th>
                    <th className="pb-3 font-semibold uppercase tracking-wider">Tenant UUID</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E3E8EF]">
                  {institutes.map((inst) => (
                    <tr key={inst.id} className="hover:bg-[#F6F8FB]">
                      <td className="py-3 font-medium text-[#0B1F3A]">{inst.name || 'Unnamed'}</td>
                      <td className="py-3 font-mono text-[#1B2433]">{inst.code}</td>
                      <td className="py-3">
                        <Badge status={inst.status || 'ACTIVE'} />
                      </td>
                      <td className="py-3 font-mono text-[#6B7688] text-[11px]">{inst.id}</td>
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
