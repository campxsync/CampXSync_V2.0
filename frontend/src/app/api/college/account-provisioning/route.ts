import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { canAccessCollegeOperationalBff, gatewayActorRoleHint } from '@/lib/auth/role-policy';

const GATEWAY_BASE_URL =
  process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

/**
 * BFF: STM-07 staff account-provisioning desk (US-003/005/006/012) via API Gateway → student-service.
 */
export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canAccessCollegeOperationalBff(resolution.session.role)) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const headers = await getGatewayHeaders(
    req,
    supabase,
    resolution.session,
    gatewayActorRoleHint(resolution.session.role)
  );
  const view = req.nextUrl.searchParams.get('view') || 'link-status';
  const studentId = req.nextUrl.searchParams.get('studentId');
  let path = '';
  if (view === 'link-status') {
    if (!studentId) {
      return NextResponse.json({ title: 'studentId is required', status: 400 }, { status: 400 });
    }
    path = `/api/v1/students/${encodeURIComponent(studentId)}/link-status`;
  } else if (view === 'provisioning-requests') {
    if (!studentId) {
      return NextResponse.json({ title: 'studentId is required', status: 400 }, { status: 400 });
    }
    path = `/api/v1/students/${encodeURIComponent(studentId)}/provisioning-requests`;
  } else if (view === 'account-role-audit') {
    const limit = req.nextUrl.searchParams.get('limit') || '50';
    path = `/api/v1/students/account-role-audit?limit=${encodeURIComponent(limit)}`;
  } else {
    return NextResponse.json({ title: 'Unknown view', status: 400 }, { status: 400 });
  }
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, { method: 'GET', headers, cache: 'no-store' });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !canAccessCollegeOperationalBff(resolution.session.role)) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  const action = String(payload?.action || '');
  const studentId = String(payload?.studentId || '');
  if (!studentId) {
    return NextResponse.json({ title: 'studentId is required', status: 400 }, { status: 400 });
  }
  const headers = await getGatewayHeaders(
    req,
    supabase,
    resolution.session,
    gatewayActorRoleHint(resolution.session.role)
  );
  if (action === 'retry-provisioning') {
    const requestId = String(payload?.requestId || '');
    if (!requestId) {
      return NextResponse.json({ title: 'requestId is required', status: 400 }, { status: 400 });
    }
    const path = `/api/v1/students/${encodeURIComponent(studentId)}/retry-provisioning`;
    const res = await fetch(`${GATEWAY_BASE_URL}${path}`, {
      method: 'POST',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ requestId }),
      cache: 'no-store',
    });
    const body = await res.text();
    return new NextResponse(body, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  }
  return NextResponse.json({ title: 'Unknown action', status: 400 }, { status: 400 });
}
