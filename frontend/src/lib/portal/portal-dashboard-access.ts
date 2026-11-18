/**
 * Access decisions for STM-10-EN-002 portal dashboard BFF.
 * Kept pure so negative tests run without spinning Next.js.
 */

export type PortalDashAccess =
  | { allow: true }
  | { allow: false; status: 401 | 403 | 400; detail: string };

export function decidePortalDashboardAccess(input: {
  authenticated: boolean;
  role?: string | null;
  tenantId?: string | null;
  userId?: string | null;
  /** Client-supplied studentId query — always forbidden (IDOR probe). */
  clientStudentId?: string | null;
}): PortalDashAccess {
  if (!input.authenticated) {
    return {
      allow: false,
      status: 401,
      detail: 'Authentication required for the student portal dashboard.',
    };
  }
  if (input.role !== 'student') {
    return {
      allow: false,
      status: 403,
      detail: 'Only students may load the student portal dashboard aggregate.',
    };
  }
  if (!input.tenantId || !input.userId) {
    return {
      allow: false,
      status: 400,
      detail: 'Tenant and user are required.',
    };
  }
  if (input.clientStudentId) {
    return {
      allow: false,
      status: 403,
      detail: 'Student id must not be supplied by the client for portal aggregation.',
    };
  }
  return { allow: true };
}
