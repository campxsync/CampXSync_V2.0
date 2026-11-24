/**
 * IADM-03 US-040 close-out checklist runner (DEV evidence pack).
 * Marks CADM-03 / STM-07 items as later integration. Does NOT claim RBAC v3.0 Compliant
 * unless every gate below is green (including blocked stories).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { spawnSync } from 'child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/closeout');
fs.mkdirSync(evidence, { recursive: true });

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

const checks = [];

function add(id, ok, detail, status = ok ? 'PASS' : 'FAIL') {
  checks.push({ id, status, ok: !!ok, detail });
}

function grepCount(pattern, cwdRel, glob) {
  const args = ['-n', '--glob', glob, pattern, cwdRel];
  const r = spawnSync('rg', args, { cwd: root, encoding: 'utf8' });
  if (r.error) {
    // fallback: simple walk for key paths
    return { ok: false, count: -1, err: String(r.error.message || r.error) };
  }
  const out = (r.stdout || '').trim();
  if (!out) return { ok: true, count: 0 };
  return { ok: true, count: out.split(/\r?\n/).length, sample: out.split(/\r?\n/).slice(0, 5) };
}

// 1) Re-run IADM-02 existing 56-cell
{
  const r = spawnSync(process.execPath, ['scripts/dev/iadm02_persona_matrix_56.mjs'], {
    cwd: root,
    encoding: 'utf8',
  });
  const body = (r.stdout || '').trim();
  let parsed = null;
  try {
    parsed = JSON.parse(body);
  } catch {
    /* ignore */
  }
  add(
    'IADM02_56_CELL',
    r.status === 0 && parsed && parsed.cellCount === 56,
    parsed || body || r.stderr
  );
}

// 2) IADM-03 iam.* 56-cell sample
{
  const r = spawnSync(process.execPath, ['scripts/dev/iadm03_persona_matrix_56.mjs'], {
    cwd: root,
    encoding: 'utf8',
  });
  const body = (r.stdout || '').trim();
  let parsed = null;
  try {
    parsed = JSON.parse(body);
  } catch {
    /* ignore */
  }
  add(
    'IADM03_56_CELL',
    r.status === 0 && parsed && parsed.cellCount === 56,
    parsed || body || r.stderr
  );
}

// 3) Health + contracts
async function httpJson(url, opts = {}) {
  try {
    const res = await fetch(url, opts);
    const text = await res.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      json = text;
    }
    return { status: res.status, json };
  } catch (e) {
    return { status: 0, json: { error: String(e.message || e) } };
  }
}

{
  const h = await httpJson('http://127.0.0.1:8101/api/v1/iadm/03/health');
  add('IADM03_HEALTH', h.status === 200 && h.json?.status === 'UP', h);
  const c = await httpJson('http://127.0.0.1:8101/api/v1/iadm/03/claims/contract');
  add(
    'CLAIMS_NOT_PER_REQUEST',
    c.status === 200 && c.json?.iadm03OnRequestPath === false && c.json?.roleNameFallback === false,
    c
  );
  const n = await httpJson('http://127.0.0.1:8101/api/v1/iadm/03/notifications/contract');
  add('NOTIFICATION_CONTRACT', n.status === 200 && /BEST_EFFORT/i.test(JSON.stringify(n.json)), n);
  const d = await httpJson('http://127.0.0.1:8101/api/v1/iadm/03/denials/r27', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ grantor: 'R01' }),
  });
  add('R27_DENIAL', d.status === 403 && d.json?.allowed === false, d);
  const g = await httpJson('http://127.0.0.1:8101/api/v1/iadm/03/governance/contract');
  add('GOVERNANCE_CONTRACT', g.status === 200 && g.json?.iadm03OnRequestPath === false, g);
}

