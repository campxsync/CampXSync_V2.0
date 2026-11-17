/** Audit who changed accounts and roles (STM-07-US-012). */
export async function run({ page, base, stack, step, note }) {
  step('Platform Admin opens account/role audit panel');
  await page.goto(
    `${base}/api/auth/test-session?role=platform_admin&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1200);
  await page.locator('[data-testid="account-role-audit-panel"]').waitFor({ state: 'visible' });
  step('Audit panel visible (B3-D6 R01–R03)');

  if (stack.student8093 && stack.gateway8080) {
    step('Load audit via desk button');
    await page.locator('[data-testid="account-role-audit-load"]').click();
    await page.waitForTimeout(1500);
  } else {
    note('SKIPPED_MISSING_STACK: account-role-audit needs gateway :8080 and student-service :8093');
  }
}
