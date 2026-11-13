#!/usr/bin/env node
/**
 * Traceability verifier for docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv
 * For every row marked done/verified/implemented (PASS / DONE / VERIFIED / IMPLEMENTED):
 *   (1) demo pack exists at docs/demos/<story ID>/DEMO.md
 *   (2) a test file referencing the story or its service exists
 *   (3) a commit message or tracked file mentions the story
 * Writes docs/reports/TRACEABILITY_VERIFICATION.md
 * No tests are run. No stack is required.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '../..');
const CSV_PATH = path.join(ROOT, 'docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv');
const DEMOS_DIR = path.join(ROOT, 'docs/demos');
const OUT_PATH = path.join(ROOT, 'docs/reports/TRACEABILITY_VERIFICATION.md');

const DONE_STATUSES = new Set(['PASS', 'DONE', 'VERIFIED', 'IMPLEMENTED', 'COMPLETE', 'CLOSED']);

function parseCsv(text) {
  const rows = [];
  let i = 0;
  let field = '';
  let row = [];
  let inQuotes = false;
  while (i < text.length) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        inQuotes = false;
        i += 1;
        continue;
      }
      field += c;
      i += 1;
      continue;
    }
    if (c === '"') {
      inQuotes = true;
      i += 1;
      continue;
    }
    if (c === ',') {
      row.push(field);
      field = '';
      i += 1;
      continue;
    }
    if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i += 1;
      row.push(field);
      if (row.some((v) => v.trim() !== '')) rows.push(row);
      row = [];
      field = '';
      i += 1;
      continue;
    }
    field += c;
    i += 1;
  }
  if (field.length || row.length) {
    row.push(field);
    if (row.some((v) => v.trim() !== '')) rows.push(row);
  }
  return rows;
}

function moduleOf(storyId) {
  const m = String(storyId || '').match(/^(STM-?\d+)/i);
  if (!m) return 'OTHER';
  return m[1].replace(/STM(\d)/i, 'STM-$1').toUpperCase().replace('STM-', 'STM-');
}

function normalizeModule(storyId) {
  const raw = String(storyId || '');
  const m = raw.match(/STM-?(\d+)/i);
  if (!m) return 'OTHER';
  return `STM-${m[1].padStart(2, '0')}`;
}

function hasDemo(storyId) {
  // Exact story-ID folder only. Legacy STM04-US-* must not inherit docs/demos/STM-04-US-*.
  return fs.existsSync(path.join(DEMOS_DIR, storyId, 'DEMO.md'));
}

function fileMentions(storyId) {
  try {
    const out = execSync(
      `git grep -l -- "${storyId}" -- "*.java" "*.ts" "*.tsx" "*.md" "*.json" "*.mjs" "*.csv" 2>nul`,
      { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
    );
    return out.trim().split(/\r?\n/).filter(Boolean);
  } catch {
    return [];
  }
}

function commitMentions(storyId) {
  try {
    const out = execSync(`git log --all --oneline --grep="${storyId}" -n 5`, {
      cwd: ROOT,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    return out.trim().split(/\r?\n/).filter(Boolean);
  } catch {
    return [];
  }
}

function serviceHint(implFiles) {
  const text = String(implFiles || '');
  const hints = [];
  if (/lifecycle/i.test(text)) hints.push('lifecycle-service');
  if (/document/i.test(text)) hints.push('document-service');
  if (/guardian/i.test(text)) hints.push('guardian-service');
  if (/enrollment/i.test(text)) hints.push('enrollment-service');
  if (/admission/i.test(text)) hints.push('admissions-service');
  if (/student/i.test(text) && !/lifecycle|document|guardian|enrollment|admission/i.test(text)) {
    hints.push('student-service');
  }
  return hints;
}

function testEvidenceExists(storyId, testEvidence, implFiles) {
  const text = `${testEvidence || ''} ${implFiles || ''}`;
  const explicit = [];
  const re = /([A-Za-z0-9_./\\-]+Test(?:\.java)?)/g;
  let m;
  while ((m = re.exec(text))) explicit.push(m[1]);
  for (const name of explicit) {
    const base = name.endsWith('.java') ? name : `${name}.java`;
    try {
      const found = execSync(`git ls-files "*${base}"`, {
        cwd: ROOT,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
      })
        .trim()
        .split(/\r?\n/)
        .filter(Boolean);
      if (found.length) return { ok: true, evidence: found[0] };
    } catch {
      /* continue */
    }
  }
  for (const svc of serviceHint(implFiles)) {
    try {
      const found = execSync(`git ls-files "services/**/${svc}/src/test/**/*.java"`, {
        cwd: ROOT,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
      })
        .trim()
        .split(/\r?\n/)
        .filter(Boolean);
      if (found.length) return { ok: true, evidence: found[0] };
    } catch {
      /* continue */
    }
  }
  const mentions = fileMentions(storyId).filter((f) => /Test\.java$|spec\.ts$|\.test\./i.test(f));
  if (mentions.length) return { ok: true, evidence: mentions[0] };
  return { ok: false, evidence: '' };
}

