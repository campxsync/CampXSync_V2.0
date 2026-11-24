/**
 * US-014 API smoke: inactive then active on demo INSTITUTION_ADMIN profile (DEV).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s1');
fs.mkdirSync(evidence, { recursive: true });

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

const supabaseUrl = (process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '').replace(
  /\/+$/,
  ''
);
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const email = process.env.PLATFORM_ADMIN_EMAIL;
const password = process.env.PLATFORM_ADMIN_PW;
const profileId = 'c03a0000-0000-4000-8000-000000000003';

const login = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
  method: 'POST',
  headers: { apikey: anon, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password }),
});
const session = await login.json();
if (!login.ok || !session.access_token) {
  console.error('login failed');
  process.exit(1);
}

async function setStatus(status, reason) {
  const res = await fetch(`http://127.0.0.1:8080/api/v1/iadm/03/profiles/${profileId}/status`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${session.access_token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': `s1-status-${status}-${Date.now()}`,
    },
    body: JSON.stringify({ status, reason }),
  });
  const text = await res.text();
  return { status: res.status, body: text };
}

const inactive = await setStatus('INACTIVE', 'S1 headed US-014 smoke — set inactive');
const active = await setStatus('ACTIVE', 'S1 headed US-014 smoke — restore active');
const out = { profileId, inactive, active, at: new Date().toISOString() };
fs.writeFileSync(path.join(evidence, 'S1_US014_STATUS_SMOKE.json'), JSON.stringify(out, null, 2));
console.log(JSON.stringify({ ok: inactive.status === 200 && active.status === 200, inactive: inactive.status, active: active.status }));
process.exit(inactive.status === 200 && active.status === 200 ? 0 : 1);
