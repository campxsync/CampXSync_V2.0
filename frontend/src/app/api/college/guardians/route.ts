import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

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
  const studentId = req.nextUrl.searchParams.get('studentId');
  if (!studentId) {
    return NextResponse.json({ title: 'Student record id is required', status: 422 }, { status: 422 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/students/${encodeURIComponent(studentId)}`, {
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
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studentId: payload.studentId,
      firstName: payload.firstName,
      lastName: payload.lastName,
      relationship: payload.relationship,
      email: payload.email,
      phone: payload.phone,
      primary: payload.primary === true,
    }),
    cache: 'no-store',
  });
  const text = await res.text();
  return new NextResponse(text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}

export async function PATCH(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  if (!payload.guardianId) {
    return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/guardians/${encodeURIComponent(payload.guardianId)}`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      relationship: payload.relationship,
      primary: payload.primary === true,
    }),
    cache: 'no-store',
  });
  const text = await res.text();
  return new NextResponse(text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
