/**
 * STM-07 batch 2 headed Playwright demo.
 * Usage: node docs/reports/playwright-stm07-batch2/run_batch2_demo.mjs
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

const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const SLOW_MO = Number(process.env.CAMPX_DEMO_SLOWMO || 700);
const HOLD_MS = Number(process.env.CAMPX_DEMO_HOLD_MS || 4000);

const SPECS = [
  '01_prior_features.spec.mjs',
  '02_us008_assign_role.spec.mjs',
  '03_us009_revoke_role.spec.mjs',
  '04_en002_iam_only.spec.mjs',
  '05_en004_retry.spec.mjs',
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
    stack: {
      ui3000: await portOpen(3000),
      gateway8080: await portOpen(8080),
      iam8081: await portOpen(8081),
      student8093: await portOpen(8093),
    },
    results: [],
  };

  if (!report.stack.student8093) {
    report.missingPiece = {
      service: 'student-service',
      port: 8093,
      reason: 'Not started by scripts/dev/start_stack.ps1',
      exactChangeNeeded:
        'Add Start-JavaService for student-service on 8093 in start_stack.ps1 (workdir services/student-services/student-service) and list it in check_stack.ps1. Set CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081.',
    };
  }

  const browser = await chromium.launch({
    headless: false,
    slowMo: SLOW_MO,
  });

  for (const name of SPECS) {
    const specPath = path.join(__dirname, name);
    const mod = await import(pathToFileURL(specPath).href);
    const context = await browser.newContext({
      recordVideo: { dir: VIDEO, size: { width: 1280, height: 720 } },
      viewport: { width: 1280, height: 720 },
    });
    const page = await context.newPage();
    const result = {
      spec: name,
      status: 'PENDING',
      steps: [],
      screenshot: null,
      notes: [],
    };
    try {
      await mod.run({
        page,
        base: BASE,
        outDir: SCREEN,
        stack: report.stack,
        note: (msg) => result.notes.push(msg),
        step: (msg) => result.steps.push(msg),
      });
      result.status = result.notes.some((n) => String(n).startsWith('SKIPPED'))
        ? 'SKIPPED_MISSING_STACK'
        : 'PASS';
    } catch (err) {
      result.status = 'FAIL';
      result.error = err && err.message ? err.message : String(err);
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
  }

  await browser.close();
  report.finishedAt = new Date().toISOString();
  const reportPath = path.join(OUT, 'DEMO_RUN_REPORT.json');
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  console.log('Wrote', reportPath);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
