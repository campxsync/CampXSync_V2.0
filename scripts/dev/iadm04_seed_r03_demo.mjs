/**
 * DEV seed: Institution Admin (R03) for IADM-04 evidence 4a gate film.
 * Never prints password. Merges into docs/iadm/iadm-02/evidence/.local/seed_accounts.json.
 * Apply companion SQL migration after this script for tenant/profile/role/subscription.
 *
 * Usage: node scripts/dev/iadm04_seed_r03_demo.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import crypto from 'crypto';

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

const URL = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
const KEY = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
const TENANT = 'd4000000-0000-4000-8000-000000000004';
const EMAIL = 'institution.iadm04.demo@campx.local';
const localPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');

if (!URL || !KEY || !String(URL).includes('azulbaxszsqxymqmlwfe')) {
  console.error('STOP: DEV supabase env missing or non-DEV URL');
  process.exit(2);
}

let seed = {};
if (fs.existsSync(localPath)) seed = JSON.parse(fs.readFileSync(localPath, 'utf8'));
const password = seed.r03demo?.password || ('Iadm04R03!' + crypto.randomBytes(4).toString('hex'));

async function createOrGetUser() {
  const res = await fetch(`${URL}/auth/v1/admin/users`, {
    method: 'POST',
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      email: EMAIL,
      password,
      email_confirm: true,
      app_metadata: { role: 'INSTITUTION_ADMIN', tenant_id: TENANT },
      user_metadata: { full_name: 'IADM-04 DEMO Institution Admin (R03)', iadm04_seed: true },
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (res.ok) return { id: body.id || body.user?.id, created: true };
  const list = await fetch(`${URL}/auth/v1/admin/users?page=1&per_page=200`, {
    headers: { apikey: KEY, Authorization: `Bearer ${KEY}` },
  });
  const data = await list.json();
  const found = (data.users || []).find((u) => u.email === EMAIL);
  if (!found) throw new Error('R03 create/list failed ' + res.status + ' ' + JSON.stringify(body).slice(0, 200));
  await fetch(`${URL}/auth/v1/admin/users/${found.id}`, {
    method: 'PUT',
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      password,
      email_confirm: true,
      app_metadata: {
        ...(found.app_metadata || {}),
        role: 'INSTITUTION_ADMIN',
        tenant_id: TENANT,
      },
    }),
  });
  return { id: found.id, created: false };
}

const user = await createOrGetUser();
seed.r03demo = {
  email: EMAIL,
  userId: user.id,
  tenantId: TENANT,
  tenantCode: 'IADM04_DEMO',
  created: user.created,
  password,
};
fs.mkdirSync(path.dirname(localPath), { recursive: true });
fs.writeFileSync(localPath, JSON.stringify(seed, null, 2));
console.log(
  JSON.stringify({
    email: EMAIL,
    userId: user.id,
    tenantId: TENANT,
    created: user.created,
    note: 'password stored in .local seed only — never printed; apply SQL next',
  })
);
