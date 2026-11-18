/**
 * Closing Playwright demo runner for STM-07 and STM-10.
 * Runs all 7 required demos with slowMo 700ms, headed, existing test accounts.
 *
 * Usage:
 *   node docs/reports/playwright-final-stm07-stm10/run_final_demos.mjs
 */
import { chromium } from '../../../docs/stm-02/e2e/node_modules/playwright-core/index.mjs';
import fs from 'fs';
import path from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const OUT = __dirname;
const SCREEN = path.join(OUT, 'screenshots');
const VIDEO = path.join(OUT, 'video');
fs.mkdirSync(SCREEN, { recursive: true });
fs.mkdirSync(VIDEO, { recursive: true });

const BASE = process.env.CAMPX_UI_BASE || 'http://localhost:3000';
const SLOW_MO = Number(process.env.CAMPX_DEMO_SLOWMO || 700);
const HOLD_MS = Number(process.env.CAMPX_DEMO_HOLD_MS || 3000);

const SPECS = [
  '01_preferences.spec.mjs',
  '02_outage.spec.mjs',
  '03_logout.spec.mjs',
  '04_timetable.spec.mjs',
  '05_activities.spec.mjs',
  '06_linked_views.spec.mjs',
  '07_registrar.spec.mjs',
];

async function portOpen(port) {
  try {
    const res = await fetch(`http://127.0.0.1:${port}/health`).catch(() => null);
    if (res) return true;
  } catch (_) {}
  try {
    const net = await import('net');
    return await new Promise((resolve) => {
      const s = net.createConnection({ host: '127.0.0.1', port }, () => {
        s.end();
        resolve(true);
      });
      s.on('error', () => resolve(false));
      setTimeout(() => resolve(false), 800);
    });
  } catch (_) {
    return false;
  }
}

async function main() {
  const report = {
    startedAt: new Date().toISOString(),
    base: BASE,
    slowMo: SLOW_MO,
    projectRef: 'azulbaxszsqxymqmlwfe',
    stack: {
      ui3000: await portOpen(3000),
      gateway8080: await portOpen(8080),
      iam8081: await portOpen(8081),
      student8093: await portOpen(8093),
      lifecycle8096: await portOpen(8096),
      document8098: await portOpen(8098),
    },
    results: [],
  };

  console.log('====================================================');
  console.log(' CampXSync STM-07 / STM-10 Final Verification Demos  ');
  console.log('====================================================');
  console.log(`Host: ${BASE} | slowMo: ${SLOW_MO}ms | Dev DB: azulbaxszsqxymqmlwfe\n`);

  let browser;
  try {
    browser = await chromium.launch({
      headless: process.env.CAMPX_DEMO_HEADLESS === 'true',
      slowMo: SLOW_MO,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
    });
  } catch (e) {
    console.log('Falling back to headless browser due to launch exception:', e.message);
    browser = await chromium.launch({
      headless: true,
      slowMo: 100,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
    });
  }

  for (const name of SPECS) {
    const specPath = path.join(__dirname, name);
    const mod = await import(pathToFileURL(specPath).href);
    const context = await browser.newContext({
      recordVideo: { dir: VIDEO, size: { width: 1280, height: 720 } },
      viewport: { width: 1280, height: 720 },
    });
    const page = await context.newPage();
    const result = { spec: name, status: 'PENDING', steps: [], screenshot: null, notes: [] };

    try {
      await mod.run({
        page,
        base: BASE,
        outDir: SCREEN,
        stack: report.stack,
        note: (msg) => result.notes.push(msg),
        step: (msg) => result.steps.push(msg),
      });
      result.status = 'PASS';
    } catch (err) {
      result.status = 'FAIL';
      result.error = err && err.message ? err.message : String(err);
      result.notes.push(`Error: ${result.error}`);
    }

    const shot = path.join(SCREEN, name.replace('.spec.mjs', '') + '.png');
    try {
      await page.screenshot({ path: shot, fullPage: true });
      result.screenshot = shot;
    } catch (_) {}

    await page.waitForTimeout(HOLD_MS);
    await context.close();
    report.results.push(result);
    console.log(`[${result.status}] ${name}`);
    for (const st of result.steps) {
      console.log(`   -> ${st}`);
    }
  }

  await browser.close();
  report.finishedAt = new Date().toISOString();
  const summaryPath = path.join(OUT, 'FINAL_DEMO_RUN_REPORT.json');
  fs.writeFileSync(summaryPath, JSON.stringify(report, null, 2));
  console.log(`\nDemo report saved: ${summaryPath}`);

  const passCount = report.results.filter((r) => r.status === 'PASS').length;
  console.log(`Final Demo Score: ${passCount}/${report.results.length} PASSED`);
}

main().catch((err) => {
  console.error('Fatal runner error:', err);
  process.exit(1);
});
