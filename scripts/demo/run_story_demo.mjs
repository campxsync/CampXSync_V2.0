// scripts/demo/run_story_demo.mjs
// Generic Headed Playwright Demo Runner for CampXSync Stories
// Usage: node scripts/demo/run_story_demo.mjs <STORY-ID>

import { chromium } from '../../docs/stm-02/e2e/node_modules/playwright-core/index.mjs';
import pg from '../../frontend/node_modules/pg/lib/index.js';
const { Client } = pg;
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DB_CONFIG = {
  host: 'aws-0-ap-south-1.pooler.supabase.com',
  port: 6543,
  user: 'postgres.azulbaxszsqxymqmlwfe',
  password: 'Platform.Capmx@161221',
  database: 'postgres',
  ssl: { rejectUnauthorized: false }
};

const storyId = process.argv[2];
if (!storyId) {
  console.error('Usage: node scripts/demo/run_story_demo.mjs <STORY-ID>');
  process.exit(1);
}

const configPath = path.resolve(__dirname, `../../docs/stories/${storyId}.demo.json`);
if (!fs.existsSync(configPath)) {
  console.error(`Demo specification not found: ${configPath}`);
  process.exit(1);
}

const spec = JSON.parse(fs.readFileSync(configPath, 'utf-8'));
const baseOutputDir = path.resolve(__dirname, `../../docs/demos/${storyId}`);
const screenshotsDir = path.join(baseOutputDir, 'screenshots');
const rawVideoDir = path.join(baseOutputDir, 'raw_video');

fs.mkdirSync(screenshotsDir, { recursive: true });
fs.mkdirSync(rawVideoDir, { recursive: true });

async function queryDatabase(sql, params = []) {
  const client = new Client(DB_CONFIG);
  await client.connect();
  try {
    const res = await client.query(sql, params);
    return res.rows;
  } finally {
    await client.end();
  }
}

