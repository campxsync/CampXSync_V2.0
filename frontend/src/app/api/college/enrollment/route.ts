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
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  const view = req.nextUrl.searchParams.get('view') || 'terms';
  const path = view === 'enrolments' ? '/api/v1/enrollment/enrolments' : '/api/v1/enrollment/terms';
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, { method: 'GET', headers, cache: 'no-store' });
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
  const action = String(payload?.action || '');
  const headers = await getGatewayHeaders(req, supabase, resolution.session, staffRole(resolution.session.role));
  let path = '';
  let body: Record<string, unknown> = {};
  if (action === 'term') {
    path = '/api/v1/enrollment/terms';
    body = { yearCode: payload.yearCode, termCode: payload.termCode, name: payload.name };
  } else if (action === 'window') {
    path = '/api/v1/enrollment/windows';
    body = {
      termId: payload.termId,
      opensAt: payload.opensAt,
      closesAt: payload.closesAt,
      minCredits: Number(payload.minCredits),
      maxCredits: Number(payload.maxCredits),
    };
  } else if (action === 'course') {
    path = '/api/v1/enrollment/courses';
    body = {
      programId: payload.programId,
      termId: payload.termId,
      code: payload.code,
      credits: Number(payload.credits),
      core: Boolean(payload.core),
      prerequisites: payload.prerequisites || [],
    };
  } else if (action === 'section') {
    path = '/api/v1/enrollment/sections';
    body = { courseId: payload.courseId, capacity: Number(payload.capacity), facultyUserId: payload.facultyUserId };
  } else if (action === 'handover') {
    path = '/api/v1/enrollment/handovers';
    body = {
      applicationId: payload.applicationId,
      studentId: payload.studentId,
      programId: payload.programId,
      termId: payload.termId,
    };
  } else if (action === 'status') {
    path = `/api/v1/enrollment/enrolments/${payload.enrolmentId}/status`;
    body = { status: payload.status };
  } else if (action === 'eligibility') {
    if (!payload?.enrolmentId) {
      return NextResponse.json({ title: 'enrolmentId is required', status: 400 }, { status: 400 });
    }
    path = `/api/v1/enrollment/enrolments/${payload.enrolmentId}/eligibility`;
    body = {};
  } else {
    return NextResponse.json({ title: 'Unknown enrolment action', status: 400 }, { status: 400 });
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
