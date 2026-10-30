// scripts/probe_resolve_role_latency.mjs
// Measure individual steps of resolveUserRole and database queries

import fs from 'node:fs';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import { createRequire } from 'node:module';

const require = createRequire(path.resolve('frontend/package.json'));
const { createClient } = require('@supabase/supabase-js');
const pg = require('pg');

const SUPABASE_URL = 'https://kpwdvvcschankgggitnj.supabase.co';
const ANON_KEY = 'sb_publishable_1B6QXXcsRiUaxj-Bk1pPuw_JMqgh4-J';

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

const collegePassword = getEnvVar('CAMPX_STAGING_TEST_PASSWORD');
const jdbcUrl = getEnvVar('SUPABASE_JDBC_URL') || '';
const dbUser = getEnvVar('SUPABASE_USER');
const dbPassword = getEnvVar('SUPABASE_PASSWORD');

async function main() {
  console.log('=== PROBING RESOLVE-ROLE & DATABASE LATENCIES ===\n');

  // 1. Measure GoTrue login
  console.log('[1] Supabase GoTrue Auth:');
  const t0 = performance.now();
  const supabase = createClient(SUPABASE_URL, ANON_KEY);
  const { data: authData, error: authError } = await supabase.auth.signInWithPassword({
    email: 'campxsync@gmail.com',
    password: collegePassword,
  });
  const tAuth = Math.round(performance.now() - t0);
  console.log(`    signInWithPassword duration: ${tAuth}ms (User: ${authData?.user?.id})`);

  const userClient = createClient(SUPABASE_URL, ANON_KEY, {
    global: {
      headers: { Authorization: `Bearer ${authData.session.access_token}` },
    },
  });

  // 2. Measure getUser() with user token
  console.log('\n[2] supabase.auth.getUser():');
  const t1 = performance.now();
  const { data: uData } = await userClient.auth.getUser();
  const tGetUser = Math.round(performance.now() - t1);
  console.log(`    getUser() duration: ${tGetUser}ms`);

  const userId = authData.user.id;

  // 3. Measure PostgREST calls vs SQL queries
  console.log('\n[3] PostgREST schema calls:');
  
  // plat.platform_admins via PostgREST
  const tPlatPgRest = performance.now();
  let platPgRestStatus = 'OK';
  try {
    const res = await userClient
      .schema('plat')
      .from('platform_admins')
      .select('id, status, role_code')
      .eq('user_id', userId)
      .eq('role_code', 'SUPER_ADMIN')
      .eq('status', 'ACTIVE')
      .is('deleted_at', null)
      .maybeSingle();
    platPgRestStatus = res.error ? `Error: ${res.error.message} (${res.error.code})` : `Data: ${JSON.stringify(res.data)}`;
  } catch (e) {
    platPgRestStatus = `Threw: ${e.message}`;
  }
  const dPlatPgRest = Math.round(performance.now() - tPlatPgRest);
  console.log(`    PostgREST plat.platform_admins: ${dPlatPgRest}ms | ${platPgRestStatus}`);

  // iam.user_profiles via PostgREST
  const tProfPgRest = performance.now();
  let profPgRestStatus = 'OK';
  try {
    const res = await userClient
      .schema('iam')
      .from('user_profiles')
      .select('id, tenant_id, status, deleted_at')
      .eq('id', userId)
      .maybeSingle();
    profPgRestStatus = res.error ? `Error: ${res.error.message} (${res.error.code})` : `Data: ${JSON.stringify(res.data)}`;
  } catch (e) {
    profPgRestStatus = `Threw: ${e.message}`;
  }
  const dProfPgRest = Math.round(performance.now() - tProfPgRest);
  console.log(`    PostgREST iam.user_profiles: ${dProfPgRest}ms | ${profPgRestStatus}`);

  // core.tenants via PostgREST
  const tTenPgRest = performance.now();
  let tenPgRestStatus = 'OK';
  try {
    const res = await userClient
      .schema('core')
      .from('tenants')
      .select('id, name, code, status, deleted_at')
      .eq('id', '11111111-1111-1111-1111-111111111111')
      .maybeSingle();
    tenPgRestStatus = res.error ? `Error: ${res.error.message} (${res.error.code})` : `Data: ${JSON.stringify(res.data)}`;
  } catch (e) {
    tenPgRestStatus = `Threw: ${e.message}`;
  }
  const dTenPgRest = Math.round(performance.now() - tTenPgRest);
  console.log(`    PostgREST core.tenants: ${dTenPgRest}ms | ${tenPgRestStatus}`);

  // iam.role_assignments via PostgREST
  const tRolesPgRest = performance.now();
  let rolesPgRestStatus = 'OK';
  try {
    const res = await userClient
      .schema('iam')
      .from('role_assignments')
      .select('id, college_id, department_id, valid_from, valid_to, roles:role_id (code, name)')
      .eq('user_id', userId)
      .is('deleted_at', null);
    rolesPgRestStatus = res.error ? `Error: ${res.error.message} (${res.error.code})` : `Data count: ${res.data?.length}`;
  } catch (e) {
    rolesPgRestStatus = `Threw: ${e.message}`;
  }
  const dRolesPgRest = Math.round(performance.now() - tRolesPgRest);
  console.log(`    PostgREST iam.role_assignments: ${dRolesPgRest}ms | ${rolesPgRestStatus}`);

  // 4. Measure Direct PostgreSQL Pool Queries
  console.log('\n[4] Direct PostgreSQL Pool via JDBC/TLS:');
  let connString = jdbcUrl.replace(/^jdbc:/, '');
  const urlObj = new URL(connString);
  if (dbUser) urlObj.username = dbUser;
  if (dbPassword) urlObj.password = dbPassword;
  urlObj.searchParams.delete('sslmode');

  const tPoolConnect = performance.now();
  const pool = new pg.Pool({
    connectionString: urlObj.toString(),
    ssl: { rejectUnauthorized: false },
    max: 3,
    idleTimeoutMillis: 5000,
  });
  const client = await pool.connect();
  const dPoolConnect = Math.round(performance.now() - tPoolConnect);
  console.log(`    Pool initial connection & TLS handshake: ${dPoolConnect}ms`);

  const tQ1 = performance.now();
  await client.query('SELECT id, status, role_code FROM plat.platform_admins WHERE user_id = $1 LIMIT 1', [userId]);
  const dQ1 = Math.round(performance.now() - tQ1);
  console.log(`    Query plat.platform_admins: ${dQ1}ms`);

  const tQ2 = performance.now();
  await client.query('SELECT id, tenant_id, status, deleted_at FROM iam.user_profiles WHERE id = $1 LIMIT 1', [userId]);
  const dQ2 = Math.round(performance.now() - tQ2);
  console.log(`    Query iam.user_profiles: ${dQ2}ms`);

  const tQ3 = performance.now();
  await client.query(`
    SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to,
           r.code as role_code, r.name as role_name
    FROM iam.role_assignments ra
    JOIN iam.roles r ON ra.role_id = r.id
    WHERE ra.user_id = $1 AND ra.deleted_at IS NULL AND r.deleted_at IS NULL
  `, [userId]);
  const dQ3 = Math.round(performance.now() - tQ3);
  console.log(`    Query iam.role_assignments: ${dQ3}ms`);

  client.release();
  await pool.end();

  console.log('\n=== PROBE COMPLETE ===');
}

main().catch(console.error);
