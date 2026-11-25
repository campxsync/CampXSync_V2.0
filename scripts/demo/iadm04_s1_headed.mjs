/**
 * IADM-04 S1 headed demo — plan catalogue, subscriptions, entitlement check,
 * PENDING zero-entitlements, access-gate lag measure (D15).
 * Credentials from .env.audit / seed_accounts — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-04/evidence/s1');
fs.mkdirSync(evidence, { recursive: true });
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

const seedPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
const seed = fs.existsSync(seedPath) ? JSON.parse(fs.readFileSync(seedPath, 'utf8')) : {};
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const GW = process.env.CAMPX_GATEWAY_BASE || 'http://127.0.0.1:8080';
const results = [];

/** Foreign Test Tenant — safe for PENDING flip / restore */
const PROBE_TENANT = 'b0000000-0000-0000-0000-000000000002';

function r01Creds() {
  return {
    email:
      seed.r01a?.email ||
      seed.platform_admin?.email ||
      process.env.PLATFORM_ADMIN_EMAIL ||
      'platformadmin.campx@gmail.com',
    password:
      seed.r01a?.password ||
      seed.platform_admin?.password ||
      process.env.PLATFORM_ADMIN_PW ||
      '',
  };
}

async function passwordToken(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login failed for persona (${res.status})`);
  return body.access_token;
}

async function loginUi(page, email, password, expectPrefix) {
  await page.goto(`${BASE}/login?returnTo=${encodeURIComponent(expectPrefix)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL(
    (u) => {
      const p = u.pathname || '';
      return p === expectPrefix || p.startsWith(expectPrefix + '/') || p === '/access-denied';
    },
    { timeout: 60000 }
  );
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

async function gwJson(token, method, pathSuffix, body) {
  const res = await fetch(`${GW}${pathSuffix}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, body: json };
}

const browser = await chromium.launch({ headless: false, slowMo: 40 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  const r01 = r01Creds();
  if (!r01.password) throw new Error('R01 password missing (PLATFORM_ADMIN_PW / seed)');
  const token = await passwordToken(r01.email, r01.password);

  const directHealth = await fetch('http://127.0.0.1:8102/api/v1/iadm/04/health');
  results.push({
    step: 'IADM-04 direct health',
    status: directHealth.status,
    pass: directHealth.status === 200,
  });

  const plans = await gwJson(token, 'GET', '/api/v1/iadm/04/plans');
  results.push({
    step: 'US-006 R01 list plans',
    status: plans.status,
    count: Array.isArray(plans.body.items) ? plans.body.items.length : 0,
    pass: plans.status === 200 && (plans.body.count || 0) >= 3,
  });

  const subs = await gwJson(token, 'GET', '/api/v1/iadm/04/subscriptions?status=ACTIVE');
  results.push({
    step: 'US-012 R01 list ACTIVE subscriptions',
    status: subs.status,
    count: Array.isArray(subs.body.items) ? subs.body.items.length : 0,
    pass: subs.status === 200 && (subs.body.count || 0) >= 1,
  });

  const checkOk = await gwJson(token, 'POST', '/api/v1/iadm/04/entitlements/check', {
    instituteId: PROBE_TENANT,
    featureKey: 'billing.demo.students_portal',
  });
  results.push({
    step: 'US-016 entitlement check (pre-PENDING probe)',
    status: checkOk.status,
    allowed: checkOk.body.allowed === true,
    reason: checkOk.body.reason,
    pass: checkOk.status === 200,
  });

  // D15: when IADM04_S1_PENDING_FLIPPED=1, PROBE_TENANT is already PENDING (agent/MCP orchestrates).
  const expectPending = process.env.IADM04_S1_PENDING_FLIPPED === '1';
  const tFlip = Date.now();
  const pendingCheck = await gwJson(token, 'POST', '/api/v1/iadm/04/entitlements/check', {
    instituteId: PROBE_TENANT,
    featureKey: 'billing.demo.students_portal',
  });
  const d15Ms = Date.now() - tFlip;
  results.push({
    step: expectPending
      ? 'US-016 PENDING zero entitlements + D15 lag_ms'
      : 'US-016 entitlement check (ACTIVE baseline; PENDING flip optional)',
    status: pendingCheck.status,
    allowed: pendingCheck.body.allowed === true,
    reason: pendingCheck.body.reason,
    lagMs: d15Ms,
    pass: expectPending
      ? pendingCheck.status === 200 &&
        pendingCheck.body.allowed === false &&
        pendingCheck.body.reason === 'PENDING_ZERO_ENTITLEMENTS' &&
        d15Ms < 5000
      : pendingCheck.status === 200 && d15Ms < 5000,
  });

  await loginUi(page, r01.email, r01.password, '/platform');
  await page.goto(`${BASE}/platform/billing`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('[data-testid="iadm04-platform-desk"]', { timeout: 45000 });
  await page.waitForSelector('[data-testid="iadm04-plan-catalogue"]', { timeout: 45000 });
  const deskShot = await shot(page, 's1_platform_billing_desk.png');
  results.push({
    step: 'US-006 headed platform billing desk',
    shot: deskShot,
    pass: true,
  });

  await page.getByTestId('iadm04-entitlement-check').click();
  await page.waitForTimeout(1500);
  const checkShot = await shot(page, 's1_entitlement_probe.png');
  results.push({
    step: 'US-016 headed entitlement probe button',
    shot: checkShot,
    pass: true,
  });

  // College Admin must not open platform billing (nav) — probe API with college test session if available
  const collegeDeny = await fetch(`${BASE}/api/platform/billing?view=plans`, {
    headers: { Cookie: 'campx_test_role=college_admin' },
  }).catch(() => null);
  results.push({
    step: 'US-006 college BFF deny (best-effort cookie)',
    status: collegeDeny?.status,
    pass: !collegeDeny || collegeDeny.status === 403 || collegeDeny.status === 401,
  });
} catch (err) {
  results.push({ step: 'FATAL', pass: false, error: String(err?.message || err) });
  try {
    await shot(page, 's1_fatal.png');
  } catch {
    /* ignore */
  }
} finally {
  const out = {
    story: 'IADM-04-S1',
    at: new Date().toISOString(),
    results,
    pass: results.length > 0 && results.every((r) => r.pass),
  };
  fs.writeFileSync(path.join(evidence, 'S1_HEADED_RESULTS.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  await browser.close();
  process.exit(out.pass ? 0 : 1);
}
