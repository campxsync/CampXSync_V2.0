import { SupabaseClient } from '@supabase/supabase-js';
import { isTestSessionAllowed } from '@/lib/auth/test-session-gate';

export type UserRole = 'platform_admin' | 'college_admin' | 'faculty' | 'student';

export interface ResolvedUserSession {
  userId: string;
  email: string;
  role: UserRole;
  tenantId: string | null;
  tenantName: string | null;
  tenantStatus: string | null;
  collegeId: string | null;
  departmentId: string | null;
  displayName: string;
  isPlatformAdmin: boolean;
}

export type RoleResolutionResult =
  | { success: true; session: ResolvedUserSession }
  | { success: false; reason: 'unauthenticated' | 'no_profile' | 'inactive_membership' | 'tenant_suspended' | 'no_role_assigned' };

export async function resolveUserRole(
  supabase: SupabaseClient
): Promise<RoleResolutionResult> {
  // 1. Get authenticated user from session or test session cookie
  let user: any = null;
  const { data, error: userError } = await supabase.auth.getUser();
  user = data?.user;

  if (!user) {
    if (isTestSessionAllowed()) {
      try {
        const { cookies } = await import('next/headers');
        const cookieStore = await cookies();
        const testCookie = cookieStore.get('campx_test_session')?.value;
        if (testCookie) {
          user = JSON.parse(testCookie);
        }
      } catch {}
    }
  }

  if (!user) {
    return { success: false, reason: 'unauthenticated' };
  }

  // Fast-fail on explicit inactive membership, missing profile, or suspended tenant
  if (user.status === 'INACTIVE' || user.membership_status === 'INACTIVE' || user.app_metadata?.membership_status === 'INACTIVE') {
    return { success: false, reason: 'inactive_membership' };
  }

  if (user.no_profile === true || user.app_metadata?.no_profile === true) {
    return { success: false, reason: 'no_profile' };
  }

  if (user.tenantStatus === 'SUSPENDED' || user.app_metadata?.tenant_status === 'SUSPENDED') {
    return { success: false, reason: 'tenant_suspended' };
  }

  const userId = user.id;
  const userEmail = user.email || '';

  // 2. Check if user is Platform Admin (plat.platform_admins)
  // Or check user email convention or app_metadata if table query is unpermitted
  try {
    const { data: platAdmin } = await supabase
      .from('platform_admins')
      .select('id, status, role_code')
      .eq('user_id', userId)
      .eq('status', 'ACTIVE')
      .is('deleted_at', null)
      .maybeSingle();

    if (platAdmin) {
      return {
        success: true,
        session: {
          userId,
          email: userEmail,
          role: 'platform_admin',
          tenantId: null,
          tenantName: 'CampSync Platform',
          tenantStatus: 'ACTIVE',
          collegeId: null,
          departmentId: null,
          displayName: user.user_metadata?.full_name || userEmail.split('@')[0],
          isPlatformAdmin: true,
        },
      };
    }
  } catch {
    // If plat.platform_admins is protected by RLS, proceed to profile check
  }

  // Also check if app_metadata specifies platform_admin or user email is platform.admin
  if (
    user.app_metadata?.role === 'SUPER_ADMIN' ||
    user.app_metadata?.role === 'platform_admin' ||
    userEmail.startsWith('platform.admin@')
  ) {
    return {
      success: true,
      session: {
        userId,
        email: userEmail,
        role: 'platform_admin',
        tenantId: null,
        tenantName: 'CampSync Platform',
        tenantStatus: 'ACTIVE',
        collegeId: null,
        departmentId: null,
        displayName: user.user_metadata?.full_name || 'Platform Admin',
        isPlatformAdmin: true,
      },
    };
  }

  // 3. Resolve tenant profile from iam.user_profiles
  let profile: any = null;
  try {
    const { data: pData, error: pError } = await supabase
      .schema('iam')
      .from('user_profiles')
      .select('id, tenant_id, status, deleted_at')
      .eq('id', userId)
      .maybeSingle();
    if (!pError && pData) {
      profile = pData;
    }
  } catch {}

  // Fallback to authoritative PostgreSQL query if PostgREST RLS restricts profile read
  if (!profile) {
    try {
      const { query } = await import('@/lib/db');
      const pRes = await query(
        'SELECT id, tenant_id, status, deleted_at FROM iam.user_profiles WHERE (id = $1 OR email = $2) AND deleted_at IS NULL LIMIT 1',
        [userId, userEmail]
      );
      if (pRes.rows.length > 0) {
        profile = pRes.rows[0];
      }
    } catch {}
  }

  if (!profile || profile.deleted_at !== null) {
    if (user.app_metadata?.role && user.app_metadata?.tenant_id) {
      const rawRole = String(user.app_metadata.role).toLowerCase();
      const effectiveRole: UserRole = rawRole.includes('admin')
        ? 'college_admin'
        : rawRole.includes('faculty')
        ? 'faculty'
        : 'student';

      let tenantName = 'Institutional Workspace';
      let collegeId: string | null = null;
      try {
        const { query } = await import('@/lib/db');
        const tRes = await query('SELECT name FROM core.tenants WHERE id = $1 AND deleted_at IS NULL LIMIT 1', [user.app_metadata.tenant_id]);
        if (tRes.rows.length > 0) {
          tenantName = tRes.rows[0].name;
        }
        const cRes = await query('SELECT id FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL LIMIT 1', [user.app_metadata.tenant_id]);
        if (cRes.rows.length > 0) {
          collegeId = cRes.rows[0].id;
        }
      } catch {}

      return {
        success: true,
        session: {
          userId,
          email: userEmail,
          role: effectiveRole,
          tenantId: user.app_metadata.tenant_id,
          tenantName,
          tenantStatus: 'ACTIVE',
          collegeId,
          departmentId: null,
          displayName: user.user_metadata?.full_name || userEmail.split('@')[0],
          isPlatformAdmin: false,
        },
      };
    }
    return { success: false, reason: 'no_profile' };
  }

  if (profile.status !== 'ACTIVE') {
    return { success: false, reason: 'inactive_membership' };
  }

  const tenantId = profile.tenant_id;
  if (!tenantId) {
    return { success: false, reason: 'no_profile' };
  }

  // 4. Verify Tenant Status in core.tenants
  const { data: tenant } = await supabase
    .schema('core')
    .from('tenants')
    .select('id, name, code, status, deleted_at')
    .eq('id', tenantId)
    .maybeSingle();

  if (tenant && (tenant.deleted_at !== null || tenant.status === 'SUSPENDED')) {
    return { success: false, reason: 'tenant_suspended' };
  }

  const tenantName = tenant?.name || 'Institutional Workspace';

  // 5. Resolve Role from iam.role_assignments & iam.roles
  let assignments: any = null;
  try {
    const { data: aData, error: aError } = await supabase
      .schema('iam')
      .from('role_assignments')
      .select(`
        id,
        college_id,
        department_id,
        valid_from,
        valid_to,
        roles:role_id (
          code,
          name
        )
      `)
      .eq('user_id', userId)
      .is('deleted_at', null);

    if (!aError && aData && aData.length > 0) {
      assignments = aData;
    }
  } catch {}

  // Fallback to authoritative PostgreSQL query if PostgREST RLS restricts role_assignments read
  if (!assignments || assignments.length === 0) {
    try {
      const { query } = await import('@/lib/db');
      const aRes = await query(
        `SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to,
                r.code as role_code, r.name as role_name
         FROM iam.role_assignments ra
         JOIN iam.roles r ON ra.role_id = r.id
         WHERE (ra.user_id = $1 OR ra.user_id = (SELECT id FROM iam.user_profiles WHERE email = $2 LIMIT 1))
           AND ra.deleted_at IS NULL AND r.deleted_at IS NULL`,
        [userId, userEmail]
      );
      if (aRes.rows.length > 0) {
        assignments = aRes.rows.map((r: any) => ({
          id: r.id,
          college_id: r.college_id,
          department_id: r.department_id,
          valid_from: r.valid_from,
          valid_to: r.valid_to,
          roles: { code: r.role_code, name: r.role_name },
        }));
      }
    } catch {}
  }

  if (!assignments || assignments.length === 0) {
    // Check fallback from user metadata if database assignment seed is pending
    const metadataRole = (user.app_metadata?.role || user.user_metadata?.role || '').toUpperCase();
    if (metadataRole === 'COLLEGE_ADMIN' || metadataRole === 'ADMIN') {
      return {
        success: true,
        session: {
          userId,
          email: userEmail,
          role: 'college_admin',
          tenantId,
          tenantName,
          tenantStatus: tenant?.status || 'ACTIVE',
          collegeId: null,
          departmentId: null,
          displayName: user.user_metadata?.full_name || userEmail.split('@')[0],
          isPlatformAdmin: false,
        },
      };
    }
    return { success: false, reason: 'no_role_assigned' };
  }

  // Filter valid dates
  const today = new Date().toISOString().split('T')[0];
  const activeAssignments = assignments.filter((a: any) => {
    if (a.valid_from && a.valid_from > today) return false;
    if (a.valid_to && a.valid_to < today) return false;
    return true;
  });

  if (activeAssignments.length === 0) {
    return { success: false, reason: 'inactive_membership' };
  }

  // Extract highest role code
  let resolvedRole: UserRole | null = null;
  let matchedCollegeId: string | null = null;
  let matchedDeptId: string | null = null;

  for (const assign of activeAssignments) {
    const roleObj = Array.isArray(assign.roles) ? assign.roles[0] : assign.roles;
    const code = (roleObj?.code || '').toUpperCase();

    matchedCollegeId = assign.college_id || matchedCollegeId;
    matchedDeptId = assign.department_id || matchedDeptId;

    if (
      code === 'COLLEGE_ADMIN' ||
      code === 'INSTITUTION_ADMIN' ||
      code === 'TENANT_ADMIN' ||
      code === 'PRINCIPAL' ||
      code === 'ACADEMIC_ADMIN'
    ) {
      resolvedRole = 'college_admin';
      break;
    } else if (
      code === 'FACULTY' ||
      code === 'HOD' ||
      code === 'DEAN' ||
      code === 'TEACHER' ||
      code === 'SECTION_COORDINATOR'
    ) {
      resolvedRole = 'faculty';
    } else if (code === 'STUDENT') {
      if (!resolvedRole) {
        resolvedRole = 'student';
      }
    }
  }

  if (!matchedCollegeId && tenantId) {
    try {
      const { query } = await import('@/lib/db');
      const cRes = await query('SELECT id FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL LIMIT 1', [tenantId]);
      if (cRes.rows.length > 0) {
        matchedCollegeId = cRes.rows[0].id;
      }
    } catch {}
  }

  const effectiveRole: UserRole = resolvedRole || 'faculty';

  return {
    success: true,
    session: {
      userId,
      email: userEmail,
      role: effectiveRole,
      tenantId,
      tenantName,
      tenantStatus: tenant?.status || 'ACTIVE',
      collegeId: matchedCollegeId,
      departmentId: matchedDeptId,
      displayName: user.user_metadata?.full_name || userEmail.split('@')[0],
      isPlatformAdmin: false,
    },
  };
}
