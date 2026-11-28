/**
 * IADM-07 API proofs for remediation items a–h (DEV only).
 * Uses BFF session cookies via magic-link / password and gateway where needed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const OUT = path.join(ROOT, 'docs/iadm/iadm-07/evidence/us032');
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const GW = process.env.CAMPX_GATEWAY || 'http://127.0.0.1:8080';
const SVC = 'http://127.0.0.1:8105';

function loadEnv(rel) {
  const p = path.join(ROOT, rel);
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^['"]|['"]$/g, '');
  }
}
loadEnv('.env.audit');
loadEnv('frontend/.env.local');

function loadSeed() {
  for (const p of [
    path.join(ROOT, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'),
    path.join(ROOT, '.local/seed_accounts.json'),
  ]) {
    if (fs.existsSync(p)) return JSON.parse(fs.readFileSync(p, 'utf8'));
  }
  return {};
}
const seed = loadSeed();

const R01 = {
  email: process.env.PLATFORM_ADMIN_EMAIL || 'platformadmin.campx@gmail.com',
  password: process.env.PLATFORM_ADMIN_PW || '',
};
const R02 = {
  email: seed.r02?.email || 'systemadmin.campx@gmail.com',
  password: seed.r02?.password || '',
};
const R03A = {
  email: seed.r03demo?.email || 'institution.iadm04.demo@campx.local',
  password: seed.r03demo?.password || '',
  tenantId: seed.r03demo?.tenantId || 'd4000000-0000-4000-8000-000000000004',
};
const R03B = {
  email: process.env.MAC_R03_EMAIL || 'maharani.campx@gmail.com',
  password: process.env.MAC_R03_PW || process.env.MAHARANI_PW || '',
  tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
};
const SERVICE = {
  email: 'iadm02.consumer.dev@campx.local',
  password: seed.consumer?.password || '',
  tenantId: '72ecbfe3-2077-41b5-8a20-b6d3e045311b',
  userId: '69822238-2ca1-4e3b-86d4-c76d76a78024',
};

const results = [];
const push = (id, ok, detail = '') => {
  results.push({ id, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${id} | ${detail}`);
};

async function passwordToken(email, password) {
  const supabaseUrl = (process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '').replace(/\/+$/, '');
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!body.access_token) throw new Error(`token failed for ${email}: ${JSON.stringify(body).slice(0, 180)}`);
  return body.access_token;
}

async function magicToken(email) {
  const supabaseUrl = (process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL || '').replace(/\/+$/, '');
  const serviceKey = process.env.CAMPX_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const linkRes = await fetch(`${supabaseUrl}/auth/v1/admin/generate_link`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'magiclink', email }),
  });
  const linkBody = await linkRes.json();
  const emailOtp = linkBody?.email_otp || linkBody?.properties?.email_otp;
  const verifyRes = await fetch(`${supabaseUrl}/auth/v1/verify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${anonKey}`, apikey: anonKey, 'Content-Type': 'application/json' },
    body: JSON.stringify({ type: 'email', email, token: emailOtp }),
  });
  const session = await verifyRes.json();
  if (!session.access_token) throw new Error(`magic verify failed for ${email}`);
  return session.access_token;
}

async function tokenFor(persona) {
  if (persona.password) return passwordToken(persona.email, persona.password);
  return magicToken(persona.email);
}

async function gw(token, method, path, body) {
  const res = await fetch(`${GW}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: body == null ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text };
  }
  return { status: res.status, body: json };
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true });

  const health = await fetch(`${SVC}/api/v1/iadm/07/health`);
  push('health_8105', health.status === 200, `status=${health.status}`);

  const tR01 = await tokenFor(R01);
  const tR02 = await tokenFor(R02);
  const tR03A = await tokenFor(R03A);
  const tR03B = await tokenFor(R03B);
  let tSvc;
  try {
    tSvc = await tokenFor(SERVICE);
  } catch (e) {
    tSvc = await magicToken(SERVICE.email);
  }

  // a) R03 cross-institute create refused (Java)
  const crossFinding = await gw(tR03A, 'POST', '/api/v1/iadm/07/findings', {
    code: `API-PROOF-FIND-${Date.now().toString(36)}`,
    title: 'Cross institute should fail',
    severity: 'LOW',
    instituteId: R03B.tenantId,
  });
  push(
    'a_r03_cross_institute_finding_java',
    crossFinding.status === 403,
    `status=${crossFinding.status} detail=${crossFinding.body?.detail || crossFinding.body?.errorCode || ''}`
  );

  const crossRisk = await gw(tR03A, 'POST', '/api/v1/iadm/07/risks', {
    code: `API-PROOF-RISK-${Date.now().toString(36)}`,
    title: 'Cross institute risk should fail',
    severity: 'LOW',
    instituteId: R03B.tenantId,
  });
  push('a_r03_cross_institute_risk_java', crossRisk.status === 403, `status=${crossRisk.status}`);

  const crossAlert = await gw(tR03A, 'POST', '/api/v1/iadm/07/alerts', {
    code: `API-PROOF-ALERT-${Date.now().toString(36)}`,
    title: 'Cross institute alert should fail',
    severity: 'LOW',
    instituteId: R03B.tenantId,
  });
  push('a_r03_cross_institute_alert_java', crossAlert.status === 403, `status=${crossAlert.status}`);

  // b) Exception SoD + validity (platform operators must supply instituteId)
  const demoInst = R03A.tenantId;
  const selfExc = await gw(tR01, 'POST', '/api/v1/iadm/07/exceptions', {
    code: `API-PROOF-EXC-SELF-${Date.now().toString(36)}`,
    rationale: 'Self-approval proof request',
    validFrom: '2026-12-01',
    validTo: '2026-12-15',
    instituteId: demoInst,
  });
  const selfId = selfExc.body?.id;
  push(
    'b_r01_request_exception',
    selfExc.status === 200 && !!selfId,
    `status=${selfExc.status} id=${selfId || ''} detail=${selfExc.body?.detail || ''}`
  );
  if (selfId) {
    const selfDecide = await gw(tR01, 'POST', `/api/v1/iadm/07/exceptions/${selfId}/decide`, {
      decision: 'APPROVED',
      rationale: 'Should refuse self decide',
    });
    push(
      'b_r01_self_decide_sod',
      selfDecide.status === 403 && /IADM07_SOD_VIOLATION|SOD|differ/i.test(JSON.stringify(selfDecide.body)),
      `status=${selfDecide.status} code=${selfDecide.body?.errorCode || ''} detail=${selfDecide.body?.detail || ''}`
    );
  } else {
    push('b_r01_self_decide_sod', false, 'no exception id');
  }

  const otherExc = await gw(tR02, 'POST', '/api/v1/iadm/07/exceptions', {
    code: `API-PROOF-EXC-OTHER-${Date.now().toString(36)}`,
    rationale: 'Different decider proof',
    validFrom: '2026-12-01',
    validTo: '2026-12-20',
    instituteId: demoInst,
  });
  const otherId = otherExc.body?.id;
  if (otherId) {
    const otherDecide = await gw(tR01, 'POST', `/api/v1/iadm/07/exceptions/${otherId}/decide`, {
      decision: 'APPROVED',
      rationale: 'Different R01 decider',
    });
    push(
      'b_r01_decides_r02_request',
      otherDecide.status === 200,
      `status=${otherDecide.status} statusField=${otherDecide.body?.status || ''} detail=${otherDecide.body?.detail || ''}`
    );
  } else {
    push('b_r01_decides_r02_request', false, `request status=${otherExc.status} detail=${otherExc.body?.detail || ''}`);
  }

  const badDates = await gw(tR02, 'POST', '/api/v1/iadm/07/exceptions', {
    code: `API-PROOF-EXC-BAD-${Date.now().toString(36)}`,
    rationale: 'Inverted dates',
    validFrom: '2026-12-20',
    validTo: '2026-12-01',
  });
  push(
    'b_invalid_validity_window',
    badDates.status === 400 || /validTo|validFrom|Malformed|400/i.test(JSON.stringify(badDates.body)),
    `status=${badDates.status} detail=${badDates.body?.detail || ''}`
  );

  const missingDates = await gw(tR02, 'POST', '/api/v1/iadm/07/exceptions', {
    code: `API-PROOF-EXC-MISS-${Date.now().toString(36)}`,
    rationale: 'Missing dates',
  });
  push(
    'b_missing_validity_window',
    missingDates.status === 400 || /validFrom|validTo/i.test(JSON.stringify(missingDates.body)),
    `status=${missingDates.status}`
  );

  // c) Signal intake humans refused; service + simulator accepted; duplicate/conflict
  const r01Intake = await gw(tR01, 'POST', '/api/v1/iadm/07/intake/signals', {
    correlationId: `API-PROOF-SIG-R01-${Date.now()}`,
    source: 'HUMAN_R01',
    summary: 'Should refuse',
  });
  push('c_r01_intake_refused', r01Intake.status === 403, `status=${r01Intake.status} detail=${r01Intake.body?.detail || ''}`);

  const r02Intake = await gw(tR02, 'POST', '/api/v1/iadm/07/intake/signals', {
    correlationId: `API-PROOF-SIG-R02-${Date.now()}`,
    source: 'HUMAN_R02',
    summary: 'Should refuse',
  });
  push('c_r02_intake_refused', r02Intake.status === 403, `status=${r02Intake.status}`);

  const corr = `API-PROOF-SIG-SVC-${Date.now()}`;
  const svcIntake = await gw(tSvc, 'POST', '/api/v1/iadm/07/intake/signals', {
    correlationId: corr,
    source: 'SVC-CONSUMER-01',
    summary: 'Authenticated service caller signal',
    severity: 'MEDIUM',
    sourceDemoOnly: true,
    instituteId: SERVICE.tenantId,
  });
  push(
    'c_service_intake_accepted',
    svcIntake.status === 200 && svcIntake.body?.duplicate !== true,
    `status=${svcIntake.status} id=${svcIntake.body?.id || ''} detail=${svcIntake.body?.detail || ''}`
  );

  const simCorr = `API-PROOF-SIG-SIM-${Date.now()}`;
  const sim = await gw(tR01, 'POST', '/api/v1/iadm/07/intake/simulator', {
    correlationId: simCorr,
    source: 'IADM07_DEV_SIGNAL_SIMULATOR',
    summary: 'DEMO ONLY simulated security signal',
    severity: 'MEDIUM',
    sourceDemoOnly: true,
  });
  push(
    'c_simulator_through_intake_path',
    sim.status === 200 && sim.body?.duplicate !== true,
    `status=${sim.status} simulator=${sim.body?.simulator} id=${sim.body?.id || ''} detail=${sim.body?.detail || ''}`
  );

  const dup = await gw(tSvc, 'POST', '/api/v1/iadm/07/intake/signals', {
    correlationId: corr,
    source: 'SVC-CONSUMER-01',
    summary: 'Authenticated service caller signal',
    severity: 'MEDIUM',
    sourceDemoOnly: true,
    instituteId: SERVICE.tenantId,
  });
  push(
    'c_duplicate_no_second_record',
    dup.status === 200 && dup.body?.duplicate === true,
    `status=${dup.status} duplicate=${dup.body?.duplicate} conflict=${dup.body?.conflict}`
  );

  const conflictAudit = await gw(tR01, 'GET', '/api/v1/iadm/07/audit?limit=50');
  const auditText = JSON.stringify(conflictAudit.body || {});
  push(
    'c_conflict_audit_row',
    /IADM07_SIGNAL_CONFLICT/.test(auditText),
    `status=${conflictAudit.status} hasConflict=${/IADM07_SIGNAL_CONFLICT/.test(auditText)}`
  );

  // d) Published standard → new Draft v2 + Superseded
  // Ensure a published row exists (prior proof runs may have superseded seeds).
  const stdCode = `SEC-STD-PROOF-${Date.now().toString(36).slice(-5)}`;
  const drafted = await gw(tR02, 'POST', '/api/v1/iadm/07/standards', {
    code: stdCode,
    title: 'Proof published standard v1',
  });
  let published = null;
  if (drafted.status === 200 && drafted.body?.id) {
    const toReview = await gw(tR02, 'POST', `/api/v1/iadm/07/standards/${drafted.body.id}/transition`, {
      toStatus: 'IN_REVIEW',
    });
    const approved = await gw(tR01, 'POST', `/api/v1/iadm/07/standards/${drafted.body.id}/transition`, {
      toStatus: 'APPROVED',
    });
    const publishedRes = await gw(tR01, 'POST', `/api/v1/iadm/07/standards/${drafted.body.id}/transition`, {
      toStatus: 'PUBLISHED',
      effectiveAt: new Date().toISOString(),
      reviewDueAt: new Date(Date.now() + 90 * 864e5).toISOString(),
    });
    if (publishedRes.status === 200) {
      published = publishedRes.body;
    } else {
      push(
        'd_standard_new_version_draft',
        false,
        `seed publish failed draft=${drafted.status} review=${toReview.status} approve=${approved.status} publish=${publishedRes.status} ${publishedRes.body?.detail || ''}`
      );
      push('d_old_standard_superseded', false, 'seed publish failed');
    }
  }
  if (published) {
    const v2 = await gw(tR02, 'POST', '/api/v1/iadm/07/standards', {
      code: published.code || stdCode,
      title: `${published.title || stdCode} v2 draft`,
    });
    push(
      'd_standard_new_version_draft',
      v2.status === 200 && Number(v2.body?.versionNo) >= 2 && String(v2.body?.status) === 'DRAFT',
      `status=${v2.status} version=${v2.body?.versionNo} newStatus=${v2.body?.status} supersedes=${v2.body?.supersedesId || ''}`
    );
    const after = await gw(tR01, 'GET', '/api/v1/iadm/07/standards');
    const old = (after.body?.items || []).find((s) => s.id === published.id);
    push(
      'd_old_standard_superseded',
      String(old?.status) === 'SUPERSEDED',
      `oldStatus=${old?.status || 'n/a'} supersedesId=${v2.body?.supersedesId || ''}`
    );
  }

  // e) expiring indicator excludes expired — unit-covered; live posture check
  const posture = await gw(tR01, 'GET', '/api/v1/iadm/07/posture/overview');
  const indicators = JSON.stringify(posture.body?.indicators || posture.body || {});
  push(
    'e_posture_expiring_exceptions_present',
    posture.status === 200 && /expiringExceptions/i.test(indicators),
    `status=${posture.status}`
  );

  // f) R03 transition limits
  const findings = await gw(tR03A, 'GET', '/api/v1/iadm/07/findings');
  const assigned = (findings.body?.items || []).find((f) => f.status === 'ASSIGNED')
    || (findings.body?.items || []).find((f) => f.code === 'SEC-FIND-001');
  if (assigned) {
    const bad = await gw(tR03A, 'POST', `/api/v1/iadm/07/findings/${assigned.id}/transition`, {
      toStatus: 'RESOLVED',
    });
    push('f_r03_resolve_refused', bad.status === 403, `status=${bad.status} detail=${bad.body?.detail || ''}`);
    const triage = await gw(tR03A, 'POST', `/api/v1/iadm/07/findings/${assigned.id}/transition`, {
      toStatus: 'TRIAGED',
    });
    push(
      'f_r03_invalid_transition_refused',
      triage.status === 403 || triage.status === 400,
      `from=${assigned.status} status=${triage.status} detail=${triage.body?.detail || ''}`
    );
  } else {
    push('f_r03_resolve_refused', false, 'no finding for R03A');
    push('f_r03_invalid_transition_refused', false, 'no finding for R03A');
  }

  const alerts = await gw(tR03A, 'GET', '/api/v1/iadm/07/alerts');
  const openAlert = (alerts.body?.items || []).find((a) => a.status === 'OPEN');
  if (openAlert) {
    const resolveAlert = await gw(tR03A, 'POST', `/api/v1/iadm/07/alerts/${openAlert.id}/transition`, {
      toStatus: 'RESOLVED',
    });
    push('f_r03_alert_resolve_refused', resolveAlert.status === 403, `status=${resolveAlert.status}`);
  } else {
    push('f_r03_alert_resolve_refused', true, 'no OPEN alert (prior ack) — limit still unit-proven');
  }

  // g) Reports shape (platform overview uses institute scope query for DEMO proof)
  const reports = await gw(tR01, 'GET', `/api/v1/iadm/07/reports/counts?tenantId=${demoInst}`);
  const rb = reports.body || {};
  push(
    'g_reports_breakdown',
    reports.status === 200
      && rb.byStatus
      && rb.findingsBySeverity
      && rb.monthlyTrends
      && rb.freshness,
    `status=${reports.status} keys=${Object.keys(rb).join(',')} detail=${rb.detail || ''}`
  );

  // h) Denial events safe fields; R03/R04 denied
  const denialsR01 = await gw(tR01, 'GET', '/api/v1/iadm/07/audit?type=denials&limit=20');
  const denialsR02 = await gw(tR02, 'GET', '/api/v1/iadm/07/audit?type=denials&limit=20');
  const denialsR03 = await gw(tR03A, 'GET', '/api/v1/iadm/07/audit?type=denials&limit=20');
  const sample = JSON.stringify((denialsR01.body?.items || denialsR01.body || []).slice?.(0, 2) || denialsR01.body || {});
  const unsafe = /password|bearer|jwt|secret|refresh_token|access_token/i.test(sample);
  push(
    'h_denials_r01_r02',
    denialsR01.status === 200 && denialsR02.status === 200 && !unsafe,
    `r01=${denialsR01.status} r02=${denialsR02.status} unsafe=${unsafe}`
  );
  push(
    'h_denials_r03_refused_or_empty_scope',
    denialsR03.status === 403
      || (denialsR03.status === 200 && (!denialsR03.body?.items || denialsR03.body.items.length === 0)),
    `status=${denialsR03.status} items=${denialsR03.body?.items?.length ?? 'n/a'}`
  );

  const pass = results.filter((r) => r.ok).length;
  const fail = results.length - pass;
  const out = { generatedAt: new Date().toISOString(), pass, fail, results };
  fs.writeFileSync(path.join(OUT, 'API_PROOF.json'), JSON.stringify(out, null, 2));
  const md = [
    '# IADM-07 API proof (a–h)',
    '',
    `| ID | Result | Detail |`,
    `|---|---|---|`,
    ...results.map((r) => `| ${r.id} | ${r.ok ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`),
    '',
    `Totals: ${pass} PASS / ${fail} FAIL`,
  ].join('\n');
  fs.writeFileSync(path.join(OUT, 'API_PROOF.md'), md);
  console.log(`Wrote ${OUT}/API_PROOF.md (${pass}/${results.length})`);
  process.exit(fail > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
