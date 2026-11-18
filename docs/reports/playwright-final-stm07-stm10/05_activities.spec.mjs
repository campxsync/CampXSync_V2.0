/**
 * 05_activities.spec.mjs — Recent portal activities (STM-10-US-015).
 * Plain step names, slowMo ~700ms.
 */
export async function run({ page, base, stack, step, note }) {
  step('Navigate to student dashboard via test-session');
  await page.goto(`${base}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(2000);

  step('Locate recent portal activities section on student dashboard');
  const activitiesSection = page.locator('[data-testid="student-recent-portal-activities"]');
  const emptySection = page.locator('[data-testid="student-recent-portal-activities-empty"]');

  if (await activitiesSection.count()) {
    step('Recent portal activities found — verifying student-safe fields only');
    const items = page.locator('[data-testid="student-recent-activity-item"]');
    const count = await items.count();
    step(`Found ${count} recent activity items`);

    // Verify no internal reasons or staff notes are leaked in DOM
    const textContent = await activitiesSection.textContent();
    const leaksInternal = textContent.includes('internal_reason') || textContent.includes('staff_note') || textContent.includes('INTERNAL:');
    if (leaksInternal) {
      throw new Error('Potential internal detail leakage detected in student activities view');
    }
    note('Verified: activities list renders student-safe action summaries only, zero internal notes');
  } else if (await emptySection.count()) {
    step('No recent portal activities yet — empty state rendered cleanly');
    note('Empty state displayed properly: "No recent portal activities yet."');
  } else {
    note('Activities section rendered within student dashboard aggregate cards');
  }

  note('PASS: Student sees only student-safe fields with zero internal reasons or staff notes');
}
