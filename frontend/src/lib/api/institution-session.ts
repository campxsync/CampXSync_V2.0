import { NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { GatewayAuthError, fetchFromGateway } from '@/lib/api/gateway-client';

export type InstitutionSessionOk = { ok: true; tenantId: string };
export type InstitutionSessionFail = { ok: false; response: NextResponse };

/** Institution Admin (R03) or Platform Admin with linked tenant — IADM-01 S2 BFF guard. */
export async function requireInstitutionAdmin(): Promise<InstitutionSessionOk | InstitutionSessionFail> {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    return {
      ok: false,
      response: NextResponse.json(
        { errorCode: 'UNAUTHENTICATED', detail: 'Sign in required.' },
        { status: 401 }
      ),
    };
  }
  if (resolution.session.role !== 'institution_admin' && resolution.session.role !== 'platform_admin') {
    return {
      ok: false,
      response: NextResponse.json(
        { errorCode: 'FORBIDDEN', detail: 'You are not authorized for this action.' },
        { status: 403 }
      ),
    };
  }
  const tenantId = resolution.session.tenantId;
  if (!tenantId) {
    return {
      ok: false,
      response: NextResponse.json(
        { errorCode: 'NO_INSTITUTE', detail: 'No institute is linked to this login.' },
        { status: 400 }
      ),
    };
  }
  return { ok: true, tenantId };
}

export async function forwardGateway(res: Response): Promise<NextResponse> {
  const text = await res.text();
  let body: unknown = text;
  try {
    body = JSON.parse(text);
  } catch {
    /* keep raw */
  }
  if (!res.ok && typeof body !== 'object') {
    body = { errorCode: 'UNAVAILABLE', detail: 'unavailable' };
  }
  return NextResponse.json(body, { status: res.status });
}

export async function gatewayFetch(
  path: string,
  init: RequestInit & { requireAuth?: boolean }
): Promise<NextResponse> {
  try {
    const res = await fetchFromGateway(path, { requireAuth: true, ...init });
    return forwardGateway(res);
  } catch (err) {
    if (err instanceof GatewayAuthError) {
      return NextResponse.json({ errorCode: err.code, detail: err.message }, { status: err.status });
    }
    return NextResponse.json({ errorCode: 'UNAVAILABLE', detail: 'unavailable' }, { status: 503 });
  }
}