import { chromium } from '../stm-02/e2e/node_modules/playwright-core/index.mjs';

const browser = await chromium.launch({ headless: true, slowMo: 0 });

async function tryBase(base) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const result = { base };
  try {
    await page.goto(base + '/api/auth/test-session?role=student&returnTo=/student', {
      waitUntil: 'networkidle',
      timeout: 60000,
    });
    await page.waitForTimeout(1500);
    result.url = page.url();
    result.hasDashboard = (await page.locator('[data-testid="student-dashboard"]').count()) > 0;
    result.hasPrefsBtn = (await page.locator('button[title="Workspace Preferences"]').count()) > 0;
    result.cookies = (await ctx.cookies()).map((c) => ({
      name: c.name,
      domain: c.domain,
      path: c.path,
    }));
  } catch (e) {
    result.error = String(e.message || e).slice(0, 400);
  }
  await ctx.close();
  return result;
}

const a = await tryBase('http://127.0.0.1:3000');
const b = await tryBase('http://localhost:3000');
console.log(JSON.stringify({ a, b }, null, 2));
await browser.close();
