/**
 * Dev environment guard for STM ops readiness (CROSS-EN-014 / STM-03-EN-007).
 * Asserts Development Supabase identity and required env *presence*. Never prints secret values.
 *
 * Usage: node scripts/dev/validate_dev_env.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const DEV_REF = 'azulbaxszsqxymqmlwfe';
const FORBIDDEN_REFS = ['ekspahbsbwdpraaqfqui', 'kpwdvvcschankgggitnj'];

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  const text = fs.readFileSync(filePath, 'utf8');
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let val = trimmed.slice(eq + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    if (!(key in process.env) || process.env[key] === '') {
      process.env[key] = val;
    }
  }
}

loadEnvFile(path.join(root, '.env.audit'));
loadEnvFile(path.join(root, 'frontend', '.env.local'));

const checks = [];
function check(name, ok, detail) {
  checks.push({ name, ok: !!ok, detail });
  const mark = ok ? 'PASS' : 'FAIL';
  console.log(`[${mark}] ${name}${detail ? ` — ${detail}` : ''}`);
}

const supabaseUrl =
  process.env.NEXT_PUBLIC_SUPABASE_URL || process.env.CAMPX_SUPABASE_URL || '';
const hasDev = supabaseUrl.includes(DEV_REF);
const forbiddenHit = FORBIDDEN_REFS.find((r) => supabaseUrl.includes(r));

check('supabase-url-present', !!supabaseUrl, supabaseUrl ? 'url present (value not logged)' : 'missing');
check('supabase-dev-ref', hasDev, hasDev ? `contains ${DEV_REF}` : 'dev ref missing');
check('supabase-not-production-or-legacy', !forbiddenHit, forbiddenHit ? `FORBIDDEN ref ${forbiddenHit}` : 'ok');

const requiredPresence = [
  'CAMPX_INTERNAL_SECRET|CAMPX_GATEWAY_HMAC_SECRET',
  'STUDENT1_EMAIL',
  'STUDENT1_PW',
  'REGISTRAR_PILOT_EMAIL',
  'REGISTRAR_PILOT_PW',
  'NEXT_PUBLIC_SUPABASE_ANON_KEY|SUPABASE_ANON_KEY',
];

for (const spec of requiredPresence) {
  const alts = spec.split('|');
  const found = alts.some((k) => process.env[k] && String(process.env[k]).length > 0);
  check(`env-present-${alts[0]}`, found, found ? 'present' : `missing one of ${spec}`);
}

const outFile = path.join(root, 'docs/reports/OPS_DEV_ENV_VALIDATE_2026-11-16.json');
const passed = checks.filter((c) => c.ok).length;
const failed = checks.filter((c) => !c.ok).length;
const report = {
  at: new Date().toISOString(),
  task: 'ops-dev-env-validate',
  projectRefExpected: DEV_REF,
  checks,
  summary: { total: checks.length, passed, failed, ok: failed === 0 },
};
fs.mkdirSync(path.dirname(outFile), { recursive: true });
fs.writeFileSync(outFile, JSON.stringify(report, null, 2));
console.log(`Evidence: ${outFile} (${passed}/${checks.length})`);
process.exit(failed === 0 ? 0 : 1);
