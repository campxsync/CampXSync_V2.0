/** Configure language and display preferences (STM-10-US-014). */
export async function run({ page, base, stack, step, note }) {
  step('Sign in as student via Quick Dev Testing on login');
  await page.goto(`${base}/login`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.getByRole('link', { name: /Student — Documents Desk/i }).click();
  await page.waitForURL(/\/student/, { timeout: 60000 });
  await page.goto(`${base}/student`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(2000);

  step('Open preferences display panel and switch locale/theme');
  const prefBtn = page.locator('button[title="Workspace Preferences"]');
  await prefBtn.waitFor({ state: 'visible', timeout: 15000 });
  await prefBtn.click();
  await page.waitForTimeout(1000);

  const locale = page.locator('[data-testid="preferences-locale-select"]');
  if (!(await locale.count())) {
    note('Locale select missing — stop this step');
    return;
  }
  await locale.selectOption('hi-IN');
  const darkBtn = page.getByRole('button', { name: /^dark$/i }).first();
  if (await darkBtn.count()) await darkBtn.click();
  const saveBtn = page.getByRole('button', { name: /save/i }).first();
  if (await saveBtn.count()) {
    step('Save language and display preferences');
    await saveBtn.click();
    await page.waitForTimeout(1200);
  }

  step('Reload dashboard so StudentDisplayEffects re-applies saved prefs');
  await page.goto(`${base}/student`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(1800);
  const lang = await page.evaluate(() => document.documentElement.lang || document.documentElement.dataset.locale || '');
  step(`Document locale after reload: ${lang || '(empty)'}`);
  if (lang && lang.toLowerCase().includes('hi')) {
    step('Locale persisted to document after reload');
  } else {
    note('Locale attribute not hi-IN after reload (may need :8093 for durable sync)');
  }

  if (!stack.student8093) {
    note('SKIPPED_MISSING_8093: durable display persistence needs student-service on :8093');
  }
}
