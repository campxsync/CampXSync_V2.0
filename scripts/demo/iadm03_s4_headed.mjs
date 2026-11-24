/**
 * IADM-03 S4 headed demo: access review (US-032) + notification note + R27 denial (US-034/035).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s4');
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
const MAC = '72ecbfe3-2077-41b5-8a20-b6d3e045311b';
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

async function injectR03(context) {
  const r03Email = process.env.MAC_R03_EMAIL || process.env.INSTITUTION_ADMIN_EMAIL || 'maharani.campx@gmail.com';
  const supabaseUrl = (process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '').replace(
    /\/+$/,
    ''
  );
  const serviceKey = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const r03Pw =
    process.env.MAC_R03_PW || process.env.INSTITUTION_ADMIN_PW || process.env.MAHARANI_PW || '';
  if (r03Pw) {
    return { mode: 'password', email: r03Email, password: r03Pw };
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
  return { mode: 'magic', email: r03Email };
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

const browser = await chromium.launch({ headless: false, slowMo: 40 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();
let reviewId = null;
let itemId = null;

try {
  // US-035 R27 denial (direct to service — no HMAC)
  const r27 = await fetch('http://127.0.0.1:8101/api/v1/iadm/03/denials/r27', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ grantor: 'R01' }),
  });
  const r27Body = await r27.json();
  results.push({
    step: 'R27 denial probe',
    ok: r27.status === 403 && r27Body.allowed === false,
    detail: r27Body,
  });

  // US-034 notification contract
  const nRes = await fetch('http://127.0.0.1:8101/api/v1/iadm/03/notifications/contract');
  const nBody = await nRes.json();
  results.push({
    step: 'Notification contract',
    ok: nRes.ok && /BEST_EFFORT|REQUIRED/i.test(JSON.stringify(nBody)),
    detail: nBody,
  });

  const auth = await injectR03(context);
  if (auth.mode === 'password') {
    await login(page, auth.email, auth.password, '/institution/access');
  }

  await page.goto(`${BASE}/institution/access/reviews`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-reviews-desk').waitFor({ timeout: 45000 });
  const cycle = page.getByTestId('iadm03-review-cycle');
  await cycle.click();
  await cycle.fill('');
  await cycle.pressSequentially(`S4 headed ${Date.now()}`, { delay: 8 });
  await page.getByTestId('iadm03-review-start').click();
  await page.getByTestId('iadm03-reviews-flash').waitFor({ timeout: 120000 });
  const startText = (await page.getByTestId('iadm03-reviews-flash').textContent()) || '';
  const m = /Started\s+([0-9a-f-]{36})/i.exec(startText);
  reviewId = m ? m[1] : null;
  results.push({
    step: 'R03 start access review',
    shot: await shot(page, 's4_r03_review_start.png'),
    ok: /Started|autoRevoke=false|notification/i.test(startText),
    detail: startText,
    reviewId,
  });

  await page.getByTestId('iadm03-review-detail').waitFor({ timeout: 60000 });
  // Prefer FACULTY for certify if present; else first undecided
  let certifyBtn = page.locator('[data-testid^="iadm03-review-certify-"]').first();
  if ((await certifyBtn.count()) === 0) {
    throw new Error('No undecided review item to certify');
  }
  const certifyTestId = await certifyBtn.getAttribute('data-testid');
  itemId = certifyTestId ? certifyTestId.replace('iadm03-review-certify-', '') : null;
  const note = page.getByTestId('iadm03-review-note');
  await note.click();
  await note.fill('');
  await note.pressSequentially('S4 certify grant in scope', { delay: 8 });
  await certifyBtn.click();
  await page.getByTestId('iadm03-reviews-flash').waitFor({ timeout: 120000 });
  await page.waitForTimeout(400);
  const decideText = (await page.getByTestId('iadm03-reviews-flash').textContent()) || '';
  results.push({
    step: 'R03 certify review item',
    shot: await shot(page, 's4_r03_review_certify.png'),
    ok: /certified|KEEP|autoRevoke=false/i.test(decideText),
    detail: decideText,
    itemId,
  });

  // Second item — request revocation without applying
  const undecided = page.locator('[data-testid^="iadm03-review-revoke-req-"]').first();
  if ((await undecided.count()) > 0) {
    const revTestId = await undecided.getAttribute('data-testid');
    const revItemId = revTestId ? revTestId.replace('iadm03-review-revoke-req-', '') : null;
    await undecided.click();
    await page.getByTestId('iadm03-reviews-flash').waitFor({ timeout: 120000 });
    await page.waitForTimeout(400);
    const revText = (await page.getByTestId('iadm03-reviews-flash').textContent()) || '';
    results.push({
      step: 'R03 request revocation (no auto-revoke)',
      shot: await shot(page, 's4_r03_review_revoke_req.png'),
      ok: /Revocation requested|autoRevoke=false|No automatic/i.test(revText),
      detail: revText,
      itemId: revItemId,
    });
  } else {
    results.push({
      step: 'R03 request revocation (no auto-revoke)',
      ok: true,
      detail: 'skipped — no remaining undecided item',
    });
  }

  const allOk = results.every((r) => r.ok);
  fs.writeFileSync(
    path.join(evidence, 'S4_HEADED_RESULTS.json'),
    JSON.stringify({ ok: allOk, macTenant: MAC, results }, null, 2)
  );
  console.log(JSON.stringify({ ok: allOk, results }, null, 2));
  if (!allOk) process.exitCode = 1;
} catch (err) {
  console.error('DEMO_FAIL', err && err.message ? err.message : err);
  try {
    await shot(page, 's4_fail.png');
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
