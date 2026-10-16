import { redirect } from 'next/navigation';
import Link from 'next/link';
import { Building2, ShieldCheck, Users, Activity, Plus, History, Clock, ArrowRight } from 'lucide-react';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { StatCard } from '@/components/ui/stat-card';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/utils';

export const dynamic = 'force-dynamic';

export default async function PlatformAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  // Load real aggregate counts from Supabase PostgreSQL
  let tenantCount = 0;
  let totalUserCount = 0;
  let roleTemplateCount = 30;
  let institutes: any[] = [];
  let recentAuditEvents: any[] = [];
  const statusCounts: Record<string, number> = {
    ACTIVE: 0,
    ONBOARDING: 0,
    SUSPENDED: 0,
    OFFBOARDED: 0,
  };

  try {
    // 1. Cross-tenant aggregated institute & user statistics (NO tenant student/faculty rows exposed)
    const tenantsRes = await query(`
      SELECT 
        t.id, 
        t.name, 
        t.code, 
        t.status, 
        t.created_at,
        count(DISTINCT c.id)::int as college_count,
        count(up.id)::int as user_count
      FROM core.tenants t
      LEFT JOIN core.colleges c ON c.tenant_id = t.id AND c.deleted_at IS NULL
      LEFT JOIN iam.user_profiles up ON up.tenant_id = t.id AND up.deleted_at IS NULL
      WHERE t.deleted_at IS NULL
      GROUP BY t.id, t.name, t.code, t.status, t.created_at
      ORDER BY t.created_at DESC
    `);
    institutes = tenantsRes.rows || [];
    tenantCount = institutes.length;

    // Group institutes by status
    institutes.forEach((inst) => {
      const s = (inst.status || 'ACTIVE').toUpperCase();
      if (s === 'ACTIVE') statusCounts.ACTIVE++;
      else if (s === 'SUSPENDED') statusCounts.SUSPENDED++;
      else if (s === 'INACTIVE' || s === 'ONBOARDING' || s === 'REQUESTED') statusCounts.ONBOARDING++;
      else statusCounts.OFFBOARDED++;
    });

    // 2. Global platform total user count
    const usersRes = await query(`
      SELECT count(*)::int as count
      FROM iam.user_profiles
      WHERE deleted_at IS NULL
    `);
    totalUserCount = Number(usersRes.rows[0]?.count) || 0;

    // 3. System role templates count
    const rolesRes = await query(`
      SELECT count(*)::int as count
      FROM iam.roles
      WHERE is_system = true AND deleted_at IS NULL
    `);
    roleTemplateCount = Number(rolesRes.rows[0]?.count) || 30;

    // 4. Recent platform audit events
    const auditRes = await query(`
      SELECT 
        ae.id,
        ae.event_time,
        ae.action,
        ae.resource_type,
        ae.status,
        ae.severity,
        t.code as tenant_code
      FROM audit.events ae
      LEFT JOIN core.tenants t ON t.id = ae.tenant_id
      ORDER BY ae.event_time DESC
      LIMIT 5
    `);
    recentAuditEvents = auditRes.rows || [];
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

        <Link href="/platform/institutes/new">
          <Button variant="primary" size="sm">
            <Plus className="mr-1.5 h-4 w-4" />
            Onboard New Institute
          </Button>
        </Link>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Onboarded Institutes"
          value={tenantCount}
          icon={Building2}
          description="Total multi-tenant nodes"
        />
        <StatCard
          title="Total Platform Users"
          value={totalUserCount}
          icon={Users}
          description="Cross-tenant registered identities"
        />
        <StatCard
          title="Active Tenancies"
          value={statusCounts.ACTIVE}
          icon={Activity}
          description="Institutes in active operation"
        />
        <StatCard
          title="System Role Templates"
          value={roleTemplateCount}
          icon={ShieldCheck}
          description="Platform RBAC definitions"
        />
      </div>

      {/* Institutes Grouped by Status */}
      <Card>
        <CardHeader className="py-3.5 border-b border-[#E3E8EF] bg-[#F6F8FB]/50">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Building2 className="h-4 w-4 text-[#1F6FEB]" />
              <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
                Institutes Grouped by Lifecycle Status
              </CardTitle>
            </div>
            <Link href="/platform/institutes" className="text-xs text-[#1F6FEB] hover:underline flex items-center gap-1">
              View All ({tenantCount}) <ArrowRight className="h-3 w-3" />
            </Link>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div className="p-3 rounded-[8px] border border-[#B2E6CE] bg-[#EAF8F1]/40 flex items-center justify-between">
              <div>
                <span className="text-[11px] font-semibold text-[#1E9E6A] uppercase tracking-wider block">Active</span>
                <span className="text-xl font-bold text-[#0B1F3A] mt-0.5 block">{statusCounts.ACTIVE}</span>
              </div>
              <Badge status="ACTIVE" />
            </div>

            <div className="p-3 rounded-[8px] border border-[#FCE2B3] bg-[#FEF7EA]/40 flex items-center justify-between">
              <div>
                <span className="text-[11px] font-semibold text-[#E39B16] uppercase tracking-wider block">Onboarding</span>
                <span className="text-xl font-bold text-[#0B1F3A] mt-0.5 block">{statusCounts.ONBOARDING}</span>
              </div>
              <Badge status="ONBOARDING" />
            </div>

            <div className="p-3 rounded-[8px] border border-[#F8BDBD] bg-[#FDECEC]/40 flex items-center justify-between">
              <div>
                <span className="text-[11px] font-semibold text-[#D64545] uppercase tracking-wider block">Suspended</span>
                <span className="text-xl font-bold text-[#0B1F3A] mt-0.5 block">{statusCounts.SUSPENDED}</span>
              </div>
              <Badge status="SUSPENDED" />
            </div>

            <div className="p-3 rounded-[8px] border border-[#E3E8EF] bg-[#F0F2F5]/40 flex items-center justify-between">
              <div>
                <span className="text-[11px] font-semibold text-[#6B7688] uppercase tracking-wider block">Offboarded</span>
                <span className="text-xl font-bold text-[#0B1F3A] mt-0.5 block">{statusCounts.OFFBOARDED}</span>
              </div>
              <Badge status="OFFBOARDED" />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Main Grid: Cross-Tenant Aggregates Table & Activity Feeds */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Aggregated Users per College Table */}
        <div className="lg:col-span-2">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>Institutes & User Aggregate Distribution</CardTitle>
                  <CardDescription>
                    Aggregated metrics per tenancy. Platform operator view isolates tenant records from raw faculty/student data.
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
                        <th className="pb-3 font-semibold uppercase tracking-wider text-right">Colleges</th>
                        <th className="pb-3 font-semibold uppercase tracking-wider text-right">Aggregated Users</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E3E8EF]">
                      {institutes.map((inst) => (
                        <tr key={inst.id} className="hover:bg-[#F6F8FB]">
                          <td className="py-3 font-medium text-[#0B1F3A]">
                            <div className="flex items-center gap-2">
                              <Building2 className="h-3.5 w-3.5 text-[#1F6FEB]" />
                              <span>{inst.name || 'Unnamed'}</span>
                            </div>
                          </td>
                          <td className="py-3 font-mono text-[#1B2433]">{inst.code}</td>
                          <td className="py-3">
                            <Badge status={inst.status || 'ACTIVE'} />
                          </td>
                          <td className="py-3 text-right font-medium text-[#1B2433]">
                            {inst.college_count || 1}
                          </td>
                          <td className="py-3 text-right font-semibold text-[#0B1F3A]">
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-mono bg-[#E8F0FE] text-[#1F6FEB]">
                              {inst.user_count} users
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right Column: Recent Onboarding Activity & Platform Audit Events */}
        <div className="space-y-6">
          {/* Recent Onboarding Activity */}
          <Card>
            <CardHeader className="py-3.5 border-b border-[#E3E8EF]">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-[#1F6FEB]" />
                  <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
                    Recent Onboarding Activity
                  </CardTitle>
                </div>
              </div>
            </CardHeader>
            <CardContent className="pt-3">
              <div className="divide-y divide-[#E3E8EF]">
                {institutes.slice(0, 4).map((inst) => (
                  <div key={inst.id} className="py-2.5 flex items-center justify-between">
                    <div>
                      <p className="text-xs font-semibold text-[#0B1F3A]">{inst.name}</p>
                      <p className="text-[11px] text-[#6B7688] font-mono mt-0.5">
                        Code: {inst.code} · {formatDate(inst.created_at)}
                      </p>
                    </div>
                    <Badge status={inst.status || 'ACTIVE'} />
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* Recent Platform Audit Events */}
          <Card>
            <CardHeader className="py-3.5 border-b border-[#E3E8EF]">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <History className="h-4 w-4 text-[#1F6FEB]" />
                  <CardTitle className="text-sm font-semibold text-[#0B1F3A]">
                    Recent Platform Audit Events
                  </CardTitle>
                </div>
                <Link href="/platform/audit" className="text-xs text-[#1F6FEB] hover:underline flex items-center gap-1">
                  Ledger <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </CardHeader>
            <CardContent className="pt-3">
              {recentAuditEvents.length === 0 ? (
                <p className="text-xs text-[#6B7688] py-2">No recent audit logs found.</p>
              ) : (
                <div className="space-y-2.5">
                  {recentAuditEvents.map((evt) => (
                    <div key={evt.id} className="p-2.5 rounded-[6px] border border-[#E3E8EF] bg-white hover:border-[#1F6FEB] transition-colors">
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-[11px] font-bold text-[#1F6FEB]">
                          {evt.action}
                        </span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                          evt.status === 'SUCCESS' ? 'bg-[#EAF8F1] text-[#1E9E6A]' : 'bg-[#FDECEC] text-[#D64545]'
                        }`}>
                          {evt.status}
                        </span>
                      </div>
                      <div className="flex items-center justify-between text-[10px] text-[#6B7688] mt-1 font-mono">
                        <span>Tenant: {evt.tenant_code || 'GLOBAL'}</span>
                        <span>{formatDate(evt.event_time)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
