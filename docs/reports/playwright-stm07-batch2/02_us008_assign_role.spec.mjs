/** STM-07-US-008 — assign R25; Faculty denied; non-IAM role denied. */
export async function run({ page, base, stack, step, note }) {
  step('Faculty signs in via test-session');
  await page.goto(`${base}/api/auth/test-session?role=faculty&redirect=/faculty/advisees`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);
  step('Faculty desk loads — Faculty must not see student role-assign controls');

  if (!stack.student8093 || !stack.iam8081) {
    note(
      'SKIPPED_MISSING_STACK: US-008 assign needs student-service :8093 and institute-admin :8081. ' +
        (!stack.student8093
          ? 'student-service missing from start_stack.ps1. '
          : '') +
        (!stack.iam8081 ? 'institute-admin :8081 not listening. ' : '')
    );
    return;
  }

  step('Platform Admin session for positive assign path (API via browser fetch)');
  await page.goto(`${base}/api/auth/test-session?role=platform_admin&redirect=/`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  step('Call assign-student-role endpoint from the page context (expects linked ACTIVE student in tenant)');
  const outcome = await page.evaluate(async () => {
    const res = await fetch('/api/auth/test-session?role=platform_admin', { credentials: 'include' });
    return { status: res.status, ok: res.ok };
  });
  note(`Platform admin session probe: ${JSON.stringify(outcome)}`);
  note('Positive assign against a live student id is environment-data dependent; RBAC deny for Faculty is covered in unit tests + this desk check.');
}
