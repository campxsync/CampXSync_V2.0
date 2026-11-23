/**
 * Prove R01-B and R02 JWTs pass gateway into IADM-02 (no passwords printed).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
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

const accounts = JSON.parse(
  fs.readFileSync(path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'), 'utf8')
);
const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

async function login(email, password) {
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login ${email} -> ${res.status}`);
  return body.access_token;
}

async function gw(token, method, apiPath, bodyObj) {
  const res = await fetch(`http://127.0.0.1:8080${apiPath}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(bodyObj ? { 'Content-Type': 'application/json' } : {}),
    },
    body: bodyObj ? JSON.stringify(bodyObj) : undefined,
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 200) };
  }
  return { status: res.status, errorCode: json.errorCode || null, detail: json.detail || null, draftId: json.draftId || null, statusField: json.status || null };
}

const r01b = await login(accounts.r01b.email, accounts.r01b.password);
const r02 = await login(accounts.r02.email, accounts.r02.password);

const r01bCat = await gw(r01b, 'GET', '/api/v1/iadm/02/configurations');
const r02Deny = await gw(r02, 'POST', '/api/v1/iadm/02/configurations/attendance.min_pct/drafts', {
  proposedValue: '80',
  reason: 'R02 headed-prep deny attendance',
  idempotencyKey: `gw-r02-deny-${Date.now()}`,
});
const r02Allow = await gw(r02, 'POST', '/api/v1/iadm/02/configurations/security.session_timeout_min/drafts', {
  proposedValue: '42',
  reason: 'R02 headed-prep allow session timeout',
  idempotencyKey: `gw-r02-allow-${Date.now()}`,
});

console.log(
  JSON.stringify(
    {
      r01bCatalogue: { status: r01bCat.status, ok: r01bCat.status === 200 },
      r02DenyAttendance: r02Deny,
      r02AllowSession: { status: r02Allow.status, draftId: r02Allow.draftId, errorCode: r02Allow.errorCode },
      pass:
        r01bCat.status === 200 &&
        r02Deny.status === 403 &&
        String(r02Deny.errorCode || '').includes('R02_CEILING') &&
        (r02Allow.status === 200 || r02Allow.status === 201),
    },
    null,
    2
  )
);
