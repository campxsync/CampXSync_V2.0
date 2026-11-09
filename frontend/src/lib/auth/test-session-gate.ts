/**
 * Trial impersonation (`campx_test_session`) is a local and Vercel Preview
 * tool. A production Node process, including `next start` on a public host,
 * must not honor ENABLE_TEST_SESSION. Vercel Production never honors it.
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
  return env.VERCEL_ENV === 'preview' && env.ENABLE_TEST_SESSION === 'true';
}
