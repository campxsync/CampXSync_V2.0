/**
 * IADM-02 S3 headed demo: consumer diagnostics + versions (US-025 / US-019 residual).
 * Credentials from .env.audit — never printed.
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
const results = [];

async function login(page, email, password) {
  await page.goto(`${BASE}/login?returnTo=/platform/configuration`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL((url) => {
    const p = url.pathname || '';
    return p === '/platform' || p.startsWith('/platform/');
  }, { timeout: 60000 });
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

const browser = await chromium.launch({ headless: false, slowMo: 60 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  await login(page, process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);

  await page.goto(`${BASE}/platform/configuration`);
  await page.getByTestId('iadm02-link-diagnostics').waitFor({ timeout: 20000 });
  results.push({ step: 'catalogue with diagnostics link', shot: await shot(page, 's3_catalogue.png') });

  await page.getByTestId('iadm02-link-diagnostics').click();
  await page.getByTestId('iadm02-diagnostics-desk').waitFor({ timeout: 20000 });
  await page.waitForTimeout(1500);
  const tableText = await page.getByTestId('iadm02-diagnostics-table').innerText();
  results.push({
    step: 'US-025 diagnostics',
    shot: await shot(page, 's3_diagnostics.png'),
    hasRow: /FRESH|STALE|88caa554|a2b94200|69822238/i.test(tableText),
    tableSnippet: tableText.slice(0, 240),
  });

  await page.goto(`${BASE}/platform/configuration/versions`);
  await page.waitForTimeout(1500);
  results.push({ step: 'versions history', shot: await shot(page, 's3_versions.png') });

  const pass = results.some((r) => r.step === 'US-025 diagnostics' && r.hasRow);
  const out = { pass, results };
  fs.writeFileSync(path.join(evidence, 'S3_HEADED.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  if (!pass) process.exitCode = 1;
} catch (e) {
  console.error(String(e && e.message ? e.message : e));
  try {
    await shot(page, 's3_headed_error.png');
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
