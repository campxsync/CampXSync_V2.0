// scripts/apply_faculty_staging_linkage.mjs
// Single-purpose script to apply the approved Faculty profile and role assignment on Staging.

import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';
const EXPECTED_STAGING_REF = 'kpwdvvcschankgggitnj';

const TARGET_FACULTY_AUTH_UUID = '619b460b-d7f9-46b6-b11a-56045a130a52';
const TARGET_FACULTY_EMAIL = 'faculty.campx@gmail.com';
const TARGET_TENANT_ID = '11111111-1111-1111-1111-111111111111';
const TARGET_ROLE_ID = '7e997223-3d56-4c4e-85bd-228d9e623423'; // FACULTY role in tenant 11111111-1111-1111-1111-111111111111
const TARGET_COLLEGE_ID = '11111111-c011-0000-0000-000000000001';

function getDbPool() {
  const jdbcUrl = process.env.SUPABASE_JDBC_URL || process.env.CAMPX_JDBC_URL || '';
  const username = process.env.SUPABASE_USER || process.env.CAMPX_DB_USER;
  const password = process.env.SUPABASE_PASSWORD || process.env.CAMPX_DB_PASSWORD;

  if (!jdbcUrl) {
    throw new Error('Database JDBC URL is not configured in environment.');
  }

  // Security Guard: Production isolation
  if (jdbcUrl.includes(FORBIDDEN_PROD_REF) || (username && username.includes(FORBIDDEN_PROD_REF))) {
    throw new Error('FATAL SECURITY VIOLATION: Database connection string targets forbidden production reference!');
  }

  // Staging assertion
  if (username && !username.includes(EXPECTED_STAGING_REF)) {
    throw new Error(`SAFETY REFUSAL: Database username does not match expected staging reference ${EXPECTED_STAGING_REF}!`);
  }

  let connString = jdbcUrl.replace(/^jdbc:/, '');
  const urlObj = new URL(connString);
  if (username) urlObj.username = username;
  if (password) urlObj.password = password;
  urlObj.searchParams.delete('sslmode');

  return new Pool({
    connectionString: urlObj.toString(),
    ssl: { rejectUnauthorized: false },
    max: 1,
    idleTimeoutMillis: 5000,
  });
}

