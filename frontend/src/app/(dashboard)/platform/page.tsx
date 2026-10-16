import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { PlatformDashboardView } from './platform-dashboard-view';
import { sanitizePreferences, PRODUCT_DEFAULTS } from '@/lib/preferences/preference-model';

export const dynamic = 'force-dynamic';

export default async function PlatformAdminDashboardPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success || resolution.session.role !== 'platform_admin') {
    redirect('/access-denied?reason=forbidden');
  }

  const { userId } = resolution.session;

  // Load real aggregate counts from Supabase PostgreSQL
  let tenantCount = 0;
  let totalUserCount = 0;
  let roleTemplateCount = 30;
  let institutes: any[] = [];
  let recentAuditEvents: any[] = [];
  let userPreferences = PRODUCT_DEFAULTS;

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

    // 5. Load User Preferences
    const prefRes = await query(
      `SELECT preferences FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1`,
      [userId]
    );
    if (prefRes.rows.length > 0 && prefRes.rows[0].preferences) {
      userPreferences = sanitizePreferences(prefRes.rows[0].preferences, 'platform_admin', PRODUCT_DEFAULTS);
    }
  } catch (e) {
    console.error('Platform aggregate query failed:', e);
  }

  return (
    <PlatformDashboardView
      tenantCount={tenantCount}
      totalUserCount={totalUserCount}
      roleTemplateCount={roleTemplateCount}
      statusCounts={statusCounts as any}
      institutes={institutes}
      recentAuditEvents={recentAuditEvents}
      userId={userId}
      initialPreferences={userPreferences}
    />
  );
}
