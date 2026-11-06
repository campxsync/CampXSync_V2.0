import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(path.resolve('frontend/package.json'));
const { chromium } = require('playwright');

const DEPLOYED_BASE = 'https://campxsync-web-dev.vercel.app';
const SCREENSHOT_DIR = path.resolve('docs/trial/screenshots/deployed_verification');

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function getEnvVar(varName) {
  let val = process.env[varName];
  if (!val) {
    try {
      const envPath = path.resolve('frontend/.env.local');
      if (fs.existsSync(envPath)) {
        const content = fs.readFileSync(envPath, 'utf8');
        const match = content.match(new RegExp(`^${varName}=(.+)`, 'm'));
        if (match) {
          val = match[1].trim().replace(/^['"]|['"]$/g, '');
        }
      }
    } catch {}
  }
  return val;
}

const COLLEGE_EMAIL = 'campxsync@gmail.com';
const COLLEGE_PASSWORD = getEnvVar('CAMPX_STAGING_TEST_PASSWORD');

const PLATFORM_EMAIL = 'platformadmin.campx@gmail.com';
const PLATFORM_PASSWORD = getEnvVar('CAMPX_STAGING_PLATFORM_ADMIN_TEST_PASSWORD');

const FACULTY_EMAIL = 'faculty.campx@gmail.com';
const FACULTY_PASSWORD = getEnvVar('CAMPX_STAGING_FACULTY_TEST_PASSWORD');

const testResults = [];

function record(id, name, pass, detail = '', screenshot = '') {
  testResults.push({ id, name, pass, detail, screenshot });
  const tag = pass ? '\x1b[32m[PASS]\x1b[0m' : '\x1b[31m[FAIL]\x1b[0m';
  console.log(`${tag} ${id}: ${name}${detail ? ` (${detail})` : ''}`);
}

async function runLiveAcceptance() {
  console.log('========================================================================');
  console.log(' CAMPXSYNC: DEPLOYED VERCEL LIVE AUTH & PROFILE RESOLUTION ACCEPTANCE   ');
  console.log('========================================================================');
  console.log(`Live Target: ${DEPLOYED_BASE}`);
  console.log(`Screenshots: ${SCREENSHOT_DIR}\n`);

  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  // ---------------------------------------------------------------------------
  // TEST 1: Check Obsolete Reference Removed from /access-denied
  // ---------------------------------------------------------------------------
  console.log('--- Test 1: Verify Obsolete Staging Reference Removed ---');
  const adContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const adPage = await adContext.newPage();
  await adPage.goto(`${DEPLOYED_BASE}/access-denied?reason=db_error`, { waitUntil: 'networkidle' });
  const adText = await adPage.locator('body').innerText();
  const hasObsoleteRef = adText.includes('Supabase Staging Policy Guard');
  const sAd = path.join(SCREENSHOT_DIR, '01_access_denied_cleaned.png');
  await adPage.screenshot({ path: sAd });
  record(
    'DEP-REF-01',
    'Obsolete reference "Supabase Staging Policy Guard" completely absent from rendered /access-denied page',
    !hasObsoleteRef,
    `Found reference: ${hasObsoleteRef}`,
    sAd
  );
  await adContext.close();

  // ---------------------------------------------------------------------------
  // TEST 2: College Admin Live Login & Dashboard Verification
  // ---------------------------------------------------------------------------
  console.log('\n--- Test 2: College Admin Live Login & Dashboard Verification ---');
  const colContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const colPage = await colContext.newPage();
  await colPage.goto(`${DEPLOYED_BASE}/login`, { waitUntil: 'networkidle' });

  await colPage.fill('input#institutional-email', COLLEGE_EMAIL);
  await colPage.fill('input#password', COLLEGE_PASSWORD);
  await colPage.click('button[type="submit"]');

  await colPage.waitForURL('**/college', { timeout: 20000 }).catch(() => {});
  const colLanded = colPage.url().includes('/college');
  const colBody = await colPage.locator('body').innerText().catch(() => '');
  const colContentLoaded = colBody.includes('College Admin') && colBody.includes('CampX Architecture Dev Test Institute');
  const sCol = path.join(SCREENSHOT_DIR, '02_deployed_college_admin.png');
  await colPage.screenshot({ path: sCol });

  record(
    'DEP-AUTH-COL-01',
    'College Admin logs in on deployed Vercel, resolves role authoritatively, and lands on /college',
    colLanded && colContentLoaded,
    `URL: ${colPage.url()}, Content loaded: ${colContentLoaded}`,
    sCol
  );

  // Verify reload/session persistence
  await colPage.reload({ waitUntil: 'networkidle' });
  const colReloaded = colPage.url().includes('/college');
  record(
    'DEP-AUTH-COL-02',
    'College Admin session persists across browser page refresh on deployed Vercel',
    colReloaded,
    `URL after reload: ${colPage.url()}`
  );

  // ---------------------------------------------------------------------------
  // TEST 3: Platform Admin Live Login & Dashboard Verification
  // ---------------------------------------------------------------------------
  console.log('\n--- Test 3: Platform Admin Live Login & Dashboard Verification ---');
  const platContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const platPage = await platContext.newPage();
  await platPage.goto(`${DEPLOYED_BASE}/login`, { waitUntil: 'networkidle' });

  await platPage.fill('input#institutional-email', PLATFORM_EMAIL);
  await platPage.fill('input#password', PLATFORM_PASSWORD);
  await platPage.click('button[type="submit"]');

  await platPage.waitForURL('**/platform', { timeout: 20000 }).catch(() => {});
  const platLanded = platPage.url().includes('/platform');
  const platBody = await platPage.locator('body').innerText().catch(() => '');
  const platContentLoaded = platBody.includes('Platform') || platBody.includes('Tenants') || platBody.includes('Operator');
  const sPlat = path.join(SCREENSHOT_DIR, '03_deployed_platform_admin.png');
  await platPage.screenshot({ path: sPlat });

  record(
    'DEP-AUTH-PLAT-01',
    'Platform Admin logs in on deployed Vercel, resolves role authoritatively, and lands on /platform',
    platLanded,
    `URL: ${platPage.url()}`,
    sPlat
  );

  // Verify reload/session persistence
  await platPage.reload({ waitUntil: 'networkidle' });
  const platReloaded = platPage.url().includes('/platform');
  record(
    'DEP-AUTH-PLAT-02',
    'Platform Admin session persists across browser page refresh on deployed Vercel',
    platReloaded,
    `URL after reload: ${platPage.url()}`
  );

  // ---------------------------------------------------------------------------
  // TEST 4: Faculty Live Login & Dashboard Verification
  // ---------------------------------------------------------------------------
  console.log('\n--- Test 4: Faculty Live Login & Dashboard Verification ---');
  const facContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const facPage = await facContext.newPage();
  await facPage.goto(`${DEPLOYED_BASE}/login`, { waitUntil: 'networkidle' });

  await facPage.fill('input#institutional-email', FACULTY_EMAIL);
  await facPage.fill('input#password', FACULTY_PASSWORD);
  await facPage.click('button[type="submit"]');

  await facPage.waitForURL('**/faculty', { timeout: 20000 }).catch(() => {});
  const facLanded = facPage.url().includes('/faculty');
  const facBody = await facPage.locator('body').innerText().catch(() => '');
  const facContentLoaded = facBody.includes('Faculty') || facBody.includes('Timetable') || facBody.includes('Attendance');
  const sFac = path.join(SCREENSHOT_DIR, '04_deployed_faculty.png');
  await facPage.screenshot({ path: sFac });

  record(
    'DEP-AUTH-FAC-01',
    'Faculty logs in on deployed Vercel, resolves role authoritatively, and lands on /faculty',
    facLanded,
    `URL: ${facPage.url()}`,
    sFac
  );

  // Verify reload/session persistence
  await facPage.reload({ waitUntil: 'networkidle' });
  const facReloaded = facPage.url().includes('/faculty');
  record(
    'DEP-AUTH-FAC-02',
    'Faculty session persists across browser page refresh on deployed Vercel',
    facReloaded,
    `URL after reload: ${facPage.url()}`
  );

  // ---------------------------------------------------------------------------
  // TEST 5: Security & Route Guard Enforcement on Deployed Application
  // ---------------------------------------------------------------------------
  console.log('\n--- Test 5: Security & Fail-Closed Route Guards ---');

  // 5a. Unauthenticated access redirects to /login
  const anonContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const anonPage = await anonContext.newPage();
  await anonPage.goto(`${DEPLOYED_BASE}/college`, { waitUntil: 'networkidle' });
  const anonRedirected = anonPage.url().includes('/login');
  record(
    'DEP-SEC-01',
    'Unauthenticated access to /college fails closed and redirects to /login',
    anonRedirected,
    `Final URL: ${anonPage.url()}`
  );
  await anonContext.close();

  // 5b. Faculty attempting cross-role access to /platform is denied
  await facPage.goto(`${DEPLOYED_BASE}/platform`, { waitUntil: 'networkidle' });
  const facCrossDenied = facPage.url().includes('/access-denied');
  record(
    'DEP-SEC-02',
    'Faculty attempting cross-role access to /platform fails closed with access-denied',
    facCrossDenied,
    `Final URL: ${facPage.url()}`
  );

  // 5c. College Admin attempting cross-role access to /platform is denied
  await colPage.goto(`${DEPLOYED_BASE}/platform`, { waitUntil: 'networkidle' });
  const colCrossDenied = colPage.url().includes('/access-denied');
  record(
    'DEP-SEC-03',
    'College Admin attempting cross-role access to /platform fails closed with access-denied',
    colCrossDenied,
    `Final URL: ${colPage.url()}`
  );

  // 5d. Cross-role returnTo parameter safely ignored
  const crossContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const crossPage = await crossContext.newPage();
  await crossPage.goto(`${DEPLOYED_BASE}/login?returnTo=%2Fplatform`, { waitUntil: 'networkidle' });
  await crossPage.fill('input#institutional-email', FACULTY_EMAIL);
  await crossPage.fill('input#password', FACULTY_PASSWORD);
  await crossPage.click('button[type="submit"]');

  await crossPage.waitForURL('**/faculty', { timeout: 20000 }).catch(() => {});
  const crossSafe = crossPage.url().includes('/faculty') && !crossPage.url().includes('/platform');
  record(
    'DEP-SEC-04',
    'Faculty login with returnTo=/platform safely ignores unauthorized returnTo and lands on /faculty',
    crossSafe,
    `Final URL: ${crossPage.url()}`
  );
  await crossContext.close();

  await browser.close();

  console.log('\n========================================================================');
  console.log(' DEPLOYED ACCEPTANCE SUMMARY');
  console.log('========================================================================');
  const passed = testResults.filter((r) => r.pass).length;
  const total = testResults.length;
  console.log(`Results: ${passed}/${total} checks PASSED\n`);

  if (passed === total) {
    console.log('\x1b[32mALL DEPLOYED ACCEPTANCE CRITERIA SATISFIED!\x1b[0m');
    process.exit(0);
  } else {
    console.error('\x1b[31mSOME DEPLOYED CHECKS FAILED!\x1b[0m');
    process.exit(1);
  }
}

runLiveAcceptance().catch((err) => {
  console.error('Fatal error during deployed acceptance:', err);
  process.exit(1);
});

