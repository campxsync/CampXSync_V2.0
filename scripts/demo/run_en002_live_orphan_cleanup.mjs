/**
 * STM-06-EN-002 live orphan cleanup against Dev Supabase only.
 * Uses document-service orphan cleanup API (confirmExecute=true).
 * Never prints secrets or object bytes.
 *
 * Prerequisites: document-service (:8098) with CAMPX_SUPABASE_* for azulbaxszsqxymqmlwfe.
 */
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');
const outFile = path.join(root, 'docs/reports/STM06_EN002_LIVE_ORPHAN_CLEANUP_2026-11-16.json');

const EXPECTED_DEV = 'azulbaxszsqxymqmlwfe';
const FORBIDDEN = ['ekspahbsbwdpraaqfqui', 'kpwdvvcschankgggitnj'];
const TENANT_A = 'a0000000-0000-0000-0000-000000000001';
const TENANT_B = 'b0000000-0000-0000-0000-000000000002';
const CAMPUS_A = 'a0000000-0000-0000-0000-000000000002';
const ADMIN_A = '70a8ffae-b5ad-4c61-a401-16f220893df4';
const ADMIN_B = '80a8ffae-b5ad-4c61-a401-16f220893df4';
const DOC_PORT = Number(process.env.CAMPX_DOC_PORT || 8098);
const DOC_BASE = `http://127.0.0.1:${DOC_PORT}`;

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

function requireSecret() {
  const secret = process.env.CAMPX_INTERNAL_SECRET || process.env.CAMPX_GATEWAY_HMAC_SECRET;
  if (!secret) throw new Error('CAMPX_INTERNAL_SECRET or CAMPX_GATEWAY_HMAC_SECRET required');
  return secret;
}

function assertDevProject() {
  const url = process.env.CAMPX_SUPABASE_URL || '';
  const key = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || '';
  if (!url || !key) throw new Error('CAMPX_SUPABASE_URL / SERVICE_ROLE_KEY required');
  if (!url.includes(EXPECTED_DEV)) {
    throw new Error(`Refused: CAMPX_SUPABASE_URL does not target ${EXPECTED_DEV}`);
  }
  for (const bad of FORBIDDEN) {
    if (url.includes(bad)) throw new Error(`Refused: forbidden project ref ${bad}`);
  }
  return {
    urlHost: (() => {
      try {
        return new URL(url).host;
      } catch {
        return 'invalid-url';
      }
    })(),
    urlLen: url.length,
    keyLen: key.length,
    bucket: process.env.CAMPX_STORAGE_BUCKET || 'student-documents',
  };
}

async function signedJson(urlPath, { method = 'POST', userId, tenantId, role, bodyObj, collegeId }) {
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
      'X-Trace-Id': crypto.randomUUID().replace(/-/g, ''),
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
  return { status: res.status, text: text.slice(0, 2000), json };
}

async function storagePut(objectPath, bytes) {
  const url = process.env.CAMPX_SUPABASE_URL.replace(/\/$/, '');
  const key = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY;
  const bucket = process.env.CAMPX_STORAGE_BUCKET || 'student-documents';
  const res = await fetch(`${url}/storage/v1/object/${bucket}/${objectPath}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      apikey: key,
      'Content-Type': 'application/pdf',
      'x-upsert': 'true',
    },
    body: bytes,
  });
  return res.status;
}

async function storageGetStatus(objectPath) {
  const url = process.env.CAMPX_SUPABASE_URL.replace(/\/$/, '');
  const key = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY;
  const bucket = process.env.CAMPX_STORAGE_BUCKET || 'student-documents';
  const res = await fetch(`${url}/storage/v1/object/${bucket}/${objectPath}`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${key}`, apikey: key },
  });
  return res.status;
}