async function showOverlay(page, stepNum, title, description, resultStatus = 'PASS', highlightSelector = null) {
  await page.evaluate(({ stepNum, title, description, resultStatus, highlightSelector }) => {
    let existing = document.getElementById('demo-caption-overlay');
    if (!existing) {
      existing = document.createElement('div');
      existing.id = 'demo-caption-overlay';
      document.body.appendChild(existing);
    }
    const borderColor = resultStatus === 'PASS' ? '#10B981' : (resultStatus === 'FAIL' ? '#EF4444' : '#3B82F6');
    const badgeBg = resultStatus === 'PASS' ? '#065F46' : (resultStatus === 'FAIL' ? '#991B1B' : '#1E3A8A');
    const badgeText = resultStatus === 'PASS' ? '#34D399' : (resultStatus === 'FAIL' ? '#F87171' : '#93C5FD');

    existing.style.cssText = `
      position: fixed;
      bottom: 24px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(15, 23, 42, 0.95);
      color: white;
      padding: 16px 24px;
      border-radius: 12px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
      border-left: 6px solid ${borderColor};
      z-index: 999999;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      max-width: 820px;
      width: 90%;
      pointer-events: none;
      backdrop-filter: blur(8px);
    `;

    existing.innerHTML = `
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span style="font-size: 11px; font-weight: 700; text-transform: uppercase; background: #334155; padding: 2px 8px; border-radius: 4px; letter-spacing: 0.05em;">STEP ${stepNum}</span>
          <span style="font-size: 13px; font-weight: 600; color: #F8FAFC;">${title}</span>
        </div>
        <span style="font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 4px; background: ${badgeBg}; color: ${badgeText};">${resultStatus}</span>
      </div>
      <div style="font-size: 11px; line-height: 1.4; color: #94A3B8;">${description}</div>
    `;

    document.querySelectorAll('.demo-highlight-box').forEach(el => {
      el.style.outline = '';
      el.style.outlineOffset = '';
      el.style.boxShadow = '';
      el.classList.remove('demo-highlight-box');
    });

    if (highlightSelector) {
      const target = document.querySelector(highlightSelector);
      if (target) {
        target.style.outline = '3px solid #F59E0B';
        target.style.outlineOffset = '2px';
        target.style.boxShadow = '0 0 15px rgba(245, 158, 11, 0.7)';
        target.classList.add('demo-highlight-box');
        target.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }
  }, { stepNum, title, description, resultStatus, highlightSelector });
  await page.waitForTimeout(600);
}

async function run() {
  console.log(`=== Starting Generic Visible Headed Demo: ${storyId} ===`);
  const results = [];
  const timings = {};
  const recordsCreated = [];

  // Timestamp tag for demo data
  const DEMO_TAG = `DEMO-${storyId}-${Date.now().toString().slice(-6)}`;
  console.log(`Demo Data Tag: ${DEMO_TAG}`);

  // 1. Run Pre-Seed SQL if present
  if (spec.seedSql && Array.isArray(spec.seedSql)) {
    console.log('Running pre-demo database seed...');
    for (const item of spec.seedSql) {
      const parsedQuery = item.query.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
      await queryDatabase(parsedQuery, item.params || []);
    }
  }

  const browser = await chromium.launch({
    headless: false,
    slowMo: spec.slowMo || 400,
  });

  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
    recordVideo: {
      dir: rawVideoDir,
      size: { width: 1280, height: 720 }
    }
  });

  const page = await context.newPage();

  try {
    let stepIndex = 1;

    for (const step of spec.steps) {
      console.log(`\n--- Step ${stepIndex}: ${step.title} ---`);
      const startTime = Date.now();
      let status = 'PASS';
      let errorMsg = null;

      try {
        switch (step.action) {
          case 'goto': {
            const url = step.url.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            const navStart = Date.now();
            await page.goto(url, { waitUntil: 'domcontentloaded' });
            timings[step.title] = Date.now() - navStart;
            break;
          }
          case 'click': {
            const selector = step.selector.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            await page.waitForSelector(selector, { timeout: 10000 });
            await page.click(selector);
            break;
          }
          case 'fill': {
            const selector = step.selector.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            const value = step.value.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            await page.waitForSelector(selector, { timeout: 10000 });
            await page.fill(selector, value);
            break;
          }
          case 'select': {
            const selector = step.selector.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            await page.waitForSelector(selector, { timeout: 10000 });
            await page.selectOption(selector, step.value);
            break;
          }
          case 'reload': {
            const navStart = Date.now();
            await page.reload({ waitUntil: 'domcontentloaded' });
            timings[`${step.title} (Reload)`] = Date.now() - navStart;
            break;
          }
          case 'wait_for': {
            const selector = step.selector.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            await page.waitForSelector(selector, { timeout: step.timeout || 10000 });
            break;
          }
          case 'api_call': {
            const url = step.url.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            const headers = step.headers || {};
            const res = await page.request[step.method.toLowerCase()](url, {
              headers,
              data: step.body || undefined
            });
            const actualStatus = res.status();
            if (step.expectedStatus && actualStatus !== step.expectedStatus) {
              throw new Error(`Expected HTTP ${step.expectedStatus} but got ${actualStatus}`);
            }
            break;
          }
          case 'db_check': {
            const query = step.query.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            const rows = await queryDatabase(query, step.params || []);
            if (step.assertCount !== undefined && rows.length !== step.assertCount) {
              throw new Error(`Expected ${step.assertCount} rows, got ${rows.length}`);
            }
            if (step.minCount !== undefined && rows.length < step.minCount) {
              throw new Error(`Expected at least ${step.minCount} rows, got ${rows.length}`);
            }
            break;
          }
          case 'shell': {
            const { execSync } = await import('child_process');
            const cmd = step.command.replace(/{{DEMO_TAG}}/g, DEMO_TAG);
            console.log(`Running shell: ${cmd}`);
            execSync(cmd, {
              cwd: step.cwd || path.resolve(__dirname, '../..'),
              stdio: 'inherit',
              shell: process.platform === 'win32' ? 'powershell.exe' : '/bin/bash',
              timeout: step.timeoutMs || 120000
            });
            if (step.waitMs) {
              await page.waitForTimeout(step.waitMs);
            }
            break;
          }
          default:
            console.warn(`Unknown action: ${step.action}`);
        }
      } catch (err) {
        status = 'FAIL';
        errorMsg = err.message;
        console.error(`Step ${stepIndex} Error:`, err.message);
      }

      const highlight = step.highlight ? step.highlight.replace(/{{DEMO_TAG}}/g, DEMO_TAG) : null;
      await showOverlay(page, stepIndex, step.title, step.description, status, highlight);

      const shotFileName = `${String(stepIndex).padStart(2, '0')}_${step.title.toLowerCase().replace(/[^a-z0-9]/g, '_')}.png`;
      const shotPath = path.join(screenshotsDir, shotFileName);
      await page.screenshot({ path: shotPath });

      results.push({
        step: stepIndex,
        title: step.title,
        status,
        description: step.description,
        screenshot: `screenshots/${shotFileName}`,
        error: errorMsg
      });

      if (status === 'FAIL') {
        throw new Error(`Step ${stepIndex} failed: ${errorMsg}`);
      }

      stepIndex++;
    }

    console.log(`\n=== All ${results.length} Demo Steps Completed Successfully ===`);

  } finally {
    await context.close();
    await browser.close();

    // Copy recorded video to standard location
    try {
      const videoFiles = fs.readdirSync(rawVideoDir).filter(f => f.endsWith('.webm'));
      if (videoFiles.length > 0) {
        const latestVideo = path.join(rawVideoDir, videoFiles[videoFiles.length - 1]);
        const targetVideo = path.join(baseOutputDir, 'demo.webm');
        fs.copyFileSync(latestVideo, targetVideo);
        console.log(`[+] Video copied to: ${targetVideo}`);
      }
    } catch (e) {
      console.warn('Video copy error:', e.message);
    }

    // Generate DEMO.md
    generateDemoReport(storyId, spec, results, timings, DEMO_TAG, baseOutputDir);
  }
}

