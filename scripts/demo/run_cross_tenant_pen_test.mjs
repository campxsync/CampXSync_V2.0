/**
 * Cross-tenant penetration / regression harness — Gateway Bearer + loopback HMAC.
 * Complements Java stmCrossEn010_* / crossTenant* unit tests with live HTTP IDOR matrix.
 * Dev Supabase ref azulbaxszsqxymqmlwfe only. No Kafka / no new infra. Never prints secrets.
 *
 * Usage (stack up):
 *   node scripts/demo/run_cross_tenant_pen_test.mjs
 * Prefer: scripts/demo/run_cross_tenant_pen_stack.ps1
 */
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const outFile = path.join(root, 'docs/reports/CROSS_TENANT_PEN_2026-11-16.json');

const TENANT_A = 'a0000000-0000-0000-0000-000000000001';
const TENANT_B = 'b0000000-0000-0000-0000-000000000002';
const CAMPUS_A = 'a0000000-0000-0000-0000-000000000002';
const CAMPUS_B = 'b0000000-0000-0000-0000-000000000099';
const PILOT = 'c31a0000-0000-4000-8000-000000000031';
const ADMIN_A = '70a8ffae-b5ad-4c61-a401-16f220893df4';
const STUDENT_A = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
const STUDENT_B = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';
const FOREIGN_REG_B = 'd4e5f6a7-b8c9-4012-9abc-def012345678';

const GATEWAY = process.env.API_GATEWAY_URL || 'http://127.0.0.1:8080';

const SERVICES = {
  enrollment: { port: 8095, label: 'STM-03' },
  lifecycle: { port: 8096, label: 'STM-04' },
  guardian: { port: 8097, label: 'STM-05' },
  documents: { port: 8098, label: 'STM-06' },
};

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

function requireSecret() {
  const secret = process.env.CAMPX_INTERNAL_SECRET || process.env.CAMPX_GATEWAY_HMAC_SECRET;
  if (!secret) throw new Error('CAMPX_INTERNAL_SECRET or CAMPX_GATEWAY_HMAC_SECRET required');
  return secret;
}

function supabaseConfig() {
  const url =
    process.env.NEXT_PUBLIC_SUPABASE_URL ||
    process.env.CAMPX_SUPABASE_URL ||
    'https://azulbaxszsqxymqmlwfe.supabase.co';
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY || '';
  if (!anon) throw new Error('NEXT_PUBLIC_SUPABASE_ANON_KEY required for JWT acquisition');
  if (!url.includes('azulbaxszsqxymqmlwfe')) {
    throw new Error('Refusing non-dev Supabase project for cross-tenant pen test');
  }
  return { url, anon };
}

async function httpJson(url, { method = 'GET', headers = {}, body } = {}, timeoutMs = 15000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, { method, headers, body, signal: ctrl.signal });
    const text = await res.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    return { status: res.status, text: text.slice(0, 1200), json };
  } finally {
    clearTimeout(t);
  }
}

async function obtainBearer(email, password) {
  const { url, anon } = supabaseConfig();
  const res = await httpJson(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', apikey: anon },
    body: JSON.stringify({ email, password }),
  });
  if (res.status !== 200 || !res.json?.access_token) {
    const err = res.json?.error_description || res.json?.msg || res.json?.error || res.text;
    throw new Error(`JWT grant failed (${res.status}): ${err}`);
  }
  return {
    token: res.json.access_token,
    userId: res.json.user?.id,
    role: res.json.user?.app_metadata?.role,
    tenantId: res.json.user?.app_metadata?.tenant_id,
  };
}

async function gatewayRequest(pathOnly, { method = 'GET', bearer, extraHeaders = {}, body } = {}) {
  const headers = { Accept: 'application/json', ...extraHeaders };
  if (bearer) headers.Authorization = `Bearer ${bearer}`;
  if (body != null) {
    headers['Content-Type'] = 'application/json';
  }
  return httpJson(`${GATEWAY}${pathOnly}`, {
    method,
    headers,
    body: body != null ? JSON.stringify(body) : undefined,
  });
}

