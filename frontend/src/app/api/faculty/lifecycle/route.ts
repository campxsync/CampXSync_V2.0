import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(req: NextRequest) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'faculty') {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'FACULTY');
  const view = req.nextUrl.searchParams.get('view') || 'advisees';
  let path = '/api/v1/lifecycle/advisees';
  if (view === 'requests') {
    path = '/api/v1/lifecycle/advisor/requests';
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
  if (!resolution.success || resolution.session.role !== 'faculty') {
    return NextResponse.json({ title: 'Forbidden', status: 403 }, { status: 403 });
  }
  const payload = await req.json();
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'FACULTY');
  const action = String(payload?.action || 'endorse');
  let path = `/api/v1/lifecycle/requests/${payload.requestId}/endorse`;
  let body: Record<string, unknown> = {
    endorsement: payload.endorsement || 'RECOMMENDED',
    remarks: payload.remarks,
  };
  const res = await fetch(`${GATEWAY_BASE_URL}${path}`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    cache: 'no-store',
  });
  const text = await res.text();
  return new NextResponse(text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
