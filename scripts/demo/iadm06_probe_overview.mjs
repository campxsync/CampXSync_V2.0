import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
function loadEnv(rel) {
  const p = path.join(root, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  }
}
loadEnv('.env.audit');
loadEnv('frontend/.env.local');
async function token(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login ${res.status}`);
  return body.access_token;
}
const tok = await token(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
for (const p of [
  '/api/v1/iadm/06/overview/platform?window=month',
  '/api/v1/iadm/06/catalogue',
  '/api/v1/iadm/06/settings/suppression',
]) {
  const res = await fetch(`http://127.0.0.1:8080${p}`, { headers: { Authorization: `Bearer ${tok}` } });
  const body = await res.json();
  console.log(p, res.status, JSON.stringify(body).slice(0, 500));
}
