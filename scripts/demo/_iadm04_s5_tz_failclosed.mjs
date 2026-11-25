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

const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const email = process.env.PLATFORM_ADMIN_EMAIL;
const password = process.env.PLATFORM_ADMIN_PW;
const tokRes = await fetch(`${url}/auth/v1/token?grant_type=password`, {
  method: 'POST',
  headers: { apikey: anon, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password }),
});
const tok = await tokRes.json();
const res = await fetch('http://127.0.0.1:8080/api/v1/iadm/04/jobs/run', {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${tok.access_token}`,
    'Content-Type': 'application/json',
  },
  body: '{}',
});
const body = await res.json();
const outDir = path.join(root, 'docs/iadm/iadm-04/evidence/s5');
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 's5_jobs_tz_fail_closed.json'), JSON.stringify({ status: res.status, body }, null, 2));
console.log(JSON.stringify({ status: res.status, ran: body.ran, errorCode: body.errorCode, detail: body.detail || body.message }, null, 2));
if (!(body.ran === false && String(body.errorCode || '').includes('BILLING_TZ'))) process.exit(1);
