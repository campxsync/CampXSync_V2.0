/**
 * Frontend 1c — Registrar (R31) role-policy unit checks (no DB writes).
 * Run: node --experimental-strip-types scripts/verify_registrar_role_policy.mjs
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import {
  REGISTRAR_HOME,
  canAccessCollegeAdminOnlyBff,
  canAccessCollegeOperationalBff,
  canRegistrarAccessCollegePath,
  defaultDashboardForRole,
  gatewayActorRoleHint,
  isRegistrarJwtClaim,
  isReturnToAuthorizedForRole,
} from '../frontend/src/lib/auth/role-policy.ts';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const navSrc = fs.readFileSync(
  path.join(__dirname, '../frontend/src/lib/nav-config.ts'),
  'utf8'
);

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

const checks = [];
function check(name, fn) {
  try {
    fn();
    checks.push({ name, ok: true });
    console.log(`[PASS] ${name}`);
  } catch (err) {
    checks.push({ name, ok: false, detail: err.message });
    console.error(`[FAIL] ${name}: ${err.message}`);
  }
}

check('JWT claim REGISTRAR/R31 recognized', () => {
  assert(isRegistrarJwtClaim('REGISTRAR'), 'REGISTRAR');
  assert(isRegistrarJwtClaim('R31'), 'R31');
  assert(!isRegistrarJwtClaim('COLLEGE_ADMIN'), 'admin');
  assert(!isRegistrarJwtClaim('ACADEMIC_ADMIN'), 'academic');
});

check('registrar home is lifecycle desk', () => {
  assert(REGISTRAR_HOME === '/college/lifecycle', REGISTRAR_HOME);
  assert(defaultDashboardForRole('registrar') === '/college/lifecycle', 'dashboard');
});

check('registrar college path allowlist', () => {
  assert(canRegistrarAccessCollegePath('/college/lifecycle'), 'lifecycle');
  assert(canRegistrarAccessCollegePath('/college/documents'), 'documents');
  assert(canRegistrarAccessCollegePath('/college/enrollment'), 'enrollment');
  assert(canRegistrarAccessCollegePath('/college/guardians'), 'guardians');
  assert(!canRegistrarAccessCollegePath('/college/departments'), 'departments denied');
  assert(!canRegistrarAccessCollegePath('/college/admissions'), 'admissions denied');
  assert(!canRegistrarAccessCollegePath('/college/attendance'), 'attendance denied');
  assert(!canRegistrarAccessCollegePath('/college/audit'), 'audit denied');
});

check('operational BFF allows registrar; admin-only does not', () => {
  assert(canAccessCollegeOperationalBff('registrar'), 'ops');
  assert(canAccessCollegeOperationalBff('college_admin'), 'admin ops');
  assert(!canAccessCollegeAdminOnlyBff('registrar'), 'admin-only deny');
  assert(canAccessCollegeAdminOnlyBff('college_admin'), 'admin-only allow');
});

check('gateway hint never maps registrar to COLLEGE_ADMIN', () => {
  assert(gatewayActorRoleHint('registrar') === 'REGISTRAR', 'hint');
  assert(gatewayActorRoleHint('college_admin') === 'COLLEGE_ADMIN', 'admin hint');
});

check('returnTo authorization respects registrar allowlist', () => {
  assert(isReturnToAuthorizedForRole('registrar', '/college/lifecycle'), 'ok');
  assert(!isReturnToAuthorizedForRole('registrar', '/college/departments'), 'deny');
  assert(isReturnToAuthorizedForRole('college_admin', '/college/departments'), 'admin ok');
  assert(isReturnToAuthorizedForRole('student', '/student/documents'), 'student unchanged');
  assert(isReturnToAuthorizedForRole('faculty', '/faculty'), 'faculty unchanged');
});

check('navConfigs.registrar is STM subset without admin inheritance', () => {
  assert(navSrc.includes('registrar: ['), 'registrar nav key');
  assert(navSrc.includes("href: '/college/lifecycle'"), 'lifecycle');
  assert(navSrc.includes("href: '/college/documents'"), 'documents');
  assert(navSrc.includes("href: '/college/enrollment'"), 'enrollment');
  assert(navSrc.includes("href: '/college/guardians'"), 'guardians');
  // Registrar block must not list Academic Admin surfaces
  const registrarBlock = navSrc.slice(navSrc.indexOf('registrar: ['), navSrc.indexOf('faculty: ['));
  assert(!registrarBlock.includes('/departments'), 'no departments');
  assert(!registrarBlock.includes('/admissions'), 'no admissions');
  assert(!registrarBlock.includes('/attendance'), 'no attendance');
  assert(navSrc.includes('college_admin: ['), 'college_admin preserved');
  assert(navSrc.includes('student: ['), 'student preserved');
  assert(navSrc.includes('faculty: ['), 'faculty preserved');
});

const failed = checks.filter((c) => !c.ok);
console.log(`\nRegistrar role policy: ${checks.length - failed.length}/${checks.length} PASS`);
if (failed.length) process.exit(1);
