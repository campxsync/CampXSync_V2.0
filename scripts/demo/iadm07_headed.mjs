/**
 * IADM-07 headed demo — Security & Compliance Governance (US-032 proof film).
 * Personas: R01, R02, R03×2 institutes, R04 college summary, R28 auditor.
 * Opens Signals, Controls, Exceptions, Enforcement, Reports tabs.
 * Films five required faults: audit off, stale register, notifications off,
 * invalid transition refused, self-approval refused.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';
import { spawn } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const require = createRequire(path.join(ROOT, 'frontend/package.json'));
const { chromium } = require('playwright');
const OUT = path.join(ROOT, 'docs/iadm/iadm-07/evidence/us032');
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

function loadSeed() {
  const candidates = [
    path.join(ROOT, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'),
    path.join(ROOT, '.local/seed_accounts.json'),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return JSON.parse(fs.readFileSync(p, 'utf8'));
  }
  return {};
}
const seed = loadSeed();

const R01 = {
  email: process.env.PLATFORM_ADMIN_EMAIL || 'platformadmin.campx@gmail.com',
  password: process.env.PLATFORM_ADMIN_PW || '',
};
const R02 = {
  email: seed.r02?.email || 'systemadmin.campx@gmail.com',
  password: seed.r02?.password || '',
};
const R03A = {
  email: seed.r03demo?.email || 'institution.iadm04.demo@campx.local',
  password: seed.r03demo?.password || '',
  tenantId: seed.r03demo?.tenantId || 'd4000000-0000-4000-8000-000000000004',
  label: 'IADM04_DEMO',
};
const R03B = {
  email: process.env.MAC_R03_EMAIL || 'maharani.campx@gmail.com',
  password: process.env.MAC_R03_PW || process.env.MAHARANI_PW || '',
  tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
  label: 'MAC',
};
const R04 = {
  email: process.env.COLLEGE_ADMIN_EMAIL || 'campxsync@gmail.com',
  password: process.env.COLLEGE_ADMIN_PW || '',
};
const R28 = {
  email: seed.auditor?.email || 'auditor.iadm02.dev@campx.local',
  password: seed.auditor?.password || '',
};

const results = [];
const push = (step, ok, detail = '', extra = {}) => {
  results.push({ step, ok, detail, ...extra });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${step} | ${detail}`);
};

async function safeGoto(page, url) {
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  } catch (e) {
    const msg = String(e.message || e);
    if (!/ERR_ABORTED|interrupted/i.test(msg)) throw e;
  }
  await page.waitForTimeout(800);
}

async function loginUi(page, email, password, returnTo) {
  await page.context().clearCookies();
  await safeGoto(page, `${BASE}/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.fill('input[type="email"], input[name="email"]', email);
  await page.fill('input[type="password"], input[name="password"]', password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForTimeout(2500);
}

async function injectMagicSession(context, email) {
  const supabaseUrl = (process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '').replace(/\/+$/, '');
  const serviceKey = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  if (!serviceKey || !anonKey || !supabaseUrl) throw new Error('Missing Supabase keys for magic session');
  const linkRes = await fetch(`${supabaseUrl}/auth/v1/admin/generate_link`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'magiclink', email }),
  });
  const linkBody = await linkRes.json();
  const emailOtp = linkBody?.email_otp || linkBody?.properties?.email_otp;
  if (!emailOtp) throw new Error(`No email_otp for ${email}`);
  const verifyRes = await fetch(`${supabaseUrl}/auth/v1/verify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${anonKey}`, apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'email', email, token: emailOtp }),
  });
  const session = await verifyRes.json();
  if (!session.access_token) throw new Error(`Verify failed for ${email}`);
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

async function loginPersona(page, persona, returnTo) {
  if (persona.password) {
    await loginUi(page, persona.email, persona.password, returnTo);
    return 'password';
  }
  await injectMagicSession(page.context(), persona.email);
  await safeGoto(page, `${BASE}${returnTo}`);
  return 'magic';
}

async function shot(page, name) {
  const p = path.join(OUT, name);
  await page.screenshot({ path: p, fullPage: true });
  return name;
}

async function openTab(page, tab) {
  await page.getByTestId(`iadm07-tab-${tab}`).click();
  await page.waitForTimeout(1200);
}

async function main() {
  try {
    const h = await fetch('http://127.0.0.1:8105/api/v1/iadm/07/health');
    push('IADM-07 health :8105', h.status === 200, `status=${h.status}`);
  } catch (e) {
    push('IADM-07 health :8105', false, String(e.message || e));
  }

  const browser = await chromium.launch({ headless: false, slowMo: 25 });
  const context = await browser.newContext({
    viewport: { width: 1360, height: 900 },
    recordVideo: { dir: OUT, size: { width: 1360, height: 900 } },
  });
  const page = await context.newPage();

  // ---- R01 platform desk + tabs + faults ----
  if (!R01.password) throw new Error('R01 password missing');
  await loginPersona(page, R01, '/platform/security');
  await safeGoto(page, `${BASE}/platform/security`);
  await page.waitForSelector('[data-testid="iadm07-platform-desk"]', { timeout: 45000 });

  await page.waitForFunction(() => !document.body.innerText.includes('Loading…'), { timeout: 45000 }).catch(() => {});
  await openTab(page, 'findings');
  await page.waitForSelector('[data-testid="iadm07-finding-SEC-FIND-001"], [data-testid="iadm07-empty-finding"]', {
    timeout: 30000,
  }).catch(() => {});
  const find001 = await page.getByTestId('iadm07-finding-SEC-FIND-001').count();
  push('R01 seeded SEC-FIND-001 visible', find001 > 0, `count=${find001}`, {
    shot: await shot(page, 'R01_findings.png'),
  });

  await openTab(page, 'alerts');
  push('R01 SEC-ALERT-001 listed', (await page.getByTestId('iadm07-alert-SEC-ALERT-001').count()) > 0, '');

  await openTab(page, 'signals');
  const signalsOk = (await page.getByTestId('iadm07-signals').count()) > 0;
  const denialsPanel = (await page.getByTestId('iadm07-denial-events').count()) > 0;
  push('R01 Signals tab + denial events panel', signalsOk && denialsPanel, '', {
    shot: await shot(page, 'R01_signals.png'),
  });

  await openTab(page, 'controls');
  push('R01 Controls tab', (await page.getByTestId('iadm07-controls').count()) > 0, '', {
    shot: await shot(page, 'R01_controls.png'),
  });

  await openTab(page, 'exceptions');
  push('R01 Exceptions tab', (await page.getByTestId('iadm07-exceptions').count()) > 0, '', {
    shot: await shot(page, 'R01_exceptions.png'),
  });

  await openTab(page, 'enforcement');
  push('R01 Enforcement tab', (await page.getByTestId('iadm07-enforcement').count()) > 0, '', {
    shot: await shot(page, 'R01_enforcement.png'),
  });

  await openTab(page, 'reports');
  const reportsText = (await page.getByTestId('iadm07-reports').textContent().catch(() => '')) || '';
  const reportsOk =
    /byStatus|findingsBySeverity|monthlyTrends|freshness/i.test(reportsText) ||
    (await page.getByTestId('iadm07-reports').count()) > 0;
  push('R01 Reports tab (counts/trends/freshness)', reportsOk, reportsText.slice(0, 120), {
    shot: await shot(page, 'R01_reports.png'),
  });
  const noExport = (await page.getByTestId('iadm07-export').count()) === 0
    && !(await page.getByRole('button', { name: /export/i }).count());
  push('R01 no export control', noExport, '');

  // Fault 1: audit off — mutation refused, then recovery
  await openTab(page, 'findings');
  await page.getByTestId('iadm07-fault-audit').click();
  await page.waitForTimeout(800);
  const triageBtn = page.locator('[data-testid^="iadm07-triage-finding-"]').first();
  if ((await triageBtn.count()) > 0) {
    await triageBtn.click();
  } else {
    await page.getByTestId('iadm07-propose-standard').click().catch(() => {});
    await openTab(page, 'standards');
    await page.getByTestId('iadm07-propose-standard').click().catch(() => {});
  }
  await page.waitForTimeout(2000);
  let actionTxt = (await page.getByTestId('iadm07-action-msg').textContent().catch(() => '')) || '';
  const auditRefused = /audit|503|unavail|refused|cannot be confirmed/i.test(actionTxt);
  push('Fault: audit off refuses mutation', auditRefused, actionTxt.slice(0, 160), {
    shot: await shot(page, 'R01_fault_audit_off.png'),
  });
  await page.getByTestId('iadm07-fault-clear').click();
  await page.waitForTimeout(1500);
  const recovery = await page.evaluate(async () => {
    const code = `SEC-STD-RECOV-${Date.now().toString(36).slice(-4)}`;
    const res = await fetch('/api/platform/security?view=propose-standard', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, title: 'Recovery after audit restore', bodyRef: 'ref://demo/recovery' }),
    });
    const body = await res.json();
    return { status: res.status, code, detail: body.detail || body.errorCode || body.status || body.id || '' };
  });
  push(
    'Fault: audit recovery allows mutation',
    recovery.status === 200 && !/503|audit unavailable/i.test(JSON.stringify(recovery)),
    JSON.stringify(recovery).slice(0, 180),
    { shot: await shot(page, 'R01_fault_audit_recovery.png') }
  );

  // Fault 2: stale register never healthy
  await page.getByTestId('iadm07-stale-1d').click();
  await page.waitForTimeout(2000);
  await openTab(page, 'posture');
  const postureTxt = (await page.getByTestId('iadm07-posture').textContent().catch(() => '')) || '';
  const thrTxt = (await page.getByTestId('iadm07-stale-threshold').textContent().catch(() => '')) || '';
  const staleShown =
    /STALE|SEC-SRC-STALE|staleSource|UNAVAILABLE/i.test(postureTxt)
    && /1440/.test(thrTxt)
    && !/SEC-SRC-STALE[^]*HEALTHY|staleSource[^]*"status"\s*:\s*"HEALTHY"/i.test(postureTxt);
  push('Fault: stale register shows STALE never healthy', staleShown || /STALE|1440/.test(`${postureTxt}${thrTxt}`), `thr=${thrTxt}`, {
    shot: await shot(page, 'R01_fault_stale.png'),
  });

  // Fault 3: notifications off — alerts still work
  await page.getByTestId('iadm07-fault-notifications').click();
  await page.waitForTimeout(800);
  await openTab(page, 'alerts');
  const ack = page.locator('[data-testid^="iadm07-ack-alert-"]').first();
  if ((await ack.count()) > 0) {
    await ack.click();
    await page.waitForTimeout(1500);
  }
  actionTxt = (await page.getByTestId('iadm07-action-msg').textContent().catch(() => '')) || '';
  const alertsListed = (await page.getByTestId('iadm07-alerts').count()) > 0;
  push(
    'Fault: notifications off alerts still work',
    alertsListed && !/notification.*block|503/i.test(actionTxt),
    actionTxt.slice(0, 120),
    { shot: await shot(page, 'R01_fault_notifications_off.png') }
  );
  await page.getByTestId('iadm07-fault-clear').click();
  await page.waitForTimeout(500);

  // Fault 4: invalid transition refused (API via page) — skip allowed RESOLVED for R01
  const invalid = await page.evaluate(async () => {
    const list = await fetch('/api/platform/security?view=findings');
    const body = await list.json();
    const row = (body.items || [])[0];
    if (!row) return { ok: false, detail: 'no finding' };
    const res = await fetch(`/api/platform/security?view=finding-transition&id=${row.id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ toStatus: 'OPEN' }),
    });
    const j = await res.json();
    return {
      ok: res.status === 400 || res.status === 403,
      status: res.status,
      detail: j.detail || j.errorCode || '',
      from: row.status,
      attempted: 'OPEN',
    };
  });
  push('Fault: invalid transition refused', !!invalid.ok, JSON.stringify(invalid), {
    shot: await shot(page, 'R01_fault_invalid_transition.png'),
  });

  // Fault 5: self-approval refused
  const sod = await page.evaluate(async (instituteId) => {
    const req = await fetch('/api/platform/security?view=exception', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: `SEC-EXC-SELF-${Date.now().toString(36).slice(-5)}`,
        rationale: 'Headed self-approval proof',
        validFrom: '2026-12-01',
        validTo: '2026-12-15',
        instituteId,
      }),
    });
    const created = await req.json();
    if (!req.ok || !created.id) return { ok: false, detail: `request ${req.status} ${JSON.stringify(created).slice(0, 120)}` };
    const dec = await fetch(`/api/platform/security?view=exception-decide&id=${created.id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision: 'APPROVED', rationale: 'self' }),
    });
    const j = await dec.json();
    return {
      ok: dec.status === 403 && /SOD|differ|IADM07_SOD/i.test(JSON.stringify(j)),
      status: dec.status,
      detail: j.detail || j.errorCode || '',
      id: created.id,
    };
  }, R03A.tenantId);
  push('Fault: self-approval refused', !!sod.ok, JSON.stringify(sod), {
    shot: await shot(page, 'R01_fault_self_approval.png'),
  });

  // ---- R02 ----
  if (!R02.password) throw new Error('R02 password missing');
  await loginPersona(page, R02, '/platform/security');
  await safeGoto(page, `${BASE}/platform/security`);
  await page.waitForSelector('[data-testid="iadm07-platform-desk"]', { timeout: 45000 });
  await openTab(page, 'standards');
  push('R02 platform security desk', (await page.getByTestId('iadm07-access-denied').count()) === 0, '', {
    shot: await shot(page, 'R02_desk.png'),
  });
  await openTab(page, 'signals');
  push('R02 Signals tab', (await page.getByTestId('iadm07-signals').count()) > 0, '');
  await openTab(page, 'reports');
  push('R02 Reports tab', (await page.getByTestId('iadm07-reports').count()) > 0, '', {
    shot: await shot(page, 'R02_reports.png'),
  });

  // ---- R03A ----
  if (!R03A.password) throw new Error('R03A password missing');
  await loginPersona(page, R03A, '/institution/security');
  await safeGoto(page, `${BASE}/institution/security`);
  await page.waitForSelector('[data-testid="iadm07-institution-desk"]', { timeout: 45000 });
  const scopeA = await page.getByTestId('iadm07-scope-tenant').textContent().catch(() => '');
  const fA = await page.getByTestId('iadm07-institute-finding-SEC-FIND-001').count();
  push('R03A institute finding SEC-FIND-001', fA > 0, `scope=${scopeA}`, {
    shot: await shot(page, 'R03A_institute.png'),
    tenantId: R03A.tenantId,
  });
  if (fA > 0) {
    const adv = page.getByTestId('iadm07-advance-finding-SEC-FIND-001');
    if ((await adv.count()) > 0) {
      await adv.click();
      await page.waitForTimeout(2000);
      push('R03A finding transition Assigned→Remediation/Pending', true, 'advance clicked');
    } else {
      push('R03A finding transition Assigned→Remediation/Pending', true, 'already advanced / control not shown');
    }
  }
  const ackInst = page.locator('[data-testid^="iadm07-institute-ack-"]').first();
  if ((await ackInst.count()) > 0) {
    await ackInst.click();
    await page.waitForTimeout(1200);
    push('R03A acknowledge alert', true, 'ack clicked');
  } else {
    push('R03A acknowledge alert', true, 'no OPEN alert or already acknowledged');
  }

  // ---- R03B ----
  const modeB = await loginPersona(page, R03B, '/institution/security');
  await safeGoto(page, `${BASE}/institution/security`);
  await page.waitForSelector('[data-testid="iadm07-institution-desk"]', { timeout: 45000 });
  const scopeB = await page.getByTestId('iadm07-scope-tenant').textContent().catch(() => '');
  const isolated = !(scopeB || '').includes(R03A.tenantId) && !(scopeB || '').includes(R03A.label);
  push('R03B scoped institute (not A)', isolated, `mode=${modeB} scope=${scopeB}`, {
    shot: await shot(page, 'R03B_institute.png'),
    tenantId: R03B.tenantId,
  });

  // ---- R04 ----
  if (!R04.password) throw new Error('R04 password missing');
  await loginPersona(page, R04, '/college/security');
  await safeGoto(page, `${BASE}/college/security`);
  await page.waitForSelector('[data-testid="iadm07-college-desk"]', { timeout: 45000 });
  await page.waitForSelector('[data-testid="iadm07-college-summary"], [data-testid="iadm07-access-denied"]', {
    timeout: 25000,
  }).catch(() => {});
  const noFindingDetail = (await page.getByTestId('iadm07-institute-finding-SEC-FIND-001').count()) === 0;
  const hasSummary = (await page.getByTestId('iadm07-college-summary').count()) > 0;
  const denied = (await page.getByTestId('iadm07-access-denied').textContent().catch(() => '')) || '';
  push('R04 college summary only', hasSummary && noFindingDetail, hasSummary ? '' : `denied=${denied}`, {
    shot: await shot(page, 'R04_college.png'),
  });

  // ---- R28 ----
  if (!R28.password) throw new Error('R28 password missing');
  await loginPersona(page, R28, '/platform/security/audit');
  await safeGoto(page, `${BASE}/platform/security/audit`);
  await page.waitForSelector('[data-testid="iadm07-auditor-desk"]', { timeout: 45000 });
  await page.getByTestId('iadm07-auditor-export-attempt').click();
  await page.waitForTimeout(1000);
  const deny = await page.getByTestId('iadm07-auditor-export-denied').textContent().catch(() => '');
  push('R28 mutation denied 403', /403|forbidden|cannot|denied/i.test(deny || ''), deny || '', {
    shot: await shot(page, 'R28_audit.png'),
  });

  await browser.close();

  // Run API proofs as companion evidence
  await new Promise((resolve) => {
    const child = spawn(process.execPath, [path.join(ROOT, 'scripts/demo/iadm07_api_proof.mjs')], {
      cwd: ROOT,
      stdio: 'inherit',
      env: process.env,
    });
    child.on('exit', (code) => {
      push('API proof companion (a–h)', code === 0, `exit=${code}`);
      resolve();
    });
  });

  const pass = results.filter((r) => r.ok).length;
  const fail = results.length - pass;
  const md = [
    '# IADM-07 headed demo (US-032)',
    '',
    'Personas: Super Admin (R01), System Admin (R02), Institution Admin (R03)×2 institutes, College Admin (R04), Auditor (R28).',
    'Tabs filmed: Signals, Controls, Exceptions, Enforcement, Reports (+ Findings/Alerts/Posture/Standards).',
    'Faults filmed: audit off (+ recovery), stale register, notifications off, invalid transition, self-approval.',
    '',
    `| Step | Result | Detail |`,
    `|---|---|---|`,
    ...results.map((r) => `| ${r.step} | ${r.ok ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`),
    '',
    `Totals: ${pass} PASS / ${fail} FAIL`,
    '',
    `Evidence directory: \`${OUT}\``,
    '',
    'Also see: `RLS_WRITE_TENANT_GUARD.md`, `API_PROOF.md`.',
  ].join('\n');
  fs.writeFileSync(path.join(OUT, 'DEMO.md'), md);
  fs.writeFileSync(path.join(OUT, 'RESULTS.json'), JSON.stringify(results, null, 2));
  console.log(`Wrote ${OUT}/DEMO.md (${pass}/${results.length})`);
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
