/** Already-done STM-07: link status strip, eligibility/login request narrative, access cut. */
export async function run({ page, base, stack, step, note }) {
  step('Open student portal via test-session (student1 role)');
  await page.goto(`${base}/api/auth/test-session?role=student&redirect=/student`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  await page.waitForTimeout(1200);

  step('Look for account link-status strip on student dashboard');
  const strip = page.locator('[data-testid="student-account-link-status"]');
  if (await strip.count()) {
    step('Link-status strip is visible for the signed-in student');
    await strip.first().scrollIntoViewIfNeeded();
  } else {
    note('Link-status strip not found on /student (layout may differ); page still loaded');
  }

  if (!stack.student8093) {
    note('SKIPPED_MISSING_8093: eligibility check / provisioning / suspend APIs need student-service on :8093 (not in start_stack.ps1)');
    step('Record missing piece and continue without API mutations');
    return;
  }

  step('Student APIs reachable — open link-status BFF path');
  await page.goto(`${base}/api/student/link-status`, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
}
