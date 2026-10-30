/**
 * Focused gate tests for trial impersonation.
 * Run: node --experimental-strip-types scripts/verify_test_session_gate.mjs
 */
import { isTestSessionAllowed } from '../frontend/src/lib/auth/test-session-gate.ts';

const cases = [
  {
    name: 'local development allows impersonation without flag',
    env: { NODE_ENV: 'development' },
    expected: true,
  },
  {
    name: 'local NODE_ENV=production requires ENABLE_TEST_SESSION',
    env: { NODE_ENV: 'production' },
    expected: false,
  },
  {
    name: 'generic production with ENABLE_TEST_SESSION denies impersonation',
    env: { NODE_ENV: 'production', ENABLE_TEST_SESSION: 'true' },
    expected: false,
  },
  {
    name: 'Vercel Preview with ENABLE_TEST_SESSION allows impersonation',
    env: { NODE_ENV: 'production', VERCEL_ENV: 'preview', ENABLE_TEST_SESSION: 'true' },
    expected: true,
  },
  {
    name: 'Vercel Preview without ENABLE_TEST_SESSION denies impersonation',
    env: { NODE_ENV: 'production', VERCEL_ENV: 'preview' },
    expected: false,
  },
  {
    name: 'Vercel Production denies impersonation even when ENABLE_TEST_SESSION=true',
    env: { NODE_ENV: 'production', VERCEL_ENV: 'production', ENABLE_TEST_SESSION: 'true' },
    expected: false,
  },
  {
    name: 'Vercel Production denies impersonation when flag is absent',
    env: { NODE_ENV: 'production', VERCEL_ENV: 'production' },
    expected: false,
  },
];

let failed = 0;
for (const test of cases) {
  const actual = isTestSessionAllowed(test.env);
  const ok = actual === test.expected;
  if (!ok) failed += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${test.name}  (expected=${test.expected}, actual=${actual})`);
}

if (failed > 0) {
  console.error(`\n${failed}/${cases.length} FAILED`);
  process.exit(1);
}

console.log(`\n${cases.length}/${cases.length} PASSED`);
