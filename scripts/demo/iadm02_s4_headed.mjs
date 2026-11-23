/**
 * IADM-02 S4 headed demo: corrective republish on versions desk.
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

const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';

async function login(page, email, password) {
  await page.goto(`${BASE}/login?returnTo=/platform/configuration/versions`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL((url) => {
    const p = url.pathname || '';
    return p === '/platform' || p.startsWith('/platform/');
  }, { timeout: 60000 });
}

const browser = await chromium.launch({ headless: false, slowMo: 60 });
const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
const results = [];

try {
  await login(page, process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
  await page.goto(`${BASE}/platform/configuration/versions`);
  await page.getByTestId('iadm02-versions-desk').waitFor({ timeout: 20000 });
  await page.waitForTimeout(1000);
  const shot1 = path.join(evidence, 's4_versions_before.png');
  await page.screenshot({ path: shot1, fullPage: true });
  results.push({ step: 'versions before', shot: shot1 });

  const btn = page.locator('[data-testid^="iadm02-corrective-"]').first();
  await btn.click();
  await page.getByTestId('iadm02-corrective-result').waitFor({ timeout: 20000 });
  const msg = await page.getByTestId('iadm02-corrective-result').innerText();
  const shot2 = path.join(evidence, 's4_corrective.png');
  await page.screenshot({ path: shot2, fullPage: true });
  results.push({
    step: 'US-027 corrective',
    shot: shot2,
    msg,
    pass: /VER-|Published|notification delayed/i.test(msg),
  });

  const out = { pass: results.some((r) => r.pass), results };
  fs.writeFileSync(path.join(evidence, 'S4_HEADED.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  if (!out.pass) process.exitCode = 1;
} catch (e) {
  console.error(String(e && e.message ? e.message : e));
  try {
    await page.screenshot({ path: path.join(evidence, 's4_headed_error.png'), fullPage: true });
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
