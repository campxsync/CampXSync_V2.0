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
const seed = JSON.parse(fs.readFileSync(path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'), 'utf8'));
async function token(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login ${email} ${res.status}`);
  return body.access_token;
}
async function gw(label, tok, p) {
  const res = await fetch(`http://127.0.0.1:8080${p}`, {
    headers: { Authorization: `Bearer ${tok}` },
  });
  const text = await res.text();
  console.log(`${label} ${p} -> ${res.status} ${text.slice(0, 280)}`);
}
const t1 = await token(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
await gw('R01', t1, '/api/v1/iadm/01/analytics-aggregates?window=month');
await gw('R01', t1, '/api/v1/iadm/04/analytics-aggregates?window=month');
await gw('R01', t1, '/api/v1/iadm/01/health');
await gw('R01', t1, '/api/v1/iadm/04/health');
const t1b = await token(seed.r01b.email, seed.r01b.password);
await gw('R01b', t1b, '/api/v1/iadm/01/analytics-aggregates?window=month');
await gw('R01b', t1b, '/api/v1/iadm/04/analytics-aggregates?window=month');
const t02 = await token(seed.r02.email, seed.r02.password);
await gw('R02', t02, '/api/v1/iadm/01/analytics-aggregates?window=month');
await gw('R02', t02, '/api/v1/iadm/04/analytics-aggregates?window=month');
