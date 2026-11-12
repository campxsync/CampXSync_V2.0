import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080';

function staffRole(sessionRole?: string | null) {
  if (sessionRole === 'platform_admin') {
    return 'INSTITUTION_ADMIN';
  }
  return 'COLLEGE_ADMIN';
}

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const view = req.nextUrl.searchParams.get('view') || 'requests';
  const studentId = req.nextUrl.searchParams.get('studentId');
  let path = '/api/v1/lifecycle/requests';
  if (view === 'report') {
    path = '/api/v1/lifecycle/reports/summary';
  } else if (view === 'history' && studentId) {
    path = `/api/v1/lifecycle/students/${studentId}/history`;
  } else if (view === 'status' && studentId) {
    path = `/api/v1/lifecycle/students/${studentId}/status`;
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
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const action = String(payload?.action || 'decide');
  let path = `/api/v1/lifecycle/requests/${payload.requestId}/decide`;
  let body: Record<string, unknown> = { decision: payload.decision, reason: payload.reason };
  if (action === 'transition') {
    path = `/api/v1/lifecycle/students/${payload.studentId}/transition`;
    body = { targetStatus: payload.targetStatus, reason: payload.reason || 'Administrative status change' };
  } else if (action === 'rule') {
    path = '/api/v1/lifecycle/rules';
    body = {
      fromStatus: payload.fromStatus,
      toStatus: payload.toStatus,
      requiresApproval: payload.requiresApproval !== false,
      active: payload.active !== false,
    };
  } else if (action === 'assign-advisor') {
    path = '/api/v1/lifecycle/advisors';
    body = { advisorUserId: payload.advisorUserId, studentId: payload.studentId };
  } else if (action === 'graduate-validate') {
    path = '/api/v1/lifecycle/graduation/validate';
    body = { studentId: payload.studentId, programId: payload.programId };
  } else if (action === 'graduate-record') {
    path = '/api/v1/lifecycle/graduation/record';
    body = {
      studentId: payload.studentId,
      programId: payload.programId,
      graduatedOn: payload.graduatedOn,
      notes: payload.notes || '',
    };
  }
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    cache: 'no-store',
  });
  const text = await res.text();
  return new NextResponse(text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
