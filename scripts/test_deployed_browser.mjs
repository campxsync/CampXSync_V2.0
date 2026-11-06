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

const DEPLOYED_BASE = 'https://campxsync-web-dev.vercel.app';
const EMAIL = 'campxsync@gmail.com';
const PASSWORD = getEnvVar('CAMPX_STAGING_TEST_PASSWORD');

async function testBrowser() {
  console.log('=== TESTING REAL BROWSER LOGIN ON DEPLOYED VERCEL ===');
  console.log(`URL: ${DEPLOYED_BASE}/login`);
  console.log(`Email: ${EMAIL}\n`);

  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });

  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  // Listen to console logs and network responses from the page
  page.on('console', msg => console.log(`[Browser Console ${msg.type()}]: ${msg.text()}`));
  page.on('response', async res => {
    if (res.url().includes('/api/auth/resolve')) {
      const text = await res.text().catch(() => '');
      console.log(`[Network Response] /api/auth/resolve (${res.status()}): ${text}`);
    }
  });

  await page.goto(`${DEPLOYED_BASE}/login`, { waitUntil: 'networkidle' });
  console.log('Landed on login page. Filling credentials...');

  await page.fill('input#institutional-email', EMAIL);
  await page.fill('input#password', PASSWORD);
  console.log('Clicking Sign In to Workspace...');
  await page.click('button[type="submit"]');

  // Wait up to 15 seconds to see where the page navigates
  await page.waitForTimeout(5000);
  console.log(`Current page URL after 5s: ${page.url()}`);

  await page.waitForTimeout(5000);
  console.log(`Current page URL after 10s: ${page.url()}`);

  const pageTitle = await page.title().catch(() => '');
  console.log(`Page title: ${pageTitle}`);

  const bodyText = await page.locator('body').innerText().catch(() => '');
  console.log(`Body text snippet:\n${bodyText.slice(0, 300)}...`);

  await browser.close();
}

testBrowser().catch(console.error);

