/**
 * Trial impersonation (`campx_test_session`) is strictly hard-disabled in production
 * and deployable builds. It cannot be enabled even if ENABLE_TEST_SESSION is set in production.
 */
export function isTestSessionAllowed(
  env: NodeJS.ProcessEnv = process.env
): boolean {
  if (
    env.VERCEL_ENV === 'production' ||
    env.NODE_ENV === 'production' ||
    env.NEXT_PUBLIC_ENVIRONMENT === 'production' ||
    env.DEPLOYMENT_ENV === 'production'
  ) {
    return false;
  }
  return env.ENABLE_TEST_SESSION === 'true';
}
