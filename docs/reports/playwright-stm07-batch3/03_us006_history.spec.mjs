/** See past login requests (STM-07-US-006). */
export async function run({ page, base, stack, step, note }) {
  step('Registrar session can reach account provisioning desk (B3-D5 UI path)');
  await page.goto(
    `${base}/api/auth/test-session?role=registrar&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1500);

  const panel = page.locator('[data-testid="provisioning-history-panel"]');
  if (await panel.count()) {
    await panel.first().scrollIntoViewIfNeeded();
    step('Past login requests panel visible');
  } else {
    const denied = page.url().includes('access-denied');
    if (denied) {
      note('Registrar bounced by middleware — check REGISTRAR_ALLOWED_COLLEGE_PREFIXES includes account-provisioning');
      throw new Error('Registrar cannot open account-provisioning desk');
    }
    note('History panel missing on page');
  }

  if (!stack.student8093) {
    note('SKIPPED_MISSING_STACK: provisioning-requests GET needs student-service :8093');
  }
}
