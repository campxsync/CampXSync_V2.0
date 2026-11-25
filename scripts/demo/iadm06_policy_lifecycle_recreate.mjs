/**
 * Recreate DEMO analytics handling policy through IADM-05 lifecycle.
 * Author (R02) ≠ Approver/Publisher (R01). DEMO ONLY. No IADM-05 code change.
 * Never prints credentials.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-06/evidence/policy-lifecycle');
fs.mkdirSync(evidence, { recursive: true });

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
const seedPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
const seed = fs.existsSync(seedPath) ? JSON.parse(fs.readFileSync(seedPath, 'utf8')) : {};
const GW = process.env.CAMPX_GATEWAY_BASE || 'http://127.0.0.1:8080';

async function token(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login ${email} -> ${res.status}`);
  return body.access_token;
}

async function gw(tok, method, p, body) {
  const res = await fetch(`${GW}${p}`, {
    method,
    headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

const r02Email = seed.r02?.email || 'systemadmin.campx@gmail.com';
const r02Pw = seed.r02?.password;
const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
const r01Pw = process.env.PLATFORM_ADMIN_PW;
if (!r02Pw || !r01Pw) throw new Error('Missing R02/R01 credentials');

const CODE = 'DEMO-ANALYTICS-HANDLING';
const log = [];
const note = (s, d) => {
  log.push({ step: s, ...d });
  console.log(`${s}: ${JSON.stringify(d)}`);
};

const t02 = await token(r02Email, r02Pw);
const propose = await gw(t02, 'POST', '/api/v1/iadm/05/retention-rules', {
  code: CODE,
  title: 'DEMO ONLY — Analytics aggregate data-handling (lifecycle recreate)',
  domainCode: 'ANALYTICS',
  recordCategory: 'AGGREGATE_METRICS',
  scopeNote: 'DEMO ONLY — aggregates for IADM-06; not a privacy standard',
  durationNote: 'TBC',
  ownerLabel: 'Platform governance (DEMO)',
});
note('propose', { status: propose.status, policyId: propose.body.policyId, versionNo: propose.body.versionNo });
if (propose.status !== 200 || !propose.body.policyId) {
  fs.writeFileSync(path.join(evidence, 'RESULTS.json'), JSON.stringify(log, null, 2));
  process.exit(1);
}
const policyId = propose.body.policyId;

const submit = await gw(t02, 'POST', `/api/v1/iadm/05/policies/${policyId}/submit-review`, {});
note('submit-review', { status: submit.status, statusField: submit.body.status });

const t01 = await token(r01Email, r01Pw);
const decide = await gw(t01, 'POST', `/api/v1/iadm/05/policies/${policyId}/decide`, {
  decision: 'APPROVE',
  rationaleRef: 'DEMO ONLY — IADM-06 analytics handling recreate; author≠approver',
});
note('approve', { status: decide.status, statusField: decide.body.status, authorNeApprover: true });

const effectiveFrom = new Date().toISOString();
const publish = await gw(t01, 'POST', `/api/v1/iadm/05/policies/${policyId}/publish`, {
  effectiveFrom,
});
note('publish', {
  status: publish.status,
  statusField: publish.body.status,
  code: CODE,
  versionNo: publish.body.versionNo || propose.body.versionNo,
  effectiveFrom,
});

const resolve = await gw(t01, 'GET', '/api/v1/iadm/05/domain/resolve-policy?policyType=RETENTION&domainCode=ANALYTICS');
note('resolve-check', {
  status: resolve.status,
  code: resolve.body.code,
  versionNo: resolve.body.versionNo,
  policyId: resolve.body.policyId,
});

fs.writeFileSync(path.join(evidence, 'RESULTS.json'), JSON.stringify(log, null, 2));
fs.writeFileSync(
  path.join(evidence, 'POLICY_LIFECYCLE.md'),
  [
    '# DEMO analytics policy — IADM-05 lifecycle recreate',
    '',
    `- Code: \`${CODE}\``,
    `- Author: System Admin (R02) — propose + submit-review`,
    `- Approver/Publisher: Super Admin (R01) — different from author`,
    `- Label: DEMO ONLY`,
    `- Finding F-02: no EXPORT policy_type propose path; export gate uses same RETENTION+ANALYTICS resolve`,
    `- Direct-insert DEMO-ANALYTICS-EXPORT retired (superseded via SQL after this run)`,
    '',
    '## Steps',
    ...log.map((r) => `- **${r.step}**: HTTP ${r.status}; ${JSON.stringify(r)}`),
  ].join('\n')
);

const ok = propose.status === 200 && submit.status === 200 && decide.status === 200 && publish.status === 200 && resolve.status === 200;
console.log(ok ? 'POLICY_LIFECYCLE_OK' : 'POLICY_LIFECYCLE_FAIL');
process.exit(ok ? 0 : 1);