async function storageList(prefix) {
  const url = process.env.CAMPX_SUPABASE_URL.replace(/\/$/, '');
  const key = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY;
  const bucket = process.env.CAMPX_STORAGE_BUCKET || 'student-documents';
  const res = await fetch(`${url}/storage/v1/object/list/${bucket}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      apikey: key,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ prefix, limit: 100, offset: 0 }),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }
  return { status: res.status, items: Array.isArray(json) ? json : [] };
}

function passFail(ok, detail) {
  return { ok: !!ok, detail };
}

async function main() {
  const project = assertDevProject();
  const results = {
    at: new Date().toISOString(),
    task: 'STM-06-EN-002-live-orphan-cleanup',
    projectRefExpected: EXPECTED_DEV,
    project: project,
    docBase: DOC_BASE,
    checks: [],
    cleanupTenantA: null,
    cleanupTenantB: null,
    plantedOrphanPath: null,
    note: 'No secrets, tokens, or object byte contents recorded.',
  };

  // Health
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
      detail: String(e.message || e).slice(0, 200),
    });
    fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
    console.error('document-service not healthy; aborting before any Storage delete');
    process.exit(1);
  }

  results.checks.push({
    name: 'project-ref-dev-only',
    ...passFail(project.urlHost.includes(EXPECTED_DEV), `host=${project.urlHost}`),
  });

  // Plant confirmed orphan under tenant A (not in DB)
  const orphanId = crypto.randomUUID();
  const orphanPath = `tenants/${TENANT_A}/students/00000000-0000-0000-0000-00000000orph/${orphanId}/v1-orphan-cleanup-probe.pdf`;
  results.plantedOrphanPathLen = orphanPath.length;
  const putStatus = await storagePut(orphanPath, Buffer.from('%PDF-1.4 orphan-cleanup-probe', 'ascii'));
  results.checks.push({
    name: 'plant-orphan-tenant-a',
    ...passFail(putStatus >= 200 && putStatus < 300, `HTTP ${putStatus}`),
  });
  if (!(putStatus >= 200 && putStatus < 300)) {
    fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
    process.exit(1);
  }

  // Cross-tenant object under B (must be retained by A cleanup)
  const foreignPath = `tenants/${TENANT_B}/students/00000000-0000-0000-0000-00000000xbt/${orphanId}/v1-foreign.pdf`;
  const foreignPut = await storagePut(foreignPath, Buffer.from('%PDF-1.4 foreign-tenant', 'ascii'));
  results.checks.push({
    name: 'plant-foreign-tenant-b',
    ...passFail(foreignPut >= 200 && foreignPut < 300, `HTTP ${foreignPut}`),
  });

  // Pre list A
  const listABefore = await storageList(`tenants/${TENANT_A}/`);
  results.checks.push({
    name: 'list-tenant-a-before',
    ...passFail(listABefore.status === 200, `HTTP ${listABefore.status} names=${listABefore.items.length}`),
  });

  // Execute cleanup for tenant A (owner-authorized confirmExecute)
  const cleanupA = await signedJson('/api/v1/documents/storage/orphans/cleanup', {
    method: 'POST',
    userId: ADMIN_A,
    tenantId: TENANT_A,
    role: 'COLLEGE_ADMIN',
    bodyObj: { confirmExecute: true },
  });
  results.cleanupTenantA = {
    httpStatus: cleanupA.status,
    dryRun: cleanupA.json?.dryRun,
    status: cleanupA.json?.status,
    scannedCount: cleanupA.json?.scannedCount,
    confirmedOrphanCount: cleanupA.json?.confirmedOrphanCount,
    referencedCount: cleanupA.json?.referencedCount,
    crossTenantCount: cleanupA.json?.crossTenantCount,
    ambiguousCount: cleanupA.json?.ambiguousCount,
    deletedCount: cleanupA.json?.deletedCount,
    deleteFailedCount: cleanupA.json?.deleteFailedCount,
    candidateClassCounts: summarizeCandidates(cleanupA.json?.candidates),
  };
  results.checks.push({
    name: 'cleanup-tenant-a-execute',
    ...passFail(
      cleanupA.status === 200 && cleanupA.json && cleanupA.json.dryRun === false && cleanupA.json.deletedCount >= 1,
      `HTTP ${cleanupA.status} status=${cleanupA.json?.status} deleted=${cleanupA.json?.deletedCount} scanned=${cleanupA.json?.scannedCount}`,
    ),
  });

  // Verify planted orphan gone
  const orphanGone = await storageGetStatus(orphanPath);
  results.checks.push({
    name: 'planted-orphan-deleted',
    ...passFail(orphanGone === 404 || orphanGone === 400, `GET HTTP ${orphanGone}`),
  });

  // Foreign tenant object must remain after tenant A cleanup
  const foreignStill = await storageGetStatus(foreignPath);
  results.checks.push({
    name: 'foreign-tenant-retained-after-a-cleanup',
    ...passFail(foreignStill >= 200 && foreignStill < 300, `GET HTTP ${foreignStill}`),
  });

  // Tenant B admin attempting to delete tenant A path via subset must retain (cross-tenant)
  const crossDeny = await signedJson('/api/v1/documents/storage/orphans/cleanup', {
    method: 'POST',
    userId: ADMIN_B,
    tenantId: TENANT_B,
    role: 'COLLEGE_ADMIN',
    collegeId: 'b0000000-0000-0000-0000-000000000002',
    bodyObj: { confirmExecute: true, objectPaths: [orphanPath] },
  });
  results.checks.push({
    name: 'cross-tenant-delete-denied',
    ...passFail(
      crossDeny.status === 200 &&
        (crossDeny.json?.deletedCount || 0) === 0 &&
        (crossDeny.json?.crossTenantCount || 0) >= 1,
      `HTTP ${crossDeny.status} deleted=${crossDeny.json?.deletedCount} crossTenant=${crossDeny.json?.crossTenantCount}`,
    ),
  });

  // Cleanup tenant B (removes planted foreign probe)
  const cleanupB = await signedJson('/api/v1/documents/storage/orphans/cleanup', {
    method: 'POST',
    userId: ADMIN_B,
    tenantId: TENANT_B,
    role: 'COLLEGE_ADMIN',
    collegeId: 'b0000000-0000-0000-0000-000000000002',
    bodyObj: { confirmExecute: true },
  });
  results.cleanupTenantB = {
    httpStatus: cleanupB.status,
    status: cleanupB.json?.status,
    scannedCount: cleanupB.json?.scannedCount,
    confirmedOrphanCount: cleanupB.json?.confirmedOrphanCount,
    referencedCount: cleanupB.json?.referencedCount,
    deletedCount: cleanupB.json?.deletedCount,
    deleteFailedCount: cleanupB.json?.deleteFailedCount,
  };
  results.checks.push({
    name: 'cleanup-tenant-b-execute',
    ...passFail(cleanupB.status === 200 && cleanupB.json?.dryRun === false, `HTTP ${cleanupB.status} deleted=${cleanupB.json?.deletedCount}`),
  });

  const foreignGone = await storageGetStatus(foreignPath);
  results.checks.push({
    name: 'foreign-probe-cleaned-by-tenant-b',
    ...passFail(foreignGone === 404 || foreignGone === 400, `GET HTTP ${foreignGone}`),
  });

  // Idempotent re-run tenant A
  const idempotent = await signedJson('/api/v1/documents/storage/orphans/cleanup', {
    method: 'POST',
    userId: ADMIN_A,
    tenantId: TENANT_A,
    role: 'COLLEGE_ADMIN',
    bodyObj: { confirmExecute: true },
  });
  results.checks.push({
    name: 'idempotent-rerun-tenant-a',
    ...passFail(
      idempotent.status === 200 && (idempotent.json?.deleteFailedCount || 0) === 0,
      `HTTP ${idempotent.status} deleted=${idempotent.json?.deletedCount} failed=${idempotent.json?.deleteFailedCount}`,
    ),
  });

  const failed = results.checks.filter((c) => !c.ok).map((c) => c.name);
  results.summary = {
    total: results.checks.length,
    passed: results.checks.length - failed.length,
    failed: failed.length,
    failedNames: failed,
  };

  fs.writeFileSync(outFile, JSON.stringify(results, null, 2));
  console.log(JSON.stringify({
    evidence: outFile,
    summary: results.summary,
    cleanupTenantA: results.cleanupTenantA,
    cleanupTenantB: results.cleanupTenantB,
  }, null, 2));
  process.exit(failed.length ? 1 : 0);
}

function summarizeCandidates(candidates) {
  const out = { CLEAN: 0, CONFIRMED_ORPHAN: 0, REFERENCED: 0, CROSS_TENANT: 0, AMBIGUOUS: 0, DELETED: 0, RETAIN: 0, other: 0 };
  if (!Array.isArray(candidates)) return out;
  for (const c of candidates) {
    const cls = c.classification || 'other';
    if (out[cls] != null) out[cls]++;
    else out.other++;
    if (c.disposition === 'DELETED') out.DELETED++;
    if (c.disposition === 'RETAIN') out.RETAIN++;
  }
  return out;
}

main().catch((e) => {
  console.error(String(e.message || e).slice(0, 300));
  process.exit(1);
});
