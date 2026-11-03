import { NextRequest } from 'next/server';
import { SupabaseClient } from '@supabase/supabase-js';

export async function getGatewayHeaders(
  req: NextRequest,
  supabase: SupabaseClient,
  session: { tenantId?: string | null; userId?: string | null; collegeId?: string | null; role?: string },
  customRole?: string
): Promise<Record<string, string>> {
  const { data: { session: authSession } } = await supabase.auth.getSession();
  const bearerToken = authSession?.access_token || req.headers.get('authorization')?.replace(/^Bearer\s+/i, '');

  const headers: Record<string, string> = {
    'Accept': 'application/json',
  };

  if (bearerToken) {
    headers['Authorization'] = `Bearer ${bearerToken}`;
  }

  if (session.tenantId) {
    headers['X-Tenant-Id'] = session.tenantId;
  }
  if (session.userId) {
    headers['X-User-Id'] = session.userId;
  }
  if (session.collegeId) {
    headers['X-College-Id'] = session.collegeId;
  }
  if (customRole) {
    headers['X-User-Role'] = customRole;
  } else if (session.role) {
    headers['X-User-Role'] = session.role === 'student' ? 'APPLICANT' : session.role.toUpperCase();
  }

  const corrId = req.headers.get('x-correlation-id');
  if (corrId) {
    headers['X-Correlation-Id'] = corrId;
  }

  return headers;
}

