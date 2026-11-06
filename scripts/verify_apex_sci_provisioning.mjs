// scripts/verify_apex_sci_provisioning.mjs
import fs from 'fs';
import path from 'path';
import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const APPROVED_DEV_REF = 'azulbaxszsqxymqmlwfe';
const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';
const FORBIDDEN_STAGING_REF = 'kpwdvvcschankgggitnj';

function getPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || '';
  const password = process.env.SUPABASE_PASSWORD || '';

  if (!username.includes(APPROVED_DEV_REF)) {
    throw new Error('FATAL: Target username does not match approved dev ref: ' + username);
  }
  if (username.includes(FORBIDDEN_PROD_REF) || jdbcUrl.includes(FORBIDDEN_PROD_REF)) {
    throw new Error('FATAL: Production target detected!');
  }
  if (username.includes(FORBIDDEN_STAGING_REF) || jdbcUrl.includes(FORBIDDEN_STAGING_REF)) {
    throw new Error('FATAL: Legacy staging target detected!');
  }

  let connString = jdbcUrl.replace(/^jdbc:/, '').replace('ap-southeast-1', 'ap-south-1');
  const urlObj = new URL(connString);
  urlObj.username = username;
  urlObj.password = password;
  urlObj.searchParams.delete('sslmode');

  return new Pool({
    connectionString: urlObj.toString(),
    ssl: { rejectUnauthorized: false },
    max: 2,
    idleTimeoutMillis: 10000,
  });
}

let passed = 0;
let failed = 0;

function assert(description, condition, extra = '') {
  if (condition) {
    console.log(`[PASS] ${description}`);
    passed++;
  } else {
    console.error(`[FAIL] ${description} ${extra}`);
    failed++;
  }
}