const raw = fs.readFileSync(CSV_PATH, 'utf8');
const table = parseCsv(raw);
if (table.length < 2) {
  console.error('CSV empty or unreadable:', CSV_PATH);
  process.exit(1);
}
const header = table[0].map((h) => h.trim());
const idx = Object.fromEntries(header.map((h, i) => [h, i]));
const idIdx = idx['Story ID'] ?? 0;
const titleIdx = idx['Title'] ?? 1;
const statusIdx = idx['Status'] ?? header.findIndex((h) => /status/i.test(h));
const filesIdx = idx['Implementation Files'] ?? 4;
const testIdx = idx['Test Evidence'] ?? 5;

const rows = table.slice(1).map((cols) => ({
  id: (cols[idIdx] || '').trim(),
  title: (cols[titleIdx] || '').trim(),
  status: (cols[statusIdx] || '').trim().toUpperCase(),
  files: (cols[filesIdx] || '').trim(),
  tests: (cols[testIdx] || '').trim(),
}));

const doneRows = rows.filter((r) => DONE_STATUSES.has(r.status));
const byModule = {};
for (const r of rows) {
  const mod = normalizeModule(r.id);
  if (!byModule[mod]) {
    byModule[mod] = { total: 0, done: 0, withDemo: 0, withoutDemo: [] };
  }
  byModule[mod].total += 1;
}

const verified = [];
const notVerified = [];

for (const r of doneRows) {
  const mod = normalizeModule(r.id);
  byModule[mod].done += 1;
  const demo = hasDemo(r.id);
  if (demo) byModule[mod].withDemo += 1;
  else byModule[mod].withoutDemo.push(r.id);

  const testCheck = testEvidenceExists(r.id, r.tests, r.files);
  const commits = commitMentions(r.id);
  const files = fileMentions(r.id);
  const mentionOk = commits.length > 0 || files.length > 0;
  const entry = {
    id: r.id,
    title: r.title,
    status: r.status,
    demo,
    testOk: testCheck.ok,
    testEvidence: testCheck.evidence,
    mentionOk,
    commits: commits.slice(0, 3),
    files: files.slice(0, 5),
  };
  if (demo && testCheck.ok && mentionOk) verified.push(entry);
  else notVerified.push(entry);
}

const idMap = new Map();
for (const r of rows) {
  const key = r.id.replace(/^STM(\d)/i, 'STM-$1').toUpperCase();
  if (!idMap.has(key)) idMap.set(key, []);
  idMap.get(key).push(r);
}
const duplicates = [...idMap.entries()].filter(([, list]) => {
  const rawIds = new Set(list.map((x) => x.id));
  return rawIds.size > 1 || list.length > 1;
});

const titleWatch = ['STM-04-US-007', 'STM-04-US-008', 'STM-04-US-010', 'STM-04-US-011'];
const titleConflicts = titleWatch.map((id) => {
  const variants = rows.filter((r) => r.id.replace(/^STM(\d)/i, 'STM-$1') === id || r.id === id.replace('-', ''));
  return { id, rows: variants };
});

const generatedAt = new Date().toISOString();
const lines = [];
lines.push('# Traceability Verification Report');
lines.push('');
lines.push(`**Generated:** ${generatedAt}`);
lines.push(`**Source CSV:** \`docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv\``);
lines.push(`**Branch check command:** \`node scripts/dev/check_traceability.mjs\``);
lines.push(`**Done statuses counted:** ${[...DONE_STATUSES].join(', ')}`);
lines.push('');
lines.push('## Summary counts');
lines.push('');
lines.push(`- Total CSV rows (stories/enablers listed): **${rows.length}**`);
lines.push(`- Rows marked done: **${doneRows.length}**`);
lines.push(`- Done rows with demo pack (\`docs/demos/<ID>/DEMO.md\`): **${doneRows.filter((r) => hasDemo(r.id)).length}**`);
lines.push(`- Done rows without demo pack (**NOT VERIFIED** for demo gate): **${doneRows.filter((r) => !hasDemo(r.id)).length}**`);
lines.push(`- Done rows passing demo+test+mention checks: **${verified.length}**`);
lines.push(`- Done rows failing one or more checks (**NOT VERIFIED**): **${notVerified.length}**`);
lines.push('');
lines.push('## Counts per module');
lines.push('');
lines.push('| Module | Total rows | Marked done | With demo pack | Without demo pack |');
lines.push('| :--- | ---: | ---: | ---: | ---: |');
for (const mod of Object.keys(byModule).sort()) {
  const m = byModule[mod];
  lines.push(`| ${mod} | ${m.total} | ${m.done} | ${m.withDemo} | ${m.done - m.withDemo} |`);
}
lines.push('');
lines.push('## Duplicate or conflicting Story IDs');
lines.push('');
const conflictPairs = [
  ['STM04-US-012', 'STM-04-US-012'],
  ['STM04-US-014', 'STM-04-US-014'],
];
for (const [a, b] of conflictPairs) {
  const ra = rows.filter((r) => r.id === a);
  const rb = rows.filter((r) => r.id === b);
  lines.push(`### ${a} vs ${b}`);
  lines.push(`- \`${a}\` rows: ${ra.length} — ${ra.map((r) => `"${r.title}" [${r.status}]`).join('; ') || 'none'}`);
  lines.push(`- \`${b}\` rows: ${rb.length} — ${rb.map((r) => `"${r.title}" [${r.status}]`).join('; ') || 'none'}`);
  lines.push(`- **Correct ID form:** \`${b}\` (hyphenated module prefix matches Final User Stories / demo pack path \`docs/demos/${b}/\`).`);
  lines.push(`- \`${a}\` is a legacy/alternate spelling; treat as conflicting duplicate until CSV is cleaned in a later approved task.`);
  lines.push('');
}
if (duplicates.length) {
  lines.push('### Additional repeated IDs in CSV');
  for (const [key, list] of duplicates) {
    if (list.length <= 1) continue;
    const unique = [...new Set(list.map((x) => x.id))];
    if (unique.length === 1 && list.length > 1) {
      lines.push(`- \`${unique[0]}\` appears **${list.length}** times.`);
    }
  }
  lines.push('');
}

