/**
 * IADM-03 S3b headed demo: US-030 catalogue change + US-031 governance read.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s3b');
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

async function injectSession(context, email) {
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
    body: JSON.stringify({ type: 'magiclink', email }),
  });
  const linkBody = await linkRes.json();
  const emailOtp = linkBody?.email_otp || linkBody?.properties?.email_otp;
  if (!emailOtp) {
    throw new Error(`No OTP for ${email}`);
  }
  const verifyRes = await fetch(`${supabaseUrl}/auth/v1/verify`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${anonKey}`,
      apikey: anonKey,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ type: 'email', email, token: emailOtp }),
  });
  const session = await verifyRes.json();
  if (!session?.access_token) {
    throw new Error(`Verify failed for ${email}`);
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
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function loadR01bEmail() {
  if (process.env.IADM02_R01B_EMAIL) return process.env.IADM02_R01B_EMAIL;
  const local = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
  if (fs.existsSync(local)) {
    try {
      const j = JSON.parse(fs.readFileSync(local, 'utf8'));
      if (j?.r01b?.email) return j.r01b.email;
    } catch {
      /* ignore */
    }
  }
  return 'platformadmin2.campx@gmail.com';
}

const browser = await chromium.launch({ headless: false, slowMo: 40 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();
let proposedId = null;

try {
  const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
  const r01Pw = process.env.PLATFORM_ADMIN_PW;
  await login(page, r01Email, r01Pw, '/platform/identity');

  // US-031 governance read
  await page.goto(`${BASE}/platform/identity/governance`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-governance-desk').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-governance-snapshot').waitFor({ timeout: 45000 });
  const snapText = (await page.getByTestId('iadm03-governance-snapshot').textContent()) || '';
  results.push({
    step: 'R01 governance snapshot',
    shot: await shot(page, 's3b_r01_governance.png'),
    ok: /NOT_PER_REQUEST|iadm03OnRequestPath|ceilingSamples/i.test(snapText),
    detail: snapText.slice(0, 400),
  });

  // US-030 propose (safe ADD of already-likely mapping or iam.audit.view on AUDITOR)
  await page.goto(`${BASE}/platform/identity/catalog-changes`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-catalog-changes-desk').waitFor({ timeout: 45000 });
  await page.getByTestId('iadm03-catalog-change-type').selectOption('ADD_MAPPING');
  await page.getByTestId('iadm03-catalog-role').fill('AUDITOR');
  await page.getByTestId('iadm03-catalog-permission').fill('iam.governance.read');
  const just = page.getByTestId('iadm03-catalog-justification');
  await just.click();
  await just.fill('');
  await just.pressSequentially('S3b R01 propose AUDITOR receives iam.governance.read', { delay: 10 });
  await page.waitForFunction(() => {
    const btn = document.querySelector('[data-testid="iadm03-catalog-propose-btn"]');
    return btn && !btn.disabled;
  }, null, { timeout: 10000 });
  await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/platform/identity/catalog-changes') && r.request().method() === 'POST',
      { timeout: 60000 }
    ),
    page.getByTestId('iadm03-catalog-propose-btn').click(),
  ]);
  await page.getByTestId('iadm03-catalog-flash').waitFor({ timeout: 30000 });
  const proposeText = (await page.getByTestId('iadm03-catalog-flash').textContent()) || '';
  const impactVisible = await page.getByTestId('iadm03-catalog-impact').count();
  // capture id from list (first PROPOSED row)
  await page.waitForTimeout(500);
  const row = page.locator('[data-testid^="iadm03-catalog-row-"]').first();
  const rowTestId = await row.getAttribute('data-testid');
  proposedId = rowTestId ? rowTestId.replace('iadm03-catalog-row-', '') : null;
  results.push({
    step: 'R01 propose catalog change',
    shot: await shot(page, 's3b_r01_propose.png'),
    ok: /Proposed|different R01|proposal/i.test(proposeText) && impactVisible > 0,
    detail: proposeText,
    requestId: proposedId,
  });

  // Self-approve must fail
  const reason = page.getByTestId('iadm03-catalog-decide-reason');
  await reason.click();
  await reason.fill('');
  await reason.pressSequentially('Self-approve probe must fail', { delay: 8 });
  if (proposedId) {
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/catalog-changes/${proposedId}/decide`) && r.request().method() === 'POST',
        { timeout: 60000 }
      ),
      page.getByTestId(`iadm03-catalog-approve-${proposedId}`).click(),
    ]);
    await page.getByTestId('iadm03-catalog-error').waitFor({ timeout: 30000 }).catch(() => null);
  }
  const selfErr =
    ((await page.getByTestId('iadm03-catalog-error').count())
      ? await page.getByTestId('iadm03-catalog-error').textContent()
      : '') || '';
  const selfFlash =
    ((await page.getByTestId('iadm03-catalog-flash').count())
      ? await page.getByTestId('iadm03-catalog-flash').textContent()
      : '') || '';
  results.push({
    step: 'R01 self-approve denied',
    shot: await shot(page, 's3b_r01_self_approve_deny.png'),
    ok: /different Super Admin|SELF_APPROVE|cannot decide|own change/i.test(selfErr + ' ' + selfFlash),
    detail: (selfErr || selfFlash).slice(0, 300),
  });

  // Different R01 approves via magic-link session
  const r01bEmail = loadR01bEmail();
  await injectSession(context, r01bEmail);
  await page.goto(`${BASE}/platform/identity/catalog-changes`, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('iadm03-catalog-changes-desk').waitFor({ timeout: 45000 });
  const reason2 = page.getByTestId('iadm03-catalog-decide-reason');
  await reason2.click();
  await reason2.fill('');
  await reason2.pressSequentially('S3b R01-B approve catalog mapping', { delay: 8 });
  let approveText = '';
  if (proposedId) {
    await page.getByTestId(`iadm03-catalog-approve-${proposedId}`).waitFor({ timeout: 30000 });
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/catalog-changes/${proposedId}/decide`) && r.request().method() === 'POST',
        { timeout: 90000 }
      ),
      page.getByTestId(`iadm03-catalog-approve-${proposedId}`).click(),
    ]);
    await page.waitForTimeout(800);
    approveText =
      ((await page.getByTestId('iadm03-catalog-flash').count())
        ? await page.getByTestId('iadm03-catalog-flash').textContent()
        : '') ||
      ((await page.getByTestId('iadm03-catalog-error').count())
        ? await page.getByTestId('iadm03-catalog-error').textContent()
        : '') ||
      '';
  }
  results.push({
    step: 'R01-B approve and apply',
    shot: await shot(page, 's3b_r01b_approve.png'),
    ok: /APPLIED|applied=true|APPROVE/i.test(approveText),
    detail: approveText,
    requestId: proposedId,
    approverEmail: r01bEmail,
  });

  const allOk = results.every((r) => r.ok);
  fs.writeFileSync(path.join(evidence, 'S3B_HEADED_RESULTS.json'), JSON.stringify(results, null, 2));
  console.log(JSON.stringify({ ok: allOk, results }, null, 2));
  if (!allOk) process.exitCode = 1;
} catch (err) {
  console.error('DEMO_FAIL', err && err.message ? err.message : err);
  try {
    await shot(page, 's3b_fail.png');
  } catch {
    /* ignore */
  }
  process.exitCode = 1;
} finally {
  await browser.close();
}