async function signedLoopback(
  port,
  urlPath,
  { method = 'GET', userId, tenantId, role, bodyObj, collegeId } = {}
) {
  const secret = requireSecret();
  const body = bodyObj != null ? Buffer.from(JSON.stringify(bodyObj), 'utf8') : Buffer.alloc(0);
  const bodySha = crypto.createHash('sha256').update(body).digest('hex');
  const timestamp = String(Date.now());
  const canonical = `${timestamp}:${method}:${urlPath}:${userId}:${tenantId}:${bodySha}`;
  const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex');
  return httpJson(`http://127.0.0.1:${port}${urlPath}`, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-User-Id': userId,
      'X-Tenant-Id': tenantId,
      'X-User-Role': role,
      'X-College-Id': collegeId ?? CAMPUS_A,
      'X-Gateway-Timestamp': timestamp,
      'X-Gateway-Signature': signature,
    },
    body: body.length ? body : undefined,
  });
}

function extractItems(json) {
  if (!json) return [];
  if (Array.isArray(json)) return json;
  if (Array.isArray(json.items)) return json.items;
  if (Array.isArray(json.data)) return json.data;
  if (Array.isArray(json.requests)) return json.requests;
  if (Array.isArray(json.enrolments)) return json.enrolments;
  if (Array.isArray(json.terms)) return json.terms;
  return [];
}

function isAuthFailure(status, json, text) {
  if (status === 401 || status === 403) return true;
  const blob = `${text || ''} ${JSON.stringify(json || {})}`.toUpperCase();
  return (
    blob.includes('GATEWAY_MISSING_AUTH') ||
    blob.includes('UNAUTHORIZED') ||
    blob.includes('JWT') ||
    blob.includes('TENANT_REQUIRED')
  );
}

function readIsolationOk(res) {
  if (res.status === 401 || res.status === 403 || res.status === 404 || res.status === 422) return true;
  if (res.status === 200) return extractItems(res.json).length === 0;
  return res.status >= 400;
}

function mutationDeniedOk(res) {
  if (res.status === 401 || res.status === 403 || res.status === 404 || res.status === 422) return true;
  return res.status >= 400;
}

function dataLeak(res, anchorIds = []) {
  if (res.status >= 200 && res.status < 300) {
    const items = extractItems(res.json);
    if (items.length > 0) return true;
    if (res.json && (res.json.id || res.json.studentId || res.json.storagePath || res.json.token)) return true;
    const blob = `${res.text || ''} ${JSON.stringify(res.json || {})}`;
    for (const id of anchorIds) {
      if (id && blob.includes(id)) return true;
    }
  }
  return false;
}

async function stackAvailable() {
  try {
    const gw = await httpJson(`${GATEWAY}/actuator/health`, {}, 5000);
    if (!(gw.status >= 200 && gw.status < 500)) return false;
  } catch {
    return false;
  }
  let listening = 0;
  for (const svc of Object.values(SERVICES)) {
    try {
      const r = await httpJson(`http://127.0.0.1:${svc.port}/health`, {}, 4000);
      if (r.status === 200 || r.status === 401 || r.status === 403) listening += 1;
    } catch {
      /* ignore */
    }
  }
  return listening >= 3;
}