lines.push('## Title conflicts (STM-04 US-007 / 008 / 010 / 011)');
lines.push('');
lines.push('Story source of truth for titles: `D:/CampXSync/Documentation/StudentModule/UserStories/CampXSync_STM-03_to_STM-10_Final_User_Stories.csv`.');
lines.push('');
const sourceTitles = {
  'STM-04-US-007': 'Request return from leave or reinstatement',
  'STM-04-US-008': 'Reinstate eligible students',
  'STM-04-US-010': 'Process transfer requests',
  'STM-04-US-011': 'Submit and process a withdrawal',
};
for (const id of titleWatch) {
  const csvRows = rows.filter((r) => r.id === id);
  lines.push(`### ${id}`);
  lines.push(`- **Correct title (Final User Stories):** ${sourceTitles[id]}`);
  for (const r of csvRows) {
    const match = r.title === sourceTitles[id] ? 'MATCH' : 'CONFLICT';
    lines.push(`- CSV title: "${r.title}" [${r.status}] — **${match}**`);
  }
  if (!csvRows.length) lines.push('- No CSV row with this exact ID.');
  lines.push('');
}

lines.push('## Done rows without demo pack (NOT VERIFIED)');
lines.push('');
const noDemo = notVerified.filter((e) => !e.demo);
if (!noDemo.length) {
  lines.push('_None._');
} else {
  lines.push('| Story ID | Title | Status | Test evidence | Commit/file mention |');
  lines.push('| :--- | :--- | :---: | :---: | :---: |');
  for (const e of noDemo) {
    lines.push(`| ${e.id} | ${e.title.replace(/\|/g, '/')} | ${e.status} | ${e.testOk ? 'YES' : 'NO'} | ${e.mentionOk ? 'YES' : 'NO'} |`);
  }
}
lines.push('');
lines.push('## Done rows failing other checks (demo present but still NOT VERIFIED)');
lines.push('');
const otherFail = notVerified.filter((e) => e.demo);
if (!otherFail.length) {
  lines.push('_None._');
} else {
  for (const e of otherFail) {
    lines.push(`- **${e.id}**: demo=YES test=${e.testOk ? 'YES' : 'NO'} mention=${e.mentionOk ? 'YES' : 'NO'}`);
  }
}
lines.push('');
lines.push('## Demo packs present on disk');
lines.push('');
const demoDirs = fs.existsSync(DEMOS_DIR)
  ? fs.readdirSync(DEMOS_DIR).filter((d) => fs.existsSync(path.join(DEMOS_DIR, d, 'DEMO.md')))
  : [];
lines.push(`Found **${demoDirs.length}** demo packs with DEMO.md:`);
for (const d of demoDirs.sort()) lines.push(`- \`docs/demos/${d}/DEMO.md\``);
lines.push('');
lines.push('## Interpretation rule for downstream docs');
lines.push('');
lines.push('- Use **done-with-demo** counts when stating verified story delivery.');
lines.push('- Any row marked PASS/DONE without `docs/demos/<Story-ID>/DEMO.md` must be labeled **NOT VERIFIED**.');
lines.push('- Do not treat legacy `STM04-US-*` IDs as the same verified row as hyphenated `STM-04-US-*` without explicit reconciliation.');
lines.push('');

fs.writeFileSync(OUT_PATH, lines.join('\n'), 'utf8');
console.log(`Wrote ${OUT_PATH}`);
console.log(`done=${doneRows.length} withDemo=${doneRows.filter((r) => hasDemo(r.id)).length} notVerified=${notVerified.length}`);
