import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403 }, { status: 403 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const url = new URL(req.url);
    const intakeId = url.searchParams.get('intakeId');
    const status = url.searchParams.get('status');

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-Tenant-Id': tenantId,
      'X-User-Id': userId,
      'X-User-Role': 'COLLEGE_ADMIN',
    };
    if (collegeId) headers['X-College-Id'] = collegeId;
    if (intakeId) headers['x-intake-id'] = intakeId;
    if (status) headers['x-status'] = status;

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/applications`;
    const res = await fetch(gatewayUrl, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
