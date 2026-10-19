// scripts/apply_platform_admin_provisioning.mjs
// Single-purpose script to apply the approved Platform Admin mapping on Staging.

import pg from '../frontend/node_modules/pg/lib/index.js';

const { Pool } = pg;
const FORBIDDEN_PROD_REF = 'ekspahbsbwdpraaqfqui';
const EXPECTED_STAGING_REF = 'kpwdvvcschankgggitnj';

const TARGET_AUTH_UUID = '54e8308c-ada3-4065-a9fe-51814af6b971';
const TARGET_AUTH_EMAIL = 'platformadmin.campx@gmail.com';
const TARGET_ROLE_CODE = 'SUPER_ADMIN';

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
  console.log('=== PLATFORM ADMIN PROVISIONING (STAGING ONLY) ===');
  console.log(`Verified Target Ref: ${EXPECTED_STAGING_REF}`);
  console.log(`Target Auth UUID:   ${TARGET_AUTH_UUID}`);
  console.log(`Target Auth Email:  ${TARGET_AUTH_EMAIL}`);
  console.log(`Target Role Code:   ${TARGET_ROLE_CODE}`);

  const pool = getDbPool();

  try {
    // 1. Verify connection and current database
    const connCheck = await pool.query('SELECT current_database(), current_user');
    console.log(`Database connected: ${connCheck.rows[0].current_database} as user ${connCheck.rows[0].current_user}`);

    // 2. Verify target Auth user exists in auth.users
    const authCheck = await pool.query('SELECT id, email FROM auth.users WHERE id = $1', [TARGET_AUTH_UUID]);
    if (authCheck.rows.length === 0) {
      throw new Error(`Target Auth User ${TARGET_AUTH_UUID} not found in auth.users on staging database!`);
    }
    console.log(`Found Auth User: ${authCheck.rows[0].id} (${authCheck.rows[0].email})`);

    // 3. Pre-check: Ensure user has no existing plat.platform_admins mapping to avoid duplication
    const existingCheck = await pool.query(
      'SELECT id, user_id, full_name, role_code, status FROM plat.platform_admins WHERE user_id = $1',
      [TARGET_AUTH_UUID]
    );

    if (existingCheck.rows.length > 0) {
      throw new Error(`Unexpected existing plat.platform_admins mapping found: ${JSON.stringify(existingCheck.rows[0])}. Stopping to avoid duplication.`);
    }

    console.log('Pre-check confirmed: Zero existing plat.platform_admins rows for this Auth UUID.');

    // 4. Execute the INSERT statement with role_code = 'SUPER_ADMIN'
    const insertSql = `
      INSERT INTO plat.platform_admins (
        id,
        user_id,
        full_name,
        role_code,
        status,
        created_at,
        updated_at
      )
      VALUES (
        gen_random_uuid(),
        $1,
        'CampXSync Platform Administrator',
        $2,
        'ACTIVE',
        NOW(),
        NOW()
      )
      RETURNING id, user_id, full_name, role_code, status, created_at, updated_at;
    `;

    console.log('Executing Operation: INSERT INTO plat.platform_admins ...');
    const res = await pool.query(insertSql, [TARGET_AUTH_UUID, TARGET_ROLE_CODE]);
    const insertResult = res.rows[0];

    console.log('\n--- INSERT ... RETURNING RESULT ---');
    console.log(JSON.stringify(insertResult, null, 2));

    // 5. Targeted Verification Query
    console.log('\n--- TARGETED VERIFICATION QUERY ---');
    const verifySql = `
      SELECT id, user_id, full_name, role_code, status, created_at, updated_at 
      FROM plat.platform_admins 
      WHERE user_id = $1;
    `;
    const verifyRes = await pool.query(verifySql, [TARGET_AUTH_UUID]);
    console.log(`Verification query row count: ${verifyRes.rows.length}`);
    console.log(JSON.stringify(verifyRes.rows, null, 2));

    if (
      verifyRes.rows.length === 1 &&
      verifyRes.rows[0].user_id === TARGET_AUTH_UUID &&
      verifyRes.rows[0].role_code === TARGET_ROLE_CODE &&
      verifyRes.rows[0].status === 'ACTIVE'
    ) {
      console.log('\nSUCCESS: Platform Admin membership authoritatively verified in database.');
    } else {
      throw new Error('VERIFICATION FAILED: Database state does not match expected Platform Admin mapping.');
    }
  } finally {
    await pool.end();
  }
}

main().catch((err) => {
  console.error('\nEXECUTION FAILED:', err.message);
  process.exit(1);
});
