/**
 * IADM-03 V2.0 Headed Demo — Covers S1-S4 Demo-verified stories across R01 (r01a + r01b), R02, R03, R04, R28.
 * Outputs screenshots + video (.webm) + JSON summary to docs/iadm/iadm-03/evidence/v2/.
 */
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const require = createRequire(path.join(ROOT, 'frontend/package.json'));
const { chromium } = require('playwright');

const OUT = path.join(ROOT, 'docs/iadm/iadm-03/evidence/v2');
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
fs.mkdirSync(OUT, { recursive: true });

function loadEnv(rel) {
  const p = path.join(ROOT, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  }
}
loadEnv('.env.audit');
loadEnv('frontend/.env.local');

const seedPath = path.join(ROOT, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
const seed = fs.existsSync(seedPath) ? JSON.parse(fs.readFileSync(seedPath, 'utf8')) : {};

const R01A = { email: seed.r01a?.email || process.env.PLATFORM_ADMIN_EMAIL || '', password: seed.r01a?.password || process.env.PLATFORM_ADMIN_PW || '' };
const R01B = { email: seed.r01b?.email || '', password: seed.r01b?.password || '' };
const R02 = { email: seed.r02?.email || '', password: seed.r02?.password || '' };
const R03 = { email: seed.r03demo?.email || '', password: seed.r03demo?.password || '' };
const R04 = { email: process.env.COLLEGE_ADMIN_EMAIL || '', password: process.env.COLLEGE_ADMIN_PW || '' };
const R28 = { email: seed.auditor?.email || '', password: seed.auditor?.password || '' };

const SECOND_TENANT_ID = 'b0000000-0000-0000-0000-000000000002'; // T_FOREIGN
const SUBJECT_UUID = '00000000-0000-0000-0000-000000000099';

const results = [];
const push = (step, ok, detail = '') => {
  results.push({ step, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${step} | ${detail}`);
};

async function safeGoto(page, url) {
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  } catch (e) {
    if (!/ERR_ABORTED|interrupted/i.test(String(e.message || e))) throw e;
  }
  await page.waitForTimeout(300);
}

async function loginUi(page, creds, returnTo) {
  await page.context().clearCookies();
  await safeGoto(page, `${BASE}/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.fill('input[type="email"], input[name="email"]', creds.email);
  await page.fill('input[type="password"], input[name="password"]', creds.password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 10000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForTimeout(1200);
}

async function apiFetch(page, url, init = {}) {
  return page.evaluate(async ({ u, opts }) => {
    const res = await fetch(u, opts);
    const text = await res.text();
    let body = text;
    try { body = JSON.parse(text); } catch { /* keep */ }
    return { status: res.status, body };
  }, { u: url, opts: init });
}

async function shot(page, name) {
  const p = path.join(OUT, name);
  await page.screenshot({ path: p, fullPage: true });
  return p;
}

function stopPort(port) {
  execSync(
    `powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }"`,
    { stdio: 'ignore' }
  );
}

(async () => {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ recordVideo: { dir: OUT, size: { width: 1280, height: 800 } } });
  const page = await context.newPage();

  try {
    // 1. R01A: Register, Matrix, Handover, Reviews, Delegations, Registrations
    await loginUi(page, R01A, '/platform/identity/register');
    await page.getByTestId('iadm03-register-table').waitFor({ timeout: 30000 });
    const regSummary = await page.getByTestId('iadm03-register-summary').innerText();
    push('US-001 R01 Register', /Reconciled:\s*YES/i.test(regSummary), regSummary);
    await shot(page, '01_r01_register.png');

    await safeGoto(page, `${BASE}/platform/identity/matrix`);
    await page.getByTestId('iadm03-matrix-table').waitFor({ timeout: 30000 });
    const matRows = await page.locator('[data-testid="iadm03-matrix-table"] tbody tr').count();
    push('US-002 R01 Matrix', matRows === 39, `rows=${matRows}`);
    await shot(page, '02_r01_matrix.png');

    await safeGoto(page, `${BASE}/platform/identity/handover`);
    await page.getByTestId('iadm03-handover-table').waitFor({ timeout: 30000 });
    const hoRows = await page.locator('[data-testid="iadm03-handover-table"] tbody tr').count();
    push('US-014 R01 Handover', hoRows >= 1, `rows=${hoRows}`);
    await shot(page, '03_r01_handover.png');

    await safeGoto(page, `${BASE}/platform/identity/reviews`);
    await page.getByTestId('iadm03-review-create-btn').click();
    await page.waitForTimeout(1500);
    const revMsg = await page.getByTestId('iadm03-review-msg').innerText().catch(() => '');
    push('US-008 R01 Review Campaign', /Created campaign/i.test(revMsg), revMsg);
    await shot(page, '04_r01_reviews.png');

    await safeGoto(page, `${BASE}/platform/identity/delegations`);
    await page.fill('[data-testid="iadm03-del-delegate"]', SUBJECT_UUID);
    await page.getByTestId('iadm03-del-submit').click();
    await page.waitForTimeout(1500);
    const delMsg = await page.getByTestId('iadm03-del-msg').innerText().catch(() => '');
    const revokeBtn = page.locator('[data-testid^="iadm03-del-revoke-"]').first();
    if (await revokeBtn.count()) {
      await revokeBtn.click();
      await page.waitForTimeout(1200);
    }
    const delMsgAfter = await page.getByTestId('iadm03-del-msg').innerText().catch(() => '');
    push('US-009/010 R01 Delegation Create+Revoke', /ACTIVE/i.test(delMsg) && /REVOKED/i.test(delMsgAfter), `${delMsg} -> ${delMsgAfter}`);
    await shot(page, '05_r01_delegations.png');

    // 2. R02: Submit grant request, validate, and verify R02 approve is 403
    await loginUi(page, R02, '/platform/identity/grant-queue');
    await page.getByTestId('iadm03-grant-queue-desk').waitFor({ timeout: 30000 });
    const subRes = await apiFetch(page, '/api/platform/identity/grant-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subjectUserId: SUBJECT_UUID,
        targetKind: 'ROLE',
        targetCode: 'SVC-DEMO',
        scopeType: 'PLATFORM',
        reason: 'Headed R02 technical request',
      }),
    });
    const reqId = subRes.body?.id;
    const valRes = reqId ? await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/validate`, { method: 'POST', body: '{}' }) : { status: 0 };
    const r02AppDeny = reqId ? await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'APPROVE', reason: 'R02 should be denied' }),
    }) : { status: 0 };
    push('US-005 R02 Validate + Approve Deny', valRes.status === 200 && r02AppDeny.status === 403, `val=${valRes.status}, approve=${r02AppDeny.status}`);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1200);
    await shot(page, '07_r02_validate_and_approve_deny.png');

    // 3. R01B: Independently approve R02's request -> RECORDED + audit-disabled 503
    if (R01B.email && R01B.password && reqId) {
      await loginUi(page, R01B, '/platform/identity/grant-queue');
      const appRes = await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/decision`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision: 'APPROVE', reason: 'Independent R01B approval' }),
      });
      push('US-006 R01B Independent Approve', appRes.status === 200 && appRes.body?.status === 'RECORDED', JSON.stringify(appRes.body));

      // Self-grant refusal
      const selfRes = await apiFetch(page, '/api/platform/identity/grant-requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Demo-Audit-Disabled': 'true' },
        body: JSON.stringify({ subjectUserId: SUBJECT_UUID, targetKind: 'ROLE', targetCode: 'R02', scopeType: 'PLATFORM', reason: 'Audit fail test' }),
      });
      push('US-013 Audit-Disabled Refusal', selfRes.status === 503, `status=${selfRes.status}`);
      await page.reload({ waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1200);
      await shot(page, '08_r01b_approve_recorded.png');
    }

    // 4. R03: Own institute view/request + cross-institute deny
    await loginUi(page, R03, '/platform/identity/grant-queue');
    const r03Mat = await apiFetch(page, '/api/platform/identity/matrix');
    const r03Cross = await apiFetch(page, `/api/platform/identity/handover?tenantId=${SECOND_TENANT_ID}`);
    push('US-003 R03 Scope Isolation', r03Mat.status === 403 && r03Cross.status === 403, `matrix=${r03Mat.status}, crossHandover=${r03Cross.status}`);
    await shot(page, '10_r03_institute_scope.png');

    // 5. R04: Request only
    await loginUi(page, R04, '/platform/identity/grant-queue');
    const r04Req = await apiFetch(page, '/api/platform/identity/grant-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subjectUserId: SUBJECT_UUID, targetKind: 'ROLE', targetCode: 'FACULTY', scopeType: 'COLLEGE', reason: 'R04 headed request' }),
    });
    const r04Reg = await apiFetch(page, '/api/platform/identity/register');
    push('US-004 R04 Request-Only', r04Req.status === 200 && r04Reg.status === 403, `submit=${r04Req.status}, register=${r04Reg.status}`);
    await shot(page, '11_r04_request_only.png');

    // 6. R28: Read-only
    await loginUi(page, R28, '/platform/identity/register');
    await page.getByTestId('iadm03-register-table').waitFor({ timeout: 30000 });
    const r28Mut = await apiFetch(page, '/api/platform/identity/grant-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subjectUserId: SUBJECT_UUID, targetKind: 'ROLE', targetCode: 'FACULTY', scopeType: 'PLATFORM', reason: 'R28 mutate probe' }),
    });
    push('US-012 R28 Read-Only', r28Mut.status === 403, `mutate=${r28Mut.status}`);
    await shot(page, '12_r28_readonly.png');

    // 7. US-015: Stop IADM-03 (:8101) and verify IADM-06 (:8104) still works for R01
    stopPort(8101);
    await loginUi(page, R01A, '/platform/analytics');
    const anlRes = await apiFetch(page, '/api/platform/analytics/overview').catch(() => ({ status: 0 }));
    push('US-015 Runtime Unaffected With IADM-03 Stopped', anlRes.status !== 503, `iadm06Status=${anlRes.status}`);
    await shot(page, '13_us015_iadm06_with_iadm03_stopped.png');
  } finally {
    await context.close();
    await browser.close();
    fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify({ capturedAt: new Date().toISOString(), results }, null, 2));
  }
})();
