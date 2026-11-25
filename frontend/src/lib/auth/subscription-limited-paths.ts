/**
 * Paths Institution Admin (R03) may open when subscription gate denies full access
 * (ACTIVE institute + PENDING/missing subscription). Sign-out is always available via topbar.
 */
export const SUBSCRIPTION_LIMITED_ALLOWLIST = [
  '/institution/billing',
  '/institution/profile',
  '/access-denied',
  '/login',
] as const;

/** Exact home `/institution` is not allowlisted — R03 is sent to the subscription desk. */

export function isSubscriptionLimitedPathAllowed(pathname: string | null | undefined): boolean {
  if (!pathname) return false;
  return SUBSCRIPTION_LIMITED_ALLOWLIST.some(
    (p) => pathname === p || pathname.startsWith(p + '/')
  );
}
