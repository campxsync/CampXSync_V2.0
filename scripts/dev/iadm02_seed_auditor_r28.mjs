/**
 * DEV seed: Auditor (R28) for IADM-02-US-028 headed demo.
 * Never prints password. Writes docs/iadm/iadm-02/evidence/.local/seed_accounts.json (merge).
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

const URL = process.env.CAMPX_SUPABASE_URL;
const KEY = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
const TENANT = '72ecbfe3-2077-41b5-8a20-b6d3e045311b';
const EMAIL = 'auditor.iadm02.dev@campx.local';
const localPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');

if (!URL || !KEY || !URL.includes('azulbaxszsqxymqmlwfe')) {
  console.error('STOP: DEV supabase env missing');
  process.exit(2);
}

let seed = {};
if (fs.existsSync(localPath)) seed = JSON.parse(fs.readFileSync(localPath, 'utf8'));
const password = seed.auditor?.password || ('Iadm02R28!' + crypto.randomBytes(4).toString('hex'));

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
      app_metadata: { role: 'AUDITOR', tenant_id: TENANT },
      user_metadata: { full_name: 'IADM-02 Auditor R28', iadm02_seed: true },
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (res.ok) return { id: body.id || body.user?.id, created: true };
  const list = await fetch(`${URL}/auth/v1/admin/users?page=1&per_page=200`, {
    headers: { apikey: KEY, Authorization: `Bearer ${KEY}` },
  });
  const data = await list.json();
  const found = (data.users || []).find((u) => u.email === EMAIL);
  if (!found) throw new Error('auditor create/list failed ' + res.status);
  // ensure app_metadata
  await fetch(`${URL}/auth/v1/admin/users/${found.id}`, {
    method: 'PUT',
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      app_metadata: { ...(found.app_metadata || {}), role: 'AUDITOR', tenant_id: TENANT },
    }),
  });
  return { id: found.id, created: false };
}

const user = await createOrGetUser();
seed.auditor = {
  email: EMAIL,
  userId: user.id,
  created: user.created,
  password,
};
fs.mkdirSync(path.dirname(localPath), { recursive: true });
fs.writeFileSync(localPath, JSON.stringify(seed, null, 2));
console.log(
  JSON.stringify({
    email: EMAIL,
    userId: user.id,
    created: user.created,
    note: 'password in .local only; apply SQL for profile+role via MCP next',
  })
);
