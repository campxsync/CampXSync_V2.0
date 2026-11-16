/**
 * Frontend 1b integrated smoke — HMAC → document-service → Supabase Storage.
 * Covers: successful contentBase64 upload, MIME/size/missing-content rejection,
 * tenant-prefixed storage path, cross-tenant denial. Does not print secrets.
 *
 * Prerequisites: document-service (:8098) with CAMPX_SUPABASE_* from .env.audit.
 * Usage (from repo root, after loading env):
 *   node scripts/demo/smoke_frontend_1b_contentbase64.mjs
 */
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const outFile = path.join(root, 'docs/reports/STM06_FRONTEND_1B_CONTENTBASE64_SMOKE_2026-11-16.json');

const TENANT_A = 'a0000000-0000-0000-0000-000000000001';
const TENANT_B = 'b0000000-0000-0000-0000-000000000002';
const CAMPUS_A = 'a0000000-0000-0000-0000-000000000002';
const ADMIN_A = '70a8ffae-b5ad-4c61-a401-16f220893df4';
const STUDENT_A = '81dd67d3-1d5f-4d8e-8061-a6764861f924';
const STUDENT_B = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';
const DOC_PORT = Number(process.env.CAMPX_DOC_PORT || 8098);
const DOC_BASE = `http://127.0.0.1:${DOC_PORT}`;

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

function requireSecret() {
  const secret = process.env.CAMPX_INTERNAL_SECRET || process.env.CAMPX_GATEWAY_HMAC_SECRET;
  if (!secret) throw new Error('CAMPX_INTERNAL_SECRET or CAMPX_GATEWAY_HMAC_SECRET required');
  return secret;
}

function pdfBytes(label) {
  return Buffer.from(`%PDF-1.4 frontend-1b ${label}`, 'ascii');
}

function sha256Hex(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}

