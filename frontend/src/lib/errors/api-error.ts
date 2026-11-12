import { NextRequest, NextResponse } from 'next/server';
import { appLogger } from '@/lib/logging/logger';

export interface ApiErrorOptions {
  status: number;
  code: string;
  title?: string;
  message: string;
  details?: unknown;
}

function defaultTitle(status: number): string {
  switch (status) {
    case 400: return 'Bad Request';
    case 401: return 'Unauthorized';
    case 403: return 'Forbidden';
    case 404: return 'Not Found';
    case 409: return 'Conflict';
    case 422: return 'Unprocessable Entity';
    case 502: return 'Bad Gateway';
    case 503: return 'Service Unavailable';
    case 504: return 'Gateway Timeout';
    default: return status >= 500 ? 'Internal Server Error' : 'Error';
  }
}

export function extractTraceId(req: NextRequest | Headers): string {
  if ('headers' in req) {
    const fromReq = req.headers.get('x-trace-id') || req.headers.get('x-correlation-id');
    if (fromReq && fromReq.trim()) return fromReq.trim();
  } else if (req instanceof Headers) {
    const fromHdr = req.get('x-trace-id') || req.get('x-correlation-id');
    if (fromHdr && fromHdr.trim()) return fromHdr.trim();
  }
  return crypto.randomUUID().replace(/-/g, '');
}

/**
 * Standard RFC 7807 problem+json error response helper.
 * Emits consistent error envelope and structured logs across Next.js API routes.
 */
export function apiError(
  req: NextRequest,
  opts: ApiErrorOptions,
  context?: { tenantId?: string; userId?: string; userRole?: string }
): NextResponse {
  const traceId = extractTraceId(req);
  const path = req.nextUrl?.pathname || '';
  const title = opts.title || defaultTitle(opts.status);

  // Log error via structured logger
  const logCtx = {
    traceId,
    tenantId: context?.tenantId,
    userId: context?.userId,
    userRole: context?.userRole,
    route: path,
    status: opts.status,
  };

  if (opts.status >= 500) {
    appLogger.error(`API Error ${opts.status} [${opts.code}]: ${opts.message}`, null, logCtx);
  } else {
    appLogger.warn(`API Client Error ${opts.status} [${opts.code}]: ${opts.message}`, logCtx);
  }

  const errorBody = {
    timestamp: Date.now(),
    status: opts.status,
    title,
    error: title,
    code: opts.code,
    errorCode: opts.code,
    message: opts.message,
    path,
    traceId,
    correlationId: traceId,
    ...(opts.details ? { details: opts.details } : {}),
  };

  const response = NextResponse.json(errorBody, {
    status: opts.status,
    headers: {
      'Content-Type': 'application/problem+json; charset=utf-8',
      'X-Trace-Id': traceId,
      'X-Correlation-Id': traceId,
    },
  });

  return response;
}

/**
 * Injects trace headers into successful JSON responses.
 */
export function apiSuccess<T>(req: NextRequest, data: T, status = 200, extraHeaders?: Record<string, string>): NextResponse {
  const traceId = extractTraceId(req);
  return NextResponse.json(data, {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'X-Trace-Id': traceId,
      'X-Correlation-Id': traceId,
      ...(extraHeaders || {}),
    },
  });
}

