/**
 * IADM-03 S1 headed demo: R01 platform desk + R03 institute desk (US-008…014).
 * Credentials from .env.audit — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s1');
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

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

const browser = await chromium.launch({ headless: false, slowMo: 60 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
  const r01Pw = process.env.PLATFORM_ADMIN_PW;
  await login(page, r01Email, r01Pw, '/platform/identity');
  await page.goto(`${BASE}/platform/identity`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-platform-desk').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-overview').waitFor({ timeout: 30000 });
  const cookieNames = (await context.cookies()).map((c) => c.name);
  fs.writeFileSync(path.join(evidence, 's1_r01_cookie_names.json'), JSON.stringify(cookieNames, null, 2));
  results.push({ step: 'R01 overview+catalogues', shot: await shot(page, 's1_r01_desk.png'), ok: true });

  await page.goto(`${BASE}/platform/identity/profiles`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-profiles-desk').waitFor({ timeout: 30000 });
  await page.getByTestId('iadm03-profiles-loading').waitFor({ state: 'detached', timeout: 45000 }).catch(() => null);
  await page.getByTestId('iadm03-profiles-table').waitFor({ timeout: 30000 });
  results.push({ step: 'R01 profiles list', shot: await shot(page, 's1_r01_profiles.png'), ok: true });

  // Prefer Institution Admin row for access detail
  const r03Row = page.locator('[data-testid^="iadm03-profile-row-"]').filter({ hasText: 'INSTITUTION_ADMIN' }).first();
  if (await r03Row.count()) {
    await r03Row.locator('a').first().click();
    await page.getByTestId('iadm03-access-detail').waitFor({ timeout: 30000 });
    await page.getByTestId('iadm03-detail-loading').waitFor({ state: 'detached', timeout: 30000 }).catch(() => null);
    results.push({ step: 'R01 access detail', shot: await shot(page, 's1_r01_detail.png'), ok: true });
  }

  await context.clearCookies();
  const r03Email = process.env.MAC_R03_EMAIL || process.env.INSTITUTION_ADMIN_EMAIL || 'maharani.campx@gmail.com';
  const r03Pw =
    process.env.MAC_R03_PW || process.env.INSTITUTION_ADMIN_PW || process.env.MAHARANI_PW || '';
  let r03Ok = false;
  if (r03Pw) {
    await login(page, r03Email, r03Pw, '/institution/access');
    r03Ok = true;
  } else {
    // DEV fallback: Auth Admin generate_link → email OTP verify → inject SSR cookies on localhost.
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
    // Match @supabase/ssr cookie encoding (base64- prefix).
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
    r03Ok = true;
  }
  await page.goto(`${BASE}/institution/access`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-institution-desk').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-inst-loading').waitFor({ state: 'detached', timeout: 60000 }).catch(() => null);
  await page.getByTestId('iadm03-inst-overview').waitFor({ timeout: 30000 });
  results.push({
    step: 'R03 institute desk',
    shot: await shot(page, 's1_r03_desk.png'),
    ok: r03Ok,
  });

  fs.writeFileSync(path.join(evidence, 'S1_HEADED_RESULTS.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify({ ok: true, results }, null, 2));
} catch (err) {
  console.error('DEMO_FAIL', err && err.message ? err.message : err);
  try {
    await shot(page, 's1_fail.png');
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
