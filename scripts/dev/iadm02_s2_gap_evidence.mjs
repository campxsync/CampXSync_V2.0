/**
 * S2 gap evidence: self-approve deny, reject, reject-cannot-publish,
 * idempotent publish retry, version compare, VER-002 metadata.
 * Never prints passwords.
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
const seed = JSON.parse(
  fs.readFileSync(path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'), 'utf8')
);
const url = process.env.CAMPX_SUPABASE_URL;
const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const evidence = path.join(root, 'docs/iadm/iadm-02/evidence');

async function login(email, password) {
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error('login failed ' + email + ' ' + res.status);
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
    json = { raw: text.slice(0, 300) };
  }
  return { status: res.status, json };
}

const r01a = await login(process.env.PLATFORM_ADMIN_EMAIL, process.env.PLATFORM_ADMIN_PW);
const r01b = await login(seed.r01b.email, seed.r01b.password);
const stamp = Date.now();

// Create + validate + submit as R01-A
const create = await gw(r01a, 'POST', '/api/v1/iadm/02/configurations/fees.late_fee_grace_days/drafts', {
  proposedValue: '9',
  reason: 'S2 gap: self-approve + reject evidence ' + stamp,
  idempotencyKey: 's2-gap-create-' + stamp,
});
const draftId = create.json.draftId;
const validate = await gw(r01a, 'POST', `/api/v1/iadm/02/drafts/${draftId}/validate`);
const submit = await gw(r01a, 'POST', `/api/v1/iadm/02/drafts/${draftId}/submit`);

// 1) R01-A self-approve denied
const selfApprove = await gw(r01a, 'POST', `/api/v1/iadm/02/drafts/${draftId}/decide`, {
  decision: 'APPROVE',
  reason: 'should be denied',
});

// Separate draft for reject path
const create2 = await gw(r01a, 'POST', '/api/v1/iadm/02/configurations/exam.grace_marks_max/drafts', {
  proposedValue: '3',
  reason: 'S2 gap: reject evidence ' + stamp,
  idempotencyKey: 's2-gap-reject-' + stamp,
});
const draft2 = create2.json.draftId;
await gw(r01a, 'POST', `/api/v1/iadm/02/drafts/${draft2}/validate`);
await gw(r01a, 'POST', `/api/v1/iadm/02/drafts/${draft2}/submit`);
const reject = await gw(r01b, 'POST', `/api/v1/iadm/02/drafts/${draft2}/decide`, {
  decision: 'REJECT',
  reason: 'S2 gap: reject with reason — not suitable',
});
const publishRejected = await gw(r01b, 'POST', `/api/v1/iadm/02/drafts/${draft2}/publish`, {
  releaseNotes: 'should fail',
  idempotencyKey: 's2-gap-pub-rejected-' + stamp,
});

// Approve first draft with R01-B for idempotent publish
const approve = await gw(r01b, 'POST', `/api/v1/iadm/02/drafts/${draftId}/decide`, {
  decision: 'APPROVE',
  reason: 'S2 gap: approve for idempotent publish',
});
const idem = 's2-gap-pub-idem-' + stamp;
const pub1 = await gw(r01b, 'POST', `/api/v1/iadm/02/drafts/${draftId}/publish`, {
  releaseNotes: 'S2 gap idempotent publish',
  idempotencyKey: idem,
});
const pub2 = await gw(r01b, 'POST', `/api/v1/iadm/02/drafts/${draftId}/publish`, {
  releaseNotes: 'S2 gap idempotent publish retry',
  idempotencyKey: idem,
});

// Versions + compare
const versions = await gw(r01a, 'GET', '/api/v1/iadm/02/versions');
const items = versions.json.items || [];
const active = items.find((v) => v.status === 'PUBLISHED') || items[0];
const prior = items.find((v) => v.versionId !== active?.versionId) || items[1];
const compare =
  active && prior
    ? await gw(r01a, 'GET', `/api/v1/iadm/02/versions/${active.versionId}/compare/${prior.versionId}`)
    : { status: 0, json: {} };

// Attempt edit published version path (no route → 404)
const editPublished = await gw(r01a, 'PATCH', `/api/v1/iadm/02/versions/${active?.versionId || 'x'}`, {
  releaseNotes: 'tamper',
});

const out = {
  draftSelfApprove: draftId,
  draftReject: draft2,
  selfApprove: {
    status: selfApprove.status,
    errorCode: selfApprove.json.errorCode,
    pass: selfApprove.status === 403 && selfApprove.json.errorCode === 'IADM02_SELF_APPROVE',
  },
  reject: {
    status: reject.status,
    draftStatus: reject.json.status,
    pass: reject.status === 200 && reject.json.status === 'REJECTED',
  },
  publishRejected: {
    status: publishRejected.status,
    errorCode: publishRejected.json.errorCode,
    pass: publishRejected.status === 409 && publishRejected.json.errorCode === 'IADM02_NOT_APPROVED',
  },
  idempotentPublish: {
    first: { status: pub1.status, versionId: pub1.json.versionId, label: pub1.json.versionLabel },
    retry: { status: pub2.status, versionId: pub2.json.versionId, label: pub2.json.versionLabel },
    sameVersion: pub1.json.versionId && pub1.json.versionId === pub2.json.versionId,
    pass:
      (pub1.status === 200 || pub1.status === 201) &&
      pub1.json.versionId === pub2.json.versionId,
  },
  versions: {
    status: versions.status,
    count: items.length,
    labels: items.map((v) => `${v.versionLabel}:${v.status}`),
  },
  compare: { status: compare.status, hasLeft: !!compare.json.left, hasRight: !!compare.json.right },
  editPublished: { status: editPublished.status, pass: editPublished.status === 404 || editPublished.status === 405 },
  createValidateSubmit: {
    create: create.status,
    validate: validate.status,
    submit: submit.status,
    approve: approve.status,
  },
};

fs.writeFileSync(path.join(evidence, 'S2_GAP_EVIDENCE.json'), JSON.stringify(out, null, 2));
console.log(JSON.stringify(out, null, 2));
