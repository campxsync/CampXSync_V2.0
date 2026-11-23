/**
 * Cutover 410 checks + S4 corrective republish smoke.
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
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

async function login(email, password) {
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error('login failed ' + res.status);
  return body.access_token;
}

async function gw(token, method, p, body) {
  const res = await fetch(`http://127.0.0.1:8080${p}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text.slice(0, 200) };
  }
  return { status: res.status, json };
}

const r01 = await login(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
const college = await login(process.env.COLLEGE_ADMIN_EMAIL, process.env.COLLEGE_ADMIN_PW);

const cutoverConfig = await gw(r01, 'GET', '/api/v1/admin/configuration');
const cutoverAlias = await gw(r01, 'GET', '/v1/platform-configs');
const cutoverPolicies = await gw(r01, 'POST', '/api/v1/admin/policies', { policyCode: 'X' });

const versions = await gw(r01, 'GET', '/api/v1/iadm/02/versions');
const items = versions.json.items || [];
const source = items.find((v) => v.status === 'SUPERSEDED') || items[items.length - 1];
const stamp = Date.now();
const corrective = source
  ? await gw(r01, 'POST', `/api/v1/iadm/02/versions/${source.versionId}/corrective-publish`, {
      reason: 'S4 smoke restore prior payload ' + stamp,
      releaseNotes: 'S4 corrective from ' + source.versionLabel,
      idempotencyKey: 's4-smoke-' + stamp,
    })
  : { status: 0, json: { error: 'no source version' } };

const retry = source
  ? await gw(r01, 'POST', `/api/v1/iadm/02/versions/${source.versionId}/corrective-publish`, {
      reason: 'retry',
      idempotencyKey: 's4-smoke-' + stamp,
    })
  : { status: 0, json: {} };

const r03deny = await gw(college, 'POST', '/api/v1/iadm/02/configurations/security.session_timeout_min/drafts', {
  proposedValue: '99',
  reason: 'should deny',
  idempotencyKey: 's4-r04-deny-' + stamp,
});

const out = {
  cutover: {
    configuration: { status: cutoverConfig.status, code: cutoverConfig.json.errorCode || cutoverConfig.json.code },
    alias: { status: cutoverAlias.status, code: cutoverAlias.json.errorCode || cutoverAlias.json.code },
    policies: { status: cutoverPolicies.status, code: cutoverPolicies.json.errorCode || cutoverPolicies.json.code },
    pass:
      cutoverConfig.status === 410 &&
      cutoverAlias.status === 410 &&
      cutoverPolicies.status === 410,
  },
  corrective: {
    sourceLabel: source?.versionLabel,
    sourceId: source?.versionId,
    status: corrective.status,
    versionId: corrective.json.versionId,
    label: corrective.json.versionLabel,
    restoredFrom: corrective.json.restoredFromVersionId,
    notificationDeferred: corrective.json.notificationDeferred === true,
    retrySameId: retry.json.versionId === corrective.json.versionId,
    pass:
      corrective.status === 200 &&
      !!corrective.json.versionId &&
      corrective.json.notificationDeferred === true &&
      retry.json.versionId === corrective.json.versionId,
  },
  r03r04Deny: {
    status: r03deny.status,
    errorCode: r03deny.json.errorCode,
    pass: r03deny.status === 403,
  },
};

out.pass = out.cutover.pass && out.corrective.pass && out.r03r04Deny.pass;

fs.writeFileSync(
  path.join(root, 'docs/iadm/iadm-02/evidence/S4_CUTOVER_SMOKE.json'),
  JSON.stringify(out, null, 2)
);
console.log(JSON.stringify(out, null, 2));
process.exitCode = out.pass ? 0 : 1;
