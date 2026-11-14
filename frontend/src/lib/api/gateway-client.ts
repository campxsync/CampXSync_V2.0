import { createClient } from '@/lib/supabase/server';
import { cookies } from 'next/headers';
import { extractAccessTokenFromCookies } from '@/lib/auth/jwt-verifier';

const GATEWAY_BASE_URL =
  process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

export class GatewayAuthError extends Error {
  status: number;
  code: string;

  constructor(message: string, status = 401, code = 'GATEWAY_MISSING_AUTH') {
    super(message);
    this.name = 'GatewayAuthError';
    this.status = status;
    this.code = code;
  }
}

/**
 * Resolves the caller's Supabase access token for Gateway Channel A (JWT forward).
 * Does not invent identity headers — the gateway verifies JWT and injects X-User-*.
 */
export async function resolveGatewayBearerToken(): Promise<string | null> {
  const supabase = await createClient();
  const { data } = await supabase.auth.getSession();
  if (data.session?.access_token) {
    return data.session.access_token;
  }

  try {
    const cookieStore = await cookies();
    return extractAccessTokenFromCookies(cookieStore);
  } catch {
    return null;
  }
}

export async function buildGatewayHeaders(
  extra?: HeadersInit,
  options?: { requireAuth?: boolean }
): Promise<Record<string, string>> {
  const requireAuth = options?.requireAuth !== false;
  const bearer = await resolveGatewayBearerToken();

  const headers: Record<string, string> = {
    Accept: 'application/json',
  };

  if (extra) {
    const h = new Headers(extra);
    h.forEach((value, key) => {
      // Never forward spoofable identity — gateway strips/injects these when auth is on.
      const lower = key.toLowerCase();
      if (
        lower === 'x-user-id' ||
        lower === 'x-tenant-id' ||
        lower === 'x-user-role' ||
        lower === 'authorization'
      ) {
        return;
      }
      headers[key] = value;
    });
  }

  if (bearer) {
    headers.Authorization = `Bearer ${bearer}`;
  } else if (requireAuth) {
    throw new GatewayAuthError(
      'Missing Supabase access token for API Gateway. Sign in or use JWT-backed test-session.',
      401,
      'GATEWAY_MISSING_AUTH'
    );
  }

  if (!headers['X-Trace-Id'] && !headers['x-trace-id']) {
    headers['X-Trace-Id'] = crypto.randomUUID();
  }

  return headers;
}

export async function fetchFromGateway(
  endpoint: string,
  options: RequestInit & { requireAuth?: boolean } = {}
): Promise<Response> {
  const { requireAuth, headers: extraHeaders, ...rest } = options;
  const headers = await buildGatewayHeaders(extraHeaders, { requireAuth });
  const url = endpoint.startsWith('http')
    ? endpoint
    : new URL(endpoint, GATEWAY_BASE_URL).toString();

  return fetch(url, {
    ...rest,
    headers,
    cache: rest.cache ?? 'no-store',
  });
}
