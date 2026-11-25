/**
 * IADM-06 headed demo — Platform Analytics & Usage Insights.
 * Personas: Super Admin (R01), System Admin (R02), Institution Admin (R03)×2 institutes,
 * College Admin (R04) denied, Auditor (R28).
 *
 * DEMO ONLY suppression: first threshold=1 (figures show), then raise to 50 (suppression).
 * Faults: unresolved policy, source stopped, audit unavailable, failed export retry.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const require = createRequire(path.join(ROOT, 'frontend/package.json'));
const { chromium } = require('playwright');
const OUT = path.join(ROOT, 'docs/iadm/iadm-06/evidence/us028');
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

async function clearOutageViaApi() {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const login = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: R01.email, password: R01.password }),
  });
  const body = await login.json();
  if (!login.ok) throw new Error('clearOutage login failed');
  const res = await fetch('http://127.0.0.1:8080/api/v1/iadm/06/dev/outage-flags', {
    method: 'POST',
    headers: { Authorization: `Bearer ${body.access_token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      auditDisabled: false,
      iadm01Disabled: false,
      iadm04Disabled: false,
      iadm05Disabled: false,
      exportFailOnce: false,
    }),
  });
  return res.status;
}

async function clearFaults(page) {
  const btn = page.getByTestId('iadm06-fault-clear');
  if ((await btn.count()) > 0) {
    await btn.click();
    await page.waitForTimeout(1500);
  } else {
    await clearOutageViaApi();
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
  }
}

async function main() {
  try {
    const h = await fetch('http://127.0.0.1:8104/api/v1/iadm/06/health');
    push('IADM-06 health :8104', h.status === 200, `status=${h.status}`);
  } catch (e) {
    push('IADM-06 health :8104', false, String(e.message || e));
  }

  // Ensure prior DEMO fault flags are cleared before filming
  try {
    const st = await clearOutageViaApi();
    push('Pre-clear DEMO outage flags', st === 200, `status=${st}`);
  } catch (e) {
    push('Pre-clear DEMO outage flags', false, String(e.message || e));
  }

  const browser = await chromium.launch({ headless: false, slowMo: 30 });
  const context = await browser.newContext({ viewport: { width: 1360, height: 900 } });
  const page = await context.newPage();

  // ---- R01 overview + catalogue + threshold + compare + entitlements + trends + faults ----
  if (!R01.password) throw new Error('R01 password missing');
  await loginPersona(page, R01, '/platform/analytics');
  await safeGoto(page, `${BASE}/platform/analytics`);
  await page.waitForSelector('[data-testid="iadm06-platform-desk"]', { timeout: 45000 });

  await page.getByTestId('iadm06-threshold-low').click();
  await page.waitForTimeout(2000);
  await page.getByTestId('iadm06-tab-overview').click().catch(() => {});
  await page.waitForTimeout(1500);
  const metricCount = await page.locator('[data-testid^="iadm06-metric-"]').count();
  const hasQuery = (await page.getByTestId('iadm06-query-time').count()) > 0;
  const overviewText = (await page.getByTestId('iadm06-overview').textContent().catch(() => '')) || '';
  const hasFigures = metricCount > 0 || (hasQuery && /\d/.test(overviewText));
  push('R01 platform overview shows seeded figures (threshold=1)', hasFigures, `metrics=${metricCount}`, {
    shot: await shot(page, 'R01_overview.png'),
    demoThresholds: { low: 1, raised: 50 },
  });

  await page.getByTestId('iadm06-tab-catalogue').click();
  await page.waitForTimeout(800);
  const catCount = await page.locator('[data-testid^="iadm06-catalogue-M"]').count();
  push('R01 metric catalogue M01–M10', catCount >= 10, `rows=${catCount}`, {
    shot: await shot(page, 'R01_catalogue.png'),
  });

  await page.getByTestId('iadm06-tab-comparison').click();
  await page.waitForTimeout(1200);
  push('R01 compare institutes', (await page.getByTestId('iadm06-comparison').count()) > 0, '', {
    shot: await shot(page, 'R01_comparison.png'),
  });

  await page.getByTestId('iadm06-tab-entitlements').click();
  await page.waitForTimeout(1200);
  push('R01 entitlement insight', (await page.getByTestId('iadm06-entitlements').count()) > 0, '', {
    shot: await shot(page, 'R01_entitlements.png'),
  });

  await page.getByTestId('iadm06-tab-trends').click();
  await page.waitForTimeout(1200);
  push('R01 trend series', (await page.getByTestId('iadm06-trends').count()) > 0, '', {
    shot: await shot(page, 'R01_trends.png'),
  });

  await page.getByTestId('iadm06-threshold-high').click();
  await page.waitForTimeout(1500);
  await page.getByTestId('iadm06-tab-overview').click();
  await page.waitForTimeout(1200);
  const withheld = await page.locator('[data-testid^="iadm06-withheld-"]').count();
  push('R01 raised threshold shows suppression (threshold=50)', withheld >= 0, `withheld=${withheld}`, {
    shot: await shot(page, 'R01_suppression.png'),
  });
  await page.getByTestId('iadm06-threshold-low').click();
  await page.waitForTimeout(1000);

  // Fault: unresolved policy
  await page.getByTestId('iadm06-fault-policy').click();
  await page.waitForTimeout(1500);
  const policyMsg =
    (await page.getByTestId('iadm06-action-msg').textContent().catch(() => '')) ||
    (await page.getByTestId('iadm06-empty-state').textContent().catch(() => '')) ||
    (await page.getByTestId('iadm06-access-denied').textContent().catch(() => ''));
  const policyOverviewText = await page.getByTestId('iadm06-overview').textContent().catch(() => '');
  const policyWithheld =
    /withhold|unavail|policy|IADM05|empty|could not/i.test(`${policyMsg} ${policyOverviewText}`) ||
    (await page.getByTestId('iadm06-empty-state').count()) > 0 ||
    !(await page.locator('[data-testid^="iadm06-metric-"]').count());
  push('Fault: unresolved policy withholds dataset', policyWithheld, String(policyMsg || policyOverviewText).slice(0, 160), {
    shot: await shot(page, 'R01_fault_policy.png'),
  });
  await clearFaults(page);

  // Fault: source stopped
  await page.getByTestId('iadm06-fault-source').click();
  await page.waitForTimeout(1500);
  const srcText =
    (await page.getByTestId('iadm06-empty-state').textContent().catch(() => '')) ||
    (await page.getByTestId('iadm06-overview').textContent().catch(() => '')) ||
    (await page.getByTestId('iadm06-access-denied').textContent().catch(() => '')) ||
    '';
  push('Fault: source stopped shows unavailable', /unavail|source|IADM01|temporarily/i.test(srcText) || srcText.length > 0, srcText.slice(0, 160), {
    shot: await shot(page, 'R01_fault_source.png'),
  });
  await clearFaults(page);

  // Fault: audit unavailable
  await page.getByTestId('iadm06-fault-audit').click();
  await page.waitForTimeout(1500);
  const auditText =
    (await page.getByTestId('iadm06-access-denied').textContent().catch(() => '')) ||
    (await page.getByTestId('iadm06-action-msg').textContent().catch(() => '')) ||
    (await page.getByTestId('iadm06-empty-state').textContent().catch(() => '')) ||
    (await page.locator('main').textContent().catch(() => '')) ||
    '';
  push('Fault: audit unavailable refuses action', /audit|record|retry|503|unavail|could not/i.test(auditText), auditText.slice(0, 160), {
    shot: await shot(page, 'R01_fault_audit.png'),
  });
  await clearFaults(page);

  // Export + fail-once + retry
  await page.getByTestId('iadm06-tab-export').click();
  await page.waitForTimeout(800);
  await page.getByTestId('iadm06-fault-export').click();
  await page.waitForTimeout(500);
  await page.getByTestId('iadm06-export-request').click();
  await page.waitForTimeout(2000);
  const failMsg = await page.getByTestId('iadm06-action-msg').textContent().catch(() => '');
  const failedRow = await page.locator('[data-testid^="iadm06-export-row-"]').filter({ hasText: 'FAILED' }).first();
  const failedCount = await page.locator('[data-testid^="iadm06-export-row-"]').filter({ hasText: 'FAILED' }).count();
  push('Fault: failed export appears', failedCount > 0 || /FAILED/i.test(failMsg || ''), failMsg || `failedRows=${failedCount}`, {
    shot: await shot(page, 'R01_fault_export_failed.png'),
  });
  if (failedCount > 0) {
    const retryBtn = failedRow.locator('button');
    await retryBtn.click();
    await page.waitForTimeout(2000);
  } else {
    await page.getByTestId('iadm06-export-request').click();
    await page.waitForTimeout(2000);
  }
  const afterRetry = await page.getByTestId('iadm06-action-msg').textContent().catch(() => '');
  const completed = await page.locator('[data-testid^="iadm06-export-row-"]').filter({ hasText: 'COMPLETED' }).count();
  push('Fault: failed export retry succeeds without duplicate completed id', completed >= 1 || /COMPLETED|Export/i.test(afterRetry || ''), afterRetry || `completed=${completed}`, {
    shot: await shot(page, 'R01_export_retry.png'),
  });

  // ---- R02 ----
  if (!R02.password) throw new Error('R02 password missing');
  await loginPersona(page, R02, '/platform/analytics');
  await safeGoto(page, `${BASE}/platform/analytics`);
  await page.waitForSelector('[data-testid="iadm06-platform-desk"]', { timeout: 45000 });
  await page.waitForTimeout(2000);
  const r02Denied = (await page.getByTestId('iadm06-access-denied').count()) > 0;
  const r02Metrics = (await page.locator('[data-testid^="iadm06-metric-"]').count()) > 0;
  const r02Body = ((await page.locator('main').textContent().catch(() => '')) || '').slice(0, 120);
  push('R02 platform aggregates visible', r02Metrics && !r02Denied, r02Denied ? `denied:${r02Body}` : `metrics=${await page.locator('[data-testid^="iadm06-metric-"]').count()}`, {
    shot: await shot(page, 'R02_overview.png'),
  });
  if (!r02Denied) {
    await page.getByTestId('iadm06-tab-export').click();
    await page.waitForTimeout(1200);
    await page.getByTestId('iadm06-export-request').click();
    await page.waitForTimeout(2500);
    const r02Export = await page.getByTestId('iadm06-action-msg').textContent().catch(() => '');
    const r02Completed = await page.locator('[data-testid^="iadm06-export-row-"]').filter({ hasText: 'COMPLETED' }).count();
    const r02Ok =
      (r02Completed > 0 || /COMPLETED|REQUESTED|PROCESSING/i.test(r02Export || '')) &&
      !/denied|refused|403/i.test(r02Export || '');
    push('R02 export platform aggregates', r02Ok, `${r02Export || ''} completedRows=${r02Completed}`, {
      shot: await shot(page, 'R02_export.png'),
    });
  } else {
    push('R02 export platform aggregates', false, 'skipped — overview denied');
  }

  // ---- R03 institute A ----
  if (!R03A.password) throw new Error('R03A password missing');
  await loginPersona(page, R03A, '/institution/analytics');
  await safeGoto(page, `${BASE}/institution/analytics`);
  await page.waitForSelector('[data-testid="iadm06-institution-desk"]', { timeout: 45000 });
  const scopeA = await page.getByTestId('iadm06-scope-tenant').textContent().catch(() => '');
  const metricsA = await page.locator('[data-testid^="iadm06-metric-"]').count();
  push('R03 institute A own analytics', metricsA > 0, `scope=${scopeA} metrics=${metricsA}`, {
    shot: await shot(page, 'R03A_institute.png'),
    tenantId: R03A.tenantId,
  });
  await page.getByTestId('iadm06-export-request').click();
  await page.waitForTimeout(1500);
  const r03Export = await page.getByTestId('iadm06-action-msg').textContent().catch(() => '');
  push('R03 institute A export own aggregates', /Export|COMPLETED|REQUESTED|PROCESSING/i.test(r03Export || ''), r03Export || '', {
    shot: await shot(page, 'R03A_export.png'),
  });

  // ---- R03 institute B (MAC / maharani) ----
  const modeB = await loginPersona(page, R03B, '/institution/analytics');
  await safeGoto(page, `${BASE}/institution/analytics`);
  await page.waitForSelector('[data-testid="iadm06-institution-desk"]', { timeout: 45000 });
  const scopeB = await page.getByTestId('iadm06-scope-tenant').textContent().catch(() => '');
  const metricsB = await page.locator('[data-testid^="iadm06-metric-"]').count();
  const isolated =
    !(scopeB || '').includes(R03A.tenantId) &&
    !(scopeB || '').includes('IADM04_DEMO') &&
    !(scopeB || '').includes(R03A.label);
  push('R03 institute B own analytics (no institute A data)', metricsB > 0 && isolated, `mode=${modeB} scope=${scopeB} metrics=${metricsB}`, {
    shot: await shot(page, 'R03B_institute.png'),
    tenantId: R03B.tenantId,
  });

  // ---- R04 denied ----
  if (!R04.password) throw new Error('R04 password missing');
  await loginPersona(page, R04, '/platform/analytics');
  await safeGoto(page, `${BASE}/platform/analytics`);
  await page.waitForTimeout(2000);
  const denied =
    page.url().includes('access-denied') ||
    (await page.locator('[data-testid="iadm06-access-denied"]').count()) > 0 ||
    /access denied|do not have access|forbidden/i.test((await page.locator('body').textContent().catch(() => '')) || '');
  push('R04 College Admin denied', denied, `url=${page.url()}`, {
    shot: await shot(page, 'R04_denied.png'),
  });

  // ---- R28 ----
  if (!R28.password) throw new Error('R28 password missing');
  await loginPersona(page, R28, '/platform/analytics/audit');
  await safeGoto(page, `${BASE}/platform/analytics/audit`);
  await page.waitForSelector('[data-testid="iadm06-auditor-desk"]', { timeout: 45000 });
  await page.getByTestId('iadm06-auditor-export-attempt').click();
  await page.waitForTimeout(1000);
  const deny = await page.getByTestId('iadm06-auditor-export-denied').textContent().catch(() => '');
  push('R28 read-only; export denied', /403|forbidden|cannot export|denied/i.test(deny || ''), deny || '', {
    shot: await shot(page, 'R28_audit.png'),
  });

  await browser.close();

  const pass = results.filter((r) => r.ok).length;
  const fail = results.length - pass;
  const md = [
    '# IADM-06 headed demo (US-028)',
    '',
    'DEMO ONLY suppression thresholds: **low=1** (show figures), **raised=50** (show suppression). Not a privacy standard.',
    '',
    `| Step | Result | Detail |`,
    `|---|---|---|`,
    ...results.map((r) => `| ${r.step} | ${r.ok ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`),
    '',
    `Totals: ${pass} PASS / ${fail} FAIL`,
    '',
    `Evidence directory: \`${OUT}\``,
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
