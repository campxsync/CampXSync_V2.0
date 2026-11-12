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
  if (!resolution.success) {
    return apiError(req, {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Authentication required to view stage document checklist',
    });
  }

  const role = resolution.session.role?.toUpperCase() || 'STUDENT';
  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  const stage = req.nextUrl.searchParams.get('stage');
  if (stage) {
    headers['x-query-stage'] = stage;
  }
  const studentId = req.nextUrl.searchParams.get('studentId');
  if (studentId) {
    headers['x-query-student-id'] = studentId;
  }
  const programId = req.nextUrl.searchParams.get('programId');
  if (programId) {
    headers['x-query-program-id'] = programId;
  }

  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/submissions/checklist`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });

  const body = await res.text();
  return new NextResponse(body, {
    status: res.status,
    headers: {
      'Content-Type': res.status >= 400 ? 'application/problem+json; charset=utf-8' : 'application/json',
      'X-Trace-Id': traceId,
      'X-Correlation-Id': traceId,
    },
  });
}