async function signedJson(urlPath, { method = 'GET', userId, tenantId, role, bodyObj, collegeId }) {
  const secret = requireSecret();
  const body = bodyObj != null ? Buffer.from(JSON.stringify(bodyObj), 'utf8') : Buffer.alloc(0);
  const bodySha = crypto.createHash('sha256').update(body).digest('hex');
  const timestamp = String(Date.now());
  const canonical = `${timestamp}:${method}:${urlPath}:${userId}:${tenantId}:${bodySha}`;
  const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex');
  const res = await fetch(`${DOC_BASE}${urlPath}`, {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-User-Id': userId,
      'X-Tenant-Id': tenantId,
      'X-User-Role': role,
      'X-College-Id': collegeId || CAMPUS_A,
      'X-Gateway-Timestamp': timestamp,
      'X-Gateway-Signature': signature,
    },
    body: body.length ? body : undefined,
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return { status: res.status, text: text.slice(0, 600), json };
}

function passFail(ok, detail) {
  return { ok: !!ok, detail };
}

async function main() {
  const results = {
    at: new Date().toISOString(),
    task: 'frontend-1b-contentBase64',
    docBase: DOC_BASE,
    checks: [],
  };

  // Health + storage configured
  try {
    const health = await fetch(`${DOC_BASE}/health`);
    const ht = await health.text();
    results.checks.push({
      name: 'document-service-health',
      ...passFail(health.status === 200, `HTTP ${health.status} ${ht.slice(0, 120)}`),
    });
  } catch (e) {
    results.checks.push({
      name: 'document-service-health',
      ok: false,
      detail: String(e.message || e),
    });
    fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
    console.error('document-service not reachable; wrote', outFile);
    process.exit(1);
  }

  const storageEnv =
    !!(process.env.CAMPX_SUPABASE_URL && process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY);
  results.checks.push({
    name: 'storage-env-present-in-smoke-process',
    ...passFail(storageEnv, storageEnv ? 'url+key present (values not logged)' : 'missing CAMPX_SUPABASE_*'),
  });

  // Admin creates document type
  const typeCode = `DOC-1B-${Date.now().toString(36).toUpperCase()}`;
  const typeRes = await signedJson('/api/v1/documents/types', {
    method: 'POST',
    userId: ADMIN_A,
    tenantId: TENANT_A,
    role: 'COLLEGE_ADMIN',
    bodyObj: {
      code: typeCode,
      name: 'Frontend 1b Upload Type',
      category: 'ACADEMIC',
      module: 'STUDENT',
      defaultClassification: 'CONFIDENTIAL',
      isActive: true,
    },
  });
  const typeId = typeRes.json && (typeRes.json.id || typeRes.json.documentTypeId);
  results.checks.push({
    name: 'admin-configure-document-type',
    ...passFail(typeRes.status === 201 || typeRes.status === 200, `HTTP ${typeRes.status} id=${typeId || 'n/a'}`),
  });
  if (!typeId) {
    fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
    console.error('Failed to create document type');
    process.exit(1);
  }

  // Reject: missing contentBase64
  {
    const r = await signedJson('/api/v1/documents/submissions', {
      method: 'POST',
      userId: STUDENT_A,
      tenantId: TENANT_A,
      role: 'STUDENT',
      bodyObj: {
        documentTypeId: typeId,
        title: 'Missing Bytes',
        mimeType: 'application/pdf',
        sizeBytes: 32,
        fileName: 'missing.pdf',
        checksumSha256: 'a'.repeat(64),
      },
    });
    const code = r.json && (r.json.code || r.json.errorCode);
    results.checks.push({
      name: 'reject-missing-contentBase64',
      ...passFail(r.status === 422 && String(code).includes('CONTENT'), `HTTP ${r.status} code=${code}`),
    });
  }

  // Reject: unsupported MIME (text/plain)
  {
    const bytes = Buffer.from('not-a-pdf');
    const r = await signedJson('/api/v1/documents/submissions', {
      method: 'POST',
      userId: STUDENT_A,
      tenantId: TENANT_A,
      role: 'STUDENT',
      bodyObj: {
        documentTypeId: typeId,
        title: 'Bad MIME',
        mimeType: 'text/plain',
        sizeBytes: bytes.length,
        fileName: 'bad.txt',
        checksumSha256: sha256Hex(bytes),
        contentBase64: bytes.toString('base64'),
      },
    });
    const code = r.json && (r.json.code || r.json.errorCode);
    results.checks.push({
      name: 'reject-unsupported-mime',
      ...passFail(r.status === 422, `HTTP ${r.status} code=${code}`),
    });
  }

  // Reject: file too large (>10MB declared)
  {
    const r = await signedJson('/api/v1/documents/submissions', {
      method: 'POST',
      userId: STUDENT_A,
      tenantId: TENANT_A,
      role: 'STUDENT',
      bodyObj: {
        documentTypeId: typeId,
        title: 'Too Large',
        mimeType: 'application/pdf',
        sizeBytes: 10 * 1024 * 1024 + 1,
        fileName: 'huge.pdf',
        checksumSha256: 'b'.repeat(64),
        contentBase64: Buffer.from('%PDF-1.4 tiny').toString('base64'),
      },
    });
    const code = r.json && (r.json.code || r.json.errorCode);
    results.checks.push({
      name: 'reject-file-too-large',
      ...passFail(r.status === 422, `HTTP ${r.status} code=${code}`),
    });
  }

  // Reject: MIME magic mismatch
  {
    const bytes = Buffer.from('NOT_A_PDF_FILE_BYTES');
    const r = await signedJson('/api/v1/documents/submissions', {
      method: 'POST',
      userId: STUDENT_A,
      tenantId: TENANT_A,
      role: 'STUDENT',
      bodyObj: {
        documentTypeId: typeId,
        title: 'Magic Mismatch',
        mimeType: 'application/pdf',
        sizeBytes: bytes.length,
        fileName: 'fake.pdf',
        checksumSha256: sha256Hex(bytes),
        contentBase64: bytes.toString('base64'),
      },
    });
    const code = r.json && (r.json.code || r.json.errorCode);
    results.checks.push({
      name: 'reject-mime-signature-mismatch',
      ...passFail(r.status === 422, `HTTP ${r.status} code=${code}`),
    });
  }

  // Success: valid PDF upload
  const pdf = pdfBytes(`ok-${Date.now()}`);
  const up = await signedJson('/api/v1/documents/submissions', {
    method: 'POST',
    userId: STUDENT_A,
    tenantId: TENANT_A,
    role: 'STUDENT',
    bodyObj: {
      documentTypeId: typeId,
      title: 'Frontend 1b Live Upload',
      mimeType: 'application/pdf',
      sizeBytes: pdf.length,
      fileName: 'frontend_1b_live.pdf',
      checksumSha256: sha256Hex(pdf),
      contentBase64: pdf.toString('base64'),
      changeNote: '1b integrated smoke',
    },
  });
  const docId = up.json && up.json.id;
  const storagePath = up.json && up.json.storagePath;
  results.checks.push({
    name: 'upload-contentBase64-success',
    ...passFail(
      (up.status === 201 || up.status === 200) && !!docId,
      `HTTP ${up.status} id=${docId || 'n/a'} path=${storagePath ? 'present' : 'missing'}`
    ),
  });
  results.checks.push({
    name: 'storage-path-tenant-prefixed',
    ...passFail(
      typeof storagePath === 'string' && storagePath.startsWith(`tenants/${TENANT_A}/`),
      storagePath ? `prefix ok (${storagePath.split('/').slice(0, 3).join('/')}/…)` : 'no path'
    ),
  });

  // Cross-tenant: student in tenant B must not access tenant A audit trail
  if (docId) {
    const cross = await signedJson(`/api/v1/documents/submissions/${docId}/audit-trail`, {
      method: 'GET',
      userId: STUDENT_B,
      tenantId: TENANT_B,
      role: 'STUDENT',
    });
    results.checks.push({
      name: 'cross-tenant-audit-denied',
      ...passFail(cross.status === 403 || cross.status === 404, `HTTP ${cross.status}`),
    });
  } else {
    results.checks.push({ name: 'cross-tenant-audit-denied', ok: false, detail: 'skipped — no docId' });
  }

  // Owner can list own submissions
  {
    const list = await signedJson('/api/v1/documents/submissions', {
      method: 'GET',
      userId: STUDENT_A,
      tenantId: TENANT_A,
      role: 'STUDENT',
    });
    const items = (list.json && (list.json.items || list.json)) || [];
    const found = Array.isArray(items) && items.some((d) => d.id === docId);
    results.checks.push({
      name: 'owner-list-includes-upload',
      ...passFail(list.status === 200 && found, `HTTP ${list.status} found=${found}`),
    });
  }

  // Frontend must not ship service-role credentials (static scan)
  {
    const page = fs.readFileSync(
      path.join(root, 'frontend/src/app/(dashboard)/student/documents/page.tsx'),
      'utf8'
    );
    const bff = fs.readFileSync(path.join(root, 'frontend/src/app/api/student/documents/route.ts'), 'utf8');
    const leak =
      /CAMPX_SUPABASE_SERVICE_ROLE_KEY|service_role|serviceRoleKey/.test(page) ||
      /CAMPX_SUPABASE_SERVICE_ROLE_KEY|serviceRoleKey\s*[:=]/.test(bff);
    const usesContent = page.includes('contentBase64') && bff.includes('contentBase64');
    results.checks.push({
      name: 'frontend-no-service-role-credential',
      ...passFail(!leak && usesContent, leak ? 'credential pattern found' : 'clean + contentBase64 wired'),
    });
  }

  // BFF guard module rejects secret fields (already unit-tested; record presence)
  results.checks.push({
    name: 'bff-guard-module-present',
    ...passFail(
      fs.existsSync(path.join(root, 'frontend/src/lib/documents/upload-payload-guard.ts')),
      'upload-payload-guard.ts'
    ),
  });

  const failed = results.checks.filter((c) => !c.ok);
  results.summary = {
    total: results.checks.length,
    passed: results.checks.length - failed.length,
    failed: failed.length,
    ok: failed.length === 0,
  };
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
  console.log(
    `Frontend 1b smoke: ${results.summary.passed}/${results.summary.total} PASS → ${outFile}`
  );
  for (const c of results.checks) {
    console.log(`${c.ok ? '[PASS]' : '[FAIL]'} ${c.name}: ${c.detail}`);
  }
  if (failed.length) process.exit(1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
