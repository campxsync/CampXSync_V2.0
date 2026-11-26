/**
 * IADM-08 headed demo — Audit, Health & Operations Oversight (US-023).
 * Personas: R01, R02, R03, R04 deny, R28 read-only + export deny.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const require = createRequire(path.join(ROOT, 'frontend/package.json'));
const { chromium } = require('playwright');
const OUT = path.join(ROOT, 'docs/iadm/iadm-08/evidence/us023');
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
const R03 = {
  email: seed.r03demo?.email || 'institution.iadm04.demo@campx.local',
  password: seed.r03demo?.password || '',
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

async function shot(page, name) {
  const p = path.join(OUT, name);
  await page.screenshot({ path: p, fullPage: true });
  return p;
}

async function main() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ recordVideo: { dir: OUT } });
  const page = await context.newPage();

  try {
    if (!R01.password) throw new Error('R01 password missing');
    await loginUi(page, R01.email, R01.password, '/platform/operations');
    await safeGoto(page, `${BASE}/platform/operations`);
    await page.getByTestId('iadm08-platform-desk').waitFor({ timeout: 15000 });
    // Health overview probes many loopback targets (~8s); wait for loading to clear.
    await page.waitForFunction(() => {
      const loading = document.querySelector('[data-testid="iadm08-loading"]');
      const panel = document.querySelector('[data-testid="iadm08-health-overview"]');
      const err = document.querySelector('[data-testid="iadm08-error"]');
      return (!loading || loading.textContent === '') && (!!panel || !!err);
    }, null, { timeout: 30000 }).catch(() => {});
    const desk = (await page.getByTestId('iadm08-platform-desk').count()) > 0;
    push('R01 platform operations desk', desk, '', { shot: await shot(page, 'R01_overview.png') });
    const healthyish = await page.getByTestId('iadm08-health-overview').count();
    const errText = ((await page.getByTestId('iadm08-error').textContent().catch(() => '')) || '');
    push('R01 health overview visible', healthyish > 0, `panel=${healthyish} err=${errText}`);

    await page.getByTestId('iadm08-tab-audit').click();
    await page.waitForTimeout(500);
    await page.getByTestId('iadm08-audit-search').click();
    await page.waitForFunction(() => {
      const loading = document.querySelector('[data-testid="iadm08-loading"]');
      const rows = document.querySelectorAll('[data-testid^="iadm08-audit-row-"]');
      const err = document.querySelector('[data-testid="iadm08-error"]');
      return (!loading || loading.textContent === '') && (rows.length > 0 || !!err);
    }, null, { timeout: 20000 }).catch(() => {});
    const auditRows = await page.locator('[data-testid^="iadm08-audit-row-"]').count();
    push('R01 audit search returns rows', auditRows > 0, `rows=${auditRows}`, {
      shot: await shot(page, 'R01_audit.png'),
    });

    await page.getByTestId('iadm08-tab-sla').click();
    await page.waitForTimeout(800);
    const slaText = await page.getByTestId('iadm08-sla-value').textContent();
    push('R01 SLA not available', /not available/i.test(slaText || ''), slaText || '');

    if (!R02.password) throw new Error('R02 password missing');
    await loginUi(page, R02.email, R02.password, '/platform/operations');
    await safeGoto(page, `${BASE}/platform/operations`);
    push('R02 platform operations desk', (await page.getByTestId('iadm08-platform-desk').count()) > 0, '', {
      shot: await shot(page, 'R02_desk.png'),
    });

    if (R03.password) {
      await loginUi(page, R03.email, R03.password, '/institution/operations');
      await safeGoto(page, `${BASE}/institution/operations`);
      await page.getByTestId('iadm08-institution-desk').waitFor({ timeout: 15000 });
      await page.waitForTimeout(4000);
      const inst = (await page.getByTestId('iadm08-institution-desk').count()) > 0;
      push('R03 institute activity desk', inst, '', { shot: await shot(page, 'R03_institute.png') });
      const denied = await page.getByTestId('iadm08-health-denied').count();
      push('R03 health view denied', denied > 0, `count=${denied}`);
    } else {
      push('R03 institute activity desk', false, 'R03 password missing in seed');
    }

    if (R04.password) {
      await loginUi(page, R04.email, R04.password, '/platform/operations');
      await safeGoto(page, `${BASE}/platform/operations`);
      await page.waitForTimeout(1500);
      const denied =
        (await page.getByTestId('iadm08-error').count()) > 0 ||
        page.url().includes('access-denied') ||
        (await page.getByTestId('iadm08-platform-desk').count()) === 0;
      push('R04 denied IADM-08 platform desk', denied, page.url(), {
        shot: await shot(page, 'R04_deny.png'),
      });
    } else {
      push('R04 denied IADM-08 platform desk', false, 'R04 password missing');
    }

    if (!R28.password) throw new Error('R28 password missing');
    await loginUi(page, R28.email, R28.password, '/platform/operations');
    await safeGoto(page, `${BASE}/platform/operations`);
    push('R28 operations desk', (await page.getByTestId('iadm08-platform-desk').count()) > 0, '', {
      shot: await shot(page, 'R28_desk.png'),
    });
    await page.getByTestId('iadm08-tab-audit').click();
    await page.waitForTimeout(400);
    await page.getByTestId('iadm08-export-attempt').click();
    await page.waitForTimeout(800);
    const exportMsg = (await page.getByTestId('iadm08-action-msg').textContent()) || '';
    push('R28 export denied', /404|not available|IADM08_NOT_AVAILABLE/i.test(exportMsg), exportMsg);
  } catch (e) {
    push('headed runner exception', false, String(e.message || e));
  } finally {
    await context.close();
    await browser.close();
  }

  const pass = results.filter((r) => r.ok).length;
  const total = results.length;
  const md = [
    '# IADM-08 US-023 Headed DEMO',
    '',
    `| Check | Result | Detail |`,
    `|---|---|---|`,
    ...results.map((r) => `| ${r.step} | ${r.ok ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`),
    '',
    `**Score:** ${pass}/${total}`,
  ].join('\n');
  fs.writeFileSync(path.join(OUT, 'DEMO.md'), md);
  fs.writeFileSync(path.join(OUT, 'RESULTS.json'), JSON.stringify({ pass, total, results }, null, 2));
  console.log(`\nHeaded score ${pass}/${total} → ${OUT}`);
  process.exit(pass === total ? 0 : 1);
}

main();
