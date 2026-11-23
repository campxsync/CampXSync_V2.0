/**
 * IADM-02 DEV seed helper: create second Super Admin (R01-B) + consumer Auth user.
 * Reads CAMPX_SUPABASE_URL / CAMPX_SUPABASE_SERVICE_ROLE_KEY from env (.env.audit).
 * Never prints passwords. Writes local untracked credential file under docs/iadm/iadm-02/evidence/.local/ if created.
 *
 * Usage: node scripts/dev/iadm02_seed_second_r01.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import crypto from 'crypto';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '../..');

function loadEnvAudit() {
  const p = path.join(root, '.env.audit');
  if (!fs.existsSync(p)) return;
  const text = fs.readFileSync(p, 'utf8');
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (!m) continue;
    if (!process.env[m[1]]) process.env[m[1]] = m[2];
  }
}

loadEnvAudit();

const URL = process.env.CAMPX_SUPABASE_URL || process.env.SUPABASE_URL;
const KEY = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
const DEV_REF = 'azulbaxszsqxymqmlwfe';

if (!URL || !KEY) {
  console.error('STOP: missing CAMPX_SUPABASE_URL or CAMPX_SUPABASE_SERVICE_ROLE_KEY');
  process.exit(2);
}
if (!URL.includes(DEV_REF)) {
  console.error('STOP: refusing non-DEV Supabase URL');
  process.exit(2);
}

async function adminCreateUser(email, password, fullName) {
  const res = await fetch(`${URL}/auth/v1/admin/users`, {
    method: 'POST',
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      email,
      password,
      email_confirm: true,
      user_metadata: { full_name: fullName, iadm02_seed: true },
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (res.status === 422 || (body.msg && /already/i.test(body.msg))) {
    // fetch existing
    const list = await fetch(`${URL}/auth/v1/admin/users?page=1&per_page=200`, {
      headers: { apikey: KEY, Authorization: `Bearer ${KEY}` },
    });
    const data = await list.json();
    const users = data.users || data || [];
    const found = users.find((u) => u.email === email);
    if (!found) throw new Error('User exists but could not be listed: ' + email);
    return { id: found.id, created: false };
  }
  if (!res.ok) {
    throw new Error('Auth admin create failed for ' + email + ' status=' + res.status);
  }
  return { id: body.id || body.user?.id, created: true };
}

async function sql(query) {
  // Use PostgREST rpc is not available for arbitrary SQL; use supabase postgres via management is MCP-only.
  // Insert platform_admins via REST if exposed — fall back to fetch auth only and print UUID for SQL apply.
  return query;
}

async function main() {
  const r01bEmail = process.env.IADM02_R01B_EMAIL || 'platformadmin2.campx@gmail.com';
  const r02Email = process.env.IADM02_R02_EMAIL || 'systemadmin.campx@gmail.com';
  const consumerEmail = 'iadm02.consumer.dev@campx.local';

  const r01bPw = process.env.IADM02_R01B_PASSWORD || ('Iadm02R01b!' + crypto.randomBytes(4).toString('hex'));
  const r02Pw = process.env.IADM02_R02_PASSWORD || ('Iadm02R02!' + crypto.randomBytes(4).toString('hex'));
  const consumerPw = process.env.IADM02_CONSUMER_PASSWORD || ('Iadm02Svc!' + crypto.randomBytes(4).toString('hex'));

  const r01b = await adminCreateUser(r01bEmail, r01bPw, 'IADM-02 Super Admin B (DEV)');
  const r02 = await adminCreateUser(r02Email, r02Pw, 'IADM-02 System Admin (DEV)');
  const consumer = await adminCreateUser(consumerEmail, consumerPw, 'IADM-02 SVC-CONSUMER-01');

  const localDir = path.join(root, 'docs/iadm/iadm-02/evidence/.local');
  fs.mkdirSync(localDir, { recursive: true });
  const credPath = path.join(localDir, 'seed_accounts.json');
  // Local only — gitignored via evidence/.local
  fs.writeFileSync(
    credPath,
    JSON.stringify(
      {
        note: 'DEV only. Do not commit. Passwords never logged to console.',
        r01a: { emailVar: 'PLATFORM_ADMIN_EMAIL', userId: '54e8308c-ada3-4065-a9fe-51814af6b971' },
        r01b: { email: r01bEmail, userId: r01b.id, created: r01b.created, password: r01bPw },
        r02: { email: r02Email, userId: r02.id, created: r02.created, password: r02Pw },
        consumer: { email: consumerEmail, userId: consumer.id, created: consumer.created, password: consumerPw },
      },
      null,
      2
    ),
    { mode: 0o600 }
  );

  // Write SQL snippet for platform_admins + re-run consumer grant (no secrets)
  const sqlPath = path.join(root, 'docs/iadm/iadm-02/evidence/SEED_PLATFORM_ADMINS.sql');
  fs.writeFileSync(
    sqlPath,
    `-- Apply via Supabase MCP execute_sql (DEV). No passwords.\n` +
      `INSERT INTO plat.platform_admins (user_id, full_name, role_code, status)\n` +
      `SELECT '${r01b.id}'::uuid, 'IADM-02 Super Admin B (DEV)', 'SUPER_ADMIN', 'ACTIVE'\n` +
      `WHERE NOT EXISTS (SELECT 1 FROM plat.platform_admins p WHERE p.user_id = '${r01b.id}'::uuid AND p.deleted_at IS NULL);\n\n` +
      `INSERT INTO plat.platform_admins (user_id, full_name, role_code, status)\n` +
      `SELECT '${r02.id}'::uuid, 'IADM-02 System Admin (DEV)', 'SYSTEM_ADMIN', 'ACTIVE'\n` +
      `WHERE NOT EXISTS (SELECT 1 FROM plat.platform_admins p WHERE p.user_id = '${r02.id}'::uuid AND p.deleted_at IS NULL);\n`
  );

  console.log('IADM-02 Auth seed OK (emails only, no passwords):');
  console.log('  R01-B:', r01bEmail, r01b.id, r01b.created ? 'created' : 'exists');
  console.log('  R02:  ', r02Email, r02.id, r02.created ? 'created' : 'exists');
  console.log('  SVC:  ', consumerEmail, consumer.id, consumer.created ? 'created' : 'exists');
  console.log('  Apply SQL:', sqlPath);
  console.log('  Local creds (gitignored path): docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
}

main().catch((e) => {
  console.error('STOP:', e.message || e);
  process.exit(1);
});
