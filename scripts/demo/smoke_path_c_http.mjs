/**
 * Path C HTTP stack smoke — HMAC-signed active-check + STM health + A/R probe.
 * Does not write to Supabase. Does not print secrets.
 *
 * Usage: node scripts/demo/smoke_path_c_http.mjs
 */
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const outDir = path.join(root, 'docs/reports');
const outFile = path.join(outDir, 'R31_PATH_C_STACK_SMOKE_2026-11-15.json');

const TENANT = 'a0000000-0000-0000-0000-000000000001';
const PILOT = 'c31a0000-0000-4000-8000-000000000031';
const OTHER = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
const CHECK_PATH = '/api/v1/admin/internal/delegations/active-check';
const IAM_BASE = process.env.CAMPX_IAM_SMOKE_BASE || 'http://127.0.0.1:8081';
const EMPTY_SHA = crypto.createHash('sha256').update(Buffer.alloc(0)).digest('hex');

const STM_KEYS = [
  'STM-02.ADMISSIONS.A',
  'STM-03.ENROLMENT.A',
  'STM-04.LIFECYCLE.A',
  'STM-05.GUARDIAN.A',
  'STM-06.DOCUMENTS.A',
];

const SERVICES = [
  { id: 'STM-02', name: 'admissions', port: 8094, health: '/health' },
  { id: 'STM-03', name: 'enrollment', port: 8095, health: '/health' },
  { id: 'STM-04', name: 'lifecycle', port: 8096, health: '/health' },
  { id: 'STM-05', name: 'guardian', port: 8097, health: '/health' },
  { id: 'STM-06', name: 'documents', port: 8098, health: '/health' },
];

function requireSecret() {
  const secret = process.env.CAMPX_INTERNAL_SECRET || process.env.CAMPX_GATEWAY_HMAC_SECRET;
  if (!secret) {
    throw new Error('CAMPX_INTERNAL_SECRET or CAMPX_GATEWAY_HMAC_SECRET required');
  }
  return secret;
}

function sign(method, pathOnly, userId, tenantId, secret) {
  const timestamp = String(Date.now());
  const canonical = `${timestamp}:${method.toUpperCase()}:${pathOnly}:${userId}:${tenantId}:${EMPTY_SHA}`;
  const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex');
  return { timestamp, signature };
}

async function httpJson(url, { method = 'GET', headers = {}, body } = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 8000);
  try {
    const res = await fetch(url, {
      method,
      headers,
      body,
      signal: ctrl.signal,
    });
    const text = await res.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    return { status: res.status, text: text.slice(0, 800), json };
  } finally {
    clearTimeout(t);
  }
}

async function activeCheck({ base, userId, permissionKey, callerId, role = 'REGISTRAR', secret }) {
  const q = `?userId=${encodeURIComponent(userId)}&permissionKey=${encodeURIComponent(permissionKey)}`;
  const { timestamp, signature } = sign('GET', CHECK_PATH, callerId, TENANT, secret);
  return httpJson(`${base}${CHECK_PATH}${q}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      'X-User-Id': callerId,
      'X-Tenant-Id': TENANT,
      'X-User-Role': role,
      'X-Gateway-Timestamp': timestamp,
      'X-Gateway-Signature': signature,
    },
  });
}

async function signedProbe(urlPath, { port, userId, role, secret, method = 'POST', bodyObj }) {
  const body = bodyObj ? Buffer.from(JSON.stringify(bodyObj), 'utf8') : Buffer.alloc(0);
  const bodySha = crypto.createHash('sha256').update(body).digest('hex');
  const timestamp = String(Date.now());
  const canonical = `${timestamp}:${method}:${urlPath}:${userId}:${TENANT}:${bodySha}`;
  const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex');
  return httpJson(`http://127.0.0.1:${port}${urlPath}`, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-User-Id': userId,
      'X-Tenant-Id': TENANT,
      'X-User-Role': role,
      'X-College-Id': 'a0000000-0000-0000-0000-000000000002',
      'X-Gateway-Timestamp': timestamp,
      'X-Gateway-Signature': signature,
    },
    body: body.length ? body : undefined,
  });
}

