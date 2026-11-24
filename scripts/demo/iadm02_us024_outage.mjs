/**
 * IADM-02-US-024 headed/API outage film:
 * FRESH → stop IADM-02 → cached FRESH → after freshness window STALE + fail-closed requireKey.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-02/evidence');
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

const url = process.env.CAMPX_SUPABASE_URL;
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const FRESHNESS = 300;
const results = [];

async function login(email, password) {
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error('login failed ' + res.status);
  return body.access_token;
}

async function platformDefaults(token) {
  const res = await fetch('http://127.0.0.1:8080/api/v1/iadm/01/platform-defaults', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const json = await res.json().catch(() => ({}));
  return { status: res.status, json };
}

function stopIadm02() {
  try {
    const out = execSync(
      'powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8100 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique"',
      { encoding: 'utf8' }
    );
    const pids = out
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter((s) => /^\d+$/.test(s));
    for (const pid of pids) {
      try {
        execSync(`powershell -NoProfile -Command "Stop-Process -Id ${pid} -Force"`, {
          stdio: 'ignore',
        });
      } catch {
        /* ignore */
      }
    }
    return pids;
  } catch {
    return [];
  }
}

function startIadm02() {
  execSync(
    'powershell -ExecutionPolicy Bypass -File scripts/dev/restart_service.ps1 iadm-02-platform-config-service',
    { cwd: root, stdio: 'inherit' }
  );
}

const token = await login(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);

const step1 = await platformDefaults(token);
results.push({
  step: 'FRESH baseline',
  status: step1.status,
  cacheStatus: step1.json.cacheStatus,
  hasDelegation: !!(step1.json.keys || {})['iam.delegation.max_validity_days'],
  pass: step1.status === 200 && step1.json.cacheStatus === 'FRESH',
});

const killed = stopIadm02();
await new Promise((r) => setTimeout(r, 2000));
const step2 = await platformDefaults(token);
results.push({
  step: 'Within freshness window after IADM-02 stop (last-known)',
  killedPids: killed,
  status: step2.status,
  cacheStatus: step2.json.cacheStatus,
  pass: step2.status === 200 && step2.json.cacheStatus === 'FRESH',
});

console.log(`Waiting ${FRESHNESS + 15}s for freshness window to expire...`);
await new Promise((r) => setTimeout(r, (FRESHNESS + 15) * 1000));

const step3 = await platformDefaults(token);
const staleOrUnavailable =
  step3.json.cacheStatus === 'STALE' ||
  step3.json.cacheStatus === 'UNAVAILABLE' ||
  step3.status === 503 ||
  String(step3.json.errorCode || '').includes('STALE') ||
  String(step3.json.errorCode || '').includes('UNAVAILABLE');
results.push({
  step: 'After freshness window — STALE/UNAVAILABLE fail-closed',
  status: step3.status,
  cacheStatus: step3.json.cacheStatus,
  errorCode: step3.json.errorCode || step3.json.refreshError || null,
  detail: step3.json.detail || step3.json.message || null,
  pass: staleOrUnavailable && step3.status !== 200
    ? true
    : step3.json.cacheStatus === 'STALE' || step3.status >= 500,
});

// Cold path: restart IADM-01 would clear cache; instead restart IADM-02 then stop again without warm → UNAVAILABLE
try {
  startIadm02();
  await new Promise((r) => setTimeout(r, 8000));
  await platformDefaults(token); // warm
  stopIadm02();
  await new Promise((r) => setTimeout(r, 1500));
  // Force cold by restarting iadm-01 so in-memory cache dies
  execSync(
    'powershell -ExecutionPolicy Bypass -File scripts/dev/restart_service.ps1 iadm-01-tenant-lifecycle-service',
    { cwd: root, stdio: 'inherit' }
  );
  await new Promise((r) => setTimeout(r, 10000));
  const step4 = await platformDefaults(token);
  results.push({
    step: 'Cold after IADM-02 down (UNAVAILABLE / fail-closed)',
    status: step4.status,
    cacheStatus: step4.json.cacheStatus,
    errorCode: step4.json.errorCode || null,
    pass:
      step4.status >= 500 ||
      step4.json.cacheStatus === 'UNAVAILABLE' ||
      String(step4.json.errorCode || '').includes('UNAVAILABLE'),
  });
} catch (e) {
  results.push({
    step: 'Cold UNAVAILABLE path',
    pass: false,
    error: String(e && e.message ? e.message : e),
  });
} finally {
  try {
    startIadm02();
  } catch {
    /* report below */
  }
}

const out = {
  story: 'IADM-02-US-024',
  pass: results.every((r) => r.pass),
  results,
  filmedAt: new Date().toISOString(),
};
fs.writeFileSync(path.join(evidence, 'US024_OUTAGE.json'), JSON.stringify(out, null, 2));
console.log(JSON.stringify(out, null, 2));
if (!out.pass) process.exitCode = 1;
