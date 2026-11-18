/**
 * 07_registrar.spec.mjs — Delegated Registrar staff link status & eligibility (Owner Decision D13).
 * Plain step names, slowMo ~700ms.
 */
export async function run({ page, base, stack, step, note }) {
  const studentId = process.env.CAMPX_DEMO_STUDENT_ID || '81dd67d3-1d5f-4d8e-8061-a67648610002';

  step('Pilot Registrar (R31) opens staff account-provisioning desk via JWT test-session');
  await page.goto(
    `${base}/api/auth/test-session?role=registrar&returnTo=/college/account-provisioning`,
    { waitUntil: 'networkidle', timeout: 60000 }
  );
  await page.waitForTimeout(1500);

  const desk = page.locator('[data-testid="college-account-provisioning-desk"]');
  await desk.waitFor({ state: 'visible', timeout: 30000 });
  step('Desk visible for Delegated Registrar');

  step('Load account link status for known Dev student');
  await page.fill('[data-testid="account-provisioning-student-id"]', studentId);
  await page.click('[data-testid="account-link-status-load"]');
  await page.waitForTimeout(3000);

  const errText = ((await page.locator('[data-testid="account-provisioning-error"]').textContent().catch(() => '')) || '').trim();
  const statusVal = (
    (await page.locator('[data-testid="account-link-status-value"]').textContent().catch(() => '')) || ''
  ).trim();

  if (errText) {
    throw new Error(`Delegated Registrar link-status failed: ${errText.slice(0, 240)}`);
  }
  if (!statusVal) {
    throw new Error('Delegated Registrar expected a link-status value after load');
  }

  step(`Delegated Registrar success — link status="${statusVal}"`);
  note(`PASS: Pilot Registrar with STM-07.ACCOUNT.A delegation loaded staff link status="${statusVal}" via Java service under Owner Decision D13`);
}
