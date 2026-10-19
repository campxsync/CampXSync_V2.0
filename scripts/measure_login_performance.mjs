// scripts/measure_login_performance.mjs
// Precise instrumented measurement of CampXSync login flow across Platform Admin, College Admin, and Faculty roles

import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { performance } from 'node:perf_hooks';

const require = createRequire(path.resolve('frontend/package.json'));
const { chromium } = require('playwright');

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

const BASE_URL = process.env.BASE_URL || 'https://frontend-murex-one-58.vercel.app';
const VERCEL_BYPASS_TOKEN = process.env.VERCEL_AUTOMATION_BYPASS_SECRET || '';

const collegePassword = getEnvVar('CAMPX_STAGING_TEST_PASSWORD');
const platformPassword = getEnvVar('CAMPX_STAGING_PLATFORM_ADMIN_TEST_PASSWORD');
const facultyPassword = getEnvVar('CAMPX_STAGING_FACULTY_TEST_PASSWORD');

const ACCOUNTS = [
  {
    role: 'College Admin',
    email: 'campxsync@gmail.com',
    password: collegePassword,
    expectedPath: '/college',
  },
  {
    role: 'Platform Admin',
    email: 'platformadmin.campx@gmail.com',
    password: platformPassword,
    expectedPath: '/platform',
  },
  {
    role: 'Faculty',
    email: 'faculty.campx@gmail.com',
    password: facultyPassword,
    expectedPath: '/faculty',
  },
];

let vercelJwtCookieValue = '';

async function ensureVercelBypassCookie() {
  if (!VERCEL_BYPASS_TOKEN || vercelJwtCookieValue) return;
  try {
    const res = await fetch(`${BASE_URL}/login`, {
      headers: {
        'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN,
        'x-vercel-set-bypass-cookie': 'true',
      },
      redirect: 'manual',
    });
    const rawCookies = res.headers.getSetCookie ? res.headers.getSetCookie() : [res.headers.get('set-cookie')];
    rawCookies.filter(Boolean).forEach((c) => {
      if (c && c.includes('_vercel_jwt=')) {
        const m = c.match(/_vercel_jwt=([^;]+)/);
        if (m) vercelJwtCookieValue = m[1];
      }
    });
  } catch {}
}

