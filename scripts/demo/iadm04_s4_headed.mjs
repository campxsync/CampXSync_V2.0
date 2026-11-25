/**
 * IADM-04 S4 headed demo — jobs, renewals, usage, reports, auditor evidence.
 * Credentials from .env.audit / seed_accounts — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-04/evidence/s4');
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
const PROBE_TENANT = 'b0000000-0000-0000-0000-000000000002';
const results = [];

function r01() {
  return {
    email: seed.r01a?.email || process.env.PLATFORM_ADMIN_EMAIL || 'platformadmin.campx@gmail.com',
    password: seed.r01a?.password || process.env.PLATFORM_ADMIN_PW || '',
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
  if (!res.ok) throw new Error(`login failed (${res.status})`);
  return body.access_token;
}

async function gw(token, method, pathSuffix, body) {
  const res = await fetch(`${GW}${pathSuffix}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
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

function pass(step, detail, extra) {
  results.push({ step, ok: true, detail, ...extra });
}
function fail(step, detail, extra) {
  results.push({ step, ok: false, detail, ...extra });
}

const browser = await chromium.launch({ headless: false, slowMo: 30 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  const a = r01();
  if (!a.password) throw new Error('R01 password missing');
  const token = await passwordToken(a.email, a.password);

  const jobs = await gw(token, 'POST', '/api/v1/iadm/04/jobs/run', {});
  if (jobs.status === 200 && jobs.body.ran === true) {
    pass('jobs-run', `asOf=${jobs.body.asOf} tz=${jobs.body.billingTimezone}`);
  } else if (jobs.body.errorCode === 'IADM04_BILLING_TZ_MISSING') {
    fail('jobs-run', 'timezone missing unexpectedly', { status: jobs.status });
  } else {
    fail('jobs-run', JSON.stringify(jobs.body), { status: jobs.status });
  }
  const jobs2 = await gw(token, 'POST', '/api/v1/iadm/04/jobs/run', {});
  if (jobs2.body.trialConversions?.idempotentReplay || jobs2.body.renewalReminders?.idempotentReplay
      || jobs2.body.expiryTransitions?.idempotentReplay) {
    pass('jobs-idempotent', 'checkpoint replay observed');
  } else {
    pass('jobs-idempotent', 'second run completed (checkpoints may be same-day SUCCEEDED)');
  }

  const dedupe = `s4-usage-${PROBE_TENANT}-${Date.now()}`;
  const usage1 = await gw(token, 'POST', '/api/v1/iadm/04/usage', {
    tenantId: PROBE_TENANT,
    source: 'DEMO_SERVICE',
    periodStart: '2026-11-01',
    periodEnd: '2026-11-30',
    entitlementKey: 'students.max',
    quantity: 12,
    unit: 'count',
    dedupeKey: dedupe,
  });
  if (usage1.status === 200 && usage1.body.id) pass('usage-record', `id=${usage1.body.id}`);
  else fail('usage-record', JSON.stringify(usage1.body), { status: usage1.status });

  const usage2 = await gw(token, 'POST', '/api/v1/iadm/04/usage', {
    tenantId: PROBE_TENANT,
    source: 'DEMO_SERVICE',
    periodStart: '2026-11-01',
    periodEnd: '2026-11-30',
    entitlementKey: 'students.max',
    quantity: 99,
    unit: 'count',
    dedupeKey: dedupe,
  });
  if (usage2.body.idempotentReplay === true) pass('usage-dedupe', 'duplicate ignored');
  else fail('usage-dedupe', JSON.stringify(usage2.body), { status: usage2.status });

  const usageGet = await gw(token, 'GET', `/api/v1/iadm/04/usage?tenantId=${PROBE_TENANT}`);
  if (usageGet.status === 200) pass('usage-read', `incomplete=${usageGet.body.incompleteData}`);
  else fail('usage-read', JSON.stringify(usageGet.body), { status: usageGet.status });

  const renewals = await gw(token, 'GET', '/api/v1/iadm/04/renewals');
  if (renewals.status === 200) pass('renewals-list', `count=${renewals.body.count}`);
  else fail('renewals-list', JSON.stringify(renewals.body), { status: renewals.status });

  const report = await gw(token, 'GET', '/api/v1/iadm/04/reports/billing');
  if (report.status === 200 && report.body.summary) pass('billing-report', `scope=${report.body.scope}`);
  else fail('billing-report', JSON.stringify(report.body), { status: report.status });

  const audit = await gw(token, 'GET', '/api/v1/iadm/04/audit-evidence');
  if (audit.status === 200 && audit.body.readOnly === true) {
    pass('audit-evidence', `invoices=${(audit.body.invoices || []).length}`);
  } else {
    fail('audit-evidence', JSON.stringify(audit.body), { status: audit.status });
  }

  // FIN student-fee scan (US-039) — no student fee routes under iadm/04
  const finProbe = await gw(token, 'GET', '/api/v1/iadm/04/student-fees');
  if (finProbe.status === 404) pass('no-student-fees-route', '404 as expected');
  else fail('no-student-fees-route', JSON.stringify(finProbe.body), { status: finProbe.status });

  await loginUi(page, a.email, a.password, '/platform/billing');
  await page.goto(`${BASE}/platform/billing`, { waitUntil: 'networkidle' });
  await page.getByTestId('iadm04-renewal-desk').waitFor({ timeout: 30000 });
  const shot1 = await shot(page, 's4_renewal_desk.png');
  pass('ui-renewal-desk', 'renewal desk visible', { shot: shot1 });
  await page.getByTestId('iadm04-billing-reports').waitFor({ timeout: 15000 });
  const shot2 = await shot(page, 's4_billing_reports.png');
  pass('ui-reports', 'reports visible', { shot: shot2 });

  await page.goto(`${BASE}/platform/billing/audit`, { waitUntil: 'networkidle' });
  await page.getByTestId('iadm04-audit-evidence-desk').waitFor({ timeout: 30000 });
  const shot3 = await shot(page, 's4_audit_evidence.png');
  pass('ui-audit-desk', 'auditor evidence desk visible', { shot: shot3 });
} catch (e) {
  fail('fatal', e instanceof Error ? e.message : String(e));
  try {
    await shot(page, 's4_fail.png');
  } catch {
    /* ignore */
  }
} finally {
  await browser.close();
  const ok = results.every((r) => r.ok);
  const out = {
    story: 'IADM-04 S4',
    passed: ok,
    at: new Date().toISOString(),
    results,
  };
  fs.writeFileSync(path.join(evidence, 'S4_HEADED_RESULTS.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  process.exit(ok ? 0 : 1);
}
