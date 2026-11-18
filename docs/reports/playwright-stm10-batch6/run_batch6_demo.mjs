/**
 * STM-10 batch 6 headed Playwright demo.
 * Usage: node docs/reports/playwright-stm10-batch6/run_batch6_demo.mjs
 * Host MUST be localhost (not 127.0.0.1) so cookies stick.
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
const HOLD_MS = Number(process.env.CAMPX_DEMO_HOLD_MS || 4000);

const SPECS = [
  '01_us004_timetable.spec.mjs',
  '02_us018_outage.spec.mjs',
  '03_us020_logout.spec.mjs',
];

async function portOpen(port) {
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
    stack: {
      ui3000: await portOpen(3000),
      gateway8080: await portOpen(8080),
      student8093: await portOpen(8093),
    },
    results: [],
  };

  if (!String(BASE).includes('localhost')) {
    console.warn('WARNING: Prefer http://localhost:3000 so auth cookies stick (not 127.0.0.1).');
  }

  const browser = await chromium.launch({ headless: false, slowMo: SLOW_MO });

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
        browser,
        note: (msg) => result.notes.push(msg),
        step: (msg) => result.steps.push(msg),
      });
      const shot = path.join(SCREEN, name.replace('.spec.mjs', '.png'));
      await page.screenshot({ path: shot, fullPage: true });
      result.screenshot = shot;
      result.status = 'PASS';
      await page.waitForTimeout(HOLD_MS);
    } catch (err) {
      result.status = 'FAIL';
      result.notes.push(String(err && err.message ? err.message : err));
      const shot = path.join(SCREEN, name.replace('.spec.mjs', '_FAIL.png'));
      try {
        await page.screenshot({ path: shot, fullPage: true });
        result.screenshot = shot;
      } catch (_) {}
    } finally {
      await context.close();
      report.results.push(result);
    }
  }

  await browser.close();
  report.finishedAt = new Date().toISOString();
  const outJson = path.join(OUT, 'demo_report.json');
  fs.writeFileSync(outJson, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
  console.log('Report:', outJson);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
