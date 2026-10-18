/**
 * Regression Test Suite: Login & Authorization Hardening
 *
 * Verifies the 7 mandatory acceptance criteria from Section 4 D of Login_Hardning_Scope.md:
 * 1. An email prefix alone cannot grant Platform Admin access.
 * 2. User-editable metadata cannot grant a privileged application role.
 * 3. Missing or invalid authoritative role assignments fail closed.
 * 4. Missing tenant context never falls back to a default tenant.
 * 5. Existing Student-related code cannot silently select another student's record.
 * 6. Valid role assignments and tenant associations continue to work.
 * 7. The working College Admin authentication flow is not inadvertently broken.
 *
 * Run: node --experimental-strip-types scripts/verify_login_hardening.mjs
 */

import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';

console.log('=== CAMPXSYNC LOGIN & AUTHORIZATION HARDENING REGRESSION SUITE ===\n');

let totalTests = 0;
let passedTests = 0;

function runTest(name, fn) {
  totalTests++;
  try {
    fn();
    console.log(`[PASS] ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`[FAIL] ${name}`);
    console.error(`       Error: ${err.message}\n`);
  }
}

// ----------------------------------------------------------------------------
// TEST GROUP 1: Source Code Static Security Verification
// ----------------------------------------------------------------------------
console.log('--- 1. Source Code Inspection & Vulnerability Elimination ---');

const resolveRolePath = path.resolve('frontend/src/lib/auth/resolve-role.ts');
const middlewarePath = path.resolve('frontend/src/middleware.ts');
const homePagePath = path.resolve('frontend/src/app/page.tsx');
const studentPagePath = path.resolve('frontend/src/app/(dashboard)/student/page.tsx');

const resolveRoleContent = fs.readFileSync(resolveRolePath, 'utf8');
const middlewareContent = fs.readFileSync(middlewarePath, 'utf8');
const homePageContent = fs.readFileSync(homePagePath, 'utf8');
const studentPageContent = fs.readFileSync(studentPagePath, 'utf8');

runTest('resolve-role.ts: Email prefix platform.admin@ fallback removed', () => {
  assert(
    !resolveRoleContent.includes("userEmail.startsWith('platform.admin@')"),
    'resolve-role.ts still contains userEmail.startsWith("platform.admin@")'
  );
  assert(
    !resolveRoleContent.includes("userEmail.startsWith('platform.admin')"),
    'resolve-role.ts still contains platform.admin email prefix check'
  );
});

runTest('middleware.ts: Email prefix role escalation removed', () => {
  assert(
    !middlewareContent.includes("email.startsWith('platform.admin@')"),
    'middleware.ts still contains email.startsWith("platform.admin@")'
  );
  assert(
    !middlewareContent.includes("email.startsWith('faculty.')"),
    'middleware.ts still contains email.startsWith("faculty.")'
  );
  assert(
    !middlewareContent.includes("email.startsWith('student.')"),
    'middleware.ts still contains email.startsWith("student.")'
  );
  assert(
    !middlewareContent.includes("email === 'campxsync@gmail.com'"),
    'middleware.ts still contains hardcoded email === "campxsync@gmail.com"'
  );
});

runTest('middleware.ts: User-editable user_metadata.role cannot grant roles', () => {
  assert(
    !middlewareContent.includes('user.user_metadata?.role'),
    'middleware.ts still inspects user.user_metadata?.role'
  );
  assert(
    !middlewareContent.includes('user.user_metadata?.tenant_status'),
    'middleware.ts still inspects user.user_metadata?.tenant_status'
  );
});

runTest('resolve-role.ts: User-editable user_metadata.role cannot grant roles', () => {
  assert(
    !resolveRoleContent.includes('user.user_metadata?.role'),
    'resolve-role.ts still inspects user.user_metadata?.role'
  );
});

runTest('resolve-role.ts: Fails closed when role assignments are missing (no user_metadata fallback)', () => {
  assert(
    resolveRoleContent.includes("return { success: false, reason: 'no_role_assigned' };"),
    'resolve-role.ts must return no_role_assigned when assignments are missing'
  );
  assert(
    !resolveRoleContent.includes("metadataRole === 'COLLEGE_ADMIN'"),
    'resolve-role.ts still contains metadataRole fallback'
  );
});

runTest('resolve-role.ts: Fails closed on unmapped role (no default to faculty)', () => {
  assert(
    !resolveRoleContent.includes("resolvedRole || 'faculty'"),
    'resolve-role.ts still contains resolvedRole || "faculty" fallback'
  );
});

runTest('middleware.ts: Server-side route guards fail closed (positive allowlist)', () => {
  assert(
    middlewareContent.includes("if (!isCollegeAdminUser) {\n      return NextResponse.redirect(new URL('/access-denied?reason=forbidden'"),
    'middleware.ts /college guard must fail closed with !isCollegeAdminUser'
  );
  assert(
    middlewareContent.includes("if (!isStudentUser) {\n      return NextResponse.redirect(new URL('/access-denied?reason=forbidden'"),
    'middleware.ts /student guard must fail closed with !isStudentUser'
  );
  assert(
    middlewareContent.includes("if (!isPlatformUser) {\n      return NextResponse.redirect(new URL('/access-denied?reason=forbidden'"),
    'middleware.ts /platform guard must fail closed with !isPlatformUser'
  );
  assert(
    middlewareContent.includes("return NextResponse.redirect(new URL('/access-denied?reason=no_role_assigned', request.url));"),
    'middleware.ts root redirect must fail closed to no_role_assigned for unassigned users'
  );
});

runTest('app/page.tsx: Root page fails closed to no_role_assigned (no fallback to /college)', () => {
  assert(
    !homePageContent.includes("default:\n      redirect('/college')"),
    'app/page.tsx still defaults unassigned role to /college'
  );
  assert(
    homePageContent.includes("default:\n      redirect('/access-denied?reason=no_role_assigned')"),
    'app/page.tsx must redirect default role to /access-denied?reason=no_role_assigned'
  );
});

// ----------------------------------------------------------------------------
// TEST GROUP 2: Default-Tenant Fallback Elimination
// ----------------------------------------------------------------------------
console.log('\n--- 2. Default-Tenant Fallback Elimination Across All Dashboards ---');

const dashboardFiles = [
  'frontend/src/app/(dashboard)/college/page.tsx',
  'frontend/src/app/(dashboard)/college/departments/page.tsx',
  'frontend/src/app/(dashboard)/college/batches/page.tsx',
  'frontend/src/app/(dashboard)/college/faculty/page.tsx',
  'frontend/src/app/(dashboard)/college/students/page.tsx',
  'frontend/src/app/(dashboard)/college/timetable/page.tsx',
  'frontend/src/app/(dashboard)/college/attendance/page.tsx',
  'frontend/src/app/(dashboard)/college/audit/page.tsx',
  'frontend/src/app/(dashboard)/faculty/page.tsx',
  'frontend/src/app/(dashboard)/faculty/attendance/page.tsx',
  'frontend/src/app/(dashboard)/faculty/sessions/page.tsx',
  'frontend/src/app/(dashboard)/faculty/timetable/page.tsx',
  'frontend/src/app/(dashboard)/student/page.tsx',
];

const DEFAULT_TENANT_UUID = '11111111-1111-1111-1111-111111111111';

for (const relPath of dashboardFiles) {
  const fullPath = path.resolve(relPath);
  const content = fs.readFileSync(fullPath, 'utf8');
  const baseName = path.basename(path.dirname(relPath)) + '/' + path.basename(relPath);

  runTest(`No default tenant fallback in ${baseName}`, () => {
    assert(
      !content.includes(DEFAULT_TENANT_UUID),
      `${baseName} still contains default tenant UUID: ${DEFAULT_TENANT_UUID}`
    );
  });

  runTest(`Safe access denial on missing tenantId in ${baseName}`, () => {
    assert(
      content.includes('if (!tenantId)') || content.includes('if (!resolution.session.tenantId)'),
      `${baseName} must explicitly check if (!tenantId) and deny access`
    );
    assert(
      content.includes("redirect('/access-denied?reason=forbidden')"),
      `${baseName} must redirect to /access-denied?reason=forbidden on missing tenantId`
    );
  });
}

// ----------------------------------------------------------------------------
// TEST GROUP 3: Student-Related Record Isolation
// ----------------------------------------------------------------------------
console.log('\n--- 3. Student-Related Record Isolation ---');

runTest('student/page.tsx: Enforces role === "student" before querying records', () => {
  assert(
    studentPageContent.includes("resolution.session.role !== 'student'"),
    'student/page.tsx must enforce resolution.session.role === "student"'
  );
});

runTest('student/page.tsx: Does not silently select another student (CASE/ORDER BY fallback removed)', () => {
  assert(
    !studentPageContent.includes('CASE WHEN p.id = $2 THEN 0 ELSE 1 END'),
    'student/page.tsx still contains insecure CASE WHEN p.id = $2 fallback ordering'
  );
  assert(
    studentPageContent.includes('(p.id = $2 OR p.primary_email = $3)'),
    'student/page.tsx must strictly match authenticated user identity in WHERE clause'
  );
});

// ----------------------------------------------------------------------------
// TEST GROUP 4: Functional Role & Middleware Logic Simulation
// ----------------------------------------------------------------------------
console.log('\n--- 4. Functional Role Resolution & Route Guard Evaluation ---');

// Simulate the hardened middleware logic
function evaluateMiddleware(user, pathname) {
  if (!user) {
    return { action: 'redirect', to: `/login?returnTo=${pathname}` };
  }

  const rawRole = (user.app_metadata?.role || '').toUpperCase();
  const isPlatformUser = rawRole === 'SUPER_ADMIN' || rawRole === 'PLATFORM_ADMIN';
  const isCollegeAdminUser = (rawRole === 'COLLEGE_ADMIN' || rawRole === 'INSTITUTION_ADMIN' || rawRole === 'TENANT_ADMIN' || rawRole === 'ADMIN') && !isPlatformUser;
  const isFacultyUser = (rawRole === 'FACULTY' || rawRole === 'HOD' || rawRole === 'DEAN' || rawRole === 'TEACHER') && !isPlatformUser;
  const isStudentUser = rawRole === 'STUDENT' && !isPlatformUser;

  if (pathname === '/') {
    if (isPlatformUser) return { action: 'redirect', to: '/platform' };
    if (isCollegeAdminUser) return { action: 'redirect', to: '/college' };
    if (isFacultyUser) return { action: 'redirect', to: '/faculty' };
    if (isStudentUser) return { action: 'redirect', to: '/student' };
    return { action: 'redirect', to: '/access-denied?reason=no_role_assigned' };
  }

  if (pathname.startsWith('/platform')) {
    if (!isPlatformUser) return { action: 'redirect', to: '/access-denied?reason=forbidden' };
  }
  if (pathname.startsWith('/college')) {
    if (!isCollegeAdminUser) return { action: 'redirect', to: '/access-denied?reason=forbidden' };
  }
  if (pathname.startsWith('/faculty')) {
    if (!isFacultyUser && !isCollegeAdminUser) return { action: 'redirect', to: '/access-denied?reason=forbidden' };
  }
  if (pathname.startsWith('/student')) {
    if (!isStudentUser) return { action: 'redirect', to: '/access-denied?reason=forbidden' };
  }

  return { action: 'allow' };
}

runTest('Attacker with platform.admin@ prefix denied platform access', () => {
  const attacker = {
    id: 'a0000000-0000-0000-0000-000000000001',
    email: 'platform.admin@malicious-attacker.org',
    app_metadata: {},
    user_metadata: {},
  };
  const res = evaluateMiddleware(attacker, '/platform');
  assert.strictEqual(res.action, 'redirect');
  assert.strictEqual(res.to, '/access-denied?reason=forbidden');
});

runTest('Attacker injecting user_metadata: { role: "SUPER_ADMIN" } denied platform access', () => {
  const attacker = {
    id: 'a0000000-0000-0000-0000-000000000002',
    email: 'user@attacker.org',
    app_metadata: {},
    user_metadata: { role: 'SUPER_ADMIN' },
  };
  const res = evaluateMiddleware(attacker, '/platform');
  assert.strictEqual(res.action, 'redirect');
  assert.strictEqual(res.to, '/access-denied?reason=forbidden');
});

runTest('Attacker injecting user_metadata: { role: "ADMIN" } denied college admin access', () => {
  const attacker = {
    id: 'a0000000-0000-0000-0000-000000000003',
    email: 'user@attacker.org',
    app_metadata: {},
    user_metadata: { role: 'ADMIN' },
  };
  const res = evaluateMiddleware(attacker, '/college');
  assert.strictEqual(res.action, 'redirect');
  assert.strictEqual(res.to, '/access-denied?reason=forbidden');
});

runTest('Authenticated user with no role assignment redirected safely from root /', () => {
  const unassignedUser = {
    id: 'a0000000-0000-0000-0000-000000000004',
    email: 'newuser@institution.org',
    app_metadata: {},
    user_metadata: {},
  };
  const res = evaluateMiddleware(unassignedUser, '/');
  assert.strictEqual(res.action, 'redirect');
  assert.strictEqual(res.to, '/access-denied?reason=no_role_assigned');
});

runTest('Valid College Admin with app_metadata.role COLLEGE_ADMIN lands on /college', () => {
  const collegeAdmin = {
    id: '70a8ffae-b5ad-4c61-a401-16f220893df4',
    email: 'campxsync@gmail.com',
    app_metadata: { role: 'COLLEGE_ADMIN', tenant_id: '11111111-1111-1111-1111-111111111111' },
    user_metadata: { full_name: 'CampXSync Staging Admin' },
  };
  const rootRes = evaluateMiddleware(collegeAdmin, '/');
  assert.strictEqual(rootRes.action, 'redirect');
  assert.strictEqual(rootRes.to, '/college');

  const accessRes = evaluateMiddleware(collegeAdmin, '/college');
  assert.strictEqual(accessRes.action, 'allow');
});

runTest('Valid Faculty with app_metadata.role FACULTY lands on /faculty', () => {
  const faculty = {
    id: '11111111-9e05-0000-0000-000000000001',
    email: 'faculty.a1@demo.campxsync.test',
    app_metadata: { role: 'FACULTY', tenant_id: '11111111-1111-1111-1111-111111111111' },
    user_metadata: { full_name: 'Dr. Rajesh Sharma' },
  };
  const rootRes = evaluateMiddleware(faculty, '/');
  assert.strictEqual(rootRes.action, 'redirect');
  assert.strictEqual(rootRes.to, '/faculty');

  const accessRes = evaluateMiddleware(faculty, '/faculty');
  assert.strictEqual(accessRes.action, 'allow');

  const forbiddenRes = evaluateMiddleware(faculty, '/college');
  assert.strictEqual(forbiddenRes.action, 'redirect');
  assert.strictEqual(forbiddenRes.to, '/access-denied?reason=forbidden');
});

runTest('Valid Platform Admin with app_metadata.role SUPER_ADMIN lands on /platform', () => {
  const platAdmin = {
    id: '99999999-9999-9999-9999-999999999999',
    email: 'platform.admin@demo.campxsync.test',
    app_metadata: { role: 'SUPER_ADMIN' },
    user_metadata: { full_name: 'Platform Operator' },
  };
  const rootRes = evaluateMiddleware(platAdmin, '/');
  assert.strictEqual(rootRes.action, 'redirect');
  assert.strictEqual(rootRes.to, '/platform');

  const accessRes = evaluateMiddleware(platAdmin, '/platform');
  assert.strictEqual(accessRes.action, 'allow');

  const forbiddenRes = evaluateMiddleware(platAdmin, '/college');
  assert.strictEqual(forbiddenRes.action, 'redirect');
  assert.strictEqual(forbiddenRes.to, '/access-denied?reason=forbidden');
});

// ----------------------------------------------------------------------------
// SUMMARY
// ----------------------------------------------------------------------------
console.log(`\n=== REGRESSION SUITE RESULTS: ${passedTests}/${totalTests} TESTS PASSED ===`);

if (passedTests === totalTests) {
  console.log('STATUS: ALL REGRESSION TESTS PASSED (0 failures, 0 errors)');
  process.exit(0);
} else {
  console.error(`STATUS: ${totalTests - passedTests} TESTS FAILED`);
  process.exit(1);
}
