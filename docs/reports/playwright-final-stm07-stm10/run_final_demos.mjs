/**
 * Closing-pass demo runner stub for STM-07 / STM-10.
 * Prefer existing batch runners when present; otherwise print the DEMO_GUIDE checklist.
 */
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../../..');

const candidates = [
  path.join(root, 'docs/reports/playwright-stm10-batch6/run_batch6_demo.mjs'),
  path.join(root, 'docs/reports/playwright-stm10-batch5/run_batch5_demo.mjs'),
];

console.log('STM-07/STM-10 final demo runner');
console.log('See DEMO_GUIDE.md in this folder for the full checklist.');
console.log('Host: http://localhost:3000  slowMo ~700ms  existing test accounts only.');

for (const script of candidates) {
  if (existsSync(script)) {
    console.log('Delegating to existing runner:', path.relative(root, script));
    const r = spawnSync(process.execPath, [script], { cwd: root, stdio: 'inherit' });
    process.exit(r.status ?? 1);
  }
}

console.log('No prior batch runner found. Open DEMO_GUIDE.md and run headed Playwright specs manually.');
process.exit(0);
