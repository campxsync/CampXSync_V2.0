/**
 * Autonomy A — create ONE active login link for Student One via existing API path.
 * Uses College Admin JWT → API Gateway → student-service (verify + iam-link).
 * Never prints passwords or tokens. Dev project azulbaxszsqxymqmlwfe only.
 *
 * Usage: node scripts/dev/ensure_student_one_login_link.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const OUT = path.join(root, 'docs/reports/STUDENT_ONE_LOGIN_LINK_BEFORE_AFTER_2026-11-18.json');

const STUDENT_ID = '81dd67d3-1d5f-4d8e-8061-a67648610002';
const STUDENT_AUTH_ID = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
const GATEWAY = process.env.API_GATEWAY_URL || 'http://localhost:8080';
const DEV_REF = 'azulbaxszsqxymqmlwfe';

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  for (const line of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    let val = trimmed.slice(eq + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!(key in process.env) || process.env[key] === '') process.env[key] = val;
  }
}

loadEnvFile(path.join(root, '.env.audit'));
loadEnvFile(path.join(root, 'frontend', '.env.local'));

function supabaseConfig() {
  const url =
    process.env.NEXT_PUBLIC_SUPABASE_URL ||
    process.env.CAMPX_SUPABASE_URL ||
    `https://${DEV_REF}.supabase.co`;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY || '';
  if (!url.includes(DEV_REF)) throw new Error('Refusing non-Dev Supabase URL');
  if (!anon) throw new Error('Anon key missing');
  return { url, anon };
}

async function httpJson(url, opts = {}) {
  const res = await fetch(url, opts);
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return { status: res.status, text: text.slice(0, 600), json };
}

async function obtainBearer(email, password) {
  const { url, anon } = supabaseConfig();
  const res = await httpJson(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', apikey: anon },
    body: JSON.stringify({ email, password }),
  });
  if (res.status !== 200 || !res.json?.access_token) {
    throw new Error(`JWT grant failed (${res.status})`);
  }
  return res.json.access_token;
}

async function gw(method, pathOnly, bearer, bodyObj) {
  const headers = {
    Accept: 'application/json',
    Authorization: `Bearer ${bearer}`,
  };
  let body;
  if (bodyObj != null) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(bodyObj);
  }
  return httpJson(`${GATEWAY}${pathOnly}`, { method, headers, body });
}

async function main() {
  const report = {
    project: DEV_REF,
    studentRecordId: STUDENT_ID,
    studentAuthUserId: STUDENT_AUTH_ID,
    email: process.env.STUDENT1_EMAIL || 'student1.campx@gmail.com',
    startedAt: new Date().toISOString(),
    steps: [],
  };

  // Use delegated Registrar (R31) who holds STM-07.ACCOUNT.A delegation, falling back to College Admin.
  const adminEmail = process.env.REGISTRAR_PILOT_EMAIL || process.env.COLLEGE_ADMIN_EMAIL;
  const adminPw = process.env.REGISTRAR_PILOT_PW || process.env.COLLEGE_ADMIN_PW;
  if (!adminEmail || !adminPw) {
    throw new Error('REGISTRAR_PILOT_EMAIL or COLLEGE_ADMIN_EMAIL missing from .env.audit');
  }

  const bearer = await obtainBearer(adminEmail, adminPw);
  report.steps.push({ step: 'college_admin_jwt', ok: true });

  const before = await gw('GET', `/api/v1/students/${STUDENT_ID}/link-status`, bearer);
  report.before = {
    httpStatus: before.status,
    body: before.json || before.text,
  };

  const alreadyActive =
    before.json &&
    (before.json.linkStatus === 'ACTIVE' || before.json.linkStatus === 'LINKED') &&
    String(before.json.iamUserId || before.json.linkedIamUserId || '') === STUDENT_AUTH_ID;

  if (alreadyActive) {
    report.action = 'noop_already_linked';
    report.after = report.before;
    report.finishedAt = new Date().toISOString();
    fs.writeFileSync(OUT, JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ ok: true, action: report.action, evidence: OUT }, null, 2));
    return;
  }

  // Check provisioning eligibility (normal operation: verified identity via STM-06 document verifications)
  const eligibility = await gw('GET', `/api/v1/students/${STUDENT_ID}/provisioning-eligibility`, bearer);
  report.steps.push({ step: 'check_eligibility', httpStatus: eligibility.status, body: eligibility.json || eligibility.text, ok: eligibility.status < 300 });

  // Link IAM account via existing API code path
  const link = await gw('POST', `/api/v1/students/${STUDENT_ID}/iam-link`, bearer, {
    iamUserId: STUDENT_AUTH_ID,
  });
  report.steps.push({ step: 'iam_link', httpStatus: link.status, ok: link.status < 300 });
  if (link.status >= 400) {
    report.linkError = link.json || link.text;
  }

  const after = await gw('GET', `/api/v1/students/${STUDENT_ID}/link-status`, bearer);
  report.after = { httpStatus: after.status, body: after.json || after.text };
  report.action = link.status < 300 ? 'linked' : 'link_failed';
  report.finishedAt = new Date().toISOString();
  fs.writeFileSync(OUT, JSON.stringify(report, null, 2));
  console.log(
    JSON.stringify(
      {
        ok: report.action === 'linked',
        action: report.action,
        beforeStatus: report.before?.body?.linkStatus || null,
        afterStatus: report.after?.body?.linkStatus || null,
        evidence: OUT,
      },
      null,
      2
    )
  );
  if (report.action !== 'linked') process.exit(1);
}

main().catch((e) => {
  console.error(String(e.message || e));
  process.exit(1);
});
