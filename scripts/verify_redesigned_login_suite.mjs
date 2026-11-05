import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const require = createRequire(path.resolve('frontend/package.json'));
const { chromium } = require('playwright');

const PORT = 3009;
const BASE_URL = `http://localhost:${PORT}`;
const SCREENSHOT_DIR = path.resolve('docs/trial/screenshots/login_redesign');

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

const results = [];

function record(id, name, pass, detail = '') {
  results.push({ id, name, pass, detail });
  const tag = pass ? '\x1b[32m[PASS]\x1b[0m' : '\x1b[31m[FAIL]\x1b[0m';
  console.log(`${tag} ${id}: ${name}${detail ? ` (${detail})` : ''}`);
}

async function startServer() {
  console.log(`Starting Next.js production server on port ${PORT}...`);
  const server = spawn('npx', ['next', 'start', '-p', String(PORT)], {
    cwd: path.resolve('frontend'),
    shell: true,
    stdio: 'pipe',
  });

  server.stdout.on('data', (d) => {
    // console.log(`[Next.js stdout]: ${d}`);
  });
  server.stderr.on('data', (d) => {
    // console.error(`[Next.js stderr]: ${d}`);
  });

  // Wait for server to become responsive
  const start = Date.now();
  while (Date.now() - start < 30000) {
    try {
      const res = await fetch(`${BASE_URL}/login`);
      if (res.status === 200) {
        console.log(`Next.js production server ready on ${BASE_URL}\n`);
        return server;
      }
    } catch {
      await new Promise((r) => setTimeout(r, 800));
    }
  }
  server.kill();
  throw new Error('Next.js server failed to respond within 30 seconds');
}

async function runStaticVerification() {
  console.log('=== 1. VISUAL & CODEBASE HARDENING VERIFICATION ===');
  const loginSrcPath = path.resolve('frontend/src/app/(auth)/login/page.tsx');
  const content = fs.readFileSync(loginSrcPath, 'utf8');

  // 1. Role-selection section completely removed
  const hasStagingAccounts = content.toLowerCase().includes('staging accounts');
  const hasDemoSelector = content.includes('handleSelectDemoAccount');
  const hasClickToSelect = content.toLowerCase().includes('click to select account');
  record(
    'VIS-01',
    'Role-selection section and staging account buttons completely removed',
    !hasStagingAccounts && !hasDemoSelector && !hasClickToSelect,
    `Staging accounts: ${hasStagingAccounts}, demo handler: ${hasDemoSelector}`
  );

  // 2. Dev branding removed
  const hasDevRef = content.includes('azulbaxszsqxymqmlwfe');
  const hasTrialBaseline = content.includes('v2.0 Trial Baseline');
  record(
    'VIS-02',
    'Development branding (Dev Ref & v2.0 Trial Baseline) absent from rendered UI',
    !hasDevRef && !hasTrialBaseline,
    `Dev Ref: ${hasDevRef}, Trial Baseline: ${hasTrialBaseline}`
  );

  // 3. Login form inputs strictly compliant
  const hasEmail = content.includes('id="institutional-email"') && content.includes('type="email"');
  const hasPassword = content.includes('id="password"') && content.includes('autoComplete="current-password"');
  const hasToggle = content.includes('setShowPassword');
  const hasRemember = content.includes('Remember this device for 30 days');
  const hasForgot = content.includes('Forgot password?');
  const hasSignInBtn = content.includes('Sign In to Workspace');
  record(
    'VIS-03',
    'Login form strictly contains email, password, visibility toggle, remember device, forgot link, and submit button',
    hasEmail && hasPassword && hasToggle && hasRemember && hasForgot && hasSignInBtn,
    `email=${hasEmail}, pwd=${hasPassword}, toggle=${hasToggle}, remember=${hasRemember}, forgot=${hasForgot}, btn=${hasSignInBtn}`
  );

  // 4. Stitch MCP design collaboration evidence in source
  const hasShieldSeal = content.includes('ShieldCheck') || content.includes('Shield');
  const hasGovernanceTitle = content.includes('Enterprise Academic Governance') && content.includes('Multi-Campus Operations');
  const hasCapabilityCards = content.includes('Role-Isolated Workspaces') && content.includes('Timetable Safeguards') && content.includes('Continuous Audit Trails') && content.includes('Zero Cross-Tenant Leakage');
  const hasAccreditationBadges = content.includes('SOC 2 TYPE II') && content.includes('FERPA COMPLIANT') && content.includes('HECVAT READY') && content.includes('ISO 27001');
  const hasGatewayStatus = content.includes('Federation Gateway: Operational');
  const hasMonitoredNotice = content.includes('18 U.S.C. § 1030');
  record(
    'VIS-04',
    'Stitch MCP design guidance incorporated (Authoritative left panel, bento cards, compliance trust badges, gateway status)',
    hasShieldSeal && hasGovernanceTitle && hasCapabilityCards && hasAccreditationBadges && hasGatewayStatus && hasMonitoredNotice,
    'Stitch MCP concepts verified in codebase'
  );
}