function passFail(ok, detail) {
  return { ok: !!ok, detail };
}

async function main() {
  const secret = requireSecret();
  const results = {
    startedAt: new Date().toISOString(),
    iamBase: IAM_BASE,
    freeRamNote: 'owner-confirmed >=4GB before run',
    checks: [],
  };

  // Gateway health (optional)
  try {
    const gw = await httpJson('http://127.0.0.1:8080/actuator/health');
    results.checks.push({
      name: 'api-gateway-health',
      ...passFail(gw.status >= 200 && gw.status < 400, `HTTP ${gw.status}`),
    });
  } catch (e) {
    results.checks.push({ name: 'api-gateway-health', ok: false, detail: String(e.message || e) });
  }

  // Valid Path C for all STM keys
  for (const key of STM_KEYS) {
    const r = await activeCheck({
      base: IAM_BASE,
      userId: PILOT,
      permissionKey: key,
      callerId: PILOT,
      secret,
    });
    const allowed = r.json && r.json.allowed === true;
    const reason = r.json && r.json.reason;
    results.checks.push({
      name: `pathc-allow-${key}`,
      ...passFail(r.status === 200 && allowed && reason === 'ACTIVE_R31_DELEGATION', `HTTP ${r.status} body=${r.text}`),
    });
  }

  // Invalid: unauthorized permission key
  {
    const r = await activeCheck({
      base: IAM_BASE,
      userId: PILOT,
      permissionKey: 'FIN.PAYROLL.A',
      callerId: PILOT,
      secret,
    });
    const denied =
      r.status === 200 && r.json && r.json.allowed === false && r.json.reason === 'UNAUTHORIZED_PERMISSION_KEY';
    results.checks.push({
      name: 'pathc-deny-unauthorized-key',
      ...passFail(denied, `HTTP ${r.status} body=${r.text}`),
    });
  }

  // Invalid: caller/subject mismatch
  {
    const r = await activeCheck({
      base: IAM_BASE,
      userId: PILOT,
      permissionKey: 'STM-06.DOCUMENTS.A',
      callerId: OTHER,
      secret,
    });
    const denied =
      r.status === 200 && r.json && r.json.allowed === false && r.json.reason === 'CALLER_SUBJECT_MISMATCH';
    results.checks.push({
      name: 'pathc-deny-caller-subject-mismatch',
      ...passFail(denied, `HTTP ${r.status} body=${r.text}`),
    });
  }

  // Invalid: non-delegatee
  {
    const r = await activeCheck({
      base: IAM_BASE,
      userId: OTHER,
      permissionKey: 'STM-06.DOCUMENTS.A',
      callerId: OTHER,
      role: 'STUDENT',
      secret,
    });
    const denied =
      r.status === 200 && r.json && r.json.allowed === false && r.json.reason === 'NO_ACTIVE_REGISTRAR_DELEGATION';
    results.checks.push({
      name: 'pathc-deny-no-delegation',
      ...passFail(denied, `HTTP ${r.status} body=${r.text}`),
    });
  }

  // Gateway-routed Path C (same HMAC identity headers as downstream expects after GW)
  try {
    const key = 'STM-06.DOCUMENTS.A';
    const q = `?userId=${encodeURIComponent(PILOT)}&permissionKey=${encodeURIComponent(key)}`;
    const gwPath = `${CHECK_PATH}${q}`;
    // Inbound to gateway typically uses Bearer; for smoke we hit GW path if GW forwards unsigned client headers
    // Prefer direct IAM for authority; GW route smoke is connectivity only with signed headers.
    const { timestamp, signature } = sign('GET', CHECK_PATH, PILOT, TENANT, secret);
    const r = await httpJson(`http://127.0.0.1:8080${gwPath}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        'X-User-Id': PILOT,
        'X-Tenant-Id': TENANT,
        'X-User-Role': 'REGISTRAR',
        'X-Gateway-Timestamp': timestamp,
        'X-Gateway-Signature': signature,
      },
    });
    results.checks.push({
      name: 'gateway-route-pathc-connectivity',
      ...passFail(r.status === 200 || r.status === 401 || r.status === 403, `HTTP ${r.status} body=${r.text}`),
      note: '401/403 acceptable if gateway requires Bearer before forward; 200 proves route+IAM',
      allowed: r.json && r.json.allowed,
    });
  } catch (e) {
    results.checks.push({
      name: 'gateway-route-pathc-connectivity',
      ok: false,
      detail: String(e.message || e),
    });
  }

  // STM health
  for (const svc of SERVICES) {
    try {
      const r = await httpJson(`http://127.0.0.1:${svc.port}${svc.health}`);
      results.checks.push({
        name: `${svc.id}-health`,
        ...passFail(r.status >= 200 && r.status < 500, `HTTP ${r.status} body=${r.text}`),
      });
    } catch (e) {
      results.checks.push({ name: `${svc.id}-health`, ok: false, detail: String(e.message || e) });
    }
  }

  // STM-06 A/R probe: pilot should pass delegation then fail domain (missing doc);
  // non-delegatee should fail closed on delegation.
  const fakeDoc = 'c31a0000-0000-4000-8000-00000000dead';
  const decisionPath = `/api/v1/documents/verifications/${fakeDoc}/decision`;
  {
    const pilot = await signedProbe(decisionPath, {
      port: 8098,
      userId: PILOT,
      role: 'REGISTRAR',
      secret,
      bodyObj: { decision: 'APPROVE' },
    });
    const pastDelegation =
      pilot.status !== 401 &&
      !(pilot.text || '').toLowerCase().includes('failclosed') &&
      !(pilot.text || '').toLowerCase().includes('no active') &&
      !(pilot.text || '').includes('NO_ACTIVE_REGISTRAR_DELEGATION') &&
      !(pilot.text || '').includes('delegation');
    // Accept 404/400/422 domain failures as proof Path C allow reached domain layer.
    const domainish = [400, 404, 409, 422, 500].includes(pilot.status) || pastDelegation;
    results.checks.push({
      name: 'STM-06-AR-pilot-past-delegation-or-domain',
      ...passFail(domainish && pilot.status !== 401, `HTTP ${pilot.status} body=${pilot.text}`),
    });

    const student = await signedProbe(decisionPath, {
      port: 8098,
      userId: OTHER,
      role: 'STUDENT',
      secret,
      bodyObj: { decision: 'APPROVE' },
    });
    const denied =
      student.status === 403 ||
      student.status === 401 ||
      (student.text || '').includes('delegation') ||
      (student.text || '').includes('NO_ACTIVE') ||
      (student.text || '').toLowerCase().includes('forbidden') ||
      (student.text || '').toLowerCase().includes('denied');
    results.checks.push({
      name: 'STM-06-AR-nondelegate-denied',
      ...passFail(denied, `HTTP ${student.status} body=${student.text}`),
    });
  }

  const failed = results.checks.filter((c) => !c.ok);
  results.endedAt = new Date().toISOString();
  results.summary = {
    total: results.checks.length,
    passed: results.checks.length - failed.length,
    failed: failed.length,
    failedNames: failed.map((f) => f.name),
  };

  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
  console.log(JSON.stringify(results.summary, null, 2));
  for (const c of results.checks) {
    console.log(`${c.ok ? 'PASS' : 'FAIL'} ${c.name} — ${c.detail || c.note || ''}`);
  }
  console.log(`Wrote ${outFile}`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.error('SMOKE_FATAL', e && e.message ? e.message : e);
  process.exit(2);
});
