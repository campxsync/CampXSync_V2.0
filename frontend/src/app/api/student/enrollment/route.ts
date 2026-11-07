import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

async function withStudentRecordHeader(
  req: NextRequest,
  supabase: Awaited<ReturnType<typeof createClient>>,
  session: { tenantId?: string | null; userId?: string | null; collegeId?: string | null; role?: string },
) {
  const headers = await getGatewayHeaders(req, supabase, session, 'STUDENT');
  try {
    const meRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });
    if (meRes.ok) {
      const me = await meRes.json();
      const studentId = me?.id || me?.studentId;
      if (studentId) {
        headers['X-Student-Record-Id'] = String(studentId);
      }
    }
  } catch {
    // Enrolment APIs still work when auth user id equals student record id.
  }
  return headers;
}

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || !resolution.session.tenantId) {
    return NextResponse.json({ title: 'Unauthenticated', status: 401 }, { status: 401 });
  }
  const headers = await withStudentRecordHeader(req, supabase, resolution.session);
  const enrolmentId = req.nextUrl.searchParams.get('enrolmentId');
  const view = req.nextUrl.searchParams.get('view') || (enrolmentId ? 'enrolment' : 'mine');

  let path = '/api/v1/enrollment/enrolments';
  if (view === 'alerts') {
    path = '/api/v1/enrollment/alerts';
  } else if (view === 'mine' || !enrolmentId) {
    path = '/api/v1/enrollment/enrolments';
  } else if (view === 'catalogue') {
    path = `/api/v1/enrollment/enrolments/${enrolmentId}/catalogue`;
  } else if (view === 'courses') {
    path = `/api/v1/enrollment/enrolments/${enrolmentId}/courses`;
  } else if (view === 'history') {
    path = `/api/v1/enrollment/enrolments/${enrolmentId}/history`;
  } else if (view === 'sections') {
    const courseId = req.nextUrl.searchParams.get('courseId');
    if (!courseId) {
      return NextResponse.json({ title: 'courseId is required for sections view', status: 400 }, { status: 400 });
    }
    path = `/api/v1/enrollment/enrolments/${enrolmentId}/courses/${courseId}/sections`;
  } else {
    path = `/api/v1/enrollment/enrolments/${enrolmentId}`;
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
  const enrolmentId = payload?.enrolmentId;
  if (!enrolmentId) {
    return NextResponse.json({ title: 'enrolmentId is required', status: 400 }, { status: 400 });
  }
  const action = String(payload?.action || 'register');
  const headers = await withStudentRecordHeader(req, supabase, resolution.session);
  const idempotencyKey = req.headers.get('idempotency-key') || payload?.idempotencyKey;
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }
  let path = `/api/v1/enrollment/enrolments/${enrolmentId}/register`;
  let requestBody: Record<string, unknown> = { selections: payload.selections };
  if (action === 'drop') {
    path = `/api/v1/enrollment/enrolments/${enrolmentId}/drop`;
    requestBody = { courseId: payload.courseId };
  } else if (action === 'swap') {
    path = `/api/v1/enrollment/enrolments/${enrolmentId}/swap`;
    requestBody = { courseId: payload.courseId, sectionId: payload.sectionId };
  } else if (action === 'waitlist') {
    path = '/api/v1/enrollment/waitlist/join';
    requestBody = { enrolmentId, sectionId: payload.sectionId };
  } else if (!Array.isArray(payload.selections) || payload.selections.length === 0) {
    return NextResponse.json({ title: 'At least one course section selection is required', status: 400 }, { status: 400 });
  }
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify(requestBody),
    cache: 'no-store',
  });
  const responseText = await res.text();
  return new NextResponse(responseText, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
