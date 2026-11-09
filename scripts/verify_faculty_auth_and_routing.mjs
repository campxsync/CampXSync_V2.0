// scripts/verify_faculty_auth_and_routing.mjs
// Focused Faculty login, authoritative role resolution, and route protection verification against staging

import fs from 'node:fs';
import path from 'node:path';
import pg from '../frontend/node_modules/pg/lib/index.js';
import ssr from '../frontend/node_modules/@supabase/ssr/dist/main/index.js';

const { Pool } = pg;
const { createChunks } = ssr;

const SUPABASE_URL = 'https://kpwdvvcschankgggitnj.supabase.co';
const ANON_KEY = 'sb_publishable_1B6QXXcsRiUaxj-Bk1pPuw_JMqgh4-J';
const FACULTY_EMAIL = 'faculty.campx@gmail.com';
function getEnvVar(varName) {
  let val = process.env[varName];
  if (!val) {
    try {
      const envPath = path.resolve('frontend/.env.local');
      if (fs.existsSync(envPath)) {
        const content = fs.readFileSync(envPath, 'utf8');
        const match = content.match(new RegExp(`^${varName}=(.+)`, 'm'));
        if (match) {
          val = match[1].trim().replace(/^['"]|['"]$/g, '');
        }
      }
    } catch {}
  }
  return val;
}

const EXPECTED_FACULTY_UUID = '619b460b-d7f9-46b6-b11a-56045a130a52';
const EXPECTED_TENANT_ID = '11111111-1111-1111-1111-111111111111';
const DEPLOYED_URL = process.env.DEPLOYED_URL || 'https://frontend-g1m0kswon-camp-xs-ync.vercel.app';
const VERCEL_BYPASS_TOKEN = process.env.VERCEL_AUTOMATION_BYPASS_SECRET || '';

const facultyPassword = getEnvVar('CAMPX_STAGING_FACULTY_TEST_PASSWORD');

async function main() {
  console.log('=== FOCUSED FACULTY LOGIN & AUTHORIZATION VERIFICATION ===\n');

  if (!facultyPassword) {
    console.error('FAIL: Missing CAMPX_STAGING_FACULTY_TEST_PASSWORD in environment or frontend/.env.local');
    process.exit(1);
  }

  // 1. Authenticate Faculty via Supabase Auth
  console.log(`[1] Authenticating ${FACULTY_EMAIL} with Supabase Auth...`);
  const authRes = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      apikey: ANON_KEY,
    },
    body: JSON.stringify({
      email: FACULTY_EMAIL,
      password: facultyPassword,
    }),
  });

  const authData = await authRes.json();
  if (!authRes.ok || !authData.access_token) {
    console.error('FAIL: Supabase Auth failed for Faculty account:', authData.error_description || authData.msg);
    process.exit(1);
  }

  const userId = authData.user?.id;
  console.log('    PASS: Supabase Auth succeeded.');
  console.log(`    Authenticated User UUID: ${userId}`);

  if (userId !== EXPECTED_FACULTY_UUID) {
    console.error(`FAIL: Authenticated UUID ${userId} did not match expected UUID ${EXPECTED_FACULTY_UUID}`);
    process.exit(1);
  }
  console.log('    PASS: User UUID matches expected staging Faculty UUID.');

  // 2. Authoritative Database Profile & Role Resolution Verification
  console.log('\n[2] Authoritative Database Profile & Role Resolution Verification...');
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || '';
  const username = process.env.SUPABASE_USER;
  const password = process.env.SUPABASE_PASSWORD;

  let connString = jdbcUrl.replace(/^jdbc:/, '');
  const urlObj = new URL(connString);
  if (username) urlObj.username = username;
  if (password) urlObj.password = password;
  urlObj.searchParams.delete('sslmode');

  const pool = new Pool({
    connectionString: urlObj.toString(),
    ssl: { rejectUnauthorized: false },
    max: 1,
    idleTimeoutMillis: 3000,
  });

  try {
    // Check Profile
    const profRes = await pool.query(
      'SELECT id, tenant_id, full_name, email, status FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL',
      [userId]
    );
    if (profRes.rows.length === 0 || profRes.rows[0].status !== 'ACTIVE') {
      console.error('FAIL: Active profile not found in iam.user_profiles for user', userId);
      process.exit(1);
    }
    const profile = profRes.rows[0];
    console.log(`    PASS: Found active profile for tenant: ${profile.tenant_id} (${profile.full_name})`);

    if (profile.tenant_id !== EXPECTED_TENANT_ID) {
      console.error(`FAIL: Tenant ID ${profile.tenant_id} did not match expected ${EXPECTED_TENANT_ID}`);
      process.exit(1);
    }
    console.log('    PASS: Tenant association matches expected tenant.');

    // Check Role Assignment
    const roleRes = await pool.query(
      `SELECT ra.id, ra.tenant_id, ra.college_id, r.code AS role_code, r.name AS role_name
       FROM iam.role_assignments ra
       JOIN iam.roles r ON ra.role_id = r.id
       WHERE ra.user_id = $1 AND ra.deleted_at IS NULL AND r.deleted_at IS NULL`,
      [userId]
    );
    if (roleRes.rows.length === 0) {
      console.error('FAIL: No active role assignments found for user', userId);
      process.exit(1);
    }
    const roleAssignment = roleRes.rows[0];
    console.log(`    PASS: Found role assignment: ${roleAssignment.role_code} (${roleAssignment.role_name})`);

    if (roleAssignment.role_code !== 'FACULTY') {
      console.error(`FAIL: Role code ${roleAssignment.role_code} is not FACULTY!`);
      process.exit(1);
    }
    console.log('    PASS: Authoritative role resolution resolves to FACULTY.');
  } finally {
    await pool.end();
  }

  // 3. SSR authentication and route testing against deployed Preview
  console.log(`\n[3] Testing HTTP Route Guards on Deployed Environment (${DEPLOYED_URL})...`);
  
  // Obtain Vercel bypass JWT cookie
  console.log('    Acquiring Vercel deployment protection JWT cookie...');
  const bypassRes = await fetch(`${DEPLOYED_URL}/login`, {
    headers: {
      'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN,
      'x-vercel-set-bypass-cookie': 'true',
    },
    redirect: 'manual',
  });
  
  const rawCookies = bypassRes.headers.getSetCookie ? bypassRes.headers.getSetCookie() : [bypassRes.headers.get('set-cookie')];
  let vercelJwtCookie = '';
  rawCookies.filter(Boolean).forEach((c) => {
    if (c.includes('_vercel_jwt=')) {
      vercelJwtCookie = c.split(';')[0];
    }
  });

  if (!vercelJwtCookie) {
    console.error('FAIL: Could not acquire _vercel_jwt cookie');
    process.exit(1);
  }
  console.log('    PASS: Deployment protection bypass cookie successfully established.');

  // Build Supabase session cookie chunk format using @supabase/ssr createChunks
  const sessionPayload = {
    access_token: authData.access_token,
    refresh_token: authData.refresh_token,
    user: authData.user,
    token_type: 'bearer',
    expires_in: authData.expires_in,
    expires_at: authData.expires_at,
  };
  const cookieName = 'sb-kpwdvvcschankgggitnj-auth-token';
  const chunks = await createChunks(cookieName, JSON.stringify(sessionPayload));
  const authCookieHeader = chunks.map((c) => `${c.name}=${encodeURIComponent(c.value)}`).join('; ');

  async function checkRoute(route, useAuth, expectedBehavior) {
    const url = `${DEPLOYED_URL}${route}`;
    const cookieHeaderStr = useAuth
      ? `${vercelJwtCookie}; ${authCookieHeader}`
      : vercelJwtCookie;

    const res = await fetch(url, {
      method: 'GET',
      headers: {
        Cookie: cookieHeaderStr,
        'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN,
      },
      redirect: 'manual',
    });

    const status = res.status;
    const location = res.headers.get('location') || '';
    let pass = false;

    if (expectedBehavior === 'ALLOW_200') {
      pass = status === 200;
    } else if (expectedBehavior === 'REDIRECT_LOGIN') {
      pass = (status === 307 || status === 302) && location.includes('/login');
    } else if (expectedBehavior === 'REDIRECT_FORBIDDEN') {
      pass = (status === 307 || status === 302) && (location.includes('/access-denied?reason=forbidden') || location.includes('/access-denied'));
    }

    console.log(`    GET ${route.padEnd(10)} [${useAuth ? 'Faculty Auth' : 'Unauthenticated'}] => Status: ${status}, Location: ${location || 'none'} | Result: ${pass ? 'PASS' : 'FAIL'}`);
    return pass;
  }

  // A. Unauthenticated access guards
  console.log('\n  A. Unauthenticated Route Protection:');
  const u1 = await checkRoute('/faculty', false, 'REDIRECT_LOGIN');
  const u2 = await checkRoute('/platform', false, 'REDIRECT_LOGIN');
  const u3 = await checkRoute('/college', false, 'REDIRECT_LOGIN');

  // B. Authenticated Faculty access
  console.log('\n  B. Authenticated Faculty Route Permissions & Boundaries:');
  const f1 = await checkRoute('/faculty', true, 'ALLOW_200');
  const f2 = await checkRoute('/platform', true, 'REDIRECT_FORBIDDEN');
  const f3 = await checkRoute('/college', true, 'REDIRECT_FORBIDDEN');

  // C. Root / Route post-login redirection
  console.log('\n  C. Post-Login Root Route Redirection:');
  const rootRes = await fetch(`${DEPLOYED_URL}/`, {
    method: 'GET',
    headers: {
      Cookie: `${vercelJwtCookie}; ${authCookieHeader}`,
      'x-vercel-protection-bypass': VERCEL_BYPASS_TOKEN,
    },
    redirect: 'manual',
  });
  const rootStatus = rootRes.status;
  const rootLocation = rootRes.headers.get('location') || '';
  const rootPass = (rootStatus === 307 || rootStatus === 302) && rootLocation.endsWith('/faculty');
  console.log(`    GET /          [Faculty Auth] => Status: ${rootStatus}, Location: ${rootLocation} | Result: ${rootPass ? 'PASS' : 'FAIL'}`);

  const allPassed = u1 && u2 && u3 && f1 && f2 && f3 && rootPass;

  if (allPassed) {
    console.log('\n=== ALL FACULTY AUTHENTICATION & ROUTE GUARD CHECKS PASSED ===\n');
  } else {
    console.error('\n=== ONE OR MORE CHECKS FAILED ===\n');
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Fatal execution error:', err.message);
  process.exit(1);
});