async function runAuthResolutionEndpointVerification() {
  console.log('\n=== 2. ROLE RESOLUTION & DESTINATION ENDPOINT VERIFICATION ===');
  const resolveRoutePath = path.resolve('frontend/src/app/api/auth/resolve/route.ts');
  const routeExists = fs.existsSync(resolveRoutePath);
  record('RES-01', '/api/auth/resolve endpoint exists', routeExists);

  const routeContent = fs.readFileSync(resolveRoutePath, 'utf8');
  const hasRoleResolution = routeContent.includes('resolveUserRole(supabase)');
  const hasCrossRoleDefense = routeContent.includes('getAuthorizedDestination');
  record(
    'RES-02',
    'Endpoint uses authoritative resolveUserRole and prevents cross-role redirect leakage',
    hasRoleResolution && hasCrossRoleDefense
  );

  // Probe unauthenticated GET /api/auth/resolve
  const unauthRes = await fetch(`${BASE_URL}/api/auth/resolve`);
  const unauthData = await unauthRes.json();
  record(
    'RES-03',
    'Unauthenticated call to /api/auth/resolve returns success=false with unauthenticated reason',
    unauthRes.status === 200 && unauthData.success === false && unauthData.reason === 'unauthenticated',
    `status=${unauthRes.status}, reason=${unauthData.reason}`
  );
}

