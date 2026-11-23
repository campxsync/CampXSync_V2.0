/**
 * S3 US-026: IADM-01 reads platform defaults via IADM-02 consumer contract.
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

const token = await login(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
const res = await fetch('http://127.0.0.1:8080/api/v1/iadm/01/platform-defaults', {
  headers: { Authorization: `Bearer ${token}` },
});
const json = await res.json();
const keys = json.keys || {};
const out = {
  status: res.status,
  cacheStatus: json.cacheStatus,
  schemaVersion: json.schemaVersion,
  configVersionId: json.configVersionId,
  onboardingSessionTimeoutMin: json.onboardingSessionTimeoutMin,
  hasDelegation: Object.prototype.hasOwnProperty.call(keys, 'iam.delegation.max_validity_days'),
  hasSession: Object.prototype.hasOwnProperty.call(keys, 'security.session_timeout_min'),
  keys,
  pass:
    res.status === 200 &&
    json.schemaVersion === 1 &&
    !!json.configVersionId &&
    !!json.onboardingSessionTimeoutMin &&
    Object.prototype.hasOwnProperty.call(keys, 'iam.delegation.max_validity_days'),
};

fs.writeFileSync(
  path.join(root, 'docs/iadm/iadm-02/evidence/S3_IADM01_DEFAULTS.json'),
  JSON.stringify(out, null, 2)
);
console.log(JSON.stringify(out, null, 2));
