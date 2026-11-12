import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { apiError, extractTraceId } from '@/lib/errors/api-error';
import { appLogger } from '@/lib/logging/logger';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const traceId = extractTraceId(req);
  const { id: documentId } = await params;
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    return apiError(req, {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Authentication required to log document access',
    });
  }

  const role = resolution.session.role?.toUpperCase() || 'STUDENT';
  let bodyJson: any = {};
  try {
    bodyJson = await req.json();
  } catch {
    // empty body is acceptable; defaults used
  }

  const channel = bodyJson?.channel || 'DOWNLOAD';
  const clientIp = req.headers.get('x-forwarded-for') || req.headers.get('x-real-ip') || '127.0.0.1';

  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['Content-Type'] = 'application/json';
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  appLogger.info('Recording regulatory document access log', {
    traceId,
    documentId,
    channel,
    userId: resolution.session.userId,
    userRole: role,
  });

  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/${documentId}/access-log`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        channel,
        clientIp,
      }),
    });

    const data = await res.json();
    return NextResponse.json(data, {
      status: res.status,
      headers: {
        'x-trace-id': traceId,
      },
    });
  } catch (err: any) {
    appLogger.error('Failed to communicate with Document Service for access logging', {
      traceId,
      error: err.message,
    });
    return apiError(req, {
      status: 502,
      code: 'GATEWAY_ERROR',
      message: 'Unable to record document access log through gateway',
    });
  }
}

