/**
 * CampXSync Frontend Structured Logger
 * Conforms to the enterprise logging architecture. Emits structured JSON logs
 * with trace correlation, security redaction, and optional remote dispatch to
 * the Logger API server (port 9898).
 */

const LOG_SERVER_URL = process.env.LOGGER_API_URL || 'http://localhost:9898/api/v1/logs';
const SERVICE_NAME = 'frontend';

export interface LogContext {
  traceId?: string;
  tenantId?: string;
  userId?: string;
  userRole?: string;
  route?: string;
  operation?: string;
  durationMs?: number;
  status?: number;
  [key: string]: unknown;
}

const REDACTED_KEYS = new Set([
  'password',
  'token',
  'jwt',
  'secret',
  'authorization',
  'access_token',
  'refresh_token',
  'service_role_key',
  'anon_key',
]);

function maskSensitiveData(obj: unknown): unknown {
  if (obj === null || obj === undefined) return obj;
  if (typeof obj === 'string') {
    // Redact Bearer tokens
    return obj.replace(/Bearer\s+[A-Za-z0-9-_=.]+/gi, 'Bearer [REDACTED]');
  }
  if (Array.isArray(obj)) {
    return obj.map(maskSensitiveData);
  }
  if (typeof obj === 'object') {
    const copy: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
      if (REDACTED_KEYS.has(k.toLowerCase())) {
        copy[k] = '[REDACTED]';
      } else {
        copy[k] = maskSensitiveData(v);
      }
    }
    return copy;
  }
  return obj;
}

function emitStructured(
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'AUDIT',
  message: string,
  context?: LogContext,
  err?: unknown
) {
  const timestamp = new Date().toISOString();
  const traceId = context?.traceId || 'unassigned';
  const tenantId = context?.tenantId || 'global';
  const userId = context?.userId || 'anonymous';

  const entry: Record<string, unknown> = {
    timestamp,
    level,
    service: SERVICE_NAME,
    traceId,
    tenantId,
    userId,
    route: context?.route,
    operation: context?.operation,
    durationMs: context?.durationMs,
    status: context?.status,
    message,
  };

  if (err instanceof Error) {
    entry.errorName = err.name;
    entry.errorMessage = err.message;
    // Don't leak stack traces into client-facing logs in production
    if (process.env.NODE_ENV !== 'production') {
      entry.stack = err.stack;
    }
  }

  const cleanEntry = maskSensitiveData(entry) as Record<string, unknown>;
  const logLine = JSON.stringify(cleanEntry);

  // Write structured JSON to stdout/stderr
  if (level === 'ERROR') {
    process.stderr.write(logLine + '\n');
  } else {
    process.stdout.write(logLine + '\n');
  }

  // Non-blocking asynchronous dispatch to Logger API server if available
  if (typeof fetch === 'function' && process.env.NODE_ENV !== 'test') {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 150);
      fetch(LOG_SERVER_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          level,
          logger: `frontend.${context?.route || 'api'}`,
          message: `${message} [traceId=${traceId}]`,
          flowId: traceId,
          operation: context?.operation || context?.route,
        }),
        signal: controller.signal,
      }).catch(() => {})
        .finally(() => clearTimeout(timer));
    } catch {}
  }
}

export const appLogger = {
  info(message: string, context?: LogContext) {
    emitStructured('INFO', message, context);
  },
  warn(message: string, context?: LogContext) {
    emitStructured('WARN', message, context);
  },
  error(message: string, err?: unknown, context?: LogContext) {
    emitStructured('ERROR', message, context, err);
  },
  audit(operation: string, message: string, context?: LogContext) {
    emitStructured('AUDIT', `AUDIT [${operation}] ${message}`, {
      ...context,
      operation,
    });
  },
};

