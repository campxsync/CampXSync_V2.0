import { redirect } from 'next/navigation';
import { headers } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { isSubscriptionLimitedPathAllowed } from '@/lib/auth/subscription-limited-paths';

export const dynamic = 'force-dynamic';

/**
 * IADM-04 S5: Institution Admin with PENDING/missing subscription may only open
 * subscription desk + own profile (sign-out via topbar). Other institute routes redirect
 * to a clear access-denied message.
 */
export default async function InstitutionLayout({ children }: { children: React.ReactNode }) {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success) {
    redirect(`/access-denied?reason=${resolution.reason}`);
  }

  if (
    resolution.session.role === 'institution_admin' &&
    resolution.session.subscriptionAllowsAccess === false
  ) {
    const hdrs = await headers();
    const raw =
      hdrs.get('next-url') ||
      hdrs.get('x-url') ||
      hdrs.get('x-invoke-path') ||
      hdrs.get('x-matched-path') ||
      '';
    let pathname = '';
    try {
      pathname = raw.includes('://') ? new URL(raw).pathname : raw;
    } catch {
      pathname = raw;
    }
    // Fall back: Next.js often exposes the path via x-pathname when middleware sets it.
    if (!pathname) {
      pathname = hdrs.get('x-pathname') || '';
    }
    // When path is unknown, allow children (billing/profile pages self-identify); client layout still guards.
    if (pathname && pathname.startsWith('/institution') && !isSubscriptionLimitedPathAllowed(pathname)) {
      redirect('/access-denied?reason=subscription_inactive');
    }
  }

  return children;
}