async function measureLogin(browser, account, attemptIndex, isCold) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    extraHTTPHeaders: {
      'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN,
    },
  });

  if (vercelJwtCookieValue) {
    try {
      const urlObj = new URL(BASE_URL);
      await context.addCookies([
        {
          name: '_vercel_jwt',
          value: vercelJwtCookieValue,
          domain: urlObj.hostname,
          path: '/',
        },
      ]);
    } catch {}
  }

  const page = await context.newPage();

  const networkLog = [];
  page.on('request', (req) => {
    req._perfStartTime = performance.now();
  });

  page.on('response', (res) => {
    const req = res.request();
    const duration = req._perfStartTime ? performance.now() - req._perfStartTime : null;
    networkLog.push({
      url: res.url(),
      method: req.method(),
      status: res.status(),
      duration: duration ? Math.round(duration) : null,
      resourceType: req.resourceType(),
    });
  });

  // Navigate to login page first
  const tNavStart = performance.now();
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  const loginPageLoadDuration = Math.round(performance.now() - tNavStart);

  // Fill in form
  await page.fill('input[type="email"]', account.email);
  await page.fill('input[type="password"]', account.password);

  // Now measure the actual login interaction
  let tClick = 0;
  let tFirstUiResponse = 0;
  let tAuthRequestStart = 0;
  let tAuthRequestEnd = 0;
  let authDuration = 0;
  let authStatus = null;
  let tRedirectStart = 0;
  let tNavigationComplete = 0;
  let tDashboardUsable = 0;

  // Listen to network specifically for auth
  const authPromise = new Promise((resolve) => {
    const onReq = (req) => {
      if (req.url().includes('/auth/v1/token')) {
        tAuthRequestStart = performance.now();
      }
    };
    const onRes = (res) => {
      if (res.url().includes('/auth/v1/token')) {
        tAuthRequestEnd = performance.now();
        authDuration = Math.round(tAuthRequestEnd - tAuthRequestStart);
        authStatus = res.status();
        page.off('request', onReq);
        page.off('response', onRes);
        resolve();
      }
    };
    page.on('request', onReq);
    page.on('response', onRes);
  });

  tClick = performance.now();

  // Click login button
  const submitButton = page.locator('button[type="submit"]');
  await submitButton.click();

  // 1. Time to first UI response (button disables or shows spinner / "Signing in...")
  try {
    await page.waitForFunction(
      () => {
        const btn = document.querySelector('button[type="submit"]');
        return btn && (btn.disabled || btn.textContent.includes('Signing in') || btn.querySelector('.animate-spin'));
      },
      { timeout: 3000 }
    );
    tFirstUiResponse = performance.now();
  } catch {
    tFirstUiResponse = performance.now();
  }

  // 2. Wait for Supabase Auth request to complete
  await authPromise;
  tRedirectStart = performance.now();

  // 3 & 4. Wait for URL change away from /login to the target role dashboard
  await page.waitForURL(`**${account.expectedPath}**`, { timeout: 25000 });
  tNavigationComplete = performance.now();

  // 5 & 6. Wait for dashboard main container and network idle
  await page.waitForSelector('main', { timeout: 10000 });
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  tDashboardUsable = performance.now();

  const totalTime = Math.round(tDashboardUsable - tClick);
  const clickToUi = Math.round(tFirstUiResponse - tClick);
  const authTime = authDuration;
  const authSuccessToNavComplete = Math.round(tNavigationComplete - tAuthRequestEnd);
  const dashboardRender = Math.round(tDashboardUsable - tNavigationComplete);

  // Filter network requests during the login transition
  const transitionRequests = networkLog.filter((n) => {
    const isDocOrApi = n.resourceType === 'document' || n.resourceType === 'fetch' || n.resourceType === 'xhr';
    return isDocOrApi;
  });

  await context.close();

  return {
    role: account.role,
    attempt: attemptIndex,
    isCold,
    totalTimeMs: totalTime,
    clickToUiMs: clickToUi,
    authRequestMs: authTime,
    authStatus,
    authToNavCompleteMs: authSuccessToNavComplete,
    dashboardRenderMs: dashboardRender,
    loginPageLoadMs: loginPageLoadDuration,
    transitionRequests,
  };
}