// 4) Architecture guards (frontend iadm paths must not import @/lib/db)
{
  const paths = [
    'frontend/src/app/(dashboard)/platform/identity',
    'frontend/src/app/(dashboard)/institution/access',
    'frontend/src/app/api/platform/identity',
    'frontend/src/app/api/institution/access',
  ];
  let hits = 0;
  const samples = [];
  for (const p of paths) {
    const full = path.join(root, p);
    if (!fs.existsSync(full)) continue;
    const walk = (dir) => {
      for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
        const fp = path.join(dir, ent.name);
        if (ent.isDirectory()) walk(fp);
        else if (/\.(ts|tsx|js|jsx)$/.test(ent.name)) {
          const text = fs.readFileSync(fp, 'utf8');
          if (/from ['"]@\/lib\/db['"]/.test(text) || /from ['"]@\/lib\/supabase\/db['"]/.test(text)) {
            hits += 1;
            samples.push(path.relative(root, fp));
          }
        }
      }
    };
    walk(full);
  }
  add('NO_FRONTEND_DB_IADM03_PATHS', hits === 0, { hits, samples });
}

// 5) No wildcard permission grant in AccessPermissionMatrix source
{
  const matrixPath = path.join(
    root,
    'services/administration-services/iadm-03-identity-access-service/src/main/java/com/campx/iadm/access/security/AccessPermissionMatrix.java'
  );
  const text = fs.readFileSync(matrixPath, 'utf8');
  const hasWildcard = /"\*"/.test(text);
  add('NO_WILDCARD_IN_MATRIX', !hasWildcard, { hasWildcard });
  const newKeysMatch = text.match(/NEW_KEYS\s*=\s*\{([\s\S]*?)\};/);
  const newKeysBody = newKeysMatch ? newKeysMatch[1] : '';
  const adm03InKeys = /"adm03\./.test(newKeysBody);
  add('ADM03_DORMANT_IN_MATRIX', !!newKeysMatch && !adm03InKeys, {
    note: 'adm03.* absent from NEW_KEYS array (comments may mention dormancy)',
    adm03InKeys,
  });
}

// 6) Prior slice evidence present
{
  const required = [
    'docs/iadm/iadm-03/evidence/s2/S2_HEADED_RESULTS.json',
    'docs/iadm/iadm-03/evidence/s3/S3_HEADED_RESULTS.json',
    'docs/iadm/iadm-03/evidence/s3b/S3B_HEADED_RESULTS.json',
    'docs/iadm/iadm-03/evidence/s4/S4_HEADED_RESULTS.json',
  ];
  const missing = required.filter((r) => !fs.existsSync(path.join(root, r)));
  add('PRIOR_SLICE_EVIDENCE', missing.length === 0, { missing });
}

// 7) Later integration / parked / blocked — documented, not failures of this runner
add('US036_CADM03', true, { status: 'LATER_INTEGRATION' }, 'LATER_INTEGRATION');
add('US037_STM07', true, { status: 'LATER_INTEGRATION' }, 'LATER_INTEGRATION');
add('US038_BREAK_GLASS', true, { status: 'PARKED' }, 'PARKED');
add('US039_TWO_PERSON', true, { status: 'PARKED' }, 'PARKED');
add('US033_AUDITOR', true, { status: 'BLOCKED_BY_D3' }, 'BLOCKED');

const hard = checks.filter((c) => !['LATER_INTEGRATION', 'PARKED', 'BLOCKED'].includes(c.status));
const allHardPass = hard.every((c) => c.ok);
const rbacV30Compliant = false; // explicit: blocked/parked/later items remain

const report = {
  title: 'IADM-03 US-040 close-out checklist',
  generatedAt: new Date().toISOString(),
  allHardPass,
  rbacV30CompliantClaim: rbacV30Compliant,
  rbacV30CompliantReason:
    'Do not claim RBAC v3.0 Compliant while US-033 is blocked, US-036/037 wait later integration, and US-038/039 remain PARKED.',
  checks,
};

fs.writeFileSync(path.join(evidence, 'US040_CLOSEOUT_RESULTS.json'), JSON.stringify(report, null, 2));
console.log(JSON.stringify({ allHardPass, rbacV30CompliantClaim: rbacV30Compliant, checks }, null, 2));
if (!allHardPass) process.exitCode = 1;
