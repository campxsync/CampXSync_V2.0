import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { apiError, extractTraceId } from '@/lib/errors/api-error';
import { appLogger } from '@/lib/logging/logger';
import { guardDocumentUploadPayload } from '@/lib/documents/upload-payload-guard';

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
  let bodyJson: Record<string, unknown>;
  try {
    bodyJson = await req.json();
  } catch {
    return apiError(req, {
      status: 400,
      code: 'INVALID_JSON',
      message: 'Malformed JSON payload',
    });
  }

  if (!documentId) {
    return apiError(req, {
      status: 422,
      code: 'MISSING_DOCUMENT_ID',
      message: 'Target documentId is required for resubmission',
    });
  }

  const changeNote = typeof bodyJson.changeNote === 'string' ? bodyJson.changeNote : '';
  if (!changeNote.trim() || changeNote.trim().length < 3) {
    return apiError(req, {
      status: 422,
      code: 'INVALID_CHANGE_NOTE',
      message: 'Change note explaining the resubmission corrections must be at least 3 characters',
    });
  }

  const guard = guardDocumentUploadPayload(bodyJson);
  if (!guard.ok) {
    return apiError(req, {
      status: guard.status,
      code: guard.code,
      message: guard.message,
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
    sizeBytes: guard.sizeBytes,
    mimeType: guard.mimeType,
  });

  const payloadStr = JSON.stringify({
    fileName: bodyJson.fileName,
    mimeType: guard.mimeType,
    sizeBytes: guard.sizeBytes,
    checksumSha256: bodyJson.checksumSha256,
    contentBase64: bodyJson.contentBase64,
    changeNote: changeNote.trim(),
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
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'gateway unreachable';
    appLogger.error('Failed to connect to gateway for document resubmission', { traceId, error: message });
    return apiError(req, {
      status: 503,
      code: 'GATEWAY_UNAVAILABLE',
      message: 'Document service gateway is temporarily unreachable',
    });
  }
}