function generateDemoReport(id, spec, results, timings, tag, outDir) {
  let md = `# DEMO EVIDENCE REPORT: ${id}\n\n`;
  md += `**Story Title:** ${spec.title}\n`;
  md += `**Execution Timestamp:** ${new Date().toISOString()}\n`;
  md += `**Environment:** Dev Supabase Database (\`azulbaxszsqxymqmlwfe\`), Local Stack (API Gateway: 8080, Next.js: 3000)\n\n`;
  md += `---\n\n`;
  md += `## 1. Acceptance Criteria Table\n\n`;
  md += `| # | Checkpoint / Criterion | Status | Evidence |\n`;
  md += `|---|---|:---:|---|\n`;

  for (const r of results) {
    md += `| ${r.step} | **${r.title}**<br>${r.description} | **${r.status}** | [\`${r.screenshot}\`](file://${path.join(outDir, r.screenshot).replace(/\\/g, '/')} ) |\n`;
  }

  md += `\n---\n\n`;
  md += `## 2. Test Accounts & Personas\n\n`;
  if (spec.personas) {
    for (const p of spec.personas) {
      md += `- **${p.name}:** \`${p.email}\` (Role: \`${p.role}\`)\n`;
    }
  }

  md += `\n---\n\n`;
  md += `## 3. Demo Records Created\n\n`;
  md += `- **Tag:** \`${tag}\`\n`;

  md += `\n---\n\n`;
  md += `## 4. Performance & Timings\n\n`;
  for (const [k, v] of Object.entries(timings)) {
    md += `- **${k}:** ${v} ms (Target <= 1500 ms: ${v <= 1500 ? '**PASS**' : '**WARN**'})\n`;
  }

  md += `\n---\n\n`;
  md += `## 5. Artifact Links\n\n`;
  md += `- **Video Recording:** [demo.webm](file://${path.join(outDir, 'demo.webm').replace(/\\/g, '/')})\n`;
  md += `- **Screenshots:** [screenshots/](file://${path.join(outDir, 'screenshots').replace(/\\/g, '/')})\n`;

  fs.writeFileSync(path.join(outDir, 'DEMO.md'), md, 'utf-8');
  console.log(`[+] Generated DEMO.md at: ${path.join(outDir, 'DEMO.md')}`);
}

run().catch(err => {
  console.error('Demo Runner Fatal Error:', err.message);
  process.exit(1);
});
