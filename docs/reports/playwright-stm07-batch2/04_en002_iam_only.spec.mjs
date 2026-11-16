/** STM-07-EN-002 — existing IAM only; no local credential UI. */
export async function run({ page, base, stack, step, note }) {
  step('Platform Admin opens login surface');
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(800);

  step('Confirm page uses existing auth (Supabase login) — no local password-mint form for STM-07');
  const bodyText = (await page.locator('body').innerText()).toLowerCase();
  if (bodyText.includes('create password for student') || bodyText.includes('mint credential')) {
    throw new Error('Unexpected local credential mint UI on login');
  }
  note('No local STM-07 credential mint UI observed on /login');

  step('Enter Platform Admin test session');
  await page.goto(`${base}/api/auth/test-session?role=platform_admin&redirect=/`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  note(`IAM Path C base expected at :8081 listening=${stack.iam8081}`);
}
