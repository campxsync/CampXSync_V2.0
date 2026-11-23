/**
 * DEV: set app_metadata.tenant_id on IADM-02 R01-B and R02 so gateway JWT validation accepts them.
 * Matches R01-A sentinel DEV_TEST_TENANT. Never prints secrets.
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
const TENANT = 'a0000000-0000-0000-0000-000000000001';
if (!url?.includes('azulbaxszsqxymqmlwfe') || !key) {
  console.error('STOP: DEV service role required');
  process.exit(2);
}

const targets = [
  { label: 'R01-B', id: '3121791f-df40-421f-8a97-f6f471c39250', role: 'SUPER_ADMIN' },
  { label: 'R02', id: '7c8e1f7a-341c-48c5-ba27-da5aac81d150', role: 'SYSTEM_ADMIN' },
];

for (const t of targets) {
  const get = await fetch(`${url}/auth/v1/admin/users/${t.id}`, {
    headers: { apikey: key, Authorization: `Bearer ${key}` },
  });
  const user = await get.json();
  if (!get.ok) {
    console.error(JSON.stringify({ label: t.label, error: 'get_failed', http: get.status }));
    process.exit(1);
  }
  const nextMeta = {
    ...(user.app_metadata || {}),
    tenant_id: TENANT,
    platform_role: t.role,
    // Gateway JWT requires tenant_id; middleware /platform accepts SUPER_ADMIN|SYSTEM_ADMIN.
    role: t.role,
  };
  const put = await fetch(`${url}/auth/v1/admin/users/${t.id}`, {
    method: 'PUT',
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ app_metadata: nextMeta }),
  });
  const body = await put.json().catch(() => ({}));
  console.log(
    JSON.stringify({
      label: t.label,
      id: t.id,
      http: put.status,
      tenant_id: body.app_metadata?.tenant_id || null,
      platform_role: body.app_metadata?.platform_role || null,
    })
  );
  if (!put.ok) process.exit(1);
}
