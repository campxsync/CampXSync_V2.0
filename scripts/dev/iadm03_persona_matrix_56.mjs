/**
 * IADM-03 56-cell persona × permission matrix (close-out US-040).
 * Keys (7) × Personas (8) = 56 — mirrors AccessPermissionMatrix for selected iam.* keys.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const evidence = path.join(root, 'docs/iadm/iadm-03/evidence/closeout');
fs.mkdirSync(evidence, { recursive: true });

const KEYS = [
  'iam.catalog.roles.view',
  'iam.profile.view',
  'iam.grant.assign',
  'iam.request.submit',
  'iam.delegation.create',
  'iam.catalog.change.propose',
  'iam.review.manage',
];
const PERSONAS = [
  'R01',
  'SUPER_ADMIN',
  'R02',
  'SYSTEM_ADMIN',
  'R03',
  'INSTITUTION_ADMIN',
  'R04',
  'R28',
];

function normalize(persona) {
  const p = persona.toUpperCase();
  if (p === 'SUPER_ADMIN' || p === 'PLATFORM_ADMIN' || p === 'R01') return 'R01';
  if (p === 'SYSTEM_ADMIN' || p === 'R02') return 'R02';
  if (p === 'INSTITUTION_ADMIN' || p === 'R03') return 'R03';
  if (p === 'COLLEGE_ADMIN' || p === 'R04' || p === 'ADMIN') return 'R04';
  if (p === 'AUDITOR' || p === 'R28') return 'R28';
  if (p === 'API_CLIENT' || p === 'R27') return 'R27';
  return p;
}

const BY_ROLE = {
  R01: new Set(KEYS),
  R02: new Set(['iam.catalog.roles.view', 'iam.profile.view']),
  R03: new Set([
    'iam.catalog.roles.view',
    'iam.profile.view',
    'iam.grant.assign',
    'iam.request.submit',
    'iam.delegation.create',
    'iam.review.manage',
  ]),
  R04: new Set(['iam.request.submit']),
  R28: new Set(['iam.catalog.roles.view', 'iam.profile.view']),
  R27: new Set(),
};

function allows(persona, key) {
  const role = normalize(persona);
  const set = BY_ROLE[role];
  return set ? set.has(key) : false;
}

const cells = [];
for (const key of KEYS) {
  for (const persona of PERSONAS) {
    cells.push({ key, persona, allowed: allows(persona, key) ? 1 : 0 });
  }
}

const out = {
  title: 'IADM-03 56-cell persona matrix (iam.* sample)',
  cellCount: cells.length,
  keys: KEYS.length,
  personas: PERSONAS.length,
  cells,
  summary: {
    allows: cells.filter((c) => c.allowed).length,
    denies: cells.filter((c) => !c.allowed).length,
  },
  note: 'Sample of owner-approved iam.* keys; full AccessPermissionMatrix covered by Java unit tests. R02 lacks grant/review/catalog-change. R04 request-only. R28 read-only.',
};
fs.writeFileSync(path.join(evidence, 'PERSONA_MATRIX_56.json'), JSON.stringify(out, null, 2));

const header = `| Key | ${PERSONAS.join(' | ')} |`;
const sep = `|---|${PERSONAS.map(() => '---').join('|')}|`;
const rows = KEYS.map((key) => {
  const cols = PERSONAS.map((p) => (allows(p, key) ? 'Y' : 'N'));
  return `| ${key} | ${cols.join(' | ')} |`;
});
const md = [
  '# IADM-03 56-cell persona matrix (iam.* sample)',
  '',
  header,
  sep,
  ...rows,
  '',
  `Total cells: ${cells.length} (allows ${out.summary.allows}, denies ${out.summary.denies}).`,
  '',
].join('\n');
fs.writeFileSync(path.join(evidence, 'PERSONA_MATRIX_56.md'), md);
console.log(JSON.stringify({ cellCount: cells.length, ...out.summary }, null, 2));
