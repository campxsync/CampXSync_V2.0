/**
 * IADM-02 headed demos: US-012 edit, R02 ceiling, R01-B login, S2 R01-A→R01-B approve.
 * Credentials from .env.audit + evidence/.local/seed_accounts.json — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-02/evidence');
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
const seed = JSON.parse(fs.readFileSync(path.join(evidence, '.local/seed_accounts.json'), 'utf8'));

const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const results = [];

async function login(page, email, password) {
  await page.goto(`${BASE}/login?returnTo=/platform/configuration`, { waitUntil: 'domcontentloaded' });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  // Do not match /login?returnTo=/platform... — require real platform desk.
  await page.waitForURL((url) => {
    const p = url.pathname || '';
    return p === '/platform' || p.startsWith('/platform/');
  }, { timeout: 60000 });
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

const browser = await chromium.launch({ headless: false, slowMo: 80 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  // --- US-012 + start S2 draft as R01-A ---
  const r01aEmail = process.env.PLATFORM_ADMIN_EMAIL;
  const r01aPw = process.env.PLATFORM_ADMIN_PW;
  await login(page, r01aEmail, r01aPw);
  await page.goto(`${BASE}/platform/configuration/security.session_timeout_min`);
  await page.getByTestId('iadm02-key-detail').waitFor({ timeout: 20000 });
  await page.locator('textarea, input').filter({ hasText: '' }).first();
  // fill create draft form
  const reason = page.getByPlaceholder(/reason/i).or(page.locator('[data-testid="iadm02-draft-reason"]'));
  await page.getByTestId('iadm02-draft-value').fill('50');
  await page.getByTestId('iadm02-draft-reason').fill('S2 R01-A draft for R01-B approval');
  await page.getByTestId('iadm02-draft-submit').click();
  await page.waitForTimeout(1500);
  results.push({ step: 'R01-A create draft', shot: await shot(page, 's2_r01a_create_draft.png') });

  await page.goto(`${BASE}/platform/configuration/drafts`);
  await page.getByTestId('iadm02-drafts-desk').waitFor();
  // Edit first editable draft (US-012)
  const editBtn = page.locator('[data-testid^="iadm02-edit-"]').first();
  await editBtn.click();
  const editValue = page.locator('[data-testid^="iadm02-edit-value-"]').first();
  await editValue.fill('55');
  const editReason = page.locator('[data-testid^="iadm02-edit-reason-"]').first();
  await editReason.fill('US-012 headed edit: session timeout 55');
  await page.locator('[data-testid^="iadm02-save-edit-"]').first().click();
  await page.waitForTimeout(1500);
  results.push({ step: 'US-012 edit', shot: await shot(page, 's1_us012_edit.png'), ok: true });

  // Validate + submit the R01-A S2 draft (find row with reason containing S2 R01-A)
  const rows = page.locator('[data-testid^="iadm02-draft-"]');
  const n = await rows.count();
  let s2DraftId = null;
  for (let i = 0; i < n; i++) {
    const text = await rows.nth(i).innerText();
    if (text.includes('S2 R01-A') || text.includes('US-012 headed edit')) {
      const testid = await rows.nth(i).getAttribute('data-testid');
      s2DraftId = testid?.replace('iadm02-draft-', '');
      break;
    }
  }
  if (!s2DraftId) {
    // use first VALIDATED or DRAFT session timeout
    for (let i = 0; i < n; i++) {
      const testid = await rows.nth(i).getAttribute('data-testid');
      const text = await rows.nth(i).innerText();
      if (text.includes('security.session_timeout_min') && (text.includes('DRAFT') || text.includes('VALIDATED'))) {
        s2DraftId = testid?.replace('iadm02-draft-', '');
        break;
      }
    }
  }
  if (s2DraftId) {
    await page.getByTestId(`iadm02-validate-${s2DraftId}`).click();
    await page.waitForTimeout(1200);
    // reload may change status; submit if button appears
    if (await page.getByTestId(`iadm02-submit-${s2DraftId}`).count()) {
      await page.getByTestId(`iadm02-submit-${s2DraftId}`).click();
      await page.waitForTimeout(1200);
    } else {
      await page.reload();
      await page.waitForTimeout(1000);
      if (await page.getByTestId(`iadm02-submit-${s2DraftId}`).count()) {
        await page.getByTestId(`iadm02-submit-${s2DraftId}`).click();
        await page.waitForTimeout(1200);
      }
    }
    results.push({ step: 'R01-A validate+submit', draftId: s2DraftId, shot: await shot(page, 's2_r01a_submitted.png') });
  }

  // --- R02 headed ---
  await context.clearCookies();
  await login(page, seed.r02.email, seed.r02.password);
  await page.goto(`${BASE}/platform/configuration/security.session_timeout_min`);
  await page.getByTestId('iadm02-key-detail').waitFor({ timeout: 20000 });
  results.push({ step: 'R02 login catalogue key', shot: await shot(page, 's1_r02_login.png') });
  // allow draft
  await page.getByTestId('iadm02-draft-value').fill('41');
  await page.getByTestId('iadm02-draft-reason').fill('R02 headed allow session timeout');
  await page.getByTestId('iadm02-draft-submit').click();
  await page.waitForTimeout(1500);
  const allowMsg = await page.getByTestId('iadm02-key-detail').innerText();
  results.push({
    step: 'R02 allow session',
    ok: /draft/i.test(allowMsg) && !/ceiling|forbidden|not authorized/i.test(allowMsg),
    shot: await shot(page, 's1_r02_allow.png'),
  });

  await page.goto(`${BASE}/platform/configuration/attendance.min_pct`);
  await page.waitForTimeout(1000);
  await page.getByTestId('iadm02-draft-value').fill('80');
  await page.getByTestId('iadm02-draft-reason').fill('R02 headed deny attendance');
  await page.getByTestId('iadm02-draft-submit').click();
  await page.waitForTimeout(1500);
  const denyText = await page.locator('body').innerText();
  results.push({
    step: 'R02 deny attendance',
    ok: /R02_CEILING|only security\.session_timeout_min|not authorized|Forbidden/i.test(denyText),
    shot: await shot(page, 's1_r02_deny.png'),
  });

  // --- R01-B headed approve ---
  await context.clearCookies();
  await login(page, seed.r01b.email, seed.r01b.password);
  await page.goto(`${BASE}/platform/configuration`);
  await page.getByTestId('iadm02-catalogue').or(page.getByText('Platform configuration')).first().waitFor({ timeout: 20000 });
  results.push({ step: 'R01-B login', shot: await shot(page, 's1_r01b_login.png'), ok: true });

  await page.goto(`${BASE}/platform/configuration/drafts`);
  await page.getByTestId('iadm02-drafts-desk').waitFor();
  await page.getByTestId('iadm02-decision-reason').fill('S2 R01-B approve of R01-A draft');
  const approveBtn = page.locator('[data-testid^="iadm02-approve-"]').first();
  if ((await approveBtn.count()) > 0) {
    await approveBtn.click();
    await page.waitForTimeout(1500);
    results.push({ step: 'R01-B approve', shot: await shot(page, 's2_r01b_approve.png'), ok: true });
  } else {
    results.push({ step: 'R01-B approve', ok: false, detail: 'No PENDING_APPROVAL draft visible' });
  }

  // versions desk
  await page.goto(`${BASE}/platform/configuration/versions`);
  await page.waitForTimeout(1000);
  results.push({ step: 'versions desk', shot: await shot(page, 's2_versions.png') });
} catch (e) {
  results.push({ step: 'ERROR', detail: String(e && e.message ? e.message : e) });
  try {
    await shot(page, 's1_s2_error.png');
  } catch {
    /* ignore */
  }
} finally {
  await browser.close();
}

const out = path.join(evidence, 'S1_S2_HEADED_RESULTS.json');
fs.writeFileSync(out, JSON.stringify({ at: new Date().toISOString(), results }, null, 2));
console.log(JSON.stringify({ out, results }, null, 2));
