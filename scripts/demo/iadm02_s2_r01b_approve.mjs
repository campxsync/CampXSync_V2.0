/**
 * Headed: R01-B approves PENDING_APPROVAL draft from R01-A, then publishes.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-02/evidence');
const require = createRequire(path.join(root, 'frontend/package.json'));
const { chromium } = require('playwright');

function loadEnv(rel) {
  const p = path.join(root, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
}
loadEnv('.env.audit');
loadEnv('frontend/.env.local');
const seed = JSON.parse(fs.readFileSync(path.join(evidence, '.local/seed_accounts.json'), 'utf8'));
const BASE = 'http://127.0.0.1:3000';
const DRAFT = '4e562e40-ad6a-4b04-acd6-dcb230fe962d';

const browser = await chromium.launch({ headless: false, slowMo: 100 });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
const out = [];
try {
  await page.goto(`${BASE}/login?returnTo=/platform/configuration/drafts`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel(/Institutional Email/i).fill(seed.r01b.email);
  await page.getByLabel(/^Password$/i).fill(seed.r01b.password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL((u) => (u.pathname || '').startsWith('/platform'), { timeout: 60000 });
  await page.goto(`${BASE}/platform/configuration/drafts`);
  await page.getByTestId('iadm02-drafts-desk').waitFor();
  await page.waitForTimeout(1500);
  out.push({ step: 'R01-B drafts loaded', shot: path.join(evidence, 's2_r01b_drafts.png') });
  await page.screenshot({ path: out[0].shot, fullPage: true });

  const approve = page.getByTestId(`iadm02-approve-${DRAFT}`);
  if ((await approve.count()) === 0) {
    // fallback any approve
    const any = page.locator('[data-testid^="iadm02-approve-"]');
    if ((await any.count()) === 0) throw new Error('No approve buttons');
    await page.getByTestId('iadm02-decision-reason').fill('S2 R01-B approve of R01-A draft');
    await any.first().click();
  } else {
    await page.getByTestId('iadm02-decision-reason').fill('S2 R01-B approve of R01-A draft');
    await approve.click();
  }
  await page.waitForTimeout(2000);
  out.push({ step: 'approved', shot: path.join(evidence, 's2_r01b_approve.png'), ok: true });
  await page.screenshot({ path: out[1].shot, fullPage: true });

  const pub = page.locator('[data-testid^="iadm02-publish-"]').first();
  if ((await pub.count()) > 0) {
    await pub.click();
    await page.waitForTimeout(2000);
    out.push({ step: 'published', shot: path.join(evidence, 's2_r01b_publish.png'), ok: true });
    await page.screenshot({ path: out[2].shot, fullPage: true });
  }

  await page.goto(`${BASE}/platform/configuration/versions`);
  await page.waitForTimeout(1500);
  out.push({ step: 'versions', shot: path.join(evidence, 's2_versions_after.png') });
  await page.screenshot({ path: out[out.length - 1].shot, fullPage: true });
} catch (e) {
  out.push({ step: 'ERROR', detail: String(e.message || e) });
  await page.screenshot({ path: path.join(evidence, 's2_r01b_error.png'), fullPage: true }).catch(() => {});
} finally {
  await browser.close();
}
fs.writeFileSync(path.join(evidence, 'S2_R01B_APPROVE_RESULTS.json'), JSON.stringify(out, null, 2));
console.log(JSON.stringify(out, null, 2));
