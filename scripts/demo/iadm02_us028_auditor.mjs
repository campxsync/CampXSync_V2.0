/**
 * IADM-02-US-028 headed: R28 auditor reads history; every mutation denied with
 * "Auditor access is read-only".
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

const seedPath = path.join(evidence, '.local/seed_accounts.json');
const seed = JSON.parse(fs.readFileSync(seedPath, 'utf8'));
const auditor = seed.auditor;
if (!auditor?.email || !auditor?.password) {
  console.error('STOP: seed auditor via scripts/dev/iadm02_seed_auditor_r28.mjs + SQL first');
  process.exit(2);
}

const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const url = process.env.CAMPX_SUPABASE_URL;
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

async function apiLogin() {
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: auditor.email, password: auditor.password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error('auditor login failed ' + res.status);
  return body.access_token;
}

async function loginUi(page) {
  await page.goto(`${BASE}/login?returnTo=/platform/configuration/versions`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(auditor.email);
  await page.getByLabel(/^Password$/i).fill(auditor.password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL(
    (u) => {
      const p = u.pathname || '';
      return p.startsWith('/platform/configuration') || p === '/access-denied';
    },
    { timeout: 60000 }
  );
}

const browser = await chromium.launch({ headless: false, slowMo: 50 });
const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
const results = [];

try {
  const token = await apiLogin();

  // API: versions read
  const versionsRes = await fetch('http://127.0.0.1:8080/api/v1/iadm/02/versions', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const versionsBody = await versionsRes.json().catch(() => ({}));
  results.push({
    step: 'API GET versions',
    status: versionsRes.status,
    count: Array.isArray(versionsBody.items) ? versionsBody.items.length : 0,
    pass: versionsRes.status === 200 && Array.isArray(versionsBody.items),
  });

  // API: mutation denied
  const draftRes = await fetch(
    'http://127.0.0.1:8080/api/v1/iadm/02/configurations/security.session_timeout_min/drafts',
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': `us028-deny-${Date.now()}`,
      },
      body: JSON.stringify({
        proposedValue: '99',
        reason: 'US-028 auditor must be denied',
        idempotencyKey: `us028-deny-${Date.now()}`,
      }),
    }
  );
  const draftBody = await draftRes.json().catch(() => ({}));
  const denyMsg = String(draftBody.detail || draftBody.message || '');
  results.push({
    step: 'API POST draft denied',
    status: draftRes.status,
    detail: denyMsg,
    errorCode: draftBody.errorCode,
    pass:
      draftRes.status === 403 &&
      /Auditor access is read-only/i.test(denyMsg),
  });

  // Headed UI
  await loginUi(page);
  await page.goto(`${BASE}/platform/configuration/versions`);
  await page.waitForTimeout(1500);
  const shot1 = path.join(evidence, 'us028_versions.png');
  await page.screenshot({ path: shot1, fullPage: true });
  const versionsVisible = await page.getByTestId('iadm02-versions-desk').count();
  results.push({
    step: 'UI versions desk',
    shot: shot1,
    pass: versionsVisible > 0 && !page.url().includes('access-denied'),
  });

  // Prefer corrective button; fall back to BFF draft. Exact deny text comes from IADM-02.
  let uiDenyPass = false;
  let uiDenyDetail = null;
  const btn = page.locator('[data-testid^="iadm02-corrective-"]').first();
  if ((await btn.count()) > 0) {
    await btn.click();
    await page.waitForTimeout(2500);
    const errText = (
      await page
        .locator('[data-testid="iadm02-versions-error"], [data-testid="iadm02-corrective-result"]')
        .allInnerTexts()
    ).join(' ');
    uiDenyDetail = errText;
    uiDenyPass = /Auditor access is read-only/i.test(errText);
  }
  if (!uiDenyPass) {
    const bff = await page.evaluate(async () => {
      const res = await fetch('/api/platform/configuration/drafts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          key: 'security.session_timeout_min',
          proposedValue: '88',
          reason: 'US-028 UI BFF deny',
          idempotencyKey: `us028-bff-${Date.now()}`,
        }),
      });
      const body = await res.json().catch(() => ({}));
      return { status: res.status, body };
    });
    uiDenyDetail = bff;
    uiDenyPass =
      bff.status === 403 &&
      /Auditor access is read-only/i.test(String(bff.body.detail || bff.body.message || ''));
  }
  const shot2 = path.join(evidence, 'us028_mutation_denied.png');
  await page.screenshot({ path: shot2, fullPage: true });
  results.push({
    step: 'UI mutation denied with auditor message',
    shot: shot2,
    detail: uiDenyDetail,
    pass: uiDenyPass,
  });

  const out = { story: 'IADM-02-US-028', pass: results.every((r) => r.pass), results };
  fs.writeFileSync(path.join(evidence, 'US028_AUDITOR_HEADED.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  if (!out.pass) process.exitCode = 1;
} catch (e) {
  console.error(String(e && e.message ? e.message : e));
  try {
    await page.screenshot({ path: path.join(evidence, 'us028_error.png'), fullPage: true });
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
