import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { apiError, extractTraceId } from '@/lib/errors/api-error';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'student') {
    return apiError(req, { status: 403, code: 'FORBIDDEN', message: 'Student session required' });
  }
  const documentId = req.nextUrl.searchParams.get('documentId');
  if (!documentId) {
    return apiError(req, { status: 422, code: 'DOCUMENT_ID_REQUIRED', message: 'documentId is required' });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  headers['X-Trace-Id'] = traceId;
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/${encodeURIComponent(documentId)}/sharing-consent`, {
    headers,
    cache: 'no-store',
  });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { 'Content-Type': 'application/json', 'X-Trace-Id': traceId },
  });
}

export async function POST(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'student') {
    return apiError(req, { status: 403, code: 'FORBIDDEN', message: 'Student session required' });
  }
  const payload = await req.json();
  const documentId = String(payload?.documentId || '');
  const purpose = String(payload?.purpose || '');
  if (!documentId || !purpose) {
    return apiError(req, { status: 422, code: 'FIELDS_REQUIRED', message: 'documentId and purpose are required' });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  headers['Content-Type'] = 'application/json';
  headers['X-Trace-Id'] = traceId;
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/${encodeURIComponent(documentId)}/sharing-consent`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ purpose }),
  });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { 'Content-Type': 'application/json', 'X-Trace-Id': traceId },
  });
}
