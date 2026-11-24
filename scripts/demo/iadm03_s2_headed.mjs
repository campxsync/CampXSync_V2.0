/**
 * IADM-03 S2 headed demo: appoint form, ceiling denial, R03 assign/deny (US-015…020).
 * Credentials from .env.audit — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s2');
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

const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const MAC_TENANT = '72ecbfe3-2077-41b5-8a20-b6d3e045311b';
const SUBJECT = '69822238-2ca1-4e3b-86d4-c76d76a78024'; // iadm02.consumer.dev
const results = [];

async function login(page, email, password, expectPathPrefix) {
  await page.goto(`${BASE}/login?returnTo=${encodeURIComponent(expectPathPrefix)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL((url) => {
    const p = url.pathname || '';
    return p === expectPathPrefix || p.startsWith(expectPathPrefix + '/');
  }, { timeout: 60000 });
}

async function injectR03(context, page) {
  const r03Email = process.env.MAC_R03_EMAIL || process.env.INSTITUTION_ADMIN_EMAIL || 'maharani.campx@gmail.com';
  const r03Pw =
    process.env.MAC_R03_PW || process.env.INSTITUTION_ADMIN_PW || process.env.MAHARANI_PW || '';
  if (r03Pw) {
    await login(page, r03Email, r03Pw, '/institution/access');
    return true;
  }
  const supabaseUrl = (process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '').replace(
    /\/+$/,
    ''
  );
  const serviceKey = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  if (!supabaseUrl || !serviceKey || !anonKey) {
    throw new Error('R03 password missing and Auth Admin/anon env not configured');
  }
  const linkRes = await fetch(`${supabaseUrl}/auth/v1/admin/generate_link`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${serviceKey}`,
      apikey: serviceKey,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ type: 'magiclink', email: r03Email }),
  });
  const linkBody = await linkRes.json();
  const emailOtp = linkBody?.email_otp || linkBody?.properties?.email_otp;
  if (!linkRes.ok || !emailOtp) {
    throw new Error('R03 magic-link OTP generation failed');
  }
  const verifyRes = await fetch(`${supabaseUrl}/auth/v1/verify`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${anonKey}`,
      apikey: anonKey,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ type: 'email', email: r03Email, token: emailOtp }),
  });
  const session = await verifyRes.json();
  if (!verifyRes.ok || !session?.access_token || !session?.refresh_token) {
    throw new Error('R03 OTP verify failed');
  }
  const projectRef = new URL(supabaseUrl).hostname.split('.')[0];
  const storageKey = `sb-${projectRef}-auth-token`;
  const sessionJson = JSON.stringify({
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_at: session.expires_at,
    expires_in: session.expires_in,
    token_type: session.token_type || 'bearer',
    user: session.user,
  });
  const cookieVal = `base64-${Buffer.from(sessionJson).toString('base64url')}`;
  await context.clearCookies();
  const host = new URL(BASE).hostname;
  await context.addCookies([
    {
      name: storageKey,
      value: cookieVal,
      domain: host,
      path: '/',
      httpOnly: false,
      secure: false,
      sameSite: 'Lax',
    },
  ]);
  return true;
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

const browser = await chromium.launch({ headless: false, slowMo: 50 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
  const r01Pw = process.env.PLATFORM_ADMIN_PW;
  await login(page, r01Email, r01Pw, '/platform/identity');

  await page.goto(`${BASE}/platform/identity`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-platform-desk').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-claims-contract').click();
  await page.waitForTimeout(1500);
  results.push({ step: 'R01 claims contract', shot: await shot(page, 's2_r01_claims.png'), ok: true });

  await page.goto(`${BASE}/platform/identity/profiles`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-appoint-form').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-appoint-email').fill(`iadm03.s2.appoint.${Date.now()}@campx.local`);
  await page.getByTestId('iadm03-appoint-name').fill('S2 Appoint Demo');
  results.push({ step: 'R01 appoint form', shot: await shot(page, 's2_r01_appoint.png'), ok: true });

  await page.goto(`${BASE}/platform/identity/profiles/${SUBJECT}`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-access-detail').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-detail-loading').waitFor({ state: 'detached', timeout: 45000 }).catch(() => null);
  await page.getByTestId('iadm03-ceiling-r03-r03').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-ceiling-r03-r03').scrollIntoViewIfNeeded();
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/platform/identity/ceilings/evaluate') && r.request().method() === 'POST',
      { timeout: 45000 }
    ),
    page.getByTestId('iadm03-ceiling-r03-r03').click(),
  ]);
  await page.getByTestId('iadm03-ceiling-result').waitFor({ timeout: 15000 });
  const ceilText = await page.getByTestId('iadm03-ceiling-result').textContent();
  results.push({
    step: 'R01 ceiling denial preview',
    shot: await shot(page, 's2_r01_ceiling.png'),
    ok: /Denied/i.test(ceilText || ''),
    detail: ceilText,
  });

  await context.clearCookies();
  await injectR03(context, page);
  await page.goto(`${BASE}/institution/access/profiles/${SUBJECT}`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-inst-access-detail').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-inst-detail-profile').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-inst-grant-role').selectOption('FACULTY');
  const reasonBox = page.getByTestId('iadm03-inst-grant-reason');
  await reasonBox.click();
  await reasonBox.fill('');
  await reasonBox.pressSequentially('S2 headed demo grant FACULTY', { delay: 15 });
  await page.getByTestId('iadm03-inst-grant-submit').waitFor({ state: 'visible', timeout: 10000 });
  await page.waitForFunction(() => {
    const btn = document.querySelector('[data-testid="iadm03-inst-grant-submit"]');
    return btn && !btn.disabled;
  }, null, { timeout: 10000 });
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/institution/access/grants/assign') && r.request().method() === 'POST',
      { timeout: 60000 }
    ),
    page.getByTestId('iadm03-inst-grant-submit').click(),
  ]);
  await page.getByTestId('iadm03-inst-grant-result').waitFor({ timeout: 45000 });
  const grantText = await page.getByTestId('iadm03-inst-grant-result').textContent();
  results.push({
    step: 'R03 assign FACULTY',
    shot: await shot(page, 's2_r03_assign.png'),
    ok: /Granted|already exists|equivalent/i.test(grantText || ''),
    detail: grantText,
    tenant: MAC_TENANT,
  });

  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/institution/access/grants/assign') && r.request().method() === 'POST',
      { timeout: 60000 }
    ),
    page.getByTestId('iadm03-inst-deny-r03').click(),
  ]);
  await page.getByTestId('iadm03-inst-ceiling-result').waitFor({ timeout: 30000 });
  const denyText = await page.getByTestId('iadm03-inst-ceiling-result').textContent();
  results.push({
    step: 'R03→R03 denial',
    shot: await shot(page, 's2_r03_deny.png'),
    ok: /Denied/i.test(denyText || ''),
    detail: denyText,
  });

  const allOk = results.every((r) => r.ok);
  fs.writeFileSync(path.join(evidence, 'S2_HEADED_RESULTS.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify({ ok: allOk, results }, null, 2));
  if (!allOk) process.exitCode = 1;
} catch (err) {
  console.error('DEMO_FAIL', err && err.message ? err.message : err);
  try {
    await shot(page, 's2_fail.png');
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
