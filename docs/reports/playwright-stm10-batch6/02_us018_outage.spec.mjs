/** Use portal during one integrated service outage (STM-10-US-018). */
export async function run({ page, base, step, note }) {
  step('Sign in as student via test-session on localhost');
  await page.goto(`${base}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1500);
  if (!page.url().includes('/student')) {
    throw new Error(
      `STOPPED at student sign-in: landed on ${page.url()} instead of /student (session cookie did not stick on localhost)`
    );
  }

  step('Open student dashboard and wait for portal aggregate panel');
  await page.goto(`${base}/student`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(2000);
  const aggregate = page.locator('[data-testid="student-portal-aggregate"]');
  await aggregate.waitFor({ state: 'visible', timeout: 20000 });
  step('Portal aggregate panel is visible');

  const partial = page.locator('[data-testid="student-portal-aggregate-partial"]');
  if (await partial.count()) {
    step('Partial-failure banner visible (one or more sections unavailable)');
  } else {
    note('All sections loaded in this run — outage simulation needs one downstream service stopped manually for full demo');
  }

  const retry = page.locator('[data-testid="student-portal-aggregate-retry"]');
  if (await retry.count()) {
    step('Click Retry failed sections');
    await retry.click();
    await page.waitForTimeout(2000);
  }

  step('Dashboard shell remains usable after aggregate load/retry');
  await page.locator('[data-testid="student-dashboard"]').waitFor({ state: 'visible', timeout: 10000 });
}