async function main() {
  console.log('========================================================================');
  console.log(' CAMPXSYNC LOGIN PERFORMANCE MEASUREMENT SUITE                          ');
  console.log('========================================================================');
  console.log(`Target: ${BASE_URL}`);
  console.log(`Node: ${process.version}`);
  console.log(`Time: ${new Date().toISOString()}\n`);

  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  await ensureVercelBypassCookie();

  const allMeasurements = [];

  for (const account of ACCOUNTS) {
    console.log(`\n------------------------------------------------------------------------`);
    console.log(`Testing Role: ${account.role} (${account.email}) -> Target: ${account.expectedPath}`);
    console.log(`------------------------------------------------------------------------`);

    for (let attempt = 1; attempt <= 10; attempt++) {
      const isCold = attempt === 1;
      const label = isCold ? '[Attempt 1 - Cold Context]' : `[Attempt ${attempt} - Warm Repeat]`;
      console.log(`  ${label} Running...`);
      try {
        const r = await measureLogin(browser, account, attempt, isCold);
        console.log(`    Total: ${r.totalTimeMs}ms | UI: ${r.clickToUiMs}ms | Auth: ${r.authRequestMs}ms | SSR/Nav: ${r.authToNavCompleteMs}ms | Dashboard: ${r.dashboardRenderMs}ms`);
        allMeasurements.push(r);
      } catch (err) {
        console.error(`    Attempt ${attempt} FAILED:`, err.message);
        allMeasurements.push({
          role: account.role,
          attempt,
          isCold,
          totalTimeMs: null,
          error: err.message,
        });
      }
      await new Promise((r) => setTimeout(r, 1000));
    }
  }

  await browser.close();

  function calculatePercentile(sortedArr, p) {
    if (sortedArr.length === 0) return 0;
    if (sortedArr.length === 1) return sortedArr[0];
    const rank = (p / 100) * (sortedArr.length - 1);
    const lowerIndex = Math.floor(rank);
    const upperIndex = Math.ceil(rank);
    const fraction = rank - lowerIndex;
    return Math.round(sortedArr[lowerIndex] + fraction * (sortedArr[upperIndex] - sortedArr[lowerIndex]));
  }

  // Print Summary Table
  console.log('\n========================================================================');
  console.log(' MEASUREMENT RESULTS SUMMARY (ALL 10 RUNS PER ROLE)');
  console.log('========================================================================');
  console.table(
    allMeasurements.map((m) => ({
      Role: m.role,
      Run: m.attempt === 1 ? 'Cold (1)' : `Warm (${m.attempt})`,
      'Total (ms)': m.totalTimeMs,
      'Click->UI (ms)': m.clickToUiMs,
      'Supabase Auth (ms)': m.authRequestMs,
      'Auth->Nav (ms)': m.authToNavCompleteMs,
      'Dashboard Render (ms)': m.dashboardRenderMs,
    }))
  );

  console.log('\n========================================================================');
  console.log(' STATISTICAL AGGREGATION & BASELINE COMPARISON');
  console.log(' Percentile Method: Linear interpolation (NIST / Excel PERCENTILE.INC)');
  console.log('========================================================================');

  const oldBaselines = {
    'College Admin': 14878,
    'Platform Admin': 7817,
    'Faculty': 12595,
  };

  const stats = [];
  for (const account of ACCOUNTS) {
    const roleRuns = allMeasurements.filter((m) => m.role === account.role && typeof m.totalTimeMs === 'number');
    const timings = roleRuns.map((m) => m.totalTimeMs);
    const sortedTimings = [...timings].sort((a, b) => a - b);
    const coldRun = roleRuns.find((m) => m.attempt === 1)?.totalTimeMs || null;
    const p50 = calculatePercentile(sortedTimings, 50);
    const p95 = calculatePercentile(sortedTimings, 95);
    const worstCase = sortedTimings.length > 0 ? sortedTimings[sortedTimings.length - 1] : null;
    const oldMedian = oldBaselines[account.role] || null;

    stats.push({
      Role: account.role,
      'Completed Runs': `${roleRuns.length}/10`,
      'Cold Run (ms)': coldRun,
      'p50 Median (ms)': p50,
      'p95 (ms)': p95,
      'Worst-Case (ms)': worstCase,
      'Old Preview Median': `${oldMedian}ms`,
      'p50 Target (<=2000ms)': p50 <= 2000 ? 'MET' : 'NOT MET',
      'p95 Target (<=3000ms)': p95 <= 3000 ? 'MET' : 'NOT MET',
    });
  }
  console.table(stats);

  // Detailed Request Analysis from Attempt 1 of each role
  console.log('\n========================================================================');
  console.log(' KEY DOCUMENT & API REQUESTS OBSERVED DURING LOGIN TRANSITION');
  console.log('========================================================================');
  for (const m of allMeasurements) {
    if (m.attempt === 1) {
      console.log(`\n--- Role: ${m.role} (Cold Attempt) ---`);
      for (const req of m.transitionRequests) {
        // Redact full query params or tokens if any
        const parsedUrl = new URL(req.url);
        const cleanUrl = parsedUrl.origin + parsedUrl.pathname;
        console.log(`  ${req.method.padEnd(5)} ${cleanUrl.padEnd(55)} [${req.status}] ${req.duration}ms (${req.resourceType})`);
      }
    }
  }

  // Save results to scratch/timing_baseline.json
  const scratchDir = path.resolve('scratch');
  if (!fs.existsSync(scratchDir)) {
    fs.mkdirSync(scratchDir, { recursive: true });
  }
  fs.writeFileSync(path.join(scratchDir, 'timing_baseline.json'), JSON.stringify(allMeasurements, null, 2));
  console.log('\nSaved baseline timing data to scratch/timing_baseline.json');
}

main().catch((err) => {
  console.error('Fatal measurement error:', err);
  process.exit(1);
});
