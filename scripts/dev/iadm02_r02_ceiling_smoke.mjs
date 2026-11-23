/**
 * IADM-02 S1 smoke: R02 may draft security.session_timeout_min only.
 * Calls IADM-02 :8100 with gateway HMAC (bypasses JWT tenant claim). Never prints secrets.
 */
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function loadEnvFile(rel) {
  const p = path.join(root, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
}
loadEnvFile('.env.audit');
loadEnvFile('frontend/.env.local');
loadEnvFile('.env');

const accounts = JSON.parse(
  fs.readFileSync(path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'), 'utf8')
);
const secret = process.env.CAMPX_INTERNAL_SECRET || process.env.CAMPX_GATEWAY_HMAC_SECRET;
if (!secret) {
  console.error('STOP: missing CAMPX_INTERNAL_SECRET');
  process.exit(2);
}

const R02 = accounts.r02.userId;
const EMPTY_TENANT = '';

async function createDraft(key, value, reason) {
  const urlPath = `/api/v1/iadm/02/configurations/${encodeURIComponent(key)}/drafts`;
  const bodyObj = {
    proposedValue: value,
    reason,
    idempotencyKey: `r02-smoke-${key}-${Date.now()}`,
  };
  const body = Buffer.from(JSON.stringify(bodyObj), 'utf8');
  const bodySha = crypto.createHash('sha256').update(body).digest('hex');
  const timestamp = String(Date.now());
  const canonical = `${timestamp}:POST:${urlPath}:${R02}:${EMPTY_TENANT}:${bodySha}`;
  const signature = crypto.createHmac('sha256', secret).update(canonical, 'utf8').digest('hex');
  const res = await fetch(`http://127.0.0.1:8100${urlPath}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-User-Id': R02,
      'X-Tenant-Id': EMPTY_TENANT,
      'X-User-Role': 'SYSTEM_ADMIN',
      'X-Gateway-Timestamp': timestamp,
      'X-Gateway-Signature': signature,
    },
    body,
  });
  const text = await res.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 240) };
  }
  return {
    status: res.status,
    errorCode: parsed.errorCode || parsed.code || null,
    detail: parsed.detail || parsed.message || null,
    draftId: parsed.draftId || null,
  };
}

const deny = await createDraft('attendance.min_pct', '80', 'R02 ceiling smoke deny attendance');
const allow = await createDraft('security.session_timeout_min', '40', 'R02 ceiling smoke allow session timeout');
const pass =
  deny.status === 403 &&
  String(deny.errorCode || '').includes('R02_CEILING') &&
  (allow.status === 200 || allow.status === 201) &&
  !!allow.draftId;

console.log(
  JSON.stringify(
    {
      persona: 'R02 SYSTEM_ADMIN',
      userId: R02,
      via: 'HMAC direct :8100',
      denyAttendance: deny,
      allowSessionTimeout: { status: allow.status, errorCode: allow.errorCode, draftId: allow.draftId },
      pass,
    },
    null,
    2
  )
);
process.exit(pass ? 0 : 1);
