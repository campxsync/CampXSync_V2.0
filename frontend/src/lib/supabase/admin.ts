import { createClient } from '@supabase/supabase-js';

/**
 * Server-only Supabase Auth admin helper.
 * Import this module only from server route handlers.
 * The service role key is read from SUPABASE_SERVICE_ROLE_KEY and must never
 * be copied into a NEXT_PUBLIC variable, logged, or hardcoded.
 */
if (typeof window !== 'undefined') {
  throw new Error('Supabase admin client is server-only.');
}
export type AuthInviteResult =
  | { authInvite: 'NOT_CONFIGURED' }
  | { authInvite: 'INVITED'; userId: string }
  | { authInvite: 'FAILED'; authInviteError: string };

export async function inviteCollegeAdmin(email: string): Promise<AuthInviteResult> {
  const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceRoleKey) {
    return { authInvite: 'NOT_CONFIGURED' };
  }

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  if (!url) {
    return {
      authInvite: 'FAILED',
      authInviteError: 'NEXT_PUBLIC_SUPABASE_URL is not configured.',
    };
  }

  try {
    const admin = createClient(url, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data, error } = await admin.auth.admin.inviteUserByEmail(email);
    if (error) {
      return {
        authInvite: 'FAILED',
        authInviteError: error.message || 'Auth invite failed.',
      };
    }
    if (!data.user?.id) {
      return {
        authInvite: 'FAILED',
        authInviteError: 'Auth invite did not return a user id.',
      };
    }
    return { authInvite: 'INVITED', userId: data.user.id };
  } catch (err) {
    const authInviteError = err instanceof Error ? err.message : 'Auth invite failed.';
    return { authInvite: 'FAILED', authInviteError };
  }
}
