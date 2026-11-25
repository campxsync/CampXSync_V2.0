/**
 * IADM-03 Pre-step P headed demo:
 * - US-033 Auditor (R28) evidence desk + mutation denied
 * - US-018 System Admin (R02) technical assign allow + business-role deny
 * Credentials from .env.audit / seed_accounts — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/prestep-p');
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

function auditorCreds() {
  return {
    email: seed.auditor?.email || process.env.AUDITOR_EMAIL || 'auditor.iadm02.dev@campx.local',
    password: seed.auditor?.password || process.env.AUDITOR_PW || '',
  };
}

function r02Creds() {
  return {
    email:
      seed.r02?.email ||
      process.env.SYSTEM_ADMIN_EMAIL ||
      process.env.IADM02_R02_EMAIL ||
      'systemadmin.campx@gmail.com',
    password:
      seed.r02?.password ||
      process.env.SYSTEM_ADMIN_PW ||
      process.env.IADM02_R02_PW ||
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
  await page.waitForURL((u) => {
    const p = u.pathname || '';
    return p === expectPrefix || p.startsWith(expectPrefix + '/') || p === '/access-denied';
  }, { timeout: 60000 });
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

const browser = await chromium.launch({ headless: false, slowMo: 40 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  // --- US-033 Auditor ---
  const aud = auditorCreds();
  if (!aud.password) throw new Error('Auditor password missing (seed auditor or AUDITOR_PW)');
  const audToken = await passwordToken(aud.email, aud.password);

  const evidenceApi = await fetch(`${GW}/api/v1/iadm/03/audit-evidence?limit=20`, {
    headers: { Authorization: `Bearer ${audToken}` },
  });
  const evidenceBody = await evidenceApi.json().catch(() => ({}));
  results.push({
    step: 'US-033 API GET audit-evidence',
    status: evidenceApi.status,
    count: Array.isArray(evidenceBody.items) ? evidenceBody.items.length : 0,
    pass: evidenceApi.status === 200,
  });

  const mutateApi = await fetch(`${GW}/api/v1/iadm/03/grants/assign`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${audToken}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `prestep-p-aud-deny-${Date.now()}`,
    },
    body: JSON.stringify({
      subjectUserId: '00000000-0000-0000-0000-000000000001',
      targetRole: 'FACULTY',
      reason: 'US-033 auditor mutation must be denied',
      tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
    }),
  });
  const mutateBody = await mutateApi.json().catch(() => ({}));
  const denyText = String(mutateBody.detail || '');
  results.push({
    step: 'US-033 API mutation denied',
    status: mutateApi.status,
    detail: denyText,
    errorCode: mutateBody.errorCode,
    pass:
      mutateApi.status === 403 &&
      (/Auditor access is read-only/i.test(denyText) ||
        mutateBody.errorCode === 'IADM03_AUDITOR_READONLY'),
  });

  await loginUi(page, aud.email, aud.password, '/platform/identity/evidence');
  await page.goto(`${BASE}/platform/identity/evidence`, { waitUntil: 'networkidle' });
  await page.getByTestId('iadm03-evidence-desk').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-evidence-audit-count').waitFor({ timeout: 30000 });
  // Wait until loading finishes (counts may be 0 briefly then populate)
  await page.waitForFunction(() => {
    const loading = document.querySelector('[data-testid="iadm03-evidence-loading"]');
    return !loading || loading.textContent?.trim() === '';
  }, null, { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(1500);
  results.push({
    step: 'US-033 headed evidence desk',
    shot: await shot(page, 'us033_evidence_desk.png'),
    pass: true,
  });
  const hasMutationForm = await page.locator('[data-testid="iadm03-grant-form"]').count();
  results.push({
    step: 'US-033 no grant form on evidence desk',
    count: hasMutationForm,
    pass: hasMutationForm === 0,
  });
  await page.getByTestId('iadm03-evidence-probe-mutation').click();
  await page.getByTestId('iadm03-evidence-deny').waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForFunction(() => {
    const el = document.querySelector('[data-testid="iadm03-evidence-deny"]');
    const t = el?.textContent || '';
    return t && !t.includes('Probing');
  }, null, { timeout: 30000 });
  const headedDeny = await page.getByTestId('iadm03-evidence-deny').innerText();
  results.push({
    step: 'US-033 headed mutation probe denied',
    detail: headedDeny,
    shot: await shot(page, 'us033_mutation_denied.png'),
    pass: /403|read-only|FORBIDDEN|IADM03_AUDITOR/i.test(headedDeny),
  });

  // --- US-018 System Admin ---
  await context.clearCookies();
  const r02 = r02Creds();
  if (!r02.password) throw new Error('System Admin password missing (SYSTEM_ADMIN_PW or seed.r02)');
  const r02Token = await passwordToken(r02.email, r02.password);

  const denyR03 = await fetch(`${GW}/api/v1/iadm/03/grants/assign`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${r02Token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `prestep-p-r02-deny-${Date.now()}`,
    },
    body: JSON.stringify({
      subjectUserId: '69822238-2ca1-4e3b-86d4-c76d76a78024',
      targetRole: 'INSTITUTION_ADMIN',
      reason: 'US-018 R02 cannot assign R03',
      tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
    }),
  });
  const denyR03Body = await denyR03.json().catch(() => ({}));
  results.push({
    step: 'US-018 API deny INSTITUTION_ADMIN',
    status: denyR03.status,
    detail: denyR03Body.detail,
    pass:
      denyR03.status === 403 &&
      /technical service identities only|cannot assign/i.test(String(denyR03Body.detail || '')),
  });

  // Ceiling probe is HMAC-free on the service; gateway may require JWT — send Bearer.
  const allowCeil = await fetch(`${GW}/api/v1/iadm/03/ceilings/evaluate`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${r02Token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ grantor: 'R02', targetRole: 'SERVICE', selfGrant: false }),
  });
  const allowCeilBody = await allowCeil.json().catch(() => ({}));
  results.push({
    step: 'US-018 ceiling allows SERVICE',
    status: allowCeil.status,
    allowed: allowCeilBody.allowed,
    pass: allowCeil.status === 200 && allowCeilBody.allowed === true,
  });

  await loginUi(page, r02.email, r02.password, '/platform/identity/technical');
  await page.goto(`${BASE}/platform/identity/technical`, { waitUntil: 'networkidle' });
  await page.getByTestId('iadm03-technical-desk').waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  results.push({
    step: 'US-018 headed technical desk',
    shot: await shot(page, 'us018_technical_desk.png'),
    pass: true,
  });
  await page.getByTestId('iadm03-technical-probe-deny').click();
  await page.getByTestId('iadm03-technical-deny').waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForFunction(() => {
    const el = document.querySelector('[data-testid="iadm03-technical-deny"]');
    const t = el?.textContent || '';
    return t && !t.includes('Probing');
  }, null, { timeout: 45000 });
  const r02Deny = await page.getByTestId('iadm03-technical-deny').innerText();
  results.push({
    step: 'US-018 headed R03 probe denied',
    detail: r02Deny,
    shot: await shot(page, 'us018_r03_denied.png'),
    pass: /403|technical|cannot assign/i.test(r02Deny),
  });
} catch (err) {
  results.push({ step: 'FATAL', pass: false, error: String(err?.message || err) });
  try {
    await page.screenshot({ path: path.join(evidence, 'prestep_p_fail.png'), fullPage: true });
  } catch {
    /* ignore */
  }
} finally {
  await browser.close();
  const out = {
    story: 'IADM-03 Pre-step P (US-033 + US-018)',
    filmedAt: new Date().toISOString(),
    pass: results.every((r) => r.pass !== false),
    results,
  };
  fs.writeFileSync(path.join(evidence, 'PRESTEP_P_HEADED_RESULTS.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  process.exit(out.pass ? 0 : 1);
}
