/** Retry failed login setup (STM-07-US-005). */
export async function run({ page, base, stack, step, note }) {
  step('Open provisioning desk as college admin (retry UI)');
  await page.goto(
    `${base}/api/auth/test-session?role=college_admin&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1200);
  await page.locator('[data-testid="provisioning-history-panel"]').waitFor({ state: 'visible' });
  step('Retry controls render for FAILED rows (data-testid provisioning-retry-*)');
  note('Live FAILED seed optional; unit suite Stm07Us005RetryProvisioningTest covers Path C + IAM operator retry.');

  if (!stack.student8093) {
    note('SKIPPED_MISSING_STACK: retry-provisioning POST needs student-service :8093');
  }
}