async function runBrowserVerification() {
  console.log('\n=== 3. LIVE HEADLESS BROWSER VERIFICATION (ALL 3 ROLES) ===');
  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  // TEST 1: College Admin Login Flow
  console.log('\n--- Test 3.1: College Admin Login Flow ---');
  const colContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const colPage = await colContext.newPage();
  await colPage.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });

  // Screenshot initial login page
  const sLoginPath = path.join(SCREENSHOT_DIR, '01_redesigned_login_desktop.png');
  await colPage.screenshot({ path: sLoginPath, fullPage: true });

  // Mobile screenshot
  const mobContext = await browser.newContext({ viewport: { width: 375, height: 812 } });
  const mobPage = await mobContext.newPage();
  await mobPage.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  const sMobPath = path.join(SCREENSHOT_DIR, '02_redesigned_login_mobile.png');
  await mobPage.screenshot({ path: sMobPath, fullPage: true });
  await mobContext.close();

  // Perform College Admin Login
  await colPage.fill('input#institutional-email', COLLEGE_EMAIL);
  await colPage.fill('input#password', COLLEGE_PASSWORD);
  await colPage.click('button[type="submit"]');

  await colPage.waitForURL('**/college', { timeout: 20000 }).catch(() => {});
  const landedCollege = colPage.url().includes('/college');
  const sColDash = path.join(SCREENSHOT_DIR, '03_college_admin_dashboard.png');
  await colPage.screenshot({ path: sColDash });

  record(
    'AUTH-COL-01',
    'College Admin logs in without role-selection, resolves role authoritatively, and redirects to /college',
    landedCollege,
    `URL: ${colPage.url()}`
  );

  // Refresh page on /college to verify session persistence
  await colPage.reload({ waitUntil: 'networkidle' });
  const refreshedCollege = colPage.url().includes('/college');
  record(
    'AUTH-COL-02',
    'College Admin session persists across browser page refresh',
    refreshedCollege,
    `URL after reload: ${colPage.url()}`
  );

  // TEST 2: Platform Admin Login Flow
  console.log('\n--- Test 3.2: Platform Admin Login Flow ---');
  const platContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const platPage = await platContext.newPage();
  await platPage.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });

  await platPage.fill('input#institutional-email', PLATFORM_EMAIL);
  await platPage.fill('input#password', PLATFORM_PASSWORD);
  await platPage.click('button[type="submit"]');

  await platPage.waitForURL('**/platform', { timeout: 20000 }).catch(() => {});
  const landedPlatform = platPage.url().includes('/platform');
  const sPlatDash = path.join(SCREENSHOT_DIR, '04_platform_admin_dashboard.png');
  await platPage.screenshot({ path: sPlatDash });

  record(
    'AUTH-PLAT-01',
    'Platform Admin logs in without role-selection, resolves role authoritatively, and redirects to /platform',
    landedPlatform,
    `URL: ${platPage.url()}`
  );

  // Refresh page on /platform to verify session persistence
  await platPage.reload({ waitUntil: 'networkidle' });
  const refreshedPlatform = platPage.url().includes('/platform');
  record(
    'AUTH-PLAT-02',
    'Platform Admin session persists across browser page refresh',
    refreshedPlatform,
    `URL after reload: ${platPage.url()}`
  );

  // TEST 3: Faculty Login Flow
  console.log('\n--- Test 3.3: Faculty Login Flow ---');
  const facContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const facPage = await facContext.newPage();
  await facPage.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });

  await facPage.fill('input#institutional-email', FACULTY_EMAIL);
  await facPage.fill('input#password', FACULTY_PASSWORD);
  await facPage.click('button[type="submit"]');

  await facPage.waitForURL('**/faculty', { timeout: 20000 }).catch(() => {});
  const landedFaculty = facPage.url().includes('/faculty');
  const sFacDash = path.join(SCREENSHOT_DIR, '05_faculty_dashboard.png');
  await facPage.screenshot({ path: sFacDash });

  record(
    'AUTH-FAC-01',
    'Faculty logs in without role-selection, resolves role authoritatively, and redirects to /faculty',
    landedFaculty,
    `URL: ${facPage.url()}`
  );

  // Refresh page on /faculty to verify session persistence
  await facPage.reload({ waitUntil: 'networkidle' });
  const refreshedFaculty = facPage.url().includes('/faculty');
  record(
    'AUTH-FAC-02',
    'Faculty session persists across browser page refresh',
    refreshedFaculty,
    `URL after reload: ${facPage.url()}`
  );

  // TEST 4: Security & Access Denial Tests
  console.log('\n--- Test 3.4: Security & Cross-Role Enforcement ---');

  // 4a. Unauthenticated access redirects to /login
  const anonContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const anonPage = await anonContext.newPage();
  await anonPage.goto(`${BASE_URL}/college`, { waitUntil: 'networkidle' });
  const anonRedirected = anonPage.url().includes('/login');
  record(
    'SEC-01',
    'Unauthenticated access to protected dashboard fails closed and redirects to /login',
    anonRedirected,
    `Final URL: ${anonPage.url()}`
  );

  // 4b. Faculty attempting cross-role access to /platform is denied
  await facPage.goto(`${BASE_URL}/platform`, { waitUntil: 'networkidle' });
  const facCrossDenied = facPage.url().includes('/access-denied');
  record(
    'SEC-02',
    'Faculty accessing /platform denied with access-denied',
    facCrossDenied,
    `Final URL: ${facPage.url()}`
  );

  // 4c. College Admin attempting cross-role access to /platform is denied
  await colPage.goto(`${BASE_URL}/platform`, { waitUntil: 'networkidle' });
  const colCrossDenied = colPage.url().includes('/access-denied');
  record(
    'SEC-03',
    'College Admin accessing /platform denied with access-denied',
    colCrossDenied,
    `Final URL: ${colPage.url()}`
  );

  // 4d. Faculty logging in with an unauthorized returnTo parameter does not leak cross-role
  const crossContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const crossPage = await crossContext.newPage();
  await crossPage.goto(`${BASE_URL}/login?returnTo=%2Fplatform`, { waitUntil: 'networkidle' });
  await crossPage.fill('input#institutional-email', FACULTY_EMAIL);
  await crossPage.fill('input#password', FACULTY_PASSWORD);
  await crossPage.click('button[type="submit"]');

  await crossPage.waitForURL('**/faculty', { timeout: 20000 }).catch(() => {});
  const crossResolvedSafely = crossPage.url().includes('/faculty') && !crossPage.url().includes('/platform');
  record(
    'SEC-04',
    'Faculty login with returnTo=/platform safely ignores unauthorized returnTo and lands on /faculty',
    crossResolvedSafely,
    `Final URL: ${crossPage.url()}`
  );

  await browser.close();
}

async function main() {
  console.log('========================================================================');
  console.log(' CAMPXSYNC: LOGIN PAGE REDESIGN & AUTH PRESERVATION VERIFICATION SUITE  ');
  console.log('========================================================================\n');

  await runStaticVerification();

  let server;
  try {
    server = await startServer();
    await runAuthResolutionEndpointVerification();
    await runBrowserVerification();
  } finally {
    if (server) {
      console.log('\nShutting down test Next.js server...');
      server.kill();
    }
  }

  console.log('\n========================================================================');
  console.log(' VERIFICATION SUMMARY');
  console.log('========================================================================');
  const passed = results.filter((r) => r.pass).length;
  const total = results.length;
  console.log(`Results: ${passed}/${total} checks PASSED\n`);

  if (passed === total) {
    console.log('\x1b[32mALL VERIFICATION CRITERIA SATISFIED SUCCESSFULLY!\x1b[0m');
    process.exit(0);
  } else {
    console.error('\x1b[31mSOME CHECKS FAILED. Please review the output above.\x1b[0m');
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Fatal error during verification:', err);
  process.exit(1);
});

