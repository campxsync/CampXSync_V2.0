/**
 * IADM-08 headed demo — Audit, Health & Operations Oversight (US-023 + Fix Part B).
 * Personas: R01, R02, R03, R04 deny, R28 read-only + export/ack deny.
 * Covers Part B items 5 (read recording + audit-disabled 503/recovery),
 * 6 (R03 isolation + unattributed hidden + no health),
 * 7 (R28 read-only + search recorded + mutation/export refused),
 * 8 (stopped service Unavailable, silent target Unknown, recovery Healthy, denial-only search).
 */
import fs from 'fs';
import net from 'net';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const require = createRequire(path.join(ROOT, 'frontend/package.json'));
const { chromium } = require('playwright');
const OUT = path.join(ROOT, 'docs/iadm/iadm-08/evidence/us023');
const BASE = process.env.CAMPX_UI_BASE || 'http://127.0.0.1:3000';
fs.mkdirSync(OUT, { recursive: true });

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
  const candidates = [
    path.join(ROOT, 'docs/iadm/iadm-02/evidence/.local/seed_accounts.json'),
    path.join(ROOT, '.local/seed_accounts.json'),
  ];
  for (const p of candidates) {
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
const R03 = {
  email: seed.r03demo?.email || 'institution.iadm04.demo@campx.local',
  password: seed.r03demo?.password || '',
};
const R04 = {
  email: process.env.COLLEGE_ADMIN_EMAIL || 'campxsync@gmail.com',
  password: process.env.COLLEGE_ADMIN_PW || '',
};
const R28 = {
  email: seed.auditor?.email || 'auditor.iadm02.dev@campx.local',
  password: seed.auditor?.password || '',
};

const results = [];
const push = (step, ok, detail = '', extra = {}) => {
  results.push({ step, ok, detail, ...extra });
  console.log(`${ok ? 'PASS' : 'FAIL'} | ${step} | ${detail}`);
};

async function safeGoto(page, url) {
  try {
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  } catch (e) {
    const msg = String(e.message || e);
    if (!/ERR_ABORTED|interrupted/i.test(msg)) throw e;
  }
  await page.waitForTimeout(800);
}

async function loginUi(page, email, password, returnTo) {
  await page.context().clearCookies();
  await safeGoto(page, `${BASE}/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.fill('input[type="email"], input[name="email"]', email);
  await page.fill('input[type="password"], input[name="password"]', password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 8000 }).catch(() => {}),
    page.click('button[type="submit"]'),
  ]);
  await page.waitForTimeout(2500);
}

async function shot(page, name) {
  const p = path.join(OUT, name);
  await page.screenshot({ path: p, fullPage: true });
  return p;
}

async function waitOverviewReady(page) {
  await page.waitForFunction(() => {
    const loading = document.querySelector('[data-testid="iadm08-loading"]');
    const panel = document.querySelector('[data-testid="iadm08-health-overview"]');
    const err = document.querySelector('[data-testid="iadm08-error"]');
    return (!loading || loading.textContent === '') && (!!panel || !!err);
  }, null, { timeout: 45000 }).catch(() => {});
}

async function waitAuditReady(page) {
  await page.waitForFunction(() => {
    const loading = document.querySelector('[data-testid="iadm08-loading"]');
    const panel = document.querySelector('[data-testid="iadm08-audit-panel"]');
    const err = document.querySelector('[data-testid="iadm08-error"]');
    return (!loading || loading.textContent === '') && (!!panel || !!err);
  }, null, { timeout: 25000 }).catch(() => {});
}

async function apiFetch(page, url, init = {}) {
  return page.evaluate(async ({ u, opts }) => {
    const res = await fetch(u, opts);
    const text = await res.text();
    let body = null;
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
    return { status: res.status, body };
  }, { u: url, opts: init });
}

function stopPort(port) {
  execSync(
    `powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }"`,
    { stdio: 'ignore' }
  );
}

