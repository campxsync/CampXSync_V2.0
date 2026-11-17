/**
 * Diagnosis-only: student sign-in step from batch3 negative spec, against live UI on :3000.
 * Usage: node docs/reports/_tmp_batch3_signin_worktree.mjs [baseUrl]
 */
import { chromium } from '../stm-02/e2e/node_modules/playwright-core/index.mjs';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.argv[2] || process.env.CAMPX_UI_BASE || 'http://localhost:3000';
const OUT = path.join(__dirname, `_verify_followup_batch3_signin_${BASE.includes('127.0.0.1') ? 'ip' : 'localhost'}.json`);

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext();
const page = await ctx.newPage();
const result = {
  label: 'batch3-student-signin-step',
  base: BASE,
  startedAt: new Date().toISOString(),
};

try {
  await page.goto(`${BASE}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1200);
  result.afterSessionUrl = page.url();
  result.hasDashboard = (await page.locator('[data-testid="student-dashboard"]').count()) > 0;
  result.cookies = (await ctx.cookies()).map((c) => ({ name: c.name, domain: c.domain }));

  // Same follow-on as batch3 negative: student navigates to staff desk (deny expected).
  await page.goto(`${BASE}/college/account-provisioning`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(800);
  result.afterStaffUrl = page.url();
  result.staffDeskVisible = (await page.locator('[data-testid="college-account-provisioning-desk"]').count()) > 0;
  result.sessionSurvivedToStaffNav = !result.afterStaffUrl.includes('/login');
  result.pass = result.hasDashboard === true && result.afterSessionUrl.includes('/student');
  result.verdict = result.pass ? 'PASS' : 'FAIL';
} catch (e) {
  result.pass = false;
  result.verdict = 'FAIL';
  result.error = String(e.message || e).slice(0, 500);
}
result.finishedAt = new Date().toISOString();
fs.writeFileSync(OUT, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));
console.log('Evidence:', OUT);
await browser.close();
