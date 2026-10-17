/**
 * Trial impersonation (`campx_test_session`) is allowed only on local/dev
 * and Vercel Preview. Vercel Production must never honor ENABLE_TEST_SESSION,
 * even if that flag was copied into Production project settings.
 */
export function isTestSessionAllowed(
  env: NodeJS.ProcessEnv = process.env
): boolean {
  if (env.VERCEL_ENV === 'production') {
    return false;
  }
  if (env.NODE_ENV !== 'production') {
    return true;
  }
  return env.ENABLE_TEST_SESSION === 'true';
}
