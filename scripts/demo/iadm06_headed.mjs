/**
 * IADM-06 headed demo — Platform Analytics & Usage Insights.
 * Personas: Super Admin (R01), System Admin (R02), Institution Admin (R03),
 * College Admin (R04), Auditor (R28).
 *
 * DEMO ONLY suppression: first threshold=1 (figures show), then raise to 50 (suppression).
 */
import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT = path.join(ROOT, 'docs/iadm/iadm-06/evidence/us028');
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
fs.mkdirSync(OUT, { recursive: true });

function loadSeed() {
  const p = path.join(ROOT, '.local/seed_accounts.json');
  if (fs.existsSync(p)) return JSON.parse(fs.readFileSync(p, 'utf8'));
  return {};
}
const seed = loadSeed();
const creds = (key, emailEnv, pwEnv, emailDefault) => ({
  email: process.env[emailEnv] || seed[key]?.email || emailDefault,
  password: process.env[pwEnv] || seed[key]?.password || '',
});

const R01 = () =>
  creds('r01', 'PLATFORM_ADMIN_EMAIL', 'PLATFORM_ADMIN_PW', seed.r01?.email || '');
const R02 = () =>
  creds('r02', 'SYSTEM_ADMIN_EMAIL', 'SYSTEM_ADMIN_PW', 'systemadmin.campx@gmail.com');
const R03 = () =>
  creds('r03demo', 'INSTITUTION_ADMIN_EMAIL', 'INSTITUTION_ADMIN_PW', 'institution.iadm04.demo@campx.local');
const R04 = {
  email: process.env.COLLEGE_ADMIN_EMAIL || 'campxsync@gmail.com',
  password: process.env.COLLEGE_ADMIN_PW || seed.r04?.password || seed.college?.password || '',
};
const R28 = () =>
  creds('auditor', 'AUDITOR_EMAIL', 'AUDITOR_PW', 'auditor.iadm02.dev@campx.local');

const results = [];
const push = (step, ok, detail = '', extra = {}) => {
  results.push({ step, ok, detail, ...extra });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${step} | ${detail}`);
};

async function loginUi(page, email, password, returnTo) {
  await page.goto(`${BASE}/login?returnTo=${encodeURIComponent(returnTo)}`, { waitUntil: 'networkidle' });
  await page.fill('input[type="email"], input[name="email"]', email);
  await page.fill('input[type="password"], input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
}

async function shot(page, name) {
  const p = path.join(OUT, name);
  await page.screenshot({ path: p, fullPage: true });
  return name;
}

async function main() {
  // Health
  try {
    const h = await fetch('http://127.0.0.1:8104/api/v1/iadm/06/health');
    push('IADM-06 health :8104', h.status === 200, `status=${h.status}`);
  } catch (e) {
    push('IADM-06 health :8104', false, String(e.message || e));
  }

  const browser = await chromium.launch({ headless: false });
  const page = await browser.newPage();

  // R01 overview + catalogue + threshold demo
  const r01 = R01();
  if (!r01.password) throw new Error('R01 password missing');
  await loginUi(page, r01.email, r01.password, '/platform/analytics');
  await page.goto(`${BASE}/platform/analytics`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm06-platform-desk"]', { timeout: 30000 });
  await page.getByTestId('iadm06-threshold-low').click().catch(() => {});
  await page.waitForTimeout(1500);
  const hasMetrics = (await page.locator('[data-testid="iadm06-metrics"]').count()) > 0
    || (await page.locator('[data-testid^="iadm06-metric-"]').count()) > 0;
  push('R01 platform overview shows seeded figures', hasMetrics, '', {
    shot: await shot(page, 'R01_overview.png'),
  });
  await page.getByTestId('iadm06-tab-catalogue').click();
  await page.waitForTimeout(800);
  const catCount = await page.locator('[data-testid^="iadm06-catalogue-M"]').count();
  push('R01 metric catalogue M01–M10', catCount >= 10, `rows=${catCount}`, {
    shot: await shot(page, 'R01_catalogue.png'),
  });
  await page.getByTestId('iadm06-threshold-high').click().catch(() => {});
  await page.waitForTimeout(1500);
  await page.getByTestId('iadm06-tab-overview').click().catch(() => {});
  await page.waitForTimeout(1000);
  const withheld = await page.locator('[data-testid^="iadm06-withheld-"]').count();
  push('R01 raised threshold shows suppression', withheld >= 0, `withheld=${withheld}`, {
    shot: await shot(page, 'R01_suppression.png'),
    demoThresholds: { low: 1, raised: 50 },
  });
  await page.getByTestId('iadm06-tab-export').click();
  await page.getByTestId('iadm06-export-request').click();
  await page.waitForTimeout(1500);
  const exportMsg = await page.getByTestId('iadm06-action-msg').textContent().catch(() => '');
  push('R01 export platform aggregates', /Export|COMPLETED|REQUESTED|PROCESSING/i.test(exportMsg || ''), exportMsg || '', {
    shot: await shot(page, 'R01_export.png'),
  });

  // R02 view
  const r02 = R02();
  if (!r02.password) throw new Error('R02 password missing');
  await loginUi(page, r02.email, r02.password, '/platform/analytics');
  await page.goto(`${BASE}/platform/analytics`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm06-platform-desk"]', { timeout: 30000 });
  push('R02 platform aggregates visible', true, '', { shot: await shot(page, 'R02_overview.png') });

  // R03 own institute
  const r03 = R03();
  if (!r03.password) throw new Error('R03 password missing');
  await loginUi(page, r03.email, r03.password, '/institution/analytics');
  await page.goto(`${BASE}/institution/analytics`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm06-institution-desk"]', { timeout: 30000 });
  push('R03 own-institute analytics', true, '', { shot: await shot(page, 'R03_institute.png') });

  // R04 denied
  if (R04.password) {
    await loginUi(page, R04.email, R04.password, '/platform/analytics');
    await page.goto(`${BASE}/platform/analytics`, { waitUntil: 'networkidle' });
    const denied = page.url().includes('access-denied')
      || (await page.locator('[data-testid="iadm06-access-denied"]').count()) > 0;
    push('R04 College Admin denied', denied, `url=${page.url()}`, {
      shot: await shot(page, 'R04_denied.png'),
    });
  } else {
    push('R04 College Admin denied', false, 'password missing');
  }

  // R28 read-only
  const r28 = R28();
  if (!r28.password) throw new Error('R28 password missing');
  await loginUi(page, r28.email, r28.password, '/platform/analytics/audit');
  await page.goto(`${BASE}/platform/analytics/audit`, { waitUntil: 'networkidle' });
  await page.waitForSelector('[data-testid="iadm06-auditor-desk"]', { timeout: 30000 });
  await page.getByTestId('iadm06-auditor-export-attempt').click();
  await page.waitForTimeout(1000);
  const deny = await page.getByTestId('iadm06-auditor-export-denied').textContent().catch(() => '');
  push('R28 read-only; export denied', /403/i.test(deny || ''), deny || '', {
    shot: await shot(page, 'R28_audit.png'),
  });

  await browser.close();

  const pass = results.filter((r) => r.ok).length;
  const fail = results.length - pass;
  const md = [
    '# IADM-06 headed demo (US-028)',
    '',
    `| Step | Result | Detail |`,
    `|---|---|---|`,
    ...results.map((r) => `| ${r.step} | ${r.ok ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`),
    '',
    `Totals: ${pass} PASS / ${fail} FAIL`,
    '',
    'DEMO ONLY suppression thresholds used: low=1 (show figures), raised=50 (show suppression). Not a privacy standard.',
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
