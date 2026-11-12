import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { getCachedReference, setCachedReference, invalidateReferenceTag } from '@/lib/cache/reference-cache';
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
      message: 'Authentication required to view document types',
    });
  }

  const tenantId = resolution.session.tenantId || 'global';
  const category = req.nextUrl.searchParams.get('category');
  const activeOnly = req.nextUrl.searchParams.get('activeOnly');
  const cacheKey = `doctypes:${tenantId}:${category || 'all'}:${activeOnly || 'all'}`;
  const cached = getCachedReference(cacheKey);
  if (cached) {
    return NextResponse.json(cached, {
      status: 200,
      headers: {
        'X-Trace-Id': traceId,
        'X-Correlation-Id': traceId,
      },
    });
  }

  const role = staffRole(resolution.session.role);
  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  if (category) {
    headers['x-query-category'] = category;
  }
  if (activeOnly) {
    headers['x-query-active-only'] = activeOnly;
  }

  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/types`, {
    method: 'GET',
    headers,
    cache: 'no-store',
  });

  const body = await res.text();
  if (res.ok) {
    try {
      const json = JSON.parse(body);
      setCachedReference(cacheKey, json);
    } catch (e) {
      appLogger.warn('Failed to parse cached document types response', { traceId });
    }
  }

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
  if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
    return apiError(req, {
      status: 403,
      code: 'FORBIDDEN',
      message: 'College Admin or Registrar role required to configure document types',
    });
  }

  const role = staffRole(resolution.session.role);
  const headers = await getGatewayHeaders(req, supabase, resolution.session, role);
  headers['Content-Type'] = 'application/json';
  headers['X-Trace-Id'] = traceId;
  headers['X-Correlation-Id'] = traceId;

  const payload = await req.json();

  appLogger.audit('document_type.configure', `Configuring document type code=${payload.code} name=${payload.name}`, {
    traceId,
    tenantId: resolution.session.tenantId || undefined,
    userId: resolution.session.userId,
    userRole: role,
  });

  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/documents/types`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload),
    cache: 'no-store',
  });

  const tenantId = resolution.session.tenantId || 'global';
  invalidateReferenceTag(`doctypes:${tenantId}`);
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
