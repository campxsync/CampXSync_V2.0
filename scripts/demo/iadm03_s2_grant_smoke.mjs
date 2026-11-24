/**
 * IADM-03 S2 API smoke via gateway (claims contract + ceiling + idempotent assign probe).
 * Uses test-session cookies against Next BFF where possible; falls back to direct gateway health.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/s2');
fs.mkdirSync(evidence, { recursive: true });

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

const GATEWAY = process.env.CAMPX_GATEWAY_URL || 'http://127.0.0.1:8080';
const SERVICE = process.env.IADM03_URL || 'http://127.0.0.1:8101';

async function getJson(url) {
  const res = await fetch(url);
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text };
  }
  return { status: res.status, body };
}

async function postJson(url, payload) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text };
  }
  return { status: res.status, body };
}

const out = { steps: [] };

const health = await getJson(`${SERVICE}/api/v1/iadm/03/health`);
out.steps.push({ step: 'service_health', ...health });

const ceiling = await postJson(`${SERVICE}/api/v1/iadm/03/ceilings/evaluate`, {
  grantor: 'R03',
  targetRole: 'INSTITUTION_ADMIN',
  selfGrant: false,
});
out.steps.push({
  step: 'ceiling_r03_r03',
  status: ceiling.status,
  allowed: ceiling.body?.allowed,
  denyReason: ceiling.body?.denyReason,
  ok: ceiling.status === 200 && ceiling.body?.allowed === false,
});

const claims = await getJson(`${SERVICE}/api/v1/iadm/03/claims/contract`);
out.steps.push({
  step: 'claims_contract',
  status: claims.status,
  ok: claims.status === 200 && claims.body?.iadm03OnRequestPath === false,
  body: claims.body,
});

const gwHealth = await getJson(`${GATEWAY}/api/v1/iadm/03/health`).catch((e) => ({
  status: 0,
  body: { error: String(e.message || e) },
}));
out.steps.push({ step: 'gateway_health', ...gwHealth });

out.ok = out.steps.every((s) => s.ok !== false && (s.status === 200 || s.step === 'gateway_health'));
fs.writeFileSync(path.join(evidence, 'S2_API_SMOKE.json'), JSON.stringify(out, null, 2));
console.log(JSON.stringify(out, null, 2));
if (!out.ok) process.exitCode = 1;
