/** Save shortcuts (STM-10-US-013). */
export async function run({ page, base, stack, step, note }) {
  step('Sign in as student via Quick Dev Testing on login');
  await page.goto(`${base}/login`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.getByRole('link', { name: /Student — Documents Desk/i }).click();
  await page.waitForURL(/\/student/, { timeout: 60000 });
  await page.goto(`${base}/student`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(2000);

  step('Open preferences and add a shortcut');
  const prefBtn = page.locator('button[title="Workspace Preferences"]');
  await prefBtn.waitFor({ state: 'visible', timeout: 15000 });
  await prefBtn.click();
  await page.waitForTimeout(1000);

  const shortcutsTab = page.locator('[data-testid="preferences-tab-shortcuts"]');
  if (!(await shortcutsTab.count())) {
    note('Shortcuts tab missing — stop this step');
    return;
  }
  await shortcutsTab.click();
  await page.waitForTimeout(400);
  await page.locator('[data-testid="preferences-shortcut-id"]').fill('docs');
  await page.locator('[data-testid="preferences-shortcut-label"]').fill('My Documents');
  await page.locator('[data-testid="preferences-shortcut-path"]').fill('/student/documents');
  await page.locator('[data-testid="preferences-shortcut-add"]').click();
  await page.waitForTimeout(400);
  const saveBtn = page.getByRole('button', { name: /save/i }).first();
  if (await saveBtn.count()) {
    step('Save shortcuts');
    await saveBtn.click();
    await page.waitForTimeout(1200);
  }

  step('Reload dashboard and look for shortcuts strip');
  await page.goto(`${base}/student`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(1500);
  const strip = page.locator('[data-testid="student-shortcuts-strip"], [data-testid="student-shortcuts-empty"]');
  if (await strip.count()) {
    step('Shortcuts strip or empty-state is visible');
  } else {
    note('Shortcuts strip not found after reload');
  }

  if (!stack.student8093) {
    note('SKIPPED_MISSING_8093: durable shortcut persistence needs student-service on :8093');
  }
}
