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
      message: 'Authentication required to resubmit documents',
    });
  }

  const role = resolution.session.role?.toUpperCase() || 'STUDENT';
  let bodyJson: any;
  try {
    bodyJson = await req.json();
  } catch {
    return apiError(req, {
      status: 400,
      code: 'INVALID_JSON',
      message: 'Malformed JSON payload',
    });
  }

  const { fileName, mimeType, sizeBytes, checksumSha256, changeNote, storagePath } = bodyJson || {};

  if (!documentId) {
    return apiError(req, {
      status: 422,
      code: 'MISSING_DOCUMENT_ID',
      message: 'Target documentId is required for resubmission',
    });
  }

  if (!changeNote || changeNote.trim().length < 3) {
    return apiError(req, {
      status: 422,
      code: 'INVALID_CHANGE_NOTE',
      message: 'Change note explaining the resubmission corrections must be at least 3 characters',
    });
  }

  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['Content-Type'] = 'application/json';
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  appLogger.info('Resubmitting rejected student document via params', {
    traceId,
    documentId,
    userId: resolution.session.userId,
    userRole: role,
  });

  const payloadStr = JSON.stringify({
    fileName,
    mimeType: mimeType || 'application/pdf',
    sizeBytes: sizeBytes || 1024,
    checksumSha256: checksumSha256 || 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    changeNote: changeNote.trim(),
    storagePath,
  });

  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/submissions/${documentId}/resubmit`, {
      method: 'POST',
      headers,
      body: payloadStr,
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
  } catch (err: any) {
    appLogger.error('Failed to connect to gateway for document resubmission', { traceId, error: err.message });
    return apiError(req, {
      status: 503,
      code: 'GATEWAY_UNAVAILABLE',
      message: 'Document service gateway is temporarily unreachable',
    });
  }
}