async function main() {
  console.log('=== CAMPXSYNC LIVE PROVISIONING & VERIFICATION: APEX_SCI ===\n');

  const pool = getPool();
  const client = await pool.connect();

  try {
    // 1. Safety Guard Checks
    console.log('--- Step 1: Target Database Verification ---');
    const targetCheck = await client.query('SELECT current_database(), current_user, inet_server_addr()::text as ip');
    const db = targetCheck.rows[0].current_database;
    const usr = targetCheck.rows[0].current_user;
    console.log(`Target: database=${db}, user=${usr}, ip=${targetCheck.rows[0].ip}`);
    assert('Target database is postgres', db === 'postgres');
    assert('Target user is postgres on dev ref', usr.includes(APPROVED_DEV_REF) || usr === 'postgres');

    // 2. Execute Migration: 20261105000400_provision_apex_sci.sql
    console.log('\n--- Step 2: Executing Live Provisioning Migration ---');
    const migrationSql = fs.readFileSync(
      path.join(process.cwd(), 'database/migrations/20261105000400_provision_apex_sci.sql'),
      'utf-8'
    );
    await client.query(migrationSql);
    console.log('Migration executed successfully.');

    // 3. Verification of Physical Records
    console.log('\n--- Step 3: Verifying Database Persistence ---');
    const tenantId = 'c33a407e-3bda-464e-9543-ca46a011aead';
    const collegeId = 'c33a407e-3bda-464e-9543-ca46a0110001';
    const adminId = 'c33a407e-3bda-464e-9543-ca46a0110002';

    // 3a. core.tenants
    const tRes = await client.query('SELECT * FROM core.tenants WHERE id = $1', [tenantId]);
    assert('core.tenants: Record exists for APEX_SCI', tRes.rows.length === 1);
    assert('core.tenants: Status is ACTIVE', tRes.rows[0]?.status === 'ACTIVE');
    assert('core.tenants: Code is APEX_SCI', tRes.rows[0]?.code === 'APEX_SCI');
    assert('core.tenants: Name is Apex Institute of Science & Technology', tRes.rows[0]?.name === 'Apex Institute of Science & Technology');

    // 3b. core.colleges
    const cRes = await client.query('SELECT * FROM core.colleges WHERE id = $1', [collegeId]);
    assert('core.colleges: Record exists for School of Computing', cRes.rows.length === 1);
    assert('core.colleges: Status is ACTIVE', cRes.rows[0]?.status === 'ACTIVE');
    assert('core.colleges: Provisioning status is ACTIVE', cRes.rows[0]?.provisioning_status === 'ACTIVE');
    assert('core.colleges: Code is SOC', cRes.rows[0]?.code === 'SOC');

    // 3c. plat.tenant_provisioning
    const pRes = await client.query('SELECT * FROM plat.tenant_provisioning WHERE tenant_id = $1', [tenantId]);
    assert('plat.tenant_provisioning: Job record exists', pRes.rows.length === 1);
    assert('plat.tenant_provisioning: Status is COMPLETED', pRes.rows[0]?.status === 'COMPLETED');

    // 3d. iam.roles & permissions
    const rRes = await client.query('SELECT count(*)::int as count FROM iam.roles WHERE tenant_id = $1 AND deleted_at IS NULL', [tenantId]);
    assert('iam.roles: 30 system roles seeded for tenant', rRes.rows[0]?.count === 30, `got ${rRes.rows[0]?.count}`);

    const rpRes = await client.query('SELECT count(*)::int as count FROM iam.role_permissions WHERE tenant_id = $1', [tenantId]);
    assert('iam.role_permissions: Permissions populated for roles', rpRes.rows[0]?.count > 0, `got ${rpRes.rows[0]?.count}`);

    // 3e. core.persons & iam.user_profiles
    const perRes = await client.query('SELECT * FROM core.persons WHERE id = $1', [adminId]);
    assert('core.persons: Administrator person record exists', perRes.rows.length === 1);
    assert('core.persons: Display name is Prof. Robert Vance', perRes.rows[0]?.display_name === 'Prof. Robert Vance');

    const uRes = await client.query('SELECT * FROM iam.user_profiles WHERE id = $1', [adminId]);
    assert('iam.user_profiles: Admin profile exists', uRes.rows.length === 1);
    assert('iam.user_profiles: Status is ACTIVE', uRes.rows[0]?.status === 'ACTIVE');
    assert('iam.user_profiles: Email is robert.vance@apex.edu', uRes.rows[0]?.email === 'robert.vance@apex.edu');

    // 3f. iam.role_assignments
    const raRes = await client.query(
      `SELECT ra.*, r.code as role_code 
       FROM iam.role_assignments ra 
       JOIN iam.roles r ON r.id = ra.role_id 
       WHERE ra.tenant_id = $1 AND ra.user_id = $2`,
      [tenantId, adminId]
    );
    assert('iam.role_assignments: Role assignment exists', raRes.rows.length >= 1);
    assert('iam.role_assignments: Assigned role is COLLEGE_ADMIN', raRes.rows.some(r => r.role_code === 'COLLEGE_ADMIN'));

    // 3g. sys.outbox_events & audit.events
    const outRes = await client.query(
      "SELECT * FROM sys.outbox_events WHERE tenant_id = $1 AND event_type = 'tenant.onboarded.v1'",
      [tenantId]
    );
    assert('sys.outbox_events: Outbox event emitted', outRes.rows.length >= 1);
    assert('sys.outbox_events: Status is PENDING', outRes.rows[0]?.status === 'PENDING');

    const audRes = await client.query(
      "SELECT * FROM audit.events WHERE tenant_id = $1 AND action = 'TENANT_ONBOARDED'",
      [tenantId]
    );
    assert('audit.events: Audit event logged', audRes.rows.length >= 1);
    assert('audit.events: Status is SUCCESS', audRes.rows[0]?.status === 'SUCCESS');

    // 4. Verification of RLS & Multi-Tenant Isolation
    console.log('\n--- Step 4: Verifying RLS & Multi-Tenant Isolation ---');
    const devTenantId = 'a0000000-0000-0000-0000-000000000001';

    // Set authenticated session as College Admin of DEV_TEST_TENANT
    await client.query('BEGIN');
    await client.query("SET LOCAL ROLE authenticated");
    await client.query(
      "SELECT set_config('request.jwt.claims', $1, true)",
      [JSON.stringify({
        sub: '70a8ffae-b5ad-4c61-a401-16f220893df4',
        user_id: '70a8ffae-b5ad-4c61-a401-16f220893df4',
        tenant_id: devTenantId,
        user_role: 'COLLEGE_ADMIN',
        role: 'authenticated'
      })]
    );

    // DEV_TEST_TENANT querying core.colleges
    const devColleges = await client.query('SELECT id, name, code, tenant_id FROM core.colleges');
    assert(
      'Isolation: DEV_TEST_TENANT cannot see APEX_SCI colleges',
      !devColleges.rows.some(r => r.tenant_id === tenantId),
      `dev saw: ${JSON.stringify(devColleges.rows.map(r => r.code))}`
    );

    // DEV_TEST_TENANT querying iam.user_profiles
    const devUsers = await client.query('SELECT email, tenant_id FROM iam.user_profiles');
    assert(
      'Isolation: DEV_TEST_TENANT cannot see APEX_SCI administrator profile',
      !devUsers.rows.some(r => r.tenant_id === tenantId),
      `dev saw: ${JSON.stringify(devUsers.rows.map(r => r.email))}`
    );

    // Now switch session to APEX_SCI College Admin
    await client.query(
      "SELECT set_config('request.jwt.claims', $1, true)",
      [JSON.stringify({
        sub: adminId,
        user_id: adminId,
        tenant_id: tenantId,
        user_role: 'COLLEGE_ADMIN',
        role: 'authenticated'
      })]
    );

    // APEX_SCI querying core.colleges
    const apexColleges = await client.query('SELECT id, name, code, tenant_id FROM core.colleges');
    assert(
      'Isolation: APEX_SCI can see own college (SOC)',
      apexColleges.rows.some(r => r.code === 'SOC' && r.tenant_id === tenantId)
    );
    assert(
      'Isolation: APEX_SCI cannot see DEV_TEST_TENANT colleges',
      !apexColleges.rows.some(r => r.tenant_id === devTenantId)
    );

    await client.query('ROLLBACK');

    // 5. Verification of Rollback Behavior
    console.log('\n--- Step 5: Verifying Transactional Rollback Behavior ---');
    await client.query('BEGIN');
    // Insert a throwaway college inside APEX_SCI
    const throwawayCollegeId = 'c33a407e-3bda-464e-9543-ca46a0119999';
    await client.query(
      `INSERT INTO core.colleges (id, tenant_id, code, name, status, provisioning_status)
       VALUES ($1, $2, 'THROWAWAY', 'Throwaway College', 'ACTIVE', 'ACTIVE')`,
      [throwawayCollegeId, tenantId]
    );
    // Rollback
    await client.query('ROLLBACK');

    const rollbackCheck = await client.query('SELECT id FROM core.colleges WHERE id = $1', [throwawayCollegeId]);
    assert('Rollback Integrity: Rolled-back record does not persist in core.colleges', rollbackCheck.rows.length === 0);

    // 6. Verification of Conflict Interception
    console.log('\n--- Step 6: Verifying Conflict Detection & Idempotency ---');
    // Try inserting duplicate tenant code with different UUID
    let conflictCaught = false;
    try {
      await client.query(
        `INSERT INTO core.tenants (id, code, name, status)
         VALUES (gen_random_uuid(), 'APEX_SCI', 'Duplicate Apex', 'ACTIVE')`
      );
    } catch (err) {
      conflictCaught = true;
      assert('Conflict Detection: Duplicate tenant code rejected by unique index', err.message.includes('unique') || err.message.includes('tenants'));
    }
    if (!conflictCaught) {
      console.error('[FAIL] Conflict Detection: Duplicate tenant code was not rejected!');
      failed++;
    }

    console.log('\n================================================================');
    console.log(`   APEX_SCI PROVISIONING VERIFICATION COMPLETE: ${passed} PASSED, ${failed} FAILED   `);
    console.log('================================================================\n');

  } finally {
    client.release();
    await pool.end();
  }

  if (failed > 0) {
    process.exit(1);
  }
}

main().catch((err) => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});

