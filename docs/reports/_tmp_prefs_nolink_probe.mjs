/**
 * Diagnosis-only: portal-preferences BFF response for student test-session (no IAM link expected).
 */
import { chromium } from '../stm-02/e2e/node_modules/playwright-core/index.mjs';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = 'http://localhost:3000';
const OUT = path.join(__dirname, '_verify_followup_prefs_nolink.json');

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext();
const page = await ctx.newPage();
const result = { base: BASE, startedAt: new Date().toISOString() };

try {
  await page.goto(`${BASE}/api/auth/test-session?role=student&returnTo=/student`, {
    waitUntil: 'networkidle',
    timeout: 60000,
  });
  await page.waitForTimeout(1000);
  result.sessionUrl = page.url();
  result.hasDashboard = (await page.locator('[data-testid="student-dashboard"]').count()) > 0;

  const me = await page.evaluate(async () => {
    const res = await fetch('/api/student/portal-preferences', { credentials: 'include' });
    const text = await res.text();
    let body = text;
    try {
      body = JSON.parse(text);
    } catch (_) {}
    return { status: res.status, body };
  });
  result.prefsGet = me;

  const meProfile = await page.evaluate(async () => {
    const res = await fetch('/api/student/me', { credentials: 'include' }).catch(() => null);
    if (!res) return { status: 0, body: null, note: 'no /api/student/me route or fetch failed' };
    const text = await res.text();
    let body = text;
    try {
      body = JSON.parse(text);
    } catch (_) {}
    return { status: res.status, body };
  });
  result.studentMe = meProfile;
} catch (e) {
  result.error = String(e.message || e).slice(0, 500);
}
result.finishedAt = new Date().toISOString();
fs.writeFileSync(OUT, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result, null, 2));
await browser.close();
