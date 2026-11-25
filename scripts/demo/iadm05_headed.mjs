/**
 * IADM-05 headed evidence film (US-032) — personas R01/R02/R03/R04/R28.
 * Usage: node scripts/demo/iadm05_headed.mjs
 * Requires stack up (UI :3000, gateway :8080, IADM-05 :8103) and .env.audit accounts.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { chromium } from 'playwright';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const evidenceDir = path.join(root, 'docs/iadm/iadm-05/evidence/us032');
fs.mkdirSync(evidenceDir, { recursive: true });

function loadEnvAudit() {
  const p = path.join(root, '.env.audit');
  if (!fs.existsSync(p)) return {};
  const out = {};
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m) out[m[1]] = m[2];
  }
  return out;
}

const env = loadEnvAudit();
const base = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';

const personas = [
  {
    id: 'R01',
    email: env.CAMPX_PLATFORM_ADMIN_EMAIL || env.PLATFORM_ADMIN_EMAIL || 'platformadmin.campx@gmail.com',
    password: env.CAMPX_PLATFORM_ADMIN_PASSWORD || env.PLATFORM_ADMIN_PASSWORD,
    path: '/platform/governance',
    testid: 'iadm05-platform-desk',
  },
  {
    id: 'R02',
    email: env.CAMPX_SYSTEM_ADMIN_EMAIL || env.SYSTEM_ADMIN_EMAIL || 'systemadmin.campx@gmail.com',
    password: env.CAMPX_SYSTEM_ADMIN_PASSWORD || env.SYSTEM_ADMIN_PASSWORD,
    path: '/platform/governance',
    testid: 'iadm05-platform-desk',
  },
  {
    id: 'R03',
    email: env.CAMPX_COLLEGE_ADMIN_EMAIL || 'campxsync@gmail.com',
    password: env.CAMPX_COLLEGE_ADMIN_PASSWORD,
    path: '/institution/governance',
    testid: 'iadm05-institution-desk',
  },
  {
    id: 'R04',
    email: env.CAMPX_FACULTY_EMAIL || 'faculty.campx@gmail.com',
    password: env.CAMPX_FACULTY_PASSWORD,
    // College admin account may differ; fall back documented in report if login fails
    path: '/college/governance',
    testid: 'iadm05-college-desk',
  },
  {
    id: 'R28',
    email: env.CAMPX_AUDITOR_EMAIL || 'auditor.campx@gmail.com',
    password: env.CAMPX_AUDITOR_PASSWORD,
    path: '/platform/governance/audit',
    testid: 'iadm05-auditor-desk',
  },
];

async function login(page, email, password) {
  await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('input[type="email"], input[name="email"]', email || '');
  await page.fill('input[type="password"], input[name="password"]', password || '');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
}

async function run() {
  const browser = await chromium.launch({ headless: false });
  const results = [];
  for (const persona of personas) {
    const context = await browser.newContext({ recordVideo: { dir: evidenceDir } });
    const page = await context.newPage();
    const shot = path.join(evidenceDir, `${persona.id}.png`);
    try {
      if (!persona.password) throw new Error('password env missing (not printed)');
      await login(page, persona.email, persona.password);
      await page.goto(`${base}${persona.path}`, { waitUntil: 'networkidle' });
      await page.waitForSelector(`[data-testid="${persona.testid}"]`, { timeout: 20000 });
      await page.screenshot({ path: shot, fullPage: true });
      results.push({ persona: persona.id, status: 'PASS', shot });
    } catch (e) {
      await page.screenshot({ path: shot, fullPage: true }).catch(() => {});
      results.push({ persona: persona.id, status: 'FAIL', error: String(e.message || e), shot });
    }
    await context.close();
  }
  await browser.close();
  const md = [
    '# IADM-05 US-032 headed evidence',
    '',
    ...results.map(
      (r) =>
        `- **${r.persona}**: ${r.status}${r.error ? ` — ${r.error}` : ''} · screenshot \`${path.basename(r.shot)}\``
    ),
    '',
  ].join('\n');
  fs.writeFileSync(path.join(evidenceDir, 'DEMO.md'), md);
  console.log(md);
  const failed = results.filter((r) => r.status !== 'PASS').length;
  process.exit(failed ? 1 : 0);
}

run().catch((e) => {
  console.error(e);
  process.exit(1);
});
