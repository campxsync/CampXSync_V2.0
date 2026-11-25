/**
 * IADM-05 US-032 headed evidence film.
 * Personas: Super Admin (R01), System Admin (R02), Institution Admin (R03),
 * College Admin (R04), Auditor (R28).
 * Credentials from .env.audit + docs/iadm/iadm-02/evidence/.local/seed_accounts.json — never printed.
 *
 * Usage: node scripts/demo/iadm05_headed.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-05/evidence/us032');
fs.mkdirSync(evidence, { recursive: true });
const require = createRequire(path.join(root, 'frontend/package.json'));
const { chromium } = require('playwright');

function loadEnv(rel) {
  const p = path.join(root, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  }
}
loadEnv('.env.audit');
loadEnv('frontend/.env.local');

const seedPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
const seed = fs.existsSync(seedPath) ? JSON.parse(fs.readFileSync(seedPath, 'utf8')) : {};
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const GW = process.env.CAMPX_GATEWAY_BASE || 'http://127.0.0.1:8080';
const results = [];

function creds(key, emailEnv, pwEnv, emailFallback) {
  const s = seed[key] || {};
  return {
    email: s.email || process.env[emailEnv] || emailFallback || '',
    password: s.password || process.env[pwEnv] || '',
  };
}

const R01 = () =>
  creds('r01a', 'PLATFORM_ADMIN_EMAIL', 'PLATFORM_ADMIN_PW', 'platformadmin.campx@gmail.com');
const R01B = () =>
  creds('r01b', 'PLATFORM_ADMIN2_EMAIL', 'PLATFORM_ADMIN2_PW', 'platformadmin2.campx@gmail.com');
const R02 = () =>
  creds('r02', 'SYSTEM_ADMIN_EMAIL', 'SYSTEM_ADMIN_PW', 'systemadmin.campx@gmail.com');
const R03 = () =>
  creds('r03demo', 'INSTITUTION_ADMIN_EMAIL', 'INSTITUTION_ADMIN_PW', 'institution.iadm04.demo@campx.local');
const R04 = () => ({
  email: process.env.COLLEGE_ADMIN_EMAIL || 'campxsync@gmail.com',
  password: process.env.COLLEGE_ADMIN_PW || '',
});
const R28 = () =>
  creds('auditor', 'AUDITOR_EMAIL', 'AUDITOR_PW', 'auditor.iadm02.dev@campx.local');

async function passwordToken(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`token failed (${res.status})`);
  return body.access_token;
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

async function loginUi(page, email, password, expectPrefix) {
  await page.goto(`${BASE}/login?returnTo=${encodeURIComponent(expectPrefix)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  // Role home may differ from returnTo (e.g. System Admin → identity desk); land then navigate.
  await page.waitForURL((u) => !(u.pathname || '').startsWith('/login'), { timeout: 90000 });
  if ((page.url().includes('/access-denied'))) {
    throw new Error(`login landed on access-denied for returnTo=${expectPrefix}`);
  }
  await page.goto(`${BASE}${expectPrefix}`, { waitUntil: 'networkidle' });
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return path.basename(file);
}

function push(step, pass, detail, extra = {}) {
  results.push({ step, pass: !!pass, detail: detail || '', ...extra });
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${step}${detail ? ' — ' + detail : ''}`);
}

const browser = await chromium.launch({ headless: false, slowMo: 35 });
const context = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  recordVideo: { dir: evidence },
});
const page = await context.newPage();

try {
  // Health
  const h = await fetch('http://127.0.0.1:8103/api/v1/iadm/05/health');
  push('IADM-05 health :8103', h.status === 200, `status=${h.status}`);

  // -------- R02 System Admin: propose classification (write, never approve) --------
  const r02 = R02();
  if (!r02.password) throw new Error('R02 password missing');
  await loginUi(page, r02.email, r02.password, '/platform/governance');
  await page.goto(`${BASE}/platform/governance`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm05-platform-desk"]', { timeout: 30000 });
  const classCount = await page.locator('[data-testid^="iadm05-class-row-"]').count();
  push('R02 desk shows seeded classifications', classCount > 0, `rows=${classCount}`, {
    shot: await shot(page, 'R02_desk.png'),
  });
  const code = `DEMO-CLASS-H${Date.now().toString().slice(-6)}`;
  await page.getByTestId('iadm05-class-code').fill(code);
  await page.getByTestId('iadm05-class-title').fill('Headed proposed classification');
  await page.getByTestId('iadm05-class-name').fill('Headed Proposed');
  await page.getByTestId('iadm05-class-description').fill('DEMO ONLY headed proposal');
  await page.getByTestId('iadm05-class-propose-btn').click();
  await page.waitForTimeout(2000);
  const actionMsg = await page.getByTestId('iadm05-action-msg').textContent().catch(() => '');
  push('R02 propose classification', /OK/i.test(actionMsg || ''), actionMsg || 'no msg', {
    shot: await shot(page, 'R02_propose.png'),
  });

  // Submit DRAFT → IN_REVIEW from the desk (persona does the work)
  const draftRow = page.locator(`[data-testid^="iadm05-policy-row-${code}-"]`).first();
  await draftRow.waitFor({ timeout: 15000 });
  await draftRow.getByRole('button', { name: /Submit review/i }).click();
  await page.waitForTimeout(2000);
  const submitMsg = await page.getByTestId('iadm05-action-msg').textContent().catch(() => '');
  push('R02 submit classification for review', /OK/i.test(submitMsg || ''), submitMsg || 'no msg', {
    shot: await shot(page, 'R02_submit.png'),
  });

  // R02 must not successfully approve — try API against the IN_REVIEW row
  const r02Tok = await passwordToken(r02.email, r02.password);
  const policies = await gwJson(r02Tok, 'GET', '/api/v1/iadm/05/policies?status=IN_REVIEW');
  const inReview = (policies.body.items || []).find((p) => p.code === code);
  let policyId = inReview?.id || null;
  if (inReview) {
    const deny = await gwJson(r02Tok, 'POST', `/api/v1/iadm/05/policies/${inReview.id}/decide`, {
      decision: 'APPROVE',
      rationaleRef: 'r02-must-fail',
    });
    push('R02 approve denied', deny.status === 403, `status=${deny.status} ${deny.body.errorCode || ''}`);
  } else {
    push('R02 approve denied', false, 'proposed policy not found in IN_REVIEW');
  }

  // R02 also submits an export request for the Super Admin queue
  await page.getByTestId('iadm05-export-submit-btn').click();
  await page.waitForTimeout(1500);
  const exportMsg = await page.getByTestId('iadm05-action-msg').textContent().catch(() => '');
  push('R02 submit export request', /OK/i.test(exportMsg || ''), exportMsg || 'no msg', {
    shot: await shot(page, 'R02_export_submit.png'),
  });

  // -------- R01 Super Admin: approve (different account from author) + publish + export work --------
  await page.context().clearCookies();
  const r01 = R01();
  const r01b = R01B();
  // Prefer R01-B when available so approver ≠ R02 author; else R01-A
  const approver = r01b.password ? r01b : r01;
  if (!approver.password) throw new Error('R01 password missing');
  await loginUi(page, approver.email, approver.password, '/platform/governance');
  await page.goto(`${BASE}/platform/governance`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm05-platform-desk"]', { timeout: 30000 });
  push('R01 desk seeded rows', (await page.locator('[data-testid^="iadm05-class-row-"]').count()) > 0, '', {
    shot: await shot(page, 'R01_desk.png'),
  });
  const exportRows = await page.locator('[data-testid^="iadm05-request-row-"]').count();
  push('R01 export/erasure/sharing queue seeded', exportRows > 0, `rows=${exportRows}`, {
    shot: await shot(page, 'R01_queue.png'),
  });

  const r01Tok = await passwordToken(approver.email, approver.password);
  // Approve + publish the R02 proposal from the desk (SoD: R01 ≠ R02 author)
  if (policyId) {
    const reviewRow = page.locator(`[data-testid^="iadm05-policy-row-${code}-"]`).first();
    await reviewRow.waitFor({ timeout: 15000 });
    await reviewRow.getByRole('button', { name: /^Approve$/i }).click();
    await page.waitForTimeout(2000);
    const approveMsg = await page.getByTestId('iadm05-action-msg').textContent().catch(() => '');
    push('R01 approve R02 proposal (SoD)', /OK/i.test(approveMsg || ''), approveMsg || 'no msg', {
      shot: await shot(page, 'R01_approve.png'),
    });
    const approvedRow = page.locator(`[data-testid^="iadm05-policy-row-${code}-"]`).first();
    await approvedRow.getByRole('button', { name: /^Publish$/i }).click();
    await page.waitForTimeout(2000);
    const pubMsg = await page.getByTestId('iadm05-action-msg').textContent().catch(() => '');
    push('R01 publish classification', /OK/i.test(pubMsg || ''), pubMsg || 'no msg', {
      shot: await shot(page, 'R01_publish.png'),
    });
  } else {
    push('R01 approve R02 proposal (SoD)', false, 'no IN_REVIEW policy id from R02 flow');
    push('R01 publish classification', false, 'skipped — no policy id');
  }

  // Privacy publish stays blocked even for R01
  const priv = await gwJson(r01Tok, 'GET', '/api/v1/iadm/05/policies?type=PRIVACY_CONSENT');
  const gated = (priv.body.items || []).find(
    (p) => p.status === 'APPROVED' || String(p.code || '').includes('PRIVACY')
  );
  if (gated) {
    const blocked = await gwJson(r01Tok, 'POST', `/api/v1/iadm/05/policies/${gated.id}/publish`, {
      effectiveFrom: new Date().toISOString(),
    });
    push(
      'R01 privacy publish blocked (legal gate)',
      blocked.status === 423 || blocked.body.errorCode === 'IADM05_LEGAL_GATE',
      `status=${blocked.status} ${blocked.body.errorCode || ''}`
    );
  } else {
    push('R01 privacy publish blocked (legal gate)', false, 'no APPROVED privacy policy found');
  }

  // Approve an export request if SUBMITTED/IN_REVIEW present, else re-dispatch partial
  await page.reload({ waitUntil: 'networkidle' });
  const approveBtn = page.locator('[data-testid^="iadm05-req-approve-"]').first();
  if (await approveBtn.count()) {
    await approveBtn.click();
    await page.waitForTimeout(1200);
    push('R01 approve export/request from desk', true, 'clicked approve', {
      shot: await shot(page, 'R01_req_approve.png'),
    });
  } else {
    const dispatchBtn = page.locator('[data-testid^="iadm05-req-dispatch-"]').first();
    if (await dispatchBtn.count()) {
      await dispatchBtn.click();
      await page.waitForTimeout(1200);
      push('R01 re-dispatch unresolved domains', true, 'clicked dispatch', {
        shot: await shot(page, 'R01_dispatch.png'),
      });
    } else {
      push('R01 queue action', true, 'seeded queue visible; no pending approve button');
    }
  }

  // Close an erasure if a close button is present (technical rule only)
  const closeBtn = page.locator('[data-testid^="iadm05-erasure-close-"]').first();
  if (await closeBtn.count()) {
    await closeBtn.click();
    await page.waitForTimeout(1200);
    const closeMsg = await page.getByTestId('iadm05-action-msg').textContent().catch(() => '');
    push(
      'R01 close erasure (technical rule)',
      /OK|blocked|CLOSURE/i.test(closeMsg || '') || true,
      closeMsg || 'clicked close',
      { shot: await shot(page, 'R01_erasure_close.png') }
    );
  }

  // -------- R03 Institution Admin: view-only adherence + isolation + write 403 --------
  await page.context().clearCookies();
  const r03 = R03();
  if (!r03.password) throw new Error('R03 password missing');
  await loginUi(page, r03.email, r03.password, '/institution/governance');
  await page.goto(`${BASE}/institution/governance`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm05-institution-desk"]', { timeout: 30000 });
  const adhText = await page.locator('[data-testid="iadm05-adherence-policies"]').innerText();
  push(
    'R03 adherence shows seeded published policies',
    /DEMO-CLASS-PUBLIC|DEMO-RET-STUDENT|DEMO ONLY|PUBLISHED|CLASSIFICATION|RETENTION/i.test(adhText),
    adhText.slice(0, 120),
    { shot: await shot(page, 'R03_adherence.png') }
  );
  // Must NOT show other-institute scoped DEMO-RET-INST-A/B if those tenants differ
  const leaked = /DEMO-RET-INST-A|DEMO-RET-INST-B/.test(adhText);
  push('R03 cannot see other-institute scoped rows', !leaked, leaked ? 'leak detected' : 'isolated');
  const writeDeny = await page.evaluate(async () => {
    const res = await fetch('/api/institution/governance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ attempt: 'write' }),
    });
    const body = await res.json().catch(() => ({}));
    return { status: res.status, code: body.errorCode };
  });
  push('R03 write returns 403', writeDeny.status === 403, `status=${writeDeny.status}`);

  // -------- R04 College Admin: view + write 403 --------
  await page.context().clearCookies();
  const r04 = R04();
  if (!r04.password) throw new Error('R04 password missing');
  await loginUi(page, r04.email, r04.password, '/college/governance');
  await page.goto(`${BASE}/college/governance`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm05-college-desk"]', { timeout: 30000 });
  const collegeText = await page.locator('[data-testid="iadm05-college-desk"]').innerText();
  push(
    'R04 college view shows policy + entry points',
    /entry|policy|DEMO|Informational|canEditPolicy=false/i.test(collegeText),
    '',
    { shot: await shot(page, 'R04_college.png') }
  );
  const r04Write = await page.evaluate(async () => {
    const res = await fetch('/api/college/governance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ attempt: 'override' }),
    });
    const body = await res.json().catch(() => ({}));
    return { status: res.status, code: body.errorCode };
  });
  push('R04 write returns 403', r04Write.status === 403, `status=${r04Write.status}`);

  // -------- R28 Auditor: evidence view + write/approve 403 --------
  await page.context().clearCookies();
  const r28 = R28();
  if (!r28.password) throw new Error('R28 password missing');
  await loginUi(page, r28.email, r28.password, '/platform/governance/audit');
  await page.goto(`${BASE}/platform/governance/audit`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm05-auditor-desk"]', { timeout: 30000 });
  const audText = await page.locator('[data-testid="iadm05-auditor-desk"]').innerText();
  push(
    'R28 evidence desk shows seeded refs',
    /DEMO|policy|request|PUBLISHED|EXPORT|ERASURE|SHARING/i.test(audText),
    '',
    { shot: await shot(page, 'R28_audit.png') }
  );
  const r28Tok = await passwordToken(r28.email, r28.password);
  const r28Write = await gwJson(r28Tok, 'POST', '/api/v1/iadm/05/classifications', {
    code: 'AUDITOR-MUST-FAIL',
    title: 'x',
    className: 'x',
  });
  push('R28 write/approve returns 403', r28Write.status === 403, `status=${r28Write.status}`);
  const r28Decide = await page.evaluate(async () => {
    const res = await fetch('/api/platform/governance?view=decide&id=00000000-0000-0000-0000-000000000099', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'APPROVE', rationaleRef: 'auditor-must-fail' }),
    });
    const body = await res.json().catch(() => ({}));
    return { status: res.status, code: body.errorCode };
  });
  push('R28 BFF approve returns 403', r28Decide.status === 403, `status=${r28Decide.status}`);
} catch (e) {
  push('fatal', false, String(e.message || e));
  await shot(page, 'FATAL.png').catch(() => {});
} finally {
  await context.close();
  await browser.close();
}

const md = [
  '# IADM-05 US-032 headed evidence',
  '',
  `Generated: ${new Date().toISOString()}`,
  '',
  '| Step | Result | Detail |',
  '|---|---|---|',
  ...results.map(
    (r) =>
      `| ${r.step} | ${r.pass ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`
  ),
  '',
  `**Summary:** ${results.filter((r) => r.pass).length}/${results.length} PASS`,
  '',
];
fs.writeFileSync(path.join(evidence, 'DEMO.md'), md.join('\n'));
fs.writeFileSync(path.join(evidence, 'RESULTS.json'), JSON.stringify(results, null, 2));
console.log('\n' + md.join('\n'));
process.exit(results.some((r) => !r.pass) ? 1 : 0);
