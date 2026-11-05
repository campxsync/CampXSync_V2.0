import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

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

const BASE_URL = process.env.BASE_URL || 'https://campxsync-web-n1l5m1q00-camp-x-java-integration.vercel.app';
const VERCEL_BYPASS_TOKEN = process.env.VERCEL_AUTOMATION_BYPASS_SECRET || 'fepvsP9ofyO34Ula4rOAQgA8H5nzW1Mr';

const collegePassword = getEnvVar('CAMPX_STAGING_TEST_PASSWORD');
const platformPassword = getEnvVar('CAMPX_STAGING_PLATFORM_ADMIN_TEST_PASSWORD');
const facultyPassword = getEnvVar('CAMPX_STAGING_FACULTY_TEST_PASSWORD');

const ACCOUNTS = [
  {
    role: 'Platform Admin',
    email: 'platformadmin.campx@gmail.com',
    password: platformPassword,
    expectedPath: '/platform',
    expectedText: 'Platform',
  },
  {
    role: 'College Admin',
    email: 'campxsync@gmail.com',
    password: collegePassword,
    expectedPath: '/college',
    expectedText: 'College',
  },
  {
    role: 'Faculty',
    email: 'faculty.campx@gmail.com',
    password: facultyPassword,
    expectedPath: '/faculty',
    expectedText: 'Faculty',
  },
];

let vercelJwt = '';
async function getVercelBypass() {
  const res = await fetch(`${BASE_URL}/login`, {
    headers: {
      'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN,
      'x-vercel-set-bypass-cookie': 'true',
    },
    redirect: 'manual',
  });
  const cookies = res.headers.getSetCookie ? res.headers.getSetCookie() : [res.headers.get('set-cookie')];
  cookies.filter(Boolean).forEach((c) => {
    if (c.includes('_vercel_jwt=')) {
      const m = c.match(/_vercel_jwt=([^;]+)/);
      if (m) vercelJwt = m[1];
    }
  });
}

async function run() {
  console.log('========================================================================');
  console.log(' CAMPXSYNC PREVIEW VERIFICATION: 3-ROLE LIVE BROWSER SUITE              ');
  console.log('========================================================================');
  console.log(`Target URL: ${BASE_URL}`);
  console.log(`Time: ${new Date().toISOString()}\n`);

  await getVercelBypass();
  console.log('Vercel protection bypass cookie acquired:', vercelJwt ? 'YES' : 'NO');

  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  const urlObj = new URL(BASE_URL);

  for (const acc of ACCOUNTS) {
    console.log(`\n--- Testing ${acc.role} (${acc.email}) ---`);
    if (!acc.password) {
      console.log(`[SKIPPED] Missing password for ${acc.role}`);
      continue;
    }

    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      extraHTTPHeaders: { 'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN },
    });

    if (vercelJwt) {
      await context.addCookies([
        { name: '_vercel_jwt', value: vercelJwt, domain: urlObj.hostname, path: '/' },
      ]);
    }

    const page = await context.newPage();

    // 1. Go to login
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
    console.log('1. On login page');

    // 2. Fill form
    await page.fill('input[type="email"]', acc.email);
    await page.fill('input[type="password"]', acc.password);
    console.log('2. Credentials entered');

    // 3. Click submit
    await page.click('button[type="submit"]');
    console.log('3. Submit clicked, awaiting navigation...');

    // 4. Wait for expected URL
    try {
      await page.waitForURL(`**${acc.expectedPath}**`, { timeout: 20000 });
      console.log(`4. Reached expected URL: ${page.url()}`);
      
      // Check content
      await page.waitForSelector('main', { timeout: 10000 });
      const mainText = await page.locator('main').innerText();
      console.log(`5. Main content loaded (${mainText.length} chars)`);

      // 5. Test session persistence on reload
      console.log('6. Testing session persistence on page refresh...');
      await page.reload({ waitUntil: 'networkidle' });
      const currentUrl = page.url();
      if (currentUrl.includes(acc.expectedPath)) {
        console.log(`7. Session persisted after refresh: ${currentUrl}`);
        console.log(`[PASS] ${acc.role} verified end-to-end!`);
      } else {
        console.error(`[FAIL] Session lost on refresh, redirected to: ${currentUrl}`);
      }
    } catch (err) {
      console.error(`[FAIL] ${acc.role} failed to reach ${acc.expectedPath}:`, err.message);
      console.log('Current URL at failure:', page.url());
      try {
        const bodyText = await page.locator('body').innerText();
        console.log('Page text snippet:', bodyText.substring(0, 300));
      } catch {}
    }

    await context.close();
  }

  // Negative test: unauthenticated access to /college
  console.log('\n--- Negative Test 1: Unauthenticated request to /college ---');
  const anonContext = await browser.newContext({
    extraHTTPHeaders: { 'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN },
  });
  if (vercelJwt) {
    await anonContext.addCookies([
      { name: '_vercel_jwt', value: vercelJwt, domain: urlObj.hostname, path: '/' },
    ]);
  }
  const anonPage = await anonContext.newPage();
  await anonPage.goto(`${BASE_URL}/college`, { waitUntil: 'networkidle' });
  console.log('Unauthenticated /college landed on:', anonPage.url());
  if (anonPage.url().includes('/login')) {
    console.log('[PASS] Unauthenticated access cleanly redirected to /login');
  } else {
    console.error('[FAIL] Unauthenticated access did not redirect to /login');
  }
  await anonContext.close();

  // Negative test: access denied reason display
  console.log('\n--- Negative Test 2: Access denied for reason=no_profile ---');
  const deniedContext = await browser.newContext({
    extraHTTPHeaders: { 'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN },
  });
  if (vercelJwt) {
    await deniedContext.addCookies([
      { name: '_vercel_jwt', value: vercelJwt, domain: urlObj.hostname, path: '/' },
    ]);
  }
  const deniedPage = await deniedContext.newPage();
  await deniedPage.goto(`${BASE_URL}/access-denied?reason=no_profile`, { waitUntil: 'networkidle' });
  const text = await deniedPage.locator('body').innerText();
  if (text.includes('Institutional Profile Not Found')) {
    console.log('[PASS] Genuinely missing profile produces Institutional Profile Not Found UI');
  } else {
    console.error('[FAIL] Missing expected title on access-denied page');
  }

  console.log('\n--- Negative Test 3: Access denied for reason=db_error ---');
  await deniedPage.goto(`${BASE_URL}/access-denied?reason=db_error`, { waitUntil: 'networkidle' });
  const dbErrText = await deniedPage.locator('body').innerText();
  if (dbErrText.includes('Institutional Database Service Unavailable')) {
    console.log('[PASS] DB error produces Institutional Database Service Unavailable UI');
  } else {
    console.error('[FAIL] Missing expected db_error title on access-denied page');
  }
  await deniedContext.close();

  await browser.close();
  console.log('\n========================================================================');
  console.log(' ALL VERIFICATIONS COMPLETE                                            ');
  console.log('========================================================================');
}

run().catch((e) => {
  console.error('Test runner fatal error:', e);
  process.exit(1);
});

