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
async function gw(tok, method, p, body) {
  const res = await fetch(`http://127.0.0.1:8080${p}`, {
    method,
    headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}
const tok = await token(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
console.log('flags before', await gw(tok, 'POST', '/api/v1/iadm/06/dev/outage-flags', { exportFailOnce: true }));
console.log('export', await gw(tok, 'POST', '/api/v1/iadm/06/exports', { scopeType: 'PLATFORM', window: 'month' }));
console.log('flags after', await gw(tok, 'GET', '/api/v1/iadm/06/dev/outage-flags'));
console.log('list', await gw(tok, 'GET', '/api/v1/iadm/06/exports'));
