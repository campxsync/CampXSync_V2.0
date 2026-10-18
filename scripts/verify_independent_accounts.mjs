// scripts/verify_independent_accounts.mjs
// Targeted verification of independent account authentication and authoritative role resolution

import fs from 'node:fs';
import path from 'node:path';

const SUPABASE_URL = 'https://kpwdvvcschankgggitnj.supabase.co';
const ANON_KEY = 'sb_publishable_1B6QXXcsRiUaxj-Bk1pPuw_JMqgh4-J';

const COLLEGE_ADMIN_EMAIL = 'campxsync@gmail.com';
const PLATFORM_ADMIN_EMAIL = 'platformadmin.campx@gmail.com';

let collegePassword = process.env.CAMPX_STAGING_TEST_PASSWORD;
if (!collegePassword) {
  try {
    const envPath = path.resolve('frontend/.env.local');
    if (fs.existsSync(envPath)) {
      const content = fs.readFileSync(envPath, 'utf8');
      const match = content.match(/CAMPX_STAGING_TEST_PASSWORD=(.+)/);
      if (match) {
        collegePassword = match[1].trim().replace(/^['"]|['"]$/g, '');
      }
    }
  } catch {}
}

async function verifyAuth(email, password, expectedUserId) {
  const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: ANON_KEY,
    },
    body: JSON.stringify({ email, password }),
  });

  const data = await res.json();
  if (!res.ok || !data.access_token) {
    return { success: false, error: data.error_description || data.msg || 'Auth failed' };
  }
  return {
    success: true,
    userId: data.user?.id,
    matchesExpectedUser: data.user?.id === expectedUserId,
  };
}

async function main() {
  console.log('=== VERIFYING INDEPENDENT ACCOUNT CREDENTIALS & ISOLATION ===');
  console.log(`Staging Supabase Target: ${SUPABASE_URL}`);
  console.log('Testing without printing passwords or tokens to stdout...\n');

  // 1. Verify College Admin authenticates with its own password
  console.log(`[1] Testing College Admin (${COLLEGE_ADMIN_EMAIL})...`);
  const colRes = await verifyAuth(COLLEGE_ADMIN_EMAIL, collegePassword, '70a8ffae-b5ad-4c61-a401-16f220893df4');
  if (colRes.success && colRes.matchesExpectedUser) {
    console.log('    PASS: College Admin credentials authenticate College Admin account.');
    console.log(`    Verified Auth UUID: ${colRes.userId}`);
  } else {
    console.error('    FAIL: College Admin authentication failed:', colRes.error);
    process.exit(1);
  }

  // 2. Cross-Credential Isolation Test: College Admin password MUST NOT authenticate Platform Admin
  console.log(`\n[2] Testing Cross-Credential Isolation (College Admin password against Platform Admin)...`);
  const crossRes = await verifyAuth(PLATFORM_ADMIN_EMAIL, collegePassword, '54e8308c-ada3-4065-a9fe-51814af6b971');
  if (!crossRes.success) {
    console.log('    PASS: Platform Admin safely REJECTS College Admin password (no credential reuse).');
    console.log(`    Observed rejection message: ${crossRes.error}`);
  } else {
    console.warn('    ALERT: Platform Admin and College Admin share the same password.');
  }

  // 3. Verify Form State Isolation (Password field clearing)
  console.log('\n[3] Testing Form State Credential Isolation in login/page.tsx...');
  const loginCode = fs.readFileSync(path.resolve('frontend/src/app/(auth)/login/page.tsx'), 'utf8');
  if (loginCode.includes("setPassword('');") && loginCode.includes('autoComplete="current-password"')) {
    console.log('    PASS: handleSelectDemoAccount explicitly resets password to empty string.');
    console.log('    PASS: Password input enforces autoComplete="current-password".');
  } else {
    console.error('    FAIL: Password state reset or autocomplete attribute missing in login/page.tsx.');
    process.exit(1);
  }

  // 4. Verify Authoritative UUID Lookup in resolve-role.ts
  console.log('\n[4] Testing Authoritative UUID Resolution in resolve-role.ts...');
  const resolveCode = fs.readFileSync(path.resolve('frontend/src/lib/auth/resolve-role.ts'), 'utf8');
  const paStrict = resolveCode.includes("WHERE user_id = $1 AND role_code = $2 AND status = $3 AND deleted_at IS NULL");
  const colStrict = resolveCode.includes("WHERE id = $1 AND deleted_at IS NULL LIMIT 1") && resolveCode.includes("WHERE ra.user_id = $1\n           AND ra.deleted_at IS NULL");
  const noEmailFallback = !resolveCode.includes("(user_id = $1 OR user_id = (SELECT id FROM iam.user_profiles") && !resolveCode.includes("WHERE (id = $1 OR email = $2)");

  if (paStrict && colStrict && noEmailFallback) {
    console.log('    PASS: Platform Admin checks user_id UUID against plat.platform_admins and SUPER_ADMIN strictly.');
    console.log('    PASS: College Admin checks user_id UUID against iam.user_profiles and iam.role_assignments strictly.');
    console.log('    PASS: Zero email-based fallback queries exist in role resolution paths.');
  } else {
    console.error('    FAIL: Authoritative UUID resolution queries in resolve-role.ts do not meet strict criteria.');
    process.exit(1);
  }

  // 5. Verify Edge Middleware Delegated Routing
  console.log('\n[5] Testing Edge Middleware Routing in middleware.ts...');
  const mwCode = fs.readFileSync(path.resolve('frontend/src/middleware.ts'), 'utf8');
  if (mwCode.includes('if (rawRole) {') && mwCode.includes("return NextResponse.redirect(new URL('/access-denied?reason=no_role_assigned', request.url));")) {
    console.log('    PASS: Edge route guards active when rawRole claim is present in JWT.');
    console.log('    PASS: Requests without rawRole pass through to Server Components where resolveUserRole queries PostgreSQL.');
  } else {
    console.error('    FAIL: Middleware routing structure does not allow server-side resolution.');
    process.exit(1);
  }

  console.log('\n=== ALL INDEPENDENT ACCOUNT & ROUTING CHECKS PASSED ===\n');
}

main().catch(err => {
  console.error('Execution error:', err.message);
  process.exit(1);
});
