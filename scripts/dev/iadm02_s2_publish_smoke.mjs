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
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const DRAFT = '4e562e40-ad6a-4b04-acd6-dcb230fe962d';

const login = await fetch(`${url}/auth/v1/token?grant_type=password`, {
  method: 'POST',
  headers: { apikey: anon, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: seed.r01b.email, password: seed.r01b.password }),
}).then((r) => r.json());

const res = await fetch(`http://127.0.0.1:8080/api/v1/iadm/02/drafts/${DRAFT}/publish`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${login.access_token}`,
    'Content-Type': 'application/json',
    'Idempotency-Key': `s2-pub-${DRAFT}`,
  },
  body: JSON.stringify({
    releaseNotes: 'S2 publish after R01-B approve',
    idempotencyKey: `s2-pub-${DRAFT}`,
  }),
});
console.log(JSON.stringify({ status: res.status, body: (await res.text()).slice(0, 700) }, null, 2));
