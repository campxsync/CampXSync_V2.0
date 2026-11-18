import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import {
  aggregateStudentPortalDashboard,
  invalidatePortalDashboardCache,
} from '@/lib/portal/dashboard-aggregator';
import { decidePortalDashboardAccess } from '@/lib/portal/portal-dashboard-access';

export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL =
  process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

/**
 * BFF: STM-10-EN-002 student portal dashboard aggregation.
 * Bounded timeouts, short-lived cache, partial-failure shape.
 * Does not accept a client-supplied student id (ownership from /students/me only).
 */
export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    const access = decidePortalDashboardAccess({
      authenticated: resolution.success,
      role: resolution.success ? resolution.session.role : null,
      tenantId: resolution.success ? resolution.session.tenantId : null,
      userId: resolution.success ? resolution.session.userId : null,
      clientStudentId: req.nextUrl.searchParams.get('studentId'),
    });

    if (!access.allow) {
      const title =
        access.status === 401
          ? 'Unauthorized'
          : access.status === 400
            ? 'Missing session context'
            : 'Forbidden';
      return NextResponse.json(
        {
          type: `https://campxsync.edu/errors/${access.status === 401 ? 'unauthorized' : access.status === 400 ? 'bad-request' : 'forbidden'}`,
          title,
          status: access.status,
          detail: access.detail,
        },
        { status: access.status }
      );
    }

    if (!resolution.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required for the student portal dashboard.',
        },
        { status: 401 }
      );
    }

    const tenantId = resolution.session.tenantId!;
    const userId = resolution.session.userId!;
    const session = resolution.session;

    const refresh = req.nextUrl.searchParams.get('refresh') === '1';
    const cacheKey = `${tenantId}:${userId}`;
    if (refresh) {
      invalidatePortalDashboardCache(cacheKey);
    }

    let headers: Record<string, string>;
    try {
      headers = await getGatewayHeaders(req, supabase, session, 'STUDENT');
    } catch {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required for the student portal dashboard.',
        },
        { status: 401 }
      );
    }

    const aggregate = await aggregateStudentPortalDashboard({
      gatewayBaseUrl: GATEWAY_BASE_URL,
      headers,
      cacheKey,
      bypassCache: refresh,
    });

    return NextResponse.json(aggregate, {
      headers: {
        'Cache-Control': 'no-store',
        'X-Portal-Aggregate': 'STM-10-EN-002',
      },
    });
  } catch (err: unknown) {
    void err;
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: 'The portal dashboard could not be assembled right now.',
      },
      { status: 500 }
    );
  }
}