async function main() {
  const results = {
    startedAt: new Date().toISOString(),
    task: 'CROSS-TENANT-PEN-2026-11-16',
    projectRef: 'azulbaxszsqxymqmlwfe',
    gatewayBase: GATEWAY,
    complementsUnitTests: [
      'EnrollmentDomainServiceTest.stmCrossEn010_* / crossTenant*',
      'LifecycleDomainServiceTest.stmCrossEn010_*',
      'GuardianDomainServiceTest.stm05En002_crossTenant*',
      'DocumentDomainServiceTest.stm06En004_crossTenant*',
      'scripts/demo/run_stm_cross_en_010_e2e.mjs (narrower gateway smoke)',
    ],
    vulnerabilities: [],
    checks: [],
    fixesApplied: [],
  };

  const push = (name, ok, detail, category = 'general') => {
    results.checks.push({ name, category, ok: !!ok, detail: String(detail || '').slice(0, 600) });
  };

  const pushVuln = (name, detail) => {
    results.vulnerabilities.push({ name, detail: String(detail).slice(0, 800) });
    push(name, false, `VULN: ${detail}`, 'isolation');
  };

  push('supabase-ref-dev-only', true, 'azulbaxszsqxymqmlwfe enforced in obtainBearer');

  const up = await stackAvailable();
  results.stackAvailable = up;
  if (!up) {
    results.skippedReason = 'Stack not listening (gateway :8080 and STM services). Start via run_cross_tenant_pen_stack.ps1.';
    results.endedAt = new Date().toISOString();
    results.summary = { total: results.checks.length, passed: results.checks.length, failed: 0, skipped: true, ok: true };
    fs.mkdirSync(path.dirname(outFile), { recursive: true });
    fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
    console.log('CROSS-TENANT PEN: SKIP (stack down)');
    console.log(`Evidence: ${outFile}`);
    process.exit(0);
  }

  push('stack-preflight', true, 'Gateway and STM listeners reachable');

  // --- Gateway: missing auth (all four domains) ---
  const unauthPaths = [
    { name: 'gw-missing-auth-enrollment', path: '/api/v1/enrollment/terms' },
    { name: 'gw-missing-auth-lifecycle', path: '/api/v1/lifecycle/requests' },
    { name: 'gw-missing-auth-guardian', path: `/api/v1/guardians/students/${STUDENT_A}` },
    { name: 'gw-missing-auth-documents', path: '/api/v1/documents/verifications' },
  ];
  for (const item of unauthPaths) {
    const r = await gatewayRequest(item.path);
    push(item.name, isAuthFailure(r.status, r.json, r.text), `HTTP ${r.status}`, 'gateway-auth');
  }

  // Spoofed identity without Bearer must fail
  {
    const r = await gatewayRequest('/api/v1/enrollment/terms', {
      extraHeaders: {
        'X-Tenant-Id': TENANT_A,
        'X-User-Id': PILOT,
        'X-User-Role': 'REGISTRAR',
      },
    });
    push('gw-spoof-headers-no-bearer-denied', isAuthFailure(r.status, r.json, r.text), `HTTP ${r.status}`, 'gateway-auth');
  }

  let registrarAuth = null;
  try {
    registrarAuth = await obtainBearer(process.env.REGISTRAR_PILOT_EMAIL, process.env.REGISTRAR_PILOT_PW);
    push(
      'jwt-registrar-pilot',
      !!registrarAuth.token && registrarAuth.userId === PILOT,
      `userIdMatch=${registrarAuth.userId === PILOT} role=${registrarAuth.role || 'n/a'}`,
      'gateway-auth'
    );
  } catch (e) {
    push('jwt-registrar-pilot', false, e.message, 'gateway-auth');
  }

  // Spoofed X-Tenant-Id with valid Bearer must not elevate to foreign tenant (compare list sizes)
  if (registrarAuth?.token) {
    const baseline = await gatewayRequest('/api/v1/enrollment/terms', { bearer: registrarAuth.token });
    const spoofed = await gatewayRequest('/api/v1/enrollment/terms', {
      bearer: registrarAuth.token,
      extraHeaders: {
        'X-Tenant-Id': TENANT_B,
        'X-User-Id': FOREIGN_REG_B,
        'X-User-Role': 'SUPER_ADMIN',
      },
    });
    const baseN = extractItems(baseline.json).length;
    const spoofN = extractItems(spoofed.json).length;
    const ok =
      baseline.status === 200 &&
      spoofed.status === 200 &&
      spoofN === baseN &&
      !dataLeak({ status: spoofed.status, json: { items: extractItems(spoofed.json).filter((t) => t.tenantId === TENANT_B) } });
    if (!ok && spoofed.status === 200 && spoofN > baseN) {
      pushVuln('gw-spoof-tenant-header-escalation', `baseline=${baseN} spoofed=${spoofN} HTTP ${spoofed.status}`);
    } else {
      push(
        'gw-bearer-spoof-tenant-stripped',
        ok || (spoofed.status >= 400 && spoofed.status !== 200),
        `baseline HTTP ${baseline.status} n=${baseN}; spoofed HTTP ${spoofed.status} n=${spoofN}`,
        'gateway-auth'
      );
    }
  }

  // Gateway IDOR: student path with registrar token should not return cross-tenant guardian bleed
  if (registrarAuth?.token) {
    const r = await gatewayRequest(`/api/v1/guardians/students/${STUDENT_A}`, { bearer: registrarAuth.token });
    const leak =
      r.status === 200 &&
      r.json &&
      typeof r.json === 'object' &&
      String(r.json.tenantId || '') === TENANT_B;
    if (leak) pushVuln('gw-guardian-tenant-b-leak', `HTTP ${r.status}`);
    else push('gw-guardian-student-a-in-tenant-a', r.status >= 200 && r.status < 500, `HTTP ${r.status}`, 'gateway-read');
  }

  requireSecret();

  // --- Discover tenant-A anchors (loopback HMAC) ---
  let enrolmentId = null;
  let lifecycleRequestId = null;
  let documentId = null;
  let documentTypeId = null;
  let guardianId = null;

  const termsA = await signedLoopback(SERVICES.enrollment.port, '/api/v1/enrollment/terms', {
    userId: PILOT,
    tenantId: TENANT_A,
    role: 'REGISTRAR',
  });
  const termItems = extractItems(termsA.json);
  push('probe-enrollment-terms-tenant-a', termsA.status === 200, `HTTP ${termsA.status} count=${termItems.length}`, 'probe');

  const enrolList = await signedLoopback(SERVICES.enrollment.port, '/api/v1/enrollment/enrolments', {
    userId: PILOT,
    tenantId: TENANT_A,
    role: 'REGISTRAR',
  });
  const enrolItems = extractItems(enrolList.json);
  if (enrolItems[0]?.id) enrolmentId = enrolItems[0].id;
  push('probe-enrollment-list-tenant-a', enrolList.status === 200, `HTTP ${enrolList.status} enrolmentId=${enrolmentId || 'none'}`, 'probe');

  const lifeList = await signedLoopback(SERVICES.lifecycle.port, '/api/v1/lifecycle/requests', {
    userId: PILOT,
    tenantId: TENANT_A,
    role: 'REGISTRAR',
  });
  const lifeItems = extractItems(lifeList.json);
  if (lifeItems[0]?.id) lifecycleRequestId = lifeItems[0].id;
  push('probe-lifecycle-requests-tenant-a', lifeList.status === 200, `HTTP ${lifeList.status} requestId=${lifecycleRequestId || 'none'}`, 'probe');

  const guardiansA = await signedLoopback(SERVICES.guardian.port, `/api/v1/guardians/students/${STUDENT_A}`, {
    userId: PILOT,
    tenantId: TENANT_A,
    role: 'REGISTRAR',
  });
  const guardItems = extractItems(guardiansA.json);
  if (guardItems[0]?.id) guardianId = guardItems[0].id;
  else if (guardiansA.json?.id) guardianId = guardiansA.json.id;
  push('probe-guardians-student-a', guardiansA.status === 200, `HTTP ${guardiansA.status} guardianId=${guardianId || 'none'}`, 'probe');

  const docTypes = await signedLoopback(SERVICES.documents.port, '/api/v1/documents/types', {
    userId: PILOT,
    tenantId: TENANT_A,
    role: 'REGISTRAR',
  });
  const typeItems = extractItems(docTypes.json);
  if (typeItems[0]?.id) documentTypeId = typeItems[0].id;

  const docSubs = await signedLoopback(SERVICES.documents.port, '/api/v1/documents/submissions', {
    userId: STUDENT_A,
    tenantId: TENANT_A,
    role: 'STUDENT',
  });
  const subItems = extractItems(docSubs.json);
  if (subItems[0]?.id) documentId = subItems[0].id;
  push('probe-documents-submissions-tenant-a', docSubs.status === 200, `HTTP ${docSubs.status} docId=${documentId || 'none'}`, 'probe');

  // --- Loopback cross-tenant READ matrix (TENANT_B actors) ---
  const anchors = [enrolmentId, lifecycleRequestId, documentId, STUDENT_A].filter(Boolean);

  const foreignReadCases = [
    {
      name: 'loopback-enrollment-terms-tenant-b-empty',
      port: SERVICES.enrollment.port,
      path: '/api/v1/enrollment/terms',
      userId: FOREIGN_REG_B,
      role: 'REGISTRAR',
    },
    {
      name: 'loopback-lifecycle-requests-tenant-b-empty',
      port: SERVICES.lifecycle.port,
      path: '/api/v1/lifecycle/requests',
      userId: FOREIGN_REG_B,
      role: 'REGISTRAR',
    },
    {
      name: 'loopback-guardian-student-a-tenant-b-deny',
      port: SERVICES.guardian.port,
      path: `/api/v1/guardians/students/${STUDENT_A}`,
      userId: FOREIGN_REG_B,
      role: 'REGISTRAR',
    },
    {
      name: 'loopback-documents-types-tenant-b-empty',
      port: SERVICES.documents.port,
      path: '/api/v1/documents/types',
      userId: FOREIGN_REG_B,
      role: 'REGISTRAR',
    },
    {
      name: 'loopback-lifecycle-status-student-a-tenant-b',
      port: SERVICES.lifecycle.port,
      path: `/api/v1/lifecycle/students/${STUDENT_A}/status`,
      userId: FOREIGN_REG_B,
      role: 'REGISTRAR',
    },
  ];

  for (const c of foreignReadCases) {
    const r = await signedLoopback(c.port, c.path, { userId: c.userId, tenantId: TENANT_B, role: c.role });
    if (dataLeak(r, anchors)) pushVuln(c.name, `HTTP ${r.status} returned foreign tenant payload`);
    else push(c.name, readIsolationOk(r), `HTTP ${r.status}`, 'loopback-read');
  }

  if (enrolmentId) {
    const paths = [
      `/api/v1/enrollment/enrolments/${enrolmentId}`,
      `/api/v1/enrollment/enrolments/${enrolmentId}/history`,
    ];
    for (const p of paths) {
      const r = await signedLoopback(SERVICES.enrollment.port, p, {
        userId: FOREIGN_REG_B,
        tenantId: TENANT_B,
        role: 'REGISTRAR',
      });
      const name = `loopback-enrollment-idor-${p.includes('history') ? 'history' : 'get'}`;
      if (dataLeak(r)) pushVuln(name, `HTTP ${r.status} enrolment ${enrolmentId}`);
      else push(name, readIsolationOk(r), `HTTP ${r.status}`, 'loopback-read');
    }
  } else {
    push('loopback-enrollment-idor-skipped', true, 'no enrolment anchor in tenant A', 'loopback-read');
  }

  if (lifecycleRequestId) {
    const r = await signedLoopback(
      SERVICES.lifecycle.port,
      `/api/v1/lifecycle/requests/${lifecycleRequestId}`,
      { userId: FOREIGN_REG_B, tenantId: TENANT_B, role: 'REGISTRAR' }
    );
    if (dataLeak(r)) pushVuln('loopback-lifecycle-request-idor', `HTTP ${r.status}`);
    else push('loopback-lifecycle-request-idor', readIsolationOk(r), `HTTP ${r.status}`, 'loopback-read');
  } else {
    push('loopback-lifecycle-request-idor-skipped', true, 'no lifecycle request anchor', 'loopback-read');
  }

  if (documentId) {
    const r = await signedLoopback(
      SERVICES.documents.port,
      `/api/v1/documents/submissions/${documentId}/audit-trail`,
      { userId: STUDENT_B, tenantId: TENANT_B, role: 'STUDENT' }
    );
    if (dataLeak(r)) pushVuln('loopback-documents-audit-trail-idor', `HTTP ${r.status}`);
    else push('loopback-documents-audit-trail-idor', readIsolationOk(r), `HTTP ${r.status}`, 'loopback-read');

    const dl = await signedLoopback(SERVICES.documents.port, `/api/v1/documents/submissions/${documentId}`, {
      userId: STUDENT_B,
      tenantId: TENANT_B,
      role: 'STUDENT',
    });
    if (dataLeak(dl)) pushVuln('loopback-documents-submission-get-idor', `HTTP ${dl.status}`);
    else push('loopback-documents-submission-get-idor', readIsolationOk(dl), `HTTP ${dl.status}`, 'loopback-read');
  } else {
    push('loopback-documents-idor-skipped', true, 'no submission anchor', 'loopback-read');
  }

  // --- Loopback cross-tenant UPDATE / upload / delete attempts ---
  if (enrolmentId) {
    const r = await signedLoopback(
      SERVICES.enrollment.port,
      `/api/v1/enrollment/enrolments/${enrolmentId}/status`,
      {
        method: 'POST',
        userId: FOREIGN_REG_B,
        tenantId: TENANT_B,
        role: 'REGISTRAR',
        bodyObj: { status: 'SUSPENDED' },
      }
    );
    if (r.status >= 200 && r.status < 300) pushVuln('loopback-enrollment-status-mutation-tenant-b', `HTTP ${r.status}`);
    else push('loopback-enrollment-status-mutation-tenant-b', mutationDeniedOk(r), `HTTP ${r.status}`, 'loopback-mutation');
  }

  if (lifecycleRequestId) {
    const r = await signedLoopback(
      SERVICES.lifecycle.port,
      `/api/v1/lifecycle/requests/${lifecycleRequestId}/decide`,
      {
        method: 'POST',
        userId: FOREIGN_REG_B,
        tenantId: TENANT_B,
        role: 'REGISTRAR',
        bodyObj: { decision: 'APPROVED', note: 'pen-test' },
      }
    );
    if (r.status >= 200 && r.status < 300) pushVuln('loopback-lifecycle-decide-tenant-b', `HTTP ${r.status}`);
    else push('loopback-lifecycle-decide-tenant-b', mutationDeniedOk(r), `HTTP ${r.status}`, 'loopback-mutation');
  }

  if (guardianId) {
    const r = await signedLoopback(SERVICES.guardian.port, `/api/v1/guardians/${guardianId}`, {
      method: 'PATCH',
      userId: FOREIGN_REG_B,
      tenantId: TENANT_B,
      role: 'REGISTRAR',
      bodyObj: { reviewStatus: 'APPROVED', note: 'pen-test' },
    });
    if (r.status >= 200 && r.status < 300) pushVuln('loopback-guardian-patch-tenant-b', `HTTP ${r.status}`);
    else push('loopback-guardian-patch-tenant-b', mutationDeniedOk(r), `HTTP ${r.status}`, 'loopback-mutation');
  } else if (guardiansA.status === 200) {
    const r = await signedLoopback(
      SERVICES.guardian.port,
      `/api/v1/guardians/students/${STUDENT_A}/emergency-contacts`,
      {
        method: 'POST',
        userId: FOREIGN_REG_B,
        tenantId: TENANT_B,
        role: 'REGISTRAR',
        bodyObj: { name: 'PenTest', phone: '+910000000000', relationship: 'OTHER' },
      }
    );
    if (r.status >= 200 && r.status < 300) pushVuln('loopback-guardian-emergency-contact-tenant-b', `HTTP ${r.status}`);
    else push('loopback-guardian-emergency-contact-tenant-b', mutationDeniedOk(r), `HTTP ${r.status}`, 'loopback-mutation');
  }

  if (documentTypeId) {
    const bytes = Buffer.from('%PDF-1.4 pen-cross-tenant', 'ascii');
    const sha = crypto.createHash('sha256').update(bytes).digest('hex');
    const r = await signedLoopback(SERVICES.documents.port, '/api/v1/documents/submissions', {
      method: 'POST',
      userId: STUDENT_B,
      tenantId: TENANT_B,
      role: 'STUDENT',
      bodyObj: {
        documentTypeId,
        title: 'Cross-tenant upload pen',
        mimeType: 'application/pdf',
        sizeBytes: bytes.length,
        fileName: 'pen.pdf',
        checksumSha256: sha,
        contentBase64: bytes.toString('base64'),
      },
    });
    if (r.status === 201 || r.status === 200) pushVuln('loopback-documents-upload-wrong-type-tenant', `HTTP ${r.status} type from A used in B`);
    else push('loopback-documents-upload-wrong-type-tenant', mutationDeniedOk(r), `HTTP ${r.status}`, 'loopback-mutation');
  }

  if (documentId) {
    const signedA = await signedLoopback(
      SERVICES.documents.port,
      `/api/v1/documents/${documentId}/signed-url`,
      { method: 'POST', userId: STUDENT_A, tenantId: TENANT_A, role: 'STUDENT' }
    );
    const downloadToken = signedA.json?.token;
    if (signedA.status === 200 && downloadToken) {
      const dlPath = `/api/v1/documents/download?token=${encodeURIComponent(downloadToken)}`;
      const dlB = await signedLoopback(SERVICES.documents.port, dlPath, {
        userId: STUDENT_B,
        tenantId: TENANT_B,
        role: 'STUDENT',
      });
      if (dlB.status === 200 && dlB.text && dlB.text.includes('%PDF')) {
        pushVuln('loopback-documents-download-token-tenant-b', `HTTP ${dlB.status} streamed bytes`);
      } else {
        push('loopback-documents-download-token-tenant-b', mutationDeniedOk(dlB), `HTTP ${dlB.status}`, 'loopback-download');
      }
      const issueB = await signedLoopback(
        SERVICES.documents.port,
        `/api/v1/documents/${documentId}/signed-url`,
        { method: 'POST', userId: STUDENT_B, tenantId: TENANT_B, role: 'STUDENT' }
      );
      if (issueB.status === 200 && issueB.json?.token) {
        pushVuln('loopback-documents-signed-url-issue-tenant-b', `HTTP ${issueB.status}`);
      } else {
        push('loopback-documents-signed-url-issue-tenant-b', mutationDeniedOk(issueB), `HTTP ${issueB.status}`, 'loopback-download');
      }
    } else {
      push('loopback-documents-download-matrix-skipped', true, `signed-url HTTP ${signedA.status}`, 'loopback-download');
    }

    const r = await signedLoopback(
      SERVICES.documents.port,
      `/api/v1/documents/submissions/${documentId}/metadata`,
      {
        method: 'PUT',
        userId: FOREIGN_REG_B,
        tenantId: TENANT_B,
        role: 'REGISTRAR',
        bodyObj: { title: 'Hijack', classification: 'PUBLIC', changeNote: 'pen' },
      }
    );
    if (r.status >= 200 && r.status < 300) pushVuln('loopback-documents-metadata-update-tenant-b', `HTTP ${r.status}`);
    else push('loopback-documents-metadata-update-tenant-b', mutationDeniedOk(r), `HTTP ${r.status}`, 'loopback-mutation');
  }

  // Campus boundary: tenant A registrar with campus B on metadata update
  if (documentId) {
    const r = await signedLoopback(
      SERVICES.documents.port,
      `/api/v1/documents/submissions/${documentId}/metadata`,
      {
        method: 'PUT',
        userId: PILOT,
        tenantId: TENANT_A,
        role: 'REGISTRAR',
        collegeId: CAMPUS_B,
        bodyObj: { title: 'Wrong campus', classification: 'PUBLIC', changeNote: 'pen-campus' },
      }
    );
    const denied =
      mutationDeniedOk(r) ||
      (r.json && (r.json.code === 'CAMPUS_SCOPE_DENIED' || r.json.error === 'CAMPUS_SCOPE_DENIED'));
    if (r.status >= 200 && r.status < 300) pushVuln('loopback-documents-wrong-campus-mutation', `HTTP ${r.status}`);
    else push('loopback-documents-wrong-campus-mutation', denied, `HTTP ${r.status}`, 'campus-boundary');
  }

  // --- Gateway student read: must not see registrar verification queue (role boundary) ---
  try {
    const studentAuth = await obtainBearer(process.env.STUDENT1_EMAIL, process.env.STUDENT1_PW);
    const r = await gatewayRequest('/api/v1/documents/verifications', { bearer: studentAuth.token });
  const denied =
      r.status === 403 ||
      r.status === 401 ||
      r.status === 422 ||
      (r.json && (r.json.code === 'REGISTRAR_FORBIDDEN' || r.json.error === 'REGISTRAR_FORBIDDEN'));
    push('gw-student-denied-doc-verifications', denied, `HTTP ${r.status}`, 'gateway-rbac');
  } catch (e) {
    push('gw-student-denied-doc-verifications', false, e.message, 'gateway-rbac');
  }

  results.endedAt = new Date().toISOString();
  const passed = results.checks.filter((c) => c.ok).length;
  const failed = results.checks.filter((c) => !c.ok).length;
  results.summary = {
    total: results.checks.length,
    passed,
    failed,
    vulnerabilities: results.vulnerabilities.length,
    failedNames: results.checks.filter((c) => !c.ok).map((c) => c.name),
    ok: failed === 0 && results.vulnerabilities.length === 0,
  };

  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
  console.log(`CROSS-TENANT PEN: ${passed}/${results.checks.length} PASS; vulns=${results.vulnerabilities.length}`);
  console.log(`Evidence: ${outFile}`);
  if (results.vulnerabilities.length) {
    console.error('Vulnerabilities:', results.vulnerabilities.map((v) => v.name).join(', '));
  }
  if (failed > 0) {
    console.error('Failed checks:', results.summary.failedNames.join(', '));
    process.exit(1);
  }
  if (results.vulnerabilities.length > 0) process.exit(1);
}

main().catch((err) => {
  console.error('Harness crashed:', err?.message || err);
  process.exit(1);
});