import { SupabaseClient } from '@supabase/supabase-js';
import { isTestSessionAllowed } from '@/lib/auth/test-session-gate';
import { query } from '@/lib/db';

export type UserRole = 'platform_admin' | 'college_admin' | 'faculty' | 'student';

function chosenName(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    const name = value?.trim();
    if (name) return name;
  }
  return 'Account';
}

export interface ResolvedUserSession {
  userId: string;
  email: string;
  role: UserRole;
  tenantId: string | null;
  tenantName: string | null;
  tenantStatus: string | null;
  collegeId: string | null;
  /** Set only when the winning role assignment names a college. Null means institute-wide. */
  assignedCollegeId: string | null;
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
  let isTestUser = false;
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
          isTestUser = true;
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

  // 2. Check if user is Platform Admin (authoritative check against plat.platform_admins via direct PostgreSQL query)
  let platAdmin: any = null;
  try {
    const paRes = await query(
      'SELECT id, status, role_code, full_name FROM plat.platform_admins WHERE user_id = $1 AND role_code = $2 AND status = $3 AND deleted_at IS NULL LIMIT 1',
      [userId, 'SUPER_ADMIN', 'ACTIVE']
    );
    if (paRes.rows.length > 0) {
      platAdmin = paRes.rows[0];
    }
  } catch {}

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
        assignedCollegeId: null,
        departmentId: null,
        displayName: chosenName(platAdmin.full_name, user.user_metadata?.full_name, userEmail.split('@')[0]),
        isPlatformAdmin: true,
      },
    };
  }

  // Allow mock platform admin only when explicitly in allowed test session mode
  if (isTestUser && isTestSessionAllowed() && user.app_metadata?.role === 'SUPER_ADMIN') {
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
        assignedCollegeId: null,
        departmentId: null,
        displayName: user.user_metadata?.full_name || 'Platform Admin',
        isPlatformAdmin: true,
      },
    };
  }

  // 3. Resolve tenant profile and role assignments directly via authoritative PostgreSQL queries
  let profile: any = null;
  let assignments: any = null;

  try {
    const [pRes, aRes] = await Promise.all([
      query(
        'SELECT id, tenant_id, status, deleted_at, full_name FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1',
        [userId]
      ),
      query(
        `SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to,
                r.code as role_code, r.name as role_name
         FROM iam.role_assignments ra
         JOIN iam.roles r ON ra.role_id = r.id
         WHERE ra.user_id = $1
           AND ra.deleted_at IS NULL AND r.deleted_at IS NULL`,
        [userId]
      ),
    ]);

    if (pRes.rows.length > 0) {
      profile = pRes.rows[0];
    }
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

  if (!profile || profile.deleted_at !== null) {
    // Only in gated test session mode: allow mock session with test cookie
    if (isTestUser && isTestSessionAllowed() && user.app_metadata?.role && user.app_metadata?.tenant_id) {
      const rawRole = String(user.app_metadata.role).toLowerCase();
      const effectiveRole: UserRole = rawRole.includes('admin')
        ? 'college_admin'
        : rawRole.includes('faculty')
        ? 'faculty'
        : 'student';

      let tenantName = 'Institutional Workspace';
      let collegeId: string | null = null;
      try {
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
          assignedCollegeId: null,
          departmentId: null,
          displayName: user.user_metadata?.full_name || userEmail.split('@')[0],
          isPlatformAdmin: false,
        },
      };
    }
    // Fail closed: real authenticated users without an authoritative iam.user_profiles record are denied
    return { success: false, reason: 'no_profile' };
  }

  if (profile.status !== 'ACTIVE') {
    return { success: false, reason: 'inactive_membership' };
  }

  const tenantId = profile.tenant_id;
  if (!tenantId) {
    return { success: false, reason: 'no_profile' };
  }

  // 4. Verify Tenant Status in core.tenants via authoritative PostgreSQL query
  let tenant: any = null;
  try {
    const tRes = await query(
      'SELECT id, name, code, status, deleted_at FROM core.tenants WHERE id = $1 LIMIT 1',
      [tenantId]
    );
    if (tRes.rows.length > 0) {
      tenant = tRes.rows[0];
    } else {
      return { success: false, reason: 'tenant_suspended' };
    }
  } catch {
    return { success: false, reason: 'tenant_suspended' };
  }

  if (tenant.deleted_at !== null || tenant.status === 'SUSPENDED') {
    return { success: false, reason: 'tenant_suspended' };
  }

  const tenantName = tenant?.name || 'Institutional Workspace';

  // 5. Fail closed if no authoritative role assignments found.
  // User-editable metadata (user_metadata.role) or unverified app_metadata must NEVER grant roles here.
  if (!assignments || assignments.length === 0) {
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
  let assignedCollegeId: string | null = null;
  let matchedDeptId: string | null = null;

  for (const assign of activeAssignments) {
    const roleObj = Array.isArray(assign.roles) ? assign.roles[0] : assign.roles;
    const code = (roleObj?.code || '').toUpperCase();

    if (
      code === 'COLLEGE_ADMIN' ||
      code === 'INSTITUTION_ADMIN' ||
      code === 'TENANT_ADMIN' ||
      code === 'PRINCIPAL' ||
      code === 'ACADEMIC_ADMIN'
    ) {
      resolvedRole = 'college_admin';
      assignedCollegeId = assign.college_id ?? null;
      matchedCollegeId = assign.college_id || matchedCollegeId;
      matchedDeptId = assign.department_id || matchedDeptId;
      break;
    } else if (
      resolvedRole !== 'faculty' &&
      (code === 'FACULTY' ||
        code === 'HOD' ||
        code === 'DEAN' ||
        code === 'TEACHER' ||
        code === 'SECTION_COORDINATOR')
    ) {
      resolvedRole = 'faculty';
      assignedCollegeId = assign.college_id ?? null;
      matchedCollegeId = assign.college_id || matchedCollegeId;
      matchedDeptId = assign.department_id || matchedDeptId;
    } else if (code === 'STUDENT' && !resolvedRole) {
      resolvedRole = 'student';
      assignedCollegeId = assign.college_id ?? null;
      matchedCollegeId = assign.college_id || matchedCollegeId;
      matchedDeptId = assign.department_id || matchedDeptId;
    }
  }


  // Fallback college lookup by tenant if not assigned at user/employee level
  if (!matchedCollegeId && tenantId) {
    try {
      const cRes = await query('SELECT id FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL LIMIT 1', [tenantId]);
      if (cRes.rows.length > 0) {
        matchedCollegeId = cRes.rows[0].id;
      }
    } catch {}
  }

  // Fail closed if active assignments do not match any recognized system role
  if (!resolvedRole) {
    return { success: false, reason: 'no_role_assigned' };
  }

  return {
    success: true,
    session: {
      userId,
      email: userEmail,
      role: resolvedRole,
      tenantId,
      tenantName,
      tenantStatus: tenant?.status || 'ACTIVE',
      collegeId: matchedCollegeId,
      assignedCollegeId,
      departmentId: matchedDeptId,
      displayName: chosenName(profile.full_name, user.user_metadata?.full_name, userEmail.split('@')[0]),
      isPlatformAdmin: false,
    },
  };
}
