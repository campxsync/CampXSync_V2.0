/**
 * Frontend R31 Registrar role policy (queue 1c).
 * UI routing / coarse BFF gates only — backend Path C + IAM remain authoritative.
 * Registrar does NOT inherit College Admin / Academic Admin surfaces.
 */

import type { UserRole } from '@/lib/auth/resolve-role';

/** Default desk after login for Registrar (R31). */
export const REGISTRAR_HOME = '/college/lifecycle';

/**
 * STM operational desks allowed for Registrar UI (D10 / D12d).
 * Catalogue / org / admissions-admin / attendance admin remain College Admin only.
 */
export const REGISTRAR_ALLOWED_COLLEGE_PREFIXES = [
  '/college/lifecycle',
  '/college/enrollment',
  '/college/account-provisioning',
  '/college/documents',
  '/college/guardians',
] as const;

export function isRegistrarUiRole(role: string | null | undefined): boolean {
  return role === 'registrar';
}

export function isRegistrarJwtClaim(rawRole: string | null | undefined): boolean {
  if (!rawRole) return false;
  const code = rawRole.trim().toUpperCase();
  return code === 'REGISTRAR' || code === 'R31';
}

export function canRegistrarAccessCollegePath(pathname: string): boolean {
  if (pathname === '/college' || pathname === '/college/') {
    // College Admin analytics dashboard is out of scope for R31 (redirect to REGISTRAR_HOME).
    return false;
  }
  return REGISTRAR_ALLOWED_COLLEGE_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`)
  );
}

/** College Admin + Registrar + Platform for STM operational BFF (lifecycle, enrollment, verify, guardians). */
export function canAccessCollegeOperationalBff(role: UserRole | string | null | undefined): boolean {
  return role === 'college_admin' || role === 'platform_admin' || role === 'registrar';
}

/** College Admin / Platform only — document types, requirements, org structure, admissions pipeline. */
export function canAccessCollegeAdminOnlyBff(role: UserRole | string | null | undefined): boolean {
  return role === 'college_admin' || role === 'platform_admin';
}

/**
 * Optional gateway role hint for legacy callers. Channel-A gateway-auth ignores this and uses JWT.
 * Never map Registrar → COLLEGE_ADMIN.
 */
export function gatewayActorRoleHint(uiRole: UserRole | string | null | undefined): string {
  switch (uiRole) {
    case 'platform_admin':
      return 'PLATFORM_ADMIN';
    case 'system_admin':
      return 'SYSTEM_ADMIN';
    case 'auditor':
      return 'AUDITOR';
    case 'institution_admin':
      return 'INSTITUTION_ADMIN';
    case 'registrar':
      return 'REGISTRAR';
    case 'faculty':
      return 'FACULTY';
    case 'student':
      return 'STUDENT';
    case 'college_admin':
    default:
      return 'COLLEGE_ADMIN';
  }
}

export function defaultDashboardForRole(role: UserRole): string {
  switch (role) {
    case 'platform_admin':
      return '/platform';
    case 'system_admin':
      return '/platform/identity/technical';
    case 'auditor':
      return '/platform/identity/evidence';
    case 'institution_admin':
      return '/institution';
    case 'college_admin':
      return '/college';
    case 'registrar':
      return REGISTRAR_HOME;
    case 'faculty':
      return '/faculty';
    case 'student':
      return '/student';
    default:
      return '/';
  }
}

export function isReturnToAuthorizedForRole(role: UserRole, returnTo: string): boolean {
  if (
    role === 'platform_admin' &&
    (returnTo.startsWith('/platform') || returnTo.startsWith('/college/account-provisioning'))
  ) {
    return true;
  }
  if (
    role === 'system_admin' &&
    (returnTo === '/platform/identity' || returnTo.startsWith('/platform/identity/'))
  ) {
    return true;
  }
  if (
    role === 'auditor' &&
    (returnTo.startsWith('/platform/configuration') ||
      returnTo === '/platform/identity' ||
      returnTo.startsWith('/platform/identity/'))
  ) {
    return true;
  }
  if (role === 'institution_admin' && returnTo.startsWith('/institution')) return true;
  if (role === 'college_admin' && returnTo.startsWith('/college')) return true;
  if (role === 'registrar' && canRegistrarAccessCollegePath(returnTo)) return true;
  if (
    role === 'faculty' &&
    (returnTo.startsWith('/faculty') || returnTo.startsWith('/college/admissions/applications'))
  ) {
    return true;
  }
  if (role === 'student' && returnTo.startsWith('/student')) return true;
  return false;
}
