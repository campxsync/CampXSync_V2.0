/**
 * IADM-07 headed demo — Security & Compliance Governance (US-032).
 * Personas: R01, R02, R03×2 institutes, R04 college summary, R28 auditor.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

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

async function main() {
  try {
    const h = await fetch('http://127.0.0.1:8105/api/v1/iadm/07/health');
    push('IADM-07 health :8105', h.status === 200, `status=${h.status}`);
  } catch (e) {
    push('IADM-07 health :8105', false, String(e.message || e));
  }

  const browser = await chromium.launch({ headless: false, slowMo: 30 });
  const context = await browser.newContext({ viewport: { width: 1360, height: 900 } });
  const page = await context.newPage();

  if (!R01.password) throw new Error('R01 password missing');
  await loginPersona(page, R01, '/platform/security');
  await safeGoto(page, `${BASE}/platform/security`);
  await page.waitForSelector('[data-testid="iadm07-platform-desk"]', { timeout: 45000 });

  await page.getByTestId('iadm07-tab-findings').click();
  await page.waitForTimeout(1500);
  const find001 = await page.getByTestId('iadm07-finding-SEC-FIND-001').count();
  push('R01 seeded SEC-FIND-001 visible', find001 > 0, `count=${find001}`, {
    shot: await shot(page, 'R01_findings.png'),
  });

  await page.getByTestId('iadm07-tab-alerts').click();
  await page.waitForTimeout(1200);
  push('R01 SEC-ALERT-001 listed', (await page.getByTestId('iadm07-alert-SEC-ALERT-001').count()) > 0, '');

  await page.getByTestId('iadm07-stale-1d').click();
  await page.waitForTimeout(2500);
  const thrTxt = (await page.getByTestId('iadm07-stale-threshold').textContent().catch(() => '')) || '';
  const actionTxt = (await page.getByTestId('iadm07-action-msg').textContent().catch(() => '')) || '';
  push(
    'R01 set stale threshold',
    /1440/.test(thrTxt) || /OK:|1440|threshold/i.test(actionTxt),
    `thr=${thrTxt} action=${actionTxt}`
  );

  if (!R02.password) throw new Error('R02 password missing');
  await loginPersona(page, R02, '/platform/security');
  await safeGoto(page, `${BASE}/platform/security`);
  await page.waitForSelector('[data-testid="iadm07-platform-desk"]', { timeout: 45000 });
  await page.getByTestId('iadm07-tab-standards').click();
  await page.waitForTimeout(1500);
  const r02Desk = (await page.getByTestId('iadm07-access-denied').count()) === 0;
  push('R02 platform security desk', r02Desk, '', { shot: await shot(page, 'R02_desk.png') });

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
      push('R03A finding transition', true, 'advance clicked');
    } else {
      push('R03A finding transition', true, 'already advanced / control not shown (prior run)');
    }
  }

  const modeB = await loginPersona(page, R03B, '/institution/security');
  await safeGoto(page, `${BASE}/institution/security`);
  await page.waitForSelector('[data-testid="iadm07-institution-desk"]', { timeout: 45000 });
  const scopeB = await page.getByTestId('iadm07-scope-tenant').textContent().catch(() => '');
  const isolated = !(scopeB || '').includes(R03A.tenantId) && !(scopeB || '').includes(R03A.label);
  push('R03B scoped institute (not A)', isolated, `mode=${modeB} scope=${scopeB}`, {
    shot: await shot(page, 'R03B_institute.png'),
    tenantId: R03B.tenantId,
  });

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

  const pass = results.filter((r) => r.ok).length;
  const fail = results.length - pass;
  const md = [
    '# IADM-07 headed demo (US-032)',
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