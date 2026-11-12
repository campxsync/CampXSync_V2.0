import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { apiError, extractTraceId } from '@/lib/errors/api-error';
import { appLogger } from '@/lib/logging/logger';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

function staffRole(sessionRole?: string | null) {
  if (sessionRole === 'platform_admin') {
    return 'INSTITUTION_ADMIN';
  }
  return 'COLLEGE_ADMIN';
}

export async function GET(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    return apiError(req, {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Authentication required to view document verification queue',
    });
  }

  if (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin') {
    return apiError(req, {
      status: 403,
      code: 'FORBIDDEN',
      message: 'College Admin or Institutional Officer role required to view verification queue',
    });
  }

  const role = staffRole(resolution.session.role);
  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  const status = req.nextUrl.searchParams.get('status');
  if (status) {
    headers['x-query-status'] = status;
  }

  appLogger.info('Retrieving document verification queue', { traceId, queryStatus: status || undefined });

  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/verifications`, {
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
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    appLogger.error('Gateway dispatch failed for verification queue', { traceId, error: errorMsg });
    return apiError(req, {
      status: 503,
      code: 'GATEWAY_UNAVAILABLE',
      message: 'Document service is temporarily unreachable',
    });
  }
}

export async function POST(req: NextRequest) {
  const traceId = extractTraceId(req);
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);

  if (!resolution.success) {
    return apiError(req, {
      status: 401,
      code: 'UNAUTHORIZED',
      message: 'Authentication required to verify documents',
    });
  }

  if (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin') {
    return apiError(req, {
      status: 403,
      code: 'FORBIDDEN',
      message: 'College Admin or Institutional Officer role required to verify documents',
    });
  }

  let bodyJson: {
    documentId?: string;
    decision?: string;
    verificationCode?: string;
    rejectionReason?: string;
    verificationNotes?: string;
  };

  try {
    bodyJson = await req.json();
  } catch {
    return apiError(req, {
      status: 400,
      code: 'INVALID_JSON',
      message: 'Malformed JSON payload in request body',
    });
  }

  if (!bodyJson.documentId) {
    return apiError(req, {
      status: 422,
      code: 'DOCUMENT_ID_REQUIRED',
      message: 'Document ID is required for verification decision',
    });
  }

  if (!bodyJson.decision || (bodyJson.decision !== 'APPROVE' && bodyJson.decision !== 'REJECT')) {
    return apiError(req, {
      status: 422,
      code: 'INVALID_DECISION',
      message: "Decision must be either 'APPROVE' or 'REJECT'",
    });
  }

  const role = staffRole(resolution.session.role);
  const payloadStr = JSON.stringify({
    decision: bodyJson.decision,
    verificationCode: bodyJson.verificationCode,
    rejectionReason: bodyJson.rejectionReason,
    verificationNotes: bodyJson.verificationNotes,
  });

  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;
  headers['Content-Type'] = 'application/json';

  appLogger.info('Submitting document verification decision', {
    traceId,
    documentId: bodyJson.documentId,
    decision: bodyJson.decision,
  });

  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/verifications/${bodyJson.documentId}/decision`, {
      method: 'POST',
      headers,
      body: payloadStr,
    });

    const resBody = await res.text();
    return new NextResponse(resBody, {
      status: res.status,
      headers: {
        'Content-Type': res.status >= 400 ? 'application/problem+json; charset=utf-8' : 'application/json',
        'X-Trace-Id': traceId,
        'X-Correlation-Id': traceId,
      },
    });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    appLogger.error('Gateway dispatch failed for verification decision', { traceId, error: errorMsg });
    return apiError(req, {
      status: 503,
      code: 'GATEWAY_UNAVAILABLE',
      message: 'Document service is temporarily unreachable',
    });
  }
}

