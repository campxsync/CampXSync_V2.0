import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { apiError, extractTraceId } from '@/lib/errors/api-error';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function POST(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'student') {
    return apiError(req, { status: 403, code: 'FORBIDDEN', message: 'Student session required' });
  }
  const payload = await req.json().catch(() => ({}));
  const documentId = String(payload?.documentId || '');
  if (!documentId) {
    return apiError(req, { status: 422, code: 'DOCUMENT_ID_REQUIRED', message: 'documentId is required' });
  }
  const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
  headers['Content-Type'] = 'application/json';
  headers['X-Trace-Id'] = traceId;
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/${encodeURIComponent(documentId)}/signed-url`, {
    method: 'POST',
    headers,
    body: JSON.stringify({}),
  });
  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: { 'Content-Type': 'application/json', 'X-Trace-Id': traceId },
  });
}
