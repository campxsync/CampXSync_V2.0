import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

/**
 * STM-10-US-021 — permitted linked views for the authenticated guardian person.
 * Uses existing guardian APIs only. Person id is always the session user id
 * (never a client-supplied guardian or student id).
 *
 * Browser demo: no dedicated guardian test account in Dev — service tests cover security.
 */
export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL =
  process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required to view permitted linked student views.',
        },
        { status: 401 }
      );
    }

    const userId = resolution.session.userId;
    if (!userId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Missing session user',
          status: 400,
        },
        { status: 400 }
      );
    }

    // Ignore any client-supplied person/student id query params (IDOR hardening).
    void req.nextUrl.searchParams.get('guardianPersonId');
    void req.nextUrl.searchParams.get('studentId');

    const headers = await getGatewayHeaders(req, supabase, resolution.session);
    const gatewayUrl = new URL(
      `/api/v1/guardians/persons/${encodeURIComponent(userId)}/permitted-views`,
      GATEWAY_BASE_URL
    );

    const gatewayResponse = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    const data = await gatewayResponse.json().catch(() => ({}));
    return NextResponse.json(data, { status: gatewayResponse.status });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unexpected error';
    if (message.startsWith('GATEWAY_MISSING_AUTH')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required to view permitted linked student views.',
        },
        { status: 401 }
      );
    }
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: message,
      },
      { status: 500 }
    );
  }
}
