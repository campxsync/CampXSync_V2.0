/**
 * S3 consumer smoke: allow keys incl. iam.delegation.max_validity_days; deny fees domain.
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
const seed = JSON.parse(
  fs.readFileSync(path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'), 'utf8')
);
const url = process.env.CAMPX_SUPABASE_URL;
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

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

async function gw(token, p) {
  const res = await fetch(`http://127.0.0.1:8080${p}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 300) };
  }
  return { status: res.status, json };
}

const token = await login(seed.consumer.email, seed.consumer.password);
const allow = await gw(
  token,
  '/api/v1/iadm/02/consumer/effective-config?domains=security,attendance,iam'
);
const deny = await gw(token, '/api/v1/iadm/02/consumer/effective-config?domains=fees');
const keys = allow.json.keys || {};

const out = {
  allow: {
    status: allow.status,
    version: allow.json.configVersionId,
    fetchedAt: allow.json.fetchedAt,
    schemaVersion: allow.json.schemaVersion,
    keyCount: Object.keys(keys).length,
    hasDelegation: Object.prototype.hasOwnProperty.call(keys, 'iam.delegation.max_validity_days'),
    hasSession: Object.prototype.hasOwnProperty.call(keys, 'security.session_timeout_min'),
    keys,
  },
  denyFees: {
    status: deny.status,
    errorCode: deny.json.errorCode,
    pass: deny.status === 403 && String(deny.json.errorCode || '').includes('DOMAIN_DENIED'),
  },
  pass:
    allow.status === 200 &&
    allow.json.schemaVersion === 1 &&
    !!allow.json.configVersionId &&
    !!allow.json.fetchedAt &&
    Object.prototype.hasOwnProperty.call(keys, 'iam.delegation.max_validity_days') &&
    deny.status === 403,
};

fs.writeFileSync(
  path.join(root, 'docs/iadm/iadm-02/evidence/S3_CONSUMER_SMOKE.json'),
  JSON.stringify(out, null, 2)
);
console.log(JSON.stringify(out, null, 2));
