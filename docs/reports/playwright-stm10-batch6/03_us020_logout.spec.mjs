/** Log out securely (STM-10-US-020). */
export async function run({ page, base, step, note }) {
  step('Sign in as student via test-session on localhost');
  await page.goto(`${base}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1500);
  if (!page.url().includes('/student')) {
    throw new Error(
      `STOPPED at student sign-in: landed on ${page.url()} instead of /student`
    );
  }

  step('Open student dashboard');
  await page.goto(`${base}/student`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1500);

  step('Open user menu and click Sign Out');
  const menuTrigger = page.locator('header button[aria-haspopup="menu"]').first();
  await menuTrigger.click();
  await page.waitForTimeout(500);
  const logout = page.locator('[data-testid="student-secure-logout"]');
  await logout.waitFor({ state: 'visible', timeout: 10000 });
  await logout.click();
  await page.waitForURL(/\/login/, { timeout: 30000 });
  step('Landed on login after secure logout');

  step('Press back and confirm protected student page is not shown');
  await page.goBack({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1500);
  const url = page.url();
  if (url.includes('/student') && !url.includes('/login')) {
    // May briefly hit student then middleware redirects
    await page.waitForTimeout(2000);
  }
  if (page.url().includes('/student') && !(await page.locator('[data-testid="student-dashboard"]').count()) === 0) {
    const dash = await page.locator('[data-testid="student-dashboard"]').count();
    if (dash > 0) {
      throw new Error('FAIL: protected student dashboard still visible after logout + back');
    }
  }
  if (!page.url().includes('/login') && (await page.locator('[data-testid="student-dashboard"]').count()) > 0) {
    throw new Error('FAIL: protected content visible after back');
  }
  step('Back navigation does not show protected dashboard content');
  note(`Final URL after back: ${page.url()}`);
}
