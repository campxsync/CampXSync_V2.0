import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

/**
 * Cutover ACD-10 College Admin Dashboard Route.
 * Routes directly to authoritative CampXSync API Gateway (/api/v1/academics/analytics/dashboard)
 * backed by Java Reporting & Analytics Service (PostgreSQL relational source of truth).
 * Direct database queries (lib/db.ts) have been retired in compliance with Wave 8.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Unauthorized: only college administrators can access college analytics dashboard.',
        },
        { status: 403 }
      );
    }

    const tenantId = resolution.session.tenantId;
    if (!tenantId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Missing Tenant Context',
          status: 400,
          detail: 'No active tenant linked to user session.',
        },
        { status: 400 }
      );
    }

    const url = new URL(req.url);
    const gatewayUrl = new URL('/api/v1/academics/analytics/dashboard', GATEWAY_BASE_URL);
    gatewayUrl.searchParams.set('role', 'COLLEGE_ADMIN');
    url.searchParams.forEach((value, key) => {
      if (key !== 'role') {
        gatewayUrl.searchParams.set(key, value);
      }
    });

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': 'COLLEGE_ADMIN',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const gatewayResponse = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
    });

    const data = await gatewayResponse.json();

    if (!gatewayResponse.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Gateway Error',
          status: gatewayResponse.status,
          detail: data.detail || 'Failed to retrieve college admin dashboard from gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json(data);
  } catch (err: any) {
    console.error('Error fetching college dashboard through API gateway:', err);
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred while communicating with backend gateway.',
      },
      { status: 500 }
    );
  }
}
