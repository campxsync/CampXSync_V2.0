import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://127.0.0.1:8080';

async function resolveStudentRecordId(
  req: NextRequest,
  supabase: Awaited<ReturnType<typeof createClient>>,
  session: { role?: string | null; tenantId?: string | null; userId?: string | null },
  headers: Record<string, string>
): Promise<string | null> {
  const meRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  if (meRes.ok) {
    try {
      const me = (await meRes.json()) as { id?: string; studentId?: string };
      const id = me.id || me.studentId;
      if (id) {
        return String(id);
      }
    } catch {
      // fall through
    }
  }
  return session.userId || null;
}

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'student') {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  const studentId = await resolveStudentRecordId(req, supabase, resolution.session, headers);
  if (!studentId) {
    return NextResponse.json({ title: 'Student record id is required', status: 422 }, { status: 422 });
  }
  headers['X-Student-Record-Id'] = studentId;
  const view = req.nextUrl.searchParams.get('view') || 'guardians';
  let path = `/api/v1/guardians/students/${encodeURIComponent(studentId)}`;
  if (view === 'emergency-contacts') {
    path = `/api/v1/guardians/students/${encodeURIComponent(studentId)}/emergency-contacts`;
  } else if (view === 'history') {
    path = `/api/v1/guardians/students/${encodeURIComponent(studentId)}/relationship-history`;
  }
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { 'Content-Type': 'application/json', 'X-Resolved-Student-Id': studentId },
  });
}

export async function POST(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'student') {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  const studentId = await resolveStudentRecordId(req, supabase, resolution.session, headers);
  if (!studentId) {
    return NextResponse.json({ title: 'Student record id is required', status: 422 }, { status: 422 });
  }
  headers['X-Student-Record-Id'] = studentId;
  const action = String(payload?.action || '');
  const guardianId = String(payload?.guardianId || '');
  if (!guardianId) {
    return NextResponse.json({ title: 'Guardian id is required', status: 422 }, { status: 422 });
  }
  let path = '';
  let body: Record<string, unknown> = {};
  if (action === 'set-period') {
    path = `/api/v1/guardians/${encodeURIComponent(guardianId)}/period`;
    body = { validFrom: payload.validFrom, validTo: payload.validTo };
  } else if (action === 'revoke-access') {
    path = `/api/v1/guardians/${encodeURIComponent(guardianId)}/revoke`;
    body = {};
  } else {
    return NextResponse.json({ title: 'Unsupported action', status: 422 }, { status: 422 });
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
