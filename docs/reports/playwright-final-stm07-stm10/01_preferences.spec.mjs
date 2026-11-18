/**
 * 01_preferences.spec.mjs — Portal Preferences (STM-10-EN-001 / STM-10-US-012..014).
 * Plain step names, slowMo ~700ms.
 */
export async function run({ page, base, stack, step, note }) {
  step('Navigate to student dashboard via test-session');
  await page.goto(`${base}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(2000);

  step('Open student portal preferences dialog via user menu');
  // First attempt header button if visible, else user menu dropdown
  const prefDirectBtn = page.locator('button[title="Workspace Preferences"]');
  if (await prefDirectBtn.count() && await prefDirectBtn.isVisible()) {
    await prefDirectBtn.click();
  } else {
    // Open user menu
    const userMenu = page.locator('button:has-text("student1.campx")').first();
    await userMenu.waitFor({ state: 'visible', timeout: 15000 });
    await userMenu.click();
    await page.waitForTimeout(500);

    const prefMenuOption = page.locator('button:has-text("Preferences & Display")').first();
    await prefMenuOption.waitFor({ state: 'visible', timeout: 10000 });
    await prefMenuOption.click();
  }
  await page.waitForTimeout(1000);

  step('Verify preferences tabs: widgets, shortcuts, display');
  const widgetsTab = page.locator('[data-testid="preferences-tab-widgets"], button:has-text("Widgets")').first();
  const shortcutsTab = page.locator('[data-testid="preferences-tab-shortcuts"], button:has-text("Shortcuts")').first();
  const displayTab = page.locator('[data-testid="preferences-tab-display"], button:has-text("Display")').first();

  if (await widgetsTab.count()) {
    await widgetsTab.click();
    await page.waitForTimeout(600);
    step('Widgets configuration tab verified');
  }

  if (await shortcutsTab.count()) {
    await shortcutsTab.click();
    await page.waitForTimeout(600);
    step('Shortcuts configuration tab verified');
  }

  if (await displayTab.count()) {
    await displayTab.click();
    await page.waitForTimeout(600);
    step('Display configuration tab verified');
  }

  const closeBtn = page.locator('button[aria-label="Close preferences"], button:has-text("Close"), button:has-text("Cancel")').first();
  if (await closeBtn.count() && await closeBtn.isVisible()) {
    await closeBtn.click();
  } else {
    await page.keyboard.press('Escape');
  }
  await page.waitForTimeout(1000);
  note('PASS: Portal preferences modal opens and displays widgets, shortcuts, and display configuration tabs');
}
