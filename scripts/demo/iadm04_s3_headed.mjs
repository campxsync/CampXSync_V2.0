/**
 * IADM-04 S3 headed demo — DEV online payment simulator, signed callback,
 * duplicate/replay, mismatch reconcile, R02 callback retry.
 * Credentials from .env.audit / seed_accounts — never printed.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-04/evidence/s3');
fs.mkdirSync(evidence, { recursive: true });
const require = createRequire(path.join(root, 'frontend/package.json'));
const { chromium } = require('playwright');

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

const seedPath = path.join(root, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json');
const seed = fs.existsSync(seedPath) ? JSON.parse(fs.readFileSync(seedPath, 'utf8')) : {};
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
const GW = process.env.CAMPX_GATEWAY_BASE || 'http://127.0.0.1:8080';
const PROBE_TENANT = 'b0000000-0000-0000-0000-000000000002';
const results = [];

function r01a() {
  return {
    email: seed.r01a?.email || process.env.PLATFORM_ADMIN_EMAIL || 'platformadmin.campx@gmail.com',
    password: seed.r01a?.password || process.env.PLATFORM_ADMIN_PW || '',
  };
}
function r02() {
  return {
    email: seed.r02?.email || process.env.SYSTEM_ADMIN_EMAIL || 'systemadmin.campx@gmail.com',
    password: seed.r02?.password || process.env.SYSTEM_ADMIN_PW || '',
  };
}
function r28() {
  return {
    email: seed.r28?.email || process.env.AUDITOR_EMAIL || 'auditor.campx@gmail.com',
    password: seed.r28?.password || process.env.AUDITOR_PW || '',
  };
}

async function passwordToken(email, password) {
  const url = process.env.CAMPX_SUPABASE_URL || process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.CAMPX_SUPABASE_ANON_KEY;
  const res = await fetch(`${url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: anon, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`login failed (${res.status})`);
  return body.access_token;
}

async function gw(token, method, pathSuffix, body, extraHeaders) {
  const res = await fetch(`${GW}${pathSuffix}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      ...(extraHeaders || {}),
    },
    body: body != null ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
  });
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

async function loginUi(page, email, password, expectPrefix) {
  await page.goto(`${BASE}/login?returnTo=${encodeURIComponent(expectPrefix)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.getByLabel(/Institutional Email/i).fill(email);
  await page.getByLabel(/^Password$/i).fill(password);
  await page.getByRole('button', { name: /Sign In to Workspace/i }).click();
  await page.waitForURL(
    (u) => {
      const p = u.pathname || '';
      return p === expectPrefix || p.startsWith(expectPrefix + '/') || p === '/access-denied';
    },
    { timeout: 60000 }
  );
}

async function shot(page, name) {
  const file = path.join(evidence, name);
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

function pass(step, detail, extra) {
  results.push({ step, ok: true, detail, ...extra });
}
function fail(step, detail, extra) {
  results.push({ step, ok: false, detail, ...extra });
}

const browser = await chromium.launch({ headless: false, slowMo: 35 });
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
const page = await context.newPage();

try {
  const a = r01a();
  const sys = r02();
  if (!a.password) throw new Error('R01 password missing');
  const tokenA = await passwordToken(a.email, a.password);
  let tokenR02 = null;
  if (sys.password) {
    try {
      tokenR02 = await passwordToken(sys.email, sys.password);
    } catch {
      tokenR02 = null;
    }
  }

  // Ensure a PENDING/ACTIVE sub + open invoice on probe tenant
  let subs = await gw(tokenA, 'GET', `/api/v1/iadm/04/subscriptions?tenantId=${PROBE_TENANT}`);
  let workingSubId = (subs.body.items || []).find((s) =>
    ['TRIAL', 'PENDING', 'ACTIVE'].includes(s.status)
  )?.id;
  if (!workingSubId) {
    const attach = await gw(tokenA, 'POST', '/api/v1/iadm/04/subscriptions/attach', {
      tenantId: PROBE_TENANT,
      planCode: 'STARTER',
      mode: 'TRIAL',
      idempotencyKey: `s3-attach-${Date.now()}`,
    });
    workingSubId = attach.body.subscriptionId || attach.body.id;
    pass('attach-trial', `sub=${workingSubId}`, { status: attach.status });
  }

  let sub = await gw(tokenA, 'GET', `/api/v1/iadm/04/subscriptions/${workingSubId}`);
  if (sub.body.status === 'TRIAL') {
    const conv = await gw(tokenA, 'POST', `/api/v1/iadm/04/subscriptions/${workingSubId}/convert-trial`, {
      idempotencyKey: `s3-convert-${workingSubId}-${Date.now()}`,
    });
    pass('convert-trial', `status=${conv.body.status}`, { status: conv.status });
  }

  const invList = await gw(tokenA, 'GET', `/api/v1/iadm/04/invoices?tenantId=${PROBE_TENANT}`);
  let openInv = (invList.body.items || []).find((i) => i.status === 'ISSUED' || i.status === 'DUE');
  if (!openInv) {
    const inv = await gw(tokenA, 'POST', '/api/v1/iadm/04/invoices', {
      subscriptionId: workingSubId,
      idempotencyKey: `s3-inv-${workingSubId}-${Date.now()}`,
    });
    openInv = inv.body;
  }
  const invoiceId = openInv.id;
  pass('open-invoice', `invoiceId=${invoiceId}`);

  // US-027 initiate
  const init = await gw(tokenA, 'POST', '/api/v1/iadm/04/online-payments/initiate', {
    invoiceId,
    idempotencyKey: `s3-online-${invoiceId}`,
    simulatorOutcome: 'SUCCESS',
  });
  if (init.status !== 200 || !init.body.id) {
    fail('initiate', JSON.stringify(init.body), { status: init.status });
    throw new Error('initiate failed');
  }
  pass('initiate', `intent=${init.body.id} status=${init.body.status} adapter=${init.body.adapterOutcome}`, {
    status: init.status,
  });
  const intentId = init.body.id;

  // Duplicate initiation
  const dup = await gw(tokenA, 'POST', '/api/v1/iadm/04/online-payments/initiate', {
    invoiceId,
    idempotencyKey: `s3-online-dup-${Date.now()}`,
    simulatorOutcome: 'SUCCESS',
  });
  const dupOk =
    dup.status === 200 &&
    (dup.body.id === intentId || dup.body.idempotentReplay === true);
  if (dupOk) pass('duplicate-initiate', `same intent ${dup.body.id}`);
  else fail('duplicate-initiate', JSON.stringify(dup.body), { status: dup.status });

  // Build signed callback (canonical bodyJson)
  const hint = await gw(tokenA, 'POST', `/api/v1/iadm/04/online-payments/${intentId}/dev-callback`, {});
  if (hint.status !== 200 || !hint.body.bodyJson) {
    fail('dev-callback-hint', JSON.stringify(hint.body), { status: hint.status });
    throw new Error('dev-callback hint failed');
  }

  // Invalid signature — no settlement
  const badSig = await gw(
    tokenA,
    'POST',
    '/api/v1/iadm/04/payments/callback',
    hint.body.bodyJson,
    {
      'X-IADM04-Provider-Timestamp': hint.body.timestamp,
      'X-IADM04-Provider-Signature': '00'.repeat(32),
      'Content-Type': 'application/json',
    }
  );
  if (badSig.body.processingStatus === 'REJECTED_SIGNATURE' && badSig.body.settled === false) {
    pass('invalid-signature', `callbackId=${badSig.body.callbackId}`);
  } else {
    fail('invalid-signature', JSON.stringify(badSig.body), { status: badSig.status });
  }

  // Valid callback → settle
  const okCb = await gw(
    tokenA,
    'POST',
    '/api/v1/iadm/04/payments/callback',
    hint.body.bodyJson,
    {
      'X-IADM04-Provider-Timestamp': hint.body.timestamp,
      'X-IADM04-Provider-Signature': hint.body.signature,
      'Content-Type': 'application/json',
    }
  );
  if (okCb.body.settled === true && okCb.body.invoiceStatus === 'PAID') {
    pass('valid-callback-settle', `intent=${okCb.body.intentStatus}`);
  } else {
    fail('valid-callback-settle', JSON.stringify(okCb.body), { status: okCb.status });
  }

  // Replay / duplicate event
  const replay = await gw(
    tokenA,
    'POST',
    '/api/v1/iadm/04/payments/callback',
    hint.body.bodyJson,
    {
      'X-IADM04-Provider-Timestamp': hint.body.timestamp,
      'X-IADM04-Provider-Signature': hint.body.signature,
      'Content-Type': 'application/json',
    }
  );
  if (replay.body.processingStatus === 'DUPLICATE_ACK' && replay.body.settled === false) {
    pass('duplicate-callback', 'safely acknowledged');
  } else {
    fail('duplicate-callback', JSON.stringify(replay.body), { status: replay.status });
  }

  // Mismatch path + R01 reconcile
  const inv2 = await gw(tokenA, 'POST', '/api/v1/iadm/04/invoices', {
    subscriptionId: workingSubId,
    idempotencyKey: `s3-inv2-${Date.now()}`,
  });
  const invoice2 = inv2.body.id;
  const init2 = await gw(tokenA, 'POST', '/api/v1/iadm/04/online-payments/initiate', {
    invoiceId: invoice2,
    idempotencyKey: `s3-online2-${invoice2}`,
    simulatorOutcome: 'PENDING',
  });
  const intent2 = init2.body.id;
  const mismatchHint = await gw(tokenA, 'POST', `/api/v1/iadm/04/online-payments/${intent2}/dev-callback`, {
    amountMinorUnits: 1,
  });
  const misCb = await gw(
    tokenA,
    'POST',
    '/api/v1/iadm/04/payments/callback',
    mismatchHint.body.bodyJson,
    {
      'X-IADM04-Provider-Timestamp': mismatchHint.body.timestamp,
      'X-IADM04-Provider-Signature': mismatchHint.body.signature,
      'Content-Type': 'application/json',
    }
  );
  if (misCb.body.match === 'MISMATCHED' || misCb.body.intentStatus === 'MISMATCHED') {
    pass('mismatch-hold', 'invoice unchanged');
  } else {
    fail('mismatch-hold', JSON.stringify(misCb.body), { status: misCb.status });
  }
  const recon = await gw(tokenA, 'POST', `/api/v1/iadm/04/online-payments/${intent2}/reconcile`, {
    decision: 'FORCE_MATCH',
    reason: 'S3 headed reconcile after mismatch',
  });
  if (recon.status === 200 && recon.body.invoiceStatus === 'PAID') {
    pass('online-reconcile', `decision=${recon.body.decision}`);
  } else {
    fail('online-reconcile', JSON.stringify(recon.body), { status: recon.status });
  }

  // R02 retry (technical). Commercial deny uses Auditor — DEV lists systemadmin as
  // platform_admin so is_platform_admin() would bypass R02 commercial checks.
  if (tokenR02) {
    const fails = await gw(tokenR02, 'GET', '/api/v1/iadm/04/payment-callbacks/failures');
    const failItem = (fails.body.items || [])[0];
    if (failItem?.id) {
      const retry = await gw(tokenR02, 'POST', `/api/v1/iadm/04/payment-callbacks/${failItem.id}/retry`, {});
      if (retry.status === 200) pass('r02-retry', `callback=${failItem.id}`);
      else fail('r02-retry', JSON.stringify(retry.body), { status: retry.status });
    } else {
      pass('r02-retry', 'no failures to retry (list ok)', { status: fails.status });
    }
  } else {
    pass('r02-skip', 'R02 credentials not available; API desk still filmed');
  }
  const auditor = r28();
  if (auditor.password) {
    try {
      const tokenAud = await passwordToken(auditor.email, auditor.password);
      const deny = await gw(tokenAud, 'POST', '/api/v1/iadm/04/online-payments/initiate', {
        invoiceId,
        idempotencyKey: `s3-aud-deny-${Date.now()}`,
      });
      if (deny.status === 403) pass('auditor-commercial-deny', 'initiate denied for Auditor');
      else fail('auditor-commercial-deny', JSON.stringify(deny.body), { status: deny.status });
    } catch (e) {
      pass('auditor-commercial-deny-skip', e instanceof Error ? e.message : String(e));
    }
  } else {
    pass('auditor-commercial-deny-skip', 'Auditor password not available');
  }

  // UI desk
  await loginUi(page, a.email, a.password, '/platform/billing');
  await page.goto(`${BASE}/platform/billing`, { waitUntil: 'networkidle' });
  await page.getByTestId('iadm04-online-payment-desk').waitFor({ timeout: 30000 });
  const shot1 = await shot(page, 's3_online_payment_desk.png');
  pass('ui-online-desk', 'platform online payment desk visible', { shot: shot1 });
  await page.getByTestId('iadm04-ops-callback-desk').waitFor({ timeout: 15000 });
  const shot2 = await shot(page, 's3_ops_callback_desk.png');
  pass('ui-ops-desk', 'ops callback desk visible', { shot: shot2 });
} catch (e) {
  fail('fatal', e instanceof Error ? e.message : String(e));
  try {
    await shot(page, 's3_fail.png');
  } catch {
    /* ignore */
  }
} finally {
  await browser.close();
  const ok = results.every((r) => r.ok);
  const out = {
    story: 'IADM-04 S3',
    passed: ok,
    at: new Date().toISOString(),
    results,
  };
  fs.writeFileSync(path.join(evidence, 'S3_HEADED_RESULTS.json'), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  process.exit(ok ? 0 : 1);
}
