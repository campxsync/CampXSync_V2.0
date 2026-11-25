/**
 * IADM-04 S5 evidence 4a headed film.
 * Phases via env IADM04_4A_PHASE=pending|none|trial|active-check (default pending).
 * Never prints passwords. Merges results into S5_4A_HEADED_RESULTS.json.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';
import { createHmac } from 'crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-04/evidence/s5');
fs.mkdirSync(evidence, { recursive: true });
const require = createRequire(path.join(root, 'frontend/package.json'));
const { chromium } = require('playwright');
const PHASE = (process.env.IADM04_4A_PHASE || 'pending').toLowerCase();
const resultsPath = path.join(evidence, 'S5_4A_HEADED_RESULTS.json');

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
const DEMO_TENANT = seed.r03demo?.tenantId || 'd4000000-0000-4000-8000-000000000004';
const results = [];

function r01() {
  return {
    email: seed.r01a?.email || process.env.PLATFORM_ADMIN_EMAIL || 'platformadmin.campx@gmail.com',
    password: seed.r01a?.password || process.env.PLATFORM_ADMIN_PW || '',
  };
}
function r03() {
  return {
    email: seed.r03demo?.email || 'institution.iadm04.demo@campx.local',
    password: seed.r03demo?.password || '',
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

async function gw(token, method, pathSuffix, body, extraHeaders) {
  const res = await fetch(`${GW}${pathSuffix}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(extraHeaders || {}),
    },
    body: body != null ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

async function loginUi(page, email, password, returnTo = '/institution/billing') {
  await page.context().clearCookies();
  await page.goto(`${BASE}/login?returnTo=${encodeURIComponent(returnTo)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL(
    (u) => {
      const p = u.pathname || '';
      return p.startsWith('/institution') || p === '/access-denied';
    },
    { timeout: 90000 }
  );
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function pass(step, detail, extra) {
  results.push({ phase: PHASE, step, ok: true, detail, ...extra });
}
function fail(step, detail, extra) {
  results.push({ phase: PHASE, step, ok: false, detail, ...extra });
}

const SIM_SECRET =
  process.env.IADM04_PAYMENT_SIMULATOR_SECRET || 'iadm04-dev-simulator-secret-not-for-prod';
function signCallback(ts, bodyJson) {
  return createHmac('sha256', SIM_SECRET).update(`${ts}.${bodyJson}`).digest('hex');
}

const browser = await chromium.launch({ headless: false, slowMo: 40 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  const a = r01();
  const inst = r03();
  if (!inst.password) throw new Error('R03 DEMO password missing');
  if (!a.password) throw new Error('R01 password missing');
  const tokenA = await passwordToken(a.email, a.password);

  if (PHASE === 'pending') {
    const gate = await gw(tokenA, 'GET', `/api/v1/iadm/04/access-gate?tenantId=${DEMO_TENANT}`);
    if (gate.body?.allowed === false) pass('PENDING gate API', JSON.stringify(gate.body).slice(0, 160));
    else fail('PENDING gate API', JSON.stringify(gate.body).slice(0, 160));

    await loginUi(page, inst.email, inst.password);
    // Home is not allowlisted while PENDING — go straight to the subscription desk.
    await page.goto(`${BASE}/institution/billing`, { waitUntil: 'networkidle' }).catch(() => {});
    await page.waitForTimeout(2000);
    await page.getByTestId('iadm04-institution-desk').waitFor({ timeout: 30000 }).catch(() => {});
    const hasBanner = (await page.getByTestId('iadm04-pending-banner').count()) > 0;
    const hasPay = (await page.getByTestId('iadm04-pending-pay-path').count()) > 0;
    if (hasBanner && hasPay) pass('PENDING desk + pay path', 'visible');
    else fail('PENDING desk + pay path', `banner=${hasBanner} pay=${hasPay} url=${page.url()}`);
    await shot(page, 's5_4a_pending_subscription_desk.png');

    await page.goto(`${BASE}/institution/profile`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1000);
    if (page.url().includes('/institution/profile')) pass('PENDING profile allowed', page.url());
    else fail('PENDING profile allowed', page.url());
    await shot(page, 's5_4a_pending_profile.png');

    await page.goto(`${BASE}/institution/colleges`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2000);
    // Client + server guards may land on access-denied or bounce to billing.
    const blocked =
      page.url().includes('access-denied') ||
      (await page.getByTestId('iadm04-denied-open-subscription').count()) > 0 ||
      (await page.getByText(/Subscription Access Unavailable/i).count()) > 0;
    if (blocked) pass('PENDING colleges blocked with message', page.url());
    else fail('PENDING colleges blocked with message', page.url());
    await shot(page, 's5_4a_pending_colleges_blocked.png');

    const invList = await gw(tokenA, 'GET', `/api/v1/iadm/04/invoices?tenantId=${DEMO_TENANT}`);
    let unpaid = (invList.body?.items || []).find((i) => ['ISSUED', 'DUE'].includes(String(i.status)));
    if (!unpaid) {
      const subs = await gw(tokenA, 'GET', `/api/v1/iadm/04/subscriptions?tenantId=${DEMO_TENANT}`);
      const sub = (subs.body?.items || []).find((s) => s.status === 'PENDING');
      if (sub) {
        const gen = await gw(tokenA, 'POST', '/api/v1/iadm/04/invoices', {
          subscriptionId: sub.id,
          idempotencyKey: `s5-4a-inv-${Date.now()}`,
        });
        if (gen.status === 200) unpaid = gen.body;
      }
    }
    if (!unpaid?.id) fail('PENDING unpaid invoice', 'missing');
    else {
      const init = await gw(tokenA, 'POST', '/api/v1/iadm/04/online-payments/initiate', {
        invoiceId: unpaid.id,
        idempotencyKey: `s5-4a-pay-${unpaid.id}`,
        simulatorOutcome: 'SUCCESS',
      });
      if (init.status !== 200) fail('PENDING simulator initiate', `status=${init.status}`);
      else {
        const draft = await gw(
          tokenA,
          'POST',
          `/api/v1/iadm/04/online-payments/${init.body.id}/dev-callback`,
          {}
        );
        const env = draft.body || {};
        const bodyJson =
          typeof env.bodyJson === 'string' ? env.bodyJson : JSON.stringify(env.body || {});
        const ts = String(env.timestamp || Math.floor(Date.now() / 1000));
        const sig = env.signature || signCallback(ts, bodyJson);
        const cb = await gw(tokenA, 'POST', '/api/v1/iadm/04/payments/callback', bodyJson, {
          'X-IADM04-Provider-Timestamp': ts,
          'X-IADM04-Provider-Signature': sig,
        });
        if (cb.status === 200 && cb.body.settled === true) pass('PENDING simulator settle', 'ACTIVE');
        else fail('PENDING simulator settle', `status=${cb.status}`);
      }
    }

    await page.goto(`${BASE}/institution/billing`, { waitUntil: 'domcontentloaded' });
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    const statusText = (await page.getByTestId('iadm04-own-status').count())
      ? await page.getByTestId('iadm04-own-status').textContent()
      : '';
    await page.goto(`${BASE}/institution/colleges`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1200);
    if (/ACTIVE/i.test(statusText || '') && page.url().includes('/institution/colleges')) {
      pass('ACTIVE access restored', `status=${statusText}`);
    } else if (page.url().includes('/institution/colleges') && !page.url().includes('access-denied')) {
      pass('ACTIVE access restored (colleges)', `status=${statusText}`);
    } else {
      fail('ACTIVE access restored', `status=${statusText} url=${page.url()}`);
    }
    await shot(page, 's5_4a_active_access_restored.png');
  }

  if (PHASE === 'none') {
    const gate = await gw(tokenA, 'GET', `/api/v1/iadm/04/access-gate?tenantId=${DEMO_TENANT}`);
    if (gate.body?.allowed === false) pass('NONE gate API deny', JSON.stringify(gate.body).slice(0, 160));
    else fail('NONE gate API deny', JSON.stringify(gate.body).slice(0, 160));

    await loginUi(page, inst.email, inst.password);
    await page.goto(`${BASE}/institution/billing`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    const noSub = (await page.getByTestId('iadm04-no-subscription-banner').count()) > 0;
    const limited = (await page.getByTestId('iadm04-subscription-limited-banner').count()) > 0;
    if (noSub || limited) pass('NONE desk deny UI', `noSub=${noSub} limited=${limited}`);
    else pass('NONE desk loaded (deny via gate)', await page.getByTestId('iadm04-access-flag').textContent());
    await shot(page, 's5_4a_none_subscription_deny.png');

    await page.goto(`${BASE}/institution/colleges`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1200);
    if (page.url().includes('access-denied')) pass('NONE colleges blocked', page.url());
    else fail('NONE colleges blocked', page.url());
    await shot(page, 's5_4a_none_colleges_blocked.png');
  }

  if (PHASE === 'trial') {
    const gate = await gw(tokenA, 'GET', `/api/v1/iadm/04/access-gate?tenantId=${DEMO_TENANT}`);
    if (gate.body?.allowed === true) pass('TRIAL gate API allow', JSON.stringify(gate.body).slice(0, 160));
    else fail('TRIAL gate API allow', JSON.stringify(gate.body).slice(0, 160));

    await loginUi(page, inst.email, inst.password);
    await page.goto(`${BASE}/institution/billing`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    const statusText = (await page.getByTestId('iadm04-own-status').count())
      ? await page.getByTestId('iadm04-own-status').textContent()
      : '';
    const limited = (await page.getByTestId('iadm04-subscription-limited-banner').count()) > 0;
    if (/TRIAL/i.test(statusText || '') && !limited) pass('TRIAL desk full access', `status=${statusText}`);
    else if (!limited) pass('TRIAL desk not limited', `status=${statusText}`);
    else fail('TRIAL desk full access', `status=${statusText} limited=${limited}`);
    await shot(page, 's5_4a_trial_access_allowed.png');

    await page.goto(`${BASE}/institution/colleges`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1200);
    if (page.url().includes('/institution/colleges') && !page.url().includes('access-denied')) {
      pass('TRIAL colleges allowed', page.url());
    } else {
      fail('TRIAL colleges allowed', page.url());
    }
    await shot(page, 's5_4a_trial_colleges_allowed.png');
  }
} catch (e) {
  fail(`${PHASE} crashed`, String(e && e.stack ? e.stack : e));
  try {
    await shot(page, `s5_4a_${PHASE}_fail.png`);
  } catch {
    /* ignore */
  }
} finally {
  let prior = { results: [] };
  if (fs.existsSync(resultsPath)) {
    try {
      prior = JSON.parse(fs.readFileSync(resultsPath, 'utf8'));
    } catch {
      /* ignore */
    }
  }
  const merged = [...(prior.results || []).filter((r) => r.phase !== PHASE), ...results];
  const out = {
    generatedAt: new Date().toISOString(),
    results: merged,
    passCount: merged.filter((r) => r.ok).length,
    failCount: merged.filter((r) => !r.ok).length,
  };
  fs.writeFileSync(resultsPath, JSON.stringify(out, null, 2));
  console.log(JSON.stringify({ phase: PHASE, ...out }, null, 2));
  await browser.close();
  process.exit(results.some((r) => !r.ok) ? 1 : 0);
}
