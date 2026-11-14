#!/usr/bin/env node
/**
 * CampXSync H-4 — Pilot college onboarding CLI
 *
 * Dry-run (default): validate tenant config + student/faculty CSVs; no writes.
 * Apply: requires --apply --approve "GO PROVISION-TENANT"; posts tenant+admin
 *        to BFF /api/platform/onboard-tenant (live write only with approval header).
 *        Roster apply prints an import plan and refuses permanent DB mutation unless
 *        --roster-apply is also set (still validation-gated; no Auth user mint here).
 *
 * Usage:
 *   node scripts/dev/pilot_onboard.mjs --config .../tenant.json --students .../students.csv --faculty .../faculty.csv
 *   node scripts/dev/pilot_onboard.mjs ... --dry-run
 *   node scripts/dev/pilot_onboard.mjs ... --apply --approve "GO PROVISION-TENANT" --bearer <jwt>
 *
 * Never hardcodes passwords. Dev ref azulbaxszsqxymqmlwfe only.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import http from 'node:http';
import https from 'node:https';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '../..');
const FORBIDDEN_PROD = 'ekspahbsbwdpraaqfqui';
const FORBIDDEN_LEGACY = 'kpwdvvcschankgggitnj';
const EXPECTED_DEV = 'azulbaxszsqxymqmlwfe';
const APPROVAL_PHRASE = 'GO PROVISION-TENANT';

const STUDENT_REQUIRED = [
  'enrollment_number',
  'first_name',
  'last_name',
  'email',
  'program_code',
  'batch_code',
];
const FACULTY_REQUIRED = [
  'employee_code',
  'first_name',
  'last_name',
  'email',
  'department_code',
  'title',
];

function parseArgs(argv) {
  const out = {
    config: path.join(__dirname, 'fixtures/pilot/tenant.json'),
    students: path.join(__dirname, 'fixtures/pilot/students.csv'),
    faculty: path.join(__dirname, 'fixtures/pilot/faculty.csv'),
    dryRun: true,
    apply: false,
    rosterApply: false,
    approve: null,
    bearer: process.env.CAMPX_BEARER_TOKEN || null,
    baseUrl: process.env.CAMPX_UI_URL || 'http://localhost:3000',
    expectFail: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    if (a === '--config') out.config = path.resolve(next());
    else if (a === '--students') out.students = path.resolve(next());
    else if (a === '--faculty') out.faculty = path.resolve(next());
    else if (a === '--dry-run') {
      out.dryRun = true;
      out.apply = false;
    } else if (a === '--apply') {
      out.apply = true;
      out.dryRun = false;
    } else if (a === '--roster-apply') out.rosterApply = true;
    else if (a === '--approve') out.approve = next();
    else if (a === '--bearer') out.bearer = next();
    else if (a === '--base-url') out.baseUrl = next();
    else if (a === '--expect-fail') out.expectFail = true;
    else if (a === '--help' || a === '-h') out.help = true;
  }
  return out;
}

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  const raw = fs.readFileSync(filePath, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx <= 0) continue;
    const k = trimmed.slice(0, idx).trim();
    let v = trimmed.slice(idx + 1).trim();
    if (
      (v.startsWith('"') && v.endsWith('"')) ||
      (v.startsWith("'") && v.endsWith("'"))
    ) {
      v = v.slice(1, -1);
    }
    if (!process.env[k]) process.env[k] = v;
  }
}

function assertDevTarget() {
  const jdbc =
    process.env.SUPABASE_JDBC_URL ||
    process.env.CAMPX_JDBC_URL ||
    process.env.DATABASE_URL ||
    '';
  const user = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER || '';
  const blob = `${jdbc} ${user}`;
  if (blob.includes(FORBIDDEN_PROD)) {
    throw new Error('FATAL: production database ref is forbidden.');
  }
  if (blob.includes(FORBIDDEN_LEGACY)) {
    throw new Error('FATAL: legacy staging database ref is forbidden.');
  }
  if (jdbc || user) {
    if (!blob.includes(EXPECTED_DEV)) {
      console.warn(
        `[WARN] JDBC/user env present but does not mention ${EXPECTED_DEV}; continuing in validation-only mode.`
      );
    } else {
      console.log(`[OK] Dev database target guard: ${EXPECTED_DEV}`);
    }
  } else {
    console.log('[OK] No JDBC env loaded — validation/dry-run only (safe).');
  }
}

function parseCsv(text) {
  const lines = text
    .replace(/^\uFEFF/, '')
    .split(/\r?\n/)
    .filter((l) => l.trim().length > 0);
  if (lines.length < 2) {
    return { headers: [], rows: [] };
  }
  const headers = splitCsvLine(lines[0]).map((h) => h.trim().toLowerCase());
  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    const cols = splitCsvLine(lines[i]);
    const row = {};
    headers.forEach((h, idx) => {
      row[h] = (cols[idx] || '').trim();
    });
    row.__line = i + 1;
    rows.push(row);
  }
  return { headers, rows };
}

function splitCsvLine(line) {
  const out = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === ',' && !inQuotes) {
      out.push(cur);
      cur = '';
    } else {
      cur += ch;
    }
  }
  out.push(cur);
  return out;
}

function isEmail(v) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
}

function validateTenant(cfg) {
  const errors = [];
  if (!cfg.tenantName || String(cfg.tenantName).trim().length < 3) {
    errors.push('tenantName: min 3 characters');
  }
  if (!/^[A-Z0-9_-]{2,30}$/.test(String(cfg.tenantCode || ''))) {
    errors.push('tenantCode: 2-30 uppercase alphanumeric/_/-');
  }
  const sub = String(cfg.subdomain || cfg.tenantCode || '')
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '');
  if (!/^[a-z0-9-]{2,30}$/.test(sub)) {
    errors.push('subdomain: 2-30 lowercase alphanumeric/-');
  }
  if (!cfg.collegeName || String(cfg.collegeName).trim().length < 3) {
    errors.push('collegeName: min 3 characters');
  }
  if (!/^[A-Z0-9_-]{2,30}$/.test(String(cfg.collegeCode || ''))) {
    errors.push('collegeCode: 2-30 uppercase alphanumeric/_/-');
  }
  if (!cfg.adminName || String(cfg.adminName).trim().length < 2) {
    errors.push('adminName: required');
  }
  if (!isEmail(String(cfg.adminEmail || ''))) {
    errors.push('adminEmail: valid email required');
  }
  const plan = cfg.plan || 'TRIAL';
  if (!['ENTERPRISE', 'GROWTH', 'STANDARD', 'TRIAL'].includes(plan)) {
    errors.push('plan: must be ENTERPRISE|GROWTH|STANDARD|TRIAL');
  }
  return {
    errors,
    normalized: {
      ...cfg,
      tenantCode: String(cfg.tenantCode).toUpperCase(),
      collegeCode: String(cfg.collegeCode).toUpperCase(),
      subdomain: sub,
      plan,
    },
  };
}

function validateRoster(label, { headers, rows }, required, keyField) {
  const errors = [];
  for (const col of required) {
    if (!headers.includes(col)) {
      errors.push(`${label}: missing required column '${col}'`);
    }
  }
  const seen = new Map();
  rows.forEach((row) => {
    for (const col of required) {
      if (!row[col]) {
        errors.push(`${label} line ${row.__line}: '${col}' is required`);
      }
    }
    if (row.email && !isEmail(row.email)) {
      errors.push(`${label} line ${row.__line}: invalid email '${row.email}'`);
    }
    const key = row[keyField];
    if (key) {
      if (seen.has(key)) {
        errors.push(
          `${label} line ${row.__line}: duplicate ${keyField} '${key}' (also line ${seen.get(key)})`
        );
      } else {
        seen.set(key, row.__line);
      }
    }
  });
  return errors;
}

function httpJson(method, urlStr, { headers = {}, body = null } = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(urlStr);
    const lib = url.protocol === 'https:' ? https : http;
    const payload = body == null ? null : JSON.stringify(body);
    const req = lib.request(
      {
        hostname: url.hostname,
        port: url.port || (url.protocol === 'https:' ? 443 : 80),
        path: url.pathname + url.search,
        method,
        headers: {
          Accept: 'application/json',
          ...(payload
            ? {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload),
              }
            : {}),
          ...headers,
        },
      },
      (res) => {
        let data = '';
        res.on('data', (c) => (data += c));
        res.on('end', () => {
          let json = null;
          try {
            json = data ? JSON.parse(data) : null;
          } catch {
            json = { raw: data };
          }
          resolve({ status: res.statusCode, json, raw: data });
        });
      }
    );
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log(`Usage: node scripts/dev/pilot_onboard.mjs [options]
  --config <tenant.json>   Tenant + admin config (default: fixtures/pilot/tenant.json)
  --students <csv>         Students roster CSV
  --faculty <csv>          Faculty roster CSV
  --dry-run                Validate only (default)
  --apply                  Call BFF onboard-tenant (requires --approve)
  --approve "${APPROVAL_PHRASE}"  Live write gate
  --roster-apply           Print roster apply plan (no Auth mint / no silent DB write)
  --bearer <jwt>           Platform admin Bearer token for apply
  --expect-fail            Exit 0 when validation fails (demo negative path)
`);
    process.exit(0);
  }

  loadEnvFile(path.join(REPO_ROOT, '.env.audit'));
  loadEnvFile(path.join(REPO_ROOT, 'frontend', '.env.local'));
  assertDevTarget();

  console.log('=== CampXSync Pilot Onboarding (H-4) ===');
  console.log(`Mode: ${args.apply ? 'APPLY' : 'DRY-RUN'}`);
  console.log(`Config:   ${args.config}`);
  console.log(`Students: ${args.students}`);
  console.log(`Faculty:  ${args.faculty}`);

  const cfg = JSON.parse(fs.readFileSync(args.config, 'utf8'));
  const students = parseCsv(fs.readFileSync(args.students, 'utf8'));
  const faculty = parseCsv(fs.readFileSync(args.faculty, 'utf8'));

  const tenantResult = validateTenant(cfg);
  const errors = [
    ...tenantResult.errors,
    ...validateRoster('students', students, STUDENT_REQUIRED, 'enrollment_number'),
    ...validateRoster('faculty', faculty, FACULTY_REQUIRED, 'employee_code'),
  ];

  if (errors.length) {
    console.error(`\n[FAIL] Validation failed with ${errors.length} error(s):`);
    errors.forEach((e) => console.error(`  - ${e}`));
    if (args.expectFail) {
      console.log('\n[OK] --expect-fail: invalid roster rejected as expected.');
      process.exit(0);
    }
    process.exit(1);
  }

  const plan = {
    tenant: tenantResult.normalized,
    studentCount: students.rows.length,
    facultyCount: faculty.rows.length,
    students: students.rows.map((r) => ({
      enrollment_number: r.enrollment_number,
      email: r.email,
      program_code: r.program_code,
      batch_code: r.batch_code,
    })),
    faculty: faculty.rows.map((r) => ({
      employee_code: r.employee_code,
      email: r.email,
      department_code: r.department_code,
      title: r.title,
    })),
  };

  console.log('\n[OK] Validation passed.');
  console.log(
    `Plan: tenant=${plan.tenant.tenantCode} admin=${plan.tenant.adminEmail} students=${plan.studentCount} faculty=${plan.facultyCount}`
  );

  const reportPath = path.join(
    REPO_ROOT,
    'docs',
    'demos',
    'H-4',
    'onboarding-plan.json'
  );
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, JSON.stringify({ mode: args.apply ? 'APPLY' : 'DRY_RUN', plan }, null, 2));
  console.log(`[OK] Wrote plan report: ${reportPath}`);

  if (!args.apply) {
    console.log('\nDry-run complete. No database writes performed.');
    console.log('To provision tenant+admin live: --apply --approve "GO PROVISION-TENANT" --bearer <jwt>');
    process.exit(0);
  }

  if (args.approve !== APPROVAL_PHRASE) {
    console.error(
      `\n[FAIL] --apply requires exact --approve "${APPROVAL_PHRASE}" (got ${args.approve ? 'mismatched phrase' : 'none'}).`
    );
    process.exit(1);
  }
  if (!args.bearer) {
    console.error('[FAIL] --apply requires --bearer <platform-admin JWT> (or CAMPX_BEARER_TOKEN).');
    process.exit(1);
  }

  console.log('\n--- Calling BFF /api/platform/onboard-tenant (live gate) ---');
  const res = await httpJson('POST', `${args.baseUrl}/api/platform/onboard-tenant`, {
    headers: {
      Authorization: `Bearer ${args.bearer}`,
      Cookie: '', // session may also be needed; Bearer preferred under H-3
      'x-provision-approval': APPROVAL_PHRASE,
      'Idempotency-Key': `pilot-${plan.tenant.tenantCode}-${Date.now()}`,
    },
    body: plan.tenant,
  });
  console.log(`BFF status: ${res.status}`);
  console.log(`BFF gated: ${res.json?.gated}`);
  console.log(`BFF message: ${res.json?.message || res.json?.detail || '(none)'}`);

  if (res.status >= 400) {
    console.error('[FAIL] Onboard-tenant request failed.');
    process.exit(1);
  }

  if (args.rosterApply) {
    console.log('\n--- Roster apply plan (no Auth user mint in H-4) ---');
    console.log(
      JSON.stringify(
        {
          note: 'H-4 validates and plans roster import. Creating auth.users requires explicit Supabase Admin approval beyond this story.',
          studentsQueued: plan.studentCount,
          facultyQueued: plan.facultyCount,
        },
        null,
        2
      )
    );
  }

  console.log('\n[OK] Apply path finished.');
  process.exit(0);
}

main().catch((err) => {
  console.error('[FATAL]', err.message || err);
  process.exit(1);
});
