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
const key = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY;
const id = seed.consumer.userId;
const get = await fetch(`${url}/auth/v1/admin/users/${id}`, {
  headers: { apikey: key, Authorization: `Bearer ${key}` },
});
const u = await get.json();
const meta = {
  ...(u.app_metadata || {}),
  tenant_id: 'a0000000-0000-0000-0000-000000000001',
  role: 'SERVICE',
};
const put = await fetch(`${url}/auth/v1/admin/users/${id}`, {
  method: 'PUT',
  headers: { apikey: key, Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ app_metadata: meta }),
});
const b = await put.json();
console.log(
  JSON.stringify({
    http: put.status,
    tenant_id: b.app_metadata?.tenant_id || null,
    role: b.app_metadata?.role || null,
  })
);
