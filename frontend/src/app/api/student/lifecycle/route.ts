import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080';

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !resolution.session.tenantId) {
    return NextResponse.json({ title: 'Unauthenticated', status: 401 }, { status: 401 });
  }
  const studentId = req.nextUrl.searchParams.get('studentId') || resolution.session.userId;
  if (!studentId) {
    return NextResponse.json({ title: 'studentId is required', status: 400 }, { status: 400 });
  }
  const view = req.nextUrl.searchParams.get('view') || 'status';
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  headers['X-Student-Record-Id'] = studentId;
  let path = `/api/v1/lifecycle/students/${studentId}/status`;
  if (view === 'history') {
    path = `/api/v1/lifecycle/students/${studentId}/history`;
  } else if (view === 'requests') {
    path = `/api/v1/lifecycle/students/${studentId}/requests`;
  }
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  const body = await res.text();
  return new NextResponse(body, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !resolution.session.tenantId) {
    return NextResponse.json({ title: 'Unauthenticated', status: 401 }, { status: 401 });
  }
  const payload = await req.json();
  const targetStudentId = payload?.studentId || resolution.session.userId;
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  if (targetStudentId) {
    headers['X-Student-Record-Id'] = String(targetStudentId);
  }
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/lifecycle/requests`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: targetStudentId,
      requestType: payload.requestType,
      reason: payload.reason,
      effectiveDate: payload.effectiveDate || null,
    }),
    cache: 'no-store',
  });
  const body = await res.text();
  return new NextResponse(body, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