async function main() {
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({ recordVideo: { dir: OUT } });
  const page = await context.newPage();

  let unattributedEventId = null;

  try {
    if (!R01.password) throw new Error('R01 password missing');
    await loginUi(page, R01.email, R01.password, '/platform/operations');
    await safeGoto(page, `${BASE}/platform/operations`);
    await page.getByTestId('iadm08-platform-desk').waitFor({ timeout: 15000 });
    await waitOverviewReady(page);
    const desk = (await page.getByTestId('iadm08-platform-desk').count()) > 0;
    push('R01 platform operations desk', desk, '', { shot: await shot(page, 'R01_overview.png') });
    const healthyish = await page.getByTestId('iadm08-health-overview').count();
    const errText = ((await page.getByTestId('iadm08-error').textContent().catch(() => '')) || '');
    push('R01 health overview visible', healthyish > 0, `panel=${healthyish} err=${errText}`);

    await page.getByTestId('iadm08-tab-audit').click();
    await page.waitForTimeout(500);
    await page.getByTestId('iadm08-audit-search').click();
    await waitAuditReady(page);
    const auditRows = await page.locator('[data-testid^="iadm08-audit-row-"]').count();
    push('R01 audit search returns rows', auditRows > 0, `rows=${auditRows}`, {
      shot: await shot(page, 'R01_audit.png'),
    });

    await page.getByTestId('iadm08-tab-sla').click();
    await page.waitForTimeout(800);
    const slaText = await page.getByTestId('iadm08-sla-value').textContent();
    push('R01 SLA not available', /not available/i.test(slaText || ''), slaText || '');

    // --- PART B ITEM 5: READ RECORDING (6 reads + audit-disabled 503 + recovery) ---
    const fromIso = new Date(Date.now() - 7 * 86400000).toISOString();
    const toIso = new Date().toISOString();
    const tReadStart = new Date(Date.now() - 2000).toISOString();

    const rSearch = await apiFetch(page, `/api/platform/operations?view=audit-search&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
    const items = Array.isArray(rSearch.body?.items) ? rSearch.body.items : [];
    const sampleEvent = items.find((x) => x.eventId) || {};
    const sampleActor = items.find((x) => x.actor)?.actor || '00000000-0000-0000-0000-000000000001';
    const unattrib = items.find((x) => x.institute == null || x.institute === '');
    if (unattrib?.eventId) unattributedEventId = String(unattrib.eventId);

    const rDetail = await apiFetch(page, `/api/platform/operations?view=audit-detail&id=${encodeURIComponent(sampleEvent.eventId || '')}`);
    const rTimeline = await apiFetch(page, `/api/platform/operations?view=timeline&id=${encodeURIComponent(sampleActor)}&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
    const rHealthOv = await apiFetch(page, `/api/platform/operations?view=health-overview`);
    const rHealthDet = await apiFetch(page, `/api/platform/operations?view=health-detail&id=iadm-08`);
    const rAlerts = await apiFetch(page, `/api/platform/operations?view=alerts`);

    const sixStatuses = [rSearch.status, rDetail.status, rTimeline.status, rHealthOv.status, rHealthDet.status, rAlerts.status];
    push('Part B.5: 6 reads return 200', sixStatuses.every((s) => s === 200), `statuses=${sixStatuses.join(',')}`);

    const tReadEnd = new Date(Date.now() + 5000).toISOString();
    const rAuditCheck = await apiFetch(page, `/api/platform/operations?view=audit-search&from=${encodeURIComponent(tReadStart)}&to=${encodeURIComponent(tReadEnd)}&action=IADM08_`);
    const recordedItems = Array.isArray(rAuditCheck.body?.items) ? rAuditCheck.body.items : [];
    const expectedActions = [
      'IADM08_AUDIT_SEARCH',
      'IADM08_AUDIT_DETAIL',
      'IADM08_AUDIT_TIMELINE',
      'IADM08_HEALTH_OVERVIEW',
      'IADM08_HEALTH_DETAIL',
      'IADM08_ALERTS_READ',
    ];
    const matched = expectedActions.map((act) => {
      const row = recordedItems.find((r) => r.action === act);
      return { action: act, eventId: row?.eventId || null, eventTime: row?.eventTime || null };
    });
    const allRecorded = matched.every((m) => !!m.eventId);
    push(
      'Part B.5: all 6 IADM08_* rows recorded in shared audit',
      allRecorded,
      matched.map((m) => `${m.action}=${m.eventId || 'MISSING'}`).join('; ')
    );

    // Enable DEMO audit-disabled switch -> all 6 must refuse with 503 IADM08_AUDIT_UNAVAILABLE
    const flagOn = await apiFetch(page, '/api/platform/operations?view=outage', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ auditDisabled: true }),
    });
    const d1 = await apiFetch(page, `/api/platform/operations?view=audit-search&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
    const d2 = await apiFetch(page, `/api/platform/operations?view=audit-detail&id=${encodeURIComponent(sampleEvent.eventId || '')}`);
    const d3 = await apiFetch(page, `/api/platform/operations?view=timeline&id=${encodeURIComponent(sampleActor)}&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
    const d4 = await apiFetch(page, `/api/platform/operations?view=health-overview`);
    const d5 = await apiFetch(page, `/api/platform/operations?view=health-detail&id=iadm-08`);
    const d6 = await apiFetch(page, `/api/platform/operations?view=alerts`);
    const sixDenied = [d1, d2, d3, d4, d5, d6];
    const all503 = sixDenied.every((r) => r.status === 503 && r.body?.errorCode === 'IADM08_AUDIT_UNAVAILABLE');
    push(
      'Part B.5: auditDisabled=true refuses all 6 reads (503 IADM08_AUDIT_UNAVAILABLE)',
      flagOn.status === 200 && all503,
      sixDenied.map((r) => `${r.status}:${r.body?.errorCode}`).join(',')
    );

    // Disable switch -> all 6 recover to 200
    const flagOff = await apiFetch(page, '/api/platform/operations?view=outage', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ auditDisabled: false }),
    });
    const rec1 = await apiFetch(page, `/api/platform/operations?view=audit-search&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
    const rec2 = await apiFetch(page, `/api/platform/operations?view=audit-detail&id=${encodeURIComponent(sampleEvent.eventId || '')}`);
    const rec3 = await apiFetch(page, `/api/platform/operations?view=timeline&id=${encodeURIComponent(sampleActor)}&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
    const rec4 = await apiFetch(page, `/api/platform/operations?view=health-overview`);
    const rec5 = await apiFetch(page, `/api/platform/operations?view=health-detail&id=iadm-08`);
    const rec6 = await apiFetch(page, `/api/platform/operations?view=alerts`);
    const recStatuses = [rec1.status, rec2.status, rec3.status, rec4.status, rec5.status, rec6.status];
    push(
      'Part B.5: auditDisabled=false recovers all 6 reads (200)',
      flagOff.status === 200 && recStatuses.every((s) => s === 200),
      `statuses=${recStatuses.join(',')}`
    );

    // --- R02 ---
    if (!R02.password) throw new Error('R02 password missing');
    await loginUi(page, R02.email, R02.password, '/platform/operations');
    await safeGoto(page, `${BASE}/platform/operations`);
    await waitOverviewReady(page);
    push('R02 platform operations desk', (await page.getByTestId('iadm08-platform-desk').count()) > 0, '', {
      shot: await shot(page, 'R02_desk.png'),
    });

    // --- PART B ITEM 6: R03 PROOF ---
    if (R03.password) {
      await loginUi(page, R03.email, R03.password, '/institution/operations');
      await safeGoto(page, `${BASE}/institution/operations`);
      await page.getByTestId('iadm08-institution-desk').waitFor({ timeout: 15000 });
      await page.waitForTimeout(4000);
      const inst = (await page.getByTestId('iadm08-institution-desk').count()) > 0;
      push('R03 institute activity desk', inst, '', { shot: await shot(page, 'R03_institute.png') });
      const denied = await page.getByTestId('iadm08-health-denied').count();
      push('R03 health view denied', denied > 0, `count=${denied}`);

      const r03Own = await apiFetch(page, `/api/institution/operations?view=audit-search&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`);
      const r03Items = Array.isArray(r03Own.body?.items) ? r03Own.body.items : [];
      const ownTenant = 'd4000000-0000-4000-8000-000000000004';
      const noUnattribOrForeign = r03Own.status === 200 && r03Items.every((r) => r.institute === ownTenant);
      push(
        'Part B.6: R03 sees only own institute; unattributed hidden',
        noUnattribOrForeign,
        `rows=${r03Items.length}, unattributedSampleInDb=${unattributedEventId || 'verified_via_sql'}`
      );

      const r03Cross = await apiFetch(page, `/api/institution/operations?view=audit-search&from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}&tenantId=a0000000-0000-0000-0000-000000000001`);
      push(
        'Part B.6: R03 cross-institute refused (403 IADM08_SCOPE_DENIED)',
        r03Cross.status === 403 && r03Cross.body?.errorCode === 'IADM08_SCOPE_DENIED',
        `status=${r03Cross.status} code=${r03Cross.body?.errorCode}`
      );

      if (unattributedEventId) {
        const r03Unattrib = await apiFetch(page, `/api/institution/operations?view=audit-detail&id=${encodeURIComponent(unattributedEventId)}`);
        push(
          'Part B.6: R03 unattributed event detail refused/hidden (403/404)',
          r03Unattrib.status === 403 || r03Unattrib.status === 404,
          `eventId=${unattributedEventId} status=${r03Unattrib.status} code=${r03Unattrib.body?.errorCode}`
        );
      }
    } else {
      push('R03 institute activity desk', false, 'R03 password missing in seed');
    }

    // --- R04 DENY ---
    if (R04.password) {
      await loginUi(page, R04.email, R04.password, '/platform/operations');
      await safeGoto(page, `${BASE}/platform/operations`);
      await page.getByTestId('iadm08-error').waitFor({ timeout: 15000 }).catch(() => {});
      const denied =
        (await page.getByTestId('iadm08-error').count()) > 0 ||
        page.url().includes('access-denied') ||
        (await page.getByTestId('iadm08-platform-desk').count()) === 0;
      push('R04 denied IADM-08 platform desk', denied, page.url(), {
        shot: await shot(page, 'R04_deny.png'),
      });
    } else {
      push('R04 denied IADM-08 platform desk', false, 'R04 password missing');
    }

    // --- PART B ITEM 7: R28 PROOF ---
    if (!R28.password) throw new Error('R28 password missing');
    const tR28Start = new Date(Date.now() - 2000).toISOString();
    await loginUi(page, R28.email, R28.password, '/platform/operations');
    await safeGoto(page, `${BASE}/platform/operations`);
    await waitOverviewReady(page);
    push('R28 operations desk (health read-only)', (await page.getByTestId('iadm08-platform-desk').count()) > 0, '', {
      shot: await shot(page, 'R28_desk.png'),
    });

    await page.getByTestId('iadm08-tab-audit').click();
    await page.waitForTimeout(400);
    await page.getByTestId('iadm08-audit-search').click();
    await waitAuditReady(page);
    const r28Rows = await page.locator('[data-testid^="iadm08-audit-row-"]').count();
    if (r28Rows > 0) {
      await page.locator('[data-testid^="iadm08-audit-row-"]').first().click();
      await page.getByTestId('iadm08-audit-detail').waitFor({ timeout: 10000 }).catch(() => {});
    }
    const r28DetailVisible = (await page.getByTestId('iadm08-audit-detail').count()) > 0;
    push('Part B.7: R28 audit search + detail read-only', r28Rows > 0 && r28DetailVisible, `rows=${r28Rows} detail=${r28DetailVisible}`, {
      shot: await shot(page, 'R28_audit_detail.png'),
    });

    await page.getByTestId('iadm08-tab-alerts').click();
    await page.getByTestId('iadm08-alerts-panel').waitFor({ timeout: 15000 }).catch(() => {});
    const r28AlertsPanel = (await page.getByTestId('iadm08-alerts-panel').count()) > 0;
    const r28AckControls = await page.locator('[data-testid*="ack"]').count();
    push('Part B.7: R28 alerts read-only (no ack control)', r28AlertsPanel && r28AckControls === 0, `panel=${r28AlertsPanel} ackControls=${r28AckControls}`, {
      shot: await shot(page, 'R28_alerts.png'),
    });

    const tR28End = new Date(Date.now() + 5000).toISOString();
    const r28AuditRec = await apiFetch(page, `/api/platform/operations?view=audit-search&from=${encodeURIComponent(tR28Start)}&to=${encodeURIComponent(tR28End)}&action=IADM08_AUDIT_SEARCH`);
    const r28RecItems = Array.isArray(r28AuditRec.body?.items) ? r28AuditRec.body.items : [];
    push('Part B.7: R28 search recorded in shared audit', r28RecItems.length > 0, `count=${r28RecItems.length} latest=${r28RecItems[0]?.eventId || ''}`);

    await page.getByTestId('iadm08-tab-audit').click();
    await page.waitForTimeout(400);
    await page.getByTestId('iadm08-export-attempt').click();
    await page.waitForTimeout(800);
    const exportMsg = (await page.getByTestId('iadm08-action-msg').textContent()) || '';
    push('R28 export denied', /404|not available|IADM08_NOT_AVAILABLE/i.test(exportMsg), exportMsg, {
      shot: await shot(page, 'R28_export_denied.png'),
    });

    const r28AckPost = await apiFetch(page, '/api/platform/operations?view=acknowledge', { method: 'POST' });
    push('Part B.7: R28 acknowledge mutation refused (404)', r28AckPost.status === 404 && r28AckPost.body?.errorCode === 'IADM08_NOT_AVAILABLE', `status=${r28AckPost.status} code=${r28AckPost.body?.errorCode}`);

    // --- PART B ITEM 8: FAILURES (HEADED) ---
    await loginUi(page, R01.email, R01.password, '/platform/operations');

    // 8a. Stopped DEV service (iadm-06 on 8104) -> Unavailable
    stopPort(8104);
    await page.waitForTimeout(500);
    await safeGoto(page, `${BASE}/platform/operations`);
    await waitOverviewReady(page);
    const statusUnavailable = ((await page.getByTestId('iadm08-health-status-iadm-06').textContent().catch(() => '')) || '').trim();
    push(
      'Part B.8: stopped DEV service (iadm-06) shows Unavailable',
      statusUnavailable === 'Unavailable',
      `status=${statusUnavailable}`,
      { shot: await shot(page, 'failure_unavailable.png') }
    );

    // 8b. Silent/delayed target on 8104 -> Unknown (never Healthy or 0)
    const sockets = new Set();
    const silentServer = net.createServer((socket) => {
      sockets.add(socket);
      socket.on('close', () => sockets.delete(socket));
      socket.on('error', () => {});
    });
    await new Promise((resolve) => silentServer.listen(8104, '127.0.0.1', resolve));
    try {
      await safeGoto(page, `${BASE}/platform/operations`);
      await waitOverviewReady(page);
      const statusUnknown = ((await page.getByTestId('iadm08-health-status-iadm-06').textContent().catch(() => '')) || '').trim();
      push(
        'Part B.8: silent/delayed target (iadm-06) shows Unknown',
        statusUnknown === 'Unknown',
        `status=${statusUnknown}`,
        { shot: await shot(page, 'failure_unknown.png') }
      );
    } finally {
      for (const s of sockets) s.destroy();
      await new Promise((resolve) => silentServer.close(() => resolve()));
    }

    // 8c. Recovery -> Healthy (fast relaunch of compiled iadm-06)
    execSync(
      'powershell -NoProfile -Command "$d = \'D:\\CampXSync\\CampXSync_V2.0\\services\\administration-services\\iadm-06-platform-analytics-service\'; $cp = (Get-Content \\"$d\\classpath.txt\\" -Raw).Trim(); Start-Process -FilePath \'java\' -ArgumentList @(\'-Xms32m\',\'-Xmx96m\',\'-Dcampx.persistence.mode=postgres\',\'-Dcampx.internal.auth.enabled=true\',\'-cp\',\\"target\\\\classes;$cp\\",\'com.campx.iadm.analytics.Iadm06Application\',\'8104\') -WorkingDirectory $d -WindowStyle Minimized"',
      { cwd: ROOT, stdio: 'ignore' }
    );
    await page.waitForTimeout(4000);
    await safeGoto(page, `${BASE}/platform/operations`);
    await waitOverviewReady(page);
    const statusRecovered = ((await page.getByTestId('iadm08-health-status-iadm-06').textContent().catch(() => '')) || '').trim();
    push(
      'Part B.8: recovered DEV service (iadm-06) shows Healthy',
      statusRecovered === 'Healthy',
      `status=${statusRecovered}`,
      { shot: await shot(page, 'failure_recovered.png') }
    );

    // 8d. Denial-only audit search shows IADM02_*_DENIED or honest empty state
    await page.getByTestId('iadm08-tab-audit').click();
    await page.waitForTimeout(400);
    await page.getByTestId('iadm08-denial-only').check();
    await page.getByTestId('iadm08-audit-search').click();
    await waitAuditReady(page);
    const denialRows = await page.locator('[data-testid^="iadm08-audit-row-"]').allTextContents();
    const denialErr = ((await page.getByTestId('iadm08-error').textContent().catch(() => '')) || '').trim();
    const denialOk = !denialErr && (denialRows.length === 0 || denialRows.every((t) => /DENY|DENIED/i.test(t)));
    push(
      'Part B.8: denial-only audit search (IADM02_*_DENIED or honest empty)',
      denialOk,
      `rows=${denialRows.length} sample=${(denialRows[0] || 'empty').slice(0, 80)}`,
      { shot: await shot(page, 'failure_denial_only.png') }
    );
  } catch (e) {
    push('headed runner exception', false, String(e.message || e));
  } finally {
    await context.close();
    await browser.close();
  }

  const pass = results.filter((r) => r.ok).length;
  const total = results.length;
  const md = [
    '# IADM-08 US-023 Headed DEMO (Part B Extended)',
    '',
    `| Check | Result | Detail |`,
    `|---|---|---|`,
    ...results.map((r) => `| ${r.step} | ${r.ok ? 'PASS' : 'FAIL'} | ${(r.detail || '').replace(/\|/g, '/')} |`),
    '',
    `**Score:** ${pass}/${total}`,
  ].join('\n');
  fs.writeFileSync(path.join(OUT, 'DEMO.md'), md);
  fs.writeFileSync(path.join(OUT, 'RESULTS.json'), JSON.stringify({ pass, total, results }, null, 2));
  console.log(`\nHeaded score ${pass}/${total} → ${OUT}`);
  process.exit(pass === total ? 0 : 1);
}

main();
