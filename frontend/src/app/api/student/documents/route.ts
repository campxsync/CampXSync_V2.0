import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { apiError, extractTraceId } from '@/lib/errors/api-error';
import { appLogger } from '@/lib/logging/logger';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function GET(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return apiError(req, {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Authentication required to view student documents',
    });
  }

  const role = resolution.session.role?.toUpperCase() || 'STUDENT';
  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  const status = req.nextUrl.searchParams.get('status');
  if (status) {
    headers['x-query-status'] = status;
  }
  const studentId = req.nextUrl.searchParams.get('studentId');
  if (studentId) {
    headers['x-query-student-id'] = studentId;
  }

  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/submissions`, {
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

export async function POST(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return apiError(req, {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Authentication required to upload documents',
    });
  }

  const role = resolution.session.role?.toUpperCase() || 'STUDENT';
  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['Content-Type'] = 'application/json';
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  const payload = await req.json();

  appLogger.audit('student_document.upload', `Uploading document title='${payload.title}' docType=${payload.documentTypeId}`, {
    traceId,
    tenantId: resolution.session.tenantId || undefined,
    userId: resolution.session.userId,
    userRole: role,
    route: '/api/student/documents',
  });

  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/submissions`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
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

