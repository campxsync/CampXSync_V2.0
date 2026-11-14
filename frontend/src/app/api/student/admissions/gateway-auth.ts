import { NextRequest } from 'next/server';
import { SupabaseClient } from '@supabase/supabase-js';
import { extractAccessTokenFromCookies } from '@/lib/auth/jwt-verifier';
import { cookies } from 'next/headers';

/**
 * Builds Gateway Channel-A headers: Authorization Bearer only.
 * Does NOT send spoofable X-User-Id / X-Tenant-Id / X-User-Role — the gateway
 * verifies the JWT and injects those downstream under HMAC.
 */
export async function getGatewayHeaders(
  req: NextRequest,
  supabase: SupabaseClient,
  _session: { tenantId?: string | null; userId?: string | null; collegeId?: string | null; role?: string },
  _customRole?: string
): Promise<Record<string, string>> {
  const { data: { session: authSession } } = await supabase.auth.getSession();
  let bearerToken = authSession?.access_token || req.headers.get('authorization')?.replace(/^Bearer\s+/i, '') || '';

  if (!bearerToken) {
    try {
      const cookieStore = await cookies();
      bearerToken = extractAccessTokenFromCookies(cookieStore) || '';
    } catch {
      // ignore — BFF without cookie store
    }
  }

  if (!bearerToken) {
    throw new Error('GATEWAY_MISSING_AUTH: No Supabase access token available for API Gateway');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${bearerToken}`,
  };

  const corrId = req.headers.get('x-correlation-id') || req.headers.get('x-trace-id');
  if (corrId) {
    headers['X-Correlation-Id'] = corrId;
    headers['X-Trace-Id'] = corrId;
  }

  return headers;
}
