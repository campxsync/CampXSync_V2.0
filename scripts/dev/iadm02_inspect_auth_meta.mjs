/**
 * Inspect Auth app_metadata for IADM-02 seeded platform users (no passwords printed).
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
const key = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY;
if (!url || !key) {
  console.error('STOP: missing service role env');
  process.exit(2);
}

const ids = [
  ['R01-A', '54e8308c-ada3-4065-a9fe-51814af6b971'],
  ['R01-B', '3121791f-df40-421f-8a97-f6f471c39250'],
  ['R02', '7c8e1f7a-341c-48c5-ba27-da5aac81d150'],
];

for (const [label, id] of ids) {
  const res = await fetch(`${url}/auth/v1/admin/users/${id}`, {
    headers: { apikey: key, Authorization: `Bearer ${key}` },
  });
  const u = await res.json();
  console.log(
    JSON.stringify({
      label,
      id,
      email: u.email || null,
      http: res.status,
      app_metadata: u.app_metadata || null,
      user_metadata_keys: u.user_metadata ? Object.keys(u.user_metadata) : [],
    })
  );
}
