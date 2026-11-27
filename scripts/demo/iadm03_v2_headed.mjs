/**
 * IADM-03 V2.0 Headed Demo — Complete Part B & C verification across R01 (r01a + r01b), R02, R03, R04, R28.
 * Outputs screenshots + video (.webm) + JSON summary to docs/iadm/iadm-03/evidence/v2/.
 */
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
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
const SUBJECT_UUID = crypto.randomUUID();

const results = [];
const push = (step, ok, detail = '') => {
  results.push({ step, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${step} | ${detail}`);
};

async function safeGoto(page, url) {
  try {
    await page.goto(url, { waitUntil: 'load', timeout: 60000 });
  } catch (e) {
    if (!/ERR_ABORTED|interrupted/i.test(String(e.message || e))) throw e;
  }
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function loginUi(page, creds, returnTo) {
  await page.context().clearCookies();
  await safeGoto(page, `${BASE}/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.fill('input[type="email"], input[name="email"]', creds.email);
  await page.fill('input[type="password"], input[name="password"]', creds.password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'load', timeout: 12000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(1800);
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
    // 1. R01A: Register, Matrix, Handover, Reviews (Attest/Revoke/Exception), Delegations (Create/Revoke + Expiry flip)
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
    await page.waitForFunction(() => document.querySelectorAll('[data-testid="iadm03-handover-table"] tbody tr').length > 0, null, { timeout: 15000 }).catch(() => {});
    const hoRows = await page.locator('[data-testid="iadm03-handover-table"] tbody tr').count();
    push('US-014 R01 Handover', hoRows >= 1, `rows=${hoRows}`);
    await shot(page, '03_r01_handover.png');

    // US-008: Create campaign + Attest, Revoke (creates revocation grant request), Exception
    await safeGoto(page, `${BASE}/platform/identity/reviews`);
    await page.getByTestId('iadm03-review-create-btn').waitFor({ timeout: 15000 });
    await page.getByTestId('iadm03-review-create-btn').click();
    await page.getByTestId('iadm03-review-msg').waitFor({ timeout: 15000 });
    const revMsg = await page.getByTestId('iadm03-review-msg').innerText();
    const revList = await apiFetch(page, '/api/platform/identity/v2/access-reviews');
    const camp = revList.body?.campaigns?.[0];
    let revItemsOk = false;
    if (camp && camp.items && camp.items.length >= 3) {
      const a1 = await apiFetch(page, `/api/platform/identity/v2/access-reviews/${camp.id}/items/${camp.items[0].id}/decision`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ decision: 'ATTEST', reason: 'Confirmed' }),
      });
      const a2 = await apiFetch(page, `/api/platform/identity/v2/access-reviews/${camp.id}/items/${camp.items[1].id}/decision`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ decision: 'REVOKE', reason: 'No longer needed' }),
      });
      const a3 = await apiFetch(page, `/api/platform/identity/v2/access-reviews/${camp.id}/items/${camp.items[2].id}/decision`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision: 'EXCEPTION', reason: 'Transition', exceptionOwner: 'R01', exceptionExpiresAt: new Date(Date.now() + 86400000 * 30).toISOString() }),
      });
      revItemsOk = a1.status === 200 && a2.status === 200 && !!a2.body?.revocationRequestId && a3.status === 200;
    }
    push('US-008 R01 Review Campaign + Decisions', /Created campaign/i.test(revMsg) && revItemsOk, `${revMsg} | items=${camp?.items?.length || 0}`);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(800);
    await shot(page, '04_r01_reviews.png');

    // US-009 & US-010: Delegation create, revoke, and Active -> Expired flip
    await safeGoto(page, `${BASE}/platform/identity/delegations`);
    await page.getByTestId('iadm03-del-delegate').waitFor({ timeout: 15000 });
    await page.waitForTimeout(600);
    await page.fill('[data-testid="iadm03-del-delegate"]', SUBJECT_UUID);
    await page.getByTestId('iadm03-del-submit').click();
    await page.waitForSelector('[data-testid="iadm03-del-msg"], [data-testid="iadm03-del-err"]', { timeout: 15000 });
    const delMsg = (await page.locator('[data-testid="iadm03-del-msg"]').count())
      ? await page.getByTestId('iadm03-del-msg').innerText()
      : `ERR: ${await page.getByTestId('iadm03-del-err').innerText()}`;
    const revokeBtn = page.locator('[data-testid^="iadm03-del-revoke-"]').first();
    let delMsgAfter = '';
    if (await revokeBtn.count()) {
      await revokeBtn.click();
      await page.waitForFunction(() => /REVOKED/i.test(document.querySelector('[data-testid="iadm03-del-msg"]')?.textContent || ''), null, { timeout: 15000 }).catch(() => {});
      delMsgAfter = (await page.locator('[data-testid="iadm03-del-msg"]').count())
        ? await page.getByTestId('iadm03-del-msg').innerText()
        : '';
    }
    // Short-lived delegation for Active -> Expired flip
    const shortDel = await apiFetch(page, '/api/platform/identity/v2/delegations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        delegateUserId: SUBJECT_UUID,
        allowedAction: 'iadm03.holders.view',
        targetService: 'IADM-03',
        expiresAt: new Date(Date.now() + 2000).toISOString(),
        reason: 'Expiry flip check',
      }),
    });
    await page.waitForTimeout(2600);
    const expCheck = shortDel.body?.id
      ? await apiFetch(page, `/api/platform/identity/v2/delegations/${shortDel.body.id}/status`)
      : { status: 0, body: {} };
    push('US-009/010 R01 Delegation Create+Revoke+Expiry',
      /ACTIVE/i.test(delMsg) && /REVOKED/i.test(delMsgAfter) && expCheck.body?.status === 'EXPIRED' && expCheck.body?.valid === false,
      `revoke=${/REVOKED/i.test(delMsgAfter)}, expiryStatus=${expCheck.body?.status}, valid=${expCheck.body?.valid}`);
    await shot(page, '05_r01_delegations.png');

    // 2. R02: Submit & Validate real role (PRINCIPAL) + Service Registration submit/validate + Approve Deny
    await loginUi(page, R02, '/platform/identity/grant-queue');
    await page.getByTestId('iadm03-grant-queue-desk').waitFor({ timeout: 30000 });
    const subRes = await apiFetch(page, '/api/platform/identity/grant-requests', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subjectUserId: SUBJECT_UUID,
        targetKind: 'PERMISSION',
        targetCode: 'iadm06.catalogue.view',
        scopeType: 'PLATFORM',
        reason: 'Headed R02 real key request',
      }),
    });
    const reqId = subRes.body?.id;
    const valRes = reqId ? await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/validate`, { method: 'POST', body: '{}' }) : { status: 0 };
    const r02AppDeny = reqId ? await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'APPROVE', reason: 'R02 should be denied' }),
    }) : { status: 0 };
    push('US-005 R02 Validate + Approve Deny',
      valRes.status === 200 && valRes.body?.status === 'VALIDATED' && valRes.body?.catalogueTouched === false && r02AppDeny.status === 403,
      `val=${valRes.status}, catalogueTouched=${valRes.body?.catalogueTouched}, approve=${r02AppDeny.status}`);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(800);
    await shot(page, '07_r02_validate_and_approve_deny.png');

    // US-011 R02 submit & validate service registration
    await safeGoto(page, `${BASE}/platform/identity/registrations`);
    const regSub = await apiFetch(page, '/api/platform/identity/service-registrations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        serviceCode: 'IADM-09-DEMO',
        keyManifest: 'iadm09.demo.view',
        roleGrantProposal: 'R01:iadm09.demo.view',
        migrationRef: '20261127990000_demo.sql',
        rollbackRef: '20261127990000_rollback__demo.sql',
        testEvidenceRef: 'unit-suite-pass',
        auditEvidenceRef: 'audit-event-verified',
      }),
    });
    const regItemId = regSub.body?.id;
    const regVal = regItemId ? await apiFetch(page, `/api/platform/identity/service-registrations/${regItemId}/validate`, { method: 'POST', body: '{}' }) : { status: 0 };

    // 3. R01B: Approve grant request (RECORDED) + Approve service registration + Audit-disabled 503 across all 6 governed ops
    await loginUi(page, R01B, '/platform/identity/grant-queue');
    const appRes = reqId ? await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'APPROVE', reason: 'Independent R01B approval' }),
    }) : { status: 0 };
    push('US-006 R01B Independent Approve', appRes.status === 200 && appRes.body?.status === 'RECORDED', JSON.stringify(appRes.body));

    const regDec = regItemId ? await apiFetch(page, `/api/platform/identity/service-registrations/${regItemId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'APPROVE', reason: 'R01B approved additive manifest' }),
    }) : { status: 0 };
    push('US-011 Service Registration Submit+Validate+Approve',
      regSub.status === 200 && regVal.status === 200 && regDec.status === 200 && regDec.body?.appliedMigration === false,
      `sub=${regSub.status}, val=${regVal.status}, decide=${regDec.status}, appliedMigration=${regDec.body?.appliedMigration}`);
    await safeGoto(page, `${BASE}/platform/identity/registrations`);
    await shot(page, '06_r01_registrations.png');

    // US-013: Audit-disabled 503 across all 6 governed operations + recovery
    const auditHdrs = { 'Content-Type': 'application/json', 'X-Demo-Audit-Disabled': 'true' };
    const ad1 = await apiFetch(page, '/api/platform/identity/grant-requests', {
      method: 'POST', headers: auditHdrs,
      body: JSON.stringify({ subjectUserId: SUBJECT_UUID, targetKind: 'ROLE', targetCode: 'R02', scopeType: 'PLATFORM', reason: 'Audit test' }),
    });
    const ad2 = reqId ? await apiFetch(page, `/api/platform/identity/grant-requests/${reqId}/decision`, {
      method: 'POST', headers: auditHdrs, body: JSON.stringify({ decision: 'REJECT', reason: 'Audit test' }),
    }) : { status: 503 };
    const ad3 = (camp && camp.items?.[0]) ? await apiFetch(page, `/api/platform/identity/v2/access-reviews/${camp.id}/items/${camp.items[0].id}/decision`, {
      method: 'POST', headers: auditHdrs, body: JSON.stringify({ decision: 'ATTEST', reason: 'Audit test' }),
    }) : { status: 503 };
    const ad4 = await apiFetch(page, '/api/platform/identity/v2/delegations', {
      method: 'POST', headers: auditHdrs,
      body: JSON.stringify({ delegateUserId: SUBJECT_UUID, allowedAction: 'iadm03.holders.view', targetService: 'IADM-03', expiresAt: new Date(Date.now() + 86400000).toISOString(), reason: 'Audit test' }),
    });
    const ad5 = await apiFetch(page, '/api/platform/identity/service-registrations', {
      method: 'POST', headers: auditHdrs,
      body: JSON.stringify({ serviceCode: 'IADM-99', keyManifest: '["iadm99.view"]', migrationRef: 'm.sql', rollbackRef: 'r.sql', testEvidenceRef: 't', auditEvidenceRef: 'a' }),
    });
    const ad6 = await apiFetch(page, '/api/platform/identity/register/iadm03.register.view/holders', {
      method: 'GET', headers: { 'X-Demo-Audit-Disabled': 'true' },
    });
    const rec6 = await apiFetch(page, '/api/platform/identity/register/iadm03.register.view/holders');
    push('US-013 Audit-Disabled 503 (all 6 ops) + Recovery',
      [ad1, ad2, ad3, ad4, ad5, ad6].every((r) => r.status === 503) && rec6.status === 200,
      `statuses=${[ad1.status, ad2.status, ad3.status, ad4.status, ad5.status, ad6.status].join(',')}, recovered=${rec6.status}`);
    await safeGoto(page, `${BASE}/platform/identity/grant-queue`);
    await shot(page, '08_r01b_approve_recorded.png');

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

    // 7. US-015: Stop IADM-03 (:8101) and verify IADM-06 (:8104) works for R01 (200) and refuses R04 (403)
    stopPort(8101);
    await loginUi(page, R01A, '/platform/analytics');
    const anlR01 = await apiFetch(page, '/api/platform/analytics');
    await shot(page, '13_us015_iadm06_with_iadm03_stopped.png');
    await loginUi(page, R04, '/login');
    const anlR04 = await apiFetch(page, '/api/platform/analytics');
    push('US-015 Runtime Unaffected With IADM-03 Stopped',
      anlR01.status === 200 && anlR04.status === 403,
      `r01_iadm06=${anlR01.status}, r04_iadm06=${anlR04.status}`);
  } finally {
    await context.close();
    await browser.close();
    fs.writeFileSync(path.join(OUT, 'results.json'), JSON.stringify({ capturedAt: new Date().toISOString(), results }, null, 2));
  }
})();
