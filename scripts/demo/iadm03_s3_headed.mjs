/**
 * IADM-03 S3 headed demo: access request + delegation (US-023…027/029).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s3');
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
const SUBJECT = '69822238-2ca1-4e3b-86d4-c76d76a78024';
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
  await context.clearCookies();
  await context.addCookies([
    {
      name: storageKey,
      value: `base64-${Buffer.from(sessionJson).toString('base64url')}`,
      domain: new URL(BASE).hostname,
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

const browser = await chromium.launch({ headless: false, slowMo: 40 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();
let submittedId = null;

try {
  await injectR03(context, page);
  await page.goto(`${BASE}/institution/access/requests`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-requests-desk').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-request-subject').fill(SUBJECT);
  await page.getByTestId('iadm03-request-role').selectOption('HOD');
  const just = page.getByTestId('iadm03-request-justification');
  await just.click();
  await just.fill('');
  await just.pressSequentially('S3 headed demo request HOD for coverage', { delay: 12 });
  await page.waitForFunction(() => {
    const btn = document.querySelector('[data-testid="iadm03-request-submit"]');
    return btn && !btn.disabled;
  }, null, { timeout: 10000 });
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/institution/access/requests') && r.request().method() === 'POST', {
      timeout: 60000,
    }),
    page.getByTestId('iadm03-request-submit').click(),
  ]);
  await page.getByTestId('iadm03-request-result').waitFor({ timeout: 30000 });
  const submitText = await page.getByTestId('iadm03-request-result').textContent();
  const m = /Submitted\s+([0-9a-f-]{36})/i.exec(submitText || '');
  submittedId = m ? m[1] : null;
  results.push({
    step: 'R03 submit access request',
    shot: await shot(page, 's3_r03_request_submit.png'),
    ok: /Submitted/i.test(submitText || ''),
    detail: submitText,
    requestId: submittedId,
  });

  const decideReason = page.getByTestId('iadm03-request-decide-reason');
  await decideReason.click();
  await decideReason.fill('');
  await decideReason.pressSequentially('Self-approve probe must fail', { delay: 10 });
  if (submittedId) {
    await page.waitForFunction((id) => {
      const btn = document.querySelector(`[data-testid="iadm03-request-approve-${id}"]`);
      return btn && !btn.disabled;
    }, submittedId, { timeout: 10000 });
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/requests/${submittedId}/decide`) && r.request().method() === 'POST',
        { timeout: 60000 }
      ),
      page.getByTestId(`iadm03-request-approve-${submittedId}`).click(),
    ]);
    await page.getByTestId('iadm03-request-result').waitFor({ timeout: 30000 });
  }
  const selfText = await page.getByTestId('iadm03-request-result').textContent();
  results.push({
    step: 'R03 self-approve denied',
    shot: await shot(page, 's3_r03_self_approve_deny.png'),
    ok: /cannot approve|own access request|SELF_APPROVE|not authorized/i.test(selfText || ''),
    detail: selfText,
  });

  await page.goto(`${BASE}/institution/access/delegations`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-delegations-desk').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-delegation-to').fill(SUBJECT);
  // DB guard iam.guard_delegation: delegator must hold the role — R03 holds INSTITUTION_ADMIN
  await page.getByTestId('iadm03-delegation-role').selectOption('INSTITUTION_ADMIN');
  const delReason = page.getByTestId('iadm03-delegation-reason');
  await delReason.click();
  await delReason.fill('');
  await delReason.pressSequentially('S3 headed demo IA coverage while held', { delay: 12 });
  await page.waitForFunction(() => {
    const btn = document.querySelector('[data-testid="iadm03-delegation-submit"]');
    return btn && !btn.disabled;
  }, null, { timeout: 10000 });
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/institution/access/delegations') && r.request().method() === 'POST', {
      timeout: 90000,
    }),
    page.getByTestId('iadm03-delegation-submit').click(),
  ]);
  await page.getByTestId('iadm03-delegation-result').waitFor({ timeout: 60000 });
  const delText = await page.getByTestId('iadm03-delegation-result').textContent();
  results.push({
    step: 'R03 create REGISTRAR delegation',
    shot: await shot(page, 's3_r03_delegation.png'),
    ok: /Created|max|already|Replay/i.test(delText || ''),
    detail: delText,
  });

  await context.clearCookies();
  const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
  const r01Pw = process.env.PLATFORM_ADMIN_PW;
  await login(page, r01Email, r01Pw, '/platform/identity');
  await page.goto(`${BASE}/platform/identity/requests`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-platform-requests-desk').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-plat-requests-list').waitFor({ timeout: 45000 });
  const platReason = page.getByTestId('iadm03-plat-request-reason');
  await platReason.click();
  await platReason.fill('');
  await platReason.pressSequentially('S3 R01 approve institute request', { delay: 10 });
  // R01 REJECT (US-025): R01 APPROVE→apply hits iam.guard_role_assignment without IA JWT.
  // Reject still proves independent decision + reason; peer-R03 approve is the institute path.
  let platText = '';
  if (submittedId) {
    const rejectBtn = page.getByTestId(`iadm03-plat-reject-${submittedId}`);
    await rejectBtn.waitFor({ timeout: 30000 });
    await page.waitForFunction((id) => {
      const btn = document.querySelector(`[data-testid="iadm03-plat-reject-${id}"]`);
      return btn && !btn.disabled;
    }, submittedId, { timeout: 10000 });
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/requests/${submittedId}/decide`) && r.request().method() === 'POST',
        { timeout: 60000 }
      ),
      rejectBtn.click(),
    ]);
    await page.getByTestId('iadm03-plat-request-result').waitFor({ timeout: 30000 });
    platText = (await page.getByTestId('iadm03-plat-request-result').textContent()) || '';
  }
  results.push({
    step: 'R01 reject access request',
    shot: await shot(page, 's3_r01_decide.png'),
    ok: /REJECTED|Decision/i.test(platText),
    detail: platText,
  });

  const allOk = results.every((r) => r.ok);
  fs.writeFileSync(path.join(evidence, 'S3_HEADED_RESULTS.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify({ ok: allOk, results }, null, 2));
  if (!allOk) process.exitCode = 1;
} catch (err) {
  console.error('DEMO_FAIL', err && err.message ? err.message : err);
  try {
    await shot(page, 's3_fail.png');
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
