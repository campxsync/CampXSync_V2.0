import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
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
const seed = JSON.parse(
  fs.readFileSync(path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'), 'utf8')
);

const browser = await chromium.launch({ headless: false, slowMo: 50 });
const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });
await page.goto('http://127.0.0.1:3000/login?returnTo=/platform/configuration/versions');
await page.getByLabel(/Institutional Email/i).fill(seed.r01b.email);
await page.getByLabel(/^Password$/i).fill(seed.r01b.password);
await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
await page.waitForURL((u) => (u.pathname || '').startsWith('/platform'), { timeout: 60000 });
await page.goto('http://127.0.0.1:3000/platform/configuration/versions');
await page.waitForTimeout(2000);
const shot = path.join(root, 'docs/iadm/iadm-02/evidence/s2_versions_ver002.png');
await page.screenshot({ path: shot, fullPage: true });
const text = await page.locator('body').innerText();
console.log(JSON.stringify({ shot, hasVer002: text.includes('VER-002'), hasVer001: text.includes('VER-001') }));
await browser.close();
