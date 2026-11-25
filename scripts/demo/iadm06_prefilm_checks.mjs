/**
 * Pre-film checks: additive IADM-01/04 analytics-aggregates (aggregates only, tenant-scoped).
 * Never prints credentials or person-level rows.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-06/evidence/prefilm');
fs.mkdirSync(evidence, { recursive: true });

function loadEnv(rel) {
  const p = path.join(root, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  }
}
loadEnv('.env.audit');
loadEnv('frontend/.env.local');
const seedPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
const seed = fs.existsSync(seedPath) ? JSON.parse(fs.readFileSync(seedPath, 'utf8')) : {};
const GW = process.env.CAMPX_GATEWAY_BASE || 'http://127.0.0.1:8080';

async function token(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login ${email} -> ${res.status}`);
  return body.access_token;
}

async function gw(tok, method, p) {
  const res = await fetch(`${GW}${p}`, {
    method,
    headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' },
  });
  const text = await res.text();
  let body = {};
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text.slice(0, 200) };
  }
  return { status: res.status, body };
}

function looksLikePersonRows(obj, depth = 0) {
  if (depth > 6 || obj == null) return false;
  if (Array.isArray(obj)) {
    if (obj.length === 0) return false;
    // Heuristic: arrays of objects with email/name/person fields look like rows
    return obj.some((x) => looksLikePersonRows(x, depth + 1));
  }
  if (typeof obj === 'object') {
    const keys = Object.keys(obj).map((k) => k.toLowerCase());
    const banned = ['email', 'full_name', 'fullname', 'phone', 'person_id', 'student_id', 'password', 'token'];
    if (keys.some((k) => banned.includes(k))) return true;
    return Object.values(obj).some((v) => looksLikePersonRows(v, depth + 1));
  }
  return false;
}

const out = [];
const pass = (name, ok, detail) => {
  out.push({ name, ok: !!ok, detail: detail || '' });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
};

const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
const r01Pw = process.env.PLATFORM_ADMIN_PW;
const r03 = seed.r03demo || {};

const t01 = await token(r01Email, r01Pw);
const a01 = await gw(t01, 'GET', '/api/v1/iadm/01/analytics-aggregates?window=month');
pass('IADM-01 analytics-aggregates 200', a01.status === 200, `status=${a01.status}`);
pass('IADM-01 aggregates only (no person fields)', !looksLikePersonRows(a01.body), `keys=${Object.keys(a01.body || {}).join(',')}`);
pass(
  'IADM-01 has aggregate shapes',
  Array.isArray(a01.body.institutesByStatus) || typeof a01.body.totalInstitutes === 'number',
  `totalInstitutes=${a01.body.totalInstitutes}`
);

const a04 = await gw(t01, 'GET', '/api/v1/iadm/04/analytics-aggregates?window=month');
pass('IADM-04 analytics-aggregates 200', a04.status === 200, `status=${a04.status}`);
pass('IADM-04 aggregates only (no person fields)', !looksLikePersonRows(a04.body), `keys=${Object.keys(a04.body || {}).join(',')}`);

if (r03.email && r03.password) {
  const t03 = await token(r03.email, r03.password);
  const a01r03 = await gw(t03, 'GET', '/api/v1/iadm/01/analytics-aggregates?window=month');
  pass('IADM-01 R03 scoped 200', a01r03.status === 200, `status=${a01r03.status}`);
  const total = a01r03.body.totalInstitutes;
  pass('IADM-01 R03 tenant-scoped (own institute count)', total === 1 || total === 0, `totalInstitutes=${total}`);
  const other = await gw(
    t03,
    'GET',
    `/api/v1/iadm/01/analytics-aggregates?window=month&tenantId=${encodeURIComponent('72ecbfe3-2077-41b5-8a20-b6d3e045311b')}`
  );
  // Either ignored and still own scope, or forbidden — never another institute's totals as platform
  const otherTotal = other.body.totalInstitutes;
  pass(
    'IADM-01 R03 cannot broaden to foreign tenant',
    other.status === 403 || otherTotal === total || otherTotal === 1 || otherTotal === 0,
    `status=${other.status} total=${otherTotal}`
  );
}

const a06 = await gw(t01, 'GET', '/api/v1/iadm/06/health');
pass('IADM-06 health via gateway', a06.status === 200, `status=${a06.status}`);

fs.writeFileSync(path.join(evidence, 'ADDITIVE_ENDPOINTS.json'), JSON.stringify(out, null, 2));
const failed = out.filter((x) => !x.ok).length;
console.log(failed === 0 ? 'PREFILM_OK' : 'PREFILM_FAIL');
process.exit(failed === 0 ? 0 : 1);