async function main() {
  console.log('=== FACULTY STAGING LINKAGE APPLICATION (STAGING ONLY) ===');
  console.log(`Verified Target Ref:     ${EXPECTED_STAGING_REF}`);
  console.log(`Target Auth UUID:       ${TARGET_FACULTY_AUTH_UUID}`);
  console.log(`Target Email:           ${TARGET_FACULTY_EMAIL}`);
  console.log(`Target Tenant ID:       ${TARGET_TENANT_ID}`);
  console.log(`Target Role ID:         ${TARGET_ROLE_ID} (FACULTY)`);
  console.log(`Target College ID:      ${TARGET_COLLEGE_ID}`);

  const pool = getDbPool();

  try {
    // 1. Verify connection and current database
    const connCheck = await pool.query('SELECT current_database(), current_user');
    console.log(`Database connected: ${connCheck.rows[0].current_database} as user ${connCheck.rows[0].current_user}`);

    // 2. Verify target Auth user exists in auth.users
    const authCheck = await pool.query('SELECT id, email FROM auth.users WHERE id = $1', [TARGET_FACULTY_AUTH_UUID]);
    if (authCheck.rows.length === 0) {
      throw new Error(`Target Auth User ${TARGET_FACULTY_AUTH_UUID} not found in auth.users on staging database!`);
    }
    console.log(`Found Auth User: ${authCheck.rows[0].id} (${authCheck.rows[0].email})`);

    // 3. Verify tenant exists
    const tenantCheck = await pool.query('SELECT id, name FROM core.tenants WHERE id = $1', [TARGET_TENANT_ID]);
    if (tenantCheck.rows.length === 0) {
      throw new Error(`Target Tenant ${TARGET_TENANT_ID} not found in core.tenants!`);
    }
    console.log(`Found Tenant: ${tenantCheck.rows[0].id} (${tenantCheck.rows[0].name})`);

    // 4. Verify role exists
    const roleCheck = await pool.query('SELECT id, code, name FROM iam.roles WHERE id = $1 AND tenant_id = $2', [
      TARGET_ROLE_ID,
      TARGET_TENANT_ID,
    ]);
    if (roleCheck.rows.length === 0) {
      throw new Error(`Target Role ${TARGET_ROLE_ID} not found in iam.roles for tenant ${TARGET_TENANT_ID}!`);
    }
    console.log(`Found Role: ${roleCheck.rows[0].id} (${roleCheck.rows[0].code} - ${roleCheck.rows[0].name})`);

    // 5. Execute Approved Linkage: iam.user_profiles
    console.log('\n--- EXECUTING APPROVED iam.user_profiles INSERT ---');
    const profileSql = `
      INSERT INTO iam.user_profiles (id, tenant_id, email, full_name, status)
      VALUES (
        $1,
        $2,
        $3,
        'Faculty Member',
        'ACTIVE'
      )
      ON CONFLICT (id) DO UPDATE SET
        tenant_id = EXCLUDED.tenant_id,
        status = 'ACTIVE',
        deleted_at = NULL
      RETURNING id, tenant_id, email, full_name, status, created_at, updated_at;
    `;
    const profileRes = await pool.query(profileSql, [
      TARGET_FACULTY_AUTH_UUID,
      TARGET_TENANT_ID,
      TARGET_FACULTY_EMAIL,
    ]);
    console.log('iam.user_profiles Result:');
    console.log(JSON.stringify(profileRes.rows[0], null, 2));

    // 6. Execute Approved Linkage: iam.role_assignments
    console.log('\n--- EXECUTING APPROVED iam.role_assignments INSERT ---');
    const roleAssignSql = `
      INSERT INTO iam.role_assignments (tenant_id, user_id, role_id, college_id)
      VALUES (
        $1,
        $2,
        $3,
        $4
      )
      ON CONFLICT DO NOTHING
      RETURNING id, tenant_id, user_id, role_id, college_id, created_at;
    `;
    const roleAssignRes = await pool.query(roleAssignSql, [
      TARGET_TENANT_ID,
      TARGET_FACULTY_AUTH_UUID,
      TARGET_ROLE_ID,
      TARGET_COLLEGE_ID,
    ]);
    console.log('iam.role_assignments Insert Result:');
    console.log(JSON.stringify(roleAssignRes.rows, null, 2));

    // 7. Authoritative Read-Only Verification
    console.log('\n--- AUTHORITATIVE READ-ONLY VERIFICATION ---');
    const verifyProfileSql = `
      SELECT id, tenant_id, email, full_name, status, deleted_at
      FROM iam.user_profiles
      WHERE id = $1;
    `;
    const verifyProfile = await pool.query(verifyProfileSql, [TARGET_FACULTY_AUTH_UUID]);
    console.log(`User Profile found: ${verifyProfile.rows.length}`);
    console.log(JSON.stringify(verifyProfile.rows, null, 2));

    const verifyRoleSql = `
      SELECT ra.id, ra.tenant_id, ra.user_id, ra.role_id, r.code AS role_code, r.name AS role_name, ra.college_id
      FROM iam.role_assignments ra
      JOIN iam.roles r ON r.id = ra.role_id
      WHERE ra.user_id = $1 AND ra.tenant_id = $2;
    `;
    const verifyRole = await pool.query(verifyRoleSql, [TARGET_FACULTY_AUTH_UUID, TARGET_TENANT_ID]);
    console.log(`Role Assignments found: ${verifyRole.rows.length}`);
    console.log(JSON.stringify(verifyRole.rows, null, 2));

    if (
      verifyProfile.rows.length === 1 &&
      verifyProfile.rows[0].status === 'ACTIVE' &&
      verifyRole.rows.length >= 1 &&
      verifyRole.rows.some((r) => r.role_code === 'FACULTY')
    ) {
      console.log('\nSUCCESS: Faculty profile and role assignment authoritatively linked and verified on staging.');
    } else {
      throw new Error('VERIFICATION FAILED: Database state does not match expected Faculty linkage.');
    }
  } finally {
    await pool.end();
  }
}

main().catch((err) => {
  console.error('\nEXECUTION FAILED:', err.message);
  process.exit(1);
});
