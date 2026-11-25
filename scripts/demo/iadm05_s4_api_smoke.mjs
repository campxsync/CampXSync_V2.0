/**
 * IADM-05 S4 API smoke — R03 isolation across institutes, R04/R28 write deny, privacy gate.
 * Never prints credentials.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-05/evidence/us032');
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
const out = [];

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

function pass(name, ok, detail) {
  out.push({ name, ok: !!ok, detail: detail || '' });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
}

const r03 = seed.r03demo || {};
const r28 = seed.auditor || {};
const r04Email = process.env.COLLEGE_ADMIN_EMAIL;
const r04Pw = process.env.COLLEGE_ADMIN_PW;
const r01Email = process.env.PLATFORM_ADMIN_EMAIL;
const r01Pw = process.env.PLATFORM_ADMIN_PW;

try {
  const t03 = await token(r03.email, r03.password);
  const adh = await gw(t03, 'GET', '/api/v1/iadm/05/adherence');
  pass('R03 adherence 200', adh.status === 200, `status=${adh.status}`);
  const codes = (adh.body.applicablePolicies || []).map((p) => p.code);
  pass('R03 sees platform DEMO policies', codes.includes('DEMO-CLASS-PUBLIC') || codes.includes('DEMO-RET-STUDENT-RECORD'), codes.join(','));
  pass(
    'R03 isolation vs other-institute DEMO-RET-INST-A/B',
    !codes.includes('DEMO-RET-INST-A') && !codes.includes('DEMO-RET-INST-B'),
    codes.filter((c) => String(c).includes('INST')).join(',') || 'none'
  );
  const w03 = await gw(t03, 'POST', '/api/v1/iadm/05/classifications', {
    code: 'R03-FAIL',
    title: 'x',
    className: 'x',
  });
  pass('R03 write 403', w03.status === 403, `status=${w03.status}`);

  const t04 = await token(r04Email, r04Pw);
  const col = await gw(t04, 'GET', '/api/v1/iadm/05/college-view');
  pass('R04 college-view 200', col.status === 200, `status=${col.status}`);
  pass('R04 canEditPolicy false', col.body.canEditPolicy === false, String(col.body.canEditPolicy));
  const w04 = await gw(t04, 'POST', '/api/v1/iadm/05/retention-rules', {
    code: 'R04-FAIL',
    title: 'x',
    domainCode: 'STM',
    recordCategory: 'x',
  });
  pass('R04 write 403', w04.status === 403, `status=${w04.status}`);

  const t28 = await token(r28.email, r28.password);
  const w28 = await gw(t28, 'POST', '/api/v1/iadm/05/classifications', {
    code: 'R28-FAIL',
    title: 'x',
    className: 'x',
  });
  pass('R28 write 403', w28.status === 403, `status=${w28.status}`);

  const t01 = await token(r01Email, r01Pw);
  const priv = await gw(t01, 'GET', '/api/v1/iadm/05/policies?type=PRIVACY_CONSENT');
  const gated = (priv.body.items || []).find((p) => p.code === 'DEMO-PRIVACY-GATED');
  if (gated) {
    const pub = await gw(t01, 'POST', `/api/v1/iadm/05/policies/${gated.id}/publish`, {
      effectiveFrom: new Date().toISOString(),
    });
    pass(
      'Privacy publish legal gate',
      pub.status === 423 || pub.body.errorCode === 'IADM05_LEGAL_GATE',
      `status=${pub.status}`
    );
  } else {
    pass('Privacy publish legal gate', false, 'DEMO-PRIVACY-GATED missing');
  }

  // Faculty (R10 family) denied — sample R05-R39
  const facultyEmail = process.env.FACULTY_EMAIL;
  const facultyPw = process.env.FACULTY_PW;
  if (facultyEmail && facultyPw) {
    const tf = await token(facultyEmail, facultyPw);
    const df = await gw(tf, 'GET', '/api/v1/iadm/05/classifications');
    pass('R10/faculty classifications denied', df.status === 403, `status=${df.status}`);
  } else {
    pass('R10/faculty classifications denied', false, 'faculty creds missing');
  }
} catch (e) {
  pass('fatal', false, String(e.message || e));
}

fs.writeFileSync(path.join(evidence, 'S4_API_SMOKE.json'), JSON.stringify(out, null, 2));
process.exit(out.some((x) => !x.ok) ? 1 : 0);
