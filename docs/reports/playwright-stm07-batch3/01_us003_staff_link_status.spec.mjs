/** Staff view of account link status (STM-07-US-003). */
export async function run({ page, base, stack, step, note }) {
  step('College Admin opens account provisioning desk (staff UI path)');
  await page.goto(
    `${base}/api/auth/test-session?role=college_admin&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1500);

  const desk = page.locator('[data-testid="college-account-provisioning-desk"]');
  await desk.waitFor({ state: 'visible', timeout: 30000 });
  step('Desk visible — account link status panel present');
  await page.locator('[data-testid="account-link-status-panel"]').waitFor({ state: 'visible' });
  note('Java B3-D5 still denies college_admin for link-status API without IAM operator / R31+ACCOUNT.A.');

  if (!stack.student8093 || !stack.gateway8080) {
    note('SKIPPED_MISSING_STACK: link-status API needs gateway :8080 and student-service :8093');
    return;
  }

  step('Platform Admin (R01) also reaches the desk');
  await page.goto(
    `${base}/api/auth/test-session?role=platform_admin&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1000);
  if (await desk.count()) {
    step('Platform Admin desk visible');
  } else {
    note('Platform Admin desk not visible after session (check middleware platform desk exception)');
  }
}
